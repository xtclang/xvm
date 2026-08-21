# Native Template Startup Safety Inventory

This appendix maps the unsafe Java patterns discussed in
[native-template-startup-safety.md](native-template-startup-safety.md). It is an
inventory and migration backlog, not a claim that every hit is independently a
bug. Some scans are intentionally broad.

The distinction matters:

- Actual races and wrong-owner caches are must-fix correctness defects.
- Constructor publication and split lifecycle state are must-fix publication
  defects even when a crash is hard to reproduce.
- Unnecessarily mutable fields, mutable arrays, and misleading names are
  should-fix design debt. They make the real races harder to see and make
  reentrant code harder to review, but they are not always independent runtime
  bugs.

The important rule is that every published or cached runtime value must have an
owner and a publication story. If the owner is process-global, the value must be
immutable and safely published by class initialization. If the owner is a
container, pool, frame, service, or template, the field must live on that owner
or in a cache owned by that owner. If the value changes over time, it needs a
real synchronization model.

The must-fix startup-race counts below use `master` as the baseline, with the
current branch remainder called out where relevant. Broader smell counts are
source-scan signals generated on branch `lagergren/lazy-instance` on
2026-08-20; they are migration backlog data, not a claim that every hit is an
independent bug.

## Severity Tiers

| Priority | Category | Signal | Why it is bad | Proper replacement |
| --- | --- | --- | --- | --- |
| Must fix | Mutable template `INSTANCE` fields and `INSTANCE = this` constructors | `master`: 143 mutable template `INSTANCE` fields and 139 constructor assignments. This branch fixes all 143 fields and all 139 constructor assignments. | Process-global last-writer-wins template lookup; constructor `this` escape; wrong container/pool can be observed | `NativeTemplates` central key table, existing container template cache, plus owner-scoped lazy lookup |
| Must fix | Static runtime metadata caches | `master`: 151 field-shaped runtime/template static metadata hits after excluding `INSTANCE`; this branch fixes all 151 and leaves 0 in the scanned runtime-template/Utils category. | `TypeConstant`, `TypeComposition`, `MethodStructure`, handles, and `xEnum` values are pool/container/runtime state, not JVM-global constants | Final `Lazy` fields on the owning template, immutable grouped info records, or a container-owned cache |
| Must fix | Split mutable lifecycle state | Old `SingletonConstant` used separate handle/owner/waiter fields | Readers can observe impossible lifecycle snapshots across fibers | One immutable state snapshot in `AtomicReference`; use CAS for transitions |
| Must fix | Natural enum construction structs escaping public paths | PR #534 enum struct mismatch | A caller can observe a construction struct where an immutable enum value is required | Public enum helpers that return initialized singletons or deferred results |
| Must fix | Unsynchronized lazy null caches in shared runtime state | 98 field-shaped checks; 47 strong same-field lazy-init matches | Plain field read/write has no happens-before edge and can publish partial state | Final `Lazy` for immutable values; `ConcurrentMap.computeIfAbsent` for keyed caches; `AtomicReference` or a lock for lifecycle/resettable state |
| Must fix | Non-final static runtime globals | 9 non-final static fields across runtime/asm/compiler; 13 across all Java sources | Plain static mutation is shared process state with no owner, no reset story, and no visibility guarantee | Delete, make `static final` immutable, move to owner scope, or guard resettable state with a lock/atomic holder |
| Should fix soon | `volatile` as partial synchronization | 21 `volatile` hits in runtime/asm/compiler | `volatile` orders one variable; it does not make a group of fields or mutable map contents atomic | Keep only for independent scalar state; otherwise use immutable state snapshots, `ConcurrentMap`, or synchronized critical sections |
| Should fix soon | Static mutable collection fields | 11 `static final` collection/resource-like fields; 0 non-final static collection/resource-like fields | `final` protects the reference, not the collection contents; global mutable maps need an update policy | `Map.of`/`Set.of`/`List.of` or `Collections.unmodifiable*` for constants; `ConcurrentMap` with documented key ownership for real caches |
| Should fix soon | Public/protected mutable fields | 166 public/protected non-final `m_`, `s_`, or `f_` fields in runtime/asm/compiler | Any caller can mutate state without preserving invariants or synchronization | Private fields plus methods that enforce ownership, synchronization, and lifecycle invariants |
| Should fix soon | Public/protected static arrays and exposed arrays | 42 public/protected static arrays; 75 public/protected array fields in runtime/asm/compiler | Array elements are mutable shared variables even when the array reference is final | Private `static final` arrays with defensive copies, immutable lists, or package-private documented internal constants |
| Should fix soon | Thread-local hidden global context | 17 `ThreadLocal`/`TransientThreadLocal` hits in runtime/asm/compiler | Thread locals hide dependencies, can leak scope across pooled threads, and make reentrancy depend on cleanup discipline | Prefer explicit context parameters or owner-scoped stacks; if unavoidable, use scoped `try/finally remove()` wrappers |
| Should fix soon | Weak/identity mutable maps | 12 `WeakHashMap`/`IdentityHashMap` construction hits in runtime/asm/compiler | These maps are not concurrent and their semantics are easy to misuse as global caches | Confine to one owner/thread, synchronize, or use the project's concurrent weak-map helper where sharing is intended |
| Should fix | Constant-looking non-final public statics | 2 public non-final uppercase/static constant-shaped fields across runtime/asm/compiler; 5 across all Java sources | They look immutable in review but can be reassigned and are not safely published as constants | `public static final` immutable values, private owner-scoped state, or accessor methods |
| Should fix | Owner-local mutable metadata fields | 181 non-final runtime/asm metadata fields of type `TypeConstant`, `TypeComposition`, `MethodStructure`, handle, template, or enum | Some are valid lifecycle fields, but many are ad hoc first-use caches with no publication story | Final eager fields, final `Lazy`, `ConcurrentMap`, or explicit lifecycle state depending on semantics |
| Should fix | Rare non-final `f_` fields | 2 direct hits | No written naming standard was found, but source usage strongly suggests `f_` normally denotes fixed/final owner state; exceptions are review hazards | Make final if immutable; otherwise rename to `m_` and document the mutation |

