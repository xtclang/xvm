# Gradle Plugin On The `XtcEngine` Compile/Run API

Date: 2026-08-26

Scope: design/research only. This document proposes driving the XTC Gradle plugin's
compile and run tasks through the new resident embedding engine
`org.xvm.api.XtcEngine`
(`javatools/src/main/java/org/xvm/api/XtcEngine.java`) instead of the current
fork-a-launcher-per-task model. It inspects the plugin and the XDK build as they
stand today and lays out a phased path, the hard problems, and open questions. No
source is modified by this document.

---

## 1. Executive Summary

Every XTC compile and run today pays a full cold-start tax. In the default
`ATTACHED` mode (`XtcPluginConstants.java:68`) the plugin starts a brand-new JVM
for each compile task and for each module a run task executes, and every one of
those JVMs re-bootstraps the entire XVM system: it classloads and verifies
`javatools`, boots a `NativeContainer` (the "-1" native plane), and loads + links
`ecstasy` + turtle (`mack`) + the native bridge (`_native`) off the module path
before it can do a single useful unit of work.

`XtcEngine` pays that tax **once**. Its constructor boots the `Runtime` plus the
`NativeContainer` a single time and keeps them warm
(`XtcEngine.java:94-99`); thereafter `compile(...)` reuses the warm library
repository and native plane, and `run(...)` executes each module as a **nested
container under the shared warm plane** via `NestedContainer.createForHost`
(`XtcEngine.java:343`, `NestedContainer.java:61`), returning an event-driven
`CompletableFuture` (`XtcEngine.java:323-345`).

Wiring the plugin onto this engine (held by a Gradle **Build Service** so it lives
across tasks within a build and stays configuration-cache compatible) turns "N
JVM boots + N system-library links" into "one warm engine, N cheap requests." Two
audiences win:

- **Third-party consumer builds** that apply the plugin (e.g. `manualTests`, the
  template app): each `compileXtc`/`runXtc` stops forking a launcher.
- **The XDK's own dependency-heavy build**: it compiles ~20 `lib_*` modules, each
  in its own subproject/task (`xdk/build.gradle.kts:96-119`), today each a
  separate JVM boot. One warm engine shared across the build amortizes all of it.

There is already a `DIRECT` execution mode and a build-scoped
`DirectRuntimeBuildService` (`plugin/src/main/java/org/xtclang/plugin/runtime/DirectRuntimeBuildService.java`)
in the tree. Crucially, that path is **half the win**: it removes the JVM fork but
still calls the CLI-style `Launcher.launch(...)` / `new Runner(...)` per invocation
(`IsolatedDirectExecutor.java:50-82`), so it re-bootstraps the system library set
every time. The proposal here is to replace those per-invocation launchers with a
single warm `XtcEngine` held by that same service.

**Recommendation in one line:** keep the existing `ExecutionMode` /
`DirectRuntimeBuildService` / isolated-classloader machinery — it is exactly the
right scaffolding — and swap the *body* of direct execution from
per-call `Launcher`/`Runner` to a warm, fingerprint-keyed `XtcEngine`, starting
with run tasks (already in-daemon and serial), then compile tasks once the
engine can consume on-disk source trees.

---

## 2. Current Architecture (fork-per-task)

### 2.1 Execution modes and the default

The plugin models three execution modes (`launchers/ExecutionMode.java:6-24`):

- `DIRECT` — in-process, in the Gradle daemon.
- `ATTACHED` — forked JVM, inherited stdout/stderr. **This is the default**
  (`XtcPluginConstants.java:68`), overridable by `-PxtcDefaultExecutionMode`
  (`XtcPluginConstants.java:67`, parsed in
  `DefaultXtcLauncherTaskExtension.java:52-55`).
- `DETACHED` — forked JVM in the background with file redirects.

Consumer builds run on the default: `manualTests` never sets `executionMode`, so
its compiles and runs fork (`manualTests/build.gradle.kts`, `xtcCompile { ... }`
and `xtcRun { ... }` blocks leave the mode at `ATTACHED`).

