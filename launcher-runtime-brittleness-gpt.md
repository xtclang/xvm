# XTC Plugin Launcher Brittleness

## Scope

This note explains why the current XTC Gradle plugin launcher path is brittle, why the
failures look intermittent and daemon/cache-related, and how to redesign it without
giving up the required execution modes:

- direct/in-process
- in-thread / same build process
- forked attached
- forked detached

It also preserves an important constraint:

- the plugin may compile against javatools APIs,
- but it must not bundle a fixed javatools runtime,
- because the actual runtime must come from the project/XDK being built.


## Short Version

The recurring `com/google/gson/JsonElement` style failures are not best described as
"Gradle cache corruption". They are a symptom of runtime loading that is only partially
modeled by Gradle and partially hidden in daemon-global mutable state.

The current design mixes three incompatible ideas:

1. Resolve the runtime from the project's XDK at execution time.
2. Mutate the Gradle daemon's plugin classloader and thread context classloader so
   direct execution can call javatools APIs.
3. For forked execution, bypass Gradle's normal runtime-classpath modeling and launch
   Java manually with a hand-built `-cp`.

Each piece can work in isolation. Together they make behavior depend on daemon reuse,
task order, previous builds, and exactly which jar got loaded first.


## Required Constraints

These are valid requirements and should remain:

- The plugin should compile source code against javatools APIs.
- The runtime used to execute `Launcher` / `Runner` must come from the resolved project
  XDK, not from a copy bundled inside the plugin.
- The plugin must support multiple execution strategies.
- The plugin must keep working for published-plugin users and for composite-build/XDK
  development.

So the redesign is not "shade javatools into the plugin".


## Evidence In The Current Code

### 1. javatools has runtime dependencies

`javatools/build.gradle.kts` declares runtime dependencies:

- `implementation(libs.javatools.utils)`
- `implementation(libs.jline)`
- `implementation(libs.apache.commons.cli)`
- `implementation(libs.gson)`

See:

- [javatools/build.gradle.kts](/Users/marcus/src/research-fork-orig/javatools/build.gradle.kts:27)

The same build then makes `javatools.jar` a fat jar by unpacking `compileClasspath`
into the jar:

- `from(configurations.compileClasspath.map { ... zipTree(file) ... })`

See:

- [javatools/build.gradle.kts](/Users/marcus/src/research-fork-orig/javatools/build.gradle.kts:176)

This matters because the launcher code assumes it can often treat "the javatools jar"
as the runtime, but that assumption only holds if the jar content is exactly the
fat-runtime shape expected at that moment.

### 2. The forked path launches Java with a manually assembled single-jar classpath

`ForkedStrategy.buildProcess()` does:

- `java`
- task JVM args
- `-cp`
- `task.resolveJavaTools().getAbsolutePath()`
- main class

See:

- [ForkedStrategy.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/launchers/ForkedStrategy.java:140)

That means the forked runtime classpath is not modeled as a proper Gradle runtime
classpath. It is whichever single jar `resolveJavaTools()` happened to pick.

### 3. Direct/in-process execution mutates daemon-global classloader state

`XtcLauncherTask.executeTask()` unconditionally calls `ensureJavaToolsInClasspath(...)`
before execution:

- [XtcLauncherTask.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLauncherTask.java:165)

`XtcLoadJavaToolsTask` also performs the same side effect and is intentionally never
up-to-date:

- [XtcLoadJavaToolsTask.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLoadJavaToolsTask.java:31)

The actual runtime loader is `XtcJavaToolsRuntime`:

- static global: `private static volatile ClassLoader javaToolsClassLoader;`
- short-circuit: if non-null, do nothing
- reflectively inject jar into plugin classloader when possible
- always set thread context classloader to a new `URLClassLoader`

See:

- [XtcJavaToolsRuntime.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/XtcJavaToolsRuntime.java:58)
- [XtcJavaToolsRuntime.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/XtcJavaToolsRuntime.java:83)
- [XtcJavaToolsRuntime.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/XtcJavaToolsRuntime.java:177)

This is the main source of daemon-state brittleness.

### 4. Runtime resolution is "find the jar", not "resolve the launcher runtime"

