# XVM Memory-Model Hygiene Plan

This is a follow-up project plan, not a first-runtime-owner-PR checklist. The
goal is to remove the remaining places where XVM/JIT runtime ownership depends
on hidden global state, construction-time owner publication, shallow owner
copies, or undocumented single-thread assumptions.

The first runtime-owner branch removes the large native-template/global-cache
failure family. This plan captures the next work that should happen in separate
waves so we do not lose the findings.

## Scope

The project covers:

- the remaining JIT `Xvm.java` constructor escape;
- ConstantPool and constant-adoption owner hazards;
- clone/copy APIs that preserve source owners or helper state;
- owner-bearing runtime/JIT caches that still depend on ambient context;
- build gates and diagnostics that keep these patterns from returning.

The project does not try to replace the compiler architecture. Compiler
parallelism and incremental compilation need their own work, but this file calls
out compiler-facing runtime/state hazards when they can poison same-JVM runtime
or JIT execution.

## Severity Labels

| Severity | Meaning |
| --- | --- |
| Must fix | A known wrong-owner value, constructor escape, mixed lifecycle state, unsafe publication, or map/set corruption hazard exists on a runtime or JIT path. |
| Must audit | The code is safe only if a threading/owner/construction assumption is true, but that assumption is not encoded by the API. Promote to must-fix as soon as a shared-owner path is found. |
| Should fix soon | The design is brittle and blocks reentrant execution, but it is not yet a proven runtime failure. |
| Should fix | Cleanup that improves immutability/final-field reasoning but is not a correctness blocker. |

## Project 1: Refactor `org.xvm.javajit.Xvm`

Status: must-fix for JIT reentrancy, separate from the main interpreter
runtime-owner PR.

### Problem

`Xvm` still passes a partially constructed owner to the native JIT type-system
startup path:

```java
this.nativeTypeSystem = NativeTypeSystem.create(this, repo);
```

That path stores the `Xvm` in `TypeSystem`, uses it for type-system naming, and
then `NativeTypeSystem.registerNativeClasses()` calls back into
`xvm.createUniqueSuffix(...)`. At that point the following final fields have not
yet been assigned:

- `nativeTypeSystem`
- `nativeContainer`
- `ecstasyLoader`
- `bridgeLoader`
- `ecstasyPool`

This is a construction-time owner publication. Making `Xvm` `final` would not
fix the memory-model problem; it would only remove subclass-dispatch risk.

`Container` currently also stores `typeSystem.xvm` as a final field, so a real
fix must cover `TypeSystem` and `Container`, not only the constructor call in
`Xvm.java`.

### Required Shape

Split the bootstrap services from the completed `Xvm` facade.

1. Introduce an internal `XvmState` or `XvmServices` object.

   It should be fully constructed before native type-system creation and own:

   - the system `ModuleRepository`;
   - container/type-system/module-loader registries;
   - package-name and method/property-name counters;
   - weak-reference cleanup counters;
   - lock striping;
   - methods such as `generateTypeSystemName(...)`,
     `createUniqueSuffix(...)`, and `register(...)`.

2. Change `NativeTypeSystem.create(...)` and `TypeSystem` construction to use
   that state object for bootstrap services.

   During native type-system construction, code must not call back into a
   not-yet-complete `Xvm`.

3. Keep the public `Xvm` object as the complete immutable facade.

   Its constructor should only assign final fields from already-created parts.
   The old public constructor should either become a factory-backed API or be
   replaced by `Xvm.create(repo)` in internal callers.

4. If long-lived objects need the completed `Xvm`, use an explicit owner
   accessor/binding.

   A small `XvmOwnerRef` with one-time `bind(Xvm)` and checked `get()` is
   acceptable only if bootstrap code uses `XvmState` and cannot call `get()`
   before binding. Prefer direct `XvmState` dependencies for startup work.

5. Update JIT callers.

   `JitConnector`, JIT bridge helpers, `TypeSystemLoader`, `Container`, and
   any `typeSystem.xvm` users should move to an accessor or state/facade split
   consistently. Do not make `nativeTypeSystem` or `nativeContainer` mutable to
   sidestep the constructor warning.

### Proof

Add focused tests before treating this as done:

- a lint/source-shape test proving no `javajit` `this-escape` warning remains
  for `Xvm`, `TypeSystem`, `NativeTypeSystem`, or `Container`;
