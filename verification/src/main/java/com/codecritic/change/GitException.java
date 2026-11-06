package com.codecritic.change;

/**
 * Signals that a git operation could not be completed, for example because git
 * is unavailable, a reference is invalid, a command fails, or a process times out.
 */
public class GitException extends RuntimeException {
    /**
     * Creates an exception with the given detail message.
     *
     * @param message the detail message
     */
    public GitException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given detail message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
}
