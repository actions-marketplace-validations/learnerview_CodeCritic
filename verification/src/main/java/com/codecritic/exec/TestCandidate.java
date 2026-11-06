package com.codecritic.exec;

/**
 * A candidate Java test source that can be injected into a worktree and executed.
 *
 * @param source complete Java source text of the test
 * @param relativePath path of the test file relative to the project root
 * @param className fully qualified class name used as the test selector
 */
public record TestCandidate(
        String source,
        String relativePath,
        String className
) {
    /**
     * Validates that all required test fields are present.
     *
     * @throws IllegalArgumentException if the source, path, or class name is null or blank
     */
    public TestCandidate {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Test source must not be blank");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Test path must not be blank");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Test class name must not be blank");
        }
    }
}
