# Fixed Sites In `lagergren/lazy-instance`

This document is the branch-delta inventory. It is based on `master` as the
baseline and the current working tree on branch `lagergren/lazy-instance`.

It answers a narrower question than the broader inventory:

- Which broken or suspicious sites existed on `master`?
- Which of those sites are actually fixed by this branch?
- Which fixes are must-fix correctness work, and which are supporting or
  should-fix cleanup?
- Did the replacement preserve the old cache behavior without preserving the
  old wrong-owner global state?

The broad backlog remains in [state-inventory.md](state-inventory.md). The
high-priority unfixed race backlog remains in
[must-fix-races.md](must-fix-races.md).

## Relationship To PR #534

PR #534 (`fix-runtime-enum-singleton-race`) is superseded by this branch for
the enum singleton race. The PR's actual delta against its merge-base with
`origin/master` is limited to:

- `SingletonConstant` lifecycle-state synchronization,
- public enum lookup helpers and call-site fixes for natural enum values,
- typed template lookup helpers used by those call sites,
- and `SingletonConstantTest`.

This branch carries those behavioral fixes forward and broadens the owner model
around them:

| PR #534 behavior | This branch |
| --- | --- |
| Only one fiber may own `SingletonConstant` initialization | Same behavior, implemented as one final `AtomicReference<InitState>` instead of three lock-protected mutable fields |
| Other fibers wait for the initializing fiber | Same shared `CompletableFuture` waiter semantics |
| Same-fiber recursion installs an `InitializingHandle` instead of deadlocking | Same behavior |
| `xEnum.createConstHandle()` must not publish a natural enum construction struct as the singleton handle | Same behavior |
| Public/native enum paths must use initialized enum handles or deferred handles | Same behavior, with `Container.nativeTemplates()` and lazy owner caches where static globals used to be |
| Repeated casts around template lookup were removed in a few places | Replaced by explicit typed accessors such as `getEnumTemplate(...)`, `getTemplate(..., Class<T>)`, and final lazy template fields |

The original PR can be closed once this branch is pushed/opened as the
replacement PR and CI has run there. Closing it earlier would remove the visible
GitHub tracking issue before the replacement branch exists on GitHub.

## Baseline Commands

Use these commands from the repository root:

```bash
git merge-base HEAD master
git diff --name-status master --
git diff --unified=0 master -- javatools/src/main/java \
  | rg "^-\\s*(public|protected|private) static (?!final)|^-\\s*INSTANCE\\s*=|^\\+\\s*private final Lazy|^\\+\\s*private record"
```

## Mechanism Rules Used By This Branch

The goal is not to remove caching. The goal is to move caches to the owner that
actually owns the value.

| Old value kind | Old pattern on `master` | Replacement in this branch | Performance/cache behavior |
| --- | --- | --- | --- |
| Native template singleton | Mutable `public static INSTANCE`, assigned from constructors | Central `NativeTemplates` lookup table plus `Container`-owned lazy cache | One resolved template per container/key, cached behind `Lazy` |
| Immutable template/pool metadata | Mutable static `TypeConstant`, `TypeComposition`, `MethodStructure`, `xEnum`, etc. | Final template field, usually `Lazy.Owner<O,T>` or grouped owner-local `Lazy<Info>` | Same "compute once" behavior, but per owning template/container |
| String handles and common string values | Mutable `xString.INSTANCE`, static `EMPTY_STRING`, `ZERO`, `ONE`, and ownerless `makeHandle(...)` | `NativeTemplates.string()`, owner-required factories, and final owner-local `Lazy` fields | Same cached empty/one/zero/empty-array handles, but one cache per owning container |
| Finite owner-derived keyed cache | Mutable static map | Final `Lazy.Owner<O,Map<K,V>>` with immutable `Map.copyOf` | Same single map build, no global cross-container map |
| Pure process-global data | Mutable static collection | `private static final Set.of(...)` or equivalent immutable constant | Class-init safe publication, no per-container overhead |
| Suspendable lifecycle state | Several mutable fields | One immutable state record in `AtomicReference` | CAS publishes complete lifecycle snapshots |
| Hot per-value memoization | Plain lazy fields on value objects | Usually unchanged in this PR unless it is a real owner/publication bug | Avoid adding per-object `Lazy` footprint for should-fix-only cleanup |
| Container-owned helper state | Base constructor passes `this` to helpers or runtime registry | Owner-explicit `ConstHeap`, owner-lazy `NativeTemplates`, and post-construction container factories | Same per-container caches and registry membership, with no partially constructed owner publication |

`Lazy.Owner` is preferred when a value is computed once from its completed
owner, has no key, has no retry/failure semantics, and the holder footprint is
appropriate for the owning object. It is not a blanket replacement for every
race fix. Keyed owner caches remain `ConcurrentMap`/immutable maps; intentional
shared state uses atomics; low-level cells with special publication or retry
rules use explicit volatile/synchronized code so the invariant is visible.

Passing a `Container` is part of the replacement semantics. A static helper can
no longer read "the" process-global template or pool; it needs the caller's
container so it can select the owner-scoped cache. For example,
`xRTComponentTemplate.ensureComponentArrayType(container)` still uses a
constant pool; it now uses the pool behind
`NativeTemplates.get(container).componentTemplate()` and that template's final
lazy field.

## Must-Fix Sites Fixed Here

### `SingletonConstant` Lifecycle State

Master had three mutable lifecycle fields:

- `m_handle`
- `m_fiberInitializing`
- `m_cfInitialized`

Those fields represented one logical state machine, but readers could observe
mixed snapshots such as "has a handle but no waiter" or "has an initializing
fiber with a stale waiter". The branch replaces them with one final
`AtomicReference<InitState>` and immutable state records in
`javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java`.

This is must-fix. It is not a `Lazy` problem because singleton construction can
suspend, recurse, abort, and retry. A CAS state machine is the correct simple
mechanism.

### Adopted Constant Runtime State

The parallel `TestProps` stress run exposed another owner bug in the same
general area: `Constant.adoptedBy(...)` uses `Object.clone()`, and the cloned
`SingletonConstant` copied the final `AtomicReference<InitState>` that stores
runtime singleton state. The field was final, but the referenced state cell was
still mutable and became shared by constants registered in different pools.

Effect:

- one child container initialized a module/package/property singleton;
- a second child container adopted the same logical singleton constant;
- the adopted constant reused the first container's runtime handle state;
- `mgmt.Container.invoke` then received a package/module handle owned by the
  wrong container;
- `TestProps` observed an `@Lazy` reference as already assigned in what should
  have been a fresh container.

Fix:

- `SingletonConstant.adoptedBy(...)` constructs a fresh singleton constant for
  the target pool, so the adopted constant starts with an empty owner-local
  `InitState` cell.
- `FSNodeConstant.adoptedBy(...)` and `FileStoreConstant.adoptedBy(...)` clear
  cloned transient runtime handles for the same reason. `FSNodeConstant` also
  clears its derived path-literal cache because that literal is owned by the
  source pool once computed.
- `OwnershipDiagnostics.assertHandleValidIfEnabled(...)` is now wired into the
  `mgmt.Container.invoke` module-target boundary used by the parallel stress
  runner, so wrong-owner handles fail structurally instead of surfacing later as
  misleading XTC-level state failures.

Ramifications:

- Constant value semantics and interning behavior are unchanged.
- The first initialization in each owner still caches the runtime handle for
  that owner.
- The source constant keeps its existing runtime cache; only the adopted copy
  starts empty.
- Normal single-container execution has no steady-state cache miss or extra
  handle allocation after initialization.
- `FSNodeConstant.getPathConstant()` still caches per node. Only adopted copies
  recompute the path in the destination pool, which is the behavior the old
  shallow copy intended but did not enforce.
- This avoids a broader and more expensive `xContainerLinker` file-structure
  cloning workaround; the correct owner boundary is constant adoption.

The full incident, rejected hypothesis, diagnostics output, and proof commands
are documented in
[stress-discovered-runtime-issues.md#adopted-singletonconstant-runtime-state-leak](stress-discovered-runtime-issues.md#adopted-singletonconstant-runtime-state-leak).

The adoption hardening wave also fixes the remaining runtime-relevant
shallow-clone helper state identified by the audit:

- `TypeConstant.setContaining(...)` now clears every non-logical transient
  helper/runtime/JIT cache when a cloned type changes pool owner, including
  recursive-depth, in-progress relation, consumption/production, type-handle,
  JIT-name, and normalization state.
- `ParameterizedTypeConstant.adoptedBy(...)` reconstructs the logical
  parameterized type for the target pool instead of shallow-cloning the final
  `StampedLock` and resolver/JIT helper state.
- `SignatureConstant.adoptedBy(...)` reconstructs the logical signature for
  the target pool, preserves the transient property-signature marker that
  participates in in-memory identity, and drops comparison/JIT helper state.
- `TypeParameterConstant.adoptedBy(...)` reconstructs the logical register
  type parameter for the target pool instead of shallow-cloning the final
  recursive-comparison `TransientThreadLocal`.
- `HandleConstant.adoptedBy(...)` now allows only the first registration of a
  fresh unowned runtime handle constant. Moving an already-owned live handle
  constant to another pool throws immediately.
- `ConstantAdoptionValidator` now treats arbitrary copied runtime handles,
  templates, and type compositions as forbidden shared owner state. The
  existing fresh `HandleConstant` first-registration path remains allowed, but
  any future default-cloned constant that carries live runtime state fails
  under `-Dxvm.asm.validateConstantAdoption=true`.

These changes preserve the old cache intent: each target owner still computes
and caches the same values locally after first use, but no owner inherits
another owner's helper cell or runtime handle through `Object.clone()`. The
focused regression test is
`javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java`; copied into a
detached `master` worktree, that test failed all five adoption cases.

The broader owner-transfer audit is documented in
[constant-adoption-clone-audit.md](constant-adoption-clone-audit.md). That file
explains why adoption exists, why it should preserve only logical constant
value state, and how this branch hardens the remaining runtime-relevant
shallow-copied helper/runtime fields.

### Container-Owned TypeHandle Cache

What was wrong:

- `TypeConstant.ensureTypeHandle(Container)` accepted the caller container, but
  the shared-handle cache lived on `TypeConstant.m_handle`.
- A `TypeHandle` is not just a logical constant. `xRTType.makeHandle(...)`
  creates a native Type object with a `TypeComposition` and helper fields owned
  by the container that built it.
- If two containers shared the same constant pool, the first call cached the
  first container's handle on the `TypeConstant`. A later call with another
  container returned that first owner handle because the cache key was only the
  `TypeConstant`.
- The field was also a plain lazy write, so parallel first use had no
  happens-before edge.

Replacement:

- `TypeConstant.ensureTypeHandle(Container)` still owns the public type API and
  still registers shared foreign-pool types into the caller pool before
  creating a handle.
- Shared handles are now cached by `Container.ensureTypeHandle(TypeConstant)`
  in `Container.f_mapTypeHandles`.
- `Container.ensureTypeHandle(...)` rejects a type from a different pool, so a
  caller cannot accidentally populate one container's cache with another
  pool's type.
- Foreign type handles remain uncached, preserving the old semantics.

Behavior and performance:

- The old intended cache behavior is preserved: after first use, a container
  reuses one shared `TypeHandle` per registered `TypeConstant`.
- The cache key now includes the owner container, so two containers sharing one
  pool no longer share owner-bearing Type handles.
- `ConcurrentHashMap.computeIfAbsent(...)` gives safe publication and
  same-owner first-use coalescing without adding locks to the hot read path.

Regression proof:

- `NativeTemplatesTest.typeHandlesAreCachedByContainerOwner()` fails on master
  because `TypeConstant.m_handle` exists there.
- The same test verifies that the replacement cache lives on `Container` as a
  `ConcurrentMap`.

### Method Parameter Clone Ownership

The clone audit found two separate method-copy bugs that were not
`Constant.adoptedBy(...)` issues:

- `Parameter.cloneBody()` used `Object.clone()`, then cleared
  `m_fImplicitDeref` and `m_regDeref` on the source `Parameter` instead of the
  copy. A temporary method clone could therefore change the source method's
  parameter semantics and keep a deref `Register` allocated for the source
  method on the copy.
- `MethodStructure.cloneBody()` copied return and parameter objects, then called
  `param.setContaining(this)`. The cloned method's parameter arrays could
  therefore contain parameters whose owner was still the source method.

The replacement keeps those two issues separate:

- `Parameter` no longer implements `Cloneable` and no longer uses
  `Object.clone()`. It has an owner-explicit `copyFor(MethodStructure)` helper
  that copies logical metadata and drops only the method-owned deref-register
  cache.
- `MethodStructure.cloneBody()` passes the cloned method (`that`) into
  `copyFor(...)` for every return and parameter, so copied parameters resolve
  through the cloned method from birth.

Behavior and performance are preserved: method cloning still allocates exactly
one `Parameter` copy per source return/parameter, and the first dereference on a
copied implicit-ref parameter still computes the same cached register lazily in
the copied method. The branch only removes accidental source mutation and stale
method-owner cache sharing.

Regression proof lives in
`javatools/src/test/java/org/xvm/asm/AsmConstructorEscapeTest.java`:

- `methodClonePreservesSourceDerefStateAndGetsFreshCloneState()` proves a method
  copy leaves the source implicit-deref state and source cached register intact,
  while the copy starts with no copied deref register.
- `methodCloneAttachesCopiedParametersToClone()` proves cloned return and
  parameter objects report the cloned method as their containing structure.

The follow-up delegated-method fix closes the related but separate array-copy
bug in `ClassStructure.ensureMethodDelegation(...)`. That path did not clone a
method body; it cloned only `Parameter[]` containers and reused the source
method's `Parameter` elements when synthesizing a delegating method. Because a
`Parameter` carries mutable transient helper state such as the implicit-deref
register, the delegated method could share or overwrite source-method state.

The branch adds `MultiMethodStructure.createMethodCopyingParameters(...)` for
this synthetic-copy case. The normal `createMethod(...)` API is left unchanged:
ordinary callers that construct fresh `Parameter` objects still get the same
construction path as before. Delegated methods use the new factory, which
constructs the method, copies supplied parameter elements for that new method
owner before publication, and then adds the method as a child. Generated
delegation code reads parameter/return metadata from the copied synthetic
method, so code generation and ownership agree.

`delegatedMethodFactoryCopiesParameterElementsForNewOwner()` proves the
delegated method owns distinct return/parameter objects, preserves logical
implicit-deref metadata, drops copied deref-register cache state, and leaves
the source parameter cache intact.

### Cross-Owner GenericHandle Masking

What was wrong:

- `GenericHandle.maskAs(...)` could use `cloneAs(...)` while changing the
  apparent owner container.
- `cloneAs(...)` is a same-object access-view operation, not an ownership
  transfer. It shallow-copies subclass state and `GenericHandle` views share
  live field storage.
- If the handle graph was not already shared with the target container, direct
  cross-owner masking could retain source-owner handles and refs while making
  the clone appear to belong to the target owner.

Replacement:

- Non-core objects still take the existing proxy path.
- Direct cross-owner `GenericHandle.maskAs(...)` now first requires
  `isShared(targetOwner, null)`.
- If the graph is not shared, masking returns `null` before `cloneAs(...)` can
  shallow-copy live runtime state.

Behavior and performance:

- Same-owner masks and reveals keep the old cheap access-view behavior.
- Valid cross-owner masks for already shared graphs keep the old path.
- Invalid cross-owner direct sharing now fails early instead of manufacturing a
  wrong-owner view. That is an intentional hardening of an invalid state, not a
  cache or hot-path performance change.

Regression proof:

- `OwnershipDiagnosticsTest.crossOwnerMaskRejectsNonSharedHandleBeforeClone()`
  builds a synthetic non-shared handle whose `cloneAs(...)` throws if reached.
  The fixed path checks sharing and returns `null`; the old path would continue
  into the shallow clone.

### Same-Owner GenericHandle Access Views

What was wrong:

- `ObjectHandle.cloneAs(...)` uses `Object.clone()`. That preserves the dynamic
  Java subtype cheaply, but it is a shallow copy and bypasses constructors.
- `GenericHandle` stores runtime field values in a final `ObjectHandle[]`.
  A shallow clone therefore creates a second Java handle view that shares the
  same mutable field array as the source view.
- Sharing that array is intentional for regular fields. A revealed view and a
  struct view represent the same logical runtime object, so a regular field
  write through either view must be visible through the other.
- The old code then rewrote inflated `RefHandle.$outer` values inside that
  shared array when cloning between struct and non-struct views. That mixed two
  incompatible concepts in one slot: shared object storage and view-specific
  holder state.

Why `clone()` was tempting but wrong here:

- It avoided writing view constructors for every `ObjectHandle` subtype.
- It preserved the Java subclass, existing cached subtype fields, and regular
  field-array sharing with very little code.
- Those are convenience wins, not correctness guarantees. In owner-bearing
  runtime objects, a shallow clone silently shares final reference fields,
  mutable helper cells, locks, arrays, and caches. If clone-time code then
  mutates any object behind those shared references, it mutates the source view
  too. That is already broken in one thread and becomes nondeterministic when
  two containers or fibers reveal/mask the same object concurrently.

Replacement:

- `GenericHandle.cloneAs(...)` keeps the old shared `m_aFields` backing for
  regular values.
- When a struct/revealed view needs a different inflated-ref `$outer`, the clone
  creates a shallow ref view and stores it in a sparse copy-on-write
  `m_aFieldOverrides` array on that view only.
- Field reads first consult the sparse override; the common no-override path is
  still a direct read from `m_aFields`.
- Field writes go to the shared backing unless that exact view has an override
  for the slot. For inflated refs, referent writes still go through the shared
  ref backing, while `$outer` stays view-local.
- `OwnershipDiagnostics.dump(...)` now prints both the effective field view and
  the sparse override array when overrides exist, so stress failures do not hide
  this view-local state.

Behavior and performance:

- Same-owner masks and reveals still allocate a cheap access view; they do not
  deep-copy the object graph.
- Existing regular field write-through semantics are preserved.
- Existing inflated ref referent state is preserved and shared as before.
- Only the holder/`$outer` edge is view-local.
- Handles that never need view-local inflated refs pay no extra per-access
  allocation and keep the original direct field-array path.
- A struct/revealed transition allocates at most one sparse override array for
  the cloned view plus one ref view per inflated field that already had an
  outer. That is bounded by the object's field layout, not by the reachable
  handle graph.

Regression proof:

- `GenericHandleCloneAsTest.sameOwnerCloneKeepsInflatedRefOuterViewLocal()`
  constructs a synthetic same-owner struct/revealed clone with an inflated ref
  and a regular field. The test is intentionally written without branch-only
  production factories so it can be copied to `master`.
- On `master`, the test fails because creating the clone rewrites the source
  ref's `$outer` to the clone.
- On this branch, the source ref still points at the source, the clone's ref
  points at the clone, referent writes through the clone's ref are visible
  through the source ref, and regular field writes through the clone are visible
  through the source handle.

The broader `ConstantPool` state audit is documented in
[constant-pool-state-audit.md](constant-pool-state-audit.md). It distinguishes
per-pool caches, ambient owner lookup, runtime registration/adoption hazards,
and compiler-only pool mutation paths.

This branch also fixes a focused late-registration source in
`ClassComposition.ensureAccess(PROTECTED)`. On master, a canonical
`ClassComposition` precomputed private and struct access type constants, but it
created the protected access type on first protected-view request. That request
can happen during ordinary runtime execution after
`MainContainer.invoke0(...)` has marked the pool as published for diagnostics.
The old pattern was bad because a runtime-hot view conversion looked like a
cache lookup while it could still grow `ConstantPool` list/map state.

The replacement prewarms private, protected, and struct access type constants
when the canonical composition is created. It deliberately keeps the actual
access-view compositions lazy in the existing owner-local composition cache.
That preserves the old runtime cache shape: one more interned logical type
constant is created during composition setup, but no protected view composition
is allocated until it is requested. The focused regression is
`ClassCompositionLateRegistrationTest.protectedAccessViewDoesNotRegisterAfterRuntimePublication()`.

This branch also fixes the related first-composition diagnostic subcase for
type/class constants that already exist before runtime publication. When
`ConstantPool.markRuntimePublishedForDiagnostics(...)` is about to install the
diagnostic marker, it prewarms private/protected/struct access-type constants
for already-known class/type identity. That lets `ClassComposition` remain
lazy, so object construction does not pay an eager composition allocation, but
the logical constants that composition construction will need are already in
the pool before the "no more runtime registrations" marker. The focused
regression is
`ClassCompositionLateRegistrationTest.firstClassCompositionDoesNotRegisterAfterRuntimePublication()`.

The broader category is still tracked in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md) and
[constant-pool-state-audit.md](constant-pool-state-audit.md): a real frozen
runtime-pool design must still decide what happens to genuinely new constants
created after publication and must not rely on an opt-in diagnostic property as
the only guard.