### 2.2 The forked path (ATTACHED / DETACHED)

Both forked modes use `ForkedStrategy`. It builds a `ProcessBuilder` whose command
is literally `java <jvmArgs> -cp <launcher runtime> <mainClass> <programArgs>` and
starts a fresh process (`launchers/ForkedStrategy.java:136-152`), once per compile
(`ForkedStrategy.java:57`), once per run module (`ForkedStrategy.java:90`), and
once per test module (`ForkedStrategy.java:118`).

The `mainClass` is a CLI tool selected per task:

- compile → `org.xvm.tool.Compiler` (`XtcPluginConstants.java:20`, wired at
  `XtcCompileTask.java:185-187`),
- run → `org.xvm.tool.Runner` (`XtcPluginConstants.java:25`,
  `XtcRunTask.java:168-170`),
- test → `org.xvm.tool.TestRunner` (`XtcPluginConstants.java:30`,
  `XtcTestTask.java:86`).

A run task iterates its modules and starts one process **per module**
(`XtcRunTask.java:320` maps `runSingleModule` over `modulesToRun`, each landing in
`ForkedStrategy.execute(...)` via `executeStrategy`, `XtcRunTask.java:387-401`,
`:466-468`). So a run task that runs 5 modules boots 5 JVMs.

### 2.3 Where the turtle / native-bridge / ecstasy paths come from

The forked launcher receives the system libraries as repeated `-L <dir>` module
path flags. `ForkedCommandLineBuilder` emits one `-L` per resolved module path
entry for compile (`ForkedCommandLineBuilder.java:39-41`), run
(`:65-67`, plus `--no-recompile` at `:69`), and test (`:80-82`). The path itself
is produced by `resolveFullModulePath()` (`XtcLauncherTask.java:378-387`) which
delegates to `ModulePathResolver.resolveFullModulePath()`
(`launchers/ModulePathResolver.java:58-89`). That union is:

1. the XDK contents dir (which contains `ecstasy.xtc`, `javatools_turtle.xtc`,
   `javatools_bridge.xtc`, and every `lib_*` binary),
2. the `xtcModule` dependency artifacts (upstream modules),
3. this project's own source-set output dirs.

The engine collapses this: it is handed the XDK lib/javatools dirs **once** on the
builder (`XtcEngine.builder().modulePath(...)`, `XtcEngine.java:429-434`) and
resolves turtle/native internally, so callers never name them per request
(the class doc calls this out explicitly, `XtcEngine.java:152-157`).

### 2.4 The existing DIRECT path — warm classloader, cold runtime

`DirectStrategy` (`launchers/DirectStrategy.java:41-71`) routes to a Gradle shared
**Build Service**, `DirectRuntimeBuildService`, registered lazily as
`"xtcDirectRuntime"` (`XtcLauncherTask.java:55`, `:160-162`; held as a
`Provider<DirectRuntimeBuildService>` at `:83`, exposed `:408-410`, and only
`.get()`-ed at execution time in `DirectStrategy.java:44`). The service:

- keys a cache of runtimes by a SHA-256 fingerprint of the launcher classpath +
  the plugin code source (`DirectRuntimeFingerprint.java:35-48`), so a runtime
  change or plugin rebuild forces a fresh entry
  (`DirectRuntimeBuildService.java:42`, `:74-88`);
- loads `javatools` in a child-first `PluginRuntimeClassLoader`
  (`DirectRuntimeBuildService.java:90-113`; child-first only for the
  `runtime.impl` bridge package, `PluginRuntimeClassLoader.java:15-19`, `:46-48`),
  so `javatools` does not collide with the daemon's own classpath;
- is build-scoped and closed at build end, releasing the classloaders
  (`DirectRuntimeBuildService.java:24-35`, `:142-151`).

