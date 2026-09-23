# Submission plan for the embedded runtime branch

This is the proposed review and submission sequence for `lagergren/embedded-gradle-runtime`,
not a set of PR descriptions or an instruction to publish branches. It records concrete scopes
for constructing smaller branches later. No split branches have been created or validated yet.

Source snapshot: `origin/master` at `6539aa6eb`; the original embedding implementation ends at
`c884779d6`. The keystore correction is now commit `ca52e2aae`, followed by the common ownership
mechanism/audit in `9b51e0f23`. The separate native migration commit is titled
**Release application-owned native resources between embedded runs**. Recheck the revisions and
working diff before extraction. The [implementation plan](embedded-runtime-plan.md) explains the
design and measurements; the [resource audit](../../../doc/embedding-resource-ownership.md)
records historical leaks, their corrections and current validation.

## Recommended sequence

| PR | Concrete scope | Required predecessors | Extraction status |
|---|---|---|---|
| 1 | Close the certificate-manager keystore stream | None | Commit `ca52e2aae`; separate from that file's IO scheduling change |
| 2 | Avoid reloading definitions already present during runtime linking | None | Existing commit `b8d031f2d` is the boundary |
| 3 | Interpreter activity tracking and bounded runtime shutdown | None; submit after 2 for a simple sequence | Extract runtime/util/native hunks from `d5947a903`, plus the generic service-context cleanup in `c884779d6` |
| 4 | Common native-resource acquisition and release mechanism | 3 | Code from `9b51e0f23`; audit tests/docs in that commit also need the scopes described below |
| 5a | Owned, headless interpreter embedding sessions | 3; 4 is recommended ordering, not an API dependency | Extract basic session/control/runner lifecycle and isolation coverage |
| 5b | Explicit interpreter run requests, resource context and xUnit | 5a | Extract request configuration, resource-provider/repository fixes and their regressions |
| 4b | Apply native ownership to channels, sockets, watches, HTTP and callbacks | 4 and 5b for the integration tests | Separate native migration commit; exact six scopes and test boundaries below |
| 6 | File compilation through the embedding API | 5b (the source-tree execution regression uses `RunRequest`) | Extract the file-compiler API and source-tree tests from `d5947a903` |
| 7 | Reuse embedding sessions for Gradle DIRECT compile/run/xUnit | 5b and 6 | Extract plugin and bootstrap-consumer changes from `d5947a903` |
| 8 | Experimental JIT execution through the owned embedding session | 5b; include the small plugin adapter after 7 | Extract JIT changes from `c884779d6`; exclude manual-test rollout |
| 9 | Make manualTests use DIRECT by default | 4b, 7 and 8 | Only the manual-test convention and Gradle preview startup changes from `c884779d6` |

PRs 1 and 2 can be submitted independently. PRs 3–8 form an implementation sequence, but PR 6
does not need JIT and PR 8 does not need the manual-test default. Keep PR 9 separate so reviewers
can approve the capability without also changing which execution mode CI/manual builds exercise.
The general plugin default stays ATTACHED throughout.

The existing commit history is **not** this PR sequence. `d5947a903` mixes lifecycle, request
isolation, compilation, xUnit and Gradle adoption. `c884779d6` mixes JIT, interpreter correctness
fixes and the default-mode change. Except for PR 2, cherry-picking either whole commit would defeat
the split. Reconstruct the listed hunks on top of their predecessors, then run that branch's checks.

### Review size and readiness

The recommended sequence now has **eleven PRs**: the original ten extraction scopes plus PR 4b
for native-resource integration. Submit 4b after 5b so its real-runner regressions are available.
These are scope judgments from the actual diff, not quality scores or validated extraction counts.

| Scope | Review assessment |
|---|---|
| 1 and 2 | Small, independent fixes with narrow regressions; easiest to submit first. |
| 3 | Broad foundational review across runtime, bridge and native call sites. Keep its completion protocol together; review the IO/timer conversions as a separate commit within this PR. |
| 4 | Bounded new ownership API with substantial race/failure tests. Current local implementation passed its 12 unit cases and the combined lifecycle regressions; no native-handle migrations are included. |
| 4b | Substantial native cleanup review with six explicit subscopes. Keep separate from the framework API, plugin adoption and rollout; further splitting remains possible using R1–R6 below. |
| 5a | A substantial lifecycle review, but smaller than the combined embedding scope. Keep session ownership, runner release and cancellation together. |
| 5b | A focused API/protocol extension with two integration regressions. Requires the explicit intermediate reconstruction described below. |
| 6 | Small compiler adapter and file-tree regressions. |
| 7 | Moderate plugin/service change; request dispatch and service/classloader lifetime are one unit. |
| 8 | Moderate, independent backend review with explicit limitations and five focused regressions. |
| 9 | Small rollout change with broad execution-mode verification, not a runtime implementation PR. |

