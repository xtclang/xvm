# Ambient Registry Must-Audit Classification

This document classifies the remaining ambient context and weak/identity
registry sites that can affect reentrant runtime execution. It is analysis
only; no source changes are made here.

Related background:

- [ambient-context-audit.md](ambient-context-audit.md)
- [scoped-value.md](scoped-value.md)
- [must-audit-backlog.md](must-audit-backlog.md)
- [state-inventory.md](state-inventory.md)
- [runtime-ownership-hardening-ledger.md](runtime-ownership-hardening-ledger.md)
- [ownership-diagnostics.md](ownership-diagnostics.md)

Classification rule:

- `MUST FIX`: a known owner-bearing static/ambient value, wrong-owner hazard,
  or runtime registry shape is unsafe for parallel/repeated containers.
- `MUST AUDIT`: safety depends on a scheduler, classloader, owner, or cleanup
  invariant that is not fully encoded in the API or tests.
- `SHOULD FIX`: not a known runtime correctness bug, but the current shape hides
  state or makes future reentrant review harder.
- `DONE`: the risky shape was already fixed and has a source/test guard.
- `PROVEN CONFINED`: the map/context is local to one traversal, build object, or
  lexical calculation and does not own runtime state.

## Summary

| Site/category | Classification | Short conclusion |
| --- | --- | --- |
| JIT generated `<clinit>` owner-bearing static fields | MUST FIX | Generated classes initialize constants, injected statics, and singleton `$INSTANCE` from `Ctx.get()` into classloader-wide fields. That is unsafe unless the classloader is proven one-owner or the state moves to owner-local tables. |
| JIT bridge static enum/class singletons using `Ctx.get()`, `$ctx()`, or `$xvm()` | MUST FIX | Bridge statics can capture the first active `Ctx` or fail during unbound class initialization. Several store `TypeConstant` metadata in static objects. |
| JIT bridge ambient helper API (`nObject.$ctx()`, `$xvm()`, `$owner()`) | MUST FIX by API before JIT reentrancy claim | Hidden current-`Ctx` lookup is usable from any bridge method; `$owner()` combines current XVM with an object-encoded owner id. Static uses are must-fix; ordinary method uses need explicit `Ctx` proof or an active-owner assertion. Completion read recorded in `jit-global-owner-classification.md`. |
| JIT `OpCondJump.buildUnary(...)` `Ctx.get().container` | MUST AUDIT | JIT build/link dead-code evaluation depends on ambient container state instead of explicit build context. |
| JIT `Xvm` weak registries (`containers`, `typeSystems`, `moduleLoaders`) | MUST AUDIT | The maps are concurrent, but live generated objects recover owners through weak container ids. Container lifetime and classloader reuse need proof. |
| `ConstantPool.s_tloPool` and `withPool(...)` bridge | MUST AUDIT, SHOULD FIX | Semantic getter is gone, but the raw thread-local bridge remains. Runtime bridges are asserted; compiler/JIT/build bridges still rely on disciplined lexical use. |
| Runtime `ConstantPool.withPool(...)` boundary scopes | DONE as transitional bridge | Runtime entry/callback sites derive an explicit owner pool and assert the ambient bridge matches it. Assertions are still not production enforcement. |
| Compiler/ASM/JIT build `ConstantPool.withPool(...)` scopes | MUST AUDIT | These scopes are lexical but generally not asserted. They can affect runtime-published pools and JIT-generated metadata. |
| `ConstantPool.f_tlolistDeferred` | MUST AUDIT | Per-pool deferred TypeInfo state is hidden per Java thread and is cleaned only when `takeDeferredTypeInfo()` removes it. |
| `ConstantPool.f_setValidPools` | MUST AUDIT | Identity set is pool-owned, but lazy mutation is not synchronized and assumes a single validation/link phase. |
| `ServiceContext.s_tloContext` | MUST AUDIT, SHOULD FIX | `drainWork()` restores the prior context, but diagnostics/debug helpers can read stale, absent, or wrong service context from a pooled worker. |
| `ServiceContext.f_mapOpInfo` | MUST AUDIT | Service-local weak op cache stores owner-bearing `TypeComposition`, `CallChain`, `ClassTemplate`, and method metadata under a single-active-service invariant. |
| `ServiceContext.m_mapTransient` | MUST AUDIT | Service-local weak transient-field map stores owner-bearing handles and relies on service confinement plus weak-key lifetime. |
| Interpreter `Runtime.f_containers` weak registry | DONE | All access paths now synchronize on the weak map monitor and `RuntimeTest` covers lookup/registration monitor sharing. |
| `OwnershipDiagnostics` identity maps | PROVEN CONFINED | Maps are per-dump/per-validation traversal state and do not outlive the call. |
| Local identity visited/copy/build maps | PROVEN CONFINED | `TypeInfoReal`, handle sharing checks, tuple/delegate sharing checks, method assembly, and JIT `BuildContext` maps are traversal/build-local, not ambient owner registries. |
| `TypeConstant.s_context` | MUST AUDIT | `ScopedValue` cleanup is lexical, but relation caching must prove no context-sensitive answer is reused under a different context. |
| `TypeConstant.m_tloInProgress` | MUST AUDIT | Recursion guard cleanup exists and adoption clears it, but relation-cache/stress coverage is still needed. |
| `TypeParameterConstant.f_tloReEntry` | DONE | The guard is per constant, scoped with `push(...)`, and adoption reconstructs a fresh helper. |
| `MultiMethodStructure.s_tloIgnoreNative` | PROVEN CONFINED, SHOULD FIX | Private serialization flag is restored in `finally` and is not owner-bearing. Replace with explicit serializer options if that code is refactored. |
| `TransientThreadLocal` utility | MUST AUDIT, SHOULD FIX | The backing per-thread `IdentityHashMap` holds keys strongly until `remove()`/sentry close, and `get()` installs even a null initial value. Callers must prove cleanup. |
| `Collections.synchronizedMap` | DONE | No production source hits were found in this audit scope. |
| Removed `xRTServer.SimpleKeyManager` thread-local route state | DONE | TLS route key-store state now resolves through explicit route data and has a source-shape test. |

## MUST FIX

### JIT Generated `<clinit>` Static Owner State

Sites:

- `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:657`
- `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:678`
- `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:701`
- `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:737`
- `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:790`
- `javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:797`

Owner/lifetime:

- The generated Java field lifetime is classloader-wide.
- The owner used to initialize the field is the dynamic `Ctx` bound at class
  initialization time.
- `TypeSystemLoader` and `ModuleLoader` explicitly support sharing module
  loaders/classes across type systems.

Cleanup discipline:

- None. Java static field initialization is one-shot.
- A wrong first `Ctx` becomes the permanent value for that generated class.