`Lazy` is only one replacement. It is the right default for immutable values
that are expensive or inconvenient to compute during construction. It is not
the right tool for values that can reset, expire, suspend, wait for a fiber,
represent in-progress state, or depend on a key chosen at call time.

The should-fix categories are not cosmetic. They are the design debt that makes
the must-fix races possible and hard to see. Keeping compiler and runtime state
casually mutable assumes today's accidental threading model will remain true
forever. That is a bad assumption: the compiler should be able to support
incremental and parallel compilation safely, and the runtime already runs
multiple containers and fibers in parallel. Immutable/final state, private
ownership, and explicit synchronization give future code a safe default instead
of requiring every new caller to rediscover undocumented "only one thread calls
this" rules.

## Policy

Prefer final state when the value has one owner and one final result:

```java
private final Lazy<Info> f_info = Lazy.of(this::createInfo);
```

Avoid mutable lazy state:

```java
private Info m_info;

Info info() {
    if (m_info == null) {
        m_info = createInfo();
    }
    return m_info;
}
```

The second form is only valid when all callers are externally synchronized or
the object is provably single-thread confined. Runtime templates, constants,
containers, frames, services, and reflection helpers should not assume that.

Choose the replacement by semantics:

- Immutable, unkeyed, owner-derived metadata: final `Lazy<T>`.
- Immutable, keyed metadata: `ConcurrentMap<K, Lazy<V>>` or
  `ConcurrentMap.computeIfAbsent`, owned by the correct container/pool/template.
- Resettable or in-progress lifecycle state: immutable state record held by
  `AtomicReference`, or a lock protecting the whole state transition.
- Mutable collections that are real state: private final collection plus a
  lock, concurrent collection, or owner-thread confinement.
- Public constants: `static final` immutable objects; do not expose mutable
  arrays or collections.
- Thread-confined compiler/AST state: keep mutable only when confinement is
  documented and no shared runtime path can reach it.

## JMM Cross-Reference

The same few Java memory model rules explain most of this inventory:

| Pattern | Specification basis | Design consequence |
| --- | --- | --- |
| Plain read/write of shared static or instance fields | [JLS 17.4.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.1), [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) | A runtime field read by multiple startup threads must be final, volatile with narrow semantics, or protected by a real synchronization edge. |
| Constructor `this` escape | [JLS 12.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.5), [JLS 17.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5) | Do not publish receivers from constructors. Final-field safety assumes the object is not made visible before construction completes. |
| Mutable static arrays and collections | [JLS 17.4.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.1), [JLS 17.5.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5.1) | `final` protects the field reference, not array elements or collection contents. Exposed mutable contents are shared variables. |
| `volatile` fields | [JLS 17.4.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.4), [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) | A volatile write/read creates an edge for that variable, but it does not make compound invariants or mutable object contents atomic. |
| Immutable state snapshots in `AtomicReference` | [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) | CAS publishes a whole state object. Reviewers can reason about one lifecycle snapshot instead of several separately racing fields. |
| Final owner fields and final `Lazy` cells | [JLS 17.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5) | The owner shape is stable after construction; lazy first computation adds an explicit synchronization boundary. |

## Tooling Baseline

Local JDK check:

```bash
javac --help-lint | rg -n "this|escape|initialization|overrides"
```

Current output:

```text
28:    overrides            Warn about issues regarding method overrides.
40:    this-escape          Warn when a constructor invokes a method that could be overriden in an external subclass.
41:                         Such a method would execute before the subclass constructor completes its initialization.
```

This is a standard javac lint key. The build should enable
`-Xlint:this-escape` and promote it to a compilation failure for runtime code.
Suppressions must be local and justified.

Mainstream IDE inspections flag the same category. JetBrains documents both
"`this` reference escaped in object construction" and "Overridable method
called during object construction" under Java initialization inspections.

## Mutable Template INSTANCE Inventory

Master baseline:

```bash
git grep -n -E 'public static [^;]* INSTANCE;' \
  master -- javatools/src/main/java/org/xvm/runtime/template | wc -l

git grep -n -E 'INSTANCE = this;' \
  master -- javatools/src/main/java/org/xvm/runtime/template | wc -l
```

```text
mutable INSTANCE fields: 143
constructor assignments: 139
```

Current branch remainder audit command:

```bash
rg -l "public static (?!final)[A-Za-z0-9_<>, ?]+ INSTANCE;" \
  --pcre2 javatools/src/main/java/org/xvm/runtime/template | sort
```

Current branch count:

```text
0
```

Current branch constructor publication audit:

```bash
rg -n "INSTANCE\s*=\s*this" \
  javatools/src/main/java/org/xvm/runtime/template | sort -u
```

Current branch count:

```text
0
```

Current branch mutable template `INSTANCE` file list:

```text
none
```

