# Changelog

## 0.1.0

Current source line. No tagged release has been published yet.

- Java callable diff with caller, affected-test, complexity, and risk evidence.
- Maven and Gradle module discovery with reverse-dependency expansion.
- Full-suite fallback for build-file, root-level, unmapped, single-module, and
  missing-module changes.
- Differential base/head test execution in isolated Docker sandboxes.
- Gate policies: `new-failure`, `any-failure`, `high-risk`, and `never`.
- Documented exit codes: `0` pass, `1` gate failure, `2` usage/Git/report error.
- Self-contained HTML and machine-readable JSON reports.
- Candidate-test classification: `F2P`, `P2F`, `F2F`, `FLAKY`, and
  `INCONCLUSIVE`.
- Composite GitHub Action with a restricted sandbox and artifact-ready report
  path.
- CLI, architecture, algorithm, limitation, benchmark, and security
  documentation.
