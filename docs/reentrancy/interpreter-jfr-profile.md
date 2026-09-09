# Interpreter JFR Profile

Where the XVM interpreter's time and garbage actually go, measured with JFR over the
`manualTests` suite run through `org.xvm.api.XtcEngine` on one warm JVM, plus three synthetic
steady-state workloads that isolate the inner loop from per-run container setup.

Every percentage below comes from a recording made on 2026-09-09 on the machine described in
[Setup](#setup). Nothing here is estimated from reading code; where a number is a *sampled
estimate* rather than a count, it says so.

---

## Setup

| | |
|---|---|
| Machine | Apple M1 Max, 10 cores, 64 GiB RAM |
| OS | macOS 26.6.2 (25G83) |
| JDK | OpenJDK 25, Corretto-25.0.0.36.2, mixed mode, sharing |
| GC | G1 (default), `-Xmx8g` (`-PtestMaxHeap=8g`) |
| Other JVM args | `-ea`, `--enable-native-access=ALL-UNNAMED` (the build's defaults; `xec` and the Gradle plugin also default to `-ea`, so this matches how the XDK actually runs) |
| Branch / HEAD | `lagergren/lazy-instance` @ `d8a9c4f49` |
| JFR | `jdk.jfr.Recording` on the `"profile"` configuration, with `jdk.ExecutionSample` and `jdk.NativeMethodSample` tightened to a 1 ms period and `jdk.ObjectAllocationSample` to `throttle=3000/s` |

Runs go through this branch's runner model: `XtcEngine` compiles in process, then posts each run to
the `runner.xtclang.org` application in container zero via `registerTransientTask` + `startTask`.
No `xec` process is forked; all phases share one warm JVM and one native container plane.

---

## What actually ran

`manualTests/src/main/x/*.x` holds **45** top-level modules (222 `.x` files exist under that tree,
but the subdirectories — `archive/`, `dbTests/`, `jit/`, `jsondb/`, `webTests/`, `multiModule/`,
`new_backend/`, `slow/` — are excluded from the manualTests main source set by its own
`build.gradle.kts`, and were excluded here too).

**26 of the 45 compiled and ran clean, and those 26 are what the profile covers.** The other 19:

| Reason | Count | Modules |
|---|---|---|
| Opens the interactive debugger (`assert:debug`) | 3 | `ConstOrdinalListTest`, `TestDec28`, `TestFizzBuzz` |
| Needs an injected resource the runner does not provide | 5 | `AesRawKeyRepro` (`crypto:Algorithms`), `TestCrypto` (`crypto:CertificateManager`), `TestSimple` (`net:Network`), `TestTcpClient` (`net:Network`), `TestContained` (a `String` named `description`) |
| Fails an assertion inside the module | 4 | `NumericConversions`, `TestRanges`, `TestRegularExpressions` (`Unknown native property: "pattern"`), `xunit_demo` |
| Needs command-line arguments | 2 | `TestRun`, `SimpleApp` (both `Unassigned value: ""`) |
| Needs a specific working directory | 1 | `TestCompiler` (looks for `src/main/x/errors.x`) |
| Does not compile | 2 | `TestContainer` (needs `ecstasy.xtclang.org` loadable as an application module), `TestCompilerErrors` (a deliberate negative test) |
| Deliberate failure probe | 1 | `FailProbe` |
| Runs longer than the 25 s triage budget | 1 | `StringBufferTest` (a randomized soak test, not a hang) |

Two of these deserve to be called out, because they are properties of the *harness and the runner
model*, not of the modules, and they will bite anyone else who tries this:

**`assert:debug` poisons every later run in the same JVM.** It opens `DebugConsole`, which blocks
on stdin *and* calls `frame.f_context.setDebuggerActive(true)`, which sets a process-global flag on
`Runtime`. `ServiceContext.execute` consults `isDebuggerActive()` in its op-budget check and on
every `R_RETURN`, so anything measured after one of these is measuring a different interpreter.
Before excluding them, `EchoTest` and `FailProbe` timed out at 25 s; in the final triage they
complete in 751 ms and 167 ms.

**One hung module wedges every later run through the same engine.** In `runner.x`, `TaskRegistry`
is a singleton `service` and `startTask(Int id) = taskFor(id).start()` — a *blocking* service call
(contrast `submitTask`, which uses `start^()`). The registry's fiber is therefore held for the
whole duration of a run, so a module that never finishes turns every subsequent `startTask` into a
false timeout. In an early triage pass this produced five consecutive bogus 25 s timeouts after
`TestTcpClient` (`TestAnnotations`, `TestArray`, `SimpleApp`, `TestCollections`, `TestCompiler` —
all of which run or fail in well under a second once the engine is clean) before that pass was
abandoned. The harness now rebuilds the engine after any failed run, which is why the triage above
is trustworthy.

---

## Method caveats, stated up front

- **Idle threads are excluded.** 66 % of raw samples in the sweep land in `sun.nio.ch.KQueue.poll`
  on the Gradle worker's IPC thread. Every percentage in this document is **of samples whose stack
  contains at least one `org.xvm` frame** (12 106 of 35 332 in `run-cold`; 8 009 of 29 673 on
  interpreter threads in `run-warm1`). Sample counts are given alongside so the denominator is
  visible.
- **Stack truncation.** JFR's default 64-frame ceiling truncates 26 % of XVM stacks in the sweep's
  run phase, 83 % in the empty-module phase, and **100 %** in the compile phase (mean depth exactly
  64). Root-down call trees are therefore only reliable for the steady-state workloads (0.1–0.4 %
  truncated, mean depth 16). Self and inclusive figures, and the leaf-upward view, are unaffected by
  root-end truncation and are what the conclusions rest on. Raising the ceiling needs
  `-XX:FlightRecorderOptions=stackdepth=…` at JVM start, which would have meant editing the shared
  build logic.
- **Allocation bytes are sampled estimates and they are *wrong in absolute terms*.** Calibrating
  `jdk.ObjectAllocationSample` totals against allocation derived from heap occupancy across GC
  cycles gives an over-attribution factor of **1.5× to 8.2×**, phase-dependent:

  | Phase | GC-derived allocation | ObjectAllocationSample total | ratio |
  |---|---|---|---|
  | `steady-HotArith` | 17.72 GB | 39.94 GB | 2.25× |
  | `steady-HotCall` | 6.61 GB | 54.22 GB | 8.21× |
  | `steady-HotVirtual` | 9.75 GB | 74.48 GB | 7.64× |
  | `run-cold` | 7.96 GB | 11.98 GB | 1.51× |
  | `run-warm1` | 5.74 GB | 19.26 GB | 3.35× |
  | `compile-warm` | 0.70 GB | 2.84 GB | 4.06× |

  So: **totals come from the GC columns, rankings come from the samples**, and a site with a large
  sampled share and no CPU samples is treated as a suspected TLAB-boundary artifact rather than a
  finding (two such are named below).
- **Inlining.** `DebugNonSafepoints` was off. Leaf attribution inside heavily inlined code is
  approximate, which specifically limits how far `ServiceContext.execute`'s self time can be read
  as "dispatch" rather than "an op body inlined into the loop".
- **Variance is reported, not smoothed.** Three identical warm passes are given separately.

---

## 1. Where the time goes

### Compile is not the problem; running is

26 modules, one warm engine, wall clock:

| Phase | Wall |
|---|---|
| compile, cold | 5 100 ms |
| compile, warm | 1 983 ms |
| run, cold | 31 400 ms |
| run, warm ×3 | 26 366 / 25 410 / 27 173 ms (mean 26 316, spread ±3.3 %) |

**Warm compile of all 26 modules costs 2.0 s; warm running of the same 26 costs 26.3 s.** Compile is
about 7 % of compile-plus-run. Everything that follows is about the run side.

The three warm passes are stable; the headline numbers move by at most 1.3 points between them:

| Measure (interpreter threads only) | warm1 | warm2 | warm3 | cold |
|---|---|---|---|---|
| samples | 8 009 | 7 691 | 7 930 | 9 782 |
| `TypeConstant.ensureTypeInfo` (inclusive) | 35.29 % | 34.83 % | 36.13 % | 39.18 % |
| `ClassTemplate.ensureClass` (inclusive) | 28.43 % | 28.92 % | 28.34 % | 28.13 % |
| `OpCondJump.process` (inclusive) | 27.44 % | 28.84 % | 27.70 % | — |
| `Constant.compareTo` (self) | 10.70 % | 11.00 % | 10.92 % | 10.00 % |

### Not all of "run" is the interpreter

Of the 9 889 XVM samples in `run-warm1`, **8 009 (81 %) are on `XvmWorker` threads** — actual
interpretation — and **1 880 (19 %) are on the calling thread**, doing `XtcEngine`'s Java-side
per-run setup. That engine-side slice is dominated by `ConstantPool.register` (24.75 % self of the
calling thread's samples) and `Constant.compareTo` (10.37 %), driven by `prepareForRun`, which
serializes the module to a byte array and reads it back to give the run its own pool, then calls
`linkModules`.

### The fixed cost of a run

An empty module (`module Nop { void run() {} }`), 20 consecutive runs on a warm engine:
**2 619 ms, i.e. 131 ms per run.** Over the recorded phase (2.9 s, 20 runs) only **238 samples**
carried an `org.xvm` frame — against ~740 samples/s that a fully busy thread produces on this
setup, that is on the order of **12–16 ms of XVM CPU per run against 131 ms of wall clock**. The
remaining ~90 % is round-trip latency through the runner: the inclusive profile of that phase is
`Invoke_N1` → `sendInvoke1Request` → `xService.invoke1` → `xContainerLinker.invokeResolveAndLink`
(74 %), plus service construction (52 %). Nothing in the sweep runs for less than this floor.

---

## 2. The interpreter loop specifically

Three synthetic modules were compiled in memory and run for tens of seconds each, so the recording
*is* the inner loop rather than a mixture of it with container setup. Each got one full warmup run
before the measured one.

| Workload | Iterations | Measured wall | Throughput |
|---|---|---|---|
| `HotArith` — loop + arithmetic, no calls | 40 M | 14 447 ms | 2.77 M it/s |
| `HotCall` — same loop, one non-virtual call per iteration | 10 M | 4 446 ms | 2.25 M it/s |
| `HotVirtual` — same loop, virtual call through an interface, alternating between two implementations | 10 M | 6 357 ms | 1.57 M it/s |

Stack quality here is excellent: mean depth 16, 0.1–0.4 % truncated, ~49 % of all samples carry XVM
frames.

### Is dispatch itself hot? No. What the ops call into is.

`HotArith`, self time, 10 765 interpreter samples:

| Self | Method |
|---|---|
| 17.80 % | `ServiceContext.execute` ← the dispatch loop itself (plus whatever the JIT inlined into it) |
| 16.47 % | `Frame.assignValue` |
| 7.09 % | `Frame.getArgument` |
| 6.70 % | `Constant.compareTo` |
| 5.73 % | `OperatorBinding.lambda$bind$0` |
| 4.46 % | `Frame.introduceVar` |
| 4.07 % | `OpInPlace.process` |
| 3.71 % | `ConstHeap.getConstHandle` |
| 3.48 % | `Frame.exitScope` |
| 3.15 % | `OpGeneral.processBinaryOp` |
| 3.00 % | `NativeTemplates.get` |
| 2.92 % | `TerminalTypeConstant.compareDetails` |
| 1.76 % | `TypeConstant.isFormalType` |

And inclusive:

| Inclusive | Path |
|---|---|
| 31.17 % | `OpTest.process` → `processBinaryOp` |
| 26.27 % | `OpGeneral.process` |
| 21.33 % | `TypeConstant.callEquals` (all of it under `IsEq.completeBinaryOp`, 21.32 %) |
| 18.62 % | `ClassTemplate.dispatchBinary` → `OperatorBinding.lambda$bind$0` |
| 13.27 % | `TypeConstant.equals` |
| 12.76 % | `TerminalTypeConstant.compareDetails` |
| 9.85 % | `Constant.compareTo` |
| 5.82 % | `Frame.getConstHandle` → `Container.ensureConstHandle` |
| 5.56 % | `OpTest.calculateCommonType` → `Frame.resolveType` |
| 4.46 % | `Var.process` → `Frame.introduceVar` |
| 4.24 % | `TerminalTypeConstant.containsFormalType` → `TypeConstant.isFormalType` |

The `R_NEXT`/`R_CALL` protocol and the `aOp[iPC].process(frame, iPC)` virtual call are **not** where
the time is. `ServiceContext.execute`'s 17.8 % self is the largest single entry, but that figure
includes op bodies the JIT inlined into the loop and cannot be separated further without
`DebugNonSafepoints`. What is unambiguous is the ~40 % of a pure arithmetic loop that goes into
*type metadata work reached from inside ops*: comparing `TypeConstant`s structurally, resolving
common types, and looking up compositions.

The single clearest instance, from the leaf-upward view of `Constant.compareTo` in `HotArith`
(1 076 samples), is that **98.5 % of it arrives through exactly one path**:

```
OpTest.processBinaryOp
  └ IsEq.completeBinaryOp
      └ TypeConstant.callEquals
          └ TypeConstant.equals
              └ TerminalTypeConstant.compareDetails
                  └ Constant.compareTo   (recursing through NamedConstant.compareDetails)
```

`TypeConstant.callEquals` opens with

```java
TypeComposition clz = clz1.getType().equals(this) ? clz1
                    : clz2.getType().equals(this) ? clz2
                    : ensureClass(frame);
```

Those `.equals` calls are *structural*: `TypeConstant.equals` short-circuits on `obj == this`, but
the op's local constant and the composition's type come from different pools, so identity misses and
the comparison walks the identity chain — module name, package name, class name, string compares —
on every single `IsEq` execution.

### The same picture in the module sweep

On the interpreter threads of `run-warm1` (8 009 samples), with real modules rather than a
microbenchmark:

| Inclusive | Method |
|---|---|
| 98.19 % | `ServiceContext.execute` |
| **35.29 %** | `TypeConstant.ensureTypeInfo` |
| 28.43 % | `ClassTemplate.ensureClass` |
| 27.44 % | `OpCondJump.process` |
| 27.39 % | `Container.resolveClass` |
| 25.97 % | `TypeConstant.collectMemberInfo` |
| 21.59 % | `TypeConstant.callEquals` |
| 20.86 % | `ClassTemplate.getPropertyValue` |
| 17.11 % | `ClassComposition.ensureFieldLayout` |
| 14.93 % | `TypeConstant.isA` |
| 12.19 % | `ConstantPool.register` |
| 11.71 % | `CallChain.invoke` |
| 11.67 % | `P_Get.process` |

**`ensureTypeInfo` is 35 % of interpreter time on the fourth run of the same 26 modules, and it does
not fall between the first run and the fourth** (39.2 % cold → 35.3 / 34.8 / 36.1 % warm). TypeInfo
is not being reused across runs: `XtcEngine.prepareForRun` deliberately serializes each module and
reads it back so the run gets a structurally disconnected `ConstantPool`, and TypeInfo is memoized
per `TypeConstant`, i.e. per pool. Every run rebuilds the world.

---

## 3. Allocation pressure

Totals below are derived from heap occupancy across GC cycles (reliable); the site rankings are
from `ObjectAllocationSample` (ranking reliable, bytes not — see the caveats).

| Phase | Allocated | Rate | Per iteration | GC pauses |
|---|---|---|---|---|
| `HotArith` | 17.72 GB | 1.23 GB/s | **443 B / iteration** | 31 pauses, 0.117 s (0.8 % of wall) |
| `HotCall` | 6.61 GB | 1.49 GB/s | **693 B / iteration** | 13 pauses, 0.087 s (2.0 %) |
| `HotVirtual` | 9.75 GB | 1.53 GB/s | **1 046 B / iteration** | 17 pauses, 0.107 s (1.7 %) |
| `run-warm1` (sweep) | 5.74 GB | 0.22 GB/s | — | 15 pauses, 0.276 s (1.0 %) |
| `compile-warm` | 0.70 GB | 0.35 GB/s | — | 6 pauses, 0.046 s (2.3 %) |

**GC pause time is never above ~2 % of wall in any phase.** The cost of this garbage is the
allocation work and the cache pressure, not stop-the-world time.

Ranked sites in `HotArith` (a pure arithmetic loop — everything here is per-op interpreter garbage):

| Share of sampled bytes | Site | What it allocates |
|---|---|---|
| 36.35 % | `Frame.getArguments` | an `ObjectHandle[]` per multi-argument op |
| 33.80 % | `Frame.introduceVar` | a `Frame$VarInfo` per `Var` op |
| 7.47 % | `xConstrainedInteger.makeJavaLong` | a fresh `ObjectHandle$JavaLong` per arithmetic result — **there is no small-value cache**; every `Int` the interpreter produces is a new object |
| 3.27 % | `OpTest.processBinaryOp` | the argument array for the comparison |
| 17.51 % | `FileStructure.hasLibraryPayload` | **artifact** — 4 CPU samples total; TLAB-boundary over-attribution |

The `Frame` constructor itself allocates twice more per call (`new VarInfo[ahVar.length]` and
`new int[cScopes]`) on top of the `Frame` and the caller's `ObjectHandle[]`, which is visible in
`HotCall` as `Frame.createFrame1` + `Frame.<init>` + `Frame.getArguments`.

Two more sites, both real mechanisms with unreliable byte counts:

- `ConstantPool.openRuntimeSynthesisWindow` returns `() -> --acDepth[0]`, a **capturing lambda
  allocated on every call**, and it is called once per `drainWork` and once per method-chain build.
  Zero CPU samples; garbage only.
- `BlockingQueueAdapter.pollInternal` allocates `new AtomicReference<>(thread)` per park, and
  another on each spurious wakeup in its retry loop. Top-ranked on worker threads in the sweep, but
  **zero CPU samples**, so the byte figure is not credible; the mechanism is real, the magnitude is
  not established.

One allocation finding is *outside* the interpreter but large enough to state: on the engine's
calling thread, **99.3 % of the bytes attributed to `Handy.stream` come from
`XvmStructure.isModified()`**, which `DirRepository$ModuleInfo.ensureModule()` calls on **every
`loadModule`**. It recursively walks the entire module structure creating a `Stream` and a
spliterator per node, purely to answer "is my cached copy stale" — for a question the
`ModuleInfo` record already tracks a file timestamp and size for. CPU cost is small (89 samples,
0.74 %); the garbage is not.

---

## 4. Cheap wins, ranked

### Obvious and local

**W1. `MethodStructure.getOps()` does a system-property lookup on every frame creation.**

> **Provenance correction (2026-09-09).** This was filed here as a master-side cheap win. It is
> not: master's `getOps` has no such lookup. The call was introduced on this branch by
> `e46b2c4f7` "Harden runtime op publication", so it is a self-inflicted regression, and it is
> fixed in `de4abe4a8`. Recorded rather than deleted because the measurement below is sound and
> the misattribution is the kind that quietly turns into a wrong bug report.

```java
var ops = code.getAssembledOps();
if (Boolean.getBoolean(VALIDATE_RUNTIME_CODE_PROPERTY)) {   // ← every call
    code.assertRuntimeReadyForDiagnostics();
}
return ops;
```

`Boolean.getBoolean` is `Boolean.parseBoolean(System.getProperty(name))` — a `Properties` (i.e.
`Hashtable`) lookup plus a `String.equalsIgnoreCase`, per Ecstasy method call.

*Measured:* of the samples whose stack contains `getOps`, **49 % in `HotCall`** (55 in
`String.equalsIgnoreCase` + 18 in `Properties.getProperty`, of 149) and **46 % in `HotVirtual`**
(46 + 7 of 114) are that lookup. `getOps` self time is 4.34 % (`HotCall`) and 2.24 %
(`HotVirtual`), so this is roughly **1–2 % of interpreter CPU spent re-reading a constant**.

*Change:* hoist to `private static final boolean VALIDATE_RUNTIME_CODE = Boolean.getBoolean(…)`.
One line. **Confidence: very high** — the cost is measured, the semantics of a start-up diagnostic
flag do not need to be re-read per call.

**W2. Memoize `TerminalTypeConstant`'s category.**

`Frame.resolveType` calls `type.containsFormalType(true)` on every binary conditional jump; for a
`TerminalTypeConstant` that is `isFormalType()` → `getCategory()` → `isSingleDefiningConstant()` →
`ensureResolvedConstant()` → a switch over the defining constant that reaches
`clz.getFormat()` through `getComponent()`. Nothing is cached: `TerminalTypeConstant` has exactly
two fields, `m_iDef` (a disassembly index) and `m_constId`.

*Measured (`HotArith`):* `OpTest.calculateCommonType` → `Frame.resolveType` 5.56 % inclusive;
`TerminalTypeConstant.containsFormalType` → `isFormalType` 4.24 % inclusive; `isFormalType` 1.76 %
self; `ensureResolvedConstant` 1.28 % self.

*Change:* cache `Category` in a field once the defining constant resolves. The value is a pure
function of immutable structure after resolution. **Confidence: high.**

**W3. Stop allocating a lambda per `openRuntimeSynthesisWindow`.**

The depth counter is already a `ThreadLocal<int[]>`; the returned `Auto` can be a per-thread
preallocated closer over the same array instead of a fresh capturing lambda per call.

*Measured:* ranked #2 by sampled bytes on interpreter threads in the sweep, **0 CPU samples** — so
this is a pure garbage reduction of unestablished size, not a CPU win. **Confidence: high that it
is free to fix, low on how much it buys.**

**W4. Give `xConstrainedInteger` a small-value handle cache.**

`makeJavaLong` unconditionally does `new JavaLong(getCanonicalClass(), lValue)`. Every `Int` the
interpreter computes is a new object; 7.47 % of sampled bytes in a pure arithmetic loop.

*Change:* an `Integer`-cache-style table of handles for a small value range, per template (the
template is per-container, so the cache has a clean owner and no cross-container sharing problem).
`JavaLong` handles carry a composition and a value; whether they are ever mutated or identity-compared
would need checking before caching. **Confidence: medium-high on the win, medium on the safety.**

**W5. Don't re-walk the module tree to answer "is this cached module stale".**

`DirRepository$ModuleInfo.ensureModule()` → `module.isModified()` → full recursive
`stream(getContained()).anyMatch(...)` over the whole structure, on every `loadModule`.

*Measured:* 2.94 GB of sampled bytes in `run-cold` (99.3 % of everything attributed to
`Handy.stream`), 0.74 % of CPU. This is engine/run setup, **not** the interpreter — but every
`engine.run(...)` pays it, and the sweep pays it 26 times per pass.

*Change:* the cheapest correct version is to keep the tree walk but stop allocating a `Stream` per
node (a plain loop over `getContained()`); the better version is a modification counter maintained
on mutation. **Confidence: high on the measurement, high that the plain-loop version is safe.**

### Real, but needs a design change

**W6. `TypeConstant.callEquals` re-derives the comparison composition on every `IsEq`.**

*Measured:* `callEquals` is **21.33 % inclusive** in `HotArith` and **21.59 %** in the warm sweep;
`Constant.compareTo` is the single hottest self frame in the sweep at **10.7–11.0 %**, and 98.5 % of
it arrives through this path.

*Change:* `OpInvocable.getCallChain` already solves exactly this problem with a per-op monomorphic
inline cache keyed on the target's `TypeComposition`
(`context.getOpInfo(this, INFO_COMPOSITION)` / `INFO_CHAIN`). `OpTest`/`IsEq` has no equivalent and
re-resolves from scratch. Giving it one is not a one-liner — the cache key has to be the *pair* of
compositions, and it interacts with the op-info cache's own cost (W7) — but the shape already exists
in the codebase. **Confidence: high that ~10 % of interpreter CPU is recoverable here; medium on
the design.**

**W7. The op-info cache is a `WeakHashMap`.**

```java
private final Map<Op, Map<Enum<?>, WeakReference<?>>> f_mapOpInfo = new WeakHashMap<>();
```

Every invoke does *two* `getOpInfo` calls (chain and composition), each a `WeakHashMap.get` (which
polls the reference queue through `getTable()`), then an `EnumMap.get`, then a `WeakReference.get`,
then a `Class.cast`.

*Measured (`HotVirtual`):* `getOpInfo` **4.78 % self** and `setOpInfo` **2.63 % self** — 7.4 % of
interpreter CPU, all of it self time, both 100 % leaf. In `HotCall` (monomorphic, so no cache
thrash) `getOpInfo` is still 2.18 %. The `setOpInfo` share in `HotVirtual` is the megamorphic case
rewriting the cache every iteration, which is worth knowing on its own: this inline cache degrades
to *worse than no cache* for a bimorphic call site.

*Change:* the comment on the field says "only one fiber can access the service context at any time,
a simple HashMap is used" — but the map is weak on the *key*, which is what makes each lookup expensive,
and the values are weak too. A per-op inline-cache field (guarded by the owning context) or an
identity-keyed open-addressed table would be far cheaper. Weak keying exists to avoid retaining ops
belonging to unloaded containers, so this cannot just be swapped for a `HashMap`.
**Confidence: high on cost, this needs a lifetime design.**

**W8. TypeInfo does not survive a run.**

*Measured:* 35 % of interpreter CPU in the warm sweep, flat from run 1 to run 4 of the same modules.

*Cause:* `prepareForRun` serializes the module through a byte array specifically so the run gets its
own `ConstantPool`, and TypeInfo is cached on `TypeConstant`, i.e. per pool. This is the largest
single number in the whole profile and it is entirely a consequence of the isolation model.

*Change:* a design change — either share a linked, TypeInfo-populated module across runs of the same
binary, or move the TypeInfo cache to something that outlives a pool. Both have obvious isolation
consequences that this profile cannot adjudicate. **Confidence: very high on the measurement, this
is not a local fix.**

**W9. 131 ms of fixed cost per run, ~12–16 ms of it CPU.**

The rest is round-trip latency through the runner's blocking `startTask` and
`xContainerLinker.invokeResolveAndLink`. Relevant to anything that runs many short modules — a test
runner, an LSP, the manualTests suite itself. **Confidence: high on the measurement; the fix is the
runner/container design.**

**W10. Per-op allocation: 443 bytes per iteration of a pure arithmetic loop.**

`Frame.getArguments` (an `ObjectHandle[]` per multi-arg op) and `Frame.introduceVar` (a `VarInfo`
per `Var` op) are 70 % of it. Both are structural: fixed-arity ops could use a reusable scratch
array if nothing retains it past the op (which needs checking against the deferred/continuation
paths), and `VarInfo` could be created lazily since `getVarInfo` already tolerates a null slot.
**Confidence: high on the numbers, medium on either fix being safe.**

---

## 5. What I could not determine

- **How much of `ServiceContext.execute`'s 17.8 % self time is dispatch versus inlined op bodies.**
  `DebugNonSafepoints` is a JVM start-up flag and enabling it would have meant editing shared build
  logic, which was out of scope. The claim "dispatch is not the bottleneck" rests on the *inclusive*
  numbers (which are unaffected), not on that 17.8 %.
- **Absolute per-site allocation bytes.** Over-attributed by 1.5×–8.2× depending on phase. Only the
  rankings and the GC-derived totals are load-bearing above.
- **Whether `BlockingQueueAdapter.pollInternal` really allocates as much as it appears to.** It
  ranks first by sampled bytes on worker threads and has zero CPU samples; both a genuine
  allocate-per-park and a pure TLAB-boundary artifact fit that evidence, and I could not separate
  them without touching production code.
- **The runner's own interpreted work versus the module's.** Both are Ecstasy running through the
  same `ServiceContext.execute`; nothing in the Java frames distinguishes them. The synthetic
  workloads sidestep this (their runner cost is one fixed 131 ms against 4–14 s of measured work),
  but the sweep numbers include it.
- **The compile phase's call structure.** 100 % of compile-phase stacks hit the 64-frame ceiling
  (mean depth exactly 64), so only its leaf/self numbers are usable. Given compile is 7 % of the
  total, this was not worth chasing.
- **What the 19 excluded modules would have added.** Notably `StringBufferTest` (a long randomized
  soak) and `TestArray`/`TestCollections`-style data-structure work are exactly the shapes most
  likely to shift an interpreter profile.
- **Anything about other hardware, other GCs, or a longer-warmed JIT.** One machine, one JDK, G1,
  one warmup pass per workload.
- **Whether the wins compose.** W1, W2 and W6 all sit on the same `OpTest` path; fixing the biggest
  may well absorb the smaller ones rather than adding to them.

---

## Appendix: the harness

`javatools/src/test/java/org/xvm/api/InterpreterProfileTest.java` — **throwaway test-only code
added for this exercise**, tagged `@Tag("heavy")` so it is excluded from an ordinary `test` run. It
reuses the existing `org.xvm.api.JfrProfile` for the per-phase summary and additionally keeps its
own `Recording` per phase, dumped to disk for the offline analysis above (`JfrProfile` deletes its
temp file on close and does not expose it, and call-tree/allocation-caller analysis needed the raw
file). Its module discovery follows the shape of the existing `EngineSuiteCompileTest.sequential()`,
which already sweeps the same 45 sources through one warm engine on the compile side.

No production Java under `javatools/src/main` was modified.

```bash
OUT=/tmp/prof
./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.triage" \
    -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=$OUT --rerun-tasks --no-build-cache
./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.profile" \
    -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=$OUT --rerun-tasks --no-build-cache
./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.profileSteadyState" \
    -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=$OUT --rerun-tasks --no-build-cache
```

`triage` and the measured phases are separate Gradle invocations on purpose: triage deliberately
provokes runs that hang, and (per the `TaskRegistry` note above) doing that in the same JVM as the
measured passes leaves stray work running underneath the recording.

The recordings land in `$OUT/*.jfr`. The offline analysis used four small single-file Java programs
over `jdk.jfr.consumer` (flat/inclusive profiles with idle-thread exclusion and a per-thread filter;
leaf-upward caller chains for one method; the GC-occupancy allocation calibration; and an event
inventory). They were scratch tools and are not checked in.