## Lazy Null Cache Inventory

Audit commands:

```bash
rg -n "==\s*null|null\s*==" javatools/src/main/java | wc -l
rg -n --pcre2 "if\s*\(\s*(?:this\.)?(?:m_|s_|f_)[A-Za-z][A-Za-z0-9_]*\s*==\s*null\s*\)" javatools/src/main/java | wc -l
rg -U --pcre2 -c "if\s*\(\s*((?:this\.)?(?:m_|s_|f_)[A-Za-z][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*\{[\s\S]{0,240}\1\s*=" javatools/src/main/java | awk -F: '{s+=$2} END {print s}'
```

Current counts:

```text
3124 broad null-equality checks
98 field-shaped lazy-null checks
47 strong same-field lazy-initialization matches
```

The broad count includes many ordinary local null checks. The field-shaped
count is the more useful smell inventory. Every line in this category should be
triaged:

- If it is immutable after first computation, convert it to a final `Lazy`.
- If it is container-derived, move it to the owning template or container.
- If it can reset, suspend, or represent in-progress state, use an explicit
  atomic/locked state machine.
- If it is truly thread-confined, document the confinement.

Field-shaped lazy-null checks:

```text
javatools/src/main/java/org/xvm/asm/ConstantPool.java:3652:        if (m_typeNakedRef == null) {
javatools/src/main/java/org/xvm/asm/MethodStructure.java:1299:            if (m_idFinally == null) {
javatools/src/main/java/org/xvm/asm/MethodStructure.java:1880:        if (m_idSuper == null) {
javatools/src/main/java/org/xvm/asm/MethodStructure.java:2503:            if (m_listOps == null) {
javatools/src/main/java/org/xvm/asm/MethodStructure.java:2701:                if (m_listOps == null) {
javatools/src/main/java/org/xvm/asm/MethodStructure.java:2794:            if (m_sSrc == null) {
javatools/src/main/java/org/xvm/asm/MethodStructure.java:2990:            if (m_aconstSrc == null) {
javatools/src/main/java/org/xvm/asm/ModuleStructure.java:777:            if (m_constVersion == null) {
javatools/src/main/java/org/xvm/asm/Op.java:482:            if (m_op == null) {
javatools/src/main/java/org/xvm/asm/Op.java:508:            if (m_op == null) {
javatools/src/main/java/org/xvm/asm/Op.java:708:            if (m_mapConstants == null) {
javatools/src/main/java/org/xvm/asm/Op.java:943:            if (m_aconst == null) {
javatools/src/main/java/org/xvm/asm/OpCondJump.java:137:        if (m_opDest == null) {
javatools/src/main/java/org/xvm/asm/OpJump.java:53:        if (m_opDest == null) {
javatools/src/main/java/org/xvm/asm/OpVar.java:210:        if (m_reg == null) {
javatools/src/main/java/org/xvm/asm/Parameter.java:352:        if (m_regDeref == null) {
javatools/src/main/java/org/xvm/asm/Scope.java:119:        if (m_scopeChild == null) {
javatools/src/main/java/org/xvm/asm/Scope.java:138:        if (m_scopeChild == null) {
javatools/src/main/java/org/xvm/asm/Scope.java:36:        if (m_scopeChild == null) {
javatools/src/main/java/org/xvm/asm/Scope.java:58:        if (m_scopeChild == null) {
javatools/src/main/java/org/xvm/asm/constants/ChildInfo.java:65:        if (m_infoType == null) {
javatools/src/main/java/org/xvm/asm/constants/DynamicFormalConstant.java:230:        if (m_reg == null) {
javatools/src/main/java/org/xvm/asm/constants/DynamicFormalConstant.java:264:        if (this.m_reg == null) {
javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:165:        if (m_constPath == null) {
javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:132:        if (m_infoMethod == null) {
javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:95:        if (m_infoType == null) {
javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java:114:        if (m_aiTypeParams == null) {
javatools/src/main/java/org/xvm/asm/constants/PropertyBody.java:168:        if (m_infoProperty == null) {
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:117:        if (m_infoType == null) {
javatools/src/main/java/org/xvm/asm/constants/RegisterConstant.java:133:        if (m_reg == null) {
javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java:363:        if (m_FConditional == null) {
javatools/src/main/java/org/xvm/asm/op/GuardStart.java:112:        if (m_aOpCatch == null) {
javatools/src/main/java/org/xvm/asm/op/JumpCond.java:50:        if (m_cond == null) {
javatools/src/main/java/org/xvm/asm/op/JumpInt.java:87:        if (m_aOpCase == null) {
javatools/src/main/java/org/xvm/asm/op/JumpNCond.java:50:        if (m_cond == null) {
javatools/src/main/java/org/xvm/asm/op/Label.java:143:        if (m_action == null) {
javatools/src/main/java/org/xvm/asm/op/LoopEnd.java:39:        if (m_opDest == null) {
javatools/src/main/java/org/xvm/asm/op/OpSwitch.java:108:        if (m_aOpCase == null) {
javatools/src/main/java/org/xvm/compiler/ast/AnnotatedTypeExpression.java:190:        if (m_typeUnresolved == null) {
javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java:271:        if (m_sName == null) {
javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java:609:                if (m_idGet == null) {
javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java:655:        if (m_idGet == null) {
javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java:726:                if (m_labelCondFalse == null) {
javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:416:        if (m_labelCurrent == null) {
javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:429:            if (m_labelDefault == null) {
javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:491:                if (m_labelDefault == null) {
javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:690:        if (m_pintMin == null) {
javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:705:        if (m_labelCurrent == null) {
javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:849:            if (m_mapWild == null) {
javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java:490:                                        if (m_aidConvKey == null) {
javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java:92:        if (m_listContinues == null) {
javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java:151:            if (m_listShorts == null) {
javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java:88:            if (m_listContinues == null) {
javatools/src/main/java/org/xvm/compiler/ast/IfStatement.java:82:            if (m_listShorts == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:1256:                        if (m_idFormal == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2095:                        if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2347:                        if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2383:                    if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2412:                if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2441:                if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2467:                if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java:2491:                if (m_method == null) {
javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:231:        if (m_lambda == null) {
javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:239:        if (m_lambda == null) {
javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:247:        if (m_lambda == null) {
javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:511:        if (m_collector == null) {
javatools/src/main/java/org/xvm/compiler/ast/MapExpression.java:396:        if (m_aKeyAST == null) {
javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java:1130:                if (m_mapTypeParams == null) {
javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java:1426:            if (m_mapTypeParams == null) {
javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java:2043:            if (m_arg == null) {
javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java:719:                if (m_mapTypeParams == null) {
javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java:246:            if (m_constant == null) {
javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java:360:        if (m_typeMode == null) {
javatools/src/main/java/org/xvm/compiler/ast/Statement.java:172:        if (m_ctx == null) {
javatools/src/main/java/org/xvm/compiler/ast/Statement.java:83:        if (m_listBreaks == null) {
javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java:208:            if (m_collector == null) {
javatools/src/main/java/org/xvm/compiler/ast/SwitchStatement.java:221:            if (m_labelContinue == null) {
javatools/src/main/java/org/xvm/compiler/ast/SwitchStatement.java:62:        if (m_labelContinue == null) {
javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java:94:            if (m_listContinues == null) {
javatools/src/main/java/org/xvm/runtime/CallChain.java:657:            if (f_constructor == null) {
javatools/src/main/java/org/xvm/runtime/ClassComposition.java:555:        if (m_mapFields == null) {
javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:179:            if (f_structSuper == null) {
javatools/src/main/java/org/xvm/runtime/DebugConsole.java:2245:            if (m_mapExpand == null) {
javatools/src/main/java/org/xvm/runtime/DebugConsole.java:2262:            if (m_listWatches == null) {
javatools/src/main/java/org/xvm/runtime/Frame.java:1830:        if (m_continuation == null) {
javatools/src/main/java/org/xvm/runtime/Frame.java:1991:        if (m_debug == null) {
javatools/src/main/java/org/xvm/runtime/Frame.java:741:        if (f_hThis == null) {
javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:786:            if (f_hException == null) {
javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:798:            if (f_hException == null) {
javatools/src/main/java/org/xvm/runtime/ServiceContext.java:1940:            if (f_fiberCaller == null) {
javatools/src/main/java/org/xvm/runtime/ServiceContext.java:1989:            if (f_hException == null) {
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:942:            if (m_next == null) {
javatools/src/main/java/org/xvm/runtime/template/text/xRegEx.java:293:            if (m_pattern == null) {
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:1246:            if (m_resdir == null) {
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:1291:            if (m_source == null) {
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:966:            if (m_resdir == null) {
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:976:                if (m_resdir == null) {
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:995:            if (m_nodeSrc == null) {
```

