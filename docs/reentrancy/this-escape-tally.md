# Javac This-Escape Tally

This file is the committed inventory for `javac -Xlint:this-escape` on branch
`lagergren/lazy-instance`.

The warning matters because it identifies code that lets `this` become
observable during construction, or calls overridable behavior while a subclass
constructor has not completed. That is not a style-only issue. It weakens
final-field publication reasoning and creates reentrancy hazards that are hard
to test: callbacks can observe default fields, wrong owner state, or partially
assembled children.

The current JDK makes this a standard javac lint key:

```text
javac 25
this-escape  Warn when a constructor invokes a method that could be overriden in an external subclass.
             Such a method would execute before the subclass constructor completes its initialization.
```

For runtime and compiler code, the target policy should be: enable this lint,
promote it to an error, and require a local suppression only when the escape is
deliberate and documented. This branch is not there yet; this file records the
current compiler tally. The removal decision layer is maintained in
[this-escape-removal-audit.md](this-escape-removal-audit.md).

## Audit Command

This was rerun from the repository root with the build cache disabled and all
tasks forced so Gradle could not reuse compiled task outputs. Warnings were not
promoted to errors for this audit only, because the goal was to collect the
complete tally.

```bash
mkdir -p /tmp/xvm-reentrancy-audit

./gradlew clean --console=plain --warning-mode=all \
  -PincludeBuildLang=false \
  -PincludeBuildAttachLang=false \
  > /tmp/xvm-reentrancy-audit/current-clean.log 2>&1

./gradlew build --rerun-tasks --no-build-cache \
  -PincludeBuildLang=false \
  -PincludeBuildAttachLang=false \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --console=plain --warning-mode=all \
  > /tmp/xvm-current-this-escape.log 2>&1
```

Result:

```text
BUILD SUCCESSFUL in 1m 2s
141 actionable tasks: 141 executed
```

`--rerun-tasks` forces task execution. `--no-build-cache` disables the Gradle
build cache. The configuration cache may still cache task graph/configuration
data; it does not provide compiled Java classes.

## Latest Targeted Delta

The full root lint build above was not rerun after the later
handle-construction and runtime constructor-assertion waves. To avoid paying
for another clean root build, this branch used a targeted javatools compile:

```bash
./gradlew :javatools:compileJava --rerun-tasks --no-build-cache \
  --no-configuration-cache \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=10000 \
  -Porg.xtclang.java.maxErrors=10000 \
  --console=plain --warning-mode=all \
  > /tmp/xvm-callchain-method-this-escape.log 2>&1
```

Result:

```text
BUILD SUCCESSFUL in 9s
40 actionable tasks: 40 executed
74 emitted this-escape diagnostics in the targeted javatools compile
71 unique file:line locations in that targeted compile
0 xRef.java, xOSFileNode.java, CallChain.java, or xRTMethod.java this-escape diagnostics
```

The five removed full-root sites were:

```text
javatools/src/main/java/org/xvm/runtime/CallChain.java:540
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFileNode.java:169
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:297
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:866
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:908
```

The next full-root lint run is expected to drop from `80` emitted diagnostics
at `77` unique locations to `75` emitted diagnostics at `72` unique locations.

## Warning Counts

All warning categories from the same root build:

```text
201 warning: [rawtypes]
144 warning: [unchecked]
112 warning: [fallthrough]
 80 warning: [this-escape]
 10 warning: [serial]
  9 warning: [try]
  4 warning: [overrides]
  4 warning: [classfile]
  1 warning: [cast]
```

`this-escape` distribution:

```text
 72 javatools
  7 javatools_utils
  1 javatools_jitbridge
```

Javac emitted 80 `this-escape` diagnostics. Those correspond to 77 unique
`file:line` source locations, because three lines produce duplicate emissions:

```text
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:71
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:99
javatools/src/main/java/org/xvm/compiler/Parser.java:70
```

## Triage Summary

