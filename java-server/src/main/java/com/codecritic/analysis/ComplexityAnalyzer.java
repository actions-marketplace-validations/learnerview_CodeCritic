package com.codecritic.analysis;

import com.codecritic.dto.ComplexityResponse;

/**
 * Strategy contract for cyclomatic/cognitive complexity analysis.
 * Implementations can use AST parsing, heuristics, or any future approach.
 */
public interface ComplexityAnalyzer {

    /**
     * Analyzes the complexity of the supplied source code.
     *
     * @param code source code to analyze
     * @return computed cyclomatic and cognitive complexity scores
     */
    ComplexityResponse analyze(String code);
}
