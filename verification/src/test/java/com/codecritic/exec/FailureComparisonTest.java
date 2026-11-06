package com.codecritic.exec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailureComparisonTest {

    @Test
    void classifiesNewFixedAndPersistentFailures() {
        TestFailure persistent = failure("PersistentTest", "fails");
        TestFailure fixed = failure("FixedTest", "fails");
        TestFailure introduced = failure("NewTest", "fails");

        TestSuiteResult base = result("base", ExecutionStatus.TEST_FAILURES, persistent, fixed);
        TestSuiteResult head = result("head", ExecutionStatus.TEST_FAILURES, persistent, introduced);

        FailureComparison comparison = FailureComparison.between(base, head);

        assertThat(comparison.newFailures()).extracting(TestFailure::id)
                .containsExactly("NewTest#fails");
        assertThat(comparison.fixedFailures()).extracting(TestFailure::id)
                .containsExactly("FixedTest#fails");
        assertThat(comparison.persistentFailures()).extracting(TestFailure::id)
                .containsExactly("PersistentTest#fails");
        assertThat(comparison.hasNewFailures()).isTrue();
    }

    @Test
    void reportsNoNewFailuresWhenOnlyPreExistingOnesRemain() {
        TestFailure persistent = failure("PersistentTest", "fails");

        FailureComparison comparison = FailureComparison.between(
                result("base", ExecutionStatus.TEST_FAILURES, persistent),
                result("head", ExecutionStatus.TEST_FAILURES, persistent));

        assertThat(comparison.hasNewFailures()).isFalse();
    }

    private static TestFailure failure(String className, String testName) {
        return new TestFailure(className, testName, "boom");
    }

    private static TestSuiteResult result(
            String revision,
            ExecutionStatus status,
            TestFailure... failures) {
        return TestSuiteResult.builder(revision, "maven", "mvn test", status)
                .counts(failures.length, failures.length, 0, 0)
                .failedTests(List.of(failures))
                .build();
    }
}
