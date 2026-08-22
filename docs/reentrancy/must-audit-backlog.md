# Must-Audit Backlog

This file tracks repository-wide state patterns that are not yet proven defects
but are too owner-sensitive or concurrency-sensitive to treat as ordinary style
cleanup.

`Must audit` sits between `must fix` and `should fix`:

- `Must fix`: a concrete race, wrong-owner cache, constructor escape, or
  broken lifecycle state is known.
- `Must audit`: the code may be safe only because of an assumption that is not
  encoded in the API, such as single-threaded compilation, per-container method
  graph ownership, no same-JVM reuse, or manual ThreadLocal cleanup.
- `Should fix`: the code is unnecessarily mutable or awkward, but current
  owner/lifecycle evidence does not make it a likely correctness bug.

The standard for closing a `must audit` item is higher than "it has not crashed
yet". Close it only by proving confinement/ownership, adding a focused test, or
moving the state behind an explicit owner/synchronization model.

## Current Wide Scans

These commands were run on branch `lagergren/lazy-instance` on 2026-08-21.

Manual lazy null caches across all Java sources:

```bash
rg -U --pcre2 -c \
  "if\s*\(\s*((?:this\.)?(?:m_|s_|f_)[A-Za-z][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*\{[\s\S]{0,320}\1\s*=(?!=)" \
  javatools/src/main/java | awk -F: '{s+=$2} END {print s+0}'
```

```text
43 strong same-field lazy-init matches across javatools/src/main/java
23 of those are in runtime/asm
```

Non-final static fields across all Java sources:

```bash
rg -n --pcre2 \
  "^\s*(public|protected|private)?\s*static\s+(?!final\b)(?!class\b|interface\b|enum\b)[^;(=]+\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java | sort
```

```text
javatools/src/main/java/org/xvm/compiler/ast/ConditionalStatement.java:80
javatools/src/main/java/org/xvm/compiler/ast/ElseExpression.java:220
javatools/src/main/java/org/xvm/compiler/ast/ElvisExpression.java:316
javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java:1265
javatools/src/main/java/org/xvm/javajit/Ctx.java:186
javatools/src/main/java/org/xvm/javajit/builders/EnumValueBuilder.java:90
javatools/src/main/java/org/xvm/javajit/builders/EnumerationBuilder.java:77
javatools/src/main/java/org/xvm/javajit/builders/EnumerationBuilder.java:82
```

Ambient and weak/identity state scan:

```bash
rg -n "ThreadLocal|TransientThreadLocal|ScopedValue|WeakHashMap|IdentityHashMap|volatile|synchronized\s*\(" \
  javatools/src/main/java/org/xvm
```

This scan is intentionally broad. Each hit needs classification by owner and
lifetime, not blind conversion.

## Consolidated Must-Fix Task List

This list intentionally pulls from every reentrancy document in this folder:
runtime ownership, `this-escape`, lint, constant-pool/adoption, clone usage,
manual lazy caches, JIT implications, and same-JVM stress planning. If a
category below is marked `must audit`, it becomes `must fix` as soon as a test,
diagnostic, or code inspection proves owner sharing, cross-request reuse, or
runtime publication.