Do not describe the combined scope 5 as a small PR: it includes both lifecycle and request/xUnit
capabilities. The 5a/5b split is recommended for submission. Combining them is a fallback only if
reconstructing and validating the intermediate protocol would create disproportionate churn.
All extracted PRs still need fresh validation; passing checks on the combined branch do not prove
that an intermediate state compiles or runs.

The earlier framework milestone executed 12 ownership tests, 14 keystore-operation tests,
7 existing runtime tests and 16 embedding lifecycle tests: 49 tests, with zero failures, errors
or skips. Commands below are the recommended per-extraction validation matrix, not a claim that
every exact command or every intermediate branch has already passed. The native integration
validation is recorded separately in the resource audit: 48 Java tests with zero skips/failures,
all 21 sequential manual modules, formatting and configuration-cache reuse.

## PR 1 — certificate-manager stream lifetime

Take the production change from `ca52e2aae` to
`javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTCertificateManager.java`:
the filesystem path in `loadKey()` delegates to `KeyStoreOperations.extractKey()`, which owns and
closes its input stream. Preserve the existing in-memory `KeyStoreHandle` path. Remove the unused
`FileInputStream` import.

Exclude the same file's `frame.scheduleIO()` conversion: that belongs to PR 3. This fix is
independent of embedding and must apply directly to master. Include
`KeyStoreOperationsTest.testExtractKeyRejectsWrongPasswordAndCanBeRetried`, added by the current
work, in this PR rather than the runtime ownership PR.

Validation:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.template._native.crypto.KeyStoreOperationsTest \
  --tests org.xvm.runtime.template._native.crypto.KeyStoreCompatibilityTest \
  --rerun-tasks --no-build-cache
```

Read the test XML for executed cases and failures. This PR establishes scoped stream ownership;
it does not establish general native-handle disposal.

## PR 2 — runtime linking preparation

Take commit `b8d031f2d` as a unit:

- `javatools/src/main/java/org/xvm/asm/FileStructure.java`: use module definitions already present
  in the file during runtime linking while still resolving their missing dependencies.
- `javatools/src/test/java/org/xvm/asm/FileStructureTest.java`: the two new runtime-linking tests
  and their helper/import changes.

Exclude repository ownership changes, compiled-metadata caches and all runtime/plugin work.
This is the measured preparation fix already on the branch, not the rejected shared-definition
cache experiment. It is technically independent of the other PRs.

Validation targets exactly the added cases, avoiding the unrelated pre-existing disabled tests:

```bash
./gradlew :javatools:test \
  --tests org.xvm.asm.FileStructureTest.testRuntimeLinkUsesDefinitionsAlreadyInTheFile \
  --tests org.xvm.asm.FileStructureTest.testRuntimeLinkStillRequiresDependenciesOfEmbeddedModules \
  --rerun-tasks --no-build-cache
```

## PR 3 — interpreter activity and runtime shutdown

Keep the runtime completion protocol together. An executor shutdown method without IO ownership,
container idleness and the matching service notifications would not implement the intended contract.

Include these scopes from the committed branch:

- `javatools/src/main/java/org/xvm/runtime/Container.java`: descendant-aware idleness, background
  failures, IO cancellation/completion, tracked timers and the original asynchronous termination
  hook. Leave the new `OwnedResource` registry replacement for PR 4.
- `Runtime.java`: runtime-owned timer, container registration during shutdown, default/overridden
  shutdown budget, both executor lifecycles and explicit termination status.
- `Fiber.java`: find the requesting application's resource owner through a shared service call.
- `Frame.java`: request-owned `scheduleIO()` and the external-completion wake-up fix. Exclude
  the local `acquireResource()` overloads until PR 4.
- `ServiceContext.java`: idleness notifications, background-error recording, removal of already
  terminated fibers, runtime-owned timer scheduling and removal of empty thread-local bindings.
  Include the `getCurrentContext()` empty-holder change even though it landed in the JIT commit.
- Under `javatools/src/main/java/org/xvm/runtime/template/_native/`, the IO scheduling conversions
  in `crypto/xRTCertificateManager.java`, `fs/xOSFile.java`, `fs/xRawOSFileChannel.java`,
  `net/xRTNameService.java`, `net/xRTSocket.java` and `web/xRTConnector.java`; the timer changes in
  `temporal/xLocalClock.java` and `temporal/xNanosTimer.java`; and the session-owned watch dispatcher
  and asynchronous close in `fs/xOSStorage.java`.
- The complete `Container.join()` declaration/bridge/native implementation trio:
  `lib_ecstasy/src/main/x/ecstasy/mgmt/Container.x`,
  `javatools_bridge/src/main/x/_native/mgmt/ContainerControl.x` and
  `javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerControl.java`.
- `javatools_utils/src/main/java/org/xvm/util/Deadline.java` and `TransientThreadLocal.java`, with
  `DeadlineTest` and `TransientThreadLocalTest`.
- `ContainerActivityTest`, `ExternalCompletionTest` and `RuntimeShutdownTest` under
  `javatools/src/test/java/org/xvm/runtime/`.

Exclude the embedding facade, runner registry changes, plugin, JIT execution and local common
ownership work. The Java shutdown machinery and the `.x` join bridge must compile together, but
the runner begins using that join only in PR 5. Session watch-dispatcher shutdown does not remove
individual requests' subscriptions.

Validation:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.ContainerActivityTest \
  --tests org.xvm.runtime.ExternalCompletionTest \
  --tests org.xvm.runtime.RuntimeShutdownTest \
  :javatools_utils:test \
  --tests org.xvm.util.DeadlineTest \
  --tests org.xvm.util.TransientThreadLocalTest \
  --rerun-tasks --no-build-cache
./gradlew :xdk:installDist
```