### ClassComposition Runtime Field Layout And Helper Caches

This branch also fixes the runtime field-layout group and two owner-bearing
lazy cells on `ClassComposition`:

- `m_mapFields`, `m_cRegularFields`, `m_fHasOuter`, and `m_fHasSpecial`, the
  field-layout group used to allocate structures, initialize refs, enumerate
  fields, and build native Stringable helpers.
- `m_ashFieldNames`, the cached `StringHandle[]` used by native Stringable
  support for const classes.
- `m_methodInit`, the cached synthetic structure initializer created from the
  class field layout.

The field-layout group was not one immutable object. One volatile map write
published several side fields by convention, and access-view clone constructors
copied whatever values existed at that instant. A protected/struct view created
before layout construction could therefore keep stale `null`/default layout
state even though the inception composition later built the real layout. That is
bad even without parallelism; parallel first access simply makes the timing
easier to hit.

The field-name and initializer caches were plain lazy fields on a runtime object
that is reachable from handles and access-view conversion. The field-name array
is not just text metadata: each `StringHandle` carries a `TypeComposition` from
the owning container. Publishing that array with a plain write could expose a
partially filled array or an array whose element handles had no happens-before
edge from the producing thread. The auto initializer is owner-pool metadata;
racing first access could create duplicate transient `MethodStructure` objects
and let the last plain write win.

Access-view clones made the shape worse. Field-name arrays were clone-local, so
each view could build a duplicate owner-bearing `StringHandle[]`. The synthetic
initializer cell was copied from the inception composition at clone construction
time, so a view created before first initialization could also build a duplicate
initializer. The owner, field layout, and structure type are the same for all
views, so view-local helper caches were unnecessary timing-dependent state.

The replacement makes the inception composition the single cache owner:

- the field-layout map, regular-field count, and flags are stored in one
  immutable `FieldLayout` record behind final `Lazy.Owner` state;
- `ensureFieldLayout(Container)` asserts the caller uses the composition's
  owner container, so the old owner parameter cannot silently build layout
  against a different container;
- access views delegate `getFieldNameArray()` and `ensureAutoInitializer()` to
  `f_clzInception`;
- the inception `f_fieldLayout`, `f_fieldNames`, and `f_methodInit` cells are final
  `Lazy.Owner` holders;
- access-view compositions reuse those final holders instead of allocating
  unused clone-local lazy cells;
- classes with no fields still return `null` for the auto initializer without
  allocating anything.

This preserves apparent behavior and performance:

- the field-layout map is still built lazily once per inception composition, and
  it preserves insertion-order iteration for field storage order;
- the public `getFieldLayout()` API still returns the same cached map identity,
  but the map shape is now immutable; the contained `FieldInfo` objects remain
  the same runtime metadata objects, so the auto-initializer can still record
  transient initializer metadata on those fields as before;
- field names are still cached as one `StringHandle[]` and returned by identity
  from the API, so no per-call defensive copy or per-call handle creation was
  added;
- the synthetic initializer remains lazy and is not created for fieldless
  classes;
- access views now share the inception cache consistently instead of sometimes
  duplicating it, so normal footprint is the same or smaller than the old
  timing-dependent behavior;
- the old two nullable cache references are replaced by two final lazy holders
  on the inception composition; access views share those holders, so the fix
  avoids multiplying holder objects across view compositions;
- no extra constant-pool entries are introduced by this change beyond the same
  field-name handles and initializer constants the old first-access path already
  created.

`ClassCompositionSafePublicationTest.accessViewsShareSafelyPublishedInceptionRuntimeCaches()`
races canonical and protected access views through the two APIs, verifies that
all callers observe one field-layout map identity and one field-name array
identity, verifies that the field-name handle belongs to the native container
that created it, verifies the map shape is immutable, and checks that the
initializer is computed through the final owner-lazy inception cell.

### PropertyComposition Struct View Cache

This branch also fixes the corresponding custom-property composition access
view cache. On master, `PropertyComposition.ensureAccess(STRUCT)` used a
mutable plain lazy field:

```java
if (m_clzStruct == null) {
    m_clzStruct = new PropertyComposition(this);
}
```

That field caches runtime `TypeComposition` identity, not a throwaway value. A
parallel first struct-view access could allocate duplicate
`PropertyComposition` objects and publish the winner through a plain write. The
struct view shares the inception composition's method/getter/setter call-chain
maps, so the intended model is one struct view for one property composition
owner.

The replacement uses final owner-derived lazy state:

- `f_clzInception` records the canonical property composition;
- `f_fStruct` records whether the object is the struct view;
- `f_structView` is a final `Lazy.Owner<PropertyComposition, PropertyComposition>`
  that creates the struct view from the completed owner on first `STRUCT`
  access.

This is a better fit than a `volatile` field here because the state is exactly
one owner-derived value, has no retry/failure semantics, and does not need a
keyed map. `Lazy.Owner` also avoids `Lazy.of(() -> new PropertyComposition(this))`,
which would create a constructor-time supplier capturing `this`.

Semantics and performance are preserved:

- the struct view is still lazy and is not allocated unless `STRUCT` access is
  requested;
- the struct view still shares the same call-chain maps as the inception
  composition;
- non-struct access from a struct view still returns the inception composition;
- the hot path is a final lazy-holder read instead of a plain nullable field
  read, which is the cost of getting final owner-derived publication without
  another custom mutable cell.

`ClassCompositionSafePublicationTest.propertyCompositionStructViewIsOwnerLazyAndShared()`
uses a real native `String.size` property composition, races eight first
`STRUCT` accesses, verifies that every caller observes the same struct-view
identity, verifies that the struct view returns the inception composition for
public access, and checks that the source shape uses final lazy/role fields.

This branch also narrows ambient `ConstantPool` lookup at runtime boundaries:

- `InterpreterConnector.invoke0(...)` uses
  `m_containerMain.getConstantPool()` instead of
  `ConstantPool.getCurrentPool()`. Correct callers see the same owner pool; stale
  ambient scope is detected by `ConstantPool.assertCurrentPoolIfPresent(...)`.
- `InterpreterConnector` now obtains the native container through
  `NativeContainer.create(...)`. The private native-container constructor only
  initializes constructor-local owner state; the factory runs native template
  loading, base-template installation, resource initialization, and
  service-context creation after construction returns and before the connector
  receives the container. That removes the three `NativeContainer` startup
  `this`-escape diagnostics without delaying initialization or dropping any
  startup cache. `InterpreterConnectorTest` covers the path by constructing
  several connectors in parallel, loading `ecstasy.xtclang.org`, and forcing
  ownership validation across the resulting containers.
- `NativeContainer.loadNativeTemplates()` no longer mutates the current pool with
  a raw setter and later clears it to `null`. It uses lexical
  `withPool(...)` scopes, preserving prior ambient state and restoring it on
  exceptional exits.
- `ConstantPool.setCurrentPool(...)` was removed after the last runtime startup
  caller was converted. Future code must use self-restoring scoped ownership.
- `ConstantPool.getCurrentPool()` was removed entirely. The scoped bridge still
  reads its thread-local slot internally, but there is no callable ownerless
  getter. This locks down the intended rule: runtime/compiler code must pass an
  explicit owner or derive it from receiver state instead of asking hidden
  thread state for ownership.
- `MainContainer.invoke0(...)`, `Container.ensureServiceContext()`,
  `ServiceContext.drainWork()`, `xOSStorage.WatchDaemon`,
  `xContainerControl.invokeInvoke(...)`, and `xRTServer.RequestHandler` now
  assert that any transitional ambient scope matches the explicit container or
  service owner already present at the boundary.

The focused regression coverage is in
`javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java`. It
proves that correct scoped owners pass, explicit-owner code can run without an
ambient pool, and wrong ambient scopes fail under assertions. It also verifies
`xvm.asm.validateConstantPoolCurrentScope=true`, which promotes those bridge
checks to normal `IllegalStateException` failures for stress and launcher runs
that do not enable Java assertions. This is a guard and ownership-visibility
change, not a cache-policy change: all constants are still interned in the same
per-owner `ConstantPool` as before.

This branch also fixes the `TypeConstant.s_setRecursions` diagnostic set. On
`master`, type relation recursion logging used one process-global `HashSet`.
The set only suppressed duplicate stderr messages, but `isA(...)` and related
type checks can run concurrently across pools, so the old design could corrupt
the set while deciding whether to print a diagnostic. The replacement keeps the
same process-wide "print each recursion once" behavior with
`ConcurrentHashMap.newKeySet()`. It does not add per-owner state, does not
change any type relation result, and only adds concurrent-set overhead on the
unusual diagnostic recursion path. `TypeConstantRecursionDiagnosticsTest`
verifies that the backing set is concurrent rather than a `HashSet` and stresses
parallel diagnostic additions.

This branch also removes ambient pool lookup from `TypeConstant` covariance and
contravariance helpers. On `master`, `isCovariantReturn(...)` and
`isContravariantParameter(...)` looked like ordinary type predicates, but they
called `ConstantPool.getCurrentPool()` when resolving auto-narrowing and generic
helper constants. In a same-JVM runtime with more than one container, that makes
the result depend on whichever pool a previous boundary happened to install on
the Java thread. The replacement requires an explicit `ConstantPool` parameter
and rejects `null`, so callers must name the owner pool used for helper
constant interning. Correct old callers pass the same pool they previously had
to install ambiently; no cache behavior changes. `TypeConstantOwnerApiTest`
guards the API shape by proving the old ownerless signatures are gone.

This branch also removes ambient pool lookup from numeric range constant
folding. On `master`, `ByteConstant.apply(...)` and `IntConstant.apply(...)`
used `ConstantPool.getCurrentPool()` for `..`, `..<`, `>..`, and `>..<`
operations. That meant a missing ambient pool could crash compile-time folding,
and a stale ambient pool could create a `RangeConstant` in another owner's pool.
The replacement uses the receiver constant's pool, preserving the old behavior
for correctly scoped callers while making owner selection deterministic.
`ConstantRangeOwnerTest` verifies both the no-ambient and wrong-ambient cases.

This branch also removes ambient pool lookup from
`ConstantPool.checkFunctionCompatibility(...)`. That method already belongs to a
specific pool, but `master` asked `ConstantPool.getCurrentPool()` for the
`Tuple<>` helper type in one compatibility exception. The replacement uses the
receiver pool directly, preserving the same compatibility rule and common-type
cache while removing a hidden thread-local precondition.
`ConstantPoolDiagnosticsTest.functionCompatibilityUsesReceiverPoolWithoutAmbientPool()`
covers the no-ambient case that would fail on `master`.

This branch also removes ambient pool lookup from nested identity generic
resolution. On `master`, `IdentityConstant.resolveNestedIdentity(pool,
resolver)` accepted an explicit output pool and then ignored that pool inside
resolver-backed `NestedIdentity` comparison/hashing. The old code later called
`ConstantPool.getCurrentPool()` when resolving a nested method signature, so a
single-threaded nested compile/type-info operation with stale ambient state
could resolve helper signatures in the wrong owner pool. Parallel containers
made the same hidden precondition easier to hit, but the bad design was already
present: the method signature lied about where ownership came from. The
replacement stores the explicit output pool in resolver-backed nested
identities and keeps canonical no-resolver identities owner-free, preserving the
old cache shape for common cache keys. `NestedIdentityOwnerTest` binds a wrong
ambient pool and proves the resolver still interns the signature in the
explicit pool.