| Status | Task | Source category | Why it matters | Required closure |
| --- | --- | --- | --- | --- |
| Done in this branch | Remove constructor-published native template `INSTANCE` globals and static owner metadata | `must-fix-races.md`, `state-inventory.md`, `fixed-in-this-branch.md` | Process-global mutable template/metadata state was the original last-writer-wins, wrong-container, constructor-publication bug family. | Keep `NativeTemplates`, owner-local lazy metadata, and source scans that prevent `INSTANCE = this` from returning. |
| Done in this branch | Replace split `SingletonConstant` lifecycle state | `must-fix-races.md`, `fixed-in-this-branch.md` | Separate handle/fiber/future fields allowed impossible mixed initialization snapshots. | Keep the `AtomicReference<InitState>` state machine and singleton lifecycle tests. |
| Done in this branch | Fix adopted constant runtime/helper state leaks proven by stress | `constant-adoption-clone-audit.md`, `stress-discovered-runtime-issues.md` | Shallow clone/adoption copied owner-local runtime state across pools, including the proven singleton handle leak. | Keep explicit `adoptedBy(...)` overrides/clears and `ConstantAdoptionValidator` stress runs. |
| Done in this branch | Close public/native natural enum raw-handle publication paths | `must-fix-races.md`, `native-template-startup-safety.md` | Public paths could expose natural enum construction structs instead of initialized enum singleton handles. | Keep public enum helpers routed through initialized/deferred enum handles and owner-local native enum factories. |
| Done in this branch | Move `NativeContainer` startup work out of the constructor | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | Startup installed owner-local templates/resources while the owner object was still under construction. | Keep `NativeContainer.create(...)` and lint tests that show no `NativeContainer.java` `this-escape` warnings. |
| Done in this branch | Remove runtime handle constructor publication/mutation escapes | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | `RefHandle` and file-node handles wrote themselves or backing fields through visible paths before construction completed. | Keep factory construction and `RefHandleConstructionTest`. |
| Done in this branch | Remove runtime constructor assertion escapes and `ClassTemplate` implicit-field hook | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | Constructor-time virtual calls made partially constructed runtime objects observable to subclass behavior. | Keep static/private constructor validation and explicit `ClassTemplate` metadata. |
| Done in this branch | Remove ASM `Op` constructor-shape virtual dispatch | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | Base opcode constructors called subclass-overridable shape predicates while deserializing packed operands before subclass construction completed. | Keep constructor-only shape metadata, private `ConstantRegistry` helpers, and `OpRuntimeCacheTest` layout/footprint guards. |
| Done in this branch | Remove utility constructor helper dispatch | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | `PackedInteger`, `HasherReference`, and `ListSet` constructors called public/protected APIs while subclass construction was incomplete. | Keep private constructor helpers and `UtilityConstructorEscapeTest`. |
| Done in this branch | Remove `MethodInfo`/`PropertyInfo` body owner constructor escapes | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | Method/property owners passed partially constructed `this` into `MethodBody.forMethod(...)` and `PropertyBody.forProperty(...)`, so child metadata could observe default owner state. | Keep static factories, non-virtual owned body copies, final body arrays, focused constructor-escape tests, and `TypeInfoReal` ownership validation. |
| Done in this branch | Remove ASM metadata owner-copy constructor escapes | `this-escape-removal-audit.md`, `fixed-in-this-branch.md`, `ownership-diagnostics.md` | `FileStructure`, `MethodStructure`, `PropertyStructure`, `VersionTree`, `PropertyConstant`, and `TypeInfoReal` used constructor-time virtual hooks or mutable source metadata owner assignment. Parallel `TypeInfoReal` construction could let one owner steal shared unowned method/property/child metadata. | Keep final root/owner assembler types, static/private constructor validation, per-owner `forType(...)` copies, focused constructor-equivalence tests, and parallel `TypeInfoReal` ownership tests. |
| Done in this branch | Remove `ModuleInfo` resource-dir constructor escape | `this-escape-removal-audit.md`, `fixed-in-this-branch.md` | The explicit-resource constructor path called overridable `getResourceDir()` before source/binary/resource fields were fully assembled. | Keep private `ensureResourceDir()` for constructor-time cache initialization and `ModuleInfoTest` hook coverage. |
| Done in this branch | Remove runtime-executed owner-bearing `Op` caches | `manual-lazy-cache-audit.md`, `fixed-in-this-branch.md` | Shared decoded op graphs could cache `Frame` constants from one owner and reuse them in another. | Keep frame-local resolution and `OpRuntimeCacheTest`. |
| Done in this branch | Fix first-PR manual lazy null cache hazards | `manual-lazy-cache-audit.md`, `fixed-in-this-branch.md` | Regex, FS path, and native file-node creation caches had real publication or owner-transfer hazards. | Keep final `Lazy` where safe, adoption cache clearing, and file owner validation. |
| Done in this branch | Fix weak registry monitor mismatch | `runtime-ownership-hardening-ledger.md` | `Runtime.findContainer(...)` could iterate a weak registry while another path registered/expunged containers under a different monitor. | Keep common monitor access and `RuntimeTest`. |
| Done in this branch | Fix structural hash lint contracts and make `Constant.m_iHash` publication explicit | `lint-parallelism-risk-audit.md`, `fixed-in-this-branch.md` | `equals(...)` without matching `hashCode()` corrupts ordinary maps/sets; a plain cached constant hash field was an unexplained race-shaped cache. | Keep recomputed hashes for mutable metadata, volatile resolved-only constant hash cache, and hash contract tests. |
| Fixed separately | Utility constructor escape hazards | `this-escape-removal-audit.md` | `CooperativelyCleanableReference` published `this` to a static set; `AbstractConverterMap` called overridable factories from the base constructor. | Merge/rebase `lagergren/fix-utils-this-escape` or keep those two sites tracked until merged. |
| Split/background | Compiler counters | `state-inventory.md`, previous compiler counter branch | Process-global non-atomic counters are wrong for parallel/same-JVM compiler requests. | Keep this out of the runtime branch; finish the separate compiler-counter PR with behavior tests. |
| Done in this branch | Compiler/lexer/parser `this-escape` warnings | `this-escape-tally.md`, `compiler-lexer-parser-this-escape.md`, `fixed-in-this-branch.md` | Incremental/parallel compilation cannot rely on parser/compiler constructors dispatching into partially built AST/request objects. | Keep private lexer priming, lazy parser token priming, AST construction factories, and `CompilerThisEscapeConstructionTest` hook coverage. |
| Compiler cleanup backlog | Constant-folding conversion diagnostics | `compiler-lexer-parser-this-escape.md`, `Expression.convertConstant(...)`, `ConvertExpression.convertConstantValue(...)` | `System.err.println("No conversion found ...")` is not a compiler diagnostic and the multi-value conversion path preserves a legacy partial `Constant[]` with null slots. | Replace stderr with `ErrorListener` diagnostics or a structured compiler logger, then decide with focused tests whether failed multi-value folding should clear the whole constant array or report a hard compile-time error. |
| Done in this branch | Remove runtime `Container` constructor owner escapes | `this-escape-removal-audit.md`, `fixed-in-this-branch.md`, `ownership-diagnostics.md` | `new ConstHeap(this)`, field-initialized `new NativeTemplates(this)`, and base-constructor `registerContainer(this)` could expose a partially constructed owner. | Keep owner-explicit `ConstHeap`, lazy owner-local `NativeTemplates`, post-construction `MainContainer`/`NestedContainer` registration, and diagnostics that show registry membership plus explicit-owner heap state. |
| Done in this branch | `Parameter.cloneBody()` source mutation and copied deref state | `clone-usage-audit.md`, `fixed-in-this-branch.md` | The old clone method cleared deref state on the source and left copied method-owned register state on the clone. | `Parameter` no longer implements `Cloneable`; `copyFor(MethodStructure)` copies logical metadata for an explicit owner and drops the method-owned deref register. |
| Done in this branch | `MethodStructure.cloneBody()` cloned parameter ownership | `clone-usage-audit.md`, `fixed-in-this-branch.md` | Cloned parameters were assigned back to the original method owner via `param.setContaining(this)`. | `MethodStructure.cloneBody()` now copies parameters with `copyFor(that)`, and tests assert copied params/returns resolve through the clone. |
| Done in this branch | Delegated method `Parameter[]` element sharing | `clone-usage-audit.md`, `fixed-in-this-branch.md` | Delegated methods cloned only parameter array containers and shared mutable `Parameter` elements. | Synthetic delegated methods now use `createMethodCopyingParameters(...)`, which copies parameter elements for the new method owner before publication. |
| Partially done; remaining must audit | `ObjectHandle.cloneAs(...)` shallow runtime-handle copies | `clone-usage-audit.md`, `fixed-in-this-branch.md` | View/mask clones can share mutable field arrays and rewired `RefHandle` objects. Cross-container movement was the highest-risk subcase because it could retain source-owner handles while changing the apparent owner. | This branch now rejects direct cross-owner `GenericHandle.maskAs(...)` unless the handle graph is already shared with the target container. Remaining work is an explicit same-object view backing model that preserves write-through semantics without source-view ref rewrites. |
| Must audit, must fix by subclass | Default `Constant.adoptedBy(...)` shallow clone | `clone-usage-audit.md`, `constant-pool-hostile-state-audit.md` | Every new owner-local helper field on a `Constant` subclass is copied unless that subclass opts out. | Move away from base shallow clone; require each subclass to declare adoption behavior; keep validator enabled in stress/CI. |
| Must audit | Complete `ConstantPool` ownership, mutation, and late-registration model | `constant-pool-state-audit.md`, `constant-pool-hostile-state-audit.md` | The pool is shared by compiler, linker, serializer, runtime, diagnostics, and JIT; late mutation or wrong ambient owners create stale owner graphs. | Keep explicit owner parameters, scoped owner assertions, late-registration diagnostics, and per-cache owner documentation. |
| Must fix when runtime path can reach it | Semantic `ConstantPool.getCurrentPool()` in constants and metadata helpers | `constant-pool-state-audit.md`, `constant-pool-hostile-state-audit.md` | Hidden thread-local owner lookup can select the wrong pool or `null`; assertion-only owner checks are not production enforcement. | Thread explicit `ConstantPool`, `Frame`, or `Container` parameters through constants/metadata helpers; keep scoped lookup only at narrow bridges with non-assert diagnostics where needed. |
| Must audit, likely must fix for published runtime pools | Runtime-published pools remain mutable unless diagnostics are enabled | `constant-pool-state-audit.md`, `constant-pool-hostile-state-audit.md` | `register(...)` and `ensure*Constant(...)` can grow or rewrite pool state after a pool is container-visible; the current late-registration guard is opt-in. | Split mutable compiler/linker pools from frozen runtime pools, or make post-publication registration fail on runtime paths after warmup proves the needed constants are pre-created. |
| Must audit | `ConstantPool.register(...)` publishes constants before recursive registration completes | `constant-pool-hostile-state-audit.md` | A constant is inserted into list/map storage before `registerConstants(...)` finishes and before some subclasses rewrite owner-sensitive child fields; another thread can observe a partial registration. | Make registration a single-owner phase or build/adopt recursively before publishing into lookup tables; assert logical hash/equality fields do not change after map insertion. |
| Done in this branch | `TypeConstant.s_setRecursions` mutable static `HashSet` | `constant-pool-hostile-state-audit.md`, `fixed-in-this-branch.md` | A process-global `HashSet` was mutated during type relation recursion diagnostics; concurrent type checks could corrupt it even though the state was diagnostic-only. | Keep `ConcurrentHashMap.newKeySet()` plus `TypeConstantRecursionDiagnosticsTest`, which fails on the old `HashSet` shape and stresses parallel diagnostic additions. |
| Done in this branch | `TypeConstant` covariance/contravariance ambient pool lookup | `constant-pool-hostile-state-audit.md`, `ambient-context-audit.md`, `fixed-in-this-branch.md` | Type relation helpers silently used `ConstantPool.getCurrentPool()` to resolve auto-narrowing/generic helper constants; parallel containers or stale scoped bridges could select an arbitrary owner pool. | Keep explicit `ConstantPool` parameters and `TypeConstantOwnerApiTest`, which proves old ownerless signatures are gone and missing owners fail immediately. |
| Done in this branch | Numeric range folding ambient pool lookup | `constant-pool-hostile-state-audit.md`, `ambient-context-audit.md`, `fixed-in-this-branch.md` | `ByteConstant` and `IntConstant` range operations used a hidden current pool, so missing ambient scope crashed and stale scope registered `RangeConstant` values in the wrong owner. | Keep receiver-pool range construction and `ConstantRangeOwnerTest`, which covers no ambient pool and wrong ambient pool. |
| Done in this branch | `ConstantPool.checkFunctionCompatibility(...)` ambient pool lookup | `constant-pool-hostile-state-audit.md`, `ambient-context-audit.md`, `fixed-in-this-branch.md` | An instance method on `ConstantPool` ignored the receiver and asked thread-local state for `Tuple<>`; missing or stale ambient scope changed or crashed the answer. | Keep receiver-pool `typeTuple0()` and the focused `ConstantPoolDiagnosticsTest` no-ambient compatibility case. |
| Done in this branch | Nested identity generic resolution ambient pool lookup | `constant-pool-hostile-state-audit.md`, `ambient-context-audit.md`, `fixed-in-this-branch.md` | `resolveNestedIdentity(pool, resolver)` accepted an explicit output pool but resolver-backed nested identities later ignored it and used `ConstantPool.getCurrentPool()`. | Keep resolver-backed nested identities carrying the explicit output pool and `NestedIdentityOwnerTest`, which proves a wrong ambient pool is ignored. |
| Done in this branch | Method/property metadata ambient pool helpers | `constant-pool-hostile-state-audit.md`, `ambient-context-audit.md`, `fixed-in-this-branch.md` | `MethodBody`, `MethodInfo`, and `PropertyInfo` used hidden current-pool state while resolving annotations, narrowing signatures, or adopting shared property identities. | Keep receiver-owner pool derivation and the no-ambient metadata owner tests in `MethodInfoTest` and `TypeInfoMemberOwnershipTest`. |
| Done in this branch | `FileStructure` ambient error-listener fallback | `ambient-context-audit.md`, `constant-pool-hostile-state-audit.md`, `fixed-in-this-branch.md` | `FileStructure.getErrorListener()` could send diagnostics to a wrong ambient pool's listener or crash when no pool was bound. | Keep file-owned diagnostics and `FileStructureTest.errorListenerIgnoresAmbientPool()`. |
| Done in this branch; scoped bridge remains | `ConstantPool.getCurrentPool()` compatibility API | `ambient-context-audit.md`, `scoped-value.md` | Keeping any getter made hidden owner lookup easy even after current call sites were fixed. | `getCurrentPool()` has been removed and is guarded by `ConstantPoolDiagnosticsTest.currentPoolLookupGetterDoesNotExist()`. Eventually replace `withPool(...)` with explicit owner APIs or a tightly scoped boundary object. |
| Must audit | Live runtime handles embedded in constants | `constant-pool-state-audit.md`, `constant-adoption-clone-audit.md` | `HandleConstant` wraps `ObjectHandle`, which is owner-specific runtime state and cannot be treated like serialized logical constant data across pools. | Keep `HandleConstant.adoptedBy(...)` rejecting already-owned handle movement; audit annotated/runtime type paths and add owner-local representations where sharing is required. |
| Should fix soon, must audit if pool is shared concurrently | Per-pool manual lazy implicit/core caches | `constant-pool-state-audit.md` | The caches are owner-local, but plain lazy writes still have no happens-before edge and can duplicate work or publish stale helper state if one pool is used concurrently. | Warm before runtime publication, convert hot/shared groups to final `Lazy` or owner-local concurrent caches, or document confinement for compiler-only caches. |
| Should fix soon | Static mutable implicit metadata maps | `constant-pool-state-audit.md` | Class initialization safely publishes the map references, but `HashMap` contents remain mutable process-wide state. | Wrap `s_implicits` and `s_implicitsByPath` with `Map.copyOf(...)` after construction. |
| Compiler/JIT backlog | Destructive pool optimization, module replacement, and disassembly/assembly mutation | `constant-pool-state-audit.md` | `optimize()`, `replaceModule(...)`, and serialization/disassembly paths mutate pool positions, contents, and caches; they are not safe as shared runtime operations. | Keep mutable compiler pools separate from frozen runtime pools; incremental compiler work should own this isolation. |
| Must audit | Runtime `Op` address/link caches | `manual-lazy-cache-audit.md` | Mutable decoded jump/catch/switch links are safe only if eagerly resolved before publication. | Prove eager linking before runtime publication or move to method-owner synchronized/atomic link state. |
| Must audit | Owner-bearing type/class metadata caches | `manual-lazy-cache-audit.md`, `state-inventory.md` | `ClassComposition`, `TypeConstant`, `TypeInfoReal`, and similar caches may be owner-local or shared; the API often does not say. | Document owner/key/invalidation for each cache; split by owner or use final `Lazy`/concurrent owner maps when shared. |
| Must audit | Raw/unchecked async/service/native-template/JIT metadata paths | `lint-parallelism-risk-audit.md` | Erasure hides whether futures, responses, op-info caches, reflected templates, and type metadata carry the correct owner/payload. | Parameterize or isolate unchecked casts behind typed checked boundaries with owner assertions. |
| Must audit | Fallthrough lifecycle/state-machine switches | `lint-parallelism-risk-audit.md` | State-machine fallthrough in runtime/compiler/JIT code can skip or repeat owner-sensitive stages. | Prove intentional transitions with comments/tests, or replace with explicit returns/continues/extracted stages. |
| Must audit | Ambient `ThreadLocal`/`TransientThreadLocal`/`ScopedValue` state | `scoped-value.md`, `must-fix-races.md`, `state-inventory.md` | Hidden owner context can leak across pooled threads and async callbacks. | Prefer explicit owner parameters; use scoped/lexical bridges with assertions only as transitional glue. |
| Must audit | JIT owner/static/runtime interaction | `jit-implications.md`, `scoped-value.md` | JIT has its own generated statics, `Ctx.Current`, classloader scope, and bridge module assumptions. This branch fixed five local JIT constructor warnings, but `Xvm` startup still publishes the owner during construction. | Do not change `Xvm` startup casually; prove classloader/container scope and document any JIT-specific owner tables before runtime integration. |
| Should fix, must audit if owner-bearing | Public/protected arrays, mutable static collections, and broad public/protected fields | `state-inventory.md`, `must-audit-backlog.md` | `final` array references still expose mutable shared elements; public mutable fields make ownership invariants unenforceable. | Use immutable collections/private arrays/defensive copies; keep hot structs only with explicit owner-thread confinement. |
| Verification backlog | Same-JVM direct/Gradle plugin repeated execution and stress benchmarks | `plans/same-jvm-launcher-stress.md` | The original user pain is repeated execution in one JVM without stale global state; unit tests alone do not prove that. | Expand `runDirectSequenceStress`, add Gradle direct-mode repeated execution, and compare same-JVM vs forked JVM performance. |
| Build-gate backlog | Enable `this-escape` lint across the entire composite Gradle build and promote it to an error | `this-escape-tally.md`, Gradle Java conventions | Once the current inventory is clean, new constructor escapes should fail immediately instead of appearing as advisory IDE/compiler warnings. This should apply to every Java source set in the full repo/composite build, not only `:javatools`. | Add a root/composite verification task that forces all Java compilation with `-Xlint:this-escape`, then make just `this-escape` fatal by default while leaving unrelated lint categories at their existing severity. Any deliberate exception must require a local suppression plus a comment explaining owner, lifetime, and why construction-time escape is safe. |