This is the correct lifecycle and isolation scaffolding. But the actual work still
goes through the CLI launcher **on every call**: `IsolatedDirectExecutor` invokes
`Launcher.launch(options, ...)` for compile (`IsolatedDirectExecutor.java:50-55`),
`new Runner(options, ...).run()` for run (`:57-75`), and
`new TestRunner(...).run()` for test (`:77-82`), each rebuilt from a fresh
`LauncherOptions` (`IsolatedLauncherOptionsBuilder.java`). Every such call
re-creates a runtime/native container and re-links the system libraries. DIRECT
mode therefore only saves the JVM start and JIT warmup — it does **not** amortize
the `NativeContainer` boot or the system-library link, which is precisely the
expensive part `XtcEngine` is built to keep warm.

### 2.5 Per-invocation cost breakdown (what a warm engine amortizes)

| Cost | ATTACHED/DETACHED (fork) | Existing DIRECT | Warm `XtcEngine` |
| --- | --- | --- | --- |
| JVM process start | per task / per module | once (daemon) | once (daemon) |
| `javatools` classload + verify | per fork | once per runtime fingerprint | once per runtime fingerprint |
| `Runtime` + `NativeContainer` "-1" boot | per fork | **per call** | **once** (`XtcEngine.java:94-99`) |
| load + link `ecstasy` + turtle + `_native` | per fork | **per call** | warm repo; light prelink per compile (`:163`), plane reused on run (`:333-335`) |
| JIT warmup of compiler/runtime | per fork | cold each build | warms across the build |

The run path benefits most starkly: `run(...)` reuses `f_containerNative`
directly and only assembles a per-module `FileStructure`
(`XtcEngine.java:333-344`), so a warm engine turns "boot the whole platform to run
one module" into "attach a nested container to the plane already running."

---

## 3. Target Architecture (plugin drives `XtcEngine`)

### 3.1 The Build Service holds one warm engine

`DirectRuntimeBuildService` already is the build-scoped owner with the right
lifecycle. The change is what it caches: instead of (or alongside) a
`RuntimeEntry` that only holds a classloader + reflected launcher methods
(`DirectRuntimeBuildService.java:153-176`), each fingerprint entry gains a warm
`XtcEngine`, built inside the isolated classloader as

```
XtcEngine.builder().modulePath(xdkLibDir, xdkJavatoolsDir, ...extraModuleDirs).build()
```

keyed by the same `DirectRuntimeFingerprint`. The module-path dirs are exactly
what `resolveFullModulePath()` already resolves (§2.3); the engine wants the
*directories* (it wraps each in a `DirRepository`, `XtcEngine.java:436-448`),
which the plugin already computes as directories in `ModulePathResolver`
(`ModulePathResolver.java:143-164`).

The engine is instantiated behind the same reflective bridge as
`IsolatedDirectExecutor` (it lives in `javatools`, loaded child-first), so the
outer plugin classes never import `org.xvm.api.XtcEngine` directly — they call it
through the `runtime.impl` bridge, exactly as the executor is called today
(`DirectRuntimeBuildService.java:56-72`).

### 3.2 Configuration-cache compatibility

This repo requires configuration-cache compatibility (AGENTS.md). The design stays
inside the sanctioned pattern:

- A Gradle Build Service is the blessed way to hold live cross-task state under the
  config cache. Only the `Provider<DirectRuntimeBuildService>` is captured in task
  state (`XtcLauncherTask.java:83`, `:160-162`), and it is dereferenced solely at
  execution time (`DirectStrategy.java:44`). The `XtcEngine` itself is **never
  serialized** — it is created lazily inside the service on first use and lives
  only in daemon memory, so it never touches the configuration cache.
- The engine must be released when the build finishes: `XtcEngine.close()` calls
  `f_runtime.shutdownXVM()` (`XtcEngine.java:349-352`), so the service's existing
  `close()` (`DirectRuntimeBuildService.java:142-151`) closes every cached engine
  in addition to the classloaders.

### 3.3 Compile requests