`resolveJavaTools()` chooses a single jar from:

- the `xdkJavaTools` configuration
- or the unpacked XDK contents

See:

- [XtcLauncherTask.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLauncherTask.java:348)
- [XtcJavaToolsRuntime.java](/Users/marcus/src/research-fork-orig/plugin/src/main/java/org/xtclang/plugin/XtcJavaToolsRuntime.java:122)

This is resolution of an artifact file, not resolution of a complete launcher runtime.

### 5. The distribution path already models runtime differently

The XDK application scripts use:

- `tasks.startScripts { classpath = configurations.xdkJavaTools.get() }`

See:

- [xdk/build.gradle.kts](/Users/marcus/src/research-fork-orig/xdk/build.gradle.kts:119)

The distribution also copies the resolved `xdkJavaTools` contents into the XDK:

- [xdk/build.gradle.kts](/Users/marcus/src/research-fork-orig/xdk/build.gradle.kts:295)

So we already have two different runtime models:

- script/distribution path: classpath-oriented
- plugin execution path: single-jar + classloader mutation


## Why The Failures Look Intermittent

The failures are intermittent because the problem is stateful and timing-sensitive.

### A. Static daemon-global loader state

`XtcJavaToolsRuntime.javaToolsClassLoader` is process-global inside the Gradle daemon.
Once it is set, later builds in the same daemon may skip reloading entirely.

That means the effective runtime can depend on:

- which project ran first in the daemon
- which XDK version was resolved first
- whether the previous build loaded a stale or partial jar
- whether the current build expected a different runtime shape

### B. Thread context classloader mutation

The loader code also sets `Thread.currentThread().setContextClassLoader(...)`.

That is not task-local in the Gradle model. It is imperative process state.

If direct execution or worker threads rely on the thread context loader, behavior can
depend on execution order and thread reuse.

### C. Single-jar assumptions hide runtime closure problems

When everything happens to line up, the fat `javatools.jar` contains Gson and the
single-jar launch works.

When it does not, the symptoms look like:

- `NoClassDefFoundError`
- `ClassNotFoundException`
- missing `com/google/gson/JsonElement`

That is runtime-shape drift, not semantic compiler failure.

### D. Fat-jar replacement is a bad fit for mutable daemon loaders

Because the daemon keeps a classloader around, replacing or rebuilding the jar file on
disk does not necessarily give the running daemon a fresh view of the runtime.

That is why old notes describing this as:

- "parallel build + fat jar race + stale classloader in `XtcLoadJavaToolsTask`"

were directionally right.

Reference:

- [lang/lsp-compiler/research/HISTORY.md](/Users/marcus/src/research-fork-orig/lang/lsp-compiler/research/HISTORY.md:245)


## What Is Actually Wrong

### Wrong 1: using side effects to make runtime classes available

Gradle understands declared inputs, outputs, and classpaths.

It does not understand:

- "this task reflectively injected jar X into the daemon classloader"
- "this daemon already has a usable javatools loader from some other build"
- "this thread still has yesterday's context classloader"

That state is invisible to incrementality, configuration cache, and daemon reuse rules.

### Wrong 2: resolving a jar file instead of a launcher runtime

The plugin should resolve a runtime *model*:

- one jar
- several jars
- extracted distribution layout
- maybe future native blobs

Today it resolves "the javatools jar" and hopes that this equals the runtime.

That is too implicit.

### Wrong 3: direct mode and forked mode use different runtime semantics

Direct mode:

- classloader mutation
- API calls in the daemon process

Forked mode:

- `ProcessBuilder`
- manual classpath construction

Those should be two execution strategies over the same resolved runtime descriptor, not
two different runtime-loading systems.

### Wrong 4: `XtcLoadJavaToolsTask` is a side-effect task pretending to be normal Gradle work

The task always runs, has no stable output representing what changed in the daemon, and
only exists to mutate process state.

That is a smell. If the runtime must be prepared, it should be prepared by a properly
scoped execution service or isolated worker, not by a task whose effect lives outside
its declared outputs.


## Redesign Goals

The redesign should achieve all of these:

1. Keep the plugin compiled against javatools APIs.
2. Preserve the ability to use real javatools APIs programmatically, not only CLI-shaped calls.
3. Resolve the actual runtime from the project/XDK at execution time.
4. Support direct/in-process, attached, and detached execution.
5. Remove daemon-global mutable runtime state.
6. Make the resolved runtime a proper Gradle input.
7. Make all modes consume the same runtime descriptor.


## Proposed Redesign

## 1. Introduce an explicit runtime descriptor

Replace "find the javatools jar" with a resolved launcher runtime model.

Example:

```kotlin
data class XtcLauncherRuntime(
    val version: String,
    val mainClass: String,
    val classpath: List<File>,
    val xdkHome: File?,
)
```

The important field is `classpath: List<File>`, not `jar: File`.

This runtime must come from the project's resolved XDK/configuration, not from the
plugin bundle.

### How to source it

Add a dedicated consumable runtime variant, for example:

- `xdkJavaToolsRuntime`

This variant should represent the full launchable runtime closure for the chosen XDK.

It may resolve to:

- a single fat jar, or
- multiple jars

The plugin should not care which.


## 2. Split "plugin control code" from "runtime-scoped javatools API usage"

The plugin should remain compiled against `compileOnly(javatools)`.

That is still desirable because it gives:

- type-safe use of `LauncherOptions`
- direct use of javatools launcher/compiler/runtime APIs
- good IDE support while developing the plugin and the XDK together

What has to change is not "whether the plugin can use javatools APIs", but *where* that
usage happens.

### The constraint

Main Gradle-facing plugin/task classes cannot safely call arbitrary types from a
runtime-selected `javatools.jar` unless one of these is true:

- those runtime classes are already on the plugin classloader, or
- the daemon/plugin classloader is mutated to make them available

The current implementation chooses the second option, and that is what makes it brittle.

### The intended replacement

Keep using real javatools APIs, but only inside an isolated runtime scope that is created
from the resolved runtime descriptor for the current build/runtime fingerprint.

That means:

- outer plugin/task code remains Gradle-facing and classloader-stable
- inner runtime-scoped executor code is allowed to use javatools APIs directly

Examples of the kinds of code that should move into that runtime scope:

- `LauncherOptions.builder()`
- `Launcher.launch(...)`
- `new Runner(...)`
- `new TestRunner(...)`
- future compiler/type-checker/incremental services

### Boundary shape

Some boundary is still unavoidable once classloader isolation exists, but it should be a
minimal loader boundary, not a fake replacement for the javatools API.

In practice this means:

- avoid turning the architecture into "string arrays and `main()` everywhere"
- avoid over-engineering a large DTO/serialization bridge if the real API should remain
  the primary programming model
- use just enough reflective/isolated invocation to cross the classloader boundary cleanly


## 3. Replace daemon-global loading with scoped execution environments

### For in-process mode

Use one of these:

- Gradle Worker API with `classLoaderIsolation`
- a dedicated scoped `URLClassLoader` owned by a `BuildService`

The important property is:

- no mutation of the plugin classloader
- no static global "already loaded" flag
- runtime loader scoped to the resolved runtime fingerprint

Preferred shape:

- `XtcInProcessRuntime` or similar creates or reuses a loader keyed by runtime classpath hash
- the loader lives in a build-scoped service
- code loaded inside that runtime scope uses real javatools APIs directly

This preserves "same build process" execution without poisoning the daemon globally.

This is especially important for self-hosting/XDK development because direct mode should
remain a first-class path for performance:

- lower startup cost than forked JVMs
- better warm-JVM behavior
- better future support for stateful incremental services
- room to move beyond coarse launcher flags like always forwarding `--rebuild`

### For attached forked mode

Keep `ProcessBuilder` or switch to `ExecOperations`, but build the command from the
same resolved runtime descriptor:

- `java`
- JVM args
- `-cp`
- joined runtime classpath
- main class
- arguments

No single-jar shortcut.

### For detached mode

Also build from the same runtime descriptor.

Detached mode is not the problem. The problem is that detached mode currently inherits
the same brittle runtime resolution assumptions.


## 4. Remove `XtcLoadJavaToolsTask`

This task should disappear.

Why:

- it models no meaningful output
- it mutates daemon state
- it duplicates work done again in `XtcLauncherTask.executeTask()`
- it hides runtime preparation in task ordering instead of in execution infrastructure

If runtime preparation is needed, it belongs in:

- a build service, or
- the executor strategy / isolated runtime owner

The goal is not to rename or relocate this pseudo-mutating setup step.
The goal is to remove the need for such a task entirely.

After redesign:

- launcher tasks should not depend on a special "prepare daemon classpath" task
- runtime preparation should happen as normal execution infrastructure, not as a fake
  cache-hostile task in the build graph
- `extractXdk` may still exist as a real artifact-preparation task when consuming an XDK
  archive, but `loadJavaTools`-style daemon mutation should be gone


## 5. Make all strategies consume one resolved runtime

The clean architecture is:

1. Resolve `XtcLauncherRuntime`
2. Choose execution strategy
3. Execute against the same runtime descriptor

The execution payload can remain close to the current programming model:

- direct mode can use real `LauncherOptions` and related javatools APIs inside the isolated runtime
- forked modes can still convert to command lines because they must cross an OS-process boundary anyway

That gives:

- same module-path logic
- same runtime inputs
- same version selection
- same classpath semantics

Only process boundary changes.


## 6. Stop preferring "the non-XDK version" by ad hoc file choice

`resolveJavaTools()` currently compares files and may prefer one artifact file over
another. That is too artifact-centric.

Instead:

- choose one runtime source of truth for the build
- derive the full runtime descriptor from it
- use that descriptor everywhere

The plugin should not switch between "config jar" and "XDK contents jar" as an implicit
runtime policy hidden in helper code.


## Execution Modes After Redesign

### `DIRECT`

Meaning after redesign:

- same daemon process
- no new OS process
- isolated runtime loader, not plugin classloader mutation

Implementation:

- isolated runtime scope using runtime descriptor classpath
- real javatools API usage inside that scope

### `ATTACHED`

Meaning after redesign:

- child Java process
- stdout/stderr wired to Gradle

Implementation:

- `ProcessBuilder` or `ExecOperations` with full runtime classpath

### `DETACHED`

Meaning after redesign:

- child Java process
- no blocking wait in Gradle

Implementation:

- same as attached, but with detached process lifecycle / I/O behavior


## Migration Plan

## Phase 1: make runtime explicit

- Add `XtcLauncherRuntime` model.
- Add a resolver that returns full classpath, not one jar.
- Update forked strategies to use full runtime classpath.
- Keep current direct mode temporarily.

Status:

- this phase is now in progress / partially implemented
- forked execution already consumes a full resolved runtime classpath
- extracted XDK runtime resolution no longer assumes only `javatools.jar`

This should already remove the `JsonElement`-style classpath misses in forked mode.

## Phase 2: isolate direct mode without giving up javatools APIs

- Introduce a build-scoped isolated runtime for direct mode.
- Load plugin runtime executor classes into that isolated runtime together with the
  resolved XDK runtime classpath.
- Move direct-mode javatools API usage into those runtime-scoped executor classes.
- Keep using real javatools APIs there (`LauncherOptions`, launcher classes, future services).

This is the key architectural move.

## Phase 3: remove daemon-global classloader mutation

- Stop calling `ensureJavaToolsInClasspath()` from `XtcLauncherTask`.
- Stop wiring launcher tasks through `loadJavaTools`.
- Delete `XtcLoadJavaToolsTask`.
- Delete the static daemon-global `javaToolsClassLoader`.

Success criteria for this phase:

- no launcher task depends on `loadJavaTools`
- there is no replacement pseudo-task whose purpose is "mutate runtime state before execution"
- any remaining preparation tasks produce real file outputs that Gradle can model normally

This should remove the daemon-state and timing sensitivity.

## Phase 4: converge strategy implementations

- Make direct/attached/detached all consume the same runtime descriptor.
- Keep only process-boundary differences in strategy classes.
- Keep shared module-path and runtime-resolution logic outside the strategies.

## Phase 5: grow into stateful in-process services where it pays off

After direct mode is isolated and stable, it becomes reasonable to move toward:

- cached runtime scopes keyed by runtime fingerprint
- longer-lived in-process compiler/type-checker services
- finer control of rebuild behavior
- future LSP-style or incremental compiler support without daemon-global pollution


## Dangers And Things To Get Right

### 1. Do not accidentally collapse the architecture to CLI-only calls

Forked modes naturally need command-line conversion.

Direct mode should not be forced through that same narrow shape if the goal is to keep:

- `LauncherOptions.builder()`
- rich programmatic launcher/compiler APIs
- future stateful incremental services

The loader boundary is necessary; reducing everything to argv is not.

### 2. The outer plugin classes must stop depending on runtime-selected javatools classes

If outer plugin/task classes still instantiate or strongly reference runtime-selected
javatools types directly, then the daemon/plugin classloader mutation problem will remain.

The main Gradle-facing layer and the runtime-scoped layer must be separated deliberately.

### 3. Reuse of isolated runtimes must be fingerprinted correctly

If a build-scoped runtime is reused, the cache key must include at least:

- resolved runtime classpath contents
- relevant execution configuration that affects compatibility

Otherwise we can recreate the same stale-runtime problems one level down.

### 4. Do not regress self-hosting performance

The XDK build itself benefits from direct mode.

A redesign that is correct but silently pushes self-hosting to slower forked execution
would be a real regression.

### 5. Keep third-party consumer behavior working

The plugin must continue to work when:

- the runtime comes from composite build substitution (`xdkJavaTools`)
- the runtime comes from an extracted XDK distribution
- the consumer is a third-party repo like `../xtc-app-template`

### 6. Keep the migration incremental and verifiable

The safe sequence is:

1. make runtime explicit
2. isolate direct mode
3. remove daemon-global loading
4. then simplify legacy code

Trying to delete the old loader path before isolated direct mode exists would create too much churn.

## Direct-Mode Isolation Cut Line

The minimal currently-known set of code that forces javatools types into the outer
plugin/task classloader is:

- `plugin/src/main/java/org/xtclang/plugin/launchers/DirectStrategy.java`
- `plugin/src/main/java/org/xtclang/plugin/launchers/LauncherOptionsBuilder.java`
- `plugin/src/main/java/org/xtclang/plugin/tasks/XtcLauncherTask.java`
- `plugin/src/main/java/org/xtclang/plugin/tasks/XtcLoadJavaToolsTask.java`

More specifically:

- `DirectStrategy` directly imports and uses:
  - `org.xvm.tool.Launcher`
  - `org.xvm.tool.Runner`
  - `org.xvm.tool.TestRunner`
  - `org.xvm.tool.Console`
  - `org.xvm.asm.ErrorListener`
  - `org.xvm.asm.ErrorList`
  - `org.xvm.util.Severity`
- `LauncherOptionsBuilder` directly imports and constructs:
  - `LauncherOptions.CompilerOptions`
  - `LauncherOptions.RunnerOptions`
  - `LauncherOptions.TestRunnerOptions`
- `XtcLauncherTask.executeTask()` currently assumes javatools must be injected into the
  outer plugin classloader before launcher execution because of those types.
- `XtcLoadJavaToolsTask` exists only to satisfy that same assumption.

This is the first isolation seam:

- move `DirectStrategy` and `LauncherOptionsBuilder` functionality into runtime-scoped
  executor classes
- then remove the outer-layer need for `ensureJavaToolsInClasspath()` and `loadJavaTools`

Progress update:

- forked execution no longer uses `LauncherOptionsBuilder`; it now builds plain launcher
  command lines in the outer layer
- `DirectStrategy` no longer imports javatools classes directly
- direct-mode `LauncherOptions` construction and launcher invocation now live in
  runtime-scoped executor classes loaded through an isolated invoker
- launcher tasks no longer depend on `loadJavaTools`, and the daemon-global
  `ensureJavaToolsInClasspath()` path has been removed together with
  `XtcLoadJavaToolsTask`

## What I Would Change Next

If doing this incrementally from the current state, the next sequence should be:

1. Introduce runtime-scoped direct executors loaded in an isolated classloader.
2. Move direct-mode `LauncherOptions` and launcher API usage into those executors.
3. Prove direct mode works for self-hosting and external-consumer cases.
4. Stop treating `XtcLoadJavaToolsTask` as required setup for launcher tasks.
5. Delete `ensureJavaToolsInClasspath()` and the static `javaToolsClassLoader`.

That sequence keeps the desirable javatools API usage model while removing the daemon-global brittleness.

## Working Task List

- [x] Introduce an explicit `XtcLauncherRuntime` model with classpath-oriented resolution.
- [x] Switch forked execution to use the resolved runtime classpath instead of a single-jar shortcut.
- [x] Preserve extracted-XDK fallback by extracting all launcher runtime jars, not only `javatools.jar`.
- [x] Add focused tests for runtime resolution from composite-build/config and extracted-XDK sources.
- [x] Identify the minimal set of direct-mode code that currently requires javatools types in the outer plugin/task classloader.
- [x] Introduce runtime-scoped direct executor classes intended to run inside an isolated classloader.
- [x] Move direct-mode `LauncherOptions` builder usage into the runtime-scoped executors.
- [x] Move direct-mode launcher invocation (`Launcher`, `Runner`, `TestRunner`) into the runtime-scoped executors.
- [x] Add an isolated runtime owner keyed by runtime fingerprint.
- [x] Choose the first isolation mechanism for direct mode:
  build-scoped service with child classloader, or Worker API with classloader isolation.
- [x] Make `DIRECT` mode execute via the isolated runtime instead of `ensureJavaToolsInClasspath()`.
- [x] Verify `DIRECT` mode still works for self-hosting/XDK builds and remains a first-class fast path.
- [x] Verify the same direct-mode design works when the runtime comes from an extracted XDK in a third-party consumer scenario.
- [x] Remove launcher task dependency wiring on `loadJavaTools`.
- [x] Stop calling `ensureJavaToolsInClasspath()` from launcher task execution.
- [x] Delete `XtcLoadJavaToolsTask`.
- [x] Delete daemon-global loader state such as static cached classloaders and thread-context mutation used as task setup.
- [x] Re-run plugin tests and focused Gradle verification for:
  `:plugin:test`, `:manualTests:compileXtc`, representative XDK compile tasks, and external-consumer scenarios.
- [ ] Check whether the redesign reduces one-time task-classpath churn and related follow-up recompilation symptoms from issue #426.
- [ ] Decide whether the build-scoped direct runtime owner should remain a shared service or move to
  a Worker API / explicitly keyed build service registration if future stateful compiler services need
  stronger isolation semantics.
- [ ] Add or keep targeted diagnostics for direct-runtime cache behavior so `--info` / `--debug` can show:
  cache miss, cache hit, runtime fingerprint, and build-end cleanup.

Recent verification notes:

- `:plugin:test` passed after removing `loadJavaTools` and the outer-layer
  `ensureJavaToolsInClasspath()` call.
- `:plugin:test --tests org.xtclang.plugin.runtime.DirectRuntimeFingerprintTest` passed
  after switching the direct-runtime cache key to content hashing.
- `:manualTests:tasks --all` no longer lists `loadJavaTools`.
- `:manualTests:runTestWithDirect` passed against the extracted-XDK layout under
  `manualTests/build/xtc/xdk/lib`.
- `:manualTests:runTestWithAttached` passed after a prior direct-mode build in the
  same daemon, which is the key cross-mode regression case that the old daemon-global
  loader approach could poison.
- `:manualTests:runXunitTests` passed through the forked `TestRunner` path against
  that same extracted-XDK runtime.
- `:manualTests:runTestAllExecutionModes` passed, covering `DIRECT`, `ATTACHED`, and
  `DETACHED` in one build against the extracted-XDK layout.
- `:manualTests:runParallel` still exposes a runtime/interpreter failure in
  `TestNesting` (`Circular initialization "ecstasy.xtclang.org"`), but that
  occurs after successful plugin launch and is tracked separately from this refactor.
- `./gradlew publishLocal` from the repository root completed successfully and
  published both `org.xtclang:xtc-plugin:0.4.4-SNAPSHOT` and
  `org.xtclang:xdk:0.4.4-SNAPSHOT` to Maven Local.