## Mutable Static Runtime-State Inventory

Master baseline audit command:

```bash
git grep -n -P '^\s*(private|protected|public) static (?!final)[^;(]*(TypeConstant|TypeComposition|ClassTemplate|ClassComposition|MethodStructure|MethodConstant|SignatureConstant|ArrayConstant|ArrayHandle|xEnum|Map<TypeConstant|Set<String>)[^;]*;' \
  master -- javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/Utils.java \
  | rg -v 'INSTANCE;' | sort -u
```

Master count and current branch remainder:

```text
master: 138
current branch: 0
fixed in this branch: 138
```

High-risk categories:

- Container-owned compositions in static fields, such as the old
  `xException.s_clzIllegalState` cache fixed in this branch.
- Pool-owned type constants in static fields remain a must-review category
  even though the known `Utils` examples are now fixed.
- Structure-owned methods in static fields, such as the old
  `xConst.FN_APPEND_TO` cache fixed in this branch.
- Runtime handles/constants in static fields, for example
  the native enum handle globals.
- Static maps keyed by pool-owned values remain a must-review category even
  after this branch removes the native-template examples currently known here.

Each of these should become final lazy state on the owner, a container-owned
cache entry, or a true immutable static constant with no runtime owner.

## Broader Source-Scan Categories

The following scans go beyond PR #534 and the native-template startup race.
They are included because the same state-management policy determines whether
parallel containers are reviewable at all.

### Must Fix: Non-Final Static Runtime Globals

Audit commands:

```bash
rg -n --pcre2 "^\s*(?:public|protected|private)?\s*static\s+(?!final\b)(?!class\b)(?!interface\b)(?!enum\b)[^;=()]+\s+[A-Za-z_][A-Za-z0-9_]*(\s*=\s*[^;]+)?;" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l

rg -n --pcre2 "^\s*(?:public|protected|private)?\s*static\s+(?!final\b)(?!class\b)(?!interface\b)(?!enum\b)[^;=()]+\s+[A-Za-z_][A-Za-z0-9_]*(\s*=\s*[^;]+)?;" \
  javatools/src/main/java | wc -l
```