This branch also removes ambient pool lookup from method/property metadata
helpers. On `master`, `MethodBody.pool()`, `MethodInfo.pool()`, and the
duck-typed repair path inside `PropertyInfo.isIdentityValid(...)` used
`ConstantPool.getCurrentPool()`. These helpers look like ordinary metadata
queries, but they can resolve annotations, narrow signatures, or adopt shared
property identities. The old design was not safe even for a single-threaded
runtime: any nested compiler/runtime helper that temporarily changed or cleared
the current pool changed the metadata owner's answer. The replacement derives
the owner from receiver state: `MethodBody` uses its `MethodConstant`,
attached `MethodInfo` uses its `TypeInfo`, unowned `MethodInfo` assembly falls
back to the head method identity, and `PropertyInfo` uses its existing
property-info pool helper. `MethodInfoTest.metadataPoolHelpersUseOwnerWithoutAmbientPool()`
and `TypeInfoMemberOwnershipTest.propertyInfoPoolHelperUsesOwnerWithoutAmbientPool()`
cover the no-ambient case.

This branch also removes ambient pool lookup from `FileStructure` diagnostics.
On `master`, `FileStructure.getErrorListener()` returned the file listener if
one was set, but otherwise looked at `ConstantPool.getCurrentPool()` and, if it
was different from the file's pool, delegated to that pool's file listener.
That is wrong even in a single-threaded tool: a nested helper can temporarily
bind a different pool, causing diagnostics for file A to be logged to file B;
with no ambient pool the fallback could dereference `null`. The replacement
makes diagnostics file-owned unless a caller explicitly sets another listener
on that file. `FileStructureTest.errorListenerIgnoresAmbientPool()` covers the
wrong-ambient and no-ambient cases.
`ConstantPoolDiagnosticsTest.semanticCurrentPoolLookupIsBridgeOnly()` prevents
new semantic source calls to `getCurrentPool()` outside `ConstantPool.java`.
`ConstantPoolDiagnosticsTest.currentPoolLookupGetterDoesNotExist()` prevents
the getter from returning as public or private API.

### Handle Construction `this` Escapes

This branch removes three runtime handle-construction `this` escapes:

- `xRef.RefHandle(clazz, frame, iVar)` no longer writes `this` into
  `Frame.VarInfo` from the constructor.
- `xRef.RefHandle(clazz, name, referent)` no longer initializes the referent
  field through constructor-time public field mutation.
- `xOSFileNode.NodeHandle` no longer initializes the native store field through
  constructor-time public field mutation.

The replacement is the same construction rule used for `NativeContainer`:
construct the object first, then publish or initialize owner-visible state from
a factory. `RefHandle.createRegisterRef(...)` still reads the existing
`Frame.VarInfo` cache, still stores the first register ref in that cache, and
still returns a linked ref for later refs to the same register. The only timing
change is that `infoSrc.setRef(ref)` runs after the handle constructor returns.

`RefHandle.createReferentRef(...)` and `NodeHandle.create(...)` preserve the old
field contents by writing the same initialized backing fields after construction
returns. No runtime cache is removed; the previous frame-local ref reuse and
file-node store association are unchanged.

`javatools/src/test/java/org/xvm/runtime/template/reflect/RefHandleConstructionTest.java`
guards the factory APIs and the two op call sites that create register refs. A
targeted lint compile after this wave:

```bash
./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --console=plain --warning-mode=all
```

emits no `this-escape` warning for `xRef.java` or `xOSFileNode.java`. The full
root lint tally was not rerun for this small wave.

The behavior path was also exercised with the ref-heavy and file-node-heavy
manual tests:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=1 \
  -PsameJvmModules=TestReflection,TestFiles \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

Result: `BUILD SUCCESSFUL in 57s`.

### Runtime Constructor Assertion `this` Escapes

This branch removes two constructor-time instance calls that existed only for
debug assertions:

- `CallChain.FieldAccessChain` no longer calls `isField()` from its
  constructor. The public `isField()` method now delegates to the same private
  static helper that the constructor assertion uses against the constructor
  argument.
- `xRTMethod.MethodHandle` no longer calls `getMethodInfo()` on itself from its
  constructor. The assertion now resolves the same `MethodInfo` from
  `typeTarget` and `method.getIdentityConstant()` directly.

These changes do not remove caching or change runtime behavior. Java assertions
are disabled in normal runs; when they are enabled, the same validation still
happens, but it no longer dispatches through a partially constructed object.

`javatools/src/test/java/org/xvm/runtime/RuntimeThisEscapeConstructionTest.java`
guards both source patterns. A targeted lint compile after this wave:

```bash
./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
  --no-configuration-cache \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --console=plain --warning-mode=all
```

emits no `this-escape` warning for `CallChain.java` or `xRTMethod.java`.

### `ClassTemplate` Implicit-Field Construction

`ClassTemplate` had one root runtime-template constructor escape that was not a
cache, but was still unsafe construction shape: the base constructor called the
overridable `registerImplicitFields(null)` hook. The only current overrides
were `xRef` and `xConst`, and both only added static implicit-field names. The
base constructor still had no right to call subclass behavior while the
subclass object was only partly constructed.

The branch replaces the hook with explicit constructor metadata:

- `ClassTemplate(Container, ClassStructure)` now delegates to the metadata
  constructor with no implicit names.
- `xRef` passes `RefHandle.REFERENT` and `GenericHandle.OUTER` to the base
  constructor.
- `xConst` passes `PROP_HASH` to the base constructor.
- The base constructor still adds `GenericHandle.OUTER` for instance-child
  structures, preserving the old default behavior.

The final `f_asFieldsImplicit` field keeps the same immutable field-name array.
The only construction-time allocation change is that templates with implicit
fields build the set from explicit names rather than by running subclass code.
Templates with no implicit fields and no instance-child role keep the shared
empty array. No runtime lookup cache is removed.

`RuntimeThisEscapeConstructionTest.implicitFieldsAreConstructorMetadata()`
guards the `xRef` and `xConst` constructor shape, and the targeted lint compile
emits no `ClassTemplate.java` `this-escape` diagnostic.

### `Container` Helper And Registry Publication

`Container` had three constructor-time owner escapes:

- `new ConstHeap(this)` stored the owner in the heap while the concrete
  container subclass was still under construction.
- `new NativeTemplates(this)` built the owner-local lookup table from a field
  initializer, before the container constructor had returned.
- `Runtime.registerContainer(this)` published child containers into the
  diagnostic weak registry from the base constructor. Because `registerContainer`
  is virtual, a runtime subclass or concurrent diagnostic path could observe the
  container before subclass fields were assigned.

The replacement keeps the same observable runtime model:

- `ConstHeap` remains a final heap object on each `Container`, but it no longer
  stores an owner field. `getConstHandle(...)`, `saveConstHandle(...)`, and
  `relocateConst(...)` now receive the owner explicitly. The same
  `ConcurrentHashMap` cache remains on the same heap object.
- `Container.getConstHeap()` is the public accessor, and `f_heap` is private.
  Branch-touched call sites use a local `var heap = container.getConstHeap()`
  for get/save pairs so the same owner-local cache is visible.
- `NativeTemplates` remains one lookup table per container. It is now reached
  through a final `Lazy.Owner<Container, NativeTemplates>`, so the table is
  constructed after the owner is fully built and only if it is used.
- `MainContainer.create(...)` and `NestedContainer.create(...)` register the
  completed container immediately after `new ...` returns. Native containers
  were not registered by the old base-constructor rule and remain unregistered
  by default.

This does not drop caching or add hot-path recomputation. Constant handles are
still cached in the container heap; empty reflection arrays still check that
heap before constructing immutable arrays; jump constant relocation still saves
through the executing container's heap. The native-template table is deferred
instead of eager, which can reduce cold startup footprint; warm containers still
have exactly one table.

`RuntimeTest.registerContainerDoesNotObservePartiallyConstructedContainer()`
uses an observing `Runtime` to prove registry observation happens after
subclass field assignment. `RuntimeThisEscapeConstructionTest` prevents the
old constructor expressions from returning. `OwnershipDiagnosticsTest` verifies
that dumps expose runtime registry membership, explicit-owner heap state, and
computed/deferred `NativeTemplates` state.

### Structural Hash Contracts

The full javac lint run also reported four `overrides` diagnostics:

- `VersionTree` overrides `equals(...)` without `hashCode()`.
- `Register` overrides `equals(...)` without `hashCode()`.
- `Register.ShadowRegister` overrides `equals(...)` without `hashCode()`.
- `ChildInfo` overrides `equals(...)` without `hashCode()`.

This is not style-only. `HashMap` and `HashSet` choose a bucket from
`hashCode()` before they call `equals(...)`. Two equal objects with different
identity hashes can be stored in different buckets, so `contains`, `get`,
`remove`, and deduplication can fail even in a single-threaded run. Parallel
same-JVM compile/runtime work makes that failure harder to diagnose because
request-local caches and metadata graphs can be rebuilt in different orders,
but concurrency is not required for the contract violation.

The branch implements `hashCode()` from exactly the same fields used by
`equals(...)`:

| Type | Equality meaning | Hash fix | Why it is recomputed |
| --- | --- | --- | --- |
| `VersionTree` | Ordered version keys plus associated values | Iterate the versions in the same order and include each value. | The tree is mutable, so a cached hash would go stale after `put`, `remove`, or `clear`. |
| `Register` | Argument index, read-only/final flags, in-place flag, and type | Use a small allocation-free helper over those fields. | Registers mutate during compiler allocation/type-narrowing; a lazy hash would corrupt maps if the register changes after first hash. |
| `ShadowRegister` | Original register plus narrowed type | Hash the original register and narrowed type. | Shadow registers are compiler metadata, not immutable value snapshots. |
| `ChildInfo` | Child component, access, and identity aliases | Hash the same child/access/id-set triple. | `ChildInfo` is assembled and copied between `TypeInfo` owners; recomputation avoids stale owner/layering assumptions. |

This deliberately does not use `Lazy`. A lazy cached hash is correct only when
the object has a proven immutable or frozen lifecycle before it becomes a hash
key. These four types are mutable assembly/compiler metadata, so the correct
minimal fix is recomputation from the structural equality fields.

The focused tests are `VersionTest.testVersionTreeHashMatchesEquality()`,
`RegisterHashCodeTest`, and
`TypeInfoMemberOwnershipTest.childInfoAdoptionCreatesOwnerLocalCopy()`. The
targeted lint compile emits no remaining `overrides` diagnostics.

### `Constant` Cached Hash Publication

`Constant.hashCode()` already had a special cache that looked especially bad:
`m_iHash == 0` meant "not cached yet", unresolved constants refused to cache,
and a real computed zero hash was rewritten to the magic non-zero value
`7654211`.

The likely intent was performance and correctness for interned constants:

- constant comparison and map/set lookup are hot;
- many constants are immutable once fully resolved;
- unresolved constants can still change what `equals(...)` observes, so caching
  before resolution would poison hash tables;
- Java `hashCode()` is allowed to return zero, so zero could not also be the
  stored "cached zero" value.

The branch keeps the compatible cache but makes its contract explicit:

- `HASH_UNCACHED` names the zero sentinel.
- `HASH_ZERO` names the non-zero stand-in for a real computed zero hash.
- `m_iHash` is now `volatile`, so a racing reader either recomputes the same
  resolved value or observes the cached value after the writer's resolution
  check.

This is a low-footprint compatibility fix. It does not add an
`AtomicInteger`, an object `Lazy`, or an `Integer` wrapper to every constant.
Those alternatives would add per-constant footprint and indirection without
solving the important invariant. The proper long-term architecture is stronger:
resolution/adoption should produce immutable or explicitly frozen constant
snapshots, and only those frozen constants should cache a final or safely
published hash. Until that larger change exists, unresolved constants must keep
returning an uncached recomputed hash and adoption must clear runtime/helper
state that is not part of the logical constant value.

`ConstantHashCodeCacheTest.cachedHashIsVolatile()` guards the publication
contract. The adoption tests remain the behavioral proof that cloned/adopted
constants do not carry owner-local runtime/helper state into a new pool.

### Runtime-Executed `Op` Frame-Constant Caches

This branch removes owner-bearing frame-constant caches from runtime-executed
op-code objects:

- `JumpCond` and `JumpNCond` no longer store a `ConditionalConstant` in
  `m_cond`. They resolve the condition from the current `Frame` on each
  execution.
- `OpTest.calculateCommonType(...)` and
  `OpCondJump.calculateCommonType(...)` no longer assign
  `frame.getConstant(m_nType, TypeConstant.class)` back to `m_typeCommon`.

The old pattern was unsafe if a decoded method/op graph is reused across
containers or constant pools: the first execution could write an owner-specific
constant into the shared op, and later execution could read that first owner's
constant. The replacement is deliberately small. `Frame.getConstant(...)` is an
indexed local-constant array lookup plus the requested `Class.cast(...)`; the
expensive type resolution and condition evaluation remain exactly where they
were and continue to use the current frame/container owner. The old cache did
not memoize `frame.resolveType(...)` or condition evaluation results, so this
does not remove a hot semantic cache. It removes only an owner-bearing shortcut
that saved that local constant lookup after the first execution. If profiling
ever shows that lookup to matter, the safe replacement is an owner-keyed cache,
not a plain field on the shared op. `m_typeCommon` remains for assembly-time
source ops and is encoded to `m_nType` before runtime execution.

`javatools/src/test/java/org/xvm/asm/OpRuntimeCacheTest.java` verifies that the
condition fields are gone and guards against reintroducing the old common-type
write-back pattern.

The same fix category now covers `JumpVal`, `JumpIsA`, and `JumpVal_N`. Those
ops used to build first-execution switch tables from `Frame` state and store
them directly on the decoded op object:

- `JumpVal` stored case `ObjectHandle` values, a jump map keyed by those
  handles, range metadata, the selected comparison algorithm, and the
  frame-derived condition `TypeConstant`.
- `JumpIsA` read `JumpVal`'s cached case-handle array directly.
- `JumpVal_N` stored row/column case handles, per-column type constants, small
  jump maps, wildcard masks, range lists, per-column algorithms, and the global
  switch algorithm.

That was not decoded bytecode metadata. It was owner-bearing runtime data
created through one `Frame`/`Container`. Synchronizing the build made only the
first field update atomic; it still let the first executing owner install
handles and type constants that later containers could reuse.

The replacement keeps the same switch-table representation and cache-hit
behavior, but moves publication to `Container.f_mapRuntimeOpCache` behind
typed `getRuntimeOpCache(...)` and `putRuntimeOpCacheIfAbsent(...)` helpers.
The runtime key is the shared decoded op identity plus an op-local cache
category, under the executing container. The hot path still uses a cached
`ObjectHandle` map/array table after first execution in that container. The
only intentional footprint change is that two containers now build two switch
tables, which is the safe equivalent because their handles and type constants
are owner-scoped. In one-container execution the table contents and matching
logic are the same shape as before, with one container cache entry replacing
the old op fields.

`OpRuntimeCacheTest.switchOpsDoNotCacheOwnerValuesOnDecodedOps()` fails on
master because the owner-bearing switch-table fields still exist on
`JumpVal`/`JumpVal_N`, and passes here because decoded ops no longer carry
those values. `RuntimeTest.runtimeOpCacheIsContainerLocalAndTyped()` proves the
new cache API itself is owner-local: a second put in the same container reuses
the first value, a different container gets a separate value for the same op,
and a wrong type token fails at the cache boundary.

`JumpNFirst` is a separate decoded-op state case. It is not owner-bearing
cache data; it implements `assert:once`, whose compiler and opcode definitions
say the assertion is evaluated only the first time execution reaches that
instruction. Moving this state to a container cache would make the assertion
run once per container and would change behavior. The old implementation still
used a plain transient boolean, so two fibers racing the first execution could
both fall through or could observe stale state under the Java memory model. The
fix keeps the same decoded-op owner key but changes the field to a final
`AtomicBoolean` and uses `compareAndSet(false, true)`. That preserves
single-threaded behavior, keeps the same one-cell footprint, and makes
parallel first execution exact.

