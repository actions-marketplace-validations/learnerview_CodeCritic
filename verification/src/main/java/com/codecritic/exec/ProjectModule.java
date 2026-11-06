package com.codecritic.exec;

import java.util.Locale;

/**
 * A build module identified by its path relative to the project root.
 *
 * @param path normalized module path, or {@code .} for the root module
 */
public record ProjectModule(String path) {

    /**
     * Normalizes the module path and rejects unsafe or build-output paths.
     *
     * @throws IllegalArgumentException if the normalized path escapes the repository
     *                                  or targets a build output directory
     */
    public ProjectModule {
        path = normalize(path);
        if (!isSafe(path)) {
            throw new IllegalArgumentException("Unsafe module path: " + path);
        }
    }

    /**
     * Returns the module that represents the project root.
     *
     * @return the root module with path {@code .}
     */
    public static ProjectModule root() {
        return new ProjectModule(".");
    }

    /**
     * Returns whether this module is the project root.
     *
     * @return {@code true} if the module path is {@code .}
     */
    public boolean isRoot() {
        return ".".equals(path);
    }

    /**
     * Returns whether a changed path belongs to this module.
     *
     * @param changedPath repository-relative path to test
     * @return {@code true} if this is the root module, or if the path equals this
     *         module path or is located beneath it
     */
    public boolean contains(String changedPath) {
        String normalized = normalize(changedPath);
        if (isRoot()) {
            return true;
        }
        return normalized.equals(path) || normalized.startsWith(path + "/");
    }

    /**
     * Returns a human-readable name for this module.
     *
     * @return {@code <root>} for the root module, otherwise the module path
     */
    public String displayName() {
        return isRoot() ? "<root>" : path;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return ".";
        }
        String normalized = value.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "." : normalized;
    }

    private static boolean isSafe(String path) {
        if (isRoot(path)) {
            return true;
        }
        if (path.startsWith("/") || path.contains(":")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            String lower = segment.toLowerCase(Locale.ROOT);
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || lower.equals("target") || lower.equals("build")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRoot(String path) {
        return ".".equals(path);
    }
}
