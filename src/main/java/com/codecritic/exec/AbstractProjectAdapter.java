package com.codecritic.exec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Base implementation shared by the Maven and Gradle project adapters.
 *
 * <p>This class builds the build-tool command, executes it through a
 * {@link CommandRunner}, parses any produced test result XML, and classifies the
 * combined evidence into a {@link TestSuiteResult}. Subclasses only describe the
 * command lines and result locations for their build tool.
 */
public abstract class AbstractProjectAdapter implements ProjectAdapter {

    private static final int OUTPUT_TAIL_CHARS = 6000;

    private final CommandRunner commandRunner;
    private final TestResultXmlParser resultParser;

    /**
     * Creates an adapter that uses the supplied command runner and result parser.
     *
     * @param commandRunner runner used to execute build-tool commands
     * @param resultParser parser used to read test result XML files
     */
    protected AbstractProjectAdapter(CommandRunner commandRunner, TestResultXmlParser resultParser) {
        this.commandRunner = commandRunner;
        this.resultParser = resultParser;
    }

    /**
     * Returns the command that runs the complete test suite.
     *
     * @param worktree worktree in which the build will run
     * @return the command and its arguments
     */
    protected abstract List<String> testCommand(Path worktree);

    /**
     * Returns the command that runs only the selected test.
     *
     * @param worktree worktree in which the build will run
     * @param testSelector build-tool selector identifying the test to run
     * @return the command and its arguments
     */
    protected abstract List<String> targetedTestCommand(Path worktree, String testSelector);

    /**
     * Returns the command that runs tests for the given modules.
     *
     * @param worktree worktree in which the build will run
     * @param modulePaths module paths relative to the project root
     * @return the command and its arguments
     */
    protected abstract List<String> moduleTestCommand(Path worktree, List<String> modulePaths);

    /**
     * Returns the directories in which test result XML files may be found.
     *
     * @param worktree worktree that was built
     * @return candidate result root directories
     */
    protected abstract List<Path> testResultRoots(Path worktree);