What can go wrong:

- A generated class shared by two containers can initialize `$scN`,
  injected static properties, or `$INSTANCE` from container A and serve the
  same static to container B.
- A generated `<clinit>` that runs outside `JitConnector.invoke0(...)` fails
  because `Ctx.Current` is unbound.
- Parallel containers can race class initialization; the first thread to load
  the class chooses owner-bearing metadata for all later users.
- Static injected resources are especially suspicious because the injector is
  container-specific.

Existing tests/diagnostics:

- Existing JIT tests check generated constructor/method descriptor shape, not
  two-container static ownership.
- No source-shape guard currently rejects generated static initialization from
  `Ctx.get()`.

Proper fix:

- Keep classloader-owned statics only for values proven immutable and owned by
  the classloader/type system.
- Move container-owned static properties, singleton instances, and injected
  resources to a table keyed by `Ctx.container` or by the owning JIT
  `TypeSystem`.
- Add generated debug assertions that the active `Ctx.container.typeSystem` is
  the owner of the generated class loader before any generated `<clinit>` uses
  it.

Recommended PR slice:

- `jit-generated-static-owner-proof`: add a two-container JIT test that forces
  generated class initialization under container A and then uses the same
  generated class under container B. Fix or document every static field that
  fails the owner check.

### JIT Bridge Static Enum/Class Singletons

Sites:

- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/eBoolean.java:14`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/eNullable.java:11`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/eOrdered.java:11`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java:88`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java:156`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.java:160`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/numbers/FPNumber.java:522`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/numbers/FPNumber.java:526`
- Related bridge statics and metadata helpers in `Boolean`, `Nullable`,
  `Ordered`, `String`, `ContainerControl`, `TerminalConsole`, and `Exception`
  that call `$ctx()`, `$xvm()`, or `$owner()`.

Owner/lifetime:

- Bridge classes are Java classloader statics.
- Several static enum/class objects store `TypeConstant` metadata, such as
  `Enumeration.$publicType`.
- `nObject` stores only an encoded container id in `$meta`; it does not store a
  final owner reference.

Cleanup discipline:

- Static `$INSTANCE`, `$names`, and `$values` fields have no cleanup.
- The first successful class initialization publishes the object for the
  lifetime of the classloader.

What can go wrong:

- Static initialization under container A stores A's pool/type metadata in a
  bridge singleton that container B later reads.
- Static initialization outside a bound `Ctx` throws from `Ctx.get()`.
- An object created under one XVM but used while another XVM is current can
  resolve `$owner()` through the wrong `Xvm` because `$owner()` calls
  `$xvm().getContainer($ownerId())`.
- Static native/core enum values that use `super(null)` may be intended as
  native-owned, but their metadata helpers still read the current XVM/pool.

Existing tests/diagnostics:

- No bridge two-container ownership test was found.
- No source-shape test rejects `static ... Ctx.get()` or static bridge
  construction that calls `$ctx()`/`$xvm()`.

Proper fix:

- Replace raw bridge `$INSTANCE` fields that store owner-bearing metadata with
  owner-local lookup tables keyed by `Ctx.container.typeSystem` or the native
  type system.
- Where a value is truly native-classloader-owned, construct it without
  ambient `Ctx` and assert that all metadata comes from the native type system.
- Prefer constructors and helper methods that accept `Ctx` explicitly. Avoid
  calling `$ctx()` from constructors that already received `ctx`.

Recommended PR slice:

- `jit-bridge-static-owner-fix`: start with the enum/class bridge singletons
  (`eBoolean`, `eNullable`, `eOrdered`, `Array.eMutability`,
  `FPNumber.eRounding`) and add a source-shape guard for static ambient
  `Ctx.get()` or `$ctx()` use in `javatools_jitbridge`.

## MUST AUDIT

### JIT Bridge Ambient Helpers

Sites:

- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:26`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:35`
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nObject.java:54`

Owner/lifetime:

- `Ctx.Current` is scoped to a generated-code invocation.
- `$ctx()` and `$xvm()` expose that scoped value as global static helpers.
- `$owner()` combines the current XVM with this object's encoded owner id.

Cleanup discipline:

- `ScopedValue` cleanup is lexical at `JitConnector.invoke0(...)`.
- Helper calls have no local proof that they run under the right binding.

What can go wrong:

- Bridge code called from an async callback, static initializer, Java library
  callback, `toString()`, or test helper can run without a bound `Ctx`.
- A method that already has a `Ctx ctx` parameter can ignore it and read a
  different ambient `Ctx` through `$ctx()`/`$xvm()`.
- `$owner()` can return the wrong owner if an object escapes between XVM
  instances or if the weak container registry no longer contains its id.

Existing tests/diagnostics:

- No direct tests cover unbound bridge helper calls or wrong-current-XVM owner
  resolution.

Proper fix:

- Prefer `$xvm(Ctx ctx)` / `$owner(Ctx ctx)` style helpers, or pass `Ctx`
  directly where the method already has it.
- Add debug assertions that an object's encoded owner id is present in the
  active `Ctx.xvm` registry and matches expected native/main ownership.

Recommended PR slice:

- `jit-bridge-explicit-ctx-helpers`: replace bridge helper use in methods that
  already accept `Ctx`, then leave only documented static/native exceptions.

### JIT `OpCondJump.buildUnary(...)` Ambient Container

Site:

- `javatools/src/main/java/org/xvm/asm/OpCondJump.java:514`

Owner/lifetime:

- Dead-code elimination during JIT build evaluates a `ConditionalConstant`
  against `Ctx.get().container`.
- The real build owner is the `BuildContext`/type system currently generating
  bytecode.

Cleanup discipline:

- Relies on the surrounding JIT invocation `ScopedValue` binding.
- No assertion ties the ambient container to the `BuildContext` type system.

What can go wrong:

- Building code outside `JitConnector.invoke0(...)` fails because `Ctx` is
  unbound.
- A wrong ambient `Ctx` can evaluate conditional code under the wrong
  container/linker environment and remove or retain bytecode incorrectly.
- Parallel JIT containers can make this sensitive to which thread happens to
  hold the active `Ctx`.

Existing tests/diagnostics:

- No focused test was found for two-container conditional JIT build context.

Proper fix:

- Store the explicit link/evaluation context on `BuildContext` and pass it to
  `ConditionalConstant.evaluate(...)`.
- Assert that any temporary ambient `Ctx` agrees with the `BuildContext`
  owner during transition.

Recommended PR slice:

- `jit-build-context-owner`: remove the `Ctx.get()` read from op build logic
  and add a test that builds the same conditional op under two JIT containers.

### JIT `Xvm` Weak Registries

Sites:

- `javatools/src/main/java/org/xvm/javajit/Xvm.java:110`
- `javatools/src/main/java/org/xvm/javajit/Xvm.java:121`
- `javatools/src/main/java/org/xvm/javajit/Xvm.java:134`
- `javatools/src/main/java/org/xvm/javajit/Xvm.java:268`
- `javatools/src/main/java/org/xvm/javajit/Xvm.java:305`
- `javatools/src/main/java/org/xvm/javajit/Xvm.java:338`
- `javatools/src/main/java/org/xvm/javajit/Xvm.java:512`

Owner/lifetime:

- One JIT `Xvm` owns weak registries for containers, type systems, and module
  loaders.
- Bridge objects store owner ids, and `$owner()` resolves those ids through
  the current XVM registry.

Cleanup discipline:

- Registries use `ConcurrentHashMap` of `WeakReference` values.
- Cleanup is periodic/best-effort; the cleanup counters are plain ints because
  exact cleanup cadence is not intended to be correctness state.

What can go wrong:

- A live object graph that only remembers an owner id can lose its owner if the
  registry weak reference clears while the object is still reachable through a
  generated static or external Java reference.
- Package/name reuse after weak cleanup can make diagnostics confusing unless
  every lookup revalidates module/type-system identity.
- Parallel type-system creation relies on a mix of concurrent maps and
  explicit synchronization. That needs stress coverage with shared loaders.

Existing tests/diagnostics:

- No JIT weak-registry ownership stress was found.
- Existing interpreter `OwnershipDiagnostics` does not inspect JIT XVM
  registries.

Proper fix:

- Prove that any live generated object retains a strong path to its owner
  container or store a final owner/XVM reference in owner-bearing bridge
  objects.
- Add JIT ownership diagnostics that dump container id, type system, module
  loader, classloader identity, and weak-registry presence.

Recommended PR slice:

- `jit-owner-registry-diagnostics`: add a two-container JIT registry dump and
  assertions for `$owner()` resolution before changing storage.

### ConstantPool Ambient Bridge

Sites:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3736`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3750`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3765`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4106`

Owner/lifetime:

- Static per-Java-thread mutable holder for a temporary current
  `ConstantPool`.
- Intended only as a transitional bridge for call trees that still need an
  ambient pool while the boundary already has an explicit owner.

Cleanup discipline:

- `withPool(...)` returns an `Auto` that restores the previous value when
  closed.
- The thread-local holder array remains attached to the Java thread; only the
  slot value is restored.

What can go wrong:

- A missed close leaks a pool into later work on the same pooled Java thread.
- A nested wrong bridge can cause helper constants to be interned in a foreign
  pool.
- Assertions catch mismatches only when assertions are enabled.
- Async callbacks must rederive and bind their owner at callback entry; a
  launcher-thread binding does not cross arbitrary executor boundaries.

Existing tests/diagnostics:

- `ConstantPoolDiagnosticsTest` covers matching, missing, and wrong scoped
  pools.
- `ConstantPoolDiagnosticsTest.semanticCurrentPoolLookupIsBridgeOnly()` rejects
  new source calls to the removed semantic getter outside `ConstantPool`.
- `currentPoolLookupGetterDoesNotExist()` guards against returning the getter.

Proper fix:

- Keep shrinking boundary bridge use by passing `ConstantPool`, `Frame`,
  `Container`, or `ServiceContext` explicitly.
- Promote bridge mismatches to an opt-in diagnostic exception that does not
  rely on Java assertions.
- Later replace the raw `ThreadLocal` holder with a `ScopedValue` bridge only
  if the bridge points at explicit owner state and does not own caches.

Recommended PR slice:

- `constant-pool-bridge-runtime-diagnostics`: add non-assert stress-mode
  mismatch checks and ambient owner dump output before changing the bridge
  implementation.

### Runtime `ConstantPool.withPool(...)` Boundaries

Sites:

- `javatools/src/main/java/org/xvm/runtime/MainContainer.java:199`
- `javatools/src/main/java/org/xvm/runtime/Container.java:120`
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:117`
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:167`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:309`
- `javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSStorage.java:336`
- `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:653`
- `javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerControl.java:111`
- `javatools/src/main/java/org/xvm/api/InterpreterConnector.java:88`
- `javatools/src/main/java/org/xvm/tool/Runner.java:227`

Owner/lifetime:

- Each site derives the pool from an explicit container, service, handler, or
  connector owner.
- Lifetime is the runtime entry/callback method body.

Cleanup discipline:

- Try-with-resource restores the previous pool.
- Most runtime sites assert that the scoped pool matches the explicit owner.

What can go wrong:

- With assertions disabled, wrong binding remains a latent production bug.
- A callback that derives the wrong owner still installs a clean but incorrect
  pool.

Existing tests/diagnostics:

- Current-pool assertion tests cover the bridge mechanics.
- Same-JVM stress can combine this with ownership validation.

Proper fix:

- Keep these as transitional only.
- Add non-assert diagnostics in stress mode and remove bridge use once deeper
  helpers no longer need ambient pool lookup.

Recommended PR slice:

- Fold into `constant-pool-bridge-runtime-diagnostics`.

### Compiler/ASM/JIT Build `ConstantPool.withPool(...)` Scopes

Sites:

