# CLI Reference

CodeCritic ships as a shaded, executable jar. It does not require MongoDB, Redis,
or Python. Differential test execution requires Docker.

Build it from the repository root:

```bash
mvn -B package
```

The jar is `target/codecritic.jar`.

## Impact report

Without `--run-tests`, CodeCritic maps changed Java callables, callers, tests,
and a risk band:

```bash
java -jar target/codecritic.jar \
  --repo . --base main --head HEAD \
  --format html --out codecritic-report.html
```

## Candidate test

Inject one Java test into both revisions and compare the result:

```bash
java -jar target/codecritic.jar \
  --repo . --base main --head HEAD \
  --test-source src/test/java/com/example/FixTest.java \
  --run-tests --format json --out codecritic-report.json
```

Candidate-test mode records `F2P`, `P2F`, `F2F`, `FLAKY`, or `INCONCLUSIVE`
and exits `0` when the report is written. It is not a gate mode.

## Merge gate

Run the modules affected by the change and fail only on a new failure:

```bash
java -jar target/codecritic.jar \
  --repo . --base main --head HEAD \
  --run-tests --select modules --fail-on new-failure \
  --format json --out codecritic-report.json
```

`--select modules` discovers Maven `<module>` entries or Gradle `include(...)`
entries, maps changed paths to the deepest matching module, expands the
selection through declared module dependencies, and runs only those modules in
both worktrees.

It falls back to the full suite for:

- build-file changes
- root-level changes
- single-module projects
- unmapped changes
- a selected module missing at either revision
- a root module that depends on the selection

Use `--select all` when dynamic Gradle logic or unresolved Maven properties make
the dependency graph uncertain.

## Options

| Option | Default | Meaning |
|---|---|---|
| `--repo` | `.` | Git repository path |
| `--base` | required | Base ref, for example `main` or `HEAD~1` |
| `--head` | `HEAD` | Head ref |
| `--format` | `html` | `html` or `json` |
| `--out` | `codecritic-report.<format>` | Report path |
| `--run-tests` | off | Run the existing test suite in both worktrees inside Docker |
| `--select` | `modules` | With `--run-tests`: `modules` or `all` |
| `--fail-on` | `new-failure` | With `--run-tests`: `new-failure`, `any-failure`, `high-risk`, or `never` |
| `--test-source` | none | Candidate Java test injected into both revisions; implies test execution and disables gating |
| `--test-path` | inferred | Project-relative destination for the candidate test |
| `--stability-check` / `--no-stability-check` | on | Repeat `F2P`/`P2F` candidates and report flakiness |
| `--test-timeout` | `600` | Seconds per test process |
| `--max-output-bytes` | `200000` | Captured stdout/stderr cap per stream |

## Gate policies

| Policy | Fails when |
|---|---|
| `new-failure` | head has a failure that base did not have, or head failures cannot be compared with a base result |
| `any-failure` | head has any test failure |
| `high-risk` | head has test failures, or risk band is `HIGH` |
| `never` | never; writes the report and exits `0` |

## Exit codes

- `0`: report written and the selected gate passed, or gating was disabled.
- `1`: the gate failed. Depending on `--fail-on`, this means a new test failure,
  any head test failure, an uncomparable head failure, a `HIGH` risk band, or a
  broken test result (setup/compile/build/timeout/error/unsupported project).
- `2`: argument, Git, or report-write error.

## JSON report shape

Top-level fields:

| Field | Meaning |
|---|---|
| `toolVersion` | Producing CodeCritic version |
| `repository` | Repository path analyzed |
| `baseRef`, `headRef` | Resolved input refs |
| `generatedAt` | ISO-8601 report timestamp |
| `changedFiles` | Sorted changed Java paths |
| `methodImpacts` | Per-callable impact assessments |
| `riskScore`, `riskBand` | Aggregate risk from `0` to `100` and band |
| `summary` | Human-readable decision summary |
| `warnings` | Non-fatal notices |
| `evidence` | Evidence ledger with stable ids, paths, and lines |
| `baseline` | Optional test execution results |

When tests run, `baseline` includes `base`, `head`, `selection`,
`failureComparison`, and optional `differential` results. A setup problem is
recorded in `baseline.setupError` and produces exit code `1`.

The report is written before the gate verdict is applied. JSON is
machine-readable; HTML is a self-contained reviewer view.

## Docker sandbox

Docker is mandatory when tests run. There is no host fallback. The sandbox drops
capabilities, disables privilege escalation, limits PIDs/memory/CPU, uses a
read-only root filesystem with a writable `/tmp`, and defaults to
`--network none`.

Set `CODECRITIC_SANDBOX_NETWORK=bridge` only when Maven or Gradle must download
dependencies. Build scripts are executable untrusted input, so bridge mode
permits outbound traffic from those scripts. See [../SECURITY.md](../SECURITY.md)
for the full threat model.

## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker is not installed, not running, or not reachable by the current user | Start Docker and confirm `docker info` succeeds |
| Dependency resolution fails with `--network none` | The build needs remote artifacts | Use a pre-warmed image or dependency cache; only then opt into `CODECRITIC_SANDBOX_NETWORK=bridge` |
| `base ref` cannot be resolved | Shallow checkout or missing remote history | Fetch the base revision, for example `git fetch --unshallow`, or pass an available SHA |
| No usable test results | The build did not emit Surefire or Gradle XML | Confirm the test task writes XML results and that tests actually ran |
| Gate passed with zero tests | No test XML or no tests were observed | Treat the report warning as a gap; add a separate CI check when tests are required |
| The full suite ran unexpectedly | A build-file, root-level, unmapped, single-module, or missing-module change forced the full-suite fallback | Inspect `baseline.selection` and the report warnings; this is intentional |
| Exit code `2` | Argument, Git, or report-write failure | Fix the invocation or output path; no gate verdict was produced |
| Exit code `2` after changing sandbox settings | `CODECRITIC_SANDBOX_NETWORK` is not `none` or `bridge` | Set it to `none` or `bridge`, or unset it for the default |
| Exit code `1` with healthy tests | A `high-risk` policy matched, or a setup, compile, build, timeout, or unsupported-project error was recorded | Inspect the gate reason, `baseline.setupError`, and the captured output |
| A report contains machine-specific paths | Reports record the paths the tool observed | Treat reports as internal artifacts and review before sharing |
