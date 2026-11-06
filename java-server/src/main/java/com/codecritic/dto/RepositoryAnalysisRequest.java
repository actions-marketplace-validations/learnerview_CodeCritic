package com.codecritic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for repository-level analysis performed by the Python agent.
 *
 * @param repoUrl URL of the repository to analyse; must not be blank and may not exceed
 *                2048 characters
 * @param branch optional branch to analyse, limited to 255 characters
 * @param maxFiles optional maximum number of files to inspect, between 1 and 30
 * @param githubToken optional access token for private repositories, limited to 512 characters
 */
public record RepositoryAnalysisRequest(
        @NotBlank(message = "repoUrl is required")
        @Size(max = 2048, message = "repoUrl must be 2048 characters or fewer")
        String repoUrl,
        @Size(max = 255, message = "branch must be 255 characters or fewer")
        String branch,
        @Min(1) @Max(30) Integer maxFiles,
        @Size(max = 512, message = "githubToken must be 512 characters or fewer")
        String githubToken) {
}