The installed distribution checks the cross-language join declaration. These unit tests do not
assume that compiled XDK modules exist. This is the broadest foundational PR; most native-file
changes are call-site conversions required to use the same ownership boundary.

## PR 4 — common native-resource ownership

Take the common mechanism from `9b51e0f23` as its own change on top of PR 3:

- New `javatools/src/main/java/org/xvm/runtime/OwnedResource.java`.
- The corresponding local `Container.java` hunks: reserve ownership before acquisition, remove
  registrations after cleanup, await acquisition/cleanup during termination, record cleanup
  failure and adapt `onTermination()` to the common mechanism.
- The local `Frame.java` acquisition helpers, using the logical request owner already introduced
  by PR 3.
- `javatools/src/test/java/org/xvm/runtime/OwnedResourceTest.java`: the deterministic ownership
  tests, including acquisition/shutdown races, explicit-close removal, cleanup failures and the
  requesting-application owner when a shared service creates a resource.
- The framework status and native integration boundaries in `doc/embedding-resource-ownership.md`.

Keep this separate from PR 1. Do not copy the complete current `Container.java` onto master: it
also contains PR 3. Extract the incremental registry replacement after PR 3's version.

Acceptance requires idempotent explicit/termination cleanup, bounded waiting using the existing
deadline, no cleanup under owner/resource monitors, failure propagation, cancellation-proof
completion tracking, rejected post-shutdown acquisition, and no retained registration after normal
close. Tests should coordinate races with latches/futures, not sleeps or elapsed-time assertions.

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.OwnedResourceTest \
  --tests org.xvm.runtime.ContainerActivityTest \
  --tests org.xvm.runtime.RuntimeShutdownTest \
  --rerun-tasks --no-build-cache
```

Re-run the watcher lifecycle cases after PRs 5a/5b are assembled because the existing dispatcher
uses `onTermination()`. The framework version has 12 ownership cases; PR 4b adds callback tests. Confirm the appropriate
cases execute after each extraction.

**Scope limit:** this API does not automatically fix existing file channels, sockets, individual
watch subscriptions, HTTP objects or cancelled callbacks. Native templates must opt into it.
Those migrations are implemented in the separate PR 4b scope; a green framework test alone is
not evidence of closed handles.

## Scope 5 — interpreter embedding, submitted as PRs 5a and 5b

This is the combined interpreter API/runner scope. Keep both sides of each runner protocol change
in the same PR: Java sends its arguments positionally, so changing one side alone is not a valid
boundary. It technically needs PR 3, not PR 4: the embedding facade does not yet call
`acquireResource()`. Landing PR 4 first is the recommended sequence for the ownership work.

Include:

- `javatools/src/main/java/org/xvm/api/EmbeddingSupport.java`: owned/lazy interpreter session,
  request controls and failure state, bounded close, request-local repositories, serialized
  preparation, pool scopes, assembled publication of in-memory compilation, and interpreter run
  overloads. Exclude `FileCompiler` integration and file-compilation overload rewrites until PR 6;
  exclude all JIT session fields/backend dispatch until PR 8.
- `InterpreterConnector.java` and `InterpreterControl.java`: headless startup, initialization
  failure cleanup, method/argument selection, request consoles/roots/injections, interruption,
  request release and connector close.
- `RunRequest.java` in its interpreter-only form from `d5947a903`, including injections and host
  filesystem context. Add the backend field/enum only in PR 8.
- The minimal `JitControl.close(Duration)` signature adaptation from `d5947a903` so it still
  implements the changed `Control` interface. This is compilation compatibility, not enabled JIT.
- `lib_runner/src/main/x/runner.x`: task registration arguments, release even after the entry
  method returns, join before reporting completion, and request resource provider. Include the
  repository/compiler/linker/injector and string-array injections needed for nested xUnit.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java` and `LinkedRepository.java`, with
  `ConstantPoolScopeTest` and `LinkedRepositoryIsolationTest`: clean host scope restoration and
  copied versioned read-through modules.
