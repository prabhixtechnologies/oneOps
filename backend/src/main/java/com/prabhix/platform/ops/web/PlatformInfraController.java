package com.prabhix.platform.ops.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.ops.aws.PlatformAwsOpsService;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.ops.service.PlatformGithubChecksService;
import com.prabhix.platform.ops.service.PlatformHealthProbeService;
import com.prabhix.platform.ops.service.PlatformPnlService;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infra / control-plane ops under the platform-admin tree.
 *
 * <p>Consolidated from the former {@code Infra/ops-tool} microservice. Soft-fails AWS/GitHub
 * errors inside response bodies so the hub can still render.
 */
@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
public class PlatformInfraController {

    private static final Pattern RANGE = Pattern.compile("^(\\d+)d$", Pattern.CASE_INSENSITIVE);

    private final PlatformAwsOpsService platformAwsOpsService;
    private final PlatformHealthProbeService platformHealthProbeService;
    private final PlatformGithubChecksService platformGithubChecksService;
    private final PlatformPnlService platformPnlService;

    @GetMapping("/aws/summary")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsSummaryResponse awsSummary(
            @RequestParam(defaultValue = "30d") String range) {
        return platformAwsOpsService.summary(parseRangeDays(range));
    }

    @GetMapping("/aws/costs")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsCostsResponse awsCosts(
            @RequestParam(defaultValue = "30d") String range) {
        return platformAwsOpsService.getCosts(parseRangeDays(range));
    }

    @GetMapping("/aws/instances")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsInstancesResponse awsInstances() {
        return platformAwsOpsService.listInstances();
    }

    @GetMapping("/health/products")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.ProductHealthResponse healthProducts() {
        return platformHealthProbeService.probeProducts();
    }

    @GetMapping("/github/checks")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.GithubChecksResponse githubChecks() {
        return platformGithubChecksService.latestChecks();
    }

    @GetMapping("/pnl")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.PnlResponse pnl(
            @RequestParam(required = false) Double mobiCaptured,
            @RequestParam(required = false) Double oneopsCaptured,
            @RequestParam(required = false) Double awsMtd) {
        return platformPnlService.pnl(mobiCaptured, oneopsCaptured, awsMtd);
    }

    private static int parseRangeDays(String range) {
        String raw = range == null || range.isBlank() ? "30d" : range.trim();
        Matcher matcher = RANGE.matcher(raw);
        if (!matcher.matches()) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "range must look like '30d'");
        }
        int days = Integer.parseInt(matcher.group(1));
        if (days < 1 || days > 365) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "range must be between 1d and 365d");
        }
        return days;
    }
}