- `javatools/src/main/java/org/xvm/tool/Compiler.java:315`
- `javatools/src/main/java/org/xvm/compiler/Compiler.java:155`
- `javatools/src/main/java/org/xvm/compiler/Compiler.java:192`
- `javatools/src/main/java/org/xvm/compiler/Compiler.java:236`
- `javatools/src/main/java/org/xvm/compiler/Compiler.java:280`
- `javatools/src/main/java/org/xvm/asm/FileStructure.java:186`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:681`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1901`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:2075`
- `javatools/src/main/java/org/xvm/javajit/NativeTypeSystem.java:108`
- `javatools/src/main/java/org/xvm/javajit/JitConnector.java:64`

Owner/lifetime:

- Mostly compiler/linker/type-info/JIT startup phases.
- The owner pool is generally available from the receiver or file structure.

Cleanup discipline:

- Try-with-resource restores the previous value.
- These sites generally do not assert the bridge against an explicit owner.

What can go wrong:

- A future runtime path can call a build helper under the wrong ambient pool
  and mutate a runtime-published pool.
- JIT startup can initialize bridge/native resources under an ambient pool that
  is not checked against the type system being loaded.
- Compiler workers on pooled Java threads can leak stale pool state if a new
  scope is added without try-with-resource.

Existing tests/diagnostics:

- No complete source-shape test requires `assertCurrentPool*` near every
  build-phase bridge.
- Late-registration diagnostics can catch some runtime-published pool mutation
  after the fact.

Proper fix:

- Thread explicit pools through build helpers when the receiver already owns
  the pool.
- Add owner assertions to the remaining build/startup bridge sites before
  using them from runtime or JIT paths.

Recommended PR slice:

- `constant-pool-build-bridge-audit`: add source-shape coverage for new
  `withPool(...)` call sites and assertions to the JIT/native startup bridges.

### ConstantPool Deferred TypeInfo Context

Sites:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3188`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3200`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3207`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4080`

Owner/lifetime:

- The `TransientThreadLocal` object is owned by one `ConstantPool`.
- The list value is per Java thread and holds deferred `TypeConstant` work for
  that pool.

Cleanup discipline:

- `takeDeferredTypeInfo()` removes the value when a non-null list exists.
- `get()` on `TransientThreadLocal` can still install a null entry in the
  backing map when no value exists.

What can go wrong:

- Failed TypeInfo construction can leave stale deferred work on a pooled Java
  thread for the same pool.
- Repeated same-JVM runtime or compiler work can process deferred TypeInfo from
  a previous operation if the top-level build path misses `takeDeferredTypeInfo()`.
- The utility backing map can retain per-thread keys even for null values.

Existing tests/diagnostics:

- No direct stale-deferred-list cleanup test was found.

Proper fix:

- Make the TypeInfo top-level build path own an explicit deferred-work context
  and clear it in `finally`.
- At minimum, add a diagnostic assertion that the deferred list is empty at
  TypeInfo entry/exit boundaries.

Recommended PR slice:

- `type-info-deferred-context-cleanup`: add forced success/failure recursion
  tests on a single pooled Java thread, then clear or replace the hidden list.

### ConstantPool Valid-Pool Identity Registry

Sites:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:269`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:327`
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4065`

Owner/lifetime:

- Identity set owned by one `ConstantPool`.
- It records which upstream pools constants in this pool may refer to.

Cleanup discipline:

- No cleanup; lifetime is the pool lifetime.
- `buildValidPoolSet()` lazily mutates the set when empty.

What can go wrong:

- Two threads building or checking the set can mutate a non-concurrent
  `IdentityHashMap`-backed set concurrently.
- A runtime-published pool can extend its allowed upstream set after
  publication, weakening the freeze/validation story.
- Cross-container metadata validation can pass or fail depending on build
  timing.

Existing tests/diagnostics:

- Existing late-registration diagnostics check new constant registration, not
  this identity-set mutation specifically.

Proper fix:

- Build the valid-pool set during the exclusive link/freeze phase and publish
  it as an immutable identity snapshot.
- If lazy construction remains, guard it with synchronization or use a
  concurrent identity set with documented semantics.

Recommended PR slice:

- `constant-pool-valid-pool-freeze`: add parallel `buildValidPoolSet()` tests
  and either eager-freeze the set or synchronize lazy construction.

### ServiceContext Ambient Current Context

Sites:

- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:171`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:303`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:333`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2167`
- Callers: `javatools/src/main/java/org/xvm/runtime/Utils.java:473`,
  `javatools/src/main/java/org/xvm/asm/Argument.java:51`,
  `javatools/src/main/java/org/xvm/asm/OpVar.java:115`

Owner/lifetime:

- Static per-Java-thread service context for the service currently being
  drained.
- Intended owner is the active `ServiceContext`, not the carrier Java thread.

Cleanup discipline:

- `drainWork()` saves the prior context, installs `this`, and restores the
  prior context in `finally`.

What can go wrong:

- Debug/log rendering outside `drainWork()` can see `null` or a stale context
  from earlier work if a future path misses cleanup.
- `Argument` and `OpVar` catch `Throwable`, which can hide wrong-context bugs
  by returning fallback names.
- `Utils.log(null, ...)` assumes an ambient context and can fail when called
  from non-service threads.

Existing tests/diagnostics:

- No focused restoration/stale-context test was found.
- Ownership diagnostics do not currently report the current ambient
  `ServiceContext`.

Proper fix:

- Pass `Frame`, `ServiceContext`, or `Constant[]` explicitly to debug/name
  helpers.
- Keep `getCurrentContext()` only as a narrow diagnostic bridge.
- Add non-invasive diagnostics that compare ambient service context with an
  explicit frame context when both are present.

Recommended PR slice:

- `service-context-ambient-debug-cleanup`: remove ambient reads from
  `Argument`, `OpVar`, and `Utils.log(null, ...)`, then add pooled-thread
  restoration tests for success, suspension, and exception paths.

### ServiceContext Op-Info Weak Cache

Sites:

- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:226`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:242`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2179`
- Representative callers: `OpVar`, `OpIndex`, `OpInvocable`, and `OpCallable`
  cache `TypeComposition`, `TypeConstant`, `CallChain`, `MethodStructure`, and
  `ClassTemplate` values through this map.

Owner/lifetime:

- One weak map per `ServiceContext`.
- Keys are decoded `Op` instances; values are weak references grouped by enum
  category.
- Cached values are often container- or pool-owned runtime metadata.

Cleanup discipline:

- Weak keys/values allow cache entries to disappear.
- There is no synchronization around the `WeakHashMap` or nested `EnumMap`.
- Safety relies on the service scheduler allowing only one active fiber/thread
  to access the service context at a time.

What can go wrong:

- If the scheduler permits two Java threads to execute the same service context
  concurrently, the weak map or nested enum maps can corrupt or publish mixed
  category pairs.
- If an op graph is shared across containers, the per-service map prevents
  direct cross-service storage, but wrong explicit service context would cache
  owner-bearing metadata under the wrong service.
- Weak values can disappear between paired category reads, causing fallback
  recomputation; this is acceptable only if recomputation is owner-correct.

Existing tests/diagnostics:

- No direct service-confinement test for `f_mapOpInfo` was found.
- Current ownership diagnostics can catch some wrong-owner handles/templates
  after the cache has been populated, but they do not inspect this weak map
  directly.

Proper fix:

- Prove and test single-active-service access around all `getOpInfo` and
  `setOpInfo` callers.
- If confinement is not airtight, replace the map with a service-owned
  concurrent structure or guard all op-info access with the service scheduling
  lock.
- Add owner assertions when storing owner-bearing values.

Recommended PR slice:

- `service-op-info-confinement`: add a scheduler stress test that repeatedly
  caches op info under one service and two containers, then either document
  confinement or switch to a concurrent/locked cache.

### ServiceContext Transient Field Map

Sites:

- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:250`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:257`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:264`
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2184`
- Representative callers:
  `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:444` and
  `javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:848`

Owner/lifetime:

- One weak map per `ServiceContext`.
- Keys are `ObjectHandle.TransientId` values stored in object fields.
- Values are service-local transient field handles.

Cleanup discipline:

- Weak keys allow entries to disappear when the owning object no longer retains
  the transient id.
- There is no explicit close/reset for a service's transient values.
- No synchronization is used; safety relies on service execution confinement.

What can go wrong:

- Concurrent access by two Java threads serving the same service can corrupt
  the weak map.
- A transient initializer continuation can write a handle under a different
  service context if callback ownership is wrong.
- Owner-bearing handles can be retained for the life of the service if the
  transient id remains strongly reachable.

Existing tests/diagnostics:

- No focused transient-field weak-map lifecycle test was found.

Proper fix:

- Add owner assertions on `setTransientValue(...)` that the handle belongs to
  the service's container or allowed native parent.
- Prove service confinement with a stress test, or use a service-owned
  concurrent weak map.
- Document weak-key semantics at the transient field API boundary.

Recommended PR slice:

- `service-transient-map-owner-proof`: add transient-field same-JVM and
  parallel service tests with ownership validation, then decide whether the map
  can stay confined.

### TypeConstant Scoped Relation Context

Sites:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5835`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5855`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5938`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6310`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8316`

