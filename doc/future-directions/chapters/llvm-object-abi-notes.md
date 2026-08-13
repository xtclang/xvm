# LLVM Object ABI Notes

Investigation date: 2026-08-13

This appendix answers a specific LLVM feasibility concern: if Ecstasy methods are JIT-compiled to LLVM, how can the generated code manipulate XTC objects?

Short answer: only through a runtime-defined object ABI. LLVM can optimize code after the object model exists, but LLVM should not own that object model.

Related notes:

- Main LLVM study: [llvm-jit-study.md](llvm-jit-study.md)
- LLVM compiler scope plan: [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md)
- Runtime port scope plan: [runtime-port-scope-plan.md](runtime-port-scope-plan.md)
- Performance and fiber runtime strategy: [performance-runtime-strategy.md](performance-runtime-strategy.md)

## Current Object Representations

The current Java runtime does not expose one clean native object layout.

Interpreter objects are Java handles:

- `ObjectHandle` carries `TypeComposition` and mutability state.
- `Frame` stores registers in `ObjectHandle[]`.
- Template-specific handles store data in Java fields, such as `xString.StringHandle` with `char[]`.
- Arrays use `ArrayHandle` plus delegate handles.
- Runtime behavior depends on templates, `ClassComposition`, `TypeComposition`, `CallChain`, `ServiceContext`, and `Frame`.

Java-JIT bridge objects are different Java objects:

- Generated objects are rooted at `org.xtclang.ecstasy.nObject`.
- `nObject.$meta` stores owner/container id, immutability, construction state, and native-private flag bits.
- `Ctx` provides result slots and runtime helper access.
- `JitFlavor` describes optimized primitive/reference shapes.
- `nRef` models value-backed and property-backed `Ref` / `Var`, sometimes using Java `MethodHandle`.
- Bridge arrays and strings use Java class fields and Java arrays, not native offsets.

This means native LLVM code has no stable layout to dereference. It cannot treat a Java `ObjectHandle`, a Java-JIT `nObject`, or a future Kotlin reference-runtime object as a C struct unless the runtime explicitly wraps it in an ABI.

## Principle

LLVM-generated code should see object references as one of two things:

1. **Opaque reference token**: `xvm_ref` is an identity-preserving handle whose contents are hidden from native code.
2. **Runtime-owned native pointer**: `xvm_ref` is, or can be decoded to, a pointer into a native XVM heap with a documented header/layout/GC contract.

The first form is the safe starting point. The second form is a later runtime-port milestone.

Raw Java object pointers are not an acceptable ABI:

- the JVM does not promise stable field offsets as an external native ABI
- moving GC can relocate objects
- JNI local/global references are handles with lifetime rules, not arbitrary pointers
- FFM does not allow native code to freely dereference Java object internals
- Java object identity and Ecstasy object identity may not remain identical during migration

## Memory Management Modes

The object ABI has to name who owns memory.

| Mode | Owner of XTC object memory | LLVM reference form | GC/rooting model | Performance ceiling |
| --- | --- | --- | --- | --- |
| Java-hosted | JVM heap | opaque handle-table token | Java GC plus JNI/FFM handles | good for primitive leaf code, poor for object-heavy code |
| Kotlin/JVM reference runtime | JVM heap | opaque handle-table token | Java GC plus runtime slots | good for correctness/LSP/eval, not production speed |
| Hybrid | JVM plus native XVM heap | tagged `xvm_ref` or discriminated handle | dual roots/adapters | useful transition, risky long-term |
| Native XVM heap | XVM runtime | native pointer, compressed ref, or tagged ref | exact XVM GC roots and barriers | required for fastest object code and tight accounting |

Java-hosted execution can count requested allocations through hooks such as `Ctx.alloc`, but it cannot cheaply and exactly manage XTC object lifetime because Java owns the real objects. Native XVM execution can make allocation, accounting, and GC part of the runtime ABI.

## Early ABI: Opaque Handles

For the Java-hosted LLVM sidecar, use an opaque handle-table model:

