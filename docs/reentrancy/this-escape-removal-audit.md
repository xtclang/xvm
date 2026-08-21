# This-Escape Removal Audit

This file classifies the remaining `javac -Xlint:this-escape` warnings by what
should happen to them. The raw compiler tally is in
[this-escape-tally.md](this-escape-tally.md).

Current clean-build source of truth:

```text
82 emitted this-escape diagnostics
79 unique file:line locations
```

## Decision Summary

| Decision | Count | Meaning |
| --- | ---: | --- |
| Fixed in this branch | 61 | Owner-local runtime `Lazy.of(...)` receiver captures converted to explicit owner-lazy state. |
| Fix, do not suppress | 2 | Concrete unsafe construction/publication pattern. Fixed on `lagergren/fix-utils-this-escape`; still present in this branch. |
| Remove after small design cleanup | 27 | Constructor-time virtual predicates/assertions or utility helper calls. Usually fixable, but should be separate from the runtime-owner PR. |
| Audit before changing | 43 | Construction publishes `this` to owner/child structures or performs owner-sensitive assembly. Needs confinement or lifecycle proof. |
| Document only for this PR | 7 | JIT/tooling paths that are not part of the runtime-owner fix. |

These counts are classification counts over the original 140 unique locations.
The remaining total is 79 unique locations; the 61 runtime lazy-capture
locations are now fixed in this branch.

## Fix, Do Not Suppress

These warnings should be removed. Suppressing them would hide a real
construction hazard.

