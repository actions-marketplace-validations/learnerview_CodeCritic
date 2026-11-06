package com.codecritic.dto;

/**
 * Complexity metrics computed for a piece of Java source.
 *
 * @param cyclomaticComplexity McCabe cyclomatic complexity of the analysed code
 * @param cognitiveComplexity cognitive complexity estimate of the analysed code
 */
public record ComplexityResponse(int cyclomaticComplexity, int cognitiveComplexity) { }
