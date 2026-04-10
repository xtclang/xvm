# Plan: Reuse CI Artifacts For IntelliJ Plugin Publication

## Problem

The IntelliJ plugin publication workflow currently rebuilds the plugin from source in the publish
job, even when the publish was triggered from a completed CI run that already built and validated
the same commit.

This is most visible in:

- `.github/workflows/publish-snapshot.yml`
- job: `publish-intellij-plugin-snapshot`

That job currently:

1. checks out the commit again
2. runs `:lang:intellij-plugin:buildSearchableOptions`
3. runs `:lang:intellij-plugin:buildPlugin`
4. runs `:lang:intellij-plugin:verifyPlugin`
5. runs `:lang:intellij-plugin:publishPlugin`

This defeats much of the value of a staged CI publish flow.

This is now visibly painful even on workflow reruns: the downstream publish job still performs a
large amount of repeated Gradle work despite the originating CI run already building the same
commit.

## Why It Is Expensive

The IntelliJ plugin is not isolated from the rest of `lang`.

In the current build:

- `:lang:intellij-plugin` depends on the LSP server fat JAR
- `:lang:lsp-server` depends on the `:lang:tree-sitter` native library configuration

So a source rebuild of the IntelliJ plugin publication path tends to drag in:

- `lsp-server`
- `tree-sitter`
- native-library/resource preparation
- searchable-options headless IDE work

This is why the publication job can end up redoing large parts of the lang toolchain even though
CI already built the commit.

## Evidence In The Current Workflow

The current publish workflow explicitly rebuilds from source:

- [publish-snapshot.yml](/Users/marcus/src/xtclang3/.github/workflows/publish-snapshot.yml)

Key steps:

- `Build IntelliJ plugin ZIP`
- `Publish IntelliJ plugin to JetBrains Marketplace alpha`

Both call `./gradlew ...` directly against source checkout.

There is no artifact download step for the IntelliJ plugin ZIP analogous to how the XDK snapshot
path downloads the CI-built artifact.

In practice, that means a rerun or manual publish behaves more like “build again, then publish”
than “promote the artifact already built for this commit”.

## Comparison With The XDK Snapshot Flow

The XDK snapshot flow already follows the better pattern:

1. build in CI
2. upload artifact
3. downstream publish job downloads that artifact
4. publish without rebuilding from source

The IntelliJ plugin publish flow should match that pattern.

More generally, the CI pipeline should converge on a common model:

1. build once
2. upload commit-scoped artifacts
3. let downstream jobs consume or promote those artifacts

The IntelliJ publish flow currently violates that model.

## Goal

Refactor IntelliJ plugin publication so the publish job consumes artifacts produced by the original
CI build, instead of rebuilding the plugin and its lang dependencies again.

## Recommended Direction

### 1. Build the plugin once in `commit.yml`

When `include-lang=true`, the main CI build should already produce the release-grade plugin ZIP for
the commit when needed for publication readiness.

That build should include:

- `-Plsp.buildSearchableOptions=true`
- `:lang:intellij-plugin:buildPlugin`
- `:lang:intellij-plugin:verifyPlugin`

If publish is not requested, this can remain optional or conditional. But when publish is requested,
the built ZIP should originate from the main CI run.

### 2. Upload a dedicated IntelliJ plugin CI artifact

The main CI run should upload a stable artifact bundle, for example:

- plugin ZIP
- checksum
- verifier report
- version metadata

Suggested contents:

- `lang/intellij-plugin/build/distributions/*.zip`
- `lang/intellij-plugin/build/distributions/SHA256SUMS.txt`
- `lang/intellij-plugin/build/reports/pluginVerifier/**`
- small metadata file containing version, suffix, commit SHA

The metadata should also include:

- Marketplace upload version
- release channel
- original CI run ID

so downstream publish jobs do not recompute publication identity differently from the originating
build.

### 3. Make the publish workflow download the CI-built plugin artifact

The publish workflow should behave like the XDK snapshot publish job:

1. resolve the source CI run
2. download the plugin artifact from that CI run
3. publish the already-built ZIP to:
   - JetBrains Marketplace alpha
   - GitHub prerelease asset

It should not run `buildPlugin` or `verifyPlugin` again.

It should also avoid a second `publishPlugin` build graph that re-enters lang/lsp-server/tree-sitter
from source. The upload step should publish the already-built ZIP directly wherever possible.

