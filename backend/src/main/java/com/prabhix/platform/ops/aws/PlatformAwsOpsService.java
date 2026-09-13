package com.prabhix.platform.ops.aws;

import com.prabhix.platform.ops.dto.OpsDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only AWS surface for the admin ops hub: Cost Explorer, EC2, CloudWatch CPU.
 *
 * <p>Soft-fails on SDK errors — returns {@code ok:false} with an error map rather than 500ing the
 * whole hub when IAM or a regional outage blocks one call.
 */
@Slf4j
@Service
public class PlatformAwsOpsService {

    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AwsOpsProperties properties;
    private final ConcurrentHashMap<String, CachedCosts> costCache = new ConcurrentHashMap<>();

    private volatile CostExplorerClient costExplorerClient;
    private volatile Ec2Client ec2Client;
    private volatile CloudWatchClient cloudWatchClient;

    public PlatformAwsOpsService(AwsOpsProperties properties) {
        this.properties = properties;
    }

    public OpsDtos.AwsCostsResponse getCosts(int days) {
        if (!properties.enabled()) {
            return costsFailure(days, "Disabled", "AWS ops is disabled (prabhix.ops.aws.enabled=false)");
        }

        String cacheKey = "costs:" + days;
        CachedCosts cached = costCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            OpsDtos.AwsCostsResponse body = cached.body();
            return new OpsDtos.AwsCostsResponse(
                    body.ok(),
                    body.error(),
                    body.region(),
                    body.start(),
                    body.end(),
                    body.days(),
                    body.currency(),
                    body.daily(),
                    body.byService(),
                    body.totalUsd(),
                    body.mtdUsd(),
                    true,
                    body.fetchedAt());
        }

        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(days);
        String startStr = start.format(ISO_DAY);
        String endStr = end.format(ISO_DAY);

