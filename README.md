# CodeCritic

[![CI](https://github.com/learnerview/CodeCritic/actions/workflows/ci.yml/badge.svg)](https://github.com/learnerview/CodeCritic/actions/workflows/ci.yml)

CodeCritic is a Java merge gate. It runs the tests affected by a pull request,
compares base and head failures, and applies the policy you choose. It does not
use an LLM.

Use it when a full test suite is too slow for every pull request and you want a
pass/fail signal based on observed tests. CodeCritic maps changed paths to Maven
or Gradle modules, expands the selection through declared module dependencies,
and falls back to the full suite when the mapping is uncertain or unsupported.

**Status:** `0.1.0`, pre-1.0. The CLI and action are usable; release automation
is not configured yet.

## What it does

1. Maps changed paths to Maven or Gradle modules.
2. Expands the selection through declared module dependencies, so a shared
   module change also runs dependent modules.
3. Falls back to the full suite for build-file changes, root-level changes,
   single-module projects, unmapped changes, or a selected module missing at
   either revision.
4. Runs base and head in separate git worktrees, with test commands executed in
   Docker.
5. Compares per-test failures, so a suite that was already failing before the
   pull request is not reported as a new regression.
6. Emits HTML or JSON evidence and returns the documented exit code.

## Requirements

- Java 21
- Maven 3.9 or newer
- Docker for any run that executes tests
- A Git repository with both revisions available locally

The CLI does not need MongoDB, Redis, Python, or a server process.

## Compatibility

CI runs on Ubuntu with Java 21 and Docker. The CLI itself is plain Java, but test
execution uses Docker bind mounts and Unix-style container commands. On Windows,
use Docker Desktop with the repository drive shared; the end-to-end and benchmark
shell scripts need Git Bash or WSL. Other platforms are best-effort.

## Build

```bash
mvn -B package
```

The shaded CLI is `target/codecritic.jar`.

## Run the gate

```bash
java -jar target/codecritic.jar \
  --repo . --base main --head HEAD \
  --run-tests --select modules --fail-on new-failure \
  --format json --out codecritic-report.json
```

Without `--run-tests`, CodeCritic produces a callable-level impact report only.

| Policy | Fails when |
|---|---|
| `new-failure` (default) | head has a failure that base did not have, or head failures cannot be compared with a base result |
| `any-failure` | head has any test failure |
| `high-risk` | head has test failures, or risk band is `HIGH` |
| `never` | never; writes the report and exits `0` |

Exit codes:

- `0`: report written and the selected gate passed, or gating was disabled.
- `1`: gate failure.
- `2`: argument, Git, or report-write error.

The gate evaluates observed results. If a run produces no test XML or zero tests,
the report includes a warning, but the gate can still pass. Add a separate CI
check when a repository must run at least one test.

Use `--select all` to force the full suite.

Docker runs with `--network none` by default. Set
`CODECRITIC_SANDBOX_NETWORK=bridge` only when Maven or Gradle must download
dependencies; build scripts are untrusted and bridge networking permits outbound
traffic. See [Security](SECURITY.md) for the threat model and residual risks.

## Use the GitHub Action

The root `action.yml` is a composite action. The consumer workflow must fetch
enough history to resolve the base commit:

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
- uses: learnerview/CodeCritic@0.1.0
  with:
    base: ${{ github.event.pull_request.base.sha }}
    head: ${{ github.event.pull_request.head.sha }}
    select: modules
    fail-on: new-failure
    sandbox-network: bridge
```

Pin the action to a release tag or commit SHA so runs are reproducible. See
[examples/github-actions/codecritic-gate.yml](examples/github-actions/codecritic-gate.yml)
for a full example workflow and
[examples/github-actions/README.md](examples/github-actions/README.md) for
inputs and security notes.

## Documentation

- [CLI and report reference](docs/cli.md): options, exit codes, report shape.
- [Analysis algorithms](docs/analysis-algorithms.md): callable diff, impact and
  risk scoring, module discovery, reverse dependency closure, failure
  comparison, Docker sandboxing, gate decisions.
- [Architecture](docs/architecture.md): verification flow, isolation, and
  failure handling.
- [Known limitations](docs/known-limitations.md): what is heuristic and what
  falls back to the full suite.
- [Benchmark](docs/benchmark.md): full-suite versus module-aware runtime
  harness.
- [Security](SECURITY.md): sandbox threat model, reporting, and operating
  guidance.
- [Changelog](CHANGELOG.md): current feature set and release status.

## Verify

```bash
mvn -B clean verify
```

This runs the test suite, applies the Javadoc warning gate, and builds the
shaded CLI. Generated API docs are written to `target/reports/apidocs`.

The end-to-end fixture builds a two-module Maven repository, changes `lib`, and
asserts that the gate selects `app` and exits `1` on the dependent failure:

```bash
mvn -B package -DskipTests
CODECRITIC_SANDBOX_NETWORK=bridge bash scripts/e2e-module-gate.sh
```

## Scope

CodeCritic is a merge gate, not a linter or a code-review chatbot. It does not
prove correctness. It records the tests it ran, the failures it observed, and
the policy decision it made. Results depend on the target project's build, test
environment, and test stability. Differential execution is limited to Maven and
Gradle projects that emit Surefire or Gradle XML test results.

## License

MIT
