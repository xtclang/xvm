# Future Directions

This directory collects exploratory design notes for the long-term XVM runtime, compiler, LLVM backend, XTC format, and Ecstasy self-hosting direction.

Start here:

1. [LLVM JIT study](chapters/llvm-jit-study.md) - current JVM-bytecode JIT anatomy, LLVM feasibility, and the recommended backend-neutral method IR path.
2. [Runtime port and self-hosting study](chapters/runtime-port-self-hosting-study.md) - the long-term split between Kotlin compiler, shared module model, runtime kernel, native execution, and Ecstasy-hosted libraries.
3. [AST vs XTC bytecode feasibility](chapters/ast-vs-xtc-feasibility.md) - whether a future compiler/runtime should keep current XTC bytecode central, move to ASTs, or introduce typed module tables plus method IR.
4. [Performance and fiber runtime strategy](chapters/performance-runtime-strategy.md) - how the conservative LLVM bridge can evolve into low-footprint fibers and fast native execution.

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
