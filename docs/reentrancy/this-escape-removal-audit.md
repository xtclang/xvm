# This-Escape Removal Audit

This file classifies the remaining `javac -Xlint:this-escape` warnings by what
should happen to them. The raw compiler tally is in
[this-escape-tally.md](this-escape-tally.md).

Current forced root-lint source of truth:

```text
Last full forced root lint before the handle-construction wave:
80 emitted this-escape diagnostics
77 unique file:line locations

Targeted javatools lint after the handle-construction and runtime
constructor-assertion waves:
74 emitted this-escape diagnostics
71 unique file:line locations
0 xRef.java, xOSFileNode.java, CallChain.java, or xRTMethod.java
this-escape diagnostics
```

The full root lint build was not rerun after these small waves to avoid paying
for another clean build. Based on the targeted compile and the five removed
full-root sites, the next full-root tally is expected to drop to 75 emitted
diagnostics at 72 unique locations.

## Decision Summary

| Decision | Count | Meaning |
| --- | ---: | --- |
| Fixed in this branch | 61 | Owner-local runtime `Lazy.of(...)` receiver captures converted to explicit owner-lazy state. |
| Fixed in this branch | 3 | `NativeContainer` startup loading moved out of the constructor and into a post-construction factory. |
| Fixed in this branch | 3 | Runtime handle construction no longer publishes `RefHandle` to `Frame.VarInfo` or initializes handle fields through constructor-time public field mutation. |
| Fixed in this branch | 2 | Runtime constructor assertions no longer call instance methods on partially constructed objects. |
| Fixed separately, still present here | 2 | Concrete unsafe construction/publication pattern. Fixed on `lagergren/fix-utils-this-escape`; still present in this branch until that PR is merged or rebased here. |
| Remove after small design cleanup | 26 | Constructor-time virtual predicates/assertions or utility helper calls. Usually fixable, but should be separate from the runtime-owner PR. |
| Audit before changing | 37 | Construction publishes `this` to owner/child structures or performs owner-sensitive assembly. Needs confinement or lifecycle proof. |
| Document only for this PR | 7 | JIT/tooling paths that are not part of the runtime-owner fix. |

The current targeted lint run reports 71 remaining unique locations. The next
full-root lint run is expected to report 72 remaining unique locations because
the full root build includes one additional non-`javatools` site.

## Fixed Separately, Do Not Suppress Here

These warnings should be removed. Suppressing them would hide a real
construction hazard. They have already been fixed on the separate branch
`lagergren/fix-utils-this-escape` at commit
`bab70f2d2 Fix concrete utility this-escape hazards`; they still appear in this
branch only because that separate fix has not been merged here.

| Site | Why it should be removed | Separate-branch fix |
| --- | --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java:80` | Constructor adds `this` to static `KEEP_ALIVE`. That is real unsafe publication before constructor completion. | The separate branch uses a private constructor plus factory registration after construction returns. |
| `javatools_utils/src/main/java/org/xvm/util/converter/AbstractConverterMap.java:40` | Base constructor calls overridable `newKeySet()`, `newValues()`, and `newEntrySet()`. A subclass can run before its fields are initialized. | The separate branch removes constructor-time virtual dispatch by using concrete final view objects. |

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

## Fixed In This Branch: NativeContainer Startup Loading

`NativeContainer` used to load native templates and initialize resource handles
from its public constructor. That produced three `this-escape` diagnostics:

```text
javatools/src/main/java/org/xvm/runtime/NativeContainer.java:103
javatools/src/main/java/org/xvm/runtime/NativeContainer.java:155
javatools/src/main/java/org/xvm/runtime/NativeContainer.java:180
```

This was the most runtime-relevant remaining `this`-escape group because the
work installs canonical native templates and owner-local runtime metadata.
Those objects are intentionally visible to later container startup, so doing
the work while the `NativeContainer` constructor was still running made the
owner lifecycle harder to reason about.

The branch now exposes `NativeContainer.create(runtime, repository)`. The
private constructor only initializes final owner fields through `Container`.
The factory then calls `initializeNativeTemplates()` after construction returns
and before the container is handed back to `InterpreterConnector`.

Semantic and performance equivalence:

- callers still receive a fully initialized native container;
- template loading, base-template installation, `registerNativeTemplates()`,
  `initNative()`, resource initialization, and service-context creation run in
  the same relative order as before;
- no cache is removed or made lazier than before;
- the only lifecycle change is that owner-sensitive loading no longer runs
  from the constructor body.

Focused coverage:

- `InterpreterConnectorTest.parallelConnectorsLoadIndependentNativeContainers()`
  creates several interpreter connectors concurrently, loads
  `ecstasy.xtclang.org`, checks that each connector has a distinct native
  container, and then forces `OwnershipDiagnostics.assertValid(true, ...)`
  across all resulting containers.

## Fixed In This Branch: Runtime Handle Construction

These warnings were removed in the handle-construction wave:

```text
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFileNode.java:169
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:866
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:908
```

`xRef.java:908` was the actual early-publication site: the register-ref
constructor wrote `this` into `Frame.VarInfo` before the constructor returned.
That preserved a useful frame-local cache, but the publication point was wrong.
The fix is `RefHandle.createRegisterRef(...)`, which constructs the handle
first, then stores it in `Frame.VarInfo`. The old repeated-ref behavior is
unchanged: the first ref is cached on the frame, and later refs to the same
register delegate to the cached first ref.

The other two warnings were constructor-time field initialization through public
generic field mutation. `RefHandle.createReferentRef(...)` and
`NodeHandle.create(...)` now construct first and initialize backing fields
afterwards. `GenericHandle.initializeField(...)` is a final helper used only for
non-transient construction-time backing-field writes; normal runtime
`setField(...)` behavior is unchanged.

Focused verification:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.template.reflect.RefHandleConstructionTest \
  --console=plain

./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --console=plain --warning-mode=all
```

