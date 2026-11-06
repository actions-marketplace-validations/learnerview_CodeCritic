package com.codecritic.report;

/**
 * Policy controlling when a verification run should fail its exit gate.
 */
public enum GatePolicy {
    /** Never fail the gate, regardless of test or risk results. */
    NEVER,
    /** Fail only when a test fails at head but not at base. */
    NEW_FAILURE,
    /** Fail when the head revision has any test failure. */
    ANY_FAILURE,
    /** Fail when the aggregate risk band is {@code HIGH}. */
    HIGH_RISK
}
