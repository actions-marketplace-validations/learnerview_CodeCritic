# Module Gate Benchmark

The benchmark compares two real executions over the same base/head pair:

- `--select all`: full Maven/Gradle test suite in both worktrees.
- `--select modules`: changed modules plus transitive dependents in both worktrees.

Both runs use `--fail-on never`, so a regression still produces a report and does
not abort the benchmark. Wall time includes git worktree setup, Docker sandbox
startup, dependency resolution, both test runs, and report generation.

## Run in GitHub Actions

`CodeCritic Benchmark` is manual:

```bash
gh workflow run benchmark.yml \
  -f repository=https://github.com/OpenFeign/feign.git \
  -f base=<base-sha> \
  -f head=<head-sha>
```

The workflow builds the CLI, runs the harness, and uploads `benchmark.md` as the
`codecritic-benchmark` artifact. Run it once per repository. Use a base/head
pair where a non-root module changed; otherwise module selection correctly falls
back to the full suite and there is no benchmark signal.

## Run locally

Docker and Java 21 are required:

```bash
mvn -B package -DskipTests
CODECRITIC_SANDBOX_NETWORK=bridge \
  bash scripts/benchmark-module-gate.sh \
  https://github.com/assertj/assertj.git <base-sha> <head-sha>
```

The script clones the repository into a temporary directory, resolves both
revisions, runs both scopes, and prints a Markdown row. Set
`CODECRITIC_BENCHMARK_TIMEOUT` to change the per-process timeout.

## Output

| Column | Meaning |
|---|---|
| Selected scope | Modules selected by the module-aware run, or `all` on fallback |
| Full ms | Wall time for `--select all` |
| Module ms | Wall time for `--select modules` |
| Saved % | `(full - modules) / full * 100` |
| Exit full/module | CLI exit codes; `--fail-on never` should produce `0/0` |
| Note | Setup error or "module fallback; no benchmark signal" |

## Candidate repositories

Use pull-request commits or a known module-local change:

- `https://github.com/OpenFeign/feign.git`
- `https://github.com/assertj/assertj.git`
- `https://github.com/GoogleContainerTools/jib.git`

Results are intentionally not committed here: test time depends on CPU, network,
dependency cache state, and the chosen commit pair. The artifact from a workflow
run is the source of truth.

## Interpretation limits

- The full-suite baseline runs the same differential base/head flow, not a single
  conventional `mvn test`, so the comparison isolates test selection rather than
  git-worktree overhead.
- A module-local change can still run most of the suite when many modules depend
  on the changed module.
- Missing or dynamic dependency declarations can under-select; use
  `--select all` when the dependency graph is uncertain.
