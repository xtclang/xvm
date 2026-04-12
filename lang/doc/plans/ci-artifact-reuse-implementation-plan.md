# Plan: CI Artifact Reuse And Redundant Work Removal

## Objective

Make the CI pipeline build heavyweight outputs once per commit and reuse them across downstream jobs.

The immediate targets are:

- plugin integration test in [`commit.yml`](../../../.github/workflows/commit.yml)
- IntelliJ plugin snapshot publication in [`publish-snapshot.yml`](../../../.github/workflows/publish-snapshot.yml)
- any other downstream jobs that currently re-enter large Gradle build graphs from a fresh checkout

This plan turns the earlier investigation notes into concrete implementation tasks.

## Scope

This plan coordinates the work described in:

- [`plugin/doc/plans/integration-test-artifact-reuse-plan.md`](../../../plugin/doc/plans/integration-test-artifact-reuse-plan.md)
- [`intellij-plugin-publication-artifact-reuse-plan.md`](./intellij-plugin-publication-artifact-reuse-plan.md)

The guiding rule is:

- build once in the originating CI run
- publish/download a commit-scoped artifact bundle
- let downstream jobs consume or promote those artifacts

## Current Audit

### Already In Good Shape

- [`commit.yml`](../../../.github/workflows/commit.yml)
  - now stages:
    - `xdk-dist-${sha}`
    - `consumer-maven-repo-${sha}`
    - `intellij-plugin-dist-${sha}`
  - integration-test now consumes the staged consumer Maven repository artifact instead of running `publishLocal`
  - IntelliJ release-grade ZIP is built in the main CI session, then staged and uploaded
- [`publish-snapshot.yml`](../../../.github/workflows/publish-snapshot.yml)
  - IntelliJ plugin publication is now a true promotion job
  - it downloads `intellij-plugin-dist-${sha}` and publishes that exact ZIP
  - it publishes the IntelliJ ZIP to both:
    - JetBrains Marketplace alpha
    - GitHub release assets
- [`publish-docker.yml`](../../../.github/workflows/publish-docker.yml)
  - already behaves like an artifact promotion workflow
  - it downloads `xdk-dist-${sha}` and builds Docker images from that ZIP
- [`homebrew-update.yml`](../../../.github/workflows/homebrew-update.yml)
  - already behaves like an artifact promotion workflow
  - it downloads `xdk-dist-${sha}`, computes SHA-256, and updates the tap formula without rebuilding XDK

### Still Rebuilds From Source

- [`publish-snapshot.yml`](../../../.github/workflows/publish-snapshot.yml)
  - `publish-snapshots` still checks out the repo and runs `./gradlew publish`
  - this republishes Maven snapshot artifacts from source instead of promoting a commit-scoped publication bundle
- [`prepare-release.yml`](../../../.github/workflows/prepare-release.yml)
  - `stage-artifacts` still checks out the release tag and rebuilds the XDK with `:xdk:distZip`
  - it also republishes/stages Maven artifacts from source with `./gradlew publish`
- [`promote-release.yml`](../../../.github/workflows/promote-release.yml)
  - does not rebuild XDK itself for GitHub release or Maven Central promotion
  - but it still checks out source and runs `:plugin:publishPlugins` from source for Gradle Plugin Portal publication

### Shared Action Gaps

- [`download-ci-artifact/action.yml`](../../../.github/actions/download-ci-artifact/action.yml)
  - is still XDK-specific
  - it should be generalized or paralleled with a small metadata-aware action for:
    - XDK ZIP
    - consumer Maven repository
    - IntelliJ plugin bundle
- [`publish-github-release/action.yml`](../../../.github/actions/publish-github-release/action.yml)
  - is still XDK-centric and still uses delete/recreate semantics for snapshot releases
  - downstream IntelliJ publishing needed workflow-local fixes instead of a shared release-promotion primitive

### CI Signal Noise

- `testapp`, `testlib`, `testsvc`, and `testmulti` annotations in GitHub Checks are emitted by nested Gradle builds
  started by [`XtcProjectCreatorIntegrationTest`](../../../javatools/src/test/java/org/xvm/tool/XtcProjectCreatorIntegrationTest.java)
