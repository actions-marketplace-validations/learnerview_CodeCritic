package com.codecritic.exec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Detects the build tool used by a worktree and creates the matching
 * {@link ProjectAdapter}.
 */
public final class ProjectAdapterFactory {

    private final Supplier<CommandRunner> commandRunner;
    private final TestResultXmlParser resultParser;

    /**
     * Creates a factory that runs builds in the Docker sandbox. The sandbox is
     * created only when a supported project is detected.
     */
    public ProjectAdapterFactory() {
        this(() -> new DockerSandboxRunner(), new TestResultXmlParser());
    }

    /**
     * Creates a factory with explicit command execution and result parsing.
     *
     * @param commandRunner runner used by the created adapters
     * @param resultParser parser used by the created adapters
     */
    public ProjectAdapterFactory(CommandRunner commandRunner, TestResultXmlParser resultParser) {
        this(() -> commandRunner, resultParser);
    }

    private ProjectAdapterFactory(
            Supplier<CommandRunner> commandRunner,
            TestResultXmlParser resultParser) {
        this.commandRunner = commandRunner;
        this.resultParser = resultParser;
    }

    /**
     * Detects a supported build tool by inspecting the worktree root.
     *
     * <p>A {@code pom.xml} selects Maven; a {@code build.gradle} or
     * {@code build.gradle.kts} selects Gradle.
     *
     * @param worktree worktree root to inspect
     * @return the matching adapter, or an empty optional when no supported build
     *         tool is present
     */
    public Optional<ProjectAdapter> detect(Path worktree) {
        if (Files.isRegularFile(worktree.resolve("pom.xml"))) {
            return Optional.of(new MavenProjectAdapter(commandRunner.get(), resultParser));
        }
        if (Files.isRegularFile(worktree.resolve("build.gradle"))
                || Files.isRegularFile(worktree.resolve("build.gradle.kts"))) {
            return Optional.of(new GradleProjectAdapter(commandRunner.get(), resultParser));
        }
        return Optional.empty();
    }
}
