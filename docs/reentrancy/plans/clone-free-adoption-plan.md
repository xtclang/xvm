# Clone-Free Constant Adoption Plan

This plan is grounded in the current `lagergren/lazy-instance` branch. It covers
only the long-term `ConstantPool` adoption design: removing the base
`Object.clone()` ownership-transfer pattern from `Constant.adoptedBy(...)` and
replacing it with explicit, owner-safe adoption/copy construction.

## Problem Statement

`ConstantPool.register(...)` accepts constants from other pools. If the constant
is logically shareable but structurally owned by a different pool, the current
code adopts it into the target pool. That operation is necessary: a logical XVM
constant can appear in multiple constant pools, but a Java `Constant` object has
pool-local structure state such as parent, index, reference count, lookup table
membership, locators, and owner-derived runtime/helper caches.

The unsafe part is the implementation. The base adoption path in
`javatools/src/main/java/org/xvm/asm/Constant.java` uses `Object.clone()`:

```java
protected Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) super.clone();
    that.setContaining(pool);
    that.resetRefs();
    return that;
}
```

This copies every reference field before assigning the destination owner.
`transient` does not affect cloning. `final` does not help either: a final field
can point at a mutable `AtomicReference`, `StampedLock`, `ThreadLocal`, runtime
handle, mutable map, or lazy/cache object, and clone will copy that reference.

The current branch has already found concrete failures in that pattern:

- `SingletonConstant` used a final `AtomicReference<InitState>`. Shallow
  adoption copied the state cell, so an adopted singleton could reuse another
  pool/container's runtime singleton lifecycle state.
- `FSNodeConstant` and `FileStoreConstant` carried transient runtime handles.
  Shallow adoption could carry a source-owner handle into a target pool.
- `ParameterizedTypeConstant`, `SignatureConstant`, and
  `TypeParameterConstant` carried final helper cells (`StampedLock` or
  `TransientThreadLocal`) and JIT/helper caches that are not logical constant
  value.
- `TypeConstant` carries relation/type-info/JIT/recursion helper state that
  must be reset when pool ownership changes.

The branch hardens known runtime-relevant cases and now makes the base API fail
closed through one final owner-transfer wrapper: `Constant.adoptedBy(...)`
constructs an `AdoptionContext`, delegates to `copyForAdoption(...)`, validates
that the returned constant is owned by the requested pool, and resets reference
counts. The default `copyForAdoption(...)` path rejects adoption unless a
new subclass implements `copyForAdoption(...)`. `Constant` no longer implements
`Cloneable`, and there is no `cloneForAdoption(...)` helper. That prevents a
new mutable helper field from silently inheriting owner-transfer clone behavior,
and it prevents subclasses from bypassing the common owner/ref checks with
ad-hoc `adoptedBy(...)` overrides.

The long-term defect this plan targets is now removed for `Constant` adoption:
`Object.clone()` is no longer an ownership-transfer mechanism. The remaining
clone work is in other owner-bearing object families such as component/method
copying and runtime handle view copies.

## Current Mechanism

The adoption entry points are:

| Site | Current role |
| --- | --- |
| `Constant.adoptedBy(ConstantPool)` | Final owner-transfer wrapper. Creates `AdoptionContext`, delegates to `copyForAdoption(...)`, checks destination ownership, and resets refs. |
| `Constant.copyForAdoption(AdoptionContext)` | Subclass/family hook for logical copy construction. The default implementation fails closed. |
| `ConstantPool.register(T)` | Adopts a foreign constant when `constant.getContaining() != this`. |
| `ConstantPool.register(T)` locator path | Adopts a foreign locator constant before publishing it in locator lookup maps. |
| `ConstantAdoptionValidator` | Opt-in diagnostic guard controlled by `xvm.asm.validateConstantAdoption`. |

The intended semantic contract is:

> Construct an equivalent logical constant in the destination pool.

The remaining transitional mechanical contract is:

> For an explicitly reviewed family, copy all Java fields, then repair the owner
> and known caches.

Those are not the same contract. The first is safe and reviewable. The second is
open-ended and fails whenever the reviewed family later grows state that is not
serialized logical constant value. The current branch narrows the blast radius by
making the review decision explicit; the clone-free plan removes the mechanical
hazard.

## Size Estimate

Current branch inventory:

| Item | Count |
| --- | ---: |
| `Constant` descendants under `org.xvm.asm.constants` | 88 |
| Abstract family bases in that set | 13 |
| Concrete constant classes in that set | 75 |
| Classes currently overriding `adoptedBy(...)` | 0 |
| Classes currently overriding `copyForAdoption(...)` | 74 |
| Concrete classes still relying on a default-clone policy | 0 |

The migration is broad but not conceptually deep. The direct source blast radius
for a complete clone-free adoption model is likely:

- `Constant`, `ConstantPool`, and adoption tests;
- all 74 current hook classes, already converted to `copyForAdoption(...)` in
  this branch;
