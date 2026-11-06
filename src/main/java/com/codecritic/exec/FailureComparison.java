package com.codecritic.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares the failed tests observed at a base revision and a head revision.
 *
 * @param newFailures tests that fail at the head revision but not at the base
 * @param fixedFailures tests that fail at the base revision but not at the head
 * @param persistentFailures tests that fail at both revisions
 */
public record FailureComparison(
        List<TestFailure> newFailures,
        List<TestFailure> fixedFailures,
        List<TestFailure> persistentFailures
) {
    /**
     * Creates immutable copies of the failure collections.
     */
    public FailureComparison {
        newFailures = List.copyOf(newFailures);
        fixedFailures = List.copyOf(fixedFailures);
        persistentFailures = List.copyOf(persistentFailures);
    }

    /**
     * Compares the failures of two suite results by test identifier.
     *
     * <p>If either result is {@code null}, an empty comparison is returned.
     *
     * @param base result from the base revision
     * @param head result from the head revision
     * @return the new, fixed, and persistent failures
     */
    public static FailureComparison between(TestSuiteResult base, TestSuiteResult head) {
        if (base == null || head == null) {
            return new FailureComparison(List.of(), List.of(), List.of());
        }

        Map<String, TestFailure> baseFailures = byId(base.failedTests());
        Map<String, TestFailure> headFailures = byId(head.failedTests());

        List<TestFailure> newFailures = new ArrayList<>();
        for (Map.Entry<String, TestFailure> entry : headFailures.entrySet()) {
            if (!baseFailures.containsKey(entry.getKey())) {
                newFailures.add(entry.getValue());
            }
        }

        List<TestFailure> fixedFailures = new ArrayList<>();
        for (Map.Entry<String, TestFailure> entry : baseFailures.entrySet()) {
            if (!headFailures.containsKey(entry.getKey())) {
                fixedFailures.add(entry.getValue());
            }
        }

        List<TestFailure> persistentFailures = new ArrayList<>();
        for (Map.Entry<String, TestFailure> entry : headFailures.entrySet()) {
            if (baseFailures.containsKey(entry.getKey())) {
                persistentFailures.add(entry.getValue());
            }
        }

        return new FailureComparison(newFailures, fixedFailures, persistentFailures);
    }

    /**
     * Returns whether the head revision introduced any failures.
     *
     * @return {@code true} if at least one new failure was found
     */
    public boolean hasNewFailures() {
        return !newFailures.isEmpty();
    }

    private static Map<String, TestFailure> byId(List<TestFailure> failures) {
        Map<String, TestFailure> result = new LinkedHashMap<>();
        for (TestFailure failure : failures) {
            result.putIfAbsent(failure.id(), failure);
        }
        return result;
    }
}
