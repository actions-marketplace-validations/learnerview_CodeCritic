package com.codecritic.exec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Executes external commands on behalf of the project adapters.
 */
public interface CommandRunner {

    /**
     * Runs a command to completion or until the timeout elapses.
     *
     * @param workingDirectory directory in which the command is started
     * @param command command and arguments to execute
     * @param timeout maximum time allowed for the process
     * @param maxOutputBytes maximum number of bytes captured from each output stream
     * @return the captured process outcome
     * @throws ProcessExecutionException if the process cannot be started or awaited
     * @throws IllegalArgumentException if the command is empty or a limit is invalid
     */
    ProcessResult run(Path workingDirectory, List<String> command, Duration timeout, int maxOutputBytes);

    /**
     * Returns a short name describing where commands are executed.
     *
     * @return the environment name, such as {@code host} or {@code docker}
     */
    default String environment() {
        return "host";
    }
}
