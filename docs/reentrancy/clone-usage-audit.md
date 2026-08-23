# Java Clone Usage Audit

Branch: `lagergren/lazy-instance`

Date: 2026-08-22

Scope commands:

```bash
rg -n --glob '*.java' '\.clone\s*\(' .
rg -n --glob '*.java' '\bclone\s*\(' .
rg -n --glob '*.java' 'implements\s+Cloneable|\bCloneable\b' .
rg -n --glob '*.java' 'super\.clone\(\)' javatools javatools_utils javatools_jitbridge plugin
rg -n --glob '*.java' 'cloneBody\(' javatools javatools_utils javatools_jitbridge plugin
rg -n --glob '*.java' 'cloneAs\(' javatools javatools_utils javatools_jitbridge plugin
```

The `\bclone\s*\(` scan returned 117 Java hits. The only `Cloneable`
declarations found are:

- `javatools/src/main/java/org/xvm/compiler/Token.java:19`
- `javatools/src/main/java/org/xvm/compiler/Source.java:28`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:46`
- `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:70`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:39`
- `javatools/src/main/java/org/xvm/asm/Component.java:107`
- `javatools/src/main/java/org/xvm/asm/Component.java:2573`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2761`
- `javatools/src/main/java/org/xvm/asm/Constant.java:73`

## Why `clone()` Is Hostile To Reentrant Runtime Code

`Object.clone()` is shallow, bypasses constructors, and does not force the
author to name the copied state. That is exactly the wrong default for this
code base:

- owner fields can still point at the source `Container`, `ConstantPool`,
  `MethodStructure`, `TypeInfo`, or runtime handle;
- final helper objects such as locks, atomics, thread-local cells, and lazy
  cells are copied by reference even though the new object appears to have
  fresh final state;
- transient caches and JIT/helper names are copied unless every subclass
  remembers to clear them;
- arrays are copied as containers only, not as deep copies of owner-bearing
  elements;
- no constructor invariant runs for the copied object, so reentrancy and
  publication assumptions have to be re-established by hand after the fact.

The branch already proved this is not theoretical. A shallow-cloned
`SingletonConstant` copied its final `AtomicReference<InitState>` into another
pool, which let one container reuse another container's initialized singleton
handle. That is the same failure family every clone site in this file is being
screened for.

## Replacement And Proof Standard

A replacement for `clone()` is acceptable only when it proves three things:

1. Semantic equivalence: the replacement copies the same logical value state as
   the old clone and intentionally rebuilds, clears, or rejects every
   non-logical helper field.
2. Owner safety: the replacement states the target owner up front, or proves
   that the copy is request-local and cannot cross a container, pool, method,
   service, or runtime-owner boundary.
3. Performance equivalence: the replacement does not add per-use synchronization
   or deep-copy hot owner graphs that were previously shared by design. If it
   changes allocation shape, a focused micro/loop test or stress counter should
   show that the old cache/share behavior is still present where it was
   legitimate.

For each clone family, the preferred mechanism is one of:

- explicit copy constructor or static factory that takes the target owner;
- `adoptedBy(...)`/`copyFor(...)` method that reconstructs owner-local helper
  state and registers/adopts child constants in the destination pool;
- immutable snapshot/record for logical data that should be shared;
- named shallow array copy for local copy-on-write containers, with owner
  assertions when elements are handles or constants;
- refusal to copy live runtime handles across containers unless the handle is
  explicitly shareable, frozen, proxied, or re-created in the target owner.

The proof should be in tests, not only comments:

- create two independent owners and show the copy belongs to the target owner;
- mutate or initialize the source after copying and show the copy does not see
  source helper/cache state;
- initialize the copy and show the source does not see target helper/cache
  state;
- for cache-preserving replacements, call the hot getter twice and assert the
  same owner-local cached value is reused;
- for hot paths, avoid `Lazy`, locks, or `Atomic*` objects per value unless the
  old path was already synchronized or the value is cold.

## Highest-Risk Findings

### 1. `Parameter.cloneBody()` mutates the source and preserves copied mutable state

Status: fixed in this branch.

References:

- `javatools/src/main/java/org/xvm/asm/Parameter.java:369`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:372`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:377`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:378`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:349`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:358`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:529`
- `javatools/src/main/java/org/xvm/asm/Parameter.java:534`

What is cloned: a `Parameter` via `super.clone()`.

Hazard: after cloning, the method clears `m_fImplicitDeref` and `m_regDeref`
on `this`, not on `that`. The clone keeps any copied transient dereference
state, including a `Register` produced for a specific `MethodStructure`, while
the source parameter is unexpectedly modified. That is both a mutable-state
hazard and an ownership hazard.

Container/ConstantPool/lock/cache impact: the copied `Register` is method-owned
state. It can point at the wrong method after method/component cloning, and the
source object's deref cache can be erased by a read-like clone operation.

Classification: must-fix, fixed.

Replacement: `Parameter` no longer implements `Cloneable` and no longer uses
`Object.clone()`. `Parameter.copyFor(MethodStructure owner)` constructs an
owner-explicit copy. It preserves logical parameter metadata, including the
implicit-deref flag, and intentionally drops `m_regDeref` because that cached
register is method-owned transient state.

Equivalence/performance proof:
`AsmConstructorEscapeTest.methodClonePreservesSourceDerefStateAndGetsFreshCloneState()`
sets up the old failure shape directly. It proves that copying a method leaves
the source parameter's implicit-deref metadata and cached register intact, while
the copied parameter starts with no copied deref register. The replacement has
the same allocation shape for normal method cloning: one `Parameter` copy per
parameter/return and no extra synchronization. The first dereference on the copy
still builds the same register lazily, but under the cloned method owner.

### 2. `MethodStructure.cloneBody()` assigns cloned parameters to the original method

References:

- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1732`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1739`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1740`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1750`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1751`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1758`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1768`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:1775`

What is cloned: a method body through `Component.cloneBody()`, then return and
parameter arrays are rebuilt with `Parameter.cloneBody()`. Code is cloned with
`m_code.cloneOnto(that)`, local constants are array-cloned, and source metadata
is cloned.

Hazard: both cloned parameter loops call `param.setContaining(this)` instead of
`param.setContaining(that)`. The clone's `m_aReturns` and `m_aParams` arrays can
therefore contain `Parameter` objects whose parent is the source method.

Container/ConstantPool/lock/cache impact: cloned module/file graphs can contain
method parameters that still resolve `getConstantPool()` through the original
method. Combined with `Parameter.m_regDeref`, a clone can retain method-local
register state from the wrong owner.

Classification: must-fix, fixed.

Replacement: `MethodStructure.cloneBody()` now calls
`Parameter.copyFor(that)` for both return and parameter arrays. The target
method owner is therefore part of the copy API, and no cloned parameter is ever
assigned back to the source method as an intermediate state.

Equivalence/performance proof:
`AsmConstructorEscapeTest.methodCloneAttachesCopiedParametersToClone()` clones a
method body and asserts every copied return and parameter reports the cloned
method as its containing structure. The fix preserves the old logical method
copy: the same return/parameter types, names, defaults, default marker, and
implicit-deref metadata are copied. It removes only stale owner/cache state.

### 3. Delegated methods shallow-copy `Parameter[]` arrays and share elements

Status: fixed in this branch.

References:

- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:3006`
- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:3007`
- `javatools/src/main/java/org/xvm/asm/Component.java:1171`
- `javatools/src/main/java/org/xvm/asm/Component.java:1180`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:258`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:299`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:115`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:116`

What is cloned: `ClassStructure.ensureMethodDelegation(...)` clones only the
parameter and return arrays before passing them to `createMethod(...)`.

Hazard: the `Parameter` elements are shared with the original method. Since
`Parameter` has mutable transient deref state, the delegated method can share
or overwrite source-method helper state.

Container/ConstantPool/lock/cache impact: array ownership is separated, but
element ownership is not. Any future parameter mutation or deref caching occurs
on objects shared across two method structures.

Classification: must-fix, fixed.

Replacement: `MultiMethodStructure.createMethodCopyingParameters(...)` creates
synthetic methods from another method's signature by copying `Parameter`
elements for the new method owner before publishing the method as a child.
`ClassStructure.ensureMethodDelegation(...)` now uses that factory instead of
cloning only the array containers.

Equivalence/performance proof:
`AsmConstructorEscapeTest.delegatedMethodFactoryCopiesParameterElementsForNewOwner()`
proves delegated returns and parameters are distinct objects owned by the
delegated method, that logical implicit-deref metadata is preserved, and that
the source method's cached deref register is not copied or cleared. The change
adds one `Parameter` allocation per delegated return/parameter at synthetic
method creation time only. The generated invocation code and runtime call path
are unchanged.

### 4. `ObjectHandle.cloneAs(...)` shallow-copies runtime handles

References:

- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:62`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:64`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:497`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:503`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:506`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:512`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:592`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:613`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:1043`
- `javatools/src/main/java/org/xvm/runtime/ConstHeap.java:215`
- `javatools/src/main/java/org/xvm/runtime/CanonicalizedTypeComposition.java:77`
- `javatools/src/main/java/org/xvm/runtime/PropertyComposition.java:127`
- `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:189`
- `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:198`
- `javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:242`
- `javatools/src/main/java/org/xvm/runtime/template/Proxy.java:424`

