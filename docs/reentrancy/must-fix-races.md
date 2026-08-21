# Must-Fix Runtime Race Inventory

This document lists the state patterns that are actually unsafe under parallel
startup or parallel container execution. These are not just style issues. They
can publish the wrong container-owned object, expose a construction struct, or
let another fiber observe a lifecycle state that never existed as a coherent
state.

Startup-race counts use `master` as the baseline and call out the current
branch remainder where this branch fixes part of a category. Broader raw-access
and lazy-publication counts are scan signals generated on branch
`lagergren/lazy-instance` on 2026-08-21.

## Summary

| Priority | Broken pattern | Signal | Failure mode | Required replacement |
| --- | --- | --- | --- | --- |
| Must fix | Mutable native template `INSTANCE` fields | `master`: 143 mutable template `INSTANCE` fields and 139 constructor assignments. This branch fixes all 143 fields and all 139 constructor assignments. | Last writer wins across containers; constructor `this` escape | `NativeTemplates` central key table, existing container template cache, plus container/frame lookup |
| Must fix | Static runtime-owned metadata | `master`: 151 field-shaped runtime/template static metadata fields after excluding `INSTANCE`. This branch fixes all 151 and leaves 0 in the scanned runtime-template/Utils category. | Type/composition/method/handle values from one owner reused in another owner | Owner-scoped final `Lazy`, grouped info records, or owner-owned `ConcurrentMap` |
| Must fix | Raw enum handles returned through public/native paths | Branch remainder: 14 raw accessor references, all protected/internal in `xEnum` or owner-local native enum factories in `xBoolean`, `xNullable`, and `xOrdered` | Natural enum construction struct escapes as if it were the finalized enum singleton | `ensureEnumByName`, `ensureEnumByOrdinal`, or `Utils.ensureInitializedEnum` on public paths |
| Must fix | Native-container startup work from constructor | Current branch fixes the three `NativeContainer` `this-escape` diagnostics by moving native-template loading to `NativeContainer.create(...)` | Canonical native templates and resource handles were installed while the owner was still under construction | Private constructor plus post-construction factory initialization before publication |
| Must audit, must fix when owner-shared | Manual lazy publication in shared runtime/asm objects | 23 strong same-field lazy-init matches in runtime/asm; 43 across all Java sources | Plain field read/write with no happens-before edge; duplicate, stale, partial, or wrong-owner state | Final `Lazy`, `ConcurrentMap.computeIfAbsent`, or explicit atomic/locked state |
| Must fix | Split lifecycle state across several fields | `SingletonConstant` was the known concrete case and is fixed in this branch | Fibers see mixed handle/owner/waiter state; false recursion or missed wait | One immutable state snapshot in `AtomicReference<State>` or one lock |
| Must fix | Runtime/helper state shallow-copied during constant adoption | Fixed in this branch for `SingletonConstant`, `FSNodeConstant`, `FileStoreConstant`, `TypeConstant`, `ParameterizedTypeConstant`, `SignatureConstant`, `TypeParameterConstant`, and `HandleConstant` | A constant registered into pool B carries pool A's runtime handle/state cell, helper lock, JIT cache, or reentrancy marker | Adoption must copy only logical constant value state; transient runtime/helper state must be fresh or cleared |
| Must audit, must fix when runtime execution depends on it | Ambient current `ConstantPool` lookup | Runtime sites include `MainContainer`, `Container`, `ServiceContext`, watcher/request callbacks, `xContainerControl`, and type helpers | A hidden thread-local owner can be stale, absent, or wrong on reused Java threads and async callbacks | Add explicit owner parameters where practical; use scoped owner lookup only as a transitional boundary bridge with assertions |

## Mutable Template INSTANCE

Status: exact defect category.

Every mutable native-template `INSTANCE` is a process-global pointer to a
container-owned object. Most legacy templates assign it from the constructor,
which also publishes `this` before construction and `initNative()` have
completed.

