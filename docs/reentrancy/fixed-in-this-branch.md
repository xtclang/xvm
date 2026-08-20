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
| Immutable template/pool metadata | Mutable static `TypeConstant`, `TypeComposition`, `MethodStructure`, `xEnum`, etc. | Final template field, usually `Lazy<T>` or grouped `Lazy<Info>` | Same "compute once" behavior, but per owning template/container |
| Finite owner-derived keyed cache | Mutable static map | Final `Lazy<Map<K,V>>` with immutable `Map.copyOf` | Same single map build, no global cross-container map |
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

### Constructor-Published Native Template `INSTANCE`

These master sites assigned `INSTANCE = this` from constructors and now resolve
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
- `xEnum.INSTANCE`
- `xService.INSTANCE`
- `xModule.INSTANCE`
- `xPackage.INSTANCE`

This is must-fix. The old pattern was both a constructor `this` escape and a
process-global last-writer-wins cache. The replacement keys are private to
`NativeTemplates`; converted template classes do not own an `INSTANCE` field at
all. The actual template object is resolved and cached by the active
`Container`.

The new files are:

- `javatools/src/main/java/org/xvm/runtime/NativeTemplates.java`
- `javatools/src/main/java/org/xvm/runtime/NativeTemplateRef.java`

`NativeTemplates` intentionally stores a `Lazy` cell in a `ConcurrentHashMap`
and resolves the template from `Lazy.get()`. That preserves per-container
caching while avoiding template bootstrap recursion inside
`ConcurrentHashMap.computeIfAbsent`.

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
| `xRTDelegate` | static `DELEGATES` map | `final Lazy<Map<TypeConstant,xRTDelegate>> f_delegates`; immutable `Map.copyOf` | Must fix |
| `xRTNameService` | `BYTE_ARRAY_ARRAY_TYPE`, lazy `m_typeCanonical` | `f_typeByteArrayArray`, `f_typeCanonical` | Must fix |
| `xRTClassTemplate` | class/template array types, contribution/method/annotation array types, empty parameter array, action enum, helper methods | final `Lazy` fields on the template | Must fix |
| `xRTComponentTemplate` | `COMPONENT_ARRAY_TYPE`, `MULTI_METHOD_TEMPLATE` | `f_typeComponentArray`, `f_templateMultiMethod` | Must fix |
| `xBitArray`, `xByteArray`, `xNibbleArray` | `INSTANCE`; `xByteArray` numeric array compositions | `NativeTemplates` array-template getters and `xByteArray` final lazy compositions | Must fix |
| `xRTViewFromBit`, `xRTViewFromByte` | specialized view lookup through subtype `INSTANCE` fields | owner-local final lazy dispatch maps keyed by `TypeConstant` | Must fix |
| `xRTBitDelegate`, `xRTBooleanDelegate`, `xRTFloat64Delegate`, `xRTInt8Delegate`, `xRTInt16Delegate`, `xRTInt64Delegate`, `xRTUInt8Delegate`, `xRTNibbleDelegate`, `xRTSlicingDelegate` | constructor-published delegate `INSTANCE` fields | owner-local delegate dispatch and `NativeTemplates.slicingDelegate()` | Must fix |
| `xRTViewFromBitTo*`, `xRTViewFromByteTo*`, `xRTViewToBitFromNibble` | constructor-published specialized view `INSTANCE` fields | owner-local base view dispatch or existing `xRTViewToBit` dispatch | Must fix |
| `xListMap`, `Utils`, map-literal opcodes, enum-name map construction | `xListMap.INSTANCE`, static `xListMap.CONSTRUCTOR`, static `Utils.LIST_MAP_CONSTRUCT` | `NativeTemplates.listMap()`, owner-scoped `xListMap.f_constructor`, and constructor lookup from the caller's map composition | Must fix |
| `xTuple`, void-return handling, async service responses | `xTuple.INSTANCE`, `xTuple.INCEPTION_CLASS`, static `xTuple.H_VOID` | `NativeTemplates.tuple()`, final owner-local inception constant, and per-container lazy `Tuple()` handle via `xTuple.ensureEmptyTuple(container)` | Must fix |
| `xFuture`, wait-frame construction, async result assignment | `xFuture.INSTANCE`, static `TYPE`, static `COMPLETION`, ownerless `makeHandle(CompletableFuture)` | `NativeTemplates.future()`, final owner-local lazy future type and completion enum template, and `makeHandle(Container, CompletableFuture)` | Must fix |
| `xAtomic`, `xAtomicIntNumber`, `xAtomicInt128` | `xAtomicIntNumber.INSTANCE`, static `xAtomic.NUMBER_TEMPLATES`, and wrapper construction from numeric template `INSTANCE` fields | final owner-local `Lazy<Map<TypeConstant,xAtomic>>`, immutable `Map.copyOf`, and wrapper construction from this container's number templates | Must fix |
| Native filesystem templates and CP filesystem constants | `xOSDirectory.INSTANCE`, `xOSFile.INSTANCE`, `xRawOSFileChannel.INSTANCE`, and static constructor `MethodStructure` caches on `xOSDirectory`, `xOSFile`, `xCPDirectory`, `xCPFile`, `xCPFileStore` | `NativeTemplates` filesystem getters plus final owner-scoped lazy constructor caches on the owning template | Must fix |
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
| `xArray` | array compositions, constructor IDs, helper methods, specialized-template map, delegates, empty byte array, mutability enum | `f_templateMutability`, `f_arrayTemplates`, and `f_info: Lazy<ArrayInfo>` | Must fix |
| `xEnum` | range template/ctor, enum name and handle lists | `f_templateRange`, `f_ctorRange`, `f_enumInfo` | Must fix for startup; see enum lifecycle note below |
| `xService` | `INCEPTION_CLASS`, `SYNCHRONICITY`, `REMAINING_TIME` | `f_constInception`, `f_templateSynchronicity`, `f_propRemainingTime` | Must fix |
| `xModule` | private static `LISTMAP_TYPE` | compute from caller `ConstantPool` | Must fix |
| `xString` | static empty string-array handle and no-container array helpers | `f_emptyStringArray`; callers pass `Container` | Must fix |

