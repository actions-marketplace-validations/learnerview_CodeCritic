# Analysis Algorithms

## 1. Change detection

`GitChangeSource` resolves both refs with `git rev-parse`, reads the changed paths
with `git diff --name-status -M -z`, and records both the Java files for
callable analysis and the complete changed-path list for test selection. Renames
are detected, additions/deletions/modifications are preserved, and the full head
source tree is loaded once through `git ls-tree` plus a batched blob reader.

`JavaDiffAnalyzer` parses both revisions with JavaParser and extracts methods and
constructors through `JavaMethodExtractor`. Each callable gets a stable key:

```text
<fully-qualified-class>#<method-name>(<parameter-types>)
```

Constructors use `<init>`. The key deliberately excludes the return type, so a
return-type-only change appears as a signature change rather than an add/remove
pair.

For each key present on either side:

| Situation | Change kind |
|-----------|-------------|
| Missing in base, present in head | `ADDED` |
| Present in base, missing in head | `REMOVED` |
| Signature text differs | `SIGNATURE_CHANGED` |
| Signature same, body text differs | `MODIFIED` |
| Both signature and body same | Not reported |

The diff records the old/new signature, line, public/protected visibility,
cyclomatic complexity, and human-readable details. A parse failure does not
abort the run: it produces a warning and skips that file, so one malformed Java
file cannot hide the rest of the change set.

## 2. Caller and test indexing (`ProjectIndex`)

`ProjectIndex` parses the head sources and indexes every `MethodCallExpr` and
`MethodReferenceExpr` by the **called method name**. Counts are name-based, not
resolved against a type hierarchy.

- A path is treated as a test when it contains `/src/test/java/`, or ends in
  `Test.java`, `Tests.java`, or `IT.java`.
- `productionCallerCount(key, methodName)` counts distinct non-test caller keys
  that reference that method name, excluding the changed callable itself.
- `affectedTests(className, methodName)` returns tests that call the method name
  **or** whose source text contains the class simple name or method name.

These are prioritization heuristics. Overloads, inherited methods, reflection,
generated code, and dynamic dispatch can all make the real caller set different
from the indexed set.

## 3. Risk scoring (`ImpactAnalyzer`)

Each changed callable gets a score capped at 100:

| Factor | Points |
|--------|--------|
| Callable removed | 25 |
| Signature changed | 20 |
| New callable | 10 |
| Body modified | 5 |
| Public/protected API | 25 |
| Complexity increase | `min(20, delta * 4)` |
| Heuristic production callers | `min(20, callers * 4)` |
| No affected tests detected | 15 |
| Public API with no callers and no tests | additional 10 |

Bands are **LOW** below 30, **MEDIUM** from 30 to 59, and **HIGH** at 60+. The
report's overall score is the maximum callable score, not an average, and
callables are sorted highest-risk first. Every factor is emitted as evidence
text, so a reviewer can see why a score was assigned instead of treating it as an
opaque number.

## 4. Module selection

`ModuleDiscovery` reads Maven `<module>` entries recursively and Gradle
`include(...)` statements from `settings.gradle`/`settings.gradle.kts`. It then
builds a reverse dependency graph from Maven `groupId:artifactId` coordinates and
Gradle `project(':module')` references.

`ModulePlan` maps each changed path to the deepest matching module, expands the
selection to transitive dependents, and runs those modules (`mvn -pl ... -am test`
or `:module:test`). It deliberately falls back to the full suite when:

- no module metadata exists
- only one module exists
- no changed path maps to a module
- a build file changed
- a root-level change was detected
- a dependent root module was selected
- a selected module is missing at either revision

`FailureComparison` compares failed-test identities between base and head, so a
gate can fail only on newly introduced failures while still reporting fixed and
pre-existing failures.

## 5. Differential execution and classification

`ProjectTestRunner` creates temporary git worktrees for the requested base and
head refs. `ProjectAdapterFactory` detects a Maven (`pom.xml`) or Gradle
(`build.gradle` / `build.gradle.kts`) project and selects the matching adapter.

