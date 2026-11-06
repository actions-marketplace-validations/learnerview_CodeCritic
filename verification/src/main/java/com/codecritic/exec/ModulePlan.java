package com.codecritic.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes the modules discovered for a project and selects modules to test.
 *
 * @param buildTool detected build tool, such as {@code maven}, {@code gradle}, or {@code none}
 * @param modules discovered modules, including the root module when a build file exists
 * @param warnings non-fatal problems encountered during discovery
 * @param graph reverse dependency graph relating the discovered modules
 */
public record ModulePlan(
        String buildTool,
        List<ProjectModule> modules,
        List<String> warnings,
        ModuleGraph graph
) {
    /**
     * Creates immutable copies of the module and warning lists and substitutes an
     * empty graph when none is supplied.
     */
    public ModulePlan {
        modules = modules == null ? List.of() : List.copyOf(modules);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        graph = graph == null ? ModuleGraph.empty() : graph;
    }

    /**
     * Creates a plan without a dependency graph.
     *
     * @param buildTool detected build tool
     * @param modules discovered modules
     * @param warnings non-fatal discovery warnings
     */
    public ModulePlan(String buildTool, List<ProjectModule> modules, List<String> warnings) {
        this(buildTool, modules, warnings, ModuleGraph.empty());
    }

    /**
     * Selects the modules that should be tested for the given changed paths.
     *
     * <p>The full suite is selected when no module metadata exists, the project
     * has a single module, no paths changed, a build file changed, no path maps
     * to a module, or the root module is affected. Otherwise the directly changed
     * modules plus all modules that transitively depend on them are selected.
     *
     * @param changedPaths repository-relative paths that changed; may be {@code null}
     * @return the selected modules or a documented full-suite fallback
     */
    public TestSelection select(Collection<String> changedPaths) {
        if (modules.isEmpty()) {
            return TestSelection.all("No module metadata detected; running the full suite.", warnings);
        }
        if (modules.size() == 1) {
            return TestSelection.all("Single-module project; running the full suite.", warnings);
        }
        if (changedPaths == null || changedPaths.isEmpty()) {
            return TestSelection.all("No changed paths; running the full suite.", warnings);
        }
        if (changedPaths.stream().anyMatch(ModulePlan::isBuildFile)) {
            return TestSelection.all("Build configuration changed; running the full suite.", warnings);
        }

        List<ProjectModule> selected = new ArrayList<>();
        for (String changedPath : changedPaths) {
            ProjectModule module = longestMatch(changedPath);
            if (module != null && !selected.contains(module)) {
                selected.add(module);
            }
        }

        if (selected.isEmpty()) {
            return TestSelection.all("No changed file mapped to a module; running the full suite.", warnings);
        }
        if (selected.stream().anyMatch(ProjectModule::isRoot)) {
            return TestSelection.all("Root-level change affects multiple modules; running the full suite.", warnings);
        }

        Map<String, ProjectModule> byPath = new LinkedHashMap<>();
        for (ProjectModule module : modules) {
            byPath.put(module.path(), module);
        }
        Set<String> expanded = new LinkedHashSet<>();
        for (ProjectModule module : selected) {
            expanded.add(module.path());
        }
        expanded.addAll(graph.dependentsClosure(expanded));

        List<ProjectModule> selection = new ArrayList<>();
        for (String path : expanded) {
            ProjectModule module = byPath.get(path);
            if (module != null) {
                selection.add(module);
            }
        }
        if (selection.stream().anyMatch(ProjectModule::isRoot)) {
            return TestSelection.all(
                    "Selected module is used by the root module; running the full suite.",
                    warnings);
        }

        selection.sort(Comparator.comparing(ProjectModule::path));
        return TestSelection.modules(selection, warnings);
    }

    private ProjectModule longestMatch(String changedPath) {
        return modules.stream()
                .filter(module -> module.contains(changedPath))
                .max(Comparator.comparingInt(module -> module.path().length()))
                .orElse(null);
    }

    /**
     * Returns whether a changed path names a build configuration file or wrapper.
     *
     * @param changedPath repository-relative path to test
     * @return {@code true} if the path affects Maven or Gradle build configuration
     */
    static boolean isBuildFile(String changedPath) {
        if (changedPath == null || changedPath.isBlank()) {
            return false;
        }
        String normalized = changedPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return fileName.equals("pom.xml")
                || fileName.equals("build.gradle")
                || fileName.equals("build.gradle.kts")
                || fileName.equals("settings.gradle")
                || fileName.equals("settings.gradle.kts")
                || fileName.equals("gradle.properties")
                || fileName.equals("mvnw")
                || fileName.equals("mvnw.cmd")
                || fileName.equals("gradlew")
                || fileName.equals("gradlew.bat")
                || normalized.startsWith(".mvn/")
                || normalized.startsWith("gradle/wrapper/");
    }
}