Full list and audit commands:
[state-inventory.md#mutable-template-instance-inventory](state-inventory.md#mutable-template-instance-inventory).

Why this is broken:

- Container A can initialize a template, then container B can overwrite the
  same static field.
- Code running for A can later read B's template, B's `f_container`, B's pool,
  or metadata computed from B.
- A constructor assignment can expose an object before subclass fields and
  native metadata are initialized.

Required replacement:

- Add a private immutable key to `NativeTemplates` and expose a named accessor:

  ```java
  private static final NativeTemplateRef<X> X_KEY =
          NativeTemplateRef.of("template.Name", X.class);

  public X x() {
      return get(X_KEY);
  }
  ```

- Resolve through the active owner:

  ```java
  X template = NativeTemplates.get(frame).x();
  X template = NativeTemplates.get(container).x();
  ```

- Delete `INSTANCE = this`.
- Move derived metadata to final `Lazy` fields on the template or to a
  container-owned cache.

## Static Runtime-Owned Metadata

Status: exact defect category for fields whose values are derived from
`Container`, `ConstantPool`, `ClassStructure`, runtime handles, enum templates,
or native templates.

Broad current-branch audit command:

```bash
rg -n --pcre2 "^\s*(?:public|protected|private)?\s*static\s+(?!final\b)(?:Map<[^;=()]+>|TypeConstant|TypeComposition|ClassTemplate|ClassComposition|MethodStructure|MethodConstant|SignatureConstant|ArrayConstant|ArrayHandle|ObjectHandle|StringHandle|TupleHandle|EnumHandle|BooleanHandle|xEnum|x[A-Z][A-Za-z0-9_]*)\s+(?!INSTANCE\b)[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/Utils.java | sort -u
```

Count with this broader command:

```text
master: 151
current branch: 0
fixed in this branch: 151
```

The broader non-final static scan also found `xRTBuffer.PROP_RAW_BYTES`, a
template-owned `PropertyConstant` cache that was not matched by the command
above because it does not include `PropertyConstant`. This branch fixes that
site as final owner-local lazy state as well.

Representative current branch hits:

```text
none
```

Why this is broken:

- These values are not JVM constants. They carry pool, container, template, or
  handle identity.
- Two containers can race to initialize the same static field. The winner is
  arbitrary from the other container's point of view.
- Related fields can be populated from different owners, creating a metadata
  graph that is not valid in any runtime world.

Required replacement:

- Unkeyed metadata: final owner-local lazy state on the owning template. Use
  `Lazy.Owner<O,T>` for constructor-created owner-derived fields so the lazy
  cell does not capture `this` while the owner is still under construction.
- Related metadata: final `Lazy<InfoRecord>` that computes all values from the
  same owner.
- Keyed metadata: owner-owned `ConcurrentMap<K, V>` or
  `ConcurrentMap<K, Lazy<V>>`.
- Runtime handles and enum values: prove they are true JVM-wide handles or move
  them behind container/frame initialized accessors.

## Native-Container Startup Constructor Escape

Status: fixed in this branch.

`NativeContainer` used to load native templates, install base templates,
register native helpers, initialize resources, and ensure the service context
from its public constructor. That path was owner-sensitive startup work: it
created canonical native templates owned by the native container and populated
the container's runtime metadata before the Java constructor had returned.

Why this was dangerous:

- Native template loading intentionally publishes template objects into the
  container's template maps.
- The base templates are canonical owner-local templates; if they escape during
  construction, later startup code can observe an owner whose constructor has
  not completed.
- This was close to the original `INSTANCE = this` failure family: the
  publication target was container-local rather than JVM-global, but the owner
  lifecycle was still blurred.

Replacement:

- `NativeContainer` now has a private constructor and
  `NativeContainer.create(runtime, repository)`.
- The constructor only initializes the owner fields required by `Container` and
  stores the repository.
- The factory calls `initializeNativeTemplates()` after construction returns
  and before `InterpreterConnector` receives the container.

Behavior and cache preservation:

- Existing callers still get a fully initialized native container.
- Native module loading, base-template installation, reflective native-template
  loading, supplemental registration, `initNative()`, resource initialization,
  and service-context creation run in the same relative order.
- No cache is removed, delayed past connector construction, or changed from
  owner-local to process-global.
- `InterpreterConnectorTest.parallelConnectorsLoadIndependentNativeContainers()`
  is the dedicated regression test. It starts several interpreter connectors
  concurrently, loads `ecstasy.xtclang.org`, checks that their native containers
  are distinct, and forces ownership validation over the warmed containers.

## Raw Natural-Enum Handles

Status: public/native publication paths are closed in this branch. This remains
an exact defect category for any future path that lets a raw `EnumHandle` cross
a public/native boundary without `Utils.ensureInitializedEnum(...)` or an
`xEnum.ensure*` helper.

Audit command:

```bash
rg -n "getEnumByName|getEnumByOrdinal" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/MainContainer.java \
  javatools/src/main/java/org/xvm/runtime/Utils.java | sort
```

Current signal:

```text
14 raw enum accessor references: protected/internal `xEnum` lookups and
owner-local native enum factories in xBoolean, xNullable, and xOrdered
```

Branch-covered groups:

- `xEnum.getEnumByName(...)` and `xEnum.getEnumByOrdinal(...)` are no longer
  public. They are protected raw lookup primitives used by `xEnum` itself and
  the owner-local native enum subclasses. `xEnum.getValues()` was removed so
  callers cannot read the raw value list directly.
- `xRTComponentTemplate.ensureFormatHandle(...)` returns an initialized or
  deferred `ObjectHandle`, not a raw `EnumHandle`. The helper Javadoc explains
  that natural enum lookup can produce a construction struct and must resolve
  through the enum's `SingletonConstant` before publication.
- `xRTType.ensureAccessHandle(...)`, `xRTType.ensureFormHandle(...)`,
  `xRTTypeTemplate.ensureAccessHandle(...)`, and
  `xRTTypeTemplate.ensureFormHandle(...)` follow the same rule. Public property
  and method paths assign the initialized/deferred result directly with
  `Frame.assignDeferredValue(...)` or `Frame.assignConditionalDeferredValue(...)`.
- `xRTClassTemplate` and `xRTPropertyClassTemplate` contribution action enum
  values use `ensureEnumByName(...)`.
- `xService.synchronicity`, `xRTServiceControl.statusIndicator`, and
  `xFuture.completion` publish initialized/deferred enum handles directly.
- `xEnumValue.value` and `xEnumeration.byName` no longer read raw enum values.
  `xEnumeration.byName` still builds the same ordinal-indexed `Map`, but it now
  asks `xEnum` to publish each value through the singleton/deferred path instead
  of duplicating struct-completion logic in the reflection template.
- `xRTDelegate` and `xArray` mutability public properties use
  `ensureEnumByOrdinal(...)`, and `xArray` constructor arguments use the same
  helper plus deferred argument handling.
- `xBoolean`, `xNullable`, and `xOrdered` no longer assign static enum handles
  during `initNative()`. They keep native enum values in the owner template and
  expose owner-required factories plus pure value predicates.

Residual raw access that remains by design:

- `xEnum` itself needs protected raw lookup to find the template-local handle
  before it can resolve natural enum structs through `SingletonConstant`.
- `xBoolean`, `xNullable`, and `xOrdered` are native enum implementations whose
  `makeEnumHandle(...)` methods create public owner-local native handles
  directly, not natural enum construction structs. Their public factories require
  a `Frame` or `Container`, so they preserve the old eager cache behavior without
  process-global handles.

Why this is broken:

- `xEnum.makeEnumHandle(...)` creates a struct first. The finalized enum
  singleton may not exist yet.
- `getEnumByName(...)` and `getEnumByOrdinal(...)` can return that construction
  struct during startup.
- Returning or assigning the raw handle through a public/native path exposes an
  object with the wrong composition. PR #534's
  `ParameterTemplate.Category.TypeParameter:struct` failure is this class of
  bug.

Required replacement:

- Public/native return paths should use `ensureEnumByName(frame, name)`,
  `ensureEnumByOrdinal(frame, ordinal)`, or
  `Utils.ensureInitializedEnum(frame, hEnum)`.
- Helpers that return raw `EnumHandle` must stay internal/protected and
  documented as not crossing a public boundary.
- Static enum-template caches must move to owner-scoped final `Lazy<xEnum>` or
  container-owned lookup.

## Stress-Discovered Runtime Issues

Status: separate runtime failures found while validating this branch.

During stress validation on 2026-08-20,
`manualTests:runParallelStress -PstressIterations=2 -PstressModules=TestServices`
failed in the runner console path:

```text
ecstasy:TypeMismatch: Expected "immutable Array<Char>", actual "Array<Char>"
    at collections.Array.add(Array.Element) (Array.x:418)
    at text.StringBuffer.commitBuf() (StringBuffer.x:630)
    at ConsoleBack.print(Object, Boolean) (runner.x:90)
```

This was not a Java static owner-cache race. It was a deterministic
`StringBuffer` chunk mutability invariant bug: large immutable string chunks
could make the committed chunk list reject a later mutable append buffer. The
fix and proof are documented in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md).

