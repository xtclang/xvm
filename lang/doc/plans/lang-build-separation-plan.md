# Lang Build Separation Plan

## Purpose

Plan how to separate the `lang/` tree from the root XDK build cycle without losing the shared
build logic, and outline what would be required later to move `lang/` into its own repository.

This is an investigation and planning document only. No code changes are proposed here.

## Current State

`lang` is already a separate Gradle build in one important sense:

- it has its own [settings.gradle.kts](/Users/marcus/src/xtclang3/lang/settings.gradle.kts)
- it has its own [build.gradle.kts](/Users/marcus/src/xtclang3/lang/build.gradle.kts)
- the root build includes it as a composite build only when `includeBuildLang=true`

So the current architecture is not "lang is just another root subproject". It is already a
standalone included build embedded inside the monorepo.

What still couples it to the root/XDK world:

1. Shared property resolution walks up to the composite root.
2. Shared version catalog resolution walks up to the composite root.
3. `lang` uses the same `xdk.version` and other XDK properties.
4. some tasks assume access to the root composite and its included builds (`xdk`, `plugin`)
5. tree-sitter validation scans the XDK source tree directly
6. docs and developer workflows still assume `./gradlew :lang:*` is invoked from the root

## What Already Works In Favor Of Separation

These are strengths of the current design:

1. `lang` already has its own Gradle root.
2. `lang` already reuses build logic via:
   - `includeBuild("../build-logic/settings-plugins")`
   - `includeBuild("../build-logic/common-plugins")`
3. `lang/build.gradle.kts` already aggregates its own internal lifecycle across:
   - `dsl`
   - `tree-sitter`
   - `lsp-server`
   - `dap-server`
   - `intellij-plugin`
   - `vscode-extension`
4. root lifecycle attachment is already optional via:
   - `includeBuildLang`
   - `includeBuildAttachLang`

This means the correct direction is not a full redesign. It is mostly a cleanup of coupling points.

## Main Coupling Points

### 1. Composite-root property loading

The shared settings/build plugins intentionally load properties from the composite root:

- [org.xtclang.build.common.settings.gradle.kts](/Users/marcus/src/xtclang3/build-logic/settings-plugins/src/main/kotlin/org.xtclang.build.common.settings.gradle.kts)
- [org.xtclang.build.xdk.properties.gradle.kts](/Users/marcus/src/xtclang3/build-logic/common-plugins/src/main/kotlin/org.xtclang.build.xdk.properties.gradle.kts)
- [XdkPropertiesService.kt](/Users/marcus/src/xtclang3/build-logic/settings-plugins/src/main/kotlin/XdkPropertiesService.kt)

Today they walk upward looking for:

- `gradle.properties`
- `xdk.properties`
- `version.properties`
- `gradle/libs.versions.toml`

That is convenient inside this monorepo, but it means `lang` is not yet self-describing.

### 2. Shared version and release metadata

The `lang` subprojects use the root property system for:

- `xdk.version`
- Java/Kotlin toolchain versions
- LSP feature flags
- IntelliJ plugin release channel
- Marketplace token/property naming

This is intentional today and is compatible with staying in one repo, but it blocks easy
standalone reuse unless `lang` gains its own local defaults or imported metadata.

### 3. IntelliJ plugin `runIde` dependency on root-published artifacts

The IntelliJ plugin build currently assumes access to the root composite parent and its included
`xdk` and `plugin` builds:

- [build.gradle.kts](/Users/marcus/src/xtclang3/lang/intellij-plugin/build.gradle.kts)

Specifically:

- `gradle.parent?.includedBuild("xdk").task(":publishToMavenLocal")`
- `gradle.parent?.includedBuild("plugin").task(":publishToMavenLocal")`

This is a genuine root coupling, not just a version/property coupling.

### 4. Tree-sitter corpus validation depends on XDK source layout

The tree-sitter project explicitly scans the composite root for `lib_*` directories:

- [build.gradle.kts](/Users/marcus/src/xtclang3/lang/tree-sitter/build.gradle.kts)

That makes sense while `lang` lives inside the XVM repo, but it is a test/validation coupling that
must become optional or externalized if `lang` is to become truly standalone.

### 5. Documentation and operator expectations

Many docs still assume root invocation with:

- `-PincludeBuildLang=true`
- `-PincludeBuildAttachLang=true`

That is a workflow coupling even when the actual Gradle topology is already cleaner than the docs imply.

## Goal State A: Standalone Build Inside The Same Repo

This is the first and recommended milestone.

Desired properties:

1. `cd lang && ./gradlew build` works naturally.
2. `lang` can still reuse shared build logic from `../build-logic`.
3. root XDK builds do not automatically pull `lang` in unless explicitly requested.
4. root CI can choose whether to include `lang`.
5. `lang` can still intentionally share versioning with the XDK when desired.

## Goal State B: Separate Repository Later

This is the second milestone.

Desired properties:

1. `lang` can live in its own git repository.
2. it still uses the same or equivalent build logic conventions.
3. it depends on a declared XDK version instead of an implicit monorepo root.
4. it can consume:
   - latest snapshot
   - pinned snapshot
   - release version