- `Annotation`, which is a `Constant` subclass outside the
  `org.xvm.asm.constants` package and now also uses an explicit hook;
- 13 abstract family bases, to place shared family adoption rules where useful;
- up to 75 concrete leaf constants, either by explicit copy/adoption
  implementation or by inheriting a reviewed family implementation;
- stress/validator tests and a source-shape test that prevents fallback clone
  from returning.

The change should be staged. A single PR that flips `Constant.adoptedBy(...)` to
abstract immediately would create useful compile errors, but it would be too
large for review. The safer path is to introduce a transition API, migrate
families, then remove the fallback after the tree is converted.

## Exact Constant Inventory

The table below is generated from the current branch by scanning
`javatools/src/main/java/org/xvm/asm/Constant.java` and
`javatools/src/main/java/org/xvm/asm/constants/*.java` for `class`, `extends`,
and `adoptedBy(...)`.

Risk buckets:

- `P0`: proven runtime/helper-state hazard already fixed or guarded in this
  branch, but must remain explicit in the clone-free design.
- `P1`: owner/cache-sensitive family. The current branch has reset/guard
  mechanisms, but the long-term API must make the behavior explicit.
- `P2`: currently appears logical-only, but still must stop inheriting a base
  shallow clone.
- `P3`: low immediate runtime risk; mostly immutable value/condition data or
  defensive arrays, but still needs an explicit adoption decision.
- `P4`: frame/runtime-shaped. Must distinguish serialized logical frame
  constants from live runtime handles.