Owner/lifetime:

- Static `ScopedValue<TypeConstant>` used as temporary recursive type-relation
  context.
- Values are pool-owned type constants, but the binding is calculation-local.

Cleanup discipline:

- `ScopedValue` restores the prior binding lexically.

What can go wrong:

- Relation results are cached on `TypeConstant`; a relation answer that depends
  on scoped context can be reused in a later context-free or differently scoped
  calculation if the bypass rules are incomplete.
- Parallel relation checks under different contexts can expose missing cache
  context keys.

Existing tests/diagnostics:

- Existing tests cover removed ambient current-pool APIs and recursion
  diagnostic concurrency, not all context-sensitive relation cache paths.

Proper fix:

- Either prove every context-sensitive path bypasses the relation cache or key
  context-sensitive results by context.
- Add diagnostics for cache hits while `s_context` is bound.

Recommended PR slice:

- `type-relation-context-cache-proof`: add two-context parallel relation tests
  with auto-narrowing/generic/union cases.

### TypeConstant Recursion Guard

Sites:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5948`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6047`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6064`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7944`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8272`

Owner/lifetime:

- The `TransientThreadLocal` cell is owned by one `TypeConstant`.
- The value is a per-Java-thread recursion set for relation checks.

Cleanup discipline:

- `calculateRelation(...)` removes the type in `finally` and removes the
  thread-local value when the set becomes empty.
- Adoption clears the helper cell at owner change.

What can go wrong:

- Any path that adds to the recursion set and misses the `finally` would leak
  relation state into later work on the same Java thread.
- Concurrent `ensureRelationMap()` can duplicate relation maps; the current
  consequence appears to be lost cache work, but it needs stress proof.
- The backing `TransientThreadLocal` map retains keys strongly until removed.

Existing tests/diagnostics:

- `ConstantAdoptionTest.adoptedTypeConstantClearsOwnerLocalHelperState()`
  covers adoption cleanup.
- `TypeConstantRecursionDiagnosticsTest` covers the separate global recursion
  diagnostic set, not this per-type thread-local cleanup.

Proper fix:

- Add explicit cleanup tests for success and exceptional recursive relation
  paths.
- Consider carrying recursion state in an explicit relation-evaluation context
  if relation APIs are refactored.

Recommended PR slice:

- Fold into `type-relation-context-cache-proof`.

### TransientThreadLocal Utility

Site:

- `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:41`
- `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:58`
- `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:115`
- `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:138`

Owner/lifetime:

- One static backing `ThreadLocal<IdentityHashMap<TransientThreadLocal<?>,
  Object>>` per Java thread.
- Individual keys are the `TransientThreadLocal` instances owned by pools,
  constants, or other objects.

Cleanup discipline:

- `remove()` removes one key from the current thread's map.
- `push(...)` returns a sentry that restores or removes on close.
- `get()` installs the initial value even when that value is `null`.

What can go wrong:

- Pooled worker threads can accumulate keys for many transient locals if
  callers probe with `get()` and never remove.
- Owner-bearing values stored in the backing map leak into later work on the
  same Java thread if cleanup is missed.
- A `withInitial(...)` supplier that captures an owner can make the key itself
  retain owner state through the backing map.

Existing tests/diagnostics:

- No direct utility test verifies the backing map returns to its prior size
  after `get()`/`remove()`/`push()` usage.
- `ConstantAdoptionValidator` treats `ThreadLocal` and `TransientThreadLocal`
  fields as forbidden copied helper state.

Proper fix:

- Change the utility so `get()` does not install absent null initial values, or
  add a separate non-mutating peek operation for callers that only need to test
  presence.
- Add reflection-based cleanup tests for pooled-thread usage.
- Keep source-shape tests for owner-bearing `TransientThreadLocal` fields.

Recommended PR slice:

- `transient-thread-local-cleanup`: add utility tests first, then adjust
  `get()`/presence semantics if compatible.

## DONE

### Interpreter Runtime Container Registry

Site:

- `javatools/src/main/java/org/xvm/runtime/Runtime.java:56`
- `javatools/src/main/java/org/xvm/runtime/Runtime.java:66`
- `javatools/src/main/java/org/xvm/runtime/Runtime.java:75`
- `javatools/src/main/java/org/xvm/runtime/Runtime.java:149`

Owner/lifetime:

- One weak diagnostic registry per interpreter `Runtime`.
- Keys are containers; values are unused.

Cleanup discipline:

- `registerContainer(...)`, `containers()`, and `findContainer(...)` all
  synchronize on the same `WeakHashMap` monitor.
- Weak keys allow diagnostic observation without keeping containers alive.

What can go wrong:

- The old bug was inconsistent synchronization: lookup iterated the weak map
  while registration or weak-map expunge could mutate it.

Existing tests/diagnostics:

- `RuntimeTest.findContainerSharesWeakRegistryMonitorWithRegistration()` covers
  the shared-monitor guarantee.
- `RuntimeTest.registerContainerDoesNotObservePartiallyConstructedContainer()`
  covers post-construction registration.
- `OwnershipDiagnostics` dumps registry membership.

Proper fix:

- Already fixed. Keep the shared-monitor rule and tests.

Recommended PR slice:

- None unless the registry grows beyond diagnostics.

### ConstantPool Semantic Current-Pool Getter

Sites:

- Historical semantic `ConstantPool.getCurrentPool()` callers are gone.
- `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:169`
- `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:193`

Owner/lifetime:

- The public semantic getter no longer exists.

Cleanup discipline:

- Remaining private bridge access is through `withPool(...)` and assertions.

What can go wrong:

- Reintroducing a getter would make hidden owner lookup compile again.

Existing tests/diagnostics:

- Source-shape and reflection tests reject the getter and non-bridge source
  calls.

Proper fix:

- Already fixed. Keep the tests.

Recommended PR slice:

- None.

### Removed TLS Route ThreadLocal

Sites:

- `javatools/src/test/java/org/xvm/runtime/template/_native/web/xRTServerTest.java:27`

Owner/lifetime:

- HTTPS route key-store selection is now explicit route/server state rather
  than per-thread ambient state.

Cleanup discipline:

- No key-store handle is left on a pooled HTTPS worker thread.

What can go wrong:

- The old shape could leak one route's key-store handle into another TLS
  callback on the same Java thread.

Existing tests/diagnostics:

- `xRTServerTest.keyManagerDoesNotStoreRouteKeyStoreInThreadLocal()` guards the
  source shape.

Proper fix:

- Already fixed.

Recommended PR slice:

- None.

### Collections.synchronizedMap

Sites:

- No production source hits found by this audit.

Owner/lifetime:

- None.

Cleanup discipline:

- None.

What can go wrong:

- Not applicable for the current tree.

Existing tests/diagnostics:

- Source scan.

Proper fix:

- No action.

Recommended PR slice:

- None.

## PROVEN CONFINED

### OwnershipDiagnostics Identity Maps

Sites:

- `javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java:275`
- `javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java:276`
- `javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java:277`
- `javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java:279`

Owner/lifetime:

- Each `Dumper` owns its maps for one dump/validation traversal.
- The maps record visited objects, container names, and expanded lazy values.

Cleanup discipline:

- The maps are discarded when the `Dumper` call returns.

What can go wrong:

- They would be unsafe if shared across dumps or stored statically, but they
  are private per-call traversal state today.
- Forced-lazy mode can instantiate owner-local lazy cells, so it binds the
  owner's pool while doing so.

Existing tests/diagnostics:

- `OwnershipDiagnosticsTest` covers mismatch detection and runtime dump shape.

Proper fix:

- No map fix needed. Future improvement is to include ambient current pool,
  current service context, and JIT `Ctx` data in diagnostics.

Recommended PR slice:

- `ownership-diagnostics-ambient-dump`: add ambient context fields to failure
  output without changing traversal map ownership.

### Local Identity Visited/Copy/Build Maps

Sites:

- `javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:123`
- `javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:179`
- `javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:289`
- `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:636`
- `javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:742`
- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java:879`
- `javatools/src/main/java/org/xvm/asm/MethodStructure.java:2746`
- `javatools/src/main/java/org/xvm/javajit/BuildContext.java:297`