If a candidate test is supplied:

1. `TestCandidateLoader` parses it as a Java compilation unit, derives the
   package/class selector, and chooses a project-relative path (or accepts
   `--test-path`).
2. The loader rejects absolute paths, `..` traversal, and non-`.java` paths.
3. `ProjectTestRunner.inject` writes to both worktrees. It rejects symlinked path
   components and symlinked targets, and opens the destination with
   `NOFOLLOW_LINKS`, so a malicious repository cannot redirect the write outside
   the worktree.
4. The adapter runs the candidate selector in both revisions.

`DockerSandboxRunner` is the only test-execution backend. It maps the host
command to a Maven or Gradle container, runs as the host uid/gid on Unix, uses a
read-only root filesystem with a writable `/tmp`, drops all capabilities, sets
`no-new-privileges`, limits PIDs to 512, memory to 2 GB, and CPUs to 2.
`CODECRITIC_SANDBOX_NETWORK` selects `none` (default) or `bridge`;
`CODECRITIC_MAVEN_IMAGE` / `CODECRITIC_GRADLE_IMAGE` override the images. Docker
exit code `125` and daemon-connection failures are surfaced as infrastructure
errors, not test failures.

`TestResultXmlParser` reads Maven Surefire / Gradle XML results, and
`TestRunClassifier` maps the process result to `SUCCESS`, `TEST_FAILURES`,
`COMPILE_FAILURE`, `BUILD_FAILURE`, `TIMEOUT`, `UNSUPPORTED`, or `ERROR`.
`DifferentialTestClassifier` then compares base and head:

| Base | Head | Verdict |
|------|------|---------|
| `SUCCESS` | `SUCCESS` | `P2P` |
| `TEST_FAILURES` | `SUCCESS` | `F2P` (candidate demonstrates the fix) |
| `SUCCESS` | `TEST_FAILURES` | `P2F` (candidate demonstrates a regression) |
| `TEST_FAILURES` | `TEST_FAILURES` | `F2F` |
| Anything else | Anything else | `INCONCLUSIVE` |

With stability checking enabled (the default), `F2P` and `P2F` runs are repeated.
If the repeat produces a different verdict, the report says `FLAKY` rather than
claiming a behavior change.

## 6. Reports and evidence

`VerificationReportBuilder` assigns stable evidence ids such as `E01` and records:

- each method change and its details;
- complexity before/after;
- heuristic caller count;
- affected test paths;
- the risk score, band, and contributing factors;
- base/head test status and the first failure;
- selected test scope and any full-suite fallback reason;
- new, fixed, and persistent test failures between base and head;
- candidate injection and differential verdict;
- setup warnings and parse warnings.

`JsonReportRenderer` emits the full model for CI or downstream tooling.
`HtmlReportRenderer` renders the same evidence inline. The static analysis path is
deterministic for a fixed repository and ref pair; test execution depends on the
target project and its environment. Reports are advisory review artifacts, not a
correctness proof.

## 7. Gate decisions and exit codes

`GateEvaluator` runs after the report is built, so a successful report write
happens before the gate decision is applied. A report-write failure exits `2`
without a gate verdict. `--fail-on` selects the policy:

| Policy | Fails when | Passes when |
|---|---|---|
| `new-failure` (default) | head has a failure absent from base, or head failures cannot be compared with a base result | no new failures |
| `any-failure` | head has any test failure | head tests pass |
| `high-risk` | head has test failures, or risk band is `HIGH` | head tests pass and risk is `LOW`/`MEDIUM` |
| `never` | never | always |

For enabled policies, any setup error, missing head result, `UNSUPPORTED` project,
compile failure, build failure, timeout, or execution error fails the gate.
`high-risk` evaluates head test health before the risk band. Exit codes are:

- `0`: report written; gate passed or was disabled.
- `1`: gate failed.
- `2`: usage, git, or report-write error.

Candidate-test mode is not gated: the verdict is reported as evidence (`F2P`,
`P2F`, `F2F`, `FLAKY`, `INCONCLUSIVE`) for a human or a separate policy decision.
