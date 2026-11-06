package com.codecritic.change;

import java.util.List;

/**
 * Describes a detected change to a method or constructor between two revisions.
 *
 * @param path the project-relative path of the file containing the callable
 * @param key the stable identity of the callable in the form {@code class#name(parameters)}
 * @param className the fully qualified name of the declaring type
 * @param methodName the simple method name, or {@code <init>} for a constructor
 * @param kind the kind of change detected
 * @param baseSignature the signature at the base revision, or {@code null} when the callable was added
 * @param headSignature the signature at the head revision, or {@code null} when the callable was removed
 * @param publicApi whether the callable is effectively public API at either revision
 * @param baseComplexity the cyclomatic complexity at the base revision, or {@code 0} when absent
 * @param headComplexity the cyclomatic complexity at the head revision, or {@code 0} when absent
 * @param baseLine the one-based line at the base revision, or {@code 0} when absent
 * @param headLine the one-based line at the head revision, or {@code 0} when absent
 * @param details human-readable notes describing the change
 */
public record MethodChange(
        String path,
        String key,
        String className,
        String methodName,
        MethodChangeKind kind,
        String baseSignature,
        String headSignature,
        boolean publicApi,
        int baseComplexity,
        int headComplexity,
        int baseLine,
        int headLine,
        List<String> details
) {
    /**
     * Creates a change and defensively copies the detail list so the instance is immutable.
     */
    public MethodChange {
        details = List.copyOf(details);
    }

    /**
     * Returns the change in cyclomatic complexity from base to head.
     *
     * @return the head complexity minus the base complexity
     */
    public int complexityDelta() {
        return headComplexity - baseComplexity;
    }

    /**
     * Returns a readable name for the changed callable.
     *
     * @return the class name and method name separated by {@code #}
     */
    public String displayName() {
        return className + "#" + methodName;
    }
}
