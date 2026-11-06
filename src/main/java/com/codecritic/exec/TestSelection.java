package com.codecritic.exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes which modules a test run should cover and why.
 *
 * @param mode selection strategy that produced this selection
 * @param modules module paths to test; empty when the full suite is selected
 * @param fallbackReason reason the full suite was chosen instead of a module subset,
 *                       or {@code null} when no fallback occurred
 * @param warnings non-fatal notices gathered while determining the selection
 */
public record TestSelection(
        TestSelectionMode mode,
        List<String> modules,
        String fallbackReason,
        List<String> warnings
) {
    /**
     * Creates immutable copies of the collection components.
     */
    public TestSelection {
        modules = modules == null ? List.of() : List.copyOf(modules);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Selects the full suite with no fallback reason or warnings.
     *
     * @return a selection that tests all modules
     */
    public static TestSelection all() {
        return new TestSelection(TestSelectionMode.ALL, List.of(), null, List.of());
    }

    /**
     * Selects the full suite and records why a narrower selection was not used.
     *
     * @param reason explanation for running the full suite
     * @param warnings warnings to carry with the selection
     * @return a selection that tests all modules
     */
    public static TestSelection all(String reason, List<String> warnings) {
        return new TestSelection(TestSelectionMode.ALL, List.of(), reason, warnings);
    }

    /**
     * Selects an explicit set of modules.
     *
     * <p>Module paths are de-duplicated and sorted for deterministic command lines.
     *
     * @param modules modules to test
     * @param warnings warnings to carry with the selection
     * @return a module-scoped selection
     */
    public static TestSelection modules(List<ProjectModule> modules, List<String> warnings) {
        List<String> paths = modules.stream()
                .map(ProjectModule::path)
                .distinct()
                .sorted()
                .toList();
        return new TestSelection(TestSelectionMode.MODULES, paths, null, warnings);
    }

    /**
     * Returns whether this selection covers the full suite.
     *
     * @return {@code true} if the mode is {@link TestSelectionMode#ALL} or no modules are listed
     */
    public boolean isAll() {
        return mode == TestSelectionMode.ALL || modules.isEmpty();
    }

    /**
     * Converts this selection to a full-suite selection while preserving warnings.
     *
     * @param reason explanation for falling back to the full suite
     * @param extraWarnings additional warnings to append, or {@code null}
     * @return a new full-suite selection
     */
    public TestSelection withFallback(String reason, List<String> extraWarnings) {
        List<String> combined = new ArrayList<>(warnings);
        if (extraWarnings != null) {
            combined.addAll(extraWarnings);
        }
        return new TestSelection(TestSelectionMode.ALL, List.of(), reason, combined);
    }
}
