# Ambient Context Audit

This document audits the runtime and JIT ambient-context mechanisms called out
by [plans/xvm-memory-model-hygiene.md](plans/xvm-memory-model-hygiene.md).
It is analysis only. No runtime fixes are implemented here.

The rule used for classification is:

> Ambient context may temporarily carry the current owner across a legacy call
> tree, but it must not choose the owner for long-lived state, cached state, or
> cross-container values.

`ScopedValue` is safer than raw `ThreadLocal` because the binding is lexical,
but it is still hidden context. A method that creates constants, type metadata,
handles, containers, services, generated classes, or runtime singletons should
receive the owner explicitly unless the ambient lookup is a narrow, asserted
boundary bridge.

## Summary

| Mechanism | Classification | Short conclusion |
| --- | --- | --- |
| `ConstantPool.s_tloPool` and semantic `getCurrentPool()` calls | Must fix where semantic code depends on it; must audit at boundaries | Runtime boundary scopes are now mostly lexical and asserted, but deeper constants/type logic still manufacture owner-scoped values from a hidden thread-local pool. |
| `ConstantPool.f_tlolistDeferred` | Must audit | Per-pool deferred TypeInfo recursion list. It is owner-local, but it is still hidden per-thread state and must prove cleanup on all TypeInfo paths. |
| `ServiceContext.s_tloContext` | Must audit, should fix toward explicit context | `drainWork()` restores the value in `finally`, but helper APIs can still silently read whatever service happens to be current on the Java thread. |
| `MultiMethodStructure.s_tloIgnoreNative` | Benign/proven today, should fix if serialization is refactored | Serialization-only flag with private scoped use and `finally` cleanup. It is not owner-bearing, but the filtering option should eventually be explicit serializer state. |
| `TypeConstant.s_context` | Must audit, currently reasonable scoped use | Lexical context for recursive type-relation probes. It is not a cache owner, but the relation cache must continue to account for context-sensitive answers. |
| `TypeConstant.m_tloInProgress` | Must audit | Per-type recursion set using `TransientThreadLocal`; cleanup exists, adoption resets the cell, but relation-cache concurrency still needs stress coverage. |
| `TypeParameterConstant.f_tloReEntry` | Benign/proven after adoption reset | Per-constant compare recursion guard. It uses `push(...)` cleanup and reconstructs the cell on adoption. |
| `TransientThreadLocal` utility | Must audit for owner-bearing values; benign only with cleanup proof | The backing per-thread `IdentityHashMap` holds keys strongly until `remove()` or sentry close. The name does not make missed cleanup safe. |
| JIT `Ctx.Current` invocation scope | Must audit | A lexical generated-code boundary is reasonable, but generated class initialization and helper calls must prove the active `Ctx` belongs to the loading type system/container. |
| JIT bridge static objects initialized with `Ctx.get()` | Must audit, likely must fix before claiming JIT reentrancy | Static `$INSTANCE` fields are classloader-wide. If the classloader is shared across containers, the first active `Ctx` can choose metadata for later containers. |

## ConstantPool Current Pool

### Implementation

