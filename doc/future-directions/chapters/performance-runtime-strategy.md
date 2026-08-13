# Performance and Fiber Runtime Strategy

Investigation date: 2026-08-13

This note answers the performance objection raised by the LLVM object ABI work: if Ecstasy methods are JIT-compiled to LLVM, how can XVM ever become small, fiber-friendly, and fast?

Short answer: the opaque-handle LLVM sidecar is a bridge, not the final architecture. It is useful for correctness and incremental migration. The fast runtime requires native object layouts, compact continuation frames, explicit safepoints, unboxed value representations, and a code cache that can combine AOT and lazy JIT.

Related notes:

- Main LLVM study: [llvm-jit-study.md](llvm-jit-study.md)
- LLVM object ABI notes: [llvm-object-abi-notes.md](llvm-object-abi-notes.md)
- LLVM compiler scope plan: [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md)
- Runtime port scope plan: [runtime-port-scope-plan.md](runtime-port-scope-plan.md)

Second-pass review (2026-08-13): several recommendations in this note are sharpened or superseded by [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md) (conservative-first root reporting, MMTk-first collector, three fiber models with mmap-stacks-first sequencing, deopt within one object world) and [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md) (the "small host kernel" goal contradicts in-process LLVM — resolutions there; OSR/deopt/suspend unified as one frame-externalization mechanism). Numeric targets replacing this note's adjectives live in [risk-matrix-and-decision-gates.md](risk-matrix-and-decision-gates.md).

## Target Runtime Shape

The production target should be:

- small host kernel
- typed module tables, not ASTs or mutable compiler state
- method IR compiled to native code
- AOT core libraries plus lazy JIT for application and specialization code
- runtime-owned object layouts
- compact suspended fiber records
- explicit safepoints and root maps
- Ecstasy runtime libraries above a small intrinsic layer

The Java interpreter and Kotlin reference runtime remain useful oracles and development tools, but they should not define the production performance model.

## Why the Opaque ABI Is Not a Dead End

Opaque `xvm_ref` handles are slow if every object operation stays behind a helper. They are still the right first ABI because they isolate correctness problems:

- object identity
- GC rooting
- Java/Kotlin/native interop
- exception and suspend status
- fallback to interpreter/reference runtime
- module unload and code invalidation

The performance plan is to graduate operations:

```text
opaque helper
  -> primitive-specialized helper
  -> layout guard plus direct fast path
  -> devirtualized or inlined operation
```

The important rule is that the neutral method IR names semantics, not host fields. `array.load`, `field.store`, `ref.load`, and `call.dynamic` can lower to helpers at first and to direct native code later without changing language semantics.

## Fast Code Execution

Fast hot code needs these compiler/runtime features:

- **Typed method IR**: normalized CFG, explicit calls, explicit exception edges, safepoints, representation facts, and debug maps.
- **Unboxed value flow**: booleans, integers, floats, nullable primitives, XVM primitives, small tuples, and multi-return values stay in registers or result areas.
- **Specialization**: generic and virtual code specializes by type id, layout id, call target, nullability, and mutability facts.
- **Inline caches**: dynamic calls start with helper dispatch but get monomorphic or small-polymorphic fast paths.
- **Direct layouts**: fields, arrays, strings, refs/vars, and numeric boxes become native layouts with offsets and barrier metadata.
- **Escape analysis and scalar replacement**: short-lived objects and tuples can disappear from the heap.
- **Bounds/check elimination**: loop analysis removes repeated array/string checks when guards prove invariants.
- **AOT/JIT split**: core runtime and stable libraries can be AOT compiled; application code and specializations are compiled lazily.

The common hot path should not call the runtime for every operation. Runtime helpers are for slow paths, uncommon traps, allocation slow paths, service boundaries, and deoptimization.

## Minimum Footprint

Minimum footprint requires controlling several independent costs:

| Area | Strategy |
| --- | --- |
| Base runtime | small native kernel, no compiler or AST dependency in production |
| Module metadata | typed tables, stable ids, lazy reflection/debug/source loading |
| Code cache | AOT core, lazy JIT app code, specialization budget, unload cold code |
| Fibers | compact heap records and continuation frames, no OS stack per suspended fiber |
| Objects | compact headers, compressed refs where viable, native arrays/strings early |
| GC roots | safepoint maps or shadow stacks, not full Java frame objects |
| Interop | explicit intrinsic/FFI boundary, not accidental Java object graphs |

The runtime should report footprint in these categories:

- base kernel
- loaded module tables
- per-fiber state
- per-object overhead
- code cache
- reflection/debug metadata

Without that accounting, "small" will remain subjective.

## Fiber Model

The future runtime should treat an Ecstasy fiber as a logical execution state, not as a permanently assigned host thread or native stack.

Suggested fiber record:

```text
Fiber {
    id
    service_id
    status
    current_method_or_resume_stub
    compact continuation frames
    wait/mailbox state
    timeout/context tokens
    root slots
    debug/profiling id
}
```

Native code can still use the machine stack while it is running. The key restriction is that it cannot suspend at arbitrary instructions. It can suspend only at compiler-emitted safepoints:

- loop backedges
- method calls
- allocation points
- service sends/waits
- explicit poll operations
- selected runtime helper calls

At each safepoint, the compiler supplies a map of live values. If the runtime returns `blocked`, `paused`, `exception`, or `deopt`, the compiled frame spills live values into a compact continuation frame. Resumption re-enters through a resume stub or method-IR continuation point.

This gives the desired tradeoff:

- running code uses native stack and native registers
- suspended code uses compact heap state
- no OS thread is parked per Ecstasy fiber
- scheduler can multiplex many fibers over a small worker pool

## Scheduler and Services

The scheduler should be built around explicit statuses shared by interpreter and compiled code:

- `ok`
- `call`
- `return`
- `blocked`
- `paused`
- `exception`
- `deopt`
- `terminated`

Service calls and futures should not be hidden blocking calls. They should return through the same status channel. A compiled method that reaches a service boundary either:

- uses a non-blocking helper that completes immediately
- materializes a continuation and yields
- deoptimizes to a reference runtime path for unsupported cases

This keeps the scheduler visible to compiled code and avoids host-thread blocking.

## Memory Manager

The memory-management story depends on which object world owns XTC values.

### JVM-Owned Object Mode

This is the current interpreter and Java-JIT bridge direction:

- XTC objects are Java `ObjectHandle` subclasses or Java-JIT bridge objects.
- Java GC owns object lifetime.
- LLVM can only hold opaque handles or JNI/FFM-managed references.
- XTC memory accounting can be approximate because the runtime does not own object size, movement, or reclamation timing.
- Native code cannot safely dereference object fields or assume object addresses are stable.

This mode is useful for conformance, debugging, and a first LLVM sidecar. It cannot be the final high-performance object model. The speed ceiling is helper-call overhead plus JVM object layout, and the footprint ceiling is Java object/header/array overhead.

The current Java JIT already hints at the intended XTC accounting surface: `Ctx.alloc`, `Ctx.allocated`, `Ctx.realloc`, and `Ctx.free` exist but are TODOs. The bridge array classes call `ctx.alloc(...)` for storage. That is the right semantic hook, but with Java-owned objects it cannot become exact or extremely cheap.

### Hybrid Object Mode

A transition runtime may have both Java/Kotlin objects and native XVM objects:

- `xvm_ref` can represent either a host handle-table entry or a native heap pointer/tagged reference.
- Native objects can be fast, but references crossing into Java need wrappers or global handles.
- Cycles across Java and native heaps are dangerous unless one side can trace the other side or all cross-heap references are rooted conservatively.
- Finalization/weak references become complicated because two collectors may disagree about reachability.

Hybrid mode should be kept temporary and deliberately narrow. It is acceptable for migrating arrays, strings, and numeric containers first. It is a poor permanent design because dual-GC boundaries tend to leak, pin, or over-root.

### Native XVM Heap Mode

The production answer is a runtime-owned XVM heap. In that mode:

- `xvm_ref` is a direct native pointer, compressed pointer, or tagged reference into XVM-managed memory.
- the runtime owns object headers, field layouts, array/string storage, allocation, and GC
- compiled LLVM code gets exact safepoint/root maps
- memory accounting charges allocations to a container/service owner at allocation time
- GC can move or compact objects because it can update all roots and object fields
- Java/Kotlin objects are outside the hot path and appear only through explicit FFI/adapters

The native runtime should prioritize allocation speed and simple exact root reporting before advanced concurrent GC algorithms.

Likely first direction:

- per-worker or per-service nursery allocation
- bump-pointer fast path
- explicit allocation slow path helper
- compact object headers
- stable or movable heap decision made before direct native field access is broad
- write barriers on reference stores
- safepoint maps or shadow-stack roots for compiled frames
- immutable/shareable object rules integrated with service boundaries

The GC strategy can evolve, but direct field access cannot be enabled broadly until the barrier and root rules are fixed.

## Native Heap Blueprint

A plausible efficient non-Java heap should have these pieces.

### Reference Representation

Use a compact `xvm_ref` representation:

- null as zero
- immediates for `Null`, booleans, small enums, or small integers only if it does not complicate generic reference handling
- compressed object references for normal heap objects when the heap layout allows it
- full native pointers for large-object, external, or debug builds if needed
- side tables for rarely used metadata rather than bloating every object header

Primitive locals should usually not be `xvm_ref` at all. They should stay as LLVM scalar values and be boxed only at semantic boundaries.

### Object Header

The hot object header should be small:

- layout/type id
- flags for immutability, construction, service/proxy/native state
- size or size-class for variable-sized objects
- owner/container/account id, or a compact indirection to one
- optional hash/identity state in a side table if it is cold

Avoid putting every reflection/debug/generic fact in the object. Those facts belong in layout/type tables.

### Allocation Fast Path

Allocation should be a bump-pointer fast path:

```text
if nursery_top + size <= nursery_limit:
    obj = nursery_top
    nursery_top += size
    initialize header
    return obj
else:
    call allocation_slow_path(ctx, size, layout_id)
```

The slow path handles:

- quota checks
- nursery refill
- large-object allocation
- GC trigger
- service/container termination on hard limit
- returning `blocked` or `exception` status if policy requires it

Memory accounting should happen on nursery refill or large allocation, not on every tiny object if that would be too expensive. The runtime can reserve chunks against a container and settle exact usage during GC or promotion.

### Collection Strategy

A practical first native GC should be exact and generational:

- young generation: copying or semi-space collection for high allocation throughput
- old generation: mark-sweep, mark-compact, or region-based collection
- large objects: separate space to avoid copying huge arrays/strings
- immutable/shared objects: promotable to shared or read-mostly spaces
- weak refs and finalization: explicit queues owned by the scheduler/runtime

Stop-the-world collection is acceptable for an early native runtime if safepoints are correct. Incremental or concurrent collection can be added later, but it requires stronger read/write barrier discipline.

### Root Reporting

The collector must never conservatively scan arbitrary native stacks as the main strategy. Roots should come from:

- fiber records and compact continuation frames
- currently running compiled frames at safepoints
- interpreter/reference-runtime value slots
- module constants and singleton handles
- service mailboxes and pending requests
- native/FFI handle scopes
- code-cache embedded references

Implementation options, in second-pass recommended order (see [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md)):

- **Conservative stack scan + exact heap + pinning collector**: the cheapest correct start (V8 and Ruby precedent); requires only pinnable collection (Immix) and no interior-pointer refs. Recommended first.
- **Shadow stack**: compiled code registers live references in explicit root slots around calls/safepoints. Use only if a non-pinning collector is forced.
- **Stack maps/statepoints**: lowest steady-state overhead, highest backend/runtime complexity, and the LLVM statepoint machinery has a weak maintenance record. Arrives last, and only on paths that need compaction or compact continuation frames.

### Barriers

Direct field stores require barriers:

- generational write barrier for old-to-young references
- immutability/construction checks for writes to frozen or uninitialized objects
- service/shareability barrier when a mutable object would cross a service/container boundary
- optional read barrier only if the selected GC requires it

The LLVM backend should not inline a store until the runtime says which barrier sequence is required for that layout and field representation.

### Service-Local and Shared Heaps

Ecstasy services are a useful memory-management boundary. A good native runtime can exploit that:

- mutable service-local objects allocate in service-owned or worker-owned nurseries
- immutable/pass-through objects can be promoted or copied into shared spaces
- service sends validate shareability and either pass immutable refs, copy values, or reject/route through proxy semantics
- per-container memory budgets are enforced by allocation reservation and GC pressure

This can reduce synchronization and GC scanning compared with one global mutable heap, while preserving explicit language semantics for service boundaries.

## Non-Java Runtime Path

To execute XTC memory management extremely efficiently without Java:

1. Define typed module/model metadata and layout tables independent of Java `ObjectHandle`.
2. Define `xvm_ref`, object headers, arrays, strings, refs/vars, and tuple/result layouts.
3. Implement a native allocator with per-service/per-worker nurseries and container accounting.
4. Implement exact root reporting from fiber frames and compiled safepoints.
5. Add a simple exact collector first, preferably generational once allocation fast paths are stable.
6. Teach LLVM lowering to emit allocation fast paths, barriers, stack maps/shadow roots, and slow-path calls.
7. Move hot runtime containers such as arrays and strings onto the native heap.
8. Keep Java/Kotlin adapters outside the hot path until they can be retired.

The performance target is not "native code with manual memory management." It is exact XVM-managed memory with compiler-visible roots and barriers, so generated code can be fast without becoming unsafe.

## Object Layout Priorities

The first native layouts should be selected by expected payoff:

1. primitive arrays and byte arrays
2. strings
3. numeric boxes and XVM primitives
4. refs/vars
5. small tuples and multi-return carriers
6. simple immutable objects
7. mutable objects with barriers
8. services and proxies

Arrays and strings deserve early attention because they dominate many real workloads and enable LLVM loop optimizations.

## Code Cache Strategy

For low footprint and fast startup:

- AOT compile the minimal runtime kernel and core Ecstasy libraries.
- Lazy JIT application methods after counters or explicit policy selects them.
- Specialize only within a budget.
- Store compiled code by module, method, specialization, and layout-version key.
- Use runtime invalidation when module/type/layout facts change.
- Unload cold code through ORC resource trackers or native code-cache regions.
- Keep debug/unwind metadata separate and load it lazily when possible.

A system that specializes everything eagerly will be fast in microbenchmarks and poor in footprint. The cache policy must be part of the runtime design.

## Milestones

1. **Correct LLVM bridge**: primitive leaf methods, opaque references, helper fallback, unloadable code.
2. **Method IR and safepoints**: explicit non-suspending/suspending classification and live-value maps.
3. **Compact fiber frames**: spill/resume compiled frames without retaining native stacks.
4. **Native arrays/strings**: direct guarded access for hot loops.
5. **Native object header and allocator**: bump allocation, type/layout ids, barriers, roots.
6. **Inline dispatch**: monomorphic and small-polymorphic inline caches.
7. **AOT/JIT policy**: core AOT, lazy app JIT, code unload.
8. **Java retirement**: Java runtime remains compatibility path, not normal execution path.

## Performance Gates

A backend/runtime phase should not be considered product-fast until it can demonstrate:

- primitive loops compile without per-op dispatch or helper calls
- direct native array loops with bounds checks hoisted or eliminated where valid
- direct field access for native-layout objects with guard and barrier correctness
- fiber suspend/resume without native-stack retention
- low per-fiber memory measured under load
- code-cache size bounded by policy
- no mandatory Java object handle in hot native paths
- clean deopt/fallback for unsupported dynamic behavior

The long-term answer is therefore not "LLVM calls helpers forever." It is "LLVM starts with helpers for correctness, then the runtime supplies enough layout, safepoint, GC, and scheduler contracts for hot operations to become direct native code."
