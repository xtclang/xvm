# Same-JVM Launcher Stress Plan

Status: phase 1 smoke task added; broader diagnostics and benchmarking remain
backlog.

The current `manualTests:runParallelStress` task is valuable because it asks the
XVM runner module to start many manual-test modules in parallel containers
during one runtime invocation. That catches container-startup races inside the
runtime, but it does not cover an equally important use case: invoking the
javatools `Launcher` several times in the same JVM and proving that one
invocation does not leave stale runtime, compiler, native-template, `VIEWS`, or
service state that corrupts the next invocation.

That same-JVM shape is what the plugin direct-execution mode wants. The Gradle
plugin has a `DirectStrategy` that calls the runtime through
`DirectRuntimeBuildService`, `PluginRuntimeClassLoader`, and
`IsolatedDirectExecutor`; the isolated executor eventually calls
`Launcher.launch(...)`, `new Runner(...).run()`, or `new TestRunner(...).run()`
in-process. This avoids process startup and command-line option round trips, so
it should be much faster than forking. Historically it was also fragile because
the runtime behaved like "one program per JVM" and cached owner-scoped data in
JVM globals.

## Goal

Add a stress and benchmark harness that repeatedly executes XTC compile, run,
and test workloads in one JVM, both serially and concurrently, and validates
that each execution owns its own container-scoped state.

The harness should answer three questions:

- Does repeated same-JVM `Launcher` use preserve behavior compared to the
  current forked process path?
- Do two same-JVM executions share only explicitly process-wide resources, not
  container-owned templates, handles, constant pools, services, compositions, or
  views?
- How much wall-clock time do we recover by avoiding process startup, repeated
  class loading, and repeated launcher setup?

## Target Modes

### Baseline Forked Mode

Use the existing forked launcher behavior as the semantic baseline. This mode
does not prove reentrancy, but it gives the current correctness and performance
reference.

Measurements:

- wall-clock time per module,
- total task time,
- exit codes,
- stdout/stderr or xUnit output shape,
- peak resident memory if available from the process wrapper.

### Current Parallel-Container Mode

Keep `manualTests:runParallelStress` as the existing runtime startup race
finder:

```bash
./gradlew :manualTests:runParallelStress \
  -PstressIterations=50 \
  -PstressModules=TestReflection,TestArray,TestServices
```

This mode starts many test modules from one runner invocation. It should remain
manual or opt-in because it is intentionally aggressive and can expose unrelated
runtime representation bugs.

Do not simulate this by starting several independent Gradle/manual-test
processes against the same checkout. The branch audit observed truncated `.xtc`
files, closed build-cache pack entries, and transient classloading failures when
unrelated Gradle builds wrote the same generated outputs concurrently. Those are
build-output isolation problems, not useful evidence about runtime owner sharing.

### Same-JVM Serial Launcher Mode

The first smoke task is:

```bash
./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=50 \
  -PsameJvmModules=TestNumbers,TestCollections,TestReflection
```

This task uses `ExecutionMode.DIRECT`, so the Gradle plugin calls
`DirectRuntimeBuildService`, reuses one build-scoped isolated runtime classloader
for the selected runtime fingerprint, and invokes `Runner.run()` once per module
entry. That is the execution shape that can expose stale JVM-global runtime
state such as the old `VIEWS` and `INSTANCE` caches:

```text
Gradle task JVM
  DirectRuntimeBuildService
    PluginRuntimeClassLoader
      Runner(TestArray).run()
      Runner(TestNumbers).run()
      Runner(TestReflection).run()
      Runner(TestArray).run()
      ...
```

The task intentionally differs from `runParallelStress`. `runParallelStress`
creates many containers through one XTC `Runner` module invocation.
`runDirectSequenceStress` creates many direct Java runner invocations in the same
Gradle task and classloader owner.

The current direct-mode task already invokes `Runner` repeatedly in one JVM and
attaches ownership diagnostics through the Gradle plugin direct executor. A
future richer harness should add structured artifacts, timings, and equivalent
coverage for `TestRunner` and compiler paths:

```java
for (String module : modules) {
    var options = runnerOptionsFor(module);
    int exit = new Runner(options, console, errors).run();
    assertEquals(0, exit, module);
    OwnershipDiagnostics.assertValid(observedContainers());
}
```

The important invariant is that invocation N must not depend on, modify, or
reuse owner-scoped objects from invocation N-1 unless that sharing is explicitly
owned by a process-wide resource and documented as such.

### Same-JVM Parallel Launcher Mode

After serial mode is stable, add a parallel mode:

```bash
./gradlew :manualTests:runSameJvmStress \
  -PsameJvmIterations=100 \
  -PsameJvmModules=TestReflection,TestArray,TestServices \
  -PsameJvmMode=parallel
```