What is cloned: arbitrary `ObjectHandle` subclasses via `super.clone()`,
usually to reveal, mask, or move a visible `TypeComposition`.

Hazard: subclass fields are shallow-copied. For `GenericHandle`, the
`ObjectHandle[] m_aFields` array is intentionally shared so public, private,
struct, masked, and revealed access views keep observing the same object state.
The old clone path then rewired inflated `RefHandle` objects in that same
shared array to point at the new handle. That made the source view's existing
ref holder change merely because another access view was created.

Container/ConstantPool/lock/cache impact: `ConstHeap` can call `cloneAs(...)`
when moving constant handles into another container. The target composition is
changed, but backing fields, handles, and ref owners can remain shared unless
the specific subclass overrides the behavior.

Classification: fixed for the two proven `GenericHandle` hazards in this
branch; still must-audit for other `ObjectHandle` subclasses and relocation
paths that inherit the base shallow clone.

Current branch fixes:

1. `GenericHandle.maskAs(...)` rejects direct cross-owner masking when the
   handle graph is not already shared with the target container. That closes
   the most dangerous misuse: treating `cloneAs(...)` as an ownership-transfer
   primitive. A masked/revealed `GenericHandle` is an access view of the same
   runtime object; it is not a deep copy and it cannot make owner-local fields
   safe for another container. Non-core objects still use the existing proxy
   path instead of direct sharing.
