package com.codecritic.change;

/**
 * Describes one method or constructor extracted from a parsed Java source file.
 *
 * @param key the stable identity of the callable in the form {@code class#name(parameters)}
 * @param className the fully qualified name of the declaring type, including enclosing types
 * @param methodName the simple method name, or {@code <init>} for a constructor
 * @param signature the normalized signature including modifiers, type parameters, return type, and throws clause
 * @param body the textual body of the callable, or an empty string when no body exists
 * @param line the one-based line number where the callable begins, or {@code 0} when unknown
 * @param publicApi whether the callable is effectively part of the public or protected API
 * @param cyclomaticComplexity the McCabe-style complexity score, or {@code 0} when no body exists
 */
public record MethodInfo(
        String key,
        String className,
        String methodName,
        String signature,
        String body,
        int line,
        boolean publicApi,
        int cyclomaticComplexity
) {
}