`OpRuntimeCacheTest.jumpNFirstUsesAtomicDecodedOpState()` verifies the atomic
field shape and sequential fall-through/skip behavior.
`OpRuntimeCacheTest.jumpNFirstConcurrentFirstExecutionHasOneWinner()` runs
parallel callers against the same decoded op and proves exactly one caller sees
the first-execution path.

`JumpNSample` is another decoded-op state case, but unlike `JumpNFirst` it was
not legitimate per-op state. It implements `assert:rnd`; the compiler validates
the sample interval expression as a runtime constant, but the opcode still
receives the interval as a `JavaLong` handle from the current `Frame`. The old
`m_nEvery` field cached the first clamped runtime operand on the decoded op. If
one invocation reached the op first with interval `1`, later invocations using
the same decoded op with interval `100` could still sample every time because
the first value had won. That is wrong even in a single container; parallel
execution simply makes the winning interval nondeterministic.

The fix removes `m_nEvery` and derives the clamped interval from the current
handle each time `completeUnaryOp(...)` runs. This keeps the old verifier-facing
behavior for legal runtime constants, keeps the old clamping of illegal values
until the verifier rejects them, and removes only a one-cast/one-clamp shortcut
that could never be safely shared. There is no lost hot cache: the old field
cached an execution operand, not a decoded bytecode structure or owner-local
table. `OpRuntimeCacheTest.jumpNSampleDoesNotCacheRuntimeOperandOnDecodedOp()`
fails on master because `m_nEvery` exists and passes here.

`MethodStructure.Code` and guard descriptors are the final op-cache hardening
piece in this branch. The address/link fields themselves are method-shape
metadata, not owner-bearing handles or constants, but master still had two
unsafe publication shapes:

- `MethodStructure.m_code` was a plain transient field. Parallel first
  `getOps()` could race duplicate `Code` construction and publish decoded op
  link state without a Java memory-model happens-before edge.
- `GuardStart.process(...)` and `GuardAll.process(...)` lazily wrote
  `m_guard` on the shared decoded op the first time a frame entered the guard.

The replacement keeps the same hot-path behavior without first-frame
publication:

- `m_code` is volatile and `ensureCode()` synchronizes first construction, so
  the decoded op graph is safely published.
- decoded `Code` runs address/scope/link simulation during construction,
  compiler-owned `Code` runs it through the existing `prepareOps()`/assembly
  path, and the volatile runtime-linked flag publishes those link writes.
  `xvm.asm.validateRuntimeCode=true` makes `MethodStructure.getOps()` assert
  that branch, switch, loop, and guard metadata is ready. `getAssembledOps()`
  intentionally remains an accessor, not a hidden linker for mutable assembly
  code.
- `Code.cloneOnto(...)` copies the runtime-linked flag with the shared op array,
  so cloned method bodies do not relink already linked decoded ops during
  connector/module loading.
- `GuardStart` and `GuardAll` build guard descriptors during address
  resolution. `process()` only reads the cached descriptor for linked code. The
  fallback for older compiler-owned paths allocates a descriptor local to that
  execution and does not write it back to the shared op.

This does not claim that `MethodStructure.Code` is now the ideal runtime
representation. The long-term cleanup is still to split mutable compiler and
assembly code from an immutable runtime `ResolvedCode` snapshot. The branch
does close the concrete JMM first-publication hole and removes the runtime
guard write. `OpRuntimeCacheTest.methodCodeIsSafelyPublishedAndLinkedBeforeRuntimeAccess()`
fails on master because `m_code` is not volatile, and
`OpRuntimeCacheTest.guardDescriptorsAreLinkedBeforeProcessAndNotPublishedByProcess()`
fails on master because `process()` writes `m_guard`.

### ASM `Op` Constructor Shape Dispatch

This branch also removes the remaining `Op*.java` constructor-time virtual
dispatch warnings. This is a different problem from the runtime-executed cache
fix above: no container-owned value was cached here. The bug shape was that
base opcode constructors asked subclass-overridable methods what byte-stream
shape to read before the subclass constructor had completed.

The fixed sites were:

- `Op.ConstantRegistry`: constructor parameter registers are now initialized
  through private helpers, not the public `init(RegisterAST[])` and
  `register(RegisterAST)` resolver callbacks.
- `OpGeneral`: deserialization receives explicit unary/binary metadata instead
  of calling `isBinaryOp()`.
- `OpCondJump` and `OpTest`: deserialization receives an explicit
  `CondJumpShape` or `TestShape` for unary, second-argument, and binary forms.
- `OpInPlace`, `OpIndex`, `OpPropInPlace`, and `OpVar`: deserialization
  receives explicit assigning/type-aware metadata instead of calling
  `isAssignOp()` or `isTypeAware()`.

The old code usually worked because the overrides were static opcode facts, not
fields initialized by the subclass constructor. That was still a brittle Java
construction pattern: a later subclass could read subclass state from one of
those predicates, and deserialization would then parse the wrong operand layout
while the object was only partially built.

The replacement keeps semantics and performance stable:

- public/source constructors keep their old arity-based APIs;
- the explicit shape values are constructor-only parameters, not per-op runtime
  fields;
- existing virtual methods such as `isBinaryOp()`, `hasSecondArgument()`,
  `isAssignOp()`, and `isTypeAware()` remain available after construction for
  formatting, register analysis, runtime behavior, and JIT paths;
- packed operand decoding populates the same fields as master.

`OpRuntimeCacheTest.opcodeShapeConstructorsPreserveDecodedOperandLayouts()`
decodes representative unary, binary, second-argument, assigning,
non-assigning, and type-aware opcodes and verifies their fields. The companion
`opcodeShapeCleanupDoesNotAddHotShapeFields()` test verifies that this cleanup
did not add per-op shape/cache fields on the hot runtime objects.

### Utility Constructor Helper Dispatch

This branch removes the smaller `javatools_utils` constructor-dispatch warning
cluster:

- `PackedInteger(long)`, `PackedInteger(BigInteger)`, and
  `PackedInteger(DataInput)` no longer call public mutable APIs from
  constructors. They use private `initLong(...)`, `initBigInteger(...)`, and
  `readObjectInternal(...)` helpers. The public mutators still enforce the old
  initialized/uninitialized checks and delegate to the same helpers after
  construction.
- `HasherReference` construction now assigns through private `assign(...)`
  instead of protected `reset(...)`. `reset(...)` remains available for
  post-construction reuse, including `TransientHasherReference`.
- `ListSet(Collection)` no longer calls `addAll(...)`, which dispatches through
  public `add(...)`. It populates through private helpers and uses
  `sizeInternal()` on the insertion path. A lambda was intentionally avoided
  here because capturing `this` in the constructor reintroduces the lint
  warning.

These fixes are not runtime owner-cache changes. They are straightforward Java
construction hygiene: no subclass should be able to run user code before the
base object is initialized. Semantics and performance remain the same for
normal callers. `PackedInteger` still stores the same long/BigInteger state and
uses the same packed stream format; `HasherReference` still hashes through the
same hasher; `ListSet` still de-duplicates and builds its hash index at the
same threshold. Construction is slightly more direct because it bypasses public
dispatch.

`UtilityConstructorEscapeTest` verifies the behavior by constructing subclasses
whose overrides throw if invoked during construction. It also checks the
resulting values and `ListSet` duplicate handling.

### MethodInfo And PropertyInfo Body Owner Factories

This branch also removes the ASM metadata constructor escape in
`MethodInfo`/`MethodBody` and `PropertyInfo`/`PropertyBody`.

The old constructors attached body owner links while the owner was still under
construction:

```java
aOwned[i] = body.forMethod(this);
aOwned[i] = body.forProperty(this);
```

Those calls are not harmless bookkeeping. The `forMethod(...)` and
`forProperty(...)` paths mutate or copy body objects so each body points back to
its containing metadata owner. Calling them from the owner constructor allowed
the body path to observe a `MethodInfo` or `PropertyInfo` before its final body
array, rank, and other fields had been assigned.

The replacement uses static factories:

- `MethodInfo.create(...)` and `PropertyInfo.create(...)` are the only public
  construction entry points;
- private constructors build owned body copies through package-private
  non-virtual `MethodBody(MethodInfo, MethodBody)` and
  `PropertyBody(PropertyInfo, PropertyBody)` copy constructors;
- the owned copies are filled into a local array, and the final owner body
  array is assigned only after that array is complete.

This preserves behavior and cache shape. Callers still get a metadata owner
whose body array contains owner-linked bodies, and `TypeInfoReal` still creates
owner-local copies when the same method/property info is reused across type
owners. The caller-supplied body object is no longer mutated as a construction
side effect; the returned owner contains an equivalent owned copy instead. That
keeps the retained graph size the same as before for normal use, while avoiding
the unsafe construction callback.

The branch deliberately keeps the owner body arrays final. A broader
volatile-publication rewrite for body arrays or body back-pointers was
considered but not kept in this PR because it would change the state model
without a failing stress proof; the final implementation removes the
constructor escape while preserving the old final-field structure.

`MethodInfoTest.methodInfoFactoryDoesNotCallOverridableBodyAttachment()` and
`TypeInfoMemberOwnershipTest.propertyInfoFactoryDoesNotCallOverridableBodyAttachment()`
prove the timing fix with body subclasses that fail if the old overridable
owner-attachment path runs during construction. Those tests would fail against
master. The existing
`TypeInfoReal.validate()` ownership checks continue to cover real type-info
graphs during unit tests and stress runs.

### Cycle-Safe MethodBody Equality

The parallel connector test exposed a separate metadata bug in the same
`MethodInfo`/`MethodBody` area. The old `MethodBody.hashCode()` used only the
method identity, while `MethodBody.equals(...)` compared `m_target` with
`Handy.equals(...)`. For `FromInto`, `Implicit`, and `Union` bodies, that target
is not a simple value: it is another `MethodInfo` or an array of `MethodInfo`
legs. Those method infos can legitimately point back into the same method-body
graph.

That was bad even without parallelism. A single-threaded `HashMap` or `HashSet`
lookup could place many equal-identity bodies in one hash bucket and then enter
recursive equality:

```text
MethodInfo.equals(...)
  -> Arrays.equals(MethodBody[])
  -> MethodBody.equals(...)
  -> Handy.equals(m_target)
  -> MethodInfo.equals(...)
```

Parallel connector loading made the problem reliable because independent
containers realize similar type-info graphs at the same time. The lookup that
should have compared stable method shape instead expanded through cyclic owner
metadata until it hit `StackOverflowError`.

The fix does not remove the metadata links. `FromInto`, `Implicit`, and `Union`
bodies still carry the same target method information used by the
compiler/runtime for dispatch, narrowing, and implicit-body replacement. The
change is only in object equality and hashing:

- constants and property targets keep their existing value equality;
- `FromInto` and `Implicit` targets compare by stable method target shape:
  rank, identity, and signature;
- `Union` targets compare those stable shapes for both legs;
- `hashCode()` now uses the same non-recursive target identity, so hash
  collections do not funnel equal-id bodies into expensive recursive
  comparisons.

This preserves the old apparent behavior for logically equal method bodies:
two bodies with the same implementation, method id, signature, and target
method shape still compare equal. It removes only the accidental graph-walk
through owner metadata, which was not a useful semantic distinction and could
never be made safe for cyclic method-target graphs.

`MethodInfoTest.methodInfoEqualityDoesNotRecurseThroughMethodTargets()` builds
the cycle directly for both `FromInto` and `Implicit` targets and proves both
equality and hash consistency. On master, the same shape recurses through
`MethodInfo.equals(...)` and fails with `StackOverflowError`. The broader
`InterpreterConnectorTest.parallelConnectorsLoadIndependentNativeContainers()`
also passes after this fix; before it, parallel connector loading failed in the
same `MethodInfo.equals(...)` / `MethodBody.equals(...)` / `Handy.equals(...)`
cycle.

### Safely Published Method And Property Runtime Chains

This branch also fixes the optimized method/property chain caches in
`MethodInfo` and `PropertyInfo`. These caches are runtime metadata, not pure
decoded source data. On master, `MethodInfo.m_aBodyResolved` and
`PropertyInfo.m_chainGet/m_chainSet` were plain lazy fields:

- method-chain optimization can mark bodies native, attach generated delegation
  `MethodStructure` objects, and replace a body with a native wrapper;
- property accessor-chain construction can create field-access bodies and
  generated delegation chains;
- property getter/setter cache methods accept a nullable `idNested`, but the
  old implementation had only one unkeyed cache slot per accessor.

The old comments treated the method cache as idempotent. That was not a safe
publication proof. Even if two threads computed logically equal arrays, there
was no happens-before edge for the array reference or for the body mutations
performed while assembling it. The property caches had the additional semantic
hazard that a non-null nested-id access could populate the same slot later used
by top-level property access.

The fix keeps the hot-path behavior and footprint of master:

- each owned `MethodInfo` still has at most one cached optimized chain;
- each owned `PropertyInfo` still has at most one cached top-level getter chain
  and one cached top-level setter chain;
- the steady-state read is one volatile field read and no monitor entry;
- the first top-level build is synchronized and publishes the completed array
  through a volatile field;
- capped method-chain redirection happens outside the monitor because capped
  chains can recurse into another `MethodInfo` cache;
- non-null nested property ids are computed separately and deliberately do not
  populate the unkeyed top-level cache.

This does not remove a working cache or add broad map footprint. The only cache
behavior change is the one that was required for correctness: nested property
requests can no longer poison the top-level get/set cache. Current runtime call
sites use the top-level path. If a future runtime path proves that non-null
nested ids are hot, the correct follow-up is a keyed owner-local cache whose key
contains `idNested`, not reuse of the top-level slot.

`MethodInfoTest.optimizedMethodChainCacheIsSafelyPublishedInParallel()` proves
that `m_aBodyResolved` is volatile and that parallel first access publishes one
fully built optimized chain.
`TypeInfoMemberOwnershipTest.optimizedPropertyAccessorChainsAreSafelyPublishedInParallel()`
does the same for `m_chainGet` and `m_chainSet`, while checking that the
generated field-access body shape is preserved.

### Safely Published TypeInfoReal Derived Caches

This branch also hardens the derived metadata caches in `TypeInfoReal`:

- `m_mapPropertiesByName`;
- `m_mapMethodsBySignature`;
- `m_delegates`;
- `m_fCacheReady`;
- `m_fChildrenChecked`.

These fields are owned by one `TypeInfoReal`, but they are runtime-reachable
through reflection, type unions/intersections/differences, method lookup,
conversion lookup, `xRTType.isNewable(...)`, and other same-JVM metadata paths.
On master, the two map caches built mutable `HashMap` instances and assigned
them to plain fields. That was not safe publication: another thread could see
the map reference without a happens-before edge for the map's internal table.
The two maps do not have identical semantics. The property-name map is a
read-only index once built. The method-signature map is an expanding lookup
cache: `getMethodBySignature(...)` adds substitutable/runtime signature
matches to it with `putIfAbsent(...)`.

`asDelegates()` had the same publication problem for a larger object graph. It
can construct a delegate-only `TypeInfoReal` that owns copied method/property/
child metadata. Publishing that through a plain field made the first thread's
partially visible owner graph available to other lookups. The replacement
synchronizes first construction and publishes the completed delegate view
through a volatile field. It preserves the old caching rule: complete
`TypeInfoReal` instances cache the delegate view, while incomplete type-info
objects still rebuild instead of caching.

`ensureCaches()` already attempted double-checked locking for the abstractness
cache, but `m_fCacheReady` was not volatile. That is a Java memory-model bug:
a reader could observe the ready flag without being guaranteed to observe the
preceding `m_fImplicitAbstract` write. The branch makes the ready flag volatile
so the existing lock/fast-path pattern has a real publication edge.

