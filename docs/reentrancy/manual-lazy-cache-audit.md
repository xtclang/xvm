# Manual Lazy Cache Audit

This audit covers the remaining manual lazy-null sites in `runtime` and `asm`
after the native-template owner-cache work. The repository-wide category is
tracked in [must-audit-backlog.md](must-audit-backlog.md); this file is the
focused runtime/ASM drilldown. These are the patterns shaped like:

```java
if (m_field == null) {
    m_field = value;
}
```

The important distinction is whether the field is owner-bearing shared state.
Some hits are real race candidates. Some are synchronized association helpers,
frame-local lifecycle state, or append-only builder state where `Lazy` would be
the wrong abstraction.

## Current Scan

Command:

```bash
rg -U -n --pcre2 \
  "if\s*\(\s*((?:this\.)?(?:m_|s_|f_)[A-Za-z][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*\{[\s\S]{0,320}\1\s*=(?!=)" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm
```

Current branch result: 23 strong same-field lazy/init sites in runtime/asm.
The same strict scan finds 43 sites across all `javatools/src/main/java`.

## Classification

| Classification | Sites | Why | Required next action |
| --- | --- | --- | --- |
| Fixed in this branch | `asm/op/JumpCond.m_cond`, `asm/op/JumpNCond.m_cond`, runtime write-back to `asm/OpTest.m_typeCommon` and `asm/OpCondJump.m_typeCommon` | These were runtime-executed `Op` objects caching constants from a `Frame`. If the same decoded op object can run under multiple container/pool owners, the cached constant is owner-bearing and the field is a wrong-owner race. | The condition caches were removed. Common-type execution now resolves `m_nType` from the current `Frame` without writing that frame constant into the shared `Op`; `m_typeCommon` remains only an assembly-time argument field. |
| Fixed in this branch | `runtime/template/text/xRegEx.RegExHandle.m_pattern` | `Pattern` is immutable, but the old plain lazy field had no publication edge and could duplicate compilation under concurrent handle use. It did not carry container ownership, so this was a low-risk hardening fix rather than an observed wrong-owner bug. | Replaced by a final `Lazy<Pattern>`. `RegExHandleTest` verifies repeated access returns the same compiled pattern and the old nullable field is gone. |
| Fixed in this branch | `asm/constants/FSNodeConstant.m_constPath` | The derived path literal is owned by a `ConstantPool`. If the source node computed it before `adoptedBy(...)`, the shallow clone copied a source-pool path constant into the adopted node. | The cache is now a volatile per-node cache and `adoptedBy(...)` clears it on the adopted copy, preserving repeated-call caching while forcing recomputation in the destination pool. `ConstantAdoptionTest` covers the old failure shape. |
| Fixed in this branch, bridge XTC lazy | `javatools_bridge/src/main/x/_native/fs/OSFileNode.x:created` | The node is owned by the native `OSStorage` service, but the `created` getter can execute in an application container. The old `@Lazy` property cached an application-owned `Time` handle inside the native file-system graph. | Removed `@Lazy` and made `created` a computed getter, matching `modified` and `accessed`. Same-JVM `TestFiles` stress no longer reports the wrong-owner `Time` handle. |
| Must audit, not yet proven runtime owner bug | `asm/OpJump.m_opDest`, `asm/OpCondJump.m_opDest`, `asm/op/LoopEnd.m_opDest`, `asm/op/OpSwitch.m_aOpCase`, `asm/op/JumpInt.m_aOpCase`, `asm/op/GuardStart.m_aOpCatch` | These mutate decoded op address/link state. They are safe only if address resolution happens before publication or under exclusive ownership. Lazy runtime resolution would be racy. | Verify when `resolveAddresses(...)` runs. Preferred fix is eager linking before code publication, or one synchronized/atomic resolved-address state per method owner. |
| Should fix / audit during asm cleanup | `asm/Parameter.m_regDeref` | This is an immutable derived value on an ASM object. It is not a known runtime container leak, but the plain lazy field still assumes benign races. | Use final `Lazy` where construction dependencies are available, or synchronize if the value depends on mutable method/register lifecycle. |
| Safe publication already present | `asm/constants/ChildInfo.m_infoType`, `MethodInfo.m_infoType`, `PropertyInfo.m_infoType`, `MethodBody.m_infoMethod`, `PropertyBody.m_infoProperty` | The association methods are `synchronized` and return a copy when the requested owner differs. This is not an unsynchronized lazy cache. | No PR blocker. Keep as-is unless a later refactor can make ownership explicit at construction. |
| Thread/service/frame confined lifecycle state | `runtime/Frame.m_continuation`, `Frame.m_debug`, `runtime/DebugConsole.DebugStash.m_mapExpand`, `DebugStash.m_listWatches` | These are mutable frame/debugger lifecycle slots. They are not immutable caches, and `Lazy` would not express append/activation semantics. | No startup-race blocker. Keep private and service-thread confined; document confinement if these objects become cross-thread. |
| Compile/write-time builder state | `asm/Scope.m_scopeChild`, `asm/Op.Prefix.m_op`, `asm/Op.ConstantRegistry.m_mapConstants`, `asm/Op.ConstantRegistry.m_aconst`, `asm/MethodStructure.Code.m_listOps`, `asm/op/Label.m_action` | These are builder/serialization/linker structures that are expected to mutate while code is being assembled. They are not owner-local runtime caches. | Not a runtime PR blocker. If same-JVM incremental compilation starts sharing these objects across worker threads, confine them to the compilation request or add builder locks. |
| Must audit for owner key semantics, implementation appears synchronized | `runtime/ClassComposition.m_mapFields` | The field is `volatile` and initialized through `synchronized ensureFieldLayoutImpl(Container)`, but the method accepts a `Container`, so the cache key/owner contract needs to be explicit. | Prove a `ClassComposition` is owner-specific or that the field layout is container-independent. If not, split by owner/container. |
| Must audit for same-JVM tooling reuse | `tool/ModuleInfo` resource/source nodes | These are tool-side source/resource caches, not runtime template state. Same-JVM Gradle direct execution and LSP reuse can still expose stale source/resource ownership if `ModuleInfo` objects are reused across requests. | Prove `ModuleInfo` is request-confined or add request-scoped invalidation/rebuild rules. |