Current counts:

```text
9 runtime/asm/compiler non-final static fields
13 all-Java non-final static fields
```

Representative examples:

- `DebugConsole.LINE_READER`, `DebugConsole.TERMINAL`, and
  `DebugConsole.READER`.
- `xTerminalConsole.READER` and `xTerminalConsole.TERMINAL`.
- Compiler counters such as `ConditionalStatement.s_nLabelCounter`,
  `ElseExpression.s_nCounter`, and `ElvisExpression.s_nCounter`.

Why this is bad design:

- It hides the owner. A static field has JVM-wide lifetime, but most runtime
  values belong to a container, pool, frame, service, or template.
- It hides the publication edge. A plain static write during startup is not
  automatically visible to other startup threads.
- It prevents clean teardown and restart. Tests that start several containers
  in one JVM inherit state from earlier runs unless every field is manually
  reset.
- It makes code review misleading. A static field looks like stable shared
  infrastructure even when it is "last writer wins".

Proper replacements:

- True process constants: `private/public static final` immutable values.
- Container/pool/template state: final fields on the owner, final `Lazy`, or an
  owner-owned `ConcurrentMap`.
- Resettable process resources: a private holder protected by a lock or
  `AtomicReference`, with explicit lifecycle methods.
- Counters used only for diagnostics: `AtomicInteger` or owner-local counters;
  for compiler-only thread confinement, document the confinement.

### Must Fix: Owner-Local Runtime Metadata Caches

Audit command:

```bash
rg -n --pcre2 "\bprivate\s+(?!final\b)(?:transient\s+)?(?:TypeConstant|TypeComposition|ClassComposition|ClassTemplate|MethodStructure|ObjectHandle|ArrayHandle|SingletonHandle|StringHandle|xEnum|x[A-Z][A-Za-z0-9_]*)\s+[ms]_[A-Za-z0-9_]+\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm | wc -l
```

Current count:

```text
181 non-final runtime/asm metadata fields
```

Representative examples:

- `ConstantPool.m_typeNakedRef` and the long list of transient
  `m_type*` constants.
- `ClassComposition.m_methodInit`.
- `NativeContainer.m_hOSStorage`, `m_hFileStore`, `m_hRootDir`, and related
  environment handles.
- `xRTCertificateManager.m_typeCanonical`, `xRTKeyStore.m_typeCanonical`,
  `xRTNetwork.m_typeCanonical`, and similar native template canonical types.
- `xRTConnector.m_typeCanonical`, `m_hDefaultNames`, and `m_hDefaultValues`.
- `xRegEx.m_constructorMatch`, `m_clzMatchStruct`, and `m_clzRangeOfInt`.

Why this is bad design:

- Some of these fields are legitimate lifecycle state, but many are manual
  first-use caches with the shape `if (m_x == null) { m_x = ...; }`.
- Even when the field is not static, the owner object can be shared. Constants,
  pools, containers, and templates are specifically shared by runtime code.
- A mutable group of fields can be observed in a mixed state if a callback,
  reflection path, or error formatting path reenters during initialization.

Proper replacements:

- Immutable metadata with one owner: final `Lazy<T>`.
- Several related metadata values that must come from the same owner:
  final `Lazy<InfoRecord>` where `InfoRecord` groups them.
- Keyed metadata: owner-owned `ConcurrentMap<K, V>` or
  `ConcurrentMap<K, Lazy<V>>`.
- True lifecycle state: one immutable state object in `AtomicReference` or a
  lock around all state fields.
- Thread-confined compiler AST state: leave mutable only with a clear
  confinement comment.

### Must Fix: Split Lifecycle State

The old `SingletonConstant` pattern used several mutable fields for one
logical state: completed handle, initializing owner, waiter future, and
in-progress marker. That style is a must-fix category wherever it appears.

Why this is bad design:

- A reader can observe the owner from one transition and the handle/waiter from
  another transition.
- A partial abort can clear one field while another field still indicates
  initialization.
- Fiber recursion and cross-fiber waiting are semantically different, but split
  fields make them easy to conflate.

Proper replacement:

- One immutable lifecycle snapshot.
- One `AtomicReference<State>` or one lock guarding all transitions.
- CAS or locked transition methods that publish the whole state at once.
- No `Lazy` when the state can suspend, recurse, abort, or wake waiters.

In this branch, `SingletonConstant` uses:

```java
private final transient AtomicReference<InitState> f_state =
        new AtomicReference<>(InitState.EMPTY);
```

`InitState` contains the handle, owner fiber, and waiter future. Local variables
named `cfInitialized` or `fiberInitializing` are not the problem; the old
problem was storing those pieces as independent mutable fields.

### Should Fix Soon: Volatile As Partial Synchronization

Audit command:

```bash
rg -n "\bvolatile\b" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l
```

Current count:

```text
21 volatile hits
```

Representative examples:

- `ConstantPool.m_mapConstants`, `m_mapLocators`, and `m_cInvalidated`.
- `ClassComposition.m_mapFields`.
- `TypeConstant.m_typeinfo`, `m_cInvalidations`, `m_mapRelations`, and
  `m_tloInProgress`.
- `TypeInfoReal.m_mapOps`.
- `Fiber.m_status` and `Fiber.m_fResponded`.
- `ServiceContext.m_lLockScheduling`.
- `xNanosTimer.Trigger.m_fDead` and `m_trigger`.
- `xRTSocket.SocketHandle.socket`.

