# Constant Adoption And Clone Audit

This document explains the `Constant.adoptedBy(...)` mechanism and records the
current audit of `clone()` and adoption-related owner state. It focuses on
runtime and multi-container safety. Compiler/AST clone work is intentionally
listed as backlog here because the compiler is being handled separately.

## Why Constant Adoption Exists

An XVM `Constant` is both a logical value and an `XvmStructure` child. The same
logical constant value often needs to exist in more than one `ConstantPool`, but
the same Java object cannot safely be owned by two pools.

Pool ownership carries mutable structural state:

- the containing `ConstantPool` through `XvmStructure.m_xsParent`;
- the constant index and reference count used when assembling the pool;
- locators and lookup maps owned by the destination pool;
- recursively referenced constants that must also be registered in that pool;
- transient type/runtime caches that are valid only for one pool/container
  owner.

`ConstantPool.register(...)` therefore accepts a constant from another pool and,
when it is legal, creates an adopted copy for the destination pool:

```java
if (constant.getContaining() != this) {
    constant = (T) constant.adoptedBy(this);
}
```

The intended semantics are:

> Preserve the serialized/logical constant value, but change the structural
> owner to the destination `ConstantPool`.

The intended semantics are not:

> Share runtime handles, lifecycle state, mutable helper cells, or in-progress
> caches between two pool/container owners.

Some constants are not legal to adopt into an unrelated pool. For `TypeConstant`
values, `ConstantPool.register(...)` first checks `type.isShared(this)`. A
non-shareable type is returned as-is instead of being adopted, because adopting
it would claim that a destination pool owns a type graph that is not actually
visible from that pool.

## Why The Master Mechanism Was Dangerous

In `master`, the base implementation used `Object.clone()`:

```java
protected Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) super.clone();
    that.setContaining(pool);
    that.resetRefs();
    return that;
}
```

`Object.clone()` is shallow. It copies field references, including references
held in `final` fields. `transient` does not help: `transient` affects Java
serialization, not cloning. Java final-field semantics also do not help: a
final reference can be safely published while the mutable object behind that
reference is still shared and mutable.

That means every constant subclass that contains runtime state, owner-derived
caches, locks, atomics, thread-local cells, or live handles must either:

- implement `copyForAdoption(...)` and create a fresh owner-local copy; or
- clear all non-logical state during `setContaining(...)` or
  `registerConstants(...)` before the adopted copy is observable through normal
  APIs.

This branch removes that bad contract. `Constant` no longer implements
`Cloneable`; `Constant.adoptedBy(...)` is now the final owner-transfer wrapper.
It constructs an `AdoptionContext`, delegates to `copyForAdoption(...)`, checks
that the result is owned by the requested pool, and resets reference counts. The
default `copyForAdoption(...)` path fails closed. There is no
`cloneForAdoption(...)` helper and no `allowsDefaultAdoptionClone()` escape
hatch. Every concrete constant adoption path must explicitly construct the
target-owned logical value or fail closed.

This is the same root cause as the fixed `SingletonConstant` incident:
`SingletonConstant.f_state` was final, but the final `AtomicReference` object
was shallow-copied, so a singleton adopted into a second pool reused the first
pool/container's runtime singleton state.

## Adoption Entry Points

These are the current adoption sites in runtime/asm source.

