package com.codecritic.report;

import com.codecritic.exec.BaselineExecution;
import com.codecritic.impact.MethodImpact;
import com.codecritic.impact.RiskBand;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete, immutable result of a verification run.
 *
 * @param toolVersion the version of the producing tool
 * @param repository the repository path that was analyzed
 * @param baseRef the base git reference
 * @param headRef the head git reference
 * @param generatedAt the ISO-8601 timestamp at which the report was generated
 * @param changedFiles the sorted, distinct project-relative paths of changed Java files
 * @param methodImpacts the per-callable impact assessments
 * @param riskScore the aggregate risk score from {@code 0} to {@code 100}
 * @param riskBand the aggregate risk band derived from the score
 * @param summary human-readable decision-summary lines
 * @param warnings human-readable warnings collected during the run
 * @param evidence the ordered evidence ledger backing the report
 * @param baseline the optional test execution results, or {@code null} when tests did not run
 */
public record VerificationReport(
        String toolVersion,
        String repository,
        String baseRef,
        String headRef,
        String generatedAt,
        List<String> changedFiles,
        List<MethodImpact> methodImpacts,
        int riskScore,
        RiskBand riskBand,
        List<String> summary,
        List<String> warnings,
        List<Evidence> evidence,
        BaselineExecution baseline
) {
    /**
     * Creates a report and defensively copies its list components so the instance
     * is immutable.
     */
    public VerificationReport {
        changedFiles = List.copyOf(changedFiles);
        methodImpacts = List.copyOf(methodImpacts);
        summary = List.copyOf(summary);
        warnings = List.copyOf(warnings);
        evidence = List.copyOf(evidence);
    }

    /**
     * Creates a builder initialized with the report metadata.
     *
     * @param toolVersion the version of the producing tool
     * @param repository the repository path that was analyzed
     * @param baseRef the base git reference
     * @param headRef the head git reference
     * @param generatedAt the ISO-8601 generation timestamp
     * @return a new builder
     */
    public static Builder builder(
            String toolVersion,
            String repository,
            String baseRef,
            String headRef,
            String generatedAt) {
        return new Builder(toolVersion, repository, baseRef, headRef, generatedAt);
    }

    /**
     * Fluent builder for {@link VerificationReport} instances.
     */
    public static final class Builder {
        private final String toolVersion;
        private final String repository;
        private final String baseRef;
        private final String headRef;
        private final String generatedAt;
        private List<String> changedFiles = List.of();
        private List<MethodImpact> methodImpacts = List.of();
        private int riskScore;
        private RiskBand riskBand = RiskBand.LOW;
        private List<String> summary = List.of();
        private List<String> warnings = List.of();
        private List<Evidence> evidence = List.of();
        private BaselineExecution baseline;

        private Builder(
                String toolVersion,
                String repository,
                String baseRef,
                String headRef,
                String generatedAt) {
            this.toolVersion = toolVersion;
            this.repository = repository;
            this.baseRef = baseRef;
            this.headRef = headRef;
            this.generatedAt = generatedAt;
        }

        /**
         * Sets the changed Java file paths.
         *
         * @param changedFiles the changed paths, or {@code null} for an empty list
         * @return this builder
         */
        public Builder changedFiles(List<String> changedFiles) {
            this.changedFiles = changedFiles == null ? List.of() : new ArrayList<>(changedFiles);
            return this;
        }

        /**
         * Sets the per-callable impact assessments.
         *
         * @param methodImpacts the impacts, or {@code null} for an empty list
         * @return this builder
         */
        public Builder methodImpacts(List<MethodImpact> methodImpacts) {
            this.methodImpacts = methodImpacts == null ? List.of() : new ArrayList<>(methodImpacts);
            return this;
        }

        /**
         * Sets the aggregate risk score and band.
         *
         * @param riskScore the risk score from {@code 0} to {@code 100}
         * @param riskBand the risk band derived from the score
         * @return this builder
         */
        public Builder risk(int riskScore, RiskBand riskBand) {
            this.riskScore = riskScore;
            this.riskBand = riskBand;
            return this;
        }

        /**
         * Sets the decision-summary lines.
         *
         * @param summary the summary lines, or {@code null} for an empty list
         * @return this builder
         */
        public Builder summary(List<String> summary) {
            this.summary = summary == null ? List.of() : new ArrayList<>(summary);
            return this;
        }

        /**
         * Sets the warnings collected during the run.
         *
         * @param warnings the warnings, or {@code null} for an empty list
         * @return this builder
         */
        public Builder warnings(List<String> warnings) {
            this.warnings = warnings == null ? List.of() : new ArrayList<>(warnings);
            return this;
        }

        /**
         * Sets the evidence ledger entries.
         *
         * @param evidence the evidence entries, or {@code null} for an empty list
         * @return this builder
         */
        public Builder evidence(List<Evidence> evidence) {
            this.evidence = evidence == null ? List.of() : new ArrayList<>(evidence);
            return this;
        }

        /**
         * Sets the optional test execution results.
         *
         * @param baseline the baseline execution, or {@code null} when tests did not run
         * @return this builder
         */
        public Builder baseline(BaselineExecution baseline) {
            this.baseline = baseline;
            return this;
        }

        /**
         * Creates the immutable report from the current builder state.
         *
         * @return the assembled verification report
         */
        public VerificationReport build() {
            return new VerificationReport(
                    toolVersion,
                    repository,
                    baseRef,
                    headRef,
                    generatedAt,
                    changedFiles,
                    methodImpacts,
                    riskScore,
                    riskBand,
                    summary,
                    warnings,
                    evidence,
                    baseline);
        }
    }
}
