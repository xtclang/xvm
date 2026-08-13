# Runtime Port and Ecstasy Self-Hosting Study

Investigation date: 2026-08-13

This document extends the LLVM JIT study into the long-term runtime question: how to port XVM away from the current Java interpreter, how to keep the Kotlin incremental compiler close enough to the runtime without collapsing boundaries, and how to move as much of the Ecstasy implementation as possible into Ecstasy itself.

The referenced Kotlin compiler work is assumed to be `../research-fork-orig`; the originally mentioned `../research-fork-org` path was not present in this workspace.

Related documents:

- LLVM study: [llvm-jit-study.md](llvm-jit-study.md)
- Performance and fiber runtime strategy: [performance-runtime-strategy.md](performance-runtime-strategy.md)
- LLVM scope plan: [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md)
- Runtime port plan: [runtime-port-scope-plan.md](runtime-port-scope-plan.md)
- Kotlin compiler/runtime boundary: [kotlin-compiler-runtime-boundary.md](kotlin-compiler-runtime-boundary.md)
- Ecstasy self-hosting plan: [ecstasy-self-hosting-study.md](ecstasy-self-hosting-study.md)
- AST-vs-XTC feasibility: [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md)

## Executive Direction

The project should stop thinking of "the runtime" as one monolithic Java thing to replace. The useful long-term split is:

1. **Compiler front end**: Kotlin, Roslyn-style, incremental, immutable syntax and semantic snapshots.
2. **Module and semantic model**: stable typed tables for declarations, symbols, types, methods, constants, and bodies. This is the contract shared by compiler, runtime, LSP, debugger, verifier, and backends.
3. **Runtime kernel**: small host implementation for allocation, object identity, services/fibers, scheduling, exceptions, native I/O, FFI, and code cache management.
4. **Portable runtime libraries**: as much Ecstasy code as possible for collections, text, numbers above primitive kernels, reflection surfaces, resource abstractions, and language-level behavior.
5. **Execution backends**: interpreter/reference execution, JVM classfile JIT while it exists, LLVM/native JIT/AOT, and test harnesses.

The first serious cleanup should be boundary work, not native code. Define what a compiled method sees, what a runtime object is, what a module exports, what a service can do, and what metadata is available without reaching into Java compiler internals.

Performance is a first-class requirement for that boundary work. The architecture should not accidentally preserve Java interpreter costs behind new names. The target production runtime is small and native-oriented: compact module tables, compact fibers, direct layouts for hot objects, AOT/JIT code cache, and Ecstasy libraries above a small host kernel.

## What Should Move Out of Java

The current Java runtime is valuable as a proof-of-semantics vehicle, but it mixes concerns that should become separate products:

- module loading and constant-pool graph repair
- type metadata and `TypeInfo` construction
- interpreter frames and fiber scheduling
- exception propagation and diagnostic formatting
- native bridge objects
- command-line runner behavior
- compiler-specific structures such as `MethodStructure` and `Op[]`

The replacement path should not be "rewrite all this in C++" or "rewrite all this in Kotlin" as a single project. It should be a sequence of smaller separations:

1. Make a runtime-facing module model that can be built from today's `.xtc` and from the Kotlin compiler's future typed module output.
2. Make an execution ABI over that model.
3. Build a Kotlin reference runtime against the ABI for clarity, tests, and LSP/eval use.
4. Build an LLVM/native backend and eventually a native runtime kernel against the same ABI.
5. Retire Java runtime pieces as each boundary gains a replacement and conformance coverage.

## Kotlin Compiler Role

The Kotlin compiler in `../research-fork-orig/lang/lsp-compiler` is already closer to the right architecture than the Java compiler/runtime. Its own docs describe:

- immutable AST and stateless lexer/parser
- external semantic models rather than mutable AST fields
- `SymbolInterner` / `FrozenSymbolInterner` with derive support for incremental edits
- `MemberIndex` and `TypeRelations` as Kotlin equivalents to Java `TypeInfo` and type checks
- explicit phase products such as `SourceIndex`, `MemberIndex`, `TypeFacts`, `CallFacts`, `FlowFacts`, `EmitPlan`, `ConstantPlan`, and `BytecodeModule`
- convergence gates comparing Kotlin output against Java
- known constant-pool/binary-format problems and a path toward stable symbolic refs