Owner/lifetime:

- Constructor-local copy maps, recursive visited maps, method-assembly maps,
  or one `BuildContext`'s method-generation state.

Cleanup discipline:

- Local maps are discarded at method/constructor exit.
- `MethodStructure` assembler and `BuildContext` maps are owned by build
  objects, not static runtime registries.

What can go wrong:

- These maps do not create cross-container owner state by themselves.
- If a build/assembler object becomes shared across threads in the future, its
  broader mutable state would need a separate compiler/JIT confinement audit.

Existing tests/diagnostics:

- `TypeInfoMemberOwnershipTest` covers TypeInfo ownership copying.
- Handle sharing checks are exercised indirectly by ownership diagnostics and
  cross-owner mask tests.

Proper fix:

- No ambient-registry fix needed.

Recommended PR slice:

- None for the runtime ambient-registry work.

### MultiMethodStructure Serialization Flag

Sites:

- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:60`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:66`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:70`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:185`
- `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:428`

Owner/lifetime:

- Private serialization-only flag.
- It does not carry a pool, container, service, handle, or type metadata owner.

Cleanup discipline:

- Set before `super.assembleChildren(...)` and cleared in `finally`.
- Nested calls that see the flag already true do not reset it.

What can go wrong:

- A future serializer or traversal API could call `children()` or
  `getChildrenCount()` expecting context-free behavior and instead observe the
  hidden flag.
- Current risk is behavioral clarity, not owner leakage.

Existing tests/diagnostics:

- No focused concurrent serialization test was found.

Proper fix:

- Replace the thread-local with explicit serialization/traversal options when
  that API is refactored.

Recommended PR slice:

- `assembly-serialization-options`: only when touching serializer traversal.

### TypeParameterConstant Reentry Guard

Sites:

- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:208`
- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:212`
- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:229`
- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:274`

Owner/lifetime:

- One final `TransientThreadLocal<Boolean>` per `TypeParameterConstant`.
- Used only as a recursive comparison guard.

Cleanup discipline:

- The recursive section uses `try (var _ = f_tloReEntry.push(true))`.
- Adoption reconstructs a fresh type parameter instead of shallow-cloning the
  helper.

What can go wrong:

- The general `TransientThreadLocal` caveat still applies, but this caller has
  scoped cleanup and no owner-bearing value.

Existing tests/diagnostics:

- `ConstantAdoptionTest` asserts the adopted constant gets a different
  `f_tloReEntry` helper.

Proper fix:

- No immediate fix.

Recommended PR slice:

- None.

## SHOULD FIX

### Ambient Context Source-Shape Guards

Owner/lifetime:

- This is a cross-cutting guard category for future changes, not one runtime
  object.

Cleanup discipline:

- Source-shape tests prevent reintroduction rather than cleaning up runtime
  state.

What can go wrong:

- New `ThreadLocal`, `TransientThreadLocal`, `ScopedValue`, `Ctx.get()`, or
  weak/identity registry sites can arrive without owner/lifetime review.

Existing tests/diagnostics:

- Current source-shape tests cover the removed `ConstantPool.getCurrentPool()`
  getter and xRTServer key-manager `ThreadLocal`.
- No unified ambient-registry allowlist exists.

Proper fix:

- Add a small allowlist test for ambient owner mechanisms:
  `ThreadLocal`, `TransientThreadLocal`, `ScopedValue`, `Ctx.get()`,
  `WeakHashMap`, `IdentityHashMap`, and `Collections.newSetFromMap(new
  IdentityHashMap...)`.
- Each allowlisted site should link to a doc entry with owner, lifetime, and
  cleanup.

Recommended PR slice:

- `ambient-registry-source-guard`: test-only/doc-only first, then promote new
  owner-bearing hits to code fixes.

## Recommended PR Order

1. `jit-bridge-static-owner-fix`: fix bridge static enum/class singleton owner
   capture and add source-shape guards for static ambient `Ctx` use.
2. `jit-generated-static-owner-proof`: prove or fix generated `<clinit>`
   owner-bearing statics with two-container JIT tests.
3. `jit-owner-registry-diagnostics`: add JIT owner/weak-registry diagnostics
   for `$owner()` and classloader/type-system ownership.
4. `service-context-ambient-debug-cleanup`: remove ambient service reads from
   debug/log helpers and add stale-context restoration tests.
