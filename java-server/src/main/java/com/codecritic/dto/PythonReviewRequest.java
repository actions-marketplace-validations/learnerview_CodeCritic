package com.codecritic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the advisory Python agent's code-review endpoints.
 *
 * @param code source code to review; must not be blank and must contain between 10 and
 *             1,500,000 characters
 */
public record PythonReviewRequest(
        @NotBlank(message = "code is required")
        @Size(min = 10, max = 1_500_000, message = "code must be between 10 and 1500000 characters")
        String code) {
}