A compile task submits its already-captured request DTO
(`DirectCompileRequest`, built at `DirectStrategy.java:73-92`) to the service,
which routes it to the engine. Two sub-cases:

- **Multiple modules in one source set** map directly onto
  `engine.compile(Map<name, source>)` (`XtcEngine.java:148-215`), which resolves
  cross-module references among the entries in one pass. A compile task's
  `resolveXtcSourceFiles()` already returns the set of top-level module source
  files for the set (`XtcCompileTask.java:171-175`), so it is a natural `Map`.
- **On-disk `.xtc` outputs** for downstream tasks and the build cache come from
  `CompileResult.writeTo(dir)` (`XtcEngine.java:407-421`), which writes each
  assembled module into the standard XDK on-disk layout via a `DirRepository`.
  Because the engine already *assembles* modules at compile time (a serialization
  round-trip that finalizes op arguments into pool indices,
  `XtcEngine.java:196-231`), the persisted binary reloads identically to a
  CLI-produced `.xtc`. The task then writes into its `@OutputDirectory`
  (`XtcCompileTask.java:284-288`) and Gradle caches it as usual
  (`@CacheableTask`, `XtcCompileTask.java:46`).

### 3.4 Inter-module dependency ordering comes from Gradle, not from the engine

A single-VM builder does **not** need to rediscover module dependency order. In
the XDK, each library is a separate Gradle subproject whose cross-module
dependencies are declared with `xtcModule(...)`
(`xdk/build.gradle.kts:96-119`; the incoming config is literally named `xtcModule`,
`XtcPluginConstants.java:13`, created per source set in
`XtcProjectDelegate.java:637-672`). Those declarations both (a) place the upstream
`.xtc` on the consumer's module path and (b), being project dependencies, order the
`compileXtc` tasks in Gradle's task graph. The plugin already leans on this: the
`test` compile depends on the `main` compile and consumes its output
(`XtcProjectDelegate.java:468-471`, `:657-661`), and every compile depends on
`extractXdk` + the javatools config (`XtcProjectDelegate.java:460-473`).

So under a warm engine, Gradle still sequences the tasks; each task submits to the
shared engine and the upstream binaries it needs are already on the engine's module
path (resolved by `resolveFullModulePath()`). Only *intra-task* multi-module
compilation (several top-level `.x` in one set) needs the `compile(Map)`
cross-reference resolution, which the engine provides.

### 3.5 Run requests ("direct mode for runners")

A run task submits `DirectRunRequest` (`DirectStrategy.java:94-105`) and the
service calls `engine.run(compileResult|moduleName, ...)`, which builds a nested
container under the warm plane and returns a `CompletableFuture<ObjectHandle>`
(`XtcEngine.java:323-345`). Gradle task success maps to the future completing
normally; a thrown XTC exception arrives as the future's exceptional value, which
the strategy converts to a non-zero exit code (mirroring the current contract in
`DirectStrategy.java:52-60`). This is the same in-daemon, single-root/nested-child
model the ownership-validation stress harness already exercises
(`IsolatedDirectExecutor.java:57-75`), so the runtime-correctness story is already
partly proven for runs.

---

## 4. Hard Problems / Risks

1. **Compile input shape: source strings vs on-disk source trees.**
   `XtcEngine.compile(...)` today consumes **in-memory source strings** and
   resolves a *single* top-level module statement per source (`parseModule`,
   `XtcEngine.java:233-250`). A real XTC module is usually an on-disk **directory
   tree** of `.x` files with nested packages. The engine itself flags this as a
   TODO: build the module node tree with `org.xvm.tool.ModuleInfo` and feed it into
   the same pipeline, adding a `compile(Path...)` overload "rather than by shelling
   out to the CLI Launcher" (`XtcEngine.java:123-128`). **This is the single
   biggest prerequisite** for replacing the compile launcher. Emit-to-disk is
   already solved (`writeTo`, §3.3); *read-from-tree* is not.

