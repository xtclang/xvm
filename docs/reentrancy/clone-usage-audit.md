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

What used to be cloned: in `master`, `Constant.adoptedBy(...)` shallow-cloned a
foreign constant with `Object.clone()`, rewrote the containing pool, and then
relied on later family-specific cleanup to drop copied owner state.

Hazard: this was the central pool-ownership clone. It was unsafe because a new
field on any constant subclass automatically crossed owners unless somebody
remembered to add cleanup. That copied caches, locks, atomic cells, live
handles, thread-local helpers, and owner-derived metadata by reference.

Replacement: this branch removes `Cloneable`, `cloneForAdoption(...)`, and
`allowsDefaultAdoptionClone()` from `Constant`. `Constant.adoptedBy(...)` is the
final wrapper around explicit `copyForAdoption(...)` hooks. A constant now
constructs a target-owned logical value, intentionally drops/rebuilds helper
state, or fails closed at the owner boundary.

Current clone-free slices have removed the highest-risk and simplest value
families from the old clone path: singleton/filesystem/handle guards,
method/property metadata, frame-dependent constants, conditions, byte-array
backed values, immutable scalars, composite value containers, parsed/delegated
values, and the `MatchAnyConstant` wildcard shell. `MatchAnyConstant` still
points at the child-owner boundary because its lookup key is a `TypeConstant`;
the shell no longer clones, unrelated foreign type keys are rejected, and shared
type keys continue through target registration and the clone-free type-family
hooks.
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
The dependant child/property type shell classes `VirtualChildTypeConstant`,
`InnerChildTypeConstant`, `AnonymousClassTypeConstant`, and
`PropertyClassTypeConstant` are also off the default clone path. They reconstruct
parent plus child name/class/property identity and let target registration intern
those pieces exactly as before, while child-structure and `PropertyInfo` caches
start empty in the target owner. `RecursiveTypeConstant` has its own hook because
it is a `TerminalTypeConstant` subclass whose recursive typedef behavior would be
lost if terminal adoption rebuilt it as a plain terminal type.
`Annotation` and `AnnotatedTypeConstant` are also off the default path.
Annotation parameter arrays are copied at construction/adoption time because
they are part of immutable hash/equality identity, not an owner-local scratch
container. The annotated type shell still registers annotation class, params,
and underlying type through the target pool; only the derived annotation-type
cache is dropped and recomputed by the destination owner.
`TypeSequenceTypeConstant` reconstructs its stateless marker explicitly, while
`PendingTypeConstant` and `UnresolvedTypeConstant` fail closed because they are
mutable compiler placeholders rather than completed pool metadata.
The pseudo family is no longer a default-clone family either. Auto-narrowing
path constants (`ThisClassConstant`, `ParentClassConstant`, and
`ChildClassConstant`) reconstruct their logical path shell with target-owned
child identities before publication. That matters because the registration
path can install a locator before recursive child registration rewrites fields.
`KeywordConstant` reconstructs the same per-format singleton shell, while
`DeferredValueConstant`, `ExpressionConstant`, and `UnresolvedNameConstant`
fail closed because they are unresolved compiler/AST placeholders.
`UnresolvedNameConstant` also copies caller name arrays so temporary
hash/equality identity cannot be mutated by the caller.

Named and type-backed identity constants are also off the inherited clone path.
`ModuleConstant`, `PackageConstant`, `ClassConstant`, `MultiMethodConstant`, and
`TypedefConstant` reconstruct target-owned path identities before publication;
`TypedefConstant` deliberately drops resolved recursion state. `DecoratedClassConstant`
and `PureIdentityConstant` reconstruct only for shared/adoptable type keys, and
`NativeRebaseConstant` fails closed because it is runtime-only facade state.

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

Minimum replacement: keep `Constant` adoption clone-free. Do not add new
owner-local fields to constants without an explicit `copyForAdoption(...)`
decision and validator coverage.

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

## Rows 125/161 Completion Sweep

Date: 2026-08-24

This section closes must-audit backlog row 125 (live runtime handles embedded
in constants) and the remaining half of rows 103/161 (the
`ObjectHandle.cloneAs(...)` subclass audit and the
`ConstHeap.relocateConst(...)` clone paths). Scope commands:

```bash
rg -n "HandleConstant" javatools/src/main/java
rg -n "cloneAs|relocateConst" javatools/src/main/java
rg -n -U "class\s+\w+\s*\n?\s*extends\s+[\w.]*Handle\b" javatools/src/main/java
```

The subclass scan finds 88 `extends *Handle` declarations in the runtime
`ObjectHandle` tree (11 nested classes in `ObjectHandle.java`, 77 template
handle classes, 6 of them abstract), plus the anonymous `ObjectHandle.DEFAULT`
marker. `cloneAs(...)` has exactly three overrides beyond the base shallow
clone: `GenericHandle` (the fixed view contract), `InitializingHandle`
(delegates to the initialized singleton handle), and `Proxy.ProxyHandle`
(delegates to the proxied target). Every other subclass inherits either the
base clone or `GenericHandle`'s view clone.

### HandleConstant Boundary Paths

`HandleConstant` (`javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java`)
wraps a live `ObjectHandle` in a final field, reuses `Format.Register`, and is
created in exactly one place. This table lists every path where the constant,
or a constant embedding one, can reach a pool/container boundary.

| Path | Boundary | Verdict |
| --- | --- | --- |
| Creation: `xRTTypeTemplate.invokeAnnotate(...)` wraps live annotation-argument handles (`runtime/template/_native/reflect/xRTTypeTemplate.java:546`) and registers the resulting `AnnotatedTypeConstant` through `pool.ensureAnnotatedTypeConstant(...)` (`xRTTypeTemplate.java:552`), where `pool` is `frame.poolContext()` (`runtime/Frame.java:1341`). | Live frame handle enters pool metadata. | Same-owner by construction: the wrapped handle came through the executing frame and the target pool is the executing container's own module pool. Residual exposure is the raw-serve row below, not this write. |
| First registration of an unowned constant: `HandleConstant.copyForAdoption(...)` (`asm/constants/HandleConstant.java:58`) constructs a target-owned wrapper when `getContaining() == null`. | Unowned constant to first pool owner. | Same-owner-proven: reachable only from the single creation site above in the same call; `ConstantAdoptionValidator.isPermittedSharedReference(...)` (`asm/ConstantAdoptionValidator.java:174`) permits the `m_hValue` same-reference for exactly this class/field/unowned shape and nothing else. |
| Re-adoption of an owned constant into another pool. | Pool-to-pool movement. | Rejected: `HandleConstant.copyForAdoption(...)` throws (`HandleConstant.java:66`); proven by `ConstantAdoptionTest.ownedHandleConstantCannotMoveToAnotherPool`. |
| `Annotation` embedding: `Annotation.copyForAdoption(...)` (`asm/Annotation.java:238`). | Annotation params carried into a target pool. | Rejected for owned live-handle params; a fresh unowned param exists only inside the single same-pool creation call, so the permitted case is same-owner. |
| `AnnotatedTypeConstant` embedding: `copyForAdoption(...)` routes the annotation through `pool.register(m_annotation)` (`asm/constants/AnnotatedTypeConstant.java:661`). | Annotated type shell adoption. | Rejected via the annotation hook above; no separate handle path exists. |
| ConstHeap caching and relocation: `ensureConstHandle(...)` short-circuits every `FrameDependentConstant` before the cache (`runtime/ConstHeap.java:49`). | Container const-heap cache and `relocateConst(...)`. | Unreachable: a `HandleConstant` is never stored in `f_mapConstants`, never `saveConstHandle`d, and can never reach `relocateConst(...)`. `JumpVal`/`JumpVal_N` case constants are serialized code constants, and a runtime-only unassemblable constant can never appear there. |
| Raw handle serve: the same short-circuit returns `m_hValue` to any frame that resolves the constant (`ConstHeap.java:50`, `HandleConstant.getHandle(...)` at `HandleConstant.java:39`), with no `isShared(frame.container())` check. Consumers: annotation construction (`runtime/Utils.java:1572`), injected-property options (`runtime/ClassTemplate.java:935`), annotation params during composition work (`ClassTemplate.java:2314`). | Constant resolution by any container sharing the pool. | HAZARD (bounded); mechanism below. |
| `FileStructure.merge(...)` (`asm/FileStructure.java:177`): merge clones module structures and re-registers only the constants those structures reference. | Module merge across FileStructures/pools. | Unreachable/rejected: no `XvmStructure` ever references a `HandleConstant`, so merge does not carry it; if one were ever referenced, registration would route through adoption and fail closed. |
| Serialization/assembly: pre-assembly registration plus `ConstantPool.optimize()` (`asm/ConstantPool.java:3979`) drop every zero-ref constant, and a `HandleConstant` can only be zero-ref. | Pool assembly to disk. | Unreachable by construction. Latent edge: the class inherits `Constant.assemble(...)` (`asm/Constant.java:548`), which writes only the `Format.Register` ordinal; if the constant ever acquired a structural ref, the stream would be silently corrupt instead of rejected. Should-fix: override `assemble(...)` to throw. |
| JIT: zero references to `HandleConstant` or `FrameDependentConstant` under `org/xvm/javajit` and `javatools_jitbridge`. | JIT codegen reading pools. | Unreachable today: JIT reads structural constants and never sees runtime-only pool entries. Add a fail-closed branch if JIT ever walks pools wholesale. |
| Adjacent, not `HandleConstant`: `SingletonConstant` also embeds a live handle, including the `setHandle(...)` write during relocation (`ConstHeap.java:238`). | Singleton handle state on pool-owned constants. | Out of row 125's scope: per-pool adopted `SingletonConstant` copies keep the state per-pool; two containers sharing one pool is the row 108/124 shared-pool family. |

