package com.prabhix.platform.ops.service;

import com.prabhix.platform.ops.aws.PlatformAwsOpsService;
import com.prabhix.platform.ops.dto.OpsDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Simple contribution strip: captured revenue inputs minus AWS month-to-date cost.
 */
@Service
@RequiredArgsConstructor
public class PlatformPnlService {

    private final PlatformAwsOpsService platformAwsOpsService;

    public OpsDtos.PnlResponse pnl(Double mobiCaptured, Double oneopsCaptured, Double awsMtdOverride) {
        double mobi = mobiCaptured != null ? mobiCaptured : 0.0;
        double oneops = oneopsCaptured != null ? oneopsCaptured : 0.0;
        double revenue = mobi + oneops;

        OpsDtos.PnlRevenue revenueDto = new OpsDtos.PnlRevenue(
                round2(mobi),
                round2(oneops),
                round2(revenue));

        double awsCost;
        String awsSource;
        if (awsMtdOverride != null) {
            awsCost = awsMtdOverride;
            awsSource = "query";
        } else {
            OpsDtos.AwsCostsResponse costs = platformAwsOpsService.getCosts(30);
            if (!Boolean.TRUE.equals(costs.ok())) {
                return new OpsDtos.PnlResponse(
                        false,
                        revenueDto,
                        null,
                        null,
                        null,
                        "Unable to load AWS costs",
                        costs.error());
            }
            awsCost = costs.mtdUsd() != null ? costs.mtdUsd() : 0.0;
            awsSource = "cost_explorer";
        }

        double contribution = revenue - awsCost;
        return new OpsDtos.PnlResponse(
                true,
                revenueDto,
                round4(awsCost),
                awsSource,
                round4(contribution),
                "Units are as supplied by the caller for revenue; AWS costs are USD.",
                null);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