2. `GenericHandle.cloneAs(...)` now separates shared object backing from
   view-specific inflated-ref holder state. The regular field array remains
   shared. Only inflated refs that need a different `$outer` are represented by
   sparse per-view overrides on the cloned view.

`OwnershipDiagnosticsTest.crossOwnerMaskRejectsNonSharedHandleBeforeClone()`
proves the guard. The synthetic handle reports `isShared(...) == false` and
throws if `cloneAs(...)` is reached; the fixed path returns `null` after the
shared-graph check. The old path would continue into `cloneAs(...)` for the
same setup.

`GenericHandleCloneAsTest.sameOwnerCloneKeepsInflatedRefOuterViewLocal()` proves
the same-owner view backing fix. It constructs a source view with an inflated
ref and a regular field, clones it to another access view, and asserts:

- the source ref still has the source handle as `$outer`;
- the clone ref has the clone handle as `$outer`;
- referent writes through the clone ref are visible through the source ref;
- regular field writes through the clone are visible through the source handle.

Copied to master, that test fails at the first holder assertion because the old
clone path rewrites the shared ref to point at the clone.

Minimum replacement rule going forward: do not use raw `Object.clone()` as an
owner-transfer or mutable-runtime-copy primitive. For same-object views, keep a
documented shared backing state and represent only view-specific state in the
view. For true copies or cross-container movement, allocate owner-local wrapper
state and assert that every retained handle is shareable with the destination
container.

