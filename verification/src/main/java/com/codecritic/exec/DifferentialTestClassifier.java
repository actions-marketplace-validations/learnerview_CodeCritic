package com.codecritic.exec;

/**
 * Maps the execution statuses of a base and head run to a differential verdict.
 */
public final class DifferentialTestClassifier {

    private DifferentialTestClassifier() {
    }

    /**
     * Compares the base and head execution statuses to determine the candidate
     * test's transition.
     *
     * <p>Only the {@code SUCCESS} and {@code TEST_FAILURES} statuses produce a
     * definitive verdict; any other combination is {@link DifferentialVerdict#INCONCLUSIVE}.
     *
     * @param base result from the base revision, or {@code null}
     * @param head result from the head revision, or {@code null}
     * @return the differential verdict, never {@code null}
     */
    public static DifferentialVerdict classify(TestSuiteResult base, TestSuiteResult head) {
        if (base == null || head == null) {
            return DifferentialVerdict.INCONCLUSIVE;
        }

        ExecutionStatus baseStatus = base.status();
        ExecutionStatus headStatus = head.status();

        if (baseStatus == ExecutionStatus.SUCCESS && headStatus == ExecutionStatus.SUCCESS) {
            return DifferentialVerdict.P2P;
        }
        if (baseStatus == ExecutionStatus.TEST_FAILURES && headStatus == ExecutionStatus.SUCCESS) {
            return DifferentialVerdict.F2P;
        }
        if (baseStatus == ExecutionStatus.SUCCESS && headStatus == ExecutionStatus.TEST_FAILURES) {
            return DifferentialVerdict.P2F;
        }
        if (baseStatus == ExecutionStatus.TEST_FAILURES && headStatus == ExecutionStatus.TEST_FAILURES) {
            return DifferentialVerdict.F2F;
        }
        return DifferentialVerdict.INCONCLUSIVE;
    }
}
