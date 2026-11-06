package com.codecritic.dto;

/**
 * A single potential defect discovered while analysing Java source code.
 *
 * @param type short category or rule name of the finding
 * @param line one-based line number the finding refers to, or a non-positive value when
 *             the location is unknown
 * @param message description of the detected problem
 * @param suggestion recommended remediation for the problem
 */
public record BugFinding(String type, int line, String message, String suggestion) { }
