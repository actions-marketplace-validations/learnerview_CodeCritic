package com.codecritic.exec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Project adapter that executes Gradle builds via the Gradle wrapper or a
 * system Gradle installation.
 */
public final class GradleProjectAdapter extends AbstractProjectAdapter {

    private static final List<String> TEST_ARGUMENTS =
            List.of("--no-daemon", "--console=plain", "test");

    /**
     * Creates a Gradle adapter.
     *
     * @param commandRunner runner used to execute Gradle commands
     * @param resultParser parser used to read Gradle test result XML
     */
    public GradleProjectAdapter(CommandRunner commandRunner, TestResultXmlParser resultParser) {
        super(commandRunner, resultParser);
    }

    /**
     * Returns the adapter name recorded in suite results.
     *
     * @return the string {@code gradle}
     */
    @Override
    public String name() {
        return "gradle";
    }

    /**
     * Builds the command that runs all Gradle tests.
     *
     * @param worktree worktree in which the build will run
     * @return the Gradle test command
     */
    @Override
    protected List<String> testCommand(Path worktree) {
        return ExecutableCommand.withWrapper(worktree, "gradle", "gradlew", TEST_ARGUMENTS);
    }

    /**
     * Builds the command that runs a single selected Gradle test.
     *
     * @param worktree worktree in which the build will run
     * @param testSelector fully qualified test class or method selector
     * @return the targeted Gradle test command
     */
    @Override
    protected List<String> targetedTestCommand(Path worktree, String testSelector) {
        return ExecutableCommand.withWrapper(
                worktree,
                "gradle",
                "gradlew",
                List.of("--no-daemon", "--console=plain", "test", "--tests", testSelector));
    }

    /**
     * Builds a command that runs tests for the given Gradle projects.
     *
     * @param worktree worktree in which the build will run
     * @param modulePaths module paths relative to the project root
     * @return the module-scoped Gradle test command
     */
    @Override
    protected List<String> moduleTestCommand(Path worktree, List<String> modulePaths) {
        List<String> arguments = new ArrayList<>();
        arguments.add("--no-daemon");
        arguments.add("--console=plain");
        for (String modulePath : modulePaths) {
            arguments.add(":" + modulePath.replace('/', ':') + ":test");
        }
        return ExecutableCommand.withWrapper(worktree, "gradle", "gradlew", arguments);
    }

    /**
     * Returns the Gradle test result directories under each module's
     * {@code build/test-results} directory.
     *
     * @param worktree worktree that was built
     * @return candidate test result root directories
     */
    @Override
    protected List<Path> testResultRoots(Path worktree) {
        return findResultDirectories(worktree, "build", "test-results");
    }
}
