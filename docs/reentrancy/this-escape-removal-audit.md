# This-Escape Removal Audit

This file classifies the remaining `javac -Xlint:this-escape` warnings by what
should happen to them. The raw compiler tally is in
[this-escape-tally.md](this-escape-tally.md).

Current forced lint source of truth:

```text
Last full forced root lint before the handle-construction wave:
80 emitted this-escape diagnostics
77 unique file:line locations

Forced targeted compile-graph lint after the handle-construction,
runtime constructor-assertion, `ClassTemplate` implicit-field, `Container`,
`Op*` constructor-shape, utility-constructor, `MethodInfo`/`PropertyInfo`
owner-body factory, and ASM metadata owner-copy waves:
25 emitted this-escape diagnostics
24 unique file:line locations
0 xRef.java, xOSFileNode.java, CallChain.java, xRTMethod.java, or
ClassTemplate.java this-escape diagnostics
0 Container.java, Op*.java, PackedInteger.java, HasherReference.java, or
ListSet.java this-escape diagnostics
0 MethodInfo.java or PropertyInfo.java this-escape diagnostics
0 ClassStructure.java, FileStructure.java, MethodStructure.java,
PropertyStructure.java, VersionTree.java, PropertyConstant.java, or
TypeInfoReal.java this-escape diagnostics
```

The full root lint build was not rerun after these waves to avoid paying for
another clean build. Based on the forced targeted compile and the removed
full-root sites, the next full-root tally is expected to report one additional
non-`javatools` site: the remaining `javatools_jitbridge` warning.

## Decision Summary

| Decision | Count | Meaning |
| --- | ---: | --- |
| Fixed in this branch | 61 | Owner-local runtime `Lazy.of(...)` receiver captures converted to explicit owner-lazy state. |
| Fixed in this branch | 3 | `NativeContainer` startup loading moved out of the constructor and into a post-construction factory. |
| Fixed in this branch | 3 | Runtime handle construction no longer publishes `RefHandle` to `Frame.VarInfo` or initializes handle fields through constructor-time public field mutation. |
| Fixed in this branch | 2 | Runtime constructor assertions no longer call instance methods on partially constructed objects. |
| Fixed in this branch | 1 | `ClassTemplate` implicit fields are explicit constructor metadata instead of an overridable constructor hook. |
| Fixed in this branch | 22 | `Op*` constructor-time virtual opcode-shape predicates are now explicit deserialization metadata or private helpers. |
| Fixed in this branch | 3 | `Container` construction no longer captures/registers a partially constructed owner through `ConstHeap`, `NativeTemplates`, or the runtime debug registry. Two of these were visible together in javac output; the registry publication became visible after the helper captures were removed. |
| Fixed in this branch | 5 | Utility constructors no longer call overridable mutation/reset APIs while the object is partially constructed. |
| Fixed in this branch | 6 | `MethodInfo` and `PropertyInfo` no longer attach method/property body owner links from constructors. |
| Fixed in this branch | 11 | ASM metadata owner assembly no longer calls constructor-time virtual hooks or steals unowned method/property/child metadata into the first `TypeInfoReal` owner. |
| Fixed separately, still present here | 2 | Concrete unsafe construction/publication pattern. Fixed on `lagergren/fix-utils-this-escape`; still present in this branch until that PR is merged or rebased here. |
| Audit before changing | 16 | Compiler/parser/AST construction publishes parent/adoption state and needs confinement or lifecycle proof before changing in this branch. |
| Document only for this PR | 7 | JIT/tooling paths that are not part of the runtime-owner fix. |

The current forced targeted lint run reports 24 remaining unique locations. The
next full-root lint run is expected to report 25 remaining unique locations
because the full root build includes one additional non-`javatools` site.

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

## Fixed In This Branch: ASM Op Constructor Predicates

Status: fixed in this branch.

The `Op*` constructors call overridable predicates such as `isBinaryOp()`,
`hasSecondArgument()`, and `isAssignOp()` from assertions or deserialization
branches. The practical risk is lower than constructor publication, but the
shape is brittle: subclass metadata is queried before the subclass constructor
has completed.

The proper fix is not to suppress these warnings. The opcode shape is static
metadata, so constructors should receive or derive that metadata without
calling subclass-overridable methods.

