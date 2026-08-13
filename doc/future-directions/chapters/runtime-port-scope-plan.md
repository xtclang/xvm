# Runtime Port Scope Plan

Investigation date: 2026-08-13

This is a scope document and tentative task list for moving the XVM runtime away from the current Java interpreter and toward cleaner Kotlin and native/runtime boundaries.

## Goal

Create a runtime architecture where:

- Java interpreter internals are not the semantic center of the project.
- Kotlin compiler products can feed runtime execution without depending on Java AST or `ConstantPool` mutation.
- A Kotlin reference runtime can validate semantics.
- An LLVM/native runtime can evolve behind the same ABI.
- Ecstasy owns the language-level runtime libraries above a small host kernel.
- Production execution can use low-footprint fibers and fast native code without an OS thread or Java frame per Ecstasy fiber.

## Non-Goals

- No immediate deletion of the Java runtime.
- No native GC in the first phase.
- No direct port of `Frame`, `ObjectHandle`, and `ServiceContext` line-for-line into Kotlin.
- No runtime dependency on source ASTs for deployed code.

## Boundary Model

The runtime consumes a frozen module model:

- module id and dependency graph
- type definitions and type relations
- method/property ids and signatures
- method body IR
- literal and metadata tables
- debug/source maps

The runtime exports:

- object allocation and identity
- type metadata/reflection lookup
- opaque object references for compiled code
- method dispatch
- service/fiber scheduling
- exception/failure propagation
- native resource access
- compiled-code ABI helpers

The compiler may produce the module model; it must not be required at execution time unless running an LSP/eval/compiler-service mode.

## Performance Targets

The runtime port should be judged against three long-term constraints:

- **Minimum footprint**: production runtime does not load ASTs, compiler mutable state, Java interpreter frames, or large per-fiber stacks. Metadata is table-backed and lazily loaded where possible.
- **Fiber scale**: suspended Ecstasy fibers are compact runtime records plus continuation frames, not parked OS threads.
- **Fast execution**: hot code is native code over typed method IR, unboxed values, direct native layouts, and helper calls only for slow paths.

Opaque object handles are acceptable during migration, but they are not sufficient for final performance. The native runtime must eventually publish object layouts, barriers, root maps, safepoints, and invalidation rules so LLVM can inline object operations safely.

## Phase 0: Stabilize Current Java Runtime as Oracle

Tasks:

- [ ] Add structured top-level failure propagation from service/fiber execution to `Connector.join()`.
- [ ] Make `Frame.toString()` and stack formatting safe and non-throwing.
- [ ] Add one owner for unhandled Ecstasy exception reporting.
- [ ] Add conformance tests that capture exit status, stdout/stderr, and safe stack traces.
- [ ] Document which runtime behaviors are known proof-of-concept only.

Acceptance:

- Runtime failures are observable as structured test failures, not timeouts or worker-thread stderr.
- Java runtime remains usable as a compatibility oracle.

## Phase 1: Frozen Module Model

Tasks:

- [ ] Define stable ids for modules, types, methods, properties, constants, signatures, and body blocks.
- [ ] Add an adapter from current Java `.xtc` v1 structures to the frozen model.
- [ ] Add an adapter from Kotlin compiler phase products to the frozen model.
- [ ] Separate serialization ids from semantic ids.
- [ ] Add a validator for model invariants.

Acceptance:

- The same small module can be loaded through v1 `.xtc` and through Kotlin compiler output into equivalent frozen model records.

## Phase 2: Kotlin Reference Runtime

Tasks:

- [ ] Implement a simple interpreter over normalized body IR.
- [ ] Implement opaque object handles and primitive value carriers.
- [ ] Specify which handle operations are semantic helpers rather than direct field access.
- [ ] Implement method calls, returns, multi-returns, exceptions, and simple properties.
- [ ] Add a runtime context equivalent to the useful parts of `Ctx`.
- [ ] Add conformance tests comparing Java interpreter, Java-JIT where possible, and Kotlin reference runtime.

Acceptance:

- A small subset of Ecstasy programs runs without using Java `Frame` or `ObjectHandle`.
- Runtime state and diagnostics are ordinary immutable/snapshot-friendly Kotlin data where possible.

## Phase 3: Services and Fibers

Tasks:

- [ ] Model service contexts and fibers explicitly.
- [ ] Define suspension statuses and safepoints.
- [ ] Implement scheduler loop in Kotlin reference runtime.
- [ ] Add resource and future handling for a restricted subset.
- [ ] Add debugger-safe snapshots.
- [ ] Define compact continuation-frame format for suspended fibers.
- [ ] Separate non-suspending native stack execution from safepoint suspension.

Acceptance:

- A compiled/interpreted function can return blocked/paused/repeat status without corrupting execution state.
- A waiting fiber can be represented without retaining an OS thread stack.

## Phase 4: Native Runtime Kernel Design

Tasks:

- [ ] Choose implementation language for kernel components.
- [ ] Define object header, type metadata pointer/handle, immutability bits, owner/container id, and construction state.
- [ ] Define allocation API and memory accounting.
- [ ] Define memory ownership modes: JVM-owned handles, hybrid native objects, and fully native XVM heap.
- [ ] Define service scheduler ABI.
- [ ] Define native helper table used by LLVM code.
- [ ] Define `xvm_ref` representation, root ownership, and helper-call lifetime rules.
- [ ] Define stack/root reporting strategy.
- [ ] Define per-service/per-thread allocation fast path and nursery ownership.
- [ ] Define compact fiber record: status, mailbox/waits, timeout/tokens, frame stack, roots, and debug id.
- [ ] Define container quota semantics: reserve, commit, promote/share, free, and hard-limit behavior.