2. **`run(...)` is under-parameterized vs the plugin's run contract.** The engine
   hardcodes the `run` entry point and passes no arguments
   (`containerRun.runModule("run")`, `XtcEngine.java:344`). The plugin supports an
   arbitrary method name (`-M`, `DirectRunRequest.methodName()`) and module
   arguments (`XtcRunModule.getModuleArgs()`, threaded through
   `DirectStrategy.java:94-105`). `run(...)` must grow method-name + args
   parameters (and an exit-code / return-handle mapping) before it can replace the
   `Runner` path for anything but the default `run()` no-arg case.

3. **We do not control the XDK's own bootstrap of the special modules.** Turtle is
   not compiled as a normal library: `javatools_turtle`'s `compileXtc` is
   **disabled** and it merely ships `mack.x` as a resource
   (`javatools_turtle/build.gradle.kts`); `lib_ecstasy` folds that `mack.x` into
   its own XTC source set (`srcDir(xdkTurtleConsumer)`) and renames the output
   `mack.xtc → javatools_turtle.xtc` (`lib_ecstasy/build.gradle.kts`). The native
   bridge is likewise special: `javatools_bridge` compiles the `_native` module and
   renames `_native.xtc → javatools_bridge.xtc`
   (`javatools_bridge/build.gradle.kts`). These bootstrap compiles cannot be naively
   handed to a generic `compile(Map)`; the plugin must keep special-casing them (or
   keep them on the launcher) until the engine models the prototype bootstrap.

4. **Config-cache and build-cache correctness / determinism.** Compile tasks are
   cacheable (`XtcCompileTask.java:46`) and only track the launcher runtime as an
   input when `rebuild` is set (`XtcCompileTask.java:250-268`). A warm in-daemon
   engine must produce **byte-identical** `.xtc` to the forked compiler, or a build
   that mixes warm and cold outputs will poison the shared build cache. Determinism
   across warm-vs-cold and across repeated warm runs (no state bleeding between
   compiles via the shared pool/native plane) must be proven before warm compile
   becomes a cache-participating default.

5. **Classloader / classpath isolation inside the daemon.** `javatools` must load in
   the isolated child-first loader so the resident engine's process-wide-ish state
   (native templates, constant pools, runtime threads) does not collide with the
   Gradle daemon or with a second engine. The existing `PluginRuntimeClassLoader`
   handles this (`PluginRuntimeClassLoader.java:15-19`), but a warm engine *retains*
   that state for the whole build, so leaks are far more consequential than in the
   fire-and-forget launcher path. The engine must be closed deterministically
   (`XtcEngine.close → shutdownXVM`, `XtcEngine.java:349-352`).

6. **Version skew / "loaded and correct" engine, and self-hosting.** The engine
   loaded must match the target XDK's `javatools` **and** its library binaries. The
   fingerprint SHA-256s the runtime classpath (`DirectRuntimeFingerprint.java:45-48`)
   and the plugin already refuses mismatched launcher versions
   (`XtcJavaToolsRuntime.resolveRuntime`, `XtcJavaToolsRuntime.java:79-80`), which
   covers ordinary consumers. The sharp edge is the **self-hosting XDK build**: the
   `javatools` being compiled is the very code that would host the engine, and the
   library binaries are produced by the same build. A warm engine must be built from
   the *already-published* javatools/libraries for the stage being compiled, never
   from half-built outputs.

7. **Parallel tasks vs one engine, and shared-lazy-state thread-safety.** Gradle
   executes tasks in parallel. `XtcEngine` claims compiles and runs are independent
   and parallelizable "subject to the runtime's shared-lazy-state being
   concurrency-safe" (`XtcEngine.java:73-76`) — which is exactly the hardening this
   whole branch is doing (native-injection singletons, pool caches). Until that is
   proven, concurrent submissions to one warm plane are a correctness risk. Note the
   plugin currently forbids parallel module execution outright
   (`XtcRunTask.java:302-305`) and the ownership stress harness keeps a *window* of
   live containers to catch cross-run sharing (`IsolatedDirectExecutor.java:40`,
   `:107-117`). A Build Service can cap concurrency (`usesService` /
   `maxParallelUsages`) as an interim guard.