The one remaining live-handle exposure is resolution, not movement. Once a
`HandleConstant` is registered in a module pool, `ensureConstHandle` serves the
wrapped `ObjectHandle` raw to any frame whose `poolContext()` can resolve the
constant. `Container.getConstantPool()` is the module's pool
(`runtime/Container.java:90`), so two containers loaded over one module - the
same-JVM reuse scenario this branch targets - share the pool and therefore the
constant. A sibling container that receives the annotated
`TypeTemplateHandle` and instantiates the annotated type gets the creating
container's live handle without passing `maskAs`/proxy/pass-through checks.
This is bounded today because the only creation site wraps annotation-argument
values that already crossed service boundaries under normal passing rules when
the `AnnotationTemplate` was assembled, and because the constant is resolvable
only through reflection values that name it. The fix shape is one check:
validate handle/container sharing in `HandleConstant.getHandle(Frame)` or in
the `ConstHeap.java:49` short-circuit before handing out `m_hValue`.

### cloneAs Callers

The complete runtime caller set, each classified:

- `ClassComposition.ensureOrigin(...)`/`ensureAccess(...)`
  (`runtime/ClassComposition.java:196`, `:205`),
  `CanonicalizedTypeComposition.ensureAccess(...)` (`:77`), and
  `PropertyComposition.ensureAccess(...)` (`:132`): access/origin views; the
  target composition always comes from the handle's own composition family, so
  these are same-container by construction.
- `GenericHandle.maskAs(...)`/`revealAs(...)`/`cloneAs(...)` internal uses
  (`runtime/ObjectHandle.java:514`, `:604`, `:625`): the fixed view contract;
  direct cross-owner masking is rejected unless the graph is already shared.
- `ConstHeap.relocateConst(...)` (`runtime/ConstHeap.java:235`): analyzed
  below.
- `xRef.maskClassHandle(...)` (`runtime/template/reflect/xRef.java:242`): runs
  in the class owner's context - directly when the executing container owns
  the class type, otherwise via an op sent to the owner
  (`xRef.java:197`) that returns a proxy - so the clone is same-owner.
- `Proxy.ProxyHandle.cloneAs(...)` (`runtime/template/Proxy.java:425`):
  delegates to the target, deliberately unwrapping the proxy. Reachable only
  through `revealAs(...)`, which is owner-gated in `GenericHandle.revealAs`;
  `ProxyComposition.maskAs(...)`/`ensureAccess(...)` throw
  (`runtime/ProxyComposition.java:59`, `:74`), so masking cannot unwrap.