Acceptance:

- A written ABI is detailed enough for LLVM compiled code and Kotlin reference runtime to share tests.
- The ABI describes what compiled code must spill at safepoints and what the scheduler owns after suspension.

## Phase 5: Hybrid Native Execution

Tasks:

- [ ] Allow Kotlin/Java host to call native compiled methods.
- [ ] Keep arbitrary objects opaque while primitives are unboxed.
- [ ] Add helper calls for type tests, property access, and allocation.
- [ ] Prevent native code from dereferencing Java `ObjectHandle` or Java-JIT bridge object fields.
- [ ] Add fallback to Kotlin reference runtime or Java runtime.
- [ ] Add unload/invalidation.

Acceptance:

- Native code can run selected methods inside the same process with fallback.

## Phase 6: Native Object Model

Tasks:

- [ ] Implement native object layout for selected runtime classes.
- [ ] Add field access and method tables.
- [ ] Add arrays and strings with stable layout.
- [ ] Add root reporting or handle-table rooting.
- [ ] Add moving/non-moving GC decision.
- [ ] Add write barriers, safepoints, and direct-access eligibility checks before LLVM inlines object field access.
- [ ] Add compact layout ids and layout versions for specialization guards.
- [ ] Add direct-access fast paths for primitive arrays, strings, refs/vars, and simple objects.
- [ ] Implement native allocation slow path: nursery refill, quota check, GC trigger, large-object path, and exception/block status.

Acceptance:

- Selected objects no longer require Java object handles.
- LLVM can inline proven-layout object operations with helper slow paths.

## Phase 6b: Native GC

Tasks:

- [ ] Choose first native collector: stop-the-world mark/sweep, semi-space young collection, or simple generational collector.
- [ ] Define exact root sources: compact fiber frames, running compiled frames, runtime globals, service queues, module constants, FFI handle scopes, and code-cache embedded refs.
- [ ] Implement shadow-stack roots or LLVM stack-map/statepoint roots for compiled code.
- [ ] Implement weak refs, finalization/notification queues, and large-object space.
- [ ] Implement write barriers for reference stores.
- [ ] Add optional read barrier only if the chosen collector requires it.
- [ ] Add per-container/service memory accounting and GC pressure policy.
- [ ] Add diagnostics for heap size, live bytes, allocation rate, promotion rate, roots, and pause time.

Acceptance:

- Native object code can allocate, store references, suspend, and collect without Java object handles.
- GC can reclaim unreachable native objects using exact roots from fibers and compiled frames.
- Container hard limits can fail or terminate deterministically through runtime status, not host OOM.

## Phase 6a: Footprint and Throughput Work

Tasks:

- [ ] Measure per-fiber memory in Java runtime, Kotlin reference runtime, and native prototype.
- [ ] Measure per-module metadata footprint and code-cache footprint.
- [ ] Add code-cache eviction/unload by module and specialization.
- [ ] AOT-compile the minimum runtime/kernel libraries needed for startup.
- [ ] Lazy-load reflection/debug/source metadata outside normal execution.
- [ ] Add benchmarks for fiber creation, suspend/resume, service send, primitive loop, array loop, allocation, and dispatch.

Acceptance:

- Runtime footprint can be reported as base runtime, per-module metadata, per-fiber state, per-object overhead, and code-cache size.
- A suspended fiber's steady-state memory is a compact frame/root record, not a native stack.
- Hot loops run through compiled code paths without interpreter dispatch.

## Phase 7: Ecstasy Runtime Libraries

Tasks:

- [ ] Classify existing Ecstasy library code into pure, intrinsic-backed, and host-only categories.
- [ ] Replace host implementations with Ecstasy modules where possible.
- [ ] Define intrinsic declarations for host kernel calls.
- [ ] Add bootstrap order and snapshot artifacts.
- [ ] Add conformance tests per library domain.

Acceptance:

- A substantial part of collections/text/numbers/reflection behavior executes as Ecstasy code over host intrinsics.

## Phase 8: Retire Java Runtime Pieces

Tasks:

- [ ] Replace Java module loader uses with frozen module model adapter.
- [ ] Retire Java interpreter for supported backends.
- [ ] Keep Java runtime only as legacy compatibility path until all conformance gates are covered.
- [ ] Remove Java-JIT class dumping and other development-only behavior from normal runs.

Acceptance:

- Normal execution no longer routes through Java `ServiceContext.execute` for supported targets.

## Task Ownership Hints

- Compiler/model tasks belong near the Kotlin compiler and module-model extraction.
- Runtime ABI tasks should live in a runtime-facing package that does not depend on AST classes.
- LLVM tasks should depend on runtime ABI and method IR only.
- Java compatibility adapters should be quarantined and treated as migration code.

## Open Decisions

1. Should the Kotlin reference runtime live in `research-fork-orig` first or be ported into this repo after the compiler branch lands?
2. What is the minimum JDK baseline for FFM support?
3. What is the native kernel language?
4. Is XTC v2 a required predecessor for native runtime, or can the v1 adapter cover enough?
5. Which tests are the runtime conformance source of truth?
6. When does `xvm_ref` become a direct native pointer rather than an opaque handle-table token?
7. What minimum per-fiber memory target is acceptable for production?
8. Should the native runtime use stackless continuations only, or allow stacklets for selected non-blocking regions?