| Site | Why it should be removed | Proper fix |
| --- | --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java:80` | Constructor adds `this` to static `KEEP_ALIVE`. That is real unsafe publication before constructor completion. | Use a private constructor plus a static factory that registers after construction returns, or move registration into a post-construction helper that cannot observe a partially constructed object. |
| `javatools_utils/src/main/java/org/xvm/util/converter/AbstractConverterMap.java:40` | Base constructor calls overridable `newKeySet()`, `newValues()`, and `newEntrySet()`. A subclass can run before its fields are initialized. | Make the views concrete/final and build them without virtual dispatch, or lazily initialize them after construction using synchronization. |

These two are not caused by the runtime native-template work, but they are the
clearest remaining true `this`-escape defects from the lint report. They are
tracked as a separate change on branch `lagergren/fix-utils-this-escape` at
commit `bab70f2d2 Fix concrete utility this-escape hazards`; they are not part
of this runtime-owner branch.

## Fixed In This Branch: Runtime Lazy Captures

These were not the same class of bug as the old `INSTANCE = this` startup
race: `Lazy.of(...)` stores the supplier and does not invoke it in the
constructor. They still needed to be removed because the constructor created a
supplier that captured the owner before construction completed.

Status in this branch:

- The 61 warning locations below have been converted to `Lazy.Owner`, eager
  final state, or equivalent owner-local lookup.
- The full clean root build shows no remaining warning from field-level
  runtime `Lazy.of(() -> ...)` owner captures.
- `NativeTemplates` was also converted to owner-lazy form even though its
  final-class constructor did not contribute to the warning count; it is the
  same owner-local cache model and should not keep constructor-capturing
  suppliers.

Preferred replacements:

- Use `Lazy.Owner<O, T>` when the value is owner-derived and should remain
  lazy.
- Use eager final fields when the value is cheap and construction-safe.
- Use an owner-local table when many related values belong to one owner.
- Use one owner-local metadata record when a template has several related
  constants, compositions, enum templates, and helper methods that are
  naturally initialized as a group.
- Suppress only with a local comment that states the owner, lifetime, and
  deferred-execution guarantee. No suppression was needed for this wave.

Original warning locations fixed in this branch:

```text
javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTKeyStore.java:584
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xCPDirectory.java:60
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xCPFile.java:60
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xCPFileStore.java:164
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSDirectory.java:136
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFile.java:515
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSStorage.java:397
javatools/src/main/java/org/xvm/runtime/template/_native/io/xRTBuffer.java:95
javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerControl.java:230
javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerLinker.java:325
javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerLinker.java:334
javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTNameService.java:288
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:600
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:603
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:606
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:609
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:612
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:615
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:619
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTComponentTemplate.java:390
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFileTemplate.java:327
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:1552
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:1555
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:1558
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:1567
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:389
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethodTemplate.java:204
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTModuleTemplate.java:167
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTProperty.java:402
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTPropertyClassTemplate.java:290
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTPropertyTemplate.java:212
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTPropertyTemplate.java:220
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:539
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:542
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:559
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:562
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:1927
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:1930
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:1933
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:1940
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:1946
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java:873
javatools/src/main/java/org/xvm/runtime/template/_native/xRTServiceControl.java:147
javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:760
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:211
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:214
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:217
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:220
javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:53
javatools/src/main/java/org/xvm/runtime/template/maps/xListMap.java:142
javatools/src/main/java/org/xvm/runtime/template/numbers/xBit.java:230
javatools/src/main/java/org/xvm/runtime/template/numbers/xBit.java:231
javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java:353
javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java:356
javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java:359
javatools/src/main/java/org/xvm/runtime/template/reflect/xPackage.java:319
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:489
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:492
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:495
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:497
javatools/src/main/java/org/xvm/runtime/template/xService.java:579
```

Remaining locations in this category:

```text
none
```

## Remove After Small Design Cleanup

These warnings should eventually disappear, but they are best handled in
focused cleanup commits rather than mixed into the native-template owner PR.

### ASM Op Constructor Predicates

The `Op*` constructors call overridable predicates such as `isBinaryOp()`,
`hasSecondArgument()`, and `isAssignOp()` from assertions or deserialization
branches. The practical risk is lower than constructor publication, but the
shape is brittle: subclass metadata is queried before the subclass constructor
has completed.

Proper fixes:

- Make the queried predicate final if it is truly invariant and does not depend
  on subclass construction.
- Replace constructor-time virtual calls with explicit constructor parameters
  or static opcode metadata.
- For deserialization, decode from opcode metadata instead of virtual methods
  on a partially constructed instance.

Current locations:

```text
javatools/src/main/java/org/xvm/asm/Op.java:681
javatools/src/main/java/org/xvm/asm/OpCondJump.java:56
javatools/src/main/java/org/xvm/asm/OpCondJump.java:69
javatools/src/main/java/org/xvm/asm/OpCondJump.java:84
javatools/src/main/java/org/xvm/asm/OpCondJump.java:99
javatools/src/main/java/org/xvm/asm/OpGeneral.java:43
javatools/src/main/java/org/xvm/asm/OpGeneral.java:58
javatools/src/main/java/org/xvm/asm/OpGeneral.java:75
javatools/src/main/java/org/xvm/asm/OpInPlace.java:52
javatools/src/main/java/org/xvm/asm/OpInPlace.java:64
javatools/src/main/java/org/xvm/asm/OpInPlace.java:79
javatools/src/main/java/org/xvm/asm/OpIndex.java:65
javatools/src/main/java/org/xvm/asm/OpIndex.java:79
javatools/src/main/java/org/xvm/asm/OpIndex.java:96
javatools/src/main/java/org/xvm/asm/OpPropInPlace.java:34
javatools/src/main/java/org/xvm/asm/OpPropInPlace.java:49
javatools/src/main/java/org/xvm/asm/OpPropInPlace.java:66
javatools/src/main/java/org/xvm/asm/OpTest.java:49
javatools/src/main/java/org/xvm/asm/OpTest.java:62
javatools/src/main/java/org/xvm/asm/OpTest.java:77
javatools/src/main/java/org/xvm/asm/OpTest.java:92
javatools/src/main/java/org/xvm/asm/OpVar.java:58
```

### Utility Constructor Helpers

These are small cleanup candidates. They are not owner-bearing runtime state,
but they should not survive a future "this-escape as error" policy.

```text
javatools_utils/src/main/java/org/xvm/util/HasherReference.java:26
javatools_utils/src/main/java/org/xvm/util/ListSet.java:46
javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:64
javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:73
javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:85
```

## Audit Before Changing

These are the warnings most likely to hide real owner/lifecycle assumptions,
but they need proof before edits. A blind mechanical rewrite could change
ownership, parent linkage, deserialization ordering, or AST invariants.

### Runtime Owner Construction

These publish the constructing runtime object to child/owner structures or call
template methods during construction. They are not all wrong, but they should
not be suppressed until the construction lifecycle is documented.

```text
javatools/src/main/java/org/xvm/runtime/CallChain.java:540
javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:95
javatools/src/main/java/org/xvm/runtime/Container.java:62
javatools/src/main/java/org/xvm/runtime/Container.java:762
javatools/src/main/java/org/xvm/runtime/NativeContainer.java:103
javatools/src/main/java/org/xvm/runtime/NativeContainer.java:173
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFileNode.java:169
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:297
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:866
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:908
```

Proper fixes depend on the owner model:

- Prefer static factories that fully construct an object, then register or link
  it after construction returns.
- For required child owner references, make the child private and prove that it
  cannot publish the parent from its constructor.
- For template registration paths, prove registration does not expose a
  partially initialized template to other threads or containers.

### ASM Metadata and Owner Assembly

These warnings sit in constant/file/type metadata construction. They may be
safe if assembly is single-thread confined until publication, but that
assumption must be documented for same-JVM incremental compile/runtime reuse.

```text
javatools/src/main/java/org/xvm/asm/ClassStructure.java:3769
javatools/src/main/java/org/xvm/asm/FileStructure.java:67
javatools/src/main/java/org/xvm/asm/FileStructure.java:137
javatools/src/main/java/org/xvm/asm/FileStructure.java:160
javatools/src/main/java/org/xvm/asm/MethodStructure.java:119
javatools/src/main/java/org/xvm/asm/PropertyStructure.java:66
javatools/src/main/java/org/xvm/asm/VersionTree.java:19
javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:62
javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:80
javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:42
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:51
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:61
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:71
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:99
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:138
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:176
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:269
```

Proper fixes:

- Split object construction from registration/linking.
- Use private construction plus factory methods where the factory can perform
  post-construction adoption.
- Prove deserialization and metadata assembly are not visible to other threads
  until complete.

### Compiler and AST Parent/Adoption

These constructor paths set parent/component/type state, create lexers, or
consume input immediately. They are likely safe only if parser/compiler objects
are request-confined. That confinement is exactly the kind of assumption that
incremental and parallel compilation can invalidate.

```text
javatools/src/main/java/org/xvm/compiler/Lexer.java:55
javatools/src/main/java/org/xvm/compiler/Parser.java:43
javatools/src/main/java/org/xvm/compiler/Parser.java:53
javatools/src/main/java/org/xvm/compiler/Parser.java:70
javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java:71
javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java:104
javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java:129
javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java:98
javatools/src/main/java/org/xvm/compiler/ast/PackExpression.java:26
javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java:79
javatools/src/main/java/org/xvm/compiler/ast/SyntheticExpression.java:27
javatools/src/main/java/org/xvm/compiler/ast/ToIntExpression.java:60
javatools/src/main/java/org/xvm/compiler/ast/TraceExpression.java:31
javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java:197
javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java:220
javatools/src/main/java/org/xvm/compiler/ast/UnpackExpression.java:36
```

Proper fixes:

- Prove parser/AST construction is request-confined and never published before
  completion, then document it.
- Prefer construction without callbacks, followed by explicit adoption/linking
  after all object fields are initialized.
- For incremental compiler work, move mutable caches/adoption state into an
  explicit compilation request/context owner.

## Document Only For This PR

These should not be changed in the runtime-owner PR without JIT/tool-specific
tests. They still matter to a future lint-clean policy.

```text
javatools/src/main/java/org/xvm/javajit/BuildContext.java:136
javatools/src/main/java/org/xvm/javajit/BuildContext.java:167
javatools/src/main/java/org/xvm/javajit/JitMethodDesc.java:53
javatools/src/main/java/org/xvm/javajit/Xvm.java:47
javatools/src/main/java/org/xvm/javajit/builders/ArrayBuilder.java:33
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:316
javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:52
```

## First Follow-Up Recommendation

The next cleanup wave should not try to remove all 140 unique warnings at once.
The least risky order is:

1. Fix the two concrete defects in `javatools_utils`.
2. Convert the 61 runtime `Lazy.of(...)` receiver captures to `Lazy.Owner`,
   eager final fields, owner-table entries, or grouped metadata records.
3. Remove the ASM `Op*` constructor predicate warnings through opcode metadata
   or final/static helpers.
4. Audit runtime owner-construction and ASM/compiler assembly paths with
   focused lifecycle tests before changing them.

## Grouped Metadata Record Candidates

Some warning sites should not be converted one-for-one to `Lazy.Owner`. A
cluster of related cached metadata can be clearer and smaller as one immutable
record owned by the template.

### `xRTClassTemplate`

Current branch shape:

```text
7 warning-producing Lazy fields:
  f_typeClassTemplate
  f_typeClassTemplateArray
  f_typeMultiMethodArray
  f_typeMethodArray
  f_typeAnnotationArray
  f_typeContributionArray
  f_constEmptyParameterArray

