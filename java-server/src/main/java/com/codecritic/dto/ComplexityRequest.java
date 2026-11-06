package com.codecritic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for complexity analysis.
 *
 * @param code Java source to analyse; must not be blank and may not exceed
 *             1,500,000 characters
 */
public record ComplexityRequest(
        @NotBlank(message = "code is required")
        @Size(max = 1_500_000, message = "code must be 1500000 characters or fewer")
        String code) { }
