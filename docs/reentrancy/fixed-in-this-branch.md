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

The broader `ConstantPool` state audit is documented in
[constant-pool-state-audit.md](constant-pool-state-audit.md). It distinguishes
per-pool caches, ambient owner lookup, runtime registration/adoption hazards,
and compiler-only pool mutation paths.

This branch also narrows ambient `ConstantPool` lookup at runtime boundaries:

- `InterpreterConnector.invoke0(...)` uses
  `m_containerMain.getConstantPool()` instead of
  `ConstantPool.getCurrentPool()`. Correct callers see the same owner pool; stale
  ambient scope is detected by `ConstantPool.assertCurrentPoolIfPresent(...)`.
- `NativeContainer.loadNativeTemplates()` no longer mutates the current pool with
  a raw setter and later clears it to `null`. It uses lexical
  `withPool(...)` scopes, preserving prior ambient state and restoring it on
  exceptional exits.
- `ConstantPool.setCurrentPool(...)` was removed after the last runtime startup
  caller was converted. Future code must use self-restoring scoped ownership.
- `MainContainer.invoke0(...)`, `Container.ensureServiceContext()`,
  `ServiceContext.drainWork()`, `xOSStorage.WatchDaemon`,
  `xContainerControl.invokeInvoke(...)`, and `xRTServer.RequestHandler` now
  assert that any transitional ambient scope matches the explicit container or
  service owner already present at the boundary.

The focused regression coverage is in
`javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java`. It
proves that correct scoped owners pass, explicit-owner code can run without an
ambient pool, and wrong ambient scopes fail under assertions. This is a guard and
ownership-visibility change, not a cache-policy change: all constants are still
interned in the same per-owner `ConstantPool` as before.

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
indexed local-constant array lookup; the expensive type resolution and condition
evaluation remain exactly where they were and continue to use the current
frame/container owner. `m_typeCommon` remains for assembly-time source ops and is
encoded to `m_nType` before runtime execution.

`javatools/src/test/java/org/xvm/asm/OpRuntimeCacheTest.java` verifies that the
condition fields are gone and guards against reintroducing the old common-type
write-back pattern.

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