Equivalence/performance proof: the fixed `GenericHandle` path does not clone
the full field array or reachable handle graph. Handles with no view-local
overrides keep direct `m_aFields` access. A struct/revealed transition allocates
at most one sparse override array plus one ref view per affected inflated
field. For immutable value handles and other subclasses, the remaining audit
must either prove that the shallow view is a pure type relabel or replace it
with an explicit owner-aware copy/view API.

### 5. Transitional `Constant.copyForAdoption(...)` shallow-clones reviewed families

References:

- `javatools/src/main/java/org/xvm/asm/Constant.java:312`
- `javatools/src/main/java/org/xvm/asm/Constant.java:315`
- `javatools/src/main/java/org/xvm/asm/Constant.java:319`
- `javatools/src/main/java/org/xvm/asm/Constant.java:320`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:211`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:213`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:214`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:231`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:233`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:234`
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:21`
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:55`

What is cloned: any `Constant` family that explicitly opts into
`allowsDefaultAdoptionClone()` is shallow-cloned by the default
`copyForAdoption(...)` hook and then reassigned to the destination
`ConstantPool`. `Constant.adoptedBy(...)` is now the final wrapper around that
hook.

Hazard: this is still the central remaining pool-ownership clone. This branch
already hardens several known owner-local fields and prevents ad-hoc
`adoptedBy(...)` overrides, but the reviewed default remains dangerous if a
family later adds a cache, lock, atomic cell, live handle, thread-local, or
owner-derived helper field without replacing the fallback.

Current clone-free slices have removed the highest-risk and simplest value
families from this default: singleton/filesystem/handle guards, method/property
metadata, frame-dependent constants, conditions, byte-array backed values,
immutable scalars, composite value containers, parsed/delegated values, and the
`MatchAnyConstant` wildcard shell. `MatchAnyConstant` still points at the
separate type-family problem because its lookup key is a `TypeConstant`; the
shell no longer clones, unrelated foreign type keys are rejected, and shared
type keys still rely on the remaining type-family clone-free adoption work.
`TerminalTypeConstant` is also no longer on that default path: it reconstructs
the type leaf from the defining identity, registers shared identities in the
target owner, and rejects unrelated foreign identities before publication.
The same rule now applies to the one-child type wrappers
`AccessTypeConstant`, `ImmutableTypeConstant`, and `ServiceTypeConstant`: the
logical modifier is reconstructed, the child type is adopted by target
registration, and unrelated foreign child types fail before publication.
For two-child relational types, `UnionTypeConstant`, `IntersectionTypeConstant`,
and `DifferenceTypeConstant` rebuild the storable relational shell and let target
registration adopt both children. `CastTypeConstant` is intentionally different:
it is a transient compiler/JIT marker whose `assemble(...)` method already
rejects storage, so adoption now fails closed instead of pretending it can be
pooled.

Container/ConstantPool/lock/cache impact: `ConstantPool.register(...)` uses this
path for foreign constants and locator constants. The validator is opt-in through
`xvm.asm.validateConstantAdoption`, so normal execution does not fail closed.

Classification: must-audit as a transitional mechanism; must-fix for any
subclass with owner-local mutable state that is not already reconstructed or
cleared.

Minimum replacement: introduce an explicit adoption/copy contract and remove
the base shallow clone as the default. Until then, keep the validator enabled in
stress/CI and require every new mutable/transient/final helper field on a
`Constant` subclass to declare its adoption behavior.

Equivalence/performance proof: for every adopted constant subclass, adopt into
two pools, initialize or warm the source's helper/runtime cache, and assert the
adopted copy either recomputes in the target pool or intentionally shares only
immutable logical children. `ConstantPool` interning must still return the same
target-pool object for repeated registration of equal logical constants.

## Other Object-Clone Implementations

