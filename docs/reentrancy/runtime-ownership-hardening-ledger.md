# Runtime Ownership Hardening Ledger

This file is the reviewer-facing ledger for the concrete runtime ownership
failures, the fixes already made in this branch, and the assertions or follow-up
work that still need to exist before the runtime can be treated as reentrant by
default.

The shorter inventories are still useful:

- [must-fix-races.md](must-fix-races.md) gives the severity summary.
- [fixed-in-this-branch.md](fixed-in-this-branch.md) lists the branch delta.
- [constant-adoption-clone-audit.md](constant-adoption-clone-audit.md) gives the
  detailed clone/adoption audit.
- [constant-pool-state-audit.md](constant-pool-state-audit.md) gives the
  `ConstantPool` and ambient owner audit.
- [must-audit-backlog.md](must-audit-backlog.md) tracks remaining risk areas.

This document deliberately uses stronger language than ordinary design notes:
the old patterns were not only stylistically weak. They made it possible for
one container, pool, fiber, or frame to observe another owner's runtime state.

## Severity Rule

Use these labels consistently:

| Severity | Meaning | Merge stance |
| --- | --- | --- |
| Must fix | A concrete race, wrong-owner value, constructor escape, or mixed lifecycle state is known or directly reproducible. | Fix in the current runtime-owner PR unless the fix is too broad and explicitly split. |
| Must audit | The code is safe only if an ownership/threading assumption is true but not encoded in the API. | Prove confinement, add diagnostics/tests, or promote to must fix. |
| Should fix soon | The code is not proven broken today, but it blocks reentrant/incremental/runtime reuse work. | Keep out of the first PR unless it is small and local. |
| Should fix | Cleanup that improves final-field reasoning or API clarity. | Backlog. |

## Fixed Must-Fix Items In This Branch

### Constructor-Published Native Template `INSTANCE`

What was wrong:

- Native template constructors assigned `INSTANCE = this`.
- `INSTANCE` was a JVM-global pointer to a container-owned template.
- The write occurred before construction and `initNative()` finished.
- Parallel containers could race: whichever constructor ran last became the
  static value seen by all other containers.
- Any code that later read `SomeTemplate.INSTANCE.f_container` could get a
  different container's owner graph.

Why this is a Java memory-model problem:

- Static fields are shared variables.
- Constructor publication of `this` defeats final-field reasoning because
  another thread can observe the object before construction completes.
- There is no happens-before edge between one startup thread's constructor write
  and another runtime thread's later static read.

Branch fix:

- Converted templates no longer expose mutable `INSTANCE`.
- `Container.nativeTemplates()` owns a final `NativeTemplates` table.
- Callers resolve native templates through the current `Container`, `Frame`, or
  owning template instead of a global field.
- Fallback/specialized templates created by `Container.getTemplate(...)` do not
  publish themselves globally.

Proof/guards:

- Current branch scan has no mutable runtime-template `INSTANCE` fields and no
  `INSTANCE = this` assignments in runtime templates.
- `NativeTemplatesTest` and same-JVM stress exercise owner-local template
  lookup.
- Add a lint/build rule that rejects new mutable `INSTANCE` fields and
  constructor `INSTANCE = this` assignments in runtime code.
- Treat `javac -Xlint:this-escape` as an error except where a local
  `@SuppressWarnings("this-escape")` has a concrete owner/lifetime comment.

### Static Runtime Metadata Caches

What was wrong:

- Static fields cached `TypeConstant`, `TypeComposition`, `MethodStructure`,
  `MethodConstant`, `SignatureConstant`, handles, enum templates, and other
  owner-derived runtime values.
- Those values are not JVM constants. They belong to a `Container`,
  `ConstantPool`, template, frame, or service context.
- A static cache built in container A could be reused by container B.

Branch fix:

- Unkeyed metadata moved to final owner-local lazy fields.
- Related metadata moved into owner-local immutable records where grouping
  prevents mixed-owner construction.
- Keyed caches moved to owner-owned maps or `NativeTemplates` lookup tables.
- Factory helpers that manufacture handles or owner-bearing metadata now take a
  `Frame`, `Container`, `ClassTemplate`, or `ConstantPool` where necessary.

Proof/guards:

- Current branch scan leaves zero hits for the field-shaped static runtime
  metadata pattern in runtime templates and `Utils`.
- Same-JVM direct stress validates that repeated runs do not reuse earlier
  owner-scoped templates, handles, compositions, services, or constants.
- Add a source scan to CI for new non-final static owner-bearing fields in
  runtime packages.

### Public Enum Publication Of Construction Structs

What was wrong:

- Natural enum construction can produce a temporary construction struct before
  the final enum singleton is initialized.
