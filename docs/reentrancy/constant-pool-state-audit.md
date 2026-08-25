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

## Completeness Status

This file is the current ConstantPool/state catalog, not a proof that every
parallelism-hostile pool pattern has been eliminated. The branch fixes the
known runtime-owner defects listed below and adds guards for the classes of bugs
that were proven during stress runs. The 2026-08-25 completion pass closed the
runtime-published mutation gap: after runtime publication, new registrations,
recursive registration/optimization, module-id surgery, disassembly reload, and
TypeInfo invalidation all fail before mutating pool storage. The remaining
pool work is structural: compiler/linker/serializer phases still need an
explicit mutable-pool owner or immutable runtime snapshot model before claiming
full pool reentrancy.

The remaining audit must enumerate every owner-sensitive state edge in:

- non-final mutable fields and mutable collections on `ConstantPool`;
- `adoptedBy(...)`, `clone()`, copy constructors, and any method that changes a
  constant's containing pool or copies helper state;
- ambient owner lookup through `ConstantPool.getCurrentPool()`,
  `ConstantPool.withPool(...)`, `ThreadLocal`, `TransientThreadLocal`, or
  `ScopedValue`;
- late runtime registration and destructive mutation after a pool has been
  published to a container;
- live runtime handles or other container-owned objects stored in constants;
- error-listener/current-pool assumptions such as `getErrorListener()` calls
  that only work when the correct owner has already been scoped;
- diagnostics, JIT, and metadata helper caches that are not serialized logical
  constant value.

The proper output of that audit is a must-fix/must-audit/should-fix/benign
classification with a guard or explicit owner parameter recommendation for each
site. The background ConstantPool-specific audit for this branch writes that
expanded catalog to `constant-pool-hostile-state-audit.md`.

## Severity Table

| Priority | Category | Sites | Why it matters | Proper fix or guard |
| --- | --- | --- | --- | --- |
| Must fix | Wrong-owner runtime/helper state copied by adoption | Fixed in this branch for `SingletonConstant`, `FSNodeConstant`, `FileStoreConstant`, `TypeConstant`, `ParameterizedTypeConstant`, `SignatureConstant`, `TypeParameterConstant`, `MethodConstant`, `PropertyConstant`, `FormalTypeChildConstant`, `DynamicFormalConstant`, `RegisterConstant`, `MethodBindingConstant`, and `HandleConstant` | `adoptedBy(...)` changes pool ownership. Shallow-copied runtime/cache/helper state can still point at the source pool/container, compiler method/register owner, frame descriptor owner, or JIT owner. | Reset or reconstruct all non-logical state at adoption/owner-change boundaries. Throw/assert when live handles, moving compiler registers, or non-shared foreign register types are adopted into another pool. |
| Done in this branch for semantic lookup; should fix bridge removal | Ambient current pool in runtime execution | Semantic main-code callers are fixed and `getCurrentPool()` has been removed; boundary scopes remain through `withPool(...)` | A thread-local owner is hidden from signatures. Missing scope cleanup or execution on a different thread selects the wrong pool or `null`. | Keep explicit `Frame`, `Container`, or `ConstantPool` parameters. Remove `withPool(...)` completely once the remaining boundaries have explicit owners. |
| Done in this branch for runtime-published mutation; must audit long-term snapshot model | Shared mutable pool mutation during parallel runtime | `register(...)`, `ensure*Constant(...)`, recursive registration, `optimize()`, `replaceModule(...)`, disassembly reload, TypeInfo invalidation, `f_listConst`, `m_mapConstants`, `m_mapLocators`, `getContained()` | Some structures are concurrent/copy-on-write; `f_listConst` itself is not a general concurrent collection. The current design assumes registration and validation reentrancy more than arbitrary parallel mutation. | This branch always installs the runtime publication marker and rejects new registrations plus destructive compiler/serializer mutations after that point. Long term, split frozen runtime pools from compiler/linker mutation and close immutable storage snapshots. |
| Must audit | Live runtime handles embedded as constants | `HandleConstant` in `xRTTypeTemplate.resolveFormalType`, used as annotation parameter values | A live `ObjectHandle` is owner-specific and cannot become a pool-shared serialized logical constant. | This branch adds a `HandleConstant.copyForAdoption(...)` guard for cross-pool movement. Diagnostics for annotated types carrying live handles remain useful. |
| Should fix soon | Unsynchronized per-pool lazy implicit caches | `f_implicits`, `m_clz*`, `m_type*`, `m_val*`, `m_sig*`, `m_setJitPrimitives` | These are owner-local, so they are not cross-container globals. They are still plain lazy writes and can duplicate work or race under concurrent use of one pool. | Use owner-local `Lazy` or `ConcurrentMap.computeIfAbsent` for hot/shared runtime caches, or freeze/warm them before parallel runtime execution. |
| Should fix soon | Ambient `ThreadLocal` implementation | `s_tloPool`, `getCurrentPool`, `withPool` | Raw `ThreadLocal` with mutable holder arrays relies on perfect manual cleanup and does not make ownership visible. This branch removed the raw setter; the remaining bridge is lexical. | Replace with explicit parameters and delete `withPool(...)`/`s_tloPool`. A `ScopedValue` bridge would improve cleanup, but it would still hide ownership and should not be the endpoint. |
| Done in this branch | Static immutable implicit metadata maps | `s_implicits`, `s_implicitsByPath` | Class-init publication was safe, but the old maps were mutable `HashMap`s held in final static fields. No code mutated them today, but process-wide static metadata should not remain accidentally writable. | The static initializer now clones parser-returned path arrays into a private map and freezes both maps with `Map.copyOf(...)`. `ConstantPoolDiagnosticsTest.staticImplicitMetadataMapsAreImmutable()` guards the shape. |
| Done in this branch for runtime-published pools; compiler backlog for phase ownership | Destructive pool optimization and module replacement | `preRegisterAll()`, `postRegisterAll(...)`, `optimize()`, `replaceModule(...)`, `FileStructure.replaceModuleId(...)`, disassembly paths | These mutate pool contents, positions, and caches. They are intended for serialization/compiler flows, not concurrent runtime execution. | Runtime-published pools now reject these paths before mutation. Incremental compiler work should still isolate mutable compiler pools from frozen runtime snapshots. |

