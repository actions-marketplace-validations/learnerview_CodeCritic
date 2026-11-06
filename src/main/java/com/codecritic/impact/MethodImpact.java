package com.codecritic.impact;

import com.codecritic.change.MethodChange;

import java.util.List;

/**
 * Risk assessment for one changed Java callable.
 *
 * @param change the detected callable change
 * @param productionCallerCount heuristic count of production callers
 * @param affectedTests tests that reference the changed class or method
 * @param riskScore computed risk score, capped at {@code 100}
 * @param riskBand risk band corresponding to {@code riskScore}
 * @param riskFactors human-readable factors that contributed to the score
 */
public record MethodImpact(
        MethodChange change,
        int productionCallerCount,
        List<String> affectedTests,
        int riskScore,
        RiskBand riskBand,
        List<String> riskFactors
) {
    /**
     * Creates an immutable method impact with copied test and factor lists.
     */
    public MethodImpact {
        affectedTests = List.copyOf(affectedTests);
        riskFactors = List.copyOf(riskFactors);
    }
}
