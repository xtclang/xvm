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
constant family explicitly opts in with `allowsDefaultAdoptionClone()` and a
local comment. That prevents a new mutable helper field from silently inheriting
owner-transfer clone behavior, and it prevents subclasses from bypassing the
common owner/ref checks with ad-hoc `adoptedBy(...)` overrides.

This is still a transitional guard, not the desired architecture. Opted-in
families still use shallow clone plus reset hooks, which means correctness still
depends on family-level review. The long-term defect this plan removes is
`Object.clone()` as an ownership-transfer mechanism at all.

## Current Mechanism

The adoption entry points are:

| Site | Current role |
| --- | --- |
| `Constant.adoptedBy(ConstantPool)` | Final owner-transfer wrapper. Creates `AdoptionContext`, delegates to `copyForAdoption(...)`, checks destination ownership, and resets refs. |
| `Constant.copyForAdoption(AdoptionContext)` | Subclass/family hook for logical copy construction. The default implementation fails closed unless the family declares the transitional default-clone policy. |
| `Constant.cloneForAdoption(ConstantPool)` | Transitional shallow clone helper used by opted-in default-clone families. The runtime-handle explicit overrides no longer call it. |
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
| Classes currently overriding `copyForAdoption(...)` | 7 |
| Classes relying on an explicit family default-clone policy somewhere in the hierarchy | 81 |

The migration is broad but not conceptually deep. The direct source blast radius
for a complete clone-free adoption model is likely:

- `Constant`, `ConstantPool`, and adoption tests;
- all 7 high-risk hook classes, already converted to `copyForAdoption(...)` in
  this branch;
- 13 abstract family bases, to place shared family adoption rules where useful;
- up to 75 concrete leaf constants, either by explicit copy/adoption
  implementation or by inheriting a reviewed family implementation;
