package com.codecritic.change;

/**
 * Describes a changed file between two git revisions together with the source
 * content that is available on each side of the comparison.
 *
 * @param oldPath the project-relative path at the base revision, or {@code null} when the file was added
 * @param newPath the project-relative path at the head revision, or {@code null} when the file was deleted
 * @param kind the kind of change detected for the file
 * @param baseSource the file content at the base revision, or {@code null} when no base content exists
 * @param headSource the file content at the head revision, or {@code null} when no head content exists
 */
public record ChangedFile(
        String oldPath,
        String newPath,
        ChangeKind kind,
        String baseSource,
        String headSource
) {
    /**
     * Returns the path that should be shown for this change.
     *
     * @return the head path when available, otherwise the base path
     */
    public String displayPath() {
        return newPath != null ? newPath : oldPath;
    }
}
