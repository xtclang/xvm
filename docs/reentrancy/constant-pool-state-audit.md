# ConstantPool State Audit

This document classifies `ConstantPool` state for the runtime reentrancy work.
It distinguishes must-fix runtime/container hazards from should-fix design debt
and compiler/incremental-compiler backlog.

The guiding rule is:

> A `ConstantPool` may cache values that it owns. It must not hide which pool is
> the owner, publish values owned by another pool/container, or let one pool's
> runtime/adoption state become another pool's state.

## Summary

`ConstantPool` is not the same problem as the old mutable native-template
`INSTANCE` fields. Most `ConstantPool` caches are instance fields, so two
containers with different pools do not automatically overwrite each other.

The real risk categories are narrower:

- ambient current-pool lookup through `ConstantPool.s_tloPool`;
- adoption of constants from one pool into another;
- runtime code that mutates a shared pool while multiple containers/fibers run;
- unsynchronized per-pool lazy caches that assume benign duplicate computation;
- live runtime values placed in constants, especially `HandleConstant`;
- diagnostics and JIT caches that are not serialized logical constant value.

## Severity Table

| Priority | Category | Sites | Why it matters | Proper fix or guard |
| --- | --- | --- | --- | --- |
| Must fix | Wrong-owner runtime/helper state copied by adoption | Fixed in this branch for `SingletonConstant`, `FSNodeConstant`, `FileStoreConstant`, `TypeConstant`, `ParameterizedTypeConstant`, `SignatureConstant`, `TypeParameterConstant`, and `HandleConstant` | `adoptedBy(...)` changes pool ownership. Shallow-copied runtime/cache/helper state can still point at the source pool/container. | Reset all non-logical state at adoption/owner-change boundaries. Throw/assert when an already-owned live handle constant is adopted into another pool. |
| Must fix when runtime path can reach it | Ambient current pool in runtime execution | `MainContainer.invoke0`, `Container.ensureServiceContext`, `ServiceContext`, watcher/request callbacks, `xContainerControl.invokeInvoke`, type-relation helpers | A thread-local owner is hidden from signatures. Missing scope cleanup or execution on a different thread selects the wrong pool or `null`. | Prefer explicit `Frame`, `Container`, or `ConstantPool` parameters. Use a scoped owner context only as a transitional bridge. |
| Must audit | Shared mutable pool mutation during parallel runtime | `register(...)`, `ensure*Constant(...)`, `f_listConst`, `m_mapConstants`, `m_mapLocators`, `getContained()` | Some structures are concurrent/copy-on-write; `f_listConst` itself is not a general concurrent collection. The current design assumes registration and validation reentrancy more than arbitrary parallel mutation. | Add stress tests that run two containers against the same module/pool and enable ownership diagnostics. Long term, split "frozen runtime pool" from compiler/linker mutation. |
| Must audit | Live runtime handles embedded as constants | `HandleConstant` in `xRTTypeTemplate.resolveFormalType`, used as annotation parameter values | A live `ObjectHandle` is owner-specific and cannot become a pool-shared serialized logical constant. | This branch adds a `HandleConstant.adoptedBy(...)` guard for cross-pool movement. Diagnostics for annotated types carrying live handles remain useful. |
| Should fix soon | Unsynchronized per-pool lazy implicit caches | `f_implicits`, `m_clz*`, `m_type*`, `m_val*`, `m_sig*`, `m_setJitPrimitives` | These are owner-local, so they are not cross-container globals. They are still plain lazy writes and can duplicate work or race under concurrent use of one pool. | Use owner-local `Lazy` or `ConcurrentMap.computeIfAbsent` for hot/shared runtime caches, or freeze/warm them before parallel runtime execution. |
| Should fix soon | Ambient `ThreadLocal` implementation | `s_tloPool`, `getCurrentPool`, `setCurrentPool`, `withPool` | Raw `ThreadLocal` with mutable holder arrays relies on perfect manual cleanup and does not make ownership visible. | Replace with explicit parameters. Where plumbing is too broad, migrate to a small `ScopedValue<RuntimeOwner>` bridge that points to the real owner. |
| Should fix | Static immutable implicit metadata maps | `s_implicits`, `s_implicitsByPath` | Class-init publication is safe, but the maps are mutable `HashMap`s held in final static fields. No code mutates them today. | Wrap with `Map.copyOf(...)` after construction. This is cleanup, not a runtime PR blocker. |
| Compiler backlog | Destructive pool optimization and module replacement | `optimize()`, `replaceModule(...)`, disassembly/assembly paths | These mutate pool contents, positions, and caches. They are intended for serialization/compiler flows, not concurrent runtime execution. | Keep out of this PR. Incremental compiler work should isolate mutable compiler pools from frozen runtime pools. |