5. `service-op-info-confinement` and `service-transient-map-owner-proof`: prove
   service-local weak maps are scheduler-confined or replace them with
   synchronized/concurrent owner-local structures.
6. `type-info-deferred-context-cleanup` and
   `type-relation-context-cache-proof`: close the remaining TypeInfo and
   relation-context hidden-thread-state assumptions.
7. `constant-pool-valid-pool-freeze`: publish the valid-pool identity registry
   during a single owner phase.
8. `ambient-registry-source-guard`: keep new ambient/weak/identity state from
   arriving without a classification.

## Row 134 Completion Sweep (2026-08-24)

This section closes must-audit backlog row 134 ("Ambient
`ThreadLocal`/`TransientThreadLocal`/`ScopedValue` state",
`must-audit-backlog.md:159`) by re-enumerating every ambient site in the
`javatools`, `javatools_utils`, and `javatools_jitbridge` main source trees and
assigning each one a closure category:

- `(a)`: lexical/scoped bridge with bounded lifetime and, where runtime-facing,
  owner assertions;
- `(b)`: proven thread-confined; no stale semantic value can be observed by
  later work on a pooled Java thread;
- `(c)`: needs explicit owner parameters; the ambient read itself is the
  defect to remove.

Enumeration command:

```
rg -n "ThreadLocal|TransientThreadLocal|ScopedValue" \
    javatools/src/main/java javatools_utils/src/main/java \
    javatools_jitbridge/src/main/java
```

plus indirect readers of `Ctx.Current` through `Ctx.get()` and the generated
`<clinit>` emission in `CommonBuilder`.

### Complete Site Table

| # | Site | Stored value / owner semantics | Set/clear discipline (evidence) | Pooled-thread stale risk | Load-bearing? | Category |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `ConstantPool.s_tloPool` (`javatools/src/main/java/org/xvm/asm/ConstantPool.java:4318`) | Static per-thread current-pool bridge slot; owner-bearing. | Access only via `withPool(...)` (`:3948`) and `assertCurrentPool*` (`:3910`, `:3929`); no getter exists. All 21 production `withPool(...)` call sites use `try (var _ = ...)`; 9 runtime boundaries also assert the explicit owner (`MainContainer.java:200`, `NativeContainer.java:118,168`, `Container.java:191`, `ServiceContext.java:310`, `InterpreterConnector.java:88`, `xOSStorage.java:337`, `xContainerControl.java:112`, `xRTServer.java:693`). | Only if a future caller skips try-with-resource; the holder array stays per thread but the slot is restored. | Yes: constants intern into whatever pool the slot names. | `(a)` for asserted runtime bridges; `(a)` without assertions for compiler/build scopes; remaining ambient consumers are the `(c)` trail. |
| 2 | `ConstantPool.f_tlolistDeferred` (`javatools/src/main/java/org/xvm/asm/ConstantPool.java:4292`) | Per-pool deferred TypeInfo work list, per Java thread. | Appended by `addDeferredTypeInfo` (`:3362`); drained by the single top-level driver `TypeConstant.ensureTypeInfo` (`TypeConstant.java:1752-1757`), cleared on `catch (Exception \| Error)` (`:1814-1818`), fail-loud entry guard throws on stale state (`:1737-1740`); `ensureObjectTypeInfo` drains at `:1923`. | Fail-loud: a leaked list makes the next build on the same pool+thread throw at entry rather than silently process stale work. Footprint: `hasDeferredTypeInfo` (`:3374`) probes with `get()`, installing a permanent null map entry per pool per thread. | Yes: deferred TypeInfo completion. | `(b)` |
| 3 | `ServiceContext.s_tloContext` (`javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2180`) | Static per-thread current service context; owner-bearing. | Single writer `drainWork()` saves prior (`:304-306`) and restores in `finally` (`:331`). Readers: `getCurrentContext()` (`:171`) from `Utils.java:473`, `Argument.java:51`, `OpVar.java:115` only. | No non-`finally` writer exists; non-service threads read `null`. Debug helpers can still read a *different* live service during nested drains, which is why the readers must go. | Readers are diagnostics-only (log timestamps, debug names behind `catch (Throwable)`). | Writer `(a)`; the three ambient readers `(c)`. |
| 4 | `MultiMethodStructure.s_tloIgnoreNative` (`javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:428`) | Static Boolean serialization flag; not owner-bearing. | Set `true` at `:66` only when previously `false` (guard `:60`), restored `false` in `finally` (`:70`); readers `:60,86,185` affect serialization traversal only. | None semantic; a `Boolean.FALSE` entry stays installed per thread (harmless). | Load-bearing for serialized shape, not for ownership. | `(b)` |
| 5 | `TypeConstant.s_context` (`javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8321`) | Static `ScopedValue<TypeConstant>` relation context; pool-owned value, calculation-local binding. | Bound at `:5855` (Null sentinel for context-free probes) and `:6310` (union context); read via `getContext()` (`:5835-5843`) and the cache-bypass guard (`:5940-5945`). `ScopedValue` restores lexically on all paths. | None: a `ScopedValue` binding cannot outlive the call or migrate to another thread. | Yes: relation answers. | `(a)`; the open item is relation-cache context-sensitivity proof, not cleanup. |
| 6 | `TypeConstant.m_tloInProgress` (`javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8282`) | Per-type recursion set, per Java thread. | Created lazily (`:8028`); set at `:6047`; `finally` removes the type and removes the cell when the set empties (`:6061-6065`) on success and throw (`:6058-6060` rethrows); adoption clears the cell with the other helper caches (`:7951-7959`). | Guaranteed clear via `finally`. Footprint: the probe at `:5948` uses `get()`, installing a null map entry per type per thread. Unproven corner: `setContaining(...)` nulling the cell concurrently with an in-flight `calculateRelation` on another thread. | Yes: recursion detection changes relation answers. | `(b)` |
| 7 | `TypeParameterConstant.f_tloReEntry` (`javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:274`) | Per-constant Boolean compare-reentry guard; not owner-bearing. | Probe `get() != null` at `:208`; scoped `push(true)` in try-with-resource at `:212` (prior value null, so the sentry removes the entry); adoption reconstructs the constant (`:229-233`). | Guaranteed removal via sentry. Footprint: the `:208` probe installs a null entry that stays. | Yes for compare correctness, not owner-bearing. | `(b)` |
| 8 | `TransientThreadLocal` backing map (`javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:138`) | Static per-thread `IdentityHashMap` keyed by `TransientThreadLocal` instances; owner semantics are the callers'. | `get()` installs even a null initial value (`:45-47`); `compute(...)` removes on null (`:75-76`); `computeIfAbsent(...)` never installs null (`:96-104`); `push(...)` restores or removes on close (`:115-133`); the per-thread map itself is never removed. | Infrastructure: risk is per caller, and rows 2, 6, and 7 enumerate every production caller. | Per caller. | Infrastructure; per-caller category. The `get()`-installs-null wart remains `transient-thread-local-cleanup`. |
| 9 | `Ctx.Current` (`javatools/src/main/java/org/xvm/javajit/Ctx.java:66`) | Static `ScopedValue<Ctx>`; owner-bearing (XVM + container). | Bound only at `JitConnector.invoke0` (`JitConnector.java:82`) for one generated-code invocation; read via `Ctx.get()` (`Ctx.java:74`). | None for the binding itself (lexical); no assertion ties the bound `Ctx` to the type system owning the executing class. | Yes. | Binding `(a)`; readers below decide the rest. |
| 10 | Static/ambient `Ctx.get()` readers: `Array.eMutability.$INSTANCE` (`javatools_jitbridge/.../ecstasy/collections/Array.java:160`), `FPNumber.eRounding.$INSTANCE` (`.../ecstasy/numbers/FPNumber.java:526`), `TerminalConsole()` (`.../_native/io/TerminalConsole.java:25`), `nObject.$ctx()/$xvm()/$owner()` (`.../ecstasy/nObject.java:26,35,54`), `OpCondJump.buildUnary` (`javatools/src/main/java/org/xvm/asm/OpCondJump.java:522`), generated `<clinit>` (`javatools/src/main/java/org/xvm/javajit/builders/CommonBuilder.java:678`) | First bound `Ctx` captured into classloader-wide statics, or ambient owner recovered inside helpers/build logic. | Static captures have no clear at all: first `<clinit>` wins for the classloader lifetime. Helper/build reads have no local proof of the right binding. | This is not thread-local staleness; it is permanent wrong-owner capture, worse than any row above. | Yes: singleton metadata, injection, owner resolution, dead-code elimination. | `(c)` |

