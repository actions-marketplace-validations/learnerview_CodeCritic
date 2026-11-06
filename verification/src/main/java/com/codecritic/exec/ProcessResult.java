package com.codecritic.exec;

import java.time.Duration;
import java.util.List;

/**
 * Captured outcome of an executed external process.
 *
 * @param command the command line that was executed
 * @param exitCode process exit code, or {@code -1} when the process timed out
 * @param stdout captured standard output
 * @param stderr captured standard error
 * @param timedOut whether the process exceeded its timeout and was terminated
 * @param stdoutTruncated whether standard output exceeded the capture limit
 * @param stderrTruncated whether standard error exceeded the capture limit
 * @param duration elapsed wall-clock time of the execution
 */
public record ProcessResult(
        List<String> command,
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean stdoutTruncated,
        boolean stderrTruncated,
        Duration duration
) {
    /**
     * Creates an immutable command list and replaces null output with empty strings.
     */
    public ProcessResult {
        command = List.copyOf(command);
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
