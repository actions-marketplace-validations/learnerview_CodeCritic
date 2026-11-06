# Analysis Algorithms

## 1. Complexity analysis (`/api/complexity`)

`JavaParserComplexityAnalyzer` parses the submitted source into an AST and walks it with a `VoidVisitorAdapter` that tracks the maximum cyclomatic complexity across all methods.

**Decision points counted (per-method):**
- `if`
- `for` / `foreach`
- `while` / `do`
- `catch`
- `switch` cases (`label` each)
- ternary `?:`
- boolean `&&` / `||`

For each `MethodDeclaration`, the visitor resets a `current` counter to 1 and records the running max after visiting the method body. **Cognitive complexity** is a simple scaled estimate (`max(1, cyclomatic / 2)`).

**Fallback (graceful degradation):** if JavaParser cannot parse the source, `fallbackHeuristic` runs. It first strips `// line`, `/* block */` comments, `"..."` string literals, and `'...'` char literals so tokens inside comments or strings are not miscounted, then counts `if(`, `for(`, `while(`, `case `, `&&`, `||`, `catch(`, `?:`, and `default:`. The stripping is escape-aware: a backslash in a string/char literal (e.g. `\\"` or `'\''`) skips the escaped character so an escaped quote does not prematurely end the literal.

## 2. Bug detection (`/api/bugs`)

`CompositeBugDetector` combines multiple detectors:

### Pattern detector (`PatternBugDetector`)
Line-scanning heuristics:
- **Division by zero**: a line matching `\d+\s*/\s*0` (digits divided by zero) → `DivisionByZeroRisk`. Requiring digits around the `/` and `0` reduces false positives from comments or unrelated `/ 0` text.
- **Unsafe `.toString()`**: a line with `.toString()` that has no `!= null` guard on it → `NullPointerRisk`.

### SpotBugs (`SpotBugsBugDetector` + `SpotBugsRunner`)
SpotBugs compiles the submitted source in a unique temp directory (named `codecritic-<requestId>`) and runs `spotbugs -textui` over the compiled classes. Each finding line is parsed into a `BugFinding` with the SpotBugs bug type (e.g. `NP_NULL_ON_SOME_PATH`, `IL_INFINITE_LOOP`) extracted from the output; `SpotBugsFinding` is the placeholder type used only when a finding carries no parseable type, or when the tool is unavailable. The request is never blocked on: if the tool is missing or errors the detector returns no findings rather than failing the call, **except** that source which does not compile yields a single `COMPILATION_ERROR` finding.

### Caching (`CachedSpotBugsBugDetector`)
SpotBugs is expensive (compile + subprocess), so results are cached:
- **Key** = SHA-256(source + `DETECTOR_VERSION`). Bumping the version invalidates everything.
- **Bounded LRU**: a synchronized, access-ordered `LinkedHashMap` limited to 256 entries evicts the least-recently-used entry instead of clearing the whole cache.
- **In-flight de-duplication**: concurrent calls for the same key share a single `CompletableFuture`, so N simultaneous submissions of identical source trigger one SpotBugs run and the rest await its result rather than recompiling in parallel.

## 3. Test generation (`/api/generate-test`)

`JavaParserTestGenerator` parses the source to extract the real class name, method name, return type, and parameter types, then emits a deterministic JUnit 5 scaffold:

```java
package <package>;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class <Class>Test {
    @Test
    void test<Method>() {
        <Class> obj = new <Class>();
        var result = assertDoesNotThrow(() -> obj.<Method>(<args>));
        assertNotNull(result);   // or assertDoesNotThrow for void
    }
}
```

**Placeholder literals** for parameters map by *simple* type name (so fully-qualified names like `java.lang.Integer` also match):

| Type | Literal |
|------|---------|
| `int`, `long`, `short`, `byte` (and boxed) | `1` |
| `float`, `double` (and boxed) | `1.0` |
| `boolean` | `true` |
| `char` | `'a'` |
| `String` | `"sample"` |
| other | `null` |

For non-void return types it emits `assertNotNull(result)`, except for `boolean`/`Boolean` returns where it emits an `assertTrue(result == Boolean.TRUE || result == Boolean.FALSE)` check; void methods use `assertDoesNotThrow`.

### LLM full suite (`/generate-tests`)
`generate_full_test_suite` reuses the deterministic findings and asks the LLM for a complete JUnit 5 class with meaningful assertions and happy-path/edge/error coverage, rejecting output that has no `class` or `@Test`.

## 4. Callable-level change detection (`verification`)

`GitChangeSource` resolves both refs with `git rev-parse`, reads the changed paths with `git diff --name-status -M -z`, and records both the Java files for callable analysis and the complete changed-path list for test selection. Renames are detected, additions/deletions/modifications are preserved, and the full head source tree is loaded once through `git ls-tree` plus a batched blob reader.

`JavaDiffAnalyzer` parses both revisions with JavaParser and extracts methods and constructors through `JavaMethodExtractor`. Each callable gets a stable key:

```
<fully-qualified-class>#<method-name>(<parameter-types>)
```

Constructors use `<init>`. The key deliberately excludes the return type, so a return-type-only change appears as a signature change rather than an add/remove pair.

For each key present on either side:

| Situation | Change kind |
|-----------|-------------|
| Missing in base, present in head | `ADDED` |
| Present in base, missing in head | `REMOVED` |
| Signature text differs | `SIGNATURE_CHANGED` |
| Signature same, body text differs | `MODIFIED` |
| Both signature and body same | Not reported |

The diff records the old/new signature, line, public/protected visibility, cyclomatic complexity, and human-readable details. A parse failure does not abort the run: it produces a warning and skips that file, so one malformed Java file cannot hide the rest of the change set.