- they are not required branch-protection checks
- they appear to come from the child generated projects' own Gradle/Develocity integration under CI
- the main branch-protection signal should remain the outer workflow/job status, especially `All builds complete`
- cleanup is still worth doing so the Checks UI reflects only repo-level CI signals

### Action Runtime Maintenance

- official action majors currently in use or updated:
  - `actions/checkout@v6`
  - `actions/setup-java@v5`
  - `actions/upload-artifact@v7`
  - `actions/download-artifact@v8`
  - `gradle/actions/setup-gradle@v6`
  - `gradle/actions/wrapper-validation@v6`
- this removes the immediate Node 20 deprecation warning caused by `actions/download-artifact@v4`
- cache-provider behavior for `gradle/actions@v6` remains unchanged for now; only the action version was bumped

## Completed Work

- [x] Stage `xdk-dist-${sha}` in `commit.yml`
- [x] Stage `consumer-maven-repo-${sha}` in `commit.yml`
- [x] Stage `intellij-plugin-dist-${sha}` in `commit.yml`
- [x] Move consumer Maven repo publication into the main Gradle session
- [x] Remove `publishLocal` from `integration-test`
- [x] Make `integration-test` consume only the staged consumer Maven repository artifact
- [x] Add provenance validation in `integration-test` so artifact commit must match `${github.sha}`
- [x] Remove the huge Gradle cache restore from `integration-test`
- [x] Move IntelliJ release-grade build into the main CI Gradle session
- [x] Convert IntelliJ snapshot publication into a downstream artifact-promotion job
- [x] Publish IntelliJ snapshot ZIP to JetBrains Marketplace alpha
- [x] Publish IntelliJ snapshot ZIP to GitHub release assets as a fallback download channel
- [x] Add fast-fail secret validation for IntelliJ publication
- [x] Fix downloaded-artifact path assumptions in IntelliJ promotion
- [x] Add clearer artifact/publishing summaries in `commit.yml`
- [x] Upgrade `actions/download-artifact` to `@v8`
- [x] Upgrade `gradle/actions` usages to `@v6`
- [x] Stage `snapshot-maven-repo-${sha}` in `commit.yml`
- [x] Replace the normal snapshot publication path in `publish-snapshot.yml` with artifact promotion from a staged Maven repository bundle
- [x] Keep a source fallback path only for manual runs without `ci-run-id`

## Highest-Value Remaining Tasks

1. Add release-flow parity for IntelliJ publication:
   - gated staging in [`prepare-release.yml`](../../../.github/workflows/prepare-release.yml)
   - gated promotion in [`promote-release.yml`](../../../.github/workflows/promote-release.yml)
   - default disabled, mirroring the snapshot gating model
2. Generalize artifact metadata/download helpers so XDK, consumer Maven repo, snapshot Maven publication bundle, and IntelliJ bundle all use the same validation pattern.
3. Decide whether Gradle Plugin Portal publication can also become artifact-driven, or document it as the one intentional source-build exception.
4. Suppress or isolate nested Gradle check-run noise from generated-project integration tests so GitHub Checks only shows meaningful repo-level signals.

## Branch Assessment

The branch has completed the core snapshot-path artifact-reuse work:

- snapshot CI now stages commit-scoped artifacts and reuses them downstream
- integration-test consumes the staged Maven repo artifact instead of rebuilding from source
- XDK snapshot publication is artifact-driven
- IntelliJ snapshot publication is artifact-driven and manual-only
- Docker and Homebrew already reuse CI-built XDK artifacts

The remaining items are not part of the same risk profile. They are:

- release-flow feature work
- metadata/helper normalization
- Gradle Plugin Portal exception handling
- GitHub Checks/UI cleanup

Recommended decision for this branch:

- do **not** keep extending it into release-flow parity and helper abstraction work
- land the validated snapshot-path work as-is
- treat the remaining items as explicit follow-up work unless a release-flow change is immediately needed

Rationale:

- the snapshot path is already validated end-to-end
- release workflows are less exercised and higher-risk
- helper abstraction is useful but not required for correctness
- GitHub Checks noise cleanup is orthogonal and should not block the branch

## Validation Summary

The original snapshot-path validation sequence has already been completed on this branch:

1. Baseline no-snapshot/no-IntelliJ path:
   - validated
2. Non-IntelliJ snapshot publication path:
   - validated
   - XDK snapshot, Docker, and Homebrew updates all completed from CI-built artifacts
