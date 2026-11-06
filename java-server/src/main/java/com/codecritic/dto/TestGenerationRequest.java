package com.codecritic.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload for JUnit test generation.
 *
 * @param className optional target class name, limited to 200 characters
 * @param methodName optional target method name, limited to 200 characters
 * @param parameters optional parameter definition string, limited to 4096 characters
 * @param code optional Java source used to extract precise signatures, limited to
 *             1,500,000 characters
 */
public record TestGenerationRequest(
        @Size(max = 200, message = "className must be 200 characters or fewer")
        String className,
        @Size(max = 200, message = "methodName must be 200 characters or fewer")
        String methodName,
        @Size(max = 4096, message = "parameters must be 4096 characters or fewer")
        String parameters,
        @Size(max = 1_500_000, message = "code must be 1500000 characters or fewer")
        String code) { }