| Class | Parent | Current adoption behavior | Risk |
| --- | --- | --- | --- |
| `AbstractDependantChildTypeConstant` | `AbstractDependantTypeConstant` | abstract helper; no default clone | P1 concrete dependant children have explicit hooks |
| `AbstractDependantTypeConstant` | `TypeConstant` | abstract helper; no default clone | P1 concrete dependant types have explicit hooks |
| `AccessTypeConstant` | `TypeConstant` | explicit clone-free hook | P1 single-child type wrapper fixed in this branch |
| `AllCondition` | `MultiCondition` | explicit clone-free hook | P3 logical condition array; simulation scratch fixed in this branch |
| `AnnotatedTypeConstant` | `TypeConstant` | explicit clone-free hook | P1 annotation children and type cache fixed in this branch |
| `AnonymousClassTypeConstant` | `AbstractDependantChildTypeConstant` | explicit clone-free hook | P1 dependant child type fixed in this branch |
| `AnyCondition` | `MultiCondition` | explicit clone-free hook | P3 logical condition array; simulation scratch fixed in this branch |
| `ArrayConstant` | `ValueConstant` | explicit clone-free hook | P3 composite value container fixed in this branch |
| `BFloat16Constant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `ByteConstant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `CastTypeConstant` | `IntersectionTypeConstant` | explicit fail-closed hook | P1 transient compiler/JIT type marker |
| `CharConstant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `ChildClassConstant` | `PseudoConstant` | explicit clone-free hook | P2 pseudo path fixed in this branch |
| `ClassConstant` | `NamedConstant` | explicit clone-free hook | P2 logical identity/path fixed in this branch |
| `ConditionalConstant` | `Constant` | fails closed; concrete leaves explicit | P3 condition family base; private simulation scratch |
| `DecimalAutoConstant` | `ValueConstant` | explicit clone-free hook | P3 delegated decimal value fixed in this branch |
| `DecimalConstant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `DecoratedClassConstant` | `IdentityConstant` | explicit clone-free hook | P2 type-backed identity fixed in this branch |
| `DeferredValueConstant` | `PseudoConstant` | explicit fail-closed hook | P2 unresolved compiler placeholder fixed in this branch |
| `DifferenceTypeConstant` | `RelationalTypeConstant` | explicit clone-free hook | P1 relational type fixed in this branch |
| `DynamicFormalConstant` | `FormalConstant` | explicit clone-free hook | P1/P2 compiler register state fixed in this branch |
| `EnumValueConstant` | `SingletonConstant` | explicit clone-free hook | P0 enum singleton runtime-state and subclass preservation fixed in this branch |
| `ExpressionConstant` | `PseudoConstant` | explicit fail-closed hook | P2 compile-time AST placeholder fixed in this branch |
| `FPNConstant` | `ValueConstant` | explicit clone-free hook | P3 byte-array-backed value fixed in this branch |
| `FSNodeConstant` | `ValueConstant` | explicit clone-free override | P0 runtime handle/path cache |
| `FileStoreConstant` | `ValueConstant` | explicit clone-free override | P0 runtime handle |
| `Float128Constant` | `ValueConstant` | explicit clone-free hook | P3 byte-array-backed value fixed in this branch |
| `Float16Constant` | `FloatConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `Float32Constant` | `FloatConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `Float64Constant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `Float8e4Constant` | `FloatConstant` | explicit clone-free hook | P3 raw-bit scalar value fixed in this branch |
| `Float8e5Constant` | `FloatConstant` | explicit clone-free hook | P3 raw-bit scalar value fixed in this branch |
| `FloatConstant` | `ValueConstant` | abstract helper; no default clone | P3 concrete float values have explicit hooks |
| `FormalConstant` | `NamedConstant` | abstract helper; no default clone | P2 concrete formal identities have explicit hooks |
| `FormalTypeChildConstant` | `PropertyConstant` | explicit clone-free hook | P2/P1 property metadata cache inheritance fixed in this branch |
| `FrameDependentConstant` | `Constant` | fails closed; no default clone | P4 frame-dependent family base |
| `HandleConstant` | `FrameDependentConstant` | explicit clone-free guard override | P0 live runtime handle |
| `IdentityConstant` | `Constant` | abstract helper; no default clone | P2 concrete identities have explicit hooks |
| `ImmutableTypeConstant` | `TypeConstant` | explicit clone-free hook | P1 single-child type wrapper fixed in this branch |
| `InnerChildTypeConstant` | `AbstractDependantChildTypeConstant` | explicit clone-free hook | P1 dependant child type fixed in this branch |
| `IntConstant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `IntersectionTypeConstant` | `RelationalTypeConstant` | explicit clone-free hook | P1 relational type fixed in this branch |
| `KeywordConstant` | `PseudoConstant` | explicit clone-free hook | P2 per-format keyword singleton fixed in this branch |
| `LiteralConstant` | `ValueConstant` | explicit clone-free hook | P3 parsed literal cache fixed in this branch |
| `MapConstant` | `ValueConstant` | explicit clone-free hook | P3 composite value container fixed in this branch |
| `MatchAnyConstant` | `ValueConstant` | explicit clone-free hook | P3 type-keyed sentinel shell fixed in this branch |
| `MethodBindingConstant` | `FrameDependentConstant` | explicit clone-free hook | P4 serialized frame-dependent constant fixed in this branch |
| `MethodConstant` | `IdentityConstant` | explicit clone-free hook | P1 JIT-name/type cache owner policy fixed in this branch |
| `ModuleConstant` | `IdentityConstant` | explicit clone-free hook | P2 logical identity/path fixed in this branch |
| `MultiCondition` | `ConditionalConstant` | abstract helper for explicit condition hooks | P3 condition-family base |
| `MultiMethodConstant` | `NamedConstant` | explicit clone-free hook | P2 logical identity/path fixed in this branch |
| `NamedCondition` | `ConditionalConstant` | explicit clone-free hook | P3 logical condition; simulation scratch fixed in this branch |
| `NamedConstant` | `IdentityConstant` | abstract helper; no default clone | P2 concrete named identities have explicit hooks |
| `NativeRebaseConstant` | `ClassConstant` | explicit fail-closed hook | P2 runtime-only identity facade fixed in this branch |
| `NotCondition` | `ConditionalConstant` | explicit clone-free hook | P3 logical condition; simulation scratch fixed in this branch |
| `PackageConstant` | `NamedConstant` | explicit clone-free hook | P2 logical identity/path fixed in this branch |
| `ParameterizedTypeConstant` | `TypeConstant` | explicit override | P0 helper lock/JIT cache |
| `ParentClassConstant` | `PseudoConstant` | explicit clone-free hook | P2 pseudo path fixed in this branch |
| `PendingTypeConstant` | `TypeConstant` | explicit fail-closed hook | P1 mutable compiler placeholder fixed in this branch |
| `PresentCondition` | `ConditionalConstant` | explicit clone-free hook | P3 logical condition; simulation scratch fixed in this branch |
| `PropertyClassTypeConstant` | `AbstractDependantTypeConstant` | explicit clone-free hook | P1 property type metadata cache fixed in this branch |
| `PropertyConstant` | `FormalConstant` | explicit clone-free hook | P1 JIT-name/type/property-info cache owner policy fixed in this branch |
| `PseudoConstant` | `Constant` | fails closed; concrete leaves explicit | P2 pseudo-family base |
| `PureIdentityConstant` | `IdentityConstant` | explicit clone-free hook | P2 type-backed identity fixed in this branch |
| `RangeConstant` | `ValueConstant` | explicit clone-free hook | P3 composite value endpoints fixed in this branch |
| `RecursiveTypeConstant` | `TerminalTypeConstant` | explicit clone-free hook | P1 recursive typedef subclass fixed in this branch |
| `RegExConstant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `RegisterConstant` | `FrameDependentConstant` | explicit clone-free hook | P1/P4 compiler register state fixed in this branch |
| `RelationalTypeConstant` | `TypeConstant` | abstract helper; no default clone | P1 concrete relational types have explicit hooks |
| `ServiceTypeConstant` | `TypeConstant` | explicit clone-free hook | P1 single-child type wrapper fixed in this branch |
| `SignatureConstant` | `PseudoConstant` | explicit override | P0 helper lock/JIT cache |
| `SingletonConstant` | `ValueConstant` | explicit override | P0 runtime singleton lifecycle state |
| `StringConstant` | `ValueConstant` | explicit clone-free hook | P3 immutable scalar value fixed in this branch |
| `TerminalTypeConstant` | `TypeConstant` | explicit clone-free hook | P1 type leaf fixed in this branch |
| `ThisClassConstant` | `PseudoConstant` | explicit clone-free hook | P2 pseudo path fixed in this branch |
| `TypeConstant` | `Constant` | abstract helper; no default clone | P1 type-family base; owner reset remains for other owner-change paths |
| `TypeParameterConstant` | `FormalConstant` | explicit override | P0 reentrancy helper cell |
| `TypeSequenceTypeConstant` | `TypeConstant` | explicit clone-free hook | P1 stateless formal marker fixed in this branch |
| `TypedefConstant` | `NamedConstant` | explicit clone-free hook | P2 logical identity/path plus resolved-state cache fixed in this branch |
| `UInt8ArrayConstant` | `ValueConstant` | explicit clone-free hook | P3 byte-array-backed value fixed in this branch |
| `UnionTypeConstant` | `RelationalTypeConstant` | explicit clone-free hook | P1 relational type fixed in this branch |
| `UnresolvedNameConstant` | `PseudoConstant` | explicit fail-closed hook | P2 unresolved compiler placeholder fixed in this branch |
| `UnresolvedTypeConstant` | `TypeConstant` | explicit fail-closed hook | P1 mutable compiler placeholder fixed in this branch |
| `ValueConstant` | `Constant` | abstract helper; no default clone | P3 concrete values have explicit hooks |
| `VersionConstant` | `LiteralConstant` | explicit clone-free hook | P3 literal subclass fixed in this branch |
| `VersionMatchesCondition` | `ConditionalConstant` | explicit clone-free hook | P3 logical condition; simulation scratch fixed in this branch |
| `VersionedCondition` | `ConditionalConstant` | explicit clone-free hook | P3 logical condition; simulation scratch fixed in this branch |
| `VirtualChildTypeConstant` | `AbstractDependantChildTypeConstant` | explicit clone-free hook | P1 dependant child type fixed in this branch |

## Clone Uses That Are Dangerous For Runtime Reentrancy

Not every `.clone()` in the tree has the same risk. Array clone used as a
defensive container copy is not the same problem as `Object.clone()` on an
owner-bearing object.

| Clone family | Runtime reentrancy classification | Why |
| --- | --- | --- |
| `Constant.adoptedBy(...)` | Must fix long term | Changes pool ownership while copying arbitrary subclass references. This is the central adoption bug. |
| `ObjectHandle.cloneAs(...)` | Must audit, partly hardened in this branch | Runtime handle view/mask copies can preserve underlying handle and field references. Safe only when the new view composition has the same owner or the code explicitly validates shareability. |
| `javatools_jitbridge` frozen array clone | Must audit for JIT runtime | JIT bridge objects can copy delegate/context/cache state. This is outside the interpreter adoption PR but relevant if JIT runtime state is shared. |
| `Component.cloneBody(...)`, `Component.Contribution.clone()`, `MethodStructure.Source.clone()` | Must audit for incremental compiler/linker; not the main runtime-container adoption path | These are structural/module-copy clones and can preserve owner/caches during compiler/linker mutation. They matter for reentrant compilation, but not as the immediate `ConstantPool.register(...)` adoption mechanism. |
| Compiler/parser/AST clones | Must audit for reentrant/incremental compiler; separate project | AST clone paths can copy semantic caches and parent/stage state. They do not directly cause two runtime containers to share native handles. |
| `Constant[]`, `TypeConstant[]`, `ObjectHandle[]`, byte-array clones | Usually should-fix/clarify, not immediate must-fix | These are mostly defensive container copies. They do not clone element objects. They become unsafe only if later code treats the container copy as deep owner transfer or mutates shared elements without registration/copy-on-write. |

## Proposed API Designs

### Option A: Final Adoption Wrapper Plus Abstract Copy Hook

Keep the external call shape used by `ConstantPool.register(...)`, but make the
base method final and delegate to an explicit copy hook:

```java
protected final Constant adoptedBy(ConstantPool pool) {
    var target = copyForAdoption(new AdoptionContext(pool));
    target.resetRefs();
    return target;
}