5. local monorepo development can still use composite builds for tight integration if wanted.

## Recommended Migration Strategy

## Phase 1: Make `lang` self-hosting inside this repo

This phase keeps the monorepo and shared build-logic checkout.

### 1. Add `lang/gradle.properties`

Give `lang` its own local defaults for properties it truly owns, for example:

- `lsp.adapter`
- `lsp.semanticTokens`
- `lsp.buildSearchableOptions`
- `xtc.intellij.semanticTokens`

Potentially also mirror the toolchain properties if needed.

Do not immediately duplicate everything from the root. Instead split the properties into:

- lang-owned runtime/build toggles
- repo-global/shared versioning and publishing metadata

### 2. Add `lang/version.properties` or equivalent local version source

If `lang` should still share the root version while in the monorepo, there are two viable models:

1. soft-link/import model
   - local `lang/version.properties` is generated or synchronized from root
2. explicit local mirror model
   - `lang/version.properties` contains the same version keys and is updated in tandem

Recommendation:

- add a local `lang/version.properties`
- keep it identical to the root for now
- treat the root file as the source of truth until/unless `lang` splits

This is the cleanest step toward later repo extraction.

### 3. Allow shared build logic to prefer local `lang` files before walking to composite root

The shared settings/build plugins should be adjusted so that:

- they first look in the current build root
- then optionally fall back to the composite root

This preserves monorepo convenience while allowing `lang` to become self-contained.

Required changes would be in:

- [org.xtclang.build.common.settings.gradle.kts](/Users/marcus/src/xtclang3/build-logic/settings-plugins/src/main/kotlin/org.xtclang.build.common.settings.gradle.kts)
- [org.xtclang.build.xdk.properties.gradle.kts](/Users/marcus/src/xtclang3/build-logic/common-plugins/src/main/kotlin/org.xtclang.build.xdk.properties.gradle.kts)
- [XdkPropertiesService.kt](/Users/marcus/src/xtclang3/build-logic/settings-plugins/src/main/kotlin/XdkPropertiesService.kt)

Conceptually:

- current behavior: "walk to composite root and read files from there"
- target behavior: "read from local build root first; optionally merge/fallback to composite root"

### 4. Give `lang` its own version catalog entry point

Currently `org.xtclang.build.common` resolves `gradle/libs.versions.toml` from the composite root.

For `lang` autonomy, choose one of:

1. local copy of `lang/gradle/libs.versions.toml`
2. generated/synced local copy
3. published shared version catalog artifact

Recommendation for Phase 1:

- local copy under `lang/gradle/libs.versions.toml`
- synchronized with root for now

That avoids hidden root dependency while keeping the same dependency coordinates.

### 5. Stop requiring `includeBuildAttachLang=true` for most lang workflows

Developer-facing docs should shift from:

- "run root build with include/attach flags"

to:

- "use `cd lang && ./gradlew ...` for lang work"
- "use root composite only when integration with XDK root lifecycle is specifically desired"

In practice:

- `includeBuildLang=true` should remain a root convenience
- `includeBuildAttachLang=true` should become clearly optional/integration-only

### 6. Reduce root assumptions in IntelliJ plugin development flow

The biggest concrete blocker is `runIde` publishing root artifacts to `mavenLocal`.

To loosen this:

1. introduce an explicit XDK/plugin artifact resolution mode for `lang:intellij-plugin`
2. support:
   - composite-local mode
   - mavenLocal mode
   - explicit repository snapshot mode

That would replace the current hard dependency on:

- `gradle.parent`
- root included builds `xdk` and `plugin`

Recommendation:

- create a small abstraction/property like `xtc.dev.artifactSource={composite,mavenLocal,repository}`
- default to `composite` when running inside monorepo root
- allow `mavenLocal` and repository resolution when `lang` is run standalone

### 7. Make tree-sitter corpus validation optional and parameterized

`testTreeSitterParse` should be able to run in at least two modes:

1. monorepo corpus mode
   - uses `lib_*` from XVM root
2. standalone corpus mode
   - uses a configured source directory or fixture corpus

Recommendation:

- add an input property like `xtc.corpus.root` or `xtc.corpus.dirs`
- if absent:
  - in monorepo mode, use current auto-detection
  - in standalone mode, fall back to a smaller checked-in corpus

That makes the project independently testable without losing the valuable full-XDK validation path.

## Phase 2: Treat root composite inclusion as integration mode only

Once Phase 1 is in place:

1. root `gradle.properties` can keep `includeBuildLang=false`
2. root CI can explicitly opt in to `lang`
3. root `build`, `check`, `assemble`, `clean` remain XDK-centric unless `attach` is requested
4. `lang` becomes operationally independent while still living in the same repo

This should be the default steady state before any repository split.

## Phase 3: Prepare for a future separate repository

At this point, `lang` would already have:

- its own properties
- its own version metadata
- its own version catalog
- fewer root assumptions

Then the repo extraction becomes mostly a source-control and artifact-boundary task.

## Build Logic Reuse Options For A Separate Repo

Yes, `lang` can still use shared build logic, but the delivery model must change.

