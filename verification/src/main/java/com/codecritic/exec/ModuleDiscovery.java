package com.codecritic.exec;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers Maven and Gradle modules and their dependency relationships.
 *
 * <p>Maven modules are read from {@code pom.xml} files and Gradle projects are
 * inferred from {@code settings.gradle} or {@code settings.gradle.kts} include
 * declarations. All parsing failures are reported as warnings on the resulting
 * {@link ModulePlan} rather than thrown.
 */
public final class ModuleDiscovery {

    private static final int MAX_MAVEN_DEPTH = 20;
    private static final Pattern GRADLE_INCLUDE = Pattern.compile(
            "\\binclude\\b\\s*(?:\\(([^)]*)\\)|([^\\r\\n]*))");
    private static final Pattern GRADLE_QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern GRADLE_PROJECT = Pattern.compile(
            "\\bproject\\s*\\(\\s*(?:path\\s*[:=]\\s*)?['\"]([^'\"]+)['\"]\\s*\\)");

    private ModuleDiscovery() {
    }

    /**
     * Detects the build tool at the worktree root and discovers its modules.
     *
     * @param worktree project root to inspect
     * @return the discovered modules, dependency graph, and warnings; a plan with
     *         build tool {@code none} is returned when no supported project is found
     */
    public static ModulePlan discover(Path worktree) {
        if (Files.isRegularFile(worktree.resolve("pom.xml"))) {
            return maven(worktree);
        }
        if (Files.isRegularFile(worktree.resolve("settings.gradle"))
                || Files.isRegularFile(worktree.resolve("settings.gradle.kts"))
                || Files.isRegularFile(worktree.resolve("build.gradle"))
                || Files.isRegularFile(worktree.resolve("build.gradle.kts"))) {
            return gradle(worktree);
        }
        return new ModulePlan(
                "none",
                List.of(),
                List.of("No supported Maven or Gradle project found."));
    }

    private static ModulePlan maven(Path worktree) {
        List<String> warnings = new ArrayList<>();
        List<ProjectModule> modules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        add(ProjectModule.root(), modules, seen);
        collectMaven(worktree, worktree, modules, seen, warnings, 0);
        return new ModulePlan(
                "maven",
                modules,
                warnings,
                mavenDependencies(worktree, modules, warnings));
    }

    private static void collectMaven(
            Path repository,
            Path projectDirectory,
            List<ProjectModule> modules,
            Set<String> seen,
            List<String> warnings,
            int depth) {
        if (depth > MAX_MAVEN_DEPTH) {
            warnings.add("Maven module nesting exceeded " + MAX_MAVEN_DEPTH + " levels at "
                    + repository.relativize(projectDirectory));
            return;
        }

        Path pom = projectDirectory.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return;
        }