- Public/native paths that returned raw `EnumHandle` values could expose that
  struct as if it were the final enum value.
- Under recursion or parallel startup, callers could observe a not-yet-final
  enum value.

Branch fix:

- Public/native enum paths use `ensureEnumByName(...)`,
  `ensureEnumByOrdinal(...)`, or `Utils.ensureInitializedEnum(...)`.
- Raw `getEnumByName(...)` and `getEnumByOrdinal(...)` remain protected/internal
  helpers for code that is deliberately inside `xEnum`.
- Native enum value globals such as Boolean/Nullable/Ordered handles are now
  owner-local and reached through container/frame factories.

Proof/guards:

- `NativeTemplatesTest` asserts that public reflection enum helpers expose
  `ObjectHandle`, not raw `EnumHandle`.
- Source scans show remaining raw enum access is protected/internal or
  owner-local factory code.
- Add tests for enum lookup under parallel container startup and recursive enum
  initialization paths.

### Split Singleton Lifecycle State

What was wrong:

- `SingletonConstant` used several mutable fields for lifecycle state:
  initialized handle, initializing fiber, and initialization future.
- Readers could observe a mixed state that never existed as a coherent
  lifecycle snapshot.
- Example failure shapes: "has handle but stale waiter", "has initializing
  fiber but no matching future", or false same-fiber recursion handling.

Branch fix:

- `SingletonConstant` now uses one final `AtomicReference<InitState>`.
- Each state transition publishes one immutable state record.
- CAS, not unrelated field updates, owns the lifecycle transition.

Proof/guards:

- `SingletonConstantTest` covers concurrent initialization, waiter sharing,
  same-fiber recursion, and adopted singleton owner-local state.
- This is the right primitive for suspendable/reentrant lifecycle state. `Lazy`
  would be the wrong abstraction because initialization can suspend, recurse,
  abort, and retry.

### Runtime State Shallow-Copied During Constant Adoption

What was wrong:

- `Constant.adoptedBy(...)` uses `Object.clone()` to move a logical constant
  into a different `ConstantPool`.
- `Object.clone()` is shallow. It copies references, including references held
  in `final` fields.
- `transient` does not help because `transient` affects serialization, not
  cloning.
- A final reference to a mutable helper object is still a shared mutable helper
  object after clone.

Concrete failures/hazards:

- `SingletonConstant.f_state` was a final `AtomicReference<InitState>`. Clone
  copied the reference, so two pools shared one runtime singleton state cell.
- `FSNodeConstant.m_handle` and `FileStoreConstant.m_handle` could copy runtime
  handles across pools.
- `FSNodeConstant.m_constPath` could copy a derived path literal owned by the
  source pool after the first `getPathConstant()` call.
- `TypeConstant.m_cRecursiveDepth` could copy a mutable recursion counter.
- `TypeConstant.m_tloInProgress`, `m_mapConsumes`, `m_mapProduces`,
  `m_sJitName`, `m_handle`, and normalized/type-info caches could copy
  in-progress or owner-derived helper state.
- `ParameterizedTypeConstant.m_lockPrev` was a final `StampedLock`. Clone could
  make two adopted constants share the same synchronization object, including a
  lock that was held during clone.
- `SignatureConstant.m_lockPrev` had the same final-lock shallow-copy hazard,
  plus comparison and JIT-name helper caches.
- `TypeParameterConstant.f_tloReEntry` was a final `TransientThreadLocal`.
  Clone could make equivalent type parameters in different pools share the same
  recursive-comparison marker.
- `HandleConstant` wraps a live `ObjectHandle`. Moving an already-owned
  `HandleConstant` into another pool would move a live runtime value across
  owners as if it were a serialized logical constant.

Branch fix:

- `SingletonConstant.adoptedBy(...)` constructs a fresh singleton constant for
  the target pool.
- `FSNodeConstant.adoptedBy(...)` and `FileStoreConstant.adoptedBy(...)` clear
  copied runtime handles. `FSNodeConstant.adoptedBy(...)` also clears the
  derived path-literal cache so it is recomputed under the destination pool.
- `TypeConstant.setContaining(...)` clears every non-logical transient helper,
  runtime, and JIT cache on owner change.
- `ParameterizedTypeConstant.adoptedBy(...)` reconstructs the logical
  parameterized type, keeping its `StampedLock` final and born with the target
  owner.
- `SignatureConstant.adoptedBy(...)` reconstructs the logical signature,
  preserves the transient property-signature marker, and drops comparison/JIT
  helpers.
- `TypeParameterConstant.adoptedBy(...)` reconstructs the logical type
  parameter, keeping the recursive-comparison helper final and owner-local.
