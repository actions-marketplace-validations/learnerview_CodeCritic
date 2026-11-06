# CodeCritic

[![CI](https://github.com/learnerview/CodeCritic/actions/workflows/ci.yml/badge.svg)](https://github.com/learnerview/CodeCritic/actions/workflows/ci.yml)

CodeCritic is a deterministic Java merge gate. It runs the tests affected by a
pull request, compares base and head failures, and fails only on the policy you
choose. It does not use an LLM.

Use it when a full test suite is too slow for every pull request but you still
need a trustworthy pass/fail decision. CodeCritic maps changed paths to Maven or
Gradle modules, expands the selection through module dependencies, and falls
back to the full suite whenever the mapping is uncertain.

## What it does

1. Maps changed paths to Maven or Gradle modules.
2. Expands the selection through declared module dependencies, so a shared
   module change also runs dependent modules.
3. Falls back to the full suite for build-file changes, root-level changes,
   single-module projects, unmapped changes, or a selected module missing at
   either revision.
4. Runs base and head in isolated git worktrees inside Docker.
5. Compares per-test failures, so a suite that was already failing before the
   pull request is not reported as a new regression.
6. Emits HTML or JSON evidence and returns a stable exit code.

## Requirements

- Java 21
- Maven 3.9 or newer
- Docker for any run that executes tests
- A Git repository with both revisions available locally

The CLI does not need MongoDB, Redis, Python, or a server process.

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
| `new-failure` (default) | head has a failure that base did not have |
| `any-failure` | head has any test failure |
| `high-risk` | risk band is `HIGH` and the head test result is healthy |
| `never` | never; writes the report and exits `0` |

Exit codes:

- `0`: report written and the selected gate passed, or gating was disabled.
- `1`: gate failure.
- `2`: argument, Git, or report-write error.

Use `--select all` to force the full suite.

Docker runs with `--network none` by default. Set
`CODECRITIC_SANDBOX_NETWORK=bridge` only when Maven or Gradle must download
dependencies; build scripts are untrusted and bridge networking permits outbound
traffic.

## Use the GitHub Action

The root `action.yml` is a composite action. The consumer workflow must fetch
enough history to resolve the base commit:

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
- uses: learnerview/CodeCritic@main
  with:
    base: ${{ github.event.pull_request.base.sha }}
    head: ${{ github.event.pull_request.head.sha }}
    select: modules
    fail-on: new-failure
    sandbox-network: bridge
```

Pin the action to a release tag or commit SHA for production use. See
[examples/github-actions/codecritic-gate.yml](examples/github-actions/codecritic-gate.yml)
for a complete workflow and
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
the policy decision it made. Differential execution is limited to Maven and
Gradle projects that emit Surefire or Gradle XML test results.

## License

MIT
