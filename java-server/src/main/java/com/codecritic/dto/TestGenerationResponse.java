package com.codecritic.dto;

/**
 * Result of a JUnit test-generation run.
 *
 * @param junitCode the generated JUnit source, or an empty string when no test could be
 *                  produced
 */
public record TestGenerationResponse(String junitCode) { }
