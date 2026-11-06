package com.codecritic.exec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Project adapter that executes Maven builds via the Maven wrapper or a system
 * Maven installation.
 */
public final class MavenProjectAdapter extends AbstractProjectAdapter {

    private static final List<String> TEST_ARGUMENTS =
            List.of("-B", "-ntp", "-Dstyle.color=never", "test");

    /**
     * Creates a Maven adapter.
     *
     * @param commandRunner runner used to execute Maven commands
     * @param resultParser parser used to read Surefire and Failsafe result XML
     */
    public MavenProjectAdapter(CommandRunner commandRunner, TestResultXmlParser resultParser) {
        super(commandRunner, resultParser);
    }

    /**
     * Returns the adapter name recorded in suite results.
     *
     * @return the string {@code maven}
     */
    @Override
    public String name() {
        return "maven";
    }

    /**
     * Builds the command that runs all Maven tests.
     *
     * @param worktree worktree in which the build will run
     * @return the Maven test command
     */
    @Override
    protected List<String> testCommand(Path worktree) {
        return ExecutableCommand.withWrapper(worktree, "mvn", "mvnw", TEST_ARGUMENTS);
    }

    /**
     * Builds the command that runs a single selected Maven test.
     *
     * @param worktree worktree in which the build will run
     * @param testSelector value supplied to Maven's {@code -Dtest} option
     * @return the targeted Maven test command
     */
    @Override
    protected List<String> targetedTestCommand(Path worktree, String testSelector) {
        return ExecutableCommand.withWrapper(
                worktree,
                "mvn",
                "mvnw",
                List.of("-B", "-ntp", "-Dstyle.color=never", "-Dtest=" + testSelector, "test"));
    }

    /**
     * Builds a command that runs tests for the given Maven modules and their
     * required upstream modules.
     *
     * @param worktree worktree in which the build will run
     * @param modulePaths module paths relative to the project root
     * @return the module-scoped Maven test command
     */
    @Override
    protected List<String> moduleTestCommand(Path worktree, List<String> modulePaths) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-B");
        arguments.add("-ntp");
        arguments.add("-Dstyle.color=never");
        arguments.add("-pl");
        arguments.add(String.join(",", modulePaths));
        arguments.add("-am");
        arguments.add("test");
        return ExecutableCommand.withWrapper(worktree, "mvn", "mvnw", arguments);
    }

    /**
     * Returns the Maven Surefire and Failsafe report directories under each
     * module's {@code target} directory.
     *
     * @param worktree worktree that was built
     * @return candidate test result root directories
     */
    @Override
    protected List<Path> testResultRoots(Path worktree) {
        return findResultDirectories(worktree, "target", "surefire-reports", "failsafe-reports");
    }
}