## 5. Caller and test indexing (`ProjectIndex`)

`ProjectIndex` parses the head sources and indexes every `MethodCallExpr` and `MethodReferenceExpr` by the **called method name**. Counts are name-based, not resolved against a type hierarchy.

- A path is treated as a test when it contains `/src/test/java/`, or ends in `Test.java`, `Tests.java`, or `IT.java`.
- `productionCallerCount(key, methodName)` counts distinct non-test caller keys that reference that method name, excluding the changed callable itself.
- `affectedTests(className, methodName)` returns tests that call the method name **or** whose source text contains the class simple name or method name.

These are prioritization heuristics. Overloads, inherited methods, reflection, generated code, and dynamic dispatch can all make the real caller set different from the indexed set.

## 6. Risk scoring (`ImpactAnalyzer`)

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

Bands are **LOW** below 30, **MEDIUM** from 30 to 59, and **HIGH** at 60+. The report's overall score is the maximum callable score, not an average, and callables are sorted highest-risk first. Every factor is emitted as evidence text, so a reviewer can see why a score was assigned instead of treating it as an opaque number.

## 7. Differential execution and classification

`ProjectTestRunner` creates temporary git worktrees for the requested base and head refs. `ProjectAdapterFactory` detects a Maven (`pom.xml`) or Gradle (`build.gradle` / `build.gradle.kts`) project and selects the matching adapter.

`ModuleDiscovery` reads Maven `<module>` entries recursively and Gradle `include(...)` statements from `settings.gradle`/`settings.gradle.kts`. It then builds a reverse dependency graph from Maven `groupId:artifactId` coordinates and Gradle `project(':module')` references. `ModulePlan` maps each changed path to the deepest matching module, expands the selection to transitive dependents, and runs those modules (`mvn -pl ... -am test` or `:module:test`). It deliberately falls back to the full suite when no module metadata exists, only one module exists, no changed path maps to a module, a build file changed, a root-level change was detected, a dependent root module was selected, or a selected module is missing at either revision. `FailureComparison` then compares failed-test identities between base and head, so a gate can fail only on newly introduced failures while still reporting fixed and pre-existing ones.

If a candidate test is supplied:

1. `TestCandidateLoader` parses it as a Java compilation unit, derives the package/class selector, and chooses a project-relative path (or accepts `--test-path`).
2. The loader rejects absolute paths, `..` traversal, and non-`.java` paths.
3. `ProjectTestRunner.inject` writes to both worktrees. It rejects symlinked path components and symlinked targets, and opens the destination with `NOFOLLOW_LINKS`, so a malicious repository cannot redirect the write outside the worktree.
4. The adapter runs the candidate selector in both revisions.

`DockerSandboxRunner` is the only test-execution backend. It maps the host command to a Maven or Gradle container, runs as the host uid/gid on Unix, uses a read-only root filesystem with a writable `/tmp`, drops all capabilities, sets `no-new-privileges`, limits PIDs to 512, memory to 2 GB, and CPUs to 2. `CODECRITIC_SANDBOX_NETWORK` selects `none` (default) or `bridge`; `CODECRITIC_MAVEN_IMAGE` / `CODECRITIC_GRADLE_IMAGE` override the images. Docker exit code `125` and daemon-connection failures are surfaced as infrastructure errors, not test failures.

`TestResultXmlParser` reads Maven Surefire / Gradle XML results, and `TestRunClassifier` maps the process result to `SUCCESS`, `TEST_FAILURES`, `COMPILE_FAILURE`, `BUILD_FAILURE`, `TIMEOUT`, `UNSUPPORTED`, or `ERROR`. `DifferentialTestClassifier` then compares base and head:

| Base | Head | Verdict |
|------|------|---------|
| `SUCCESS` | `SUCCESS` | `P2P` |
| `TEST_FAILURES` | `SUCCESS` | `F2P` (candidate demonstrates the fix) |
| `SUCCESS` | `TEST_FAILURES` | `P2F` (candidate demonstrates a regression) |
| `TEST_FAILURES` | `TEST_FAILURES` | `F2F` |
| Anything else | Anything else | `INCONCLUSIVE` |

With stability checking enabled (the default), `F2P` and `P2F` runs are repeated. If the repeat produces a different verdict, the report says `FLAKY` rather than claiming a behavior change.

## 8. Reports and evidence

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

`JsonReportRenderer` emits the full model for CI or downstream tooling. `HtmlReportRenderer` renders the same evidence inline. Neither report treats an LLM as evidence: the verification path is fully deterministic, and reports are advisory review artifacts, not a correctness proof.

## 9. Gate decisions and exit codes

`GateEvaluator` runs after the report is built, so the artifact is always written before the process exits. `--fail-on` selects the policy:

| Policy | Fails when | Passes when |
|---|---|---|
| `new-failure` (default) | head has a failure absent from base, or head has failures with no comparable baseline | no new failures |
| `any-failure` | head has any test failure | head tests pass |
| `high-risk` | risk band is `HIGH` after health checks | risk is `LOW`/`MEDIUM` |
| `never` | never | always |

Before applying the policy, any setup error, missing head result, `UNSUPPORTED` project, compile failure, build failure, timeout, or execution error fails the gate. `high-risk` does not hide a broken test run. Exit codes are:

- `0`: report written; gate passed or was disabled.
- `1`: gate failed.
- `2`: usage, git, or report-write error.

Candidate-test mode is not gated: the verdict is reported as evidence (`F2P`, `P2F`, `F2F`, `FLAKY`, `INCONCLUSIVE`) for a human or a separate policy decision.
