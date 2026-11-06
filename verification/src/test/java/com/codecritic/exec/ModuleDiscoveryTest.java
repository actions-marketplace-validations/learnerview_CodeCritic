package com.codecritic.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDiscoveryTest {

    @TempDir
    Path worktree;

    @Test
    void discoversNestedMavenModules() throws Exception {
        Files.writeString(worktree.resolve("pom.xml"), """
                <project>
                  <modules>
                    <module>service</module>
                  </modules>
                </project>
                """);
        Files.createDirectories(worktree.resolve("service/lib"));
        Files.writeString(worktree.resolve("service/pom.xml"), """
                <project>
                  <modules>
                    <module>lib</module>
                  </modules>
                </project>
                """);

        ModulePlan plan = ModuleDiscovery.discover(worktree);

        assertThat(plan.buildTool()).isEqualTo("maven");
        assertThat(plan.modules()).extracting(ProjectModule::path)
                .containsExactly(".", "service", "service/lib");
    }

    @Test
    void selectsTheDeepestChangedMavenModule() throws Exception {
        ModulePlan plan = mavenPlan();

        TestSelection selection = plan.select(java.util.List.of(
                "service/src/main/java/App.java",
                "service/lib/src/main/java/Lib.java"));

        assertThat(selection.mode()).isEqualTo(TestSelectionMode.MODULES);
        assertThat(selection.modules()).containsExactly("service", "service/lib");
    }

    @Test
    void expandsMavenSelectionToDependentModules() throws Exception {
        Files.writeString(worktree.resolve("pom.xml"), """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>root</artifactId>
                  <version>1.0</version>
                  <modules>
                    <module>lib</module>
                    <module>app</module>
                  </modules>
                </project>
                """);
        Files.createDirectories(worktree.resolve("lib"));
        Files.writeString(worktree.resolve("lib/pom.xml"), """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0</version>
                  </parent>
                  <artifactId>lib</artifactId>
                </project>
                """);
        Files.createDirectories(worktree.resolve("app"));
        Files.writeString(worktree.resolve("app/pom.xml"), """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>root</artifactId>
                    <version>1.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        TestSelection selection = ModuleDiscovery.discover(worktree)
                .select(java.util.List.of("lib/src/main/java/Lib.java"));

        assertThat(selection.mode()).isEqualTo(TestSelectionMode.MODULES);
        assertThat(selection.modules()).containsExactly("app", "lib");
    }

    @Test
    void expandsGradleSelectionToDependentModules() throws Exception {
        Files.writeString(worktree.resolve("settings.gradle"), """
                include ':lib'
                include ':app'
                """);
        Files.createDirectories(worktree.resolve("lib"));
        Files.createDirectories(worktree.resolve("app"));
        Files.writeString(worktree.resolve("app/build.gradle"), """
                dependencies {
                    implementation project(':lib')
                }
                """);

        TestSelection selection = ModuleDiscovery.discover(worktree)
                .select(java.util.List.of("lib/src/main/java/Lib.java"));

        assertThat(selection.mode()).isEqualTo(TestSelectionMode.MODULES);
        assertThat(selection.modules()).containsExactly("app", "lib");
    }

    @Test
    void fallsBackToTheFullSuiteForBuildAndRootChanges() throws Exception {
        ModulePlan plan = mavenPlan();

        assertThat(plan.select(java.util.List.of("pom.xml")).isAll()).isTrue();
        assertThat(plan.select(java.util.List.of("README.md")).isAll()).isTrue();
        assertThat(plan.select(java.util.List.of()).isAll()).isTrue();
    }

    @Test
    void discoversGradleIncludes() throws Exception {
        Files.writeString(worktree.resolve("settings.gradle"), """
                include 'service'
                include(':service:lib')
                """);

        ModulePlan plan = ModuleDiscovery.discover(worktree);

        assertThat(plan.buildTool()).isEqualTo("gradle");
        assertThat(plan.modules()).extracting(ProjectModule::path)
                .containsExactly(".", "service", "service/lib");
    }

    @Test
    void ignoresUnsafeMavenModulePaths() throws Exception {
        Files.writeString(worktree.resolve("pom.xml"), """
                <project>
                  <modules>
                    <module>../outside</module>
                    <module>service</module>
                  </modules>
                </project>
                """);

        ModulePlan plan = ModuleDiscovery.discover(worktree);

        assertThat(plan.modules()).extracting(ProjectModule::path)
                .containsExactly(".", "service");
        assertThat(plan.warnings()).anyMatch(warning -> warning.contains("../outside"));
    }

    private ModulePlan mavenPlan() throws Exception {
        Files.writeString(worktree.resolve("pom.xml"), """
                <project>
                  <modules>
                    <module>service</module>
                  </modules>
                </project>
                """);
        Files.createDirectories(worktree.resolve("service/lib"));
        Files.writeString(worktree.resolve("service/pom.xml"), """
                <project>
                  <modules>
                    <module>lib</module>
                  </modules>
                </project>
                """);
        return ModuleDiscovery.discover(worktree);
    }
}
