# Security Policy

CodeCritic executes build and test commands from the repository it analyzes.
Treat the target repository, its build files, and its test code as untrusted.

## Supported versions

| Version | Support |
|---|---|
| `0.1.x` | Current source line; security fixes and normal bug fixes |
| `< 0.1` | Not supported |

CodeCritic is pre-1.0. Breaking changes may occur in minor releases.

## Reporting a vulnerability

Use GitHub private vulnerability reporting:

<https://github.com/learnerview/CodeCritic/security/advisories/new>

Do not open a public issue for a suspected vulnerability. Include:

- the CodeCritic version or commit,
- the operating system and Docker version,
- a minimal reproduction,
- the impact you believe is possible.

## Threat model

The primary risk is a malicious or compromised repository. Build scripts are
executable input, not inert data.

When tests run, CodeCritic has no host-execution fallback. It runs Maven or
Gradle inside Docker with:

- `--network none` by default,
- `--cap-drop ALL`,
- `no-new-privileges`,
- a read-only root filesystem with a writable `/tmp`,
- PID, memory, and CPU limits,
- a bind mount of the isolated worktree only,
- a per-test timeout and forced container cleanup.

These controls reduce risk; they are not a general-purpose sandbox or a defense
against Docker or kernel escapes. Residual risks include:

- **Bridge networking.** `CODECRITIC_SANDBOX_NETWORK=bridge` lets untrusted build
  scripts make outbound connections. Use it only when dependency downloads are
  required, and prefer a pre-warmed image, dependency cache, or internal mirror.
- **Docker daemon access.** CodeCritic invokes the local Docker CLI. A user who
  can run CodeCritic can generally use the same Docker daemon directly. Run it
  on a dedicated CI runner or with the least privilege available.
- **Resource exhaustion.** Limits bound CPU, memory, PIDs, and test time, but a
  hostile build can still consume a runner until the timeout or limits apply.
- **Workspace contents.** Build scripts can read files in the checked-out
  worktree and write build outputs. Do not check out deployment secrets into a
  repository that runs untrusted pull-request code.
- **Report contents.** Reports and CI logs can include repository paths, test
  names, compiler output, and captured process output. Treat them as internal
  artifacts and apply your normal retention policy.

## Operating guidance

- Keep the sandbox network at `none` unless the build requires downloads.
- Pin the GitHub Action to a release tag or commit SHA.
- Do not pass secrets to workflows that execute fork pull-request code.
- Keep Docker and the host patched; the sandbox inherits the host kernel.
- Upload reports as CI artifacts instead of pasting them into public issues.
- Run `mvn -B clean verify` before using a new CodeCritic build.