- `xCoreRepository.java` and `xRTFileTemplate.java` under the native templates: nested linking
  must use the repository represented by the request's handle. These are interpreter correctness
  fixes carried by `c884779d6`, not JIT-only changes.
- `xdk/build.gradle.kts`: installed distribution as a declared test input; the documented,
  configurable test-worker heap allowance needed for the nested xUnit regression. No new daemon
  heap/metaspace policy belongs here.
- Interpreter integration tests and helpers from `xdk/src/test/java/org/xvm/xdk/EmbeddingLifecycleTest.java`:
  `headlessSessionReusesRuntimeAndIsolatesRepeatedModules`,
  `failedRequestDoesNotContaminateTheNextRequest`,
  `failedRequestCanCloseWithAnOutstandingServiceCall`,
  `joinReportsAnUnhandledBackgroundFailure`,
  `requestClosePreservesCallerRootAndDeletesOwnedRoot`,
  `sessionCloseCancelsWaitingRequestsAndAllowsANewSession`,
  `nestedModuleResolutionUsesTheRequestRepository`,
  `xunitUsesRequestInjectionsAndReportsFailures` and `sessionCloseStopsFilesystemWatchers`.
  Keep the current audit expansion of the watcher case and its
  `xdk/src/test/resources/ownership/Watching.x` fixture together.
- The assertion-based watcher and explicit channel-close coverage in
  `manualTests/src/main/x/files.x`. It remains the existing `TestFiles` module, with no new suite
  dependency or automatic JIT run. The code can be independently reviewed as a test-only commit.
- Interpreter design/limitations from `embedded-runtime-plan.md` and the resource audit. Separate
  actual guarantees from missing native-handle migrations in both documents.

### PR 5a — session ownership and the existing run entry point

From the combined list above, include:

- The `EmbeddingSupport.create()`/close/owned-control/lazy-interpreter machinery, serialized
  in-memory compilation and assembly, pool scopes, and fresh repositories for the existing
  `run(...)` API. Do not add `RunRequest` or configurable entry methods yet.
- The entire `InterpreterConnector` lifecycle change. In `InterpreterControl`, include headless
  startup, failure cleanup, temporary root and console ownership, interruption and bounded release;
  retain the old fixed `run()` registration signature for this intermediate branch.
- In `runner.x`, include `releaseTask`, retained task/container ownership after entry completion,
  join-before-result behavior and corrected `kill()`/registry removal. Keep the original
  registration arguments and basic resource-provider shape until 5b. These Java and `.x` versions
  must agree in the same commit; restoring the intermediate signature is deliberate extraction work.
- The `Control.close(Duration)` contract and minimal `JitControl` signature adaptation, plus
  `ConstantPool`/`LinkedRepository` and their two unit-test classes.
- The installed-distribution test input declaration in `xdk/build.gradle.kts`.
- The seven lifecycle tests in the combined list excluding the nested-resolution and xUnit tests.
  For `sessionCloseStopsFilesystemWatchers`, use the original simple success/session-close case
  from `d5947a903`; the current audit expansion uses entry arguments and therefore lands in 5b.
- The manual `TestFiles` assertions and lifecycle/limitations documentation appropriate to this API.

Exclude configurable request injections, host filesystem mode, nested-provider resources, native
repository-handle resolution changes, file compilation, plugin use and JIT enablement.

### PR 5b — configured requests and nested execution

Include all remaining scope-5 production hunks:

- Interpreter-only `RunRequest`, the matching `EmbeddingSupport.run(RunRequest, ...)` overload
  and routing from legacy overloads; configurable method/arguments, host filesystem context and
  request-local string/list injections in `InterpreterControl`.