`m_fChildrenChecked` now follows the same rule. The old code cached only a
successful virtual-child newability check; if a child check failed, it returned
`false` and retried on later calls. This branch preserves that behavior. The
successful check is synchronized and then published through a volatile flag, so
later readers can skip it safely. The full virtual-child path still belongs in
runtime/late-registration stress because fake unit `TypeInfoReal` objects are
not registered in the pool and cannot safely drive `ensureVirtualChildTypeInfo`
without tripping unrelated placeholder assumptions.

The performance shape is the same or better:

- one name-index map per `TypeInfoReal`;
- one expanding signature-index cache per `TypeInfoReal`;
- one cached delegate view per complete `TypeInfoReal`;
- one abstractness readiness bit and one successful child-newability bit;
- no added per-entry wrapper objects or owner maps;
- steady-state reads are volatile reads plus either immutable property-map
  lookups or synchronized signature-cache lookups.

The intentional semantic tightening is limited to the property-name lookup
map. Callers now receive an immutable property-name snapshot because no
production caller mutates that index; mutation would corrupt owner metadata.
The method-signature cache deliberately remains mutable and is now a
synchronized `HashMap` wrapper. That preserves master behavior and selection
shape: first exact signatures are preloaded, and later substitutable/runtime
signature hits are cached under the same owner without racing a plain
`HashMap`.

