# Known Limitations & Future Work

This document records acknowledged limitations. Most are intentional trade-offs rather than defects, but they are worth keeping in mind — especially if you are evaluating the project or being asked about them.

## Analysis accuracy

- **Heuristics are approximate by design.** The deterministic bug detector is line-based (`PatternBugDetector`) and the complexity fallback is token-based. They can produce false positives (e.g., a string or comment that looks like `/ 0`) and false negatives (e.g., a division by zero involving a variable that cannot be resolved statically). The comment/string stripping in the fallback reduces, but does not eliminate, false positives.
- **Cognitive complexity is a crude estimate** (`max(1, cyclomatic / 2)`), not Sonar-style cognitive complexity. Fine for a demo signal, not a precise quality gate.
- **Test scaffolds are templates, not proven tests.** `JavaParserTestGenerator` outputs a deterministic JUnit 5 skeleton with placeholder literals; parameters it cannot map default to `null`. The LLM-generated suite (`/generate-tests`) is richer but still not guaranteed to compile without edits for complex generics or dependencies.

## Verification-specific heuristics

- **Caller counts are name-based.** `ProjectIndex` indexes method-call and method-reference names; it does not resolve overloads, inheritance, interfaces, reflection, or generated call sites. Treat caller counts as a prioritization signal, not reachability proof.
- **Affected-test detection is a name/path heuristic.** A test is linked when it calls the same method name or mentions the changed class/method in its source. Tests that exercise behavior indirectly, through helpers or integration boundaries, may be missed.
- **Only `.java` changes are analyzed for impact.** Non-Java changed paths are still collected; build-file changes force the safe full-suite fallback, and other paths can select their containing module, but no callable-level impact is inferred from them.
- **Differential execution is Maven/Gradle oriented.** Other build systems, test frameworks that do not emit Surefire/Gradle XML results, and module layouts without standard `pom.xml`/`settings.gradle` declarations are outside the model.
- **Module dependency closure is best-effort.** Maven closure is derived from declared `<dependency>` coordinates; Gradle closure is derived from `project(':module')` references in module build files. Dynamic dependencies, generated builds, unresolved property expressions, non-standard layouts, and composite builds may be missed, so use `--select all` when the graph is uncertain.
- **Docker is required for execution.** There is no host fallback. The sandbox defaults to no network; projects whose builds need remote dependencies must explicitly opt in with `CODECRITIC_SANDBOX_NETWORK=bridge`.
- **Gate policies are coarse.** `--fail-on` compares failed-test identities between base and head; it does not classify severity, ownership, or flakiness across repeated CI runs.
- **Reports are advisory.** A risk score or a green `P2P` candidate run does not prove correctness; it records the evidence the tool could collect.

## Type and code parsing edge cases

- **Fully-qualified generics / complex types** in test generation use primitive placeholder literals. Types like custom objects, arrays, or generics default to `null`, so generated tests may need manual argument values.
- **`is_java_code`** in the Python agent is a heuristic. It now accepts common Java tokens (`;`, `{`, `return`, `@Override`, `new`, …) so short snippets pass, but pathological inputs can still be misclassified.

## Language and input coverage

- **The deterministic analyzers are Java-only.** `JavaParserComplexityAnalyzer`, `PatternBugDetector`/SpotBugs and `JavaParserTestGenerator` parse and reason about Java source; the `/review` snippet endpoint rejects non-Java input via `is_java_code`.
- **Repository analysis is best-effort multi-language.** Files with extensions `.java`, `.py`, `.js`, `.ts`, `.tsx` are collected (nothing else), but deterministic complexity and bug findings are only produced for `.java`; other languages are counted and summarised but not statically analysed.
- **Repository file selection is size-biased.** Files are sorted largest-first and truncated to the effective `maxFiles`, so a repo with many small files may have relevant files skipped. Generated/vendored directories (`node_modules`, `.git`, `target`, `dist`, `build`, `.venv`, `venv`) are excluded, but no ignore-file (`.gitignore`, `.ccignore`) is honoured.
- **Only github.com URLs are accepted** (`_parse_github_repo`), and the repository is fetched as a zip archive over the network — offline or non-GitHub sources are unsupported.