3. IntelliJ-enabled snapshot publication path:
   - validated
   - IntelliJ snapshot publication reused the staged plugin artifact and updated the stable GitHub snapshot assets in place

Remaining validation work belongs to any future release-flow changes, not to the already-finished
snapshot-path implementation in this branch.

## Non-Goals

- Do not remove local developer workflows like `publishLocal`
- Do not try to solve unrelated Gradle classpath-stability issues here
- Do not switch to a remote package registry as the primary per-commit integration mechanism

## Phase 1: Define The Artifact Contract

### Task 1.1: Define a commit-scoped CI artifact bundle

Create a single logical artifact contract produced by the originating `build-and-test` run.

Suggested top-level structure:

```text
ci-artifacts/
  metadata/
    commit.json
    versions.json
    publications.json
  xdk/
    dist/
    maven-repo/
  plugin/
    maven-repo/
  intellij-plugin/
    distributions/
    verifier/
```

Implementation notes:

- `commit.json` should include commit SHA, branch/ref, CI run ID, and workflow name
- `versions.json` should include XDK version and any publish-specific derived versions
- `publications.json` should list expected coordinates, filenames, and checksums

Status:

- partially complete
- current metadata is artifact-family-specific:
  - `consumer-artifacts.json`
  - `intellij-plugin-metadata.json`
- still worth normalizing into a shared schema
- not recommended to complete in this branch unless a downstream workflow is blocked by the mismatch

### Task 1.2: Decide artifact naming and retention

Define stable GitHub Actions artifact names, for example:

- `xdk-dist-${sha}`
- `consumer-maven-repo-${sha}`
- `intellij-plugin-dist-${sha}`

Implementation notes:

- keep naming aligned with the existing XDK artifact conventions
- make names deterministic from commit SHA to simplify downstream lookup
- keep artifact contents minimal enough to upload/download quickly

Status:

- complete for current snapshot CI artifact families

### Task 1.3: Decide where version suffixes are computed

For any publish path where the artifact embeds its final version:

- compute the final version in the originating CI build
- write it into metadata
- reuse it downstream

Implementation notes:

- IntelliJ Marketplace suffixes must not be recomputed later if the ZIP already contains the version
- downstream jobs should treat version metadata as input, not derive it again

Status:

- complete for IntelliJ snapshot publication

## Phase 2: Extend The Originating CI Build

### Task 2.1: Stage XDK/plugin Maven-consumer artifacts during `build-and-test`

Add a build step in `commit.yml` that assembles a job-local file-backed Maven repository for:

- XDK Maven publication
- Gradle plugin Maven publication
- Gradle plugin marker publication

Implementation notes:

- this should be staged in the workspace, not written to shared Maven Local
- repository layout should be directly consumable as a `maven { url = uri(...) }` repo
- use generated POMs/module metadata from the build, not hand-written placeholders

Status:

- complete

### Task 2.2: Stage IntelliJ plugin distribution artifacts during `build-and-test`

When IntelliJ publication is requested, or on `master`, add a release-grade plugin build in the
originating CI run that produces:

- ZIP
- checksum
- verifier report
- publish metadata

Implementation notes:

- build with `-Plsp.buildSearchableOptions=true`
- use the final publication suffix here, not later
- upload both the binary and metadata

Status:

- complete for snapshot CI and snapshot publication
- not yet mirrored into formal release workflows

### Task 2.3: Upload the artifact bundles

Upload the staged artifact directories as named GitHub Actions artifacts.

Implementation notes:

- one artifact may be enough if the layout is clean
- separate artifacts are also acceptable if that simplifies consumers
- prefer explicit metadata files over trying to infer everything from filenames later

Status:

- complete for current snapshot CI artifact families

## Phase 3: Refactor The Plugin Integration Test

### Task 3.1: Stop using `publishLocal` in CI integration test

Replace the `publishLocal` step in `commit.yml` integration-test with:

1. download staged consumer repository artifact
2. unpack into a workspace-local path
3. point `xtc-app-template` at that local repository

Implementation notes:

- this job should no longer rebuild `:xdk:*:compileXtc`
- it should behave as an external consumer of built artifacts

Status:

- complete

### Task 3.2: Add repository override support to `xtc-app-template` if needed

If the template only supports `-PlocalOnly=true` with Maven Local semantics, add support for:

