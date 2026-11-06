package com.codecritic.report;

import com.codecritic.exec.BaselineExecution;
import com.codecritic.exec.ExecutionStatus;
import com.codecritic.exec.FailureComparison;
import com.codecritic.exec.TestFailure;
import com.codecritic.exec.TestSelection;
import com.codecritic.exec.TestSuiteResult;
import com.codecritic.impact.RiskBand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GateEvaluatorTest {

    @Test
    void failsOnANewTestFailure() {
        TestSuiteResult base = result(ExecutionStatus.SUCCESS);
        TestSuiteResult head = result(ExecutionStatus.TEST_FAILURES, failure("NewTest"));

        GateDecision decision = GateEvaluator.evaluate(
                report(RiskBand.LOW, base, head),
                GatePolicy.NEW_FAILURE);

        assertThat(decision.failed()).isTrue();
        assertThat(decision.reason()).contains("NewTest#fails");
    }

    @Test
    void allowsAPreExistingFailureWhenGatingOnlyOnNewFailures() {
        TestSuiteResult base = result(ExecutionStatus.TEST_FAILURES, failure("OldTest"));
        TestSuiteResult head = result(ExecutionStatus.TEST_FAILURES, failure("OldTest"));

        assertThat(GateEvaluator.evaluate(
                report(RiskBand.LOW, base, head),
                GatePolicy.NEW_FAILURE).failed()).isFalse();
        assertThat(GateEvaluator.evaluate(
                report(RiskBand.LOW, base, head),
                GatePolicy.ANY_FAILURE).failed()).isTrue();
    }

    @Test
    void failsOnCompilationAndUnsupportedResults() {
        TestSuiteResult success = result(ExecutionStatus.SUCCESS);

        assertThat(GateEvaluator.evaluate(
                report(RiskBand.LOW, success, result(ExecutionStatus.COMPILE_FAILURE)),
                GatePolicy.NEW_FAILURE).failed()).isTrue();
        assertThat(GateEvaluator.evaluate(
                report(RiskBand.LOW, success, result(ExecutionStatus.UNSUPPORTED)),
                GatePolicy.NEW_FAILURE).failed()).isTrue();
        assertThat(GateEvaluator.evaluate(
                report(RiskBand.LOW, success, result(ExecutionStatus.COMPILE_FAILURE)),
                GatePolicy.HIGH_RISK).failed()).isTrue();
    }

    @Test
    void appliesTheHighRiskPolicyAndNeverPolicy() {
        VerificationReport highRisk = report(RiskBand.HIGH, result(ExecutionStatus.SUCCESS), result(ExecutionStatus.SUCCESS));

        assertThat(GateEvaluator.evaluate(highRisk, GatePolicy.HIGH_RISK).failed()).isTrue();
        assertThat(GateEvaluator.evaluate(highRisk, GatePolicy.NEVER).failed()).isFalse();
    }

    private static VerificationReport report(
            RiskBand band,
            TestSuiteResult base,
            TestSuiteResult head) {
        FailureComparison comparison = FailureComparison.between(base, head);
        BaselineExecution baseline = BaselineExecution.completed(
                base,
                head,
                null,
                TestSelection.all(),
                comparison);
        return VerificationReport.builder("test", "repo", "base", "head", "now")
                .risk(band == RiskBand.HIGH ? 90 : 10, band)
                .baseline(baseline)
                .build();
    }

    private static TestFailure failure(String className) {
        return new TestFailure(className, "fails", "boom");
    }

    private static TestSuiteResult result(ExecutionStatus status, TestFailure... failures) {
        return TestSuiteResult.builder("HEAD", "maven", "mvn test", status)
                .counts(failures.length, failures.length, 0, 0)
                .failedTests(List.of(failures))
                .build();
    }
}
