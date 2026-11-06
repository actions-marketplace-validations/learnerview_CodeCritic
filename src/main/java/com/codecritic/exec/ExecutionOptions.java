package com.codecritic.exec;

import java.time.Duration;

/**
 * Limits applied when a project test command is executed.
 *
 * @param timeout maximum time allowed for the test process; must be positive
 * @param maxOutputBytes maximum number of bytes captured from each output stream; must be at least one
 */
public record ExecutionOptions(
        Duration timeout,
        int maxOutputBytes
) {
    /**
     * Validates the configured limits.
     *
     * @throws IllegalArgumentException if {@code timeout} is null, zero, or negative,
     *                                  or if {@code maxOutputBytes} is less than one
     */
    public ExecutionOptions {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Test timeout must be positive");
        }
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("Output limit must be positive");
        }
    }

    /**
     * Returns the default execution limits: a ten-minute timeout and 200,000 captured bytes.
     *
     * @return default execution options
     */
    public static ExecutionOptions defaults() {
        return new ExecutionOptions(Duration.ofMinutes(10), 200_000);
    }
}