This distinction was found by verification. A short
`manualTests:runDirectSequenceStress` attempt failed during prerequisite
`lib-ecstasy` compilation with `UnsupportedOperationException` from
`TypeInfoReal.getMethodBySignature(...)` when the earlier branch draft returned
an immutable method-signature map. The final implementation keeps the safe
volatile first publication but returns a synchronized expanding cache for
`m_mapMethodsBySignature`. The same stress command then reaches a separate
`lib-json` accessibility diagnostic that predates this cache wave; that blocker
is tracked in
[stress-discovered-runtime-issues.md#typeinforeal-method-signature-cache-mutability](stress-discovered-runtime-issues.md#typeinforeal-method-signature-cache-mutability).

`TypeInfoMemberOwnershipTest.derivedTypeInfoCachesAreSafelyPublishedInParallel()`
checks the volatile source shape, runs parallel first access to the name map,
signature map, delegate view, and abstractness cache, verifies that every
thread observes the same cache identities, asserts that the property-name map
is immutable, and asserts that the method-signature cache accepts same-key
`putIfAbsent(...)` through the synchronized mutable map.

### Safely Published PropertyInfo Helper Caches

This branch also closes the remaining `PropertyInfo` helper-cache cells:

- `m_annotations`;
- `m_FInjected`;
- `m_FImplicitlyAssigned`;
- `m_typeBaseRef`;
- `m_idGetter`;
- `m_idSetter`.

These fields looked like ordinary local memoization on master, but they are
runtime metadata owned by the containing `TypeInfo` and its `ConstantPool`.
That matters because the first build is not always a pure Java calculation:
`getBaseRefType()` can intern the property's Ref/Var type in the owner pool,
and `getGetterId()`/`getSetterId()` intern owner-pool method constants. Plain
lazy writes had no happens-before edge for either the cache reference or the
constant-pool side effects that produced it.

`getRefAnnotations()` had a separate API hazard. The public return type is
`Annotation[]`, so callers still receive the cached array object. Returning a
fresh clone on every call would be a broader semantic/performance change:
master cached one array per property, and annotation metadata can be read from
field-layout and reflection paths. The replacement therefore keeps the old
cached-array behavior, but the first source annotation array is copied into a
detached snapshot and that snapshot is published once through a volatile field.
The remaining array mutability is tracked by the broader array/API backlog; it
is not silently made worse here.

The fix uses the same small publication mechanism for all six helpers:

- read the volatile field on the hot path;
- synchronize only first construction;
- publish exactly one completed value through the volatile field;
- keep the owner/key/invalidation model as one helper value per owned
  `PropertyInfo`, invalidated only by rebuilding the containing `TypeInfo`.

Behavior and performance are preserved:

- no helper cache is removed;
- no per-call annotation-array clone is added;
- getter/setter/base-ref constants are still interned in the same owner pool;
- after warmup, each accessor is a volatile read plus the same returned value
  identity master intended to cache;
- under a same-owner race, losing threads reuse the winner's completed cache
  value rather than duplicating owner-pool work or observing a partially
  visible helper.

`TypeInfoMemberOwnershipTest.propertyHelperCachesAreSafelyPublishedInParallel()`
guards the field shape and behavior. It checks all six fields are volatile,
races eight first-access callers, verifies each caller observes the same cached
annotation array, base-ref type, injected/implicitly-assigned booleans, getter
id, and setter id, and proves the getter/setter/base-ref values belong to the
owner pool.

### ASM Metadata Owner Assembly

This branch also removes the remaining ASM metadata constructor escapes in
`FileStructure`, `ClassStructure.SimpleTypeResolver`, `MethodStructure`,
`PropertyStructure`, `VersionTree`, `PropertyConstant`, and `TypeInfoReal`.

The old patterns were a mix of constructor-time virtual hooks and owner
mutation:

- `MethodStructure` called `setConditionalReturn(...)` from its constructor;
- `PropertyStructure` called `setVarAccess(...)` and `setType(...)`;
- `VersionTree` called the public `clear()` hook;
- `PropertyConstant` called the protected `checkParent(...)` hook, which
  `FormalTypeChildConstant` overrides;
- `TypeInfoReal` remained subclassable because `ConstantPool.infoPlaceholder()`
  created an anonymous placeholder subclass;
- `MethodInfo.forType(...)`, `PropertyInfo.forType(...)`, and
  `ChildInfo.forType(...)` mutated unowned source metadata to attach the first
  `TypeInfoReal` owner.

The first group is unsafe because subclasses can observe default fields before
construction has completed. The last group is the real parallel-owner failure:
two same-JVM type-info builds using the same unowned source metadata could let
one owner claim the shared source object. Any later reuse of that source
metadata then carried the wrong owner.

The fixes are deliberately small:

- root/owner assembler types that should not be externally specialized are
  final (`FileStructure`, `ClassStructure.SimpleTypeResolver`, and
  `TypeInfoReal`);
- constructor behavior is expressed as static/private validation or direct
  field initialization, preserving the old resulting flags, property type,
  var-access value, version-tree empty state, and parent-validation rules;
- `ConstantPool.infoPlaceholder()` still returns a cached placeholder, and
  `TypeInfoReal.toString()` preserves the legacy `"Placeholder"` output;
- `forType(...)` on method, property, and child metadata returns the same
  object only for the same owner and otherwise creates an owner-local copy.

This preserves the runtime and compiler cache shape. Each realized `TypeInfo`
still has one owned method/property/child metadata graph, and the constant-pool
placeholder remains one cached object per pool. The only extra allocation is an
owner-local construction copy when caller-provided source metadata is reused
across owners; that allocation replaces the old unsafe owner-stealing side
effect and is not retained in addition to the owned graph.

`AsmConstructorEscapeTest` covers the constructor-equivalence details, including
the root envelope, conditional-return flag, property type/var access, cached
placeholder string, and `VersionTree` construction. It also contains
hook-detecting subclasses that prove the changed constructors do not call
overridden `setConditionalReturn(...)`, `setVarAccess(...)`, `setType(...)`, or
`checkParent(...)` before subclass construction completes, while the same hooks
remain callable after construction. The parallel tests
`MethodInfoTest.typeInfoConstructionCopiesMethodInfoInParallel()` and
`TypeInfoMemberOwnershipTest.typeInfoConstructionCopiesPropertyAndChildInfoInParallel()`
prove that shared source metadata remains unowned while concurrent
`TypeInfoReal` owners receive distinct correctly back-linked copies. The
targeted lint run at `/tmp/xvm-asm-this-escape-wave.log` reports zero warnings
for the fixed ASM group.

### ModuleInfo ResourceDir Constructor Escape

The tooling `ModuleInfo` constructor also had a single `this-escape` warning in
the explicit resource path branch. It called the public `getResourceDir()`
accessor while the constructor was still merging source, binary, and resource
path state. A subclass override could therefore observe the object before those
fields were fully assembled.

The fix keeps the same cache and lookup behavior but routes constructor-time
resource-dir discovery through a private `ensureResourceDir()` helper. Public
callers still use `getResourceDir()`, and explicit resource paths still take
priority over the discovered defaults.

`ModuleInfoTest.constructorWithExplicitResourcesDoesNotCallOverridableResourceDir()`
uses a subclass override that would throw if the old constructor called
`getResourceDir()` before subclass construction completed. It also verifies
that the override remains callable after construction and that the explicit
resource path is still present. The targeted lint run at
`/tmp/xvm-moduleinfo-this-escape.log` reports zero `ModuleInfo.java`
`this-escape` diagnostics.

### Compiler, Lexer, and Parser Constructor Escapes

The compiler-side wave removes the remaining `javatools/src/main/java/org/xvm/compiler/**`
`this-escape` diagnostics. These were not runtime template globals, but they
still mattered for same-JVM and incremental compiler work: lexer/parser hooks,
AST parent links, component links, stage state, and validation metadata could
be published while the concrete node was still under construction.

The fix keeps the old semantics while separating construction from publication:

- `Lexer` preserves initial whitespace priming, but uses private static source
  helpers instead of constructor-time `eatWhitespace()` dispatch.
- `Parser` preserves the primed token-stream behavior lazily; `mark()` and
  `restore()` now save the primed flag so speculative parsing remains
  equivalent.
- Synthetic expression and statement nodes use private constructors plus
  factories that attach parent/component/type state after the object is fully
  initialized.
- `ConvertExpression` preserves the old constant-folding fallback. If
  `Constant.convertTo(...)` throws `ArithmeticException`, the expression is
  not folded into a compile-time constant while `m_aidConv` still emits the
  runtime conversion. The old stderr soft-assert and multi-value partial-null
  behavior are deliberately left in place for behavior preservation, but both
  conversion-folding sites now carry TODOs to replace that path with structured
  compiler logging/diagnostics.

`CompilerThisEscapeConstructionTest` uses hook-detecting `Lexer` and `Parser`
subclasses that would fail if the old constructors dispatched to overrides
before subclass construction completed. It also guards the factory source shape
for synthetic AST nodes. The forced lint run at
`/tmp/xvm-compiler-this-escape.log` reports zero `Lexer.java`, `Parser.java`,
or `compiler/ast` `this-escape` diagnostics.

## Manual Lazy Cache Hardening

This branch also removes two concrete lazy-null cache hazards found by the
runtime/ASM manual-lazy audit:

- `xRegEx.RegExHandle` now stores its compiled `Pattern` in a final
  `Lazy<Pattern>` instead of `m_pattern`. This preserves the old first-use
  compilation and per-handle repeated-call caching, but publishes the cached
  value through the `Lazy` synchronization rather than a plain nullable field.
- `FSNodeConstant.m_constPath` is now a volatile per-node cache and adopted
  copies clear it. The old cache could be correct inside one pool and still be
  wrong after `Constant.adoptedBy(...)`, because the shallow clone copied the
  already-computed source-pool path literal.
- `_native:fs.OSFileNode.created` is no longer `@Lazy`. Native file nodes are
  owned by the native `OSStorage` service, but the getter can execute in an
  application container. The old lazy property cached an application-owned
  `Time` handle inside the native file-system graph.

`RegExHandleTest` verifies the regex cache remains per-handle and final-lazy.
`ConstantAdoptionTest.adoptedFSNodeConstantDropsSourcePoolPathCache()` verifies
that adoption preserves caching in the destination pool without reusing the
source-pool path constant. `manualTests:runDirectSequenceStress` with
`TestFiles` verifies the native file-node change under ownership validation.

### Constructor-Published Native Template `INSTANCE`

These master sites assigned `INSTANCE = this` from constructors and now resolve
through an owner-scoped template cache. Most externally used templates resolve
through the central `NativeTemplates` table. Some still expose compatibility
getters for existing call sites; resource templates are resolved directly from
`Container.nativeTemplates()` and no longer expose `INSTANCE` at all:

- `xRTDelegate.INSTANCE`
- `xRTViewFromBit.INSTANCE`
- `xRTViewFromByte.INSTANCE`
- `xRTViewToBit.INSTANCE`
- `xBitArray.INSTANCE`
- `xByteArray.INSTANCE`
- `xNibbleArray.INSTANCE`
- `xRTBitDelegate.INSTANCE`
- `xRTBooleanDelegate.INSTANCE`
- `xRTFloat64Delegate.INSTANCE`
- `xRTInt8Delegate.INSTANCE`
- `xRTInt16Delegate.INSTANCE`
- `xRTInt64Delegate.INSTANCE`
- `xRTUInt8Delegate.INSTANCE`
- `xRTNibbleDelegate.INSTANCE`
- `xRTSlicingDelegate.INSTANCE`
- `xRTViewFromBitToBoolean.INSTANCE`
- `xRTViewFromBitToByte.INSTANCE`
- `xRTViewFromBitToNibble.INSTANCE`
- `xRTViewFromByteToFloat64.INSTANCE`
- `xRTViewFromByteToInt16.INSTANCE`
- `xRTViewFromByteToInt64.INSTANCE`
- `xRTViewFromByteToInt8.INSTANCE`
- `xRTViewToBitFromNibble.INSTANCE`
- `xListMap.INSTANCE`
- `xTuple.INSTANCE`
- `xFuture.INSTANCE`
- `xAtomicIntNumber.INSTANCE`
- `xOSDirectory.INSTANCE`
- `xOSFile.INSTANCE`
- `xRawOSFileChannel.INSTANCE`
- `xRTNameService.INSTANCE`
- `xRTClassTemplate.INSTANCE`
- `xRTComponentTemplate.INSTANCE`
- `xRTMethod.INSTANCE`
- `xRTModuleTemplate.INSTANCE`
- `xRTProperty.INSTANCE`
- `xRTPropertyClassTemplate.INSTANCE`
- `xRTSignature.INSTANCE`
- `xRTFunction.INSTANCE`
- `xRTType.INSTANCE`
- `xRTTypeTemplate.INSTANCE`
- `xRTServiceControl.INSTANCE`
- `xContainerControl.INSTANCE`
- `xContainerLinker.INSTANCE`
- `xBasicHashCollector.INSTANCE`
- `xRTAlgorithms.INSTANCE`
- `xRTCertificateManager.INSTANCE`
- `xRTKeyStore.INSTANCE`
- `xTerminalConsole.INSTANCE`
- `xRTCompiler.INSTANCE`
- `xCoreRepository.INSTANCE`
- `xRTNetwork.INSTANCE`
- `xRTRandom.INSTANCE`
- `xLocalClock.INSTANCE`
- `xNanosTimer.INSTANCE`
- `xRTConnector.INSTANCE`
- `xRTServer.INSTANCE`
- `xInjector.INSTANCE`
- `xRTDecryptor.INSTANCE`
- `xRTHasher.INSTANCE`
- `xRTKeyGenerator.INSTANCE`
- `xRTSigner.INSTANCE`
- `xRTBuffer.INSTANCE`
- `xRTNetworkInterface.INSTANCE`
- `xRTSocket.INSTANCE`
- `LongBasedBitView.INSTANCE`
- `LongDelegate.INSTANCE`
- `LongLongDelegate.INSTANCE`
- `xRTCharDelegate.INSTANCE`
- `xRTInt128Delegate.INSTANCE`
- `xRTInt32Delegate.INSTANCE`
- `xRTStringDelegate.INSTANCE`
- `xRTUInt128Delegate.INSTANCE`
- `xRTUInt16Delegate.INSTANCE`
- `xRTUInt32Delegate.INSTANCE`
- `xRTUInt64Delegate.INSTANCE`
- `xRTViewToBitFromFloat64.INSTANCE`
- `xRTViewToBitFromInt128.INSTANCE`
- `xRTViewToBitFromInt16.INSTANCE`
- `xRTViewToBitFromInt32.INSTANCE`
- `xRTViewToBitFromInt64.INSTANCE`
- `xRTViewToBitFromInt8.INSTANCE`
- `xRTViewToBitFromUInt128.INSTANCE`
- `xRTViewToBitFromUInt16.INSTANCE`
- `xRTViewToBitFromUInt32.INSTANCE`
- `xRTViewToBitFromUInt64.INSTANCE`
- `xRTViewToBitFromUInt8.INSTANCE`
- `xArray.INSTANCE`
- `xString.INSTANCE`
- `xEnum.INSTANCE`
- `xConst.INSTANCE`
- `xException.INSTANCE`
- `xService.INSTANCE`
- `xModule.INSTANCE`
- `xPackage.INSTANCE`
- `xRef.INSTANCE`
- `xVar.INSTANCE`
- `xCheckedInt8.INSTANCE`
- `xCheckedUInt8.INSTANCE`
- `xCheckedInt16.INSTANCE`
- `xCheckedUInt16.INSTANCE`
- `xCheckedInt32.INSTANCE`
- `xCheckedUInt32.INSTANCE`
- `xCheckedInt64.INSTANCE`
- `xCheckedUInt64.INSTANCE`
- `xDec32.INSTANCE`
- `xDec64.INSTANCE`
- `xDec128.INSTANCE`
- `xFloat32.INSTANCE`
- `xFloat64.INSTANCE`
- `xFPLiteral.INSTANCE`

This is must-fix. The old pattern was both a constructor `this` escape and a
process-global last-writer-wins cache. The replacement keys are private to
`NativeTemplates` where named external access is needed; converted template
classes do not own an `INSTANCE` field at all. The actual template object is
resolved and cached by the active `Container`.

The new files are:

- `javatools/src/main/java/org/xvm/runtime/NativeTemplates.java`
- `javatools/src/main/java/org/xvm/runtime/NativeTemplateRef.java`

`NativeTemplates` intentionally stores a `Lazy` cell in a `ConcurrentHashMap`
and resolves the template from `Lazy.get()`. That preserves per-container
caching while avoiding template bootstrap recursion inside
`ConcurrentHashMap.computeIfAbsent`.

The checked-integer templates use an even smaller replacement. Their only
static readers were sibling signed/unsigned lookups inside instance methods
such as `CheckedInt8.magnitude`. Those peer templates are already native
templates registered and cached by the same `Container`, so
`xConstrainedInteger.getComplimentaryTemplate(String, Class<T>)` now resolves
through `f_container.getTemplate(...)`. That keeps the old
one-template-per-container behavior and removes the process-global peer pointer
without adding a second cache table.

The decimal and binary floating-point templates use the central
`NativeTemplates` table because their readers are spread across conversion
code, random number generation, and array delegates. The replacement preserves
the old cache behavior: `Dec32`, `Dec64`, `Dec128`, `Float32`, and `Float64`
are still resolved once per owner and then reused for handle creation. The only
semantic change is removing the JVM-global last-writer-wins pointer, so a
conversion in container A cannot accidentally allocate a decimal or float
handle with container B's composition. `FPLiteral` had no legitimate external
singleton readers left, so it only needed the constructor publication removed.

The primitive integer and `Char` wave removes the remaining numeric/text
template globals from the central hot handle path. `Int8`, `Int16`, `Int32`,
`Int64`, `Int128`, `IntN`, `UInt8`, `UInt16`, `UInt32`, `UInt64`, `UInt128`,
`UIntN`, `Nibble`, and `Char` are resolved through `NativeTemplates` when a
static helper needs an owner. `Int64.makeHandle(...)`, `UInt8.makeHandle(...)`,
`Nibble.makeHandle(...)`, and `Char.makeHandle(...)` now require a `Frame`,
`Container`, `ClassTemplate`, or existing owner handle, so new no-owner handle
construction fails at compile time. The old per-template caches are preserved:
`UInt8`, `Nibble`, and `Char` still prebuild their byte/nibble/ASCII handles on
the owner template during `initNative()`, and uncached values still allocate the
same Java handle type. The semantic change is only that the owner is explicit
and cannot be stolen from a process-global `INSTANCE`.

The root support wave removes `xObject.INSTANCE`, `xObject.CLASS`,
`Identity.INSTANCE`, `Identity.INCEPTION_CLASS`, and `Proxy.INSTANCE`. `Object`
and `Ref.Identity` now resolve through `NativeTemplates`, while `Proxy` is an
owner-local support object constructed by `NativeTemplates.proxy()` and touched
during `Service` registration to preserve the old eager creation point.
`Identity.ensureIdentity(...)` derives the owner from the wrapped referent, the
tuple fallback equality path uses the caller frame's `Object` template, and
opaque Java handles such as constant-pool filesystem cookies, crypto provider
objects, and HTTP exchange wrappers now receive an explicit `Container`.
This preserves the old role of representing these wrappers as `Object`, but the
composition now belongs to the runtime owner that created the wrapper instead
of whichever container last assigned the global root template.

The resource-template wave updates `NativeContainer.initResources()` to resolve
injectable resource suppliers from `Container.nativeTemplates()` instead of
public template statics. The old suppliers were registered during native
container startup but still captured whatever process-global `INSTANCE` value
was most recently assigned. The new code captures this container's template.
`xRTCertificateManager.invokeKeystoreFor(...)` now resolves `xRTKeyStore`
through the caller frame's container for the same reason. `xRTConnector` also
moves the old static agent string into a final owner field, preserving the
same user-agent value without tying it to the removed `INSTANCE` branch.

The leaf-template wave deletes remaining constructor-published statics from
native templates that had no external `X.INSTANCE` readers. Most of these
classes do not need named `NativeTemplates` accessors because ordinary
container template registration already owns them. The exception is
`xRTSocket.connect(...)`, which is a static helper and now resolves
`NativeTemplates.socket()` from the callback frame instead of reading the old
global `INSTANCE`. The important fix is removing the public process-global
field and constructor escape.

The array leaf wave removes unused or constructor-published statics from
delegate/view leaves that are already resolved by the owner container's native
template registration. It intentionally leaves array templates that still have
real static call sites from `xBitArray`, `xByteArray`, or `xNibbleArray` for a
separate owner-plumbing pass.

The unused-own-`INSTANCE` wave removes template singleton fields that had no
remaining own `X.INSTANCE` readers and no `this == INSTANCE` behavior. These
classes still use the reflected native-template constructor shape, but they no
longer publish themselves into process-global mutable fields:

- `xAtomic.INSTANCE`
- `xInject.INSTANCE`
- `BitBasedArray.INSTANCE`
- `xFloat16.INSTANCE`
- `xIntLiteral.INSTANCE`
- `xClass.INSTANCE`
- `xClassTemplate.INSTANCE`
- `xEnumValue.INSTANCE`
- `xEnumeration.INSTANCE`
- `xRegEx.INSTANCE`

`xClass.createConstHandle(...)` was also changed to resolve
`reflect.EnumValue` and `reflect.Enumeration` through the caller frame's
container. That preserves the old behavior of using the specialized templates
for enum class constants without depending on process-global singleton fields.

### Static Runtime Metadata Caches

The following master caches held runtime-owned values in JVM-global static
fields. The branch moves them to owner-scoped final lazy state.

| File | Master cache | Replacement | Priority |
| --- | --- | --- | --- |
| `xRTDelegate` | static `DELEGATES` map | final owner-local lazy map `f_delegates`; immutable `Map.copyOf` | Must fix |
| `xRTNameService` | `BYTE_ARRAY_ARRAY_TYPE`, lazy `m_typeCanonical` | `f_typeByteArrayArray`, `f_typeCanonical` | Must fix |
| `xRTClassTemplate` | class/template array types, contribution/method/annotation array types, empty parameter array, action enum, helper methods | final `Lazy` fields on the template | Must fix |
| `xRTComponentTemplate` | `COMPONENT_ARRAY_TYPE`, `MULTI_METHOD_TEMPLATE` | `f_typeComponentArray`, `f_templateMultiMethod` | Must fix |
| `xBitArray`, `xByteArray`, `xNibbleArray` | `INSTANCE`; `xByteArray` numeric array compositions | `NativeTemplates` array-template getters and `xByteArray` final lazy compositions | Must fix |
| `xRTViewFromBit`, `xRTViewFromByte` | specialized view lookup through subtype `INSTANCE` fields | owner-local final lazy dispatch maps keyed by `TypeConstant` | Must fix |
| `xRTBitDelegate`, `xRTBooleanDelegate`, `xRTFloat64Delegate`, `xRTInt8Delegate`, `xRTInt16Delegate`, `xRTInt64Delegate`, `xRTUInt8Delegate`, `xRTNibbleDelegate`, `xRTSlicingDelegate` | constructor-published delegate `INSTANCE` fields | owner-local delegate dispatch and `NativeTemplates.slicingDelegate()` | Must fix |
| `xRTViewFromBitTo*`, `xRTViewFromByteTo*`, `xRTViewToBitFromNibble` | constructor-published specialized view `INSTANCE` fields | owner-local base view dispatch or existing `xRTViewToBit` dispatch | Must fix |
| `xListMap`, `Utils`, map-literal opcodes, enum-name map construction | `xListMap.INSTANCE`, static `xListMap.CONSTRUCTOR`, static `Utils.LIST_MAP_CONSTRUCT` | `NativeTemplates.listMap()`, owner-scoped `xListMap.f_constructor`, and constructor lookup from the caller's map composition | Must fix |
| `Utils` runtime helper metadata | `CONST_HELPER`, annotation/argument/parameter templates, constructor methods, `STRING_VALUE_OF`, annotation/argument array types, and freeze/resource/inject signatures | `Container.f_runtimeMetadata`, a final owner-passed lazy `Utils.RuntimeMetadata` immutable bundle; helpers resolve metadata from `Frame` or `Container`, and `CreateParameters` captures the starting owner | Must fix |
| `xTuple`, void-return handling, async service responses | `xTuple.INSTANCE`, `xTuple.INCEPTION_CLASS`, static `xTuple.H_VOID` | `NativeTemplates.tuple()`, final owner-local inception constant, and per-container lazy `Tuple()` handle via `xTuple.ensureEmptyTuple(container)` | Must fix |
| `xFuture`, wait-frame construction, async result assignment | `xFuture.INSTANCE`, static `TYPE`, static `COMPLETION`, ownerless `makeHandle(CompletableFuture)` | `NativeTemplates.future()`, final owner-passed lazy future type and completion enum template, and `makeHandle(Container, CompletableFuture)` | Must fix |
| `xAtomic`, `xAtomicIntNumber`, `xAtomicInt128` | `xAtomicIntNumber.INSTANCE`, static `xAtomic.NUMBER_TEMPLATES`, and wrapper construction from numeric template `INSTANCE` fields | final owner-passed lazy map, immutable `Map.copyOf`, and wrapper construction from this container's number templates | Must fix |
| Native filesystem templates and CP filesystem constants | `xOSDirectory.INSTANCE`, `xOSFile.INSTANCE`, `xRawOSFileChannel.INSTANCE`, and static constructor `MethodStructure` caches on `xOSDirectory`, `xOSFile`, `xCPDirectory`, `xCPFile`, `xCPFileStore` | `NativeTemplates` filesystem getters plus final owner-scoped lazy constructor caches on the owning template | Must fix |
| Leaf static metadata caches | `xRTKeyStore.s_typeNamedPassword`, `xOSStorage.s_methodOnEvent`, `xRTCompiler.GET_MODULE_ID`, `xNanosTimer.s_clzDuration`, `xRTBuffer.PROP_RAW_BYTES`, `xClass.CLASS_ARRAY_TYPE` | final owner-local `Lazy` fields for template-owned metadata; `xClass.ensureArrayComposition(Container)` computes from the caller's `ConstantPool`, which interns the same `Array<Class>` type per owner | Must fix |
| `xRTFunction` | `LISTMAP_TYPE`, ownerless native/internal function factories, process-global finalizer no-op anchor | `f_typeListMap`, owner-required helper APIs, `FullyBoundHandle.noOp(Container)` | Must fix |
| `xRTMethod` | `EMPTY_ARRAY` | `f_constEmptyArray` | Must fix |
| `xRTMethodTemplate` | `INSTANCE`, `METHOD_TEMPLATE_COMP`, ownerless `makeHandle(MethodStructure)` | caller-owned `makeHandle(Container, MethodStructure)` and `f_compMethodTemplate` | Must fix |
| `xRTFileTemplate` | `INSTANCE`, `FILE_TEMPLATE_TYPE`, `LINK_MODULES_METHOD` | caller-owned template lookup, `f_typeFileTemplate`, `f_methodLinkModules`, and `ensureFileTemplateType(container)` | Must fix |
| `xRTModuleTemplate` | private static `LISTMAP_TYPE` | compute from caller `ConstantPool` | Must fix |
| `xRTPackageTemplate` | `INSTANCE`, `PACKAGE_TEMPLATE_TYPE` | caller-owned template lookup and `f_typePackageTemplate` | Must fix |
| `xRTProperty` | `INSTANCE`, `EMPTY_PROPERTY_ARRAY`, ownerless property handle construction | caller-owned template lookup, `f_constEmptyPropertyArray`, and `NativeTemplates.property()` | Must fix |
| `xRTPropertyTemplate` | `INSTANCE`, `PROPERTY_TEMPLATE_COMP`, `ARRAY_PROP_COMP`, ownerless property handle helpers | caller-owned helpers with `f_compPropertyTemplate` and `f_compPropertyTemplateArray` | Must fix |
| `xRTPropertyClassTemplate` | `PROPERTY_CLASS_TEMPLATE_COMP` | `f_compPropertyClassTemplate` | Must fix |
| `xRTSignature` | `INSTANCE`, return/parameter type constants, RT templates, and array compositions | caller-owned helper APIs with final lazy signature metadata | Must fix |
| `xRTType` | `TYPE_ARRAY_TYPE`, `EMPTY_TYPE_ARRAY`, `LISTMAP_TYPE`, register composition/constructor, lazy property constants | final `Lazy` fields | Must fix |
| `xRTTypeTemplate` | `TEMPLATE_ARRAY_TYPE`, `CREATE_COMPOSITION_METHOD` | `f_typeTemplateArray`, `f_methodCreateComposition` | Must fix |
| `xRTServiceControl` | static `SERVICE_STATUS`, mutable control composition cache | `f_templateServiceStatus`, `f_clzControl` | Must fix |
| `xContainerControl` | mutable control composition cache | `f_clzControl` | Must fix |
| `xContainerLinker` | static `GET_RESOURCE`, mutable linker handle cache | `f_sigGetResource`, `f_hLinker` | Must fix |
| `xArray` | array compositions, constructor IDs, helper methods, specialized-template map, delegates, empty byte array, mutability enum | `f_templateMutability`, `f_arrayTemplates`, and owner-passed `f_info` metadata | Must fix |
| `xEnum` | range template/ctor, enum name and handle lists | `f_templateRange`, `f_ctorRange`, `f_enumInfo` | Must fix for startup; see enum lifecycle note below |
| `xService` | `INCEPTION_CLASS`, `SYNCHRONICITY`, `REMAINING_TIME` | `f_constInception`, `f_templateSynchronicity`, `f_propRemainingTime` | Must fix |
| `xModule` | private static `LISTMAP_TYPE` | compute from caller `ConstantPool` | Must fix |
| `xString` | `INSTANCE`, `EMPTY_STRING`, `EMPTY_ARRAY`, `ZERO`, `ONE`, `METHOD_APPEND_TO`, and ownerless `makeHandle(...)`/array helpers | `NativeTemplates.string()`, `f_emptyString`, `f_emptyStringArray`, `f_zero`, `f_one`, `f_methodAppendTo`, and owner-required factories | Must fix |
| `xRef` | `INSTANCE`, `INCEPTION_CLASS`, `s_sigGet` | `NativeTemplates.ref()`, canonical owner `f_constInception`/`f_sigGet`, derived templates delegating back to owner Ref, and owner-required call sites | Must fix |
| `xVar` | `INSTANCE`, `INCEPTION_CLASS`, `s_sigSet` | `NativeTemplates.var()`, canonical owner `f_constInception`/`f_sigSet`, derived templates delegating back to owner Var, and owner-required call sites | Must fix |
| `xConst` | `INSTANCE`, helper method caches, construct-method caches, and `HASH_SIG` | `NativeTemplates.constTemplate()`, owner-passed `f_info` metadata, and owner-template abstract checks | Must fix |
| `xException` | `INSTANCE`, well-known exception class compositions, format method, and ownerless `Utils.translate(Throwable)` path | `NativeTemplates.exception()`, owner-passed `f_info` metadata, static factories resolving from `Frame`/`Container`, and `Utils.translate(Container, Throwable)` | Must fix |
| `xBoolean`, `xNullable`, `xOrdered` | public mutable native enum value handles: `TRUE`, `FALSE`, `NULL`, `LESSER`, `EQUAL`, `GREATER` | `NativeTemplates.booleanTemplate()`, `nullable()`, and `ordered()` plus owner-required factories and pure value predicates | Must fix |
| `xBit` | public mutable native value handles: `ZERO`, `ONE`, ownerless `makeHandle(boolean)`, and delegate assignability through `xBit.ZERO.getTemplate()` | `NativeTemplates.bit()`, final owner-local lazy zero/one handles warmed during `initNative()`, owner-required factories, and delegate assignability against the delegate owner's Bit template | Must fix |

These replacements preserve caching. They do not turn old bootstrap caches into
repeated lookups. The cache key changed from "entire JVM" to "owning
container/template".

The leaf static metadata wave follows the same rule without adding unnecessary
tables. `xRTKeyStore`, `xOSStorage`, `xRTCompiler`, `xNanosTimer`, and
`xRTBuffer` each own exactly one metadata value, so a final `Lazy` field on the
template keeps the old one-time lookup. `xClass.ensureArrayComposition(Container)`
already has the owner as a parameter, so it asks that owner's `ConstantPool` for
`Array<Class>`; the pool interns the value, preserving the old cache behavior
without a process-global `TypeConstant`.

The Bit handle wave follows the same value-handle rule as Boolean, Nullable,
and Ordered, while preserving the old cache behavior: each owning `xBit`
template still creates and reuses exactly one zero handle and one one handle,
and `initNative()` still warms both handles. The difference is that those
handles are no longer JVM-global. The `xRTBitDelegate` assignability check now
compares an incoming value's template to the delegate owner's Bit template
instead of comparing to a sample handle from a process-global static.

The `Utils` metadata wave removes the old split static helper block entirely.
The same templates, constructors, array types, and signatures are still looked
up once, but the lazy cell now lives on `Container`. That is semantically the
same cache granularity the runtime actually needs: one metadata bundle per
owner. It is also safer than separate lazy fields because the template, method,
type, and signature values are created together from one owner and then
published as one immutable `RuntimeMetadata` object. The regression test
`NativeTemplateOldPatternTest.splitStaticMetadataCanMixOwnersAcrossContainers`
demonstrates the old failure shape: two simulated container owners interleave
updates to separate static template/method fields and then throw when a method
from one owner is invoked through a template from another. The owner-scoped
bundle in the same test does not allow that mixed state.

The final root-template wave applies the same rule to `xConst` and
`xException`. `xConst` still performs the same helper method lookups for
stringification, freezing, range/nibble/literal construction, and hash support;
they are now one owner-scoped `ConstInfo` lazy bundle. `xException` still caches
the same well-known exception class compositions and formatting method; they
are now one owner-scoped `ExceptionInfo` lazy bundle. Java-side async
`Throwable` translation now requires a `Container` because those exception
handles need an owner even when there is no live XTC frame. The helper preserves
the old empty-stack behavior for Java `Throwable` translation while removing
the process-global exception-class cache.

`NativeTemplateOldPatternTest.staticExceptionClassCacheCanUseForeignOwner`
models the old failure in which a stock exception factory creates a handle with
a class composition from another owner.

All `boolean fInstance` native-template constructors have been removed from the
converted templates. The last five semantic uses are covered in
[remaining-finstance-constructors.md](remaining-finstance-constructors.md):
`xRef`/`xVar` now use an explicit role, and `xChar`/`xNibble`/`xUInt8` keep the
same eager private final owner-local small-value arrays without a role branch.

The native enum value wave closes the remaining scanned static runtime metadata
category. On master, `xBoolean.TRUE/FALSE`, `xNullable.NULL`, and
`xOrdered.LESSER/EQUAL/GREATER` were public mutable process-global handles
assigned during `initNative()`. Those handles are not JVM constants: each one
carries the owning enum template and composition. The replacement keeps the old
cache semantics by retrieving the same owner-local enum handle from
`getEnumByOrdinal(...)` on the owner template. It removes only the process
global shortcut. Static factories such as `xBoolean.makeHandle(...)`,
`xNullable.makeHandle(...)`, and `xOrdered.makeHandle(...)` now require a
`Frame` or `Container`, and branch conditions use `isTrue(...)`,
`isNull(...)`, or `isEqual(...)` style predicates instead of comparing to a
global handle. There is no new per-use allocation: the value factories return
the already-cached enum handle for the caller's owner.

The `TestCompiler` stress run also exposed an older `xRTCompiler.addError(...)`
bug: `CompilerAdapter.getErrors()` returns `stream().toList()`, which is
unmodifiable on current Java, and the exception path appended to that list.
This branch now appends to a mutable copy, preserving the existing compiler
diagnostics and adding the caught exception without crashing the native
compiler service. The observed failure, mutability contract mistake, and fix are
recorded in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md).