| Site | Purpose | Runtime/container assessment |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/asm/Constant.java` | Final adoption wrapper that delegates to `copyForAdoption(...)`, checks target ownership, and resets refs. | Central guard. `Constant` is not `Cloneable`; concrete leaves construct fresh logical copies or fail closed. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:199` | Adopt a foreign constant during pool registration. | Expected owner-transfer path. Must not preserve source-pool runtime state. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:216` | Adopt a foreign locator constant used by the pool lookup table. | Same hazard as normal adoption, but through locators. |
| `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java` | Branch `copyForAdoption(...)` hook that constructs a fresh singleton constant for the target pool. | Fixed must-fix site. Prevents cross-container singleton handle/lifecycle state sharing. |
| `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java` | Branch `copyForAdoption(...)` hook that constructs a fresh target-pool logical file-node constant. | Fixed must-fix site. Prevents copied runtime handles and source-pool path literals from crossing pools without relying on shallow clone cleanup. |
| `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java` | Branch `copyForAdoption(...)` hook that constructs a fresh target-pool logical file-store constant. | Fixed must-fix site. Prevents copied runtime handles from crossing pools without relying on shallow clone cleanup. |
| `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs the logical parameterized type for the target pool. | Fixed hardening site. Keeps the final helper lock owner-local and drops resolver/JIT helper state. |
| `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs the logical signature for the target pool. | Fixed hardening site. Preserves transient property-signature identity while dropping comparison/JIT helper state. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs the logical register type parameter for the target pool. | Fixed hardening site. Keeps the final reentrancy helper owner-local. |
| `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java` | Branch `copyForAdoption(...)` guard for live runtime handles. | Fixed must-fix guard. Allows first registration of a fresh unowned runtime handle constant by constructing a target-owned wrapper, but rejects moving an already-owned live handle into another pool. |
| `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs method identity from parent, signature, and lambda id. | Fixed P1 hardening site. Drops cached method type and JIT method name instead of carrying owner/type-system helper state across pools. |
| `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs property identity from parent and name. | Fixed P1 hardening site. Drops cached property type, signature, constraint, property info, and JIT property name instead of carrying owner/type-system helper state across pools. |
| `javatools/src/main/java/org/xvm/asm/constants/FormalTypeChildConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs formal-child identity from formal parent and name. | Fixed P1 hardening site. Preserves `FormalTypeChild` format while dropping inherited property metadata/JIT caches. |
| `javatools/src/main/java/org/xvm/asm/constants/DynamicFormalConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs dynamic-formal identity from parent, name, register index/id, register type, and underlying formal. | Fixed P1/P2 hardening site. Drops the transient compiler `Register` and rejects adoption when the register type is not shared with the destination pool. |
| `javatools/src/main/java/org/xvm/asm/constants/RegisterConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs register identity from the serialized register index. | Fixed P1/P4 hardening site. Drops the transient compiler `Register` and rejects unknown registers whose index can still move during allocation. |
| `javatools/src/main/java/org/xvm/asm/constants/MethodBindingConstant.java` | Branch `copyForAdoption(...)` hook that reconstructs the bind-target descriptor from its method identity. | Fixed P4 hardening site. This closes the frame-dependent default-clone fallback; target registration still adopts the method identity as before. |
| `javatools/src/main/java/org/xvm/asm/constants/ConditionalConstant.java` and condition leaves | Branch `copyForAdoption(...)` hooks that reconstruct link-time predicates from logical child/name/module/version values. | Fixed P5 clone-free hardening site. Prevents the transient brute-force `iTest` simulation slot from being copied into another pool, and makes the field private scratch state instead of public mutable API. |
| `javatools/src/main/java/org/xvm/asm/constants/UInt8ArrayConstant.java`, `FPNConstant.java`, `Float128Constant.java` | Branch `copyForAdoption(...)` hooks and defensive byte-array constructors for array-backed scalar values. | Fixed P5 clone-free hardening site. Prevents caller arrays and adopted constants from sharing mutable byte storage across logical immutable constant values. |
| `javatools/src/main/java/org/xvm/asm/Annotation.java` and `AnnotatedTypeConstant.java` | Branch `copyForAdoption(...)` hooks that reconstruct annotation metadata and annotated type shells. | Fixed P5/P1 clone-free hardening site. Annotation parameter arrays are detached at adoption and in `resolveParams(...)` (the constructor must keep aliasing the caller's array for the compiler's emit-time placeholder back-fill — a constructor copy broke the XDK build, verified by bisect), already-owned runtime handle params are rejected, and the annotated type cache is recomputed by the destination owner. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeSequenceTypeConstant.java`, `PendingTypeConstant.java`, `UnresolvedTypeConstant.java` | Branch `copyForAdoption(...)` hooks for transient type markers/placeholders. | Fixed P5/P1 hardening site. The stateless type-sequence marker is reconstructed explicitly; mutable pending/unresolved compiler placeholders fail closed before they can be published as completed target-pool metadata. |
| `javatools/src/main/java/org/xvm/asm/constants/PseudoConstant.java` and pseudo leaves | Branch `copyForAdoption(...)` hooks for pseudo path constants and fail-closed hooks for unresolved placeholders. | Fixed P5/P2 hardening site. Auto-narrowing pseudo class paths and keyword singletons are reconstructed from logical fields; deferred/expression/unresolved-name placeholders cannot be adopted before resolution. |
| `javatools/src/main/java/org/xvm/asm/constants/ModuleConstant.java`, `PackageConstant.java`, `ClassConstant.java`, `MultiMethodConstant.java`, `TypedefConstant.java`, `DecoratedClassConstant.java`, `PureIdentityConstant.java`, `NativeRebaseConstant.java` | Branch `copyForAdoption(...)` hooks for named and type-backed identity constants. | Fixed P5/P2 hardening site. Named path identities rebuild target-owned parent/name shells, `TypedefConstant` drops resolved-state helper data, type-backed artificial identities require target-shareable type keys, and runtime-only native rebase identities fail closed. |

## Explicit Default-Clone Policy Inventory

This inventory was generated from the current tree by scanning constant
subclasses and whether they override `copyForAdoption(...)`. The exact command
shape is:

```bash
for f in javatools/src/main/java/org/xvm/asm/constants/*.java \
         javatools/src/main/java/org/xvm/asm/Annotation.java \
         javatools/src/main/java/org/xvm/asm/Constant.java; do
  # strip comments, read "class X extends Y", then check for copyForAdoption(...)
done
```

The important result is that adoption is no longer an inherited clone policy.
Every concrete class below either reconstructs logical value state through an
explicit hook, inherits a reviewed explicit hook, or fails closed.

| Group | Classes | Current assessment |
| --- | --- | --- |
| Explicit adoption hook | `Annotation`, `AccessTypeConstant`, `AllCondition`, `AnnotatedTypeConstant`, `AnonymousClassTypeConstant`, `AnyCondition`, `ArrayConstant`, `BFloat16Constant`, `ByteConstant`, `CastTypeConstant`, `CharConstant`, `ChildClassConstant`, `ClassConstant`, `DecimalAutoConstant`, `DecimalConstant`, `DecoratedClassConstant`, `DeferredValueConstant`, `DifferenceTypeConstant`, `DynamicFormalConstant`, `ExpressionConstant`, `FPNConstant`, `FSNodeConstant`, `FileStoreConstant`, `Float128Constant`, `Float16Constant`, `Float32Constant`, `Float64Constant`, `Float8e4Constant`, `Float8e5Constant`, `FormalTypeChildConstant`, `HandleConstant`, `ImmutableTypeConstant`, `InnerChildTypeConstant`, `IntConstant`, `IntersectionTypeConstant`, `KeywordConstant`, `LiteralConstant`, `MapConstant`, `MatchAnyConstant`, `MethodBindingConstant`, `MethodConstant`, `ModuleConstant`, `MultiMethodConstant`, `NamedCondition`, `NativeRebaseConstant`, `NotCondition`, `PackageConstant`, `ParameterizedTypeConstant`, `ParentClassConstant`, `PendingTypeConstant`, `PresentCondition`, `PropertyClassTypeConstant`, `PropertyConstant`, `PureIdentityConstant`, `RangeConstant`, `RecursiveTypeConstant`, `RegExConstant`, `RegisterConstant`, `ServiceTypeConstant`, `SignatureConstant`, `SingletonConstant`, `StringConstant`, `TerminalTypeConstant`, `ThisClassConstant`, `TypeParameterConstant`, `TypeSequenceTypeConstant`, `TypedefConstant`, `UInt8ArrayConstant`, `UnionTypeConstant`, `UnresolvedNameConstant`, `UnresolvedTypeConstant`, `VersionConstant`, `VersionMatchesCondition`, `VersionedCondition`, `VirtualChildTypeConstant` | Fixed or guarded in this branch. These were known runtime/helper/scratch-state cases, array-backed values, immutable scalar values, composite value containers, parsed-value caches, annotation value containers, named/type-backed identities, pseudo path/placeholder values, type-keyed sentinel shells, type leaves, single-child type wrappers, storable relational types, dependant child/property type shells, recursive typedef shells, or transient type markers where adoption should be explicit rather than inherited from the transitional clone helper. They now implement `copyForAdoption(...)` under the final wrapper and use explicit construction or fail closed. |
| Abstract type-family helpers | `AbstractDependantChildTypeConstant`, `AbstractDependantTypeConstant`, `RelationalTypeConstant` | These are not adoption endpoints. Concrete type leaves/wrappers/relations now use explicit construction or fail-closed hooks. `TypeConstant.setContaining(...)` still clears owner-derived helper state for other owner-change paths, but adoption no longer relies on clone-then-reset. |
| Abstract constant-family bases | `IdentityConstant`, `TypeConstant`, `ValueConstant`, `FloatConstant`, `FormalConstant`, `NamedConstant` | These abstract bases no longer grant adoption by shallow clone. Concrete leaves must provide or inherit an explicit `copyForAdoption(...)` implementation. |
| Scalar value constants | `BFloat16Constant`, `ByteConstant`, `CharConstant`, `DecimalConstant`, `Float16Constant`, `Float32Constant`, `Float64Constant`, `Float8e4Constant`, `Float8e5Constant`, `IntConstant`, `RegExConstant`, `StringConstant` | Fixed in this branch. Each class reconstructs the same immutable logical scalar value in the destination pool. FP8 constants use private raw-bit adoption constructors so deserialized encodings are preserved even though the public float conversion path is incomplete for some values. |
| Composite value constants | `ArrayConstant`, `MapConstant`, `RangeConstant` | Fixed in this branch for value-container adoption. Array/map constructors defensively copy caller arrays, adoption creates fresh target-owned shells, and target registration rewrites child constants without mutating source containers. Type constants referenced by array/map value types now pass through clone-free type-family adoption hooks. |
| Parsed/delegated value constants | `DecimalAutoConstant`, `LiteralConstant`, `VersionConstant` | Fixed in this branch. `LiteralConstant` reconstructs text/format and drops the transient parsed `m_oVal` cache; `VersionConstant` preserves its subclass instead of becoming a plain literal; `DecimalAutoConstant` keeps its delegated decimal child on the normal recursive registration path. |
| Value family bases | `FloatConstant`, `ValueConstant` | Abstract bases only. They do not provide fallback clone adoption. |
| Condition constants | `AllCondition`, `AnyCondition`, `MultiCondition`, `NamedCondition`, `NotCondition`, `PresentCondition`, `VersionMatchesCondition`, `VersionedCondition` | Fixed in this branch. The family no longer opts into default clone: each concrete condition reconstructs the logical predicate and lets target registration adopt child constants. The base `ConditionalConstant.iTest` simulation slot is private transient scratch state and is not copied. |
| Frame-dependent constants | explicit `MethodBindingConstant`, explicit `RegisterConstant`, and guarded `HandleConstant` | The frame-dependent family no longer opts into default clone. `MethodBindingConstant` reconstructs the serialized descriptor, `RegisterConstant` drops transient compiler register state, and `HandleConstant` is guarded because it wraps a live `ObjectHandle`. |
| Not a constant adoption target | `TypeInfoReal` | It appears in the scan only because it extends `TypeInfo`, not `Constant`. It is tracked in the TypeInfo owner-copy audit instead. |

The long-term adoption rule is now in place for constants: owner transfer must
construct a new target-pool constant from listed logical fields and
intentionally drop, rebuild, or reject every helper/cache field. Keep
`ConstantAdoptionValidator` in stress/CI because a future explicit hook can
still be written incorrectly by reusing owner-local helper/runtime references.

## Runtime-Relevant Adoption Risks

These are the adoption-related sites that matter for parallel runtime or
multiple containers in one JVM.

| Site | Current state | Risk | Proper fix |
| --- | --- | --- | --- |
| `SingletonConstant.f_state` | Fixed in this branch by constructing a fresh adopted constant. | Shallow clone shared final `AtomicReference<InitState>`; second pool could receive first container's singleton handle. | Keep this branch fix. Adoption must not clone lifecycle state cells. |
| `FSNodeConstant.m_handle` | Fixed in this branch by constructing a fresh logical file-node copy. | Shallow clone could copy a runtime handle into another pool. | Keep clone-free adoption for this class. Runtime handle caches are owner-local and should not be copied. |
| `FSNodeConstant.m_constPath` | Fixed in this branch by constructing a fresh adopted copy with an empty path cache and recomputing on demand under the destination pool. | Shallow clone could copy a cached `LiteralConstant` whose owner was the source pool. | Keep the per-node cache, but never copy it across adoption. |
| `FileStoreConstant.m_handle` | Fixed in this branch by constructing a fresh logical file-store copy. | Same as `FSNodeConstant`. | Keep clone-free adoption for this class. |
| Former `TypeConstant.m_handle` | Removed in this branch; shared Type handles are cached by `Container.ensureTypeHandle(TypeConstant)`. | A Type handle is runtime owner state. Clearing at adoption protected only pool movement; it did not protect two containers sharing one pool from reusing the first container's handle. | Keep the owner-local container cache. `TypeConstant.ensureTypeHandle(Container)` remains the public API and still leaves foreign handles uncached. |
| `TypeConstant.m_typeinfo`, `m_mapRelations`, `m_typeNormalized`, `m_cInvalidations` | Cleared/reset by `TypeConstant.setContaining(...)`. | Type-info and relation caches are computed against a pool/type graph and must not be copied as authoritative target-pool state. | Existing reset is correct. |
| `TypeConstant.m_cRecursiveDepth` | Fixed in this branch: no longer final and reset in `TypeConstant.setContaining(...)`. | Shallow clone shared the same mutable recursion counter. A concurrent or recursive type-info build in one pool could affect another adopted type and produce false "infinite loop" failures. | Keep the owner-change reset. A broader fresh-constructor rewrite for every `TypeConstant` subclass is not needed for this PR. |
| `TypeConstant.m_tloInProgress` | Fixed in this branch by clearing in `TypeConstant.setContaining(...)`. | If adoption occurred while a relation calculation had installed a thread-local in-progress set, the adopted copy could inherit that cell. Same-thread reentrant adoption could observe source relation state and return a false incompatibility. | Keep clearing on owner change. |
| `TypeConstant.m_mapConsumes`, `m_mapProduces` | Fixed in this branch by clearing in `TypeConstant.setContaining(...)`. | They cache type-derived answers and can contain in-progress sentinels. Copying them preserved source-pool work as target-pool truth. | Keep clearing on owner change. |
| `TypeConstant.m_sJitName` | Fixed in this branch by clearing in `TypeConstant.setContaining(...)`. | JIT-generated names are not logical constant value and can depend on the JIT/type-system owner. | Keep clearing on owner change; deeper JIT policy remains tracked in `jit-implications.md`. |
| `ParameterizedTypeConstant.m_lockPrev` | Fixed in this branch by reconstructing the adopted parameterized type. The lock remains `final transient`. | The adopted constant shared the source constant's lock object. The cached values were cleared later, but the synchronization object itself was cross-pool mutable state and could be cloned while locked. | Keep fresh-constructor adoption so helper cells are born with the target owner. |
| `ParameterizedTypeConstant.m_typeJitCallable` | Fixed in this branch by fresh-constructor adoption. | A JIT-specific type cache is not serialized logical value and should not cross a pool adoption boundary. | Keep fresh-constructor adoption. |
| `SignatureConstant.m_lockPrev` | Fixed in this branch by reconstructing the adopted signature. The lock remains `final transient`. | Adopted signatures shared source synchronization state. This is not as directly owner-bearing as a handle, but it is still mutable cross-pool state created by shallow clone. | Keep fresh-constructor adoption and preserve only logical signature identity. |
| `SignatureConstant.m_sJitName` | Fixed in this branch by fresh-constructor adoption. | JIT-generated names are owner/type-system state, not serialized signature value. | Keep fresh-constructor adoption. |
| `TypeParameterConstant.f_tloReEntry` | Fixed in this branch by reconstructing the adopted type parameter. The helper remains `final transient`. | Shallow clone shared reentrancy tracking between equivalent type-parameter constants in different pools. Same-thread recursive comparison could accidentally suppress parent comparison in the adopted constant. | Keep fresh-constructor adoption. |
| `MethodConstant.m_type`, `m_sJitName` | Fixed in this branch by reconstructing the adopted method identity from parent, signature, and lambda id. | The cached method type is owner-derived, and the JIT name belongs to a future `TypeSystem` owner, not serialized method identity. Default clone copied both into the target pool. | Keep `MethodConstant.copyForAdoption(...)` and recompute these caches under the target owner. |
| `PropertyConstant.m_type`, `m_constSig`, `m_typeConstraint`, `m_info`, `m_sJitName` | Fixed in this branch by reconstructing the adopted property identity from parent and name. | These caches come from the owner component graph, target type, or future JIT `TypeSystem`. Default clone copied them into the target pool. | Keep `PropertyConstant.copyForAdoption(...)` and recompute these caches under the target owner. |
| `FormalTypeChildConstant` inherited property caches | Fixed in this branch by reconstructing the adopted formal-child identity from formal parent and name. | A base `PropertyConstant` copy would preserve the wrong format, while default clone would preserve inherited property caches. | Keep the subclass hook so adoption preserves `FormalTypeChild` identity and drops inherited caches. |
| `DynamicFormalConstant.m_reg`, `m_typeReg` | Fixed in this branch by reconstructing from serialized register index/id and by rejecting a register type that is not shared with the destination pool. | The transient `Register` is a compiler/method-owner object, not logical constant data. The old shallow clone could also publish a dynamic formal whose register type named a source module class that the target pool could neither own nor share. | Keep the explicit hook. Valid shared/upstream types keep the same registration behavior; invalid foreign types fail at adoption instead of escaping into the target pool. |
| `RegisterConstant.m_reg` | Fixed in this branch by reconstructing from the serialized register index and by rejecting unknown registers. | The transient `Register` is compiler-local state. If it is already allocated, runtime only needs the index; if it is still unknown, the index can move later and copying it would either leak the source `Register` or freeze the wrong value. | Keep the explicit hook. Adopted constants match the deserialized/runtime shape: index only, no compiler register. |
| `MethodBindingConstant.m_idMethod` | Fixed in this branch by reconstructing the bind-target descriptor from method identity. | The field is logical, but the old behavior depended on `FrameDependentConstant` granting shallow clone to every current and future subclass. That is too broad for a runtime/frame-dependent family. | Keep the explicit hook. The destination pool still registers/adopts the method identity recursively, preserving interning behavior without a family clone fallback. |
| `ConditionalConstant.iTest` | Fixed in this branch by removing the family default-clone opt-in, adding explicit condition copy hooks, and making the field private. | `terminalInfluences()` writes this field as brute-force simulation scratch on terminal conditions. It is not serialized predicate value. A shallow adoption clone copied whatever previous analysis stored there into another pool, and the old public field made external mutation possible. | Keep explicit copy hooks for all condition leaves. Adoption preserves name/module/version/child predicates and target registration still interns child constants, but simulation scratch starts clean in the destination owner. |
| Array-backed scalar value constants | Fixed in this branch for `UInt8ArrayConstant`, `FPNConstant`, and `Float128Constant` by defensively copying constructor input arrays and reconstructing adopted values with fresh arrays. | A byte string or encoded floating-point constant participates in hash/equality and pool lookup as immutable logical value, but the old constructors and shallow adoption path could share the same mutable `byte[]` across caller code or pool owners. Mutating the source array after construction or adoption could change another owner's constant value. | Keep constructor/adoption byte copies. The legacy raw `getValue()` API still returns the internal array and remains an array-immutability follow-up; this wave avoids extra per-read allocation and fixes the proven cross-owner/input-sharing hazard. |
| Immutable scalar value constants | Fixed in this branch for `BFloat16Constant`, `ByteConstant`, `CharConstant`, `DecimalConstant`, `Float16Constant`, `Float32Constant`, `Float64Constant`, `Float8e4Constant`, `Float8e5Constant`, `IntConstant`, `RegExConstant`, and `StringConstant`. | These were not known to carry owner-local state today, but inheriting shallow clone made them one field edit away from copying helper caches or owner references across pools. | Keep explicit scalar reconstruction. It has the same allocation shape as shallow clone, preserves constant-pool interning through registration, and makes future owner-derived fields visible in code review instead of silently inherited. |
| Composite value containers | Fixed in this branch for `ArrayConstant`, `MapConstant`, and `RangeConstant`. | Array and map constants store `Constant[]` containers used by hash/equality and later mutated by `registerConstants(...)` to hold registered child constants. Default clone and caller-owned constructor arrays let one owner or caller mutate another owner's logical value container. | Keep constructor container copies and explicit copy hooks. Pool factory paths still perform one container copy because copying moved from `ConstantPool.ensure*Constant(...)` into `ArrayConstant`; map/entry map construction keeps its generated arrays without an extra copy. Child type constants are adopted through the clone-free type-family hooks. |
| Parsed and delegated value caches | Fixed in this branch for `LiteralConstant`, `VersionConstant`, and `DecimalAutoConstant`. | `LiteralConstant.m_oVal` is a parsed helper cache, not serialized literal value, and shallow clone copied it. `VersionConstant` must not be copied by a base literal hook that loses the subclass. `DecimalAutoConstant` delegates to a child decimal constant that must be registered in the target pool. | Keep explicit hooks. Literal adoption drops parsed cache, version adoption preserves the concrete subclass, and decimal-auto target registration adopts the delegated decimal child. |
| `MatchAnyConstant` type-keyed sentinel shell | Fixed in this branch for the value shell and the foreign-key boundary. | The wildcard's logical value is a `TypeConstant`. Shallow clone was unnecessary for the shell, and a copied shell could hide whether the type key was actually valid in the destination owner. | Keep `MatchAnyConstant.copyForAdoption(...)`. It rebuilds the shell, rejects unrelated foreign type keys before publication, and lets target registration adopt/intern shared type keys through the clone-free type-family hooks. |
| Dependant child/property type shells | Fixed in this branch for `VirtualChildTypeConstant`, `InnerChildTypeConstant`, `AnonymousClassTypeConstant`, and `PropertyClassTypeConstant`. | These types contain logical parent plus child name/class/property identity, but can also cache resolved child structure or `PropertyInfo` metadata derived from one owner graph. The old shallow clone copied that cache shape and then relied on later mutation/reset. | Keep explicit hooks. They reconstruct the target-owned shell, target registration interns parent and child constants as before, transient virtual-origin parent metadata is preserved when present, and owner-derived caches are rebuilt locally. |
| `RecursiveTypeConstant` subclass shell | Fixed in this branch by reconstructing the concrete recursive type from its typedef identity. | `RecursiveTypeConstant` extends `TerminalTypeConstant`; without its own hook, clone-free terminal adoption would produce a plain terminal type and lose recursive typedef behavior. The old shallow clone preserved the subclass accidentally. | Keep the subclass hook. Shared typedef identities are adopted/interned by the target pool; unrelated foreign typedefs fail before publication. |
| `Annotation` parameter arrays and `AnnotatedTypeConstant` shells | Fixed in this branch by defensively copying annotation parameter arrays and reconstructing annotated type shells. | Annotation params are part of immutable logical constant identity, but the old constructor and shallow adoption path could reuse caller/source-owner arrays. The annotated type also carried a derived annotation-type cache that belongs to one pool owner. Runtime `HandleConstant` params are live `ObjectHandle` state and cannot be moved after first registration. | Keep the constructor/adoption array copies, the `AnnotatedTypeConstant.copyForAdoption(...)` hook, and the already-owned handle guard. Target registration still interns annotation class, params, and underlying type exactly as before; the derived annotation-type cache starts empty in the target owner. |
| `PendingTypeConstant` and `UnresolvedTypeConstant` compiler placeholders | Fixed in this branch by failing adoption closed. | These are mutable compiler/name-resolution placeholders. Publishing them into another pool would claim that unfinished compiler state is completed target-pool metadata. | Keep the fail-closed hooks. These constants are already unassemblable/unresolved; valid runtime pools should see resolved constants instead. |
| `TypeSequenceTypeConstant` formal marker | Fixed in this branch by reconstructing the stateless marker. | It has no child state, but inheriting shallow clone kept the type-family fallback alive for no benefit. | Keep explicit reconstruction. Allocation and interning behavior are the same as shallow clone, but the adoption policy is visible. |
| Pseudo class-path constants | Fixed in this branch for `ThisClassConstant`, `ParentClassConstant`, `ChildClassConstant`, and `KeywordConstant`. | These are logical pseudo path/category values, but a family shallow clone would copy any future pseudo helper state. The test also exposed that locator adoption alone does not rewrite child fields when recursive registration is deferred. | Keep explicit reconstruction and pre-register logical child identities in the target pool. Keyword constants still use per-format singleton lookup in each pool. |
| Pseudo compiler placeholders | Fixed in this branch for `DeferredValueConstant`, `ExpressionConstant`, and `UnresolvedNameConstant`. | These are unresolved compiler/AST placeholders, not completed pool metadata. `UnresolvedNameConstant` also stored a caller-owned `String[]` used by temporary hash/equality. | Keep fail-closed adoption and constructor array copies. Valid runtime pools should see resolved constants, not these placeholders. |
| Named and type-backed identity constants | Fixed in this branch for `ModuleConstant`, `PackageConstant`, `ClassConstant`, `MultiMethodConstant`, `TypedefConstant`, `DecoratedClassConstant`, `PureIdentityConstant`, and `NativeRebaseConstant`. | Named identities looked low-risk, but default clone still copied identity-family helper fields and any future owner-derived state. `TypedefConstant.m_fInitialized` is resolved recursion state, not logical identity. Type-backed identities can also smuggle a source-owner type key if the key is not target-shareable. `NativeRebaseConstant` is a runtime-only facade and should never become serialized pool metadata. | Keep explicit reconstruction. Named identities register the target-owned parent before publishing the shell. Type-backed identities register a target-owned shared type key or reject a foreign key. Native rebase adoption remains fail-closed. |
| `Constant.m_oValue` | Private transient base field with no current read/write API hits. | Under the old default clone path, any future runtime use would have been shallow-copied across owners. | Leave documented. If revived, either handle it explicitly in `copyForAdoption(...)` or remove the field. |

This branch now fixes the constant-adoption hardening items in this table. The
common rule is that adoption may preserve logical constant identity, but not
mutable helper cells, in-progress state, JIT caches, or live runtime handles.

## Frame-Dependent Constants

`FrameDependentConstant` is not automatically illegal. The family used to hide
behind inherited clone adoption, but now fails closed unless a concrete
serialized constant form states its owner-transfer rule:

- `RegisterConstant` represents a frame register and can be serialized as
  `Format.Register`. Its in-memory compile-time form can still carry a
  transient `Register`, so this branch gives it an explicit adoption hook that
  keeps only the register index and rejects unknown registers.
- `MethodBindingConstant` represents a bind-target constant and can be
  serialized as `Format.BindTarget`. It now has an explicit adoption hook so
  the family base can fail closed for future subclasses.

`HandleConstant` is different:

- it is explicitly runtime-only;
- it wraps a live `ObjectHandle`;
- it is constructed with `super(null)`;
- it reuses `Format.Register` only to avoid adding a new format;
- it is currently created by
  `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java:546`
  when annotation arguments are live runtime handles.

That current use can be valid as a frame-local runtime operation, so this branch
allows the first adoption of a fresh unowned `HandleConstant` into the current
pool. It rejects a second adoption once the constant is already pool-owned,
because that would silently move a live handle into another owner.

## Clone Sites Checked

This table records every current runtime/asm `clone()` hit from:

```bash
rg -n "implements Cloneable|super\\.clone\\(|\\.clone\\(" \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/runtime
```

| Site | Category | Runtime/container assessment |
| --- | --- | --- |
| `ClassStructure.java:3006` | Defensive parameter-array copy. | Not a runtime owner-transfer clone; compiler/linker backlog. |
| `ClassStructure.java:3007` | Defensive return-array copy. | Not a runtime owner-transfer clone; compiler/linker backlog. |
| `Component.java:1973` | Component body clone. | Compiler/linker structure clone. Audit separately for incremental compiler and parallel module mutation. |
| `Component.java:1984` | Contribution clone during component clone. | Same as `Component.cloneBody`; not part of this runtime PR. |
| `Component.java:2573` | `Contribution implements Cloneable`. | Same compiler/linker backlog category. |
| `Component.java:3150` | `Contribution.clone()`. | Same compiler/linker backlog category. |
| `Constant.java:315` | Base adoption clone. | Central runtime/pool owner-transfer hazard. |
| `Constant.java:716` | Copy-on-write array of underlying constants. | Defensive array container copy before rehoming elements; intended to avoid mutating a cloned constant's shared array. |
| `ConstantPool.java:294` | Byte-array defensive copy for `UInt8ArrayConstant`. | Safe defensive copy of value bytes. |
| `ConstantPool.java:755` | Array-constant argument array copy. | Safe defensive copy of array container; elements are constants registered by the pool. |
| `ConstantPool.java:769` | Set-constant argument array copy. | Same as array constant. |
| `ConstantPool.java:783` | Tuple-constant argument array copy. | Same as array constant. |
| `LinkedRepository.java:40` | Repository-array defensive copy. | Process structure setup; not runtime owner transfer. |
| `MethodStructure.java:1768` | Local constants array copy during method clone. | Compiler/linker structure clone; audit separately. |
| `MethodStructure.java:1775` | Source clone during method clone. | Compiler/linker structure clone; audit separately. |
| `MethodStructure.java:2761` | Nested source `Cloneable`. | Compiler/linker source metadata clone. |
| `MethodStructure.java:2954` | Nested source `clone()`. | Compiler/linker source metadata clone. |
| `OpCallable.java:637` | Result type array copy. | Defensive array copy in op/runtime metadata; no owner transfer by itself. |
| `Parameter.java:30` | `Parameter implements Cloneable`. | Compiler/linker parameter clone; audit separately for incremental compiler. |
| `Parameter.java:372` | Parameter clone. | Compiler/linker parameter clone; not part of this runtime PR. |
| `AllCondition.java:108` | Condition array copy. | Defensive immutable/logical constant array copy. |
| `AllCondition.java:114` | Condition array copy. | Same. |
| `AllCondition.java:139` | Condition array copy. | Same. |
| `ArrayConstant.java:205` | Array value copy-on-write. | Defensive logical value copy before replacing elements. |
| `MapConstant.java:231` | Key-array copy-on-write. | Defensive logical value copy before replacing elements. |
| `MapConstant.java:245` | Value-array copy-on-write. | Defensive logical value copy before replacing elements. |
| `MethodInfo.java:239` | MethodBody array copy. | Metadata chain copy; not a constant-pool adoption clone. Audit if method info is reused across owners. |
| `MethodInfo.java:491` | MethodBody chain copy. | Same metadata-chain category. |
| `MethodInfo.java:583` | MethodBody array copy. | Same metadata-chain category. |
| `PropertyInfo.java:1392` | MethodBody chain copy. | Same metadata-chain category. |
| `SignatureConstant.java:180` | Parameter-type array defensive copy. | Safe array container copy. |
| `SignatureConstant.java:268` | Parameter type array copy-on-write. | Safe logical copy. |
| `SignatureConstant.java:282` | Return type array copy-on-write. | Safe logical copy. |
| `SignatureConstant.java:318` | Type array copy. | Safe logical copy. |
| `SignatureConstant.java:333` | Type array copy. | Safe logical copy. |
| `TerminalTypeConstant.java:727` | Parameter type array copy. | Safe logical type-array copy. |
| `TerminalTypeConstant.java:731` | Annotation array copy. | Safe array copy; annotation ownership still depends on later registration. |
| `TypeConstant.java:1014` | Type array copy. | Safe logical type-array copy. |
| `ObjectHandle.java:46` | `ObjectHandle implements Cloneable`. | Runtime handle masking/reveal mechanism. It preserves the underlying handle value while changing visible `TypeComposition`; not a pool adoption path. |
| `ObjectHandle.java:64` | `ObjectHandle.cloneAs(...)`. | Same-owner view/mask operation. Ownership diagnostics should catch accidental cross-container compositions. |
| `Proxy.java:338` | Object-handle array copy. | Defensive array copy for proxy arguments/fields; not owner transfer by itself. |
| `xRTDelegate.java:154` | Object-handle content array copy. | Defensive array copy for delegate storage; elements remain same operation/owner values. |
| `xRTFunction.java:253` | Argument array copy/resize. | Per-call defensive array copy; not owner transfer. |
| `xTuple.java:139` | Tuple handle array copy. | Per-handle defensive copy before mutation. |
| `xTuple.java:366` | Tuple handle array copy. | Same. |
| `xTuple.java:387` | Tuple handle array copy. | Same. |
| `xTuple.java:558` | Type array copy. | Logical type-array copy. |
| `xTuple.java:728` | Type array copy. | Logical type-array copy. |

Compiler-only clone sites under `javatools/src/main/java/org/xvm/compiler`
are intentionally excluded from this runtime table. They remain important for
incremental compiler correctness, but they do not directly answer whether two
runtime containers can share handles, templates, or constant-pool runtime state.

## Static And Global State Adjacent To Adoption

The adoption hazards above combine badly with process-global caches. This
branch already removes the mutable native-template `INSTANCE` fields and the
runtime-template static metadata caches documented in
[fixed-in-this-branch.md](fixed-in-this-branch.md).

The remaining runtime/container-adjacent global state worth watching is:

- `ConstantPool.s_tloPool`: hidden current-pool context. It is scoped with
  `withPool(...)`, but any missing cleanup leaks ambient owner context on a
  reused thread. Prefer explicit owner parameters where practical.
- `TypeConstant.s_setRecursions`: process-global diagnostic set. It should not
  affect semantics, but it can hide repeated recursion reports across
  containers and must stay diagnostic-only. This branch keeps the old
  process-wide suppression behavior but backs it with a concurrent set so
  parallel type checks cannot corrupt a plain `HashSet`.
- JIT-generated name caches on adopted type/signature constants are cleared or
  avoided by fresh-constructor adoption in this branch. Broader JIT policy is
  tracked in [jit-implications.md](jit-implications.md).
- Runtime op caches such as `JumpVal.m_ahCase` and `JumpVal_N.m_aahCases`:
  these are decoded-op runtime handle caches. They are not `Constant.adoptedBy`
  sites, but they are owner-shaped and should remain on the must-audit backlog
  if op graphs are reused across containers.

## Assertion And Diagnostic Plan

The fastest way to find remaining broken adoption is to fail at the owner
boundary instead of letting a wrong handle surface later.

Recommended guards:

- `HandleConstant.copyForAdoption(...)` throws when an already-owned live
  `ObjectHandle` constant is moved to another pool.
- `ConstantPool.register(...)` now optionally asserts that the adopted copy
  does not retain forbidden runtime-owner fields. The opt-in
  `ConstantAdoptionValidator` is enabled by
  `-Dxvm.asm.validateConstantAdoption=true` and checks for copied owner/runtime
  references, live `ObjectHandle` values, runtime templates/compositions,
  mutable `Atomic*`, lock objects, Java references, thread-local cells, and
  mutable collections that are identical between source and adopted copy.
  `HandleConstant` remains the one legacy exception: a fresh unowned
  `HandleConstant` can still be registered once in the current pool, while a
  second cross-pool adoption of the already-owned live handle still throws in
  `HandleConstant.copyForAdoption(...)`.
- `OwnershipDiagnostics` should keep validating handles at runtime boundaries
  such as `mgmt.Container.invoke`, because that catches wrong-owner values even
  when the source is not constant adoption.

The validator is deliberately narrow. It does not reject shared logical child
`Constant` objects during adoption, because `ConstantPool.register(...)`
recursively adopts those after the outer constant is constructed. It rejects
only helper/runtime references that are never serialized constant identity.

The validator reports:

- source constant class and pool owner;
- adopted constant class and pool owner;
- same-reference transient fields copied by clone;
- any handle/composition/container owner found under those fields.

Once the diagnostic set is understood for more call paths, specific findings
can be promoted from opt-in diagnostics to hard assertions.

## Current Must-Fix Conclusion

For this PR's runtime/container goal, adoption is not inherently wrong. It is
necessary. The broken part is shallow-copying non-logical runtime state while
changing the pool owner.

Already fixed in this branch:

- native template `INSTANCE` publication and static runtime-template metadata;
- enum raw-handle publication paths;
- `SingletonConstant` lifecycle-state sharing across adopted constants;
- `FSNodeConstant` and `FileStoreConstant` runtime handle copying;
- `TypeConstant` transient/helper state copied by base clone adoption;
- `ParameterizedTypeConstant` resolver/JIT helper state copied by clone
  adoption;
- `SignatureConstant` comparison/JIT helper state copied by clone adoption;
- `TypeParameterConstant` recursive-comparison helper state copied by clone
  adoption;
- cross-pool adoption of already-owned `HandleConstant` live runtime handles;
- named and type-backed identity constants that previously inherited the
  family shallow-clone path. Path identities now rebuild with target-owned
  parents, `TypedefConstant` drops resolved-state cache, type-backed identities
  require target-shareable type keys, and `NativeRebaseConstant` fails closed.
- `EnumValueConstant` now has its own copy hook so enum singleton adoption keeps
  the enum-value subclass and ordinal/operator behavior while still getting
  fresh owner-local singleton runtime state.
- the base `Constant` fallback no longer uses `Object.clone()`, and `Constant`
  no longer implements `Cloneable`.
- opt-in adoption validation at `ConstantPool.register(...)`, with a focused
  regression test that proves a bad explicit copy hook is rejected when
  diagnostics are enabled. The validator also rejects explicit copy hooks that
  reuse arbitrary live `ObjectHandle` references, while preserving the existing
  first-registration `HandleConstant` behavior.

The focused regression test is
`javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java`. It directly
exercises the adoption boundary and would fail against the old shallow-clone
behavior by observing shared helper cells or by allowing cross-pool handle
adoption. The validator-specific tests are branch-side guard tests; they depend
on the new `ConstantAdoptionValidator` class and therefore are not copied into
old `master` unchanged.

Regression evidence from 2026-08-21:

- Current branch:
  `./gradlew :javatools:test --tests org.xvm.asm.ConstantAdoptionTest --rerun-tasks --no-build-cache --console=plain`
  passed.
- Current branch:
  `./gradlew :javatools:test --rerun-tasks --no-build-cache --console=plain`
  passed.
- Current branch:
  `CI=true ./gradlew :manualTests:runDirectSequenceStress -PsameJvmIterations=2 -PsameJvmModules=TestProps --rerun-tasks --no-build-cache --console=plain`
  passed.
- Current branch:
  `CI=true ./gradlew :manualTests:runParallelStress -PstressIterations=2 -PstressModules=TestProps --rerun-tasks --no-build-cache --console=plain`
  passed.
- Detached `master` worktree at commit `145f12f51`, with only
  `ConstantAdoptionTest.java` copied in, failed all five tests:
  `adoptedTypeConstantClearsOwnerLocalHelperState`,
  `adoptedParameterizedTypeConstantGetsFreshSubclassHelpers`,
  `adoptedSignatureConstantGetsFreshComparisonAndJitHelpers`,
  `adoptedTypeParameterConstantGetsFreshReentryCell`, and
  `ownedHandleConstantCannotMoveToAnotherPool`.

Compiler `clone()` cleanup, `Component.cloneBody(...)`, `Parameter.clone()`,
and compiler AST clone hazards should be handled with the incremental compiler
work, not mixed into this runtime-owner PR.
