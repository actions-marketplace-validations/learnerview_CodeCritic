package com.codecritic.cli;

import com.codecritic.change.GitException;
import com.codecritic.exec.BaselineExecution;
import com.codecritic.exec.DifferentialExecution;
import com.codecritic.exec.ExecutionOptions;
import com.codecritic.exec.TestCandidate;
import com.codecritic.exec.TestCandidateLoader;
import com.codecritic.exec.TestSelectionMode;
import com.codecritic.exec.TestSuiteResult;
import com.codecritic.report.GateDecision;
import com.codecritic.report.GateEvaluator;
import com.codecritic.report.GatePolicy;
import com.codecritic.report.HtmlReportRenderer;
import com.codecritic.report.JsonReportRenderer;
import com.codecritic.report.ReportFormat;
import com.codecritic.report.VerificationReport;
import com.codecritic.report.VerificationService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Picocli command that maps the callable-level impact of a Java change between
 * two git references and can optionally run both revisions' test suites.
 * <p>
 * The command writes an HTML or JSON report and returns a process exit code that
 * reflects argument errors, report-writing failures, or gate results.
 */
@Command(
        name = "codecritic",
        mixinStandardHelpOptions = true,
        version = "CodeCritic 0.1.0",
        description = "Map the callable-level impact of a Java change between two git refs, optionally running both revisions' test suites in Docker.")
public final class CodeCriticCommand implements Callable<Integer> {

    @Option(names = "--repo", defaultValue = ".", description = "Path to a git repository.")
    private Path repository;

    @Option(names = "--base", required = true, description = "Base git ref, for example main or HEAD~1.")
    private String base;

    @Option(names = "--head", defaultValue = "HEAD", description = "Head git ref. Defaults to HEAD.")
    private String head;

    @Option(names = "--out", description = "Output report path. Defaults to codecritic-report.<format>.")
    private Path output;

    @Option(
            names = "--format",
            defaultValue = "html",
            description = "Report format: ${COMPLETION-CANDIDATES}.")
    private ReportFormat format;

    @Option(
            names = "--run-tests",
            description = "Run each revision's existing test suite in isolated git worktrees inside Docker.")
    private boolean runTests;

    @Option(
            names = "--select",
            defaultValue = "modules",
            description = "With --run-tests: run only changed Maven/Gradle modules, or all modules. Values: modules, all.")
    private String select;

    @Option(
            names = "--fail-on",
            defaultValue = "new-failure",
            description = "With --run-tests: gate on new-failure, any-failure, high-risk, or never.")
    private String failOn;

    @Option(
            names = "--test-source",
            description = "Java test source to inject into both revisions before running a differential test.")
    private Path testSource;

    @Option(
            names = "--test-path",
            description = "Project-relative path for the injected test. Defaults to src/test/java/<package>/<Class>.java.")
    private String testPath;

    @Option(
            names = "--stability-check",
            negatable = true,
            defaultValue = "true",
            description = "Repeat F2P/P2F candidates and reject unstable results. Use --no-stability-check to skip.")
    private boolean stabilityCheck;

    @Option(
            names = "--test-timeout",
            defaultValue = "600",
            description = "Test process timeout in seconds. Default: ${DEFAULT-VALUE}.")
    private long testTimeoutSeconds;

    @Option(
            names = "--max-output-bytes",
            defaultValue = "200000",
            description = "Maximum captured bytes per test process stream. Default: ${DEFAULT-VALUE}.")
    private int maxOutputBytes;

    private final VerificationService service;
    private final HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
    private final JsonReportRenderer jsonRenderer = new JsonReportRenderer();

    /**
     * Creates the command with the default verification service.
     */
    public CodeCriticCommand() {
        this(VerificationService.createDefault());
    }

    CodeCriticCommand(VerificationService service) {
        this.service = service;
    }