`ConstantPool` still has a process-wide static thread-local holder:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3729` | `getCurrentPool()` returns `s_tloPool.get()[0]`. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3769` | `withPool(pool)` writes a new value and returns an `Auto` that restores the previous value. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3741` | `assertCurrentPool(...)` checks that an explicit owner matches the ambient pool. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3755` | `assertCurrentPoolIfPresent(...)` accepts no ambient pool but rejects the wrong one. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4115` | Static `ThreadLocal<ConstantPool[]> s_tloPool`. |

The current `withPool(...)` shape is much better than the old open-ended
setter because nested scopes restore the previous value. It is still raw
`ThreadLocal` state. If code forgets the try-with-resource pattern, runs after
an async hop, or executes on a reused Java worker thread under a stale binding,
the signature gives no clue that owner lookup is happening.

The assertions are diagnostics, not production enforcement. With assertions
disabled, a wrong ambient pool can still be used unless the caller no longer
depends on `getCurrentPool()`.

### Why Thread-Local Current Pool Is Arbitrary And Dangerous

`ThreadLocal` does not mean "the current runtime owner". It only means "the
last value associated with this Java thread". That distinction is catastrophic
for a runtime that wants same-JVM repeated execution, parallel containers,
service callbacks, compiler workers, and JIT-generated code.

This is poor design even before adding parallel execution. It creates a hidden
global precondition: the caller must somehow know that a previous frame of code
installed the right pool and that no nested call replaced it. The type system
cannot express that precondition, the compiler cannot check it, and a unit test
that happens to run under a lucky outer scope can pass while the same helper
fails when called directly.

Concrete failure modes:

- A worker thread finishes work for container A with pool A in the thread-local
  slot because some path forgot to restore it. Later the same Java thread runs
  work for container B. `ConstantPool.getCurrentPool()` now silently returns
  pool A while the frame, service, handle, or template belongs to B.
- A nested helper temporarily installs pool B while an outer operation is
  working for pool A. If code inside the nested helper calls a method that was
  written assuming the outer owner, it manufactures helper constants in B.
- An async callback or native callback runs on a Java thread that never entered
  the original runtime scope. `getCurrentPool()` returns `null` or a stale pool
  from unrelated work.
- Two containers can run the same decoded metadata/type path concurrently. A
  hidden current pool makes it impossible to tell from the method signature
  which pool will receive newly interned constants.
- Assertions do not save production execution. `assertCurrentPool(...)` is
  useful in stress runs, but when `-ea` is off, a semantic
  `getCurrentPool()` call still accepts the wrong owner.

The damage is not limited to an immediate exception. The worse outcome is a
valid-looking object graph with mixed ownership:

```java
TypeConstant helper = ConstantPool.getCurrentPool()
        .ensureIntersectionTypeConstant(left, right);