## Field Inventory

Current `ConstantPool` fields fall into these groups.

### Static Process State

| Field | Classification | Notes |
| --- | --- | --- |
| `s_implicits` | Done in this branch | Parsed once from `implicit.x`. The static initializer clones parsed path arrays and stores them in an immutable map. |
| `s_implicitsByPath` | Done in this branch | Same static catalog, now frozen with `Map.copyOf(...)`. |
| `s_tloPool` | Should fix, runtime-relevant | Ambient owner lookup. The stored value is per thread, but the owner dependency is hidden. This branch removed raw `setCurrentPool(...)` mutation and removed `getCurrentPool()`, leaving only lexical `withPool(...)` scopes. Replace the remaining scopes with explicit owner parameters and delete the thread-local bridge. |

### Core Pool Storage

| Field | Classification | Notes |
| --- | --- | --- |
| `f_listConst` | Done for runtime-published writes; must audit for immutable snapshot | Owner-local list of constants by index. Registration mutates it; `getContained()` intentionally returns a live index iterator so validation can append while iterating. Runtime publication now blocks new registration and destructive storage rewrites. A future immutable runtime snapshot would remove the remaining reliance on mutable `ArrayList` reads. |
| `m_mapConstants` | Mostly OK, must audit with `f_listConst` | Volatile copy-on-write `EnumMap` of per-format `ConcurrentHashMap`s. The lookup maps are concurrent, but they sit over mutable `f_listConst`. |
| `m_mapLocators` | Mostly OK, must audit with `f_listConst` | Same shape as `m_mapConstants`. |
| `f_setValidPools` | Should fix soon | Identity set of upstream pool owners. Built lazily and read during registration assertions. It is not a runtime cache, but mutable lazy build should be synchronized or eagerly built before parallel registration. |
| `m_fRecurseReg` | Compiler/assembly state | Registration mode used while preparing/assembling constants. Not a runtime execution cache. |

### Type-Info And Runtime Metadata

