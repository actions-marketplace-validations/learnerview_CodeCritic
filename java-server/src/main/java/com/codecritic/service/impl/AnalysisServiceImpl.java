package com.codecritic.service.impl;

import com.codecritic.analysis.ComplexityAnalyzer;
import com.codecritic.analysis.BugDetector;
import com.codecritic.analysis.TestGenerator;
import com.codecritic.dto.BugReport;
import com.codecritic.dto.ComplexityResponse;
import com.codecritic.dto.TestGenerationResponse;
import com.codecritic.job.JobCoordinator;
import com.codecritic.metrics.AnalysisMetrics;
import com.codecritic.service.AnalysisService;
import io.github.learnerview.simplydone4j.dto.JobSubmissionResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Facade (Structural pattern) — the single entry point the controller layer
 * needs. Delegates each concern to a focused collaborator:
 *
 * <ul>
 *   <li>Complexity -> ComplexityAnalyzer (Strategy)</li>
 *   <li>Bugs -> BugDetector (Composite of pattern + SpotBugs)</li>
 *   <li>Tests -> TestGenerator (Strategy)</li>
 *   <li>Jobs -> JobCoordinator (Adapter over SimplyDone4J)</li>
 * </ul>
 *
 * This class has no business logic of its own, satisfying SRP while keeping
 * the controller API stable.
 */
@Service
public class AnalysisServiceImpl implements AnalysisService {

    private final ComplexityAnalyzer complexityAnalyzer;
    private final BugDetector bugDetector;
    private final TestGenerator testGenerator;
    private final JobCoordinator jobCoordinator;
    private final AnalysisMetrics metrics;

    /**
     * Creates the facade with its analysis and job collaborators.
     *
     * @param complexityAnalyzer analyzer computing complexity metrics
     * @param bugDetector detector searching for potential defects; may be {@code null}
     * @param testGenerator generator producing JUnit test source
     * @param jobCoordinator coordinator handling asynchronous job submission and retrieval
     * @param metrics collector recording analysis timing metrics
     */
    public AnalysisServiceImpl(ComplexityAnalyzer complexityAnalyzer,
                               BugDetector bugDetector,
                               TestGenerator testGenerator,
                               JobCoordinator jobCoordinator,
                               AnalysisMetrics metrics) {
        this.complexityAnalyzer = complexityAnalyzer;
        this.bugDetector = bugDetector;
        this.testGenerator = testGenerator;
        this.jobCoordinator = jobCoordinator;
        this.metrics = metrics;
    }

    /**
     * Computes complexity metrics and records the elapsed time.
     *
     * @param code Java source to analyse
     * @return the computed cyclomatic and cognitive complexity
     */
    @Override
    public ComplexityResponse calculateComplexity(String code) {
        long started = System.nanoTime();
        try {
            return complexityAnalyzer.analyze(code);
        } finally {
            metrics.record("complexity", (System.nanoTime() - started) / 1_000_000);
        }
    }

    /**
     * Detects potential bugs and records the elapsed time. Returns an empty report when no
     * bug detector is configured.
     *
     * @param code Java source to analyse
     * @return the detected findings, possibly empty
     */
    @Override
    public BugReport findBugs(String code) {
        long started = System.nanoTime();
        try {
            if (bugDetector == null) {
                return new BugReport(List.of());
            }
            return new BugReport(bugDetector.detect(code));
        } finally {
            metrics.record("bugs", (System.nanoTime() - started) / 1_000_000);
        }
    }

    /**
     * Generates a JUnit test template and records the elapsed time.
     *
     * @param className target class name, possibly empty
     * @param methodName target method name
     * @param parameters optional parameter definition string
     * @param code optional Java source used to extract signatures
     * @return the generated JUnit source
     */
    @Override
    public TestGenerationResponse generateTest(String className, String methodName, String parameters, String code) {
        long started = System.nanoTime();
        try {
            return testGenerator.generate(className, methodName, parameters, code);
        } finally {
            metrics.record("test-generation", (System.nanoTime() - started) / 1_000_000);
        }
    }

    /**
     * Submits an analysis job through the job coordinator.
     *
     * @param jobType type of analysis job
     * @param payload job-specific input values
     * @param producer identity that owns the submitted job
     * @return the submission receipt containing the job identifier and initial status
     */
    @Override
    public JobSubmissionResponse submitAnalysisJob(String jobType, Map<String, Object> payload, String producer) {
        return jobCoordinator.submit(jobType, payload, producer);
    }

    /**
     * Retrieves the current state of a submitted job.
     *
     * @param jobId identifier of the job to retrieve
     * @return the job state, or {@code null} when no such job exists
     */
    @Override
    public Object getJobResult(String jobId) {
        return jobCoordinator.getJobResult(jobId);
    }
}