The parallel `TestServices` stress run exposed a separate `StringBuffer`
representation bug, documented in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md).
Large immutable string chunks could make the committed chunk list reject a later
mutable append buffer. `StringBuffer.commitBuf()` now commits append buffers as
immutable chunks, preserving the chunked cache behavior while making the
internal invariant stable. `StringBufferTest.committedChunksStayAppendable()`
verifies the deterministic failing sequence, and the `TestServices` parallel
stress command that exposed the crash now passes.

A first-PR readiness audit on 2026-08-21 reran the plan checks and then pushed a
more aggressive mixed parallel stress shape:

```bash
./gradlew :manualTests:runParallelStress \
  -PstressIterations=5 \
  -PstressModules=TestReflection,TestArray,TestServices,TestTuples \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

That run found one additional branch bug before this note was written:
`xException.buildStringValue()` still read `ExceptionInfo` from the concrete
exception template. That was wrong for subclasses such as `IllegalState` because
`formatExceptionString` is declared by the canonical `Exception` template. The
fix now reads the owner-local canonical exception metadata from the handle's
container, preserving the old single formatter cache semantics without returning
to a JVM-global method cache. The same mixed stress command passes after the
fix.

The same audit also showed why verification needs controlled stress harnesses:
running multiple unrelated Gradle/manual-test invocations concurrently in one
checkout can produce truncated `.xtc` files, closed build-cache pack entries, or
transient classloading failures while one build observes another build's
partially written outputs. That is a build-output isolation problem, not a
runtime owner-state proof. It is documented as a same-JVM stress harness
requirement in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md) and
[plans/same-jvm-launcher-stress.md](plans/same-jvm-launcher-stress.md).

The `xString` wave removes the last-writer-wins string template bridge entirely.
On master, `xString.makeHandle("...")` used `xString.INSTANCE`, and the common
handles `EMPTY_STRING`, `ZERO`, `ONE`, and `EMPTY_ARRAY` were mutable static
runtime handles. A string handle carries a `TypeComposition`; it is therefore not
a JVM-global value. This branch keeps the old cache shape, but moves each cache to
the owning string template:

- `EMPTY_STRING` -> `f_emptyString`
- `EMPTY_ARRAY` -> `f_emptyStringArray`
- `ZERO` -> `f_zero`
- `ONE` -> `f_one`
- `METHOD_APPEND_TO` -> `f_methodAppendTo`

The factories now require a `Frame`, `Container`, `ClassTemplate`, or existing
owner handle. That preserves the old "make a string handle here" behavior while
making the owner explicit enough for the compiler to reject new no-owner calls.
`NativeTemplateOldPatternTest.staticStringFactoryCanReturnForeignOwnerHandles`
demonstrates the old failure shape: two simulated containers initialize the
global string cache, then container A receives a string handle owned by container
B. The owner-scoped replacement in the same test keeps the per-owner empty-string
cache and does not allow cross-owner handles.

The `xRef`/`xVar` wave removes the reflected-reference singleton bridge. The old
static `INCEPTION_CLASS` values and `get`/`set` signatures were canonical base
Ref/Var metadata stored in JVM-global fields. That distinction matters: `xVar`
inherits `Ref.get()`, and Var annotations such as `@Lazy` inherit `Var.set()`;
they do not declare those methods on their own structures. This branch keeps the
same semantics by storing the canonical Ref/Var inception constants and
owner-passed lazy `SignatureConstant` values on the owner-scoped base templates, while
derived templates lazily delegate back to the owner base template. Existing
dynamic reference creation still caches through `ensureParameterizedClass(...)`;
only the template owner source changed from `xRef.INSTANCE`/`xVar.INSTANCE` to
`NativeTemplates.ref()`/`NativeTemplates.var()`. The old `fInstance`
constructor flag is gone. `xRef`/`xVar` now use an explicit `NativeRole` for
the one remaining role distinction: the canonical owner-local base template
owns the rebased metadata, and derived templates delegate back to that owner.
`xRef` registers its native `Identity` child only from the canonical role,
without comparing against a mutable global.

One intentional exception is `xService`'s atomic property-name set. On `master`
it was a mutable `static Set<String>` even though it contains only string
literals. This branch makes it `private static final Set.of(...)`, not a
`Lazy`, because it is pure process-global data and has no container owner.

### `xArray` Static Helper Ownership

Master exposed static helpers such as:

- `xArray.makeStringArrayHandle(...)`
- `xArray.makeByteArrayHandle(...)`
- `xArray.makeObjectArrayHandle(...)`
- `xArray.ensureEmptyByteArray()`
- `xArray.getBooleanArrayComposition()`

Those helpers used static cached compositions and delegates. The branch widens
the helpers with `Container` parameters and updates callers to pass
`frame.container()`, an existing `Container`, or the composition owner.

This is must-fix and not merely mechanical. The array handle's composition and
delegate must come from the same runtime owner as the caller. The old no-arg
helpers could return a handle backed by another container's composition.

The branch keeps the old performance shape: the specialized-template dispatch
map is built once per `xArray` template, the heavier `ArrayInfo` cache is built
once per `xArray` template, and the empty byte array handle remains a nested
lazy value.

### Owner-Explicit Helper APIs

Several helpers did not directly own a mutable `INSTANCE` field, but they still
manufactured runtime handles by falling back to a process-global template. That
is the same bug in a smaller package: the helper could build a handle with a
composition from whichever container initialized the static template last.

This branch removes or hardens those overloads:

- `xString.makeArrayHandle(String[])` and `xString.ensureEmptyArray()` were
  removed. Callers now use `xString.makeArrayHandle(Container, String[])` and
  `xString.ensureEmptyArray(Container)`.
- `xString.makeHandle(String)`, `xString.makeHandle(char[])`, `xString.ZERO`,
  `xString.ONE`, and `xString.EMPTY_STRING` were removed. Callers now pass a
  `Frame`, `Container`, `ClassTemplate`, or existing handle owner, or use
  `xString.zero(frame)`, `xString.one(frame)`, and `xString.emptyString(...)`.
- `xRTType.makeForeignHandle(TypeConstant)` was replaced by
  `xRTType.makeForeignHandle(Container, TypeConstant)`. `TypeConstant` already
  had the caller container in `ensureTypeHandle(Container)`, so the owner is now
  passed through instead of discarded.
- `xFuture.makeHandle(CompletableFuture<ObjectHandle>)` was replaced by
  `xFuture.makeHandle(Container, CompletableFuture<ObjectHandle>)`.
- `xRTFunction.makeAsyncNativeHandle(MethodStructure)` was replaced by
  `xRTFunction.makeAsyncNativeHandle(Frame, MethodStructure)`.
- `NativeTemplates.get(...)`, `xRTFunction.makeInternalHandle(...)`,
  `xRTFunction.makeHandle(Frame, ...)`, and `xRTType.makeHandle(...)` now
  reject null owners at the boundary.
- `xRTFunction.NativeFunctionHandle` now requires a `Container`.
- `xRTFunction.FullyBoundHandle.NO_OP` became `FullyBoundHandle.noOp(Container)`
  so the finalizer anchor is created in the frame owner's runtime.
- `xRTFunction.ensureListMapType`, `xRTModuleTemplate.ensureListMapType`, and
  `xModule.ensureListMapType` now use an explicit caller `Container`.

These changes preserve behavior for correctly owned calls. They do not remove
caching: `ConstantPool` interns type constants, and per-template lazy fields
still memoize metadata where the value is template state. What changes is only
the owner selection. The compiler now rejects the old no-owner call shapes, and
runtime null checks fail immediately if a new caller tries to reintroduce a
hidden global-owner fallback.

The call sites updated for this category include:

- `InterpreterConnector` and `MainContainer` argument-array creation
- `xOSStorage`, `xRTCompiler`, `xCoreRepository`, `xRTKeyStore`
- `xRTNameService`, `xRTNetwork`, `xRTNetworkInterface`, `xRTSocket`
- `xRTConnector`, `xRTServer`
- `xClass`, `xModule`, `xRTModuleTemplate`, `xRTType`, `xRTFunction`
- `ClassTemplate`, `ServiceContext`, `xLocalClock`, `xNanosTimer`

### Natural Enum Initialization

Master's natural enum path could publish a construction struct through
`SingletonConstant.setHandle()` before the enum value had completed
construction. This branch removes that early publication from
`xEnum.createConstHandle`; `Utils.initConstants()` publishes the final public
handle after construction completes.

The branch also adds:

- `xEnum.ensureEnumByName(Frame, String)`
- `xEnum.ensureEnumByOrdinal(Frame, int)`
- `Utils.ensureInitializedEnum(Frame, EnumHandle)`
- `Utils.assignInitializedEnum(Frame, EnumHandle, int)`

Fixed public/native paths include:

- `MainContainer` injectable enum values
- `xArray` mutability construction arguments
- `xRTClassTemplate` contribution action enum values
- `xRTPropertyClassTemplate` contribution action enum values
- `xRTMethod` access enum result
- `xRTComponentTemplate` access and format enum results
- `xRTType` access and form enum results
- `xRTTypeTemplate` access and form enum results
- `xService.synchronicity`, `xRTServiceControl.statusIndicator`, and
  `xFuture.completion` property results
- `xEnumValue.value` and `xEnumeration.byName` reflection results
- `xRTDelegate` and `xArray` mutability property results through
  `ensureEnumByOrdinal(...)`

The reflection helper wave intentionally renames raw-sounding helpers from
`make*Handle` to `ensure*Handle` and changes their return type from
`EnumHandle` to `ObjectHandle`. That is not cosmetic. `ensureEnumByName(...)`
can return the final enum value immediately or a deferred handle while the
corresponding `SingletonConstant` finishes initialization. A raw `EnumHandle`
cannot represent that deferred result and, for a natural enum, may be the
construction struct that must not cross a public/native publication boundary.

This is must-fix. Raw `xEnum.getEnumByName()` and `getEnumByOrdinal()` are now
protected lookup primitives because some internal paths still need the
template-local index before singleton resolution. `xEnum.getValues()` was
removed so reflection code cannot read the raw handle list directly. New code
that can surface an enum handle must use the initialized helpers or assign
through `Utils.assignInitializedEnum`.

### `xLocalClock` Process Timer

Master exposed the LocalClock scheduler as a mutable public static field:

```java
public static Timer TIMER = new Timer("ecstasy:LocalClock", true);
```

That was not owner-scoped metadata, but it was still a dangerous runtime global.
Any code in the JVM could reassign or cancel the timer, which would strand
LocalClock alarms, NanoTimer alarms, and service wake-ups from every container.
It also made the public API say that the `Timer` object itself was the shared
contract, rather than the runtime operation of scheduling wake-up tasks.

This branch keeps the old scheduling and caching behavior: there is still one
daemon Java timer for these runtime wake-ups, so the change does not add one
timer per container and does not add any per-alarm cache miss. The difference is
that the timer is now a private `static final` process resource, and callers use
`xLocalClock.scheduleTimer(TimerTask, long)`.

This is the correct narrow fix for this site. A container-scoped timer would
change behavior and footprint because the existing design intentionally lets
LocalClock, NanoTimer, and service wake-ups share one daemon scheduler while the
scheduled task itself carries the callback/container owner. The must-fix bug was
the public non-final global reference, not the use of one scheduler.

### `xOSStorage` Watch Daemon

Master kept one mutable static watcher daemon:

```java
private static WatchServiceDaemon s_daemonWatch;
```

The daemon itself is reasonably process-wide: Java's `WatchService` watches OS
paths, not XVM containers, and sharing one daemon avoids one native watch thread
per container. The bug was that the daemon constructor also captured a
`ConstantPool` from whichever container first called `watch(...)`:

```java
daemonWatch = s_daemonWatch = new WatchServiceDaemon(pool);
...
try (var _ = ConstantPool.withPool(f_pool)) {
    ...
}
```

That means container B could register a path after container A created the
daemon, but B's later file event would run under A's ambient pool. The handles
created for event paths and event-kind values were already built from the
watched storage handle's container; the ambient pool was the inconsistent part.

This branch keeps exactly one process-wide daemon but moves ownership to a final
synchronized holder:

```java
private static final WatchDaemonHolder WATCH_DAEMON = new WatchDaemonHolder();
```

The daemon no longer stores a `ConstantPool`. Instead, each delivered event
looks at the registered `OSStorage` service handle, obtains that handle's
container, and temporarily installs that container's pool only while preparing
that event. This preserves the old process-wide watcher behavior without
preserving the first-container-wins ambient state leak.

This is a must-fix runtime race because watch events arrive on a Java daemon
thread, outside the service fiber that registered the watch. Any ambient owner
used there must be re-established from the watched handle for each event.

### `xRTServer` TLS Key Selection

Master selected the HTTPS route key store by writing a `KeyStoreHandle` into a
`ThreadLocal` from `SimpleKeyManager.chooseEngineServerAlias(...)` and later
reading it from `getCertificateChain(...)` and `getPrivateKey(...)`.

That is unsafe owner-bearing ambient state. HTTPS worker threads are pooled Java
threads, while `KeyStoreHandle` belongs to an XVM service/container owner. If a
later callback arrived on the same worker without a fresh alias selection, or if
route removal/replacement interleaved with a handshake, the key manager could
observe stale owner state left by a previous handshake.

This branch removes the thread-local state. Each route now has a stable
synthetic TLS alias derived from its host/ports/key name, and the key-manager
certificate/private-key callbacks resolve that alias through the server's
explicit `Router` state. The externally visible TLS key remains the same
keystore entry name; the synthetic alias is internal to the JDK key-manager
callback sequence.

This keeps the old behavior of selecting certificates per route and does not add
a runtime hot-path cost. The route lookup happens during TLS handshake callbacks
only, and route maps were already concurrent. Missing or removed routes now
return the normal empty certificate chain or null private key instead of reading
a stale per-thread handle.

`xRTServerTest` verifies that `SimpleKeyManager` has no thread-local field and
that synthetic aliases resolve through explicit route state.

### Runtime Container Registry

`Runtime.f_containers` is a weak diagnostic registry. Keeping weak keys is the
right lifetime policy because diagnostics must not keep completed containers
alive. The bug was inconsistent synchronization: `registerContainer(...)` and
`containers()` synchronized on the weak map, but `findContainer(...)` iterated
the same `WeakHashMap` without that monitor.

That is a real race because `WeakHashMap` is not concurrent and can mutate its
internal table while expunging stale keys. A parallel registration or cleanup
could produce missed containers or `ConcurrentModificationException` while
diagnostics were trying to map a `ConstantPool` back to its owner container.

This branch keeps the same weak registry and the same debug-only behavior, but
puts `findContainer(...)` under the same monitor. It does not retain containers
longer, does not allocate additional owner state, and does not change runtime
scheduling. It only makes the existing diagnostic registry internally
consistent.

`RuntimeTest.findContainerSharesWeakRegistryMonitorWithRegistration()` exercises
lookup while another thread attempts registration and proves the lookup path
shares the registry monitor.

### Terminal And Debug Console Process State

Master exposed JLine terminal state as public mutable process globals:

```java
public static LineReader READER;
public static Terminal   TERMINAL;
```

`DebugConsole` then copied those same process resources into three additional
mutable statics: `LINE_READER`, `TERMINAL`, and `READER`.

The terminal itself is intentionally process-wide. `System.in`, `System.out`,
`System.console()`, and the JLine terminal are not owned by an XVM container,
and making one terminal per container would change behavior and could corrupt
interactive input. The bug was the representation: unrelated code could reassign
the public reader/terminal, and the debugger had a second mutable snapshot that
could diverge from the console's state.

This branch keeps the same process-wide console behavior but moves the mutable
JLine handles behind one private final synchronized holder:

```java
private static final TerminalState TERMINAL_STATE = new TerminalState();
```

`xTerminalConsole.ensureLineReader(...)`, `lineReader()`, and `terminal()` are
now the only access paths. `DebugConsole` reads through those accessors instead
of caching duplicate static aliases. This preserves the old startup and
performance shape: the JLine reader is still built at most once for the process,
and plain console input still falls back to `CONSOLE_IN` when JLine is
unavailable. The difference is that the mutable process resource is no longer a
public API.

## Supporting Edits

These edits are not independent bug fixes, but they are needed to keep the
must-fix changes short and readable:

- `Container.nativeTemplates()`
- typed `Container.getTemplate(..., Class<T>)`
- `Container.getEnumTemplate(String)`
- `Frame.container()`
- typed `ObjectHandle.getTemplate(Class<T>)`
- typed `TypeComposition.getTemplate(Class<T>)`

They do not change runtime semantics. They remove repeated casts and make owner
selection explicit at call sites.

This is also the answer to the "unnecessary cast removal" concern raised
against the older branch. A cast disappears only when there is a replacement API
that encodes the expected type, or when the cached value has moved behind a
typed final `Lazy` field on the owning template. For example:

- `frame.container().getEnumTemplate("reflect.Access")` replaces repeated
  `(xEnum) ...getTemplate(...)` calls and names the fact that the lookup is for
  an enum template.
- `clz.getTemplate(xEnum.class)` replaces a local cast while preserving an
  explicit `xEnum` local variable.
- `xRTClassTemplate.getActionTemplate()` hides the template-local lazy cache;
  callers no longer need to know how the enum template is cached.
- Remaining casts, such as `xEnum templateEnum = (xEnum) getSuper()`, are
  local structural knowledge, not repeated container-lookup boilerplate.

Files that changed mostly as mechanical owner plumbing include crypto, FS, IO,
network, web, number, and reflection helpers that now pass a `Container` into
`xArray` or `xString` helpers. Those call-site edits should stay with this PR;
without them, the unsafe global helper APIs could not be removed.

## Should-Fix Or Follow-Up Items Touched Here

These are lower priority than the startup races. They may stay if reviewers
accept small opportunistic cleanup, but they are not the reason for the PR.

| Site | Current branch state | Recommendation |
| --- | --- | --- |
| `xRTClassTemplate.NO_TEMPLATES` | made `public static final` | Safe to keep; pure empty array reference, but exposed mutable array contents remain a broader design smell |
| `ClassTemplate` common native signature arrays | changed from public mutable non-final arrays to protected static final startup descriptors | Safe to keep in this PR; they are used by many template subclasses for `markNativeMethod`, and a broader signature-descriptor refactor should be separate |
| `Op.NO_ARGS`, `StmtBlockAST.EMPTY`, `Compiler.EXPRESSION_UNREACHABLE` | made `public static final` | Safe to keep; these are literal constants, not owner-bearing runtime state |
| `xString.StringHandle` hash/String memoization | left as the old per-handle transient cache | Keep out of this PR; replacing with `Lazy` would add two objects per string handle for a should-fix-only concern |
| `xIntLiteral.IntNHandle` text memoization | now stores the constructor-provided text handle and otherwise creates text through its own handle owner | Keep; this preserves the old optional cache and avoids reintroducing ownerless string creation |

## Remaining Legacy Runtime Globals

This branch fixes all native-template `INSTANCE` fields found by the current
runtime/template audit. The full current list is maintained in
[state-inventory.md#mutable-template-instance-inventory](state-inventory.md#mutable-template-instance-inventory)
and is empty on this branch.

The scanned runtime-template/Utils static metadata category is also empty on
this branch. `xLocalClock.TIMER`, `xOSStorage`'s watch daemon, and terminal
JLine state are now encapsulated final process resources. Remaining global-state
backlog now lives in the broader categories documented in
[state-inventory.md](state-inventory.md), such as compiler/JIT
counters/constants. Those remaining counters are same-JVM compiler/tooling
reentrancy work, not owner-bearing runtime template state.

## Proof Points Added By This Branch

`javatools/src/test/java/org/xvm/runtime/NativeTemplateOldPatternTest.java`
contains deterministic demonstrations of the old pattern:

- a static `INSTANCE` cache is last-writer-wins across two owners,
- constructor assignment can expose a partially initialized object,
- static exception class factories can create handles whose class composition
  belongs to a foreign owner,
- static string factories can return handles owned by the wrong container,
- static native enum value factories can return handles owned by the wrong
  container,
- static Bit value factories can return handles owned by the wrong container,
  and the old delegate template check can accept that foreign Bit owner,
- static Ref signature caches can invoke a Ref handle with a foreign owner
  signature,
- derived Ref/Var templates fail if they compute `get`/`set` metadata from their
  own structures instead of inheriting the owner base Ref/Var signatures.

`javatools/src/test/java/org/xvm/runtime/SingletonConstantTest.java` covers the
new singleton state machine:

- concurrent initialization chooses one owner,
- unrelated waiters share completion,
- same-fiber recursion installs an initializing placeholder without deadlock,
- adopted singleton constants get fresh owner-local runtime state,
- adopted file-system constants clear copied owner-local handles.

`javatools/src/test/java/org/xvm/runtime/NativeTemplatesTest.java` also asserts
that reflection enum publication helpers return `ObjectHandle`, not raw
`EnumHandle`, so the unsafe helper signature cannot come back unnoticed.

`manualTests:runParallelStress` is an opt-in stress runner that invokes the
existing parallel `Runner` with repeated module arguments, creating many
lightweight containers in one process:

```bash
./gradlew :manualTests:runParallelStress -PstressIterations=50
```

`manualTests:runDirectSequenceStress` is the complementary same-JVM direct-mode
stress task. It runs selected manual modules repeatedly as separate sequential
`Runner.run()` calls through the Gradle plugin's `ExecutionMode.DIRECT` path,
reusing one build-scoped isolated runtime classloader:

```bash
./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=10 \
  -PsameJvmModules=TestArray,TestNumbers,TestReflection
