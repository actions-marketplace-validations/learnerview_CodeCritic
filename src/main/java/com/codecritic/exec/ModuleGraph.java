package com.codecritic.exec;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Directed module dependency graph expressed as reverse edges.
 *
 * <p>Each entry maps a module path to the paths of modules that directly depend
 * on it. The graph is immutable.
 *
 * @param dependents map from a module path to the set of modules that depend on it
 */
public record ModuleGraph(Map<String, Set<String>> dependents) {

    /**
     * Creates immutable copies of the dependency map and its value sets.
     */
    public ModuleGraph {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        if (dependents != null) {
            dependents.forEach((module, values) -> copy.put(
                    module,
                    values == null ? Set.of() : Set.copyOf(values)));
        }
        dependents = Map.copyOf(copy);
    }

    /**
     * Returns an empty module graph.
     *
     * @return a graph with no dependency edges
     */
    public static ModuleGraph empty() {
        return new ModuleGraph(Map.of());
    }

    /**
     * Computes the transitive set of modules that depend on the given modules.
     *
     * <p>The result excludes the input modules themselves.
     *
     * @param modules starting module paths; may be null or empty
     * @return every module reachable through reverse dependency edges
     */
    public Set<String> dependentsClosure(Collection<String> modules) {
        Set<String> closure = new LinkedHashSet<>();
        if (modules == null || modules.isEmpty()) {
            return closure;
        }
        Set<String> visited = new LinkedHashSet<>(modules);
        Deque<String> queue = new ArrayDeque<>(modules);
        while (!queue.isEmpty()) {
            String module = queue.removeFirst();
            for (String dependent : dependents.getOrDefault(module, Set.of())) {
                if (visited.add(dependent)) {
                    closure.add(dependent);
                    queue.addLast(dependent);
                }
            }
        }
        return closure;
    }

    /**
     * Creates a builder for incrementally assembling a module graph.
     *
     * @return a new empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects reverse dependency edges before producing an immutable {@link ModuleGraph}.
     */
    public static final class Builder {

        /**
         * Creates an empty builder; use {@link ModuleGraph#builder()} instead.
         */
        private Builder() {
        }

        private final Map<String, Set<String>> dependents = new LinkedHashMap<>();

        /**
         * Records that {@code dependent} directly depends on {@code module}.
         *
         * <p>Null values and self-dependencies are ignored.
         *
         * @param module path of the module being depended on
         * @param dependent path of the module that depends on {@code module}
         * @return this builder
         */
        public Builder addDependency(String module, String dependent) {
            if (module == null || dependent == null || module.equals(dependent)) {
                return this;
            }
            dependents.computeIfAbsent(module, ignored -> new LinkedHashSet<>()).add(dependent);
            return this;
        }

        /**
         * Builds an immutable module graph from the collected edges.
         *
         * @return the assembled module graph
         */
        public ModuleGraph build() {
            return new ModuleGraph(dependents);
        }
    }
}
