package com.codecritic.exec;

/**
 * Overall outcome of a test-suite execution.
 */
public enum ExecutionStatus {
    /**
     * The build completed and all executed tests passed.
     */
    SUCCESS,
    /**
     * The build completed but at least one test failed or errored.
     */
    TEST_FAILURES,
    /**
     * The build failed while compiling production or test sources.
     */
    COMPILE_FAILURE,
    /**
     * The build failed for a reason other than test failures or compilation errors.
     */
    BUILD_FAILURE,
    /**
     * The test process exceeded the configured timeout.
     */
    TIMEOUT,
    /**
     * No supported Maven or Gradle project could be detected.
     */
    UNSUPPORTED,
    /**
     * The test process could not be started or an internal execution error occurred.
     */
    ERROR
}
