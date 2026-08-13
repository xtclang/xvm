# Kotlin Compiler and Runtime Boundary

Investigation date: 2026-08-13

This note uses `../research-fork-orig` as the relevant Kotlin compiler fork. It focuses on how a Kotlin compiler that may become the main compiler should work closely with a simplified runtime without making the runtime just another compiler package.

## Observed Kotlin Compiler Direction

The fork already points the right way:

- `lang/lsp-compiler` is split into lexer, parser, and compiler subprojects.
- Lexer/parser are stateless and immutable.
- Semantic state lives outside AST nodes.
- `SymbolInterner` and `FrozenSymbolInterner` support canonical identity and incremental derive.
- `MemberIndex` and `TypeRelations` replace large parts of Java `TypeInfo` / `TypeConstant` behavior.
- Planning docs call for immutable phase products: `SourceIndex`, `MemberIndex`, `TypeFacts`, `CallFacts`, `FlowFacts`, `EmitPlan`, `ConstantPlan`, `BytecodeModule`.
- Constant-pool notes identify current `.xtc` v1 as structurally hostile to incremental and parallel compilation.

This is a good basis for both LSP and a future main compiler. It is not by itself a runtime architecture.

## Boundary Rule

The runtime must not depend on:

- AST node object identity
- parser visitor classes
- broad `SemanticModel` fallback maps
- compiler mutable builders
- convergence-only compatibility helpers
- current physical constant-pool insertion order

The runtime may depend on:

- frozen module model
- stable symbol/type/method/property ids
- method body IR
- debug/source maps
- runtime ABI descriptors
- intrinsic declarations

## Proposed Module Split

| Module | Owner | Runtime dependency? |
| --- | --- | --- |
| `xtc-source` | compiler/LSP | no |
| `xtc-syntax` | compiler/LSP | no |
| `xtc-semantics` | compiler | no direct runtime dependency |
| `xtc-module-model` | shared | yes |
| `xtc-xtc-v1` | compatibility | yes, adapter only |
| `xtc-xtc-v2` | future format | yes |
| `xtc-runtime-api` | runtime | yes |
| `xtc-runtime-kotlin` | reference runtime | yes |
| `xtc-backend-llvm` | backend | yes |
| `xtc-java-compat` | migration | adapter only |

The compiler publishes to `xtc-module-model`. The runtime consumes from it. LSP can keep richer syntax and semantic snapshots separately.

## Shared Data Products

### `SourceIndex`

Compiler-only. Good for LSP and diagnostics. Do not require it at runtime.

### `MemberIndex`

Shared concept, but runtime should receive a frozen runtime-facing projection, not the compiler builder object. Useful for dispatch, reflection, and type tests.

### `TypeFacts`

Compiler-owned. Runtime receives canonical type tables and relation helpers, not every intermediate source fact.

### `CallFacts`

Compiler-owned but can generate runtime call descriptors. The runtime needs selected callable, dispatch kind, argument layout, return vector, and specialization.

### `FlowFacts`

Compiler-owned. Runtime receives lowered control-flow/body IR and safepoint metadata.

### `EmitPlan`

Backend-owned. JVM, LLVM, and interpreter can consume the same plan if it is kept backend-neutral.

### `ConstantPlan`

Compiler/serializer-owned. Runtime receives stable metadata tables, not mutable pool-builder internals.

### `BytecodeModule` / `ModuleModel`

Runtime-facing. This should become the shared artifact.

## Kotlin Reference Runtime

A Kotlin runtime should be built as a reference runtime, not as a hidden extension of the compiler.

Responsibilities:

- load frozen module model
- execute normalized body IR
- model object handles and primitives
- model services/fibers explicitly
- provide safe diagnostics
- compare behavior with Java runtime and LLVM backend

Advantages:

- close to compiler tests
- easier than Java runtime to make immutable/snapshot-friendly
- useful for LSP compile-time evaluation
- good oracle for native runtime

Limits:

- still JVM-hosted
- not final performance target
- must avoid using compiler AST as runtime body format

## Runtime Service for LSP

The LSP server can host:

- syntax snapshot cache
- semantic query database
- module model cache
- optional Kotlin reference runtime for evaluation/testing

The runtime service should operate on snapshots. A new edit creates a new compiler snapshot; existing runtime executions either finish against their snapshot or are cancelled.

Useful API shape:

```text
CompilerHost
  parse(fileKey, text) -> SourceSnapshot
  semanticModel(projectSnapshot) -> SemanticSnapshot
  lower(memberKey) -> MethodBodyModel
  moduleModel(moduleKey) -> ModuleModel

RuntimeHost
  load(moduleModel) -> RuntimeModule
  invoke(methodKey, args, options) -> RuntimeResult
  cancel(executionId)
```

## Migration Steps

1. [ ] Extract a frozen `ModuleModel` schema from Kotlin compiler outputs.
2. [ ] Add v1 `.xtc` reader adapter into `ModuleModel`.
3. [ ] Add `RuntimeAbi` descriptors next to `ModuleModel`, not in compiler emit.
4. [ ] Build a Kotlin reference runtime for primitive leaf methods.
5. [ ] Add method-body IR tests independent of binary pool order.
6. [ ] Add Java runtime adapter for comparison.
7. [ ] Add LLVM backend consuming the same body IR.
8. [ ] Gradually move runtime tests from Java-specific expectations to shared model expectations.

## Anti-Patterns to Avoid

- Runtime reaches into `SemanticModel` to resolve missing facts.
- LLVM backend consumes raw AST directly while interpreter consumes bytecode.
- Kotlin runtime imports compiler emit builders.
- Source/LSP keys become runtime identity.
- Constant pool position remains the semantic id in new APIs.
- Java runtime compatibility quirks are normalized as permanent semantics without a named adapter.

## Decision

Use Kotlin to simplify runtime development by sharing immutable model vocabulary, tests, and tooling. Keep architecture separation by making the runtime consume frozen module/model artifacts and runtime ABI descriptors, not compiler internals.