- `HandleConstant.adoptedBy(...)` allows first registration of a fresh unowned
  runtime handle constant but throws if an already-owned live handle constant is
  moved to another pool.

Proof/guards:

- `ConstantAdoptionTest` directly exercises the adoption boundary.
- The same test copied into detached `master` failed all five cases; it passes
  on this branch.
- `ConstantAdoptionValidator` now runs at `ConstantPool.register(...)` when
  `-Dxvm.asm.validateConstantAdoption=true` is enabled. It compares source and
  adopted copies and reports identical helper/runtime references unless they
  are logical child `Constant` objects that the pool will recursively adopt.
- Forbidden categories include owner/runtime references, `Atomic*` helpers,
  lock objects, Java references, `ThreadLocal`, `TransientThreadLocal`, and
  mutable collections.
- Promote specific validator findings to hard assertions once the intentional
  allowlist is known.

### Ownerless Runtime Handle Factories

What was wrong:

- Helpers such as `makeHandle(...)`, `makeInternalHandle(...)`, empty-array
  factories, and type-handle factories could create owner-bearing handles
  without an explicit owner parameter.
- Some old callers implicitly reached the owner through global template state,
  which is exactly the state this branch removes.

Branch fix:

- Runtime factories that need ownership now accept `Frame`, `Container`, or an
  owner template/composition.
- Null-owner paths were removed or made private where possible.
- Existing caching behavior is preserved by resolving through the same
  container-local template/cache that master intended to use globally.

Proof/guards:

- Source compilation catches many missing owner arguments because ownerless
  overloads were removed.
- Add hard argument checks for APIs that still accept a `Container` and cannot
  safely operate with `null`.
- Add a scan for newly introduced ownerless `makeHandle`/`ensure*Handle`
  helpers in runtime templates.

### Stress-Discovered Adjacent Runtime Bugs

These were found while validating owner safety. They are not all caused by the
old `INSTANCE` pattern, but they block trustworthy stress validation.

| Issue | Why it mattered | Branch action |
| --- | --- | --- |
| `StringBuffer` chunk invariant | Stress could expose inconsistent append/chunk behavior that looked like a runtime race. | Documented and fixed separately in the branch history. |
| `xRTCompiler` unmodifiable diagnostics list | Parallel compiler/manual stress could fail for a non-owner reason and mask real ownership bugs. | Documented as adjacent; compiler work is separate. |
| `xException` canonical formatter lookup | Wrong or missing owner metadata during exception formatting could hide the original failure. | Owner-local exception metadata and frame/container-owned translation paths. |
| Concurrent Gradle/XTC output writes | Running multiple heavy manual-test builds against one checkout can corrupt or race output files and masquerade as runtime corruption. | Documented as a stress-harness caveat; use one controlled stress task or isolated outputs. |

## Remaining Must-Audit Items And Required Closure

These are not all first-PR blockers. Each item must either be proven safe or
promoted to a must-fix change when runtime sharing is demonstrated.

## Recommended Fix Order

Do these as separate commits. Each commit should contain the code change, the
focused test or diagnostic that proves the old shape was unsafe, and the doc
update that explains the ownership rule it enforces. Avoid bundling unrelated
cleanup into these commits; the review needs to be able to answer "which owner
bug did this commit close?"

The twelve rows below are all real hardening work. The order is about reducing
risk and making each later fix easier to prove, not about deciding which items
can be ignored.