These replacements preserve caching. They do not turn old bootstrap caches into
repeated lookups. The cache key changed from "entire JVM" to "owning
container/template".

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
- `xRTMethod` access enum result
- `xRTDelegate` and `xArray` mutability property results through
  `Utils.assignInitializedEnum`

This is must-fix. Raw `xEnum.getEnumByName()` and `getEnumByOrdinal()` still
exist because some internal paths need the template-local index. They are not a
safe public publication boundary for natural enum values. New code that can
surface an enum handle must use the initialized helpers or assign through
`Utils.assignInitializedEnum`.

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
| `xString.EMPTY_STRING_ARRAY` | moved from static mutable handle to per-template `Lazy<ArrayHandle>` | Keep; this is container-owned handle state and is a must-fix cache |
| `xString.StringHandle` hash/String memoization | left as the old per-handle transient cache | Keep out of this PR; replacing with `Lazy` would add two objects per string handle for a should-fix-only concern |
| `xString.INSTANCE`, `EMPTY_STRING`, `EMPTY_ARRAY`, `ZERO`, `ONE`, `METHOD_APPEND_TO` | still old-style static compatibility state | TODO; needs a separate container-widening pass |

## Unfixed Legacy `INSTANCE` Patterns

This branch deliberately fixes only the `INSTANCE` sites needed by the native
template startup and enum/singleton race. Many mutable `INSTANCE` fields remain
on `master` and still remain after this branch. The full list is maintained in
[state-inventory.md#mutable-template-instance-inventory](state-inventory.md#mutable-template-instance-inventory).

Examples still requiring follow-up include root templates such as `Identity`,
`xConst`, `xException`, and `xObject`; reflection templates such as `xRef` and
`xVar`; many primitive templates; and `xString`/`xChar`. Those are not safe by
design just because this PR does not touch them; they are the next migration
backlog.

## Proof Points Added By This Branch

`javatools/src/test/java/org/xvm/runtime/NativeTemplateOldPatternTest.java`
contains deterministic demonstrations of the old pattern:

- a static `INSTANCE` cache is last-writer-wins across two owners,
- constructor assignment can expose a partially initialized object.

`javatools/src/test/java/org/xvm/runtime/SingletonConstantTest.java` covers the
new singleton state machine:

- concurrent initialization chooses one owner,
- unrelated waiters share completion,
- same-fiber recursion installs an initializing placeholder without deadlock.

`manualTests:runParallelStress` is an opt-in stress runner that invokes the
existing parallel `Runner` with repeated module arguments, creating many
lightweight containers in one process:

```bash
./gradlew :manualTests:runParallelStress -PstressIterations=50
```

These tests do not prove the absence of every race in the runtime. They prove
that the old pattern is concretely broken and that the new replacement has the
intended ownership and lifecycle behavior for the most important fixed paths.

No existing manual module was found that directly exercises `@Atomic`
specialized numeric references; the atomic owner-scope wave is covered by Java
compile/test verification and should get an explicit X-level test in a
follow-up.
