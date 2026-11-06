# Known Limitations

This document records acknowledged limitations. Most are intentional trade-offs
rather than defects, and they are the first things to discuss when evaluating the
gate.

## Impact analysis

- **Caller counts are name-based.** `ProjectIndex` indexes method-call and
  method-reference names; it does not resolve overloads, inheritance, interfaces,
  reflection, or generated call sites. Treat caller counts as a prioritization
  signal, not reachability proof.
- **Affected-test detection is a name/path heuristic.** A test is linked when it
  calls the same method name or mentions the changed class/method in its source.
  Tests that exercise behavior indirectly, through helpers or integration
  boundaries, may be missed.
- **Only `.java` changes are analyzed for impact.** Non-Java changed paths are
  still collected; build-file changes force the safe full-suite fallback, and
  other paths can select their containing module, but no callable-level impact is
  inferred from them.
- **Risk scoring is a heuristic.** A `HIGH` band means the change deserves
  attention, not that it is defective.

## Module selection

- **Maven/Gradle only.** Other build systems, test frameworks that do not emit
  Surefire/Gradle XML results, and module layouts without standard
  `pom.xml`/`settings.gradle` declarations are outside the model.
- **Dependency closure is best-effort.** Maven closure is derived from declared
  `<dependency>` coordinates; Gradle closure is derived from `project(':module')`
  references in module build files. Dynamic dependencies, generated builds,
  unresolved property expressions, non-standard layouts, and composite builds may
  be missed. Use `--select all` when the graph is uncertain.
- **Selection prefers safety over speed.** It falls back to the full suite for
  build-file changes, root-level changes, single-module projects, unmapped
  changes, missing modules, and dependent root modules. A fallback is not a
  failure, but it means the module-aware saving is zero for that run.

## Differential execution

- **Docker is required for execution.** There is no host fallback. The sandbox
  defaults to no network; projects whose builds need remote dependencies must
  explicitly opt in with `CODECRITIC_SANDBOX_NETWORK=bridge`.
- **Bridge networking is a security trade-off.** Build scripts are executable
  untrusted input, so bridge mode permits outbound traffic from those scripts.
  Prefer a dependency cache or a pre-warmed image.
- **One candidate test per run.** Candidate mode injects a single Java test and
  compares its verdict across revisions.
- **Stability checks are bounded.** `F2P` and `P2F` results are repeated once by
  default. A different repeat verdict becomes `FLAKY`, but repeated runs cannot
  prove the absence of flakiness.
- **Test discovery depends on XML output.** A project that runs tests but does
  not produce Surefire/Gradle XML is classified as having no usable results.

## Gate behavior

- **Gate policies are coarse.** `--fail-on` compares failed-test identities
  between base and head; it does not classify severity, ownership, or flakiness
  across historical CI runs.
- **A broken head run fails the gate.** Missing head results, compile failures,
  build failures, timeouts, unsupported projects, and execution errors fail even
  under `new-failure`, because the tool cannot prove the absence of a regression.
- **Reports are advisory.** A green run does not prove correctness; it records
  the evidence the tool could collect.
- **No automatic test discovery from the change.** CodeCritic runs existing
  suites or one injected candidate test. It does not generate tests.

## Repository and change coverage

- **Java-focused change analysis.** The diff engine parses Java callables. Other
  languages can still select modules and run their tests, but their callables are
  not indexed for impact.
- **Git history must be available locally.** Both refs must resolve in the local
  repository. Shallow checkouts can make the base ref unavailable.
- **Malformed Java is skipped with a warning.** One unparseable file cannot hide
  the rest of the change set, but its callables are not analyzed.

## Operational surface

- **No server, database, or queue.** This is deliberate. CodeCritic is a CLI and
  a composite GitHub Action, and it keeps no state between runs.
- **No release automation yet.** The action example should be pinned to a tag or
  commit SHA; `@main` is only a development convenience.
- **Runtime depends on the host and Docker.** Wall time includes worktree setup,
  container startup, dependency resolution, both test runs, and report
  generation. Benchmarks are environment-specific and are not committed as
  universal numbers.

## Future work

- Precise caller resolution for overloads and interfaces.
- More build systems and test-result formats.
- Multi-candidate test injection.
- Historical flakiness tracking across CI runs.
