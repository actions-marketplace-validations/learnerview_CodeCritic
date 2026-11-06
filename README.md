# CodeCritic

CodeCritic is a deterministic Java merge gate. It runs the tests affected by a
pull request, compares base and head failures, and fails only on the policy you
choose. The gate does not depend on an LLM.

The web dashboard is a secondary demo of the same deterministic analyzers plus
advisory LLM synthesis. It is useful for showing the analysis surface, but it is
not required for CI.

## What the gate does

1. Maps changed paths to Maven or Gradle modules.
2. Expands the selection through declared module dependencies, so a shared
   module change also runs dependent modules.
3. Falls back to the full suite when module selection is uncertain.
4. Runs base and head in isolated git worktrees inside Docker.
5. Compares per-test failures, so a passing suite that was already failing
   before the PR is not reported as a new regression.
6. Emits HTML or JSON evidence and returns a stable exit code.

The primary public interface is the `verification` CLI and the root
`action.yml`. The Spring server and Python agent are the demo/dashboard layer.

## Build and run the gate

```powershell
# The verification CLI does not need MongoDB, Redis, or Python.
mvn -B -pl verification -am package
java -jar verification\target\codecritic-verification.jar `
  --repo . --base main --head HEAD `
  --run-tests --select modules --fail-on new-failure `
  --format json --out codecritic-report.json
```

Without `--run-tests`, the CLI produces a callable-level impact report only.
Docker is a hard requirement for test execution; there is no host fallback.

| Policy | Fails when |
|---|---|
| `new-failure` (default) | head has a failure that base did not have |
| `any-failure` | head has any test failure |
| `high-risk` | risk band is `HIGH` and the head test result is healthy |
| `never` | never; always writes the report and exits `0` |

Exit codes: `0` pass/disabled, `1` gate failure, `2` usage/git/report-write
error. Use `--select all` to force the full suite.

Module selection falls back to the full suite for build-file changes,
root-level changes, single-module projects, unmapped changes, a selected module
missing at either revision, or a root module that depends on the selection. The
dependency graph is best-effort: use `--select all` when dynamic Gradle logic or
unresolved Maven properties make the graph uncertain.

Docker runs with `--network none` by default. Set
`CODECRITIC_SANDBOX_NETWORK=bridge` only when Maven or Gradle must download
dependencies; build scripts are untrusted and bridge networking permits outbound
traffic.

Run the full build gate:

```powershell
mvn -B clean verify
```

This compiles both modules, runs all tests, applies the JaCoCo threshold, and
fails on Javadoc warnings for public types and members. The `java-server`
integration tests need MongoDB on `localhost:27017`; without it, start just the
database with `docker compose up -d mongo`, or build only the CLI with
`mvn -B -pl verification package`. Generated API docs are written to
`java-server/target/reports/apidocs` and `verification/target/reports/apidocs`.

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

See [examples/github-actions/codecritic-gate.yml](examples/github-actions/codecritic-gate.yml)
for a complete workflow and [examples/github-actions/README.md](examples/github-actions/README.md)
for inputs and security notes.

## How it works

- [Analysis algorithms](docs/analysis-algorithms.md): callable diff, impact and
  risk scoring, module discovery, reverse dependency closure, failure
  comparison, Docker sandboxing, gate decisions.
- [Architecture](docs/architecture.md): service boundaries, verification flow,
  concurrency, idempotent jobs, observability.
- [CLI and API reference](docs/api.md): options, exit codes, HTTP routes.
- [Known limitations](docs/known-limitations.md): what is heuristic and what
  falls back to the full suite.
- [Benchmark](docs/benchmark.md): full-suite vs module-aware runtime harness.

## Run the dashboard demo

`docker-compose.yml` starts the Java server, Python agent, MongoDB, and Redis
with the development profile and local demo credentials:

```bash
docker compose up --build
curl http://localhost:8080/health
curl http://localhost:8000/health
```

Open `http://localhost:8080` for the dashboard. The demo account is `admin` /
`admin`; these values are accepted only because Compose sets
`SPRING_PROFILES_ACTIVE=dev`. Supply `GROQ_API_KEY` or `OPENAI_API_KEY` to enable
LLM synthesis; without either key, AI routes degrade to deterministic output.

The Java service calls the Python agent only through its internal
`PythonAgentClient`. Set `PYTHON_AGENT_TOKEN` to the same random secret in both
services; Python review endpoints fail closed without it. For local-only runs
you can set `ALLOW_INSECURE_AGENT=1`, but never on a reachable deployment. User
JWTs are accepted by Java and are never used as Python service credentials.

```bash
docker compose down
```