### Option A: Keep build logic as a checked-out included build

In a separate repo, `lang/settings.gradle.kts` could still do:

- `includeBuild("../shared-build-logic/settings-plugins")`
- `includeBuild("../shared-build-logic/common-plugins")`

Pros:

- same development model as today
- fast local iteration on conventions

Cons:

- requires a sibling checkout layout
- not self-contained
- brittle for CI and for external contributors

This is acceptable for internal development, but not ideal as the only model.

### Option B: Vendor or subtree the needed build logic into the `lang` repo

Pros:

- self-contained
- no plugin publishing infrastructure required

Cons:

- code duplication
- drift between root repo and lang repo
- conventions must be manually synchronized

This is the simplest bootstrap path, but not the best long-term one.

### Option C: Publish the convention plugins and consume them normally

For example, publish `org.xtclang.build.common`, `org.xtclang.build.xdk.properties`, etc. to:

- Gradle Plugin Portal
- GitHub Packages
- internal Maven/Gradle repository

Then `lang/settings.gradle.kts` and `lang/build.gradle.kts` would apply them by version, not by local included build.

Pros:

- cleanest independent-repo model
- no checkout-topology assumptions
- normal Gradle consumer experience

Cons:

- needs a plugin publication pipeline
- versioning of build logic becomes explicit
- more release-management discipline required

Recommendation:

- long term, this is the cleanest separate-repo solution
- short term, do not start here unless repo split is imminent

## XDK Dependency Model For A Separate Repo

If `lang` becomes its own repo, it should depend on an explicit XDK version rather than an implicit monorepo root.

There are several dependency surfaces:

1. build-time toolchain/version metadata
2. runtime assets used by `runIde` / project creation / plugin integration
3. validation corpus from `lib_*`

### Recommended dependency strategy

### 1. Use explicit `xdk.version`

`lang` should declare:

- `xdk.version=<version>`

Potentially also:

- `xdk.channel=snapshot|release`
- `xdk.repository=<url>` if non-default

### 2. Resolve XDK artifacts, not source-tree assumptions

For runtime/development flows, prefer:

- published snapshot artifacts
- `mavenLocal`
- downloaded XDK distribution ZIP

over:

- assuming `../xdk` or monorepo root tasks exist

### 3. Keep composite development as an optional local integration mode

Even after a split, local integration with XVM could still be supported via composite build:

- `includeBuild("../xvm/xdk")`
- `includeBuild("../xvm/plugin")`

But this should become an optional dev mode, not a requirement for ordinary builds.

### 4. Treat corpus validation separately

The full XDK `lib_*` parse corpus is valuable, but it should become:

- an optional integration test mode
- not a baseline requirement for every standalone `lang` build

## Proposed End-State Architecture

### Inside the monorepo

- root repo:
  - owns XDK build, release pipeline, Homebrew, Docker, etc.
- `lang`:
  - builds independently from `lang/`
  - may share version with root by policy
  - root includes it only when explicitly requested

### Outside the monorepo

- `lang` repo:
  - owns LSP/IDE/editor tooling build and release
  - depends on explicit XDK version(s)
  - reuses shared build logic either via published plugins or synchronized vendored logic

## Concrete Work Breakdown

## Work Package 1: Local self-description

1. add `lang/gradle.properties`
2. add `lang/version.properties`
3. add `lang/gradle/libs.versions.toml`
4. update build logic to prefer local files over composite-root files

## Work Package 2: Remove hard root runtime assumptions

1. decouple IntelliJ plugin `runIde` from `gradle.parent`
2. introduce configurable XDK/plugin artifact source modes
3. support standalone `lang` execution without root included builds

## Work Package 3: Parameterize validation/test inputs

1. make tree-sitter corpus location configurable
2. introduce small standalone fixture corpus
3. retain full-XDK corpus as optional integration mode

## Work Package 4: Documentation and workflow cleanup

1. rewrite docs to prefer `cd lang && ./gradlew ...`
2. reposition root `includeBuildLang` / `includeBuildAttachLang` as integration controls
3. document standalone vs composite modes explicitly

## Work Package 5: Separate-repo readiness

1. choose build-logic reuse model
2. formalize XDK artifact dependency contract
3. define snapshot/release consumption policy for `lang`
4. decide whether CI/release remains in XVM repo or moves with `lang`

## Recommendation

Do not split `lang` into its own repository yet.

The better immediate move is:

1. make `lang` independently buildable from `lang/`
2. keep shared build logic via local included builds
3. reduce root/XDK assumptions in `runIde` and validation tasks
4. preserve version alignment with the XDK by policy, not by hidden file-walk coupling

After that, a repo split becomes a packaging/governance decision instead of a difficult build-system rewrite.

## Answer To The Direct Question

Yes: `lang` can still include shared build logic from its own `lang/settings.gradle.kts` / `lang/build.gradle.kts` master files.

Today it already does that.

For a future separate repository, that remains possible if:

- the build logic is checked out alongside it, or
- the build logic is vendored into the repo, or
- preferably, the build logic plugins are published and consumed by version

The limiting factor is not Gradle capability. It is how you want to distribute and version the shared build logic.