| Site | What is cloned | Ownership and mutable-state assessment | Classification | Minimum replacement |
| --- | --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/compiler/Token.java:434`, `javatools/src/main/java/org/xvm/compiler/Token.java:436`; used by `javatools/src/main/java/org/xvm/compiler/Parser.java:5412`, `javatools/src/main/java/org/xvm/compiler/Parser.java:5413`, `javatools/src/main/java/org/xvm/compiler/Parser.java:5414` | Parser bookmark tokens. | Shallow-copies `m_oValue`; currently appears to be token literal/name payload, not owner or lock state. If future token values become mutable, parser backtracking can share them. | Should-fix, low risk. | Prefer a copy constructor that documents which token payloads may be shared. |
| `javatools/src/main/java/org/xvm/compiler/Source.java:461`, `javatools/src/main/java/org/xvm/compiler/Source.java:463`, `javatools/src/main/java/org/xvm/compiler/Source.java:541` | Compiler source cursor. | The clone shares the final `char[]` source text and file/node identity, then resets cursor fields. That is safe if the char array remains immutable after construction. | Should-fix, low risk. | Use an explicit cursor copy or immutable source buffer plus separate cursor object. |
| `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:211`, `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:214`, `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:231`, `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:238`, `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:2092`, `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:2097` | Base AST tree clone. | Registered child fields are deep-cloned and adopted, but the clone starts with the source node's parent and compilation stage. Subclass semantic caches/components are copied unless each subclass resets them. | Must-audit for parallel/incremental compiler work. | Replace with `copyForValidation(...)` or copy constructors that explicitly decide parent, stage, component, semantic caches, and child ownership. |
| `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:982`, `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:983`, `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:986`, `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:1091`, `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:1097`, `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:1098` | Named-type AST with manually cloned dynamic expression. | Dynamic expression is handled, but resolved constants and unresolved type/name helper fields are still shallow-copied. | Must-audit. | Reset or re-resolve semantic caches in an explicit copy path. |
| `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:150`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:151`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:155`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1129`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1133`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1134`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1135`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1159`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1232`, `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1239` | New-expression and anonymous-inner-class AST clones. | The body and selected lists are manually deep-cloned for rough draft/capture analysis. The `Actual` path intentionally shares lists/body; the clone still depends on correct parent/component rewiring. | Must-audit. | Split draft, actual, and capture copies into explicit constructors with ownership assertions. |
| `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:860`, `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:862`, `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:863`, `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:1508`, `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:1529` | Lambda AST clone. | `m_lambda` is correctly cleared, but required type and emitted AST helper state are copied unless reset elsewhere. | Must-audit. | Explicit lambda validation copy that clears method/component/emitted-AST state and preserves only source syntax. |
| `javatools/src/main/java/org/xvm/asm/Component.java:1970`, `javatools/src/main/java/org/xvm/asm/Component.java:1973`, `javatools/src/main/java/org/xvm/asm/Component.java:1984`, `javatools/src/main/java/org/xvm/asm/Component.java:1989`, `javatools/src/main/java/org/xvm/asm/Component.java:1990`, `javatools/src/main/java/org/xvm/asm/Component.java:1991`, `javatools/src/main/java/org/xvm/asm/Component.java:3538` | Component body clone plus contribution clones. | Children, siblings, and lazy child bytes are nulled; contributions are cloned. Parent, identity, condition, docs, flags, modified flag, and transient recursion marker are shallow-copied. | Must-audit; reset `m_FVisited` should-fix. | Use explicit `copyBodyTo(parent)` and initialize clone lifecycle fields rather than depending on later `setContaining(...)`. |
| `javatools/src/main/java/org/xvm/asm/Component.java:2573`, `javatools/src/main/java/org/xvm/asm/Component.java:3148`, `javatools/src/main/java/org/xvm/asm/Component.java:3150` | `Component.Contribution` shallow clone. | Contribution fields are logical composition/type/injection state today, but any added mutable helper would be shared. | Should-fix. | Replace with a small copy constructor. |
| `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2761`, `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2952`, `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2954`, `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2983` | Method source metadata clone and constant registration. | The source clone shallow-copies source arrays/constants, then `registerConstants(pool)` can rehome source constants. No locks, but arrays remain mutable. | Should-fix. | Use immutable source metadata or copy arrays when normalizing/registering. |
| `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:269`, `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:274`, `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:64`, `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:66`, `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:68` | JIT bridge long-backed array during freeze. | The object is shallow-cloned; storage is cloned only when local storage is present. Delegate, context/type inherited state, and cached hash are copied. | Must-audit. | Replace with an explicit frozen-copy constructor that documents delegate sharing and resets/recomputes helper caches as needed. |

## Component And Structure Clone Family

References:

- `javatools/src/main/java/org/xvm/asm/Component.java:1824`
- `javatools/src/main/java/org/xvm/asm/Component.java:1828`
- `javatools/src/main/java/org/xvm/asm/Component.java:1831`
- `javatools/src/main/java/org/xvm/asm/Component.java:2001`
- `javatools/src/main/java/org/xvm/asm/Component.java:2015`
- `javatools/src/main/java/org/xvm/asm/Component.java:2016`
- `javatools/src/main/java/org/xvm/asm/Component.java:2023`
- `javatools/src/main/java/org/xvm/asm/Component.java:2024`
- `javatools/src/main/java/org/xvm/asm/ComponentBifurcator.java:82`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:173`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:174`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:185`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:186`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:535`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:537`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:234`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:235`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:237`
- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:1008`
- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:1009`
- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:1012`
- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:1016`
- `javatools/src/main/java/org/xvm/asm/ModuleStructure.java:656`
- `javatools/src/main/java/org/xvm/asm/ModuleStructure.java:657`
- `javatools/src/main/java/org/xvm/asm/CompositeComponent.java:385`