| Priority | Bucket | Sites | Ease | Proper fix |
| --- | --- | --- | --- | --- |
| Must fix, already fixed in this branch | Legacy native-template `INSTANCE = this` constructor publication from `master` | 139 constructor assignments in `master`; 0 remain in this branch | Done for this PR | Keep `INSTANCE` removed/private via owner APIs; do not reintroduce constructor-published static template singletons. |
| Must fix, already fixed in this branch | Runtime template `Lazy.of(...)` receiver captures | 61 unique warning locations removed in this branch; 0 remain in this category | Done for this PR | Use `Lazy.Owner<O,T>`, eager final state, or grouped owner-local metadata so field initializers do not capture a constructing receiver. |
| Must fix, already fixed in this branch | `NativeContainer` loads native templates and resources from its constructor | 3 unique warning locations removed in this branch; 0 remain in this category | Done for this PR | Use `NativeContainer.create(...)` so owner-sensitive startup runs after construction and before the container is returned to the connector. |
| Must fix, already fixed in this branch | Runtime handle constructors publish or mutate visible state during construction | 3 unique warning locations removed in this branch; 0 remain in this category | Done for this PR | Use `RefHandle.createRegisterRef(...)`, `RefHandle.createReferentRef(...)`, and `NodeHandle.create(...)` so construction completes before frame publication or backing-field initialization. |
| Must fix, already fixed in this branch | Runtime constructor assertions call instance methods on partially constructed objects | 2 unique warning locations removed in this branch; 0 remain in this category | Done for this PR | Validate constructor arguments with static/private helpers so debug assertions keep their old checks without dispatching through `this`. |
| Must fix, fixed separately | `CooperativelyCleanableReference` publishes `this` to a static set from the constructor | 1 | Done on `lagergren/fix-utils-this-escape` | Use a private constructor plus factory/registration step after construction, or another design that does not publish the object until construction has returned. |
| Must fix, fixed separately | `AbstractConverterMap` calls overridable factory methods from the base constructor | 1 | Done on `lagergren/fix-utils-this-escape` | Make factory results final concrete nested classes that do not dispatch to subclasses during construction, or lazily initialize views after construction with synchronization. |
| Must audit, runtime owner construction | `Container` and `ClassTemplate` owner/child construction paths | 3 unique locations | Mixed | Prove construction confinement/publication, or split construction from owner registration/adoption. |
| Must audit, ASM owner-copy and metadata construction | `FileStructure`, `ClassStructure`, `MethodStructure`, `PropertyInfo`, `MethodInfo`, `TypeInfoReal`, `PropertyConstant`, `VersionTree` | 17 unique locations | Mixed | Prove construction is request/thread confined, or split assembly from publication so owned children are connected after the owner constructor returns. |
| Must audit, `Op` constructor virtual predicates/asserts | `Op`, `OpCondJump`, `OpGeneral`, `OpInPlace`, `OpIndex`, `OpPropInPlace`, `OpTest`, `OpVar` | 22 unique locations | Moderate | Replace constructor-time virtual predicate calls with explicit constructor parameters, final helper methods, or subclass-independent op metadata. |
| Must audit, compiler/parser/AST construction callbacks | `Lexer`, `Parser`, expression/statement constructors and `adopt`/parent-link calls | 16 unique locations | Mixed | For incremental/parallel compiler safety, prove AST/request confinement or separate object construction from parent/adoption callbacks. |
| Must audit, JIT path | `javajit` and `javatools_jitbridge` constructors | 6 unique locations | Unknown | Document in `jit-implications.md`; do not change in this runtime-owner PR without JIT-specific tests. |
| Should fix, easy cleanup unless subclassing is intentional | `PackedInteger`, `HasherReference`, `ListSet` | 5 unique locations | Small | Replace constructor calls to overridable methods with private helpers, make classes final where appropriate, or inline construction loops. |
| Should inspect, tooling | `ModuleInfo` | 1 unique location | Small | Confirm no subclass-visible construction callback is needed; otherwise make the constructor path final/private. |

Current unique-location classification:

```text
 22 Must audit: ASM Op constructor dispatch
 17 Must audit: ASM metadata/owner construction
 16 Must audit: compiler/parser/AST construction
  3 Must audit: runtime owner/container construction
  6 Must audit: JIT construction
  5 Should fix: utility cleanup
  2 Must fix, fixed separately: concrete unsafe utility construction
  1 Should inspect: tooling
```

## What Is Actually Unsafe

The old native-template pattern is the most relevant runtime bug family: a
constructor wrote a process-global mutable `INSTANCE` field while the object was
still being built. Under parallel containers that can publish a partially built
template or the template for the wrong container. This branch removes that
entire family.

`CooperativelyCleanableReference` is also an actual unsafe publication. The
constructor assigns the final cleaner and then adds `this` to a static
`KEEP_ALIVE` set before the constructor returns. Another thread can observe the
reference through that static set without the normal final-field construction
safety that applies after construction completes.

