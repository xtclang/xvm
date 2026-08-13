# AST vs XTC Bytecode Feasibility

Investigation date: 2026-08-13

Second-pass review (2026-08-13): the recommendation below (typed module tables + method IR) is developed into a concrete format/identity/verifier design in [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md), which also records two facts that change the sequencing: `.xtc` already double-encodes bodies (ops + BinaryAST, with execution using the lower encoding), and the Kotlin compiler's only module artifact today is the v1 format itself — so the frozen module model and XTC v2 are one design task, and the schema work should be pulled forward.

This document answers three feasibility questions:

1. If starting from the Kotlin compiler today, how much simpler would it be to use ASTs/semantic IR and remove major parts of the current XTC bytecode/constant-pool format?
2. How does that question change for LLVM?
3. What runtime changes would be required?

## Short Answer

If starting today, the project should not keep the current XTC v1 bytecode and recursive constant-pool format as the primary architecture. It should keep ASTs for source/LSP, but the deployable/runtime artifact should be a typed module model or semantic IR, not raw AST alone.

For LLVM, source ASTs are more useful than current XTC ops because they preserve high-level semantics. But LLVM should still not compile raw ASTs directly. It should compile a typed HIR/MIR/CFG produced by the Kotlin compiler.

Runtime impact is large: replacing XTC bytecode as the central artifact means the runtime needs a new loader, metadata model, method body representation, dispatch metadata, reflection mapping, and migration adapter for old `.xtc` files.

## Question 1: Starting from Kotlin ASTs Today

### What Gets Simpler

Using Kotlin compiler AST/semantic products as the source of truth would simplify:

- constant identity: stable ids/tables instead of recursive pool positions
- method type parameters: owner id plus ordinal instead of `TypeParameterConstant -> MethodConstant -> SignatureConstant` cycles
- incremental compilation: re-emit one member without global pool renumbering
- LSP: source snapshots and semantic facts stay valid without mutable AST fields
- code generation: emit consumes `CallFacts`, `TypeFacts`, and `FlowFacts` instead of rediscovering semantics from op shape
- runtime loading: typed tables can be loaded into arrays/maps directly
- testing: compare semantic module model before byte-for-byte binary output

This could eliminate or downgrade major parts of:

- heterogeneous global `ConstantPool` as semantic identity
- method-local constant pool indirection
- BAST plus op-bytecode duplication
- Java-style late `getPosition()` registration
- Kotlin symbolic-ref bridges needed only to target XTC v1
- bytecode-level semantic recovery in JIT lowering

### What Does Not Get Simpler

The hard language semantics remain:

- conditional contributions
- generic constraints
- variance and relational types
- `Ref` / `Var`
- multi-return values
- services/fibers
- immutability and shareability
- reflection/type objects
- overload selection and method identity
- exception and finally semantics

ASTs do not remove those. They only put the facts at a better level.

### Better Target Than Raw AST

Raw AST is not ideal as the deployed/runtime format:

- it preserves too much source syntax
- it requires semantic analysis at runtime
- it may be unavailable for binary dependencies
- it is not compact enough for normal runtime loading
- it makes reflection/debug/source concerns too entangled

Better target:

```text
Source AST -> SemanticModel -> ModuleModel -> Method IR
```

Where `ModuleModel` contains typed tables and `Method IR` is a lowered, typed CFG suitable for interpretation, LLVM, or a verifier.

### Feasibility

For a new compiler/runtime line: high.

For immediate compatibility with current XDK/runtime: medium-low, because the current Java runtime expects `.xtc` v1 structures and op bytecode. A v1 adapter is required for migration.

## Question 2: Same Question in LLVM Land

### AST/HIR to LLVM Is Easier Than XTC Ops to LLVM

LLVM benefits from high-level typed facts before lowering:

- structured control flow can become clean CFG
- source-level types can drive representation choices
- captures, default args, and overload decisions can be explicit
- exception/finally regions can be explicit
- safepoints can be inserted at semantic boundaries
- generic specialization can be planned before IR emission

Current XTC ops force the backend to recover:

- register types
- high-level call shape
- finally edges
- source-level nullable/relational narrowing
- specialization intent
- constant identity

That recovery is visible in current `BuildContext.preprocess` and per-op Java-JIT hooks.

### Raw AST to LLVM Is Still the Wrong Direct Path

LLVM IR is too low-level for direct AST emission of Ecstasy semantics. A direct AST-to-LLVM backend would either:

- become a second semantic analyzer inside codegen, or
- encode too much runtime behavior in ad hoc helper calls, or
- miss optimization opportunities because semantics are not normalized.

The right LLVM path:

```text
AST -> SemanticModel -> XIR/HIR -> MIR/typed CFG -> LLVM IR
```

Possible layers:

- **HIR/XIR**: source-shaped but semantic, with calls resolved and types known
- **MIR**: control-flow graph, explicit locals/registers, explicit exception/safepoint edges
- **LLVM IR**: ABI-shaped native code

### What Could Be Removed in LLVM-First Design

If LLVM/native were first-class from scratch, the project could remove:

- current op-bytecode as the required runtime body representation
- local/global constant-pool indirection for method bodies
- JVM-classfile-specific JIT naming as the primary specialization identity
- bytecode-only JIT hooks on `Op`
- BAST as parallel method body serialization

It would still need:

- stable module metadata
- type/reflection tables
- method dispatch metadata
- body IR or LLVM bitcode/object cache
- debug maps
- runtime ABI declarations

### Feasibility

For greenfield LLVM runtime: high, but large.

For current project migration: medium. It is feasible if the Kotlin compiler first produces a runtime-facing method IR and the current `.xtc` v1 format becomes an adapter.

## Question 3: Runtime Changes Required

Replacing XTC bytecode as the central artifact requires the runtime to change in these areas.

### Loader

Current:

- read recursive constant pool
- resolve constants
- construct Java structures
- interpret/JIT `Op[]`

Needed:

- read typed module tables
- build stable id maps
- validate acyclic metadata
- expose runtime type/method/property descriptors
- load method IR blocks
- support v1 `.xtc` through adapter

### Type Metadata

Current:

- runtime queries often chase `ConstantPool`, `TypeConstant`, and `TypeInfo`

Needed:

- array-backed type tables
- explicit relation/contribution records
- stable ids for reflection
- cached assignability/member lookup built from typed tables
- no dependency on physical serialization order

### Method Execution

Current:

- interpreter executes `Op.process(Frame, pc)`
- Java-JIT emits JVM bytecode from `Op.build`

Needed:

- interpreter over typed body IR or normalized bytecode
- LLVM backend over typed CFG
- shared call descriptors and result vectors
- explicit fallback/bailout metadata
- explicit safepoints and suspension statuses

### Constants and Literals

Current:

- global heterogeneous pool plus method-local constant pool

Needed:

- typed literal tables
- direct stable ids for cross-component refs
- method-local literal blobs only where compactness matters
- serializer maps semantic ids to physical offsets late

### Reflection

Current:

- reflection is naturally tied to `ConstantPool` and `TypeConstant`

Needed:

- reflection objects backed by stable module/type/member ids
- compatibility layer to expose old v1 identities where needed
- Ecstasy-level reflection facade over runtime metadata

### Debugging

Current:

- Java frames and source maps are tied to interpreter structures

Needed:

- method IR source maps
- compiled-frame metadata
- safe stack formatting independent of broken runtime invariants
- deoptimization/bailout maps for native code

### Services/Fibers

Current:

- Java `ServiceContext` loop owns scheduling and negative op return codes

Needed:

- runtime status model shared by interpreter and compiled code
- safepoint and poll operations in method IR
- scheduler API independent of Java frames
- compiled code cannot hide blocking operations

## Design Options

| Option | Description | Simplicity | Compatibility | Long-term value |
| --- | --- | --- | --- | --- |
| Keep XTC v1 ops forever | JIT/interpreter remain bytecode-centered | low | high | low |
| AST as deployed artifact | Store source-shaped AST and reanalyze | medium | low | medium |
| Typed module tables + method IR | Store semantic metadata and lowered bodies | high | medium via adapter | high |
| LLVM bitcode as artifact | Store LLVM IR/bitcode plus metadata | medium | low | medium-high |
| Native object files as artifact | AOT output plus metadata | low as only format | low | high as cache/output |

Recommended: typed module tables plus method IR, with optional LLVM bitcode/object caches.

## Migration Path

1. Define `ModuleModel` independent of v1 constant-pool positions.
2. Build v1 `.xtc` adapter into `ModuleModel`.
3. Make Kotlin compiler emit `ModuleModel` before v1 binary.
4. Add interpreter/JIT tests against `ModuleModel`.
5. Define method IR and lower from Kotlin semantic facts.
6. Teach LLVM backend to consume method IR.
7. Version XTC v2 around typed tables and method IR.
8. Keep v1 reader/writer for compatibility.
9. Gradually stop treating v1 op bytecode as the authoritative internal IR.

## Verdict

Starting from the Kotlin compiler today, a typed AST/semantic pipeline plus a new module model would be materially simpler than preserving the current XTC v1 bytecode and constant-pool architecture.

In LLVM land, the simplification is even stronger, but only if LLVM consumes a typed lowered IR rather than raw ASTs.

The cost is runtime migration. The runtime must become a consumer of explicit typed metadata and body IR. That is a major project, but it is also the same project needed to port the runtime away from Java cleanly.