### 4. Treat publication as promotion, not rebuild

The publish job should be a promotion step:

- validate artifact presence
- validate metadata
- upload to destination(s)

It should not be another source build.

This rule should apply to all downstream publish jobs, not just the IntelliJ plugin one.

If a publish job needs a heavyweight source rebuild to function, that is a sign the build/publish
boundary is in the wrong place.

## What Still Needs Source Builds

There are only two cases where rebuilding from source in the publish workflow would still make
sense:

1. emergency manual publish without a usable CI artifact
2. explicit maintenance workflow for rebuilding old commits

That should be a fallback mode, not the default path.

If a fallback rebuild mode is kept, it should be explicit in the workflow inputs and clearly labeled
as slower and less deterministic than the normal artifact-promotion path.

## Proposed Workflow Shape

### In `commit.yml`

If:

- branch is `master`, or
- workflow_dispatch explicitly requests IntelliJ publication

then:

1. build the release-grade IntelliJ plugin ZIP once
2. verify it once
3. upload it as a named CI artifact
4. publish metadata needed by downstream promotion jobs

### In `publish-snapshot.yml`

For `publish-intellij-plugin-snapshot`:

1. determine commit / originating CI run
2. download plugin artifact from that CI run
3. compute Marketplace/GitHub-facing version naming if needed
4. publish the downloaded ZIP
5. upload the same ZIP as GitHub release asset

No source rebuild should occur in this path.

More specifically, the downstream publish job should avoid all of the following unless running in an
explicit fallback mode:

- `:lang:intellij-plugin:buildSearchableOptions`
- `:lang:intellij-plugin:buildPlugin`
- `:lang:intellij-plugin:verifyPlugin`
- source-driven lang/lsp-server/tree-sitter rebuilds

## Versioning Consideration

JetBrains Marketplace still requires a unique version per upload.

That does not require a full rebuild from source if the ZIP was already built with a publication
suffix in the originating CI run.

So if Marketplace publication is requested, the version suffix should be decided in the original CI
build, recorded in metadata, and then reused by the publish job.

Do not compute the publish suffix only in the downstream publish workflow if the ZIP itself embeds
that version.

## Important Consequence

If publication suffixing is part of the plugin version baked into the ZIP, then the CI build and the
publish job must agree on the version before the ZIP is built.

That means:

- the suffix should be determined in `commit.yml`
- the built ZIP should already carry that final Marketplace version
- the publish workflow should only upload that exact ZIP

This is the key requirement that lets artifact promotion replace rebuilds cleanly.

## Fallback Option

If you do not want the main CI build to always do the release-grade IntelliJ build, then keep it
conditional:

- only when `publish-intellij-plugin=true`
- or on `master`

But even then, the build should happen in the original CI run, not in the later publish workflow.

If a manual publish is triggered without a `ci-run-id`, the workflow can either:

- fail fast and ask for a CI run ID, or
- require an explicit `rebuild-from-source=true` override

The current implicit rebuild behavior is too expensive and too surprising.

## Broader CI Reuse

This plan should be coordinated with:

- [`plugin/doc/plans/integration-test-artifact-reuse-plan.md`](../../../plugin/doc/plans/integration-test-artifact-reuse-plan.md)

Both plans point toward the same broader cleanup:

- one commit-scoped consumer-facing artifact bundle
- multiple downstream jobs consuming it
- minimal rebuild work outside the original CI build

The IntelliJ publish job is just the most visible case today because it drags lang/tree-sitter work
into what should be a promotion workflow.

## Validation Plan

After refactoring:

1. `publish-intellij-plugin-snapshot` should not invoke `:lang:intellij-plugin:buildPlugin`
2. it should not invoke `:lang:intellij-plugin:verifyPlugin`
3. it should not pull `lsp-server` / `tree-sitter` into the downstream publish job
4. the uploaded Marketplace version should match the metadata produced in the original CI run
5. the GitHub release asset should be byte-identical to the CI-built ZIP
6. retrying or rerunning the publish workflow should not repeat heavyweight lang build work beyond
   artifact download and upload/promotion steps

## Expected Outcome

If this is implemented correctly:

- publish workflows become much faster
- publication becomes deterministic and easier to reason about
- lang/tree-sitter work happens once in CI, not again in promotion jobs
- IntelliJ plugin publication matches the existing XDK artifact-promotion pattern instead of
  rebuilding from source
- reruns become cheap enough to be operationally sane
