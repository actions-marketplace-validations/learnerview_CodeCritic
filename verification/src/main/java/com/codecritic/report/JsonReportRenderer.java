package com.codecritic.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Renders a {@link VerificationReport} as indented JSON using Jackson.
 */
public final class JsonReportRenderer {

    /**
     * Creates a JSON report renderer.
     */
    public JsonReportRenderer() {
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Serializes the report as indented JSON.
     *
     * @param report the report to render
     * @return the JSON representation of the report
     * @throws IllegalStateException if the report cannot be serialized
     */
    public String render(VerificationReport report) {
        try {
            return mapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize verification report", e);
        }
    }
}
