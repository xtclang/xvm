# Agent Configuration

Repo-local operator checklist for the XVM project.

## Git — no unsupervised remote/destructive ops

Never push, create/delete remote branches, or open PRs without explicit user confirmation.
This covers `git push`, `git push -u`, `git branch -D`, `git push --delete`, and any state-changing
`gh` command (e.g. `gh pr create`). Local commits and local branches are fine when requested —
nothing leaves the machine without the user saying so.

## Gradle: `clean` runs alone

A custom aggregator over the composite builds rejects `clean` combined with any other task
(race conditions, cross-subproject conflicts, task-ordering). Run it standalone, wait, then run the rest.

```bash
# FORBIDDEN
./gradlew clean build
./gradlew clean publishLocal

# REQUIRED
./gradlew clean          # alone; wait for it to finish
./gradlew build publishLocal
```

Non-`clean` combinations are fine: `./gradlew build installDist`, `./gradlew test jar`, etc.

## Composite build semantics

- `settings.gradle.kts` is an aggregator root; root `build` is an aggregate lifecycle over the included builds.
- Always included: `javatools`, `javatools_jitbridge`, `javatools_utils`, `javatools_unicode`, `plugin`, `xdk`, `docker`.
- Optional builds are gated by `includeBuildLang` / `includeBuildManualTests` (visibility) and attached to the
  root lifecycle by `includeBuildAttachLang` / `includeBuildAttachManualTests`. All default to `false`.
- `manualTests` is a fake third-party consumer build that verifies published/composite plugin+XDK behavior.
- Running any `:lang:*` task from root needs BOTH flags (either one alone fails with "project ':lang' not found"):

```bash
./gradlew :lang:<task> -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

## Run vs build lifecycle

- A leaf task does not imply `build`. `runXtc`/alias/`greet` tasks behave like Gradle `run`: they build only
  what they need, not full `check`/`build`.
- `testXtc` is wired into `check`, so `build` runs it — but `runXtc`/`greet` do not.

## Running tests so they actually run

- Gradle caches test results: use `--rerun-tasks --no-build-cache` to force a real re-run instead of `UP-TO-DATE`.
  Single class via `--tests`.

  ```bash
  ./gradlew :javatools:test :javatools_utils:test --rerun-tasks --no-build-cache
  ./gradlew :javatools:test --tests "fully.qualified.ClassName" --rerun-tasks --no-build-cache
  ```

- **Silent skips**: many `javatools` tests `assumeTrue(...)` on compiled XDK outputs and SKIP silently when absent —
  a "green" run may have executed nothing. Build outputs first with `./gradlew xdk:installDist` (alone if you just
  cleaned), then confirm the run reports `skipped=0` when you need it to have actually run. Compiled system modules live under:
  - `xdk/build/install/xdk/lib`, `xdk/build/install/xdk/javatools`
  - `lib_ecstasy/build/xtc/main/lib`, `javatools_bridge/build/xtc/main/lib`
- **Read results from XML, not console** (console output is often truncated). Authoritative per-test results are the
  JUnit XMLs under `javatools/build/test-results/test/*.xml` — check `tests`/`failures`/`errors`/`skipped` and the
  `<failure>` stack.
- Bootstrap modules are special: turtle (`mack.xtclang.org`) is the NakedRef prototype at the bottom of the type
  system, not a normal library; the native bridge is `_native.xtclang.org`. Keep them in mind for module-path /
  repository / compile-bootstrap issues.

## Gradle build logic

- **Configuration cache is mandatory** — every Gradle change must work with it. Test with a real task after editing
  (e.g. `./gradlew <task> --info`).
  - Never capture script objects (`project`, `logger`, …) inside task actions.
  - Use injected services (`@Inject` `ExecOperations`, `FileSystemOperations`, `Logger`) instead of `project.exec`/`project.javaexec`.
  - Prefer Provider APIs, declared inputs/outputs, Worker API, or convention plugins; avoid eager configuration-time work.
## Java/Kotlin style

- Always end files with a newline.
- Don't use star imports unless there is more than 20 from the same package.
- Don't use fully-qualified Java type names in source when an import works (`ObjectFactory`, not `org.gradle.api.model.ObjectFactory`).
- For new code, write modern Java — records, streams, generics, fluent/functional style — not Java-1.0-style raw objects/arrays.
- Prefer immutable or minimally mutable state when there is no concrete reason for mutation; this reduces the amount of
  parallelism/reentrancy reasoning required later.
- For lazy cached values, prefer final holder patterns such as `Lazy`/`Lazy.Bound` over mutable `if (field == null)` getter
  caches when the holder fits the ownership and construction constraints.
- When fixing bugs in existing code, do not perform broad modernization rewrites unless they are needed for the fix or make
  the touched code materially safer or clearer.
- Keep the style of the code you add to an existing class consistent with its current style.


## General

- Do only what was asked; no speculative extra changes.
- Never proactively create docs/README (`*.md`) unless the user explicitly requests it.
- Never add `Co-Authored-By` lines.