## Audit Categories

| Priority | Category | Representative sites | Why it must be audited | Proper closure |
| --- | --- | --- | --- | --- |
| Fixed in this branch | Runtime-executed owner-bearing `Op` caches | `asm/op/JumpCond.m_cond`, `asm/op/JumpNCond.m_cond`, runtime write-back to `asm/OpTest.m_typeCommon` and `asm/OpCondJump.m_typeCommon` | These cached constants obtained from a `Frame`. If decoded op graphs are reused across containers or constant pools, the cached value can carry the wrong owner. | The condition fields were removed, and common-type execution resolves from the current `Frame` without writing frame constants onto the shared `Op`. `OpRuntimeCacheTest` guards against reintroducing the pattern. |
| Must audit | Complete `ConstantPool` ownership and mutation inventory | `asm/ConstantPool`, `asm/Constant`, `asm/constants/*`, `adoptedBy(...)`, `clone()`, ambient current-pool helpers, late registration, live `HandleConstant` values | This branch fixes the proven runtime-owner defects, but `ConstantPool` is still shared by compiler, linker, serializer, runtime, diagnostics, and JIT paths. Each mutable or owner-changing edge needs an explicit classification before we can claim the pool is fully reentrant-safe. | Maintain the detailed catalog in `constant-pool-state-audit.md` and the expanded background inventory in `constant-pool-hostile-state-audit.md`. Proper closure is an explicit owner parameter, scoped owner bridge with assertions, owner-local concurrent cache, immutable snapshot/freeze, or a documented proof of confinement. |
| Must audit, likely safe if eagerly linked before publication | Runtime `Op` address/link caches | `asm/OpJump.m_opDest`, `asm/OpCondJump.m_opDest`, `asm/op/LoopEnd.m_opDest`, `asm/op/OpSwitch.m_aOpCase`, `asm/op/JumpInt.m_aOpCase`, `asm/op/GuardStart.m_aOpCatch` | Mutable decoded instruction links are safe only if resolution happens during exclusive linking before runtime publication. Lazy runtime linking would be a plain-field race. | Document and test eager link ordering, or move to synchronized/atomic method-owner link state. |
| Must audit, runtime metadata owner boundary | Type and constant metadata caches | `asm/ConstantPool` volatile maps and thread-local pool, `asm/constants/TypeConstant` volatile caches and `ScopedValue`, `asm/constants/TypeInfoReal` maps, `runtime/ClassComposition.m_mapFields` | These are central owner-bearing metadata structures. Some use synchronization already, but same-JVM incremental compile/runtime reuse depends on exact ownership. Runtime boundary `ConstantPool` scopes are now asserted, but deeper ASM helpers still use ambient lookup. | For each cache, document owner, key, invalidation, and publication. Run with `-Dxvm.asm.validateConstantPoolLateRegistration=true` to find constants created after runtime publication. Convert plain lazy fields to final `Lazy` or owner-owned concurrent caches when they escape one thread. |
| Must audit, compiler reentrancy | Compiler AST and context mutation | `compiler/ast/Context.m_mapByName`, `NameResolver.m_constant`, `InvocationExpression.m_method`, `LambdaExpression.m_lambda`, statement break/continue lists | The compiler historically assumes request-local AST mutation. Incremental and parallel compilation makes that assumption dangerous unless request ownership is explicit. | Prove AST/request confinement, or move caches to a compilation context keyed by request. Separate known global counters are fixed on `lagergren/compiler-counter-atomics`. |
| Must audit, process-global compiler/JIT state | Non-final statics | The four compiler counters; `javajit/Ctx.MD_inject`; JIT builder field-name strings | Non-final statics are shared across every compile/runtime in the JVM and usually have no reset story. | Make immutable constants `static final`; use atomics or owner/request state for counters. |
| Must audit, ambient context | `ThreadLocal`, `TransientThreadLocal`, and `ScopedValue` | `ConstantPool.s_tloPool`, `MultiMethodStructure.s_tloIgnoreNative`, `ServiceContext.s_tloContext`, `TypeConstant.s_context`, `javajit/Ctx.Current` | Ambient context hides dependencies from APIs. ThreadLocal state also leaks across pooled threads if cleanup is missed. This branch removed raw `ConstantPool.setCurrentPool(...)`; the remaining current-pool bridge is lexical and asserted at runtime boundaries. | Prefer explicit owner parameters for permanent APIs. Use `ScopedValue` only as a bounded transitional bridge with lexical lifetime. |
| Must audit, weak/identity owner registries | Weak/identity maps | Fixed in this branch for `Runtime.f_containers`; remaining audit examples are `ServiceContext.m_mapTransient`, `ServiceContext.f_mapOpInfo`, `ConstantPool.f_setValidPools`, and `OwnershipDiagnostics` maps | These maps often encode identity ownership or lifecycle. They are not concurrent by default and weak keys can disappear at surprising times. `Runtime.findContainer(...)` was a real inconsistent-monitor bug and now uses the same monitor as registration/snapshotting. | Prove confinement or synchronization around every access; otherwise use owner-owned concurrent structures or immutable snapshots. |
| Must audit, with must-fix subitems | Java `clone()` / `Cloneable` ownership paths | `MethodStructure.cloneBody()`, `ObjectHandle.cloneAs(...)`, `Constant.adoptedBy(...)`, AST/component clones | The focused audit found clone paths that can preserve original owners, share method/register state, shallow-copy runtime handles, or rely on opt-in constant-adoption validation. The `Parameter` source-mutation, `MethodStructure` copied-owner, and delegated-parameter sharing bugs are fixed here. | Track and close the detailed findings in `clone-usage-audit.md`: replace object clones with explicit owner-aware copy/adoption APIs, and keep shallow array clones limited to documented container-copy use. |
| Should fix soon, must audit if owner-bearing | Static mutable arrays and collections | `BinaryAST.ALREADY_DISPLAYED`, `Token.KEYWORDS`, `NativeNames.reservedMethodName`, static `Op[]` wait-frame arrays, public/protected `NO_*` arrays | `final` protects only the reference. Public arrays and mutable static collections are shared mutable variables. | Replace true constants with immutable collections or private arrays plus defensive copies. Keep only stateless immutable op arrays as documented process-wide constants. |
| Should fix soon, must audit if externally shared | Public/protected mutable fields | `Frame` public execution fields, `ObjectHandle.m_clazz`, `ObjectHandle.m_fMutable`, array delegate storage fields, compiler AST protected fields | Broad mutability makes invariants unenforceable and hides synchronization requirements. Some are deliberate hot-path structs; some are accidental. | Make owner-bearing fields private and expose methods that preserve invariants. For hot-path structs, document owner-thread confinement. |
| Should fix, API cleanup | Explicit anonymous arrays in expression contexts | `createFrameN(..., new int[] {...})`, `ensureSignatureConstant(..., new TypeConstant[] {...})`, `ensureFileConstant(..., new byte[] {...})`, one-element `ObjectHandle[]` varargs bridges | Java array initializer sugar only works when the target array type is explicit, so method-argument sites keep ugly `new T[] {...}` unless the API offers a typed helper/overload. This is mostly readability, but owner-heavy code benefits from naming argument arrays such as return registers or file contents. | Prefer typed locals with `{...}` when touching nearby code. For repeated expression sites, add small owner-preserving overloads or helper methods instead of spreading anonymous arrays through runtime code. This branch removed the explicit anonymous arrays from its added Java lines where a local/helper was clean. |

## Manual Lazy Null Caches

The focused classification of current same-field lazy-null sites is maintained
in [manual-lazy-cache-audit.md](manual-lazy-cache-audit.md).

The rule is:

- If the receiver can be shared by runtime containers, fibers, compilation
  workers, or same-JVM executions, this is `must audit`.
- If the cached value is owner-bearing and the receiver can be shared across
  owners, it becomes `must fix`.
- If the receiver is proven request/thread confined, document that proof and
  leave it out of the first runtime-owner PR.

## Stress Verification Backlog

More same-JVM direct-mode stress is useful verification depth, not a known code
hole by itself. The first PR already adds `manualTests:runDirectSequenceStress`;
the next confidence wave is to run more modules and more iterations with
ownership validation enabled:

```bash
CI=true ./gradlew :manualTests:runDirectSequenceStress \
  -PsameJvmIterations=2 \
  -PsameJvmModules=TestArray,TestReflection \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

The longer plan is in
[plans/same-jvm-launcher-stress.md](plans/same-jvm-launcher-stress.md): serial
same-JVM execution first, then structured failure artifacts, benchmark output,
parallel same-JVM execution, and Gradle direct-mode integration coverage.