- `../xtc-app-template` succeeded with
  `./gradlew greet -PlocalOnly=true --refresh-dependencies --console=plain --info`,
  which proved the third-party consumer path against the newly published artifacts:
  the build resolved `org.xtclang.xtc-plugin:0.4.4-SNAPSHOT`, loaded the published
  plugin jar from Gradle's transformed plugin cache, extracted
  `~/.m2/repository/org/xtclang/xdk/0.4.4-SNAPSHOT/xdk-0.4.4-SNAPSHOT.zip`, then
  compiled and ran `HelloWorld` successfully via the new plugin/runtime model.
- `../xtc-app-template` also succeeded with the build-level direct override:
  `./gradlew greet -PxtcDefaultExecutionMode=DIRECT -PlocalOnly=true --refresh-dependencies --info`.
  After switching back to the default mode, Gradle invalidated configuration cache on
  the property change and the launcher tasks reran because `executionMode` changed.
- `../xtc-app-template` succeeded with direct-mode test execution after republishing:
  `./gradlew testXtc -PxtcDefaultExecutionMode=DIRECT -PlocalOnly=true --refresh-dependencies --info`.
  That run exposed and validated a real fix in the direct test-runner options builder:
  the direct path must not inject runner-only `-M` semantics into `TestRunnerOptions`.

Additional discoveries:

- A Gradle shared `BuildService` is the right lifecycle boundary for direct-mode
  runtime reuse: build-scoped rather than daemon-global, and closed automatically
  when the build completes.
- A build-level execution-mode override is more useful than task-local `--mode`
  flags for alias tasks and third-party consumers. The plugin now supports
  `-PxtcDefaultExecutionMode=DIRECT|ATTACHED|DETACHED`, which changes the default
  launcher mode convention for plugin-created tasks without requiring the consumer
  to call `runXtc` or `compileXtc` directly.
- Runtime reuse should not be keyed only by file path and timestamps. The current
  implementation fingerprints runtime entries by content hash to avoid accidental
  reuse when self-hosting builds rewrite jars in place.
- The build service is also the right place for cache diagnostics because it is the
  only component that can see cache hits, misses, and shutdown cleanup in one place.
- This is a working model for the currently verified scenarios, but not yet a final
  proof against every future subtle bug. The remaining uncertainty is around longer-term
  evolution: persistent/stateful compiler services, additional mixed-mode builds not yet
  covered by tests, and whether issue #426 follow-up churn is fully addressed.
- The third-party template flow now works with that override as well:
  `../xtc-app-template` succeeded with
  `./gradlew greet -PxtcDefaultExecutionMode=DIRECT -PlocalOnly=true --refresh-dependencies --info`,
  and the logs showed direct compile/run execution plus build-scoped isolated runtime
  reuse from the newly published Maven Local plugin and XDK artifacts.
- Direct-mode tests needed one additional correction that only showed up in the
  external-consumer path: `TestRunnerOptions` does not accept the same method flag
  shape as `RunnerOptions`, so the isolated direct builder must treat test launches
  as test-runner launches, not generic runner launches.

## Next Architecture: Reused External Runtime

The next performance/stability step should not be "make the root composite build
default to DIRECT". The current build-scoped direct runtime is a good opt-in path,
but it still executes inside the Gradle daemon process. That means it cannot protect
the build from JVM crashes, native failures, abrupt exits, or similar direct-mode
catastrophes.

The better target architecture is:

- preserve the current explicit launcher runtime descriptor
- preserve the current `DIRECT` path as an opt-in in-daemon fast path
- add a reused external worker/daemon process keyed by the resolved launcher runtime
- make that reused external process the likely long-term default for performance-sensitive
  aggregate builds such as the XDK self-hosting build

### Why This Is Better Than "DIRECT Everywhere"

- It amortizes JVM startup cost across many compile/test invocations.
- It keeps launcher/runtime crashes outside the Gradle daemon.
- It preserves runtime selection by resolved XDK fingerprint.
- It leaves room for future stateful compiler/typechecker services without reintroducing
  daemon-global classloader mutation.

### Candidate Designs

#### 1. Gradle Worker API with process isolation

