package com.codecritic.change;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads multiple git blobs efficiently through a single long-running
 * {@code git cat-file --batch} process.
 * <p>
 * Paths that git reports as missing are omitted from the result rather than
 * treated as errors. The process is always terminated before this reader returns.
 */
final class GitBlobBatchReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /**
     * Reads the contents of the requested paths at the given revision.
     *
     * @param repository the repository directory
     * @param ref the git reference from which blobs are read
     * @param paths the project-relative paths to read
     * @return blob contents keyed by path; missing or ambiguous paths are skipped
     * @throws GitException if the git process fails, times out, returns malformed
     *         output, or the current thread is interrupted
     */
    Map<String, String> read(Path repository, String ref, List<String> paths) {
        if (paths.isEmpty()) {
            return Map.of();
        }

        ProcessBuilder builder = new ProcessBuilder(
                "git",
                "-C",
                repository.toAbsolutePath().normalize().toString(),
                "cat-file",
                "--batch");
        builder.redirectErrorStream(false);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = null;
        try {
            process = builder.start();

            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Process running = process;
            Thread errorDrain = Thread.startVirtualThread(() -> {
                try (InputStream error = running.getErrorStream()) {
                    error.transferTo(stderr);
                } catch (IOException ignored) {
                }
            });

            AtomicReference<IOException> writeFailure = new AtomicReference<>();
            Thread inputDrain = Thread.startVirtualThread(() -> {
                try (OutputStream stdin = running.getOutputStream()) {
                    for (String path : paths) {
                        stdin.write((ref + ":" + path + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    writeFailure.set(e);
                }
            });

            Map<String, String> sources = new LinkedHashMap<>();
            try (BufferedInputStream stdout = new BufferedInputStream(process.getInputStream())) {
                for (String path : paths) {
                    String header = readLine(stdout);
                    if (header == null) {
                        throw new GitException("git cat-file ended before returning " + path);
                    }
                    if (header.endsWith(" missing") || header.endsWith(" ambiguous")) {
                        continue;
                    }

                    String[] parts = header.split(" ");
                    if (parts.length != 3) {
                        throw new GitException("Unexpected git cat-file header: " + header);
                    }

                    int size = Integer.parseInt(parts[2]);
                    byte[] content = stdout.readNBytes(size);
                    if (content.length != size) {
                        throw new GitException("Short read from git cat-file for " + path);
                    }
                    if (stdout.read() != '\n') {
                        throw new GitException("Malformed git cat-file response for " + path);
                    }
                    sources.put(path, new String(content, StandardCharsets.UTF_8));
                }
            }

            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new GitException("git cat-file timed out");
            }
            inputDrain.join(1_000);
            errorDrain.join(1_000);

            if (writeFailure.get() != null) {
                throw new GitException("Unable to send git cat-file request", writeFailure.get());
            }
            if (process.exitValue() != 0) {
                throw new GitException("git cat-file failed: "
                        + stderr.toString(StandardCharsets.UTF_8).strip());
            }
            return sources;
        } catch (IOException e) {
            throw new GitException("Unable to read git blobs", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Interrupted while reading git blobs", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            line.write(next);
        }
        if (line.size() == 0) {
            return null;
        }
        return line.toString(StandardCharsets.UTF_8);
    }
}
