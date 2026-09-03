# Compiling in parallel on a warm engine — task list

The target is the one the XDK direction already sanctions: a resident host compiles many requests
against an **already-built XDK**, concurrently, in one VM, without going through `Launcher`. That
is `LspSupport`'s stated contract — *"the methods on the LspSupport itself can be assumed to be
thread-safe and concurrent"* — and its `compile(source, input, errs)` signature, which takes the
library per call.

Building the XDK itself in parallel is a separate, harder problem (the library is being produced as
you go); it is T7 and deliberately last.

## Where this starts from — measured, not assumed

| | result |
| --- | --- |
| Sequential, 45 manualTests modules, one warm engine | 42 ok, **0 crashes**, 9.5s |
| Parallel, 42 modules, 8 threads, 3 iterations | **42/42 every iteration**; 4660 / 2578 / 2489 ms |
| Cold sequential vs warm parallel | 10.6s -> ~2.5s |
| Cloning `ecstasy` per compile | 60-72ms warm, **~18% of a warm compile** |

Two things are already true and should not be re-litigated:

- **A compile never touches a container.** `containerNative()` is reached only from `runFrom` and
  `diagnosticContainer()`. Concurrent compiles are concurrent users of a module repository, not
  concurrent containers.
- **Concurrent compiles do not mutate shared library structures today.** A read-through
  `LinkedRepository` clones each library module into that compile's own `BuildRepository` and
  returns the clone (`LinkedRepository:119`). That is why the parallel runs are correct - and it is
  also what costs the 18%.

## T1 - Share the library instead of cloning it

The one change that matters. Link and inject the system libraries **once per engine**, publish
their pools, and serve them read-only to every compile.

1. Hoist `prelinkSystemLibraries` out of `compileInternal` to a `Lazy.Bound` on the engine, so
   `setErrorListener` and `linkModules(repo, false)` run once rather than per compile.
2. Hoist `injectNakedRefType` for the library modules the same way. It is idempotent - the same
   NakedRef type every time - so a library injected once satisfies every later compile.
3. Stop read-through cloning for the shared library: the per-compile `BuildRepository` holds only
   the modules being compiled; library modules are resolved from the shared, published repository.
4. Mark the shared library pools published (`markRuntimePublished`).

**Verification.** Clone time per compile goes to zero (instrument as in
[engine-compile-divergence.md](../engine-compile-divergence.md)); `EngineSuiteCompileTest` stays at
0 crashes; `EngineParallelCompileTest` stays 42/42 across iterations; measure warm compile time
before and after and state the delta.

**Risk, and why it is bounded.** A compile that registers a NEW constant into a shared library pool
- which happens when user code parameterizes a library generic - would leak it into every later
compile. That is exactly what `assertRegisterBeforeRuntimePublished` exists to catch, so the
failure mode is a loud error at the offending write rather than a silent cross-request leak. T2
makes that error usable.

### T1 progress - implemented, measured, and blocked on T4

**Done and measured.** The change is small: prepare each library once (`ensureLibraryPrepared`,
keyed by repository so a per-call library gets its own preparation), take the NakedRef type from
that preparation rather than from a clone in the build repo, and turn read-through OFF so
`repoBuild` holds only the modules being compiled.

| | before | after |
| --- | --- | --- |
| Sequential, 45 manualTests modules | 9494 ms | **5855 ms** (-38%) |
| crashes | 0 | 0 |
| ok / fail | 42 / 3 | 42 / 3 |

One bug found on the way, caught immediately by `EngineSuiteCompileTest`: `injectNakedRefType`
looked the turtle up **in `repoBuild`**, which only ever contained it because read-through had
cloned it there. With cloning off, all 43 compiles threw "Mack module (javatools_turtle) is
missing". Sourcing the type from the prepared library fixes it - and is the more honest place for
it to come from.

**Blocked on T4, exactly as this plan predicted.** With the clone gone, concurrent compiles share
the library's `TypeConstant`s, and `EngineParallelCompileTest` goes from 42/42 to **0/42**, every
one of them:

```
NullPointerException: Cannot invoke "TypeInfo.isPlaceHolder()" because "info" is null
```

That is the TypeInfo placeholder race - row 26 of the master issue board, *"TypeInfo placeholder
identity race strands types as 'being built'"*. The per-compile clone was what hid it. This is the
plan's own warning coming true: *"the clone is currently what makes concurrent compiles safe, so
removing it moves the burden to the shared read path"*.

**State.** T1 is reverted from the tree so the suite stays green, and the diff is kept at
[t1-share-the-library.patch](t1-share-the-library.patch) - 106 lines, re-appliable once T4 lands.
It is worth ~38% sequentially on its own, so if concurrency slips, T1 could ship first behind a
single-threaded engine contract; that is a decision, not a default.

**T4 is therefore no longer optional or later - it is T1's blocker**, and it has a precise target
rather than a general audit: make `TypeInfo` construction safe for concurrent readers of a shared
library, starting from master row 26.

## T2 - Make a publication-guard trip actionable

Today the guard throws. For a host serving requests it has to say which module, which constant, and
which request, and it has to arrive as a diagnostic the caller can attribute rather than an
exception escaping `compile()`.

**Verification.** A test that deliberately registers into a published library pool gets a
diagnostic naming the constant and the module, and the engine remains usable for the next compile.

## T3 - Shared `TypeInfo`

Falls out of T1: once library modules are shared, the `TypeInfo` built on their `TypeConstant`s is
built once instead of per compile. `ensureTypeInfo` was 12% of runtime startup in
[the performance analysis](../../perf/runtime-performance-analysis.md), and compiles pay a similar
cost today.

**Verification.** Measure it separately from T1 rather than claiming it as part of the same number:
count `ensureTypeInfo` calls per compile before and after.

## T4 - Audit the shared read path for thread safety

T1 turns "each compile has its own copy" into "every compile reads one structure". That is the
point at which the read path has to actually be safe:

- `DirRepository`'s scan cache - already the subject of a fixed data race (row 27, merged as #547);
  re-check it under concurrent load.
- `ConstantPool`'s lookup maps under concurrent read, including the lazily-created per-format
  locator tables.
- The `TypeInfo` cache from T3, which becomes a concurrently-populated cache rather than a
  per-compile one.

**Verification.** A stress test at higher thread counts than 8, run repeatedly, asserting every
module matches its sequential result - the shape `EngineParallelCompileTest` already has. A single
green run is not evidence; iterate.

## T5 - Prove diagnostics stay per request

Each compile already collects into its own `ErrorList`, but that has not been tested under
concurrency, and a host attributing another request's errors to the wrong file is worse than a
slow compile.

**Verification.** N concurrent compiles of modules with *distinct, known* errors; each result must
contain exactly its own and none of its neighbours'.

## T6 - Finish aligning the API with `LspSupport`

- Output repository per call (the input repository landed in `0bf7c88bd`).
- Adopt the `TC-xx` diagnostic code vocabulary rather than inventing a second one.
- Console capture for runs - they already built `xExternalConsole` for this; take theirs rather
  than writing a second.

## T7 - Building the XDK itself in parallel

Deliberately last. Every earlier task assumes a stable prebuilt library; here the library is the
output. It needs dependency ordering between module compiles and a story for what "the library" is
while it is still being produced. Worth doing, but not before T1-T5 make the simple case solid.

## What not to do

- **Do not route through `Launcher`.** It is a CLI entry point that does whole-process setup per
  invocation. It was used once as a diagnostic - to establish that warm-JVM compilation works at
  all, which it does - and has no place in the engine.
- **Do not make the engine a singleton.** `LspSupport.instance()` plus a one-shot `configure` is
  process-global first-configuration-wins state, and it forecloses the isolated parallel compiles
  its own proposal asks for.
- **Do not treat a passing parallel run as proof of thread safety.** The current one passes because
  of the clone, not because the shared path is safe; T1 removes that protection and T4 is what
  replaces it.