- The matching positional protocol extension in `runner.x`, invocation arguments and expanded
  `TaskResourceProvider`, including repository/compiler/linker/masked injector support.
- `xCoreRepository`/`xRTFileTemplate` repository-handle resolution and the nested-resolution test.
- `xunitUsesRequestInjectionsAndReportsFailures` and the independently configurable XDK test heap
  setting it requires.
- The current `sessionCloseStopsFilesystemWatchers` audit expansion and `ownership/Watching.x`
  fixture. Unlike the simple 5a case, this runs success/failure/cancellation outcomes using
  `RunRequest` arguments and checks caller ownership through subsequent requests.
- The matching resource-context/xUnit/audit documentation.

This is the exact 5a/5b boundary; copying current whole API/runner files into 5a is incorrect.
Run the same scope-5 commands below after each PR. The XDK lifecycle class has seven cases at 5a,
then nine at 5b (one existing case expands). Neither includes the later compiler or JIT tests.

Do not split xUnit resource injection from its interpreter regression merely because the Gradle
adapter comes later. This API should prove it can run xUnit without invoking `TestRunner` itself.
In-memory assembly before publication also belongs here: copied compiled modules must be safe
before any execution consumer uses them.

One test needs reconstruction rather than a straight copy:
`nestedModuleResolutionUsesTheRequestRepository` currently calls the file-compilation overload
assigned to PR 6. For PR 5b, compile its dependency, consumer and loader source strings through
`session.compile(source, inputRepository, errors)` and store them in a fresh `BuildRepository`.
Retain both expected values (41 and 42), the same session, a fresh request repository for each
generation, and the nested-container resolution assertions. This preserves the actual regression
without pulling the file compiler forward or invoking a launcher. Validate the adapted test before
claiming the separation works; if that adaptation fails, bring PR 6 into this PR rather than leave
the repository fix without coverage. Keep the adapted version later instead of gratuitously
rewriting it again when the file compiler lands.

Validation:

```bash
./gradlew :javatools:test \
  --tests org.xvm.asm.ConstantPoolScopeTest \
  --tests org.xvm.asm.LinkedRepositoryIsolationTest \
  :xdk:test --tests org.xvm.xdk.EmbeddingLifecycleTest \
  --rerun-tasks --no-build-cache
./gradlew :manualTests:runOne --mode=ATTACHED -PtestName=TestFiles \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

On this extracted PR, the lifecycle class contains only the listed interpreter cases; the compiler
and JIT cases have not been added. Its test task provisions the XDK. Do not move these integration
tests to `javatools:test` with assumptions that silently skip missing binaries.

Readiness: implemented and exercised on the combined branch, but the reconstructed intermediate
branch must be validated. Its guarantees concern owned runtime workers, request state, roots,
consoles and pending work. Idle native-handle/subscription cleanup is still incomplete, as the
audit explicitly demonstrates.

## PR 6 — embedded file compilation

Include `javatools/src/main/java/org/xvm/api/FileCompiler.java` and only these remaining
`EmbeddingSupport.java` hunks: `compile(CompilerOptions, Console, ErrorListener)` and replacement
of `compile(File, ...)` with the standard source-tree/resource/emission pipeline. Preserve lazy
execution startup: compiling bootstrap modules must not need an already-built `runner.xtc`.

Add `fileCompilationIncludesNestedSourcesResourcesAndFreshOutputs` and
`legacyFileCompilationLoadsASourceTree` to the XDK lifecycle class, including their file-creation
helpers/imports. Exclude Gradle adoption, JIT and shared compiler-metadata caches.

```bash
./gradlew :xdk:test \
  --tests org.xvm.xdk.EmbeddingLifecycleTest.fileCompilationIncludesNestedSourcesResourcesAndFreshOutputs \
  --tests org.xvm.xdk.EmbeddingLifecycleTest.legacyFileCompilationLoadsASourceTree \
  --rerun-tasks --no-build-cache
