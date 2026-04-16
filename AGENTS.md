# Agent Instructions

Canonical repo-local agent guidance lives here. `CLAUDE.md` may refer to this file.

## Git

- Never push, delete remote refs, or create PRs without explicit user approval.
- Local commits are fine when requested.

## Gradle Safety

- Never run `./gradlew clean` with any other task in this repository.
- `clean` must run alone because the root build uses a custom aggregator over composite builds.
- Safe pattern:
  1. `./gradlew clean`
  2. wait
  3. `./gradlew <other tasks>`

Examples:

```bash
./gradlew clean
./gradlew build publishLocal
./gradlew :plugin:test
```

Forbidden:

```bash
./gradlew clean build
./gradlew clean publishLocal
```

## Composite Build Semantics

- `settings.gradle.kts` is an aggregator root over included builds.
- Core builds are always included: `javatools`, `javatools_jitbridge`, `javatools_utils`,
  `javatools_unicode`, `plugin`, `xdk`, `docker`.
- Optional composite builds are controlled by properties:
  - `includeBuildLang`
  - `includeBuildManualTests`
- Attachment of optional builds to root lifecycle is controlled separately:
  - `includeBuildAttachLang`
  - `includeBuildAttachManualTests`

Implications:

- `:lang:*` tasks are not visible from the root unless `-PincludeBuildLang=true`.
- To make root lifecycle tasks also include `lang`, also set `-PincludeBuildAttachLang=true`.
- `manualTests` is a fake third-party consumer build used to verify published/composite plugin+XDK behavior.

Use this when running `lang` tasks from root:

```bash
./gradlew :lang:<task> -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

## Build-Lifecycle Semantics

- Root `build` is an aggregate lifecycle over included builds.
- Running a leaf task does not imply running `build`.
- `runXtc`/alias tasks behave like Gradle Java `run`: they build what they need, not full `check`/`build`.
- `testXtc` is wired into `check`, so `build` runs it, but `greet`/`runXtc` do not.

## Build Logic Rules

- All Gradle changes must remain configuration-cache compatible.
- Prefer typed Kotlin DSL:

```kotlin
val taskName by tasks.registering {
    dependsOn(tasks.named("otherTask"))
}
```

- Do not use string-based untyped task wiring when typed providers are available.
- Do not capture `project`, `logger`, or other script objects inside task actions.
- Prefer Provider APIs, declared inputs/outputs, injected services, Worker API, or convention plugins.
- Test Gradle changes with real tasks after editing.

## Java/Kotlin Style

- Always end files with a newline.
- Never use star imports.
- Never use fully qualified Java type names in Java source when an import should be used.
- Prefer `var` when the type is obvious from the right-hand side.

## General

- Do what was asked; no speculative extra changes.
- Do not create new documentation files unless explicitly requested.
- Never add `Co-Authored-By` lines.