- a parallel startup test that creates several JIT `Xvm` instances from the
  same repository and verifies that every type system/container points to its
  own completed owner;
- a same-JVM repeated JIT connector smoke test;
- owner assertions that fail if a JIT container, type system, module loader, or
  generated bridge context sees a different `Xvm` from the one that created it.

### Equivalence Requirements

The refactor must preserve:

- the native container id `-1`;
- the same weak-map and cleanup behavior;
- the same type-system and module-loader name generation;
- the same `ecstasyLoader`, `bridgeLoader`, and `ecstasyPool` resolution;
- no additional synchronization in generated-code hot paths.

Expected overhead is one small state object and possibly one owner-reference
object per `Xvm`, which is startup-only and not a runtime bottleneck.

## Project 2: ConstantPool Runtime Ownership

Status: mixed must-fix and must-audit. This is the largest remaining cluster.

The current branch fixes the proven constant-adoption leaks for
`SingletonConstant`, filesystem constants, several type/signature constants,
and live `HandleConstant` adoption. It does not prove that `ConstantPool` as a
whole is reentrant-safe.

Completed wave in this branch:

- `TypeConstant.s_setRecursions` no longer uses a process-global mutable
  `HashSet`. The replacement preserves the old diagnostic suppression behavior
  with `ConcurrentHashMap.newKeySet()` and is covered by
  `TypeConstantRecursionDiagnosticsTest`.
- `TypeConstant.isCovariantReturn(...)` and
  `TypeConstant.isContravariantParameter(...)` no longer call
  `ConstantPool.getCurrentPool()`. They require an explicit owner pool and are
  covered by `TypeConstantOwnerApiTest`.
- `ByteConstant` and `IntConstant` range folding no longer calls
  `ConstantPool.getCurrentPool()`. Folded ranges use the receiver constant's
  pool and are covered by `ConstantRangeOwnerTest`.
- `ConstantPool.checkFunctionCompatibility(...)` no longer calls
  `ConstantPool.getCurrentPool()` from an instance method. It uses receiver
  `typeTuple0()` and is covered by `ConstantPoolDiagnosticsTest`.
- `IdentityConstant.resolveNestedIdentity(pool, resolver)` no longer loses the
  explicit output pool. Resolver-backed nested identities carry that pool and
  are covered by `NestedIdentityOwnerTest`, which runs under a wrong ambient
  pool and verifies the requested pool owns the resolved signature.
- `MethodBody`, `MethodInfo`, and `PropertyInfo` no longer use ambient current
  pool lookup for metadata owner helpers. Method bodies derive from their
  method identity, attached method info derives from its `TypeInfo`, unowned
  assembly falls back to the head method identity, and property info uses its
  existing property-info owner helper. No-ambient tests cover these paths.
- `FileStructure.getErrorListener()` no longer redirects through the ambient
  current pool. Diagnostics are file-owned unless a listener is explicitly set,
  and `FileStructureTest.errorListenerIgnoresAmbientPool()` covers wrong and
  missing ambient scopes.

### Must Fix

| Item | Why it is unsafe | Proper fix |
| --- | --- | --- |
| Semantic `ConstantPool.getCurrentPool()` use | Hidden thread-local owner lookup can be stale, absent, or wrong on reused Java threads, nested scopes, or async callbacks. Assertions are disabled in normal production runs. | Thread explicit `ConstantPool`, `Container`, or `Frame` parameters through semantic APIs. Keep scoped lookup only as a boundary bridge with non-assert diagnostics. |
| Remove the current-pool compatibility API | Semantic main-code callers have been removed, and `ConstantPool.getCurrentPool()` no longer exists. The remaining risk is the scoped `withPool(...)` bridge. | Keep the no-getter reflection/source-shape tests. Replace `withPool(...)` bridges with explicit owner APIs where practical. |
| Generic/typed API cleanup | Raw types, broad `Object` returns, and scattered casts make owner/type boundaries invisible and move failures away from the call that selected an owner. | Use existing typed helpers, add typed owner-boundary accessors where missing, and keep unavoidable unchecked casts in small documented helpers. See `generics-api-audit.md`. |
| Runtime-published pools remain mutable by default | A container-visible pool can keep registering constants unless an opt-in diagnostic property is enabled. Parallel readers can observe growth, invalidation, or partial registration. The protected `ClassComposition.ensureAccess(...)` access-type subcase is fixed in this branch, but first-time composition construction after publication remains open. | Freeze runtime pools after warmup or split mutable compiler/linker pools from immutable runtime pools. Make post-publication registration fail on runtime paths. |
| Base `Constant.adoptedBy(...)` shallow clone contract | Every new owner-local helper field is copied by default unless a subclass opts out. Final locks, atomics, lazy cells, thread-local cells, handles, and JIT caches are especially dangerous. | Replace base shallow clone with explicit copy/adoption contracts by subclass. Keep and expand `ConstantAdoptionValidator` in stress/CI. |
| `ConstantPool.register(...)` publishes before recursive registration completes | A constant is inserted into pool list/map storage before child constants are recursively registered and before some subclasses rewrite owner-sensitive fields. | Build/adopt recursively before publishing, or enforce single-owner registration phase with assertions that hash/equality fields do not change after insertion. |