### Non-Owner Noise And Guards

| Site | Note |
| --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/WeakHasherMap.java:61,69` | `ThreadLocalRandom` randomness only; no owner state. |
| `javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java:94` | Same. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/numbers/xRTRandom.java:314` | `ThreadLocalRandom.current()` per call; correct contract. |
| `javatools/src/main/java/org/xvm/asm/op/JumpNSample.java:67` | Newly noted: `static final ThreadLocalRandom f_rnd = ThreadLocalRandom.current()` captures the instance at class-init, violating the documented `current().nextX(...)` usage pattern. Current OpenJDK keeps seeds on `Thread`, so no cross-thread state leaks; contract/style wart only, not owner state. |
| `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:1070` | Comment reference to `ThreadLocal.java` hash constant only. |
| `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:160` | Guard, not a site: adoption validation rejects any `ThreadLocal` instance reachable from copied constant fields. |

### Deltas Versus The Previous Classification

- Newly recorded entries, all non-owner: `JumpNSample.f_rnd` static
  `ThreadLocalRandom` capture; the `ConstantAdoptionValidator` `ThreadLocal`
  rejection guard; and the three `ThreadLocalRandom` noise sites previously
  listed only in `ambient-context-audit.md`. Zero new owner-bearing ambient
  sites were found.
- Changed verdict for `ConstantPool.f_tlolistDeferred`: the earlier entry said
  repeated same-JVM work "can process deferred TypeInfo from a previous
  operation if the top-level build path misses `takeDeferredTypeInfo()`".
  Current code shows the sole top-level driver clears the list on the
  exceptional path (`TypeConstant.java:1814-1818`) and throws on stale state at
  entry (`:1737-1740`), so a leak is fail-loud, not silently processed.
  Reclassified `(b)`; the empty-at-boundary diagnostic recommendation stands,
  but the must-fix promotion trigger described earlier is gone.
- Refinement for `ServiceContext.s_tloContext`: `drainWork()` is confirmed to
  be the only writer, so the thread-local discipline itself is `(a)`; the
  must-audit weight moves entirely onto the three ambient diagnostic readers,
  which are `(c)`.
- Per-site footprint caveat made explicit: `hasDeferredTypeInfo()`,
  `m_tloInProgress.get()` (`TypeConstant.java:5948`), and the
  `f_tloReEntry.get()` probe (`TypeParameterConstant.java:208`) each install a
  permanent null entry in the backing per-thread map. This retains keys, not
  stale semantic values.
- Line-number refresh: `ConstantPool` bridge now `:3910/:3929/:3948/:4318`;
  deferred list now `:3362/:3374/:3381/:4292`; `Container.java` bridge now
  `:190-191`; `xRTServer.java` now `:692-693`; `MethodStructure` decode bridge
  now `:701`; `MultiMethodStructure` flag now `:428`; `TypeConstant`
  `s_context` now `:8321`, `m_tloInProgress` now `:8282`, recursion cleanup now
  `:6061-6065`, adoption clear now `:7951-7959`; `OpCondJump` ambient read now
  `:522`.
- Everything else re-verified with unchanged verdicts: all 21 `withPool(...)`
  sites use try-with-resource, `drainWork()` restores in `finally`,
  `s_tloIgnoreNative` is `finally`-scoped, and both `ScopedValue` bindings are
  lexical.

### Closing Verdict

Row 134 can be closed as "all sites classified". The sweep found 10 ambient
owner-context mechanisms (rows 1-10, including the `TransientThreadLocal`
infrastructure) and 6 noise/guard hits. No `ThreadLocal` or
`TransientThreadLocal` site sets a value without a guaranteed clear on a
pooled thread: every writer runs under try-with-resource, `try`/`finally`, or
a fail-loud entry guard, and both `ScopedValue` bindings are lexical.

Three groups remain category `(c)` — the ambient read, not the cleanup, is the
defect — and each is tracked by an existing PR slice in this document:

1. JIT static/ambient `Ctx` capture (bridge singletons, `TerminalConsole`,
   `nObject` helpers, generated `<clinit>`, `OpCondJump`):
   `jit-bridge-static-owner-fix`, `jit-generated-static-owner-proof`, and
   `jit-build-context-owner`.
2. `ServiceContext` diagnostic ambient readers (`Utils.log`, `Argument`,
   `OpVar`): `service-context-ambient-debug-cleanup`.
3. Remaining `withPool(...)` ambient-pool consumers, with compiler/build scopes
   still unasserted: `constant-pool-bridge-runtime-diagnostics` and
   `constant-pool-build-bridge-audit`.

The static `Ctx` captures in group 1 are the standing genuinely dangerous
shape: unlike every thread-local in this table they have no clear at all —
the first bound `Ctx` wins for the classloader lifetime. They are already
MUST FIX above; closing row 134 does not soften them.