The broader lesson remains relevant to this inventory: the parallel runner is
valuable because it turns hidden representation and ownership assumptions into
observable crashes. Future waves should continue to record any such finding as a
separate issue with a focused reproducer and a post-fix stress command.

## Shallow-Cloned Constant Runtime State

Status: exact defect category, fixed for the known owner-bearing constant
runtime fields and the small runtime-relevant adoption hardening candidates in
this branch. The broader adoption/clone audit is tracked in
[constant-adoption-clone-audit.md](constant-adoption-clone-audit.md).

`Constant.adoptedBy(...)` is the owner-transfer boundary for registering a
constant from one `ConstantPool` into another. The base implementation uses
`Object.clone()`, which shallow-copies fields. That is dangerous for any
constant field that is not part of the serialized logical constant value but is
instead a runtime cache, handle, owner pointer, lifecycle cell, or mutable
diagnostic state.

Concrete failure:

- parallel `TestProps` created two child containers from the same module;
- one container initialized a module/package/property singleton;
- the other container adopted the same logical `SingletonConstant`;
- the adopted constant shared the source constant's final
  `AtomicReference<InitState>`;
- the second container received a singleton handle graph owned by the first
  container;
- `@Lazy` property state then appeared already assigned in the wrong container.

This is not fixed by making the field final. Java final-field semantics safely
publish the reference value; they do not deep-copy or freeze the mutable object
behind that reference. Cloning a final reference to mutable owner state shares
the owner state.

