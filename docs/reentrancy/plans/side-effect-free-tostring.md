# Side-Effect-Free toString() And Display Paths

This plan covers the redesign of `toString()` and the display helpers it calls
(`getValueString()`, `getDescription()`, `getPathString()`, `toString(...)`
overloads) across `org.xvm.asm`, `org.xvm.asm.constants`, `org.xvm.asm.op`,
`org.xvm.runtime` (and subpackages), and `org.xvm.javajit`. It is the backing
document for the "Side-effect-free `toString()`/display path redesign" row in
[../must-audit-backlog.md](../must-audit-backlog.md) (should-fix, near the end
of the should queue). The core deliverable is the exhaustive inventory below:
every problematic display site found, with file:line, the offending call
chain, a side-effect classification, and a per-site replacement.

## Problem Statement

Debugging the runtime is badly compromised because many `toString()` methods,
and the helpers they call, have side effects: they force lazy caches,
construct `TypeInfo`, resolve types, register constants in pools, and allocate
handles. Just stepping through in a debugger — or IntelliJ evaluating
`toString()` to render a variable in the Variables view — is enough to mutate
the state under investigation. That makes race investigations
nondeterministic: the act of observing changes the outcome. The branch owner
has repeatedly had to hand-patch `toString()` methods mid-debug just to avoid
disturbing state. Heisenbug-grade observation is unacceptable in a
reentrancy/ownership hardening effort.

The repo already contains the precedent this plan generalizes:
`OwnershipDiagnostics` default mode deliberately reports lazy cells as
"deferred" instead of forcing them
([../ownership-diagnostics.md](../ownership-diagnostics.md)): "It reports lazy
cells that are already computed and marks deferred cells as deferred. It does
not instantiate new templates, views, handles, or metadata just because the
dump was requested." The same contract must hold for every `toString()`,
because the debugger calls `toString()` without asking.

Concretely, one IntelliJ Variables-view refresh over a suspended runtime frame
today can:

- intern new constants into a `ConstantPool` (`ensureStringConstant`,
  `ensureImmutableTypeConstant`, `ensureClassConstant`, implicit-identity
  lookup) from the debugger thread, unsynchronized with the owning container;
- force `Lazy`/`Lazy.Owner` cells (`ClassComposition.f_fieldLayout`,
  `xEnum.f_enumInfo`) — the exact cells the ownership dump refuses to force;
- write resolution results back into constants
  (`TerminalTypeConstant.m_constId`, `Annotation.m_constClass`) and populate
  `TypeConstant` relation caches via `isA(...)`;
- join a `CompletableFuture` and allocate a new exception `ObjectHandle`
  inside the owning container (`xFuture.FutureHandle`);
- read ambient thread-local state (`ServiceContext.getCurrentContext()`) that
  belongs to a different fiber than the object being rendered, and swallow
  whatever breaks with `catch (Throwable ignore)`.

## Scope And Method

Enumerated by grep, then read: every `public String toString()` override, and
every `getValueString()`, `getDescription()`, `getPathString()` implementation
in the packages above, plus the display helpers those bodies call
(`Argument.toIdString`, `OpVar.getName`, `Frame.getStackTrace`/
`formatFrameDetails`, `TypeInfo.toString(boolean)`, and similar). Declared
display methods in scope: 217 `toString()` overrides, 68 `getValueString()`,
62 `getDescription()`, 2 `getPathString()`. Each implementation was read
together with its callees one level down; callees that are clearly
lazy-forcing entry points were followed deeper. Total display sites examined:
353 (plus roughly 40 shared helper callees).

Classification per site:

| Verdict | Meaning |
|---|---|
| PURE | Reads plain/cached fields, allocates only local strings, cannot throw, cannot mutate anything observable. |
| Problem category (below) | The side effect originates in this method or a helper it directly owns. |
| DELEG | The body is pure, but it delegates to a display method classified as impure elsewhere in this inventory; fixing the callee fixes this site with no local change. |
| SUSPECT | Purity not provable from one level of reading; the row says exactly what must be checked. |

## Side-Effect Categories

| Category | Definition | Entry points found in this codebase |
|---|---|---|
| LAZY | Forces lazy computation from a display path | `Lazy.get()`/`Lazy.Owner.get()`, `ClassComposition.fieldLayout()`/`getFieldInfo(...)`, `xEnum.enumInfo()`, `MethodInfo.ensureOptimizedMethodChain(...)`, `MethodBody.getMethodStructure()` (caches `m_structMethod`), `PropertyStructure.getPropertyAnnotations()` → `buildAnnotationArrays()`, `TerminalTypeConstant.ensureResolvedConstant()` (writes `m_constId`), `Source.normalize()` via `getLineCount()` |
| POOL | Registers or interns constants in a `ConstantPool` | `ConstantPool.register(...)` (via `isA` → `calculateRelation`), `ensureStringConstant`, `ensureImmutableTypeConstant`, `ensureTerminalTypeConstant`, `ensureClassConstant`, `ensurePackageConstant`, `getImplicitlyImportedIdentity(...)`, lazy canonical getters `typeFunction()`, `typeObject()`, `clzOp()`, `clzRO()`, `clzOverride()`, `clzInject()`, `TypeConstant.freeze()`, `ensureService()`, `removeAccess()` |
| RESOLVE | Resolves or normalizes types, or collapses unresolved constants | `resolveTypedefs()`, `TypeConstant.isA(...)`/`calculateRelation(...)` (writes relation cache), `Annotation.getAnnotationClass()` (writes `m_constClass`), `getDefiningConstant()` (throws when unresolved), `ObjectHandle.getType()` → `augmentType(...)` |
| AMBIENT | Reads ambient thread-local context or takes locks from a display path | `ServiceContext.getCurrentContext()` → `getCurrentFrame().localConstants()` (from `Argument.toIdString`, `OpVar.getName`) |
| GLOBAL | Mutates diagnostic/global static state | `BinaryAST.reportUnimplemented(...)` → static unsynchronized `ALREADY_DISPLAYED` set + stderr |
| ALLOC | Allocates owner-bearing runtime objects | `Utils.translate(container, e)` → `xException.makeHandle(...)`, `CompletableFuture.get()` join, `DeferredCallHandle` allocation on field-miss paths |
| EXPENSIVE | Pure but expensive (deep recursion, unbounded string building) | Lower severity, tracked separately: full op-array rendering, unbounded array/map/tuple element rendering, whole-TypeInfo member dumps |

## Inventory Of Problematic Sites

Paths are relative to `javatools/src/main/java`. Every row is intended to be
independently actionable: pick a row, apply the replacement, done. Rows marked
SUSPECT state what still needs checking. Sites verified PURE are not tabled
except where noted as model citizens.

### ASM Constants (org.xvm.asm.constants)

134 display implementations examined; 122 pure; 12 flagged. The two leaf
methods at the bottom of nearly every impure chain in the whole codebase live
here: `ParameterizedTypeConstant.getValueString()` and
`TerminalTypeConstant.getValueString()`.

