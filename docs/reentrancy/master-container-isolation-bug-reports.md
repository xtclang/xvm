# Master Bug Reports: XVM Container Isolation Violations

This document is a bug-report inventory against `master`. It is intentionally
framed as a set of concrete defects, not as a general style critique. Each
section names a bad source pattern, explains the practical failure, explains
why the pattern is broken even in a single-threaded run, and states the
replacement rule that later PRs should implement or preserve.

The point is not that master lacks a container model. It has one. The bug is
that several Java implementation shortcuts bypass that model and let one XVM
owner become visible through JVM process-global state, ambient thread-local
state, shallow owner transfer, or shared decoded runtime metadata.

## Isolation Contract

XTC and XVM need same-JVM isolation between independent runtime executions. A
single Java process can load the native system modules, create application
containers, run services and fibers, and cache runtime metadata. The isolation
boundary is therefore not the JVM process. The isolation boundary is the XVM
owner graph: `Runtime`, `Container`, `ConstantPool`, `ConstHeap`,
`ClassTemplate`, `ClassComposition`, `ServiceContext`, `Frame`, and, for the
JIT, `Xvm`, `TypeSystem`, `ModuleLoader`, generated classes, and `Ctx`.

Master already encodes much of this model:

- `Container` owns a `ConstHeap`, parent container, module id, service context,
  services, class compositions, and template caches.
- `Container.getConstantPool()` derives the pool from the container's module.
- `Container.getTemplate(TypeConstant)` registers foreign constants into the
  container pool and only delegates to a parent when the type is shared.
- `ConstHeap` is explicitly constructed for one container and contains comments
  about avoiding singleton-handle leaks into a parent pool.
- `ClassTemplate` stores a final `f_container`; `ClassComposition` similarly
  records the container that owns the runtime composition.

Those are not incidental implementation details. They are the design: runtime
objects have owners, and owner-scoped values must be reached through the owner.

The incompatible pattern is any API that makes an owner-scoped value look like
a JVM singleton, a thread-global current owner, a globally cached handle, or a
decoded-code field shared by every container executing the same op.

## Why This Is Broken Even Single-Threaded

This is not only a parallel-startup race. It is also brittle in one thread
because hidden owner state makes call order part of correctness.

A single-threaded run can still:

- start container A, then container B, and leave process-global state pointing
  at whichever owner initialized last;
- enter a helper while the ambient `ConstantPool` points at the wrong pool or
  no pool;
- adopt a constant into a destination pool after the source constant has filled
  runtime/helper caches;
- execute the same decoded method/op under a different container and reuse the
  first execution's handles;
- trigger generated JIT class initialization under a `Ctx` that is convenient
  for the call stack but not the owner of the generated class.

The security and isolation consequence is the same in each case: code can read,
reuse, or pin state from a different container. Single-threaded determinism only
makes the bug more repeatable; it does not make the owner boundary valid.

## Master Bug Reports

These examples are source shapes present on master or documented by the current
reentrancy audits. Each is a real bug report because it either has a known
failure in this branch's tests/stress runs or violates an owner boundary that
the runtime already relies on for correctness.

### Bug: Mutable Native Template `INSTANCE` Fields

Master has native templates that publish container-owned templates through
mutable statics. For example, `xService` declares:

```java
public static xService INSTANCE;
public static ClassConstant INCEPTION_CLASS;
```

and its constructor assigns both when `fInstance` is true:

```java
INSTANCE = this;
INCEPTION_CLASS = new NativeRebaseConstant(
    (ClassConstant) structure.getIdentityConstant());
```

Broken master behavior: a `ClassTemplate` carries `f_container`, but a mutable
static `INSTANCE` pretends there is one template for the entire JVM.
`Container.ensureServiceContext()` then uses `xService.INSTANCE` to create
service handles; after another container has initialized the static, the lookup
can address the wrong template owner.

The same category includes enum/native handle globals such as `xBoolean.TRUE`
and `xBoolean.FALSE`. They are `BooleanHandle` values derived from enum
initialization. A `BooleanHandle` contains a `TypeComposition`; a composition is
container-owned runtime state. Caching it in a static field makes a runtime
handle look like a JVM constant.

Single-threaded failure mode: container A can initialize first, container B can
initialize second, and later container A can observe B's template or handle
because the static was reassigned. No data race is required; startup order
becomes a hidden part of semantics.