```

The full bootstrap build in PR 7 validates the plugin consumer. Keeping this API separate gives
reviewers a small source-tree compilation change before the plugin begins relying on it.

## PR 7 — Gradle DIRECT uses embedding

Include the complete interpreter compile/run/xUnit adapter and service lifetime together:

- `plugin/src/main/java/org/xtclang/plugin/launchers/DirectStrategy.java`.
- Under `plugin/src/main/java/org/xtclang/plugin/runtime/`:
  `DirectCompileRequest.java`, `DirectRunRequest.java`, `DirectTestRequest.java`,
  `DirectRuntimeBuildService.java`, `DirectRuntimeFingerprint.java`, `PluginRuntimeClassLoader.java`,
  `impl/IsolatedDirectExecutor.java` and `impl/IsolatedLauncherOptionsBuilder.java`.
- `plugin/src/main/java/org/xtclang/plugin/tasks/XtcLauncherTask.java` and
  `plugin/src/test/java/org/xtclang/plugin/runtime/DirectRuntimeFingerprintTest.java`.
- `javatools/src/main/java/org/xvm/tool/Launcher.java`: the static version-reporting helper used
  by the adapter. Calling this helper does not launch a compiler or runner.
- `lib_json/build.gradle.kts` and `lib_metrics/build.gradle.kts`: declared runner dependencies
  for bootstrap xUnit execution before an installed XDK exists.
- The within-build service design, bootstrap behavior, configuration-cache results and performance
  measurements in `embedded-runtime-plan.md`. Label measurements as results of the stated full
  configuration; do not attribute the entire gain to this one PR or to Java startup alone.

Extract the interpreter-only adapter from `d5947a903`. Leave DIRECT JIT rejected until PR 8.
Do not leave compile or xUnit on repeated launcher calls as an intermediate implementation.
Fingerprinting, selected-XDK class isolation, per-request output handling, restored context loader,
service shutdown and task `usesService()` registration are part of the same correct reuse boundary.

Exclude `manualTests/build.gradle.kts` and `gradle.properties`; opt in explicitly for validation.
The manual tests already request preview at JVM startup, so before PR 9 pass an explicit Gradle
JVM argument string preserving the existing daemon settings and adding `--enable-preview`.

```bash
./gradlew :plugin:test spotlessCheck
./gradlew :xdk:build :manualTests:runSequential :manualTests:runParallel \
  :manualTests:runXunitTests :manualTests:runTestAllExecutionModes \
  -PxtcDefaultExecutionMode=DIRECT \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 --enable-preview' \
  --configuration-cache --info -x spotlessApply
```

Repeat the second command unchanged to verify stored configuration-cache reuse with actual run
tasks executing. Caches remain enabled for this check. Force Java test re-execution separately
with `--rerun-tasks --no-build-cache` when needed and inspect XML. This command preserves existing
memory settings for the temporary preview override; it does not introduce new production tuning.

Readiness: the implementation plan records combined-branch DIRECT build/manual-suite checks;
the command above is the recommended extraction gate, not an attestation for a constructed PR.
Fresh intermediate validation is still required. Keep DIRECT opt-in and document the resource
limits; shared-service reuse must not be described as complete automatic native-resource cleanup.

## PR 8 — experimental embedded JIT

Include the JIT-specific API/runtime boundary from `c884779d6`:

- Remaining JIT hunks in `EmbeddingSupport.java`, the backend extension of `RunRequest.java`,
  and the full `JitControl.java` implementation beyond PR 5's signature adaptation.
- `javatools/src/main/java/org/xvm/javajit/JitConnector.java`, `NativeTypeSystem.java` and `Xvm.java`.
- `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java` and
  `org/xtclang/_native/mgmt/nMainInjector.java`: per-request output/injections and explicit rejection
  of embedded input.
- JIT backend selection, diagnostics and explicit JIT-xUnit rejection in
  `plugin/.../runtime/impl/IsolatedDirectExecutor.java`.
- The five `jit*` tests and the `runJit`/blocked-worker helpers in `EmbeddingLifecycleTest.java`.
- `doc/jit-embedding.md`, including its current local follow-up additions, and the JIT-specific
  sections of the implementation plan.

Exclude `ServiceContext`/repository fixes already assigned to PRs 3/5, every manual-default hunk,
JIT allowlist changes, arbitrary language conformance work and automatic JIT CI dependencies.
Retain the original ATTACHED CLI path when changing `JitConnector` entry invocation.

```bash
./gradlew :xdk:test --tests 'org.xvm.xdk.EmbeddingLifecycleTest.jit*' :plugin:test
./gradlew :manualTests:runSmallFloats --mode=DIRECT \
  :manualTests:runSmallFloatsJit --mode=DIRECT \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 --enable-preview'
./gradlew :manualTests:runSmallFloatsJit --mode=ATTACHED \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

Verify the five JIT integration tests actually executed. Keep the known-working small-float suite
explicit: the incomplete JIT may return default values from skipped method bodies, so a zero exit
code alone does not prove general coverage. Native resources, reflection/nested xUnit and reliable
forced termination remain outside the supported subset.

## PR 9 — manual-test DIRECT default

Include only `manualTests/build.gradle.kts`, the `--enable-preview` addition and its comment in
root `gradle.properties`, and matching documentation of the manual-test default/override.
Preserve existing heap/metaspace settings rather than making an unrelated tuning change.