This branch applies that fix:

- `Op.ConstantRegistry` initializes constructor parameter registers through a
  private helper instead of calling the public `init(RegisterAST[])` contract
  method from its constructor.
- `OpGeneral`, `OpCondJump`, `OpTest`, `OpInPlace`, `OpIndex`,
  `OpPropInPlace`, and `OpVar` no longer call virtual shape predicates from
  constructors.
- Deserialization constructors receive explicit static shape metadata:
  binary/unary, second-argument, assigning/non-assigning, or type-aware.
- Source constructors keep the same public API and use their argument arity as
  the shape. The old assertion-only virtual calls are gone because they added
  no runtime behavior and were the unsafe construction edge.
- Existing virtual methods such as `isBinaryOp()`, `hasSecondArgument()`,
  `isAssignOp()`, and `isTypeAware()` remain for post-construction runtime,
  formatting, register, and JIT behavior.

Performance and semantics are intentionally unchanged. The branch does not add
per-op shape fields or owner state. The shape parameters are constructor-only
values used while reading the packed operand stream, and the hot runtime path
continues to call the same post-construction virtual methods as before.

`OpRuntimeCacheTest.opcodeShapeConstructorsPreserveDecodedOperandLayouts()`
decodes representative binary, unary, second-argument, assigning,
non-assigning, and type-aware opcodes and verifies the same operand fields are
populated. `opcodeShapeCleanupDoesNotAddHotShapeFields()` guards against
turning the constructor-only shape into a per-op runtime cache field.

| Site(s) | Old behavior | Seriousness | Replacement |
| --- | --- | --- | --- |
| `javatools/src/main/java/org/xvm/asm/Op.java:681` | `ConstantRegistry` called public `init(RegisterAST[])` from its constructor. The class is non-final, so a subclass could observe an incomplete registry. | Should fix. This is not runtime container state, but it is a real constructor-time virtual call. | Constructor setup now calls private `initRegisters(...)`; public `init(...)` still delegates to the helper after construction for the `BinaryAST.ConstantResolver` contract. |
| `javatools/src/main/java/org/xvm/asm/OpCondJump.java:56`, `:69`, `:84`, `:99` | Constructors and deserialization branched on `isBinaryOp()` and `hasSecondArgument()`. Several concrete op subclasses override those predicates. | Should fix. Existing overrides are static opcode shape, but a future override could read subclass fields before initialization. | Deserialization receives `CondJumpShape`; source constructors use arity. Existing virtual predicates remain for post-construction behavior. |
| `javatools/src/main/java/org/xvm/asm/OpGeneral.java:43`, `:58`, `:75` | Constructors and deserialization called overridable `isBinaryOp()`. | Should fix. Same brittle static-shape problem as conditional jumps. | Deserialization receives an explicit `binary` constructor parameter for the two unary exceptions; source constructors use arity. |
| `javatools/src/main/java/org/xvm/asm/OpInPlace.java:52`, `:64`, `:79` | Constructors and deserialization called overridable `isAssignOp()`. | Should fix. Existing overrides are static role markers, but the base constructor should not ask a subclass object what role it has before construction completes. | Deserialization receives an explicit `assigns` parameter for non-assigning opcodes. |
| `javatools/src/main/java/org/xvm/asm/OpIndex.java:65`, `:79`, `:96` | Constructors and deserialization called overridable `isAssignOp()`. | Should fix for the same reason as `OpInPlace`. | Deserialization receives an explicit `assigns` parameter; non-assigning index variants pass `false`. |
| `javatools/src/main/java/org/xvm/asm/OpPropInPlace.java:34`, `:49`, `:66` | Constructors and deserialization called overridable `isAssignOp()` after the property-base constructor. | Should fix. It is a static opcode role check, not stateful runtime behavior. | Deserialization receives an explicit `assigns` parameter; non-assigning property variants pass `false`. |
| `javatools/src/main/java/org/xvm/asm/OpTest.java:49`, `:62`, `:77`, `:92` | Constructors and deserialization called overridable `isBinaryOp()` and `hasSecondArgument()`. | Should fix. Same static-shape problem as `OpCondJump`. | Deserialization receives `TestShape`; source constructors use arity. |
| `javatools/src/main/java/org/xvm/asm/OpVar.java:58` | Deserialization called overridable `isTypeAware()` before concrete construction completed. | Should fix. Existing type-aware overrides are static op shape, so virtual dispatch is unnecessary. | Deserialization receives an explicit `typeAware` parameter for `Var_C`, `Var_CN`, `CatchStart`, and `FinallyStart`. |