Reviewer rule: a native template or handle may be globally reachable only if it
is genuinely immutable process state and has no container, pool, composition,
service, frame, injector, or runtime handle owner. Otherwise it must live under
the owning `Container` or another explicit owner table.

### Bug: Static Runtime Metadata Caches

Master has static caches in shared runtime helpers. `Utils` declares static
fields for `ClassTemplate`, `MethodStructure`, `TypeConstant`, and
`SignatureConstant` values such as `ANNOTATION_TEMPLATE`,
`ANNOTATION_CONSTRUCT`, `ARGUMENT_ARRAY_TYPE`, and `SIG_INJECT`.

Broken master behavior: those values are not equivalent to Java constants. A
`MethodStructure` comes from a module/pool owner. A `TypeConstant` belongs to a
`ConstantPool`. A `ClassTemplate` carries a `Container`. If a static cache is
populated while container A is active, container B can later reuse A's metadata.

This is brittle even if the cached object is mostly read-only. The cache key is
missing the owner. If the value is safe only for the first owner that populated
the field, it is not process-global state.

Reviewer rule: unkeyed static caches may hold only immutable, owner-free data.
Owner-derived metadata belongs in final owner-local lazy fields, owner-local
records, or maps keyed by the owner dimension that affects correctness.

### Bug: Owner-Bearing Runtime Handles Cached Globally

The most dangerous static caches are not just templates or metadata; they are
live runtime handles. `xBoolean.TRUE` and `xBoolean.FALSE` are simple examples.
Other native enum or singleton caches follow the same pattern when construction
stores enum handles, type handles, resource handles, or object handles in static
fields.

Broken master behavior: runtime handles are capabilities into an owner graph.
They can point at a container's composition, service context, native resource,
constant heap, or injector result. If such a handle is cached globally, later
code can receive a capability minted by another container.

Security consequence: a container boundary cannot be used to reason about
resource or service access if runtime values can arrive through static fields.
Even when the current program is trusted, this undermines future same-process
multi-tenant, tool-server, language-server, test-runner, and embedding
scenarios.

Reviewer rule: handles are never process-global unless the owner is explicitly
process-global by design and documented. Prefer `Frame`, `Container`,
`ServiceContext`, or owner-template factories so the handle is produced or
looked up from the current owner.

### Bug: Ambient `ConstantPool.getCurrentPool()`

Master exposes a thread-local current pool:

```java
public static ConstantPool getCurrentPool() {
    return s_tloPool.get()[0];
}
```

and APIs call it from helper code, including constant operations and method
metadata. Examples include range folding in `ByteConstant` and `IntConstant`,
identity resolution in `IdentityConstant`, and `MethodBody.pool()`:

```java
private ConstantPool pool() {
    return ConstantPool.getCurrentPool();
}
```

Broken master behavior: owner selection is invisible in method signatures. A
call that appears to compare types, fold constants, or inspect method metadata
actually depends on whatever pool the current thread happens to have scoped.

This is insecure and brittle because thread identity is not the same as
container identity. A reused worker, callback, nested compile/runtime helper,
diagnostic path, or generated-code bridge can run with stale, missing, or
wrong ambient state. The bug can occur in one thread if a helper temporarily
changes the current pool and a nested call observes that temporary owner.

Reviewer rule: if a method creates, registers, resolves, reports, or caches
pool-owned data, the owner must be visible in its receiver or parameters.
Ambient owner context is acceptable only as a narrow boundary bridge with
assertions against the explicit `Frame`, `Container`, or `ServiceContext` owner.

### Bug: `Constant.adoptedBy(...)` Shallow Clone

Master's base adoption path uses shallow clone:

```java
protected Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) super.clone();
    that.setContaining(pool);
    that.resetRefs();
    return that;
}
```

The intended semantic is valid: the same logical constant value often needs to
exist in another `ConstantPool`, and the Java object cannot be owned by two
pools. Broken master behavior: shallow clone is the default implementation for
that owner transfer.

`Object.clone()` copies references. It copies references held in `final` fields
too. `transient` does not help because it affects serialization, not cloning.
Therefore any constant subclass that has a lock, lazy cell, thread-local helper,
runtime handle, JIT name, type-info cache, recursion marker, or in-progress
state can carry source-owner state into the destination pool.

The existing reentrancy docs list concrete hazards:

- `SingletonConstant` lifecycle state can be copied so two pools share one
  singleton initialization cell.
- `FSNodeConstant` and `FileStoreConstant` runtime handles can be copied across
  pools.