This changes compilation, interpreter execution and xUnit conventions in manualTests. The existing
explicit JIT task consequently also uses DIRECT; it must wait for PR 8. Explicit execution-mode
smoke tasks retain their own modes. Add no new JIT dependencies to build/check/CI, and do not change
the plugin-wide default.

```bash
./gradlew :manualTests:runSequential :manualTests:runParallel \
  :manualTests:runXunitTests :manualTests:runTestAllExecutionModes \
  :manualTests:runSmallFloats :manualTests:runSmallFloatsJit spotlessCheck \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true \
  --configuration-cache --info
./gradlew :manualTests:runSequential -PxtcDefaultExecutionMode=ATTACHED \
  -PincludeBuildManualTests=true -PincludeBuildAttachManualTests=true
```

Repeat the first command to verify configuration-cache reuse. Check the chosen modes in task
output, not just the aggregate result. Land PR 4b and its native-disposal assertions before this
test-only rollout. The common mechanism alone does not establish cleanup. The JIT task still
covers only its documented subset; interpreter native-resource tests do not establish JIT parity.

## Shared files and extraction rules

| Shared file | Ownership of hunks |
|---|---|
| `xRTCertificateManager.java` | PR 1 keystore delegation; PR 3 IO scheduling |
| `Container.java` / `Frame.java` | PR 3 activity/IO/termination foundation; PR 4 resource registry/helpers; PR 4b cancellation outside the owner monitor and resource-owner accessor |
| `EmbeddingSupport.java` | PR 5 session/interpreter/in-memory compilation; PR 6 file compilation; PR 8 JIT |
| `RunRequest.java` | PR 5 interpreter request, then PR 8 backend field and constructors |
| `JitControl.java` | PR 5 interface-signature compatibility, then PR 8 real embedded implementation |
| `ServiceContext.java` | Generic lifecycle/thread-local changes in PR 3; concurrent callback map in PR 4b |
| `xCoreRepository.java` / `xRTFileTemplate.java` | PR 5 interpreter request-repository correctness |
| `IsolatedDirectExecutor.java` | PR 7 interpreter compile/run/xUnit; PR 8 JIT selection and limitation message |
| `EmbeddingLifecycleTest.java` | PR 5 interpreter/audit cases; PR 6 file compilation; PR 8 five JIT cases |
| `embedded-runtime-plan.md` | PR 5 API design; PR 6 compilation; PR 7 plugin/measurements; PR 8 JIT; PR 9 defaults |

Imports, overloads, helpers and Javadocs must follow their owning code. Taking whole current files
from the branch is unsafe for these shared files. A test may be moved to a focused class during
extraction if that makes review clearer, but do not duplicate fixtures or create installed-XDK
assumptions in unit-test builds.

The current working changes and all three original commits should remain recoverable until the
final union of extracted scopes is compared with this branch. Check that every source/test hunk
is assigned once; compare documentation semantically because it must describe each intermediate
state accurately. In particular, do not copy the final implementation plan into PR 5 while it
claims that the plugin, JIT or default-mode rollout has already shipped.

Each intermediate branch needs `git diff --check`, applicable tests, `spotlessCheck` and a clean
review of `git status`. New Java regression cases must report zero unexpected skips in XML.
Run Gradle `clean` separately if required; none of the checks above needs it. No branch here has
permission to silently add or enable tests that substantially expand automatic JIT execution.

## Work deliberately outside this submission series

- **Further metadata reuse:** explicit constant-pool ownership, immutable definition generations
  and measured compiler/application preparation caches. Coordinate with the `lagergren/errs`
  work; do not import that redesign while extracting these PRs.
- **A host warm across builds:** the build-scoped service already exists in PR 7. A daemon-wide
  or worker-process lifetime needs its own ownership, invalidation and shutdown design.
- **Broader execution policy:** concurrent DIRECT requests, custom injectors, plugin-wide DIRECT
  default and broader JIT capability/strict-placeholder reporting.

The user authorized local implementation and commits. This plan does not authorize publishing:
pushes and PR submission remain separate actions. Detailed validation and remaining capability
limits are recorded in the implementation plan and resource audit.

### Native resource integration scopes

These six scopes are now implemented together in the separate native migration commit, assigned
to **PR 4b**. They build on PR 4's common mechanism; host integration regressions need PRs 5a/5b.
They do not require the Gradle adapter or a host kept warm across builds. Sequential execution
already needs these fixes: serialization does not dispose resources left by a completed request.

