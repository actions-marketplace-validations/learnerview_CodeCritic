package com.codecritic.exec;

import com.codecritic.change.GitException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates baseline test execution at a base revision and a head revision.
 *
 * <p>The runner creates temporary Git worktrees for both revisions, optionally
 * injects a candidate test, selects the modules to test, executes them through
 * the detected project adapter, and optionally compares the results. Setup
 * failures are returned as a {@link BaselineExecution} rather than propagated.
 */
public final class ProjectTestRunner {

    private final ProjectAdapterFactory adapterFactory;

    /**
     * Creates a runner with the default adapter factory.
     */
    public ProjectTestRunner() {
        this(new ProjectAdapterFactory());
    }

    /**
     * Creates a runner that uses the supplied adapter factory.
     *
     * @param adapterFactory factory used to detect and create project adapters
     */
    ProjectTestRunner(ProjectAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
    }

    /**
     * Runs the full test suite at both revisions without a candidate test or
     * module selection.
     *
     * @param repository repository that contains both revisions
     * @param baseRef revision used as the baseline
     * @param headRef revision used as the candidate change
     * @param options execution limits
     * @return the base and head results with warnings
     */
    public BaselineExecution run(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions options) {
        return run(repository, baseRef, headRef, options, null, false, List.of(), TestSelectionMode.ALL);
    }

    /**
     * Runs an optional candidate test at both revisions with the full suite.
     *
     * @param repository repository that contains both revisions
     * @param baseRef revision used as the baseline
     * @param headRef revision used as the candidate change
     * @param options execution limits
     * @param candidate test to inject and run, or {@code null} for the existing suite
     * @param stabilityCheck whether to repeat a candidate run to detect flakiness
     * @return the base and head results, differential verdict, and warnings
     */
    public BaselineExecution run(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions options,
            TestCandidate candidate,
            boolean stabilityCheck) {
        return run(
                repository,
                baseRef,
                headRef,
                options,
                candidate,
                stabilityCheck,
                List.of(),
                TestSelectionMode.ALL);
    }

    /**
     * Runs the existing tests at both revisions using the requested module
     * selection strategy.
     *
     * @param repository repository that contains both revisions
     * @param baseRef revision used as the baseline
     * @param headRef revision used as the candidate change
     * @param options execution limits
     * @param changedPaths repository-relative paths changed between the revisions
     * @param selectionMode strategy for choosing modules to test
     * @return the base and head results, selection, and warnings
     */
    public BaselineExecution run(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions options,
            List<String> changedPaths,
            TestSelectionMode selectionMode) {
        return run(
                repository,
                baseRef,
                headRef,
                options,
                null,
                false,
                changedPaths,
                selectionMode);
    }

    /**
     * Runs the complete A/B workflow with all optional features.
     *
     * <p>When a candidate is supplied, only that test is executed and module
     * selection is not used. When stability checking is enabled, a definitive
     * forward or reverse transition is repeated and replaced with
     * {@link DifferentialVerdict#FLAKY} if the verdict changes.
     *
     * @param repository repository that contains both revisions
     * @param baseRef revision used as the baseline
     * @param headRef revision used as the candidate change
     * @param options execution limits
     * @param candidate test to inject and run, or {@code null} for the existing suite
     * @param stabilityCheck whether to repeat a candidate run to detect flakiness
     * @param changedPaths repository-relative paths changed between the revisions
     * @param selectionMode strategy for choosing modules to test
     * @return the base and head results plus all collected differential details
     */
    public BaselineExecution run(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions options,
            TestCandidate candidate,
            boolean stabilityCheck,
            List<String> changedPaths,
            TestSelectionMode selectionMode) {
        List<String> warnings = new ArrayList<>();
        GitWorktreeManager worktrees = new GitWorktreeManager(repository);
        BaselineExecution execution;

        try {
            Path baseWorktree = worktrees.add(baseRef);
            Path headWorktree = worktrees.add(headRef);
            TestSelection selection = candidate != null
                    ? TestSelection.all("Candidate test only; module selection was not used.", List.of())
                    : selection(headWorktree, changedPaths, selectionMode);
            selection = ensureModulesExist(baseWorktree, headWorktree, selection);
            warnings.addAll(selection.warnings());
            if (selection.fallbackReason() != null) {
                warnings.add(selection.fallbackReason());
            }

            TestSuiteResult base = runOne(baseWorktree, baseRef, options, candidate, selection);
            TestSuiteResult head = runOne(headWorktree, headRef, options, candidate, selection);
            FailureComparison failureComparison = FailureComparison.between(base, head);

            DifferentialExecution differential = null;
            if (candidate != null) {
                DifferentialVerdict verdict = DifferentialTestClassifier.classify(base, head);
                boolean stabilityChecked = false;
                TestSuiteResult baseRepeat = null;
                TestSuiteResult headRepeat = null;
                List<String> differentialWarnings = new ArrayList<>();

                if (stabilityCheck
                        && (verdict == DifferentialVerdict.F2P
                        || verdict == DifferentialVerdict.P2F)) {
                    stabilityChecked = true;
                    baseRepeat = runOne(baseWorktree, baseRef, options, candidate, selection);
                    headRepeat = runOne(headWorktree, headRef, options, candidate, selection);
                    DifferentialVerdict repeat = DifferentialTestClassifier.classify(
                            baseRepeat,
                            headRepeat);
                    if (repeat != verdict) {
                        differentialWarnings.add(
                                "Candidate result was unstable: first " + verdict
                                        + ", repeat " + repeat + ".");
                        verdict = DifferentialVerdict.FLAKY;
                    }
                }

                if (verdict == DifferentialVerdict.INCONCLUSIVE) {
                    differentialWarnings.add(
                            "Differential test was inconclusive; inspect base and head statuses.");
                }
                warnings.addAll(differentialWarnings);
                differential = DifferentialExecution
                        .builder(candidate.relativePath(), candidate.className(), verdict)
                        .stability(stabilityChecked, baseRepeat, headRepeat)
                        .warnings(differentialWarnings)
                        .build();
            }

            execution = BaselineExecution.completed(
                    base,
                    head,
                    differential,
                    selection,
                    failureComparison);
        } catch (GitException e) {
            execution = BaselineExecution.setupFailed(e.getMessage());
        } finally {
            warnings.addAll(worktrees.closeAndCollect());
        }

        if (warnings.isEmpty()) {
            return execution;
        }
        List<String> combinedWarnings = new ArrayList<>(execution.warnings());
        combinedWarnings.addAll(warnings);
        return execution.withWarnings(combinedWarnings);
    }

