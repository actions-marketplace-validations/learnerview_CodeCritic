package com.codecritic.exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the differential result of a candidate test injected into two revisions.
 *
 * @param testPath path of the injected test relative to the project root
 * @param testClass fully qualified test class used as the selector
 * @param verdict differential outcome for the candidate test
 * @param stabilityChecked whether a repeat run was performed to check stability
 * @param warnings notices generated while evaluating the candidate
 * @param baseRepeat repeat base-revision result, or {@code null} if not run
 * @param headRepeat repeat head-revision result, or {@code null} if not run
 */
public record DifferentialExecution(
        String testPath,
        String testClass,
        DifferentialVerdict verdict,
        boolean stabilityChecked,
        List<String> warnings,
        TestSuiteResult baseRepeat,
        TestSuiteResult headRepeat
) {
    /**
     * Creates an immutable copy of the warning list.
     */
    public DifferentialExecution {
        warnings = List.copyOf(warnings);
    }

    /**
     * Creates a builder initialized with the candidate identity and verdict.
     *
     * @param testPath path of the injected test
     * @param testClass fully qualified test class selector
     * @param verdict differential outcome
     * @return a new builder
     */
    public static Builder builder(
            String testPath,
            String testClass,
            DifferentialVerdict verdict) {
        return new Builder(testPath, testClass, verdict);
    }

    /**
     * Accumulates optional differential details before producing a {@link DifferentialExecution}.
     */
    public static final class Builder {
        private final String testPath;
        private final String testClass;
        private final DifferentialVerdict verdict;
        private boolean stabilityChecked;
        private List<String> warnings = List.of();
        private TestSuiteResult baseRepeat;
        private TestSuiteResult headRepeat;

        private Builder(String testPath, String testClass, DifferentialVerdict verdict) {
            this.testPath = testPath;
            this.testClass = testClass;
            this.verdict = verdict;
        }

        /**
         * Sets the repeat-run results used to check candidate stability.
         *
         * @param stabilityChecked whether a stability check was performed
         * @param baseRepeat repeat base-revision result, or {@code null}
         * @param headRepeat repeat head-revision result, or {@code null}
         * @return this builder
         */
        public Builder stability(
                boolean stabilityChecked,
                TestSuiteResult baseRepeat,
                TestSuiteResult headRepeat) {
            this.stabilityChecked = stabilityChecked;
            this.baseRepeat = baseRepeat;
            this.headRepeat = headRepeat;
            return this;
        }

        /**
         * Sets the warnings, replacing any previously supplied list.
         *
         * @param warnings warnings to record, or {@code null} for an empty list
         * @return this builder
         */
        public Builder warnings(List<String> warnings) {
            this.warnings = warnings == null ? List.of() : new ArrayList<>(warnings);
            return this;
        }

        /**
         * Builds the differential execution record from the accumulated values.
         *
         * @return a new immutable differential execution
         */
        public DifferentialExecution build() {
            return new DifferentialExecution(
                    testPath,
                    testClass,
                    verdict,
                    stabilityChecked,
                    warnings,
                    baseRepeat,
                    headRepeat);
        }
    }
}