The local commit boundary is **Release application-owned native resources between embedded runs**.
Keep it separate from `ca52e2aae` (keystore) and `9b51e0f23` (mechanism/audit). It is a coherent
cleanup change but a substantial review; R1–R6 remain explicit extraction boundaries if reviewers
prefer smaller PRs. Do not claim that the current combined commit is six independently validated
intermediate branches.

Paths in this table are under `javatools/src/main/java/org/xvm/runtime/` unless qualified.
Each scope includes focused unit tests and, where supported, `.x` fixtures under
`xdk/src/test/resources/ownership/` exercised by `EmbeddingLifecycleTest`. Networking fixtures
must supply their own resource provider without silently expanding default embedding injections.

| Scope | Exact production boundary | Required evidence |
|---|---|---|
| R1: file channels | `template/_native/fs/xOSFile.java` acquisition and `xRawOSFileChannel.java` handle transfer/close, including ignored results and failed delivery | The same native channel closes after explicit close or request success/failure/cancellation; caller files survive; repeated requests do not retain registrations |
| R2: sockets | `template/_native/net/xRTSocket.java` connection acquisition, asynchronous transfer and close | Loopback connect/transfer races and blocked reads terminate; idle sockets close with their owner; subsequent requests still work |
| R3: watcher subscriptions | `javatools_bridge/src/main/x/_native/fs/OSStorage.x` listener identity/cancellation and `template/_native/fs/xOSStorage.java` registration/unwatch | Last listener releases its native key, one owner cannot cancel another's subscription, abandoned listeners disappear after control close, and sequential requests do not grow the maps |
| R4: HTTP clients | `template/_native/web/xRTConnector.java` connector/client-pool ownership and active-send cancellation | Owned clients shut down, cookie state does not cross unrelated owners, cancelled sends release resources, and the next sequential request succeeds |
| R5: HTTP servers and exchanges | `template/_native/web/xRTServer.java` HTTP/HTTPS setup, partial-failure disposal, close, exchange lifetime and handler executor | Listeners, active exchanges, executor and keep-alive registration are released on explicit/owner close and every partial-startup failure; test missing second listener and unfinished requests |
| R6: cancelled callbacks | `WeakCallback.java`, `ServiceContext.java` callback-map operations and `template/_native/temporal/{xLocalClock,xNanosTimer}.java` cancellation paths | Cancel/execute races release references exactly once, repeated cancellation leaves no callback entries, and keep-alive accounting remains correct |

R1 and R3 address the reproduced leaks; R2/R4/R5/R6 address source-confirmed missing cleanup.
The new focused tests retain actual native handles and check release after **control close while
the session remains open**, followed by a healthy request. Existing lifecycle tests also exercise
session close/reopen. Use explicit counters/handle state, loopback peers, dynamic ports and synchronization;
avoid public network dependencies, sleeps, GC assertions and timing thresholds.

Keep watcher event semantics (directory classification, invalid keys and overflow handling) as a
separate behavior change unless required for R3's disposal contract. Retained heap/classloader
measurements follow the deterministic disposal tests. None of these scopes includes metadata
caching, concurrent DIRECT execution, plugin-default changes or broader JIT resource support.


PR 4b also owns these supporting changes and tests:

- `OwnedResource.closeOnWorker()` for awaited, potentially blocking native shutdown;
  `Frame.getResourceContainer()`; timer cancellation outside the `Container` monitor.
- New `template/_native/web/HttpClientPool.java` and `HttpServerResources.java`. Client pools are
  scoped on first use by each application, including calls through a shared injected connector.
- The added callback cases in `OwnedResourceTest` and the loopback/partial-startup tests in
  `template/_native/web/HttpResourceOwnershipTest`.
- `xdk/src/test/java/org/xvm/xdk/EmbeddingResourceOwnershipTest.java` and its
  `ownership/NativeResources.x` and `ownership/NetworkResources.x` fixtures. The latter only extends
  the test runner's provider; production injection policy is unchanged.
- The `xdk/build.gradle.kts` test-resource input for the production runner source. The installed
  XDK remains provisioned by the existing test-task dependencies; no skip-on-missing-binary tests
  are added to javatools, and no JIT execution dependency is added.
- Current cleanup status in the resource audit, implementation plan and JIT limitations document.

Use the [resource audit's regression command](../../../doc/embedding-resource-ownership.md#current-regression-coverage-and-submission-boundary)
as this scope's validation gate. Also verify configuration-cache reuse after the XDK test-resource
change. Tests cover these interpreter resource paths, not general JIT filesystem/network support,
custom injectors, retained-heap bounds or watcher directory/overflow event semantics.
