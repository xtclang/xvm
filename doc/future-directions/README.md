# Future Directions

This directory collects exploratory design notes for the long-term XVM runtime, compiler, LLVM backend, XTC format, and Ecstasy self-hosting direction.

Agent/human handoff:

- [For agents](for-agents.md) - continuation prompt and research summary for another agent or reviewer to challenge and extend these notes.

Start here:

1. [LLVM JIT study](chapters/llvm-jit-study.md) - current JVM-bytecode JIT anatomy, LLVM feasibility, and the recommended backend-neutral method IR path.
2. [Runtime port and self-hosting study](chapters/runtime-port-self-hosting-study.md) - the long-term split between Kotlin compiler, shared module model, runtime kernel, native execution, and Ecstasy-hosted libraries.
3. [AST vs XTC bytecode feasibility](chapters/ast-vs-xtc-feasibility.md) - whether a future compiler/runtime should keep current XTC bytecode central, move to ASTs, or introduce typed module tables plus method IR.
4. [Performance and fiber runtime strategy](chapters/performance-runtime-strategy.md) - how the conservative LLVM bridge can evolve into low-footprint fibers and fast native execution.

Second pass (2026-08-13) — verification, challenges, and new directions:

- [Second-opinion review](chapters/second-opinion-review.md) - claim-by-claim verification of the first pass against the repo and the research fork; corrections, contradictions, and resolutions. Read this after the first-pass studies.
- [Alternative backends and precedents](chapters/alternative-backends-and-precedents.md) - what JSC/HHVM/Azul/Julia learned about LLVM as a JIT; Cranelift, Truffle, MLIR, Wasm/WasmGC; the JVM-max (Loom/Valhalla/ZGC) Plan B; revised tiering and OSR.
- [Memory, fibers, and GC alternatives](chapters/memory-fibers-gc-alternatives.md) - MMTk vs custom collector, conservative-first roots, three fiber models, deopt across object worlds, Erlang/Pony precedents for per-service heaps, the (identity, type-view) reference problem.
- [XTC v2 format and method IR](chapters/xtc-v2-format-and-method-ir.md) - the ops+BAST double-encoding fact, ECMA-335 as deliberate precedent, identity/content-hash design, the "one IR, two producers" invariant.
- [Minimal cleanroom runtime study](chapters/minimal-cleanroom-runtime-study.md) - how big the runtime is today (measured), and the smallest complete interpreter-only cleanroom runtime: component budget, schedule, language choice.
- [Risk matrix and decision gates](chapters/risk-matrix-and-decision-gates.md) - the risk register, numeric targets replacing adjectives, and go/no-go gates G0-G6.

Planning chapters:

- [LLVM compiler scope plan](chapters/llvm-compiler-scope-plan.md)
- [Runtime port scope plan](chapters/runtime-port-scope-plan.md)
- [Kotlin compiler/runtime boundary](chapters/kotlin-compiler-runtime-boundary.md)
- [Ecstasy self-hosting study](chapters/ecstasy-self-hosting-study.md)

Appendices:

- [Current JIT anatomy](chapters/jit-current-anatomy.md)
- [LLVM JIT appendix](chapters/llvm-jit-appendix.md)
- [LLVM object ABI notes](chapters/llvm-object-abi-notes.md)

Naming convention:

- `README.md` is the directory entry point.
- Chapter documents live under `chapters/`.
- Chapter filenames use lower-kebab-case.