| Order | Commit scope | Why this order | Required test/proof |
| --- | --- | --- | --- |
| 1 | Adoption/clone validator assertions | Done in this branch wave. The shallow-clone bug is proven, dangerous, and the reusable validator catches copied handles, locks, thread locals, and owner references while later work runs stress. | `ConstantAdoptionTest` proves direct detection and opt-in `ConstantPool.register(...)` failure for a default shallow-cloned helper reference. |
| 2 | `ConstantPool` late-mutation guard | Done in this branch wave. Hidden late mutation now becomes an immediate diagnostic instead of a stale-owner symptom when `xvm.asm.validateConstantPoolLateRegistration` is enabled. | `ConstantPoolDiagnosticsTest` proves existing constants still return after publication and new registrations fail before publication into the pool. Diagnostic same-JVM stress now identifies `ClassComposition` access-type registration as the next warmup/design target. |
| 3 | Ambient `ConstantPool` lookup cleanup | Done in this branch wave for runtime boundaries. `InterpreterConnector.invoke0(...)` now uses the main container's explicit pool, native startup no longer uses raw current-pool mutation, and the remaining runtime scoped bridges assert that the scoped pool matches the explicit owner. | `ConstantPoolDiagnosticsTest` covers exact scoped-owner assertions, explicit-owner calls with no ambient pool, and wrong-scope failures. Source scan now shows no runtime/API callers of raw `setCurrentPool(...)`; the method itself was removed. |
| 4 | `NativeContainer` constructor startup escape | Done in this branch wave. Native template loading, base-template installation, resource initialization, and service-context creation no longer run from the native-container constructor. `NativeContainer.create(...)` constructs the owner first, then initializes it before handing it to `InterpreterConnector`. | `javac -Xlint:this-escape` now emits no `NativeContainer.java` diagnostics. `InterpreterConnectorTest` starts several connectors in parallel, loads `ecstasy.xtclang.org`, checks distinct native containers, and forces ownership validation over the warmed containers. Startup order is preserved because the factory still completes all native-template/resource initialization before returning the container. |
| 5 | Runtime handle construction escapes | Done in this branch wave. `RefHandle.createRegisterRef(...)` stores register refs in `Frame.VarInfo` only after construction, `RefHandle.createReferentRef(...)` initializes referent fields after construction, and `NodeHandle.create(...)` initializes native store fields after construction. | `RefHandleConstructionTest` verifies the factory APIs and runtime op call sites. Targeted `:javatools:compileJava` lint emits no `xRef.java` or `xOSFileNode.java` `this-escape` diagnostics. Same-JVM direct stress with `TestReflection,TestFiles` passes. Existing frame-local ref caching and file-node store initialization are preserved. |
| 6 | Runtime constructor assertion escapes | Done in this branch wave. `FieldAccessChain` validates `aMethods` with a static helper, and `MethodHandle` resolves assertion metadata from `typeTarget` and `method` directly instead of calling instance methods while the subclass constructor is still running. | `RuntimeThisEscapeConstructionTest` guards both source patterns. Targeted `:javatools:compileJava` lint emits no `CallChain.java` or `xRTMethod.java` `this-escape` diagnostics. Assertions keep their old validation semantics; normal runtime behavior is unchanged because assertions are disabled by default. |
| 7 | `ClassTemplate` implicit-field constructor escape | Done in this branch wave. The base `ClassTemplate` constructor no longer calls the overridable `registerImplicitFields(...)` hook. `xRef` and `xConst` pass their implicit field names as explicit constructor metadata, and the base still adds `GenericHandle.OUTER` for instance-child structures. | `RuntimeThisEscapeConstructionTest.implicitFieldsAreConstructorMetadata()` guards the constructor shape. Targeted `:javatools:compileJava` lint emits no `ClassTemplate.java` `this-escape` diagnostic. Cache behavior is unchanged because `f_asFieldsImplicit` is still final constructor metadata. |
| 8 | Structural hash contracts and `Constant` hash publication | Done in this branch wave. `VersionTree`, `Register`, `Register.ShadowRegister`, and `ChildInfo` now implement `hashCode()` from the same fields as `equals(...)`. `Constant.m_iHash` now uses named sentinels and volatile publication for its resolved-only cached hash. | `VersionTest`, `RegisterHashCodeTest`, `TypeInfoMemberOwnershipTest`, and `ConstantHashCodeCacheTest` cover the changed contracts. Targeted lint emits no `overrides` diagnostics. Mutable metadata recomputes hashes instead of using lazy caches; constants keep the old resolved-only cache behavior with explicit safe publication. |
| 9 | Runtime-executed `Op` caches | Done in this branch wave for owner-bearing frame-constant caches. `JumpCond`/`JumpNCond` no longer cache `ConditionalConstant` on the op, and `OpTest`/`OpCondJump` no longer write frame type constants back to `m_typeCommon` during execution. | `OpRuntimeCacheTest` verifies the condition fields are gone and guards against the old common-type write-back pattern. Same-JVM sequence and parallel stress exercise these op paths under runtime ownership validation. |
| 10 | Manual lazy null caches in runtime/asm | Done for the first-PR must-fix slice. `xRegEx.RegExHandle` uses a final `Lazy<Pattern>`, `FSNodeConstant` no longer carries a source-pool path literal across adoption, and `_native:fs.OSFileNode.created` no longer caches a caller-owned `Time` handle inside the native file-system graph. Remaining Java hits are still audited below as builder/linkage, frame/debug lifecycle, synchronized association helpers, or must-audit op-address/class-layout state. | `RegExHandleTest` proves the regex cache remains per-handle and cached after first use. `ConstantAdoptionTest.adoptedFSNodeConstantDropsSourcePoolPathCache()` proves the adopted path cache is recomputed in the destination pool and still cached after that. `manualTests:runDirectSequenceStress` with `TestFiles` proves the native file-node owner leak is gone. |
| 11 | Thread-local and scoped ambient state | Done for the first-PR must-fix slice. Runtime boundary `ConstantPool` scopes were already made lexical/asserted in wave 3. This wave removes one remaining owner-bearing runtime thread-local: `xRTServer.SimpleKeyManager` no longer keeps a selected `KeyStoreHandle` on the HTTPS worker thread. Broader compiler/type recursion ambient state remains audited below. | `xRTServerTest` proves the key manager has no thread-local field and resolves TLS aliases through explicit route state. Existing `ConstantPoolDiagnosticsTest` covers scoped pool assertions. |
| 12 | Weak/identity owner registries | Done for the first-PR must-fix slice. `Runtime.f_containers` is a diagnostic weak registry and every access path now uses the same monitor; the old `findContainer(...)` iteration raced with registration and weak-map expunge. Remaining weak/identity maps are documented as service-local, metadata-audit, local traversal, or diagnostic-local state. | `RuntimeTest.findContainerSharesWeakRegistryMonitorWithRegistration()` proves lookup does not race registration. |

