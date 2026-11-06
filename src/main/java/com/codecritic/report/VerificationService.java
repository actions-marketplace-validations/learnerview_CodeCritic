package com.codecritic.report;

import com.codecritic.change.ChangeSet;
import com.codecritic.change.ChangedFile;
import com.codecritic.change.GitChangeSource;
import com.codecritic.change.JavaDiffAnalyzer;
import com.codecritic.change.JavaDiffResult;
import com.codecritic.change.MethodChange;
import com.codecritic.change.ProjectIndex;
import com.codecritic.exec.BaselineExecution;
import com.codecritic.exec.ExecutionOptions;
import com.codecritic.exec.ProjectTestRunner;
import com.codecritic.exec.TestCandidate;
import com.codecritic.exec.TestSelectionMode;
import com.codecritic.impact.ImpactAnalyzer;
import com.codecritic.impact.ImpactReport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a verification run: loads the git change set, analyzes Java
 * callable changes and their impact, optionally executes tests, and assembles a
 * {@link VerificationReport}.
 * <p>
 * Collaborators are injected through the constructor so the workflow can be
 * tested with substitutes.
 */
public final class VerificationService {

    private final GitChangeSource changeSource;
    private final JavaDiffAnalyzer diffAnalyzer;
    private final ImpactAnalyzer impactAnalyzer;
    private final VerificationReportBuilder reportBuilder;
    private final ProjectTestRunner testRunner;

    /**
     * Creates a service with the supplied collaborators.
     *
     * @param changeSource loads the git change set
     * @param diffAnalyzer compares callables within each changed file
     * @param impactAnalyzer assesses the impact of the detected changes
     * @param reportBuilder assembles the final report
     * @param testRunner optionally executes test suites for both revisions
     */
    public VerificationService(
            GitChangeSource changeSource,
            JavaDiffAnalyzer diffAnalyzer,
            ImpactAnalyzer impactAnalyzer,
            VerificationReportBuilder reportBuilder,
            ProjectTestRunner testRunner) {
        this.changeSource = changeSource;
        this.diffAnalyzer = diffAnalyzer;
        this.impactAnalyzer = impactAnalyzer;
        this.reportBuilder = reportBuilder;
        this.testRunner = testRunner;
    }

    /**
     * Creates a service wired with the default implementations.
     *
     * @return a fully configured verification service
     */
    public static VerificationService createDefault() {
        return new VerificationService(
                new GitChangeSource(),
                new JavaDiffAnalyzer(),
                new ImpactAnalyzer(),
                new VerificationReportBuilder(),
                new ProjectTestRunner());
    }

    /**
     * Verifies a revision range without executing tests.
     *
     * @param repository the repository directory
     * @param baseRef the base git reference
     * @param headRef the head git reference
     * @return the verification report
     * @throws com.codecritic.change.GitException if the repository or a reference cannot be loaded
     */
    public VerificationReport verify(Path repository, String baseRef, String headRef) {
        return verify(repository, baseRef, headRef, null);
    }

    /**
     * Verifies a revision range and optionally runs existing tests.
     *
     * @param repository the repository directory
     * @param baseRef the base git reference
     * @param headRef the head git reference
     * @param executionOptions test execution settings, or {@code null} to skip tests
     * @return the verification report
     * @throws com.codecritic.change.GitException if the repository or a reference cannot be loaded
     */
    public VerificationReport verify(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions executionOptions) {
        return verify(repository, baseRef, headRef, executionOptions, null, false);
    }

    /**
     * Verifies a revision range with optional test execution and a differential
     * candidate test, using all modules for test selection.
     *
     * @param repository the repository directory
     * @param baseRef the base git reference
     * @param headRef the head git reference
     * @param executionOptions test execution settings, or {@code null} to skip tests
     * @param candidate the test to inject into both revisions, or {@code null} when none
     * @param stabilityCheck whether to repeat differential candidates to detect flakiness
     * @return the verification report
     * @throws com.codecritic.change.GitException if the repository or a reference cannot be loaded
     */
    public VerificationReport verify(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions executionOptions,
            TestCandidate candidate,
            boolean stabilityCheck) {
        return verify(
                repository,
                baseRef,
                headRef,
                executionOptions,
                candidate,
                stabilityCheck,
                List.of(),
                TestSelectionMode.ALL);
    }

    /**
     * Performs a complete verification run.
     *
     * @param repository the repository directory
     * @param baseRef the base git reference
     * @param headRef the head git reference
     * @param executionOptions test execution settings, or {@code null} to skip tests
     * @param candidate the test to inject into both revisions, or {@code null} when none
     * @param stabilityCheck whether to repeat differential candidates to detect flakiness
     * @param changedPaths explicit paths used for test selection, or an empty list to use the loaded change set
     * @param selectionMode how test modules are selected for execution
     * @return the verification report
     * @throws com.codecritic.change.GitException if the repository or a reference cannot be loaded
     */
    public VerificationReport verify(
            Path repository,
            String baseRef,
            String headRef,
            ExecutionOptions executionOptions,
            TestCandidate candidate,
            boolean stabilityCheck,
            List<String> changedPaths,
            TestSelectionMode selectionMode) {
        ChangeSet changeSet = changeSource.load(repository, baseRef, headRef);

        List<MethodChange> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (ChangedFile file : changeSet.files()) {
            JavaDiffResult result = diffAnalyzer.diff(
                    file.displayPath(),
                    file.baseSource(),
                    file.headSource());
            changes.addAll(result.changes());
            warnings.addAll(result.warnings());
        }

        ProjectIndex index = ProjectIndex.from(changeSet.headSources());
        ImpactReport impact = impactAnalyzer.analyze(changes, index);
        List<String> paths = changedPaths == null || changedPaths.isEmpty()
                ? changeSet.allChangedPaths()
                : changedPaths;
        BaselineExecution baseline = executionOptions == null
                ? null
                : testRunner.run(
                        repository,
                        baseRef,
                        headRef,
                        executionOptions,
                        candidate,
                        stabilityCheck,
                        paths,
                        selectionMode);
        return reportBuilder.build(changeSet, impact, warnings, baseline);
    }
}