That compiler should become the semantic owner for the language. The runtime should not depend on its AST classes or on broad `SemanticModel` fallback maps. The shared layer should be a frozen module/metadata model with stable ids.

Recommended package boundary:

- `xtc-source`: source text, spans, diagnostics, tokens, immutable AST
- `xtc-semantics`: symbols, types, member index, call/flow facts
- `xtc-module-model`: frozen module tables and body IR
- `xtc-xtc-v1`: adapter for current `.xtc` constant-pool/op format
- `xtc-runtime-api`: object/type/service/method ABI interfaces
- `xtc-runtime-kotlin`: reference runtime and interpreter
- `xtc-backend-llvm`: LLVM lowering and native code cache
- `xtc-runtime-native`: long-term native kernel
- `xtc-java-compat`: adapters to current Java `javatools` runtime/compiler while migration is in progress

This keeps Kotlin close to runtime development without letting every runtime piece call into compiler internals.

## Runtime Port Strategy

The runtime port should have two tracks.

### Track A: Kotlin reference runtime

Purpose:

- make runtime semantics readable and testable
- integrate naturally with the Kotlin compiler/LSP process
- provide a stable conformance oracle independent of the messy Java interpreter
- execute a simpler body IR or normalized bytecode from the module model

Non-goal:

- final high-performance production runtime

This runtime can remain JVM-hosted. That is acceptable because its job is clarity, conformance, and boundary validation.

### Track B: Native/LLVM runtime

Purpose:

- execute hot/full programs without the Java interpreter
- provide native object layout, allocation, service scheduling, and code cache
- support LLVM JIT first and AOT later
- keep suspended fibers as compact runtime records rather than parked host-thread stacks
- make hot code run as direct native code with helper calls only for slow paths

Non-goal:

- reuse the interpreter `Frame`/`ObjectHandle` structure directly

This runtime should use the same module model and ABI as the Kotlin reference runtime.

## Performance Thesis

The conservative LLVM sidecar is not the performance destination. It is a way to validate lowering, ABI boundaries, and fallback while the runtime is still Java-hosted.

The fast runtime requires these properties:

- method bodies execute from typed IR compiled to native code, not from per-op interpreter dispatch
- primitive and nullable primitive values remain unboxed through calls where signatures allow it
- hot object operations become direct loads/stores guarded by type/layout/version checks
- arrays, strings, refs/vars, and numeric boxes have stable native layouts early
- memory management is owned by the runtime in production, not delegated to Java object lifetime
- safepoints are explicit in method IR and compiled code
- suspension spills live values to compact continuation frames
- blocked fibers do not keep native stacks or OS threads alive
- runtime metadata can be loaded without source ASTs or compiler state
- core runtime libraries can be AOT compiled and application code can be lazily JIT compiled

This implies a staged performance arc:

1. Java-hosted LLVM proves primitive-heavy methods and fallback.
2. Kotlin reference runtime proves compact state and snapshot semantics.
3. Native kernel defines object layout, fiber frames, allocator, and safepoints.
4. LLVM backend graduates object operations from helpers to guarded direct access.
5. AOT/JIT cache keeps startup and footprint controlled.

Memory management has the same staged shape:

- **Java-hosted**: useful for migration; JVM GC owns object lifetime, so XVM memory accounting and native object access are limited.
- **Hybrid**: selected arrays/strings/boxes move to native storage behind `xvm_ref`; useful but must avoid becoming a permanent dual-GC architecture.
- **Native XVM heap**: production target; XVM owns allocation, headers, roots, barriers, container accounting, and GC.

The native heap is what makes the long-term performance goal credible. It lets the compiler emit allocation fast paths, stack/root maps, write barriers, direct array/string access, and compact suspended fiber roots without Java `ObjectHandle` overhead.

## Self-Hosting Strategy

Ecstasy should own as much of its implementation as practical, but self-hosting should be layered:

1. **Language library self-hosting**: keep/push pure library behavior into Ecstasy modules.
2. **Runtime-library self-hosting**: implement collection/text/number/reflection/service-facing behavior in Ecstasy where host primitives are not required.
3. **Compiler-pass self-hosting**: move pure analysis, lowering, diagnostics, optimizer, and verifier logic into Ecstasy modules once the runtime can execute them reliably.
4. **Compiler front-end self-hosting**: consider Ecstasy lexer/parser/semantic passes after the Kotlin compiler is stable enough to serve as bootstrap and oracle.
5. **Full bootstrap**: Ecstasy compiler modules compile themselves and produce equivalent module artifacts.