protected abstract Constant copyForAdoption(AdoptionContext context);
```

`AdoptionContext` owns helper methods such as:

```java
final class AdoptionContext {
    ConstantPool pool();
    <T extends Constant> T register(T constant);
    TypeConstant[] copyTypes(TypeConstant[] types);
    Constant[] copyConstants(Constant[] constants);
}
```

Benefits:

- No subclass can accidentally call `super.clone()` through the base method.
- The owner is explicit and available during construction.
- Reviewers can inspect each subclass's copied fields.
- `ConstantPool.register(...)` does not need a large call-site rewrite.

Costs:

- Every concrete constant class, or a reviewed abstract family base, must
  implement the hook.
- Some existing factory methods may be easier to reuse than direct constructors.

This is the recommended migration target.

### Option B: Re-Adopt Through ConstantPool Factories

Instead of copy constructors, each subclass implements adoption by calling the
target pool's existing `ensure...` factory:

```java
protected Constant copyForAdoption(AdoptionContext context) {
    var pool = context.pool();
    return pool.ensureParameterizedTypeConstant(
            context.register(m_constType),
            context.copyTypes(m_atypeParams));
}
```

Benefits:

- Reuses existing interning and normalization rules.
- Avoids hand-duplicating constructor validation.
- Often returns the already-interned destination constant immediately.

Costs:

- Some factories do not exist or do not expose the right logical fields.
- Factory calls can change registration order unless carefully designed.
- Recursive registration must not publish half-adopted constants.

This is attractive for value/type families once registration-order tests exist.

### Option C: Temporary Explicit Policy Marker

As a transition only, add a marker for classes that are intentionally logical-only
under the current field set:

```java
protected AdoptionPolicy adoptionPolicy() {
    return AdoptionPolicy.LOGICAL_FIELDS_ONLY;
}
```

The base fallback would still be rejected for owner-bearing fields by tests and
validator, and the marker would be removed before the final clone-free flip.

Benefits:

- Lets the tree move in reviewable slices.
- Makes every default-adoption class declare intent before source behavior
  changes.

Costs:

- It is not the final design if it still permits shallow clone.
- It can become a new footgun unless the final PR removes the fallback.

Use this only to make the migration reviewable.

## Recommended Design

Use Option A as the architectural endpoint, with Option B inside implementations
where an existing pool factory is the clearest expression of the logical value.
Avoid a permanent "logical-only shallow clone" escape hatch.

The final `Constant` shape should:

- remove `Cloneable` from `Constant`;
- make `adoptedBy(ConstantPool)` final, or keep it package/protected but
  non-overridable;
- call a subclass/family `copyForAdoption(AdoptionContext)` method;
- reject `null` target pools;
- reject adoption of non-shareable `TypeConstant` values before copying;
- preserve `resetRefs()` and destination `setPosition(...)` semantics in
  `ConstantPool.register(...)`;
- continue to register logical child constants in the destination pool;
- never copy runtime handles, lock objects, atomic cells, thread-local cells,
  mutable maps, JIT type-system names, in-progress sentinels, or container/frame
  references.

## Semantic Equivalence

The required external behavior is unchanged:

- equal logical constants still intern to one target-pool object;
- source-pool constants remain owned by the source pool;
- destination-pool constants have the same serialized value and comparison/hash
  behavior as before;
- child constants are registered or resolved in the destination pool as before;
- locators continue to resolve the same logical target constant;
- adoption still returns an existing target-pool constant when one is already
  interned.

The behavior that intentionally changes:

- runtime/helper state is never copied as part of adoption;
- already-owned live `HandleConstant` movement remains illegal;
- subclasses without an explicit adoption implementation fail at compile time or
  fail closed during the transition instead of silently shallow-copying.

The current branch already demonstrates the semantic shape for high-risk cases:
fresh owner-local singleton state, fresh filesystem logical constants with empty
runtime/path caches, fresh wrappers for first-registration runtime handles,
fresh helper locks/reentrancy cells, and target-owner recomputation of derived
caches.

## Performance Equivalence

Cross-pool adoption already allocated in `master`: `Object.clone()` created a
new object and `ConstantPool.register(...)` interned it. Explicit construction
keeps the same one-new-object adoption shape and should not add steady-state
runtime cost.

Expected performance properties:

- no extra cost when registering an already-interned equivalent constant in the
  target pool, because the lookup still returns the existing object;
- no steady-state runtime cost in hot execution paths after constants are
  registered;
- no loss of old caching behavior, because owner-local caches are still retained
  in the owner that computes them;
- no cross-owner cache reuse, which is the bug being removed;
- possible small construction-time cost if a copy constructor uses
  `Arrays.copyOf(...)` where clone used to copy an array. That is equivalent
  allocation and often clearer in profiling;
- possible performance win where adoption uses a target pool factory and avoids
  constructing a duplicate object if the target already interns an equivalent
  constant.

Verification should compare:

- constant-pool sizes before/after compiling representative modules;
- direct sequence stress time with one JVM versus master and versus current
  branch;
- allocation counts in constant registration/adoption micro workloads;
- no increase in runtime container sharing diagnostics.

## Test Strategy

### Source-Shape Tests

Add a test that enumerates every concrete `Constant` subclass and fails unless
the class or an approved abstract parent implements the new explicit adoption
contract. This test replaces the current "remember to override" convention with
an executable rule.

Add a second source-shape test that fails if `Constant` implements `Cloneable` or
if `Constant.adoptedBy(...)` calls `super.clone()`.

### Focused Reproducers

Keep and extend the current `ConstantAdoptionTest` pattern:

- warm source singleton state, adopt into target pool, assert target state cell
  is distinct and uninitialized;
- warm `FSNodeConstant` path cache and runtime handle, adopt into target pool,
  assert the destination recomputes the path constant in the destination pool and
  has no source runtime handle;
- warm `FileStoreConstant` runtime handle, adopt into target pool, assert no
  handle sharing;
- warm `ParameterizedTypeConstant` resolver/JIT helper state, adopt, assert a
  fresh helper lock and no copied JIT cache;
- warm `SignatureConstant` comparison/JIT helper state, adopt, assert a fresh
  lock and no copied JIT cache;
- warm `TypeParameterConstant` reentry helper, adopt, assert a fresh helper cell;
- attempt second adoption of an already-owned `HandleConstant`, assert failure.

Add one synthetic bad constant test while the transition API exists. The test
should create a constant with a final mutable helper object, try to adopt it
through the legacy fallback, and prove the fallback is unavailable or rejected.
This is the minimal "master is bad, clone-free design is safe" proof.

### Family Equivalence Tests

For each migrated family:

- create representative constants in source pool;
- adopt/register them in a target pool;
- assert logical equality, comparison ordering, hash behavior, format, locator,
  `getValueString()`, and assembly/disassembly round trip are unchanged;
- assert every underlying constant is owned by or shared with the target pool;
- assert warmed owner-local caches are not shared by identity between source and
  target.

### Stress And Runtime Diagnostics

Run existing same-JVM and parallel stress with:

```text
-Dxvm.asm.validateConstantAdoption=true
-Dxvm.asm.validateConstantPoolCurrentScope=true
-Dxvm.asm.validateConstantPoolLateRegistration=true
-Dxvm.runtime.validateOwnership=true
```

Stress ideas:

- start multiple runtime containers from the same module repository in parallel;
- run known manual test modules repeatedly in direct same-JVM mode;
- create two pools that adopt equivalent constants concurrently and assert target
  interning returns one target object without sharing source helper state;
- run reflection/type-template paths that create `HandleConstant` annotation
  values and assert second adoption is rejected;
- exercise JIT name generation in two separate JIT type systems once JIT tests
  are part of this work.

### Performance Tests

Add a small benchmark or timed test harness around:

- repeated cross-pool registration of primitive/value constants;
- parameterized type and signature adoption;
- module load/startup with runtime ownership validation disabled;
- module load/startup with diagnostics enabled.

The acceptance criterion is not "no allocation at all"; adoption already
allocates. The criterion is that target-pool interning and owner-local cache
behavior match current branch semantics and no hot runtime path repeatedly
recomputes values that master cached.

## PR Split Plan

### PR 1: Add Clone-Free Adoption Contract In Parallel

Scope:

- add `AdoptionContext`;
- add `copyForAdoption(AdoptionContext)` or equivalent hook;
- make `Constant.adoptedBy(...)` the final owner-transfer wrapper;
- keep legacy fallback temporarily;
- add source-shape tests that prove special cases use the hook and the wrapper
  remains final;
- keep `ConstantAdoptionValidator`.

Review goal: establish the target architecture without changing all constants at
once.

Current branch note: the final wrapper, `AdoptionContext`, `copyForAdoption(...)`
hook, and special-case source-shape test are implemented here. A later standalone
PR can lift this commit out with no broad family migration.

### PR 2: Convert Existing High-Risk Overrides

Scope:

- migrate `SingletonConstant`, `FSNodeConstant`, `FileStoreConstant`,
  `DynamicFormalConstant`, `FormalTypeChildConstant`, `HandleConstant`, `MethodConstant`,
  `ParameterizedTypeConstant`, `PropertyConstant`, `SignatureConstant`, and
  `TypeParameterConstant`, `RegisterConstant`, and `MethodBindingConstant` from
  ad-hoc overrides to the new adoption hook;
- keep current branch tests as equivalence tests.

Current branch note: `FSNodeConstant`, `FileStoreConstant`,
`DynamicFormalConstant`, `FormalTypeChildConstant`, `HandleConstant`,
`MethodBindingConstant`, `MethodConstant`, `PropertyConstant`, and
`RegisterConstant` already use clone-free construction inside
`copyForAdoption(...)`; the other P0/P1 special cases use the same hook for
fresh helper/runtime state. The later API migration should preserve that
behavior while broadening it to family bases.

Review goal: prove the new API expresses already-known fixes without behavior or
performance regressions.

### PR 3: Convert `TypeConstant` Base And Type Families

Scope:

- migrate `TypeConstant` owner reset to explicit copy/adoption construction;
- convert type-family classes:
  `AbstractDependantChildTypeConstant`, `AbstractDependantTypeConstant`,
  `AnnotatedTypeConstant`, `AnonymousClassTypeConstant`,
  `InnerChildTypeConstant`, `PendingTypeConstant`, `PropertyClassTypeConstant`,
  `RecursiveTypeConstant`, `RelationalTypeConstant`, `TypeSequenceTypeConstant`,
  `UnresolvedTypeConstant`, and `VirtualChildTypeConstant`;
- keep the `TerminalTypeConstant` branch fix, which reconstructs the type leaf
  from its defining identity and rejects unrelated foreign identities;
- keep the `AccessTypeConstant`, `ImmutableTypeConstant`, and
  `ServiceTypeConstant` branch fixes, which reconstruct logical single-child
  wrappers and reject unrelated foreign child types;
- keep the `UnionTypeConstant`, `IntersectionTypeConstant`, and
  `DifferenceTypeConstant` branch fixes, which reconstruct logical relational
  type expressions and reject unrelated foreign children;
- keep the `VirtualChildTypeConstant`, `InnerChildTypeConstant`,
  `AnonymousClassTypeConstant`, and `PropertyClassTypeConstant` branch fixes,
  which reconstruct dependant child/property type shells, preserve target-pool
  interning, and rebuild child-structure or property-info caches locally;
- keep the `RecursiveTypeConstant` branch fix, which preserves the recursive
  typedef subclass while still adopting/interning the typedef identity through
  the destination pool;
- keep the `Annotation` and `AnnotatedTypeConstant` branch fixes, which copy
  annotation parameter containers, reject already-owned runtime handle params,
  reconstruct the annotated type shell, and let the destination pool rebuild
  the derived annotation-type cache;
- keep the `TypeSequenceTypeConstant`, `PendingTypeConstant`, and
  `UnresolvedTypeConstant` branch fixes. The type sequence marker is stateless
  and can be reconstructed directly; pending and unresolved compiler
  placeholders fail closed because they are not completed pool metadata;
- keep the `CastTypeConstant` branch guard, which rejects adoption because cast
  types are transient compiler/JIT markers that cannot be assembled into a pool;
- preserve existing cache reset behavior exactly.

Review goal: remove the largest owner-cache family from shallow clone fallback.

### PR 4: Convert Identity, Named, Formal, And Pseudo Constants

Scope:

- convert `IdentityConstant`, `NamedConstant`, `FormalConstant`,
  `PseudoConstant`, and leaves:
  `ChildClassConstant`, `ClassConstant`, `DecoratedClassConstant`,
  `DeferredValueConstant`, `DynamicFormalConstant`, `ExpressionConstant`,
  `KeywordConstant`, `ModuleConstant`, `MultiMethodConstant`,
  `NativeRebaseConstant`, `PackageConstant`, `ParentClassConstant`,
  `PureIdentityConstant`, `ThisClassConstant`, `TypedefConstant`, and
  `UnresolvedNameConstant`;
- keep the `MethodConstant.copyForAdoption(...)` branch fix, which already
  drops method type and JIT-name caches;
- keep the `PropertyConstant.copyForAdoption(...)` and
  `FormalTypeChildConstant.copyForAdoption(...)` branch fixes, which already
  drop property metadata and JIT-name caches while preserving each format;
- keep the `DynamicFormalConstant.copyForAdoption(...)` branch fix, which
  records serialized register identity, rejects non-shared foreign register
  types, and drops the transient compiler `Register`.
- keep the pseudo-family branch fixes. `ThisClassConstant`,
  `ParentClassConstant`, and `ChildClassConstant` rebuild logical auto-narrowing
  paths with target-owned child identities; `KeywordConstant` rebuilds the
  per-format singleton shell; `DeferredValueConstant`, `ExpressionConstant`,
  and `UnresolvedNameConstant` fail closed because they are unresolved
  compiler/AST placeholders. `UnresolvedNameConstant` also copies caller name
  arrays at construction.
- keep the named-identity branch fixes. `ModuleConstant`, `PackageConstant`,
  `ClassConstant`, `MultiMethodConstant`, and `TypedefConstant` rebuild their
  logical parent/name or name/version shells with target-owned parents before
  publication. `TypedefConstant` intentionally starts with unresolved
  recursion state in the target owner instead of copying `m_fInitialized`.
- keep the type-backed identity branch fixes. `DecoratedClassConstant` and
  `PureIdentityConstant` rebuild with a target-owned shared/adoptable type key
  and reject foreign type keys before publication.
- keep the `NativeRebaseConstant` guard. Native rebase identities are
  runtime-only facades and are documented as not registered with a
  `ConstantPool`, so adoption fails closed.

Review goal: make identity/path adoption explicit and remove hidden JIT-name
owner coupling.

### PR 5: Convert Value And Condition Constants

Scope:

- convert `ValueConstant` and primitive/literal/map/range/regex leaves;
- convert `ConditionalConstant`, `MultiCondition`, and condition leaves;
- use `Arrays.copyOf(...)` or immutable containers where array ownership must be
  explicit.

Current branch note: condition constants and the byte-array-backed scalar values
are already converted in the integration branch. `ConditionalConstant` no
longer opts into default shallow clone, the concrete condition leaves
reconstruct name/module/version/child predicate state through
`copyForAdoption(...)`, and `ConstantAdoptionTest` proves warmed `iTest`
simulation scratch is not copied into a target pool. `UInt8ArrayConstant`,
`FPNConstant`, and `Float128Constant` also copy constructor/adoption byte arrays
so immutable hash/equality value is not shared across callers or pool owners.
`BFloat16Constant`, `ByteConstant`, `CharConstant`, `DecimalConstant`,
`Float16Constant`, `Float32Constant`, `Float64Constant`, `Float8e4Constant`,
`Float8e5Constant`, `IntConstant`, `RegExConstant`, and `StringConstant`
reconstruct immutable logical scalar values explicitly. No concrete value
constant remains in a default-clone bucket.
`ArrayConstant`, `MapConstant`, and `RangeConstant` are also converted for
value-container ownership: constructor arrays are copied, adoption creates a
fresh target-owned shell, and target registration still performs recursive
child-value adoption. `MatchAnyConstant` now rebuilds the wildcard shell instead
of cloning it. It accepts shared type keys through normal target registration
and rejects unrelated foreign type keys before publication; shared/adoptable
type keys then continue through the clone-free type-family hooks. `LiteralConstant`,
`VersionConstant`, and `DecimalAutoConstant` are converted as the
parsed/delegated value wave: literal adoption drops transient parsed caches,
version adoption preserves the concrete subclass, and decimal-auto target
registration adopts the delegated decimal child.
`AccessTypeConstant`, `ImmutableTypeConstant`, and `ServiceTypeConstant` are
also converted: each rebuilds one logical modifier around a shared child type,
and target registration still interns that child exactly as before.
`UnionTypeConstant`, `IntersectionTypeConstant`, and `DifferenceTypeConstant`
rebuild two-child relational shells and still intern both children through target
registration. `CastTypeConstant` is different: it is documented in source as a
transient compiler/JIT marker and `assemble(...)` rejects storing it, so adoption
now fails closed instead of inheriting a storable intersection copy path.

Review goal: land the explicit logical-value copies for the remaining
low-risk value constants and show that constant adoption has no fallback clone
population left.

### PR 6: Convert Frame-Dependent Constants

Scope:

- keep `HandleConstant` as runtime-only and guarded;
- keep the `RegisterConstant.copyForAdoption(...)` branch fix, which records
  serialized register identity, rejects unknown moving compiler registers, and
  drops the transient compiler `Register`;
- keep the `MethodBindingConstant.copyForAdoption(...)` branch fix, which
  reconstructs the serialized logical method-binding descriptor and lets target
  registration adopt the method identity;
- make second adoption of live runtime values impossible without an explicit
  owner-local representation.

Review goal: make frame/runtime semantics visible at the API boundary.

### PR 7: Remove Fallback Clone

Scope:

- remove `Cloneable` from `Constant`;
- remove `super.clone()` from adoption;
- make fallback adoption impossible;
- turn source-shape migration test into a permanent regression test.

Review goal: fail closed forever. New constants must declare adoption semantics
before compiling.

Status in this branch: done. `Constant` no longer implements `Cloneable`,
`cloneForAdoption(...)` and `allowsDefaultAdoptionClone()` no longer exist, and
the source-shape test rejects their reintroduction.

### PR 8: Registration Publication Follow-Up

Scope:

- address the separate `ConstantPool.register(...)` publication-order issue:
  constants are currently added to list/map before recursive child registration
  completes;
- consider transactional registration or immutable registered snapshots;
- keep this separate from clone-free adoption unless a migrated family requires
  it.

Review goal: adoption no longer copies wrong state, and registration no longer
publishes half-registered state.

### PR 9: Non-Constant Clone Follow-Ups

Scope:

- `ObjectHandle.cloneAs(...)` owner-safe view/copy contract;
- `Component.cloneBody(...)` and `MethodStructure` structural copy;
- compiler/parser/AST clone paths for incremental compiler;
- JIT bridge frozen array clone.

Review goal: prevent the clone-free adoption PRs from expanding into every
remaining clone in the repository.

## Acceptance Criteria

The long-term migration is done when:

- `Constant` no longer implements `Cloneable`;
- `Constant.adoptedBy(...)` cannot call `Object.clone()`;
- every concrete constant class has explicit adoption behavior through its class
  or a reviewed abstract family base;
- live runtime handles, owner caches, locks, atomics, thread-local cells, and JIT
  names cannot be copied by adoption;
- source-shape tests fail on any new fallback clone/adoption path;
- focused adoption tests prove warmed source owner state is not visible from the
  destination owner;
- same-JVM sequence and parallel container stress pass with constant adoption and
  runtime ownership validators enabled;
- constant-pool size, interning behavior, and runtime startup time are equivalent
  to the current branch within expected noise.
