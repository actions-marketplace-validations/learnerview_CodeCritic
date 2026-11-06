package com.codecritic.impact;

/**
 * Coarse risk classification derived from an impact score.
 */
public enum RiskBand {
    /** Low risk: score below {@code 30}. */
    LOW,
    /** Medium risk: score from {@code 30} through {@code 59}. */
    MEDIUM,
    /** High risk: score of {@code 60} or more. */
    HIGH
}
