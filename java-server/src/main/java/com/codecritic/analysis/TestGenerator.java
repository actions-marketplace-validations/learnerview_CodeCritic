package com.codecritic.analysis;

import com.codecritic.dto.TestGenerationResponse;

/**
 * Strategy contract for JUnit test scaffold generation.
 */
public interface TestGenerator {

    /**
     * Generates a JUnit test scaffold for a method.
     *
     * @param className name of the class under test; may be blank to infer it from the source
     * @param methodName name of the method under test; may be blank to select the first method
     * @param parameters comma-separated parameter types used as a fallback when parsing fails
     * @param code source code from which signatures are extracted
     * @return generated test source wrapped in a response
     */
    TestGenerationResponse generate(String className, String methodName, String parameters, String code);
}
