package com.codecritic.change;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Indexes parsed Java sources by the simple names of the methods they call.
 * <p>
 * The index supports heuristic caller counts and affected-test discovery. Caller
 * relationships are name-based rather than symbol-resolved, so unrelated methods
 * that share a name are not distinguished.
 */
public final class ProjectIndex {

    private final Map<String, String> sources;
    private final Map<String, List<Caller>> callersByMethodName;

    private ProjectIndex(Map<String, String> sources, Map<String, List<Caller>> callersByMethodName) {
        this.sources = Map.copyOf(sources);
        this.callersByMethodName = Map.copyOf(callersByMethodName);
    }

    /**
     * Builds an index from project-relative paths to Java source text.
     * <p>
     * Sources that cannot be parsed are ignored.
     *
     * @param sources the Java sources keyed by project-relative path
     * @return a new index containing caller relationships for every parsable source
     */
    public static ProjectIndex from(Map<String, String> sources) {
        Map<String, List<Caller>> callers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String path = entry.getKey();
            boolean test = isTestPath(path);
            JavaSourceParser.parse(entry.getValue()).ifPresent(unit ->
                    indexUnit(unit, path, test, callers));
        }
        return new ProjectIndex(sources, callers);
    }

    /**
     * Counts distinct production callables that reference the given method name,
     * excluding the callable identified by {@code changedKey}.
     *
     * @param changedKey the key of the changed callable to exclude from the count
     * @param methodName the simple name of the referenced method
     * @return the number of distinct non-test caller callables
     */
    public int productionCallerCount(String changedKey, String methodName) {
        return (int) callersByMethodName.getOrDefault(methodName, List.of()).stream()
                .filter(caller -> !caller.test())
                .filter(caller -> !caller.callerKey().equals(changedKey))
                .map(Caller::callerKey)
                .distinct()
                .count();
    }

    /**
     * Finds test files that call the given method or mention the altered class.
     * <p>
     * The result combines indexed test callers with test sources whose text
     * contains the simple class name or the method name.
     *
     * @param className the class name whose simple form is searched for
     * @param methodName the simple name of the changed method
     * @return sorted, distinct project-relative paths of potentially affected tests
     */
    public List<String> affectedTests(String className, String methodName) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        Set<String> tests = new TreeSet<>();

        callersByMethodName.getOrDefault(methodName, List.of()).stream()
                .filter(Caller::test)
                .map(Caller::path)
                .forEach(tests::add);

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            if (!isTestPath(entry.getKey())) {
                continue;
            }
            String source = entry.getValue();
            if (source.contains(simpleName) || source.contains(methodName)) {
                tests.add(entry.getKey());
            }
        }
        return new ArrayList<>(tests);
    }

    /**
     * Returns the indexed Java sources.
     *
     * @return an unmodifiable map of project-relative path to Java source text
     */
    public Map<String, String> sources() {
        return sources;
    }

    private static void indexUnit(
            CompilationUnit unit,
            String path,
            boolean test,
            Map<String, List<Caller>> callers) {
        unit.findAll(MethodDeclaration.class).forEach(method ->
                indexCallable(method, path, test, callers));
        unit.findAll(ConstructorDeclaration.class).forEach(constructor ->
                indexCallable(constructor, path, test, callers));
    }

    private static void indexCallable(
            CallableDeclaration<?> callable,
            String path,
            boolean test,
            Map<String, List<Caller>> callers) {
        String callerKey = JavaMethodExtractor.key(callable);

        callable.findAll(MethodCallExpr.class).stream()
                .filter(call -> CallableScope.belongsTo(call, callable))
                .forEach(call -> addCaller(
                        callers,
                        call.getNameAsString(),
                        new Caller(path, callerKey, test, line(call))));

        callable.findAll(MethodReferenceExpr.class).stream()
                .filter(reference -> CallableScope.belongsTo(reference, callable))
                .forEach(reference -> addCaller(
                        callers,
                        reference.getIdentifier(),
                        new Caller(path, callerKey, test, line(reference))));
    }

    private static void addCaller(Map<String, List<Caller>> callers, String methodName, Caller caller) {
        callers.computeIfAbsent(methodName, ignored -> new ArrayList<>()).add(caller);
    }

    private static int line(com.github.javaparser.ast.Node node) {
        return node.getBegin().map(position -> position.line).orElse(0);
    }

    /**
     * Tests whether a path looks like a Java test source.
     *
     * @param path the project-relative path to inspect
     * @return {@code true} for test source directories or conventional test file suffixes
     */
    public static boolean isTestPath(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.contains("/src/test/java/")
                || normalized.endsWith("Test.java")
                || normalized.endsWith("Tests.java")
                || normalized.endsWith("IT.java");
    }

    /**
     * A callable that references a method at a particular source location.
     *
     * @param path the project-relative path of the calling source
     * @param callerKey the key of the calling callable
     * @param test whether the calling file is a test source
     * @param line the line number of the call, or {@code 0} when unknown
     */
    private record Caller(String path, String callerKey, boolean test, int line) {
    }
}
