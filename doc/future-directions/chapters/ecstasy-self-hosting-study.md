# Ecstasy Self-Hosting Study

Investigation date: 2026-08-13

This document studies how the Ecstasy implementation can use Ecstasy itself as much as possible while still maintaining a small, explicit host kernel.

## Thesis

Ecstasy should self-host at the language and library level first, then at compiler-pass level, and only later at full compiler front-end level. Full self-hosting is a bootstrap project, not a precondition for runtime cleanup or LLVM.

The goal is not "zero host code." The goal is:

- host code provides primitives that cannot be meaningfully implemented in Ecstasy
- Ecstasy code implements language-level behavior above those primitives
- compiler and runtime boundaries are stable enough that Ecstasy modules can participate without special cases

## What Can Be Ecstasy

Good self-hosting candidates:

- collections behavior
- text manipulation
- higher-level number operations once primitive kernels exist
- reflection facade behavior
- resource abstractions
- diagnostic formatting
- compiler source-model utilities
- semantic checks that operate on frozen module/type/call facts
- optimizers over a method IR
- bytecode/IR verification
- test harnesses and conformance specs

Poor first candidates:

- allocator
- GC
- executable memory/code cache
- OS threads and atomics
- blocking I/O primitives
- clocks, entropy, process/environment access
- LLVM ORC wrapper
- bootstrap module loader
- native ABI bridge

## Required Host Kernel

The host kernel should expose intrinsics as a small declared surface, not as random native classes. Minimum categories:

- memory: allocate object, allocate array, make immutable, copy, compare identity
- type metadata: lookup type, check assignability, get method table, get property metadata
- calls: invoke by method id, invoke virtual, call compiled pointer, fallback
- services: send, receive, schedule, yield, block, resume
- exceptions: create, throw/status, capture stack, attach cause
- I/O: console, file, network, time, environment
- concurrency: atomics, locks if needed, scheduler integration
- FFI/codegen: native library calls, LLVM code cache, symbol lookup

Each intrinsic should have:

- Ecstasy declaration
- host ABI name
- argument and return shape
- purity/suspension annotation
- failure behavior
- test coverage

## Bootstrap Layers

### Stage 0: Host compiler and host runtime

Use Kotlin compiler and current Java/Kotlin runtime infrastructure to compile and run Ecstasy code. This stage is allowed to be impure and tool-heavy.

Deliverables:

- stable compiler output
- runtime conformance suite
- intrinsic declaration format

### Stage 1: Self-hosted standard library slices

Move pure library behavior into Ecstasy modules, leaving only primitive kernels as host intrinsics.

Candidates:

- collection algorithms
- string formatting and parsing helpers
- numeric conversions above primitive arithmetic
- diagnostics and stack formatting
- reflection facade utilities

Acceptance:

- Java/Kotlin/native runtime all call the same Ecstasy library modules for those behaviors.

### Stage 2: Self-hosted verifier and optimizer

Write method-IR verification and safe local optimizations in Ecstasy. These operate on frozen module/model records, not on Java/Kotlin AST internals.

Second-pass note: the verifier is more than a self-hosting candidate — once native codegen exists, it is the security gate that preserves Ecstasy's container guarantees for loaded modules, so it must exist regardless ([xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md)). That makes it the highest-leverage first self-hosted compiler component: it is needed anyway, it is pure, and it runs against frozen records.

Candidates:

- control-flow graph verifier
- definite assignment verifier
- simple constant folding
- dead label/block cleanup
- register-shape validation
- intrinsic-use validation

Acceptance:

- The host compiler can call Ecstasy verifier/optimizer modules during build.

### Stage 3: Self-hosted compiler semantics

Move pure semantic passes into Ecstasy once the model APIs are stable.

Candidates:

- type relation checks over frozen type tables
- overload filtering
- annotation and modifier validation
- call fact validation
- diagnostic rendering

Keep Kotlin as the LSP/incremental host and bootstrap implementation until Ecstasy versions are proven.

Acceptance:

- Kotlin and Ecstasy implementations of selected semantic passes produce identical facts on a corpus.

### Stage 4: Self-hosted source compiler

Port lexer/parser/semantic front-end pieces to Ecstasy only after:

- the Kotlin compiler is converged and stable
- module model and runtime are no longer Java-bound
- Ecstasy runtime can execute compiler workloads reliably
- bootstrap artifacts are versioned and reproducible

Acceptance:

- Ecstasy compiler compiles a subset of itself.
- Outputs match Kotlin compiler at semantic/module-model level.

### Stage 5: Reproducible bootstrap

Full bootstrap sequence:

1. trusted Kotlin compiler compiles Ecstasy compiler sources
2. host runtime runs Ecstasy compiler
3. Ecstasy compiler compiles its own sources
4. output is compared against trusted compiler output
5. differences are either debug metadata or documented canonicalization

Acceptance:

- Rebuild is reproducible enough to trust a release pipeline.

## Relationship to Kotlin Compiler

Kotlin should remain the primary LSP and incremental compiler implementation in the medium term. It is pragmatic: excellent tooling, fast development, and good integration with existing Gradle/JVM infrastructure.

Ecstasy self-hosting should start with libraries and passes that operate on stable compiler/runtime data. This avoids forcing the Ecstasy compiler to reproduce every Kotlin editor-service optimization before it can contribute.

## Relationship to LLVM Runtime

LLVM makes self-hosting more valuable because compiled Ecstasy code can become fast enough to host compiler passes. It also makes intrinsic boundaries more important:

- every self-hosted pass that can run under interpreter should also run under LLVM
- every intrinsic must be callable from compiled native code
- every suspension point must be explicit so self-hosted code can cooperate with services/fibers

LLVM does not remove the need for Kotlin bootstrap or a host kernel.

## Task List

1. [ ] Inventory current Ecstasy modules and classify pure vs intrinsic-backed behavior.
2. [ ] Define intrinsic declaration schema.
3. [ ] Add runtime tests that prove host implementation and Ecstasy implementation agree for one library slice.
4. [ ] Choose first self-hosted slice: diagnostics or collection algorithms are likely best.
5. [ ] Define frozen module/model APIs accessible from Ecstasy.
6. [ ] Implement method-IR verifier in Ecstasy.
7. [ ] Run verifier from Kotlin compiler as an optional pass.
8. [ ] Port one semantic validation pass to Ecstasy.
9. [ ] Compare Kotlin vs Ecstasy fact output on corpus.
10. [ ] Add bootstrap artifact/versioning plan.

## Risk Register

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Self-hosting starts too low in the stack | stalls on allocator/scheduler | start with pure libraries and verifier passes |
| Ecstasy compiler depends on unstable Kotlin internals | brittle bootstrap | expose frozen model APIs only |
| Intrinsics become undocumented native sprawl | impossible portability | require declarations, ABI, tests |
| Performance too low for compiler workloads | unusable self-hosting | use LLVM for pass execution later |
| Bootstrap differences hard to interpret | low trust | compare semantic module model before byte-for-byte output |
