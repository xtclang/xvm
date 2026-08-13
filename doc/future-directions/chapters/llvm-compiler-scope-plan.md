# LLVM Compiler Scope Plan

Investigation date: 2026-08-13

This is a scope document and tentative task list for an LLVM-based compiler/JIT path. It assumes the current Java-JIT anatomy in [jit-current-anatomy.md](jit-current-anatomy.md), the LLVM study in [llvm-jit-study.md](llvm-jit-study.md), and the longer runtime direction in [runtime-port-self-hosting-study.md](runtime-port-self-hosting-study.md).

## Scope Boundary

LLVM work should compile a normalized XVM method body representation to native code. It should not initially:

- replace the whole Java interpreter
- implement a native heap and GC
- compile arbitrary Ecstasy ASTs directly
- require the current JVM classfile JIT to be finished
- depend on Java `Frame` / `ObjectHandle` internals as the native frame ABI

The LLVM backend should be built around a small ABI and a backend-neutral lowering layer.

## Compiler Inputs

Acceptable first inputs, in order:

1. Current `MethodStructure.getOps()` plus current Java-JIT type-flow data.
2. A neutral method IR built from `Op[]`, call chains, type facts, and register shapes.
3. A future Kotlin compiler `EmitPlan` / method-body IR.
4. A future XTC v2 typed method-body section.

Do not make raw AST the first LLVM input for the existing runtime. ASTs are source artifacts and may not exist for deployed `.xtc` modules.

## Phase 0: Boundary Design

Tasks:

- [ ] Define `CompiledMethodKey`: module id, type id, method id, specialization id, signature, and invalidation version.
- [ ] Define `RuntimeAbi`: context pointer, object handle, result area, status code, exception state, and helper-call table.
- [ ] Define native representation for current `JitFlavor` categories.
- [ ] Define fallback contract to Java interpreter or Java-JIT.
- [ ] Define unload unit and resource tracker.
- [ ] Decide FFM vs JNI based on actual JDK baseline.

Acceptance:

- A design doc has one C ABI table and one Java/Kotlin facade API.
- No ABI field depends on Java object layout unless it is explicitly an opaque Java handle.

## Phase 1: ORC Sidecar Skeleton

Tasks:

- [ ] Add optional native project for `libxvmllvmjit`.
- [ ] Create/destroy an LLVM ORC `LLJIT` engine.
- [ ] Add an in-memory LLVM IR module with one exported function.
- [ ] Look up function pointer by symbol.
- [ ] Call it from Java/Kotlin through FFM or JNI.
- [ ] Release compiled code through ORC resource trackers.

Acceptance:

- A test calls a native compiled `add(i64, i64) -> i64`.
- The library is optional and skipped cleanly when LLVM is unavailable.

## Phase 2: Minimal XTC Bytecode Lowering

Tasks:

- [ ] Build a method CFG from `Op[]`.
- [ ] Lower local registers and simple scopes.
- [ ] Support integer/boolean constants.
- [ ] Support moves, arithmetic, comparisons, conditional branches, loops, and returns.
- [ ] Reject unsupported ops with a structured bailout reason.
- [ ] Compare results against interpreter for small methods.

Acceptance:

- At least ten leaf methods compile from real `MethodStructure.getOps()`.
- Unsupported methods fall back without crashing.

## Phase 3: Shared Lowering Layer

Tasks:

- [ ] Introduce a backend-neutral method IR near `javajit` or shared compiler model.
- [ ] Move type-flow and register-shape decisions out of Java `CodeBuilder` emission where practical.
- [ ] Add Java-classfile backend adapter or keep JVM backend as-is while LLVM consumes the new IR.
- [ ] Add serialization/debug dumping for the method IR.

Acceptance:

- LLVM does not call individual `Op.build(BuildContext, CodeBuilder)` methods.
- New lowering tests validate CFG, register shapes, and guard/finally metadata before backend emission.

## Phase 4: Runtime Helper ABI

Tasks:

- [ ] Add helpers for type constant lookup, object type test, boxing/unboxing, array access, string primitives, and constant literals.
- [ ] Define result-area layout for multiple returns.
- [ ] Define nullable primitive and XVM primitive ABI.
- [ ] Define bailout-to-interpreter metadata for live registers.
- [ ] Add safepoint poll helper.

Acceptance:

- LLVM can compile methods that call helper functions and return multi-slot results.
- Bailout reports a specific method/op/register state.

## Phase 5: Calls and Dispatch

Tasks:

- [ ] Support direct compiled calls by method key.
- [ ] Support runtime call helper for unresolved/dynamic calls.
- [ ] Add inline-cache skeleton for receiver-shaped virtual calls.
- [ ] Support primitive and opaque-reference argument passing.
- [ ] Add fallback for formal type, virtual constructor, and service cases.

Acceptance:

- Compiled method A can call compiled method B.
- Compiled method can call back into runtime for unsupported dynamic dispatch.

## Phase 6: Guards, Exceptions, and Finally

Tasks:

- [ ] Lower current guard/finally preprocessing into neutral IR.
- [ ] Use status-return unwinding first.
- [ ] Map exception object into runtime context.
- [ ] Run finally blocks on normal return, jump, bailout, and exception.
- [ ] Investigate native EH personality only after status-return path is correct.

Acceptance:

- Guard/finally tests match interpreter behavior.
- No native exception unwinds through JVM frames without a documented bridge.

## Phase 7: Services, Fibers, and Suspension

Tasks:

- [ ] Classify ops as non-suspending, safepoint-only, or suspending.
- [ ] Exclude suspending ops from early LLVM compilation.
- [ ] Add poll/safepoint at loop headers and calls.
- [ ] Define status returns for blocked, paused, repeated, and exception states.
- [ ] Integrate with future runtime scheduler API.

Acceptance:

- Compiled code cannot block the scheduler invisibly.
- Long loops can yield at runtime-defined safepoints.

## Phase 8: Native Runtime Expansion

Tasks:

- [ ] Decide object layout or handle-table strategy.
- [ ] Add allocation and memory accounting.
- [ ] Add stack maps or explicit shadow-stack root reporting.
- [ ] Add code invalidation and module unload.
- [ ] Add debug metadata.

Acceptance:

- Native code can allocate and call without depending on Java `ObjectHandle` internals.

## Phase 9: AOT

Tasks:

- [ ] Make lowering independent of live ORC session.
- [ ] Emit relocatable object files for a module.
- [ ] Serialize metadata sidecar.
- [ ] Add runtime loader for compiled artifacts.
- [ ] Add target triple/data layout controls.

Acceptance:

- A small module can run from precompiled object code plus metadata.

## Risk Register

| Risk | Impact | Mitigation |
| --- | --- | --- |
| LLVM backend duplicates Java-JIT op hooks | high maintenance cost | require neutral lowering before broad support |
| Native frames cannot be debugged or bailed out | correctness loss | status-return ABI plus explicit live-register metadata |
| FFM/JNI overhead erases benefit | weak performance | compile coarser leaf regions and batch helper calls |
| Current `.xtc` format forces late graph repair | compiler/runtime complexity | consume adapter-built method IR, pursue XTC v2 |
| Services/fibers hidden in runtime calls | scheduler bugs | classify safepoints and exclude suspending ops early |
| Native GC attempted too early | project stalls | opaque handles first, native heap later |

## Near-Term Candidate Tests

- primitive arithmetic method
- branch and loop method
- nullable primitive return
- multi-return scalar method
- direct call chain of two compiled methods
- unsupported op fallback
- thrown Ecstasy exception converted to status
- finally-on-return behavior
- code unload between test runs
