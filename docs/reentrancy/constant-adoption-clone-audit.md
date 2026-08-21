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

## Why The Current Mechanism Is Dangerous

The base implementation uses `Object.clone()`:

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

- override `adoptedBy(...)` and create a fresh owner-local copy; or
- clear all non-logical state during `setContaining(...)` or
  `registerConstants(...)` before the adopted copy is observable through normal
  APIs.

This is the same root cause as the fixed `SingletonConstant` incident:
`SingletonConstant.f_state` was final, but the final `AtomicReference` object
was shallow-copied, so a singleton adopted into a second pool reused the first
pool/container's runtime singleton state.

## Adoption Entry Points

These are the current adoption sites in runtime/asm source.

| Site | Purpose | Runtime/container assessment |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/asm/Constant.java:312` | Base adoption by shallow clone, owner reassignment, and reference-count reset. | Central risk. Safe only for subclasses whose copied fields are pure logical value state or are reset after cloning. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:199` | Adopt a foreign constant during pool registration. | Expected owner-transfer path. Must not preserve source-pool runtime state. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:216` | Adopt a foreign locator constant used by the pool lookup table. | Same hazard as normal adoption, but through locators. |
| `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:262` | Branch override that constructs a fresh singleton constant for the target pool. | Fixed must-fix site. Prevents cross-container singleton handle/lifecycle state sharing. |
| `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:238` | Branch override that clears cloned runtime file-system handle state and the derived path-literal cache. | Fixed must-fix site. Prevents copied runtime handles and source-pool path literals from crossing pools. |
| `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:123` | Branch override that clears cloned runtime file-store handle state. | Fixed must-fix site. Prevents copied runtime handles from crossing pools. |
| `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java` | Branch override that reconstructs the logical parameterized type for the target pool. | Fixed hardening site. Keeps the final helper lock owner-local and drops resolver/JIT helper state. |
| `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java` | Branch override that reconstructs the logical signature for the target pool. | Fixed hardening site. Preserves transient property-signature identity while dropping comparison/JIT helper state. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java` | Branch override that reconstructs the logical register type parameter for the target pool. | Fixed hardening site. Keeps the final reentrancy helper owner-local. |
| `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java` | Branch guard for live runtime handles. | Fixed must-fix guard. Allows first registration of a fresh unowned runtime handle constant, but rejects moving an already-owned live handle into another pool. |

## Runtime-Relevant Adoption Risks

These are the adoption-related sites that matter for parallel runtime or
multiple containers in one JVM.

| Site | Current state | Risk | Proper fix |
| --- | --- | --- | --- |
| `SingletonConstant.f_state` | Fixed in this branch by constructing a fresh adopted constant. | Shallow clone shared final `AtomicReference<InitState>`; second pool could receive first container's singleton handle. | Keep this branch fix. Adoption must not clone lifecycle state cells. |
| `FSNodeConstant.m_handle` | Fixed in this branch by clearing the adopted copy. | Shallow clone could copy a runtime handle into another pool. | Keep this branch fix. Runtime handle caches are owner-local. |
| `FSNodeConstant.m_constPath` | Fixed in this branch by clearing the adopted copy and recomputing on demand under the destination pool. | Shallow clone could copy a cached `LiteralConstant` whose owner was the source pool. | Keep the cache, but clear it at adoption boundaries. A final `Lazy` would not be correct until adoption stops using shallow clone or the lazy cell can be reset during adoption. |
| `FileStoreConstant.m_handle` | Fixed in this branch by clearing the adopted copy. | Same as `FSNodeConstant`. | Keep this branch fix. |
| `TypeConstant.m_handle` | Cleared by `TypeConstant.setContaining(...)`. | Without this reset, adopted types could cache a `xRTType.TypeHandle` from another container. | Existing reset is correct. Keep owner-specific `ensureTypeHandle(Container)` behavior. |
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
| `AnnotatedTypeConstant` parameters containing `HandleConstant` | Hardened in this branch. Fresh unowned handle constants can still be registered in the current pool; moving an already-owned handle constant to another pool throws. | `HandleConstant` wraps an `ObjectHandle`, starts with `pool == null`, and is not a serialized logical constant. If such an annotated type is adopted again, the live handle can be carried into another pool. | Keep the `HandleConstant.adoptedBy(...)` guard. If runtime annotation values must be shared, they need an owner-local representation or explicit frame-time resolution. |
| `Constant.m_oValue` | Private transient base field with no current read/write API hits. | If future code uses it for runtime state, base adoption would shallow-copy it. | Leave documented. If revived, either clear it in `adoptedBy(...)` or remove the field. |

This branch now fixes the highest-risk small adoption hardening items in this
table. The common rule is that adoption may preserve logical constant identity,
but not mutable helper cells, in-progress state, JIT caches, or live runtime
handles.

## Frame-Dependent Constants

`FrameDependentConstant` is not automatically illegal. Two subclasses are
serialized constant forms:

- `RegisterConstant` represents a frame register and can be serialized as
  `Format.Register`.
- `MethodBindingConstant` represents a bind-target constant and can be
  serialized as `Format.BindTarget`.

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
  containers and should stay diagnostic-only.
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

- `HandleConstant.adoptedBy(...)` throws when an already-owned live
  `ObjectHandle` constant is moved to another pool.
- `ConstantPool.register(...)` now optionally asserts that the adopted copy
  does not retain forbidden runtime-owner fields. The opt-in
  `ConstantAdoptionValidator` is enabled by
  `-Dxvm.asm.validateConstantAdoption=true` and checks for copied owner/runtime
  references, mutable `Atomic*`, lock objects, Java references, thread-local
  cells, and mutable collections that are identical between source and adopted
  copy.
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
- cross-pool adoption of already-owned `HandleConstant` live runtime handles.
- opt-in adoption validation at `ConstantPool.register(...)`, with a focused
  regression test that proves a default shallow-cloned helper reference is
  rejected when diagnostics are enabled.

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