## Fixed In This Branch: Utility Constructor Helpers

These warnings were not owner-bearing runtime state, but they were still real
constructor hazards: a subclass could observe its object before its own fields
were initialized. This branch removes them instead of suppressing them.

| Site(s) | Old behavior | Seriousness | Replacement |
| --- | --- | --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/HasherReference.java:26` | Constructor called protected `reset(...)`. A subclass can override it and run before its own fields are initialized. | Should fix. Probably not a runtime-owner race, but it is a classic unsafe-construction shape. | Constructor now calls private `assign(...)`; protected `reset(...)` remains the post-construction reuse API used by `TransientHasherReference`. |
| `javatools_utils/src/main/java/org/xvm/util/ListSet.java:46` | Collection constructor called `addAll(...)`, which dispatches through overridable `add(...)`. Its private insertion path also called public `size()`. | Should fix. A subclass can observe the not-yet-constructed `ListSet` while elements are being added. | Constructor population uses private `addAllInternal(...)`, `addElement(...)`, and `sizeInternal()`. A lambda was not used because capturing `this` in the constructor still triggers `this-escape`. |
| `javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:64`, `:73`, `:85` | Constructors called public mutable methods `setLong(...)`, `setBigInteger(...)`, and `readObject(...)`. | Should fix. The object is mutable by design, but constructors should not dispatch through public mutation APIs. | Constructors now use private `initLong(...)`, `initBigInteger(...)`, and `readObjectInternal(...)`; public mutators keep the old validation and delegate to those helpers after construction. |

`UtilityConstructorEscapeTest` creates subclasses whose overrides throw if a
constructor calls `setLong(...)`, `setBigInteger(...)`, `readObject(...)`,
`reset(...)`, or `add(...)`. Those tests would fail on the old implementation
and now pass while preserving normal values, duplicate handling, hash indexing,
and post-construction mutation APIs.

## Fixed In This Branch: MethodInfo And PropertyInfo Body Ownership

`MethodInfo` and `PropertyInfo` used to attach child body owner links from the
owner constructors:

```java
aOwned[i] = body.forMethod(this);
aOwned[i] = body.forProperty(this);
```

That was not just a lint nuisance. `MethodBody.forMethod(...)` and
`PropertyBody.forProperty(...)` are synchronized methods that mutate or copy the
body so it points back to the containing `MethodInfo` or `PropertyInfo`. Passing
`this` before the owner constructor returned allowed the body path to observe an
owner whose final fields, rank, and body array had not yet been assigned. A
subclass of `MethodBody` or `PropertyBody` could also run code during that
owner construction window. In same-JVM incremental or parallel type-info
assembly, that shape is exactly the kind of hidden parent/child publication
that makes ownership bugs nondeterministic.

The replacement makes owner construction explicit:

- public constructors are replaced by `MethodInfo.create(...)` and
  `PropertyInfo.create(...)`;
- the private constructor builds non-virtual owned `MethodBody`/`PropertyBody`
  copies into a local array;
- the final owner body array is assigned only after that local array is
  complete;
- callers still receive `MethodInfo`/`PropertyInfo` objects with owned body
  arrays, so existing caching and `TypeInfoReal` ownership validation semantics
  are preserved.

This shape was chosen over assigning `m_aBody` first and then filling it
because that would make a partially filled final array visible through the
owner during owned-body construction. It was also chosen over streams because
`Arrays.setAll(...)` keeps the allocation direct, short, and free of extra
collection machinery.

The factory deliberately did not turn `m_aBody` into a volatile mutable field
or make body back-pointers volatile in this wave. That was considered and
rejected because it would widen the state model and remove the simple final
owner-array invariant without a failing stress proof that requires it. The
minimal fix keeps the final arrays and removes the constructor escape; any
future publication hardening should be a separate, documented memory-model
change.

`MethodInfoTest.methodInfoFactoryDoesNotCallOverridableBodyAttachment()` and
`TypeInfoMemberOwnershipTest.propertyInfoFactoryDoesNotCallOverridableBodyAttachment()`
prove the old failure shape directly. They use body subclasses whose
`forMethod(...)`/`forProperty(...)` methods read the owner rank and body-array
length and throw if the old virtual attachment path is used during owner
construction. Those tests would fail on master, where owner attachment happened
before field assignment, and pass with the non-virtual copy model. The tests
also verify that the caller-supplied body object is not mutated and that the
returned owner has its own correctly linked body copy. The same test classes
verify that `TypeInfoReal` still creates owner-local copies and that body
back-pointers target the copied owner.

Stress validation should exercise this through the existing type-info paths:
`TypeInfoReal.validate()` already asserts that each `MethodBody` points to its
owning `MethodInfo` and each `PropertyBody` points to its owning `PropertyInfo`.
Running the direct and parallel stress tasks with runtime ownership validation
enabled forces warmed type-info graphs through those checks; a split body owner
graph fails structurally instead of waiting for a later language-level crash.

## Fixed In This Branch: ASM Metadata Owner Assembly

The next ASM wave removed these `this-escape` diagnostics:

```text
javatools/src/main/java/org/xvm/asm/ClassStructure.java:3769
javatools/src/main/java/org/xvm/asm/FileStructure.java:67
javatools/src/main/java/org/xvm/asm/FileStructure.java:137
javatools/src/main/java/org/xvm/asm/FileStructure.java:160
javatools/src/main/java/org/xvm/asm/MethodStructure.java:119
javatools/src/main/java/org/xvm/asm/PropertyStructure.java:66
javatools/src/main/java/org/xvm/asm/VersionTree.java:20
javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:42
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:138
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:176
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:269
```

These were more than cosmetic constructor warnings. They had two separate
failure modes:

- several constructors called public or protected methods while construction
  was incomplete (`setConditionalReturn(...)`, `setVarAccess(...)`,
  `setType(...)`, `clear()`, and `checkParent(...)`);
- `TypeInfoReal` assembled owner-local method, property, and child metadata by
  mutating a caller-supplied unowned `MethodInfo`, `PropertyInfo`, or
  `ChildInfo` the first time it was attached to an owner.

The second case is a real parallel-owner bug. If two `TypeInfoReal` instances
were built concurrently from the same source metadata, the old `forType(...)`
methods let the first owner mutate and claim the shared source object. The
second owner then got a copy of already-owned metadata, while any retained
source reference now pointed at the first owner. That violates the model that
each realized type-info graph owns its own metadata and makes later diagnostics
or incremental reuse see the wrong owner.

The replacement keeps behavior and cache shape but removes construction-time
publication:

- `FileStructure` is now final. It is the root envelope that creates the root
  pool and module graph with itself as owner; the codebase has no subclass
  contract that needs to observe that root during construction.
- `ClassStructure.SimpleTypeResolver` and `TypeInfoReal` are final. The
  resolver can be used while generic arguments are normalized, and
  `TypeInfoReal` must create owner-local metadata while its constructor runs.
  The old anonymous `TypeInfoReal` placeholder subclass only changed
  `toString()`, so that behavior is handled directly by `TypeInfoReal`.
- `MethodStructure`, `PropertyStructure`, `VersionTree`, and `PropertyConstant`
  now use constructor-local static/private validation or direct field
  initialization instead of public/protected mutation hooks.
- `MethodInfo.forType(...)`, `PropertyInfo.forType(...)`, and
  `ChildInfo.forType(...)` never mutate unowned source metadata. They return
  `this` only when it already belongs to the requested owner; otherwise they
  allocate an equivalent owner-local copy.

No hot cache was removed. `ConstantPool.infoPlaceholder()` still caches one
placeholder object per pool, and `TypeInfoReal.toString()` preserves the old
`"Placeholder"` output. Normal type-info construction still retains one owned
method/property/child metadata graph per realized owner. The only additional
allocation is a construction-time copy when the caller supplied shared source
metadata; that is the intentional replacement for the old unsafe owner-stealing
side effect, and it does not add retained footprint to normal owner graphs.

`AsmConstructorEscapeTest` verifies the constructor-equivalence pieces:
`FileStructure` remains the root envelope, conditional-return method flags are
preserved, property type and var access are preserved, the placeholder cache and
string output are preserved, and `VersionTree` no longer calls an overridable
`clear()` during construction. It also uses hook-detecting subclasses to prove
that the changed constructors no longer dispatch to overridden
`setConditionalReturn(...)`, `setVarAccess(...)`, `setType(...)`, or
`checkParent(...)` before subclass construction completes, while those hooks
still work after construction. `MethodInfoTest.typeInfoConstructionCopiesMethodInfoInParallel()`
and
`TypeInfoMemberOwnershipTest.typeInfoConstructionCopiesPropertyAndChildInfoInParallel()`
construct several `TypeInfoReal` graphs concurrently from the same source
metadata and assert that every result is owner-local, identity-distinct, and
correctly back-linked while the source metadata remains unowned. Those tests
would fail against the old source-mutation model. The forced lint run at
`/tmp/xvm-asm-this-escape-wave.log` reports no remaining warning from this ASM
group.

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
- `ClassTemplate` collects implicit field names from explicit constructor
  metadata. `xRef` passes `RefHandle.REFERENT` and `GenericHandle.OUTER`;
  `xConst` passes `PROP_HASH`; the base still adds `GenericHandle.OUTER` for
  instance-child structures.

## Fixed In This Branch: Container Owner Construction Escapes

The base `Container` constructor had three owner-publication problems:

- `new ConstHeap(this)` stored a not-yet-fully-constructed container in a child
  helper object.
- `new NativeTemplates(this)` built an owner-retaining native-template lookup
  table from a field initializer before the container constructor returned.
- `f_runtime.registerContainer(this)` published child containers into the
  runtime debug weak registry from the base constructor. That call was virtual
  through `Runtime`, so subclasses or concurrent diagnostics could observe the
  container before the concrete container fields were assigned.

The replacement keeps the same runtime behavior and cache shape:

- `ConstHeap` is now ownerless. It remains one final heap per container, with
  the same `ConcurrentHashMap` constant-handle cache, but operations receive
  the owning `Container` explicitly. `Container.f_heap` is private and exposed
  through `getConstHeap()`.
- `Container.nativeTemplates()` stores a final `Lazy.Owner<Container,
  NativeTemplates>`. This still creates at most one `NativeTemplates` table per
  container, but construction is deferred until the owner is fully built.
- `MainContainer.create(...)` and `NestedContainer.create(...)` register the
  newly constructed container immediately after `new ...` returns. Native
  containers were not registered before and remain unregistered by default.

This does not remove caching. The constant-handle cache remains on the same
container heap, and the native-template cache remains one table per container.
The only footprint change is trading an eager `NativeTemplates` object for one
small `Lazy.Owner` cell until the table is actually used; initialized
containers still have one table as before.

`RuntimeTest.registerContainerDoesNotObservePartiallyConstructedContainer()`
uses an observing `Runtime` override to prove the old base-constructor
publication would have been able to call `findContainer(...)` before subclass
fields were assigned. `RuntimeThisEscapeConstructionTest` guards against
restoring `new ConstHeap(this)`, `new NativeTemplates(this)`, or
`registerContainer(this)`. The forced lint compile at
`/tmp/xvm-container-this-escape.log` reports no `Container.java`
`this-escape` diagnostics.

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

## Expected Full-Root Remaining Classification

```text
16 Must audit: compiler/parser/AST construction
 6 Document only: JIT construction
 2 Fixed separately, still present here: concrete unsafe utility construction
 1 Should inspect: tooling
```

## First Follow-Up Recommendation

The next cleanup wave should not try to remove all remaining warnings at once.
The least risky order after this runtime-owner branch is:

1. Merge or rebase the separate `lagergren/fix-utils-this-escape` branch that
   fixes the two concrete `javatools_utils` construction defects.
2. Audit the remaining compiler/parser/AST assembly paths with focused
   lifecycle tests before changing them in this runtime-owner branch.

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