```c
typedef struct xvm_ctx xvm_ctx;
typedef uintptr_t xvm_ref;
typedef uint32_t xvm_type_id;
typedef uint32_t xvm_field_id;
typedef uint32_t xvm_method_id;

typedef enum xvm_status {
    XVM_OK,
    XVM_EXCEPTION,
    XVM_BLOCKED,
    XVM_UNSUPPORTED,
    XVM_DEOPT
} xvm_status;
```

The exact representation can be a Java-side table id, a JNI global reference wrapper, a Kotlin runtime handle id, or a native heap pointer later. LLVM IR should not depend on which one it is.

The runtime helper table should cover at least:

```c
xvm_status xvm_ref_type      (xvm_ctx*, xvm_ref, xvm_type_id* out);
xvm_status xvm_is_a         (xvm_ctx*, xvm_ref, xvm_type_id, bool* out);
xvm_status xvm_ref_eq       (xvm_ctx*, xvm_ref, xvm_ref, bool* out);
xvm_status xvm_get_field    (xvm_ctx*, xvm_ref, xvm_field_id, xvm_ref* out);
xvm_status xvm_set_field    (xvm_ctx*, xvm_ref, xvm_field_id, xvm_ref value);
xvm_status xvm_ref_get      (xvm_ctx*, xvm_ref ref_box, xvm_ref* out);
xvm_status xvm_ref_set      (xvm_ctx*, xvm_ref var_box, xvm_ref value);
xvm_status xvm_array_get    (xvm_ctx*, xvm_ref array, int64_t index, xvm_ref* out);
xvm_status xvm_array_set    (xvm_ctx*, xvm_ref array, int64_t index, xvm_ref value);
xvm_status xvm_box_i64      (xvm_ctx*, int64_t value, xvm_type_id type, xvm_ref* out);
xvm_status xvm_unbox_i64    (xvm_ctx*, xvm_ref value, xvm_type_id type, int64_t* out);
xvm_status xvm_alloc        (xvm_ctx*, xvm_type_id type, xvm_ref* out);
xvm_status xvm_freeze       (xvm_ctx*, xvm_ref value, xvm_ref* out);
xvm_status xvm_call         (xvm_ctx*, xvm_method_id method, const xvm_ref* args, uint32_t argc);
xvm_status xvm_poll         (xvm_ctx*);
```

Primitive-specialized variants should exist for hot paths so every integer array operation does not box. For example, `xvm_array_get_i64` and `xvm_array_set_i64` can work for arrays whose element representation is known by the runtime.

Handle lifetime must also be explicit:

- input handles are valid for the duration of the compiled call unless promoted
- helper-returned handles are rooted in the current native frame, result area, or runtime context
- loops and calls must expose safepoints so the runtime can see live references
- bailouts must report live reference registers
- unloading compiled code must release any helper tables or embedded handles

This design is slower for object-heavy methods, but it is correct and incremental. It allows LLVM to accelerate primitive-heavy code, branches, loops, and small direct calls without pretending the Java runtime has a native object ABI.

The handle table must not become the permanent performance model. It is acceptable only while the Java/Kotlin host owns objects. Every high-frequency helper should have a planned inline replacement:

- `xvm_is_a` becomes a type-id or layout-version guard plus slow helper
- `xvm_get_field` becomes a guarded offset load
- `xvm_set_field` becomes a guarded offset store plus write barrier
- `xvm_array_get_i64` becomes bounds check plus direct element load
- `xvm_ref_get` becomes direct ref-box load only for native-layout boxes
- `xvm_call` becomes direct call, inline cache, or devirtualized inlined body

The mature rule is: helper calls remain for slow paths and semantic boundaries; hot layout-proven operations are direct native code.

## Later ABI: Native Object Layout

Direct object manipulation requires a runtime-owned heap or a constrained native object arena. The minimum object header probably needs:

- type descriptor pointer or compact type id
- owner/container id for accounting and isolation
- flags for immutability, construction state, service identity, and native-private state
- optional hash/identity field
- GC metadata or descriptor reference

Field access needs:

- stable field ids and layout versions
- field offset tables per concrete layout
- generic substitution and type-guard metadata
- nullable and flattened primitive field representation
- barriers for reference stores
- read barriers if the GC requires them
- deoptimization metadata if a guard fails

Dispatch needs:

- method table or inline-cache layout
- call-chain semantics
- support for virtual constructors, formal types, default parameters, multi-returns, and service boundaries