Required replacement:

- For constants with runtime lifecycle state, override adoption and construct a
  fresh constant for the target pool.
- For constants whose serialized value can safely use the legacy clone path,
  clear all transient runtime fields immediately after cloning.
- Do not copy `ObjectHandle`, `TypeComposition`, `ClassTemplate`,
  `ServiceContext`, `Fiber`, `CompletableFuture`, `AtomicReference<State>`, or
  owner-bearing caches across constant-pool adoption.
- Add a test for every owner-bearing runtime field added to a constant class:
  source has state, adopted copy starts empty, source state remains intact.

The fixed incident is documented in
[stress-discovered-runtime-issues.md#adopted-singletonconstant-runtime-state-leak](stress-discovered-runtime-issues.md#adopted-singletonconstant-runtime-state-leak).

## Manual Lazy Publication

Status: exact defect category when the owner object is shared by runtime
threads or containers; must-review elsewhere.

Audit command:

```bash
rg -U --pcre2 -c "if\s*\(\s*((?:this\.)?(?:m_|s_|f_)[A-Za-z][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*\{[\s\S]{0,320}\1\s*=(?!=)" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm | awk -F: '{s+=$2} END {print s}'
```

Current count:

```text
23 strong same-field lazy-initialization matches in runtime/asm
```

Runtime-template subset after this branch:

```text
No strict same-field lazy-null hits remain under javatools/src/main/java/org/xvm/runtime/template.
```

This branch removed the low-risk `xRegEx.RegExHandle.m_pattern` cache by
replacing it with a final `Lazy<Pattern>`. It also fixed the owner-sensitive
`FSNodeConstant.m_constPath` derived path cache: adopted copies now clear the
cached source-pool path and recompute it under the destination pool while
preserving repeated-call caching. A same-JVM `TestFiles` run found one
additional bridge-XTC cache with the same owner shape: native
`OSFileNode.created` was `@Lazy` even though the node belongs to the native
`OSStorage` service and the getter can run in an application container. That
cache is removed; `created` is now a computed getter like `modified` and
`accessed`.

Why this is broken in shared owners:

- A plain read/write pair has no happens-before edge.
- A racing reader can observe stale null, duplicate computation, or partially
  related state.
- Duplicate computation is not harmless when the value is tied to a
  `ConstantPool`, `Container`, `ClassStructure`, or runtime owner.
- A null check cannot represent in-progress, completed, aborted, and waiting
  states.

Required replacement:

- Immutable unkeyed cache: final `Lazy`.
- Keyed cache: owner-owned `ConcurrentMap.computeIfAbsent`.
- Recursion/lifecycle: `AtomicReference<State>` or lock-protected transitions.
- Compiler-only cache: document confinement now and convert before enabling
  parallel or incremental compilation over the same objects.

## Split Lifecycle State

Status: exact defect category.

The known concrete case is `SingletonConstant`, which this branch replaces with
one atomic `InitState`. Similar designs should be rejected in review when one
logical lifecycle is represented by several mutable fields:

- current owner,
- current handle,
- in-progress marker,
- waiter future,
- abort/error flag.

Why this is broken:

- Another fiber can observe a handle without the matching owner or a waiter
  without the matching initialization attempt.
- Same-fiber recursion and other-fiber waiting become timing-dependent.
- Error cleanup can complete or clear only part of the state.

Required replacement:

- Store one immutable state snapshot in `AtomicReference<State>`.
- Use CAS for transitions and complete waiters after the successful transition.
- Use a lock only if it covers the entire state transition.
- Do not use `Lazy`; this is not a synchronous compute-once value.
