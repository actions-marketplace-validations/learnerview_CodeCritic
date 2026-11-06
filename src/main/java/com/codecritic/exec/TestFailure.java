package com.codecritic.exec;

/**
 * Describes a single failed or errored test case.
 *
 * @param className fully qualified name of the class that declares the test
 * @param testName name of the failing test method or case
 * @param message captured failure message, normalized to a single line
 */
public record TestFailure(
        String className,
        String testName,
        String message
) {
    /**
     * Returns a stable identifier for the test that combines its class and test names.
     *
     * @return the identifier in the form {@code className#testName}
     */
    public String id() {
        return className + "#" + testName;
    }
}