### Must Audit, Likely Must Fix On Shared Runtime Paths

| Item | Risk | Closure |
| --- | --- | --- |
| Unsynchronized `f_listConst` readers | Appends are synchronized, but reads/iteration are often not. That is a single-thread reentrant traversal model, not arbitrary parallel mutation. | Freeze before runtime publication or expose snapshots for public traversal. |
| Copy-on-write `EnumMap` caches cleared in place | Volatile map fields are treated as copy-on-write, but destructive paths call `clear()` on the live map. | Assign fresh maps under lock or assert destructive paths are pre-publication only. |
| Recursive assembly registration fields | Pool-wide booleans and plain reference counts are single-thread by convention. | Assert assembly is single-owner or move registration counts to an isolated deterministic worklist. |
| Deferred TypeInfo thread-local worklists | Threads can build different deferred worklists while publishing to shared per-type caches. | Use an explicit TypeInfo build context or guard TypeInfo building per pool/type. |
| `TypeConstant` mixed cache discipline | Some caches use atomics/concurrent maps, others are plain fields. Runtime handles and JIT names are owner sensitive. | Classify each cache as duplicate-compute-ok, immutable-after-compute, or owner-locked. Key runtime handles by owner. |
| Live runtime handles in constants | `HandleConstant`, `FSNodeConstant`, and `FileStoreConstant` can make runtime/container state reachable from pools. | Reject cross-owner live-handle movement and replace assert-only setters with checked owner-local initialization. |
| Lazy valid-pool identity set | The valid upstream pool set is an unsynchronized lazy identity set. | Publish immutable snapshots or build eagerly before parallel registration. |
| Identity/member constant helper caches | Some caches are reset on owner change, but JIT names and info caches still need explicit proof. | Add adoption/reset hooks or prove global stability per field. |

## Project 3: Clone And Copy APIs

Status: must-fix for known ASM/runtime owner-copy hazards.

The first branch proved that `Object.clone()` is not a harmless convenience:
it shallow-copied a final `AtomicReference` lifecycle cell from one
`SingletonConstant` owner to another.

Completed in this branch:

- `Parameter.cloneBody()` was removed. `Parameter` no longer implements
  `Cloneable`; it uses `copyFor(MethodStructure)` to copy logical metadata for
  an explicit method owner while dropping method-owned deref-register cache
  state.
- `MethodStructure.cloneBody()` now copies parameters and returns for the
  cloned method owner, not the source method owner. Focused tests prove the
  source parameter state is preserved and copied parameters resolve through the
  clone.
- Synthetic delegated methods no longer share `Parameter` elements with the
  source method. `createMethodCopyingParameters(...)` copies the signature
  metadata for the delegated method owner before publication.

The remaining highest-risk clone findings are:

| Item | Why it matters | Proper fix |
| --- | --- | --- |
| `ObjectHandle.cloneAs(...)` | Runtime handles and field arrays can be shallow-copied while changing visible type/composition. | Replace hot cases with explicit view/copy constructors or add strict owner/freeze/share assertions. |
| Default constant clone adoption | See ConstantPool project. | Explicit subclass copy/adoption contracts. |

Each replacement needs a two-owner test: initialize/copy the source, initialize
the target, and prove no owner-local helper state is shared.

## Project 4: Runtime And JIT Ambient Context

