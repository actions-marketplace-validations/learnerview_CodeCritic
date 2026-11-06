package com.codecritic.exec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleGraphTest {

    @Test
    void expandsTransitiveDependentsAndDeduplicatesCycles() {
        ModuleGraph graph = ModuleGraph.builder()
                .addDependency("lib", "service")
                .addDependency("service", "app")
                .addDependency("app", "lib")
                .build();

        assertThat(graph.dependentsClosure(List.of("lib")))
                .containsExactlyInAnyOrder("service", "app");
    }

    @Test
    void ignoresSelfDependenciesAndNulls() {
        ModuleGraph graph = ModuleGraph.builder()
                .addDependency("lib", "lib")
                .addDependency(null, "app")
                .addDependency("lib", null)
                .build();

        assertThat(graph.dependentsClosure(List.of("lib"))).isEmpty();
    }
}