The compiler/JIT bucket should remain separate unless an interpreter runtime
path depends on it. The compiler counter atomics belong in their own PR from
`master`; JIT generated static fields and `Ctx.Current` need a dedicated JIT
ownership proof before changing interpreter code for them.

### Ambient `ConstantPool` Lookup

Risk:

- `ConstantPool.getCurrentPool()` hides the owner from method signatures.
- Raw `ThreadLocal` state can be stale, absent, or leaked across pooled Java
  threads if cleanup is missed.
- Async callbacks are especially risky because they resume on Java threads that
  are not the original interpreter thread.

Required closure:

- Prefer explicit `Frame`, `Container`, `ServiceContext`, or `ConstantPool`
  parameters where the caller already has one.
- Keep `ConstantPool.withPool(...)` only as a narrow boundary bridge.
- Add assertions in bridge code:

  ```java
  ConstantPool pool = container.getConstantPool();
  try (var _ = ConstantPool.withPool(pool)) {
      ConstantPool.assertCurrentPool(pool, "Owner.boundary");
      ...
  }
  ```

- For async callbacks, assert that the scoped pool is the pool owned by the
  handle/container that scheduled the callback.
- Candidate runtime sites are listed in
  [constant-pool-state-audit.md](constant-pool-state-audit.md).

This branch also removed the raw public `ConstantPool.setCurrentPool(...)`
setter after converting the last runtime startup caller. That is intentionally
stricter than leaving it unused: future code now has to use a lexical
`withPool(...)` scope, which restores the previous value on normal and
exceptional exits.

### Shared `ConstantPool` Mutation After Runtime Publication

Risk:

- `ConstantPool.register(...)`, `ensure*Constant(...)`, locator maps, and
  `f_listConst` mutate pool state.
- Some maps are concurrent or copy-on-write, but the pool as a whole is not a
  general parallel mutation abstraction.
- If a pool is still mutating while multiple containers execute against it,
  readers can observe incomplete registration or invalid owner relationships.

Required closure:

- `ConstantPool.markRuntimePublishedForDiagnostics(...)` establishes an opt-in
  runtime publication boundary. `MainContainer.invoke0(...)` marks the pool
  after entry setup and module singleton resolution, immediately before the
  user call chain is invoked.
- `ConstantPool.register(...)` rejects new registrations after that marker when
  `-Dxvm.asm.validateConstantPoolLateRegistration=true` is enabled; existing
  constants are still returned normally.
- The first diagnostic same-JVM stress run now fails on
  `ClassComposition.<init>(...)` registering private/struct access-type
  constants for `TestProps:Standard` during `New_1`. That is not a wrong-owner
  leak by itself, but it proves the runtime still extends the pool after user
  execution begins. The fix is to pre-warm those composition/access constants,
  move non-logical helper state out of pool registration, or add a narrow
  documented allowlist if the late registration is proven safe.
- If runtime registration remains necessary, protect it with an explicit
  owner/synchronization policy and tests.
- Run same-JVM direct and parallel stress with ownership diagnostics enabled.

### Runtime-Executed `Op` Caches

Risk:

- Some op classes cache constants or resolved targets after seeing a `Frame`.
- If the decoded op graph is reused across containers/pools, those caches can
  carry a value from the first owner into the second.
- Link/address caches are safe only if resolved before publication under
  exclusive ownership.

Required closure:

- Prove decoded op graphs are owner-confined or method-confined.
- Add tests that run the same method/module through two containers and validate
  no op cache contains the wrong owner.
- If confinement cannot be proven, remove the field cache and resolve from the
  current frame, or key the cache by `ConstantPool`/owner. This branch used the
  simpler removal for frame constants: `Frame.getConstant(...)` is an indexed
  local-constant lookup plus `Class.cast(...)`, while `resolveType(...)` and
  condition evaluation still use the same owner-local machinery as before. The
  removed fields did not cache resolved types or evaluated conditions, so the
  runtime semantics and meaningful caching behavior are preserved; the only
  repeated work is the cheap owner-correct constant lookup.