        for (String declared : readMavenModules(pom, warnings)) {
            Path resolved = projectDirectory.resolve(declared).normalize();
            if (!resolved.startsWith(repository)) {
                warnings.add("Ignoring Maven module outside the repository: " + declared);
                continue;
            }
            String relative = repository.relativize(resolved).toString().replace('\\', '/');
            try {
                ProjectModule module = new ProjectModule(relative);
                add(module, modules, seen);
                collectMaven(repository, resolved, modules, seen, warnings, depth + 1);
            } catch (IllegalArgumentException e) {
                warnings.add("Ignoring unsafe Maven module path: " + declared);
            }
        }
    }

    private static List<String> readMavenModules(Path pom, List<String> warnings) {
        try {
            Document document = readDocument(pom);
            Element modules = directChild(document.getDocumentElement(), "modules");
            if (modules == null) {
                return List.of();
            }

            List<String> result = new ArrayList<>();
            NodeList children = modules.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE || !"module".equals(child.getNodeName())) {
                    continue;
                }
                String value = child.getTextContent();
                if (value != null && !value.isBlank()) {
                    result.add(value.strip());
                }
            }
            return result;
        } catch (Exception e) {
            warnings.add("Unable to read Maven modules from " + pom + ": " + e.getMessage());
            return List.of();
        }
    }

    private static Document readDocument(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private static ModuleGraph mavenDependencies(
            Path worktree,
            List<ProjectModule> modules,
            List<String> warnings) {
        Map<String, MavenPom> poms = new LinkedHashMap<>();
        for (ProjectModule module : modules) {
            Path directory = module.isRoot() ? worktree : worktree.resolve(module.path());
            Path pom = directory.resolve("pom.xml");
            if (!Files.isRegularFile(pom)) {
                continue;
            }
            try {
                poms.put(module.path(), readMavenPom(pom));
            } catch (Exception e) {
                warnings.add("Unable to read Maven metadata from " + pom + ": " + e.getMessage());
            }
        }

        MavenPom root = poms.get(".");
        String rootGroupId = root == null ? null : firstNonBlank(root.groupId(), root.parentGroupId());
        String rootVersion = root == null ? null : firstNonBlank(root.version(), root.parentVersion());
        Map<String, String> rootProperties = root == null ? Map.of() : root.properties();

        Map<String, String> coordinates = new LinkedHashMap<>();
        List<MavenModule> effectiveModules = new ArrayList<>();
        for (Map.Entry<String, MavenPom> entry : poms.entrySet()) {
            String path = entry.getKey();
            MavenPom pom = entry.getValue();
            Map<String, String> properties = new HashMap<>(rootProperties);
            properties.putAll(pom.properties());

            String groupId = firstNonBlank(pom.groupId(), pom.parentGroupId(), rootGroupId);
            String version = firstNonBlank(pom.version(), pom.parentVersion(), rootVersion);
            properties.put("project.groupId", groupId == null ? "" : groupId);
            properties.put("project.version", version == null ? "" : version);
            properties.put("project.artifactId", pom.artifactId() == null ? "" : pom.artifactId());
            properties.put(
                    "project.parent.groupId",
                    pom.parentGroupId() == null ? "" : pom.parentGroupId());
            properties.put(
                    "project.parent.version",
                    pom.parentVersion() == null ? "" : pom.parentVersion());

            groupId = resolve(groupId, properties);
            version = resolve(version, properties);
            String artifactId = resolve(pom.artifactId(), properties);
            properties.put("project.groupId", groupId == null ? "" : groupId);
            properties.put("project.version", version == null ? "" : version);

            if (groupId == null || groupId.isBlank()
                    || artifactId == null || artifactId.isBlank()) {
                warnings.add("Ignoring Maven project without groupId/artifactId: " + path);
                continue;
            }

            String coordinate = groupId + ":" + artifactId;
            String previous = coordinates.putIfAbsent(coordinate, path);
            if (previous != null && !previous.equals(path)) {
                warnings.add("Duplicate Maven coordinates " + coordinate
                        + " in " + previous + " and " + path);
            }

            List<MavenDependency> dependencies = new ArrayList<>(pom.dependencies());
            if (root != null && !".".equals(path)) {
                dependencies.addAll(root.dependencies());
            }
            effectiveModules.add(new MavenModule(path, coordinate, properties, dependencies));
        }

        ModuleGraph.Builder graph = ModuleGraph.builder();
        for (MavenModule module : effectiveModules) {
            for (MavenDependency dependency : module.dependencies()) {
                String groupId = resolve(dependency.groupId(), module.properties());
                String artifactId = resolve(dependency.artifactId(), module.properties());
                if (groupId == null || groupId.isBlank()
                        || artifactId == null || artifactId.isBlank()) {
                    continue;
                }
                String target = coordinates.get(groupId + ":" + artifactId);
                if (target != null) {
                    graph.addDependency(target, module.path());
                }
            }
        }
        return graph.build();
    }

    private static MavenPom readMavenPom(Path pom) throws Exception {
        Document document = readDocument(pom);
        Element project = document.getDocumentElement();
        Element parent = directChild(project, "parent");

        Map<String, String> properties = new LinkedHashMap<>();
        Element propertiesElement = directChild(project, "properties");
        if (propertiesElement != null) {
            NodeList children = propertiesElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    properties.put(child.getNodeName(), child.getTextContent().strip());
                }
            }
        }

        return new MavenPom(
                directText(project, "groupId"),
                directText(project, "artifactId"),
                directText(project, "version"),
                parent == null ? null : directText(parent, "groupId"),
                parent == null ? null : directText(parent, "version"),
                properties,
                readMavenDependencies(project));
    }

    private static List<MavenDependency> readMavenDependencies(Element project) {
        Element dependencies = directChild(project, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        List<MavenDependency> result = new ArrayList<>();
        NodeList children = dependencies.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"dependency".equals(child.getNodeName())) {
                continue;
            }
            Element dependency = (Element) child;
            String groupId = directText(dependency, "groupId");
            String artifactId = directText(dependency, "artifactId");
            if (groupId != null && !groupId.isBlank()
                    && artifactId != null && !artifactId.isBlank()) {
                result.add(new MavenDependency(groupId, artifactId));
            }
        }
        return List.copyOf(result);
    }

    private static String directText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? null : child.getTextContent().strip();
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (value == null) {
            return null;
        }
        String resolved = value;
        for (int pass = 0; pass < 10; pass++) {
            String next = resolved;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String replacement = entry.getValue() == null ? "" : entry.getValue();
                next = next.replace("${" + entry.getKey() + "}", replacement);
            }
            if (next.equals(resolved)) {
                break;
            }
            resolved = next;
        }
        return resolved;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static ModulePlan gradle(Path worktree) {
        List<String> warnings = new ArrayList<>();
        List<ProjectModule> modules = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        add(ProjectModule.root(), modules, seen);

        Path settings = Files.isRegularFile(worktree.resolve("settings.gradle.kts"))
                ? worktree.resolve("settings.gradle.kts")
                : worktree.resolve("settings.gradle");
        if (!Files.isRegularFile(settings)) {
            return new ModulePlan(
                    "gradle",
                    modules,
                    warnings,
                    gradleDependencies(worktree, modules, warnings));
        }

        try {
            String content = Files.readString(settings, StandardCharsets.UTF_8);
            Matcher include = GRADLE_INCLUDE.matcher(content);
            while (include.find()) {
                String expression = include.group(1) != null ? include.group(1) : include.group(2);
                Matcher quoted = GRADLE_QUOTED.matcher(expression == null ? "" : expression);
                while (quoted.find()) {
                    String projectPath = quoted.group(1);
                    String relative = (projectPath.startsWith(":") ? projectPath.substring(1) : projectPath)
                            .replace(':', '/');
                    try {
                        ProjectModule module = new ProjectModule(relative);
                        add(module, modules, seen);
                    } catch (IllegalArgumentException e) {
                        warnings.add("Ignoring unsafe Gradle module path: " + projectPath);
                    }
                }
            }
        } catch (Exception e) {
            warnings.add("Unable to read Gradle modules from " + settings + ": " + e.getMessage());
        }

        return new ModulePlan(
                "gradle",
                modules,
                warnings,
                gradleDependencies(worktree, modules, warnings));
    }

    private static ModuleGraph gradleDependencies(
            Path worktree,
            List<ProjectModule> modules,
            List<String> warnings) {
        Set<String> knownPaths = new LinkedHashSet<>();
        for (ProjectModule module : modules) {
            knownPaths.add(module.path());
        }

        ModuleGraph.Builder graph = ModuleGraph.builder();
        for (ProjectModule module : modules) {
            Path directory = module.isRoot() ? worktree : worktree.resolve(module.path());
            Path buildFile = Files.isRegularFile(directory.resolve("build.gradle.kts"))
                    ? directory.resolve("build.gradle.kts")
                    : directory.resolve("build.gradle");
            if (!Files.isRegularFile(buildFile)) {
                continue;
            }
            try {
                String content = Files.readString(buildFile, StandardCharsets.UTF_8);
                Matcher projects = GRADLE_PROJECT.matcher(content);
                while (projects.find()) {
                    String dependency = gradleModulePath(projects.group(1));
                    if (dependency == null) {
                        continue;
                    }
                    if (!knownPaths.contains(dependency)) {
                        warnings.add("Ignoring Gradle dependency on unknown project: "
                                + projects.group(1));
                        continue;
                    }
                    graph.addDependency(dependency, module.path());
                }
            } catch (Exception e) {
                warnings.add("Unable to read Gradle dependencies from " + buildFile + ": "
                        + e.getMessage());
            }
        }
        return graph.build();
    }

    private static String gradleModulePath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }
        String normalized = projectPath.replace('\\', '/').strip();
        if (normalized.equals(":") || normalized.equals("./")) {
            return ".";
        }
        while (normalized.startsWith(":")) {
            normalized = normalized.substring(1);
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String path = normalized.replace(':', '/');
        return path.isBlank() ? "." : path;
    }

    private static void add(ProjectModule module, List<ProjectModule> modules, Set<String> seen) {
        if (seen.add(module.path())) {
            modules.add(module);
        }
    }

    private static Element directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private record MavenDependency(String groupId, String artifactId) {
    }

    private record MavenPom(
            String groupId,
            String artifactId,
            String version,
            String parentGroupId,
            String parentVersion,
            Map<String, String> properties,
            List<MavenDependency> dependencies
    ) {
        private MavenPom {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }
    }

    private record MavenModule(
            String path,
            String coordinate,
            Map<String, String> properties,
            List<MavenDependency> dependencies
    ) {
    }
}