| file:line | Class.method | What it calls | Category | Why dangerous | Replacement |
|---|---|---|---|---|---|
| org/xvm/asm/constants/ParameterizedTypeConstant.java:975 | `ParameterizedTypeConstant.getValueString` | `m_constType.isA(pool.typeFunction())`; `pool.extractFunctionParams/Returns(this)` | POOL (+LAZY, RESOLVE) | `pool.typeFunction()` lazily interns the canonical Function type (writes the pool's `m_typeFunction` cache); `isA` → `calculateRelation` (TypeConstant.java:5906) can call `poolLeft.register(this)` (5915), resolve typedefs, and write the type's relation cache (`ensureRelationMap()`, 5936). Rendering a type variable mutates two pools' intern state and relation caches mid-race. | Render `m_constType.getValueString() + '<' + params + '>'` unconditionally from fields; move the `function R(P)` pretty form to `describeForced()`. See before/after below. |
| org/xvm/asm/constants/TerminalTypeConstant.java:2048 | `TerminalTypeConstant.getValueString` | `ensureResolvedConstant()` | LAZY | `ensureResolvedConstant()` writes `m_constId = resolved`, collapsing an unresolved constant at display time; viewing a variable during compilation advances name-resolution state. | Read the resolution without storing: `var r = m_constId.resolve(); return r == null ? "<unresolved:" + m_constId + '>' : r.getValueString();` — keep the write in non-display callers. |
| org/xvm/asm/constants/TypeInfoReal.java:2164/2169 | `TypeInfoReal.toString` / `toString(boolean)` | per-member `resolveNestedIdentity(pool, null)`; `fRuntime=true`: `method.ensureOptimizedMethodChain(this)`, `MethodInfo.create(...)`; renders every `MethodInfo`/`PropertyInfo` | LAZY (+ALLOC, EXPENSIVE) | `toString(true)` computes and caches optimized chains and allocates fresh `MethodInfo`s; even `toString(false)` (the debugger default via `TypeInfo.toString()`) renders every member, triggering the `MethodInfo.toString` POOL chain below, and memoizes `IdentityConstant.m_canonicalNid`. One Variables-view render warms method chains across the whole type. | `toString()` prints the header only (type, progress, format, member counts). Full member dump becomes `describeForced(boolean fRuntime)`; the `fRuntime` branch renders `m_aBodyResolved` when already present, else `[chain deferred]`. See before/after below. |
| org/xvm/asm/constants/MethodInfo.java:1640 | `MethodInfo.toString` | `isOp()` → `MethodBody.findAnnotation(pool().clzOp())`, `getMethodStructure()`, `extendsClass(...)` | POOL (+LAZY) | `pool().clzOp()` interns the `Op` class identity via `getImplicitlyImportedIdentity` (ConstantPool.java:1447: `ensurePackageConstant`/`ensureClassConstant` + `f_implicits` write); `getMethodStructure()` (MethodBody.java:215) caches `m_structMethod` and forces `IdentityConstant.getComponent()`. | Render `getSignature().getValueString()` plus per-body lines only; emit `@Op` only from already-computed annotation state; annotation-classified rendering moves to `describeForced()`. |
| org/xvm/asm/constants/PropertyBody.java:517 (fragment 546-560) | `PropertyBody.toString` | `isInjected()`, `isExplicitAbstract()`, `isExplicitOverride()`, `isExplicitReadOnly()` | POOL (+LAZY) | `isExplicitAbstract()` (441) → `TypeInfo.containsAnnotation(prop.getPropertyAnnotations(), "Abstract")`: forces the lazy `buildAnnotationArrays()` split on the structure, then interns package/class constants via `getImplicitlyImportedIdentity`. The other three helpers hit `pool.clzOverride()/clzRO()/clzInject()` — same interning. | Print field-backed flags only (`m_fRO`, `m_fRW`, `m_fConstant`, `m_fField`, `m_fCustom`, `m_impl`, `m_infoFormal != null`); the four annotation-derived suffixes move to `describeForced()` or gate on already-built annotation arrays. |
| org/xvm/asm/constants/ParamInfo.java:108 | `ParamInfo.toString` | `typeConstraint.getConstantPool().typeObject()`; `typeConstraint.equals(...)`; `typeConstraint.isTuple()` | POOL (+LAZY) | `typeObject()` lazily interns the canonical Object type; `isTuple()` on a typedef'd terminal calls `ensureResolvedConstant()` (`m_constId` write) and `getReferredToType()`. Rendering one type-parameter row interns into the pool. | Suppress the `extends` clause by comparing the constraint's already-present value string against `"Object"`; never call `typeObject()`/`isTuple()` here. |
| org/xvm/asm/constants/TypeInfo.java:~740 | `TypeInfo.containsAnnotation` (static helper reached from `PropertyBody.toString`) | `annotations[0].getConstantPool().getImplicitlyImportedIdentity(sName)` | POOL | Shared helper that interns package/class constants and writes the pool's `f_implicits` map on first use of each name; any display path that classifies annotations through it mutates the pool. | Keep for compiler use; ban from display paths. Display code compares annotation class names as strings instead of interned identity equality. |
| org/xvm/asm/constants/ArrayConstant.java:250 | `ArrayConstant.getValueString` | `toString()` on every element constant | EXPENSIVE | Unbounded element rendering, recursing into element description chains; large constant arrays stall the Variables view. | Cap: first N elements + `", … (" + cConsts + " total)"`. |
| org/xvm/asm/constants/MapConstant.java:300 | `MapConstant.getValueString` | `toString()` on every key/value constant | EXPENSIVE | Same unbounded rendering. | Same cap pattern. |
| org/xvm/asm/constants/HandleConstant.java:77 | `HandleConstant.getValueString`/`getDescription` | `m_hValue.toString()` (runtime `ObjectHandle`) | SUSPECT (ALLOC-transitive) | Escapes the ASM plane into runtime handle rendering, which builds compositions/types (see Runtime Core). A "constant" render can execute runtime display logic. | Render `"Handle(" + m_hValue.getClass().getSimpleName() + ")"` until the runtime handle contract lands; then delegate to the pure handle summary. |
| org/xvm/asm/constants/ExpressionConstant.java:69 | `ExpressionConstant.getValueString`/`getDescription` | `m_expr.toString()` (`org.xvm.compiler.ast.Expression`) | SUSPECT | Delegates to compiler AST rendering outside this scope; purity unverified, output can be very large. Needs: check `Expression.toString()`/dump helpers for type-resolution calls. | Render node class + source position; full AST dump only via explicit call. |
| org/xvm/asm/constants/TypeConstant.java:8174 | `TypeConstant.Origin.toString` | outer `TypeConstant.toString()` → `getDescription()` → `getValueString()` | SUSPECT (transitive) | Pure locally; funnels into whatever the concrete type's `getValueString()` does, including both leaf offenders above. | No local change once the leaves are pure; re-verify after leaf migration. |

Benign-memoization notes: `IdentityConstant.getCanonicalNestedIdentity`
(IdentityConstant.java:284) writes `m_canonicalNid` when reached from
`TypeInfoReal.toString`. `IdentityConstant.getPathString()`
(IdentityConstant.java:111) is pure recursion over resolved parents — the
model for identity-first rendering. The existing model citizens are
`UnresolvedNameConstant`, `UnresolvedTypeConstant`, and
`DeferredValueConstant`: their display methods branch on already-resolved
state and print the raw name otherwise.

### ASM Structures And BinaryAST (org.xvm.asm, org.xvm.asm.ast)

80 display implementations examined; ~55 pure (most `ast` renderers are pure
but recursive). The two funnels live here: `XvmStructure.toString()` (line
579) and `Constant.toString()` (line 645) both dispatch to virtual
`getDescription()`; the purity contract must be imposed at those funnels and
honored by every override.

| file:line | Class.method | What it calls | Category | Why dangerous | Replacement |
|---|---|---|---|---|---|
| org/xvm/asm/MethodStructure.java:2131 (chain 2170 → 2888 → 2963 → 2986) | `MethodStructure.getDescription` | `m_source.getLineCount()` → `Source.normalize()` → `pool.ensureStringConstant(...)` per source line; writes `m_aconstSrc`/`m_anIndents` | POOL | Rendering a method in the debugger interns one StringConstant per source line into the pool and mutates the Source object — pool growth plus unsynchronized field publication under parallel observation. | Report line count only from already-normalized state; print `line-count=<deferred>` when `m_aconstSrc == null`. See before/after below. |
| org/xvm/asm/Annotation.java:296, 356 (root cause 119-140) | `Annotation.getValueString` and `Annotation.getDescription` | `getAnnotationClass()` — `constClass.resolve()`, `getReferredToType()`, `isSingleUnderlyingClass(true)`, then writes `m_constClass = resolved` | RESOLVE | Display resolves an unresolved annotation class and caches the write-back unsynchronized; observing an annotation mid-compilation changes which constant identity later code sees. | Render from raw `m_constClass`: `m_constClass.containsUnresolved() ? "<unresolved>" : m_constClass.getValueString()`. |
| org/xvm/asm/ast/BinaryAST.java:70 (271-277) | `BinaryAST.toString` (base, hit by any node lacking an override) | `reportUnimplemented(...)` → `ALREADY_DISPLAYED.add(msg)` (plain static `HashSet`) + `System.err.println` | GLOBAL | Debugger rendering mutates a process-global unsynchronized HashSet (corruptible under concurrent add) and writes stderr; nondeterministic across runs. | Return `nodeType().name()` only; move the TODO nag to an explicit verifier path; if kept anywhere, use `ConcurrentHashMap.newKeySet()`. |
| org/xvm/asm/Component.java:3194 | `Component.Contribution.toString` | `m_typeContrib.resolveTypedefs().getDescription()` (3244); `constMixin.getDefiningConstant()` (3215); `constParam.getValueString()` | RESOLVE | `resolveTypedefs()` can build new TypeConstants (pool interning) during display; `getDefiningConstant()` throws on unresolved/relational types, so the Variables view can blow up mid-render. | Render `m_typeContrib.getValueString()` verbatim without `resolveTypedefs()`; guard `getDefiningConstant()` behind `isSingleDefiningConstant()`. |
| org/xvm/asm/MethodStructure.java:2495 | `MethodStructure.Code.toString` | `op.toString()` for every op in `m_listOps`/`m_aop` | EXPENSIVE (+DELEG to op display) | Deliberately non-forcing (prints "native" rather than deserializing — good), but renders the whole op array, and per-op toStrings are AMBIENT (see Ops). Also mislabels not-yet-deserialized code (`m_abOps != null`, `m_aop == null`) as "native". | Keep non-forcing; print `<not deserialized>` when `f_method.m_abOps != null && m_aop == null`; optionally cap rendered ops. |
| org/xvm/asm/CompositeComponent.java:480 | `CompositeComponent.toString` | full `toString()` of every sibling | EXPENSIVE (+DELEG) | Multiplies whatever cost/side effects sibling descriptions have. | Render sibling identity paths only (name + format). |
| org/xvm/asm/ClassStructure.java:3376 | `ClassStructure.getDescription` | `constType.getValueString()` twice per type param (comparison against `"ecstasy:Object"` + append); `m_constPath.getValueString()` | EXPENSIVE (+DELEG) | Doubled per-param rendering cost; inherits TypeConstant display effects per param. | Render each param once into a local; compare on the raw constant, not a freshly built string. |
| org/xvm/asm/ast/SwitchAST.java:332 | `SwitchAST.toString` | recursive `body.toString()` per case + case-constant display | EXPENSIVE (+DELEG) | Deep recursive rendering of whole case bodies from a hover. | Summarize (`switch(<cond>) {<n> cases}`); full render in `dump()`. |
| org/xvm/asm/XvmStructure.java:579 | `XvmStructure.toString` (funnel) | virtual `getDescription()` | DELEG (funnel) | Every structure's debugger render runs whatever its `getDescription()` does; the contract must be documented and enforced here. | Javadoc the purity contract on `toString()`/`getDescription()`; forcing renderings move to the existing `dump()` plane. |
| org/xvm/asm/Constant.java:645 | `Constant.toString` (funnel) | `getFormat().name()` + virtual `getDescription()` | DELEG (funnel) | Same funnel for all constants. | Same contract point. |
| org/xvm/asm/Component.java:3377 | `Component.Injection.toString` | `type.toString()` (TypeConstant) | DELEG | Inherits TypeConstant display behavior. | No local change once constant display is pure. |
| org/xvm/asm/PropertyStructure.java:766 | `PropertyStructure.getDescription` | `getIdentityConstant().getValueString()`, type rendering | DELEG | Same. | Same. |
| org/xvm/asm/TypedefStructure.java:89 | `TypedefStructure.getDescription` | `m_type` rendering | DELEG | Same. | Same. |
| org/xvm/asm/Parameter.java:429 | `Parameter.getDescription` | `m_constType.getValueString()`, `m_constDefault.getValueString()` | DELEG | Same. | Same. |
| org/xvm/asm/Register.java:459 | `Register.toString` | `m_type.getValueString()` | DELEG | Same; also reached from `OpVar.toString()`. | Same; op paths should prefer `getIdString()`. |
| org/xvm/asm/InjectionKey.java:46 | `InjectionKey.toString` | `f_type.getValueString()` | DELEG | Same. | Same. |
| org/xvm/asm/SimulatedLinkerContext.java:183 | `SimulatedLinkerContext.toString` | `cond` (ConditionalConstant) rendering | DELEG | Same. | Same. |
| org/xvm/asm/ClassStructure.java:2902 | anonymous `Op.toString` ("initRef:") | `idField` (PropertyConstant) rendering | DELEG | Same. | Same. |
| org/xvm/asm/ErrorListener.java:345 | `ErrorInfo.toString` | `constId` rendering + source location getters | DELEG (pure locally) | Same. | Same. |
| org/xvm/asm/ast/ConstantExprAST.java:84; ConvertExprAST.java:118; NarrowedExprAST.java:31; NewExprAST.java:179; PropertyExprAST.java:95; RegAllocAST.java:146,149 | ast expression toStrings | `getValueString()` on constants/types | DELEG | Inherit constant display behavior. | No local change once constant display is pure. |
| org/xvm/asm/ConstantPool.java:3189 | `ConstantPool.getDescription` | `f_listConst.size()`, `m_fRecurseReg` | SUSPECT (racy read, no mutation) | Unsynchronized reads of concurrently mutated pool state — stale/torn values, but nothing written. | Acceptable; document snapshot semantics. |
| org/xvm/asm/ErrorList.java:145 | `ErrorList.toString` | `f_list.get(f_list.size()-1)` | SUSPECT (racy read) | Can throw IndexOutOfBounds if the list shrinks concurrently mid-render. | Copy size/last into locals defensively. |
| org/xvm/asm/MethodStructure.java:2131 | `MethodStructure.getDescription` (second issue) | `id.getValueString()`, `id.getSignature()` (guarded by `isNascent()`) | DELEG | MethodConstant display builds signature text; the nascent guard is already correct. | Covered by constants-area fixes. |

In-tree models to preserve: `MethodStructure.dump()` (line 2176) is where
forcing already lives — it explicitly calls `ensureCode().toString()`; and
`Code.toString()` deliberately does not deserialize ops. That is exactly the
pure-toString/forced-dump split this plan generalizes.

### Ops (org.xvm.asm Op bases, org.xvm.asm.op)

48 display sites examined; 5 toString bodies and 5 helpers pure. The entire
area has one structural root cause: all op argument rendering funnels through
`Argument.toIdString(Argument, int)` (plus its sibling `OpVar.getName`), which
reads ambient thread state. Fixing the two roots plus the `getValueString()`
delegation fixes all 33 dependent rows; they are listed anyway so no row needs
re-investigation.

| file:line | Class.method | What it calls | Category | Why dangerous | Replacement |
|---|---|---|---|---|---|
| org/xvm/asm/Argument.java:40 | `Argument.toIdString` (root cause 1) | `Constant.getValueString()`; `ServiceContext.getCurrentContext()` (ThreadLocal); `context.getCurrentFrame().localConstants()[convertId(nArg)]`; swallows `Throwable` | AMBIENT | The debugger evaluating any op toString reads the suspended thread's current fiber/frame — often unrelated to the op — indexing the wrong constant array (AIOOBE silently swallowed) or printing misleading text; the resolved constant's `getValueString()` can force resolution; `catch (Throwable ignore)` hides real corruption. | Pure form with `const:#n` markers, no ambient lookup; explicit `toIdString(Frame, Argument, int)` overload for forced rendering from frame dumps. See before/after below. |
| org/xvm/asm/OpVar.java:103 | `OpVar.getName(aconst, constName, nNameId)` (root cause 2) | same ambient chain; swallows `Throwable` | AMBIENT | Identical to root cause 1; reached via `getName(null)` from every named-var op toString. | Return `name:#n` marker when `aconst == null`; keep an explicit `getName(Frame)` overload for forced paths. |
| org/xvm/asm/Op.java:445 | `Op.build(...)` | `toString()` inside thrown `UnsupportedOperationException` | AMBIENT (indirect) | Exception construction on the JIT path triggers full ambient rendering. | Acceptable once toString is pure; note only. |
| org/xvm/asm/OpCallable.java:142 | `OpCallable.toString` | `getFunctionString`/`getParamsString`/`getReturnsString` → `toIdString` | AMBIENT (via root 1) | Inherits root cause 1 on every CALL_* rendering. | Inherits root-site fix. |
| org/xvm/asm/OpCondJump.java:332 | `OpCondJump.toString` | `toIdString` ×3 (incl. `m_typeCommon` TypeConstant) | AMBIENT (via root 1) | Compare-jumps also render the common type via delegated forcing. | Inherits fix; type renders cached-only or `<unresolved>`. |
| org/xvm/asm/OpCondJump.java:311 | `OpCondJump.getArg2Desc` | `assert hasSecondArgument()` + `toIdString` | AMBIENT (via root 1) | The assert can throw under `-ea` from a display path. | Drop the assert or return a marker. |
| org/xvm/asm/OpGeneral.java:198 | `OpGeneral.toString` | `toIdString` ×3 | AMBIENT (via root 1) | Inherits root cause 1. | Inherits fix. |
| org/xvm/asm/OpIndex.java:216 | `OpIndex.toString` | `toIdString` ×3 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/OpInPlace.java:203 | `OpInPlace.toString` | `getTargetString`/`getReturnString` → `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/OpInPlaceAssign.java:239 | `OpInPlaceAssign.toString` | `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/OpInvocable.java:263 | `OpInvocable.toString` | `getTargetString`/`getMethodString`/`getParamsString`/`getReturnsString` → `toIdString` | AMBIENT (via root 1) | Every NVOK_* rendering. | Inherits fix. |
| org/xvm/asm/OpMove.java:71 | `OpMove.toString` | `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/OpProperty.java:55 | `OpProperty.toString` | `toIdString(m_idProp, m_nPropId)` | AMBIENT (via root 1) | Same, plus PropertyConstant `getValueString()` delegation. | Inherits fix. |
| org/xvm/asm/OpPropInPlace.java:170 | `OpPropInPlace.toString` | `toIdString` ×2; bug: renders `m_nTarget` twice, never `m_nRetValue` | AMBIENT (via root 1) | Wrong-id output — direct evidence the display code is unaudited. | Inherits fix; correct the second index to `m_nRetValue`. |
| org/xvm/asm/OpPropInPlaceAssign.java:107 | `OpPropInPlaceAssign.toString` | `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/OpTest.java:271 | `OpTest.toString` | `toIdString` ×4 (incl. `m_typeCommon`) | AMBIENT (via root 1) | Same plus type delegation. | Inherits fix. |
| org/xvm/asm/OpVar.java:209 | `OpVar.toString` | `getName(null)` (ambient); `toIdString(null, m_nType)` (null arg + constant id — always takes the ambient branch for deserialized ops); `m_reg` → `Register.toString()` → `m_type.getValueString()` | AMBIENT (via roots 1+2) | Worst base-class case: three distinct impure paths in one body. | Inherits both root fixes; render the type id as `type:#n`; render `m_reg` via `getIdString()`, not full `toString()`. |
| org/xvm/asm/op/Assert.java:169 | `Assert.toString` | `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/AssertM.java:83 | `AssertM.toString` | `toIdString(m_constMsg, m_nMsgConstId)` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/AssertV.java:212 | `AssertV.toString` | `toIdString` per value | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/GP_DivRem.java:138 | `GP_DivRem.toString` | `toIdString` ×4 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/JumpInt.java:182 | `JumpInt.toString` | `toIdString` + `getLabelDesc` (pure) | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/JumpVal.java:368 | `JumpVal.appendArgDescription` | `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/JumpVal_N.java:531 | `JumpVal_N.appendArgDescription` | `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/L_Get.java:106 | `L_Get.toString` | super + `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/L_Set.java:100 | `L_Set.toString` | super + `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/Label.java:77 | `Label.toString` | `getNextOp().toString()` | SUSPECT (chain) | Pure itself; recurses into the appended op's impure toString. | Fine once ops are pure. |
| org/xvm/asm/op/MoveCast.java:115 | `MoveCast.toString` | super + `toIdString(m_typeTo, m_nToType)` | AMBIENT (via root 1) | TypeConstant render → `ensureResolvedConstant` delegation. | Inherits fix. |
| org/xvm/asm/op/MoveThis.java:155 | `MoveThis.toString` | `toIdString`; `default -> throw new IllegalStateException()` in the access switch | AMBIENT (via root 1) | A toString that can throw kills the Variables-view rendering of the whole op array. | Replace the throw with a `"?"` marker; inherits fix. |
| org/xvm/asm/op/OpSwitch.java:252 | `OpSwitch.toString` | `appendArgDescription` + `toIdString` per case + label descs | AMBIENT (via root 1) | One toString renders every case constant — N delegated `getValueString()` calls. | Inherits fix; consider capping case rendering. |
| org/xvm/asm/op/P_Get.java:124 | `P_Get.toString` | super + `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/P_Ref.java:125 | `P_Ref.toString` | super + `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/P_Set.java:111 | `P_Set.toString` | super + `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/P_Var.java:125 | `P_Var.toString` | super + `toIdString` ×2 | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/Redundant.java:39 | `Redundant.toString` | `getNextOp().toString()` | SUSPECT (chain) | Pure itself; recurses into the discarded op. | Fine once ops are pure. |
| org/xvm/asm/op/Return_1.java:90 | `Return_1.toString` | `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/Return_N.java:122 | `Return_N.toString` | `toIdString` per return | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/Throw.java:92 | `Throw.toString` | `toIdString` | AMBIENT (via root 1) | Same. | Inherits fix. |
| org/xvm/asm/op/Var_I.java:101 | `Var_I.toString` | super (`OpVar`, all three impure paths) + `toIdString` | AMBIENT (via roots 1+2) | Same. | Inherits fix. |

Pure for the record: `Op.toString` (Op.java:451, `toName(getOpCode())`),
`Op.ConstantRegistry.toString` (Op.java:1008), `OpJump.toString`,
`LoopEnd.toString`, `Nop.toString`, `Op.toName`, `Register.getIdString(int)`,
`OpJump.getLabelDesc`, `OpCondJump.getLabelDesc`.

### Runtime Core (org.xvm.runtime)

50 display sites examined (44 toString overrides + 6 helpers); 24 pure; 26
flagged. The structural root cause is `ObjectHandle.toString()`
(ObjectHandle.java:344), inherited by nearly every handle — every Variables
row of every handle runs it.

| file:line | Class.method | What it calls | Category | Why dangerous | Replacement |
|---|---|---|---|---|---|
| org/xvm/runtime/ObjectHandle.java:344 | `ObjectHandle.toString` (base, inherited by nearly all handles) | `getComposition()`; `clz.getType().isImmutable()`; string concat of `clz` → `ClassComposition.toString` → `TypeConstant.getValueString()` | RESOLVE | For parameterized/function types `getValueString()` runs `isA(pool.typeFunction())` (relation-cache write, possible `pool.register`) and function-shape extraction; `isImmutable()` recurses for relational types. Every debugger row of any handle mutates type-system caches mid-race. | Render `m_fMutable` plus a pure composition label; drop the `getType().isImmutable()` refinement. Type rendering becomes safe transitively once the constants-leaf wave lands. See before/after below. |
| org/xvm/runtime/ObjectHandle.java:754 | `ExceptionHandle.toString` | `getField(null, "text")` → `getComposition().getFieldInfo(...)` → `fieldLayout()` → `f_fieldLayout.get(...)` (Lazy force → `buildFieldLayout`) | LAZY | Forces the `ClassComposition.f_fieldLayout` Lazy cell — the very cell OwnershipDiagnostics default mode refuses to force — on the debugger thread with no owner pool bound; the miss path allocates via `xException.makeHandle(null frame)`. | Read the text field only when the layout is already computed (`Lazy.isComputed()` peek), else `<text deferred>`. See before/after below. |
| org/xvm/runtime/ObjectHandle.java:777 | `ExceptionHandle.WrapperException.toString` | `getExceptionHandle().toString()` | LAZY (via above) | Java itself calls `Throwable.toString()` when printing stack traces, so the forcing triggers on any exception print, not just in the debugger. | Delegates to the pure ExceptionHandle rendering; forced message only in explicit reporting code. |
| org/xvm/runtime/ObjectHandle.java:820 | `JavaLong.toString` | `super.toString()`; `m_clazz.getTemplate()` | RESOLVE (via base) | A boxed long in the Variables view still mutates type caches through the base. | Fixed by the base fix; the value part is already pure. |
| org/xvm/runtime/ObjectHandle.java:844 | `ConstantHandle.toString` | `f_constant.toString()` → `Constant.getDescription()` | SUSPECT | Purity is constant-subclass-specific; some `getDescription()` bodies resolve/register (see ASM Constants). | Print format name + cached value string only; resolved by the constants wave. |
| org/xvm/runtime/ObjectHandle.java:901 | `DeferredCallHandle.toString` | `f_frameNext` → `Frame.toString` → `formatFrameDetails` → `calculateLineNumber` (O(iPC)) + `aOp[iPC]` op toString | SUSPECT | Op toStrings are AMBIENT; frame fields read racily while the fiber runs. | Print method id path + pc only. |
| org/xvm/runtime/ObjectHandle.java:997 | `DeferredSingletonHandle.toString` | `f_constSingleton.toString()` | SUSPECT | `SingletonConstant.getDescription` purity unverified; singleton state is a lifecycle state machine. | Print the identity path from the already-resolved name only. |
| org/xvm/runtime/ObjectHandle.java:1057 | `DeferredArrayHandle.toString` | `getType()` → `augmentType` → `freeze()` → `ConstantPool.ensureImmutableTypeConstant`; possibly `ensureService()` | POOL | Interns a new immutable/service TypeConstant into the pool from the debugger thread, unsynchronized with the owning container's warmup. | Print `f_clzArray`'s pure label directly; never `getType()` from display. |
| org/xvm/runtime/ObjectHandle.java:1204 | `InitializingHandle.toString` | `getInitialized()` (plain read) then delegate `hConst.toString()` | RESOLVE (via delegate) | The `<initializing>` marker branch is the correct pattern; the non-null branch inherits base handle impurity. | Keep the marker; the delegate becomes safe when the base is pure. |
| org/xvm/runtime/ClassComposition.java:757 | `ClassComposition.toString` | `f_typeRevealed.getValueString()` | RESOLVE | Function/parameterized types compute `isA()` (relation-cache write) and pool function-shape extraction; compositions are printed by OwnershipDiagnostics, handles, and logs. | Cache the rendered label at construction (the type is final), or print the terminal class name + `<params deferred>`; becomes pure transitively after the constants-leaf wave. |
| org/xvm/runtime/PropertyComposition.java:321 | `PropertyComposition.toString` | `f_clzParent` (ClassComposition.toString) + `f_infoProp.getName()` | RESOLVE (via callee) | Inherits ClassComposition impurity. | Same fix; the property-name part is pure. |
| org/xvm/runtime/ProxyComposition.java:109 | `ProxyComposition.toString` | `f_clzOrigin.toString()` + `f_typeProxy.getValueString()` | RESOLVE | Same TypeConstant rendering impurity, twice. | Pure labels for both sides. |
| org/xvm/runtime/CallChain.java:749 | `CallChain.toString` | `f_aMethods[0].getIdentity().getSignature().getValueString()` | RESOLVE (via callee) | SignatureConstant rendering renders param/return TypeConstants — same relation-cache mutation. | Print the method identity path (pure `getPathString`-style) + chain depth. |
| org/xvm/runtime/ClassTemplate.java:307 | `ClassTemplate.toString` | `f_struct.toString()` → `ClassStructure.getDescription()` | RESOLVE (via callee) | Renders every type param via `getValueString`; templates print in ownership dumps and logs. | Print `f_sName` (final, precomputed path string on the template). |
| org/xvm/runtime/Fiber.java:500 | `Fiber.reportWaiting` (helper) | iterates `m_oPendingRequests`; renders other fibers | SUSPECT (racy) | Unsynchronized iteration of a concurrently mutated map — ConcurrentModificationException/torn view while debugging a live service. | Snapshot the map reference, copy entries defensively, tolerate CME with `<concurrently modified>`. |
| org/xvm/runtime/FiberQueue.java:116 | `FiberQueue.reportStatus` (helper) | ring-buffer scan + `Frame.toString` per queued frame | SUSPECT (racy) | Racy scan plus impure frame/op rendering. | Render sizes + fiber ids/status only. |
| org/xvm/runtime/Frame.java:2021/2178/2227 | `Frame.getStackTrace` / `getStackTraceArray` / `formatFrameDetails` (helpers) | live `f_framePrev`/fiber traversal; `calculateLineNumber` (O(iPC) op scan); `aOp[iPC]` op toString | SUSPECT | Op toString impurity plus racy traversal of a running fiber's frame chain (`m_iPC`, `f_framePrev` unsynchronized). | This IS the forced variant — keep it explicit, but make per-op rendering pure and snapshot the pc once. |
| org/xvm/runtime/Frame.java:2050 | `Frame.StackFrame.toString` | `frame.f_aOp[0]` op toString; `calculateLineNumber`; `getSourceFileName` | SUSPECT | Same op dependency; called by DebugConsole per rendered frame. | Same as above. |
| org/xvm/runtime/Frame.java:2268 | `Frame.toString` | `formatFrameDetails(...)` | SUSPECT | Frames render in the Variables view constantly; inherits all of the above; racy `m_iPC`. | Pure short form: method path + pc; line-number/op text moves to the explicit trace helper. |
| org/xvm/runtime/Frame.java:2543 | `Frame.VarInfo.toString` | `getName()` — memoizes `m_sVarName` (unsynchronized write) | LAZY (memoization) | Display path writes an instance field; first observation changes object state. | Precompute the name eagerly, or read without caching from toString. |
| org/xvm/runtime/ServiceContext.java:1261 | anonymous `opCall.toString` (completeSendInvoke1) | `hFunction.toString()` | SUSPECT | FunctionHandle rendering (templates area) reaches the impure base handle toString. | Print the function name only. |
| org/xvm/runtime/ServiceContext.java:1319 | anonymous `opCall.toString` (completeSendInvokeN) | `hFunction.toString()` | SUSPECT | Same. | Same. |
| org/xvm/runtime/ServiceContext.java:1560 | `ServiceContext.toString` | appends `m_frameCurrent` → `Frame.toString` | SUSPECT | Renders another thread's live current frame; racy read plus frame/op impurity — showing a service list touches every service's running frame. | Name + id + synchronicity only; frame detail in an explicit dump. |
| org/xvm/runtime/ServiceContext.java:1886 | `OpRequest.toString` | `f_op.toString()` | SUSPECT | Delegates to arbitrary op toString (AMBIENT area). | Safe once op toStrings are pure; until then the op class simple name. |
| org/xvm/runtime/ServiceContext.java:1946 | anonymous `opCall.toString` (CallLaterRequest) | `f_hFunction.getName()` | SUSPECT | `FunctionHandle.getName()` purity — verified as plain reads in the templates pass, but can NPE when the method is null. | Null-guard; otherwise fine. |
| org/xvm/runtime/WeakCallback.java:41 | `WeakCallback.toString` | `get()` (weak deref); `context.getCallbackMap().get(id)`; `callback.functionHandle().toString()` | SUSPECT | Reads the live callback registry racily and delegates to handle rendering. | Print callback id + context name; keep the existing `Empty` fallback. |

Pure for the record: `DeferredPropertyHandle` (957), `TransientId` (1079),
`NativeFutureHandle` (1222), `ObjectHandle.DEFAULT` (1238),
`ClassComposition.FieldInfo` (866), `NativeTemplateRef` (43),
`ClassTemplate` anonymous "CheckAndYield" (977), `Container` (854),
`NativeContainer` (893), `Fiber` (552 — racy `m_status` read, benign),
`FiberQueue` (325), `Frame` deferred-action toStrings (2740/2758),
`ServiceContext` message toStrings (773/1178/1205/1439/1499/1518/1775/1786),
`Utils` (942 — renders a ClassConstant, verify with constants wave; 1916),
`DebugConsole.BreakPoint` (2176). `InitializingHandle`'s `<initializing>`
branch is the marker pattern this plan standardizes.

### Runtime Templates (org.xvm.runtime.template)

34 toString sites examined (plus ~15 helper callees); 21 pure locally; 13
flagged (one row covers 11 sites whose only impurity is the inherited
`ObjectHandle.toString()` base).

| file:line | Class.method | What it calls | Category | Why dangerous | Replacement |
|---|---|---|---|---|---|
| org/xvm/runtime/template/annotations/xFuture.java:881 | `xFuture.FutureHandle.toString` | `getFuture().isDone()`; `toSafeString()` → `getFuture().get()` (join) → on throw `Utils.translate(getComposition().getContainer(), e)` → `xException.makeHandle(...)`; `m_clazz` concat | ALLOC | Rendering a failed/cancelled future allocates a new xException ObjectHandle inside the owning container and consumes the completion-exception path; `FutureTupleHandle.getFuture()` additionally scans element futures; the referent value handle is rendered recursively. | State-only rendering from `isDone()`/`isCompletedExceptionally()`/`getNow(null)`; value/exception rendering moves to `describeForced(frame)`. See before/after below. |
| org/xvm/runtime/template/xEnum.java:421 | `xEnum.EnumHandle.toString` | `getName()` → `getTemplate().getNameByOrdinal(m_index)` → `enumInfo()` → `f_enumInfo.get(this)` (`Lazy.Owner.get`) | LAZY | Forces the template's lazy `EnumInfo` (walks class-structure children, builds names and the enum handle list) — exactly the deferred-cell class OwnershipDiagnostics refuses to force; stepping over an EnumHandle warms owner-local metadata mid-race. | `f_enumInfo.isComputed() ? name : "<enum ordinal=" + m_index + '>'`; forced name lookup in `describeForced()`. |
| org/xvm/runtime/template/_native/reflect/xRTType.java:1922 | `xRTType.TypeHandle.toString` | `getDataType()` → `getType()` → `augmentType` → `TypeConstant.freeze()` → `pool.ensureImmutableTypeConstant(...)` (handle is immutable), possibly `ensureService()`; then `getValueString()` | POOL | Rendering a Type variable interns a new immutable-type constant into the pool, then runs the type value-string renderer; pool registration under observation perturbs exactly the pool races under investigation. | Render from the raw composition type without `augmentType`; forced form in `describeForced()`. |
| org/xvm/runtime/template/reflect/xClass.java:478 | `xClass.ClassHandle.toString` | `getType()` (→ `augmentType`/`freeze()` → pool intern) `.getParamType(0)` + TypeConstant rendering | POOL | Same pool-interning path as TypeHandle. | Cache the display name at construction, or render the composition type without `augmentType`. |
| org/xvm/runtime/template/Proxy.java:454 | `Proxy.ProxyHandle.toString` | `f_hTarget.toString()` recursion; strict mode: `getType().getValueString()` | POOL | Pool intern via `augmentType` plus arbitrary recursion into the proxied handle's toString. | `"Proxy -> " + f_hTarget.getClass().getSimpleName()`; strict-type rendering in `describeForced()`. |
| org/xvm/runtime/template/_native/reflect/xRTMethod.java:342 | `xRTMethod.MethodHandle.toString` | `"Method: " + getMethod()` → `MethodStructure.toString()` → `getDescription()` | SUSPECT (transitive) | Delegates to the MethodStructure description renderer (POOL via source normalization, see ASM Structures). | Render `f_idMethod.getPathString()` only; full structure render in `describeForced()`. |
| org/xvm/runtime/template/_native/reflect/xRTSignature.java:408 | `xRTSignature.SignatureHandle.toString` | `"Signature: " + getMethod()` → `MethodStructure.toString()` | SUSPECT (transitive) | Same chain. | Render the method name via plain reads instead of the whole MethodStructure. |
| org/xvm/runtime/template/annotations/xAtomic.java:157 | `xAtomic.AtomicHandle.toString` | `m_clazz` (ClassComposition.toString) + `f_atomic.get()` referent recursion | SUSPECT (transitive) | Recurses into an arbitrary referent toString (may hit FutureHandle-class offenders). | `"(Atomic) "` + referent class simple name or `<unassigned>`; deep render in `describeForced()`. |
| org/xvm/runtime/template/reflect/xRef.java:1089 | `xRef.RefHandle.toString` | `super.toString()` (base); referent recursion in REF_REFERENT/REF_REF/REF_ARRAY branches | SUSPECT (transitive) | Arbitrary recursion into referent toString; `getReferent()` overrides could compute (base branch verified as plain reads). | Print referent class name + identity, not `referent.toString()`; keep the existing `<unassigned>` marker. |
| org/xvm/runtime/template/collections/xTuple.java:750 | `xTuple.TupleHandle.toString` | `Arrays.toString(m_ahValue)` — recursion into every element handle | EXPENSIVE (+transitive) | Unbounded element rendering; pulls in any impure element toString; large tuples build large strings mid-debug. | `"Tuple[" + m_ahValue.length + "]"`; element dump in `describeForced()`. |
| org/xvm/runtime/template/text/xString.java:391 | `xString.StringHandle.toString` | `getStringValue()` — memoizes `m_sValue = new String(m_achValue)` (unsynchronized lazy write) | GLOBAL (low) | Display path writes handle state; idempotent value race, but still a mutation performed by the observer. | Either document as a tolerated idempotent cache or render `new String(m_achValue)` without storing. |
| org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java:221 | `xRTTypeTemplate.TypeTemplateHandle.toString` | `super.toString()` + `f_type` → TypeConstant rendering | SUSPECT (transitive) | TypeConstant display chain (constants area). | Render a cached id string until the constants wave lands. |
| (collective, 11 sites) xOSFileNode:181, xRawOSFileChannel:298, xRTComponentTemplate:382, xInject:181, xRegEx:306, xString:391, BaseDecFP:565, BaseInt128:586, xFPLiteral:219, xIntLiteral:464, xArray:1011 | handle toStrings calling `super.toString()` | `ObjectHandle.toString()` (ObjectHandle.java:344) | DELEG | Own bodies are pure field rendering; impurity is inherited from the base handle/composition rendering. | Fix `ObjectHandle.toString()` once (Runtime Core); no per-site change needed. |

Pure for the record: xRTCharDelegate (416), xRTDelegate (951),
xRTSlicingDelegate (163), xContainerControl (207), xRTFunction
(604/717/740/979 — plain reads; 604 can NPE when the method is null,
robustness note), xRTType anonymous op toStrings
(565/627/760/848/918/980/1078/1208), xRTConnector (348), xRTServer (1016),
xAtomicInt128 (143), xAtomicIntNumber (154), xBoolean (130), LongLong (614).

### JavaJIT (org.xvm.javajit)

7 sites examined; 1 pure; 6 flagged.

| file:line | Class.method | What it calls | Category | Why dangerous | Replacement |
|---|---|---|---|---|---|
| org/xvm/javajit/builders/CommonBuilder.java:3999 | `CommonBuilder.toString` | `thisType.removeAccess()` → `replaceUnderlying(pool, ...)` when access-qualified → pool interning; then `getValueString()` | POOL | Build types routinely carry access (`:private`); rendering a builder in the debugger interns stripped-access type constants into the pool mid-JIT. | Cache the display string at construction (`thisType` is final); forced form in `describeForced()`. |
| org/xvm/javajit/BuildContext.java:3627 | `BuildContext.toString` | `methodStruct.getIdentityConstant().getValueString()` | SUSPECT (transitive) | MethodConstant rendering builds signature/type text (constants area); hot debugger target during JIT stepping. | Use the identity `getPathString()` (pure) or cache at construction. |
| org/xvm/javajit/ModuleLoader.java:110 | `ModuleLoader.toString` | `module.toString()` → `ModuleStructure.getDescription()` (contribution rendering reaches `resolveTypedefs()`, Component.java:3244) | SUSPECT (transitive) | ClassLoader toString is rendered by debugger frame dumps constantly; the description chain can resolve typedefs. | Render the module name string only. |
| org/xvm/javajit/registers/MultiSlot.java:190 | `MultiSlot.toString` | `type.getValueString()` | SUSPECT (transitive) | TypeConstant chain, invoked per register row in the Variables view. | Safe after the constants-leaf wave; until then a cached label. |
| org/xvm/javajit/registers/Narrowed.java:159 | `Narrowed.toString` | `type.getValueString()` | SUSPECT (transitive) | Same. | Same. |
| org/xvm/javajit/registers/Ref.java:141 | `Ref.toString` | `referentType.getValueString()` | SUSPECT (transitive) | Same. | Same. |

Pure: `JitMethodDesc.toString` (450) — delegates to
`java.lang.constant.MethodTypeDesc` rendering.

## Category Rollup

Primary classification per flagged site (a site with several effects is
counted once, under its worst category):

| Area | POOL | LAZY | RESOLVE | AMBIENT | GLOBAL | ALLOC | EXPENSIVE | SUSPECT | DELEG | Flagged | Examined |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ASM constants | 5 | 2 | — | — | — | — | 2 | 3 | — | 12 | 134 |
| ASM structures/ast | 1 | — | 3 | — | 1 | — | 4 | 2 | 17 | 28 | 80 |
| Ops | — | — | — | 33 | — | — | — | 2 | — | 35 | 48 |
| Runtime core | 1 | 3 | 8 | — | — | — | — | 14 | — | 26 | 50 |
| Templates | 3 | 1 | — | — | 1 | 1 | 1 | 6 | 11 | 24 | 34 |
| JavaJIT | 1 | — | — | — | — | — | — | 5 | — | 6 | 7 |
| Total | 11 | 6 | 11 | 33 | 2 | 1 | 7 | 32 | 28 | 131 | 353 |

The DELEG and per-area dependent counts explain why the migration is smaller
than 131 individual fixes: five root sites (`Argument.toIdString`,
`OpVar.getName`, `ObjectHandle.toString`, `ClassComposition.toString`,
`ParameterizedTypeConstant.getValueString`/`TerminalTypeConstant.getValueString`)
account for more than 80 dependent rows.

## Replacement Design

### The Contract

`toString()` must be pure and cheap: already-computed state only, deferred
markers for uncomputed state. This mirrors OwnershipDiagnostics default mode,
which reports computed lazy cells and marks deferred cells as deferred without
instantiating anything. Concretely, a display method (`toString()`, any
`toString(...)` overload reachable from it, `getValueString()`,
`getDescription()`, `getPathString()`, and helpers they call) must not:

1. force lazy computation (`Lazy.get`, `ensure*`, compute-on-demand caches,
   future joins);
2. register or intern constants in any `ConstantPool`;
3. resolve or normalize types, or write resolution results back into fields;
4. mutate any static or instance state (memoizing the rendered string is
   allowed only for a documented idempotent value cache — prefer computing
   final labels eagerly at construction);
5. read ambient context (thread-locals, current-frame bridges) or take locks;
6. allocate owner-bearing runtime objects (handles, compositions, exceptions);
7. throw or assert — a partially initialized object renders as a marker, not
   an exception (a throwing `toString()` takes down the rendering of every
   container that holds the object);
8. produce unbounded output — collections render a bounded prefix plus an
   elision marker; deep dumps belong to the forced variant.

Racy-but-read-only rendering of live state (a fiber's status, a pool's size)
is permitted but must be single-field snapshot reads that cannot throw.

### Marker Vocabulary

One shared vocabulary so dumps are grep-able:

| Marker | Meaning |
|---|---|
| `<deferred>` | A lazy cell exists but has not been computed; rendering did not compute it. |
| `<unresolved>` / `<unresolved:NAME>` | A constant/type has not been resolved; rendering did not resolve it. |
| `<not deserialized>` | Binary state (ops, nested structure) has not been inflated. |
| `<pending>` / `<failed: Type>` / `<cancelled>` | Future state without joining. |
| `<unassigned>` / `<initializing>` | Reference/handle lifecycle states (already in use at `xRef.RefHandle` and `ObjectHandle.InitializingHandle` — keep). |
| `const:#n`, `name:#n`, `type:#n` | An op operand known only as a pool index; no ambient lookup performed. |
| `… (N total)` | Bounded collection rendering elided N-k elements. |

### Pattern Set

1. Peek, never force. `Lazy`/`Lazy.Owner` already expose `isComputed()` and
   `orElse(...)` (`javatools_utils/.../org/xvm/util/Lazy.java`) — display code
   renders `cell.isComputed() ? render(cell.get()) : "<deferred>"`. No new
   API is required for the Lazy-backed sites.
2. Read resolution, never store it. Where display wants the resolved form of
   a maybe-unresolved constant, call `resolve()` into a local and render it;
   the write-back (`ensureResolvedConstant()`, `getAnnotationClass()`) stays
   in non-display callers.
3. Identity-first rendering. Prefer path strings, simple names, ordinals, and
   final label fields (`ClassTemplate.f_sName`,
   `IdentityConstant.getPathString()`) over type rendering.
4. Construction-time labels for final state. Objects whose display identity
   is fixed at construction (compositions, JIT builders) capture the rendered
   label once in the constructor, where the computation context is already
   owned — but only after the constants-leaf wave makes the label computation
   itself pure.
5. Explicit forced variant. Rich renderings that force, resolve, or traverse
   move to a method that Java never calls implicitly: `describeForced()` by
   convention (parameterized variants like `describeForced(Frame)` /
   `describeForced(boolean fRuntime)` where context is needed), or the
   existing `dump()` plane on `XvmStructure`/`MethodStructure`. `dump()` and
   `describeForced()` may call `toString()`; never the reverse.
6. Explicit context instead of ambient context. Op rendering that wants real
   constant values takes a `Frame` parameter
   (`Argument.toIdString(Frame, arg, nArg)`); the parameterless overload
   renders `const:#n` markers. `Frame.getStackTrace`/`formatFrameDetails`
   remain the explicit diagnostic surface — they may stay rich, but must
   route through the frame-parameterized renderers rather than thread-locals,
   and must not force.

### The Resulting API

The solution is deliberately small: no framework, no base class, no annotations. It is three kinds of
method plus a marker vocabulary, and it exists because **most display impurity turned out to be
incidental rather than inherent** — a cached resolution, or an interned key used only for a
comparison. Where that was true the information was KEPT in the pure path; only genuinely-deferred
state (a lazy split, a field layout, an `EnumInfo`, source normalization) needs a peek.

**1. Peek accessors — non-forcing reads of deferred state** (Pattern 1). Each returns a
"not computed" sentinel instead of building anything:

| Accessor | Returns when not computed |
|---|---|
| `Lazy.Bound.isComputed()` (master #539, reused) | `false` |
| `ClassComposition.isFieldLayoutComputed()` (package-private) | `false` |
| `GenericHandle.peekField(String)` (protected) | `null` |
| `PropertyStructure.peekPropertyAnnotations()` | `null` |
| `MethodStructure.Source.peekLineCount()` | `-1` |
| `xEnum.peekNameByOrdinal(int)` | `"<enum ordinal=N>"` |

**2. Resolve-without-store — read the resolution, never write it back** (Pattern 2). The write-back
is pure caching, so dropping it costs nothing and the rendered output is unchanged:

- `Annotation.peekAnnotationName()` and private `Annotation.peekAnnotationClass()` — versus
  `getAnnotationClass()`, which stores into `m_constClass`.
- `TerminalTypeConstant.getValueString()` reads `resolve()` into a local — versus
  `ensureResolvedConstant()`, which stores into `m_constId`.

Corollary: compare annotation classes **by name**, never by interned identity — that removes the
`getImplicitlyImportedIdentity` / `clzInject` / `clzOverride` / `clzRO` interning entirely.

**3. Explicit forced variants — the rich rendering Java never calls implicitly** (Patterns 5/6):

- `TypeInfo.toString(boolean fRuntime)` — the pre-existing arity; no-arg is the pure header.
- `Argument.toIdString(Frame, Argument, int)` and
  `OpVar.getName(Frame, Constant[], StringConstant, int)` — the ambient `ServiceContext` lookup
  replaced by an explicitly-supplied `Frame`.

**4. Marker vocabulary** — what a pure renderer prints when the state is not built:
`const:#n`, `name:#n`, `<text deferred>`, `<enum ordinal=N>`, `line-count=<deferred>`.

**5. Two gates**, because either alone is insufficient:

- `DisplayPurityTest` — static; greps banned callees inside display bodies; BASELINE now empty.
  Textual, so it cannot see impurity behind a helper.
- `DisplayPurityRuntimeTest` — empirical; renders 100+ live type-system objects three times and
  asserts the shared `ConstantPool` does not grow, with a negative control proving `pool.size()`
  actually detects interning. This is what catches the transitive cases the static gate cannot.

### Naming Rule For The Forced Variant

The Pattern Set above sanctions several shapes, which is correct (different sites need different
mechanisms) — but the NAME of a forced variant follows one rule, in this precedence order:

1. **A pre-existing arity that already means "full/rich" wins.** If the class already declares an
   overload whose documented meaning is the rich rendering, use it rather than inventing a name —
   `TypeInfo.toString(boolean fRuntime)` is exactly this (master already had it; no-arg became the
   pure header, the overload stayed the full dump).
2. **An explicit context parameter wins over a new name.** Where the impurity was *ambient* context,
   the forced variant is the same method taking that context explicitly:
   `Argument.toIdString(Frame, …)`, `OpVar.getName(Frame, …)`. (Pattern 6.)
3. **Otherwise `describeForced(...)`**, or the existing `dump()` plane on `XvmStructure`.
4. **No forced variant at all** when nothing needs the rich form. Prefer deleting it: a public impure
   method with no caller is dead surface. `ParameterizedTypeConstant` is the worked example — an
   interim `describeForced()` was added and then removed, because the pure structural `Function<…>`
   spelling is also what the runtime already prints (`reflect/Type.x`), so dropping it made the
   compiler CONSISTENT with the runtime instead of preserving a second spelling.

`dump()`/`describeForced()` may call `toString()`; never the reverse.

### Pattern Conformance Of Landed Sites

| Landed site | Pattern | Shape |
|---|---|---|
| `TypeInfoReal.toString` | 5 + naming rule 1 | no-arg = pure header; `toString(boolean)` = full dump |
| `MethodInfo.toString` | — (already pure output) | delegates to an `appendTo(StringBuilder)` primitive |
| `Argument.toIdString` | 6 + naming rule 2 | `const:#n` marker; `toIdString(Frame, …)` forced |
| `OpVar.getName` | 6 + naming rule 2 | `name:#n` marker; `getName(Frame, …)` forced |
| `ParameterizedTypeConstant.getValueString` | 3 + naming rule 4 | structural only; no forced variant (deleted) |
| `ObjectHandle.toString` | 3 | handle's own `m_fMutable` flag; no type resolution |
| `ClassComposition.toString` | — (pure transitively) | unchanged; pure once the type leaf is |
| `ExceptionHandle.toString` | 1 | `peekField` guarded on `isFieldLayoutComputed()`, else `<text deferred>` |

### Enforcement Status

`DisplayPurityTest` (javatools/src/test/java/org/xvm/asm/DisplayPurityTest.java) is the executable
form of this contract: it greps the banned-callee tokens inside display-method bodies and holds a
BASELINE of the violations that still exist. It fails both on a NEW violation and on a STALE baseline
entry, so each fixed slice must delete its baseline line and the gate tightens monotonically. The scan
is textual, not transitive, so known-impure *helpers* (`toSafeString`, `isExplicitAbstract`,
`reportUnimplemented`, `getDataType`, …) are themselves banned tokens.

**Status: the BASELINE is now EMPTY.** Every site the inventory flagged is pure, so the gate is
unconditional — any display method that acquires an impure callee fails the build outright. The one
site the gate cannot detect (`xEnum.EnumHandle.toString`, whose forcing hid behind a plain
`getName()`) was closed by hand.

### Forced Variants And Diagnostic Quality

The fix must not degrade log or diagnostic quality. Every rendering that
today is rich-but-forcing remains available — at an explicitly named call
site. `DebugConsole` is an interactive diagnostic surface and may call
`describeForced()` deliberately (documented as state-changing, matching the
`OwnershipDiagnostics.dump(true, ...)` precedent). Exception reporting keeps
full text via `ExceptionHandle.getException()`/explicit describe calls. Log
statements that want forced detail call the forced variant visibly at the log
site; nothing forces just because a string was concatenated.

### IntelliJ Debugger Guidance

IntelliJ evaluates `toString()` for the Variables/Watches views whenever
"Enable 'toString()' object view" is active (Settings → Build, Execution,
Deployment → Debugger → Data Views → Java). Until the migration lands,
anyone debugging races in this codebase should either disable that option or
switch it to "For classes from the list" and exclude `org.xvm.*` — otherwise
merely expanding a variable node mutates pools, caches, and resolution state
as documented above (and collection renderers/expression evaluation can do
the same). After the migration, pure `toString()` is safe to leave enabled,
and the forced variants are available on demand via Evaluate Expression
(`x.describeForced()`), with the understanding that evaluating them mutates
warmup state exactly like any other code execution. Note that the debugger is
not the only implicit caller: string concatenation, logging, and
`Throwable.toString()` during stack-trace printing (the
`ExceptionHandle.WrapperException` path) all hit the same methods, so the
purity contract protects normal runs too, not just IDE sessions.

## Before/After For Representative Sites

Six worked examples, one per problem family. All "before" code is verbatim
from the current branch.

### 1. Constant display that interns into a pool: ParameterizedTypeConstant

`javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java:975`

Before:

```java
@Override
public String getValueString() {
    var sb = new StringBuilder();

    ConstantPool pool = getConstantPool();
    if (m_constType.isA(pool.typeFunction())) {
        sb.append("function ");

        TypeConstant[] atypeParams  = pool.extractFunctionParams(this);
        TypeConstant[] atypeReturns = pool.extractFunctionReturns(this);
        // ... renders "function R(P)" from the extracted shapes ...
    } else {
        sb.append(m_constType.getValueString())
          .append('<')
          .append(Arrays.stream(m_atypeParams)
                  .map(TypeConstant::getValueString)
                  .collect(Collectors.joining(", ")))
          .append('>');
    }
    // ...
}
```

`pool.typeFunction()` (ConstantPool.java:2481) interns the canonical Function
type on first call; `isA` → `calculateRelation` (TypeConstant.java:5906) may
run `poolLeft.register(this)` (5915) — registering the displayed type into a
pool — and writes the relation cache (5936). One debugger render of a
parameterized type mutates pool intern state and relation caches.

After:

```java
@Override
public String getValueString() {
    // pure: renders only this constant's own fields; the "function R(P)"
    // pretty form requires pool/relation work and lives in describeForced()
    return m_constType.getValueString()
            + '<'
            + Arrays.stream(m_atypeParams)
                    .map(TypeConstant::getValueString)
                    .collect(Collectors.joining(", "))
            + '>';
}

/**
 * Forced display: pretty-prints function types as "function R(P)". May
 * consult the pool and compute type relations; never called implicitly.
 */
public String describeForced() {
    // previous function-type rendering moves here unchanged
}
```

### 2. TypeInfo-forcing site: TypeInfoReal

`javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:2164`
(`TypeInfo.toString()` at TypeInfo.java:706 funnels to `toString(false)`)

Before (abridged; the forcing lines verbatim):

```java
@Override
public String toString(boolean fRuntime) {
    // ... header ...
    // per property/method entry:
    if (f_mapVirtProps.containsKey(entry.getKey().resolveNestedIdentity(pool, null))) {
        sb.append("(v) ");
    }
    // ...
    if (fRuntime) {
        MethodBody[] chain = method.ensureOptimizedMethodChain(this);
        method = chain.length == 0
            ? MethodInfo.create(new MethodBody(method.getHead(), Implementation.Native), 0)
            : MethodInfo.create(chain, 0);
    }
    sb.append(entry.getKey())
      .append("=")
      .append(method);          // MethodInfo.toString -> isOp() -> pool.clzOp() interning
    // ...
}
```

Rendering a `TypeInfo` walks every member: each row memoizes canonical nested
identities, each `MethodInfo` value render interns the `Op` class identity
and forces `MethodBody.getMethodStructure()`, and the `fRuntime` branch
computes and caches optimized call chains and allocates fresh `MethodInfo`s.

After:

```java
@Override
public String toString(boolean fRuntime) {
    // pure header: identity, progress, format, flags, member counts
    return "TypeInfo: " + f_type + " (" + describeFlags() + ", props="
            + f_mapProps.size() + ", methods=" + f_mapMethods.size() + ')';
}

/**
 * Forced display: the full member dump. fRuntime=true additionally shows
 * optimized chains, using m_aBodyResolved when already computed and
 * "[chain deferred]" otherwise; it never computes a chain to display it.
 */
public String describeForced(boolean fRuntime) {
    // previous member-dump body moves here, with the ensureOptimizedMethodChain
    // call replaced by an already-computed read or the deferred marker
}
```

### 3. Op display that reads ambient state: Argument.toIdString

`javatools/src/main/java/org/xvm/asm/Argument.java:40` — called by 31 of the
38 op toStrings.

Before:

```java
static String toIdString(Argument arg, int nArg) {
    if (arg instanceof Constant) {
        return ((Constant) arg).getValueString();
    }

    if (arg instanceof Register) {
        return ((Register) arg).getIdString();
    }

    try {
        if (nArg <= Op.CONSTANT_OFFSET) {
            ServiceContext context = ServiceContext.getCurrentContext();
            if (context != null) {
                return context.getCurrentFrame().localConstants()[Op.convertId(nArg)].getValueString();
            }
        }
    } catch (Throwable ignore) {}

    return Register.getIdString(nArg);
}
```

After deserialization ops hold only int ids (`m_argXxx == null`), so at
runtime every op rendering takes the ambient branch: it reads the ThreadLocal
service context, dereferences whatever fiber happens to be current on the
observing thread — under a debugger, frequently a different frame than the op
belongs to — indexes that unrelated method's constant array, and swallows
whatever goes wrong with `catch (Throwable ignore)`.

After:

```java
static String toIdString(Argument arg, int nArg) {
    if (arg instanceof Constant constant) {
        return constant.getValueString();
    }
    if (arg instanceof Register reg) {
        return reg.getIdString();
    }
    if (nArg <= Op.CONSTANT_OFFSET) {
        return "const:#" + Op.convertId(nArg);      // no ambient lookup
    }
    return Register.getIdString(nArg);
}

/**
 * Forced display: resolves constant ids against an explicitly supplied
 * frame. Used by Frame.formatFrameDetails and other frame-owning dumps.
 */
static String toIdString(Frame frame, Argument arg, int nArg) {
    if (arg == null && nArg <= Op.CONSTANT_OFFSET && frame != null) {
        return frame.localConstants()[Op.convertId(nArg)].getValueString();
    }
    return toIdString(arg, nArg);
}
```

`OpVar.getName(...)` (OpVar.java:103) gets the same treatment: `name:#n`
marker in the pure form, `getName(Frame)` for forced rendering.

### 4. Runtime handle/composition display: ObjectHandle and ExceptionHandle

`javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:344` and `:754`

Before:

```java
@Override
public String toString() {
    TypeComposition clz = getComposition();

    // don't add "immutable" for immutable types
    return "(" + (m_fMutable || clz.getType().isImmutable() ? "" : "immutable ") + clz + ") ";
}
```

```java
@Override
public String toString() {
    ObjectHandle hText = getField(null, "text");
    return super.toString() +
        (hText instanceof StringHandle hString
            ? Handy.quotedString(hString.getStringValue())
            : "");
}
```

The base render runs `ClassComposition.toString()` →
`f_typeRevealed.getValueString()` (the site-1 offender) for every handle row
in the Variables view, plus `isImmutable()` recursion for relational types.
The exception render forces `ClassComposition.f_fieldLayout`
(`getField` → `getFieldInfo` → `fieldLayout()` → `Lazy.Owner.get`) — the
cell OwnershipDiagnostics default mode explicitly refuses to force — and
`WrapperException.toString()` (line 777) forwards here, so plain Java
stack-trace printing triggers it too.

After:

```java
@Override
public String toString() {
    // pure: composition label is safe after the constants-leaf wave;
    // mutability from the handle's own field only
    return "(" + (m_fMutable ? "" : "immutable ") + m_clazz + ") ";
}
```

```java
@Override
public String toString() {
    // reads the "text" field only when the field layout is already computed
    ObjectHandle hText = peekField("text");
    return super.toString() +
        (hText instanceof StringHandle hString
            ? Handy.quotedString(hString.getStringValue())
            : "<text deferred>");
}
```

with a small supporting reader on `GenericHandle`/`ClassComposition`:

```java
/**
 * Non-forcing field read: returns the field value iff the composition's
 * field layout has already been computed, else null. Display use only.
 */
protected ObjectHandle peekField(String sName) {
    return m_clazz.isFieldLayoutComputed() ? getField(null, sName) : null;
}
```

(`ClassComposition.isFieldLayoutComputed()` is `f_fieldLayout.isComputed()` —
the `Lazy` API already exists.)

### 5. MethodStructure/Code site: source normalization from getDescription

`javatools/src/main/java/org/xvm/asm/MethodStructure.java:2168` (chain:
`getLineCount()` :2888 → `normalize()` :2963 → `ensureStringConstant` :2986)

Before:

```java
if (fSrc) {
    sb.append(", line-number=")
      .append(m_source.getLineNumber())
      .append(", line-count=")
      .append(m_source.getLineCount());
}
```

```java
protected void normalize() {
    if (m_aconstSrc == null && m_sSrc != null) {
        // ... per source line:
        aconstLine[iLine] = pool.ensureStringConstant(sLine.substring(ofBegin, ofEnd));
        // ...
        m_aconstSrc = aconstLine;
        m_anIndents = anIndent;
    }
}
```

Rendering a method whose source has not been normalized interns one
StringConstant per source line into the ConstantPool and publishes
`m_aconstSrc`/`m_anIndents` unsynchronized — from
`XvmStructure.toString()`, i.e. from any debugger row showing a
MethodStructure.

After:

```java
if (fSrc) {
    sb.append(", line-number=")
      .append(m_source.getLineNumber())
      .append(", line-count=")
      .append(m_source.isNormalized() ? String.valueOf(m_source.getLineCount())
                                      : "<deferred>");
}
```

with `Source.isNormalized()` returning `m_aconstSrc != null`. Assembly and
`dump()` keep calling `getLineCount()`/`normalize()` — they are entitled to
force.

### 6. Runtime future display that joins and allocates: xFuture.FutureHandle

`javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:881`

Before:

```java
@Override
public String toString() {
    return "(" + m_clazz + ") " + (
            getFuture().isDone() ? "Completed: " + toSafeString(): "Not completed"
            );
}

protected String toSafeString() {
    try {
        return String.valueOf(getFuture().get());
    } catch (Throwable e) {
        return Utils.translate(getComposition().getContainer(), e).toString();
    }
}
```

Rendering a failed or cancelled future joins the CompletableFuture and — on
the exceptional path — calls `Utils.translate(container, e)`, which allocates
a brand-new xException `ObjectHandle` inside the owning container: an
owner-bearing allocation performed by the debugger. The success path renders
the referent handle recursively.

After:

```java
@Override
public String toString() {
    var cf = getFuture();
    String state;
    if (cf == null || !cf.isDone()) {
        state = "<pending>";
    } else if (cf.isCancelled()) {
        state = "<cancelled>";
    } else if (cf.isCompletedExceptionally()) {
        state = "<failed>";
    } else {
        ObjectHandle hValue = cf.getNow(null);
        state = "Completed: " + (hValue == null
                ? "<null>"
                : hValue.getClass().getSimpleName());
    }
    return "(" + m_clazz + ") " + state;
}

/**
 * Forced display: renders the completion value or the translated
 * exception. Allocates handles; requires an explicit frame/container.
 */
public String describeForced(Frame frame) {
    // previous toSafeString() behavior moves here
}
```

`FutureTupleHandle.getFuture()` scans element futures; its pure render
reports `<composite>` unless all element futures are plainly done.

## Enforcement Plan

### Mechanism

A JUnit source-shape gate, `DisplayPurityTest`, in
`javatools/src/test/java/org/xvm/asm/`, modeled on the existing regex-based
source scans (`OpRuntimeCacheTest.sourcePath(...)` already resolves
`src/main/java` vs `javatools/src/main/java` and pattern-scans production
sources; `NativeTemplateOldPatternTest` is the behavioral-precedent style).
The test:

1. walks every `.java` file under `org/xvm/asm`, `org/xvm/runtime`, and
   `org/xvm/javajit` in `javatools/src/main/java`;
2. extracts the bodies of `toString()`, `toString(boolean)`,
   `getValueString()`, `getDescription()`, `getPathString()`, and the named
   display helpers (`Argument.toIdString`, `OpVar.getName`,
   `OpCallable.getFunctionString/getParamsString/getReturnsString`,
   `OpInvocable.getTargetString/getMethodString`, `Frame.formatFrameDetails`
   excluded as an explicit forced surface) by signature match plus brace
   counting;
3. fails if a body (or a same-file private helper reachable only from display
   bodies) contains a banned callee or a banned shape.

ArchUnit would work as well, but the repo already has the regex-scan idiom
and no ArchUnit dependency; the regex gate keeps the toolchain unchanged.

### Banned-Callee List

Derived from the inventory; exact tokens the gate greps for inside display
bodies:

Pool interning/registration:

- `.register(` on a ConstantPool receiver
- `ensure` + `Constant(` — i.e. the regex `\.ensure[A-Z]\w*Constant\(`
  (covers `ensureStringConstant`, `ensureTerminalTypeConstant`,
  `ensureImmutableTypeConstant`, `ensureClassConstant`,
  `ensurePackageConstant`, `ensureAccessTypeConstant`, ...)
- `getImplicitlyImportedIdentity(`
- lazy canonical pool getters: `typeFunction()`, `typeObject()`, `clzOp()`,
  `clzRO()`, `clzOverride()`, `clzInject()` (extend to the full
  `type*()`/`clz*()` getter family as sites are migrated)
- `freeze()`, `ensureService()`, `removeAccess()` on TypeConstant receivers

Lazy forcing:

- `.get(` on a `Lazy`/`Lazy.Owner`-typed field; `orElse(` and `isComputed()`
  remain allowed
- `ensureTypeInfo(`, `ensureOptimizedMethodChain(`, `ensureCode(`,
  `ensureOps(`, `ensureAssembled(`, `getOps(`
- `fieldLayout()`, `getFieldInfo(`, `getField(` (display bodies use
  `peekField`)
- `enumInfo(`, `getNameByOrdinal(`
- `getPropertyAnnotations(`, `buildAnnotationArrays(`
- `normalize(`, `getLineCount(`, `getText(` on `Source`
- `getMethodStructure(` on MethodBody, `getComponent(` on IdentityConstant

Resolution with write-back / throwing resolution:

- `ensureResolvedConstant(`
- `getAnnotationClass(`
- `resolveTypedefs(`, `resolveGenerics(`, `normalizeParameters(`
- `isA(`, `calculateRelation(`
- `getDefiningConstant(` unless guarded by `isSingleDefiningConstant()`
- `isTuple(` on TypeConstant (typedef branch resolves)

Ambient context:

- `ServiceContext.getCurrentContext(`
- `ConstantPool.getCurrentPool(`, `ConstantPool.withPool(`

Runtime allocation/joins:

- `CompletableFuture` `.get()`/`.join()` in display bodies
- `Utils.translate(`, `xException.makeHandle(`
- `getType()` on an ObjectHandle receiver (routes through `augmentType`)

Banned shapes (regex, not callees):

- `catch (Throwable` inside a display body (silent swallow)
- `throw new` inside a display body
- assignment to an `m_`/static field inside a display body
- `assert ` inside a display body

### Ratchet

The gate lands before the migration finishes, with a checked-in baseline file
listing the currently failing `file#method` sites (exactly the inventory
above). New violations fail immediately; migration PRs shrink the baseline;
when the baseline is empty it is deleted and the gate becomes unconditional.
This is the same ratchet shape as the existing lint/parallelism scans.

## Migration Plan

**Landed so far (commit 8382e0268, branch `lagergren/lazy-instance`):** the two
ASM-constants leaves from slice 2 — `TypeInfoReal.toString` and
`MethodInfo.toString`. Naming nuance discovered while landing: `TypeInfo`
*already* declares `abstract toString(boolean fRuntime)` in master (fRuntime =
"optimize the method call chains"), so the pure/forced split reused that existing
ARITY (no-arg `toString()` = pure header; `toString(boolean)` = full dump) rather
than adding a `describeForced()` method. Most OTHER flagged classes have no spare
boolean overload, so they will still need an explicit `describeForced()`/`dump()`
method as this plan's Pattern Set describes — the `toString(boolean)` shortcut is
specific to `TypeInfo`. Scope/portability recorded in
[tostring-purity-enhancement-scope.md](tostring-purity-enhancement-scope.md).

**Also landed (commit `a9e7d58c0`):** the two AMBIENT op-display roots from slice 1
— `Argument.toIdString` and `OpVar.getName`. The pure form now renders a
`const:#n` / `name:#n` marker with NO ambient `ServiceContext.getCurrentContext()`
lookup (which read an unrelated observing-thread fiber and indexed the wrong
constant array, AIOOBE swallowed by `catch(Throwable)`), and each gained an explicit
frame-parameterized forced overload (`toIdString(Frame, …)`, `getName(Frame, …)`).
These two feed 31 of 38 op toStrings. Suites green, no op-dump golden regressed.

**Slice 1 now COMPLETE.** The riskier roots landed after a production-use audit:
- `ParameterizedTypeConstant.getValueString` renders the structural `Function<…>`
  form (pure). The pretty `function R(P)` form was **dropped**, not preserved: an
  interim `describeForced()` was removed (commit after `a03a998f1`) because nothing
  needs it and the structural form is what the runtime already prints
  (`reflect/Type.x` renders `Function<…>`), so dropping it makes the compiler's type
  spelling CONSISTENT with the runtime instead of diverging. `TerminalTypeConstant
  .getValueString` is pure transitively. The audit verdict was SAFE — the output
  change affects function types only; no consumer parses/branches on `"function "`,
  keys a map/cache/equality on the string, round-trips or serializes it (the pool
  serializes positions), and the scalar-spelling `switch`/`equals` sites never
  traverse the function branch. If a nicer function rendering is ever wanted, it
  belongs in the compiler's error-message formatter, not a general constant method.
- `ObjectHandle.toString` dropped the `getType().isImmutable()` recursion;
  `ClassComposition.toString` is pure transitively (`a03a998f1`).
- `ExceptionHandle.toString` reads `"text"` via a non-forcing `peekField` (guarded on
  `ClassComposition.isFieldLayoutComputed()`), else `<text deferred>` (`6c1c7c686`).

All gated with the unit suites + `xdk:installDist`. With the five/six roots pure, the
80+ `DELEG`/`SUSPECT` dependent rows are pure transitively.

**The remaining flagged sites then landed, and the ratchet's BASELINE is now EMPTY:**

| Site | Was | Now | Commit |
|---|---|---|---|
| `ParamInfo.toString` | `pool.typeObject()` interned; `isTuple()` resolved a typedef | compares the constraint's value string | `4f35e55a6` |
| `TerminalTypeConstant.getValueString` | `ensureResolvedConstant()` wrote back `m_constId` | reads `resolve()` into a local; output byte-identical | `4f35e55a6` |
| `PropertyBody.toString` | 4 flag helpers forced the annotation split + interned a comparison identity | reads the ALREADY-SPLIT annotations, compares by NAME — flags retained | `4f35e55a6` |
| `Annotation.getValueString`/`getDescription` | `getAnnotationClass()` resolve-and-stored | new private `peekAnnotationClass()` (resolve, don't store) | `23e2307d6` |
| `Contribution.toString` | `resolveTypedefs()` built new TypeConstants; unguarded `getDefiningConstant()` threw | renders `getValueString()`; guarded by `isSingleDefiningConstant()`; both `fFirst` loops modernized to `Collectors.joining` | `23e2307d6` |
| `MethodStructure.getDescription` | `getLineCount()` → `normalize()` interned a StringConstant PER SOURCE LINE | new non-forcing `Source.peekLineCount()`, else `line-count=<deferred>` | `a700cca30` |
| `BinaryAST.toString` | `reportUnimplemented(...)` mutated a process-global set + wrote stderr | renders `nodeType().name()`; the helper and its static set are deleted | `a700cca30` |
| `xRTType.TypeHandle.toString` | `getDataType()` → `augmentType` → `freeze()` interned an immutable type | reads the foreign/composition type directly | `63ee73a86` |
| `xFuture.FutureHandle.toString` | `toSafeString()` JOINED the future and could allocate an exception handle | state-only; `getNow(null)` for an already-completed value | `63ee73a86` |
| `xEnum.EnumHandle.toString` | `getName()` forced the template's lazy `EnumInfo` | new non-forcing `peekNameByOrdinal()`, else `<enum ordinal=N>` | `63ee73a86` |

Supporting pure accessors added along the way: `Annotation.peekAnnotationName()`,
`PropertyStructure.peekPropertyAnnotations()`, `MethodStructure.Source.peekLineCount()`,
`xEnum.peekNameByOrdinal()`, `GenericHandle.peekField()`,
`ClassComposition.isFieldLayoutComputed()`, and `FrozenArray.stream()`.

Deliberately still open (not display-purity blockers): the `Frame`/`FiberQueue`/`ServiceContext`
short forms and the JavaJIT rows, which the inventory classifies as SUSPECT/racy-read rather than
mutating, plus slice 6's SUSPECT closure.

Small, independently landable PR slices, worst offenders first because five
root sites unblock most dependent rows:

1. Slice 1 — root helpers (the multiplier wave):
   `Argument.toIdString` + `OpVar.getName` (clears all 33 op AMBIENT rows,
   adds the `Frame`-parameterized forced overloads),
   `TerminalTypeConstant.getValueString` +
   `ParameterizedTypeConstant.getValueString` (the leaves under nearly every
   chain), `ObjectHandle.toString` + `ClassComposition.toString` (clears the
   base-handle RESOLVE family and the 11-site template collective row),
   `ExceptionHandle`/`WrapperException` + the `peekField` reader,
   `BinaryAST.reportUnimplemented` removal. Verify with existing unit suites;
   op-dump golden output in tests updates to the marker forms.
2. Slice 2 — ASM constants remainder: `TypeInfoReal.toString` split,
   `MethodInfo.toString`, `PropertyBody.toString`, `ParamInfo.toString`,
   `TypeInfo.containsAnnotation` display ban, Array/Map render caps,
   `HandleConstant`/`ExpressionConstant` summaries.
3. Slice 3 — ASM structures: `MethodStructure.getDescription`
   (`Source.isNormalized()`), `Annotation` display, `Contribution.toString`,
   `Code.toString` `<not deserialized>` label and cap,
   `CompositeComponent`/`ClassStructure`/`SwitchAST` bounds, purity javadoc
   on the `XvmStructure.toString()`/`Constant.toString()` funnels.
4. Slice 4 — runtime core: `DeferredArrayHandle`, `Deferred*Handle`
   summaries, `Frame`/`StackFrame`/`ServiceContext`/`Fiber`/`FiberQueue`
   short forms with the rich versions routed through the explicit
   frame-parameterized helpers, `CallChain`, `ClassTemplate.f_sName`,
   `PropertyComposition`/`ProxyComposition`, `Frame.VarInfo` eager name.
5. Slice 5 — templates and javajit: `xFuture`, `xEnum`, `xRTType.TypeHandle`,
   `xClass.ClassHandle`, `Proxy.ProxyHandle`, `xTuple` cap,
   `xRTMethod`/`xRTSignature`/`xAtomic`/`xRef` referent summaries,
   `CommonBuilder` construction-time label, `ModuleLoader`,
   `registers/*` after the constants wave.
6. Slice 6 — suspect closure: verify or fix the 32 SUSPECT rows (compiler-AST
   escape from `ExpressionConstant`, `SingletonConstant.getDescription`,
   `FunctionHandle` null-safety, racy-read hardening in
   `ErrorList`/`Fiber.reportWaiting`), and re-verify the DELEG rows are now
   pure end-to-end.
7. Slice 7 — the gate: land `DisplayPurityTest` with the banned-callee list
   and the baseline ratchet (the gate can land as early as slice 2 with a
   full baseline; it flips to unconditional when the baseline empties).

Each slice must leave diagnostics at least as good as before it landed: any
call site that relied on forced rendering gains an explicit
`describeForced()`/`dump()` call in the same PR.

### Priority

This work is deliberately parked near the end of the should-fix queue, after
the must-fix and must-audit work. The queue position is recorded in the task
list at [../must-audit-backlog.md](../must-audit-backlog.md) ("Should fix
(near end of should queue): Side-effect-free `toString()`/display path
redesign"); this document is the inventory and design that row points to. The
one exception worth pulling forward opportunistically: slice 1 is small, has
outsized debugging value for the must-fix races themselves (it makes the
debugger trustworthy while investigating them), and touches only display
code.