- Keep eager address/link caches only with documented link-before-publication
  ordering.

Completed in this branch:

- `JumpCond` and `JumpNCond` resolve the `ConditionalConstant` from the current
  `Frame` on each execution and no longer hold `m_cond`.
- `OpTest.calculateCommonType(...)` and
  `OpCondJump.calculateCommonType(...)` resolve `m_nType` from the current
  `Frame` when the op was deserialized. They do not write that value to the
  shared op object.
- `m_typeCommon` remains as an assembly-time source-op argument field. It is
  encoded to `m_nType` during write/registration; it is not a runtime cache for
  deserialized methods.

The separate address/link fields (`m_opDest`, `m_aOpCase`, `m_aOpCatch`) are
not frame-constant caches. They link ops inside one method graph. They remain a
must-audit category for eager link-before-publication ordering, not a known
cross-pool owner leak from this wave.

### Manual Lazy Null Caches

Risk:

- `if (field == null) field = ...` has no happens-before edge.
- In shared objects this can duplicate work, publish partially initialized
  helper state, or store an owner-derived value from the wrong owner.
- The pattern makes future parallel compiler/runtime execution unsafe by
  assumption rather than by API.

Required closure:

- If the cached value is immutable and owner-local, use final `Lazy` or
  `Lazy.Owner`.
- If it is keyed, use an owner-owned `ConcurrentMap.computeIfAbsent(...)`.
- If it is lifecycle/resettable state, use one `AtomicReference<State>` or one
  lock.
- If the object is provably request/thread confined, document that proof at the
  field or in the audit.

First-PR closure:

- The owner-bearing runtime `Op` constant caches were removed in wave 4.
- `xRegEx.RegExHandle.m_pattern` was converted to final `Lazy<Pattern>` in wave
  5. The cache is not owner-bearing, but the old plain field was still an
  unnecessary unsafe-publication pattern.
- `FSNodeConstant.m_constPath` remains a lazy per-node cache, because converting
  it to a final `Lazy` would be wrong with the current shallow-clone adoption
  path: a computed final lazy cell would be copied to the adopted constant. The
  correct minimal fix is to clear the adopted copy and recompute the literal in
  the destination pool; the field is volatile for safe publication of the
  immutable derived literal.
- `_native:fs.OSFileNode.created` was changed from `@Lazy` to a computed getter.
  The file node is native-owned, but the getter can be called from an
  application container. Caching the resulting `Time` handle in the native node
  retained the caller's owner. A computed getter matches `modified` and
  `accessed` and avoids any cross-owner cache.

### Thread-Local And Scoped Ambient State

Risk:

- `ThreadLocal` and `TransientThreadLocal` hide dependencies from APIs.
- Missing cleanup leaks context into later work on the same Java thread.
- `ScopedValue` is safer because scope is lexical, but it is still ambient
  context and must not become a cache holder.

Required closure:

- Permanent APIs should pass owners explicitly.
- Transitional bridges may use `ScopedValue<RuntimeScope>` only to point at the
  real owner objects.
- Add scope assertions at runtime boundaries.
- Do not store templates, handles, or metadata caches inside the scoped value.

Completed in this branch:

- `xRTServer.SimpleKeyManager` no longer stores the selected
  `KeyStoreHandle` in a per-thread slot after `chooseEngineServerAlias(...)`.
  The JDK TLS callback API later asks for certificate and private-key material
  by alias, so each route now has a stable synthetic TLS alias that encodes the
  route identity. `getCertificateChain(...)` and `getPrivateKey(...)` resolve
  that alias through the server's explicit `Router` state.
- This preserves the old per-route keystore behavior without retaining an
  owner-bearing handle in a pooled HTTPS worker thread. Removed or missing
  routes return the normal empty/null key-manager answers instead of observing
  whatever a previous handshake left on the same Java thread.

### Weak/Identity Owner Registries

Risk:

- Weak/identity maps often encode ownership or lifecycle.
- They are not inherently concurrent.
- Weak keys can disappear at times unrelated to runtime semantics.

Required closure:

- Document owner, lifetime, and synchronization for each map.
- Use owner-owned concurrent maps or immutable snapshots where shared.
- Keep diagnostic maps in `OwnershipDiagnostics` bounded so validation does not
  create a new long-lived owner graph.

Completed in this branch:

- `Runtime.f_containers` remains a weak diagnostic registry so containers are
  not kept alive only because they were observed by diagnostics. The bug was
  inconsistent synchronization: `registerContainer(...)` and `containers()`
  used the map monitor, while `findContainer(...)` iterated the same
  `WeakHashMap` without it.
- `findContainer(...)` now uses the same monitor. This does not change
  semantics or footprint. It only prevents concurrent registration or
  weak-map cleanup from mutating the registry while a lookup is iterating.
- Local identity maps in `ObjectHandle`, `xTuple`, `xRTDelegate`, and
  `TypeInfoReal` remain local visited/copy maps, not owner registries.
- `ServiceContext` weak maps remain service-local caches and are still audited
  by service confinement. `ConstantPool.f_setValidPools` remains under the
  broader metadata owner audit because it is part of pool validation, not a
  runtime container registry.

### Compiler And JIT State

Risk:

- Compiler AST and context caches assume request-local mutation.
- Non-final compiler/JIT statics are process-global and have no reset story.
- The JIT has separate generated static fields, `Ctx.Current`, and JIT-specific
  caches that may or may not share the interpreter runtime ownership model.

Required closure:

- Keep compiler counter atomics in a separate PR from this runtime-owner branch.
- For incremental compiler work, move mutable caches to a compilation request
  context or prove AST/request confinement.
- For JIT, prove generated `$INSTANCE` and `$scN` fields are classloader/type
  system scoped and cannot leak container-owned values, or route owner-bearing
  values through `Ctx.container` or a container-owned table.
- Keep detailed JIT analysis in [jit-implications.md](jit-implications.md).

### Remaining Clone Usage

Risk:

- `clone()` is almost never the right abstraction for owner-bearing structures
  because it copies object references without knowing which references are
  logical value state and which are helper/runtime state.
- The adoption bug proved that a final mutable helper object can be correct for
  one owner and catastrophically wrong after shallow clone.

Required closure:

- Do not introduce new owner-transfer `clone()` paths.
- For existing runtime/asm clone sites, classify each as one of:
  defensive immutable/logical array copy, same-owner handle view copy,
  constant-pool adoption, compiler/linker structural clone, or unknown.
- For owner-transfer clones, construct a fresh logical object or clear every
  non-logical field at the owner boundary.
- For compiler/linker clones, handle in the incremental compiler work unless a
  runtime container can observe the cloned state.

## Assertions To Add Or Keep

The branch already has focused tests and runtime ownership diagnostics. The next
assertion work should be explicit and opt-in first, then hardened where proven.

| Assertion/guard | Where | Purpose |
| --- | --- | --- |
| Reject new mutable runtime-template `INSTANCE` fields | Build/source scan | Prevent returning to constructor-published globals. |
| Reject `INSTANCE = this` in constructors | Build/source scan and `-Xlint:this-escape` | Prevent early publication of partially constructed templates. |
| Reject static owner-bearing runtime metadata fields | Build/source scan | Prevent process-global `TypeConstant`, composition, method, handle, or enum caches. |
| Guard `HandleConstant` cross-pool adoption | `HandleConstant.adoptedBy(...)` | Prevent live runtime handles from becoming shared logical constants. |
| Detect cloned forbidden helper fields | Opt-in `ConstantAdoptionValidator` via `xvm.asm.validateConstantAdoption` | Catch `Atomic*`, locks, references, thread-local cells, owner/runtime references, and mutable collections copied by clone. |
| Assert scoped pool equals explicit owner | Runtime callback/bridge sites | Catch stale or missing ambient `ConstantPool` context. |
| Assert handle/composition owner at boundaries | `OwnershipDiagnostics` and runtime entry points | Catch cross-container values before they surface as misleading XTC-level failures. |
| Assert no late pool registration after freeze | Opt-in `ConstantPool.register(...)` guard via `xvm.asm.validateConstantPoolLateRegistration` | Find runtime paths that mutate supposedly published pools. |
| Assert op cache owner confinement | Runtime stress and diagnostics | Prove or reject decoded op graph sharing across containers. |

## Test And Proof Requirements

No race fix is "proven" by one passing run. The proof standard for this branch
is practical and layered:

- deterministic unit tests for each concrete bug class;
- a version of the test that fails on `master` where possible;
- same-JVM direct sequence stress to expose stale process-global state;
- parallel manual-test stress to expose inter-container owner races;
- ownership diagnostics that fail on wrong-owner handles/compositions/templates;
- source scans that keep the fixed bad patterns from reappearing.

Current concrete proof points:

- `ConstantAdoptionTest` fails on detached `master` and passes here.
- `SingletonConstantTest` covers lifecycle and adopted singleton owner state.
- `NativeTemplatesTest` covers template lookup and enum-publication signatures.
- `runDirectSequenceStress` and `runParallelStress` pass on `TestProps` after
  the adoption hardening wave.
