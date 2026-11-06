package com.codecritic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for debugging assistance.
 *
 * @param code Java source related to the failure; must not be blank and may not exceed
 *             1,500,000 characters
 * @param errorLog captured error output or stack trace; must not be blank and may not
 *                 exceed 500,000 characters
 * @param language optional language identifier, limited to 32 characters
 */
public record DebugAnalysisRequest(
        @NotBlank(message = "code is required")
        @Size(max = 1_500_000, message = "code must be 1500000 characters or fewer")
        String code,
        @NotBlank(message = "errorLog is required")
        @Size(max = 500_000, message = "errorLog must be 500000 characters or fewer")
        String errorLog,
        @Size(max = 32, message = "language must be 32 characters or fewer")
        String language) {
}