Implementation sketch:

```java
ExecutorService executor = Executors.newFixedThreadPool(parallelism);
List<Future<Result>> results = modules.stream()
        .map(module -> executor.submit(() -> runOneModule(module)))
        .toList();

for (Future<Result> result : results) {
    result.get().assertSuccess();
}

OwnershipDiagnostics.assertValid(observedContainers());
```

This mode is the closest simulation of a Gradle daemon or language-server
process that compiles or runs independent work in one JVM.

### Plugin Direct-Mode Loop

Add a plugin-level integration stress task only after the javatools-level
harness is useful. It should execute through the same surface that the plugin
uses today:

- `DirectStrategy`,
- `DirectRuntimeBuildService`,
- `PluginRuntimeClassLoader`,
- `IsolatedDirectExecutor`,
- `Launcher.launch(...)`, `Runner.run()`, and `TestRunner.run()`.

This is important because the plugin direct path adds one more owner: the
build-scoped isolated runtime classloader. The stress test must prove that
runtime state is scoped to that build/runtime entry, not leaked into the Gradle
daemon or into a different direct runtime fingerprint.

The plugin/direct-mode variant must also isolate generated XTC and cache output
per build service or per stress run. Otherwise a file-system race can hide the
runtime result by making one invocation read another invocation's partially
written module file or jar.

## Required Diagnostics

### Container Visibility

`OwnershipDiagnostics.assertValid(...)` needs real `Container` roots. The
current `Launcher` API returns an exit code. The direct-run path now uses the
smallest production-safe hook:

- `Connector.diagnosticContainer()` is a default no-op for connectors with a
  different ownership model,
- `InterpreterConnector` retains the completed main container after `join()`
  only for opt-in diagnostics,
- `Runner.diagnosticContainer()` exposes that completed container after
  `Runner.run()`,
- `IsolatedDirectExecutor` stores a bounded recent-container window in the
  build-scoped direct runtime classloader and validates that window after each
  successful direct run.

A callback or listener is still the preferred shape for broader future
coverage, because it can cover `TestRunner`, compile/test direct requests, and
parallel execution without teaching production return values about diagnostics:

```java
var probe = new RuntimeOwnershipProbe();
var runner = new Runner(options, console, errors);
runner.setDiagnostics(probe);
int exit = runner.run();
probe.assertValid();
```

### Ownership Validation

After each warm path, validate:

- native template tables,
- computed `Lazy` template metadata,
- `templatesByType`,
- `TypeComposition` caches,
- service contexts,
- handle/composition owners,
- constant-pool ownership,
- and cross-container identity sharing.

Use default non-invasive mode during normal stress loops. Long loops should
validate a bounded recent-container window rather than retaining every completed
container strongly:

```java
OwnershipDiagnostics.assertValid(recentContainers);
```

The current container must always be included. That catches stale owner values
reachable from the current run, while the window catches direct cross-run
sharing without turning the diagnostic harness into a heap-retention test.

On failure, emit the full dump as an artifact:

```java
String dump = OwnershipDiagnostics.dump(true, containers);
writeFailureArtifact(module, iteration, dump);
```

Forced lazy dumping belongs only in the failure path or in an explicitly named
diagnostic run, because it changes cache warmup.

### Explicit Process-Wide Allowlist

Some state is intentionally process-wide. The validator and docs should
recognize it instead of treating every shared object as a bug.

Known examples:

- a main container sharing class templates with its own `NativeContainer` parent
  through `Container.getTemplate(...)`,
- terminal input/output and JLine terminal state,
- the `xLocalClock` daemon `Timer`,
- the OS file-watch daemon holder,
- immutable Java constants such as empty arrays or enum-independent strings,
- plugin direct-mode classloader entries scoped to one Gradle build service.

The allowlist must stay narrow. Anything that carries a `Container`,
`ConstantPool`, `ClassTemplate`, `ObjectHandle`, `TypeComposition`, `Service`,
`Frame`, `Fiber`, or module structure is owner-scoped unless proved otherwise.

## Benchmark Plan

Record measurements for each mode:

- forked process baseline,
- same-JVM serial launcher loop,
- same-JVM parallel launcher loop,
- plugin direct-mode loop.

For each run, capture:

- module list and iteration count,
- total wall time,
- median and p95 per-module time,
- JVM startup count,
- classloader count,
- container count,
- GC count and pause time if cheap to collect,
- peak heap after warmup and after teardown,
- failure count and first failure artifact path.

The first useful report can be plain text under `manualTests/build/reports`,
for example:

```text
mode=forked modules=12 iterations=10 total=...
mode=same-jvm-serial modules=12 iterations=10 total=...
mode=same-jvm-parallel modules=12 iterations=10 total=...
speedup.serial=...
speedup.parallel=...
```

Backlog task:

- Add a controlled benchmark target that runs the same known-working manual-test
  module sequence once through forked JVM execution and once through
  `ExecutionMode.DIRECT` in the same Gradle task JVM.
- Use the same compiled inputs and module list for both modes; do not run
  unrelated Gradle tasks concurrently against the same checkout.
- Capture the report above plus ownership-validation failures from the direct
  run.
- Treat the benchmark as informational until the same-JVM direct validation is
  stable across the full known-working module set.

This benchmark matters because the direct mode is not only a correctness goal.
It is the path that can make language-server incremental compile/run loops and
Gradle test tasks practical without starting a fresh VM for every attempt.

## Failure Classes This Should Catch

The harness is designed to provoke:

- stale native-template `INSTANCE` and `NativeTemplates` owner mistakes,
- static `VIEWS` or composition reuse across containers,
- static enum handle reuse across containers,
- owner-derived type/method/constant metadata cached in process globals,
- process-wide singleton services retaining the first caller's pool,
- non-final lazy fields that publish half-built owner state,
- service, fiber, and terminal state that survives into the next run,
- plugin direct-mode classloader leaks between build-scoped runtime entries,
- and compiler/runtime incremental state that assumes one execution per JVM.

Some of these are fixed by this branch; others remain backlog. The purpose of
the test is to keep finding the next unsafe global until same-JVM execution is
boring.

## Implementation Phases

### Phase 0: Diagnostic API

Add the smallest diagnostic hook needed to capture containers created by
`Runner` and `TestRunner`. Do not change launcher semantics or public exit-code
behavior.

Current state: done for interpreted `Runner` direct-mode execution through
`Connector.diagnosticContainer()`, `InterpreterConnector`, `Runner`, and
`IsolatedDirectExecutor`. `TestRunner` and compiler direct-mode ownership roots
remain future coverage.

### Phase 1: Same-JVM Serial Manual Task

Add a manual Gradle task, probably in `manualTests`, that loops through selected
modules in one JVM and records validation plus timing artifacts.

Current state: `manualTests:runDirectSequenceStress` provides the direct-mode
same-JVM sequence stress with ownership validation enabled after every
successful direct run. By default it runs the same known-working module set as
`runSequential`: all `testModuleNames` except `TestAnnotations`, which is
already documented as failing. This phase is only fully done when the task also
records structured failure artifacts and timing artifacts.

Validated branch verification passed with two direct iterations of `TestArray`
and `TestReflection`, and the default task now targets two iterations of the full
known-working module set. The first full-list run exposed an all-container
retention problem in the diagnostic harness itself; the validator now keeps a
bounded recent-container window for long stress runs.

### Phase 2: Same-JVM Parallel Manual Task

Extend the same harness to run independent module executions concurrently.

Done when two or more containers can execute concurrently and the ownership
validator can inspect them together after warmup.

### Phase 3: Plugin Direct-Mode Stress

Add a plugin integration stress path that executes through the direct-mode
Gradle plugin classes, including the isolated runtime classloader.

Current state: done for direct run requests via
`manualTests:runDirectSequenceStress`. Broader direct compile/test request
coverage and benchmark artifacts remain backlog.

### Phase 4: Non-Default CI Gate

Keep the long stress loop opt-in, but add a short same-JVM smoke variant that
can run in CI after the must-fix global state has been burned down.

Done when CI has one cheap same-JVM serial smoke test and developers have a
documented long race-hunting command.

## Open TODOs

- Add a `TestRunner` diagnostic hook or equivalent container observer.
- Teach `OwnershipDiagnostics` how to write structured failure artifacts.
- Add benchmark reporting to `manualTests:runDirectSequenceStress`.
- Add same-JVM parallel mode after serial mode is reliable.
- Add direct compile/test integration stress through `DirectRuntimeBuildService`.
- Investigate asynchronous native-service classloader context in direct mode.
  `TestFiles` same-JVM stress can log `NoClassDefFoundError` for
  `org/xvm/runtime/template/reflect/xClass$1` from `OSStorage` watcher fibers
  even though the class is present in the built `javatools` jar and the Gradle
  task exits successfully.
- Define and document the process-wide allowlist used by the validator.
- Isolate XTC output, jar output, and Gradle cache locations for any stress mode
  that starts more than one outer Gradle process.
- Compare benchmark output against forked execution and keep the report in
  build artifacts.
- Decide which short same-JVM smoke test is stable enough for CI.

## Non-Goals For The First Pass

- Do not fix JIT-specific ownership problems as part of this harness.
- Do not make plugin direct mode the default until the same-JVM stress result is
  clean and repeatable.
- Do not hide failures behind broad static allowlists.
- Do not use forced lazy evaluation in normal validation loops; reserve it for
  failure dumps and explicit diagnostics.