Why this is bad design:

- `volatile` can be correct for one independent status flag or reference.
- It is not a general substitute for ownership. It does not make operations on
  a mutable `Map` safe, and it does not make several fields visible as one
  state transition.
- A volatile reference to a mutable object only safely publishes the reference.
  The object's later mutations need their own synchronization.

Proper replacements:

- Keep volatile for independent scalar state with documented semantics.
- Use `AtomicReference<State>` for compound lifecycle state.
- Use `ConcurrentMap` or synchronize around the map for shared mutable maps.
- Use immutable replacement maps when updates are rare and readers dominate.

### Should Fix Soon: Static Mutable Collections

Audit commands:

```bash
rg -n --pcre2 "^\s*(?:public|protected|private)\s+static\s+final\s+(?:Map|HashMap|List|ArrayList|Set|HashSet|EnumMap|ConcurrentHashMap|ConcurrentMap|Deque|Queue|AtomicReference|Timer)(?:<[^;=()]+>)?\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java | wc -l

rg -n --pcre2 "^\s*(?:public|protected|private)\s+static\s+(?!final\b)(?:Map|HashMap|List|ArrayList|Set|HashSet|EnumMap|ConcurrentHashMap|ConcurrentMap|Deque|Queue|AtomicReference|Timer)(?:<[^;=()]+>)?\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java | wc -l
```

Current counts:

```text
11 static final collection/resource-like fields
0 non-final static collection/resource-like fields
```

Representative examples:

- `Token.KEYWORDS`, `ALL_KEYWORDS`, and `PREFIXES`.
- `ConstantPool.s_implicits` and `s_implicitsByPath`.
- `BinaryAST.ALREADY_DISPLAYED`.
- `TypeConstant.s_setRecursions`.
- `UnionTypeConstant.SpecialFunkies`.
- `xService.ATOMIC_PROPERTIES`.
- `xLocalClock.TIMER`, which is now private `static final` and reachable only
  through the narrow `scheduleTimer(...)` scheduler API.

Why this is bad design:

- `static final Map` often gets mistaken for an immutable constant. It is only
  a final reference.
- Global mutable maps are hard to reset between runtime instances and tests.
- If the map key contains pool or type identity, a static map can silently mix
  several runtime worlds.

Proper replacements:

- Immutable lookup tables: `Map.of`, `Map.copyOf`, `Set.of`, `List.of`, or
  unmodifiable wrappers after construction.
- Container/pool/template keyed caches: owner-owned `ConcurrentMap`.
- One-time grouped metadata: final `Lazy<InfoRecord>`.
- Resettable resources such as timers: explicit lifecycle owner plus close or
  reset semantics.

### Should Fix Soon: Exposed Mutable Arrays

Audit commands:

```bash
rg -n --pcre2 "\b(public|protected)\s+static\s+(final\s+)?[^;\n]*\[\]\s+[A-Za-z_][A-Za-z0-9_]*\s*=" \
  javatools/src/main/java | wc -l

rg -n --pcre2 "^\s*(?:public|protected)\s+(?:final\s+)?[^;\n]*\[\]\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l
```

Current counts:

```text
42 public/protected static arrays
75 public/protected array fields in runtime/asm/compiler
```

Representative examples:

- `Utils.OBJECTS_NONE`, `Utils.STRINGS_NONE`, and `Utils.NO_NAMES`.
- `ClassTemplate.VOID`, `THIS`, `OBJECT`, `INT`, `STRING`, `BOOLEAN`, and
  `BYTES`.
- `Annotation.NO_ANNOTATIONS`.
- `BinaryAST.NO_ASTS`, `NO_EXPRS`, `NO_TYPES`, and related arrays.
- `Constant.NO_CONSTS`, `TypeConstant.NO_TYPES`, `MethodBody.NO_BODIES`.
- `Frame.WAIT_FOR_FUTURE`, `Frame.WAIT_FOR_IO`, and `Utils.WAIT_FOR_RELIEF`.
- `xRTDelegate.ELEMENT_TYPE` and `xArray.ELEMENT_TYPE`.
- `LongLong.ZEROx2` and `OVERFLOWx2`.
- `Frame.f_ahVar`, `Frame.f_aInfo`, and `Frame.f_anNextVar`.

Why this is bad design:

- Java arrays are mutable. `public static final T[]` is still writable by any
  caller that has the reference.
- Array elements are separate shared variables under the memory model.
- Empty arrays are less risky because there is no element to overwrite, but
  the API still teaches callers that arrays are acceptable public constants.
- Exposed frame/register arrays can be valid for performance, but only if the
  owning frame is thread-confined or all mutation is controlled by the runtime
  scheduler.

Proper replacements:

- Public constants: immutable lists, or private arrays returned through
  defensive copies.
- Internal empty arrays: make private or package-private and document that
  callers must not write through them.
- Hot frame/register storage: keep arrays only behind clear owner-thread
  confinement.

### Should Fix Soon: Public Or Protected Mutable Fields

Audit command:

```bash
rg -n --pcre2 "^\s*(?:public|protected)\s+(?!static\b)(?!final\b)(?:transient\s+)?(?:volatile\s+)?[^;=()]+\s+[msf]_[A-Za-z0-9_]+\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l
```

Current count:

```text
166 public/protected mutable fields
```

Representative examples:

- `Frame.m_iPC`, `m_iScope`, `m_hException`, `m_deferred`,
  `m_frameNext`, and `m_continuation`.