- `-PlocalRepoPath=...`

or equivalent.

Implementation notes:

- prefer a file-backed repo path over writing into `~/.m2`
- keep `-PlocalOnly=true` for local-dev convenience

Status:

- not needed for the current validated flow
- current approach works by pointing `-Dmaven.repo.local` at the staged repo artifact

### Task 3.3: Verify no second XDK publication build occurs

Acceptance criteria for the integration-test job:

- no `publishLocal`
- no `:xdk:distZip`
- no long `:xdk:*:compileXtc` chain

Status:

- complete

## Phase 4: Refactor IntelliJ Plugin Publication

### Task 4.1: Move IntelliJ plugin build/verify into the originating CI run

When publish is requested:

1. compute publish suffix in `commit.yml`
2. build ZIP in `commit.yml`
3. verify ZIP in `commit.yml`
4. upload ZIP + metadata as CI artifact

Implementation notes:

- downstream publish jobs should no longer own the build identity
- the ZIP built here is the one to be published later

Status:

- complete

### Task 4.2: Convert `publish-intellij-plugin-snapshot` into a promotion job

In `publish-snapshot.yml`, change the IntelliJ publish job to:

1. determine originating CI run
2. download IntelliJ artifact bundle
3. validate presence and metadata
4. upload ZIP to JetBrains Marketplace
5. upload same ZIP to GitHub release asset

Implementation notes:

- remove downstream calls to:
  - `:lang:intellij-plugin:buildSearchableOptions`
  - `:lang:intellij-plugin:buildPlugin`
  - `:lang:intellij-plugin:verifyPlugin`
- if possible, avoid using a Gradle publish task for the upload step if it forces a rebuild graph
- if Gradle upload must remain, make it consume the already-built ZIP and metadata only

Status:

- complete

### Task 4.3: Add an explicit fallback rebuild mode

If manual emergency publication without a CI artifact is still needed, add an explicit workflow
input such as:

- `rebuild-from-source=true`

Implementation notes:

- default should be artifact promotion
- fallback mode should be opt-in and clearly labeled slower

Status:

- not implemented
- still optional
- do not add this in this branch unless emergency manual publication without `ci-run-id` is needed

## Phase 5: Audit Other Downstream Jobs

### Task 5.1: Review all workflows that run after `build-and-test`

Audit:

- `publish-snapshot.yml`
- `publish-docker.yml`
- `homebrew-update.yml`
- release/promotion workflows

Implementation notes:

- identify which jobs already behave as artifact-promotion jobs
- identify which still check out source and rebuild large graphs

Status:

- complete

### Task 5.2: Normalize on one reuse pattern

Pick a standard downstream pattern:

1. download CI artifact
2. validate metadata
3. publish/package/promote

Implementation notes:

- use the same helper actions where practical
- prefer small generalizations of existing artifact-download actions over workflow-specific shell glue

Status:

- partially complete
- the workflows mostly follow the same pattern now, but helper abstraction and metadata shape are still uneven
- not required to land this branch safely

### Task 5.3: Convert snapshot Maven publication into artifact promotion

Refactor the XDK snapshot publication path in [`publish-snapshot.yml`](../../../.github/workflows/publish-snapshot.yml) so it no longer runs `./gradlew publish` from a fresh checkout.

Target shape:

1. originating `build-and-test` run produces a Maven-consumer bundle and a publish-ready snapshot publication bundle
2. `publish-snapshot.yml` downloads that publication bundle
3. downstream job publishes staged artifacts to:
   - GitHub Packages
   - Maven Central Snapshots
   - GitHub release asset

Implementation notes:

- do not try to infer publication identity from source checkout at publish time
- include coordinate metadata, checksums, and expected package list in the staged bundle
- if exact remote publication still requires Gradle task execution, make it consume the staged publication repository rather than rebuilding modules

Status:

- complete

### Task 5.4: Convert release staging into artifact promotion where possible

Refactor [`prepare-release.yml`](../../../.github/workflows/prepare-release.yml) so `stage-artifacts` does not rebuild more than necessary.

Target shape:

1. release-tag build produces:
   - release XDK ZIP
   - release Maven publication bundle
   - release plugin publication bundle
2. GitHub draft release and Maven Central staging consume those outputs

Implementation notes:

- keep the explicit release-tag build for correctness
- but split build from publication, just like snapshot CI now does
- if a single release-tag build must remain, stage all publication bundles from that one build session and never re-enter a second Gradle graph in the workflow

Status:

- incomplete
- this is the highest-value remaining feature follow-up
- recommended as a separate follow-up branch unless release-flow work is immediately needed

### Task 5.5: Decide whether Gradle Plugin Portal promotion can be artifact-driven

Investigate whether [`promote-release.yml`](../../../.github/workflows/promote-release.yml) must keep running `:plugin:publishPlugins` from source, or whether it can publish from a staged plugin publication bundle.

Implementation notes:

- if the portal plugin requires source-driven publication, document that as an intentional exception
- if it can publish from staged plugin artifacts, convert it to the same promotion pattern
- this should be the only acceptable remaining source-build exception if no staged alternative exists

Status:

- incomplete
- current recommendation is to treat this as the likely intentional source-build exception
- do not pursue it further in this branch

### Task 5.6: Keep Docker and Homebrew on the current promotion model

No architecture change is needed for:

- [`publish-docker.yml`](../../../.github/workflows/publish-docker.yml)
- [`homebrew-update.yml`](../../../.github/workflows/homebrew-update.yml)

Implementation notes:

- these already consume CI-built XDK artifacts correctly
- keep them aligned with the shared metadata contract so commit/run provenance can be enforced consistently
- do not over-refactor these unless helper-action simplification materially reduces maintenance

Status:

- complete

## Phase 5A: Standardize Artifact Metadata

### Task 5A.1: Use one metadata schema across staged artifacts

Every staged artifact family should provide a metadata file with at least:

- commit
- branch
- runId
- workflow
- version
- artifact name
- checksum or expected file list

Apply this to:

- XDK ZIP artifact
- consumer Maven repository
- IntelliJ plugin distribution
- future publish-ready Maven snapshot bundle

Status:

- partially complete
- provenance exists, but the schemas are still artifact-family-specific
- useful follow-up, but not urgent for this branch

### Task 5A.2: Add provenance validation to all downstream promotion jobs

Status:

- complete for the current snapshot-era downstream jobs
- integration-test, snapshot publication, Docker, and Homebrew now validate source commit/run provenance before promotion
- any future downstream job should follow the same pattern, but this branch closes the current gap

Downstream jobs should explicitly validate:

- downloaded artifact commit equals source CI commit
- artifact run ID equals the triggering `ci-run-id`
- required files are present before publish starts

Implementation notes:

- the consumer Maven repository provenance check in `integration-test` is now the reference implementation
- Docker and Homebrew now inherit explicit XDK artifact provenance validation through the shared `download-ci-artifact` action
- the remaining follow-up is schema normalization under Task 5A.1, not missing downstream provenance checks

## Phase 6: Instrumentation And Guardrails

### Task 6.1: Add fast-fail checks for required downstream inputs

Downstream jobs should fail early if required inputs are missing:

- missing artifact
- missing metadata
- missing secret

Implementation notes:

- the new `JETBRAINS_TOKEN` check in `publish-snapshot.yml` is one example
- add similar checks for artifact bundle presence and expected files

### Task 6.2: Add simple timing comparisons

Capture before/after timing for:

- integration-test job
- IntelliJ publish job
- any other refactored downstream jobs

Implementation notes:

- avoid overengineering
- a few step-level timings in job summaries are enough

## Phase 7: Validation Checklist

The refactor is successful when all of the following are true:

1. `build-and-test` still produces the authoritative XDK/plugin outputs for a commit
2. integration-test consumes a staged repository artifact instead of `publishLocal`
3. IntelliJ publish consumes a CI-built ZIP instead of rebuilding from source
4. rerunning a publish workflow is cheap and does not re-enter lang/tree-sitter build graphs
5. downstream jobs still fail clearly when artifacts or secrets are missing
6. local developer workflows remain available and unchanged where practical

## Suggested Implementation Order

1. Define and upload the shared consumer artifact bundle
2. Refactor plugin integration test to consume it
3. Refactor IntelliJ publish to consume it
4. Audit and normalize other downstream publish jobs
5. Add instrumentation and cleanup

This order reduces risk because the integration test is the simplest consumer of the staged
artifact bundle and validates the artifact contract before publication workflows depend on it.