- `TypeConstant` helper state such as recursion markers, relation caches, JIT
  names, and runtime type handles can be copied as if it were logical value.
- `SignatureConstant` and `ParameterizedTypeConstant` can shallow-copy helper
  locks and JIT/helper caches.
- `HandleConstant` wraps a live `ObjectHandle`, so adopting an already-owned
  handle constant can move a runtime value across owners.

Reviewer rule: adoption must preserve serialized/logical constant identity, not
runtime/helper state. The safe default is explicit owner-aware construction or
per-subclass adoption review; shallow clone must not be treated as safe merely
because the bug has not reproduced yet.

### Bug: `MethodBody` Equality And Hash Recursion

`MethodBody` on master hashes only `m_id` but equality compares implementation,
id, signature, and `m_target`:

```java
return this.m_impl == that.m_impl
    && Handy.equals(this.m_id, that.m_id)
    && Handy.equals(this.m_sig, that.m_sig)
    && Handy.equals(this.m_target, that.m_target);
```

Broken master behavior: for `FromInto`, `Implicit`, and `Union` bodies,
`m_target` can point into `MethodInfo` graphs. Those graphs can contain owner
links and cycles. Equality then becomes a graph walk through metadata ownership
rather than comparison of a stable method-body key.

This is not a data race. It is a brittle ownership bug because using metadata
objects as equality keys can re-enter the same owner graph and recurse until
`StackOverflowError`, or compare the wrong shape when independently realized
owner graphs should be treated by stable identity. Parallel connector loading
made this easier to find, but a single-threaded lookup that creates the same
cyclic metadata graph can still fail.

Reviewer rule: equality and hash for owner-bearing metadata must use stable
logical keys. Do not compare through mutable owner graphs, caches, or back
references unless recursion and owner equivalence are explicit parts of the
contract.

### Bug: Decoded `Op` Runtime Caches

Master's decoded `JumpVal` stores execution-derived switch state on the op:

```java
protected transient ObjectHandle[] m_ahCase;
private transient Map<ObjectHandle, Integer> m_mapJump;
private transient Algorithm m_algorithm;
private transient TypeConstant m_typeCond;
private transient List<Object[]> m_listRanges;
```

Broken master behavior: `buildJumpMap(...)` resolves case constants through the
current `Frame`, relocates handles through the executing container heap,
computes a condition type from the frame, and writes those values back to the
shared decoded op.

The synchronization around map construction makes first construction atomic,
but it does not make the cache owner-correct. The first container to execute
the op installs handles and type data from its owner. A later container that
executes the same decoded method can reuse those values.

This is brittle even with one thread because the decoded op outlives the frame
that supplied the cached values. The fact that container A ran first becomes an
implicit part of container B's execution.

Reviewer rule: decoded code can cache decode/link metadata that is independent
of runtime owners. It must not store `Frame`, `Container`, `ObjectHandle`,
`TypeComposition`, owner-pool `TypeConstant`, resource, or injector-derived
state unless the cache is owned or keyed by the runtime owner.

### Bug: JIT Generated Static Owner Context

The JIT path has a different owner model, but the same rule applies. `Xvm`
owns a native type system, native container, module loaders, type systems, and
JIT containers. `Ctx` carries the active JIT `Xvm` and container, and
`Ctx.Current` is a `ScopedValue<Ctx>`.

Broken master behavior: `Xvm` passes `this` into `NativeTypeSystem.create(this,
repo)` before all final owner fields have been assigned. Generated bytecode in
`CommonBuilder.assembleCLInit(...)` obtains the current `Ctx`, resolves
constants through `ctx.getConstant(className, index)`, stores synthetic
`$scN` static fields, can cache injected constant properties through
`ctx.inject(...)`, and creates generated singleton `$INSTANCE` fields in
`<clinit>`.

Those static fields are safe only if their owner is truly the generated
classloader/type-system, and if class initialization is guaranteed to run under
the matching `Ctx`. They are not safe for container-specific injection results,
services, mutable singleton state, resources, or anything whose semantics
depend on the active JIT container.

Reviewer rule: JIT static fields may cache immutable classloader/type-system
metadata only after owner checks prove the active `Ctx` matches the generated
class owner. Container-owned JIT state must be reached through `Ctx.container`,
`Ctx.inject(...)`, or a container-owned table, not through generated class
statics.

## Consequences For Container Isolation