## Field Inventory

Current `ConstantPool` fields fall into these groups.

### Static Process State

| Field | Classification | Notes |
| --- | --- | --- |
| `s_implicits` | Should fix | Parsed once from `implicit.x`. Class initialization safely publishes the reference, but the `HashMap` is mutable. Use `Map.copyOf(...)` as cleanup. |
| `s_implicitsByPath` | Should fix | Same as `s_implicits`. |
| `s_tloPool` | Must audit, runtime-relevant | Ambient owner lookup. The stored value is per thread, but the owner dependency is hidden. Replace with explicit owner parameters where possible; otherwise migrate to `ScopedValue` as a bridge. |

### Core Pool Storage

| Field | Classification | Notes |
| --- | --- | --- |
| `f_listConst` | Must audit | Owner-local list of constants by index. Registration mutates it; `getContained()` intentionally returns a live index iterator so validation can append while iterating. This is a reentrant single-thread workaround, not a general parallel mutation guarantee. |
| `m_mapConstants` | Mostly OK, must audit with `f_listConst` | Volatile copy-on-write `EnumMap` of per-format `ConcurrentHashMap`s. The lookup maps are concurrent, but they sit over mutable `f_listConst`. |
| `m_mapLocators` | Mostly OK, must audit with `f_listConst` | Same shape as `m_mapConstants`. |
| `f_setValidPools` | Should fix soon | Identity set of upstream pool owners. Built lazily and read during registration assertions. It is not a runtime cache, but mutable lazy build should be synchronized or eagerly built before parallel registration. |
| `m_fRecurseReg` | Compiler/assembly state | Registration mode used while preparing/assembling constants. Not a runtime execution cache. |

### Type-Info And Runtime Metadata

| Field | Classification | Notes |
| --- | --- | --- |
| `f_tlolistDeferred` | Must audit | Per-pool `TransientThreadLocal` list used during type-info recursion. It should not cross pool owners. It is safer than a global, but still hidden context and should stay tightly scoped. |
| `f_listInvalidated` / `m_cInvalidated` | Mostly OK | `Vector` plus volatile count. This is an explicit per-pool invalidation log. It is not cross-container global state. Long term, prefer a clearer synchronized/immutable snapshot API. |
| `f_mapRefTypes` | Mostly OK | Per-pool `ConcurrentHashMap` for NakedRef `TypeInfo`. This is the right kind of keyed owner-local cache. |
| `m_typeNakedRef` | Must audit with runtime initialization | Set by native/runtime/JIT initialization. It is per-pool, but wrong ambient pool setup can leave it unset or set on the wrong pool. Explicit pool parameters are preferred. |

### Implicit Type/Class/Value Caches

These fields are the long `m_clz*`, `m_type*`, `m_val*`, `m_sig*`, and
`m_setJitPrimitives` cache block.

Classification: should fix soon, not a first-order cross-container global.

They are per-pool instance fields and therefore preserve the old caching
semantics for one pool. The problem is the manual lazy pattern:

```java
TypeConstant c = m_typeObject;
if (c == null) {
    m_typeObject = c = ensureTerminalTypeConstant(clzObject());
}
return c;
```

If a single pool is touched by multiple runtime threads, this can duplicate
work. For interned constants, duplicate computation should converge to the same
registered value, so the likely failure mode is wasted work rather than a
wrong-owner object. However, the fields include singleton constants and
signature constants, and these now have owner-local runtime state. They should
not be written off forever.

Recommended future shapes:

- Eagerly pre-warm these fundamental constants before a pool is published to
  parallel runtime execution.
- Replace the one-line manual lazy fields with generated or table-driven
  `Lazy`/`Lazy.Owner` cells owned by the `ConstantPool`.
- For large related groups, use a final immutable metadata record built once:

  ```java
  private final Lazy.Owner<ConstantPool, CoreTypes> coreTypes =
          Lazy.ofOwner(ConstantPool::buildCoreTypes);
  ```

This is a should-fix because it improves reentrant reasoning and removes
hundreds of manual lazy writes. It is not the same must-fix category as static
template `INSTANCE` because the owner is already the pool.

## Ambient Current-Pool Sites

Audit command:

```bash
rg -n "ConstantPool\\.getCurrentPool|ConstantPool\\.setCurrentPool|ConstantPool\\.withPool|getCurrentPool\\(\\)|setCurrentPool\\(|withPool\\(" \
  javatools/src/main/java
```

Current sites:

| Site | Category | Recommended direction |
| --- | --- | --- |
| `org/xvm/tool/Compiler.java:315` | Compiler/tool | Compiler rewrite backlog. Explicit pool parameter is better; not this runtime PR. |
| `org/xvm/tool/Runner.java:227` | Runtime launch | Acceptable scoped owner setup at launch boundary. |
| `org/xvm/api/InterpreterConnector.java:87` | Runtime API | Prefer explicit `ConstantPool`/`Container` from connector state instead of ambient lookup. |
| `org/xvm/compiler/Compiler.java:155,192,236,280` | Compiler | Compiler rewrite backlog. |
| `org/xvm/runtime/MainContainer.java:193` | Runtime | Acceptable boundary scope today, but `findModuleMethod` and nested helpers should eventually receive owner explicitly. |
| `org/xvm/runtime/Container.java:117` | Runtime | `ensureServiceContext()` already has `this`; helper paths should use `getConstantPool()` explicitly where possible. |
| `org/xvm/runtime/NativeContainer.java:104` | Runtime startup | Acceptable startup scope, but prefer scoped `try` form consistently. |
| `org/xvm/runtime/NativeContainer.java:152,222` | Runtime startup | `setCurrentPool`/manual clear is riskier than `withPool`. Replace with `try (var _ = ConstantPool.withPool(pool))` or future `ScopedValue`. |
| `org/xvm/runtime/ServiceContext.java:309` | Runtime | Service has `f_pool`; keep explicit where possible and scope only legacy callees. |
| `org/xvm/runtime/template/_native/fs/xOSStorage.java:337` | Runtime async watch thread | Runtime-relevant. The watch thread extracts the container from the storage handle and scopes that pool. Good guard would assert the storage handle owner matches the scoped container. |
| `org/xvm/runtime/template/_native/web/xRTServer.java:653` | Runtime request thread | Runtime-relevant. Request handling has a service context and should use its explicit pool. A scoped bridge is acceptable for legacy callees. |
| `org/xvm/runtime/template/_native/mgmt/xContainerControl.java:111` | Runtime management invoke | Runtime-relevant. It already has a container/pool; helper APIs should take explicit owners. |
| `org/xvm/asm/FileStructure.java:181,1004` | Assembly/linker | Not runtime-container execution, but important for incremental compiler. |
| `org/xvm/asm/MethodStructure.java:666` | Assembly/linker | Compiler/linker backlog. |
| `org/xvm/asm/constants/TypeConstant.java:1901,2075,6272,6352` | Type logic | Runtime-relevant type analysis. Prefer adding explicit pool parameters to the covariance/contravariance helpers that currently call `getCurrentPool()`. |
| `org/xvm/asm/constants/MethodInfo.java:1476` | Type/method metadata | Prefer passing pool from owning method/type info instead of ambient lookup. |
| `org/xvm/asm/constants/MethodBody.java:693` | Type/method metadata | Prefer passing pool from owning method/body instead of ambient lookup. |
| `org/xvm/asm/constants/IdentityConstant.java:506` | Identity/type logic | Prefer caller-supplied pool for auto-narrowing/resolution paths. |
| `org/xvm/asm/constants/PropertyInfo.java:683` | Type/property metadata | Prefer owner pool from property/type info. |
| `org/xvm/asm/constants/IntConstant.java:725,739,753,767` | Constant operations | Prefer an explicit output pool parameter for range-producing operations. |
| `org/xvm/asm/constants/ByteConstant.java:295,297,370,372,374,376` | Constant operations | Same as `IntConstant`. |
| `org/xvm/javajit/NativeTypeSystem.java:108` | JIT | Documented in `jit-implications.md`; not this runtime PR. |
| `org/xvm/javajit/JitConnector.java:64` | JIT | Documented in `jit-implications.md`; not this runtime PR. |

The runtime must-fix direction is not "delete all ambient lookup in this PR".
The direction is:

1. When a method already has `Frame`, `Container`, `ServiceContext`, or
   `ConstantPool`, use that explicit owner instead of `ConstantPool.getCurrentPool()`.
2. When an API manufactures a type/constant/handle, add an owner parameter if
   it does not already have one.
3. Use ambient scope only at narrow boundary adapters where plumbing every
   legacy call would make the PR too broad.
4. Add assertions in those adapters that the scoped pool equals the pool of the
   frame/container/service being used.

## Recommended Guards

These guards are diagnostic-first and can be promoted to hard assertions after
the stress suite has shaken out remaining owner violations.

### Guard Live Handle Constants

An already-owned `HandleConstant` should not be adopted into another pool:

```java
@Override
protected Constant adoptedBy(ConstantPool pool) {
    if (getContaining() == null) {
        return super.adoptedBy(pool);
    }
    throw new IllegalStateException(
            "HandleConstant wraps a live ObjectHandle and cannot be adopted into " + pool);
}
```

