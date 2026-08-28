# Java Array Element Exposure Audit

Branch: `lagergren/lazy-instance`

Date: 2026-08-24

This is the definitive site-level audit of Java array use and misuse across the
XVM Java implementation. It extends, and does not repeat, the design-level
[array-list-immutability-study.md](array-list-immutability-study.md). That
study explains why arrays are both essential and overused; this audit
enumerates every public/protected exposure, traces who actually writes
elements through somebody else's array, and classifies each finding as a
deliberate protocol, a latent hazard, or a bug candidate precise enough to
become a must-fix row.

Scope: production sources under `javatools/src/main/java`,
`javatools_utils/src/main/java`, and `javatools_jitbridge/src/main/java`.

Related documents read first and extended here:

- [array-list-immutability-study.md](array-list-immutability-study.md)
- [state-inventory.md](state-inventory.md) ("Exposed Mutable Arrays",
  "Public Or Protected Mutable Fields")
- [must-audit-backlog.md](must-audit-backlog.md) (rows 141, 167, 168, 169)
- [clone-usage-audit.md](clone-usage-audit.md) (the Rows 125/161 Completion
  Sweep; array storage-pointer forks are mechanism 4 there)
- [generics-api-audit.md](generics-api-audit.md)
- [fixed-in-this-branch.md](fixed-in-this-branch.md) (the `Annotation`
  constructor aliasing contract)

## Problem Statement

Java array elements are effectively mutable everywhere. `final` protects only
the reference; any code holding an array reference can write any element at
any time, silently, from any thread. An array type also says nothing about
ownership: `TypeConstant[]` does not say "interned, never write",
`ObjectHandle[]` does not say "this is a live register file", and
`Constant[]` does not say "the compiler will back-fill slot 3 later". Arrays
have no generic element form either, so the runtime combines element
mutability with erasure casts.

This is endemic because arrays are the runtime's core currency -
`ObjectHandle[]` registers and argument lists, `GenericHandle` field arrays as
THE object representation, delegate storage, `Op[]` instruction streams,
switch tables - and the compiler/ASM metadata currency - `TypeConstant[]`,
`Parameter[]`, `Constant[]`, `Annotation[]`, `MethodBody[]`. Every one of
those arrays is a set of unsynchronized shared variables whose write policy
lives only in the heads of the original authors. That defeats ownership
boundaries, hides bugs behind aliasing distance, and is exactly the kind of
state that blocks same-JVM sequential/parallel reuse.