        try {
            CostExplorerClient ce = costExplorer();
            DateInterval period = DateInterval.builder().start(startStr).end(endStr).build();

            GetCostAndUsageResponse dailyResp = ce.getCostAndUsage(GetCostAndUsageRequest.builder()
                    .timePeriod(period)
                    .granularity(Granularity.DAILY)
                    .metrics("UnblendedCost")
                    .build());

            GetCostAndUsageResponse byServiceResp = ce.getCostAndUsage(GetCostAndUsageRequest.builder()
                    .timePeriod(period)
                    .granularity(Granularity.MONTHLY)
                    .metrics("UnblendedCost")
                    .groupBy(GroupDefinition.builder()
                            .type(GroupDefinitionType.DIMENSION)
                            .key("SERVICE")
                            .build())
                    .build());

            List<OpsDtos.AwsDailyCost> daily = new ArrayList<>();
            double total = 0.0;
            for (ResultByTime result : dailyResp.resultsByTime()) {
                double amount = unblendedAmount(result.total());
                total += amount;
                String day = result.timePeriod() != null ? result.timePeriod().start() : null;
                String unit = unblendedUnit(result.total());
                daily.add(new OpsDtos.AwsDailyCost(day, round4(amount), unit));
            }

            List<OpsDtos.AwsServiceCost> byService = new ArrayList<>();
            for (ResultByTime result : byServiceResp.resultsByTime()) {
                for (Group group : result.groups()) {
                    String service = group.keys() != null && !group.keys().isEmpty()
                            ? group.keys().get(0)
                            : "Unknown";
                    double amount = unblendedAmount(group.metrics());
                    byService.add(new OpsDtos.AwsServiceCost(service, round4(amount)));
                }
            }
            byService.sort(Comparator.comparingDouble(OpsDtos.AwsServiceCost::amountUsd).reversed());

            String monthPrefix = end.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            double mtd = daily.stream()
                    .filter(row -> row.date() != null && row.date().startsWith(monthPrefix))
                    .mapToDouble(OpsDtos.AwsDailyCost::amountUsd)
                    .sum();

            OpsDtos.AwsCostsResponse result = new OpsDtos.AwsCostsResponse(
                    true,
                    null,
                    properties.ceRegion(),
                    startStr,
                    endStr,
                    days,
                    "USD",
                    daily,
                    byService,
                    round4(total),
                    round4(mtd),
                    false,
                    Instant.now());

            long ttl = Math.max(1, properties.costCacheTtlSeconds());
            costCache.put(cacheKey, new CachedCosts(result, Instant.now().plusSeconds(ttl)));
            return result;
        } catch (SdkException ex) {
            log.warn("Cost Explorer GetCostAndUsage failed: {}", ex.getMessage());
            return costsFailure(days, awsErrorCode(ex), awsErrorMessage(ex), startStr, endStr);
        }
    }

    public OpsDtos.AwsInstancesResponse listInstances() {
        if (!properties.enabled()) {
            return instancesFailure("Disabled", "AWS ops is disabled (prabhix.ops.aws.enabled=false)");
        }

        try {
            Ec2Client ec2 = ec2();
            CloudWatchClient cw = cloudWatch();
            List<OpsDtos.AwsInstanceRow> instances = new ArrayList<>();

            String nextToken = null;
            do {
                var request = software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest.builder()
                        .nextToken(nextToken)
                        .build();
                DescribeInstancesResponse page = ec2.describeInstances(request);
                for (Reservation reservation : page.reservations()) {
                    for (Instance inst : reservation.instances()) {
                        String state = inst.state() != null ? inst.state().nameAsString() : "unknown";
                        String instanceId = inst.instanceId() != null ? inst.instanceId() : "";
                        Double cpu = null;
                        if ("running".equals(state) && !instanceId.isBlank()) {
                            cpu = cpuAverage1h(cw, instanceId);
                        }
                        instances.add(new OpsDtos.AwsInstanceRow(
                                instanceId,
                                instanceName(inst.tags()),
                                inst.instanceTypeAsString(),
                                state,
                                inst.placement() != null ? inst.placement().availabilityZone() : null,
                                inst.privateIpAddress(),
                                inst.publicIpAddress(),
                                inst.launchTime(),
                                cpu));
                    }
                }
                nextToken = page.nextToken();
            } while (nextToken != null && !nextToken.isBlank());

            int running = (int) instances.stream().filter(i -> "running".equals(i.state())).count();
            return new OpsDtos.AwsInstancesResponse(
                    true,
                    null,
                    properties.region(),
                    instances,
                    running,
                    instances.size());
        } catch (SdkException ex) {
            log.warn("EC2 DescribeInstances failed: {}", ex.getMessage());
            return instancesFailure(awsErrorCode(ex), awsErrorMessage(ex));
        }
    }

    public OpsDtos.AwsSummaryResponse summary(int days) {
        OpsDtos.AwsCostsResponse costs = getCosts(days);
        OpsDtos.AwsInstancesResponse instances = listInstances();

        List<OpsDtos.AwsServiceCost> topServices = costs.byService() == null
                ? List.of()
                : costs.byService().stream().limit(5).toList();

        return new OpsDtos.AwsSummaryResponse(
                Boolean.TRUE.equals(costs.ok()) && Boolean.TRUE.equals(instances.ok()),
                properties.region(),
                properties.ceRegion(),
                new OpsDtos.AwsSummaryCosts(
                        costs.totalUsd(),
                        costs.mtdUsd(),
                        costs.days() != null ? costs.days() : days,
                        topServices,
                        costs.cached(),
                        costs.ok(),
                        costs.error()),
                new OpsDtos.AwsSummaryInstances(
                        instances.total(),
                        instances.running(),
                        instances.ok(),
                        instances.error()),
                Instant.now());
    }

    private Double cpuAverage1h(CloudWatchClient cw, String instanceId) {
        Instant end = Instant.now();
        Instant start = end.minus(1, ChronoUnit.HOURS);
        try {
            GetMetricStatisticsResponse resp = cw.getMetricStatistics(GetMetricStatisticsRequest.builder()
                    .namespace("AWS/EC2")
                    .metricName("CPUUtilization")
                    .dimensions(Dimension.builder().name("InstanceId").value(instanceId).build())
                    .startTime(start)
                    .endTime(end)
                    .period(3600)
                    .statistics(Statistic.AVERAGE)
                    .unit(software.amazon.awssdk.services.cloudwatch.model.StandardUnit.PERCENT)
                    .build());
            List<Datapoint> datapoints = resp.datapoints();
            if (datapoints == null || datapoints.isEmpty()) {
                return null;
            }
            Datapoint latest = datapoints.stream()
                    .max(Comparator.comparing(Datapoint::timestamp, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            if (latest == null || latest.average() == null) {
                return null;
            }
            return Math.round(latest.average() * 100.0) / 100.0;
        } catch (SdkException ex) {
            log.debug("CloudWatch CPU for {} failed: {}", instanceId, ex.getMessage());
            return null;
        }
    }

    private CostExplorerClient costExplorer() {
        CostExplorerClient local = costExplorerClient;
        if (local == null) {
            synchronized (this) {
                local = costExplorerClient;
                if (local == null) {
                    local = CostExplorerClient.builder()
                            .region(Region.of(properties.ceRegion()))
                            .credentialsProvider(DefaultCredentialsProvider.create())
                            .build();
                    costExplorerClient = local;
                }
            }
        }
        return local;
    }

    private Ec2Client ec2() {
        Ec2Client local = ec2Client;
        if (local == null) {
            synchronized (this) {
                local = ec2Client;
                if (local == null) {
                    local = Ec2Client.builder()
                            .region(Region.of(properties.region()))
                            .credentialsProvider(DefaultCredentialsProvider.create())
                            .build();
                    ec2Client = local;
                }
            }
        }
        return local;
    }

    private CloudWatchClient cloudWatch() {
        CloudWatchClient local = cloudWatchClient;
        if (local == null) {
            synchronized (this) {
                local = cloudWatchClient;
                if (local == null) {
                    local = CloudWatchClient.builder()
                            .region(Region.of(properties.region()))
                            .credentialsProvider(DefaultCredentialsProvider.create())
                            .build();
                    cloudWatchClient = local;
                }
            }
        }
        return local;
    }

    private OpsDtos.AwsCostsResponse costsFailure(int days, String code, String message) {
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(days);
        return costsFailure(days, code, message, start.format(ISO_DAY), end.format(ISO_DAY));
    }

    private OpsDtos.AwsCostsResponse costsFailure(
            int days, String code, String message, String start, String end) {
        return new OpsDtos.AwsCostsResponse(
                false,
                errorMap(code, message),
                properties.ceRegion(),
                start,
                end,
                days,
                "USD",
                List.of(),
                List.of(),
                0.0,
                0.0,
                false,
                null);
    }

    private OpsDtos.AwsInstancesResponse instancesFailure(String code, String message) {
        return new OpsDtos.AwsInstancesResponse(
                false,
                errorMap(code, message),
                properties.region(),
                List.of(),
                0,
                0);
    }

    private static Map<String, String> errorMap(String code, String message) {
        Map<String, String> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    private static String awsErrorCode(SdkException ex) {
        if (ex instanceof AwsServiceException ase && ase.awsErrorDetails() != null
                && ase.awsErrorDetails().errorCode() != null) {
            return ase.awsErrorDetails().errorCode();
        }
        if (ex instanceof SdkClientException) {
            return ex.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName();
    }

    private static String awsErrorMessage(SdkException ex) {
        if (ex instanceof AwsServiceException ase && ase.awsErrorDetails() != null
                && ase.awsErrorDetails().errorMessage() != null) {
            return ase.awsErrorDetails().errorMessage();
        }
        String msg = ex.getMessage();
        return msg != null && !msg.isBlank() ? msg : ex.getClass().getSimpleName();
    }

    private static String instanceName(List<Tag> tags) {
        if (tags == null) {
            return null;
        }
        for (Tag tag : tags) {
            if ("Name".equals(tag.key())) {
                return tag.value();
            }
        }
        return null;
    }

    private static double unblendedAmount(Map<String, MetricValue> metrics) {
        if (metrics == null) {
            return 0.0;
        }
        MetricValue value = metrics.get("UnblendedCost");
        if (value == null || value.amount() == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.amount());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static String unblendedUnit(Map<String, MetricValue> metrics) {
        if (metrics == null) {
            return "USD";
        }
        MetricValue value = metrics.get("UnblendedCost");
        if (value == null || value.unit() == null || value.unit().isBlank()) {
            return "USD";
        }
        return value.unit();
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record CachedCosts(OpsDtos.AwsCostsResponse body, Instant expiresAt) {
    }
}