    /**
     * Searches the worktree for result directories with the given names whose
     * parent directory matches {@code outputDirectory}.
     *
     * <p>Discovery is best-effort: filesystem errors are ignored and simply
     * yield fewer directories.
     *
     * @param worktree root of the worktree to search
     * @param outputDirectory name of the expected parent output directory, such
     *                        as {@code target} or {@code build}
     * @param reportDirectoryNames names of the report directories to collect
     * @return matching directories sorted by path, never {@code null}
     */
    protected List<Path> findResultDirectories(
            Path worktree,
            String outputDirectory,
            String... reportDirectoryNames) {
        Set<String> names = Set.of(reportDirectoryNames);
        List<Path> roots = new ArrayList<>();
        try (var paths = Files.walk(worktree)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> {
                        Path parent = path.getParent();
                        return parent != null
                                && outputDirectory.equals(parent.getFileName().toString())
                                && names.contains(path.getFileName().toString());
                    })
                    .forEach(roots::add);
        } catch (IOException | UncheckedIOException ignored) {
            // Discovery is best-effort; missing reports are handled as absent evidence.
        }
        roots.sort(Comparator.comparing(Path::toString));
        return List.copyOf(roots);
    }

    /**
     * Runs the full test suite for a revision.
     *
     * @param worktree worktree checked out at the revision
     * @param revision revision label recorded in the result
     * @param options execution limits
     * @return the classified test-suite result
     */
    @Override
    public TestSuiteResult execute(Path worktree, String revision, ExecutionOptions options) {
        return execute(worktree, revision, options, null);
    }

    /**
     * Runs either the full suite or a single selected test.
     *
     * <p>A {@code null} or blank selector runs the full suite.
     *
     * @param worktree worktree checked out at the revision
     * @param revision revision label recorded in the result
     * @param options execution limits
     * @param testSelector build-tool selector for one test, or {@code null} for all tests
     * @return the classified test-suite result
     */
    @Override
    public TestSuiteResult execute(
            Path worktree,
            String revision,
            ExecutionOptions options,
            String testSelector) {
        List<String> command = testSelector == null || testSelector.isBlank()
                ? testCommand(worktree)
                : targetedTestCommand(worktree, testSelector);
        return executeCommand(worktree, revision, options, command);
    }

    /**
     * Runs tests limited to the given modules, falling back to the full suite
     * when no narrowing is possible or the root module is selected.
     *
     * @param worktree worktree checked out at the revision
     * @param revision revision label recorded in the result
     * @param options execution limits
     * @param modulePaths module paths relative to the project root
     * @return the classified test-suite result
     */
    @Override
    public TestSuiteResult executeModules(
            Path worktree,
            String revision,
            ExecutionOptions options,
            List<String> modulePaths) {
        if (modulePaths == null || modulePaths.isEmpty() || modulePaths.contains(".")) {
            return execute(worktree, revision, options);
        }
        return executeCommand(worktree, revision, options, moduleTestCommand(worktree, modulePaths));
    }

    private TestSuiteResult executeCommand(
            Path worktree,
            String revision,
            ExecutionOptions options,
            List<String> command) {
        String displayCommand = displayCommand(command);

        ProcessResult process;
        try {
            process = commandRunner.run(worktree, command, options.timeout(), options.maxOutputBytes());
        } catch (RuntimeException e) {
            String message = "Test process did not start: " + e.getMessage();
            return TestSuiteResult.builder(revision, name(), displayCommand, ExecutionStatus.ERROR)
                    .output(message, false)
                    .warnings(List.of(message))
                    .build();
        }

        ParsedTestResults parsed;
        try {
            parsed = resultParser.parse(testResultRoots(worktree));
        } catch (RuntimeException e) {
            parsed = new ParsedTestResults(
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of("Unable to parse test results: " + e.getMessage()));
        }
        ExecutionStatus status = TestRunClassifier.classify(process, parsed);
        String combinedOutput = combineOutput(process);
        boolean reportTruncated = combinedOutput.length() > OUTPUT_TAIL_CHARS;

        List<String> warnings = new ArrayList<>(parsed.warnings());
        if (process.stdoutTruncated() || process.stderrTruncated()) {
            warnings.add("Test process output exceeded the capture limit and was truncated.");
        }
        if (reportTruncated) {
            warnings.add("Report shows only the last " + OUTPUT_TAIL_CHARS + " characters of test output.");
        }
        if (process.timedOut()) {
            warnings.add("Test process timed out after " + options.timeout().toSeconds() + " seconds.");
        }
        if (parsed.suiteFiles() == 0
                && (status == ExecutionStatus.TEST_FAILURES || status == ExecutionStatus.BUILD_FAILURE)) {
            warnings.add("No test result XML was found; status was classified from build output.");
        }
        if (parsed.testsRun() == 0 && status == ExecutionStatus.SUCCESS) {
            warnings.add("No tests were observed; the run produced no test evidence.");
        }

        return TestSuiteResult.builder(revision, name(), displayCommand, status)
                .counts(parsed.testsRun(), parsed.failures(), parsed.errors(), parsed.skipped())
                .durationMillis(process.duration().toMillis())
                .failedTests(parsed.failedTests())
                .output(
                        tail(combinedOutput),
                        process.stdoutTruncated() || process.stderrTruncated() || reportTruncated)
                .warnings(warnings)
                .build();
    }

    private static String displayCommand(List<String> command) {
        if (command.size() >= 5
                && "cmd.exe".equalsIgnoreCase(command.get(0))
                && "/c".equalsIgnoreCase(command.get(3))) {
            return String.join(" ", command.subList(4, command.size()));
        }
        return String.join(" ", command);
    }

    private static String combineOutput(ProcessResult process) {
        if (process.stderr().isBlank()) {
            return process.stdout();
        }
        if (process.stdout().isBlank()) {
            return process.stderr();
        }
        return process.stdout() + "\n" + process.stderr();
    }

    private static String tail(String output) {
        if (output.length() <= OUTPUT_TAIL_CHARS) {
            return output;
        }
        return "... (earlier output omitted) ...\n"
                + output.substring(output.length() - OUTPUT_TAIL_CHARS);
    }
}
