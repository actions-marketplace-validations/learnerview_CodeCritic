package com.codecritic.report;

/**
 * One entry in the report's evidence ledger.
 *
 * @param id the stable evidence identifier, such as {@code E01}
 * @param type the evidence category, such as {@code METHOD_CHANGE} or {@code TESTS}
 * @param path the project-relative path the evidence refers to, or an empty string for execution-level evidence
 * @param line the one-based line number, or {@code 0} when not applicable
 * @param description a human-readable explanation of the evidence
 */
public record Evidence(
        String id,
        String type,
        String path,
        int line,
        String description
) {
}
