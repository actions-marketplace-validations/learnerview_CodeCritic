package com.codecritic.change;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of loading a git revision range, containing the analyzed Java
 * changes as well as general repository metadata.
 *
 * @param repository the normalized absolute path of the repository
 * @param baseRef the base git reference
 * @param headRef the head git reference
 * @param files the Java files included in the comparison
 * @param allChangedPaths every path reported by git, including non-Java files
 * @param headSources Java source content at the head revision keyed by project-relative path
 */
public record ChangeSet(
        Path repository,
        String baseRef,
        String headRef,
        List<ChangedFile> files,
        List<String> allChangedPaths,
        Map<String, String> headSources
) {
    /**
     * Creates a change set and defensively copies the mutable-looking collection
     * components so the instance is immutable.
     */
    public ChangeSet {
        files = List.copyOf(files);
        allChangedPaths = List.copyOf(allChangedPaths);
        headSources = Map.copyOf(headSources);
    }
}