```

If the current thread happens to contain pool B, that helper is interned in B
even if the type relation being checked belongs to pool A. Later caches, maps,
or runtime handles can retain that helper and fail far away from the original
bug with a misleading "wrong handle", "already initialized", or "not shared"
symptom.

Design rule: semantic code must not ask "what pool is on this Java thread?"
It must receive the pool from the owner it is actually operating on, such as a
`Frame`, `Container`, `TypeSystem`, `ConstantPool`, `ClassStructure`, or
already-owned constant. Ambient current-pool lookup is acceptable only as a
short transitional bridge at a boundary that already has an explicit owner and
asserts the two match.

### Runtime Boundary Scopes

These sites are transitional boundary bridges. They already have an explicit
owner and install an ambient pool only for older helper code.

| Site | Current status | Classification |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/runtime/MainContainer.java:198` | Uses the main module pool, scopes it at `invoke0`, and asserts it at line 200. | Benign/proven as a boundary bridge, but keep diagnostics. |
| `javatools/src/main/java/org/xvm/runtime/Container.java:119` | Uses `getConstantPool()` during main service creation and asserts it at line 121. | Benign/proven as a boundary bridge. |
| `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:116` | Scopes the native pool around resource initialization and asserts it at line 118. | Benign/proven as startup bridge. |
| `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:166` | Scopes native template loading and asserts it at line 168. | Benign/proven as startup bridge. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:309` | Scopes `f_pool` while draining service work and restores the service context in `finally` at line 333. | Benign/proven as scheduler bridge, but assertion-only. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSStorage.java:334` | Derives the owner from the watched storage handle's container and asserts at line 337. | Benign/proven for this callback shape. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:653` | Uses the request handler's captured `ServiceContext.f_pool` and asserts at line 654. | Benign/proven for request boundary. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerControl.java:108` | Uses the target container's pool and asserts at line 112. | Benign/proven for management invoke. |
| `javatools/src/main/java/org/xvm/api/InterpreterConnector.java:87` | Does not install a pool; it asserts any existing ambient pool matches the main container at line 88. | Benign/proven, with assertion-only caveat. |
| `javatools/src/main/java/org/xvm/tool/Runner.java:226` | Launch boundary around method lookup. | Acceptable bridge, should eventually be explicit all the way down. |

Failure modes if the bridge is wrong:

- Same-JVM repeated runtime can reuse a Java thread with a stale pool and create
  constants in the previous container's pool.
- Parallel containers can build type metadata under one pool while the caller
  uses handles/templates from another.
- Async callbacks can run on non-runtime Java threads and find no current pool
  unless the callback derives and scopes the owner itself.

Recommended diagnostics:

- Extend `OwnershipDiagnostics` to dump the current ambient pool, explicit
  frame/container/service pool, and the boundary owner name when a mismatch is
  detected.
- Promote the boundary checks to a diagnostic method that can throw without
  requiring `-ea` in stress mode.
- Keep `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java`
  as the focused unit coverage and add a stress-mode variant that runs with
  assertions disabled but ownership validation enabled.

### Semantic Current-Pool Lookups

These sites do real work based on the hidden current pool. They are the
remaining important ConstantPool ambient-context risk.

| Site | Why it is dangerous | Proper fix |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3471` | Fixed in this branch: `checkFunctionCompatibility(...)` is already an instance method on a pool, but it called `getCurrentPool().typeTuple0()`. A wrong or missing ambient pool changed the compatibility answer or threw. | The method now uses receiver `typeTuple0()`. `ConstantPoolDiagnosticsTest.functionCompatibilityUsesReceiverPoolWithoutAmbientPool()` covers the no-ambient failure mode. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6272` | Fixed in this branch: `isCovariantReturn(...)` used to resolve auto-narrowing through `ConstantPool.getCurrentPool()`. Type relation checks are runtime-relevant and can run under parallel containers. | The helper now requires an explicit `ConstantPool` parameter; old two-argument API shape is rejected by `TypeConstantOwnerApiTest`. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6352` | Fixed in this branch: `isContravariantParameter(...)` had the same hidden pool dependency. | Same explicit owner parameter as covariance. |
| `javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java:295,297,370,372,374,376` | Fixed in this branch: range-producing constant operations used to create the result in the ambient pool. Wrong scope registered the range constant in the wrong owner. | Numeric range folding now uses the receiver constant's pool. `ConstantRangeOwnerTest` covers missing and wrong ambient pools. |
| `javatools/src/main/java/org/xvm/asm/constants/IntConstant.java:725,739,753,767` | Fixed in this branch: same range-producing owner issue as `ByteConstant`. | Same receiver-pool fix. |
| `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:693` | Fixed in this branch: `isIdentityValid(...)` could register a shared property constant into the ambient pool to recover its component. Wrong scope mutated the wrong pool and changed later metadata lookup. | It now uses the owning `PropertyInfo` pool helper. `TypeInfoMemberOwnershipTest.propertyInfoPoolHelperUsesOwnerWithoutAmbientPool()` verifies that helper under a null ambient pool. |
| `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:506` | Fixed in this branch: `resolveNestedIdentity(pool, resolver)` accepted an explicit output pool, but resolver-backed `NestedIdentity` objects discarded it and later resolved generic signatures through `ConstantPool.getCurrentPool()`. A wrong or missing ambient pool made the result depend on unrelated thread state. | Resolver-backed nested identities now carry the explicit output pool. `NestedIdentityOwnerTest` runs under a wrong ambient pool and proves generic signature resolution still interns in the requested pool. |
| `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:694` | Fixed in this branch: private `pool()` helper hid a current-pool dependency in method-body annotation logic. | It now uses the owning `MethodConstant` pool. `MethodInfoTest.metadataPoolHelpersUseOwnerWithoutAmbientPool()` covers method-body annotation lookup with no ambient pool. |
| `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:1484` | Fixed in this branch: private `pool()` helper hid a current-pool dependency in method metadata merge/narrowing logic. JIT and runtime metadata can reach `MethodInfo`. | Attached method info now derives the pool from its `TypeInfo`; unowned assembly falls back to the head method identity. `MethodInfoTest.metadataPoolHelpersUseOwnerWithoutAmbientPool()` covers the no-ambient owner helper. |
| `javatools/src/main/java/org/xvm/asm/FileStructure.java:1009` | Error listener lookup redirects through the ambient pool if it differs from the file's pool. This is compiler/linker-facing, but wrong ambient state can hide diagnostics. | Prefer explicit error listener ownership. At minimum guard null and wrong-pool cases with a real diagnostic. |

