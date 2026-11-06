package com.codecritic.exec;

/**
 * Outcome of comparing a candidate test's results between a base and head revision.
 */
public enum DifferentialVerdict {
    /**
     * The test failed at the base revision and passes at the head revision.
     */
    F2P,
    /**
     * The test passed at the base revision and fails at the head revision.
     */
    P2F,
    /**
     * The test passes at both revisions.
     */
    P2P,
    /**
     * The test fails at both revisions.
     */
    F2F,
    /**
     * A repeated execution produced a different verdict, so the result is unstable.
     */
    FLAKY,
    /**
     * The revision statuses do not map to a definitive differential verdict.
     */
    INCONCLUSIVE
}
