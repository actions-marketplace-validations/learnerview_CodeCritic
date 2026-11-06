package com.codecritic.controller;

import com.codecritic.agent.PythonAgentClient;
import com.codecritic.dto.*;
import com.codecritic.metrics.AnalysisMetrics;
import com.codecritic.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.learnerview.simplydone4j.dto.JobResponse;
import io.github.learnerview.simplydone4j.dto.JobSubmissionResponse;
import io.github.learnerview.simplydone4j.exception.QueueFullException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * REST controller exposing sync and async analysis endpoints used by the Python agent.
 *
 * <p>Sync endpoints ({@code /complexity}, {@code /bugs}, {@code /generate-test}) run
 * analysis immediately and return the result directly.</p>
 *
 * <p>Async endpoints ({@code /jobs/*}) submit work to the SimplyDone4J job queue
 * and return a job identifier for later retrieval.</p>
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final AnalysisService analysisService;
    private final AnalysisMetrics metrics;
    private final PythonAgentClient pythonAgentClient;

    /**
     * Creates a controller without an advisory Python agent client.
     *
     * <p>This convenience constructor is intended for tests or configurations that only
     * exercise the local analysis endpoints; the agent-backed endpoints will not have a
     * client available.</p>
     *
     * @param analysisService service performing the local analysis work
     * @param metrics collector recording analysis metrics
     */
    public AnalysisController(AnalysisService analysisService, AnalysisMetrics metrics) {
        this(analysisService, metrics, null);
    }

    /**
     * Creates a controller with an optional advisory Python agent client.
     *
     * @param analysisService service performing the local analysis work
     * @param metrics collector recording analysis metrics
     * @param pythonAgentClient provider for the optional Python agent client; when no
     *                          client bean exists, agent-backed endpoints are unavailable
     * @throws NullPointerException if {@code pythonAgentClient} is {@code null}
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AnalysisController(AnalysisService analysisService, AnalysisMetrics metrics,
                              ObjectProvider<PythonAgentClient> pythonAgentClient) {
        this.analysisService = analysisService;
        this.metrics = metrics;
        this.pythonAgentClient = pythonAgentClient.getIfAvailable();
    }

    /**
     * Returns a snapshot of the analysis metrics collected by this server.
     *
     * @return HTTP 200 with the metric names and their current values
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(metrics.snapshot());
    }

    /**
     * Calculates cyclomatic and cognitive complexity for the submitted Java source.
     *
     * @param req request containing the source code to analyse
     * @return HTTP 200 with the complexity metrics, HTTP 400 when the code is missing or
     *         blank, or HTTP 500 when analysis fails
     */
    @PostMapping("/complexity")
    public ResponseEntity<ComplexityResponse> complexity(@Valid @RequestBody ComplexityRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            ComplexityResponse resp = analysisService.calculateComplexity(req.code());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Complexity analysis failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Detects potential bugs and risky patterns in the submitted Java source.
     *
     * @param req request containing the source code to analyse
     * @return HTTP 200 with the detected findings, HTTP 400 when the code is missing or
     *         blank, or HTTP 500 when analysis fails
     */
    @PostMapping("/bugs")
    public ResponseEntity<BugReport> bugs(@Valid @RequestBody BugRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            BugReport report = analysisService.findBugs(req.code());
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Bug detection failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generates a JUnit test template for the requested class and method.
     *
     * @param req request containing the class, method, parameters, and optional source
     * @return HTTP 200 with the generated JUnit source, HTTP 400 when the request is
     *         missing, or HTTP 500 when generation fails
     */
    @PostMapping("/generate-test")
    public ResponseEntity<TestGenerationResponse> generateTest(@Valid @RequestBody TestGenerationRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().build();
        }

        String className = req.className() != null ? req.className() : "";
        String methodName = req.methodName() != null ? req.methodName() : "";
        String parameters = req.parameters() != null ? req.parameters() : "";
        String code = req.code() != null ? req.code() : "";
        try {
            TestGenerationResponse resp = analysisService.generateTest(className, methodName, parameters, code);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Test generation failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Proxies the submitted source to the advisory Python agent for a code review.
     *
     * @param req request containing the source code to review
     * @return HTTP 200 with the agent's review response
     * @throws NullPointerException if no Python agent client is configured
     */
    @PostMapping("/review")
    public ResponseEntity<?> review(@Valid @RequestBody PythonReviewRequest req) {
        return ResponseEntity.ok(pythonAgentClient.review(req));
    }

    /**
     * Proxies the submitted source to the advisory Python agent for full test generation.
     *
     * @param req request containing the source code used to generate tests
     * @return HTTP 200 with the agent's generated tests
     * @throws NullPointerException if no Python agent client is configured
     */
    @PostMapping("/generate-tests")
    public ResponseEntity<?> generateFullTests(@Valid @RequestBody PythonReviewRequest req) {
        return ResponseEntity.ok(pythonAgentClient.generateTests(req));
    }

    /**
     * Proxies a repository-analysis request to the advisory Python agent.
     *
     * @param req request describing the repository, branch, file limit, and optional token
     * @return HTTP 200 with the agent's repository analysis
     * @throws NullPointerException if no Python agent client is configured
     */
    @PostMapping("/analyze-repository")
    public ResponseEntity<?> analyzeRepository(@Valid @RequestBody RepositoryAnalysisRequest req) {
        return ResponseEntity.ok(pythonAgentClient.analyzeRepository(req));
    }

    /**
     * Proxies a debugging request to the advisory Python agent.
     *
     * @param req request containing the source code, error log, and optional language
     * @return HTTP 200 with the agent's debugging analysis
     * @throws NullPointerException if no Python agent client is configured
     */
    @PostMapping("/debug")
    public ResponseEntity<?> debug(@Valid @RequestBody DebugAnalysisRequest req) {
        return ResponseEntity.ok(pythonAgentClient.debug(req));
    }

    /**
     * Submits an asynchronous complexity-analysis job.
     *
     * @param req request containing the source code to analyse
     * @return HTTP 202 with the job identifier and initial status, HTTP 400 when the code
     *         is missing or blank, HTTP 503 when the queue is full, or HTTP 500 when
     *         submission fails
     */
    @PostMapping("/jobs/complexity")
    public ResponseEntity<?> submitComplexityJob(@Valid @RequestBody ComplexityRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String producer = getProducer();
            JobSubmissionResponse job = analysisService.submitAnalysisJob("complexity-analysis",
                    Map.of("code", req.code()), producer);
            return ResponseEntity.accepted().body(Map.of("jobId", job.getJobId(), "status", job.getStatus()));
        } catch (QueueFullException e) {
            log.warn("Complexity job rejected because the queue is full");
            return queueFull();
        } catch (Exception e) {
            log.error("Failed to submit complexity job", e);
            return internalServerError();
        }
    }

    /**
     * Submits an asynchronous bug-detection job.
     *
     * @param req request containing the source code to analyse
     * @return HTTP 202 with the job identifier and initial status, HTTP 400 when the code
     *         is missing or blank, HTTP 503 when the queue is full, or HTTP 500 when
     *         submission fails
     */
    @PostMapping("/jobs/bugs")
    public ResponseEntity<?> submitBugsJob(@Valid @RequestBody BugRequest req) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String producer = getProducer();
            JobSubmissionResponse job = analysisService.submitAnalysisJob("bug-detection",
                    Map.of("code", req.code()), producer);
            return ResponseEntity.accepted().body(Map.of("jobId", job.getJobId(), "status", job.getStatus()));
        } catch (QueueFullException e) {
            log.warn("Bug detection job rejected because the queue is full");
            return queueFull();
        } catch (Exception e) {
            log.error("Failed to submit bug detection job", e);
            return internalServerError();
        }
    }

    /**
     * Returns the current state of a job, whatever that state is: queued, running, succeeded,
     * failed or dead-lettered. A 404 is reserved for a jobId that does not exist, so a client
     * polling this endpoint can tell "still working" from "finished, and it failed" — without
     * that distinction a failed job is indistinguishable from a queued one and the caller just
     * spins until it gives up.
     *
     * @param jobId identifier of the job to retrieve
     * @return HTTP 200 with the job state, HTTP 404 when no such job exists, or HTTP 403
     *         when the job belongs to a different producer
     * @throws RuntimeException if the job backend fails to return the job
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobResult(@PathVariable String jobId) {
        Object response;
        try {
            response = analysisService.getJobResult(jobId);
        } catch (RuntimeException e) {
            // Not an unknown job — the coordinator only reports those as an absent result.
            // Swallowing this would turn a backend outage into a 404 and a silent client hang,
            // so it propagates and GlobalExceptionHandler renders a sanitised 5xx.
            log.error("Failed to retrieve job {}", jobId, e);
            throw e;
        }
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiErrorResponse.of("NOT_FOUND", "No such job: " + jobId, null));
        }
        if (response instanceof JobResponse job) {
            // Only the job's owner may read its payload/result (avoids IDOR).
            String expectedProducer = getProducer() + "-" + job.getJobType();
            if (!expectedProducer.equals(job.getProducer())) {
                log.warn("User {} attempted to access job {} owned by {}", getProducer(), jobId, job.getProducer());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ApiErrorResponse.of("FORBIDDEN", "You do not own this job", null));
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Submits an asynchronous test-generation job.
     *
     * @param req request containing the class, method, parameters, and optional source
     * @return HTTP 202 with the job identifier and initial status, HTTP 400 when the request
     *         is missing, HTTP 503 when the queue is full, or HTTP 500 when submission fails
     */
    @PostMapping("/jobs/generate-test")
    public ResponseEntity<?> submitTestGenerationJob(@Valid @RequestBody TestGenerationRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().build();
        }
        String className = req.className() != null ? req.className() : "";
        String methodName = req.methodName() != null ? req.methodName() : "";
        String parameters = req.parameters() != null ? req.parameters() : "";
        String code = req.code() != null ? req.code() : "";
        try {
            String producer = getProducer();
            JobSubmissionResponse job = analysisService.submitAnalysisJob("test-generation",
                    Map.of("className", className, "methodName", methodName,
                            "parameters", parameters, "code", code), producer);
            return ResponseEntity.accepted().body(Map.of("jobId", job.getJobId(), "status", job.getStatus()));
        } catch (QueueFullException e) {
            log.warn("Test generation job rejected because the queue is full");
            return queueFull();
        } catch (Exception e) {
            log.error("Failed to submit test generation job", e);
            return internalServerError();
        }
    }

    /**
     * The exception's own message can carry connection strings, hostnames and driver
     * internals, so the caller gets the same sanitised body GlobalExceptionHandler emits and
     * the real cause stays in the log where the operator wrote it.
     */
    /**
     * Builds the sanitised 500 response used when job submission fails.
     *
     * @return HTTP 500 with an {@code INTERNAL_ERROR} error body
     */
    private ResponseEntity<ApiErrorResponse> internalServerError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", null));
    }

    /**
     * Builds the 503 response used when the job queue is saturated.
     *
     * @return HTTP 503 with a {@code QUEUE_FULL} error body
     */
    private ResponseEntity<ApiErrorResponse> queueFull() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("QUEUE_FULL", "The job queue is full; retry later", null));
    }

    /**
     * Resolves the producer identity used for job ownership from the security context.
     *
     * @return the authenticated principal name, or {@code codecritic-anonymous} when no
     *         authenticated user is present
     */
    private String getProducer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null && !"anonymous".equals(auth.getPrincipal())) {
            return auth.getPrincipal().toString();
        }
        return "codecritic-anonymous";
    }
}