The kernel that should remain host-defined:

- memory allocation and object identity
- GC or lifetime management
- thread/service/fiber scheduling and atomics
- native I/O, clocks, OS process integration
- FFI and LLVM ORC wrappers
- code cache, executable memory, and platform unwind metadata
- bootstrap loader and verifier

Self-hosting does not mean deleting all host code. It means the host code becomes a small, explicit kernel and Ecstasy owns the language-level behavior above it.

## AST vs XTC Binary Direction

If the project were starting today from the Kotlin compiler, it would not design the current recursive constant pool and XTC bytecode format. It would use:

- immutable AST for source/LSP
- semantic facts for analysis
- a typed module model for persistence
- a structured method IR or typed CFG for execution/lowering
- stable section/table ids instead of pool position as identity
- optional lowered bytecode or native-object caches as derived artifacts

The existing `.xtc` format must still be supported for compatibility, but it should become an adapter format, not the architecture center.

The most realistic long-term direction is **XTC v2 as typed module tables plus method-body IR**, not raw ASTs as the only deployed format and not today's op stream as the core compiler IR.

## Workstreams

1. **Boundary specification**
   - Define frozen module model.
   - Define runtime ABI.
   - Define object/type/method ids.
   - Define method body IR expectations.

2. **Java runtime stabilization**
   - Add structured failure propagation.
   - Make diagnostics safe.
   - Keep Java runtime usable as compatibility oracle during migration.

3. **Kotlin shared model extraction**
   - Extract compiler semantic products into reusable modules.
   - Ensure runtime consumes frozen facts, not AST internals.
   - Add current `.xtc` v1 loader into the same model.

4. **Kotlin reference runtime**
   - Interpret normalized method IR.
   - Implement services/fibers explicitly.
   - Compare behavior with Java interpreter and Java-JIT.

5. **LLVM/native backend**
   - Build ORC sidecar.
   - Compile whitelisted body IR.
   - Integrate fallback and conformance tests.

6. **Ecstasy self-hosting**
   - Classify host intrinsics.
   - Move pure runtime library behavior into Ecstasy.
   - Move pure compiler passes into Ecstasy when execution is stable.

7. **XTC v2**
   - Version a typed table format.
   - Preserve v1 compatibility through adapters.
   - Make v2 the native output of the Kotlin compiler.

## Decision Summary

- Kotlin should take over the main compiler path, but not become the runtime by accident.
- A Kotlin reference runtime is valuable even if the final production runtime is native.
- LLVM should target a typed method IR/module model, not raw Java `Op.build` hooks.
- ASTs are the right LSP/source representation, but not sufficient as the only deployed runtime artifact.
- Today's `.xtc` v1 format should become a compatibility adapter.
- Ecstasy self-hosting should proceed above a small host kernel, not by pretending memory, scheduling, native I/O, and code cache management can be pure language code immediately.

## Sources Consulted

- Local current repo Java runtime/JIT under `javatools/src/main/java/org/xvm/runtime` and `javatools/src/main/java/org/xvm/javajit`
- Local Kotlin compiler fork under `../research-fork-orig/lang/lsp-compiler`
- `../research-fork-orig/research/plans/PLAN_LSP_INCREMENTAL_RESUMABLE.md`
- `../research-fork-orig/research/plans/PLAN_STATELESS_COMPILER_ARCHITECTURE.md`
- `../research-fork-orig/compiler-refectoring.md`
- `../research-fork-orig/lang/lsp-compiler/constant-pool-binary-format-issues.md`
- `../research-fork-orig/lang/lsp-compiler/pool-symbolic-refs-plan.md`
- Microsoft Roslyn syntax analysis docs: https://learn.microsoft.com/en-us/dotnet/csharp/roslyn-sdk/get-started/syntax-analysis
- rust-analyzer guide and Salsa docs: https://rust-analyzer.github.io/book/contributing/guide.html and https://docs.rs/salsa/latest/salsa/
