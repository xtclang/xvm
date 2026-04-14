# Plan: Reuse Built Artifacts In Plugin Integration Test CI

## Problem

The current plugin integration test in [`commit.yml`](../../../.github/workflows/commit.yml)
verifies the external consumer path by:

1. checking out the repository in a fresh job
2. running `./gradlew publishLocal`
3. cloning `xtc-app-template`
4. running the template against Maven Local

This works, but it is expensive for the wrong reason.

The job is intended to verify that the XDK/plugin outputs produced by CI can be consumed by an
external build. Instead, it rebuilds the XDK publication graph from source in a second job.

## Evidence

In GitHub Actions run `24263389480`, job `70853721715`, the integration test does:

```text
./gradlew ... publishLocal
```

That second invocation does not download the `distZip` artifact from the earlier `build-and-test`
job. It recomputes the publication from the checked-out source tree.

The log shows:

- `:plugin:publishToMavenLocal`
- `:xdk:publishToMavenLocal`
- `:xdk:distZip`
- a long chain of `:xdk:*:compileXtc` tasks

So while Gradle cache reuse helps, the job still pays for the XDK compile/publish graph again.

## What Is Reused Today

Some reuse is already happening:

- Gradle caches are restored in the integration-test job
- many setup and extraction tasks are `FROM-CACHE`
- several Java/build-logic tasks are avoided or reduced

That is useful, but it is not enough. The dominant cost is still the live XDK `compileXtc` chain
required by `publishLocal`.

## Broader CI Context

This waste is not isolated to the plugin integration test job.

The same pattern shows up elsewhere in the pipeline:

- downstream publish jobs start from a fresh checkout
- Gradle cache restore saves some work
- but downstream jobs still rerun expensive source-based publication/build graphs

So this plan should be treated as one piece of a broader CI artifact-promotion cleanup.

The core design rule should become:

- downstream jobs may validate, package, or publish
- but they should not rebuild heavyweight source outputs already produced by the originating CI run

The plugin integration test is one of the clearest examples because it currently redoes the XDK
publication graph to produce artifacts that already conceptually exist.

## What This Is Not

This is related to, but distinct from, the classpath-stability issue described in
[`javatools-loading-plan.md`](./javatools-loading-plan.md).

That plan explains why a follow-up invocation can see certain XTC tasks become stale because plugin
or build-logic runtime classpaths changed.

That issue may still add some extra churn, but it is not the primary reason for the integration-test
job cost. The bigger architectural issue is simply that the job re-publishes from source instead of
reusing the artifacts already built by CI.

## Goal

Refactor the integration test so it verifies:

- the XDK artifact built by CI
- the plugin artifact built by CI
- external consumer behavior via `xtc-app-template`

without recompiling the XDK publication graph in the integration-test job.

## Desired Shape

### Build-and-test job

Continue to build the XDK/plugin once and publish CI artifacts that downstream jobs can consume.

The relevant outputs should include:

- the built XDK ZIP used for publication
- the published Gradle plugin Maven artifacts, or enough material to reconstruct a local repository
- metadata describing version and coordinates

### Integration-test job

Consume those CI artifacts directly instead of running `publishLocal` from source.

The job should:

1. download the CI artifacts from `build-and-test`
2. stage them into a temporary local Maven repository or equivalent file-backed repository
3. point `xtc-app-template` at that staged repository
4. run the template integration test

## Candidate Approaches

### Option 1: Publish a synthetic local Maven repository as a CI artifact

During `build-and-test`, stage the needed outputs into a directory structured like a Maven repo, for
example:

- `org/xtclang/xdk/...`
- Gradle plugin marker/module metadata for the plugin
- plugin publication artifacts and POMs

Then upload that repository directory as a CI artifact.

In `integration-test`:

1. download the repository artifact
2. expose it as a file repository
3. configure `xtc-app-template` to resolve only from that local staged repo

Pros:

- closest to real published-consumer behavior
- simple, explicit, deterministic inputs
- avoids rerunning Maven publication tasks

Cons:

- requires packaging and maintaining a mini repository artifact
- may need care for Gradle plugin marker resolution

### Option 2: Download raw publication outputs and install them into a temp Maven local

During `build-and-test`, upload:

- XDK ZIP
- plugin publication jars/zips
- generated POM/module files
- publication metadata