What is cloned: component bodies, cloned child trees, modules, classes,
multi-method caches, import-version collections, and sometimes a split
component body.

Hazard: the core body clone uses `Object.clone()` and then relies on specific
subclasses and callers to reset caches and ownership. `ComponentBifurcator`
contains an explicit review/TODO near the clone path and does not link the false
component as a sibling at the clone site.

Container/ConstantPool/lock/cache impact: component clones retain identity and
condition constants until registration; condition constants now have explicit
logical adoption hooks, but component clone still depends on later registration
to re-own the graph. Component clone semantics also cross the `XvmStructure`
design rule that parentage is fixed after construction
(`javatools/src/main/java/org/xvm/asm/XvmStructure.java:41`,
`javatools/src/main/java/org/xvm/asm/XvmStructure.java:120`).

Classification: must-audit, with the `MethodStructure` and `Parameter` subcases
above marked must-fix.

Minimum replacement: make component cloning a construction operation that takes
the target parent and target pool up front. The copy path should declare which
fields are logical value, which are owner-local caches, and which are rebuilt by
registration.

## Constant And ConstantPool Array Clones

References:

- `javatools/src/main/java/org/xvm/asm/Constant.java:700`
- `javatools/src/main/java/org/xvm/asm/Constant.java:716`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:361`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:822`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:836`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:850`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:180`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:268`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:282`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:318`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:333`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1014`
- `javatools/src/main/java/org/xvm/asm/constants/TerminalTypeConstant.java:727`
- `javatools/src/main/java/org/xvm/asm/constants/TerminalTypeConstant.java:731`
- `javatools/src/main/java/org/xvm/asm/constants/AllCondition.java:108`
- `javatools/src/main/java/org/xvm/asm/constants/AllCondition.java:114`
- `javatools/src/main/java/org/xvm/asm/constants/AllCondition.java:139`
- `javatools/src/main/java/org/xvm/asm/constants/ArrayConstant.java:205`
- `javatools/src/main/java/org/xvm/asm/constants/MapConstant.java:231`
- `javatools/src/main/java/org/xvm/asm/constants/MapConstant.java:245`

What is cloned: byte arrays, `Constant[]`, `TypeConstant[]`,
`Annotation[]`, and `ConditionalConstant[]` containers.

Hazard: these are mostly defensive or copy-on-write container clones. They do
not clone the elements. That is correct when elements are immutable logical
constants, but it is not a deep ownership transfer by itself.

Container/ConstantPool/lock/cache impact: `Constant.registerConstants(...)`
exists specifically because arrays can be shared with cloned constants in other
pools (`javatools/src/main/java/org/xvm/asm/Constant.java:702`). If an array is
later modified without registration/copy-on-write, the clone and original can
observe each other's element changes.