The focused test ran `tests="3" skipped="0" failures="0" errors="0"`. The
targeted lint compile emitted no `this-escape` diagnostics for `xRef.java` or
`xOSFileNode.java`.

## Remove After Small Design Cleanup

These warnings should eventually disappear, but they are best handled in
focused cleanup commits rather than mixed into the native-template owner PR.

### ASM Op Constructor Predicates

The `Op*` constructors call overridable predicates such as `isBinaryOp()`,
`hasSecondArgument()`, and `isAssignOp()` from assertions or deserialization
branches. The practical risk is lower than constructor publication, but the
shape is brittle: subclass metadata is queried before the subclass constructor
has completed.

The proper fix is not to suppress these warnings. The opcode shape is static
metadata, so constructors should receive or derive that metadata without
calling subclass-overridable methods.

| Site(s) | Current behavior | Seriousness | Proper refactor |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/asm/Op.java:681` | `ConstantRegistry` calls public `init(RegisterAST[])` from its constructor. The class is non-final, so a subclass could observe an incomplete registry. | Should fix. This is not runtime container state, but it is a real constructor-time virtual call. | Make `ConstantRegistry` final if subclassing is not intended, and move constructor initialization into a private helper such as `initParameters(...)`. Keep the public `init(...)` method only for the `BinaryAST.ConstantResolver` contract after construction. |
| `javatools/src/main/java/org/xvm/asm/OpCondJump.java:56`, `:69`, `:84`, `:99` | Constructors and deserialization branch on `isBinaryOp()` and `hasSecondArgument()`. Several concrete op subclasses override those predicates. | Should fix. Existing overrides appear to be constant opcode shape, but a future override could read subclass fields before they are initialized. | Introduce explicit constructor shape flags or an immutable opcode-shape record. The source constructors should pass the shape they already imply, and the deserialization constructor should decode shape from the op code metadata rather than virtual dispatch. |
| `javatools/src/main/java/org/xvm/asm/OpGeneral.java:43`, `:58`, `:75` | Constructors and deserialization call overridable `isBinaryOp()`. | Should fix. Same brittle static-shape problem as conditional jumps. | Pass an explicit `binary` flag to protected base constructors, or make binary-ness final/static metadata attached to the concrete op code. |
| `javatools/src/main/java/org/xvm/asm/OpInPlace.java:52`, `:64`, `:79` | Constructors and deserialization call overridable `isAssignOp()`. | Should fix. Existing overrides are static role markers, but the base constructor should not ask a subclass object what role it has before construction completes. | Replace `isAssignOp()` constructor checks with an explicit `assignsResult` constructor parameter or static opcode metadata; keep the virtual method for already-constructed behavior if needed. |
| `javatools/src/main/java/org/xvm/asm/OpIndex.java:65`, `:79`, `:96` | Constructors and deserialization call overridable `isAssignOp()`. | Should fix for the same reason as `OpInPlace`. | Use the same explicit `assignsResult`/opcode metadata model as `OpInPlace`. |
| `javatools/src/main/java/org/xvm/asm/OpPropInPlace.java:34`, `:49`, `:66` | Constructors and deserialization call overridable `isAssignOp()` after the property-base constructor. | Should fix. It is a static opcode role check, not stateful runtime behavior. | Carry the assign-role through constructor parameters or opcode metadata and avoid virtual calls until after construction. |
| `javatools/src/main/java/org/xvm/asm/OpTest.java:49`, `:62`, `:77`, `:92` | Constructors and deserialization call overridable `isBinaryOp()` and `hasSecondArgument()`. | Should fix. Same static-shape problem as `OpCondJump`. | Use an immutable test-op shape record with `binary` and `secondArgument` bits, or pass both booleans explicitly to the base constructor. |
| `javatools/src/main/java/org/xvm/asm/OpVar.java:58` | Deserialization calls overridable `isTypeAware()` before concrete construction has completed. | Should fix. Existing type-aware overrides are static op shape, so virtual dispatch is unnecessary. | Decode type-awareness from opcode metadata or pass a final constructor flag from each concrete op. |

### Utility Constructor Helpers

These are small cleanup candidates. They are not owner-bearing runtime state,
but they should not survive a future "this-escape as error" policy.

| Site(s) | Current behavior | Seriousness | Proper refactor |
| --- | --- | --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/HasherReference.java:26` | Constructor calls protected `reset(...)`. A subclass can override it and run before its own fields are initialized. | Should fix. Probably not a runtime-owner race, but it is a classic unsafe-construction shape. | Assign `referent` and `hasher` directly in the constructor or through a private helper. Leave protected `reset(...)` for post-construction reuse. |
| `javatools_utils/src/main/java/org/xvm/util/ListSet.java:46` | Collection constructor calls `addAll(...)`, which dispatches through overridable `add(...)`. | Should fix. A subclass can observe the not-yet-constructed `ListSet` while elements are being added. | Move population into a private insertion helper that uses the base storage directly, or make the class/fill path final if subclassing is not intended. |
| `javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:64`, `:73`, `:85` | Constructors call public mutable methods `setLong(...)`, `setBigInteger(...)`, and `readObject(...)`. | Should fix. The object is mutable by design, but constructors should not dispatch through public mutation APIs. | Extract private assignment/read helpers used by constructors and public methods. Public setters can delegate to the private helpers after construction. |