8. **Worker API vs in-daemon execution trade-off.** Running the engine in the daemon
   process (as `DirectRuntimeBuildService` does) maximizes warmth but couples engine
   memory and any engine instability to the long-lived daemon. Gradle's Worker API
   with a persistent classloader-isolated worker is the alternative, but a *new*
   worker per task throws warmth away. `XtcRunTask` already carries a TODO to add the
   Worker API for parallel runs (`XtcRunTask.java:79`). The design must pick: warm
   in-daemon service (recommended for the amortization win) with strict isolation and
   close semantics, vs. worker isolation at the cost of re-boot.

9. **Incremental compilation is not method-level (separate project).** The engine
   recompiles a whole module per request; method-granular recompilation is an
   explicit non-goal here (`XtcEngine.java:130-142`). So the warm-engine win is
   **boot amortization**, not incremental compile. Gradle's existing input/output
   up-to-date checks remain the only "don't recompile unchanged modules" mechanism.

---

## 5. Phased Adoption Plan

**Phase 0 — today.** `ATTACHED` default (fork per task/module); `DIRECT` exists but
re-bootstraps per call through the CLI launcher (`IsolatedDirectExecutor.java:50-82`).

**Phase 1 — warm runs, no XDK changes.** Give `DirectRuntimeBuildService` a
fingerprint-keyed warm `XtcEngine` and route **run tasks** through
`engine.run(...)`. Runs are already in-daemon, already ownership-validated, and
already serial (`XtcRunTask.java:302-305`), so this is the lowest-risk swap and the
biggest per-unit speedup (§2.5). Prerequisite: parameterize `run(...)` with method
name + module args (Risk 2). Compile stays on the launcher. `ATTACHED` remains the
untouched fallback.

**Phase 2 — warm compile behind an opt-in mode.** Teach the engine to consume
on-disk source trees (`compile(Path...)` via `ModuleInfo`, Risk 1), then route
`compileXtc` through `engine.compile(...).writeTo(outputDir)` under a new opt-in
execution mode (e.g. `DIRECT_WARM`), verifying byte-identical outputs against the
forked compiler before letting warm outputs participate in the build cache (Risk 4).
Keep the special turtle/bridge bootstrap on the launcher (Risk 3). `ATTACHED` stays
the default and the correctness oracle.

**Phase 3 — warm compile as default for consumers.** Once determinism and
single-engine-under-serial-tasks are proven, flip the default execution mode (via
the existing `-PxtcDefaultExecutionMode` knob, `XtcPluginConstants.java:67`) for
third-party consumer builds. Unify diagnostics onto one engine-level sink
(`XtcEngine.java:78-86`) so compile + run errors share a channel.

**Phase 4 — the XDK's own dependency-heavy build (needs XDK-side work).** Share one
warm engine across **all** `lib_*` compile tasks in a single XDK build — the largest
self-build payoff — after: (a) the special turtle/bridge bootstrap is modeled inside
the engine or explicitly excepted, and (b) parallel or safely-serialized submission
to one plane is proven (Risk 7). This is where "N launcher boots" collapses to "one
warm engine."

**Fallback discipline throughout.** The `ExecutionMode` enum
(`launchers/ExecutionMode.java`) stays; `ATTACHED` remains a correct, isolated
fallback selectable per task or globally; any warm-engine failure can fall back to a
fork without changing the build's observable contract.

---

## 6. Open Questions For Maintainers

1. **Emit vs hand-off.** Should warm compile always `writeTo(outputDir)`
   (`XtcEngine.java:407-421`) so the on-disk `.xtc` remains the cache-tracked
   artifact, or should the engine hand a live `FileStructure`/`CompileResult`
   straight to a downstream run task in the same build, skipping disk entirely for
   compile→run chains?
