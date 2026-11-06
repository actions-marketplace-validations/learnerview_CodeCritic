# GitHub Actions Example

`codecritic-gate.yml` shows a complete pull-request gate. Copy it to
`.github/workflows/` in the repository that should be checked.

## Required workflow setup

1. Check out full history so the base commit is resolvable:

   ```yaml
   - uses: actions/checkout@v4
     with:
       fetch-depth: 0
   ```

2. Point `base` at the PR base SHA and `head` at the PR head SHA. Do not use a
   shallow checkout with `HEAD~1` on a merge commit.

3. Pin the action to a tag or SHA so consumer workflows are reproducible:

   ```yaml
   - uses: learnerview/CodeCritic@<tag-or-sha>
   ```

4. Docker must be available on the runner. GitHub-hosted Ubuntu runners include
   it. The action does not fall back to running tests on the host.

## Inputs that matter

| Input | Use |
|---|---|
| `base` | Required base ref or commit SHA |
| `head` | Head ref or commit SHA; defaults to `HEAD` |
| `select` | `modules` (changed modules plus dependents) or `all` |
| `fail-on` | `new-failure`, `any-failure`, `high-risk`, or `never` |
| `sandbox-network` | `none` by default; set `bridge` when Maven/Gradle must download dependencies |
| `test-timeout` | Seconds per test process; default `600` |
| `output` | Report path; defaults to `codecritic-report.<format>` |

The `report` output is the absolute path to the generated file, so a later step
can upload it with `actions/upload-artifact`.

## Security notes

- The default sandbox has no network. `bridge` allows dependency downloads but
  also gives untrusted build scripts outbound access. Use a dependency cache or
  a pre-warmed image when possible.
- Fork pull requests do not receive repository secrets by default. This action
  does not need secrets; it only needs the checked-out repository and Docker.
- The report is evidence, not a proof of correctness. Keep the gate as the
  merge authority and review the evidence before overriding it.

## Validate the action locally

The end-to-end fixture builds a two-module Maven repository, makes a `lib` change
that only breaks an `app` test, and asserts that the gate selects `app,lib` and
exits `1`:

```bash
mvn -B package -DskipTests
CODECRITIC_SANDBOX_NETWORK=bridge bash scripts/e2e-module-gate.sh
```

See [../../docs/benchmark.md](../../docs/benchmark.md) for the runtime benchmark.