Classification: must fix for the small obvious receiver-pool case; must fix
for runtime-reachable type and range creation; must audit for compiler/linker
metadata paths before incremental compiler work.

Recommended tests:

- `TypeConstantOwnerApiTest` proves the covariance/contravariance helpers no
  longer expose the old ownerless signatures and reject a missing owner pool.
- `ConstantRangeOwnerTest` proves numeric range folding works with no ambient
  pool and ignores a wrong ambient pool, returning a range owned by the
  receiver's pool.
- `ConstantPoolDiagnosticsTest.functionCompatibilityUsesReceiverPoolWithoutAmbientPool()`
  proves function compatibility uses the receiver pool even when no ambient
  pool exists.
- A two-pool unit test for `ConstantPool.checkFunctionCompatibility(...)` that
  runs under a wrong ambient pool and proves the result comes from the receiver
  after the fix.
- Range-operation tests for `ByteConstant` and `IntConstant`: install pool A as
  ambient, apply operands owned by pool B, and assert the produced range belongs
  to the intended output pool after the fix.
- Type-relation stress with two pools and union/auto-narrowing types that call
  covariance and contravariance from parallel threads.
- A source-shape test that rejects new semantic `ConstantPool.getCurrentPool()`
  calls outside an allowlist of boundary bridge methods.

## ConstantPool Deferred TypeInfo Context

`ConstantPool` owns a per-pool transient thread-local deferred list:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3188` | `addDeferredTypeInfo(...)` appends to the per-thread deferred list. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3200` | `hasDeferredTypeInfo()` checks the per-thread list. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3207` | `takeDeferredTypeInfo()` reads and removes the per-thread list. |
| `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4089` | `f_tlolistDeferred` is the per-pool `TransientThreadLocal<List<TypeConstant>>`. |

This is not process-global owner state. The `TransientThreadLocal` instance is
an instance field of the owning pool, so different pools do not share the same
deferred list. The code intentionally allows two threads to duplicate TypeInfo
work rather than block each other while the root object graph is being built.

The risk is hidden lifecycle, not wrong static ownership. A missed
`takeDeferredTypeInfo()` leaves stale deferred work attached to the Java thread
for that pool. Same-JVM repeated runs on a reused Java worker can then process
old deferred type work during a later operation on the same pool.

Classification: must audit. Promote to must-fix if stress shows stale deferred
lists after failed type-info construction or after runtime pool publication.

Recommended tests:

- Force TypeInfo recursion that creates deferred work, both successful and
  failing, and assert the deferred list is removed after the top-level build.
- Run the same TypeInfo build repeatedly on a single pooled Java thread and
  assert no previous deferred list is visible at the next entry.
- Run parallel TypeInfo builds on one pool and on two pools; assert duplicate
  work can occur but no deferred list crosses pools.

## ServiceContext Current Context

### Implementation

`ServiceContext` uses a static thread-local to identify which service is being
served by the current Java thread:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:171` | `getCurrentContext()` returns `s_tloContext.get()[0]`. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:303` | `drainWork()` saves the prior context and installs `this`. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:332` | `finally` restores the previous context. |
| `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2167` | Static `ThreadLocal<ServiceContext[]> s_tloContext`. |