| Field | Classification | Notes |
| --- | --- | --- |
| `f_tlolistDeferred` | Must audit | Per-pool `TransientThreadLocal` list used during type-info recursion. It should not cross pool owners. It is safer than a global, but still hidden context and should stay tightly scoped. |
| `f_listInvalidated` / `m_cInvalidated` | Done for runtime-published writes; compiler metadata state otherwise | `Vector` plus volatile count. This is an explicit per-pool invalidation log. Runtime publication now blocks TypeInfo invalidation because it is compile-time mutation. Long term, prefer a clearer synchronized/immutable snapshot API for compiler-owned pools. |
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
rg -n "ConstantPool\\.getCurrentPool|ConstantPool\\.withPool|getCurrentPool\\(\\)|withPool\\(" \
  javatools/src/main/java
```

Current sites after the runtime-boundary cleanup:

| Site | Category | Recommended direction |
| --- | --- | --- |
| `org/xvm/tool/Compiler.java:315` | Compiler/tool | Compiler rewrite backlog. Explicit pool parameter is better; not this runtime PR. |
| `org/xvm/tool/Runner.java:227` | Runtime launch | Acceptable scoped owner setup at launch boundary. |
| `org/xvm/api/InterpreterConnector.java:87` | Runtime API | Fixed in this branch: uses `m_containerMain.getConstantPool()` and asserts any existing ambient pool matches that explicit owner. |
| `org/xvm/compiler/Compiler.java:155,192,236,280` | Compiler | Compiler rewrite backlog. |
| `org/xvm/runtime/MainContainer.java:194` | Runtime | Fixed in this branch as a boundary scope with `ConstantPool.assertCurrentPool(...)`. Deeper helper APIs can still be made explicit later. |
| `org/xvm/runtime/Container.java:118` | Runtime | Fixed in this branch as a boundary scope using `getConstantPool()` plus `ConstantPool.assertCurrentPool(...)`. |
| `org/xvm/runtime/NativeContainer.java:104,153` | Runtime startup | Fixed in this branch: startup now uses lexical `withPool(...)` scopes and no raw setter. |
| `org/xvm/runtime/ServiceContext.java:309` | Runtime | Fixed in this branch as a boundary scope asserting `f_pool`. |
| `org/xvm/runtime/template/_native/fs/xOSStorage.java:338` | Runtime async watch thread | Fixed in this branch: each event derives the pool from the watched storage handle's container and asserts that scoped owner. |
| `org/xvm/runtime/template/_native/web/xRTServer.java:653` | Runtime request thread | Fixed in this branch as a request boundary scope asserting the service context pool. |
| `org/xvm/runtime/template/_native/mgmt/xContainerControl.java:111` | Runtime management invoke | Fixed in this branch as a management boundary scope asserting the target container pool. |
| `org/xvm/asm/FileStructure.java:181` | Assembly/linker | Boundary scope only; `getErrorListener()` no longer redirects through ambient pool. |
| `org/xvm/asm/MethodStructure.java:666` | Assembly/linker | Compiler/linker backlog. |
| `org/xvm/asm/constants/TypeConstant.java:1901,2075` | Type logic | Boundary scopes remain for recursive type work; covariance/contravariance semantic lookups now take explicit pools. |
| `org/xvm/asm/constants/MethodInfo.java` | Type/method metadata | Fixed in this branch: derives pool from `TypeInfo` or method identity. |
| `org/xvm/asm/constants/MethodBody.java` | Type/method metadata | Fixed in this branch: derives pool from method identity. |
| `org/xvm/asm/constants/IdentityConstant.java` | Identity/type logic | Fixed in this branch: resolver-backed nested identities carry caller-supplied pool. |
| `org/xvm/asm/constants/PropertyInfo.java` | Type/property metadata | Fixed in this branch: shared identity repair uses property/type-info owner pool. |
| `org/xvm/asm/constants/IntConstant.java` | Constant operations | Fixed in this branch: range folding uses receiver pool. |
| `org/xvm/asm/constants/ByteConstant.java` | Constant operations | Fixed in this branch: range folding uses receiver pool. |
| `org/xvm/javajit/NativeTypeSystem.java:108` | JIT | Documented in `jit-implications.md`; not this runtime PR. |
| `org/xvm/javajit/JitConnector.java:64` | JIT | Documented in `jit-implications.md`; not this runtime PR. |

### Runtime-Boundary Fix Completed In This Branch

The runtime cleanup wave is intentionally narrow:

- `InterpreterConnector.invoke0(...)` no longer asks
  `ConstantPool.getCurrentPool()` for its owner. It uses
  `m_containerMain.getConstantPool()`, which is the same pool used to start and
  invoke the container.
- `NativeContainer.loadNativeTemplates()` no longer pairs
  `setCurrentPool(pool)` with a later `setCurrentPool(null)`. It uses
  `try (var _ = ConstantPool.withPool(pool))`, so the previous thread value is
  restored even when startup fails.
- Native template loading now runs through `NativeContainer.create(...)` after
  the native-container constructor returns, so the scoped pool is no longer
  bound while the native-container owner itself is still under construction.
- `ConstantPool.setCurrentPool(...)` was removed because no source caller
  remained. Keeping only `withPool(...)` prevents open-ended ambient owner
  mutation from being reintroduced accidentally.
- `MainContainer.invoke0(...)`, `Container.ensureServiceContext()`,
  `ServiceContext.drainWork()`, `xOSStorage.WatchDaemon`,
  `xContainerControl.invokeInvoke(...)`, and `xRTServer.RequestHandler` now
  assert that the scoped pool equals the explicit owner pool they already have.

This does not change caching behavior. The same owner `ConstantPool` is used as
before; the difference is that callers no longer depend on an arbitrary
thread-local value, and scoped bridges fail under assertions if the owner is
wrong. The focused verification lives in
`javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java`.

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
constant forms. `RegisterConstant` still has its own adoption hook because its
compile-time form can carry a transient `Register`, and `MethodBindingConstant`
has an explicit descriptor hook so the frame-dependent base fails closed. The
live-handle guard belongs specifically on `HandleConstant`.

### Guard Runtime Current-Pool Scopes

At runtime thread/adaptor boundaries, assert the scope matches the explicit
owner:

```java
ConstantPool pool = container.getConstantPool();
try (var _ = ConstantPool.withPool(pool)) {
    ConstantPool.assertCurrentPool(pool, "Owner.boundary");
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

### Guard Runtime Pool Mutation

`ConstantPool` now has an always-on runtime publication marker:

```java
pool.markRuntimePublishedForDiagnostics("MainContainer.invoke0(main)");
```

`MainContainer.invoke0(...)` installs this marker immediately after entry setup
and module singleton resolution. A later `register(...)` for an already-known
constant still returns the existing value; a genuinely new registration throws
before adding the constant to `f_listConst` or the lookup maps.

This is now a normal runtime boundary check, not a stress-only diagnostic. The
marker discovers which `ensure*` calls remain hot during user-code execution so
they can be warmed, re-keyed, or given an explicit synchronization/owner policy.

The first same-JVM diagnostic stress run found exactly that kind of hot path:
`New_1` first instantiated `TestProps:Standard` during user code, and
`ClassComposition.<init>(...)` registered private/struct access-type constants
for that class. This branch fixes one narrower access-view subcase:
`ClassComposition.ensureAccess(PROTECTED)` no longer registers the protected
access type after a canonical composition already exists. The constructor now
prewarms private/protected/struct access-type constants, while the actual view
compositions remain in the old lazy owner-local cache.

This branch also fixes the first-composition subcase for type/class constants
that are already present before the runtime publication marker is installed.
`ConstantPool.markRuntimePublishedForDiagnostics(...)` prewarms
private/protected/struct access-type constants for already-known class/type
identity, then installs the marker. That preserves normal runtime behavior:
the actual `ClassComposition` and access-view compositions are still created
lazily in the owner-local composition cache.

The broader fix is still to pre-warm class compositions/access-type constants
before marking the pool, move non-logical composition helper state out of pool
registration, split runtime pools from compiler/linker mutation, or add a
narrow allowlist only after proving the late registration is deterministic,
owner-local, and concurrency-safe.

### Runtime-Published Destructive Mutation Guard

The 2026-08-25 completion pass extended the publication marker from "no new
constants" to "no destructive pool lifecycle mutation." The fix is a guard on
the runtime-published pool itself, so compiler/linker/serializer copies keep
their current behavior while runtime-visible pools fail loud before their list,
positions, lookup maps, or TypeInfo invalidation state are rewritten.

| Site | Classification after source read | Runtime-published behavior in this branch |
| --- | --- | --- |
| `ConstantPool.register(...)` and all `ensure*Constant(...)` callers | Runtime-published must-fix, now guarded | A genuinely new constant throws before insertion into `f_listConst` or lookup maps; an already interned constant still returns normally. |
| `ConstantPool.preRegisterAll()` | Runtime-published must-fix, now guarded | `FileStructure.writeTo(...)` cannot reset reference tallies on a runtime-visible pool. |
| `ConstantPool.postRegisterAll(...)` / `optimize()` | Runtime-published must-fix, now guarded | Assembly cannot reorder constants, rewrite positions, clear lookup maps, or clear implicit caches after publication. |
| `FileStructure.replaceModuleId(...)` / `ConstantPool.replaceModule(...)` | Runtime-published must-fix, now guarded at entry and pool surgery | Module-id replacement fails before mutating the module identity or pool identities on a runtime-visible file. Fresh temporary compiler/linker copies still use the existing destructive path. |
| `ConstantPool.disassemble(...)` | Defensive guard | Disassembly normally targets a fresh pool, but a marked pool now refuses list/map reload before clearing storage. |
| `ConstantPool.invalidateTypeInfos(...)` | Runtime-published must-fix, now guarded | Compile-time invalidation cannot append to `f_listInvalidated` or clear relation/TypeInfo state after runtime publication. |
| `ensureConstantLookupComplex(...)` / `ensureLocatorLookupComplex(...)` | Allowed runtime cache construction | These build lookup caches over already-registered constants. They do not change logical pool contents and remain concurrent/copy-on-write. |
| Per-pool implicit/core caches (`m_clz*`, `m_type*`, `m_val*`, `m_sig*`) | Should-fix, not closed by this guard | Runtime publication catches any cache path that would intern a missing constant. Duplicate publication of an already-known owner-local helper remains a later `Lazy`/snapshot cleanup. |
| `HandleConstant` live payloads | Separate must-audit/fixed subcase | The publication marker fences new handle constants, and the adoption guard blocks cross-pool movement. A future policy should remove live handles from frozen/shared pool state entirely. |

Verification:

- Red-on-old-shape: with only the new tests present, the old production shape
  compiled and failed `runtimePublishedPoolRejectsRecursiveRegistrationPass`,
  `runtimePublishedPoolRejectsModuleReplacement`, and
  `runtimePublishedPoolRejectsTypeInfoInvalidation` behaviorally.
- Green-on-new-shape:
  `./gradlew :javatools:test --tests org.xvm.asm.ConstantPoolDiagnosticsTest --rerun-tasks --no-build-cache`.

## Ambient Bridge End State

A `ScopedValue<RuntimeOwner>` bridge would be safer than the current raw
`ThreadLocal` because the binding is lexical:

```java
record RuntimeOwner(Container container, ConstantPool pool) {
    RuntimeOwner {
        Objects.requireNonNull(container);
        Objects.requireNonNull(pool);
    }
}
```

Boundary code could bind it:

```java
RuntimeOwner owner = new RuntimeOwner(container, container.getConstantPool());
ScopedValue.where(RuntimeOwners.CURRENT, owner).run(() -> invoke(...));
```

But that is still not the desired final API. A helper that reads

```java
static ConstantPool currentPool() {
    return RuntimeOwners.CURRENT.orElseThrow().pool();
}
```

still hides an owner from the signature. The endpoint is explicit ownership:

```java
TypeConstant ensureResultType(ConstantPool pool, TypeConstant left, TypeConstant right) {
    return pool.ensureUnionTypeConstant(left, right);
}
```

The follow-up should remove `ConstantPool.withPool(...)`, `s_tloPool`, and the
current-pool source-shape allowlist completely. `ScopedValue` remains valid for
narrow context-sensitive algorithms such as `TypeConstant.s_context`, but it is
not a replacement for owner parameters.

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