`AbstractConverterMap` is another real construction hazard. The base
constructor calls `newKeySet()`, `newValues()`, and `newEntrySet()`. They are
overridable. A subclass can therefore run code before its own constructor has
initialized its fields.

The runtime-template `Lazy.of(...)` warnings that existed before this cleanup
were different from global `INSTANCE` publication. `Lazy.of` constructs a lazy
cell that stores the supplier; it does not call the supplier until `get()`. That
made the old lazy captures much safer than `INSTANCE = this`, but they were
still lint debt because the constructor created supplier objects that captured
the owner before construction completed. This branch removes that warning
family by making owner passing explicit with `Lazy.Owner`, eager final state, or
grouped owner-local metadata.

## Why Lazy Triggers This Warning

`Lazy` is useful here because it gives one final field and one thread-safe
memoization boundary. The warning is not saying that `Lazy.get()` is racy. It
is saying that this common construction shape captures a receiver while the
receiver is still being constructed:

```java
private final Lazy<EnumInfo> f_enumInfo = Lazy.of(this::createEnumInfo);
```

or:

```java
private final Lazy<TypeConstant> f_type = Lazy.of(() ->
        pool().ensureEcstasyTypeConstant("reflect.Type"));
```

Java lowers those field initializers into constructor code. The constructor
creates a supplier object that references the not-yet-fully-constructed
receiver and passes that supplier to `Lazy.of(...)`. Javac cannot prove that
`Lazy.of(...)` only stores the supplier, cannot prove that a future
implementation will not call it from the constructor, and cannot prove that the
supplier will not be stored in a shared object. It therefore reports a possible
`this` escape.

The current `Lazy.of` implementation is deferred:

```java
ThreadSafeLazy(Supplier<T> supplier) {
    this.supplier = requireNonNull(supplier, "supplier");
}
```

and the supplier is invoked only by `get()`. That makes the existing owner-local
uses materially safer than the old `INSTANCE = this` pattern, because there is
no process-global publication during construction. It does not make them
lint-clean, because javac has no API contract that says "this supplier is only
stored and never invoked or published during construction".

The preferred long-term fix is to keep the lazy/final-field design but avoid
capturing `this` while constructing the lazy cell. One possible helper shape is
an owner-passed lazy:

```java
private final Lazy.Owner<xEnum, EnumInfo> enumInfo =
        Lazy.ofOwner(xEnum::createEnumInfoFor);

private static EnumInfo createEnumInfoFor(xEnum owner) {
    return owner.createEnumInfo();
}

EnumInfo enumInfo() {
    return enumInfo.get(this);
}
```

The lazy field is still final. The result is still computed once and safely
published. The owner is explicit at the access point, not captured by the
constructor. If the compute helper can be static or final, this also avoids
constructor-time virtual dispatch concerns.

The migration rule should be:

- Use ordinary `Lazy.of(...)` freely when the supplier does not capture the
  constructing receiver.
- For owner-derived runtime state, prefer an owner-passed lazy helper or eager
  final computation if the value is cheap and construction-safe.
- Suppress `this-escape` only locally, with a comment that says the lazy cell is
  owner-local and the supplier is not invoked or published during construction.

## Full File Count

This is the file-level count from the last full-root lint run, before the
targeted-delta fixes above. It still includes the five fixed sites listed in
that section.