The core scheduler use has cleanup. That makes it much less dangerous than an
unscoped static mutable field. It is still ambient owner lookup and still
depends on every service execution entering through `drainWork()`.

Current source callers are narrow:

| Site | Use | Classification |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/runtime/Utils.java:472` | `Utils.log(null, ...)` recovers a service context for timestamps and fiber labeling. | Should fix to require `Frame` or `ServiceContext`; currently diagnostic only. |
| `javatools/src/main/java/org/xvm/asm/Argument.java:51` | Debug name rendering uses the current frame's local constants. | Must audit because broad catch hides wrong-context failures; should fix to pass constants/frame explicitly. |
| `javatools/src/main/java/org/xvm/asm/OpVar.java:115` | Variable-name rendering uses the current frame's local constants. | Must audit; same wrong-frame and swallowed-error problem. |

Concrete failure modes:

- Same-JVM repeated runtime on a pooled thread can report/debug against the
  previous service if a non-`drainWork()` path calls these helpers.
- Parallel containers can produce misleading diagnostics if an ASM helper is
  called while a different service is current on the same Java thread.
- Async callbacks that run outside `ServiceContext.drainWork()` see `null` and
  can fall back to vague names or throw if the caller expects a context.
- The `catch (Throwable ignore) {}` blocks in `Argument` and `OpVar` hide
  wrong-owner bugs by converting them into generic debug names.

Recommended fixes:

- Replace helper calls with explicit `Frame`, `ServiceContext`, or
  `Constant[] localConstants` parameters where the caller has them.
- Keep `getCurrentContext()` only as a boundary diagnostic bridge.
- Replace swallowed `Throwable` in debug-name helpers with narrower failure
  handling or a debug diagnostic counter.

Recommended tests/diagnostics:

- A scheduler test that nests service execution and asserts the prior context
  is restored after success, suspension, and exception paths.
- A pooled-thread same-JVM test that calls debug rendering before and after a
  service drain and asserts no stale context is observed.
- Ownership diagnostics should dump `ServiceContext.getCurrentContext()`, the
  explicit frame context, and whether they disagree.

## MultiMethodStructure Native-Ignore Context

`MultiMethodStructure` uses a private static thread-local flag to suppress
native/transient methods while serializing synthetic Const interface functions:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:60` | Serialization checks parent shape and flag. |
| `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:66` | Sets the flag before delegating to `super.assembleChildren(...)`. |
| `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:70` | Clears the flag in `finally`. |
| `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:84` | `getChildrenCount()` changes count behavior while the flag is set. |
| `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:185` | `children()` filters transient/native methods while the flag is set. |
| `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:406` | Static `ThreadLocal<Boolean> s_tloIgnoreNative`. |

This flag is not an owner lookup and does not cache container state. It is
private, scoped with `finally`, and only affects serialization traversal.

Classification: benign/proven today, should fix if serialization is refactored.

Why it is still ugly:

- `getChildrenCount()` and `children()` have hidden behavior that depends on a
  thread flag rather than an explicit serialization option.
- A future parallel serializer or visitor API could accidentally reuse those
  methods outside the intended dynamic scope.
- The flag says "ignore native" but actually drives a serialization shape for
  synthetic Const-interface methods. The lifetime should be visible at the
  serializer boundary.

Recommended future fix:

- Replace the thread-local flag with an explicit `AssemblyOptions` /
  `ChildTraversalOptions` parameter passed through serialization.
- Until then, add a focused serialization test that concurrently assembles a
  Const multimethod and a normal multimethod and verifies the native/transient
  filtering does not bleed between them.

## TypeConstant Scoped Relation Context

### `s_context`

