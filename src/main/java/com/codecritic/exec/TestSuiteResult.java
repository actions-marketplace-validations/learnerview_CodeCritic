package com.codecritic.exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable summary of one test-suite execution for a single revision.
 *
 * @param revision revision that was tested
 * @param adapter name of the build adapter that ran the suite
 * @param command display form of the executed command
 * @param status overall execution outcome
 * @param testsRun number of test cases observed
 * @param failures number of test failures
 * @param errors number of test errors
 * @param skipped number of skipped tests
 * @param durationMillis execution duration in milliseconds
 * @param failedTests failed and errored tests collected from the run
 * @param outputTail trailing portion of the captured test output
 * @param outputTruncated whether the stored output was truncated
 * @param warnings non-fatal notices produced during the run
 */
public record TestSuiteResult(
        String revision,
        String adapter,
        String command,
        ExecutionStatus status,
        int testsRun,
        int failures,
        int errors,
        int skipped,
        long durationMillis,
        List<TestFailure> failedTests,
        String outputTail,
        boolean outputTruncated,
        List<String> warnings
) {
    /**
     * Creates immutable copies of the collection components and normalizes null output.
     */
    public TestSuiteResult {
        failedTests = List.copyOf(failedTests);
        warnings = List.copyOf(warnings);
        outputTail = outputTail == null ? "" : outputTail;
    }

    /**
     * Returns whether the run recorded any test failures or errors.
     *
     * @return {@code true} if {@code failures} or {@code errors} is greater than zero
     */
    public boolean hasTestFailures() {
        return failures > 0 || errors > 0;
    }

    /**
     * Creates a builder initialized with the required result fields.
     *
     * @param revision revision that was tested
     * @param adapter name of the build adapter
     * @param command display form of the executed command
     * @param status overall execution outcome
     * @return a new builder
     */
    public static Builder builder(
            String revision,
            String adapter,
            String command,
            ExecutionStatus status) {
        return new Builder(revision, adapter, command, status);
    }

    /**
     * Accumulates optional result details before producing a {@link TestSuiteResult}.
     */
    public static final class Builder {
        private final String revision;
        private final String adapter;
        private final String command;
        private final ExecutionStatus status;
        private int testsRun;
        private int failures;
        private int errors;
        private int skipped;
        private long durationMillis;
        private List<TestFailure> failedTests = List.of();
        private String outputTail = "";
        private boolean outputTruncated;
        private List<String> warnings = List.of();

        private Builder(
                String revision,
                String adapter,
                String command,
                ExecutionStatus status) {
            this.revision = revision;
            this.adapter = adapter;
            this.command = command;
            this.status = status;
        }

        /**
         * Sets the test counts.
         *
         * @param testsRun number of test cases observed
         * @param failures number of test failures
         * @param errors number of test errors
         * @param skipped number of skipped tests
         * @return this builder
         */
        public Builder counts(int testsRun, int failures, int errors, int skipped) {
            this.testsRun = testsRun;
            this.failures = failures;
            this.errors = errors;
            this.skipped = skipped;
            return this;
        }

        /**
         * Sets the execution duration.
         *
         * @param durationMillis duration in milliseconds
         * @return this builder
         */
        public Builder durationMillis(long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        /**
         * Sets the failed tests, replacing any previously supplied list.
         *
         * @param failedTests failed tests, or {@code null} for an empty list
         * @return this builder
         */
        public Builder failedTests(List<TestFailure> failedTests) {
            this.failedTests = failedTests == null ? List.of() : new ArrayList<>(failedTests);
            return this;
        }

        /**
         * Sets the stored output tail and whether it was truncated.
         *
         * @param outputTail trailing output text
         * @param outputTruncated whether the output was truncated
         * @return this builder
         */
        public Builder output(String outputTail, boolean outputTruncated) {
            this.outputTail = outputTail;
            this.outputTruncated = outputTruncated;
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
         * Builds the test suite result from the accumulated values.
         *
         * @return a new immutable result
         */
        public TestSuiteResult build() {
            return new TestSuiteResult(
                    revision,
                    adapter,
                    command,
                    status,
                    testsRun,
                    failures,
                    errors,
                    skipped,
                    durationMillis,
                    failedTests,
                    outputTail,
                    outputTruncated,
                    warnings);
        }
    }
}
