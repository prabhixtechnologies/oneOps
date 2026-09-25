package com.prabhix.platform.ops.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.ops.aws.PlatformAwsOpsService;
import com.prabhix.platform.ops.domain.StaffRole;
import com.prabhix.platform.ops.dto.OpsDtos;
import com.prabhix.platform.ops.service.PlatformGithubChecksService;
import com.prabhix.platform.ops.service.PlatformHealthProbeService;
import com.prabhix.platform.ops.service.PlatformPnlService;
import com.prabhix.platform.ops.service.PlatformStaffService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infra / control-plane ops under the platform-admin tree.
 *
 * <p>Consolidated from the former {@code Infra/ops-tool} microservice. Soft-fails AWS/GitHub
 * errors inside response bodies so the hub can still render. OPERATOR (or OWNER) only.
 */
@RestController
@RequestMapping("/api/v1/oneops/admin/platform")
@RequiredArgsConstructor
public class PlatformInfraController {

    private static final Pattern RANGE = Pattern.compile("^(\\d+)d$", Pattern.CASE_INSENSITIVE);

    private final PlatformAwsOpsService platformAwsOpsService;
    private final PlatformHealthProbeService platformHealthProbeService;
    private final PlatformGithubChecksService platformGithubChecksService;
    private final PlatformPnlService platformPnlService;
    private final PlatformStaffService staff;

    private void requireOperator(PrabhixPrincipal principal) {
        staff.requireAny(principal.userId(), java.util.Set.of(StaffRole.OPERATOR));
    }

    @GetMapping("/aws/summary")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsSummaryResponse awsSummary(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(defaultValue = "30d") String range) {
        requireOperator(principal);
        return platformAwsOpsService.summary(parseRangeDays(range));
    }

    @GetMapping("/aws/costs")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsCostsResponse awsCosts(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(defaultValue = "30d") String range) {
        requireOperator(principal);
        return platformAwsOpsService.getCosts(parseRangeDays(range));
    }

    @GetMapping("/aws/instances")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsInstancesResponse awsInstances(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformAwsOpsService.listInstances();
    }

    @GetMapping("/aws/rds")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsRdsResponse awsRds(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformAwsOpsService.listRds();
    }

    @GetMapping("/aws/elasticache")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsElastiCacheResponse awsElastiCache(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformAwsOpsService.listElastiCache();
    }

    @GetMapping("/aws/ecr")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.AwsEcrResponse awsEcr(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformAwsOpsService.listEcr();
    }

    @GetMapping("/health/products")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.ProductHealthResponse healthProducts(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformHealthProbeService.probeProducts();
    }

    @GetMapping("/github/checks")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.GithubChecksResponse githubChecks(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformGithubChecksService.latestChecks();
    }

    @GetMapping("/github/pulls")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.GithubPullsResponse githubPulls(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformGithubChecksService.openPulls();
    }

    @GetMapping("/github/deploys")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.GithubDeploysResponse githubDeploys(@CurrentUser PrabhixPrincipal principal) {
        requireOperator(principal);
        return platformGithubChecksService.recentDeploys();
    }

    @PostMapping("/github/promote")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.PromoteResponse promote(@CurrentUser PrabhixPrincipal principal,
                                           @RequestBody OpsDtos.PromoteRequest request) {
        requireOperator(principal);
        return platformGithubChecksService.promote(request.service(), request.tag());
    }

    @GetMapping("/pnl")
    @PreAuthorize(Authorize.PLATFORM_ADMIN)
    public OpsDtos.PnlResponse pnl(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) Double mobiCaptured,
            @RequestParam(required = false) Double oneopsCaptured,
            @RequestParam(required = false) Double awsMtd) {
        requireOperator(principal);
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
