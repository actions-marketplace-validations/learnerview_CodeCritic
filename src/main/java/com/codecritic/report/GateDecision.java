package com.codecritic.report;

/**
 * Outcome of applying a gate policy to a verification report.
 *
 * @param failed whether the gate failed
 * @param reason a human-readable explanation for the decision
 */
public record GateDecision(boolean failed, String reason) {
    /**
     * Creates a passing decision.
     *
     * @param reason the explanation for the decision
     * @return a decision with {@code failed} set to {@code false}
     */
    public static GateDecision pass(String reason) {
        return new GateDecision(false, reason);
    }

    /**
     * Creates a failing decision.
     *
     * @param reason the explanation for the decision
     * @return a decision with {@code failed} set to {@code true}
     */
    public static GateDecision fail(String reason) {
        return new GateDecision(true, reason);
    }
}