## Audit Before Changing

These are the warnings most likely to hide real owner/lifecycle assumptions,
but they need proof before edits. A blind mechanical rewrite could change
ownership, parent linkage, deserialization ordering, or AST invariants.

### Runtime Owner Construction

These publish the constructing runtime object to child/owner structures or call
template methods during construction. They are not all wrong, but they should
not be suppressed until the construction lifecycle is documented.

Already fixed in this branch:

- `CallChain.FieldAccessChain` validates the constructor argument with
  `CallChain.isFieldChain(aMethods)` instead of calling `isField()` through the
  partially constructed subclass.
- `xRTMethod.MethodHandle` preserves the old debug assertion with
  `resolveMethodInfo(typeTarget, method)` instead of calling `getMethodInfo()`
  on a partially constructed handle.

| Site | Current behavior | Seriousness | Proper refactor |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:95` | Base template constructor calls overridable `registerImplicitFields(null)`. Current overrides in `xRef` and `xConst` add static field names. | Must audit. This is in the root template hierarchy, and future subclasses could read owner/template fields before their constructor body runs. | Move implicit-field collection to explicit metadata: pass immutable implicit-field names to the base constructor, or use a post-construction template initialization hook called by the owning container before publication. |
| `javatools/src/main/java/org/xvm/runtime/Container.java:62` | Base constructor creates `new ConstHeap(this)`. The current `ConstHeap` constructor only stores the owner and does not publish it. | Must audit. This is probably safe today but still stores a not-yet-fully-constructed owner in a child object. | Either keep a local suppression with a proof that `ConstHeap` cannot publish/callback during construction, or create `ConstHeap` from a post-construction factory before the container is registered. |
| `javatools/src/main/java/org/xvm/runtime/Container.java:764` | Field initializer creates `new NativeTemplates(this)`. `NativeTemplates` is final and currently stores the owner plus owner-lazy cells. | Must audit. Lower risk than old static `INSTANCE`, but it still captures the owner during base construction. | Initialize `NativeTemplates` from the same post-construction owner-registration path as the heap, or suppress locally only with a final-class/no-publication proof. |

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

## Current Remaining Classification

```text
22 Should fix: ASM Op constructor dispatch
17 Must audit: ASM metadata/owner construction
16 Must audit: compiler/parser/AST construction
 3 Must audit: runtime owner/container construction
 6 Document only: JIT construction
 5 Should fix: utility cleanup
 2 Fixed separately, still present here: concrete unsafe utility construction
 1 Should inspect: tooling
```

## First Follow-Up Recommendation

The next cleanup wave should not try to remove all remaining warnings at once.
The least risky order after this runtime-owner branch is:

1. Merge or rebase the separate `lagergren/fix-utils-this-escape` branch that
   fixes the two concrete `javatools_utils` construction defects.
2. Audit the three remaining runtime owner-construction warnings with focused
   lifecycle tests.
3. Remove the ASM `Op*` constructor predicate warnings through opcode metadata
   or final/static helpers.
4. Audit ASM/compiler assembly paths with
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