```

That is the shape that used to be vulnerable to stale process-global runtime
state such as static `VIEWS`, `INSTANCE`, and owner-derived metadata caches
surviving from run N into run N+1.

The task now enables `XtcRunTask.validateRuntimeOwnership`, so it is not only a
crash smoke. After each direct run, the plugin direct executor retains the
completed interpreter container in a bounded recent-container window scoped to
the build-scoped direct runtime classloader and validates the window with
`OwnershipDiagnostics.assertValid(...)`. The current container is always in the
window, so a later run fails if it reuses an owner-scoped template, handle,
composition, service context, or constant-pool value from an earlier run. The
window avoids making the diagnostic harness retain every completed runtime
graph during long all-module stress loops.

The validator allows the normal same-runtime `NativeContainer` parent sharing
implemented by `Container.getTemplate(...)`: a main container may cache
owner-local type keys whose implementation template is owned by its own native
parent. It still rejects native-template values from any other run, and it
validates nested lazy fields of native-owned templates against the native owner.

The validated branch stress passed with:

```bash
./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestArray,TestReflection \
  --console=plain \
  --warning-mode=all \
  --no-daemon \
  --no-configuration-cache
```

The default validated stress now runs two iterations of every known working
manual test module. The module list is shared with `runSequential` and excludes
only `TestAnnotations`, which the build already documents as failing:

```bash
./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  --console=plain \
  --warning-mode=all \
  --no-daemon \
  --no-configuration-cache
```

This all-module validated stress passed locally in 2m29s.

The branch also smoke-tested the launcher JIT path after the owner API changes:

```bash
./gradlew :xdk:installDist --console=plain --warning-mode=all --no-daemon --no-configuration-cache
xdk/build/install/xdk/bin/xec \
  -L manualTests/build/xtc/main/lib \
  -L manualTests/build/xtc/xdk/lib \
  -J EchoTest hello jit
```

That run used `JitConnector`, printed the expected `EchoTest` arguments, and
exited successfully. It proves the changed interpreter owner APIs did not break
the launcher-level JIT connector path. It does not prove JIT ownership safety;
that remains the separate audit described in [jit-implications.md](jit-implications.md).

### Local JIT Constructor Escapes

The branch also removes the local JIT constructor escapes whose behavior could
be preserved without redesigning the JIT lifecycle:

- `BuildContext` now uses `forMethod(...)` and `forProperty(...)` factories.
  Its constructors allocate `TypeMatrix` from method metadata only; the factory
  binds the matrix to the completed context before returning. The matrix owner
  reference is volatile because the binding occurs after construction. That
  preserves the old live-context lookup behavior during type computation
  without letting `TypeMatrix` observe a partially initialized `BuildContext`.
- `JitMethodDesc` no longer calls protected descriptor hooks from its
  constructor. `JitCtorDesc` passes the same implicit parameter sequence as
  constructor data, so normal methods still use `[Ctx]`, constructors still use
  `[Ctx, CtorCtx?, TypeConstant?, target?]`, and primitive receiver parameters
  still stay before `Ctx`.
- `ArrayBuilder` reads the constructor-supplied `TypeSystem` pool directly
  instead of dispatching through the inherited `pool()` accessor.
- `nLongBasedArray` initializes the packed size/mutability field directly in
  the raw-storage constructor. A newly constructed raw-storage array has no
  delegate, so this is equivalent to `$size(smallSize)` followed by
  `$mut($CONSTANT)` without calling subclass-visible methods during
  construction.
- `BuildContext` now marks two intentional JIT switch fallthrough state
  machines with narrow `@SuppressWarnings("fallthrough")` annotations and local
  comments. These are not behavior changes: defaulted optimized parameter
  flavors first emit/consume marker state and then reuse the matching
  base-flavor registration/loading path.

`JitConstructorEscapeTest` verifies the descriptor equivalence and guards
against reintroducing constructor descriptor hooks. The forced lint compile now
reports only one JIT `this-escape` site, `Xvm.java:47`; that remaining startup
owner-publication issue is intentionally left to the JIT lifecycle work
tracked in [jit-implications.md](jit-implications.md).

During development, the first all-module validated run exposed a harness
footprint problem rather than an owner mismatch: retaining every completed
container strongly exhausted the Gradle JVM heap during `TestLambda`. The
validator now uses a bounded recent-container window for long stress runs, and
`OwnershipDiagnostics.validate(...)` no longer builds the full textual dump on
the success path.

These tests do not prove the absence of every race in the runtime. They prove
that the old pattern is concretely broken and that the new replacement has the
intended ownership and lifecycle behavior for the most important fixed paths.

The broader `runtime`/`asm` metadata-shaped instance-field scan remains
follow-up inventory, not a first-PR blocker by itself. A site from that scan
should be promoted to must-fix when it is shared, owner-bearing, lazily
published without synchronization, or part of a multi-field lifecycle
transition. The first PR is intentionally scoped to the confirmed
native-template/static-owner leaks, enum publication, and singleton lifecycle
defects.

No existing manual module was found that directly exercises `@Atomic`
specialized numeric references; the atomic owner-scope wave is covered by Java
compile/test verification and should get an explicit X-level test in a
follow-up.
