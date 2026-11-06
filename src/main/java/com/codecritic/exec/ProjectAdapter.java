package com.codecritic.exec;

import java.nio.file.Path;
import java.util.List;

/**
 * Executes a project's tests through a specific build tool.
 */
public interface ProjectAdapter {

    /**
     * Returns the adapter name recorded in test-suite results.
     *
     * @return a short build-tool name, such as {@code maven} or {@code gradle}
     */
    String name();

    /**
     * Runs the complete test suite for a revision.
     *
     * @param worktree worktree checked out at the revision
     * @param revision revision label recorded in the result
     * @param options execution limits
     * @return the classified test-suite result
     */
    TestSuiteResult execute(Path worktree, String revision, ExecutionOptions options);

    /**
     * Runs either the full suite or a single selected test.
     *
     * @param worktree worktree checked out at the revision
     * @param revision revision label recorded in the result
     * @param options execution limits
     * @param testSelector build-tool selector for one test, or {@code null} for all tests
     * @return the classified test-suite result
     */
    TestSuiteResult execute(
            Path worktree,
            String revision,
            ExecutionOptions options,
            String testSelector);

    /**
     * Runs tests limited to the given modules.
     *
     * <p>The default implementation ignores the module list and runs the full
     * suite.
     *
     * @param worktree worktree checked out at the revision
     * @param revision revision label recorded in the result
     * @param options execution limits
     * @param modulePaths module paths relative to the project root
     * @return the classified test-suite result
     */
    default TestSuiteResult executeModules(
            Path worktree,
            String revision,
            ExecutionOptions options,
            List<String> modulePaths) {
        return execute(worktree, revision, options);
    }
}
