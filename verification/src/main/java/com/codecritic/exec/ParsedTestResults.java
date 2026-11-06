package com.codecritic.exec;

import java.util.List;

/**
 * Aggregated counts and failures parsed from one or more test result XML files.
 *
 * @param suiteFiles number of result XML files that were read
 * @param testsRun total number of test cases observed
 * @param failures number of test cases reported as failures
 * @param errors number of test cases reported as errors
 * @param skipped number of test cases reported as skipped
 * @param failedTests failures and errors collected from the result files
 * @param warnings non-fatal problems encountered while parsing results
 */
public record ParsedTestResults(
        int suiteFiles,
        int testsRun,
        int failures,
        int errors,
        int skipped,
        List<TestFailure> failedTests,
        List<String> warnings
) {
    /**
     * Creates immutable copies of the collection components.
     */
    public ParsedTestResults {
        failedTests = List.copyOf(failedTests);
        warnings = List.copyOf(warnings);
    }

    /**
     * Returns an empty result set with zero counts and no failures or warnings.
     *
     * @return an empty parsed result
     */
    public static ParsedTestResults empty() {
        return new ParsedTestResults(0, 0, 0, 0, 0, List.of(), List.of());
    }
}