    private static TestSelection selection(
            Path headWorktree,
            List<String> changedPaths,
            TestSelectionMode selectionMode) {
        if (selectionMode == null || selectionMode == TestSelectionMode.ALL) {
            return TestSelection.all();
        }
        return ModuleDiscovery.discover(headWorktree).select(changedPaths);
    }

    private static TestSelection ensureModulesExist(
            Path baseWorktree,
            Path headWorktree,
            TestSelection selection) {
        if (selection == null || selection.isAll()) {
            return selection == null ? TestSelection.all() : selection;
        }
        List<String> missing = selection.modules().stream()
                .filter(module -> !Files.isDirectory(baseWorktree.resolve(module))
                        || !Files.isDirectory(headWorktree.resolve(module)))
                .toList();
        if (missing.isEmpty()) {
            return selection;
        }
        return selection.withFallback(
                "Selected module(s) missing at one revision: " + String.join(", ", missing)
                        + "; running the full suite.",
                List.of("Module selection fell back to the full suite."));
    }

    private TestSuiteResult runOne(
            Path worktree,
            String revision,
            ExecutionOptions options,
            TestCandidate candidate,
            TestSelection selection) {
        if (candidate != null) {
            try {
                inject(worktree, candidate);
            } catch (IOException e) {
                return error(revision, "Unable to inject candidate test: " + e.getMessage());
            }
        }

        return adapterFactory.detect(worktree)
                .map(adapter -> {
                    if (candidate != null) {
                        return adapter.execute(worktree, revision, options, candidate.className());
                    }
                    if (selection == null || selection.isAll()) {
                        return adapter.execute(worktree, revision, options);
                    }
                    return adapter.executeModules(worktree, revision, options, selection.modules());
                })
                .orElseGet(() -> unsupported(revision));
    }

    /**
     * Writes a candidate test into a worktree after validating that the target
     * path cannot escape the worktree.
     *
     * <p>Symbolic links and non-regular files at the target path are rejected,
     * and every existing parent directory must resolve inside the worktree.
     *
     * @param worktree worktree that receives the test file
     * @param candidate test source and relative destination path
     * @throws IOException if the path is unsafe, a parent is not a directory, or
     *                     the file cannot be written
     */
    static void inject(Path worktree, TestCandidate candidate) throws IOException {
        Path root = worktree.toAbsolutePath().normalize();
        Path realRoot = root.toRealPath();
        Path relative = Path.of(candidate.relativePath().replace('\\', '/')).normalize();
        if (relative.isAbsolute()
                || relative.getRoot() != null
                || relative.getNameCount() == 0
                || relative.startsWith("..")) {
            throw new IOException("Candidate path escapes the worktree: " + candidate.relativePath());
        }

        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Candidate path escapes the worktree: " + candidate.relativePath());
        }

        createSafeParent(root, realRoot, relative.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target)) {
                throw new IOException(
                        "Candidate path must not be a symbolic link: " + candidate.relativePath());
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(
                        "Candidate path is not a regular file: " + candidate.relativePath());
            }
        }

        try (SeekableByteChannel channel = Files.newByteChannel(
                target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            channel.write(ByteBuffer.wrap(candidate.source().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static void createSafeParent(Path root, Path realRoot, Path relativeParent)
            throws IOException {
        if (relativeParent == null) {
            return;
        }

        Path current = root;
        for (Path component : relativeParent) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) {
                    throw new IOException(
                            "Candidate path traverses a symbolic link: "
                                    + relativeParent);
                }
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException(
                            "Candidate path parent is not a directory: "
                                    + relativeParent);
                }
                Path real = current.toRealPath();
                if (!real.startsWith(realRoot)) {
                    throw new IOException(
                            "Candidate path escapes the worktree: "
                                    + relativeParent);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static TestSuiteResult unsupported(String revision) {
        String message = "No supported Maven or Gradle project found";
        return TestSuiteResult.builder(revision, "none", "", ExecutionStatus.UNSUPPORTED)
                .output(message, false)
                .warnings(List.of(message))
                .build();
    }

    private static TestSuiteResult error(String revision, String message) {
        return TestSuiteResult.builder(revision, "injection", "", ExecutionStatus.ERROR)
                .output(message, false)
                .warnings(List.of(message))
                .build();
    }
}