```text
   6 javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java
   4 javatools/src/main/java/org/xvm/compiler/Parser.java
   4 javatools/src/main/java/org/xvm/asm/OpTest.java
   4 javatools/src/main/java/org/xvm/asm/OpCondJump.java
   3 javatools_utils/src/main/java/org/xvm/util/PackedInteger.java
   3 javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java
   3 javatools/src/main/java/org/xvm/asm/OpPropInPlace.java
   3 javatools/src/main/java/org/xvm/asm/OpIndex.java
   3 javatools/src/main/java/org/xvm/asm/OpInPlace.java
   3 javatools/src/main/java/org/xvm/asm/OpGeneral.java
   3 javatools/src/main/java/org/xvm/asm/FileStructure.java
   2 javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java
   2 javatools/src/main/java/org/xvm/runtime/Container.java
   2 javatools/src/main/java/org/xvm/javajit/BuildContext.java
   2 javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java
   2 javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java
   2 javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java
   1 javatools_utils/src/main/java/org/xvm/util/converter/AbstractConverterMap.java
   1 javatools_utils/src/main/java/org/xvm/util/ListSet.java
   1 javatools_utils/src/main/java/org/xvm/util/HasherReference.java
   1 javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java
   1 javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java
   1 javatools/src/main/java/org/xvm/tool/ModuleInfo.java
   1 javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFileNode.java
   1 javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java
   1 javatools/src/main/java/org/xvm/runtime/ClassTemplate.java
   1 javatools/src/main/java/org/xvm/runtime/CallChain.java
   1 javatools/src/main/java/org/xvm/javajit/builders/ArrayBuilder.java
   1 javatools/src/main/java/org/xvm/javajit/Xvm.java
   1 javatools/src/main/java/org/xvm/javajit/JitMethodDesc.java
   1 javatools/src/main/java/org/xvm/compiler/ast/UnpackExpression.java
   1 javatools/src/main/java/org/xvm/compiler/ast/TraceExpression.java
   1 javatools/src/main/java/org/xvm/compiler/ast/ToIntExpression.java
   1 javatools/src/main/java/org/xvm/compiler/ast/SyntheticExpression.java
   1 javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java
   1 javatools/src/main/java/org/xvm/compiler/ast/PackExpression.java
   1 javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java
   1 javatools/src/main/java/org/xvm/compiler/ast/ConvertExpression.java
   1 javatools/src/main/java/org/xvm/compiler/Lexer.java
   1 javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java
   1 javatools/src/main/java/org/xvm/asm/VersionTree.java
   1 javatools/src/main/java/org/xvm/asm/PropertyStructure.java
   1 javatools/src/main/java/org/xvm/asm/OpVar.java
   1 javatools/src/main/java/org/xvm/asm/Op.java
   1 javatools/src/main/java/org/xvm/asm/MethodStructure.java
   1 javatools/src/main/java/org/xvm/asm/ClassStructure.java
```

## Full Emitted Diagnostic Locations

Duplicates are retained here because they are emitted diagnostics, not just
unique source lines.

This list is the last full-root output captured before the handle-construction
and runtime constructor-assertion waves. It therefore still contains the five
removed lines listed in the targeted-delta section above; those no longer
appear in the targeted javatools lint compile.

```text
javatools/src/main/java/org/xvm/asm/ClassStructure.java:3769
javatools/src/main/java/org/xvm/asm/FileStructure.java:137
javatools/src/main/java/org/xvm/asm/FileStructure.java:160
javatools/src/main/java/org/xvm/asm/FileStructure.java:67
javatools/src/main/java/org/xvm/asm/MethodStructure.java:119
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
javatools/src/main/java/org/xvm/asm/PropertyStructure.java:66
javatools/src/main/java/org/xvm/asm/VersionTree.java:19
javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:62
javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:80
javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:42
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:51
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:61
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:71
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:71
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:99
javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:99
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:138
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:176
javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java:269
javatools/src/main/java/org/xvm/compiler/Lexer.java:55
javatools/src/main/java/org/xvm/compiler/Parser.java:43
javatools/src/main/java/org/xvm/compiler/Parser.java:53
javatools/src/main/java/org/xvm/compiler/Parser.java:70
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
javatools/src/main/java/org/xvm/javajit/BuildContext.java:136
javatools/src/main/java/org/xvm/javajit/BuildContext.java:167
javatools/src/main/java/org/xvm/javajit/JitMethodDesc.java:53
javatools/src/main/java/org/xvm/javajit/Xvm.java:47
javatools/src/main/java/org/xvm/javajit/builders/ArrayBuilder.java:33
javatools/src/main/java/org/xvm/runtime/CallChain.java:540
javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:95
javatools/src/main/java/org/xvm/runtime/Container.java:62
javatools/src/main/java/org/xvm/runtime/Container.java:764
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFileNode.java:169
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTMethod.java:297
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:866
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:908
javatools/src/main/java/org/xvm/tool/ModuleInfo.java:316
javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:52
javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java:80
javatools_utils/src/main/java/org/xvm/util/HasherReference.java:26
javatools_utils/src/main/java/org/xvm/util/ListSet.java:46
javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:64
javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:73
javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:85
javatools_utils/src/main/java/org/xvm/util/converter/AbstractConverterMap.java:40
```
