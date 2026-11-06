package com.codecritic.exception;

/**
 * Signals that a requested application resource does not exist.
 *
 * <p>{@link GlobalExceptionHandler} maps this exception to an HTTP 404 response.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a human-readable description.
     *
     * @param message description of the missing resource
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}