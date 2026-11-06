package com.codecritic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Standard error payload returned by the API when a request fails.
 *
 * <p>Null fields are omitted from the serialized response via
 * {@link JsonInclude.Include#NON_NULL}.</p>
 *
 * @param status machine-readable status code such as {@code NOT_FOUND} or
 *               {@code VALIDATION_ERROR}
 * @param message human-readable description of the failure
 * @param timestamp when the error was produced, or {@code null} if not supplied
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String status, String message, Instant timestamp) {

    /**
     * Creates an error response, substituting the current instant when no timestamp is given.
     *
     * @param status machine-readable status code
     * @param message human-readable description of the failure
     * @param timestamp the error timestamp, or {@code null} to use {@link Instant#now()}
     * @return a new error response with a non-null timestamp
     */
    public static ApiErrorResponse of(String status, String message, Instant timestamp) {
        return new ApiErrorResponse(status, message, timestamp != null ? timestamp : Instant.now());
    }
}