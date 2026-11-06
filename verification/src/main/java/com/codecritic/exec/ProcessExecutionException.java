package com.codecritic.exec;

/**
 * Signals that an external process could not be started, completed, or awaited.
 */
public final class ProcessExecutionException extends RuntimeException {

    /**
     * Creates an exception with the given message.
     *
     * @param message description of the execution failure
     */
    public ProcessExecutionException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message description of the execution failure
     * @param cause underlying exception that triggered the failure
     */
    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