Pros:
- Uses Gradle-owned process isolation.
- Cleaner lifecycle management.
- Better fit if each launcher invocation is still mostly request/response.

Cons:
- May not provide enough control over true process reuse and runtime pinning.
- Less obvious fit if we later want a long-lived compiler service with internal state.

#### 2. Plugin-managed external daemon keyed by runtime fingerprint

Pros:
- Explicit control over process reuse, runtime fingerprint, and protocol.
- Strong fit for future persistent compiler/typechecker services.
- Clear separation between Gradle daemon and javatools runtime process.

Cons:
- More code: protocol, startup, handshake, shutdown, health checks.
- More verification burden.

Current recommendation:

- prototype the plugin-managed external daemon path first if we want the best long-term
  architecture for XDK self-hosting and future incremental services
- otherwise prototype Worker API first if the immediate goal is only to cut per-task JVM
  startup overhead with minimal surface area

### Incremental Plan

1. Define the execution boundary.
- Keep the current direct request DTO shape as the initial transport boundary.
- Do not collapse back to raw argv strings unless needed for bootstrap fallback.
- Keep launcher-runtime resolution exactly as it is now.

2. Introduce an external runtime owner.
- Add a new runtime owner keyed by the resolved launcher runtime fingerprint.
- The owner should manage:
  - process startup
  - handshake / liveness
  - request dispatch
  - shutdown
  - stderr/stdout routing

3. Add a narrow protocol.
- Start simple:
  - `compile(request)`
  - `run(request)`
  - `test(request)`
- Use plain serializable request/response objects.
- Include protocol version and runtime fingerprint in the handshake.

4. Implement a bootstrap launcher in plugin/javatools-facing code.
- This process should start with the resolved runtime classpath.
- It should load the same isolated executor implementation logic, but in its own JVM.
- It should never mutate the Gradle daemon.

5. Add health and invalidation rules.
- Invalidate/restart when:
  - runtime fingerprint changes
  - plugin code source changes
  - protocol version changes
  - process exits unexpectedly
- Add explicit idle/build-end shutdown rules.

6. Route selected launcher tasks to the external daemon.
- Start with compile tasks only.
- Then test tasks.
- Then evaluate run tasks separately; they may need different stdout/stderr semantics.

7. Benchmark against real aggregate workflows.
- Measure from the repo root, not just isolated subprojects.
- Use representative commands such as:
  - `./gradlew build`
  - `./gradlew :xdk:build`
  - `./gradlew :manualTests:runTestAllExecutionModes`
- Compare:
  - current ATTACHED per-task fork
  - current DIRECT
  - reused external worker/daemon

8. Decide default mode only after verification.
- Do not flip the global default based on intuition.
- Require proof that:
  - performance improves for real root aggregate builds
  - crashes do not poison the Gradle daemon
  - mixed execution modes remain safe

### New Risks To Track

- Protocol drift between plugin-side request DTOs and runtime-side executor logic.
- External process lifecycle leaks or orphan processes.
- Incorrect reuse across runtime or plugin version changes.
- Output handling regressions for test tasks and xunit output.
- Interaction with Gradle configuration cache and included-build task fan-out.

### Verification Matrix For The Next Step

- Same build, repeated compile requests using the same runtime fingerprint.
- Same daemon, separate builds with changed plugin commit/build-info.
- Same daemon, separate builds with changed XDK runtime jars.
- Mixed `DIRECT`, reused external worker, and `ATTACHED` tasks in the same overall build.
- Root aggregate `build` from the repo root with optional included builds enabled.
- Failure injection:
  - external process crash
  - protocol mismatch
  - abrupt exit during compile
  - bad runtime fingerprint reuse attempt


## Bottom Line

The brittleness is not because supporting multiple strategies is inherently wrong.

The brittleness comes from:

- resolving an artifact instead of a runtime,
- mutating daemon-global classloader state,
- and letting direct/forked modes use different runtime-loading semantics.

The correct redesign is:

- keep the project-supplied XDK runtime,
- keep multiple execution strategies,
- but make them all run against one explicit runtime descriptor,
- with scoped loaders/processes instead of daemon-global mutation.