    /**
     * Executes the verification workflow described by the parsed options.
     *
     * @return {@code 0} on success, {@code 1} when the configured gate fails, or
     *         {@code 2} for argument, git, or report-writing errors
     */
    @Override
    public Integer call() {
        try {
            TestCandidate candidate = candidate();
            ExecutionOptions executionOptions = executionOptions(candidate != null);
            TestSelectionMode selectionMode = selectionMode();
            GatePolicy gatePolicy = gatePolicy();
            VerificationReport report = service.verify(
                    repository,
                    base,
                    head,
                    executionOptions,
                    candidate,
                    stabilityCheck,
                    List.of(),
                    selectionMode);
            Path target = output != null
                    ? output
                    : Path.of("codecritic-report." + format.name());
            Path absolute = target.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String content = format == ReportFormat.html
                    ? htmlRenderer.render(report)
                    : jsonRenderer.render(report);
            Files.writeString(absolute, content, StandardCharsets.UTF_8);

            System.out.println("CodeCritic change impact");
            System.out.println("Repository: " + report.repository());
            System.out.println("Range:      " + report.baseRef() + " -> " + report.headRef());
            System.out.println("Risk:       " + report.riskBand() + " (" + report.riskScore() + "/100)");
            System.out.println("Changes:    " + report.methodImpacts().size() + " callable(s)");
            if (report.baseline() != null) {
                System.out.println("Baseline:   " + describe(report.baseline()));
                DifferentialExecution differential = report.baseline().differential();
                if (differential != null) {
                    System.out.println("Difference: " + differential.verdict()
                            + " (" + differential.testClass() + ")");
                }
            }
            if (report.baseline() != null && report.baseline().selection() != null) {
                String scope = report.baseline().selection().isAll()
                        ? "all modules"
                        : String.join(", ", report.baseline().selection().modules());
                System.out.println("Selection:  " + scope);
            }
            System.out.println("Report:     " + absolute);

            if (runTests && candidate == null) {
                GateDecision decision = GateEvaluator.evaluate(report, gatePolicy);
                System.out.println("Gate:       " + (decision.failed() ? "FAIL" : "PASS")
                        + " (" + decision.reason() + ")");
                if (decision.failed()) {
                    return 1;
                }
            }
            return 0;
        } catch (GitException | IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("error: unable to write report: " + e.getMessage());
            return 2;
        }
    }

    private TestSelectionMode selectionMode() {
        return switch (select == null ? "modules" : select.trim().toLowerCase(Locale.ROOT)) {
            case "modules" -> TestSelectionMode.MODULES;
            case "all" -> TestSelectionMode.ALL;
            default -> throw new IllegalArgumentException("--select must be 'modules' or 'all'");
        };
    }

    private GatePolicy gatePolicy() {
        return switch (failOn == null ? "new-failure" : failOn.trim().toLowerCase(Locale.ROOT)) {
            case "never" -> GatePolicy.NEVER;
            case "new-failure" -> GatePolicy.NEW_FAILURE;
            case "any-failure" -> GatePolicy.ANY_FAILURE;
            case "high-risk" -> GatePolicy.HIGH_RISK;
            default -> throw new IllegalArgumentException(
                    "--fail-on must be 'never', 'new-failure', 'any-failure', or 'high-risk'");
        };
    }

    private TestCandidate candidate() {
        if (testSource == null) {
            if (testPath != null) {
                throw new IllegalArgumentException("--test-path requires --test-source");
            }
            return null;
        }
        return TestCandidateLoader.load(testSource, repository, testPath);
    }

    private ExecutionOptions executionOptions(boolean testsRequested) {
        if (!testsRequested && !runTests) {
            return null;
        }
        if (testTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("--test-timeout must be positive");
        }
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("--max-output-bytes must be positive");
        }
        return new ExecutionOptions(Duration.ofSeconds(testTimeoutSeconds), maxOutputBytes);
    }

    private static String describe(BaselineExecution baseline) {
        if (baseline.setupError() != null) {
            return "setup failed: " + baseline.setupError();
        }
        return "base " + describe(baseline.base()) + ", head " + describe(baseline.head());
    }

    private static String describe(TestSuiteResult result) {
        if (result == null) {
            return "not run";
        }
        return result.status() + " (" + result.testsRun() + " tests)";
    }
}