3 related non-warning Lazy fields:
  f_templateAction
  f_methodCreateContrib
  f_methodCreateTypeParameters
```

A natural replacement is:

```java
private final Lazy.Owner<xRTClassTemplate, Metadata> metadata =
        Lazy.ofOwner(xRTClassTemplate::createMetadata);

private record Metadata(
        TypeConstant classTemplateType,
        TypeConstant classTemplateArrayType,
        TypeConstant multiMethodArrayType,
        TypeConstant methodArrayType,
        TypeConstant annotationArrayType,
        TypeConstant contributionArrayType,
        ArrayConstant emptyParameterArray,
        xEnum actionTemplate,
        MethodStructure createContribMethod,
        MethodStructure createTypeParametersMethod) {}
```

Size and behavior analysis:

- Today this class uses ten lazy cells for one cohesive metadata group. Each
  lazy cell has a holder object plus a supplier until first computation.
- A grouped record uses one lazy holder plus one immutable record. With
  compressed references, the record is roughly ten references plus object
  header/alignment, while avoiding nine extra lazy holder objects and supplier
  captures.
- The tradeoff is granularity: the first metadata access computes all ten
  values. For `xRTClassTemplate`, that is likely acceptable because the values
  are all reflection-template metadata from the same `ConstantPool` and
  structure, and several are used together by the same public helper paths.
- If a future profile shows one field is hot while the others are never used,
  split that field back into its own `Lazy.Owner`. The grouped record should be
  chosen for cohesion and owner safety, not as a blanket replacement for all
  lazy fields.

This is a good cleanup candidate for the current owner-lazy wave if tests stay
green, but it is not required to preserve behavior. A one-for-one `Lazy.Owner`
conversion is the lowest-risk mechanical fallback.

## Potential Task: ConstantPool-Interned Cache Simplification

Some explicit owner-local lazy caches wrap values that the owning
`ConstantPool` already interns, such as `TypeConstant`, `ArrayConstant`,
`SignatureConstant`, `VersionConstant`, and many `PropertyConstant` identities.
For those values, removing the explicit template field and recomputing through
the current owner's `ConstantPool` can be semantically sound: repeated
`ensure...` calls return the canonical value for that pool.

That does not mean the cache should be removed in this PR. Master often had an
explicit process-global static cache for exactly these values. This branch
replaces that with an owner-local cache to preserve the same one-time lookup
shape without cross-container leakage. Removing the explicit cache would be a
separate performance/cleanup decision: it simplifies code, but repeated calls
would still pay the `ConstantPool` lookup path instead of a field read.

Candidate explicit caches that are probably redundant semantically because the
result is pool-interned or identity-constant metadata:

```text
javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTKeyStore.java:
  f_typeNamedPassword

