package com.codecritic;

import com.codecritic.cli.CodeCriticCommand;
import picocli.CommandLine;

/**
 * Application entry point for the CodeCritic command-line interface.
 */
public final class CodeCritic {

    private CodeCritic() {
    }

    /**
     * Launches the CodeCritic command-line interface and terminates the process
     * with its exit code.
     *
     * @param args the command-line arguments passed to the process
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CodeCriticCommand()).execute(args);
        System.exit(exitCode);
    }
}