`TypeConstant` uses a private `ScopedValue<TypeConstant>` for context-sensitive
relation probes:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5835` | `getContext()` returns the bound type context or `null`. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5855` | `calculateContextFreeRelation(...)` binds a Null sentinel to suppress context. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6191` | Pseudo-type relation checks consult `getContext()`. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6306` | Union-context covariance binds the union context lexically. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8308` | Static `ScopedValue<TypeConstant> s_context`. |

This is a reasonable use of `ScopedValue`: the value is a temporary recursive
calculation parameter, not a cache owner. It avoids a raw `ThreadLocal` and
automatically restores nested relation scopes.

Classification: must audit, currently reasonable.

The reason it remains must-audit is the interaction with relation caching. The
relation cache is stored on the `TypeConstant` at
`javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8259`, and
`calculateRelation(...)` bypasses a cached relation when context is present and
auto-narrowing is involved at lines 5938-5945. That is a deliberate guard, but
the proof has to cover every context-sensitive relation path, not only the
obvious auto-narrowing case.

Recommended tests:

- Build two different union contexts and run relation checks in parallel under
  different `ScopedValue` bindings. Assert no cached answer from one context is
  reused in the other.
- Repeat the same relation after the scoped call exits and assert the
  context-free result is unchanged.
- Add a diagnostic assertion that, when `s_context` is bound, any relation
  cache hit that depends on auto-narrowing must be skipped or context-keyed.

### `m_tloInProgress`

Type relation recursion also uses a per-type `TransientThreadLocal` set:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5948` | Reads the current recursion set. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6047` | Creates/stores the per-thread recursion set. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6061` | Removes the type and clears the thread-local when empty. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7935` | Adoption cleanup resets `m_tloInProgress` on the adopted copy. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8264` | Field declaration. |

This is owner-local to a specific `TypeConstant`, and the current branch resets
it during adoption so a target-pool clone does not inherit a source-pool helper
cell. The recursion set itself is still hidden per-thread state.

Classification: must audit.

Recommended tests:

- Force a recursive relation path and assert `m_tloInProgress` is removed after
  both success and thrown exception.
- Adopt a type into a different pool after relation checks and assert the
  adopted type has no copied transient thread-local or relation helper state.
  `ConstantAdoptionTest` already covers the adoption reset pattern; keep that
  coverage tied to this audit.

### `s_setRecursions`

`s_setRecursions` is process-wide diagnostic suppression at
`javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8298`. In this
branch it is a concurrent set, not a plain `HashSet`. That makes it safe as a
best-effort global diagnostic filter. It should not be used for correctness.

Classification: benign/proven after the branch change.

## TypeParameterConstant Reentry Context

`TypeParameterConstant` has a per-constant `TransientThreadLocal<Boolean>`:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:208` | Avoids recursive parent comparison. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:212` | Uses `push(true)` in try-with-resource. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:225` | Registration clears the cached constraint type. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:229` | Adoption reconstructs the logical constant instead of shallow-cloning the final reentry marker. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:274` | Field declaration. |

This guard is not container-owned state and does not select a pool. The branch
already avoids shallow-cloning the final `TransientThreadLocal` during adoption.

Classification: benign/proven for current use, with utility-level
`TransientThreadLocal` cleanup caveat.

Recommended test:

- Keep an adoption test that proves the copied `TypeParameterConstant` gets a
  fresh reentry marker and does not share the source marker.

## TransientThreadLocal Utility

The utility implementation is at
`javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java`.

Important sites:

| Site | Role |
| --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:41` | `get()` reads from a backing per-thread identity map and installs `initialValue()` when absent. |
| `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:58` | `remove()` removes this key from the backing map. |
| `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:115` | `push(value)` returns a sentry that restores/removes on close. |
| `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:138` | Static backing `ThreadLocal<Map<TransientThreadLocal<?>, Object>>`. |

This class reduces the cost of many short-lived thread-local objects by using
one per-thread map. It does not make missed cleanup harmless. The backing
`IdentityHashMap` holds the `TransientThreadLocal` key strongly until
`remove()` or `Sentry.close()` cleans it up.

