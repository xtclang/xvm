# For Agents

Investigation date: 2026-08-13

This file is a continuation prompt for another agent or human reviewer. It summarizes the conversation that produced the future-direction documents, the research conclusions so far, and the questions that still deserve independent challenge.

Use it to double-check the work, add missing detail, and propose better or lateral alternatives.

## Repository Context

Repository: `xtclang/xvm`

Current working branch for these notes: `lagergren/future-directions`

Future-direction docs live under:

```text
doc/future-directions/
```

Start with:

- [README.md](README.md)
- [chapters/llvm-jit-study.md](chapters/llvm-jit-study.md)
- [chapters/performance-runtime-strategy.md](chapters/performance-runtime-strategy.md)
- [chapters/runtime-port-self-hosting-study.md](chapters/runtime-port-self-hosting-study.md)
- [chapters/ast-vs-xtc-feasibility.md](chapters/ast-vs-xtc-feasibility.md)

Important nearby repo/code areas:

- `javatools/src/main/java/org/xvm/runtime`
- `javatools/src/main/java/org/xvm/javajit`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy`
- `javatools/src/main/java/org/xvm/runtime/gc`
- `doc/x.md`

Relevant external local fork:

- `../research-fork-orig`

That fork is assumed to contain the Kotlin/Roslyn-style incremental compiler work. Do not use the misspelled `../research-fork-org`; that path was mentioned in conversation but was not the intended location.

## Conversation Summary

The user asked for a serious research pass over XVM/XTC future execution architecture.

Requests included:

- Understand how the current continuously-added JIT works.
- Study whether XTC could get an LLVM-based JIT/compiler.
- Account for the unorthodox current choice: JIT XTC bytecode instead of ASTs.
- Study long-term runtime porting away from Java.
- Explore whether the Kotlin incremental compiler in `../research-fork-orig` should become the main compiler and how runtime boundaries should work with it.
- Study how much Ecstasy can be written in itself.
- Compare AST/semantic-IR vs current XTC bytecode and constant-pool format, including LLVM implications.
- Move all future-direction docs into `doc/future-directions`, use lower-kebab-case chapter filenames, add a README, and keep links working.
- Create and push branch `lagergren/future-directions`; do not open a PR.
- Add deeper treatment of LLVM compiled code manipulating XTC objects.
- Add deeper treatment of fast execution, low footprint, fibers, memory management, and native GC.
- Create this handoff file for other agents/humans to review and extend the work.

One correction from the user: the `ai-dev` preflight tooling should only be used for the `zombiesnack` project. Do not run ai-dev preflight for this repo unless explicitly redirected by the user.

## Documents Produced

Entry point:

- [README.md](README.md)

Primary studies:

- [chapters/llvm-jit-study.md](chapters/llvm-jit-study.md)
- [chapters/runtime-port-self-hosting-study.md](chapters/runtime-port-self-hosting-study.md)
- [chapters/ast-vs-xtc-feasibility.md](chapters/ast-vs-xtc-feasibility.md)
- [chapters/performance-runtime-strategy.md](chapters/performance-runtime-strategy.md)

Planning/scope docs:

- [chapters/llvm-compiler-scope-plan.md](chapters/llvm-compiler-scope-plan.md)
- [chapters/runtime-port-scope-plan.md](chapters/runtime-port-scope-plan.md)
- [chapters/kotlin-compiler-runtime-boundary.md](chapters/kotlin-compiler-runtime-boundary.md)
- [chapters/ecstasy-self-hosting-study.md](chapters/ecstasy-self-hosting-study.md)

Appendices:

- [chapters/jit-current-anatomy.md](chapters/jit-current-anatomy.md)
- [chapters/llvm-jit-appendix.md](chapters/llvm-jit-appendix.md)
- [chapters/llvm-object-abi-notes.md](chapters/llvm-object-abi-notes.md)

## Current Conclusions

### Current JIT

The current JIT is not AST-based.

It is a lazy XTC-bytecode-to-Java-classfile backend:

- `xtc run -J` selects the Java JIT path.
- `JitConnector` and `Xvm` set up a Java-JIT execution environment.
- `ModuleLoader` lazily asks `TypeSystem` for generated class bytes.
- `CommonBuilder` and `BuildContext` lower `MethodStructure.getOps()`.
- Individual `Op` classes expose `computeTypes(BuildContext)` and `build(BuildContext, CodeBuilder)` hooks.

The current interpreter and Java JIT are also separate object/runtime worlds:

- interpreter: `Frame`, `ServiceContext`, `ObjectHandle[]`, `ObjectHandle`
- Java JIT: `Ctx`, `JitFlavor`, `nObject`, bridge classes under `javatools_jitbridge`

### LLVM Feasibility

LLVM is feasible, but it should not be attached directly to every `Op.build(...)` method as a second backend.

Recommended path:

1. Preserve current XTC bytecode as the first compatibility input.
2. Introduce a backend-neutral method IR / typed CFG.
3. Keep the current JVM classfile JIT while the neutral layer matures.
4. Add an LLVM ORC sidecar or native backend behind a stable runtime ABI.
5. Start with primitive-heavy leaf methods and safe fallback.
6. Graduate object operations from helpers to direct native code only after the runtime owns layout, roots, barriers, and safepoints.

### AST vs XTC

If starting from the Kotlin compiler today, current XTC v1 bytecode and recursive constant-pool identity should not be the main architecture.

Recommended model:

```text
Source AST -> SemanticModel -> ModuleModel -> Method IR
```

ASTs are excellent for source/LSP. They are not sufficient as the deployed runtime format. A typed module model plus method IR is the better long-term artifact.

### Kotlin Compiler Boundary

The Kotlin incremental compiler should likely become the semantic owner for the language, but the runtime should not depend on AST objects, broad semantic maps, or compiler builders.

Recommended split:

- compiler/LSP owns syntax and semantic snapshots
- shared model owns frozen module/type/method/body tables
- runtime consumes frozen model and runtime ABI descriptors
- LLVM/JVM/reference interpreters consume method IR
- Java runtime compatibility is quarantined as adapter code

### Runtime Port

Do not think of the runtime as one Java thing to rewrite line-for-line.

Split the future runtime into:

- Kotlin reference runtime for clarity, conformance, LSP/eval, and tests
- native runtime kernel for production performance
- LLVM backend over typed method IR
- Ecstasy runtime libraries above a small host intrinsic/kernel layer

Do not port Java `Frame`, `ObjectHandle`, and `ServiceContext` mechanically into Kotlin.

### Self-Hosting

Ecstasy should own as much language-level behavior as practical.

Layered path:

1. Pure language/runtime libraries in Ecstasy.
2. Runtime-library behavior in Ecstasy over host intrinsics.
3. Pure compiler passes in Ecstasy after execution is stable.
4. Front-end self-hosting only after Kotlin compiler is a stable bootstrap/oracle.
5. Full bootstrap when Ecstasy compiler modules can compile themselves.

Host kernel remains responsible for allocation, GC/lifetime, scheduling, native I/O, FFI, code cache, executable memory, platform unwind metadata, and bootstrap loader/verifier.

### Object ABI

LLVM compiled code manipulating XTC objects is a central issue.

Current docs recommend:

- early LLVM treats non-primitive XTC values as opaque `xvm_ref` handles
- object semantics go through runtime helpers until a native object layout exists
- direct field/array/string/ref access is only allowed for runtime-owned native layouts with known root/barrier/safepoint rules
- `xvm_ref` must eventually move from opaque handle token to native pointer/compressed pointer/tagged ref if production performance is the goal

### Performance and Fibers

Opaque helpers are a bridge, not the end state.

Fast execution needs:

- typed method IR
- unboxed values
- representation planning
- direct native layouts for hot objects
- helper-call elimination through guards and slow paths
- inline caches and devirtualization
- compact fiber continuation frames
- explicit safepoints and root maps
- AOT core runtime plus lazy JIT for app/specialization code

Suspended Ecstasy fibers should not retain native stacks or OS threads. Running compiled code may use the native stack, but suspension must happen only at known safepoints that can spill live state into compact fiber frames.

### Memory Management and GC

The docs now distinguish three memory ownership modes:

- **JVM-owned**: current interpreter/Java-JIT bridge; Java GC owns lifetime; XVM memory accounting is approximate and object-heavy LLVM remains helper-bound.
- **Hybrid**: selected native layouts behind `xvm_ref` while Java/Kotlin objects still exist; useful during migration but risky as a permanent design due to dual-GC rooting, pinning, and cross-heap cycles.
- **Native XVM heap**: production target; XVM owns allocation, headers, roots, barriers, container/service accounting, GC, arrays/strings/refs/vars, and object layouts.

The efficient non-Java runtime path requires:

- compact `xvm_ref`
- compact object headers
- per-service/per-worker bump-pointer nurseries
- allocation slow path for quota, GC, large objects, and hard-limit behavior
- exact roots from fiber frames, compiled safepoints, module constants, service queues, FFI scopes, and code-cache metadata
- shadow stack first if needed, stack maps/statepoints later for lower overhead
- write barriers before direct reference stores
- service-local/shared heap strategy aligned with Ecstasy service/shareability semantics

## Key Assumptions to Challenge

Another agent should not simply accept the current docs. Challenge these assumptions:

- Is LLVM the best native backend, or would Cranelift, MLIR, libgccjit, Graal, or a custom threaded interpreter plus AOT be better for XVM goals?
- Is the proposed typed method IR enough, or should the project adopt MLIR dialects earlier?
- Is XTC v2 typed module tables plus method IR the right persistence artifact?
- Can current `.xtc` v1 remain only an adapter, or will compatibility force it to remain central longer?
- Is a Kotlin reference runtime worth the cost if production must be native?
- Should native GC use MMTk or a custom collector?
- Is stackless continuation lowering the right fiber model, or should stacklets/segmented stacks be considered for some workloads?
- How much code specialization is tolerable before code-cache footprint becomes unacceptable?
- Are service-local heaps compatible with Ecstasy sharing/pass-through semantics?
- Is exact GC mandatory from the start, or can conservative roots be used for the first native prototype without painting the architecture into a corner?
- Can self-hosting compiler passes in Ecstasy coexist with a Kotlin compiler that remains the bootstrap oracle?

## Suggested Review Tasks

1. Read [chapters/jit-current-anatomy.md](chapters/jit-current-anatomy.md) and verify every code claim against the current repo.
2. Read [chapters/llvm-jit-study.md](chapters/llvm-jit-study.md) and identify missing LLVM runtime risks.
3. Read [chapters/llvm-object-abi-notes.md](chapters/llvm-object-abi-notes.md) and challenge the `xvm_ref` plan.
4. Read [chapters/performance-runtime-strategy.md](chapters/performance-runtime-strategy.md) and propose a sharper GC/fiber/code-cache architecture.
5. Read [chapters/runtime-port-scope-plan.md](chapters/runtime-port-scope-plan.md) and turn vague phases into more concrete milestones and acceptance tests.
6. Read [chapters/kotlin-compiler-runtime-boundary.md](chapters/kotlin-compiler-runtime-boundary.md) and compare it to actual files under `../research-fork-orig`.
7. Read [chapters/ecstasy-self-hosting-study.md](chapters/ecstasy-self-hosting-study.md) and suggest which library/compiler pieces should be self-hosted first.
8. Look for contradictions between the LLVM plan, runtime port plan, and AST-vs-XTC feasibility doc.
9. Add a risk matrix for native GC, service-local heaps, self-hosting bootstrap, and XTC v2 migration.
10. Propose at least one alternative architecture that does not use LLVM and explain why it is better or worse.

## Prompt for Another Agent

You are reviewing XVM/XTC future-direction docs in `doc/future-directions`.

Your job is to independently evaluate, challenge, and improve the research. Do not assume the existing docs are correct. Read the linked docs and relevant source code. Verify factual claims against the repository and `../research-fork-orig`.

Focus especially on:

- current JIT architecture
- bytecode vs AST/semantic IR
- LLVM feasibility
- object ABI and `xvm_ref`
- native runtime port away from Java
- Kotlin compiler/runtime boundary
- self-hosting Ecstasy
- low-footprint fiber model
- native memory management and GC
- alternatives to LLVM or to a custom GC

Produce concrete edits or a review document that includes:

- confirmed findings
- incorrect or weak claims
- missing technical risks
- better alternatives
- proposed next milestones
- open questions for humans

Use repo-local Markdown links. Keep docs lower-kebab-case under `doc/future-directions/chapters` unless adding another top-level handoff/index file is clearly useful.

Do not run project-specific ai-dev preflight here unless the user explicitly asks; it was called out as intended only for the `zombiesnack` project.

## Current Branch History

Recent commits on `lagergren/future-directions` at the time this handoff was written:

```text
b0cd25d18 Document native runtime performance strategy
545db11b0 Document LLVM object ABI constraints
7539cd02f Organize future direction docs
33a7cad6d Document future runtime and LLVM directions
```

No PR was opened, per user request.