- `ObjectHandle.m_clazz` and `m_fMutable`.
- `Container.m_contextMain` and `m_hTypeSystem`.
- `ClassTemplate.m_templateSuper` and `m_clazzCanonical`.
- Many mutable opcode fields such as `OpIndex.m_nTarget` and
  `OpInvocable.m_constMethod`.
- `xArray.ArrayHandle.m_hDelegate` and `m_hHash`.
- `xTuple.TupleHandle.m_ahValue`.

Why this is bad design:

- Public/protected mutation bypasses invariants. Any subclass or package peer
  can change state without taking the lock or preserving owner relationships.
- It makes threading policy implicit. A field might be safe only because frames
  are fiber-confined, but the declaration does not say that.
- It weakens final-field reasoning. Reviewers cannot tell which references are
  stable after construction.

Proper replacements:

- Private fields plus methods that preserve invariants.
- Final fields for constructor-fixed dependencies.
- Explicit owner-thread comments for performance-critical mutable frame/opcode
  state.
- For subclass extension points, protected final accessors instead of protected
  mutable fields.

### Should Fix Soon: Thread-Local Hidden Context

Audit command:

```bash
rg -n "\bThreadLocal\b|\bTransientThreadLocal\b" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l
```

Current count:

```text
17 ThreadLocal/TransientThreadLocal hits
```

Representative examples:

- `ConstantPool.s_tloPool`.
- `ConstantPool.f_tlolistDeferred`.
- `MultiMethodStructure.s_tloIgnoreNative`.
- `TypeConstant.m_tloInProgress` and the associated
  `AtomicReferenceFieldUpdater`.
- `TypeParameterConstant.f_tloReEntry`.
- `ServiceContext.s_tloContext`.
- `xRTServer.f_tloKeyStore`.

Why this is bad design:

- Thread locals are hidden global state. They make behavior depend on the
  current carrier thread instead of explicit runtime context.
- They are easy to leak across pooled or reused threads if cleanup is not
  perfectly scoped.
- They are a poor fit for fibers and continuations because the logical owner is
  usually a service, fiber, frame, or container, not the Java thread.

Proper replacements:

- Pass the owner context explicitly when practical.
- Store recursion guards or deferred lists on the pool/container/fiber that
  owns the operation.
- If a thread local remains necessary, wrap it in a scoped API that restores or
  removes the value in `finally`.

### Should Fix Soon: Weak And Identity Mutable Maps

Audit command:

```bash
rg -n --pcre2 "Collections\.newSetFromMap\(new (?:IdentityHashMap|WeakHashMap)|new WeakHashMap|new IdentityHashMap" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l
```

Current count:

```text
12 weak/identity map construction hits
```

Representative examples:

- `Runtime.f_containers = new WeakHashMap<>()`.
- `ConstantPool.f_setValidPools =
  Collections.newSetFromMap(new IdentityHashMap<>())`.
- `ServiceContext.m_mapTransient = new WeakHashMap<>()`.
- `ServiceContext.f_mapOpInfo = new WeakHashMap<>()`.
- Local identity maps used for copy/visited algorithms in `TypeInfoReal`,
  `ObjectHandle`, `xRTDelegate`, and `xTuple`.

Why this is bad design:

- `WeakHashMap` and `IdentityHashMap` are not concurrent collections.
- Weak-key behavior can make cache contents disappear at surprising points if
  ownership is not explicit.
- Identity-key behavior is correct for visited sets and pool identity, but it
  is easy to accidentally use as a process-global cache.

Proper replacements:

- Local visited maps: keep them local.
- Owner-owned weak state: synchronize or use a concurrent weak-map helper.
- Identity caches: document why identity is required and keep the cache under
  the owner that defines identity.

### Should Fix: Constant-Looking Non-Final Public Statics

Audit command:

```bash
rg -n --pcre2 "\bpublic\s+static\s+(?!final\b)[^;=()]+\s+[A-Z][A-Z0-9_]*\s*=|\bpublic\s+static\s+(?!final\b)[^;=()]+\s+[A-Z][A-Z0-9_]*;" \
  javatools/src/main/java | wc -l
```

Current count:

```text
2 runtime/asm/compiler public non-final uppercase/static constant-shaped fields
5 all-Java public non-final uppercase/static constant-shaped fields
```

Representative examples:

- `EnumValueBuilder.NAME`, `EnumerationBuilder.NAMES`, and
  `EnumerationBuilder.VALUES`.
- `xTerminalConsole.READER` and `xTerminalConsole.TERMINAL`.

Why this is bad design:

- Uppercase naming tells reviewers "constant", but the Java declaration says
  mutable global state.
- If the value is actually a runtime handle or composition, making it look like
  a constant hides the owner problem.
- If the value is a literal, omitting `final` throws away class-initialization
  safety and invites accidental reassignment.

Proper replacements:

- Literal constants: `public static final`.
- Mutable runtime handles: owner-scoped final `Lazy` or explicit container
  accessor.
- Compatibility fields that cannot yet move: isolate and mark as temporary
  TODOs, then remove in the next migration slice.

### Should Fix: Rare Non-Final `f_` Fields

Audit command:

```bash
rg -n --pcre2 "^\s*(?:public|protected|private)\s+(?!final\b)(?!static\s+final\b)(?:static\s+)?(?:transient\s+)?(?:volatile\s+)?[^;=()]+\s+f_[A-Za-z0-9_]+\s*(?:=|;)" \
  javatools/src/main/java | wc -l
```

