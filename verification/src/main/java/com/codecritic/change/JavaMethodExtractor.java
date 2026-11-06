package com.codecritic.change;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts methods and constructors from a JavaParser compilation unit and
 * derives stable identities, readable signatures, bodies, and visibility flags.
 */
public final class JavaMethodExtractor {

    private JavaMethodExtractor() {
    }

    /**
     * Extracts every method and constructor declared in the compilation unit.
     *
     * @param unit the parsed compilation unit to inspect
     * @return extracted callable information keyed by {@link #key(CallableDeclaration)}
     */
    public static Map<String, MethodInfo> extract(CompilationUnit unit) {
        Map<String, MethodInfo> methods = new LinkedHashMap<>();
        unit.findAll(MethodDeclaration.class).forEach(method -> {
            MethodInfo info = describe(method, false);
            methods.put(info.key(), info);
        });
        unit.findAll(ConstructorDeclaration.class).forEach(constructor -> {
            MethodInfo info = describe(constructor, true);
            methods.put(info.key(), info);
        });
        return methods;
    }

    /**
     * Builds the stable identity for a callable.
     *
     * @param callable the method or constructor to identify
     * @return the fully qualified class name, method name, and parameter types
     */
    public static String key(CallableDeclaration<?> callable) {
        String name = callable instanceof ConstructorDeclaration ? "<init>" : callable.getNameAsString();
        return className(callable) + "#" + name + "(" + parameterTypes(callable) + ")";
    }

    /**
     * Resolves the fully qualified name of the type declaring the callable,
     * including any enclosing types.
     *
     * @param callable the callable whose declaring type is required
     * @return the package-qualified type name, or an empty string when unavailable
     */
    public static String className(CallableDeclaration<?> callable) {
        List<String> names = new ArrayList<>();
        Node current = callable.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) {
                names.add(type.getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        Collections.reverse(names);
        String packageName = callable.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(declaration -> declaration.getNameAsString())
                .orElse("");
        String localName = String.join(".", names);
        return packageName.isBlank() ? localName : packageName + "." + localName;
    }

    /**
     * Returns the simple name of the nearest type declaring the callable.
     *
     * @param callable the callable whose declaring type is required
     * @return the simple type name, or an empty string when unavailable
     */
    public static String simpleClassName(CallableDeclaration<?> callable) {
        Node current = callable.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) {
                return type.getNameAsString();
            }
            current = current.getParentNode().orElse(null);
        }
        return "";
    }

    /**
     * Determines whether a callable is effectively part of the public or
     * protected API.
     * <p>
     * A callable is considered API-visible when it is public or protected, when
     * it is a non-private member of an interface, and when all enclosing types
     * are public.
     *
     * @param callable the callable to classify
     * @return {@code true} if the callable is externally visible
     */
    public static boolean isPublicApi(CallableDeclaration<?> callable) {
        boolean visible = callable.isPublic() || callable.isProtected();
        if (!visible) {
            TypeDeclaration<?> nearestType = null;
            Node current = callable.getParentNode().orElse(null);
            while (current != null) {
                if (current instanceof TypeDeclaration<?> type) {
                    nearestType = type;
                    break;
                }
                current = current.getParentNode().orElse(null);
            }
            if (nearestType instanceof ClassOrInterfaceDeclaration type
                    && type.isInterface()
                    && !callable.isPrivate()) {
                visible = true;
            }
        }
        if (!visible) {
            return false;
        }

        Node current = callable.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type && !type.isPublic()) {
                return false;
            }
            current = current.getParentNode().orElse(null);
        }
        return true;
    }

    /**
     * Builds a normalized textual signature for the callable.
     * <p>
     * The signature includes sorted modifiers, type parameters, the return type
     * for methods, the parameter type list, and declared thrown exceptions.
     *
     * @param callable the callable to describe
     * @return the normalized signature text
     */
    public static String signature(CallableDeclaration<?> callable) {
        List<String> parts = new ArrayList<>();
        String modifiers = callable.getModifiers().stream()
                .map(modifier -> modifier.getKeyword().asString())
                .sorted()
                .collect(Collectors.joining(" "));
        if (!modifiers.isBlank()) {
            parts.add(modifiers);
        }
        if (!callable.getTypeParameters().isEmpty()) {
            parts.add("<" + callable.getTypeParameters().stream()
                    .map(Node::toString)
                    .collect(Collectors.joining(", ")) + ">");
        }
        if (callable instanceof MethodDeclaration method) {
            parts.add(method.getTypeAsString());
        }
        parts.add(callable.getNameAsString() + "(" + parameterTypes(callable) + ")");
        if (!callable.getThrownExceptions().isEmpty()) {
            parts.add("throws " + callable.getThrownExceptions().stream()
                    .map(Node::toString)
                    .collect(Collectors.joining(", ")));
        }
        return String.join(" ", parts);
    }

    /**
     * Returns the comma-separated parameter type list of the callable.
     *
     * @param callable the callable whose parameters are required
     * @return the parameter types in declaration order, with varargs expressed as {@code ...}
     */
    public static String parameterTypes(CallableDeclaration<?> callable) {
        return callable.getParameters().stream()
                .map(JavaMethodExtractor::parameterType)
                .collect(Collectors.joining(", "));
    }

    private static String parameterType(Parameter parameter) {
        String type = parameter.getTypeAsString();
        return parameter.isVarArgs() ? type + "..." : type;
    }

    /**
     * Returns the textual body of the callable.
     *
     * @param callable the callable whose body is required
     * @return the body text, or an empty string for abstract or native methods
     */
    public static String body(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration method) {
            return method.getBody().map(Node::toString).orElse("");
        }
        if (callable instanceof ConstructorDeclaration constructor) {
            return constructor.getBody().toString();
        }
        return "";
    }

    /**
     * Returns the one-based line where the callable begins.
     *
     * @param callable the callable to locate
     * @return the starting line, or {@code 0} when source position information is absent
     */
    public static int line(CallableDeclaration<?> callable) {
        return callable.getBegin().map(position -> position.line).orElse(0);
    }

    private static MethodInfo describe(CallableDeclaration<?> callable, boolean constructor) {
        String methodName = constructor ? "<init>" : callable.getNameAsString();
        return new MethodInfo(
                key(callable),
                className(callable),
                methodName,
                signature(callable),
                body(callable),
                line(callable),
                isPublicApi(callable),
                CyclomaticComplexity.of(callable));
    }
}