In `integration-test`, reconstruct a temporary local Maven repo layout from those files.

Pros:

- more compact than shipping all of `~/.m2`
- still tests real publication outputs

Cons:

- more custom assembly logic in CI
- easy to get wrong if metadata/layout changes

### Option 3: Use GitHub Packages or another remote snapshot repository for this job

Have `build-and-test` or a downstream publish step push the artifacts to a temporary remote
repository, then have `xtc-app-template` resolve them from there.

Pros:

- matches true external consumption closely

Cons:

- more credentials and external side effects
- slower and more fragile than pure CI artifact reuse
- overkill for a per-commit integration test

## Recommended Direction

Prefer **Option 1**.

The integration-test job is a consumer verification job, not a publication-from-source job. The
cleanest way to model that is to stage a file-backed repository artifact from the earlier build and
consume it directly.

This keeps the concerns separate:

- `build-and-test` builds
- artifact staging packages consumer-facing outputs
- `integration-test` consumes those outputs

This recommendation also aligns with the publication-job cleanup plan in:

- [`lang/doc/plans/intellij-plugin-publication-artifact-reuse-plan.md`](../../../lang/doc/plans/intellij-plugin-publication-artifact-reuse-plan.md)

Both plans are really asking for the same underlying change:

- a first-class CI artifact bundle representing the consumer-facing build outputs for a commit

## Required Workflow Changes

### 1. Extend build-and-test outputs

Add a step that stages the external-consumer artifacts into a directory, for example:

- `ci-artifacts/maven-repo/`

Contents should include:

- XDK Maven publication files
- plugin Maven publication files
- plugin marker publication files
- metadata file with version and commit SHA

Then upload that directory as a named artifact.

This artifact should be treated as a reusable contract, not a job-local convenience.

Suggested bundle categories:

- XDK distribution artifacts
- Maven publication-ready files for XDK/plugin/plugin-marker
- metadata describing versions, commit SHA, and publication coordinates

That same bundle can then feed:

- plugin integration test
- downstream snapshot publication jobs
- future smoke tests for external consumers

### 2. Change integration-test to download staged artifacts

Replace:

```text
./gradlew publishLocal
```

with:

1. download staged repository artifact
2. unpack to a temporary directory
3. configure the template build to resolve from that directory only

### 3. Avoid global Maven Local when possible

Prefer a job-local repository path over writing into the runner's shared Maven Local location.

For example:

- a temporary repo under the workspace
- template invocation configured with a property like `-PlocalRepoPath=...`

This makes the integration test:

- more hermetic
- easier to debug
- less coupled to Maven Local semantics

### 4. Keep a separate local-dev path

Do not remove `publishLocal`. It is still useful for local developer workflows.

The change should be specific to CI integration testing.

### 5. Keep cache reuse, but stop depending on it for correctness

Gradle cache restore is still valuable and should remain enabled.

But downstream CI jobs should not rely on cache warmth to be reasonably fast. Artifact reuse should
provide the main efficiency win; Gradle cache reuse should be a secondary optimization.

## Template-Side Considerations

`xtc-app-template` currently supports:

- `-PlocalOnly=true`

That behavior likely assumes Maven Local.

To support artifact-reuse CI cleanly, the template may need a small enhancement such as:

- `-PlocalRepoPath=/path/to/staged/repo`

or equivalent repository override support.

If that is not available today, it is the one likely required change outside this repository.

## Validation Plan

After the refactor:

1. `build-and-test` should still produce the normal XDK/plugin outputs.
2. `integration-test` should no longer run `publishLocal`.
3. `integration-test` should no longer execute the long `:xdk:*:compileXtc` chain.
4. `xtc-app-template` should still compile and run successfully against the staged artifacts.
5. The job should fail clearly if staged publication metadata is incomplete or mismatched.
6. The same staged artifact contract should be reusable by downstream publish jobs without requiring
   a second source rebuild.

## Expected Outcome

If this is implemented well:

- integration-test runtime should drop substantially
- the test will better represent actual consumer usage
- CI will stop rebuilding the XDK publication graph in a second job
- publication verification will become easier to reason about than the current `publishLocal`
  approach
- the CI pipeline will have a clearer separation between build, consume, and promote phases