Current count:

```text
2 non-final f_ fields
```

Naming-convention note: no formal XVM Java naming document was found in this
repo. The claim here is based on observed source usage, not a written standard.
An explicit declaration scan found hundreds of final `f_` field declarations
and only the two non-final declarations below with access modifiers. That makes
non-final `f_` fields unusual enough to be review hazards, even if the prefix
is not formally specified.

Current hits:

```text
javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2027:        private long      f_ldtScheduled; // when
javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTConnector.java:362:        protected SSLContext f_sslContext;
```

Why this is bad design:

- The dominant source convention makes `f_` read as fixed/final owner state.
- A mutable `f_` field defeats that expectation and increases review burden.
- If the prefix is meant to mean something else, the codebase needs a written
  convention; otherwise reviewers will infer fixed/final state from usage.

Proper replacements:

- If immutable, make the field `final`.
- If mutable, rename to the mutable-field convention and document who mutates
  it and under what lock or owner-thread rule.

### Should Fix: Mutable Collections Requiring Confinement Proof

Audit command:

```bash
rg -n --pcre2 "^\s*(?:public|protected|private)\s+(?:final\s+)?(?:Map|HashMap|List|ArrayList|Set|HashSet|WeakHashMap|IdentityHashMap|EnumMap|ConcurrentHashMap|ConcurrentMap|Deque|Queue)(?:<[^;=()]+>)?\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l
```

Current count:

```text
366 collection fields in runtime/asm/compiler
```

Representative examples:

- `Runtime.f_containers`.
- `NestedContainer.f_mapResources`.
- `ClassComposition.f_mapCompositions`, `f_mapProxies`, and
  `f_mapProperties`.
- `Container.f_mapCompositions` and `f_mapTemplatesByType`.
- `ConstantPool.f_listConst`, `f_setValidPools`, `f_implicits`,
  `f_listInvalidated`, and `f_mapRefTypes`.
- `Fiber.m_mapTokens`, `m_listUnhandledEx`, and
  `m_mapPendingUncaptured`.
- Many compiler AST lists and maps such as statement/expression child lists.

Why this is bad design:

- A final collection field is still mutable. The field is stable, but the
  collection contents are not.
- Some of these are correctly owner-confined. The problem is that the
  declaration alone does not say which ones.
- Runtime-owned collections are common places for accidental cross-container
  and cross-fiber sharing.

Proper replacements:

- Immutable collections for fixed data.
- `ConcurrentHashMap`/concurrent sets for shared keyed caches.
- Private final collections plus synchronized methods for compound invariants.
- Clear owner-thread or fiber-confinement comments for mutable compiler/AST or
  frame state.

## Replacement Decision Matrix

| Current pattern | Use `Lazy`? | Better mechanism when `Lazy` is wrong |
| --- | --- | --- |
| `private T m_x; if (m_x == null) m_x = compute();` where `T` is immutable owner-derived metadata | Yes, `private final Lazy<T>` | Eager final field if cheap |
| Several caches computed from the same container/pool | Yes, but group them | `private final Lazy<InfoRecord>` |
| Cache keyed by `TypeConstant`, `ClassConstant`, or other owner identity | Sometimes | Owner-owned `ConcurrentMap<K, V>` or `ConcurrentMap<K, Lazy<V>>` |
| Singleton construction that can suspend or recurse | No | `AtomicReference<State>` plus CAS, or a lock and condition/future |
| Timer, terminal, socket, file watcher, or external resource | No | Explicit lifecycle owner with start/stop/close and synchronized access |
| Public empty array constant | No | Immutable list, private array plus clone, or documented private/package empty array |
| Debug counter or label counter | No | `AtomicInteger`, owner-local counter, or documented compiler-thread confinement |
| Volatile mutable map reference | No | `ConcurrentMap`, immutable copy-on-write map, or synchronized map owner |

## Why This Matters For Reentrancy

Reentrant code is possible when observing an object never requires knowing which
phase of startup mutated it. Final fields give that property. Final `Lazy`
fields preserve it for expensive metadata: the reference to the lazy cell is
stable, and the cell synchronizes first computation.

Mutable fields destroy automatic reentrancy. A callback, async continuation,
reflection path, debugger display, or `toString()` can enter code while startup
is in progress and observe a mixture of before and after state. That forces
every caller to know undocumented temporal rules such as "do not call this
helper before `initNative()` has assigned the static constructor cache". Those
rules are not enforceable by Java and are exactly what failed in the parallel
runner.

The goal is not "all fields must be final" as dogma. The goal is:

- immutable state is final,
- expensive immutable state is final `Lazy`,
- mutable lifecycle state is represented by one atomic/locked state object,
- container-derived state is scoped to the container/template that owns it,
- every suppression is reviewed as an exception.

## References

- Oracle `javac` manual:
  https://docs.oracle.com/en/java/javase/26/docs/specs/man/javac.html
- JLS 12.5, Creation of New Class Instances:
  https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.5
- JLS 17.4, Memory Model:
  https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4
- JLS 17.4.5, Happens-before Order:
  https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5
- JLS 17.5, Final Field Semantics:
  https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5
- JetBrains Inspectopedia, "`this` reference escaped in object construction":
  https://www.jetbrains.com/help/inspectopedia/ThisEscapedInObjectConstruction.html
- JetBrains Inspectopedia, "Overridable method called during object construction":
  https://www.jetbrains.com/help/inspectopedia/OverridableMethodCallDuringObjectConstruction.html
