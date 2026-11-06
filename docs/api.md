# API Reference

These are the public Java endpoints exposed by CodeCritic. The Python agent is
an internal implementation detail and is never called directly by the browser.

## Java server (`:8080`)

All `/api/**` endpoints require a JWT except `/api/auth/*` and `/api/config`, which are public. `GET /health` and `/error` are also public.

### Auth

A service account is created at startup from `AUTH_USERNAME` / `AUTH_PASSWORD` (the `dev` profile uses `admin` / `admin`). You can also register your own account; passwords must be at least 8 characters. Then log in with those credentials:

```bash
# Create your account (returns a token too)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"me","password":"password123"}'

# Login → returns {"token":"<jwt>", ...}
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"me","password":"password123"}'

export TOKEN="<jwt>"
```

### Deterministic analysis

```bash
# Cyclomatic + cognitive complexity
curl -X POST http://localhost:8080/api/complexity \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"public class A { public int f(int x){ if(x>0){ return x; } return 0; } }"}'
# → {"cyclomaticComplexity":2,"cognitiveComplexity":1}

# Bug findings (pattern detector, best-effort SpotBugs when installed)
curl -X POST http://localhost:8080/api/bugs \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"public class A { public int f(int x){ return 10 / x; } }"}'
# → {"bugs":[{"type":"DivisionByZeroRisk","line":1,"message":"...","suggestion":"..."}]}

# JUnit 5 scaffold
curl -X POST http://localhost:8080/api/generate-test \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"className":"Calculator","methodName":"add","parameters":"int a, int b","code":"public class Calculator { public int add(int a, int b){ return a + b; } }"}'
```

### Async jobs (Redis/SimplyDone4J)

Each analysis endpoint has a `POST /api/jobs/*` variant returning `{"jobId":"<id>","status":"QUEUED"}`. Poll `GET /api/jobs/{id}` for the result.

```bash
curl -X POST http://localhost:8080/api/jobs/complexity \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"public class A { public int f(int x){ return x; } }"}'
curl http://localhost:8080/api/jobs/<id> -H "Authorization: Bearer $TOKEN"
```

Jobs are idempotent: the idempotency key is a **SHA-256 of the job type plus a canonical JSON encoding of the full payload** (map keys sorted at every nesting level, list order preserved), so identical submissions always map to the same key and distinct payloads cannot collide. Async jobs need the scheduler/Redis enabled on the instance; the sync endpoints above work without it. If the queue is at its configured depth limit, submission returns `503` with status `QUEUE_FULL` instead of running outside that limit. If the queue backend is unreachable, the request is transparently executed on a bounded in-memory fallback pool so the submit → poll contract still holds (see [docs/architecture.md](architecture.md)).

### Metrics

```bash
curl http://localhost:8080/api/metrics -H "Authorization: Bearer $TOKEN"
```

### Health / config

```bash
curl http://localhost:8080/health       # public
curl http://localhost:8080/api/config   # public UI-safe feature configuration
```

## Advisory review routes

These routes are handled by Java and then forwarded to the internal Python agent
with the service credential. Clients must use the Java base URL and a JWT.

```bash
# Review pipeline (deterministic analysis + LLM review)
curl -X POST http://localhost:8080/api/review \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"public class Calculator { public int divide(int a,int b){ return a/b; } }"}'

# Full LLM JUnit test suite
curl -X POST http://localhost:8080/api/generate-tests \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"public class Calculator { public int add(int a,int b){ return a+b; } }"}'

# GitHub repository analysis
curl -X POST http://localhost:8080/api/analyze-repository \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"repoUrl":"https://github.com/octocat/Hello-World","branch":null,"maxFiles":15,"githubToken":null}'

# Debug/diagnosis
curl -X POST http://localhost:8080/api/debug \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"language":"java","code":"public class A { int f(int x){ return 10/x; } }","errorLog":"ArithmeticException: / by zero"}'
```

The Python health and metrics endpoints are internal diagnostics, not part of
the public browser API.

A worked example of the `/analyze-repository` response format (deterministic metrics plus the LLM summary) is in [docs/sample-analysis-report.md](sample-analysis-report.md).

## Verification CLI

