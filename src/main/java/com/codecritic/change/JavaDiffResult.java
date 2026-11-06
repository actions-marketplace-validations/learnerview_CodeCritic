package com.codecritic.change;

import java.util.List;

/**
 * Result of comparing the callables of two Java source versions.
 *
 * @param changes the detected method and constructor changes
 * @param warnings human-readable notes about sources that could not be parsed
 */
public record JavaDiffResult(
        List<MethodChange> changes,
        List<String> warnings
) {
    /**
     * Creates a diff result and defensively copies both lists so the instance is immutable.
     */
    public JavaDiffResult {
        changes = List.copyOf(changes);
        warnings = List.copyOf(warnings);
    }
}