2. **Determinism.** Are warm-engine `.xtc` outputs guaranteed byte-identical to the
   forked `org.xvm.tool.Compiler` outputs, run-to-run and warm-vs-cold? If not, what
   is the minimum canonicalization needed before warm compile can share the build
   cache (Risk 4)?
3. **Engine reuse scope.** One engine per build (current Build Service lifetime), or
   should a daemon keep one engine alive across builds keyed by fingerprint for even
   more warmth? The latter trades daemon memory and stale-state risk for cross-build
   speed.
4. **Parallelism policy.** Do we cap the Build Service to serial usage
   (`maxParallelUsages = 1`) until shared-lazy-state concurrency is proven, or invest
   in parallel-safe submission now (Risk 7)? How does this interact with the
   already-forbidden parallel run path (`XtcRunTask.java:302-305`)?
5. **Self-hosting bootstrap.** For the XDK build compiling its own `javatools` and
   libraries, exactly which stage's published binaries feed the engine's module path,
   and how do we prevent an engine from ever loading half-built outputs (Risk 6)?
6. **`run(...)` contract.** What return/exit-code and stdout/stderr mapping do we want
   from `CompletableFuture<ObjectHandle>` back into Gradle task success/failure and
   the plugin's redirect model (`XtcRunTask.java:403-464`)?
7. **Worker API.** Is the resident engine acceptable inside the long-lived daemon
   (recommended), or must it live in a persistent isolated worker, and if so how do we
   keep it warm across tasks (Risk 8)?
8. **Turtle/bridge modeling.** Should the special turtle (`mack`) and native-bridge
   (`_native`) bootstrap be taught to the engine, or remain forever on the launcher
   path as a documented exception (Risk 3)?

---

## 7. Key File Map (for implementers)

- New API: `javatools/src/main/java/org/xvm/api/XtcEngine.java`
  (constructor boot `:94-99`; `compile(Map)` `:148-215`; assemble `:196-231`;
  source-tree TODO `:123-128`; incremental TODO `:130-142`; `run` `:323-345`;
  `writeTo` `:407-421`; `close` `:349-352`; `Builder` `:426-451`).
- Engine test / current only caller:
  `javatools/src/test/java/org/xvm/api/XtcEngineTest.java` (module path derivation
  `:146-152`; `writeTo` round-trip `:113-122`). The plugin does **not** yet
  reference `XtcEngine`.
- Execution strategy: `plugin/src/main/java/org/xtclang/plugin/launchers/`
  (`ExecutionMode.java`, `ExecutionStrategy.java`, `ForkedStrategy.java`,
  `DirectStrategy.java`, `ForkedCommandLineBuilder.java`, `ModulePathResolver.java`).
- Build Service / isolated runtime:
  `plugin/src/main/java/org/xtclang/plugin/runtime/` (`DirectRuntimeBuildService.java`,
  `DirectRuntimeFingerprint.java`, `PluginRuntimeClassLoader.java`,
  `DirectCompileRequest.java`, `DirectRunRequest.java`,
  `impl/IsolatedDirectExecutor.java`, `impl/IsolatedLauncherOptionsBuilder.java`).
- Tasks: `plugin/src/main/java/org/xtclang/plugin/tasks/`
  (`XtcLauncherTask.java`, `XtcCompileTask.java`, `XtcRunTask.java`).
- DSL / wiring: `plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java`,
  `XtcPluginConstants.java`.
- XDK build: `settings.gradle.kts`, `xdk/build.gradle.kts`,
  `lib_ecstasy/build.gradle.kts`, `javatools_turtle/build.gradle.kts`,
  `javatools_bridge/build.gradle.kts`, `manualTests/build.gradle.kts`.
- Runtime deployment model: `javatools/src/main/java/org/xvm/runtime/NestedContainer.java:61`
  (`createForHost`).
</content>
</invoke>
