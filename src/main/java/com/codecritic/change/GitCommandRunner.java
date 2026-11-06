package com.codecritic.change;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executes git commands for a repository with a bounded timeout and terminal
 * prompting disabled.
 * <p>
 * Standard output and standard error are captured concurrently to avoid pipe
 * deadlocks. Failures are reported as {@link GitException} instances.
 */
public final class GitCommandRunner {

    /**
     * Creates a git command runner.
     */
    public GitCommandRunner() {
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /**
     * Runs a git command and captures its raw output.
     *
     * @param repository the repository directory passed to {@code git -C}
     * @param arguments the git arguments following the repository location
     * @return the exit code and captured standard output and error streams
     * @throws GitException if git cannot be started, the command times out, or the
     *         current thread is interrupted
     */
    public Result run(Path repository, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repository.toAbsolutePath().normalize().toString());
        command.addAll(List.of(arguments));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");

        try {
            Process process = builder.start();
            CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));

            boolean finished = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitException("git command timed out: " + String.join(" ", command));
            }

            return new Result(process.exitValue(), stdout.join(), stderr.join());
        } catch (IOException e) {
            throw new GitException("Unable to run git. Is git installed and on PATH?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Interrupted while running git", e);
        }
    }

    /**
     * Runs a git command and returns its standard output as UTF-8 text.
     *
     * @param repository the repository directory passed to {@code git -C}
     * @param arguments the git arguments following the repository location
     * @return the command's standard output decoded as UTF-8
     * @throws GitException if the command fails, times out, or git cannot be started
     */
    public String runText(Path repository, String... arguments) {
        Result result = run(repository, arguments);
        if (result.exitCode() != 0) {
            String stderr = new String(result.stderr(), StandardCharsets.UTF_8).strip();
            throw new GitException("git " + String.join(" ", arguments) + " failed: " + stderr);
        }
        return new String(result.stdout(), StandardCharsets.UTF_8);
    }

    /**
     * Runs a git command and returns its raw standard output.
     *
     * @param repository the repository directory passed to {@code git -C}
     * @param arguments the git arguments following the repository location
     * @return the command's raw standard output bytes
     * @throws GitException if the command fails, times out, or git cannot be started
     */
    public byte[] runBytes(Path repository, String... arguments) {
        Result result = run(repository, arguments);
        if (result.exitCode() != 0) {
            String stderr = new String(result.stderr(), StandardCharsets.UTF_8).strip();
            throw new GitException("git " + String.join(" ", arguments) + " failed: " + stderr);
        }
        return result.stdout();
    }

    private static byte[] readAll(InputStream stream) {
        try (InputStream input = stream) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new GitException("Unable to read git output", e);
        }
    }

    /**
     * Captured result of a git command.
     *
     * @param exitCode the process exit code
     * @param stdout the raw standard output bytes
     * @param stderr the raw standard error bytes
     */
    public record Result(int exitCode, byte[] stdout, byte[] stderr) {
    }
}