Classification: should-fix for API clarity, not immediate must-fix where the
existing code performs copy-on-write and pool registration.

Minimum replacement: use immutable lists or `Arrays.copyOf(...)` for clearer
defensive copies. Any owner-transfer path must register or reconstruct elements,
not rely on array clone alone.

## Constant Adoption Safeguards Already Present

References:

- `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:262`
- `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:264`
- `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:332`
- `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:123`
- `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:128`
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:238`
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:243`
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:244`
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:54`
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:62`
- `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java:1040`
- `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java:1044`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:749`
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:750`
- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:229`
- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:232`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7924`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7932`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7939`

What is cloned or avoided: this branch already avoids or repairs several
constant-adoption shallow clones by reconstructing owner-local constants or
clearing copied runtime state.

Hazard: these are positive examples, but they also prove the base clone was
capable of copying final atomics, locks, handles, and JIT/cache state.
The branch now extends that pattern to value constants as well: condition
predicates, byte-array-backed values, immutable scalars, array/map/range
containers, and parsed/delegated literals all have explicit adoption hooks.

Classification: fixed or hardened in current branch; keep covered by tests and
validator runs.

Minimum replacement: keep moving subclasses away from default clone adoption and
toward explicit constructors. Do not add new owner-local fields to constants
without an adoption decision.

## Compiler AST Temporary Clone Sites

References:

- `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:308`
- `javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java:328`
- `javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java:332`
- `javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java:334`
- `javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java:235`
- `javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java:237`
- `javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java:297`
- `javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java:298`
- `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:709`
- `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:731`
- `javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java:117`
- `javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java:165`
- `javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java:434`
- `javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java:369`
- `javatools/src/main/java/org/xvm/compiler/ast/AssertStatement.java:556`
- `javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java:417`

What is cloned: AST expressions, condition lists, update statements, statement
blocks, and property declaration statements for validation/retry paths.

Hazard: these clones are request-local compiler scratch state today, but the
base clone copies parentage/stage and subclass semantic fields. Parallel or
incremental compiler reuse would need a stronger request-owner model.

Container/ConstantPool/lock/cache impact: AST nodes can carry resolved constants
and components. Those are owner-bearing even when the AST itself is compiler
local.

Classification: must-audit for compiler reentrancy; not a runtime-container
must-fix by itself.

Minimum replacement: introduce validation/scratch-copy APIs that explicitly
reset semantic caches and bind the copy to a compilation request/context.

## Type And Constant Array Copy Sites In Compiler/Runtime Logic

References:

- `javatools/src/main/java/org/xvm/compiler/ast/ReturnStatement.java:137`
- `javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:77`
- `javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:86`
- `javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:179`
- `javatools/src/main/java/org/xvm/compiler/ast/SwitchExpression.java:174`
- `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:547`
- `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:664`
- `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:788`
- `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:1062`
- `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2917`
- `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:642`
- `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:780`
- `javatools/src/main/java/org/xvm/compiler/ast/Expression.java:815`
- `javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java:557`
- `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:1247`
- `javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java:980`
- `javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java:476`
- `javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:467`
- `javatools/src/main/java/org/xvm/asm/OpCallable.java:637`
- `javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:558`
- `javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:728`

What is cloned: `TypeConstant[]`, `Constant[]`, `Assignable[]`, and AST arrays
used as copy-on-write temporary containers during type inference, conversion,
case analysis, tuple typing, and op metadata.

Hazard: array containers are copied, not elements. This is fine for local
copy-on-write use, but it must not be mistaken for deep cloning constants or
ownership transfer.

Container/ConstantPool/lock/cache impact: `TypeConstant` elements are
owner-bearing. When the array crosses a pool/container boundary, every element
needs an explicit shared/adopted proof.

Classification: should-fix documentation/API clarity; must-audit only when the
array outlives the request or crosses owners.

Minimum replacement: use `List.copyOf(...)` for immutable snapshots and name
copy-on-write helpers after their shallow behavior. Owner-transfer paths should
register/adopt elements explicitly.