The global/static/ambient-owner/cache patterns break isolation in four ways.

1. Wrong-owner reads: container B reads a template, handle, constant, method
   graph, or generated static initialized by container A.
2. Wrong-owner writes: code mutates a cache, helper cell, lifecycle state, or
   inflated reference that another owner later observes.
3. Owner pinning: a global or copied helper keeps a container, service, handle,
   or pool reachable after the owner should be collectable.
4. Hidden authority: resource, service, injector, singleton, or native callback
   access can arrive through cached runtime state instead of the current
   container's policy path.

These are security issues because an embedding cannot prove that two module
runs, tests, tenants, language-server sessions, or tool invocations are
isolated if runtime capabilities can cross through Java process state. They
are also reliability issues because startup order, callback nesting, and first
execution of a decoded op become observable semantics.

## Reviewer Checklist

When reviewing a reentrancy or owner-safety change, ask:

- What is the owner of this value: process, `Runtime`, `Container`,
  `ConstantPool`, `ClassTemplate`, `ClassComposition`, `ServiceContext`,
  `Frame`, JIT `Xvm`, `TypeSystem`, `ModuleLoader`, generated classloader, or
  `Ctx`?
- Is that owner explicit in the receiver, parameters, cache key, or field
  location?
- Does the value contain or derive from a runtime handle, type composition,
  template, service, injector result, frame, current pool, or live resource?
- If a static field is used, can the value be proven immutable and owner-free?
- If a lazy cache is used, is first publication safe, and does the key include
  every owner dimension that affects the answer?
- If clone/adoption/copy is used, are helper cells, locks, thread-locals,
  in-progress markers, JIT names, and live handles cleared or reconstructed?
- If ambient context is used, is it only a boundary adapter, and does it assert
  against an explicit owner already available at that boundary?
- If generated JIT statics are used, are they classloader/type-system-owned
  metadata rather than container-owned resources?

## Principles For Remediation

1. Put state under its real owner.

   Templates, compositions, type handles, enum handles, services, resource
   handles, and runtime metadata should be reached through `Container`,
   `Frame`, `ServiceContext`, or an owner-local table. JIT runtime state should
   be reached through `Xvm`, `TypeSystem`, `ModuleLoader`, `Ctx`, or a
   container-owned JIT table.

2. Make owner dependencies visible.

   Prefer explicit `ConstantPool`, `Container`, `Frame`, `ServiceContext`, or
   `Ctx` parameters over `getCurrentPool()`, static `INSTANCE`, or hidden
   singleton lookup. Receiver-owned methods should derive from the receiver's
   owner.

3. Keep process statics immutable and owner-free.

   `static final` is acceptable for descriptors, primitive constants, immutable
   lookup tables, and generated names that do not carry runtime ownership.
   Static mutable caches of owner-derived values should be removed, owner-keyed,
   or moved under the owner.

4. Construct first, publish second.

   Constructors should not assign global fields, register `this`, or install
   owner-visible state before invariants and final fields are complete.
   Factories can construct, validate, and then publish into the owner table.

5. Treat shallow clone as hostile at owner boundaries.

   Owner transfer must be explicit. Copy logical value state; reconstruct or
   clear runtime/helper state. Do not rely on `transient`, `final`, or
   `resetRefs()` to make shallow clone safe.

6. Separate decode/link caches from runtime execution caches.

   Decoded `Op` objects can hold immutable bytecode/link information. Values
   resolved through a `Frame` or `Container` belong in an owner-local runtime
   cache, or must be recomputed from the current owner.

7. Add diagnostics where migration must be staged.

   During migration, add source-shape checks and runtime assertions for static
   owner-bearing fields, ambient pool mismatches, late constant-pool
   registration, adoption of live handles, and generated JIT class
   initialization under the wrong `Ctx`.

## References In This Folder

- [bad-design-decisions-reference.md](bad-design-decisions-reference.md)
- [runtime-ownership-hardening-ledger.md](runtime-ownership-hardening-ledger.md)
- [constant-pool-state-audit.md](constant-pool-state-audit.md)
- [constant-adoption-clone-audit.md](constant-adoption-clone-audit.md)
- [runtime-metadata-op-cache-classification.md](runtime-metadata-op-cache-classification.md)
- [ambient-context-audit.md](ambient-context-audit.md)
- [jit-implications.md](jit-implications.md)
- [jit-global-owner-classification.md](jit-global-owner-classification.md)
