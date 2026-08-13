# Second-Opinion Review

Investigation date: 2026-08-13 (second pass)

This chapter is the independent review requested by [for-agents.md](../for-agents.md). Every load-bearing claim in the first-pass documents was re-verified against the repository, and the Kotlin compiler claims were verified against `../research-fork-orig` (branch `symbols-and-types`, HEAD `ca6a7ef48`, 2026-08-11). Verdict up front: **the first-pass architecture holds — staged backend behind a neutral IR, runtime-owned object ABI, layered self-hosting — but several factual claims need correction, two structural contradictions need resolution, the strongest historical counter-evidence was missing, and the recommended sequencing changes.**

New chapters produced by this pass:

- [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md) — LLVM-as-JIT history (JSC, HHVM, Azul, Julia), Cranelift/Truffle/MLIR/Wasm, the JVM-max Plan B, revised tiering, OSR
- [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md) — MMTk vs. custom, conservative-first roots, three fiber models, deopt-across-heaps, Erlang/Pony precedents
- [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md) — the BAST double-encoding fact, ECMA-335 as deliberate precedent, identity design, "one IR, two producers"
- [minimal-cleanroom-runtime-study.md](minimal-cleanroom-runtime-study.md) — runtime size today, cleanroom effort/schedule for a complete interpreter-only runtime
- [risk-matrix-and-decision-gates.md](risk-matrix-and-decision-gates.md) — the requested risk matrix, numeric targets, and go/no-go gates

## Confirmed Findings

The following first-pass claims were verified as correct against source:

- **JIT selection and anatomy.** `-J`/`--jit` → `JitConnector` vs `InterpreterConnector` (`Runner.java:346`); lazy classfile generation via `ModuleLoader.findClass` → `TypeSystem.genClass`; body lowering from `MethodStructure.getOps()` in `BuildContext.assembleCode`; per-op `computeTypes`/`build` hooks. All confirmed at the cited lines.
- **Two separate object worlds, more separate than the docs said.** Zero imports from `org.xvm.javajit` into `org.xvm.runtime` or vice versa; the sole crossover is console-stream reuse in the jitbridge `TerminalConsole`. There is **no mixed-mode execution, no handle bridge, and no fallback path of any kind** — `-J` picks one world for the whole process.
- **Fibers are stackless heap `Frame` chains; suspended fibers hold no OS thread.** Confirmed in `ServiceContext.execute`/`drainWork` and `Fiber.setStatus`. Caveats worth recording: synchronous cross-service calls run the callee inline on the caller's thread (thread borrowing, not fiber blocking); preemption is op-count-based (`MAX_OPS_PER_RUN` = 1,000,000); worker parallelism is a fixed pool of platform threads — no virtual threads anywhere in the interpreter.
- **The interpreter is allocation-heavy and uniformly boxed.** Every `Int64` arithmetic result is a fresh `JavaLong`; every object is a `TypeComposition` + `ObjectHandle[]`; every call allocates a `Frame` + `VarInfo[]`. Two mitigations the first pass didn't mention: primitive arrays are stored unboxed (`long[]`/`byte[]` delegates, 50 files), and tiny domains (UInt8, Char<128, Boolean, Null) are cached.
- **The `runtime/gc` package is a dead experiment** — 736 lines, zero references from production code, functionally untouched since 2023-02. Its long-address, `long[]`-backed object model is a useful *shape* sketch for a native object ABI and nothing more.
- **The constant pool is as hostile to evolution as claimed, and then some.** Positional identity re-shuffled by `optimize()`; `Format.ordinal()` as the wire tag with no per-constant length (unknown formats cannot be skipped); file version check accepts only an exact (major, minor) match; pool identity is runtime-observable state (`ServiceContext.f_pool`, `ConstantPool.withPool`). 97 constant classes; `TypeConstant.java` alone is 8,296 lines.
- **`doc/x.md` supports the service-heap direction more strongly than the first pass claimed.** Per-service allocation scopes, immutable-only escape, per-service GC without cross-service coordination, cheap container "yanking," and real-time per-container metering are all *explicit design text*, not extrapolation. The per-service heap design should be treated as committed intent — see [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md).

## Corrections

Claims that are wrong or materially imprecise in the first-pass documents:

1. **`Op.build` is not a void visitor hook.** It returns an `int` control directive (−1 = continue; positive address = skip/eliminate ops up to that address). The lowering API already embeds peephole/dead-range elision. Matters for anyone designing the neutral-IR producer from ops.
2. **Op coverage arithmetic.** `asm/op` holds 215 op classes; 81 define `build` directly and six shared bases (`OpGeneral`, `OpCondJump`, `OpIndex`, `OpInPlace`, `OpInPlaceAssign`, `OpTest`) cover another ~86, for ≈78% effective coverage. Uncovered as a family: **all tuple-form calls/invokes (20 classes), all property in-place ops (17), tuple/multi vars, `Return_T`.** The "88 files with build hooks" figure in the handoff undercounts inherited coverage and overcounts semantic completeness.
3. **There is no interpreter fallback, and unsupported ops do not "fall back" — they abort.** An unimplemented op throws from `Op.build`, which aborts generation of the entire class. Worse, methods outside the `JIT_LIST` allowlist are **silently stubbed to return default values** — a correctness hazard (risk #12 in the [risk matrix](risk-matrix-and-decision-gates.md)) that should be converted to a loud failure before any benchmarking or user exposure.
4. **The JDK-baseline "open question" is closed.** The repo pins JDK 25 (`version.properties`), the Class-File API is final (JEP 484, JDK 24), and the JIT already depends on `ScopedValue` (final in 25). Consequence: **FFM is the interop default, unconditionally; the JNI contingency planning in the LLVM appendix is moot.**
5. **Memory accounting today is not "approximate" — it is absent.** `bytesAllocated`/`bytesReserved`/`backlogDepth` have no implementation; `cpuTime`/`upTime` throw "unknown native property"; `Container.Control.gc()` has no handler; `Ctx.alloc/allocated/realloc/free` are empty TODOs. The only quota logic in the repo is in the dead GC experiment. First-pass statements that accounting "can be approximate because Java owns objects" understate how far the headline language promise is from existing.
6. **The docs missed Loom, but the code didn't.** `Ctx`'s javadoc plans to "park this virtual thread and schedule a different fiber" — the Java-JIT runtime is already designed around virtual threads. The first-pass fiber discussion (compact continuation frames vs. OS threads) never engages with the fact that the JVM already ships the copy-on-suspend fiber model. See the Loom experiment gate (G1).
7. **The `.xtc` format double-encodes method bodies, and execution uses the lower encoding.** Every method carries both `Op[]` and a BinaryAST (53 node classes, `MethodStructure.getAst()`); the only execution-stack consumer of BAST is debugger eval. The BAST section also carries no independent version/magic. None of the first-pass documents treat this as the IR opportunity it is — see [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md).
8. **The Kotlin compiler claims run both hot and cold against reality.** Verified against the fork:
   - *Understated:* it is not "in-progress front-end work" — it is a complete second implementation (≈230k lines Kotlin, 2,302 tests) with a **full XTC v1 backend** that reached method-for-method bytecode equivalence with the Java compiler across the entire XDK (10,587/10,587 methods, zero unmapped semantic differences, 2026-08-11).
   - *Overstated:* "Roslyn-style incremental" is aspiration, not implementation. There are no red/green trees, no query/memoization engine, no invalidation; `FrozenSymbolInterner.derive()` has three call sites; the pipeline is batch. The phase products named in the first pass (`SourceIndex`, `TypeFacts`, `CallFacts`, `FlowFacts`, `EmitPlan`, `ConstantPlan`, `BytecodeModule`) come from the fork's *planning documents*; the implemented artifact is a batch `SemanticModel` whose facts live in ~20 side-channel maps keyed by AST object identity — with five fallback lookup modes because identity already leaks. The authors' own `step-by-step-roslyn.md` describes the migration as unstarted.
   - *Missing entirely:* there is **no abstract module model**. The fork's only module artifact is the v1 binary format itself, which means the "frozen module model" is a design-and-build task, not an extraction — and it is the same task as XTC v2.
   - *Not green:* the runtime lane (Kotlin-compiled modules executing on the Java runtime) has known silent failures; bytecode convergence ≠ proven execution.
   - *Governance:* personal fork, feature branch, "Alpha/unsupported," gated out of the default build, documented in ~150k lines of partially contradictory agent-handoff markdown. Becoming "the semantic owner for the language" requires upstreaming and a second maintainer (risk #11).

## Contradictions Between First-Pass Documents (Now Resolved)

1. **Small kernel vs. in-process LLVM.** The performance strategy demands a small native kernel; the LLVM plan puts ORC inside it. Resolution: AOT-first kernel, out-of-process compile service, or Cranelift in-process — see [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md).
2. **"Fall back to the interpreter" vs. the native heap.** Cross-world fallback doesn't exist today and cannot exist mid-method once objects live in a native heap. Resolution: fallback tier must share the compiled code's object world — the native runtime carries its own method-IR interpreter; the Java runtime is a differential oracle, never a deopt target. See [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md).
3. **Two IR producers, implicitly two IRs.** The LLVM plan derives the neutral IR from `Op[]`; the AST-vs-XTC doc derives it from Kotlin semantic facts. Unless stated as an invariant, these drift into dialects. Resolution: one specified, verified XIR with two producers and a convergence corpus — see [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md).
4. **Sidecar benchmarks vs. sidecar architecture.** The Java-hosted LLVM sidecar routes every object operation through FFM *upcalls* into Java helpers — the most expensive boundary crossing available. Its object-heavy numbers will measure boundary costs, not the architecture, and will likely lose to the classfile JIT (which gets C2 free, with no boundary). Resolution: the sidecar is ABI rehearsal only; performance go/no-go decisions are made on native-hosted measurements (risk #5, gate G6).

## New Risks the First Pass Did Not Carry

- **LLVM-as-JIT historical failure modes** (JSC abandoned it; HHVM declined it; Azul succeeded with permanent specialist staffing) and **statepoint bitrot** — both argue for conservative-roots-first and a Cranelift understudy.
- **No OSR story anywhere in the plans** — long-running loops never tier up. Resolved by unifying OSR/deopt/suspend/GC-poll into one frame-externalization mechanism.
- **Reference = (identity, type-view).** `maskAs`/`revealAs` and today's `cloneAs`-per-retype mean a bare-pointer `xvm_ref` is semantically insufficient; the view must live in static use-site types with a boxed-wrapper escape hatch, and that is an obligation on the module model, not codegen. Added to the object-ABI discussion in [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md).
- **Silent-stub hazard** in today's JIT (correction #3).
- **Spec-by-implementation**: the binary format now has a written spec (in the fork, 258 KB); runtime *behavior* still has none — every new runtime pays the reverse-engineering tax until differential-test discrepancies are captured as spec text.

## Sequencing Changes Recommended

1. **Pull the reference runtime forward** ([minimal-cleanroom-runtime-study.md](minimal-cleanroom-runtime-study.md)): build it against v1 `.xtc` now rather than after the frozen module model; it produces the behavior spec, the second oracle, and unblocks the Kotlin compiler's runtime lane.
2. **Measure before building** ([risk-matrix-and-decision-gates.md](risk-matrix-and-decision-gates.md)): baseline corpus (G0) and the Loom fiber experiment (G1) are weeks of work that determine whether the native runtime's fiber/footprint case survives contact with a 2026 JVM.
3. **Merge the module-model and XTC v2 workstreams** — they are one artifact ([xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md)); specify XIR from BAST-level semantics, not from `Op[]` reconstruction.
4. **Demote the LLVM sidecar** from first mover to post-XIR ABI rehearsal.
5. **Fix the silent-stub behavior and upstream the fork** as near-term hygiene items that cost little and remove standing hazards.

## Open Questions for Humans

1. What is the actual deployment matrix (server-only? embedded? browser/edge?) — footprint targets and the Wasm backend's priority both hang on this.
2. Is the product requirement to *beat* the JVM-max path on throughput, or to deliver what the JVM cannot (exact container accounting, hard-kill, embeddability)? Gate G3 needs the answer stated, not assumed.
3. Kernel language: is Rust acceptable? (MMTk pushes toward yes; team background may push otherwise.)
4. Who upstreams and co-maintains the Kotlin compiler, and on what timeline does it leave the personal fork?
5. Which body encoding is deprecated first once XIR exists — ops or BAST — and how long must v1 double-encoding be emitted for compatibility?
6. Who owns writing the runtime-behavior specification as differential testing surfaces it?
7. What per-fiber and per-container numeric targets does the product actually need (the numbers in the gates chapter are proposals, not requirements)?