## Repository size and payload caps

- **`MAX_REPO_FILES`** caps how many files are analysed per request (default `30`); the HTTP layer rejects `maxFiles` outside `1..MAX_REPO_FILES` with `422` and the value is clamped a second time in `build_repository_context`.
- **`MAX_REQUEST_BYTES`** (default `1500000`) caps every Python-agent request body (`413`); the Java multipart limits are `max-file-size: 1MB` / `max-request-size: 2MB`.
- **Prompt budget is finite.** Repository analysis feeds at most ~32,000 characters of source excerpts to the LLM and truncates each snippet to 4,000 characters, so very large repos are summarised from a sample, not the whole tree.

## External LLM dependency

- **AI features require a third-party LLM.** `/review`, `/generate-tests`, `/analyze-repository` and `/debug` call Groq (primary) then OpenAI (fallback). With no API key configured, these degrade gracefully to deterministic output (raw templates/findings) rather than failing.
- **Provider rate limits and outages apply.** Transient failures (network, `429`, `5xx`) are retried up to 3 times with exponential backoff (tenacity), then the provider fallback runs; sustained provider trouble still degrades output to the deterministic path.
- **LLM output is non-deterministic and advisory.** See the sample in `docs/sample-analysis-report.md`: generated reviews/tests can be wrong and must be human-reviewed. The merge gate never uses LLM output for its verdict.

## Configuration (default-deny)

- **The server refuses to start without real secrets outside `dev`/`test`.** A blank/insecure `AUTH_PASSWORD` or the committed demo `JWT_SECRET` aborts startup (see `DeploymentProfile`, `DefaultUserInitializer`, `JwtTokenProvider`). This is intentional fail-safe behavior, but it means a fresh `java -jar` with no profile and no env vars does **not** start — run with `SPRING_PROFILES_ACTIVE=dev` for local demos or supply real secrets.

## Concurrency & storage

- **LRU SpotBugs cache** evicts the least-recently-used entry at 256 entries. This bounds memory but means hot files churning through the cache will occasionally recompile. This is a deliberate trade-off, not a cap on correctness. The `size()/hits()/misses()` counters let you observe it.
- **No persistent storage for user data.** `User` documents persist in MongoDB, but no usage history or review storage exists between runs.

## Security posture

- **Untrusted-source scope is explicit.** The Java service accepts arbitrary
  Java source for static analysis; SpotBugs compilation uses a temporary
  directory, while differential build/test execution is isolated in the
  Docker sandbox. The internal Python routes require the shared service token,
  but the Java API still needs normal network and authentication controls.
- **Sandbox network access is disabled by default.** The Docker sandbox runs
  with `--network none` unless `CODECRITIC_SANDBOX_NETWORK=bridge` is set.
  Bridge mode is needed only when Maven or Gradle must download dependencies;
  build scripts are executable untrusted input, so allowing network access
  permits outbound traffic from those scripts. Candidate-test injection
  rejects symlinked path components and symlink targets so it cannot write
  outside the checked-out worktree.
- **Stateless JWT** means tokens are not revocable before expiry, and there is no refresh-token rotation.
- **SpotBugs is best-effort.** When the `spotbugs` CLI is available (see
  `java-server/Dockerfile`), `/api/bugs` combines the pattern detector with
  SpotBugs analysis. If it is unavailable the detector still fails gracefully
  and returns only pattern-based findings rather than failing the request.

## Observability gaps

- Structured JSON logs and metrics exist (see `docs/architecture.md`), but there is no request correlation across the queue lifecycle yet, and no tracing (e.g., OpenTelemetry) across the Java → Python → LLM chain.

## Future work

- Precise Sonar-style cognitive complexity.
- Persistent review history and per-user dashboards.
- Revocable/rotating tokens and optional PKCE if a browser login flow is added.
- LRU-aware cache metrics reporting and possibly a Redis-backed distributed cache.