## Runtime ObjectHandle Array Copy Sites

References:

- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java:154`
- `javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:139`
- `javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:366`
- `javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:387`
- `javatools/src/main/java/org/xvm/runtime/template/Proxy.java:338`
- `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:253`

What is cloned: arrays of `ObjectHandle` values for tuple mutation/freeze,
proxy conversion, array delegate storage, and function call var arrays.

Hazard: these clones copy only the handle array container. Handles inside remain
shared. That is correct for immutable tuple/argument snapshots but dangerous if
the receiving owner can mutate the elements or assumes deep ownership.

Container/ConstantPool/lock/cache impact: `ObjectHandle` elements can be
container-owned, service-owned, mutable, or proxy-backed. The array clone itself
does not make them safe to pass between containers.

Classification: should-fix; must-audit for pass-through and cross-service paths.

Minimum replacement: keep array container copies for local isolation, but add
owner/pass-through assertions where arrays are sent across service or container
boundaries. Deep copy is not generally correct; runtime needs explicit share,
freeze, proxy, or owner-transfer semantics.

## Metadata Chain Array Copy Sites

References:

- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:239`
- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:491`
- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:583`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1392`

What is cloned: `MethodBody[]` chains while deriving method/property metadata.

Hazard: these are shallow chain-container copies. `MethodBody` references remain
shared unless a specific element is replaced.

Container/ConstantPool/lock/cache impact: `MethodBody` can point back to a
`MethodStructure`. If metadata is reused across type owners, shallow chain
copies can preserve original method ownership.

Classification: must-audit for type-info reuse across owners; should-fix for
single-owner metadata.

Minimum replacement: make `MethodInfo`/`PropertyInfo` chain snapshots immutable
and document whether `MethodBody` is logical metadata or owner-bound structure.

## Utility And Defensive Array Clones

References:

- `javatools_utils/src/main/java/org/xvm/util/ConstBitSet.java:110`
- `javatools_utils/src/main/java/org/xvm/util/ConstOrdinalList.java:135`
- `javatools_utils/src/main/java/org/xvm/util/Handy.java:1903`
- `javatools_utils/src/main/java/org/xvm/util/Handy.java:2023`
- `javatools_utils/src/main/java/org/xvm/util/ConsoleLog.java:37`
- `javatools/src/main/java/org/xvm/asm/LinkedRepository.java:40`

What is cloned: primitive/value arrays or repository arrays for defensive
copying and copy-on-write helpers.

Hazard: primitive array copies are safe container isolation. `LinkedRepository`
copies the repository array but not repository objects, which is normal
constructor defensive copying.

Container/ConstantPool/lock/cache impact: no ConstantPool or runtime lock
ownership moves through these clones. `LinkedRepository` can still share mutable
repositories by design.

Classification: low risk; should-fix only if APIs should expose immutable
collections instead of arrays.

Minimum replacement: prefer `Arrays.copyOf(...)` or immutable collections for
clarity. Keep copy-on-write helpers documented as shallow.

## Summary Backlog

Must-fix:

- Replace `Parameter.cloneBody()` with an explicit logical copy that clears
  transient deref state on the clone, not on the source.
- Fix `MethodStructure.cloneBody()` so cloned parameters are owned by the cloned
  method.
- Deep-copy `Parameter` elements for delegated methods or prove
  `Parameter.deref(...)` cannot run on shared parameters.

Must-audit:

- Finish the `ObjectHandle.cloneAs(...)` view-backing redesign for mutable
  `GenericHandle` field arrays. The direct cross-owner `GenericHandle.maskAs(...)`
  subcase is now constrained by a target-owner `isShared(...)` guard.
- Continue moving `Constant.adoptedBy(...)` away from default shallow cloning.
- Give AST/component clone paths explicit owner/context/copy contracts before
  treating compiler/linker structures as reentrant-safe.

Should-fix:

- Replace remaining low-risk `Cloneable` uses with copy constructors over time.
- Name shallow array container copies clearly and use immutable snapshots where
  callers should not mutate returned arrays.