The verification engine ships as a shaded, executable jar. It is separate from
the HTTP server and never requires Mongo, Redis, or the Python agent.
Differential test execution requires Docker.

Build it from the repository root:

```bash
mvn -B -pl verification -am package
```

Map the callable-level impact of a change:

```bash
java -jar verification/target/codecritic-verification.jar \
  --repo . --base main --head HEAD --format html \
  --out codecritic-report.html
```

Run a candidate test against both revisions:

```bash
java -jar verification/target/codecritic-verification.jar \
  --repo . --base main --head HEAD \
  --test-source src/test/java/com/example/FixTest.java \
  --run-tests --format json --out codecritic-report.json
```

Run a merge gate over changed Maven/Gradle modules:

```bash
java -jar verification/target/codecritic-verification.jar \
  --repo . --base main --head HEAD \
  --run-tests --select modules --fail-on new-failure \
  --format json --out codecritic-report.json
```

`--select modules` discovers Maven `<module>` entries or Gradle `include(...)`
entries, maps changed paths to the deepest matching module, expands the
selection through declared module dependencies, and runs only those modules in
both worktrees. It falls back to the full suite for build-file changes,
root-level changes, single-module projects, unmapped changes, or a selected
module missing at either revision.

Options:

| Option | Default | Meaning |
|--------|---------|---------|
| `--repo` | `.` | Git repository path |
| `--base` | required | Base ref, for example `main` or `HEAD~1` |
| `--head` | `HEAD` | Head ref |
| `--format` | `html` | `html` or `json` |
| `--out` | `codecritic-report.<format>` | Report path |
| `--run-tests` | off | Run the existing test suite in both worktrees inside Docker |
| `--select` | `modules` | With `--run-tests`: `modules` (changed modules plus dependents) or `all` |
| `--fail-on` | `new-failure` | With `--run-tests`: `new-failure`, `any-failure`, `high-risk`, or `never` |
| `--test-source` | none | Candidate Java test injected into both revisions; implies test execution and disables gating |
| `--test-path` | inferred | Project-relative destination for the candidate test |
| `--stability-check` / `--no-stability-check` | on | Repeat `F2P`/`P2F` candidates and report flakiness |
| `--test-timeout` | `600` | Seconds per test process |
| `--max-output-bytes` | `200000` | Captured stdout/stderr cap per stream |

Exit codes:

- `0`: report written and the selected gate passed (or gating was disabled).
- `1`: the gate failed. Depending on `--fail-on`, this means a new test failure,
  any head test failure, a `HIGH` risk band, or a broken test result
  (setup/compile/build/timeout/error/unsupported project).
- `2`: argument, git, or report-write error.

`--test-source` is candidate-test mode, not gate mode: the report records
`F2P`/`P2F`/`F2F`/`FLAKY`/`INCONCLUSIVE` and exits `0` when the report is
written, so callers can decide how to use the verdict.

Docker is mandatory when tests run; the sandbox defaults to `--network none`,
with `CODECRITIC_SANDBOX_NETWORK=bridge` as the explicit opt-in for dependency
downloads.

## Limits you can tune on your own instance

- `MAX_REQUEST_BYTES` (default `1500000`) — payloads larger than this get `413`. This ceiling applies to every request body on the Python agent.
- Repository analysis accepts `maxFiles` from `1` through `MAX_REPO_FILES` (**default `30`**), defaulting to `15` when omitted; larger or invalid values are rejected with `422` before any GitHub work starts. Inside `build_repository_context` the value is clamped again with `max(1, min(maxFiles, MAX_REPO_FILES))`, and the response reports `maxFilesRequested` / `maxFilesApplied` / `filesDiscovered` / `analyzedFileCount` so the effective limit is never silent. The dashboard input mirrors this with `min="1" max="30" value="15"`.
- Rate limiting per IP: `RATE_LIMIT_REQUESTS` (default `60`) per `RATE_LIMIT_WINDOW_SECONDS` (default `60`) → `429` when exceeded.
- Which endpoints are public (`permitAllPaths` in `JwtProperties`) and how long tokens live (`JWT_EXPIRATION_MS`).

See the source configuration classes and [docs/known-limitations.md](known-limitations.md)
for request limits and intentional behavior.