Extension (added during this audit at the owner's request): the same disease
exists one abstraction level up. Getters that return internal mutable
`Map`/`List`/`Set` instances hand out the same unenforced "please do not
write" contract, and the repository contains a family of getters whose
unmodifiable wrapping exists **only when Java assertions are enabled**. The
collection variant is covered in its own inventory category and hunt section
below, with the same classification standard.

Known deliberate mutable-array protocols, classified as protocols and not as
accidents throughout this document:

- `GenericHandle` field arrays are intentionally SHARED by `cloneAs` views;
  view semantics depend on the sharing
  (`javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:655`, `:680`).
- `Annotation`'s constructor deliberately aliases the caller's param array for
  the compiler's emit-time back-fill
  (`javatools/src/main/java/org/xvm/asm/Annotation.java:63`-`:71`; see
  [fixed-in-this-branch.md](fixed-in-this-branch.md)).
- `JumpVal`/`JumpVal_N` switch tables are container-scoped runtime caches
  (`javatools/src/main/java/org/xvm/asm/op/JumpVal.java:121`, `:330`;
  `javatools/src/main/java/org/xvm/runtime/Container.java:143`).

## Method

Every site classified as dangerous below was read, not grepped. Counts come
from the following scans, run on this branch on 2026-08-24. The alias-tracing
scans are deliberately two-stage because no single regex can see "array
obtained from another object, mutated later": stage one finds the variable
that stores an accessor result, stage two finds element writes through that
variable in the same file. Cross-file aliases were traced by hand for the
named high-value accessors.

```bash
# census: public/protected array-typed field declarations
rg -n --pcre2 '^\s*(public|protected)\s+(static\s+)?(final\s+)?(transient\s+)?[A-Za-z_][\w.<>?,\[\] ]*\[\]\s+[A-Za-z_$][\w$]*\s*(=|;)' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# accessors that return an internal array field raw (strict single-return shape)
rg -nU --pcre2 '(public|protected)\s+[A-Za-z_][\w.<>, ]*\[\]\s+\w+\s*\([^)]*\)\s*(\n\s*)?\{\s*\n?\s*return\s+(this\.)?[fms]_[A-Za-z0-9_]+;\s*\n?\s*\}' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# all public/protected array-returning method declarations (the haystack)
rg -n --pcre2 '^\s*(public|protected)\s+(static\s+)?(final\s+)?[A-Za-z_][\w.<>, ]*\[\]\s+[a-z]\w*\s*\(' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# direct element writes through a method-call result (found zero hits)
rg -n --pcre2 '\.\w+\([^)]*\)\s*\[[^\]]+\]\s*(=[^=]|\+\+|--|\+=|-=)' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# stage-1/stage-2 alias hunt, per accessor name (getRawParams shown; repeated for
# getRawReturns, getParamTypesArray, getParams, getChain, getLocalConstants,
# getReturnArray, getParamArray, getAnnotations, getValue, getImplicitFields,
# getOptimized*Chain, sortedMethods, sortedProperties, getFieldNameArray, ...)
for f in $(rg -l '\.getRawParams\(\)' javatools/src/main/java); do
  rg -n --pcre2 '(\w+)\s*=\s*[^;=]*\.getRawParams\(\);' "$f" -o -r '$1' | cut -d: -f2 | sort -u |
  while read v; do rg -n --pcre2 "^\s*${v}\[[^\]]+\]\s*=[^=]" "$f"; done
done

# element writes into the named static tables (found zero hits)
rg -n --pcre2 '(ZEROx2|OVERFLOWx2|xB_FACTORS|xI_FACTORS|CDs_\w+|ELEMENT_TYPE|NO_\w+|OBJECTS_NONE|STRINGS_NONE|EMPTY_\w+|WAIT_FOR_\w+|VOID|THIS|OBJECT|INT|STRING|BOOLEAN|BYTES)\s*\[[^\]]+\]\s*(=[^=]|\+\+|--)' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# collection extension: getters returning internal mutable collections raw
rg -nU --pcre2 '(public|protected)\s+(static\s+)?(final\s+)?(Map|HashMap|List|ArrayList|Set|HashSet|ListMap|EnumMap|Collection)<[^;{]*>\s+\w+\s*\([^)]*\)\s*\{\s*\n?\s*return\s+(this\.)?[fms]_[A-Za-z0-9_]+;\s*\n?\s*\}' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# collection extension: unmodifiable wrapping that exists only under -ea
rg -n --pcre2 'assert\s*\(\s*\w+\s*=\s*Collections\.unmodifiable' \
  javatools/src/main/java javatools_utils/src/main/java javatools_jitbridge/src/main/java

# collection extension: mutation chained or aliased through collection getters
rg -n --pcre2 '\.(getProperties|getMethods|getVirtMethods|getVirtProperties|getTypeParams|getContributionList|getClassChain|getChildInfosByName|getFibers|getServices|getTokens|getErrors|getInjections)\(\)\s*\.\s*(put|add|remove|clear|merge|putAll|addAll)\(' \
  javatools/src/main/java
```

Classification standard, identical to the other audits:

- DELIBERATE PROTOCOL: documented or provably intended sharing/mutation; the
  protocol is named and its safety argument recorded.
- LATENT HAZARD: mutation is possible through the exposure but no mutation
  site was found; the thing standing between today and a bug is convention.
- BUG CANDIDATE: mutation actually happens where the owner cannot know, or
  two owners disagree about who may write; precise enough to become a
  must-fix row.
- SUSPECT: a verdict could not be proven either way; what needs checking is
  stated instead of guessed.

## 1. Exposure Inventory

### 1.0 Census

| Scan | Count |
| --- | ---: |
| Public/protected array-typed field declarations (all three roots) | 105 |
| ... of which `static` (every one is `static final`; zero non-final statics) | 58 |
| ... of which zero-length empty sentinels (`new T[0]`, `{}`, or aliases of one) | 30 |
| ... of which non-empty static tables | 28 |
| ... of which instance fields (public or protected, final and non-final) | 47 |
| Public/protected methods declared with array return types | 358 |
| ... verified strict raw-field-return accessors (single `return m_x;` shape) | 21 |
| Direct `x.foo()[i] = ...` element writes through a call result | 0 |
| Element writes into any named public static array table | 0 |
| Collection getters returning internal mutable Map/List/Set raw (strict shape) | 21 |
| Getters whose `Collections.unmodifiable*` wrapper exists only under `-ea` | 8 |

The two zero rows are the good news and they are load-bearing: nobody in the
tree writes `other.getFoo()[i] = v` on one line, and nobody pokes the named
static tables. Every real mutation flows through a **stored alias**, which is
why the two-stage hunt in section 2 is the heart of this audit.

### 1.1 Category (a): public/protected array fields

These are the worst exposures - anyone holding the object (or, for statics,
anyone at all) can poke elements at any time. All 47 instance fields, grouped
by role. "Hot currency" rows are deliberate performance structs whose safety
argument is fiber/owner confinement, not encapsulation.

| Group | Fields | Role / verdict |
| --- | --- | --- |
| Interpreter register files (hot currency) | `Frame.f_ahVar` (`runtime/Frame.java:73`, public), `f_aInfo` (`:74`, public), `f_anNextVar` (`:80`, public), `f_aOp` (`:69`, protected), `f_aiReturn` (`:77`, protected) | THE runtime currency. Written cross-class by design: `ServiceContext.java:795` installs a `VarInfo` into another frame's `f_aInfo`; `xVar.java:173` writes `frameRef.f_ahVar[nVar]` for register-delegating Var handles; `FinallyEnd.java:59`/`:61` and `xRef.java:974`, `:1099` read registers of a captured frame. DELIBERATE hot-path struct; confinement is the fiber scheduler. Must stay raw (section 4). |
| Tuple element storage | `xTuple.TupleHandle.m_ahValue` (`runtime/template/collections/xTuple.java:681`, **public, non-final**) | Live tuple contents, read by 10+ classes across 3 packages (`ServiceContext.java:1818`, `Frame.java:1296`, `Proxy.java:330`, `Call_T*`/`Invoke_T*` ops, `xRTMethod.java:211`, `xRTFunction.java:250`, `xContainerControl.java:115`). Source of BUG CANDIDATE 1 in section 2. |
| Array delegate storage | `xRTDelegate.GenericArrayDelegate.m_ahValue` (`xRTDelegate.java:831`), `ByteBasedDelegate.m_abValue` (`:395`), `LongBasedDelegate.m_alValue` (`:547`), `xRTFloat64Delegate.m_adValue` (`:270`), `xRTCharDelegate.m_achValue` (`:350`), `xRTStringDelegate.m_asValue` (`:239`) | Runtime array payload, protected, non-final (storage pointer is reassigned on grow). External readers exist (`xRTFileTemplate.java:239`, `xRTTypeTemplate.java:535` - both verified read-only). The per-view storage-pointer fork is must-fix row 147 mechanism 4 in [clone-usage-audit.md](clone-usage-audit.md). Keep raw; the fix is the row-147 shared-cell redesign, not a wrapper. |
| Decoded-op assembly state | `OpSwitch.m_anConstCase`/`m_aofCase`/`m_acExits` (`asm/op/OpSwitch.java:457`-`:465`), `JumpInt.m_aofCase` (`:255`), `OpCallable.m_anRetValue`/`m_aArgReturn` (`:995`, `:999`), `OpInvocable` same (`:522`, `:527`), `GP_DivRem.m_anRetValue` (`:261`) | Mutable during assembly/link only; `OpSwitch.resolveAddresses` (`OpSwitch.java:106`-`:135`) rewrites them at link time and `assertReadyForRuntime` (`:138`) guards runtime readiness (backlog row 160, done-for-first-publication). Protected-to-subclass exposure is wider than needed but the lifecycle is policed. LATENT, cold. |
| Runtime call metadata | `CallChain.f_aMethods` (`runtime/CallChain.java:773`, protected final) | Aliases the `MethodInfo` chain cache array. Verified read-only inside `CallChain` (only indexed reads). LATENT: any element write would corrupt the TypeInfo-owned chain for the whole container. |
| Native argument shells | `xRTFunction.FullyBoundHandle.f_ahArg` (`:872`), `NativeFunctionHandle.f_aParams` (`:1486`), `AssertV` message closure `ahValue`/`asLabel` (`asm/op/AssertV.java:197`-`:198`), `Frame.MultiGuard.f_an*` (`runtime/Frame.java:2337`-`:2339`) | Constructor-retained arrays (see category (c)). Protected final; single-writer construction, read-only after. LATENT, narrow. |
| JIT facade / ABI | `Ctx.iN`, `Ctx.oN` (`javajit/Ctx.java:61`-`:62`, public non-final), `JitMethodDesc.standardReturns/standardParams/optimizedReturns/optimizedParams` (`javajit/JitMethodDesc.java:77`-`:80`), `TypeSystem.shared/owned` (`javajit/TypeSystem.java:147`, `:152`), `TypeSystemLoader.shared/owned` (`javajit/TypeSystemLoader.java:60`, `:65`), `BuildContext.callChain` (`javajit/BuildContext.java:255`) | `Ctx.iN/oN` are the generated-code multi-return overflow registers - ABI, addressed by field name from generated bytecode; cannot be encapsulated without regenerating the calling convention (section 3). The descriptor/loader arrays are metadata handed around raw; LATENT, cold, good `List.copyOf` candidates except where generated code binds to them. |
| JIT bridge storage/ABI | `nLongBasedArray.$storage` (`javatools_jitbridge/.../nLongBasedArray.java:70`, public), `ArrayᐸObjectᐳ.$storage` (`.../ArrayᐸObjectᐳ.java:31`, public) | Bridge array payload, public because generated code and bridge natives address it. ABI wall (section 3). |
| Compiler AST scratch | `CmpChainExpression.operators` (`compiler/ast/CmpChainExpression.java:575`), `MultipleLValueStatement.aGroundLabels` (`:546`) | Request-local AST state; compiler-reentrancy plane, not a runtime hazard. LATENT under the existing "AST is request-confined" assumption. |
| Value-object internals | `Version.ints` (`asm/Version.java:604`, protected non-final) plus raw getter `getIntArray()` (`:594`) and the sharing constructor at `:538` (`new Version(ints, null)` shares the array between two `Version` instances) | `Version` is used as a map/tree key. No writer found (`rg 'ints\[' Version.java` shows only reads), so LATENT - but a mutable shared array inside an equals/hashCode-bearing value type is exactly the shape that corrupted constants elsewhere. Cheap fix: private final + copy-in. |
| ASM metadata | `MultiCondition.m_aconstCond` (`asm/constants/MultiCondition.java:383`, protected, aliased from the constructor at `:56`/`:108`) | Condition constants; `registerConstants` (`:289`) replaces the array wholesale (copy-on-write helper), elements interned. LATENT, cold. |

The 58 statics are covered in categories (d) and (e).

### 1.2 Category (b): accessors that return internal arrays raw

The strict-shape scan found 21 such methods; four more reach the same arrays
through one level of delegation. The table lists every one, with what callers
actually do (from the alias hunt in section 2).

| Accessor | Returns | Caller behavior (verified) | Verdict |
| --- | --- | --- | --- |
| `SignatureConstant.getRawParams()` (`asm/constants/SignatureConstant.java:172`-`:174`), `getRawReturns()` (`:193`-`:195`), and the delegating `MethodConstant.getRawParams()` | The interned signature's own `TypeConstant[]` | 126 call sites. Every mutating consumer clones first: `InvocationExpression.java:788` (`atypeParams.clone()`), `:1062` (`clone(); // don't mess up the actual types`), `OpCallable.java:637` (clone-on-first-diff). One consumer mutates ELEMENTS through it deliberately: `ClassStructure.java:3237`-`:3243` (see protocol table). | LATENT - the only guard between callers and the interned array is a hand-written clone convention; the comment at `InvocationExpression.java:1062` proves the authors know it. |
| `ParameterizedTypeConstant.getParamTypesArray()` (`:168`-`:170`) | Interned type's param array | 70 call sites; all mutating consumers verified clone-before-write (`TypeConstant.java:1014`-`:1017`, `ForEachStatement.java:476`-`:481`, `Expression.java:780`-`:821` uses an `fCloneActual` copy-on-write flag, `TernaryExpression.java:557`). | LATENT, same clone-convention guard. |
| `Annotation.getParams()` (`asm/Annotation.java:176`-`:178`) and `AnnotatedTypeConstant.getAnnotationParams()` | The annotation's live param array | Two callers WRITE elements: `VariableDeclarationStatement.java:270`-`:288` and `NewExpression.java:1053`-`:1073`. | DELIBERATE PROTOCOL (the emit-time back-fill; section 2). |
| `MethodStructure.getParamArray()` (`:584`-`:586`), `getReturnArray()` (`:468`-`:470`), `getAnnotations()` (`:257`-`:259`), `getLocalConstants()` (`:728`-`:730`) | Method's own metadata arrays | One caller retains ELEMENTS into a second owner: `MethodDeclarationStatement.java:503`/`:521` (BUG CANDIDATE 2). All other verified writes were into freshly built arrays. | BUG CANDIDATE via element sharing; otherwise LATENT. |
| `MethodInfo.getChain()` (`asm/constants/MethodInfo.java:1144`-`:1146`), `PropertyInfo.getPropertyBodies()` (`:628`-`:630`), `TypeInfo.getOptimizedMethodChain/GetChain/SetChain` (implemented at `TypeInfoReal.java:1625`-`:1662`, returning the `MethodInfo`/`PropertyInfo` cached arrays) | Shared, container-visible chain caches (safe publication fixed in backlog row 118) | Alias hunt found zero element writes. `CallChain` retains `getChain()` results read-only. | LATENT - these arrays are the exact working set of every dispatch; one write corrupts dispatch for the whole TypeInfo. Top candidates for `List.copyOf`/frozen wrappers in section 4. |
| `TypeInfoReal.getClassAnnotations()`/`getMixinAnnotations()` (`:999`-`:1006`) | TypeInfo's annotation arrays | Read-only callers. | LATENT, cold. |
| `ConstantPool.getConstants()` (`asm/ConstantPool.java:142`-`:151`) | **A fresh copy** (`f_listConst.toArray`), documented "the caller can safely modify the array... must NOT modify [the elements]" (`:132`-`:136`) | Single external caller, `tool/Disassembler.java:169`, read-only. | PROVEN SAFE accessor shape (copy-out). The remaining exposure is element mutability of `Constant`, which is the interning/adoption story, already covered by the constant-pool audits. |
| `ArrayConstant.getValue()` (`asm/constants/ArrayConstant.java:157`-`:159`) | Internal `Constant[]` | Copy-in at construction (`:47`, branch hardening) but raw-out; all callers verified read-only (e.g. `xTuple.java:85`). | LATENT - asymmetric copy-in/raw-out API. |
| `UInt8ArrayConstant.getValue()` (`:80`-`:82`), `FPNConstant.getValue()` (`:102`-`:104`), `Float128Constant.getValue()` (`:86`-`:88`) | Internal `byte[]` payloads | Copy-in at construction (branch fix); callers read-only (e.g. `javajit/Builder.java:582`-`:586` packs into a fresh `long[]`). | LATENT - the study already lists the follow-up (defensive-copy accessor or immutable view). |
| `Parameter.getAnnotations()` (`asm/Parameter.java:125`-`:127`) | Parameter's annotation array | `addAnnotation` (`:134`-`:146`) is correct copy-on-write (new array, reassign field); no external writes found. | LATENT; note stale-alias semantics: callers holding the old array miss later additions. |
| `ClassTemplate.getImplicitFields()` (`runtime/ClassTemplate.java:705`-`:707`) returning `f_asFieldsImplicit` (`:2498`) | Template's implicit-field name list | Read-only callers. | LATENT, cold. |
| `ClassComposition.getFieldNameArray()` (`runtime/ClassComposition.java:439`-`:445`, cached via `Lazy.Owner` at `:993`) plus the `DelegatingComposition`/`PropertyComposition` forwards | The composition-wide cached `StringHandle[]` | `xConst.callEstimateLength`/`callAppendTo` (`xConst.java:391`, `:423`) wrap it into a `Mutability.Constant` xArray handle - and `createDelegate` deliberately ALIASES Constant-mutability content arrays (`xRTDelegate.java:152`-`:154`), so the shared cache becomes reachable from natural Ecstasy code. | DELIBERATE zero-copy protocol whose guard is `checkWrite` (`xRTDelegate.java:529`-`:541`); see section 2 for why row 147 mechanism 5 erodes it. |
| `xString.StringHandle.getValue()` (`runtime/template/text/xString.java:331`-`:333`) | String payload `char[]` | Read-only callers found. | LATENT, hot - must stay raw; document read-only. |
| `EvalCompiler.getArgs()` (`compiler/EvalCompiler.java:157`-`:159`), `ErrorListener.ErrorInfo.getParams()` (`asm/ErrorListener.java:248`-`:250`), `Version.getIntArray()` (`asm/Version.java:594`) | Debug/diagnostic internals | Read-only callers. | LATENT, cold. |

Everything else in the 358-method haystack was screened by the alias hunt and
either returns freshly built arrays (`ClassComposition.getFieldValueArray` at
`:478`-`:498`, `TypeInfoReal.sortedMethods()` at `:1361`-`:1367`,
`Handy`/`ConstOrdinalList` utility functions), returns defensive clones
(`ConstBitSet.getBytes()` `javatools_utils/.../ConstBitSet.java:110`,
`ConstOrdinalList.getBytes()` `:135`), or returns the proven-safe sentinels of
category (e). One residual note on `sortedMethods()`/`sortedProperties()`
(`TypeInfoReal.java:1361`, `:1069`): the array is fresh, but the elements are
live `Map.Entry` objects from the internal maps - `entry.setValue(...)` by any
caller would write through into `f_mapMethods`. No caller does; LATENT.

### 1.3 Category (c): arrays accepted and retained without copy

The `Annotation` protocol generalized: constructors and factories that keep
the caller's array, so the caller retains write access to the object's
insides. Each verified by reading the constructor.

| Site | Retained array | Verdict |
| --- | --- | --- |
| `Annotation` constructor (`asm/Annotation.java:63`-`:71`) | Caller's `Constant[]` params | DELIBERATE PROTOCOL, documented in-code; adoption detaches (`Annotation.copyForAdoption`, guarded by `ConstantAdoptionTest.annotationConstructionAliasesParamsForCompilerBackfill`). |
| `MethodStructure` constructor (`asm/MethodStructure.java:116`-`:123`) via `MultiMethodStructure.createMethod` (`:276`-`:319`) | `aReturns`, `aParams`, `annotations` arrays AND their `Parameter` elements | Convention: callers hand over freshly built arrays. The copying variant `createMethodCopyingParameters` (`MultiMethodStructure.java:269`-`:274`, branch fix for backlog row 103) exists precisely because element handover is not enforceable. One caller violates element ownership today - BUG CANDIDATE 2. |
| `xTuple.TupleHandle` constructor (`xTuple.java:683`-`:688`) | Element array `m_ahValue` | Convention: creators hand over ownership; `xTuple` clones internally where it mutates (`:139`, `:387`). But the field is public and callers alias it onward - BUG CANDIDATE 1. |
| `xRTFunction.FullyBoundHandle` (`xRTFunction.java:875`-`:879`) | Bound-argument array `f_ahArg` | Handover convention; bind-time `addBoundArguments` writes into the OUTGOING `ahVar` array, not into `f_ahArg`. Row 147 already bans bound handles from `cloneAs`. LATENT. |
| `xRTDelegate.createDelegate` (`xRTDelegate.java:140`-`:157`) | Caller's `ObjectHandle[]` when `mutability == Constant` (`:152`-`:154`); clones otherwise | DELIBERATE zero-copy for constant arrays; safety = the `checkWrite` mutability guard. Composes badly with row 147 mechanism 5 (per-view `m_fMutable`/`m_mutability` forks). |
| `Frame` constructors (`runtime/Frame.java:140`, `:165`, `:186`) | `ahVar` becomes `f_ahVar` | THE register-file handoff convention: ops build a fresh array per call and surrender it. Correct everywhere except where the array was never fresh - the tuple ops (section 2). |
| `JitMethodDesc` constructor (`javajit/JitMethodDesc.java:56`-`:66`) | All four `JitParamDesc[]` | Build-time metadata handover; single-threaded builder. LATENT, cold. |
| `TypeSystemLoader` constructor (`javajit/TypeSystemLoader.java:43`-`:44`) | `shared` aliased; `owned` **copied** (`new ModuleLoader[owned.length]`); `TypeSystem` then aliases both (`TypeSystem.java:123`-`:124`) | Half-and-half; the asymmetry is undocumented. LATENT, JIT plane. |
| `MultiCondition` constructors (`asm/constants/MultiCondition.java:56`, `:108`) | `ConditionalConstant[]` | Interned-constant convention. LATENT, cold. |
| `OpSwitch`/`JumpInt` deserialization constructors (`OpSwitch.java:77`-`:78`) | Decoded case arrays | Op decode plane, single-owner `Code`. LATENT under row 130's publication guard. |
| `UnresolvedNameConstant` | Now COPIES caller name arrays (branch fix; see [fixed-in-this-branch.md](fixed-in-this-branch.md)) | Fixed precedent for this category. |
| `ArrayConstant` main constructor (`ArrayConstant.java:47`) | Now COPIES (`Arrays.copyOf`) | Fixed precedent. |

### 1.4 Category (d): static shared arrays - constants vs genuinely mutable

All 58 public/protected static arrays are `static final`; zero non-final
statics remain (backlog row 163 closed that). 30 are empty sentinels
(category (e)). The 28 non-empty tables:

| Table | Contents | Written? | Verdict |
| --- | --- | --- | --- |
| `PackedInteger.xB_FACTORS`, `xI_FACTORS` (`javatools_utils/.../PackedInteger.java:1167`, `:1171`) | 8 `PackedInteger` values each | No writes found | LATENT constant table; `PackedInteger` is mutable-until-set, so elements are not even deeply immutable. Should become private + accessor. |
| `ClassTemplate.THIS/OBJECT/INT/STRING/BOOLEAN/BYTES` (`runtime/ClassTemplate.java:2462`-`:2467`; `VOID` at `:2461` is empty) | 1-element `String[]` descriptors | No writes found; `markNativeMethod` (`:2010`-`:2012`) converts to fresh `TypeConstant[]` and does NOT retain them | LATENT: any subclass writing `STRING[0]` changes native method registration globally. Cold path; prime `List.of` candidates. |
| `xArray.ELEMENT_TYPE` (`:1018`), `xRTDelegate.ELEMENT_TYPE` (`:960`) | `{"Element"}` | No writes | Same family as above. |
| `LongLong.ZEROx2`, `OVERFLOWx2` (`runtime/template/numbers/LongLong.java:623`-`:624`) | 2-element result pairs handed to callers of 128-bit math | No writes found | LATENT: these are RETURN VALUES shared process-wide; a single consumer writing a slot corrupts 128-bit arithmetic everywhere. Verified all consumers unpack immediately. Should become private with accessors or records. |
| `Frame.WAIT_FOR_FUTURE` (`:2707`), `WAIT_FOR_IO` (`:2750`), `Utils.WAIT_FOR_RELIEF` (`Utils.java:1906`) | 1-element `Op[]` of stateless anonymous ops (verified: all state read from the executing frame) | No writes | DELIBERATE process-wide immutable implementation tables; document and keep. |
| `Builder.CDs_Int/CDs_Long/CDs_LongLong` (`javajit/Builder.java:2006`-`:2008`) | `ClassDesc[]` | No writes | LATENT, JIT descriptor plane. |
| jitbridge enum tables: `eBoolean.$names/$values` (`:19`-`:20`), `eNullable` (`:16`-`:17`), `eOrdered` (`:16`, `:22`), `FPNumber.Rounding` (`:528`, `:536`), `Array.Mutability` (`Array.java:162`, `:169`) | Enum identity tables generated code binds to (`EnumerationBuilder.java:77`-`:82` emits synthetic `$names`/`$values` properties) | No writes | ABI wall (section 3): public because generated classes address them; a write would corrupt enum identity for the whole classloader. Guarding belongs to the JIT bridge rows (backlog rows 137-139). |
| Related note, not public: jitbridge numeric caches (`UInt8.CACHE` `UInt8.java:25`, `Nibble.CACHE`, `Bit.CACHE`, `Int64.SMALL_CACHE`) | Private static arrays with UNSYNCHRONIZED lazy element population (`UInt8.java:64`-`:66`) | Racy by construction | Not an exposure (private), but a static-state note for the row 137/138 JIT work: two threads can install two distinct `UInt8(5)` instances, splitting identity within one classloader. Final-field semantics make the published objects safe to read; identity `==` is not. |

### 1.5 Category (e): proven-safe empty sentinels

Zero-length, `static final`, no element to overwrite. Listed once so the noise
is separated from the signal permanently. Risk is API-shape only (they teach
callers that public arrays are fine), which the study already covers.

`Handy.EMPTY_BYTE_ARRAY/EMPTY_CHAR_ARRAY/NO_ARGS`
(`javatools_utils/.../Handy.java:2310`-`:2320`), `LongList.NO_LONGS` (`:51`),
`Utils.OBJECTS_NONE/STRINGS_NONE/NO_NAMES` (`runtime/Utils.java:1896`-`:1898`),
`Constant.NO_CONSTS` (`asm/Constant.java:1012`), `Annotation.NO_ANNOTATIONS`
(`:402`), `Parameter.NO_PARAMS` (`:498`), `MethodBody.NO_BODIES` (`:938`),
`TypeConstant.NO_TYPES` (`:8248`), `ConstantPool.NO_TYPES` (alias, `:4239`),
`InjectionKey.NO_INJECTIONS` (`:53`), `FSNodeConstant.NO_NODES` (`:380`),
`Op.NO_OPS/NO_ARGS` (`asm/Op.java:2289`, `:2294`),
`BinaryAST.NO_ASTS/NO_EXPRS/NO_CONSTS/NO_TYPES/NO_REGS/NO_ALLOCS`
(`asm/ast/BinaryAST.java:260`-`:265`), `AstNode.NO_FIELDS`
(`compiler/ast/AstNode.java:2088`), `Expression.NO_LVALUES/NO_RVALUES`
(`:3117`-`:3118`), `VariableDeclarationStatement.NONE` (`:331`),
`JitParamDesc.NONE` (`javajit/JitParamDesc.java:37`),
`xRTClassTemplate.NO_TEMPLATES` (`:599`), `ClassTemplate.VOID`
(`:2461`).

One non-obvious interaction verified safe: `Utils.ensureSize(OBJECTS_NONE, 0)`
returns the shared sentinel itself as a frame's `f_ahVar`
(`xEnum.java:108` and the `_native/fs` call sites); a zero-length register
file cannot be written, so sharing it is harmless.

### 1.6 Category (f), extension: collection getters returning internal mutable Map/List/Set

The same exposure one level up, added to this audit at the owner's request.
Strict-shape scan: 21 getters. The dangerous subset, each read:

| Getter | Returns | Verdict |
| --- | --- | --- |
| `TypeInfoReal.getProperties()` (`:1064`), `getVirtProperties()` (`:1078`), `getMethods()` (`:1356`), `getVirtMethods()` (`:1370`), `getTypeParams()` (`:989`), `getContributionList()` (`:1024`), `getClassChain()` (`:1029`), `getDefaultChain()` (`:1034`), `getChildInfosByName()` (`:2152`) | **Live mutable `HashMap`/`ListMap`/`ArrayList`** - `ensurePropertyOwnership` builds a plain `HashMap` (`TypeInfoReal.java:265`-`:269`) and the constructor aliases the builder's collections for the rest (`:114`-`:122`) | The central shared metadata of the whole runtime, container-visible and cached, handed out with zero enforcement. Borrowed read-only by TypeInfo layering (verified below) - LATENT of the highest value. Contrast: `buildPropertiesByName()` (`:1103`-`:1118`) already returns `Map.copyOf`, so the codebase knows the right shape. Note: `TypeInfoReal` is an upstream JIT-milestone class (commit `70422a197`), not a branch invention; this branch only hardened its publication (rows 89/119). Derived views (`asInto`/`asDelegates`/`excluding`, `TypeInfoReal.java:422`, `:475`, `:566`) share these collections between TypeInfo instances. |
| `Component.getContributionsAsList()` (`asm/Component.java:467`-`:474`), `Component.Contribution.getTypeParams()` (`:2895`-`:2902`), `Component.getChildrenAsList` family (`:1568`), `MultiMethodStructure.methods()` (`:372`), `FileStructure.moduleIds()` (`:294`), `ModuleStructure` (`:332`), `CompositeComponent` (`:44`), `ListMap.entryList` (`javatools_utils/.../ListMap.java:74`) | The raw internal collection - wrapped `Collections.unmodifiable*` **only when assertions are enabled**: `assert (list = Collections.unmodifiableList(m_listContribs)) != null;` | The 8-site assert-only-unmodifiable idiom. Under `-ea` a writing caller throws; in production the same caller silently corrupts ASM structures. The "read-only" javadoc contract literally evaporates at deployment. All current callers verified read-only (`resolveConditionalMixin` at `TypeCompositionStatement.java:2594`, `:2840` iterate only). LATENT with a uniquely testable shape (below). |
| `Fiber.getTokens()` (`runtime/Fiber.java:141`-`:143`) vs `ensureTokens()` (`:148`-`:158`) | Caller fiber's live token map, shared into callee fibers cross-service (`ServiceContext.java:1590`, `Fiber.java:66`-`:73`) | DELIBERATE copy-on-write protocol: async calls clone eagerly, sync calls share with `m_fCloneMap` armed so the first write clones; the read/write split is documented in the two getters' javadoc. Verified: `xService.invokeFindToken` (`xService.java:465`) only reads; `invokeRegisterToken` goes through `ensureTokens()`. Sound, but read-only-ness of `getTokens()` callers is convention-only. |
| `ServiceContext.getFibers()` (`:165`), `Container.getServices()` (`:218`) | Live registries | Only `DebugConsole` iterates (`:1900`, `:1908`). LATENT, diagnostics plane. |
| `ServiceContext.getCallbackMap()` (`:281`) | The row-145 `ConcurrentHashMap` | Concurrent by design after the row-145 fix. Protocol. |
| `ErrorList.getErrors()` (`asm/ErrorList.java:120`-`:122`) | Live error list | `xRTCompiler` copies before adding (`xRTCompiler.java:274`-`:275`) - the caller defends because the API will not. LATENT. |
| `ChildInfo.getAllIdentities()` (`asm/constants/ChildInfo.java:106`-`:108`), `Component.Injector.getInjections()` (`:2802`-`:2804`), `ModuleInfo` node accessors (`tool/ModuleInfo.java:1134`-`:1150`) | Internal sets/lists/maps | Read-only or single-threaded launcher plane. LATENT, cold. |

Owned-vs-borrowed trap, verified twice: `TypeConstant`'s TypeInfo layering
assigns the SAME variable either a fresh owned map (`mapContribProps = new
HashMap<>()`, `TypeConstant.java:3567`) that it then legitimately mutates
(`mapContribProps.remove(idProp)`, `:3601`), or a BORROWED live map from a
cached contributed TypeInfo (`mapContribProps = infoContrib.getProperties()`,
`:3652`-`:3654`) that must never be written and today is only iterated
(`layerOnProps`, `:4043`-`:4056`, reads only). The identical shape exists in
`PropertyClassTypeConstant.java:327`/`:338` (owned) vs `:389`-`:391`
(borrowed). Nothing but positional code review keeps the `remove` in the owned
branch. This is the collection twin of the getRawParams clone convention.

Can it be tested? Yes, three ways, all cheap:

1. Alias-mutation regression tests per accessor: grab the collection/array,
   mutate it, assert the owner did not change (arrays) or that
   `UnsupportedOperationException` is thrown (collections). Red today on
   every raw getter above.
2. A reflective sweep harness in the spirit of `ConstantAdoptionValidator`:
   walk declared public/protected methods returning arrays or collections on
   representative constructed instances, attempt a mutation, and report every
   owner that observed the change. This converts the whole category into one
   enumerated, diffable report.
3. The assert-only-unmodifiable family specifically: run the same mutation
   test with `-ea` and with `-da` and assert identical (throwing) behavior;
   it fails today under `-da`, which is precisely the production
   configuration. The fix - unconditional wrapping or `List.copyOf` - is
   one line per site, eight sites.

## 2. Mutation-Without-Ownership Hunt

Zero one-line `accessor()[i] = v` writes exist; every finding below came from
tracing stored aliases. Findings are grouped by classification.

### 2.1 Deliberate protocols (classified, not accidents)

| Protocol | Exposure | Mutation | Safety argument |
| --- | --- | --- | --- |
| Annotation emit-time back-fill | `Annotation.java:71` (ctor aliases caller array), `getParams()` `:176` | `VariableDeclarationStatement.java:285` (`aConst[j] = new RegisterConstant(...)`), `NewExpression.java:1068` | Placeholder-bearing annotations are unregisterable and hash-uncached until resolved; adoption detaches the array. Documented in-code (`:64`-`:70`) and test-guarded. The model citizen of "deliberate aliasing, documented, with an owner-boundary detach". |
| Unresolved-type in-place resolution | `MethodConstant.getRawParams()` via `ClassStructure.java:3237` | `ClassStructure.java:3242`-`:3243`: `((UnresolvedTypeConstant) atypeParams[i]).resolve(typeFormal)` | Synthetic `equals`-function construction: the method constant must exist before its type params can be instantiated (comment at `:3202`-`:3204`), so `UnresolvedTypeConstant` placeholders are resolved through the raw signature array, then `resolveTypedefs()` (`:3247`) cleans up. Element mutation, not slot mutation - but reached through the raw accessor. Same family as the back-fill; UNDOCUMENTED at the accessor. |
| GenericHandle shared field arrays | `ObjectHandle.java:680` (`m_aFields` shared across views), `:655` (sparse per-view overrides) | Every field write through any access view | View semantics REQUIRE the sharing (backlog rows 104/105); the remaining per-view desyncs are the five named row-147 mechanisms, tracked as must-fix. |
| Register-delegating Var writes | `Frame.f_ahVar` public | `xVar.java:173` (`frameRef.f_ahVar[nVar] = hValue`), dereference rewrite `xRef.java:1079`/`Frame.java:2539` | A register-bound `Var` IS an alias of a frame slot by spec. Fiber confinement is the guard; the view-clone desync is row 147 mechanism 1. |
| Frame register-file handoff | `Frame.java:140`/`:165`/`:186` (`f_ahVar = ahVar`) | Callee register writes (`Frame.assignValue`) | The op convention: every caller builds a fresh `ObjectHandle[]` per call and surrenders ownership. Nowhere written down; violated by the tuple ops (2.3). |
| Cross-frame return-register setup | `Frame.f_aInfo` public | `ServiceContext.java:795` writes `frame.f_aInfo[nVar]` | The service context is the frame's scheduler/owner; same confinement argument as above. |
| Constant-array zero-copy wrap | `xRTDelegate.createDelegate` `:152`-`:154` aliases `Mutability.Constant` content | none permitted | `checkWrite` (`:529`-`:541`) raises `readOnly` for Constant delegates and the mutability property only narrows (`:187`-`:195`, `Constant` is ordinal 0 of `{Constant, Persistent, Fixed, Mutable}`, `xArray.java:1016`). Sound UNTIL a view desync forks `m_mutability` (row 147 mechanisms 4/5) - then the "constant" storage, which may BE the `ClassComposition` field-name cache (`ClassComposition.java:444` -> `xConst.java:391` -> `xArray.java:873`-`:875`), is writable from natural code. The composition cache aliasing is therefore only as safe as row 147 is fixed. |
| JumpVal switch tables | container-scoped cache API | `Container.getRuntimeOpCache/putRuntimeOpCacheIfAbsent` (`Container.java:143`, `:158`) | Fixed shape from backlog row 91: decoded-op-keyed runtime state lives under the executing container, not on the shared op. |
| Fiber token map copy-on-write | `Fiber.getTokens()` | callee clones before write (`Fiber.java:66`-`:73`, `ensureTokens` `:148`-`:158`) | Documented read/write accessor split; sync-call sharing is safe because the caller fiber is blocked. |
| Op address/link resolution | `OpSwitch.m_aofCase`/`m_acExits` etc. | `resolveAddresses` (`OpSwitch.java:106`-`:135`) at link time | Row 130/160 publication guard (`assertReadyForRuntime`) polices the lifecycle boundary. |

### 2.2 Latent hazards (mutation possible, none observed)

The high-value subset; every row was read, and the thing that keeps it safe
today is named.

| # | Exposure | What keeps it safe today |
| --- | --- | --- |
| L1 | `SignatureConstant.getRawParams()/getRawReturns()`, `ParameterizedTypeConstant.getParamTypesArray()` - interned metadata arrays returned raw to 196 call sites | A hand-maintained clone-before-write idiom at every mutating consumer (`InvocationExpression.java:788`, `:1062`, `OpCallable.java:637`, `TypeConstant.java:1014`, `Expression.java:781`/`:817` `fCloneActual`, `ForEachStatement.java:476`, `TernaryExpression.java:557` - all verified). One forgotten `.clone()` writes into an interned, hash-cached, container-shared constant. The raw alias at `InvocationExpression.java:881` travels ~180 lines before its guard clone at `:1062`. |
| L2 | `MethodInfo.getChain()`/`getOptimized*Chain()` cached `MethodBody[]` returned raw; retained by `CallChain.f_aMethods` | All consumers read-only (verified by alias scan). These arrays are dispatch truth for every container sharing the TypeInfo. |
| L3 | `TypeInfoReal` collection getters return live `HashMap`s (1.6) and the owned-vs-borrowed `mapContribProps` trap (`TypeConstant.java:3601` vs `:3652`) | `layerOnProps`/`layerOnMethods` iterate only; the mutating `remove` sits in the owned-map branch. Positional discipline, zero enforcement. |
| L4 | The 8 assert-only-unmodifiable ASM getters (`Component.java:472` et al.) | Callers happen to be read-only; production (`-da`) removes the wrapper entirely. |
| L5 | Non-empty public static tables: `LongLong.ZEROx2/OVERFLOWx2`, `PackedInteger.x*_FACTORS`, `ClassTemplate` descriptor arrays, `ELEMENT_TYPE`, `Builder.CDs_*`, jitbridge `$names/$values` | Nothing but the absence of a writer. One write is process- or classloader-global corruption. |
| L6 | `ClassComposition` field-name `StringHandle[]` cache aliased into Constant-mutability delegates reachable by natural code | `checkWrite` + the not-yet-fixed row 147 mutability-fork mechanisms. |
| L7 | Tuple-arg ISA ops `Call_T0/T1/TT/TN`, `Invoke_T0/T1/TN`, `Construct_T` alias `TupleHandle.m_ahValue` into callee register files via `Utils.ensureSize` (`Call_T0.java:125`/`:135`/`:140`/`:145`, `Call_T1.java:143`/`:155`, `Call_TT.java:143`/`:155`, `Call_TN.java:148`/`:160`, `Invoke_T0.java:91`-`:96` -> `CallChain.java:199`/`:213`, `Construct_T.java:102`) | The current compiler NEVER emits them: `m_fTupleArg` is declared but never assigned (`InvocationExpression.java:3055`, `NewExpression.java:1636`) and `Construct_T` emission is `UnsupportedOperationException("TODO")` (`InvocationExpression.java:1604`). The ops remain fully decodable from any `.xtc` (`Op.java:1545`-`:1548`, `:1562`-`:1565`, `:1588`), so this is one serialized module away from being live - and the reflection path (BUG 1) is the same mechanism already reachable today. The cross-service amplification: `FunctionProxyHandle.callT` (`xRTFunction.java:1192`-`:1204`) forwards the caller-side array into `sendInvoke1Request`, whose request op hands the SAME array to the callee service (`ServiceContext.java:1243`-`:1251`) - `validatePassThrough` checks elements, never the container - so a retained caller array would become a two-service unsynchronized register file. |
| L8 | `Version.ints` protected non-final + `getIntArray()` raw + instance-sharing constructor (`Version.java:538`, `:594`, `:604`) inside an equals/hash-bearing key type | No writer exists today. |
| L9 | `sortedMethods()/sortedProperties()` return fresh arrays of LIVE map entries (`TypeInfoReal.java:1362`) | No caller invokes `Entry.setValue`. |
| L10 | `xTuple.TupleHandle.m_ahValue` public non-final, read raw by 10+ classes | Every reader except the tuple ops treats it read-only or clones (`Proxy.convertTupleResult` clones copy-on-write, `Proxy.java:330`-`:345`; `ServiceContext.java:1818` validates then freezes). |

Compiler-plane latents (AST protected fields, `CmpChainExpression.operators`,
`MultipleLValueStatement.aGroundLabels`, `EvalCompiler.getArgs`) stay parked
under the existing "compiler is request-confined until the incremental
compiler work" umbrella (backlog row 162) and are not repeated here.

### 2.3 Bug candidates

Two new candidates, both traced end to end, both present on `master`
(verified by `git show master:...` for the load-bearing files).

#### BUG CANDIDATE 1: tuple element storage becomes a callee register file

- Exposure: `xTuple.TupleHandle.m_ahValue` public
  (`javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:681`);
  aliasing primitive `Utils.ensureSize` returns the caller's array unchanged
  whenever it is already big enough
  (`javatools/src/main/java/org/xvm/runtime/Utils.java:209`-`:218`).
- Reachable-today path: natural reflection `Method.invoke(target, argsTuple)`
  is implemented by `xRTMethod.invokeInvoke`, which takes the tuple's array
  raw - with an in-code confession: `ObjectHandle[] ahPass =
  hTuple.m_ahValue;  // TODO GG+CP do we need to check these?`
  (`javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:211`)
  - and forwards it to `CallChain.invokeT`, where
  `Utils.ensureSize(ahArg, getMaxVars())`
  (`javatools/src/main/java/org/xvm/runtime/CallChain.java:241`) returns the
  tuple's own array whenever the target method allocates no registers beyond
  its parameters (`getMaxVars()` "including the parameters",
  `asm/MethodStructure.java:1140`-`:1143`). That array is then installed as
  the callee frame's live register file (`Frame.java:140`/`:165`/`:186`).
- Mutation site: any register write in the callee - `Frame.assignValue`
  writing `f_ahVar[nVar]`, i.e. any assignment to a parameter in a method
  with no additional locals, or any register-bound `Var` write
  (`xVar.java:173`).
- Concrete wrong outcome: the caller's tuple visibly changes element values
  after the call, even when the tuple reports immutable
  (`TupleHandle.m_fMutable` is never consulted by frame register writes).
  Worst case: the tuple is a constant literal, whose handle is cached
  container-wide by the const heap (generic caching path,
  `runtime/ConstHeap.java:117`-`:133`) - the callee's parameter reassignment
  then rewrites the cached constant, corrupting the literal for every
  subsequent use in the container. That is cross-execution state corruption,
  exactly the class of defect that blocks same-JVM reuse.
- The defense already exists 40 lines away, proving intent:
  `xRTFunction.invokeInvoke` (the `Function.invoke` twin) special-cases
  exactly this: `ahVar = cArgs == cVars ? ahArg.clone() :
  Utils.ensureSize(...)` (`xRTFunction.java:254`). The reflection Method
  path and all seven tuple-arg ops (L7) lack the clone.
- Reachable on master: yes - `Call_T0`, `Utils.ensureSize`, and
  `xRTMethod.invokeInvoke` are byte-identical in the relevant lines on
  `master`. Blocks reentrancy: yes (container-wide constant corruption), and
  L7's cross-service forwarding upgrades it to a data race once tuple-arg
  ops are ever emitted.
- Fix shape (small): clone in `xRTMethod.invokeInvoke`, and either give
  `ensureSize` an always-copy variant for tuple-sourced arrays or add the
  `cArgs == cVars ? clone : ...` guard at the seven op sites. Test shape:
  an XTC test invoking, via reflection, a one-parameter/no-locals method
  that reassigns its parameter, asserting the argument tuple (and a second
  use of the same tuple literal) is unchanged - red on master.

#### BUG CANDIDATE 2: short-hand property methods share the super method's `Parameter` elements across modules

- Exposure: `MethodStructure.getReturn(i)`/`getReturnArray()` hand out live
  `Parameter` objects (`asm/MethodStructure.java:468`-`:470`), and
  `createMethod` aliases both the array and its elements into the new
  `MethodStructure` (`asm/MultiMethodStructure.java:316`-`:319`,
  `asm/MethodStructure.java:116`-`:123`).
- Sharing site: the short-hand property method path in the compiler.
  `MethodDeclarationStatement.resolveNames` finds `methodSuper` via
  `findRefMethod(property, annotations, ...)`
  (`compiler/ast/MethodDeclarationStatement.java:474`) - a method on
  `Ref`/`Var` or an annotation mixin, typically living in ANOTHER loaded
  module's `FileStructure` (ecstasy.xtclib or a library). It then copies the
  super's return `Parameter` objects by reference: `aReturns[i] = param`
  (`MethodDeclarationStatement.java:521`) and creates the synthetic override
  with them (`:531`-`:533`). Only the special case "terminal `Referent`
  return" gets a fresh `Parameter` (`:512`-`:519`); any other return type -
  e.g. a mixin method returning `FutureVar<Referent>` or `Int` - shares.
- Mutation site: `Parameter.registerConstants` REWRITES the shared
  parameter's fields in place - `m_constType = pool.register(m_constType)`
  etc. (`asm/Parameter.java:408`-`:413`) - when the USER module is
  assembled. `pool.register` on a foreign constant adopts it into the user
  module's pool, so the library method's live return `Parameter` ends up
  holding a `TypeConstant` owned by the user module's pool.
- Concrete wrong outcome: the loaded library `FileStructure` (shared across
  compilations in one JVM - the incremental/same-JVM compile target) is
  silently re-owned member by member; a later compilation against the same
  loaded ecstasy structure resolves that parameter's type through the wrong
  pool, and re-assembling the library would write `Constant.indexOf` against
  a foreign pool. This is the exact wrong-owner family of backlog rows
  101-103, one call site over.
- Reachable on master: yes - `MethodDeclarationStatement.java` is identical
  in this region on `master` (verified). Reachability requires a short-hand
  property method whose ref-method return is not the bare `Referent`
  terminal; the common `get()` case is defended by the `:512`-`:519` fresh
  parameter, which is why this has not burned visibly.
- Blocks reentrancy: yes for same-JVM compiler reuse (shared loaded module
  structures); inert for a single cold compile per JVM.
- Fix shape (tiny, precedented): route this call site through
  `createMethodCopyingParameters(...)`
  (`MultiMethodStructure.java:269`-`:274`) / `Parameter.copyFor(...)`, the
  exact machinery this branch built for the delegated-method twin (backlog
  row 103). Test shape: compile a module with a short-hand property method
  over a library mixin with a non-Referent return; assert the library
  method's `Parameter.getType().getConstantPool()` is still the library
  pool afterward - red on master.

#### Previously-tracked, confirmed here, not double-counted

- `ConstHeap`'s raw `HandleConstant` serve (fixed this branch, ledger row 36).
- The five `cloneAs` view-lifecycle mechanisms (must-fix row 147), of which
  mechanism 4 (delegate storage-pointer forks) and mechanism 5 (per-view
  mutability bit) are the array-specific halves that also underwrite L6 in
  this audit.

## 3. XTC Bytecode And JIT Representation Assumptions

The walls an alternative representation must respect. Each is a place where
"raw Java array" is not an implementation detail but a contract.

1. **Register files are `ObjectHandle[]` by construction.**
   `Frame.f_ahVar/f_aInfo/f_aiReturn/f_anNextVar` (`Frame.java:69`-`:80`) are
   indexed on every op dispatch; every `createFrame*`/`call1/callT/callN`
   signature takes `ObjectHandle[] ahVar` and installs it verbatim
   (`Frame.java:140`/`:165`/`:186`). Every native
   (`invokeNative1/N/NN/T`) receives `ObjectHandle[] ahArg`. Changing this
   type touches every op, every template, every native - hundreds of files.
   Cannot change cheaply; the ownership convention (fresh array per call)
   must be documented and enforced instead.
2. **Argument passing IS the register file.** `Utils.ensureSize`
   (`Utils.java:209`) exists precisely to let a caller-built argument array
   BECOME the callee frame - the zero-copy design at the heart of BUG 1. Any
   wrapper type here is a per-call allocation on the hottest path in the
   interpreter.
3. **Decoded code is `Op[]`.** `MethodStructure.getOps()` (`:668`), decoded
   at `:2206`-`:2212`, executed as `f_aOp[iPC]`; jump/switch ops hold
   `Op[]`/`int[]` case tables resolved in place at link time
   (`OpSwitch.resolveAddresses`). The serialized opcode table
   (`Op.java:1545`-`:1600`) fixes the ISA, including the seven tuple-arg ops
   whether or not today's compiler emits them: any migration must keep those
   ops' semantics or version the format.
4. **The object representation is a field array.** `GenericHandle.m_aFields`
   with view sharing and sparse overrides (`ObjectHandle.java:655`, `:680`)
   is load-bearing for maskAs/revealAs/struct semantics. Rows 104/105/147
   already define the target model (shared canonical cells); a `List` adds
   indirection to every field read.
5. **Delegate storage is primitive arrays with an aliasing constructor.**
   `xRTDelegate` and the typed delegates store `byte[]/long[]/char[]/
   double[]/String[]/ObjectHandle[]` and deliberately alias
   Constant-mutability inputs (`xRTDelegate.java:152`-`:154`). Boxing is
   disqualifying; the fix budget goes to the row-147 mutability-cell
   redesign, not to the storage type.
6. **JIT calling convention.** Generated methods take `(Ctx, slots...)` per
   `JitMethodDesc.standardMD/optimizedMD` computed from `JitParamDesc[]`
   (`JitMethodDesc.java:70`-`:75`); overflow multi-returns are the PUBLIC
   `Ctx.i0..i7/o0..o7/iN/oN` fields addressed by name from generated
   bytecode (`Ctx.java:45`-`:62`). Frozen ABI until the JIT regenerates.
7. **JIT bridge ABI.** Generated enum classes bind to public static
   `$names`/`$values` arrays (`EnumerationBuilder.java:77`-`:82`; bridge
   tables in `eBoolean`/`eNullable`/`eOrdered`/`FPNumber`/`Array`), and
   bridge array classes expose public `$storage`. These are generated-code
   contracts, not style choices; they belong to backlog rows 137-139.
8. **JIT reads interpreter metadata arrays raw.** `BuildContext.callChain`
   is a `MethodBody[]` (`BuildContext.java:255`), `Builder` consumes
   `UInt8ArrayConstant.getValue()` byte arrays (`Builder.java:582`) and
   `ClassDesc[]` descriptor tables. Any metadata-side wrapper must keep an
   `unsafeArray()`-grade escape hatch or the JIT build path allocates.
9. **Serialization contracts.** `assemble`/`disassemble` build and consume
   arrays positionally throughout ASM (`Parameter.assemble`,
   `MethodStructure` local constants, `OpSwitch` case offsets). These are
   file-format walls, orthogonal to in-memory representation but sharing the
   same types.

## 4. Alternative Designs And Incremental Transition

### 4.1 Options evaluated

**(a) Repo-owned wrapper (`FrozenArray<T>` / `ArrayView<T>`).** A final class
holding a private array: `get(i)`, `size()`, iteration, `copy()`, plus an
explicit `unsafeArray()` escape hatch for hot consumers, constructed via
`FrozenArray.adopt(T[])` (documented ownership transfer) or
`FrozenArray.copyOf(T[])`.

- Element-type genericity: full - `FrozenArray<TypeConstant>` finally says
  what the array means; erasure casts collapse into the one constructor.
- Cost: one extra object and one pointer hop per container; `get(i)`
  inlines to an array load after JIT warmup. Fine for metadata; NOT fine
  for `f_ahVar`, field arrays, or delegate storage, which must stay raw
  (walls 1-5).
- JIT compatibility: safe on the metadata plane if `unsafeArray()` exists
  for `BuildContext`-style consumers; irrelevant to generated code, which
  never sees repo types it doesn't already bind to.
- Verdict: the right tool for interned/cached metadata arrays
  (`getRawParams`, chains, annotations) where identity and zero-copy reads
  matter but writes must die. Subclassing `AbstractList` instead would buy
  `Collection` interop at the cost of a fatter API surface inviting
  `set()`-shaped confusion; a small bespoke type states the contract better.

**(b) `List.of`/`List.copyOf` at metadata boundaries.** Best where callers
iterate rather than index, and where null elements are illegal anyway
(`List.of` rejects nulls - which is a feature against the partial-fill
anti-pattern, but a blocker for legacy null-slot arrays like the multi-value
constant-folding buffer noted in
[must-audit-backlog.md](must-audit-backlog.md)). Right answer for the
collection getters of 1.6 (`Map.copyOf`/unconditional
`Collections.unmodifiable*`) and for descriptor tables (`ClassTemplate`
signatures, `PackedInteger` factors). Wrong answer for indexed hot chains
unless profiling clears it.

**(c) Defensive copies at accessor boundaries only.** Cheapest to retrofit,
keeps every signature. Two real costs: allocation per call on accessors that
are hit in loops (`getRawParams` is called inside signature resolution -
copying there is measurable), and it FIXES nothing about writers who
legitimately need the shared array (the deliberate protocols). Use only for
cold accessors (`getConstants` already does; `Version.getIntArray`,
`getValue()` byte payloads).

**(d) Non-options.** `VarHandle`-guarded arrays add release/acquire
discipline, not immutability - solves the wrong problem. JEP 8261007-style
frozen arrays remain speculative with no committed JDK release; waiting on
them is not a plan. `Collections.unmodifiableList` VIEWS over live lists
preserve aliasing (the study already bans them where snapshots are needed) -
and the assert-only variant found in 1.6 is strictly worse than either
choice.

### 4.2 Staged migration

| Stage | Scope | Mechanism | Effort | Risk |
| --- | --- | --- | --- | --- |
| 0. Bug fixes first | BUG 1 (tuple/register aliasing: 1 clone in `xRTMethod`, guards at 7 op sites or one `ensureSizeCopy` variant), BUG 2 (one call site to `createMethodCopyingParameters`) | targeted, with red-on-master tests | S (each is a PR-of-one-hunk plus test) | Low; both have in-tree precedent (`xRTFunction.java:254`, row 103 machinery) |
| 1. Assert-only-unmodifiable eradication | 8 sites (1.6) | make wrapping unconditional, or better `List.copyOf` at construction; add the `-da` mutation test | S | Low; callers verified read-only |
| 2. Cold raw accessors -> copy or wrapper | `getValue()` byte payloads, `ArrayConstant.getValue`, `Version`, `ErrorInfo.getParams`, `EvalCompiler.getArgs`, `LongLong.ZEROx2`/`PackedInteger` factors/`ClassTemplate` descriptors to private+`List.of` | (b)/(c) | S-M (mechanical, ~20 sites, each independently shippable) | Low |
| 3. Metadata arrays with clone conventions -> `FrozenArray` | `SignatureConstant` params/returns, `ParameterizedTypeConstant` params, `Annotation` params (KEEPING the documented back-fill via an explicit mutable-until-resolved builder state), `MethodInfo`/`PropertyInfo` chains, `TypeInfo` annotation arrays | (a), with `unsafeArray()` for the JIT build path; delete the scattered `.clone()` guards as each accessor converts | M-L: ~200 consumer sites compile-break per family; mechanical but wide. Requires the API-design pass for the two deliberate write-back protocols (annotation back-fill, unresolved-type resolution) | Medium; per-family PRs keep it reviewable |
| 4. Collection getters -> immutable snapshots | `TypeInfoReal` getters (`Map.copyOf` at construction - `ensure*Ownership` already allocates fresh maps, so freezing is nearly free), `Component` family, registries stay live-but-concurrent with documented ownership | (b) | M (the owned-vs-borrowed layering code keeps its builder-plane mutable maps; only publication freezes) | Medium: must not freeze the builder-phase maps the layering legitimately mutates |
| 5. Never move | `Frame` registers and arg handoff, `GenericHandle.m_aFields`, delegate storage, `Op[]` streams, `Ctx` overflow slots, bridge `$storage`/`$names`/`$values`, serialization buffers | document the ownership conventions (fresh-array-per-call; view-cell redesign per row 147); enforce with the reflective sweep harness and targeted tests, not with types | ongoing | The row-147 fixes carry the real safety here |

Honest bottom line on the full change: converting "all arrays" is not a
project that terminates - the runtime currency (stage 5) is structurally
array-shaped and correctly so. The tractable 80% of the risk sits in stages
0-2 (small, independently shippable, low regression risk) plus stage 3 for
the four or five metadata families where a forgotten `.clone()` is a
container-corruption bug. Stage 3 is the only genuinely wide one: it is a
few hundred mechanical call-site edits per family, gated on designing two
write-back protocols honestly. Stages 0-2 are a week-scale effort combined;
stage 3 is a multi-week background series; stage 4 rides the TypeInfo
plane's existing hardening cadence.

## 5. Verdicts

| Category | Count | Worst finding | Reentrancy-blocking? |
| --- | --- | --- | --- |
| (a) public/protected array fields | 47 instance + 58 static | `xTuple.TupleHandle.m_ahValue` public non-final -> BUG 1 | Yes (BUG 1 corrupts container-cached constants) |
| (b) raw-array-returning accessors | 21 strict (+4 delegating) of 358 array-returning methods | `getRawParams` clone-convention (L1); `MethodStructure.getReturn` element sharing -> BUG 2 | BUG 2 blocks same-JVM compiler reuse |
| (c) arrays accepted and retained | 12 constructor families | tuple/`Frame` handoff convention violated by tuple ops (feeds BUG 1) | Yes via BUG 1 |
| (d) static shared arrays (non-empty) | 28 | `LongLong.ZEROx2`/jitbridge `$values` - one write is process/classloader-global | Latent only |
| (e) empty sentinels | 30 | none - proven safe | No |
| (f) collection getters (extension) | 21 raw + 8 assert-only-unmodifiable | `TypeInfoReal` live `HashMap`s; `-da` strips the ASM getters' wrappers | Latent; L3/L4 are the compiler/runtime metadata plane |
| Mutation findings | 10 DELIBERATE protocols, 10 named LATENT hazards, 2 BUG CANDIDATES | - | - |

### New bug candidates for the must-fix list

1. **Tuple storage served as callee register file.** Exposure
   `xTuple.java:681` + `Utils.java:209`-`:218`; reachable today via
   `xRTMethod.invokeInvoke` (`xRTMethod.java:211` -> `CallChain.java:241`);
   latent via the seven tuple-arg ISA ops and the `FunctionProxyHandle`
   cross-service forward. Callee register writes mutate the caller's
   (possibly immutable, possibly const-heap-cached) tuple. On master. Fix:
   clone where `ensureSize` would alias, exactly as `xRTFunction.java:254`
   already does.
2. **Short-hand property method shares super-method `Parameter` elements
   cross-module.** `MethodDeclarationStatement.java:521` +
   `MultiMethodStructure.java:316`-`:319`; mutation at
   `Parameter.java:408`-`:413` on assembly re-owns the loaded library's
   parameter constants into the user module's pool. On master. Fix: use
   `createMethodCopyingParameters`/`Parameter.copyFor` (row 103 machinery).

No other array mutation across an ownership boundary was found that is not
already a tracked row (`HandleConstant` serve - fixed; row 147 mechanisms -
open must-fix) or a documented protocol.

### What this means for the fork-vs-PRs decision

This audit strengthens the case that the array problem is not one big
rewrite but a small must-fix core plus a long mechanical tail - which favors
PRs over a fork. The two bug candidates and the stage 0-2 hardening are
master-portable, hunk-sized, and each carries a red-on-master test, exactly
the shape that has been landing from this branch already. The wide stage-3
`FrozenArray` migration is the only fork-flavored temptation, and it should
be resisted: it compile-breaks hundreds of call sites per family, which is
precisely the kind of change that must ride master in reviewable
family-sized PRs or it will never merge. The genuinely fork-scoped work
remains what it was before this audit - the row-147 view-cell redesign and
the JIT generated-static/bridge rows - because those change runtime
representation semantics, not API shape. Everything newly found here should
go to master as ordinary PRs, with the reflective alias-mutation sweep
harness added early so the category stays closed after it is emptied.


## Stage 3 Progress (2026-08-25)

- `FrozenArray<T>` landed in `javatools_utils` (`org.xvm.util.FrozenArray`) per
  option (a): adopt/copyOf construction, size/get/iteration, `copy()`, and the
  documented read-only `unsafeArray()` escape hatch for hot consumers.
- **Family A (SignatureConstant params/returns) converted**: frozen fields,
  wrapper-returning `getRawParams()/getRawReturns()` (+ MethodConstant
  delegates), the `getReturns()` live-`Arrays.asList` view eliminated (was a
  set()-writable window into interned storage - red on master), wrapper-sharing
  signature/pool overloads replacing raw-array aliasing between constants.
  Red-verified contract test `SignatureConstantTest.signatureTypeStorageIsFrozen`.
- Remaining escapes into Family B (`ensureTupleType(sig.getRawReturns()
  .unsafeArray())` at MethodConstant/Frame) are marked and close when
  ParameterizedTypeConstant converts.
- **Family B (ParameterizedTypeConstant / getParamTypesArray surface)
  converted**: frozen field + wrapper-returning base API (`NO_TYPES_FROZEN`
  empty default), `getParamTypes()` live-view eliminated, frozen-sharing
  overloads on `ensureParameterizedTypeConstant`/`adoptParameters`, and the
  first clone-convention death (TerminalTypeConstant's defensive `.clone()`
  before parameter adoption replaced by safe wrapper sharing). Red-verified by
  `ParameterizedTypeFrozenTest`.
- **Family C (MethodBody chains) evaluated and DEFERRED 2026-08-26**: all
  consumers re-verified read-only, the cache write side is already
  synchronized + volatile + synthesis-windowed (ledger rows 44/51), and the
  retention path (`CallChain`) must keep raw-array indexing for dispatch -
  the conversion is defense-in-depth only. Per the series' own lean rule
  ("if it grows beyond a day, drop it"), the read-only verification plus the
  escape ratchet stand in for the wrapper.
- **Family C deferral rationale PARTLY INVALIDATED 2026-08-28** (`ec43d7dfb`).
  The load-bearing half of the deferral was "`CallChain` must keep raw-array
  indexing for dispatch", which priced the conversion at 28 open-coded accesses
  (`.length` x12, `[0]` x11, `[nDepth]` x5). Those are now routed through two
  accessors, `head()` and `bodyAt(int)`, so the *width* argument no longer
  holds - the field conversion is a 2-method change plus a small boundary
  (constructor adoption, `MethodBody.isFieldChain(...)`, `PropertyComposition`).
  The encapsulation also fixed a real inconsistency it exposed: `getTop()`
  guarded the empty chain but `getProperty()` indexed `[0]` unguarded, so it
  threw `ArrayIndexOutOfBounds` where its siblings returned null, and empty
  chains ARE constructible.
- **Family C re-priced 2026-08-28 - and `CallChain` is the wrong target.**
  `CallChain.f_aMethods` is `protected final` and, after `ec43d7dfb`, never read
  outside its two accessors: it does not escape, and each `CallChain` is
  per-composition. The genuine Family C exposure is **`MethodInfo`**, which is
  interned in `TypeInfo` and shared across containers:
  - `getChain()` returns `m_aBody` **raw** to 10 call sites
    (`TypeInfoReal` x3, `MethodInfo` x2, `TypeConstant` x3, `BuildContext`,
    `MethodDeclarationStatement`).
  - `ensureOptimizedMethodChain()` returns the `m_aBodyResolved` cache **raw**
    to 4 external consumers. Ledger rows 44/51 made that cache safely
    *published*; they did not make it *immutable*, so every consumer still holds
    a writable alias of interned runtime metadata.
  - `BuildContext.callChain` is a **`public final MethodBody[]`** holding the
    escaped array - a public mutable alias of shared metadata, and the exact
    hazard shape stage 3 exists to remove. `public final` on a `FrozenArray`
    would be genuinely safe; on `MethodBody[]` it is not.
  All 10 consumers re-verified read-only (`Handy.prepend` and
  `Collections.addAll` copy out; the rest are for-each or indexing), so the
  conversion is behavior-preserving and the value is structural enforcement,
  not a bug fix. Priority order if resumed: `MethodInfo.m_aBody` (the shared
  one) > `m_aBodyResolved` > `CallChain.f_aMethods` (now trivial, lowest value).
- **Escape ratchet standing**: `FrozenArrayEscapeRatchetTest` pins the
  `unsafeArray()` count (115) as a ceiling that only moves down, so the
  stage-3 contract cannot erode and tier-2 sites can tighten incrementally.
- Annotation params stay gated on the builder-state redesign; TypeInfo
  annotation arrays already closed by stage-2 cloning. Stage 3 is therefore
  COMPLETE for the families the audit priced as worth the width.

## Stage 4 Candidate Survey (2026-08-28)

Complete sweep for **direct field escapes** — accessors whose body hands out
stored array state rather than a fresh allocation. Detector: `public|protected`
accessor returning `T[]`, widened by hand for lazy-compute bodies
(`ModuleStructure.getDigest`) and delegating ones (`FSNodeConstant.getFileBytes`).
Factories (`toByteArray`, `grow`, `reverse`, `extractBits`, `getBits(handle,…)`)
are NOT escapes and are excluded.

### Object arrays — 9 escapes, exactly ONE trivial

| Site | Field | Verdict |
| --- | --- | --- |
| `PropertyInfo.getPropertyBodies()` | `private final PropertyBody[] m_aBody` | **TRIVIAL — the one clean candidate** |
| `MethodInfo.getChain()` | `private final MethodBody[] m_aBody` | Family C; 10 consumers |
| `MethodStructure.getParamArray()` | `m_aParams` | Non-final, reassigned — see below |
| `MethodStructure.getReturnArray()` | `m_aReturns` | ditto |
| `MethodStructure.getAnnotations()` | `m_aAnnotations` | ditto |
| `MethodStructure.getLocalConstants()` | `m_aconstLocal` | ditto |
| `Parameter.getAnnotations()` | `m_aAnnotations` | ditto |
| `Annotation.getParams()` | `m_aParams` | ditto |
| `PropertyStructure.peekPropertyAnnotations()` | `m_aPropAnno` | `transient`, rebuilt |

**`PropertyInfo.m_aBody` is the single trivial conversion**: already `final`,
**one** write (the constructor), 5 consumers all verified read-only
(`TypeInfoReal` for-each; `PropertyClassTypeConstant` and `UnionTypeConstant`×2
index; `PropertyInfo.create` uses `Handy.prepend`, which copies out), and —
unlike `MethodInfo` — **no lazy resolved cache**, so none of the row 44/51
publication subtleties apply. It is an interned `TypeInfo` member with the same
cross-container sharing profile as `MethodInfo`, so it belongs to Family C and
should land WITH it, not alone: converting only the easy twin leaves the ratchet
counting the harder one indefinitely.

### Why the structure-type fields are not final (investigated, not assumed)

The seven `MethodStructure`/`Parameter`/`Annotation`/`PropertyStructure` fields
are non-final for **three distinct structural reasons**, each already mapped to
an existing enhancement. This is why they are not cheap `FrozenArray` targets:

1. **Two-phase deserialization** (`MethodStructure:2023` in `disassemble`,
   `Annotation.resolveConstants:109/115`). A `Component` is constructed from the
   stream header before its body is read, and the binary stores constant
   *indices* (`m_aiParam`) that cannot be resolved to `Constant` objects until
   the whole pool has been read. A constructor cannot fill these fields because
   the data does not exist yet. Making them final requires a **builder/two-object
   split** across the `Component` disassembly contract — wide, and a format-protocol
   change, not an initialization tidy-up.
2. **`registerConstants(pool)` rewrites the field by design**
   (`Annotation:369`, `m_aParams = registerConstants(pool, m_aParams)`): the
   assembly pass replaces each constant with its pool-interned equivalent,
   mutating a nominally-immutable interned constant. This is an **E3** hazard and
   the codebase already half-knows it — `resolveParams:220` guards with
   `isHashCached()` and, when the hash has been observed, returns a *different*
   pooled `Annotation` instead of mutating ("we must never change the
   hashCode/equality for already registered constants"). The field is non-final
   because the design is *mutate-until-first-observation* rather than
   *never-mutate*; the immutable path already exists beside it.
3. **Clone-based copying with owner fix-up** (`MethodStructure:1803` copy
   constructor aliases, then `cloneBody:1832` / `copyParametersBeforePublication:1860`
   overwrite via `copyParametersFor(that, …)`). Construct-then-fix-up, because
   the copy helper needs the fully-constructed target as the `Parameter` owner.
   This is the **E6** target — but note the trap: hoisting `copyParametersFor(this, …)`
   into the constructor makes it a **`this`-escape**, since the helper calls
   `source[i].copyFor(method)` on a partially-constructed method. That collides
   with **E9**'s fatal this-escape lint. So (3) is fixable only as E6+E9 together.

Net: the non-finality is load-bearing protocol, not sloppiness. These fields
follow E3/E6/E9 and cannot be picked off individually by stage 4.

### Primitive arrays — the `FrozenArray<T>` gap

`FrozenArray<T>` is generic and therefore cannot hold primitives at all, so
these escapes are currently **unclosable** and invisible to the ratchet.
Proposed (user, 2026-08-28): add primitive specializations. The survey supports
three, and the motivation is stronger than for the object case, because two
sites are **already paying a defensive copy on every call** that a frozen type
would eliminate:

| Proposed type | Site | Today |
| --- | --- | --- |
| `FrozenByteArray` | `UInt8ArrayConstant.getValue()` | **raw escape**, pool-interned constant |
| | `FPNConstant.getValue()` | **raw escape**, pool-interned constant |
| | `Float128Constant.getValue()` | **raw escape**, pool-interned constant |
| | `FSNodeConstant.getFileBytes()` | transitively the same field |
| | `ModuleStructure.getDigest()` | **raw escape** after lazy compute |
| `FrozenCharArray` | `xString.getValue()` | **raw escape** of immutable-String backing |
| `FrozenIntArray` | `BindFunctionAST.getIndexes()` | **raw escape** |
| | `Version.getIntArray()` | `ints.clone()` — **copy on each of 14 call sites** |
| | `EvalCompiler.getArgs()` | `m_aiArg.clone()` — **copy on each of 4 call sites** |

The byte/char rows are the higher-value half: three of them are **`ConstantPool`-
interned constants**, the strongest form of the shared-metadata hazard — a
mutable alias of storage every container shares. The int rows are where the
copy-avoidance argument bites: `Version.getIntArray()` and `EvalCompiler.getArgs()`
clone *because there is no frozen type to hand back*, which is precisely the
scattered-`.clone()`-convention tax stage 3 exists to retire.

Cost note: primitive specializations cannot share an interface with
`FrozenArray<T>` beyond a marker (no generic `get`), so this is three small
independent classes plus three ratchet counters, not one generic addition.