The unowned case preserves the current runtime annotation path that creates a
fresh frame-local handle constant and registers it in the current pool. The
guard rejects the dangerous second adoption to another pool. This does not ban
`RegisterConstant` or `MethodBindingConstant`; they are real frame-dependent
constant forms. The guard belongs specifically on `HandleConstant`.

### Guard Runtime Current-Pool Scopes

At runtime thread/adaptor boundaries, assert the scope matches the explicit
owner:

```java
ConstantPool pool = container.getConstantPool();
try (var _ = ConstantPool.withPool(pool)) {
    assert ConstantPool.getCurrentPool() == pool;
    ...
}
```

For async callbacks, also assert the handle owner:

```java
Container owner = context.hStorage.getComposition().getContainer();
assert owner == context.hStorage.f_context.f_container;
```

The exact accessor should use existing public APIs where available, but the
invariant is simple: the Java callback must bind the pool owned by the handle's
container, not a cached/global pool.

### Guard Adoption Copies

`ConstantAdoptionValidator` now provides an opt-in adoption guard at
`ConstantPool.register(...)` with `-Dxvm.asm.validateConstantAdoption=true`.
It compares source and adopted copies and reports identical references for
forbidden categories:

- `ObjectHandle`
- `Container`, `Frame`, `Fiber`, `ServiceContext`
- `TypeComposition`, `ClassComposition`
- `xRTType.TypeHandle`
- `AtomicReference`, `AtomicInteger`, `StampedLock`
- `ThreadLocal`, `TransientThreadLocal`
- mutable maps/lists/sets unless allowlisted as logical immutable data

The first wave keeps this diagnostic opt-in to avoid surprising normal
runtime/compiler cost. After stress runs identify the intentional allowlist,
specific findings should be promoted to hard assertions.

This can start inside `OwnershipDiagnostics` and be called only under a system
property, so normal runtime does not pay reflection cost.

### Guard Runtime Pool Mutation

If the runtime should treat a pool as frozen after container startup, introduce
a debug state bit:

```java
pool.markRuntimePublished();
```

Then in `register(...)`, emit diagnostics when a published pool is mutated from
more than one runtime container/fiber path. This is not a proposed immediate
behavior change, because runtime paths still create owner-local constants
today. It is a way to discover which `ensure*` calls remain hot after startup.

## ScopedValue Replacement Shape

The future shape for ambient owner lookup should mirror
[scoped-value.md](scoped-value.md):

```java
record RuntimeOwner(Container container, ConstantPool pool) {
    RuntimeOwner {
        Objects.requireNonNull(container);
        Objects.requireNonNull(pool);
    }
}
```

Boundary code binds it:

```java
RuntimeOwner owner = new RuntimeOwner(container, container.getConstantPool());
ScopedValue.where(RuntimeOwners.CURRENT, owner).run(() -> invoke(...));
```

Legacy helpers can read it only as a bridge:

```java
static ConstantPool currentPool() {
    return RuntimeOwners.CURRENT.orElseThrow().pool();
}
```

Preferred APIs still take explicit owners:

```java
TypeConstant ensureResultType(ConstantPool pool, TypeConstant left, TypeConstant right) {
    return pool.ensureUnionTypeConstant(left, right);
}
```

`ScopedValue` improves cleanup and makes the dynamic scope lexical. It does not
make hidden owner lookup ideal, and it must not become a storage location for
caches. The actual caches must remain on `Container`, `ConstantPool`,
`NativeTemplates`, templates, or other real owners.

## Stress Strategy

To find existing-world crashes without changing too much code:

- run direct same-JVM sequence stress so stale pool/template state has no
  process restart to hide behind it;
- run parallel manual stress on modules that exercise reflection annotations,
  enum initialization, file-system callbacks, and service/container management;
- enable ownership diagnostics at runtime boundaries such as
  `mgmt.Container.invoke`, async watch callbacks, and HTTP request callbacks;
- add a debug adoption validator for copied forbidden fields;
- add counters/logging around `ConstantPool.register(...)` after container
  startup to see which runtime paths still mutate the pool.

Good stress targets:

- `TestProps`, because it already exposed adopted `SingletonConstant` runtime
  state leakage;
- service and container manual tests, because they cross fibers and child
  containers;
- reflection-heavy tests using annotations with runtime argument values;
- file-system watcher tests, because they bind pool context on Java callback
  threads;
- same-JVM launcher/direct execution loops, because they expose stale process
  state that one-process-per-run hides.

The goal is to prove two things separately:

1. No owner-bearing value from container A is reachable from container B unless
   it is intentionally shared immutable module/type metadata.
2. Runtime execution does not depend on a stale ambient `ConstantPool` left on
   a reused Java thread.
