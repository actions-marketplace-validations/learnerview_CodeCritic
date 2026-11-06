package com.codecritic.impact;

import java.util.List;

/**
 * Aggregated impact assessment for a change set.
 *
 * @param impacts per-callable impacts, ordered from highest to lowest risk
 * @param riskScore highest per-callable risk score, capped at {@code 100}
 * @param riskBand risk band corresponding to {@code riskScore}
 * @param summary human-readable summary lines describing the change set
 */
public record ImpactReport(
        List<MethodImpact> impacts,
        int riskScore,
        RiskBand riskBand,
        List<String> summary
) {
    /**
     * Creates an immutable impact report.
     */
    public ImpactReport {
        impacts = List.copyOf(impacts);
        summary = List.copyOf(summary);
    }
}