- Current focused verification on 2026-08-22:

  ```bash
  ./gradlew :javatools:test \
    --tests org.xvm.asm.ConstantHashCodeCacheTest \
    --tests org.xvm.asm.RegisterHashCodeTest \
    --tests org.xvm.asm.VersionTest \
    --tests org.xvm.asm.constants.TypeInfoMemberOwnershipTest \
    --tests org.xvm.runtime.RuntimeThisEscapeConstructionTest \
    --tests org.xvm.asm.ConstantAdoptionTest \
    --tests org.xvm.runtime.SingletonConstantTest \
    --tests org.xvm.api.InterpreterConnectorTest \
    --tests org.xvm.runtime.NativeTemplatesTest \
    --console=plain
  ```

  Result: `BUILD SUCCESSFUL in 10s`; `42 actionable tasks: 4 executed,
  2 from cache, 36 up-to-date`.

- Targeted forced lint verification after the same wave:

  ```bash
  ./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
    --no-configuration-cache \
    -Porg.xtclang.java.lint=true \
    -Porg.xtclang.java.warningsAsErrors=false \
    -Porg.xtclang.java.maxWarnings=10000 \
    -Porg.xtclang.java.maxErrors=10000 \
    --console=plain --warning-mode=all
  ```

  Result: `BUILD SUCCESSFUL in 8s`; `73` emitted `this-escape` diagnostics at
  `70` unique source locations, no `overrides` diagnostics, and only the two
  `Container.java` runtime constructor warnings remain.

- The current forced root lint/build source of truth is
  `/tmp/xvm-current-this-escape.log`:

  ```bash
  ./gradlew clean --console=plain --warning-mode=all \
    -PincludeBuildLang=false \
    -PincludeBuildAttachLang=false

  ./gradlew build --rerun-tasks --no-build-cache \
    -PincludeBuildLang=false \
    -PincludeBuildAttachLang=false \
    -Porg.xtclang.java.lint=true \
    -Porg.xtclang.java.warningsAsErrors=false \
    -Porg.xtclang.java.maxWarnings=10000 \
    -Porg.xtclang.java.maxErrors=10000 \
    --console=plain --warning-mode=all
  ```

  Result: `BUILD SUCCESSFUL in 1m 2s`, `141 actionable tasks: 141 executed`,
  `80` emitted `this-escape` warnings at `77` unique source locations, and no
  `NativeContainer.java` `this-escape` diagnostics.

- The later handle-construction wave was checked with a targeted javatools
  lint compile rather than another full clean root build:

  ```bash
  ./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
    -Porg.xtclang.java.lint=true \
    -Porg.xtclang.java.warningsAsErrors=false \
    -Porg.xtclang.java.maxWarnings=10000 \
    -Porg.xtclang.java.maxErrors=10000 \
    --console=plain --warning-mode=all
  ```

  Result: `BUILD SUCCESSFUL in 25s`; no `xRef.java` or `xOSFileNode.java`
  `this-escape` diagnostics. `RefHandleConstructionTest` ran with `tests="3"`
  and no skips/failures/errors.

- The runtime constructor-assertion wave was checked with a focused unit test
  and targeted javatools lint compile:

  ```bash
  ./gradlew :javatools:test \
    --tests org.xvm.runtime.RuntimeThisEscapeConstructionTest \
    --console=plain

  ./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
    --no-configuration-cache \
    -Porg.xtclang.java.lint=true \
    -Porg.xtclang.java.warningsAsErrors=false \
    -Porg.xtclang.java.maxWarnings=10000 \
    -Porg.xtclang.java.maxErrors=10000 \
    --console=plain --warning-mode=all
  ```

  Result: the focused test passed. The lint compile passed in `9s` with
  `40 actionable tasks: 40 executed` and emitted no `CallChain.java` or
  `xRTMethod.java` `this-escape` diagnostics.

- Narrow same-JVM and parallel runtime stress also passed without a new clean or
  lint rerun:

  ```bash
  CI=true ./gradlew :manualTests:runDirectSequenceStress \
    -PsameJvmIterations=1 \
    -PsameJvmModules=TestArray \
    --console=plain --warning-mode=all --no-daemon --no-configuration-cache

  CI=true ./gradlew :manualTests:runParallelStress \
    -PstressIterations=1 \
    -PstressModules=TestArray \
    --console=plain --warning-mode=all --no-daemon --no-configuration-cache
  ```

  Results: direct sequence stress passed in `52s`; parallel stress passed in
  `11s`.

The remaining audit items become must-fix when these tests or diagnostics show
that the owner object is shared, the cache is owner-bearing, or runtime execution
can observe a stale/mixed state.
