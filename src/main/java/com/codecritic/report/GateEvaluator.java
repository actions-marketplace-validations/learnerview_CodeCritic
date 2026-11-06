package com.codecritic.report;

import com.codecritic.exec.BaselineExecution;
import com.codecritic.exec.ExecutionStatus;
import com.codecritic.exec.FailureComparison;
import com.codecritic.exec.TestFailure;
import com.codecritic.exec.TestSuiteResult;
import com.codecritic.impact.RiskBand;

/**
 * Evaluates a {@link VerificationReport} against a {@link GatePolicy} to decide
 * whether a run should fail.
 */
public final class GateEvaluator {

    private GateEvaluator() {
    }

    /**
     * Evaluates a verification report against a gate policy.
     * <p>
     * A {@code null} policy is treated as {@link GatePolicy#NEVER}. Setup
     * failures, missing head results, unsupported projects, unusable head
     * results, head test failures, failures without a usable comparison, and
     * high risk are considered according to the selected policy.
     *
     * @param report the verification report to evaluate
     * @param policy the gate policy, or {@code null} to disable the gate
     * @return the resulting gate decision
     */
    public static GateDecision evaluate(VerificationReport report, GatePolicy policy) {
        if (policy == null || policy == GatePolicy.NEVER) {
            return GateDecision.pass("gate disabled");
        }

        BaselineExecution baseline = report.baseline();
        if (baseline != null && baseline.setupError() != null) {
            return GateDecision.fail("test setup failed: " + baseline.setupError());
        }
        if (baseline != null && baseline.head() == null) {
            return GateDecision.fail("head tests did not run");
        }

        if (baseline == null) {
            return policy == GatePolicy.HIGH_RISK
                    ? riskDecision(report)
                    : GateDecision.pass("tests were not requested");
        }

        TestSuiteResult head = baseline.head();
        if (head.status() == ExecutionStatus.UNSUPPORTED) {
            return GateDecision.fail("no supported Maven or Gradle project was found");
        }
        if (head.status() == ExecutionStatus.COMPILE_FAILURE
                || head.status() == ExecutionStatus.BUILD_FAILURE
                || head.status() == ExecutionStatus.TIMEOUT
                || head.status() == ExecutionStatus.ERROR) {
            return GateDecision.fail("head did not produce a usable test result: " + head.status());
        }

        boolean headFailed = hasHeadFailures(head);

        if (policy == GatePolicy.HIGH_RISK) {
            if (headFailed) {
                return GateDecision.fail("head has test failures");
            }
            return riskDecision(report);
        }

        FailureComparison comparison = baseline.failureComparison();
        if (policy == GatePolicy.NEW_FAILURE) {
            if (comparison != null && comparison.hasNewFailures()) {
                TestFailure first = comparison.newFailures().get(0);
                return GateDecision.fail("new test failure: " + first.id());
            }
            if (headFailed) {
                if (baseline.base() == null) {
                    return GateDecision.fail(
                            "head reported test failures without a base result");
                }
                if (head.failedTests().isEmpty()) {
                    return GateDecision.fail(
                            "head reported test failures without identifiable failed tests");
                }
                if (comparison == null) {
                    return GateDecision.fail(
                            "head reported test failures without a comparable baseline");
                }
            }
            return GateDecision.pass("no new test failures");
        }

        if (headFailed) {
            return GateDecision.fail("head has test failures");
        }
        return GateDecision.pass("head tests passed");
    }

    private static boolean hasHeadFailures(TestSuiteResult head) {
        return head.status() == ExecutionStatus.TEST_FAILURES || head.hasTestFailures();
    }

    private static GateDecision riskDecision(VerificationReport report) {
        return report.riskBand() == RiskBand.HIGH
                ? GateDecision.fail("risk is HIGH (" + report.riskScore() + "/100)")
                : GateDecision.pass("risk is " + report.riskBand());
    }
}