- `InitializingHandle.cloneAs(...)` (`runtime/ObjectHandle.java:1124`):
  delegates to the initialized singleton handle; initialization state cannot
  fork.

### ConstHeap.relocateConst Clone Paths

- The walk-up loop re-checks `hConst.isShared(parent, null)` at every level
  (`ConstHeap.java:213`), and `GenericHandle.isShared` recurses the whole
  reachable field graph, so a relocated handle only lands in a container whose
  type system can see everything it references.
- The final `cloneAs` (`ConstHeap.java:233`) is a composition relabel of an
  immutable constant handle: `JumpVal.java:289` asserts `!hCase.isMutable()`
  before relocation, and the singleton branch stores per-pool handles onto
  per-pool adopted `SingletonConstant` copies.
- Two soft notes, neither a current defect: (1) the final placement level does
  not re-assert `isShared(owner)` before cloning when the walk stops early -
  today's inputs are handles produced under the owner's own chain (created by
  `createConstHandle` in that container, or taken from a parent heap through
  the `isShared` check at `ConstHeap.java:156`) - but a local assert would make
  the invariant self-contained; (2) the clone shares inner field/delegate
  handles whose compositions still name a source-chain container, which is
  correct for immutable type-shared graphs and exactly why the mutable-state
  fork hazards below must stay out of this path.

Verdict for the relocation half of row 103/161: same-owner/shared-proven for
every input it can currently receive.

### cloneAs Subclass Table

Question audited per subclass: does the shallow copy share mutable
owner-bearing state (field arrays, ref outers, native resources, futures,
services) in a way that `GenericHandle`'s fixed cases (cross-owner `maskAs`
rejection, view-local inflated-ref outers) do not cover? The recurring failure
shape is the inverse of sharing: state that must stay canonical for one
runtime object lives in per-view Java fields, so a view clone forks it.