Status: must-audit, must-fix when runtime code depends on it.
Detailed site classification lives in
[../ambient-context-audit.md](../ambient-context-audit.md).

Remaining owner context mechanisms include:

- `ThreadLocal` and `TransientThreadLocal` owners;
- `ScopedValue` relation contexts;
- JIT `Ctx.Current`;
- service/native/request callbacks that recover owner state indirectly.

`ScopedValue` is safer than raw `ThreadLocal` because it is lexically scoped,
but it is still hidden context. New APIs should prefer explicit owner
parameters. Scoped/ambient lookup is acceptable only as a transitional bridge
around legacy recursion or generated-code boundaries, and it must have owner
assertions at the boundary.

Work items:

- audit every semantic `ThreadLocal`/`ScopedValue` owner lookup;
- replace runtime-facing ambient owner lookup with explicit `Frame`,
  `Container`, `ConstantPool`, `TypeSystem`, or `XvmState` parameters;
- add diagnostics that dump the active owner chain when ambient and explicit
  owners disagree;
- document any remaining generated-code `Ctx.Current` dependency in the JIT
  implications doc.

## Project 5: Runtime Op And Metadata Caches

Status: must-audit.

The current branch removes the known owner-bearing runtime `Op` caches that
stored frame-derived constants on shared decoded op objects. Remaining decoded
op address/link caches appear intended to be resolved during exclusive linking,
but that needs proof.

Work items:

- prove decoded jump/catch/switch links are eagerly resolved before method
  runtime publication, or make the link state method-owner synchronized/atomic;
- document owner/key/invalidation for `ClassComposition`, `TypeInfoReal`,
  `TypeConstant`, and related runtime metadata caches;
- convert shared owner-bearing manual lazy null caches to final owner-local
  `Lazy`, `ConcurrentMap.computeIfAbsent`, or explicit atomic/locked state;
- leave hot per-handle duplicate-compute caches alone only when they are proven
  owner-local and immutable.

## Project 6: Proof And Build Gates

Status: required before claiming the runtime is reentrant by default.

Work items:

1. Enable `javac -Xlint:this-escape` across the full composite build.

   Once the current warning inventory is clean, make that warning a hard error
   by default. Any suppression must be local and must explain owner, lifetime,
   and why construction-time publication is safe.

2. Keep ownership diagnostics in stress paths.

   The diagnostics should be able to dump:

   - containers;
   - native template tables;
   - constant pools;
   - runtime handles;
   - JIT type systems/module loaders;
   - view/mask handle relationships;
   - ambient owner scope vs explicit owner.

3. Expand same-JVM stress.

   Run known manual-test modules repeatedly in one JVM, then run selected
   modules in parallel with ownership diagnostics and late ConstantPool
   registration/adoption validation enabled.

4. Add Gradle plugin direct-mode sequence tests.

   The target use case is repeated compile/run in one JVM without stale
   process-global state. Measure same-JVM direct mode against forked execution
   so performance wins and state safety are both visible.

5. Keep source scans for banned patterns.

   At minimum:

   - mutable runtime-template `INSTANCE`;
   - `INSTANCE = this`;
   - owner-bearing non-final static runtime metadata;
   - ownerless runtime handle factories;
   - semantic `ConstantPool.getCurrentPool()`;
   - new `Object.clone()` adoption/copy sites;
   - public/protected mutable owner-bearing arrays or collections.

## Recommended Order

1. Finish and merge the current interpreter runtime-owner PR.
2. Merge the separate utility `this-escape` fix.
3. Refactor `Xvm.java` with `XvmState`/owner binding and JIT startup tests.
4. Start the deeper ConstantPool freeze/registration/adoption redesign.
5. Expand same-JVM direct-mode and Gradle plugin stress so regressions are
   observable before the next broad state cleanup.

## References

- [../must-fix-races.md](../must-fix-races.md)
- [../must-audit-backlog.md](../must-audit-backlog.md)
- [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md)
- [../constant-pool-state-audit.md](../constant-pool-state-audit.md)
- [../clone-usage-audit.md](../clone-usage-audit.md)
- [../jit-implications.md](../jit-implications.md)
- [../scoped-value.md](../scoped-value.md)
- [same-jvm-launcher-stress.md](same-jvm-launcher-stress.md)