Arrays and strings should be treated as special native layouts because they dominate useful object operations. They need separate layout contracts for:

- element storage kind
- mutability
- slice/view ownership
- bounds checks
- Unicode representation for strings
- hash caching
- copy-on-write or persistent-array behavior

Only after these contracts exist should LLVM emit direct loads/stores for object data.

## Native GC Contract

For direct native object access, the runtime must publish a GC contract as part of the ABI:

- `xvm_ref` encoding: pointer, compressed pointer, handle-table index, or tagged value
- object header shape and alignment
- layout id and layout-version invalidation rules
- allocation fast path and slow path
- exact root reporting for compiled frames
- continuation-frame root layout for suspended fibers
- write-barrier sequence for reference stores
- optional read-barrier sequence if the GC needs it
- large-object and external-memory handling
- weak-reference and finalization queues
- container/service memory accounting

The compiler must then emit GC-aware code:

- keep references in known LLVM values
- spill live references at safepoints or describe them in stack maps
- call the allocation slow path when the nursery is exhausted
- run write barriers after reference stores
- avoid holding unreported references across helper calls
- record deopt metadata for live primitive and reference values

The current Java `runtime/gc` package contains an experimental `GcSpace` / `MarkAndSweepGcSpace` model with long addresses, roots, weak refs, and byte limits. It is useful precedent for an XVM-owned address space, but it still stores resources on the Java heap. The production native runtime needs the same kind of explicit root/address discipline backed by native storage and compiled-code metadata.

## Fast-Path Lowering Model

Object operations should be lowered in tiers:

```text
semantic operation
  -> helper call
  -> helper with primitive specialization
  -> layout guard plus direct fast path plus helper slow path
  -> devirtualized or inlined operation when specialization proves the shape
```

Example field read:

```text
field.load receiver, field_id
  if unknown layout:
      call xvm_get_field(ctx, receiver, field_id, out)
  if known native layout:
      guard receiver.layout_version == expected
      value = load receiver + field_offset
      if guard fails:
          call xvm_get_field(...)
```

Example virtual call:

```text
call.dynamic receiver, method_id
  if monomorphic:
      guard receiver.type_id == expected
      direct call compiled_method
  if polymorphic but small:
      inline cache over receiver.type_id/layout_id
  otherwise:
      call xvm_call(...)
```

The compiler can only emit these fast paths if the runtime publishes:

- stable type/layout ids
- invalidation/version rules
- field offsets and representation kinds
- barrier requirements
- safepoint and root-map requirements
- deoptimization entry metadata

This is why object performance is a runtime design issue, not an LLVM codegen issue alone.

## Migration Guidance

The project should avoid creating a third incompatible object world by accident. The migration should be explicit:

1. `xvm_ref` is opaque and backed by Java/Kotlin handles.
2. Selected immutable values gain native layouts behind the same `xvm_ref` API.
3. Arrays and strings gain direct native layouts and helper fast paths.
4. Ordinary objects gain native layouts.
5. Java `ObjectHandle` and Java-JIT bridge objects become compatibility adapters.

The neutral method IR should name object semantics, not host layouts:

- `object.identity_eq`
- `type.is_a`
- `field.load`
- `field.store`
- `array.load`
- `array.store`
- `ref.load`
- `var.store`
- `object.freeze`
- `object.alloc`
- `call.dynamic`

Each operation can lower to direct LLVM, helper call, or bailout depending on the runtime representation available for the specific method specialization.

## Feasibility Impact

Object manipulation is not a reason to reject LLVM, but it does change the scope:

- LLVM JIT without an object ABI is limited to primitive leaf acceleration.
- LLVM JIT with opaque references is feasible and useful, but object-heavy code pays helper-call overhead.
- LLVM JIT with direct object field access requires a runtime-port project, not just a compiler backend.
- A native runtime can make LLVM much more valuable because the compiler and runtime can share object layout, GC maps, and barriers.

The recommended project order remains: opaque handles first, helper-backed object semantics second, selected native layouts third, full native heap last.

The expected performance order is the reverse: full speed appears only as the runtime moves from opaque handles toward native layouts. The prototype is supposed to prove lowering and fallback. The product-grade runtime must make object operations inlineable.