Classification:

- benign/proven for sites that always use try-with-resource `push(...)` or
  guaranteed `remove()`;
- must audit for any site that stores owner-bearing objects or can fail before
  cleanup;
- should fix if callers treat it as automatically garbage-collected transient
  state.

Recommended tests:

- Reflection-based utility test that creates transient locals in a pooled
  thread, closes/removes them, and verifies the backing map returns to its
  previous size.
- Source-shape test that flags `new TransientThreadLocal` fields holding
  runtime owners, handles, pools, frames, services, or containers.

## JIT `Ctx.Current` And Generated-Code Owner Lookup

### Invocation Scope

`Ctx.Current` is the JIT execution context:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/javajit/Ctx.java:29` | `Ctx` stores the JIT `Xvm` and JIT `Container`. |
| `javatools/src/main/java/org/xvm/javajit/Ctx.java:66` | Static `ScopedValue<Ctx> Current`. |
| `javatools/src/main/java/org/xvm/javajit/Ctx.java:74` | `Ctx.get()` reads the current scoped value. |
| `javatools/src/main/java/org/xvm/javajit/JitConnector.java:82` | `invoke0(...)` binds a new `Ctx(xvm, container)`. |
| `javatools/src/main/java/org/xvm/javajit/JitConnector.java:94` | The reflective entry recovers the same `Ctx` for module construction/invocation. |

This is the best current use of ambient context in the tree. The lifetime is a
single generated-code invocation, and most bridge/generated APIs still take
`Ctx` explicitly. The risk is not the lexical binding by itself. The risk is
what code does while the binding is active.

Classification: must audit.

Failure modes:

- Generated code or bridge code called outside `JitConnector.invoke0(...)`
  throws because `Ctx.Current` is unbound.
- Generated class initialization can occur under the wrong `Ctx` and initialize
  static fields from the wrong type system or container.
- Parallel JIT containers can race class initialization for shared classes if
  classloader ownership is not isolated.

### Generated Class Initialization

`CommonBuilder.assembleCLInit(...)` emits generated class initialization that
calls `Ctx.get()`:

| Site | Role |
| --- | --- |
| `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:657` | Begins generated static initializer assembly. |
| `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:678` | Generated `<clinit>` obtains `Ctx.get()`. |
| `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:685` | Uses generated class name and the owner `ModuleLoader`/pool to emit constants. |

This can be sound only if generated class initialization is always triggered
under the owning type system's `Ctx`, and if the static fields initialized are
type-system/classloader-owned immutable values. It is not sound for
container-owned handles, services, injections, resource views, or mutable
runtime data.

Classification: must audit, likely must fix if any generated static stores
container-owned values.

Recommended diagnostics:

- In JIT debug mode, generated `<clinit>` should assert that
  `Ctx.get().container.typeSystem` matches the `ModuleLoader`/`TypeSystemLoader`
  that owns the generated class.
- The JIT ownership dump should list generated classloader, type system,
  `$scN` constant fields, `$INSTANCE` fields, and the owner id encoded in
  `nObject.$meta` for each generated singleton.

### Bridge Static Singletons

The following bridge classes call `Ctx.get()` during object creation or static
initialization:

| Site | Risk |
| --- | --- |
| `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java:160` | Static final `eMutability.$INSTANCE` is created from the current `Ctx`. If the bridge classloader is shared, the first context to initialize the class selects the singleton metadata. |
| `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/numbers/FPNumber.java:526` | Static final `eRounding.$INSTANCE` has the same first-context-wins risk. |
| `javatools_jitbridge/src/main/java/org/xtclang/_native/io/TerminalConsole.java:24` | No-arg constructor calls `super(Ctx.get())`. This is safe only if all construction occurs inside a bound JIT invocation or explicit native-resource initialization scope. |
| `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:26` | `$ctx()` helper exposes ambient lookup to any bridge method. |
| `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:35` | `$xvm()` recovers the `Xvm` from ambient context. |
| `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:54` | `$owner()` uses ambient `Xvm` plus `$meta` owner id to find the container. |

Classification: must audit, likely must fix before claiming JIT reentrancy.

The bridge enum singletons are the most suspicious. They are Java static
fields, so they are safe under the Java memory model as singletons, but their
semantic owner is classloader-wide. That is only correct if the bridge
classloader/type system is also the intended owner and if the object does not
carry container-specific state. If it carries a container id or metadata from
the current `Ctx`, parallel or repeated containers can observe the first
container's object.

Recommended fixes:

- Prefer explicit `Ctx` parameters for construction paths that already have
  one.
- For bridge singleton enum metadata, use a type-system-owned table keyed by
  the owning JIT `TypeSystem` instead of a raw static `$INSTANCE`, unless the
  classloader boundary is proven one-owner.
- If a classloader-owned static is intentional, add a debug assertion that the
  active `Ctx` belongs to the same `TypeSystemLoader` every time the static is
  used.

Recommended tests:

- Construct two JIT containers in one JVM and force `Array.eMutability` and
  `FPNumber.eRounding` initialization under container A, then use them under
  container B. Assert either the classloader/type-system owner is intentionally
  shared or the singleton is owner-local.
- Run the same test in parallel so the class-initialization race is exercised.
- Add a source-shape test that rejects `static ... = ... Ctx.get()` in
  `javatools_jitbridge` unless the field is on a documented allowlist.
- Add a JIT dump that prints `Ctx.xvm`, `Ctx.container.id`, generated
  classloader identity, and static singleton owner ids.

## Non-Owner ThreadLocal Noise

The scan also finds `ThreadLocalRandom` in utility/test/runtime random paths,
for example:

- `javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java:78`
- `javatools_utils/src/main/java/org/xvm/util/WeakHasherMap.java:61`
- `javatools/src/main/java/org/xvm/runtime/template/_native/numbers/xRTRandom.java:314`

These are not ambient owner context. They do not select a container, pool,
frame, service, or JIT `Ctx`. They are benign for this audit unless randomness
itself becomes part of a deterministic compiler/runtime test.

## Recommended Work Order

1. Fix the obvious receiver-pool bug in
   `ConstantPool.checkFunctionCompatibility(...)`.
2. Fix runtime-reachable semantic `ConstantPool.getCurrentPool()` uses in
   `TypeConstant`, `ByteConstant`, and `IntConstant` by passing an explicit
   output/owner pool.
3. Add an allowlist source-shape test for semantic `getCurrentPool()` so new
   ownerless calls do not appear.
4. Add ambient owner state to `OwnershipDiagnostics`: current pool, current
   service context, optional type relation context, and JIT `Ctx` when present.
5. Add ServiceContext restoration and stale-context tests.
6. Add JIT two-container tests for generated `<clinit>`, `$scN`, and bridge
   `$INSTANCE` ownership.
7. Decide whether `ConstantPool.withPool(...)` should migrate from raw
   `ThreadLocal` to a single `ScopedValue<RuntimeOwner>` bridge. This is useful
   only after permanent APIs keep passing explicit owners.

## Merge Gate For This Category

Before claiming ambient runtime context is safe by default:

- no semantic `ConstantPool.getCurrentPool()` calls should remain outside a
  documented boundary allowlist;
- every boundary bridge should compare ambient owner against an explicit owner
  and be able to throw in stress mode without relying on `assert`;
- same-JVM repeated runtime and parallel-container stress should run with the
  ambient owner diagnostics enabled;
- JIT generated class initialization must prove classloader/type-system owner
  agreement with the active `Ctx`;
- bridge static `$INSTANCE` fields must either be proven classloader-owned and
  immutable or moved to owner-local tables.
