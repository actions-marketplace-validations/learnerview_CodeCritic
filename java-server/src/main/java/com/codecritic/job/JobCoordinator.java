package com.codecritic.job;

import io.github.learnerview.simplydone4j.dto.JobSubmissionResponse;
import java.util.Map;

/**
 * Contract for async job submission and retrieval.
 */
public interface JobCoordinator {

    /**
     * Submits a job for asynchronous execution.
     *
     * @param jobType type of analysis job, for example {@code complexity-analysis}
     * @param payload job-specific input values
     * @param producer identity that owns the submitted job
     * @return the submission receipt containing the job identifier and initial status
     */
    JobSubmissionResponse submit(String jobType, Map<String, Object> payload, String producer);

    /**
     * Retrieves the current state of a previously submitted job.
     *
     * @param jobId identifier returned by {@link #submit(String, Map, String)}
     * @return the job state, or {@code null} when no job with that identifier exists
     */
    Object getJobResult(String jobId);
}