| Class(es) | Mutable fields beyond base | Shared after shallow clone | Verdict |
| --- | --- | --- | --- |
| Immutable value handles: `JavaLong`, `BaseInt128.LongLongHandle`, `BaseBinaryFP.FloatHandle`, `BaseDecFP.DecimalHandle`, `xIntLiteral.IntNHandle`, `xFPLiteral.FPNHandle`, `xString.StringHandle`, `xRegEx.RegExHandle`, `Identity.IdentityHandle`, `ConstantHandle`, `TransientId` | Set-once payloads (`JavaLong.m_lValue`); lazy display/hash caches (`StringHandle.m_hash`/`m_sValue`); `RegExHandle` final `Lazy<Pattern>` cell | Immutable payloads; caches fork per view and merely recompute; the shared `Lazy` cell is a safe publication cell | Safe. |
| `GenericHandle` const/metadata shells with final-only extras: `ExceptionHandle` (`f_sRTError`), `xOSFileNode.NodeHandle` (`f_path`), `xPackage.PackageHandle`, `xClass.ClassHandle`, `xRTType.TypeHandle` (`f_typeForeign`), `xRTTypeTemplate.TypeTemplateHandle`, `xRTComponentTemplate.ComponentTemplateHandle`, `xRTProperty.PropertyHandle`, `xRTBuffer.RTBufferHandle`, `xEnum.EnumHandle`/`xBoolean.BooleanHandle` (`m_index` set-once) | None mutable beyond the base | `m_aFields` shared by design; `m_aFieldOverrides` copy-on-write per view | Safe under the fixed view contract. |
| Function/signature metadata: `xRTSignature.SignatureHandle` (abstract: `f_idMethod`/`f_method`/`f_type`/`f_chain`/`f_nDepth` all final), `xRTMethod.MethodHandle`, `xRTFunction.FunctionHandle`, `NativeFunctionHandle` (`f_op`), `AsyncHandle`, `ConstructorHandle`, `FunctionProxyHandle` (`f_ctx`), `NoOpHandle` | None mutable | Final metadata; these are the handles `relocateConst` actually clones for function constants (`xRTFunction.java:121`) | Safe relabel. |
| Bound-function binders: `SingleBoundHandle` (`m_iArg`, `m_hArg`), `FullyBoundHandle` (`f_ahArg` shared array, `m_next` chain) | Bind slots and chain links are per-instance and mutated after construction | A clone would fork `m_next`/`m_hArg` mid-bind | Unreachable today (never constants, function masking uses the proxy path); rule: bound-function handles must never cross `cloneAs`. |
| Service/native-resource handles: `xService.ServiceHandle` (`f_context` final), `TimerHandle`, `SocketHandle` (volatile `socket`), `HttpServerHandle` (`f_aoNative`), `RandomHandle`, `KeyStoreHandle` (`f_achPwd`), `ConnectorHandle`, `CompilerHandle`, `xRawChannel.ChannelHandle` (`m_cPreferredBufferSize`) / `xRawOSFileChannel` (`f_channel`), `xRTServiceControl.ControlHandle`, `xContainerControl.ControlHandle`, `CoreRepoHandle`, `HttpContextHandle`, `HashCollectorHandle`, crypto handles (`DigestHandle`, `CipherHandle`, `SignatureHandle`, `MacHandle`, `SecretHandle`, `KeyGenHandle`) | Live Java resources and per-service context | `f_context` sharing across service views is the point of a service view (calls proxy to the one `ServiceContext`); plain native-resource `ObjectHandle`s have no `cloneAs` caller today | Safe for service views; unreachable for the plain resource wrappers. Rule: native-resource handles stay out of the const heap and masking. |
| Deferred/internal handles: `DeferredCallHandle`, `DeferredPropertyHandle`, `DeferredSingletonHandle`, `DeferredArrayHandle`, `NativeFutureHandle`, `InitializingHandle`, `ObjectHandle.DEFAULT` | Frame-bound control-flow state | Never cached, masked, or relocated; `InitializingHandle` delegates `cloneAs` to the initialized handle | Safe/unreachable. |
| `xRef.RefHandle` (and `IndexedRefHandle`: `f_lIndex` final, target write-once - safe) | `m_frame`, `m_iVar` (rewritten by `dereference()`, `xRef.java:1079`, called from `Frame.java:2539`), `m_sName` lazy, `m_hReferent` (documented write-once, `xRef.java:1100`) | `$value`/`$outer` shared via field array/overrides per the fixed contract; the delegation-mode fields are NOT shared | HAZARD: a view clone made while the ref is register-delegating keeps `(m_frame, m_iVar)`; scope exit dereferences only the `Frame.VarInfo`-cached instance, so the clone keeps reading the recycled register slot `f_ahVar[iVar]` - a different variable's value once the slot is reused. Reachable via access-view clones of a register-bound `Var` (`Frame.getPrivateThis()` family, `initializeCustomFields` struct clone at `xRef.java:987`). Fix shape: make delegation mode canonical shared state, dereference through all views, or reject `cloneAs` while register-bound. |
| `xLazy.LazyHandle` | `m_setInitFiber` (`xLazy.java:116`) plus `synchronized (hLazy)` guard (`xLazy.java:88`) | The `$value` slot is shared, but the monitor and the fiber set are per-view | HAZARD: views arise from the inflated-ref clone path (`ObjectHandle.java:508`) on struct/reveal transitions of the holder; two views racing first-get lock different monitors and keep different fiber sets, defeating duplicate-assign detection on immutable holders - the exact multi-service case the set exists for. Fix shape: canonical guard cell shared through the field array. |
| Lazily installed referent cells: `AtomicJavaLongHandle.m_atomicValue` (`xAtomicIntNumber.java:130`), `AtomicLongLongHandle.m_atomicValue` (`xAtomicInt128.java:119`), `InjectedHandle.m_hReferent` (`xInject.java:174`) | The atomic cell/injected referent installs post-construction into a per-view Java field | A view cloned before install forks the cell: two `AtomicLong`s for one `@Atomic` property break atomicity between views; injected refs can double-inject | HAZARD (narrow: requires a pre-install clone, e.g. the construction-time struct-to-public transition cloning inflated refs). `xAtomic.AtomicHandle` is immune - final `f_atomic` built in the constructor (`xAtomic.java:140`) - and `FutureHandle`/`FutureTupleHandle` are immune for the same reason. Fix shape: install cells in the constructor like `AtomicHandle`, or store them in the shared field array. |
| Array/delegate storage family: `xArray.ArrayHandle` (`m_mutability`, `m_hDelegate` - swapped by `clear()` at `xArray.java:466` and narrowed by `setMutability` at `xArray.java:394`; `m_hHash` cache), `DelegateHandle` (`m_cSize`, `m_mutability`), `GenericArrayDelegate` (`m_ahValue`), `LongArrayHandle`/`ByteArrayHandle`/`BitArrayHandle`/`CharArrayHandle`/`DoubleArrayHandle`/`StringArrayHandle` (storage pointers reassigned on grow, e.g. `ByteBasedDelegate.java:129`, `LongBasedDelegate.java:172`), `SliceHandle` (`f_hSource` final - shares source by spec), `ViewHandle` leaves | Mutable storage pointers and size/mutability live in per-instance Java fields, unlike `GenericHandle`'s shared field array | A `cloneAs` view forks the storage pointer: after `clear()`/grow through one alias, the other alias still reads the old storage | HAZARD for mutable handles; safe for the immutable const clones `relocateConst` performs (`JumpVal.java:289` asserts immutability). Access-view clones of mutable arrays are producible by `this:<access>` in natural collection code. `xTuple.TupleHandle` is the safe contrast: `m_ahValue` is assigned once and mutated element-wise, so views stay consistent. Fix shape: assert `!isMutable()` in the base `cloneAs` for non-`GenericHandle` subclasses, or move array mutable state behind a shared box. |
| Base-field caveat, every subclass: `ObjectHandle.m_fMutable` | Copied per view by the shallow clone | `GenericHandle.makeImmutable()` (`ObjectHandle.java:547`) freezes the shared structure but flips only the receiving view's flag | HAZARD (bounded - freeze normally happens before views proliferate): a sibling view of a frozen object still reports `isMutable() == true`, so template-level mutation guards keyed on `hTarget.isMutable()` can permit a write into the frozen shared field array, and `is(immutable)`/pass-through answers disagree between views. Not covered by the two fixed `GenericHandle` cases. Fix shape: share the mutability bit through canonical state, or freeze through the origin handle only. |

