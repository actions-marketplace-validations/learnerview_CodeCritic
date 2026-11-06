package com.codecritic.exec;

import java.util.List;

/**
 * Result of executing tests at a base revision and a head revision.
 *
 * <p>Either both suite results are present, or {@code setupError} explains why
 * the execution could not be prepared.
 *
 * @param base test-suite result for the base revision, or {@code null} on setup failure
 * @param head test-suite result for the head revision, or {@code null} on setup failure
 * @param setupError reason the base and head runs could not be prepared, or {@code null}
 * @param warnings non-fatal notices collected during setup and execution
 * @param differential differential result for an injected candidate test, or {@code null}
 * @param selection module selection used for the runs, or {@code null} when not applicable
 * @param failureComparison comparison of base and head failures, or {@code null}
 */
public record BaselineExecution(
        TestSuiteResult base,
        TestSuiteResult head,
        String setupError,
        List<String> warnings,
        DifferentialExecution differential,
        TestSelection selection,
        FailureComparison failureComparison
) {
    /**
     * Creates an immutable copy of the warning list.
     */
    public BaselineExecution {
        warnings = List.copyOf(warnings);
    }

    /**
     * Creates a result without differential, selection, or failure-comparison details.
     *
     * @param base test-suite result for the base revision, or {@code null}
     * @param head test-suite result for the head revision, or {@code null}
     * @param setupError reason setup failed, or {@code null}
     * @param warnings non-fatal notices to record
     */
    public BaselineExecution(
            TestSuiteResult base,
            TestSuiteResult head,
            String setupError,
            List<String> warnings) {
        this(base, head, setupError, warnings, null, null, null);
    }

    /**
     * Creates a successful result without selection or failure-comparison details.
     *
     * @param base test-suite result for the base revision
     * @param head test-suite result for the head revision
     * @param differential differential result for an injected candidate, or {@code null}
     * @return a completed execution with no warnings
     */
    public static BaselineExecution completed(
            TestSuiteResult base,
            TestSuiteResult head,
            DifferentialExecution differential) {
        return completed(base, head, differential, null, null);
    }

    /**
     * Creates a successful result with all optional details.
     *
     * @param base test-suite result for the base revision
     * @param head test-suite result for the head revision
     * @param differential differential result for an injected candidate, or {@code null}
     * @param selection module selection used for the runs, or {@code null}
     * @param failureComparison comparison of base and head failures, or {@code null}
     * @return a completed execution with no warnings
     */
    public static BaselineExecution completed(
            TestSuiteResult base,
            TestSuiteResult head,
            DifferentialExecution differential,
            TestSelection selection,
            FailureComparison failureComparison) {
        return new BaselineExecution(
                base,
                head,
                null,
                List.of(),
                differential,
                selection,
                failureComparison);
    }

    /**
     * Creates a result representing a failure to prepare the worktrees or runs.
     *
     * @param error description of the setup failure
     * @return a failed execution with no suite results
     */
    public static BaselineExecution setupFailed(String error) {
        return new BaselineExecution(null, null, error, List.of(), null, null, null);
    }

    /**
     * Returns a copy of this result with a replacement warning list.
     *
     * @param combinedWarnings warnings to store in the copy
     * @return a new execution carrying the same results and the given warnings
     */
    public BaselineExecution withWarnings(List<String> combinedWarnings) {
        return new BaselineExecution(
                base,
                head,
                setupError,
                combinedWarnings,
                differential,
                selection,
                failureComparison);
    }
}