- JIT-name owner cache policy for `MethodConstant` and `PropertyConstant`;
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
| `AbstractDependantChildTypeConstant` | `AbstractDependantTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `AbstractDependantTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `AccessTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `AllCondition` | `MultiCondition` | family default-clone policy | P3 logical condition array |
| `AnnotatedTypeConstant` | `TypeConstant` | family default-clone policy | P1 annotation children and type cache reset |
| `AnonymousClassTypeConstant` | `AbstractDependantChildTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `AnyCondition` | `MultiCondition` | family default-clone policy | P3 logical condition array |
| `ArrayConstant` | `ValueConstant` | family default-clone policy | P3 logical value array |
| `BFloat16Constant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `ByteConstant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `CastTypeConstant` | `IntersectionTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `CharConstant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `ChildClassConstant` | `PseudoConstant` | family default-clone policy | P2 logical identity/path |
| `ClassConstant` | `NamedConstant` | family default-clone policy | P2 logical identity/path |
| `ConditionalConstant` | `Constant` | family default-clone policy | P3 condition family base |
| `DecimalAutoConstant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `DecimalConstant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `DecoratedClassConstant` | `IdentityConstant` | family default-clone policy | P2 logical identity/path |
| `DeferredValueConstant` | `PseudoConstant` | family default-clone policy | P2 pseudo/logical value |
| `DifferenceTypeConstant` | `RelationalTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `DynamicFormalConstant` | `FormalConstant` | family default-clone policy | P2 formal logical identity |
| `EnumValueConstant` | `SingletonConstant` | inherits explicit singleton adoption | P0 singleton runtime-state family |
| `ExpressionConstant` | `PseudoConstant` | family default-clone policy | P2 pseudo/logical value |
| `FPNConstant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `FSNodeConstant` | `ValueConstant` | explicit clone-free override | P0 runtime handle/path cache |
| `FileStoreConstant` | `ValueConstant` | explicit clone-free override | P0 runtime handle |
| `Float128Constant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `Float16Constant` | `FloatConstant` | family default-clone policy | P3 primitive/logical value |
| `Float32Constant` | `FloatConstant` | family default-clone policy | P3 primitive/logical value |
| `Float64Constant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `Float8e4Constant` | `FloatConstant` | family default-clone policy | P3 primitive/logical value |
| `Float8e5Constant` | `FloatConstant` | family default-clone policy | P3 primitive/logical value |
| `FloatConstant` | `ValueConstant` | family default-clone policy | P3 float-family base |
| `FormalConstant` | `NamedConstant` | family default-clone policy | P2 formal logical identity |
| `FormalTypeChildConstant` | `PropertyConstant` | family default-clone policy | P2/P1 property metadata cache inheritance |
| `FrameDependentConstant` | `Constant` | family default-clone policy | P4 frame-dependent family base |
| `HandleConstant` | `FrameDependentConstant` | explicit clone-free guard override | P0 live runtime handle |
| `IdentityConstant` | `Constant` | family default-clone policy | P2 identity-family base |
| `ImmutableTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `InnerChildTypeConstant` | `AbstractDependantChildTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `IntConstant` | `ValueConstant` | family default-clone policy | P3 primitive/logical value |
| `IntersectionTypeConstant` | `RelationalTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `KeywordConstant` | `PseudoConstant` | family default-clone policy | P2 pseudo/logical value |
| `LiteralConstant` | `ValueConstant` | family default-clone policy | P3 logical literal value |
| `MapConstant` | `ValueConstant` | family default-clone policy | P3 logical map arrays |
| `MatchAnyConstant` | `ValueConstant` | family default-clone policy | P3 logical sentinel |
| `MethodBindingConstant` | `FrameDependentConstant` | family default-clone policy | P4 serialized frame-dependent constant |
| `MethodConstant` | `IdentityConstant` | family default-clone policy | P1 JIT-name/type cache owner policy |
| `ModuleConstant` | `IdentityConstant` | family default-clone policy | P2 logical identity/path |
| `MultiCondition` | `ConditionalConstant` | family default-clone policy | P3 condition-family base |
| `MultiMethodConstant` | `NamedConstant` | family default-clone policy | P2 logical identity/path |
| `NamedCondition` | `ConditionalConstant` | family default-clone policy | P3 logical condition |
| `NamedConstant` | `IdentityConstant` | family default-clone policy | P2 named identity-family base |
| `NativeRebaseConstant` | `ClassConstant` | family default-clone policy | P2 logical identity/path |
| `NotCondition` | `ConditionalConstant` | family default-clone policy | P3 logical condition |
| `PackageConstant` | `NamedConstant` | family default-clone policy | P2 logical identity/path |
| `ParameterizedTypeConstant` | `TypeConstant` | explicit override | P0 helper lock/JIT cache |
| `ParentClassConstant` | `PseudoConstant` | family default-clone policy | P2 pseudo/logical identity |
| `PendingTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `PresentCondition` | `ConditionalConstant` | family default-clone policy | P3 logical condition |
| `PropertyClassTypeConstant` | `AbstractDependantTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `PropertyConstant` | `FormalConstant` | family default-clone policy | P1 JIT-name/type/property-info cache owner policy |
| `PseudoConstant` | `Constant` | family default-clone policy | P2 pseudo-family base |
| `PureIdentityConstant` | `IdentityConstant` | family default-clone policy | P2 logical identity/path |
| `RangeConstant` | `ValueConstant` | family default-clone policy | P3 logical range value |
| `RecursiveTypeConstant` | `TerminalTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `RegExConstant` | `ValueConstant` | family default-clone policy | P3 logical regex string/options |
| `RegisterConstant` | `FrameDependentConstant` | family default-clone policy | P4 serialized frame register constant |
| `RelationalTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `ServiceTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `SignatureConstant` | `PseudoConstant` | explicit override | P0 helper lock/JIT cache |
| `SingletonConstant` | `ValueConstant` | explicit override | P0 runtime singleton lifecycle state |
| `StringConstant` | `ValueConstant` | family default-clone policy | P3 immutable logical string |
| `TerminalTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `ThisClassConstant` | `PseudoConstant` | family default-clone policy | P2 pseudo/logical identity |
| `TypeConstant` | `Constant` | family default-clone policy plus owner reset in `setContaining(...)` | P1 type-family base |
| `TypeParameterConstant` | `FormalConstant` | explicit override | P0 reentrancy helper cell |
| `TypeSequenceTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `TypedefConstant` | `NamedConstant` | family default-clone policy | P2 logical identity/path |
| `UInt8ArrayConstant` | `ValueConstant` | family default-clone policy | P3 immutable byte-array value |
| `UnionTypeConstant` | `RelationalTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `UnresolvedNameConstant` | `PseudoConstant` | family default-clone policy | P2 unresolved logical name |
| `UnresolvedTypeConstant` | `TypeConstant` | family default-clone policy | P1 type-family cache/reset contract |
| `ValueConstant` | `Constant` | family default-clone policy | P3 value-family base |
| `VersionConstant` | `LiteralConstant` | family default-clone policy | P3 immutable logical version |
| `VersionMatchesCondition` | `ConditionalConstant` | family default-clone policy | P3 logical condition |
| `VersionedCondition` | `ConditionalConstant` | family default-clone policy | P3 logical condition |
| `VirtualChildTypeConstant` | `AbstractDependantChildTypeConstant` | family default-clone policy | P1 type-family cache/reset contract |

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

Cross-pool adoption already allocates today: `Object.clone()` creates a new
object and `ConstantPool.register(...)` interns it. Replacing clone with explicit
construction should not add steady-state runtime cost.

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
  `HandleConstant`, `ParameterizedTypeConstant`, `SignatureConstant`, and
  `TypeParameterConstant` from ad-hoc overrides to the new adoption hook;
- keep current branch tests as equivalence tests.

Current branch note: `FSNodeConstant`, `FileStoreConstant`, and `HandleConstant`
already use clone-free construction inside `copyForAdoption(...)`; the other
P0 special cases use the same hook for fresh helper/runtime state. The later API
migration should preserve that behavior while broadening it to family bases.

Review goal: prove the new API expresses already-known fixes without behavior or
performance regressions.

### PR 3: Convert `TypeConstant` Base And Type Families

Scope:

- migrate `TypeConstant` owner reset to explicit copy/adoption construction;
- convert type-family classes:
  `AbstractDependantChildTypeConstant`, `AbstractDependantTypeConstant`,
  `AccessTypeConstant`, `AnnotatedTypeConstant`, `AnonymousClassTypeConstant`,
  `CastTypeConstant`, `DifferenceTypeConstant`, `ImmutableTypeConstant`,
  `InnerChildTypeConstant`, `IntersectionTypeConstant`, `PendingTypeConstant`,
  `PropertyClassTypeConstant`, `RecursiveTypeConstant`,
  `RelationalTypeConstant`, `ServiceTypeConstant`, `TerminalTypeConstant`,
  `TypeSequenceTypeConstant`, `UnionTypeConstant`, `UnresolvedTypeConstant`,
  and `VirtualChildTypeConstant`;
- preserve existing cache reset behavior exactly.

Review goal: remove the largest owner-cache family from shallow clone fallback.

### PR 4: Convert Identity, Named, Formal, And Pseudo Constants

Scope:

- convert `IdentityConstant`, `NamedConstant`, `FormalConstant`,
  `PseudoConstant`, and leaves:
  `ChildClassConstant`, `ClassConstant`, `DecoratedClassConstant`,
  `DeferredValueConstant`, `DynamicFormalConstant`, `ExpressionConstant`,
  `FormalTypeChildConstant`, `KeywordConstant`, `MethodConstant`,
  `ModuleConstant`, `MultiMethodConstant`, `NativeRebaseConstant`,
  `PackageConstant`, `ParentClassConstant`, `PropertyConstant`,
  `PureIdentityConstant`, `ThisClassConstant`, `TypedefConstant`, and
  `UnresolvedNameConstant`;
- settle `MethodConstant.m_sJitName` and `PropertyConstant.m_sJitName` owner
  policy instead of copying or accidentally preserving JIT names.

Review goal: make identity/path adoption explicit and remove hidden JIT-name
owner coupling.

### PR 5: Convert Value And Condition Constants

Scope:

- convert `ValueConstant` and primitive/literal/map/range/regex leaves;
- convert `ConditionalConstant`, `MultiCondition`, and condition leaves;
- use `Arrays.copyOf(...)` or immutable containers where array ownership must be
  explicit.

Review goal: replace low-risk default clone users with explicit logical-value
copy code and remove the largest remaining fallback population.

### PR 6: Convert Frame-Dependent Constants

Scope:

- keep `HandleConstant` as runtime-only and guarded;
- document/encode why `RegisterConstant` and `MethodBindingConstant` are
  serialized logical frame-dependent constants, not live runtime handles;
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