### Row Closure Verdicts

Row 125 (live runtime handles embedded in constants): closable. Every
pool-boundary crossing is rejected (`copyForAdoption` guards on
`HandleConstant`, `Annotation`, `AnnotatedTypeConstant`; merge/adoption
fail-closed) or same-owner by construction (single creation site, first
registration, cache bypass, assembly unreachability). Two narrowed follow-ups
replace the open-ended row: must-fix - `ConstHeap.ensureConstHandle` serves
`m_hValue` raw with no handle/container sharing check, which leaks a live
handle to a sibling container sharing the module pool; should-fix -
`HandleConstant.assemble(...)` should throw instead of inheriting the
format-byte-only writer, removing the latent corrupt-serialization edge that
today is prevented only by `optimize()` dropping zero-ref constants.

Rows 103/161 remainder (`cloneAs` subclasses and `relocateConst` clone paths):
closable as an audit. The two fixed `GenericHandle` cases stand, and
`relocateConst` is same-owner/shared-proven for every input it can currently
receive (immutable constant handles, per-level `isShared` walk, per-pool
singleton copies). The open-ended "keep auditing other subclasses" is replaced
by five named mechanisms, each precise enough to become its own backlog row:
`RefHandle` delegation-mode desync on `dereference()`; `LazyHandle` per-view
monitor/fiber-set guard split; lazily installed referent cells
(`AtomicJavaLongHandle`, `AtomicLongLongHandle`, `InjectedHandle`) forked by
pre-install view clones; mutable array/delegate storage-pointer forking under
access-view clones; and the base `m_fMutable` per-view freeze split. All five
are view-lifecycle/single-instance-aliasing gaps inside one container - none
is cross-owner movement, which remains guarded by the `maskAs` shared-graph
rejection.
