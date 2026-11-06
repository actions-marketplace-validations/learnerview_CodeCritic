package com.codecritic.exec;

import com.codecritic.change.GitCommandRunner;
import com.codecritic.change.GitException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and removes temporary Git worktrees for a repository.
 *
 * <p>Worktrees are created in the system temporary directory in detached mode.
 * Closing the manager removes all worktrees it created and prunes stale Git
 * worktree metadata, collecting any cleanup problems as warnings.
 */
public final class GitWorktreeManager implements AutoCloseable {

    private final GitCommandRunner git;
    private final Path repository;
    private final List<Path> worktrees = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * Creates a manager for the given repository.
     *
     * @param repository repository root used as the source of worktrees
     */
    public GitWorktreeManager(Path repository) {
        this(repository, new GitCommandRunner());
    }

    /**
     * Creates a manager that uses the supplied Git command runner.
     *
     * @param repository repository root used as the source of worktrees
     * @param git runner used to invoke Git
     */
    GitWorktreeManager(Path repository, GitCommandRunner git) {
        this.repository = repository.toAbsolutePath().normalize();
        this.git = git;
    }

    /**
     * Creates a detached worktree for the given Git reference.
     *
     * @param ref branch, tag, or commit to check out
     * @return the temporary directory containing the new worktree
     * @throws GitException if the worktree cannot be created
     */
    public Path add(String ref) {
        Path path;
        try {
            path = Files.createTempDirectory("codecritic-worktree-");
        } catch (IOException e) {
            throw new GitException("Unable to create a worktree directory: " + e.getMessage(), e);
        }

        try {
            git.runText(
                    repository,
                    "worktree",
                    "add",
                    "--detach",
                    "--force",
                    path.toString(),
                    ref);
            worktrees.add(path);
            return path;
        } catch (RuntimeException e) {
            deleteRecursively(path);
            throw e;
        }
    }

    /**
     * Removes every worktree created by this manager and prunes Git metadata.
     *
     * <p>Cleanup is best-effort; problems are returned as warnings instead of
     * being thrown.
     *
     * @return immutable warnings collected while cleaning up
     */
    public List<String> closeAndCollect() {
        for (int i = worktrees.size() - 1; i >= 0; i--) {
            Path path = worktrees.get(i);
            try {
                git.runText(repository, "worktree", "remove", "--force", path.toString());
            } catch (RuntimeException e) {
                warnings.add("Unable to remove git worktree " + path + ": " + e.getMessage());
            }
            if (Files.exists(path) && !deleteRecursively(path)) {
                warnings.add("Unable to delete worktree directory " + path);
            }
        }
        worktrees.clear();

        try {
            git.runText(repository, "worktree", "prune");
        } catch (RuntimeException e) {
            warnings.add("Unable to prune git worktree metadata: " + e.getMessage());
        }
        return List.copyOf(warnings);
    }

    /**
     * Removes all managed worktrees and discards any cleanup warnings.
     */
    @Override
    public void close() {
        closeAndCollect();
    }

    private static boolean deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return true;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                /**
                 * Deletes a visited file.
                 *
                 * @param file file being visited
                 * @param attrs file attributes
                 * @return {@link FileVisitResult#CONTINUE}
                 * @throws IOException if the file cannot be deleted
                 */
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                /**
                 * Deletes a directory after its entries have been visited.
                 *
                 * @param dir directory being visited
                 * @param exc failure encountered while visiting entries, or {@code null}
                 * @return {@link FileVisitResult#CONTINUE}
                 * @throws IOException if the directory cannot be deleted
                 */
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return !Files.exists(root);
        } catch (IOException e) {
            return false;
        }
    }
}
