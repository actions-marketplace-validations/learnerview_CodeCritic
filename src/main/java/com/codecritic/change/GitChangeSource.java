package com.codecritic.change;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the files and source contents for a pair of git revisions.
 * <p>
 * The source uses batched {@code git cat-file} reads for head content and falls
 * back to individual {@code git show} calls when necessary. Only Java files are
 * included in the resulting {@link ChangeSet#files()}, while every changed path
 * is retained in {@link ChangeSet#allChangedPaths()}.
 */
public final class GitChangeSource {

    private final GitCommandRunner git;
    private final GitBlobBatchReader blobReader;

    /**
     * Creates a change source backed by the default git command runner and blob reader.
     */
    public GitChangeSource() {
        this(new GitCommandRunner(), new GitBlobBatchReader());
    }

    /**
     * Creates a change source with the supplied command runner.
     *
     * @param git the runner used to execute git commands
     */
    GitChangeSource(GitCommandRunner git) {
        this(git, new GitBlobBatchReader());
    }

    /**
     * Creates a change source with the supplied command runner and blob reader.
     *
     * @param git the runner used to execute git commands
     * @param blobReader the reader used to batch-fetch head source content
     */
    GitChangeSource(GitCommandRunner git, GitBlobBatchReader blobReader) {
        this.git = git;
        this.blobReader = blobReader;
    }

    /**
     * Loads the change set between the given git references.
     *
     * @param repository the repository directory
     * @param baseRef the base git reference
     * @param headRef the head git reference
     * @return the loaded change set with Java sources for both revisions where available
     * @throws GitException if the repository is not a directory, either reference
     *         cannot be resolved, or a git command fails
     */
    public ChangeSet load(Path repository, String baseRef, String headRef) {
        Path root = repository.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new GitException("Repository directory does not exist: " + root);
        }

        git.runText(root, "rev-parse", "--verify", baseRef + "^{commit}");
        git.runText(root, "rev-parse", "--verify", headRef + "^{commit}");

        Map<String, String> headSources = loadHeadSources(root, headRef);
        List<ChangedFile> files = new ArrayList<>();
        java.util.Set<String> allChangedPaths = new java.util.LinkedHashSet<>();

        for (RawChange raw : readRawChanges(root, baseRef, headRef)) {
            if (raw.oldPath() != null) {
                allChangedPaths.add(raw.oldPath());
            }
            if (raw.newPath() != null) {
                allChangedPaths.add(raw.newPath());
            }
            if (!isJava(raw.oldPath()) && !isJava(raw.newPath())) {
                continue;
            }
            switch (raw.kind()) {
                case ADDED -> files.add(new ChangedFile(
                        null,
                        raw.newPath(),
                        ChangeKind.ADDED,
                        null,
                        headSource(root, headRef, headSources, raw.newPath())));
                case DELETED -> files.add(new ChangedFile(
                        raw.oldPath(),
                        null,
                        ChangeKind.DELETED,
                        readAtRef(root, baseRef, raw.oldPath()),
                        null));
                case RENAMED -> files.add(new ChangedFile(
                        raw.oldPath(),
                        raw.newPath(),
                        ChangeKind.RENAMED,
                        readAtRef(root, baseRef, raw.oldPath()),
                        headSource(root, headRef, headSources, raw.newPath())));
                case MODIFIED -> files.add(new ChangedFile(
                        raw.oldPath(),
                        raw.newPath(),
                        ChangeKind.MODIFIED,
                        readAtRef(root, baseRef, raw.oldPath()),
                        headSource(root, headRef, headSources, raw.newPath())));
            }
        }

        return new ChangeSet(
                root,
                baseRef,
                headRef,
                files,
                new ArrayList<>(allChangedPaths),
                headSources);
    }

    private Map<String, String> loadHeadSources(Path root, String headRef) {
        byte[] tree = git.runBytes(root, "ls-tree", "-r", "--name-only", "-z", headRef);
        String[] paths = new String(tree, StandardCharsets.UTF_8).split("\u0000");
        List<String> javaPaths = new ArrayList<>();
        for (String path : paths) {
            if (isJava(path)) {
                javaPaths.add(path);
            }
        }

        List<String> batchPaths = javaPaths.stream()
                .filter(path -> !path.contains("\n") && !path.contains("\r"))
                .toList();
        Map<String, String> batched = blobReader.read(root, headRef, batchPaths);

        Map<String, String> sources = new LinkedHashMap<>();
        for (String path : javaPaths) {
            String source = batched.get(path);
            sources.put(path, source != null ? source : readAtRef(root, headRef, path));
        }
        return sources;
    }

    private List<RawChange> readRawChanges(Path root, String baseRef, String headRef) {
        byte[] diff = git.runBytes(
                root,
                "diff",
                "--name-status",
                "-M",
                "-z",
                "--no-ext-diff",
                baseRef,
                headRef);
        String[] tokens = new String(diff, StandardCharsets.UTF_8).split("\u0000");
        List<RawChange> changes = new ArrayList<>();

        int index = 0;
        while (index < tokens.length) {
            String status = tokens[index++];
            if (status.isBlank()) {
                continue;
            }
            char code = status.charAt(0);
            if (code == 'R') {
                String oldPath = tokens[index++];
                String newPath = tokens[index++];
                changes.add(new RawChange(ChangeKind.RENAMED, oldPath, newPath));
            } else if (code == 'C') {
                index++;
                String newPath = tokens[index++];
                changes.add(new RawChange(ChangeKind.ADDED, null, newPath));
            } else {
                String path = tokens[index++];
                ChangeKind kind = switch (code) {
                    case 'A' -> ChangeKind.ADDED;
                    case 'D' -> ChangeKind.DELETED;
                    default -> ChangeKind.MODIFIED;
                };
                changes.add(new RawChange(kind, path, path));
            }
        }
        return changes;
    }

    private String headSource(Path root, String headRef, Map<String, String> headSources, String path) {
        String source = headSources.get(path);
        return source != null ? source : readAtRef(root, headRef, path);
    }

    private String readAtRef(Path root, String ref, String path) {
        if (path == null) {
            return null;
        }
        byte[] content = git.runBytes(root, "show", ref + ":" + path);
        return new String(content, StandardCharsets.UTF_8);
    }

    private static boolean isJava(String path) {
        return path != null && path.endsWith(".java");
    }

    private record RawChange(ChangeKind kind, String oldPath, String newPath) {
    }
}