## Why The Runtime Op Caches Were Serious

The native-template fixes in this branch removed static owner caches, but an
`Op` field can create a similar problem if the `Op` object is shared. For
example:

```java
protected int processUnaryOp(Frame frame, int iPC) {
    if (m_cond == null) {
        m_cond = frame.getConstant(m_nArg, ConditionalConstant.class);
    }
    return m_cond.evaluate(frame.f_context.getLinkerContext()) ? ... : ...;
}
```

If the first execution happens in container A and caches A's constant, container
B can later evaluate against A's constant unless the method/op graph is
owner-confined. The safest implementation is:

```java
ConditionalConstant cond = frame.getConstant(m_nArg, ConditionalConstant.class);
return cond.evaluate(frame.f_context.getLinkerContext()) ? ... : ...;
```

That preserves semantics and removes the cache. If `frame.getConstant(...)` is
too expensive on a hot path, the cache needs an explicit owner key:

```java
private final ConcurrentMap<ConstantPool, ConditionalConstant> condByPool =
        new ConcurrentHashMap<>();

ConditionalConstant cond = condByPool.computeIfAbsent(
        frame.poolContext(), pool -> frame.getConstant(m_nArg, ConditionalConstant.class));
```

The key point is that the owner is visible in the type. A plain field on a
shared `Op` is not enough.

This branch applies the no-field-cache form to `JumpCond` and `JumpNCond`.
`OpTest` and `OpCondJump` no longer write `frame.getConstant(m_nType, ...)`
back to `m_typeCommon` during execution. The remaining `m_typeCommon` field is
kept for assembly-time source ops, where it is encoded to `m_nType` before
runtime execution.

This is not a semantic cache removal. The old fields did not cache the result
of `frame.resolveType(...)`, did not cache condition evaluation, and did not
skip any owner-local type machinery. They only avoided a repeated
`Frame.getConstant(...)` lookup, which is an indexed local-constant array read
plus a `Class.cast(...)`. That shortcut is not safe on a shared decoded op
object because the cached constant carries the first frame's owner. If a future
profile proves that this lookup is material, the replacement should be an
owner-keyed cache, not a process/shared-op field.

## Stress Verification Backlog

This is verification depth, not a known remaining code defect in the current
branch.

Current branch already has `manualTests:runDirectSequenceStress`, which runs
selected modules repeatedly in direct mode inside one Gradle JVM with runtime
ownership validation enabled.

Already used as smoke verification on this branch:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=1 \
  -PsameJvmModules=TestArray \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

Stronger pre-merge confidence would come from running a larger but still
bounded matrix:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestArray,TestReflection \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

And, when time permits, the default known-working module set:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

Remaining harness backlog is tracked in
[plans/same-jvm-launcher-stress.md](plans/same-jvm-launcher-stress.md):

- add structured failure artifacts,
- add benchmark reporting,
- add a same-JVM parallel mode after serial mode is reliable,
- add direct compile/test integration stress through `DirectRuntimeBuildService`,
- decide which short same-JVM smoke is stable enough for CI.

## First-PR Decision

The manual lazy-null audit does not currently identify a new native-template
startup blocker comparable to the old `INSTANCE` fields. The owner-bearing
runtime `Op` cache slice identified here has been handled in this branch; the
remaining `Op` address/link fields are tracked separately because they link ops
inside one decoded method graph rather than caching frame constants.

Recommended handling:

1. Keep this PR focused on native-template owner caches, enum publication,
   same-JVM ownership validation, and the owner-bearing runtime `Op` cache fix.
2. Verify eager link ordering for `Op` address/link fields separately; if any
   runtime path lazily links after publication, move that state under a
   synchronized/atomic method-owner policy.
3. Treat the remaining immutable ASM derived values as should-fix cleanup unless
   stress testing produces a concrete cross-owner failure.