javatools/src/main/java/org/xvm/runtime/template/_native/io/xRTBuffer.java:
  f_propRawBytes

javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerLinker.java:
  f_sigGetResource

javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTNameService.java:
  f_typeCanonical
  f_typeByteArrayArray

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:
  f_typeClassTemplate
  f_typeClassTemplateArray
  f_typeMultiMethodArray
  f_typeMethodArray
  f_typeAnnotationArray
  f_typeContributionArray
  f_constEmptyParameterArray

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTComponentTemplate.java:
  f_typeComponentArray

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFileTemplate.java:
  f_typeFileTemplate

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java:
  f_typeFunctionArray
  f_constEmptyFunctionArray
  f_typeListMap

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:
  f_constEmptyArray

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTModuleTemplate.java:
  f_typeModuleTemplate

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTPackageTemplate.java:
  f_typePackageTemplate

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTProperty.java:
  f_constEmptyPropertyArray

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:
  f_typeReturn
  f_typeParam
  f_typeRTReturn
  f_typeRTParam

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java:
  f_typeArray
  f_constEmptyTypeArray
  f_typeListMap
  f_propCalculate
  f_propHasher

javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java:
  f_typeTemplateArray

javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:
  f_type

javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java:
  f_typeModuleArray
  f_constEmptyModuleArray
  f_constDefaultVersion

javatools/src/main/java/org/xvm/runtime/template/reflect/xPackage.java:
  f_typeListMap

javatools/src/main/java/org/xvm/runtime/template/xService.java:
  f_propRemainingTime
```

Values that should usually keep an explicit owner-local cache are different:

- `TypeComposition`: container resolution can be more expensive than a
  `ConstantPool` lookup and may carry runtime composition state.
- `MethodStructure` or `ClassTemplate`: structure/template lookup is not merely
  a pool intern lookup and can encode initialization order.
- `ObjectHandle`, `ArrayHandle`, `StringHandle`, and numeric handles: handles
  carry `TypeComposition` or runtime state, and master often cached them for a
  hot-path reason.

Potential simplification rule:

1. For cold `TypeConstant`/`ArrayConstant` helpers, consider deleting the
   explicit lazy field and computing through the caller's owner pool.
2. For cohesive reflection metadata, prefer one grouped metadata record over
   deleting every field or keeping many separate lazy holders.
3. Do not remove handle/composition/method caches without a benchmark or a
   clear proof that the call is cold.
