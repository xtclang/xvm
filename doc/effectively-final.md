# Fields that could be final

An inventory of the fields in the main Java sources of `javatools` and `javatools_utils` that are not `final`, sorted by why: fields that can simply be declared `final` (proven by compiling them that way), fields that are set once but in a way javac does not accept for `final`, and fields that are really mutated. It also covers non-final statics and `static final` arrays and collections whose contents are never changed.

Snapshot: branch `lagergren/errs` at `d2ecd0a254` (2026-11-11). Line numbers refer to that commit.

## Why this matters

A `final` field gets the Java memory model's initialization-safety guarantee (JLS 17.5): any thread that sees a reference to the object after its constructor finishes sees the constructor's values of its final fields, and of everything reachable through them, even if the reference was handed over through a data race. A non-final field has no such guarantee; a thread that reads it needs a happens-before edge with the write (a lock, a volatile, a concurrent collection, `Thread.start`, and so on).

The compiler and the runtime are to become safe to run in parallel through `org.xvm.api.EmbeddingSupport` (see `doc/parallel-compiler.md` and `doc/parallel-runtime.md`). The objects that will be shared between threads are library `ModuleStructure`/`FileStructure` trees, their `ConstantPool`s and `Constant`s (`TypeConstant`, `TypeInfo`, and so on), runtime templates and compositions, containers, and runtime handles that cross service boundaries. This document therefore ranks findings on those structures ahead of local style fixes. A field that is set once but cannot be `final` is not a bug by itself: it is safe as long as the object reaches other threads only through a happens-before edge. The lists below show where that argument currently has to be made by hand.

Paths are relative to `javatools/src/main/java/`, except `org/xvm/util/...`, which is under `javatools_utils/src/main/java/` (javatools has no `org.xvm.util` package). `path:line` is the line of the field's name in its declaration. Writer sites are given as `method`:line; `<init>` is a constructor, `<clinit>` a static initializer.

Labels: **proven** means javac accepted the field as `final` in a full compile. **Read in code** means the statement was checked by reading the source. **Inferred** means it comes from the classification heuristics below and was not checked one by one.

## Summary

- 4586 fields are declared in the scope (enum constants, interface constants and record components excluded); 2384 are already `final`, 2202 are not (301 static, 1901 instance).
- **Category 1, non-final statics:** 301, all of them in `org.xvm.runtime` and its subpackages. 9 can simply be `final` (proven). 147 are template singletons assigned from an instance constructor, 134 are assigned in `initNative()`, the rest are lazy, unused or mutable. `org.xvm.compiler`, `org.xvm.asm`, `javajit`, `tool` and `javatools_utils` have no non-final static fields.
- **Category 2, instance fields that can simply be `final`:** 174, proven by javac (plus the 9 statics above, 183 in all). 134 of them are compiler AST node fields; the ones on shared structures are runtime handle values and a few `asm` value classes.
- **Category 3, effectively final:** 1117 instance fields are written only by constructors, deserialization, pool registration, a freeze step, a lazy cache (some with a reset) or setters used while building. 150 of them are proven to need only a definite-assignment fix; the rest are classified from the shape of their writes (inferred). The biggest clusters are the two-phase `Constant`/`Op` fields, the `ConstantPool` cache block, and lazy caches.
- **Category 4, final but mutable:** 122 `static final` arrays and collections are not empty constants; 20 are provably never changed after class initialization, 95 are passed to or returned to other code (the 69 compiler `CHILD_FIELDS` arrays among them were checked and are only read), and 5 are changed after initialization. 26 are empty arrays.
- Not candidates (instance fields): 197 compiler AST fields that change between compile stages, 341 fields with ordinary mutation, 50 volatile fields, 1 field written through a `VarHandle`, and 21 fields that are never assigned in Java source.

## Main findings, shared structures first

1. **`Constant` two-phase construction (`org.xvm.asm.constants`).** The deserialization constructor stores pool indexes (`m_iType`, `m_aiVal`, ...), `resolveConstants()` turns them into constants once the whole pool has been read, and `registerConstants(pool)` rewrites each referenced constant with the pool's canonical copy. 58 index fields (with `Annotation`'s two) are proven to fail only javac's definite-assignment check (the other constructors never assign them), and 64 resolved fields are reassigned in `resolveConstants`/`registerConstants`. Example, read in code: `ArrayConstant` stores `m_iType` at `org/xvm/asm/constants/ArrayConstant.java:67`, resolves `m_constType` at :75 and re-registers it at :279 (fields at :313 and :329). These are the objects of every library `ConstantPool`. The index fields can become `final` cheaply; the resolved fields need a pool reader that constructs constants after reading all records, or resolution on access. See [Two-phase setup](#two-phase-setup-deserialization-and-registration).
2. **The `ConstantPool` cache block.** 185 transient fields at `org/xvm/asm/ConstantPool.java:3840`-4024 cache well-known classes, types and values (`m_clzObject`, `m_typeString`, ...). Each accessor is a one-line `if (c == null) m_x = c = ...` (:2176 onward, read in code), and `optimize()` at :3489 clears 169 of them. Every library pool is shared, so two threads can race on each of these, and a constant published through a plain field carries no ordering for its own non-final fields. `Lazy.Bound` with `reset()` fits; the cost is mechanical but large (one file, one holder object per field per pool).
3. **`PackedInteger` is effectively immutable but has no final field.** `m_fInitialized`, `m_fBig` and `m_lValue` (`org/xvm/util/PackedInteger.java:944`, :949, :955) are written only by `setLong`, `setBigInteger` and `readObject`, which the constructors call; no main source outside the class calls them (read in code). `m_bigint` (:962) is set by `setBigInteger` and is also a lazy cache in `getBigInteger` (:240); `BigInteger` is immutable, so that cache can stay racy. Instances are shared through the static `CACHE` array (:967), filled by a racy `CACHE[i] = pint` at :101, and through every integer constant. With final fields the racy cache would be safe; today it is not. Fix: compute in the constructors, drop the public setters. Cost: low. `Decimal128` (`org/xvm/type/Decimal128.java:512`, :517) has the same shape through a private helper called only from a constructor.
4. **`TypeConstant.s_setRecursions` is a shared `HashSet` that is written during type checks.** `private static final Set<String> s_setRecursions` (`org/xvm/asm/constants/TypeConstant.java:8656`) is filled in a static block and then `add`ed to in `calculateRelation` at :6189 (read in code). Parallel compiles would race on a plain `HashSet`. Fix: `ConcurrentHashMap.newKeySet()`, or drop the log-once set. Cost: low. This is the only category 4 field with a real mutation on the compiler path.
5. **`JumpVal` builds its jump map lazily with a double-checked pattern but without `volatile`.** `m_ahCase`, `m_mapJump`, `m_algorithm` and `m_typeCond` (`org/xvm/asm/op/JumpVal.java:758`-773) are set in the synchronized `buildJumpMap` (:274, assignments :330-333), but `ensureJumpMap` (:116) reads `m_algorithm` and `complete` (:141) reads `m_mapJump` without the lock (read in code). An `Op` belongs to a method's code, which all services of a container share, so a second thread can see `m_algorithm` set and `m_mapJump` still null. Fix: build one immutable jump-table object and publish it through a single `volatile` field or a `Lazy`. Cost: low. `JumpVal_N` (:559-586) has the same shape.
6. **Runtime statics are per-container state.** 147 static fields (mostly `INSTANCE`) are assigned in template constructors, for example `xObject.INSTANCE = this` (`org/xvm/runtime/template/xObject.java:14`, assigned at :21), and 134 more in `initNative()`, for example `xBoolean.TRUE`/`FALSE` (`org/xvm/runtime/template/xBoolean.java:18`, assigned at :31-32). `InterpreterConnector` creates a `NativeContainer` per connector (`org/xvm/api/InterpreterConnector.java:40`), so a second embedded runtime assigns them again (inferred from the call site; not run). They are not effectively final per process. Their redesign belongs to `doc/parallel-runtime.md`; [Category 1](#category-1-non-final-static-fields) lists them.
7. **`ObjectHandle.m_clazz` is rewritten after `clone()`.** `cloneAs` (`org/xvm/runtime/ObjectHandle.java:61`) calls `super.clone()` and then assigns `m_clazz` at :64 (field at :46). `ObjectHandle` is the base of every runtime value, and immutable handles cross service boundaries. A copy constructor per handle class would allow `final`. Cost: high (every cloneable handle subclass). `m_fMutable` (:47) is a real one-way state change (`makeImmutable`) and stays mutable.
8. **`Op` fields.** 30 `Op` argument fields (`m_aArgValue`, `m_argValue`, ...) are proven to fail only definite assignment: the assembly constructor sets them, the deserialization constructor does not (`org/xvm/asm/op/Call_N0.java:170`, set at :38). Assigning `null` there makes them `final`. Another 181 index fields (`m_nRetValue`, `m_anArgValue`, ...) are set by the deserialization constructor or rewritten by `write()` during assembly (:60), and jump ops set their addresses in `resolveAddresses()` (called from `MethodStructure.java:2600`). Ops that the runtime loads from bytes are effectively final after `resolveAddresses` (inferred).
9. **`asm` structures are "mutable until frozen".** `XvmStructure.markReadOnly()` (`org/xvm/asm/XvmStructure.java:221`) is the point after which a `FileStructure` tree is meant to be immutable; 37 fields (for example `ClassStructure.m_mapParams`, `org/xvm/asm/ClassStructure.java:3782`) are written by builder methods that check `isReadOnly()`/`verifyMutable()` and replaced by read-only copies in `markReadOnly`. 17 more (12 of them in `asm`, in `cloneBody()`) are rewritten after `clone()`. Library structures reach other threads after the freeze, so the useful fix is to publish them through a happens-before edge at that point; making the fields `final` needs a builder/immutable split.
10. **The proven list is mostly AST fields, but not only.** Of the 174 proven instance fields, the ones on shared objects are runtime handle values (`ObjectHandle.JavaLong.m_lValue`, `xEnum.EnumHandle.m_index`, `xIntLiteral.IntNHandle.m_piValue`, `xTuple.TupleHandle.m_ahValue`, the `xRTFunction` handles) and `Version`/`VersionTree.Node`/`MapConstant.ROMap` in `asm`. Of the 9 proven statics, the seven `ClassTemplate` name arrays (`org/xvm/runtime/ClassTemplate.java:2428`-2434) are also mutable public arrays.

Also worth knowing: `TypeInfo` has no non-final field, and `TypeConstant` keeps its main caches in volatile fields updated through field updaters (`m_typeinfo`, `m_cInvalidations`, `m_tloInProgress`, `org/xvm/asm/constants/TypeConstant.java:8600`-8624); the rest of `TypeConstant`'s non-final fields are lazy caches.

## Method

### Tools

1. A throwaway worktree of `d2ecd0a254` outside the checkout.
2. An analyzer built on the javac Compiler Tree API (`JavacTask.parse()` + `analyze()`, all 835 main files of both modules in one compilation, the jars from the version catalog on the class path). It records, with symbols resolved by javac rather than by name:
   - every field declaration: owner, modifiers, initializer, type, and the source offset of its type;
   - every write to a field (`=`, compound assignment, `++`/`--`): the target form (`x`, `this.x`, `other.x`, `Type.x`), the enclosing class and member (constructor, initializer, method), whether it is inside a lambda, the enclosing `if` conditions, the right-hand side, and whether the method also calls `verifyMutable`, `ensureMutable` or `isReadOnly`;
   - every method call and method reference with its caller, to find private methods that only constructors call;
   - every use of an array or collection field (element write, method call, escape as argument or return value, iteration, read);
   - string literals passed to `VarHandles.of`, `findVarHandle` and the `Atomic*FieldUpdater` factories.
3. Candidate selection. A non-final, non-volatile field that is not reached reflectively is a candidate if every write is a plain `=` to `x` or `this.x` in a constructor or instance initializer of the declaring class (static: to `x` in a static initializer) outside any lambda, and it has either an initializer and no writes, or no initializer and at least one write. Multi-variable declarations (`int a, b;`) are taken all or none; there were none in the result.
4. Proof loop. A script inserted `final ` before the type of every candidate in the worktree and compiled with javac (`-Xmaxerrs 100000 -XDshould-stop.ifError=FLOW`, so flow analysis runs despite earlier errors). Each "might not have been initialized" / "might already have been assigned" error was mapped back to its field through the symbol at the error position, or the field of that name in the enclosing class; those fields were reverted and the loop repeated from the pristine sources. javac reports flow errors class by class, so it took 13 rounds: 333 candidates, 150 rejected, 183 left. The result was then compiled with `./gradlew :javatools:compileJava :javatools_utils:compileJava --offline -q` (exit 0, `javap` shows the fields `final`) and with `./gradlew :javatools:compileTestJava :javatools_utils:compileTestJava --offline -q` (exit 0), so the test sources do not assign them either.
5. Classification of every other non-final field by its writes outside constructors (first match wins, per write):

| Write kind | Rule |
|---|---|
| ctor-helper | in a private method whose every caller is a constructor of the same class (transitively), outside lambdas |
| clone | in `clone*`, or the target is a local initialized from `clone()` |
| freeze | in `markReadOnly` |
| lazy | inside `if (x == null)` / `== 0` / `!x` on the same field, or the local-copy form `if (c == null) { m_x = c = ...; }`, or in a `get*`/`ensure*`/`is*`/`has*`/`compute*`/`find*`/`collect*` method with a non-null value |
| deser | in `disassemble`, `resolveConstants`, `readObject`, `read*` |
| register | in `registerConstants`, `write`, `preWrite`, `assemble`, `encode*` |
| compile-phase | in `org.xvm.compiler` code, in `validate*`, `resolve*`, `generate*`, `emit*`, `introduce*` and similar stage methods |
| reset | the value is `null`, or the method name says reset/clear/close/discard/... |
| setup | in `init*`, `configure*`, `link*`, `prepare*`, `load*`, `resolve*`, `register*`, `build*`, `create*`, `validate*`, ... |
| setter | in `set[A-Z]*` |
| counter | compound assignment or `++`/`--` |
| builder | in a method that also calls `verifyMutable`/`ensureMutable`/`isReadOnly` |
| other | anything else |

A field with any `other` or `counter` write is **mutable**. Otherwise its label is the first that applies: `ctor-helper` (only ctor-helper writes), `frozen` (freeze or builder writes), `clone`, `lazy-reset` (lazy plus reset writes), `lazy`, `two-phase` (deser or register writes), `compile-phase`, `setup`, `setter`, `reset`. javac-rejected candidates are labeled `flow`. Static fields get `static-from-ctor` (written only in an instance constructor), `static-setup` (other methods), `lazy`, or `mutable`.

These rules are name- and shape-based. They cannot see call order, so a setter called after publication or a `clear()` called while other threads read looks the same as one used only while building. Everything outside the `flow` and proven lists is therefore inferred unless a section says "read in code".

## Counts per package

Instance fields unless the column says static. "Eff. final" is the sum of the category 3 labels; "phase" is compiler AST stage state; "other" is volatile, reflective and never-assigned fields.

| Package | Non-final | Cat 1 static | of which proven | Cat 2 proven | Cat 3 eff. final | flow | two-phase | frozen/clone | lazy | lazy-reset | setup/setter/reset/helper | Phase | Mutable | Other |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| org.xvm.api | 14 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | 1 | 1 | 3 | 0 | 5 | 4 |
| org.xvm.asm | 439 | 0 | 0 | 5 | 374 | 11 | 84 | 47 | 36 | 175 | 21 | 0 | 53 | 7 |
| org.xvm.asm.constants | 212 | 0 | 0 | 3 | 183 | 56 | 64 | 2 | 51 | 7 | 3 | 0 | 18 | 8 |
| org.xvm.asm.op | 268 | 0 | 0 | 1 | 252 | 30 | 181 | 0 | 11 | 1 | 29 | 0 | 14 | 1 |
| org.xvm.compiler | 32 | 0 | 0 | 0 | 4 | 2 | 0 | 0 | 2 | 0 | 0 | 9 | 19 | 0 |
| org.xvm.compiler.ast (with bindings, partial) | 469 | 0 | 0 | 134 | 109 | 35 | 0 | 4 | 58 | 3 | 9 | 188 | 36 | 2 |
| org.xvm.javajit (with builders, registers) | 44 | 0 | 0 | 2 | 5 | 0 | 0 | 0 | 2 | 0 | 3 | 0 | 19 | 18 |
| org.xvm.runtime (with debug, gc) | 219 | 27 | 7 | 15 | 63 | 8 | 0 | 1 | 33 | 0 | 21 | 0 | 88 | 26 |
| org.xvm.runtime.template (all subpackages) | 393 | 274 | 2 | 9 | 75 | 1 | 0 | 0 | 51 | 1 | 22 | 0 | 32 | 3 |
| org.xvm.tool | 36 | 0 | 0 | 3 | 25 | 7 | 0 | 0 | 13 | 1 | 4 | 0 | 8 | 0 |
| org.xvm.type | 5 | 0 | 0 | 0 | 5 | 0 | 0 | 0 | 3 | 0 | 2 | 0 | 0 | 0 |
| org.xvm.util (javatools_utils, with concurrent, converter) | 71 | 0 | 0 | 2 | 17 | 0 | 0 | 0 | 6 | 1 | 10 | 0 | 49 | 3 |
| **Total** | **2202** | **301** | **9** | **174** | **1117** | **150** | **329** | **54** | **267** | **190** | **127** | **197** | **341** | **72** |

Category 4 (`static final` arrays and collections) per package:

| Package | Never mutated | Escapes (read-only where checked) | Mutated | Already immutable | Empty arrays |
|---|---:|---:|---:|---:|---:|
| org.xvm.asm | 8 | 2 | 0 | 0 | 7 |
| org.xvm.asm.constants | 1 | 0 | 1 | 0 | 3 |
| org.xvm.compiler | 5 | 1 | 0 | 0 | 0 |
| org.xvm.compiler.ast (with bindings, partial) | 4 | 69 | 0 | 0 | 4 |
| org.xvm.javajit (with builders, registers) | 0 | 5 | 1 | 1 | 1 |
| org.xvm.runtime (with debug, gc) | 0 | 5 | 0 | 0 | 4 |
| org.xvm.runtime.template (all subpackages) | 1 | 9 | 1 | 0 | 3 |
| org.xvm.tool | 0 | 1 | 0 | 1 | 0 |
| org.xvm.util (javatools_utils, with concurrent, converter) | 1 | 3 | 2 | 0 | 4 |

## Category 2: fields that can simply be final (proven)

javac accepted all 183 of these as `final` in one compile of both modules, main and test sources. The 9 static ones are listed here too and again under category 1. Adding `final` to exactly this list is a mechanical commit; nothing else needs to change.

### org.xvm.asm (5)

| Class | Field | Location |
|---|---|---|
| MethodStructure$Code$BlackHole | `f_wrappee` | org/xvm/asm/MethodStructure.java:2418 |
| Version | `literal` | org/xvm/asm/Version.java:629 |
| Version | `ints` | org/xvm/asm/Version.java:630 |
| VersionTree$Node | `parent` | org/xvm/asm/VersionTree.java:1027 |
| VersionTree$Node | `part` | org/xvm/asm/VersionTree.java:1037 |

### org.xvm.asm.constants (3)

| Class | Field | Location |
|---|---|---|
| MapConstant$ROMap | `ak` | org/xvm/asm/constants/MapConstant.java:479 |
| MapConstant$ROMap | `av` | org/xvm/asm/constants/MapConstant.java:480 |
| VirtualChildTypeConstant | `m_fThisClass` | org/xvm/asm/constants/VirtualChildTypeConstant.java:378 |

### org.xvm.asm.op (1)

| Class | Field | Location |
|---|---|---|
| MoveThis | `m_cSteps` | org/xvm/asm/op/MoveThis.java:215 |

### org.xvm.compiler.ast (with bindings, partial) (134)

| Class | Field | Location |
|---|---|---|
| AnnotationExpression | `lStartPos` | org/xvm/compiler/ast/AnnotationExpression.java:581 |
| AnnotationExpression | `lEndPos` | org/xvm/compiler/ast/AnnotationExpression.java:582 |
| ArrayAccessExpression | `indexes` | org/xvm/compiler/ast/ArrayAccessExpression.java:1274 |
| ArrayAccessExpression | `tokClose` | org/xvm/compiler/ast/ArrayAccessExpression.java:1275 |
| ArrayTypeExpression | `dims` | org/xvm/compiler/ast/ArrayTypeExpression.java:148 |
| ArrayTypeExpression | `indexes` | org/xvm/compiler/ast/ArrayTypeExpression.java:149 |
| ArrayTypeExpression | `lEndPos` | org/xvm/compiler/ast/ArrayTypeExpression.java:150 |
| AsExpression | `lEndPos` | org/xvm/compiler/ast/AsExpression.java:136 |
| AssertStatement | `keyword` | org/xvm/compiler/ast/AssertStatement.java:610 |
| AssertStatement | `lEndPos` | org/xvm/compiler/ast/AssertStatement.java:614 |
| AssignmentStatement | `op` | org/xvm/compiler/ast/AssignmentStatement.java:974 |
| AssignmentStatement | `term` | org/xvm/compiler/ast/AssignmentStatement.java:976 |
| BadTypeExpression | `nonType` | org/xvm/compiler/ast/BadTypeExpression.java:78 |
| BiExpression | `operator` | org/xvm/compiler/ast/BiExpression.java:115 |
| BiTypeExpression | `operator` | org/xvm/compiler/ast/BiTypeExpression.java:129 |
| CaseStatement | `keyword` | org/xvm/compiler/ast/CaseStatement.java:125 |
| CaseStatement | `exprs` | org/xvm/compiler/ast/CaseStatement.java:126 |
| CaseStatement | `lEndPos` | org/xvm/compiler/ast/CaseStatement.java:127 |
| CatchStatement | `lStartPos` | org/xvm/compiler/ast/CatchStatement.java:148 |
| CmpChainExpression | `expressions` | org/xvm/compiler/ast/CmpChainExpression.java:485 |
| CmpChainExpression | `operators` | org/xvm/compiler/ast/CmpChainExpression.java:486 |
| CompositionNode$Annotates | `annotation` | org/xvm/compiler/ast/CompositionNode.java:242 |
| CompositionNode$Incorporates | `args` | org/xvm/compiler/ast/CompositionNode.java:326 |
| CompositionNode$Incorporates | `constraints` | org/xvm/compiler/ast/CompositionNode.java:327 |
| CompositionNode$Delegates | `delegatee` | org/xvm/compiler/ast/CompositionNode.java:386 |
| CompositionNode$Delegates | `lEndPos` | org/xvm/compiler/ast/CompositionNode.java:387 |
| CompositionNode$Import | `modifier` | org/xvm/compiler/ast/CompositionNode.java:550 |
| CompositionNode$Import | `vers` | org/xvm/compiler/ast/CompositionNode.java:555 |
| CompositionNode$Import | `injects` | org/xvm/compiler/ast/CompositionNode.java:560 |
| CompositionNode$Import | `injector` | org/xvm/compiler/ast/CompositionNode.java:565 |
| CompositionNode$Import | `lEndPos` | org/xvm/compiler/ast/CompositionNode.java:567 |
| CompositionNode$Default | `expr` | org/xvm/compiler/ast/CompositionNode.java:605 |
| CompositionNode$Default | `lEndPos` | org/xvm/compiler/ast/CompositionNode.java:606 |
| CompositionNode | `condition` | org/xvm/compiler/ast/CompositionNode.java:613 |
| CompositionNode | `keyword` | org/xvm/compiler/ast/CompositionNode.java:614 |
| CompositionNode | `type` | org/xvm/compiler/ast/CompositionNode.java:615 |
| ConditionalStatement | `keyword` | org/xvm/compiler/ast/ConditionalStatement.java:74 |
| DecoratedTypeExpression | `keyword` | org/xvm/compiler/ast/DecoratedTypeExpression.java:102 |
| ExpressionStatement | `term` | org/xvm/compiler/ast/ExpressionStatement.java:124 |
| FileExpression | `type` | org/xvm/compiler/ast/FileExpression.java:447 |
| FileExpression | `path` | org/xvm/compiler/ast/FileExpression.java:452 |
| ForStatement | `init` | org/xvm/compiler/ast/ForStatement.java:534 |
| FunctionTypeExpression | `function` | org/xvm/compiler/ast/FunctionTypeExpression.java:172 |
| FunctionTypeExpression | `conditional` | org/xvm/compiler/ast/FunctionTypeExpression.java:173 |
| FunctionTypeExpression | `returnValues` | org/xvm/compiler/ast/FunctionTypeExpression.java:174 |
| FunctionTypeExpression | `paramTypes` | org/xvm/compiler/ast/FunctionTypeExpression.java:175 |
| FunctionTypeExpression | `lEndPos` | org/xvm/compiler/ast/FunctionTypeExpression.java:176 |
| GotoStatement | `keyword` | org/xvm/compiler/ast/GotoStatement.java:173 |
| GotoStatement | `name` | org/xvm/compiler/ast/GotoStatement.java:174 |
| ImportStatement | `cond` | org/xvm/compiler/ast/ImportStatement.java:274 |
| ImportStatement | `keyword` | org/xvm/compiler/ast/ImportStatement.java:275 |
| ImportStatement | `qualifiedName` | org/xvm/compiler/ast/ImportStatement.java:277 |
| InvocationExpression | `async` | org/xvm/compiler/ast/InvocationExpression.java:3020 |
| InvocationExpression | `lEndPos` | org/xvm/compiler/ast/InvocationExpression.java:3022 |
| KeywordTypeExpression | `keyword` | org/xvm/compiler/ast/KeywordTypeExpression.java:85 |
| LabeledStatement | `label` | org/xvm/compiler/ast/LabeledStatement.java:157 |
| LambdaExpression | `operator` | org/xvm/compiler/ast/LambdaExpression.java:1451 |
| LambdaExpression | `body` | org/xvm/compiler/ast/LambdaExpression.java:1452 |
| LambdaExpression | `lStartPos` | org/xvm/compiler/ast/LambdaExpression.java:1453 |
| ListExpression | `exprs` | org/xvm/compiler/ast/ListExpression.java:379 |
| ListExpression | `lStartPos` | org/xvm/compiler/ast/ListExpression.java:380 |
| ListExpression | `lEndPos` | org/xvm/compiler/ast/ListExpression.java:381 |
| LiteralExpression | `literal` | org/xvm/compiler/ast/LiteralExpression.java:427 |
| MapExpression | `keys` | org/xvm/compiler/ast/MapExpression.java:412 |
| MapExpression | `values` | org/xvm/compiler/ast/MapExpression.java:413 |
| MapExpression | `lEndPos` | org/xvm/compiler/ast/MapExpression.java:414 |
| MethodDeclarationStatement | `body` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1248 |
| MultipleLValueStatement | `lValues` | org/xvm/compiler/ast/MultipleLValueStatement.java:509 |
| NameExpression | `amp` | org/xvm/compiler/ast/NameExpression.java:3151 |
| NameExpression | `name` | org/xvm/compiler/ast/NameExpression.java:3152 |
| NameExpression | `params` | org/xvm/compiler/ast/NameExpression.java:3153 |
| NameExpression | `lEndPos` | org/xvm/compiler/ast/NameExpression.java:3154 |
| NamedTypeExpression | `left` | org/xvm/compiler/ast/NamedTypeExpression.java:1098 |
| NamedTypeExpression | `immutable` | org/xvm/compiler/ast/NamedTypeExpression.java:1099 |
| NamedTypeExpression | `names` | org/xvm/compiler/ast/NamedTypeExpression.java:1100 |
| NamedTypeExpression | `access` | org/xvm/compiler/ast/NamedTypeExpression.java:1101 |
| NamedTypeExpression | `nonnarrow` | org/xvm/compiler/ast/NamedTypeExpression.java:1102 |
| NamedTypeExpression | `paramTypes` | org/xvm/compiler/ast/NamedTypeExpression.java:1103 |
| NamedTypeExpression | `lStartPos` | org/xvm/compiler/ast/NamedTypeExpression.java:1104 |
| NamedTypeExpression | `lEndPos` | org/xvm/compiler/ast/NamedTypeExpression.java:1105 |
| NewExpression | `operator` | org/xvm/compiler/ast/NewExpression.java:1607 |
| NewExpression | `dims` | org/xvm/compiler/ast/NewExpression.java:1610 |
| NewExpression | `lEndPos` | org/xvm/compiler/ast/NewExpression.java:1613 |
| NonBindingExpression | `lStartPos` | org/xvm/compiler/ast/NonBindingExpression.java:129 |
| NonBindingExpression | `lEndPos` | org/xvm/compiler/ast/NonBindingExpression.java:130 |
| NotNullExpression | `operator` | org/xvm/compiler/ast/NotNullExpression.java:274 |
| NullableTypeExpression | `lEndPos` | org/xvm/compiler/ast/NullableTypeExpression.java:94 |
| Parameter | `type` | org/xvm/compiler/ast/Parameter.java:119 |
| Parameter | `name` | org/xvm/compiler/ast/Parameter.java:120 |
| Parameter | `value` | org/xvm/compiler/ast/Parameter.java:121 |
| PrefixExpression | `operator` | org/xvm/compiler/ast/PrefixExpression.java:212 |
| PropertyDeclarationStatement | `condition` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:837 |
| PropertyDeclarationStatement | `modifiers` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:838 |
| PropertyDeclarationStatement | `annotations` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:839 |
| PropertyDeclarationStatement | `type` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:840 |
| PropertyDeclarationStatement | `name` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:841 |
| PropertyDeclarationStatement | `tokAsn` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:842 |
| PropertyDeclarationStatement | `body` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:844 |
| PropertyDeclarationStatement | `doc` | org/xvm/compiler/ast/PropertyDeclarationStatement.java:845 |
| ReturnStatement | `keyword` | org/xvm/compiler/ast/ReturnStatement.java:484 |
| ReturnStatement | `exprs` | org/xvm/compiler/ast/ReturnStatement.java:485 |
| StatementBlock | `source` | org/xvm/compiler/ast/StatementBlock.java:1684 |
| StatementBlock | `lStartPos` | org/xvm/compiler/ast/StatementBlock.java:1686 |
| StatementBlock | `lEndPos` | org/xvm/compiler/ast/StatementBlock.java:1687 |
| SwitchExpression | `keyword` | org/xvm/compiler/ast/SwitchExpression.java:340 |
| SwitchExpression | `cond` | org/xvm/compiler/ast/SwitchExpression.java:341 |
| SwitchExpression | `contents` | org/xvm/compiler/ast/SwitchExpression.java:342 |
| SwitchExpression | `lEndPos` | org/xvm/compiler/ast/SwitchExpression.java:343 |
| SwitchStatement | `block` | org/xvm/compiler/ast/SwitchStatement.java:602 |
| TemplateExpression | `exprs` | org/xvm/compiler/ast/TemplateExpression.java:293 |
| TemplateExpression | `lStartPos` | org/xvm/compiler/ast/TemplateExpression.java:294 |
| TemplateExpression | `lEndPos` | org/xvm/compiler/ast/TemplateExpression.java:295 |
| ThrowExpression | `keyword` | org/xvm/compiler/ast/ThrowExpression.java:374 |
| TryStatement | `keyword` | org/xvm/compiler/ast/TryStatement.java:532 |
| TryStatement | `resources` | org/xvm/compiler/ast/TryStatement.java:533 |
| TryStatement | `block` | org/xvm/compiler/ast/TryStatement.java:534 |
| TryStatement | `catches` | org/xvm/compiler/ast/TryStatement.java:535 |
| TupleExpression | `exprs` | org/xvm/compiler/ast/TupleExpression.java:475 |
| TupleExpression | `m_lStartPos` | org/xvm/compiler/ast/TupleExpression.java:476 |
| TupleExpression | `m_lEndPos` | org/xvm/compiler/ast/TupleExpression.java:477 |
| TupleTypeExpression | `paramTypes` | org/xvm/compiler/ast/TupleTypeExpression.java:87 |
| TupleTypeExpression | `lStartPos` | org/xvm/compiler/ast/TupleTypeExpression.java:88 |
| TupleTypeExpression | `lEndPos` | org/xvm/compiler/ast/TupleTypeExpression.java:89 |
| TypeCompositionStatement | `category` | org/xvm/compiler/ast/TypeCompositionStatement.java:3125 |
| TypeCompositionStatement | `name` | org/xvm/compiler/ast/TypeCompositionStatement.java:3126 |
| TypedefStatement | `cond` | org/xvm/compiler/ast/TypedefStatement.java:132 |
| TypedefStatement | `modifier` | org/xvm/compiler/ast/TypedefStatement.java:133 |
| TypedefStatement | `alias` | org/xvm/compiler/ast/TypedefStatement.java:134 |
| TypedefStatement | `type` | org/xvm/compiler/ast/TypedefStatement.java:135 |
| VariableDeclarationStatement | `term` | org/xvm/compiler/ast/VariableDeclarationStatement.java:306 |
| VariableTypeExpression | `token` | org/xvm/compiler/ast/VariableTypeExpression.java:66 |
| VersionOverride | `verb` | org/xvm/compiler/ast/VersionOverride.java:105 |
| VersionOverride | `exprVer` | org/xvm/compiler/ast/VersionOverride.java:110 |
| WhileStatement | `lEndPos` | org/xvm/compiler/ast/WhileStatement.java:610 |

### org.xvm.javajit (with builders, registers) (2)

| Class | Field | Location |
|---|---|---|
| Scope | `parent` | org/xvm/javajit/Scope.java:52 |
| CommonBuilder | `constants` | org/xvm/javajit/builders/CommonBuilder.java:150 |

### org.xvm.runtime (with debug, gc) (22)

| Class | Field | Location |
|---|---|---|
| ClassTemplate | `VOID` (static) | org/xvm/runtime/ClassTemplate.java:2428 |
| ClassTemplate | `THIS` (static) | org/xvm/runtime/ClassTemplate.java:2429 |
| ClassTemplate | `OBJECT` (static) | org/xvm/runtime/ClassTemplate.java:2430 |
| ClassTemplate | `INT` (static) | org/xvm/runtime/ClassTemplate.java:2431 |
| ClassTemplate | `STRING` (static) | org/xvm/runtime/ClassTemplate.java:2432 |
| ClassTemplate | `BOOLEAN` (static) | org/xvm/runtime/ClassTemplate.java:2433 |
| ClassTemplate | `BYTES` (static) | org/xvm/runtime/ClassTemplate.java:2434 |
| DebugConsole$VarDisplay | `indent` | org/xvm/runtime/DebugConsole.java:2316 |
| DebugConsole$VarDisplay | `path` | org/xvm/runtime/DebugConsole.java:2317 |
| DebugConsole$VarDisplay | `name` | org/xvm/runtime/DebugConsole.java:2318 |
| DebugConsole$VarDisplay | `hVar` | org/xvm/runtime/DebugConsole.java:2319 |
| DebugConsole$VarDisplay | `canExpand` | org/xvm/runtime/DebugConsole.java:2322 |
| DebugConsole$VarDisplay | `expanded` | org/xvm/runtime/DebugConsole.java:2323 |
| DebugConsole$Watch | `form` | org/xvm/runtime/DebugConsole.java:2406 |
| DebugConsole$Watch | `path` | org/xvm/runtime/DebugConsole.java:2407 |
| DebugConsole$Watch | `name` | org/xvm/runtime/DebugConsole.java:2408 |
| DebugConsole$Watch | `hVar` | org/xvm/runtime/DebugConsole.java:2409 |
| DebugConsole | `prefs` | org/xvm/runtime/DebugConsole.java:2436 |
| Frame$StackFrame | `frame` | org/xvm/runtime/Frame.java:2088 |
| Frame$StackFrame | `function` | org/xvm/runtime/Frame.java:2090 |
| Frame$StackFrame | `iPC` | org/xvm/runtime/Frame.java:2091 |
| ObjectHandle$JavaLong | `m_lValue` | org/xvm/runtime/ObjectHandle.java:696 |

### org.xvm.runtime.template (all subpackages) (11)

| Class | Field | Location |
|---|---|---|
| xRTClassTemplate | `NO_TEMPLATES` (static) | org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:573 |
| xRTFunction$DelegatingHandle | `m_hDelegate` | org/xvm/runtime/template/_native/reflect/xRTFunction.java:675 |
| xRTFunction$SingleBoundHandle | `m_iArg` | org/xvm/runtime/template/_native/reflect/xRTFunction.java:913 |
| xRTFunction$SingleBoundHandle | `m_hArg` | org/xvm/runtime/template/_native/reflect/xRTFunction.java:918 |
| xLocalClock | `TIMER` (static) | org/xvm/runtime/template/_native/temporal/xLocalClock.java:319 |
| xRTConnector$ConnectorHandle | `f_sslContext` | org/xvm/runtime/template/_native/web/xRTConnector.java:358 |
| xTuple$TupleHandle | `m_ahValue` | org/xvm/runtime/template/collections/xTuple.java:668 |
| BaseInt128$LongLongHandle | `m_llValue` | org/xvm/runtime/template/numbers/BaseInt128.java:554 |
| xFPLiteral$FPNHandle | `m_decValue` | org/xvm/runtime/template/numbers/xFPLiteral.java:223 |
| xIntLiteral$IntNHandle | `m_piValue` | org/xvm/runtime/template/numbers/xIntLiteral.java:471 |
| xEnum$EnumHandle | `m_index` | org/xvm/runtime/template/xEnum.java:436 |

### org.xvm.tool (3)

| Class | Field | Location |
|---|---|---|
| Launcher | `m_options` | org/xvm/tool/Launcher.java:125 |
| ModuleInfo$Node | `m_parent` | org/xvm/tool/ModuleInfo.java:1057 |
| ModuleInfo$Node | `m_file` | org/xvm/tool/ModuleInfo.java:1062 |

### org.xvm.util (javatools_utils, with concurrent, converter) (2)

| Class | Field | Location |
|---|---|---|
| Lazy$Bound | `function` | org/xvm/util/Lazy.java:425 |
| AbstractConverterEntry | `storage` | org/xvm/util/converter/AbstractConverterEntry.java:15 |

## Category 3: effectively final

Fields that are assigned once in their useful life, in a way javac does not accept for `final`. Each subsection names the technicality, the change that would make the fields `final`, and a rough cost (low: mechanical, under a day; medium: one class hierarchy, days; high: a redesign of a lifecycle).

### Definite assignment (proven)

These 150 fields are assigned only in constructors, but javac rejected them as `final` with "might not have been initialized" (some constructor does not assign it, or reads it first) or "might already have been assigned" (assigned on more than one path). They are the cheapest category 3 fix.

- **Technicality:** one constructor (usually the deserialization or the synthetic one) leaves the field at its default value, or a constructor assigns it in several branches.
- **Fix:** assign the default explicitly (`-1`, `null`, `false`) in the constructors that skip it, or compute the value in a local and assign it once; then declare the field `final`.
- **Cost:** low. One line per constructor; no behavior change.

"javac error at" is where javac reported the flow error: for "not initialized" that is the closing brace of a constructor that does not assign the field (or a read before the assignment), for "assigned twice" the second assignment.

#### Constant pool index fields (58)

The deserialization constructor stores the index that `resolveConstants()` later reads; the value constructors never touch it (read in code for `ArrayConstant`, `SignatureConstant`, `ModuleConstant`). After `resolveConstants()` the index is dead, so the alternative to a sentinel is to pass the indexes to resolution in a side array and drop the field.

| Class | Field | Location | javac error at | Assigned in constructors at |
|---|---|---|---|---|
| Annotation | `m_iClass` | org/xvm/asm/Annotation.java:368 | not initialized :62 | 76 |
| Annotation | `m_aiParam` | org/xvm/asm/Annotation.java:373 | not initialized :86 | 84 |
| AbstractDependantTypeConstant | `m_iTypeParent` | org/xvm/asm/constants/AbstractDependantTypeConstant.java:338 | not initialized :43 | 58 |
| AccessTypeConstant | `m_iType` | org/xvm/asm/constants/AccessTypeConstant.java:304 | not initialized :61 | 76 |
| AnnotatedTypeConstant | `m_iAnno` | org/xvm/asm/constants/AnnotatedTypeConstant.java:781 | not initialized :71,94 | 109 |
| AnnotatedTypeConstant | `m_iType` | org/xvm/asm/constants/AnnotatedTypeConstant.java:786 | not initialized :71,94 | 110 |
| AnonymousClassTypeConstant | `m_iAnon` | org/xvm/asm/constants/AnonymousClassTypeConstant.java:270 | not initialized :81 | 96 |
| ArrayConstant | `m_iType` | org/xvm/asm/constants/ArrayConstant.java:313 | not initialized :44 | 67 |
| ArrayConstant | `m_aiVal` | org/xvm/asm/constants/ArrayConstant.java:318 | not initialized :44 | 68 |
| ChildClassConstant | `m_iParent` | org/xvm/asm/constants/ChildClassConstant.java:203 | not initialized :53 | 68 |
| ChildClassConstant | `m_iName` | org/xvm/asm/constants/ChildClassConstant.java:209 | not initialized :53 | 69 |
| DecoratedClassConstant | `m_iType` | org/xvm/asm/constants/DecoratedClassConstant.java:188 | not initialized :51 | 37 |
| DynamicFormalConstant | `m_nRegId` | org/xvm/asm/constants/DynamicFormalConstant.java:246 | not initialized :48 | 63 |
| DynamicFormalConstant | `m_iType` | org/xvm/asm/constants/DynamicFormalConstant.java:251 | not initialized :48 | 64 |
| DynamicFormalConstant | `m_iFormal` | org/xvm/asm/constants/DynamicFormalConstant.java:256 | not initialized :48 | 65 |
| DynamicFormalConstant | `m_reg` | org/xvm/asm/constants/DynamicFormalConstant.java:271 | not initialized :66 | 44 |
| FSNodeConstant | `m_iName` | org/xvm/asm/constants/FSNodeConstant.java:360 | not initialized :56,87,118 | 134 |
| FSNodeConstant | `m_iCreated` | org/xvm/asm/constants/FSNodeConstant.java:365 | not initialized :56,87,118 | 135 |
| FSNodeConstant | `m_iModified` | org/xvm/asm/constants/FSNodeConstant.java:370 | not initialized :56,87,118 | 136 |
| FSNodeConstant | `m_iData` | org/xvm/asm/constants/FSNodeConstant.java:376 | not initialized :56,87,118 | 137 |
| FileStoreConstant | `m_iPath` | org/xvm/asm/constants/FileStoreConstant.java:193 | not initialized :45 | 60 |
| FileStoreConstant | `m_iDir` | org/xvm/asm/constants/FileStoreConstant.java:198 | not initialized :45 | 61 |
| ImmutableTypeConstant | `m_iType` | org/xvm/asm/constants/ImmutableTypeConstant.java:274 | not initialized :48 | 63 |
| InnerChildTypeConstant | `m_iChild` | org/xvm/asm/constants/InnerChildTypeConstant.java:180 | not initialized :64 | 79 |
| LiteralConstant | `m_iStr` | org/xvm/asm/constants/LiteralConstant.java:1653 | assigned twice/not initialized :141,156 | 156 |
| MapConstant | `m_iType` | org/xvm/asm/constants/MapConstant.java:488 | not initialized :104 | 129 |
| MapConstant | `m_aiKeys` | org/xvm/asm/constants/MapConstant.java:493 | not initialized :104 | 130 |
| MapConstant | `m_aiVals` | org/xvm/asm/constants/MapConstant.java:498 | not initialized :104 | 131 |
| MatchAnyConstant | `m_iType` | org/xvm/asm/constants/MatchAnyConstant.java:146 | not initialized :37 | 52 |
| MethodBindingConstant | `m_iMethod` | org/xvm/asm/constants/MethodBindingConstant.java:171 | not initialized :44 | 58 |
| MethodConstant | `m_iParent` | org/xvm/asm/constants/MethodConstant.java:698 | not initialized :99 | 114 |
| MethodConstant | `m_iSig` | org/xvm/asm/constants/MethodConstant.java:704 | not initialized :99 | 115 |
| ModuleConstant | `m_iName` | org/xvm/asm/constants/ModuleConstant.java:312 | not initialized :60 | 75 |
| ModuleConstant | `m_iVersion` | org/xvm/asm/constants/ModuleConstant.java:317 | not initialized :60 | 76 |
| MultiCondition | `m_aiCond` | org/xvm/asm/constants/MultiCondition.java:359 | not initialized :54 | 79 |
| NamedCondition | `m_iName` | org/xvm/asm/constants/NamedCondition.java:152 | not initialized :37 | 52 |
| NamedConstant | `m_iParent` | org/xvm/asm/constants/NamedConstant.java:191 | not initialized :45 | 60 |
| NamedConstant | `m_iName` | org/xvm/asm/constants/NamedConstant.java:197 | not initialized :45 | 61 |
| NotCondition | `m_iCond` | org/xvm/asm/constants/NotCondition.java:193 | not initialized :45 | 62 |
| ParameterizedTypeConstant | `m_iType` | org/xvm/asm/constants/ParameterizedTypeConstant.java:1114 | not initialized :79 | 94 |
| ParentClassConstant | `m_iChild` | org/xvm/asm/constants/ParentClassConstant.java:200 | not initialized :44 | 59 |
| PresentCondition | `m_iStruct` | org/xvm/asm/constants/PresentCondition.java:190 | not initialized :38 | 53 |
| PropertyClassTypeConstant | `m_iProp` | org/xvm/asm/constants/PropertyClassTypeConstant.java:490 | not initialized :82 | 97 |
| PureIdentityConstant | `m_iType` | org/xvm/asm/constants/PureIdentityConstant.java:188 | not initialized :46 | 34 |
| RangeConstant | `m_iVal1` | org/xvm/asm/constants/RangeConstant.java:364 | not initialized :50 | 86 |
| RangeConstant | `m_iVal2` | org/xvm/asm/constants/RangeConstant.java:369 | not initialized :50 | 87 |
| RegisterConstant | `m_reg` | org/xvm/asm/constants/RegisterConstant.java:158 | not initialized :55 | 38 |
| RelationalTypeConstant | `m_iType1` | org/xvm/asm/constants/RelationalTypeConstant.java:730 | not initialized :56 | 71 |
| RelationalTypeConstant | `m_iType2` | org/xvm/asm/constants/RelationalTypeConstant.java:735 | not initialized :56 | 72 |
| ServiceTypeConstant | `m_iType` | org/xvm/asm/constants/ServiceTypeConstant.java:179 | not initialized :38 | 53 |
| SignatureConstant | `m_iName` | org/xvm/asm/constants/SignatureConstant.java:909 | not initialized :94,116 | 131 |
| SingletonConstant | `m_iClass` | org/xvm/asm/constants/SingletonConstant.java:275 | not initialized :56 | 72 |
| TerminalTypeConstant | `m_iDef` | org/xvm/asm/constants/TerminalTypeConstant.java:2135 | not initialized :71 | 86 |
| ThisClassConstant | `m_iClass` | org/xvm/asm/constants/ThisClassConstant.java:155 | not initialized :32 | 47 |
| VersionMatchesCondition | `m_iModule` | org/xvm/asm/constants/VersionMatchesCondition.java:195 | not initialized :39 | 54 |
| VersionMatchesCondition | `m_iVer` | org/xvm/asm/constants/VersionMatchesCondition.java:200 | not initialized :39 | 55 |
| VersionedCondition | `m_iVer` | org/xvm/asm/constants/VersionedCondition.java:195 | not initialized :45 | 60 |
| VirtualChildTypeConstant | `m_iName` | org/xvm/asm/constants/VirtualChildTypeConstant.java:367 | not initialized :65,94 | 109 |

#### Op argument fields (30)

The assembly constructor (from compiler `Argument`s) sets these; the deserialization constructor, used for code loaded from a module, does not (read in code for `Call_N0`). Assign `null` in the deserialization constructor.

| Class | Field | Location | javac error at | Assigned in constructors at |
|---|---|---|---|---|
| AssertV | `m_aArgValue` | org/xvm/asm/op/AssertV.java:397 | not initialized :75 | 62 |
| Call_N0 | `m_aArgValue` | org/xvm/asm/op/Call_N0.java:170 | not initialized :52 | 38 |
| Call_N1 | `m_aArgValue` | org/xvm/asm/op/Call_N1.java:185 | not initialized :58 | 42 |
| Call_NN | `m_aArgValue` | org/xvm/asm/op/Call_NN.java:187 | not initialized :55 | 39 |
| Call_NT | `m_aArgValue` | org/xvm/asm/op/Call_NT.java:188 | not initialized :58 | 42 |
| Construct_N | `m_aArgValue` | org/xvm/asm/op/Construct_N.java:136 | not initialized :51 | 37 |
| FBind | `m_aArgParam` | org/xvm/asm/op/FBind.java:474 | not initialized :96 | 72 |
| GP_DivRem | `m_aargReturn` | org/xvm/asm/op/GP_DivRem.java:263 | not initialized :65 | 51 |
| Invoke_N0 | `m_aArgValue` | org/xvm/asm/op/Invoke_N0.java:130 | not initialized :52 | 38 |
| Invoke_N1 | `m_aArgValue` | org/xvm/asm/op/Invoke_N1.java:138 | not initialized :56 | 40 |
| Invoke_NN | `m_aArgValue` | org/xvm/asm/op/Invoke_NN.java:147 | not initialized :60 | 44 |
| Invoke_NT | `m_aArgValue` | org/xvm/asm/op/Invoke_NT.java:145 | not initialized :58 | 42 |
| JumpVal_N | `m_aArgCond` | org/xvm/asm/op/JumpVal_N.java:549 | not initialized :88 | 66 |
| MoveThis | `m_nAccess` | org/xvm/asm/op/MoveThis.java:217 | not initialized :75,92 | 63,66,69,72 |
| NewCG_N | `m_aArgValue` | org/xvm/asm/op/NewCG_N.java:156 | not initialized :64 | 45 |
| NewC_N | `m_aArgValue` | org/xvm/asm/op/NewC_N.java:146 | not initialized :60 | 43 |
| NewG_N | `m_aArgValue` | org/xvm/asm/op/NewG_N.java:150 | not initialized :61 | 43 |
| NewV_N | `m_aArgValue` | org/xvm/asm/op/NewV_N.java:165 | not initialized :65 | 48 |
| New_N | `m_aArgValue` | org/xvm/asm/op/New_N.java:143 | not initialized :63 | 47 |
| OpSwitch | `m_aConstCase` | org/xvm/asm/op/OpSwitch.java:457 | not initialized :85 | 61 |
| Return_N | `m_aArg` | org/xvm/asm/op/Return_N.java:176 | not initialized :44 | 32 |
| Throw | `m_argValue` | org/xvm/asm/op/Throw.java:114 | not initialized :52 | 41 |
| Var_M | `m_aArgKey` | org/xvm/asm/op/Var_M.java:127 | not initialized :72 | 45 |
| Var_M | `m_aArgVal` | org/xvm/asm/op/Var_M.java:128 | not initialized :72 | 46 |
| Var_MN | `m_aArgKey` | org/xvm/asm/op/Var_MN.java:144 | not initialized :77 | 48 |
| Var_MN | `m_aArgVal` | org/xvm/asm/op/Var_MN.java:145 | not initialized :77 | 49 |
| Var_S | `m_aArgValue` | org/xvm/asm/op/Var_S.java:139 | not initialized :60 | 46 |
| Var_SN | `m_aArgValue` | org/xvm/asm/op/Var_SN.java:158 | not initialized :67 | 52 |
| Var_T | `m_aArgValue` | org/xvm/asm/op/Var_T.java:118 | not initialized :56 | 42 |
| Var_TN | `m_aArgValue` | org/xvm/asm/op/Var_TN.java:135 | not initialized :64 | 49 |

#### Other classes (62)

Mostly compiler AST nodes with extra constructors for synthetic nodes (`MethodDeclarationStatement` has two constructors for synthetic methods, from an expression and from an `eval` statement block, that set only `body`; read in code), `tool/ModuleInfo` fields assigned in several constructor branches, and a few runtime and debugger classes.

| Class | Field | Location | javac error at | Assigned in constructors at |
|---|---|---|---|---|
| ClassStructure$SimpleTypeResolver | `m_listActual` | org/xvm/asm/ClassStructure.java:3773 | assigned twice :3720 | 3708,3720 |
| ErrorListener$ErrorInfo | `m_source` | org/xvm/asm/ErrorListener.java:1065 | not initialized :852 | 830 |
| ErrorListener$ErrorInfo | `m_lPosStart` | org/xvm/asm/ErrorListener.java:1066 | not initialized :852 | 831 |
| ErrorListener$ErrorInfo | `m_lPosEnd` | org/xvm/asm/ErrorListener.java:1067 | not initialized :852 | 832 |
| ErrorListener$ErrorInfo | `m_xs` | org/xvm/asm/ErrorListener.java:1068 | not initialized :833 | 850 |
| FileStructure | `m_fLazyDeser` | org/xvm/asm/FileStructure.java:1595 | not initialized :98,181 | 153 |
| OpVar | `m_reg` | org/xvm/asm/OpVar.java:333 | not initialized :65 | 51 |
| Scope | `m_opEnter` | org/xvm/asm/Scope.java:249 | not initialized :13 | 23 |
| Version | `build` | org/xvm/asm/Version.java:631 | not initialized :276 | 197,262 |
| Source | `m_node` | org/xvm/compiler/Source.java:589 | not initialized :123 | 80 |
| Source | `m_file` | org/xvm/compiler/Source.java:599 | assigned twice :66 | 66,77 |
| AnnotationExpression | `m_node` | org/xvm/compiler/ast/AnnotationExpression.java:586 | not initialized :58 | 62 |
| CompositionNode$Extends | `args` | org/xvm/compiler/ast/CompositionNode.java:182 | not initialized :145 | 150 |
| CompositionNode$Extends | `lEndPos` | org/xvm/compiler/ast/CompositionNode.java:183 | not initialized :145 | 151 |
| Expression$Assignable | `m_arg` | org/xvm/compiler/ast/Expression.java:2762 | not initialized :2082,2160,2174 | 2091,2120,2132,2146 |
| Expression$Assignable | `m_prop` | org/xvm/compiler/ast/Expression.java:2763 | not initialized :2092,2134,2148 | 2121,2158,2172 |
| Expression$Assignable | `m_oIndex` | org/xvm/compiler/ast/Expression.java:2764 | not initialized :2122 | 2133,2147,2159,2173 |
| FileExpression | `m_file` | org/xvm/compiler/ast/FileExpression.java:457 | not initialized :57 | 53 |
| FileExpression | `m_dir` | org/xvm/compiler/ast/FileExpression.java:462 | not initialized :57 | 55 |
| ImportStatement | `alias` | org/xvm/compiler/ast/ImportStatement.java:276 | not initialized :63 | 44 |
| ImportStatement | `star` | org/xvm/compiler/ast/ImportStatement.java:278 | not initialized :46 | 62 |
| LambdaExpression | `params` | org/xvm/compiler/ast/LambdaExpression.java:1449 | not initialized :93 | 87 |
| LambdaExpression | `paramNames` | org/xvm/compiler/ast/LambdaExpression.java:1450 | not initialized :93 | 85 |
| MethodDeclarationStatement | `condition` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1239 | not initialized :115,132 | 75 |
| MethodDeclarationStatement | `modifiers` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1240 | not initialized :115,132 | 76 |
| MethodDeclarationStatement | `annotations` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1241 | not initialized :115,132 | 77 |
| MethodDeclarationStatement | `typeParams` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1242 | not initialized :115,132 | 79 |
| MethodDeclarationStatement | `conditional` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1243 | not initialized :115,132 | 78 |
| MethodDeclarationStatement | `returns` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1244 | not initialized :115,132 | 80 |
| MethodDeclarationStatement | `name` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1245 | not initialized :115,132 | 81 |
| MethodDeclarationStatement | `redundant` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1246 | not initialized :115,132 | 82 |
| MethodDeclarationStatement | `params` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1247 | not initialized :115,132 | 83 |
| MethodDeclarationStatement | `doc` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1249 | not initialized :115,132 | 87 |
| MethodDeclarationStatement | `m_tokFinally` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1251 | not initialized :115,132 | 85 |
| MethodDeclarationStatement | `m_bodyFinally` | org/xvm/compiler/ast/MethodDeclarationStatement.java:1252 | not initialized :115,132 | 86 |
| NamedTypeExpression | `module` | org/xvm/compiler/ast/NamedTypeExpression.java:1097 | not initialized :67,82,100 | 107 |
| TypeCompositionStatement | `source` | org/xvm/compiler/ast/TypeCompositionStatement.java:3121 | not initialized :170,199 | 113,135,212 |
| TypeCompositionStatement | `condition` | org/xvm/compiler/ast/TypeCompositionStatement.java:3122 | not initialized :116,223 | 136 |
| TypeCompositionStatement | `modifiers` | org/xvm/compiler/ast/TypeCompositionStatement.java:3123 | not initialized :116,170,199,223 | 137 |
| TypeCompositionStatement | `annotations` | org/xvm/compiler/ast/TypeCompositionStatement.java:3124 | not initialized :116,223 | 138,163,188 |
| TypeCompositionStatement | `typeParams` | org/xvm/compiler/ast/TypeCompositionStatement.java:3128 | not initialized :170 | 142,191 |
| TypeCompositionStatement | `constructorParams` | org/xvm/compiler/ast/TypeCompositionStatement.java:3129 | not initialized :199 | 143 |
| TypeCompositionStatement | `typeArgs` | org/xvm/compiler/ast/TypeCompositionStatement.java:3130 | not initialized :147 | 166,218 |
| TypeCompositionStatement | `args` | org/xvm/compiler/ast/TypeCompositionStatement.java:3131 | not initialized :147 | 167,193 |
| TypeCompositionStatement | `doc` | org/xvm/compiler/ast/TypeCompositionStatement.java:3134 | not initialized :116,199,223 | 146,169 |
| TypeCompositionStatement | `m_fAnon` | org/xvm/compiler/ast/TypeCompositionStatement.java:3140 | not initialized :147 | 195 |
| DebugConsole$BreakPoint | `oneTime` | org/xvm/runtime/DebugConsole.java:2216 | not initialized :2036,2047 | 2041 |
| DebugConsole$VarDisplay | `isArray` | org/xvm/runtime/DebugConsole.java:2320 | not initialized :2286 | 2283 |
| DebugConsole$VarDisplay | `size` | org/xvm/runtime/DebugConsole.java:2321 | not initialized :2286 | 2284 |
| DebugConsole$Watch | `sVar` | org/xvm/runtime/DebugConsole.java:2410 | not initialized :2340,2368 | 2353 |
| DebugConsole$Watch | `index` | org/xvm/runtime/DebugConsole.java:2411 | not initialized :2354 | 2367 |
| Frame$VarInfo | `m_resolver` | org/xvm/runtime/Frame.java:2478 | not initialized :2497 | 2507 |
| Frame$VarInfo | `m_nTargetId` | org/xvm/runtime/Frame.java:2479 | not initialized :2497 | 2503 |
| PropertyComposition | `m_clzInception` | org/xvm/runtime/PropertyComposition.java:369 | not initialized :55 | 67 |
| xRef$RefHandle | `m_idProp` | org/xvm/runtime/template/reflect/xRef.java:1069 | not initialized :814,877 | 844 |
| ModuleInfo$DirNode | `m_fileSrc` | org/xvm/tool/ModuleInfo.java:1391 | not initialized :1132 | 1129 |
| ModuleInfo$FileNode | `m_text` | org/xvm/tool/ModuleInfo.java:1647 | not initialized :1427 | 1436 |
| ModuleInfo | `fileName` | org/xvm/tool/ModuleInfo.java:2002 | assigned twice :147,156,168,218... | 84,145,147,156,168 |
| ModuleInfo | `projectDir` | org/xvm/tool/ModuleInfo.java:2004 | assigned twice/not initialized :241,243,245,247 | 86,192,243,245,247 |
| ModuleInfo | `sourceDir` | org/xvm/tool/ModuleInfo.java:2007 | assigned twice/not initialized :242,254,255,258... | 85,192,211,255,284 |
| ModuleInfo | `sourceFile` | org/xvm/tool/ModuleInfo.java:2008 | assigned twice/not initialized :253,267,285 | 82,219,267,285 |
| ModuleInfo | `sourceIsTree` | org/xvm/tool/ModuleInfo.java:2009 | not initialized :93 | 302 |

### Assignment through a constructor-only helper

- **Technicality:** the constructor calls a private method that assigns the fields; javac only accepts assignments in the constructor body itself.
- **Fix:** have the helper return the value (a record or array for several fields) and assign in the constructor.
- **Cost:** low.

| Class | Field | Location | Helper and line |
|---|---|---|---|
| SimulatedLinkerContext | `names` | org/xvm/asm/SimulatedLinkerContext.java:189 | `extractRequiredConditions`:85 |
| SimulatedLinkerContext | `present` | org/xvm/asm/SimulatedLinkerContext.java:190 | `extractRequiredConditions`:90 |
| SimulatedLinkerContext | `modules` | org/xvm/asm/SimulatedLinkerContext.java:191 | `extractRequiredConditions`:95 |
| SimulatedLinkerContext | `version` | org/xvm/asm/SimulatedLinkerContext.java:192 | `extractRequiredConditions`:101 |
| DebugConsole | `m_listHistory` | org/xvm/runtime/DebugConsole.java:78 | `loadHistory`:1310 |
| NativeContainer | `m_moduleSystem` | org/xvm/runtime/NativeContainer.java:895 | `loadNativeTemplates`:145 |
| NativeContainer | `m_moduleTurtle` | org/xvm/runtime/NativeContainer.java:896 | `loadNativeTemplates`:146 |
| NativeContainer | `m_moduleNative` | org/xvm/runtime/NativeContainer.java:897 | `loadNativeTemplates`:147 |
| Decimal128 | `m_nHBits` | org/xvm/type/Decimal128.java:512 | `convertBigDecToLongs`:324,398 |
| Decimal128 | `m_nLBits` | org/xvm/type/Decimal128.java:517 | `convertBigDecToLongs`:325,399 |

`PackedInteger` (finding 3) is the same pattern through public setters, so the call-graph rule does not catch it; it is listed under [setters](#setters-used-while-building).

### Two-phase setup: deserialization and registration

329 fields whose writes outside constructors are only in `disassemble`/`resolveConstants`/`read*` (deserialization) or `registerConstants`/`write`/`assemble` (registration into a pool and assembly), sometimes plus setters.

- **Technicality, deserialization:** a pool or structure is read in one pass, and an entry can refer to entries after it. The constructor stores indexes; `resolveConstants()`/`disassemble()` looks them up after the pass.
- **Technicality, registration:** `registerConstants(pool)` replaces each referenced constant with the copy registered in the target pool (`m_constType = pool.register(m_constType)`), and `write()`/`assemble()` caches register and constant indexes in `Op` fields. The object is rewritten in place instead of copied.
- **Fix:** for deserialization, either read all records first and construct the constants afterwards in dependency order, or keep a `final` index plus the `final` pool and resolve on access (a lookup per access, or a `Lazy`). For registration, make registration produce a new constant (copy-on-register) instead of mutating sub-constants; `ConstantPool.register` already returns the canonical copy for the top-level constant. For `Op` indexes, keep the assembled form apart from the compiler form: ops the runtime executes are constructed from bytes (inferred), where the indexes are constructor values.
- **Cost:** high for `Constant` and `Component` (the pool reader, registration and every subclass). Medium for `Op` (215 classes, but one pattern).
- **Until then:** a library pool must be completely resolved and registered before it is handed to other threads, through a happens-before edge (for example a `ConcurrentHashMap` publish of the finished `FileStructure`).

#### org.xvm.asm (84)

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| Annotation | org/xvm/asm/Annotation.java | `m_aParams`:383 | `resolveConstants`:98,104; `resolveParams`:203; `registerConstants`:304 |
| ClassStructure | org/xvm/asm/ClassStructure.java | `m_constPath`:3789 | `setSourcePath`:98; `disassemble`:3316; `registerConstants`:3334 |
| Component$Contribution | org/xvm/asm/Component.java | `m_constProp`:3427, `m_annotation`:3432 | `registerConstants`:3205,3209 |
| FileStructure | org/xvm/asm/FileStructure.java | `m_nMajorVer`:1579, `m_nMinorVer`:1585, `m_kind`:1590, `m_pool`:1621 | `disassemble`:1321,1322,1323...; `assemble`:1370,1371; `setFileKind`:384 |
| MethodStructure$Source | org/xvm/asm/MethodStructure.java | `m_iFirstLine`:2959 | `disassemble`:2916 |
| MethodStructure | org/xvm/asm/MethodStructure.java | `m_idFinally`:2988, `m_cTypeParams`:2998, `m_cDefaultParams`:3003, `m_idSuper`:3014, `m_registry`:3036, `m_FHasCode`:3068 | `setConstructFinally`:1277; `disassemble`:1856,1881,1919...; `registerConstants`:1937,1949,1966; `configureLambda`:434; `setShorthandInitialization`:1516; `assemble`:2041 |
| ModuleStructure | org/xvm/asm/ModuleStructure.java | `m_constDir`:1023, `m_constTimestamp`:1028 | `setSourceDir`:252; `disassemble`:840,841; `registerConstants`:860,861; `setTimestamp`:274 |
| OpCallable | org/xvm/asm/OpCallable.java | `m_nFunctionId`:1119, `m_nRetValue`:1120, `m_anRetValue`:1121, `m_argFunction`:1123, `m_argReturn`:1124 | `write`:95; `<init> in Call_01`:41,54; `write in Call_01`:63; `<init> in Call_0T`:41,54; `write in Call_0T`:63; `<init> in Call_11`:43,57; +63 more methods |
| OpCondJump | org/xvm/asm/OpCondJump.java | `m_nType`:684, `m_nArg`:685, `m_nArg2`:686, `m_argVal`:690, `m_argVal2`:691 | `write`:110,114,117; `<init> in JumpNotZero`:29; `registerConstants`:250,252 |
| OpGeneral | org/xvm/asm/OpGeneral.java | `m_nTarget`:367, `m_nArgValue`:368, `m_nRetValue`:369, `m_argTarget`:371, `m_argValue`:372, `m_argReturn`:373 | `write`:85,87,89; `registerConstants`:172,174,176 |
| OpInPlace | org/xvm/asm/OpInPlace.java | `m_nTarget`:376, `m_nRetValue`:377, `m_argTarget`:379, `m_argReturn`:380 | `<init> in IP_PostInc`:31,32; `write`:87,89; `registerConstants`:181,183 |
| OpInPlaceAssign | org/xvm/asm/OpInPlaceAssign.java | `m_nTarget`:253, `m_nArgValue`:254, `m_argTarget`:256, `m_argValue`:257 | `write`:63,64; `registerConstants`:242,243 |
| OpIndex | org/xvm/asm/OpIndex.java | `m_nTarget`:580, `m_nIndex`:581, `m_nRetValue`:582, `m_argTarget`:584, `m_argIndex`:585, `m_argReturn`:586 | `write`:107,108,110; `registerConstants`:196,197,199 |
| OpIndexInPlace | org/xvm/asm/OpIndexInPlace.java | `m_nValue`:162, `m_argValue`:164 | `write`:63; `registerConstants`:100 |
| OpInvocable | org/xvm/asm/OpInvocable.java | `m_nTarget`:618, `m_nMethodId`:619, `m_nRetValue`:620, `m_anRetValue`:621, `m_argTarget`:623, `m_argReturn`:625 | `<init> in Invoke_1N`:40,41,43...; `<init> in Invoke_1T`:40,41,43...; `write`:83,84; `<init> in Invoke_01`:39,52; `write in Invoke_01`:61; `<init> in Invoke_0T`:40,53; +22 more methods |
| OpMove | org/xvm/asm/OpMove.java | `m_nFromValue`:87, `m_nToValue`:88, `m_argFrom`:90, `m_argTo`:91 | `write`:44,45; `registerConstants`:69,70 |
| OpPropInPlace | org/xvm/asm/OpPropInPlace.java | `m_nTarget`:226, `m_nRetValue`:227, `m_argTarget`:229, `m_argReturn`:230 | `write`:84,86; `registerConstants`:158,160 |
| OpPropInPlaceAssign | org/xvm/asm/OpPropInPlaceAssign.java | `m_nTarget`:242, `m_nValue`:243, `m_argTarget`:245, `m_argValue`:246 | `write`:65,66; `registerConstants`:110,111 |
| OpProperty | org/xvm/asm/OpProperty.java | `m_nPropId`:58, `m_idProp`:60 | `write`:42; `registerConstants`:50 |
| OpTest | org/xvm/asm/OpTest.java | `m_nType`:469, `m_nValue1`:470, `m_nValue2`:471, `m_nRetValue`:472, `m_argVal1`:475, `m_argVal2`:476, `m_argReturn`:477 | `write`:105,109,112...; `registerConstants`:217,219,221 |
| OpVar | org/xvm/asm/OpVar.java | `m_nType`:343 | `preWrite in CatchStart`:70; `setTypeId in CatchStart`:82; `write`:73 |
| Parameter | org/xvm/asm/Parameter.java | `m_constType`:520, `m_constName`:525, `m_constDefault`:530 | `resolveAnnotations`:193,211; `registerConstants`:424,425,426; `setDefaultValue`:323 |
| PropertyStructure | org/xvm/asm/PropertyStructure.java | `m_accessVar`:799, `m_type`:804, `m_constVal`:809 | `setVarAccess`:120; `disassemble`:735,736,739; `setType`:157; `registerConstants`:747,750; `setInitialValue`:363 |
| TypedefStructure | org/xvm/asm/TypedefStructure.java | `m_type`:97 | `setType`:58; `disassemble`:69; `registerConstants`:76 |

#### org.xvm.asm.constants (64)

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| AbstractDependantTypeConstant | org/xvm/asm/constants/AbstractDependantTypeConstant.java | `m_typeParent`:333 | `resolveConstants`:63; `registerConstants`:318 |
| AccessTypeConstant | org/xvm/asm/constants/AccessTypeConstant.java | `m_constType`:309 | `resolveConstants`:82; `registerConstants`:258 |
| AnnotatedTypeConstant | org/xvm/asm/constants/AnnotatedTypeConstant.java | `m_annotation`:791, `m_constType`:796 | `resolveConstants`:117,118; `registerConstants`:703,704 |
| AnonymousClassTypeConstant | org/xvm/asm/constants/AnonymousClassTypeConstant.java | `m_idAnon`:275 | `resolveConstants`:110; `registerConstants`:246 |
| ArrayConstant | org/xvm/asm/constants/ArrayConstant.java | `m_constType`:329, `m_aconstVal`:334 | `resolveConstants`:75,83; `registerConstants`:279,280 |
| ChildClassConstant | org/xvm/asm/constants/ChildClassConstant.java | `m_constParent`:215, `m_constName`:220 | `resolveConstants`:76,77; `registerConstants`:180,181 |
| DecimalAutoConstant | org/xvm/asm/constants/DecimalAutoConstant.java | `m_dec`:171 | `registerConstants`:144 |
| DecoratedClassConstant | org/xvm/asm/constants/DecoratedClassConstant.java | `m_type`:193 | `resolveConstants`:55; `registerConstants`:153 |
| DynamicFormalConstant | org/xvm/asm/constants/DynamicFormalConstant.java | `m_typeReg`:261, `m_constFormal`:266 | `resolveConstants`:74,75; `registerConstants`:173,174 |
| FSNodeConstant | org/xvm/asm/constants/FSNodeConstant.java | `m_constName`:381, `m_constCreated`:391, `m_constModified`:396, `m_constData`:408 | `resolveConstants`:144,145,146...; `registerConstants`:309,310,311... |
| FileStoreConstant | org/xvm/asm/constants/FileStoreConstant.java | `m_constPath`:203, `m_constDir`:208 | `resolveConstants`:68,69; `registerConstants`:163,164 |
| ImmutableTypeConstant | org/xvm/asm/constants/ImmutableTypeConstant.java | `m_constType`:279 | `resolveConstants`:68; `registerConstants`:222 |
| InnerChildTypeConstant | org/xvm/asm/constants/InnerChildTypeConstant.java | `m_idChild`:185 | `resolveConstants`:86; `registerConstants`:156 |
| LiteralConstant | org/xvm/asm/constants/LiteralConstant.java | `m_constStr`:1658 | `resolveConstants`:161; `registerConstants`:1621 |
| MapConstant | org/xvm/asm/constants/MapConstant.java | `m_constType`:508, `m_aconstKey`:513, `m_aconstVal`:518 | `resolveConstants`:138,149,150; `registerConstants`:308,309,310 |
| MatchAnyConstant | org/xvm/asm/constants/MatchAnyConstant.java | `m_constType`:151 | `resolveConstants`:57; `registerConstants`:119 |
| MethodBindingConstant | org/xvm/asm/constants/MethodBindingConstant.java | `m_idMethod`:176 | `resolveConstants`:63; `registerConstants`:143 |
| MethodConstant | org/xvm/asm/constants/MethodConstant.java | `m_constParent`:709, `m_constSig`:714 | `resolveConstants`:123,124; `registerConstants`:632,633; `setSignature`:173 |
| ModuleConstant | org/xvm/asm/constants/ModuleConstant.java | `m_constName`:322, `m_constVersion`:327 | `resolveConstants`:83,84; `registerConstants`:282,283 |
| MultiCondition | org/xvm/asm/constants/MultiCondition.java | `m_aconstCond`:364 | `resolveConstants`:95; `registerConstants`:272 |
| NamedCondition | org/xvm/asm/constants/NamedCondition.java | `m_constName`:157 | `resolveConstants`:57; `registerConstants`:130 |
| NamedConstant | org/xvm/asm/constants/NamedConstant.java | `m_constParent`:203, `m_constName`:208 | `resolveConstants`:68,69; `registerConstants`:162,163 |
| NotCondition | org/xvm/asm/constants/NotCondition.java | `m_constCond`:198 | `resolveConstants`:67; `registerConstants`:171 |
| ParameterizedTypeConstant | org/xvm/asm/constants/ParameterizedTypeConstant.java | `m_aiTypeParams`:1119, `m_constType`:1124, `m_atypeParams`:1129, `m_typeResolverPrev`:1139, `m_typeResolvedPrev`:1144 | `resolveConstants`:110,113,120...; `registerConstants`:1058,1059,1063...; `resolveGenerics`:328,329 |
| ParentClassConstant | org/xvm/asm/constants/ParentClassConstant.java | `m_constChild`:205 | `resolveConstants`:64; `registerConstants`:173 |
| PresentCondition | org/xvm/asm/constants/PresentCondition.java | `m_constStruct`:195 | `resolveConstants`:58; `registerConstants`:168 |
| PropertyClassTypeConstant | org/xvm/asm/constants/PropertyClassTypeConstant.java | `m_idProp`:485 | `resolveConstants`:104; `registerConstants`:461 |
| PureIdentityConstant | org/xvm/asm/constants/PureIdentityConstant.java | `m_type`:193 | `resolveConstants`:50; `registerConstants`:167 |
| RangeConstant | org/xvm/asm/constants/RangeConstant.java | `m_const1`:374, `m_const2`:384 | `resolveConstants`:94,95; `registerConstants`:326,327 |
| RelationalTypeConstant | org/xvm/asm/constants/RelationalTypeConstant.java | `m_constType1`:740, `m_constType2`:745 | `resolveConstants`:79,80; `registerConstants`:698,699 |
| ServiceTypeConstant | org/xvm/asm/constants/ServiceTypeConstant.java | `m_constType`:184 | `resolveConstants`:58; `registerConstants`:157 |
| SignatureConstant | org/xvm/asm/constants/SignatureConstant.java | `m_aiParams`:914, `m_aiReturns`:919, `m_constName`:924, `m_aconstParams`:929, `m_aconstReturns`:934 | `resolveConstants`:140,141,142...; `registerConstants`:745,746,747 |
| SingletonConstant | org/xvm/asm/constants/SingletonConstant.java | `m_constClass`:280 | `resolveConstants`:77; `registerConstants`:242 |
| ThisClassConstant | org/xvm/asm/constants/ThisClassConstant.java | `m_constClass`:160 | `resolveConstants`:52; `registerConstants`:122 |
| UnresolvedNameConstant | org/xvm/asm/constants/UnresolvedNameConstant.java | `m_constId`:295 | `resolve`:113; `registerConstants`:235 |
| VersionConstant | org/xvm/asm/constants/VersionConstant.java | `m_ver`:91 | `resolveConstants`:47 |
| VersionMatchesCondition | org/xvm/asm/constants/VersionMatchesCondition.java | `m_constStruct`:205, `m_constVer`:210 | `resolveConstants`:62,63; `registerConstants`:170,171 |
| VersionedCondition | org/xvm/asm/constants/VersionedCondition.java | `m_constVer`:200 | `resolveConstants`:65; `registerConstants`:173 |
| VirtualChildTypeConstant | org/xvm/asm/constants/VirtualChildTypeConstant.java | `m_constName`:372, `m_typeOriginParent`:384 | `resolveConstants`:117,118; `registerConstants`:335,336 |

#### org.xvm.asm.op (181)

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| Assert | org/xvm/asm/op/Assert.java | `m_nTest`:233, `m_nConstructor`:234, `m_argTest`:236, `m_idConstruct`:237 | `write`:78,81; `registerConstants`:162,163 |
| AssertM | org/xvm/asm/op/AssertM.java | `m_nMsgConstId`:94, `m_constMsg`:96 | `write`:57; `registerConstants`:77 |
| AssertV | org/xvm/asm/op/AssertV.java | `m_anValue`:395 | `write`:83 |
| Call_10 | org/xvm/asm/op/Call_10.java | `m_nArgValue`:164, `m_argValue`:166 | `write`:64; `registerConstants`:142 |
| Call_11 | org/xvm/asm/op/Call_11.java | `m_nArgValue`:175, `m_argValue`:177 | `write`:66; `registerConstants`:152 |
| Call_1N | org/xvm/asm/op/Call_1N.java | `m_nArgValue`:185, `m_argValue`:187 | `write`:66; `registerConstants`:162 |
| Call_1T | org/xvm/asm/op/Call_1T.java | `m_nArgValue`:188, `m_argValue`:190 | `write`:69; `registerConstants`:165 |
| Call_N0 | org/xvm/asm/op/Call_N0.java | `m_anArgValue`:168 | `write`:60 |
| Call_N1 | org/xvm/asm/op/Call_N1.java | `m_anArgValue`:183 | `write`:66 |
| Call_NN | org/xvm/asm/op/Call_NN.java | `m_anArgValue`:185 | `write`:63 |
| Call_NT | org/xvm/asm/op/Call_NT.java | `m_anArgValue`:186 | `write`:66 |
| Call_T0 | org/xvm/asm/op/Call_T0.java | `m_nArgTupleValue`:160, `m_argValue`:162 | `write`:62; `registerConstants`:152 |
| Call_T1 | org/xvm/asm/op/Call_T1.java | `m_nArgTupleValue`:171, `m_argValue`:173 | `write`:64; `registerConstants`:162 |
| Call_TN | org/xvm/asm/op/Call_TN.java | `m_nArgTupleValue`:176, `m_argValue`:178 | `write`:64; `registerConstants`:167 |
| Call_TT | org/xvm/asm/op/Call_TT.java | `m_nArgTupleValue`:171, `m_argValue`:173 | `write`:64; `registerConstants`:162 |
| CatchStart | org/xvm/asm/op/CatchStart.java | `m_nNameId`:177, `m_constName`:179 | `preWrite`:73; `setNameId`:90; `registerConstants`:130 |
| Construct_1 | org/xvm/asm/op/Construct_1.java | `m_nArgValue`:140, `m_argValue`:142 | `write`:62; `registerConstants`:118 |
| Construct_N | org/xvm/asm/op/Construct_N.java | `m_anArgValue`:134 | `write`:59 |
| Construct_T | org/xvm/asm/op/Construct_T.java | `m_nArgTupleValue`:119, `m_argValue`:121 | `write`:60; `registerConstants`:111 |
| FBind | org/xvm/asm/op/FBind.java | `m_anParamValue`:472 | `write`:104 |
| GP_DivRem | org/xvm/asm/op/GP_DivRem.java | `m_nTarget`:257, `m_nArgValue`:258, `m_anRetValue`:259, `m_argTarget`:261, `m_argValue`:262 | `write`:78,79,80; `registerConstants`:130,131 |
| GuardStart | org/xvm/asm/op/GuardStart.java | `m_anTypeId`:207, `m_anNameId`:208 | `write`:95,96 |
| I_Set | org/xvm/asm/op/I_Set.java | `m_nValue`:180, `m_argValue`:182 | `write`:78; `registerConstants`:138 |
| Invoke_10 | org/xvm/asm/op/Invoke_10.java | `m_nArgValue`:124, `m_argValue`:126 | `write`:61; `registerConstants`:102 |
| Invoke_11 | org/xvm/asm/op/Invoke_11.java | `m_nArgValue`:131, `m_argValue`:133 | `write`:64; `registerConstants`:109 |
| Invoke_1N | org/xvm/asm/op/Invoke_1N.java | `m_nArgValue`:153, `m_argValue`:155 | `write`:81; `registerConstants`:131 |
| Invoke_1T | org/xvm/asm/op/Invoke_1T.java | `m_nArgValue`:153, `m_argValue`:155 | `write`:81; `registerConstants`:131 |
| Invoke_N0 | org/xvm/asm/op/Invoke_N0.java | `m_anArgValue`:128 | `write`:60 |
| Invoke_N1 | org/xvm/asm/op/Invoke_N1.java | `m_anArgValue`:136 | `write`:64 |
| Invoke_NN | org/xvm/asm/op/Invoke_NN.java | `m_anArgValue`:145 | `write`:68 |
| Invoke_NT | org/xvm/asm/op/Invoke_NT.java | `m_anArgValue`:143 | `write`:66 |
| Invoke_T0 | org/xvm/asm/op/Invoke_T0.java | `m_nArgTupleValue`:105, `m_argValue`:107 | `write`:59; `registerConstants`:102 |
| Invoke_T1 | org/xvm/asm/op/Invoke_T1.java | `m_nArgTupleValue`:112, `m_argValue`:114 | `write`:62; `registerConstants`:109 |
| Invoke_TN | org/xvm/asm/op/Invoke_TN.java | `m_nArgTupleValue`:117, `m_argValue`:119 | `write`:62; `registerConstants`:114 |
| Invoke_TT | org/xvm/asm/op/Invoke_TT.java | `m_nArgTupleValue`:112, `m_argValue`:114 | `write`:62; `registerConstants`:109 |
| JumpInt | org/xvm/asm/op/JumpInt.java | `m_nArg`:244, `m_argVal`:248 | `write`:69; `registerConstants`:168 |
| JumpVal | org/xvm/asm/op/JumpVal.java | `m_nArgCond`:752, `m_argCond`:753 | `write`:91; `registerConstants`:370 |
| JumpVal_N | org/xvm/asm/op/JumpVal_N.java | `m_anArgCond`:547 | `write`:96 |
| L_Get | org/xvm/asm/op/L_Get.java | `m_nRetValue`:122, `m_argReturn`:124 | `write`:61; `registerConstants`:99 |
| L_Set | org/xvm/asm/op/L_Set.java | `m_nValue`:112, `m_argValue`:114 | `write`:60; `registerConstants`:94 |
| MoveCast | org/xvm/asm/op/MoveCast.java | `m_nToType`:170, `m_typeTo`:172 | `write`:67; `registerConstants`:110 |
| MoveThis | org/xvm/asm/op/MoveThis.java | `m_nToValue`:216, `m_argTo`:219 | `write`:100; `registerConstants`:128 |
| NewCG_0 | org/xvm/asm/op/NewCG_0.java | `m_nParentValue`:131, `m_nTypeValue`:132, `m_argParent`:134, `m_argType`:135 | `write`:68,69; `registerConstants`:113,114 |
| NewCG_1 | org/xvm/asm/op/NewCG_1.java | `m_nParentValue`:150, `m_nTypeValue`:151, `m_nArgValue`:152, `m_argParent`:154, `m_argType`:155, `m_argValue`:156 | `write`:71,72,73; `registerConstants`:126,127,128 |
| NewCG_N | org/xvm/asm/op/NewCG_N.java | `m_nParentValue`:150, `m_nTypeValue`:151, `m_anArgValue`:152, `m_argParent`:154, `m_argType`:155 | `write`:72,73,74; `registerConstants`:126,127 |
| NewC_0 | org/xvm/asm/op/NewC_0.java | `m_nParentValue`:118, `m_argParent`:120 | `write`:64; `registerConstants`:101 |
| NewC_1 | org/xvm/asm/op/NewC_1.java | `m_nParentValue`:143, `m_nArgValue`:144, `m_argParent`:146, `m_argValue`:147 | `write`:68,69; `registerConstants`:120,121 |
| NewC_N | org/xvm/asm/op/NewC_N.java | `m_nParentValue`:142, `m_anArgValue`:143, `m_argParent`:145 | `write`:68,69; `registerConstants`:119 |
| NewG_0 | org/xvm/asm/op/NewG_0.java | `m_nTypeValue`:118, `m_argType`:120 | `write`:64; `registerConstants`:101 |
| NewG_1 | org/xvm/asm/op/NewG_1.java | `m_nTypeValue`:145, `m_nArgValue`:146, `m_argType`:148, `m_argValue`:149 | `write`:68,69; `registerConstants`:121,122 |
| NewG_N | org/xvm/asm/op/NewG_N.java | `m_nTypeValue`:146, `m_anArgValue`:147, `m_argType`:149 | `write`:69,70; `registerConstants`:122 |
| NewG_T | org/xvm/asm/op/NewG_T.java | `m_nTypeValue`:126, `m_nArgTupleValue`:127, `m_argType`:129, `m_argValue`:130 | `write`:66,67; `registerConstants`:116,117 |
| NewV_0 | org/xvm/asm/op/NewV_0.java | `m_nType`:143, `m_argType`:145 | `write`:68; `registerConstants`:119 |
| NewV_1 | org/xvm/asm/op/NewV_1.java | `m_nType`:158, `m_nArgValue`:159, `m_argType`:161, `m_argValue`:162 | `write`:71,72; `registerConstants`:132,133 |
| NewV_N | org/xvm/asm/op/NewV_N.java | `m_nType`:161, `m_anArgValue`:162, `m_argType`:164 | `write`:73,74; `registerConstants`:135 |
| New_1 | org/xvm/asm/op/New_1.java | `m_nArgValue`:140, `m_argValue`:142 | `write`:67; `registerConstants`:118 |
| New_N | org/xvm/asm/op/New_N.java | `m_anArgValue`:141 | `write`:71 |
| New_T | org/xvm/asm/op/New_T.java | `m_nArgTupleValue`:123, `m_argValue`:125 | `write`:66; `registerConstants`:115 |
| OpSwitch | org/xvm/asm/op/OpSwitch.java | `m_anConstCase`:453 | `write`:93 |
| P_Get | org/xvm/asm/op/P_Get.java | `m_nTarget`:156, `m_nRetValue`:157, `m_argTarget`:159, `m_argReturn`:160 | `write`:66,67; `registerConstants`:116,117 |
| P_Ref | org/xvm/asm/op/P_Ref.java | `m_nTarget`:148, `m_nRetValue`:149, `m_argTarget`:151, `m_argReturn`:152 | `write`:66,67; `registerConstants`:118,119 |
| P_Set | org/xvm/asm/op/P_Set.java | `m_nTarget`:123, `m_nValue`:124, `m_argTarget`:126, `m_argValue`:127 | `write`:64,65; `registerConstants`:103,104 |
| P_Var | org/xvm/asm/op/P_Var.java | `m_nTarget`:148, `m_nRetValue`:149, `m_argTarget`:151, `m_argReturn`:152 | `write`:66,67; `registerConstants`:118,119 |
| Return_1 | org/xvm/asm/op/Return_1.java | `m_nArg`:121, `m_arg`:122 | `write`:53; `registerConstants`:86 |
| Return_N | org/xvm/asm/op/Return_N.java | `m_anArg`:175 | `write`:58 |
| Return_T | org/xvm/asm/op/Return_T.java | `m_nArg`:157, `m_argT`:158 | `write`:69; `registerConstants`:103 |
| Throw | org/xvm/asm/op/Throw.java | `m_nArgValue`:112 | `write`:60 |
| Var_C | org/xvm/asm/op/Var_C.java | `m_nArgValue`:140, `m_argValue`:142 | `write`:72; `registerConstants`:112 |
| Var_CN | org/xvm/asm/op/Var_CN.java | `m_nNameId`:152, `m_nArgValue`:153, `m_constName`:155, `m_argValue`:156 | `write`:76,77; `registerConstants`:118,119 |
| Var_DN | org/xvm/asm/op/Var_DN.java | `m_nNameId`:105, `m_constName`:107 | `write`:63; `registerConstants`:86 |
| Var_I | org/xvm/asm/op/Var_I.java | `m_nValueId`:117, `m_argValue`:119 | `write`:62; `registerConstants`:97 |
| Var_IN | org/xvm/asm/op/Var_IN.java | `m_nNameId`:125, `m_nValueId`:126, `m_constName`:128, `m_argValue`:129 | `write`:68,69; `registerConstants`:105,106 |
| Var_M | org/xvm/asm/op/Var_M.java | `m_anArgKey`:124, `m_anArgVal`:125 | `write`:80,81 |
| Var_MN | org/xvm/asm/op/Var_MN.java | `m_nNameId`:139, `m_anArgKey`:140, `m_anArgVal`:141, `m_constName`:143 | `write`:85,86,87; `registerConstants`:129 |
| Var_N | org/xvm/asm/op/Var_N.java | `m_nNameId`:100, `m_constName`:102 | `write`:60; `registerConstants`:82 |
| Var_S | org/xvm/asm/op/Var_S.java | `m_anArgValue`:137 | `write`:68 |
| Var_SN | org/xvm/asm/op/Var_SN.java | `m_nNameId`:154, `m_anArgValue`:155, `m_constName`:157 | `write`:75,76; `registerConstants`:136 |
| Var_T | org/xvm/asm/op/Var_T.java | `m_anArgValue`:116 | `write`:64 |
| Var_TN | org/xvm/asm/op/Var_TN.java | `m_nNameId`:131, `m_anArgValue`:132, `m_constName`:134 | `write`:72,73; `registerConstants`:114 |

### Mutable until `markReadOnly` (frozen)

37 `asm` fields are changed by builder methods that guard with `isReadOnly()`/`verifyMutable()`, and some are replaced by read-only copies in `markReadOnly()` (read in code: `XvmStructure.java:166`-240, `ClassStructure.java:3297`).

- **Technicality:** the object is built in place and frozen with a flag. The freeze is a convention, not a Java memory model edge.
- **Fix:** split builder and immutable form (the freeze returns a new object with `final` fields), or keep the in-place freeze and publish the frozen tree through a happens-before edge.
- **Cost:** high (the `Component` hierarchy and everything that calls `ensureMutable`).

| Class | Field | Location | Writes outside constructors |
|---|---|---|---|
| ClassStructure | `m_mapParams` | org/xvm/asm/ClassStructure.java:3782 | `addTypeParam`:566; `cloneBody`:1005; `markReadOnly`:3297; `disassemble`:3315; `registerConstants`:3333 |
| ClassStructure | `m_aAnnoClass` | org/xvm/asm/ClassStructure.java:3809 | `collectAnnotations`:208; `cloneBody`:1008; `addAnnotation`:1694; `markReadOnly`:3300 |
| ClassStructure | `m_aAnnoMixin` | org/xvm/asm/ClassStructure.java:3815 | `collectAnnotations`:210; `cloneBody`:1009; `addAnnotation`:1693; `markReadOnly`:3303 |
| Component$Contribution | `m_typeContrib` | org/xvm/asm/Component.java:3421 | `narrowType`:2996; `registerConstants`:3204 |
| Component$Contribution | `m_mapParams` | org/xvm/asm/Component.java:3437 | `markReadOnly`:3046; `registerConstants`:3222 |
| Component$Contribution | `m_constInjector` | org/xvm/asm/Component.java:3442 | `addInjector`:2914; `registerConstants`:3226 |
| Component$Contribution | `m_listInject` | org/xvm/asm/Component.java:3448 | `addInjector`:2915; `markReadOnly`:3043 |
| Component | `m_constId` | org/xvm/asm/Component.java:3619 | `replaceThisIdentityConstant`:264; `registerConstants`:2305 |
| Component | `m_cond` | org/xvm/asm/Component.java:3625 | `addAndCondition`:1295; `addOrCondition`:1309; `registerConstants`:2306; `setCondition`:2353 |
| Component | `m_nFlags` | org/xvm/asm/Component.java:3631 | `setAccess`:309; `setAbstract`:331; `setStatic`:353; `setSynthetic`:375; `setConditionalReturn`:397; `markAuxiliary`:418 |
| FileStructure | `m_file` | org/xvm/asm/FileStructure.java:1573 | `merge`:297 |
| FileStructure | `m_idModule` | org/xvm/asm/FileStructure.java:1610 | `merge`:250,296; `replaceChildIdentityConstant`:1138; `disassemble`:1330 |
| FileStructure | `m_moduleById` | org/xvm/asm/FileStructure.java:1616 | `replaceChildIdentityConstant`:1135; `addChild`:1168; `removeChild`:1197; `replaceChild`:1222 |
| MethodStructure$Code | `m_listOps` | org/xvm/asm/MethodStructure.java:2658 | `cloneOnto`:2320; `ensureAppending`:2429; `markReadOnly`:2438 |
| MethodStructure$Code | `m_fTrailingPrefix` | org/xvm/asm/MethodStructure.java:2663 | `add`:2177; `cloneOnto`:2323 |
| MethodStructure$Code | `m_nPrevLine` | org/xvm/asm/MethodStructure.java:2678 | `add`:2165; `cloneOnto`:2325 |
| MethodStructure$Code | `m_nCurLine` | org/xvm/asm/MethodStructure.java:2683 | `updateLineNumber`:2149; `cloneOnto`:2326 |
| MethodStructure | `m_aAnnotations` | org/xvm/asm/MethodStructure.java:2983 | `reorderAnnotations`:260; `resolveAnnotations`:341,359; `addAnnotation`:1613,1618; `cloneBody`:1707; `markReadOnly`:1809; `disassemble`:1917; +1 more |
| MethodStructure | `m_aReturns` | org/xvm/asm/MethodStructure.java:2993 | `configureLambda`:435; `cloneBody`:1718; `markReadOnly`:1812; `disassemble`:1918 |
| MethodStructure | `m_aParams` | org/xvm/asm/MethodStructure.java:3008 | `configureLambda`:433; `cloneBody`:1729; `markReadOnly`:1815; `disassemble`:1921 |
| MethodStructure | `m_aconstSuper` | org/xvm/asm/MethodStructure.java:3021 | `setShorthandInitialization`:1517; `cloneBody`:1743; `markReadOnly`:1821; `disassemble`:1895; `registerConstants`:1950 |
| MethodStructure | `m_aconstLocal` | org/xvm/asm/MethodStructure.java:3031 | `createCode`:642; `forceAssembly`:1056; `setAbstract`:1090; `cloneBody`:1740; `markReadOnly`:1818; `disassemble`:1922; +2 more |
| MethodStructure | `m_fNative` | org/xvm/asm/MethodStructure.java:3057 | `createCode`:641; `resetRuntimeInfo`:1039; `setAbstract`:1093; `markNative`:1114 |
| MethodStructure | `m_fTransient` | org/xvm/asm/MethodStructure.java:3063 | `markNative`:1115; `markTransient`:1131 |
| ModuleStructure | `m_moduletype` | org/xvm/asm/ModuleStructure.java:1033 | `fingerprintDesired`:376; `fingerprintRequired`:388; `markEmbedded`:427; `markPrimary`:442; `disassemble`:813 |
| ModuleStructure | `m_abDigest` | org/xvm/asm/ModuleStructure.java:1067 | `getDigest`:201; `cloneBody`:745; `markModified`:771; `markReadOnly`:802 |
| ModuleStructure | `m_mapCondNames` | org/xvm/asm/ModuleStructure.java:1072 | `getConditionalNames`:567; `cloneBody`:746; `markReadOnly`:798,800 |
| ModuleStructure | `m_mapDependencies` | org/xvm/asm/ModuleStructure.java:1077 | `getDependencies`:158; `cloneBody`:747; `markReadOnly`:795 |
| Parameter | `m_aAnnotations` | org/xvm/asm/Parameter.java:515 | `addAnnotation`:136,141; `resolveAnnotations`:192,212; `cloneBody`:382; `markReadOnly`:393; `registerConstants`:423 |
| Parameter | `m_fHasDefault` | org/xvm/asm/Parameter.java:546 | `markDefaultValue`:295 |
| Parameter | `m_fImplicitDeref` | org/xvm/asm/Parameter.java:552 | `markImplicitDeref`:333; `cloneBody`:383 |
| PropertyStructure | `m_aPropAnno` | org/xvm/asm/PropertyStructure.java:819 | `buildAnnotationArrays`:328; `addAnnotation`:700; `cloneBody`:709; `markReadOnly`:718 |
| PropertyStructure | `m_aRefAnno` | org/xvm/asm/PropertyStructure.java:824 | `buildAnnotationArrays`:331; `addAnnotation`:701; `cloneBody`:710; `markReadOnly`:721 |
| VersionTree$Node | `value` | org/xvm/asm/VersionTree.java:1044 | `put in VersionTree`:312; `remove`:822 |
| XvmStructure | `m_fReadOnly` | org/xvm/asm/XvmStructure.java:645 | `markReadOnly`:224; `clone`:626 |
| UnresolvedNameConstant | `m_consumer` | org/xvm/asm/constants/UnresolvedNameConstant.java:305 | `addConsumer`:92,94 |
| UnresolvedTypeConstant | `m_fRecursive` | org/xvm/asm/constants/UnresolvedTypeConstant.java:572 | `markRecursive`:69 |

### `clone()`-based copies

- **Technicality:** `super.clone()` copies every field, and the copy then has to change some of them (sibling links, child maps, contribution lists, the handle's class). A cloned class can keep `final` fields only where the copy keeps the original value.
- **Fix:** a copy constructor (`Component(Component that)`, `ObjectHandle(ObjectHandle that, TypeComposition clazz)`) that assigns the new values, replacing `clone()`.
- **Cost:** medium for `Component`/`MethodStructure`/`ModuleStructure`/`Parameter` (`cloneBody` overrides in one hierarchy); high for `ObjectHandle` (every cloneable handle class).

| Class | Field | Location | Writes outside constructors |
|---|---|---|---|
| Component | `m_sibling` | org/xvm/asm/Component.java:3612 | `setNextSibling`:703; `cloneBody`:2065 |
| Component | `m_listContribs` | org/xvm/asm/Component.java:3636 | `addContribution`:602; `cloneBody`:2062; `disassemble`:2289 |
| Component | `m_childByName` | org/xvm/asm/Component.java:3659 | `ensureChildByNameMap`:788; `adoptChildren`:957; `cloneBody`:2066 |
| MethodStructure | `m_code` | org/xvm/asm/MethodStructure.java:3041 | `ensureCode`:624; `createCode`:644; `resetRuntimeInfo`:1036; `setAbstract`:1092; `cloneBody`:1734,1736 |
| MethodStructure | `m_structFinally` | org/xvm/asm/MethodStructure.java:3092 | `getConstructFinally`:1263; `setConstructFinally`:1276; `cloneBody`:1747 |
| ModuleStructure | `m_vtreeImportAllowVers` | org/xvm/asm/ModuleStructure.java:1044 | `cloneBody`:734; `disassemble`:832 |
| ModuleStructure | `m_listImportPreferVers` | org/xvm/asm/ModuleStructure.java:1050 | `cloneBody`:739; `disassemble`:833 |
| ModuleStructure | `m_moduleActual` | org/xvm/asm/ModuleStructure.java:1056 | `setFingerprintOrigin`:457; `cloneBody`:743 |
| ModuleStructure | `m_pkgImport` | org/xvm/asm/ModuleStructure.java:1062 | `cloneBody`:744; `getImportedPackage`:762 |
| ModuleStructure | `m_vtree` | org/xvm/asm/ModuleStructure.java:1092 | `getVersions`:505; `cloneBody`:748 |
| MultiMethodStructure | `m_methodByConstant` | org/xvm/asm/MultiMethodStructure.java:402 | `adoptChildren`:148; `cloneBody`:237; `ensureMethodByConstantMap`:388 |
| Parameter | `m_regDeref` | org/xvm/asm/Parameter.java:557 | `deref`:363; `cloneBody`:384 |
| LambdaExpression | `m_lambda` | org/xvm/compiler/ast/LambdaExpression.java:1472 | `validate`:511; `discard`:856; `copyTree`:864 |
| NamedTypeExpression | `m_exprDynamic` | org/xvm/compiler/ast/NamedTypeExpression.java:1111 | `resolveNames`:560; `validate`:580; `copyTree`:1006 |
| NewExpression | `body` | org/xvm/compiler/ast/NewExpression.java:1611 | `copyTree`:164 |
| TypeCompositionStatement | `body` | org/xvm/compiler/ast/TypeCompositionStatement.java:3133 | `<init> in IncompleteTypeCompositionStatement`:69,85; `copyTree in IncompleteTypeCompositionStatement`:109; `ensureBody`:372; `registerStructures`:1373 |
| ObjectHandle | `m_clazz` | org/xvm/runtime/ObjectHandle.java:46 | `cloneAs`:64 |

### Lazy caches

267 fields are written only by a lazy initializer, and 190 more also by an invalidation that sets them back to `null` (`optimize()`, `invalidateCache()`, `resetCachedInfo()`, `setContaining()`, ...).

- **Technicality:** `if (m_x == null) m_x = compute();` cannot be `final`. Without `volatile`, a thread can see the reference before the object it points to is fully visible, and two threads can both compute.
- **Fix:** `org.xvm.util.Lazy` (`javatools_utils/src/main/java/org/xvm/util/Lazy.java`) has what is needed: `private final Lazy.Bound<Owner, T> x = Lazy.ofBound(Owner::computeX)` for owner-derived values (no `this` capture in the constructor), `Lazy.Bound.reset()` or `Lazy.ofResettable` for the invalidated ones. Two uses exist today: `ConstantPool.f_jitPrimitives` (`org/xvm/asm/ConstantPool.java:3823`) and `AbstractConverterMap` (`org/xvm/util/converter/AbstractConverterMap.java:30`-42).
- **Cost:** low per field, but each holder is an extra object (about 24 bytes) per field per instance. That is fine on objects that exist a few times per pool or container (`ConstantPool`, `NativeContainer`, `ClassTemplate`, `Container`) and noticeable on objects that exist by the hundred thousand (`Constant` subclasses, `PropertyInfo`, `MethodBody`, runtime handles). For those, a `volatile` field with a benign race, or one holder for all of an object's caches, is cheaper.
- **Caution for `clone()`d owners:** a `Lazy.Bound` stays bound to its first owner, and `clone()` copies the reference, so the copy would throw. Holders in cloned classes need the copy constructor first.

#### Lazy, never reset (267)

**org.xvm.api (1)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| InterpreterConnector | org/xvm/api/InterpreterConnector.java | `m_fStarted`:216 | `start`:102 |

**org.xvm.asm (36)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| Annotation | org/xvm/asm/Annotation.java | `m_constClass`:378 | `resolveConstants`:92; `getAnnotationClass`:132; `registerConstants`:300 |
| ClassStructure | org/xvm/asm/ClassStructure.java | `m_safety`:3804 | `getConcurrencySafety`:948 |
| Constant | org/xvm/asm/Constant.java | `m_iHash`:975 | `computeHashCodeInternal`:585 |
| ConstantPool | org/xvm/asm/ConstantPool.java | `m_clzSliceable`:3870, `m_typeFPNumber`:3940, `m_typeBinFPNumber`:3941, `m_typeDecFPNumber`:3942, `m_typeIntNumber`:3944, `m_typeUIntNumber`:3951, `m_typeInjector`:4000, `m_typeRounding`:4001, `m_typeAppender`:4002, `m_valTiesToEven`:4010, `m_valTiesToAway`:4011, `m_valTowardPositive`:4012, `m_valTowardZero`:4013, `m_valTowardNegative`:4014, `m_sigAppendTo`:4020, `m_sigEstStrLen`:4021 | `clzSliceable`:2206; `typeFPNumber`:2272; `typeBinFPNumber`:2273; `typeDecFPNumber`:2274; `typeIntNumber`:2276; `typeUIntNumber`:2284; +10 more methods |
| DirRepository$ModuleInfo | org/xvm/asm/DirRepository.java | `module`:427 | `ensureModule`:410 |
| FileRepository | org/xvm/asm/FileRepository.java | `reportedFileInfoError`:361, `reportedFileStructureError`:362 | `readFileStructure`:311; `readFileInfo`:296 |
| FileStructure | org/xvm/asm/FileStructure.java | `m_fLinked`:1600, `m_ctx`:1626 | `markLinked`:948; `ensureContext`:973 |
| MethodStructure$Code | org/xvm/asm/MethodStructure.java | `m_hole`:2673 | `blackhole`:2346 |
| MethodStructure$Source | org/xvm/asm/MethodStructure.java | `m_sSrc`:2964, `m_aconstSrc`:2969 | `inflate`:2892; `normalize`:2863; `disassemble`:2924; `registerConstants`:2932 |
| MethodStructure | org/xvm/asm/MethodStructure.java | `m_safety`:3078 | `getConcurrencySafety`:1656 |
| Op$ConstantRegistry | org/xvm/asm/Op.java | `m_mapConstants`:778, `m_aconst`:783 | `register`:681; `ensureOptimized`:731,739,745 |
| OpCondJump | org/xvm/asm/OpCondJump.java | `m_typeCommon`:689 | `write`:111; `calculateCommonType`:194; `registerConstants`:248 |
| OpInvocable | org/xvm/asm/OpInvocable.java | `m_constMethod`:624 | `registerConstants`:135; `getCallChain`:160 |
| OpTest | org/xvm/asm/OpTest.java | `m_typeCommon`:474 | `write`:106; `calculateCommonType`:185; `registerConstants`:215 |
| PropertyStructure | org/xvm/asm/PropertyStructure.java | `m_fNative`:814 | `markNative`:451 |
| VersionTree$Node | org/xvm/asm/VersionTree.java | `version`:1032 | `ensureNode in VersionTree`:491; `getVersion`:534 |
| VersionTree | org/xvm/asm/VersionTree.java | `readOnly`:1069 | `ensureReadOnly`:282 |

**org.xvm.asm.constants (51)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| AnnotatedTypeConstant | org/xvm/asm/constants/AnnotatedTypeConstant.java | `m_typeAnno`:801 | `getAnnotationType`:179; `registerConstants`:707 |
| ChildInfo | org/xvm/asm/constants/ChildInfo.java | `m_infoType`:212 | `forType`:64 |
| FSNodeConstant | org/xvm/asm/constants/FSNodeConstant.java | `m_constPath`:386 | `getPathConstant`:163 |
| IdentityConstant | org/xvm/asm/constants/IdentityConstant.java | `m_canonicalNid`:805, `m_fNested`:810 | `getCanonicalNestedIdentity`:285; `isNested`:195 |
| LiteralConstant | org/xvm/asm/constants/LiteralConstant.java | `m_oVal`:1663 | `getPackedInteger`:373; `getBigDecimal`:400; `getFloat`:439; `getDouble`:459 |
| MapConstant$ROMap | org/xvm/asm/constants/MapConstant.java | `setKeys`:476, `setEntries`:477 | `keySet`:392; `entrySet`:431 |
| MethodBody | org/xvm/asm/constants/MethodBody.java | `m_infoMethod`:916, `m_structMethod`:921 | `forMethod`:131; `getMethodStructure`:219; `setMethodStructure`:231 |
| MethodConstant | org/xvm/asm/constants/MethodConstant.java | `m_type`:724, `m_sJitName`:729 | `getType`:481; `registerConstants`:628; `assemble`:639; `ensureJitMethodName`:332 |
| MethodInfo | org/xvm/asm/constants/MethodInfo.java | `m_infoType`:1744, `m_aBodyResolved`:1756 | `forType`:95; `ensureOptimizedMethodChain`:1339 |
| ParameterizedTypeConstant | org/xvm/asm/constants/ParameterizedTypeConstant.java | `m_typeJitCallable`:1149 | `getJitCCType`:818,909 |
| PropertyBody | org/xvm/asm/constants/PropertyBody.java | `m_infoProperty`:584 | `forProperty`:167 |
| PropertyClassTypeConstant | org/xvm/asm/constants/PropertyClassTypeConstant.java | `m_info`:495 | `getPropertyInfo`:124 |
| PropertyConstant | org/xvm/asm/constants/PropertyConstant.java | `m_info`:466 | `getPropertyInfo`:220 |
| PropertyInfo | org/xvm/asm/constants/PropertyInfo.java | `m_infoType`:1556, `m_chainGet`:1584, `m_chainSet`:1589, `m_annotations`:1594, `m_FInjected`:1599, `m_FImplicitlyAssigned`:1604, `m_typeBaseRef`:1609, `m_idGetter`:1614, `m_idSetter`:1619, `m_jmdGetter`:1624, `m_jmdSetter`:1629 | `forType`:119; `ensureOptimizedGetChain`:1159; `ensureOptimizedSetChain`:1198; `getRefAnnotations`:1002; `isInjected`:1235; `isImplicitlyAssigned`:1108,1113,1119; +5 more methods |
| SignatureConstant | org/xvm/asm/constants/SignatureConstant.java | `m_sJitName`:964 | `ensureJitMethodName`:734 |
| TerminalTypeConstant | org/xvm/asm/constants/TerminalTypeConstant.java | `m_constId`:2142 | `resolveConstants`:91; `ensureResolvedConstant`:515; `registerConstants`:2070 |
| TypeCollector | org/xvm/asm/constants/TypeCollector.java | `m_listMulti`:469 | `ensureMulti`:140 |
| TypeConstant | org/xvm/asm/constants/TypeConstant.java | `m_fValidated`:8595, `m_mapConsumes`:8631, `m_mapProduces`:8636, `m_sJitName`:8646 | `validate`:8323; `ensureConsumesMap`:8388; `ensureProducesMap`:8396; `ensureJitName`:7408 |
| TypeInfoReal | org/xvm/asm/constants/TypeInfoReal.java | `m_fImplicitAbstract`:2370, `m_mapPropertiesByName`:2445, `m_mapMethodsBySignature`:2480, `m_fCacheReady`:2483, `m_fChildrenChecked`:2484, `m_setAuto`:2489, `m_setOps`:2490, `m_typeAuto`:2491, `m_methodAuto`:2492, `m_mapMethodsByName`:2494, `m_mapNestedProperties`:2495 | `ensureCaches`:2253,2255; `ensurePropertiesByName`:1056; `ensureMethodsBySignature`:1330; `isNewable`:840; `getAutoMethodInfos`:2029; `getOpMethodInfos`:1758; +3 more methods |
| TypeParameterConstant | org/xvm/asm/constants/TypeParameterConstant.java | `m_typeConstraint`:246 | `getConstraintType`:100,108,126; `registerConstants`:209 |
| TypedefConstant | org/xvm/asm/constants/TypedefConstant.java | `m_fInitialized`:146 | `getReferredToType`:88 |
| UnresolvedTypeConstant | org/xvm/asm/constants/UnresolvedTypeConstant.java | `m_type`:567 | `getResolvedType`:80; `resolve`:98 |
| VirtualChildTypeConstant | org/xvm/asm/constants/VirtualChildTypeConstant.java | `m_clzChild`:389 | `getChildStructure`:143,158; `registerConstants`:339 |

**org.xvm.asm.op (11)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| GuardAll | org/xvm/asm/op/GuardAll.java | `m_guard`:193 | `process`:70 |
| GuardStart | org/xvm/asm/op/GuardStart.java | `m_aOpCatch`:211, `m_guard`:215 | `resolveCode`:71; `resolveAddresses`:114; `process`:146 |
| JumpCond | org/xvm/asm/op/JumpCond.java | `m_cond`:56 | `processUnaryOp`:49 |
| JumpInt | org/xvm/asm/op/JumpInt.java | `m_aOpCase`:249 | `resolveAddresses`:89 |
| JumpNCond | org/xvm/asm/op/JumpNCond.java | `m_cond`:56 | `processUnaryOp`:49 |
| JumpNFirst | org/xvm/asm/op/JumpNFirst.java | `m_fVisited`:67 | `process`:45 |
| JumpNSample | org/xvm/asm/op/JumpNSample.java | `m_nEvery`:67 | `completeUnaryOp`:59 |
| JumpVal_N | org/xvm/asm/op/JumpVal_N.java | `m_alistRangeSmall`:580 | `ensureRangeList`:498 |
| Label | org/xvm/asm/op/Label.java | `m_mapRestore`:183 | `addRestore`:55 |
| OpSwitch | org/xvm/asm/op/OpSwitch.java | `m_aOpCase`:458 | `resolveAddresses`:116 |

**org.xvm.compiler (2)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| Parser | org/xvm/compiler/Parser.java | `m_fDone`:6913 | `parseSource`:162 |
| Source | org/xvm/compiler/Source.java | `m_sFile`:594 | `getFileName`:132 |

**org.xvm.compiler.ast (with bindings, partial) (58)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| AnnotatedTypeExpression | org/xvm/compiler/ast/AnnotatedTypeExpression.java | `m_typeUnresolved`:400 | `calculateType`:338,359 |
| AnonInnerClass | org/xvm/compiler/ast/AnonInnerClass.java | `m_sName`:380, `m_listAnnos`:385, `m_listCompositions`:390 | `addContribution`:270; `setSuper`:314; `ensureAnnotations`:285; `ensureCompositions`:332 |
| ArrayAccessExpression | org/xvm/compiler/ast/ArrayAccessExpression.java | `expr`:1273, `m_fSlice`:1279 | `validate`:454,469 |
| AssertStatement | org/xvm/compiler/ast/AssertStatement.java | `conds`:612 | `demorgan`:504 |
| AssignmentStatement | org/xvm/compiler/ast/AssignmentStatement.java | `m_fCondFalseLabelTaken`:983 | `getConditionFalseLabel`:238 |
| CaseManager | org/xvm/compiler/ast/CaseManager.java | `m_labelDefault`:1268, `m_labelConstant`:1273, `m_mapWild`:1298 | `validateCase`:403,467,503; `validateEnd`:703,707; `collides`:821 |
| CatchStatement | org/xvm/compiler/ast/CatchStatement.java | `m_opCatch`:150 | `ensureCatchStart`:61 |
| CmpChainExpression | org/xvm/compiler/ast/CmpChainExpression.java | `m_constEq`:501 | `validate`:212 |
| ConditionalStatement | org/xvm/compiler/ast/ConditionalStatement.java | `m_nLabel`:79 | `getLabelId`:67 |
| Context$CaptureContext | org/xvm/compiler/ast/Context.java | `m_mapCapture`:2554, `m_mapRegisters`:2559, `m_mapFormalInfo`:2565 | `ensureCaptureMap`:2529; `ensureRegisterMap`:2487; `ensureFormalMap`:2544 |
| Context | org/xvm/compiler/ast/Context.java | `m_mapAssigned`:2654, `m_regThis`:2659 | `ensureDefiniteAssignments`:1107; `getThisRegister`:187 |
| ElseExpression | org/xvm/compiler/ast/ElseExpression.java | `m_labelElse`:223 | `ensureShortCircuitLabel`:184 |
| ElvisExpression | org/xvm/compiler/ast/ElvisExpression.java | `m_labelEnd`:325 | `getEndLabel`:311 |
| Expression | org/xvm/compiler/ast/Expression.java | `m_oType`:3094 | `finishValidation`:543,604; `finishValidations`:858 |
| ForEachStatement | org/xvm/compiler/ast/ForEachStatement.java | `m_labelContinue`:1300, `m_regLast`:1304, `m_regEntry`:1305, `m_regKeyType`:1306, `m_regValType`:1307, `m_aidConvKey`:1309 | `getContinueLabel`:102; `getLabelVar`:241,243,244...; `validateLoop`:478 |
| ForStatement | org/xvm/compiler/ast/ForStatement.java | `m_labelContinue`:538, `m_listShorts`:543 | `getContinueLabel`:79; `ensureShortCircuitLabel`:120; `validateLoop`:210,327 |
| IfStatement | org/xvm/compiler/ast/IfStatement.java | `m_labelElse`:316, `m_listShorts`:321 | `getElseLabel`:51; `ensureShortCircuitLabel`:71 |
| ImportStatement | org/xvm/compiler/ast/ImportStatement.java | `m_resolver`:280, `m_fImportRegistered`:281 | `getNameResolver`:169; `resolveNames`:191 |
| LoopStatement | org/xvm/compiler/ast/LoopStatement.java | `firstRegister`:30, `countRegister`:31, `continues`:36 | `getLabelVar in ForEachStatement`:240,242; `getLabelVar`:93,95; `ensureContinueLabel in ForEachStatement`:79; `validateLoop in ForStatement`:312; `ensureContinueLabel`:54; `validateLoop in WhileStatement`:183 |
| MethodDeclarationStatement | org/xvm/compiler/ast/MethodDeclarationStatement.java | `m_stmtComplement`:1255 | `getConstructorFinally`:201; `bindConstructor`:212 |
| MultipleLValueStatement | org/xvm/compiler/ast/MultipleLValueStatement.java | `aGroundLabels`:514, `expr`:519 | `ensureShortCircuitLabel`:160; `getLValueExpression`:82 |
| NamedTypeExpression | org/xvm/compiler/ast/NamedTypeExpression.java | `m_resolver`:1107, `m_constId`:1108, `m_constUnresolved`:1114 | `getNameResolver`:363; `getIdentityConstant`:195,196,198; `setTypeConstant`:418; `resolveNames`:513,518; `validate`:590,610 |
| SequentialAssignExpression | org/xvm/compiler/ast/SequentialAssignExpression.java | `m_LValTarget`:149 | `ensureTarget`:103 |
| Statement | org/xvm/compiler/ast/Statement.java | `m_labelEnd`:301, `m_listBreaks`:311 | `getEndLabel`:47; `addBreak`:137 |
| StatementBlock$RootContext | org/xvm/compiler/ast/StatementBlock.java | `m_ctxValidating`:1488, `m_mapCaptureContexts`:1495, `m_mapCaptureVars`:1501 | `validatingContext`:1396; `emittingContext`:1415,1444; `ensureCaptureContexts`:1465; `ensureCaptureVars`:1478 |
| StatementBlock | org/xvm/compiler/ast/StatementBlock.java | `stmts`:1685, `imports`:1691, `importsWild`:1692 | `validateImpl`:413; `validateImpl in SwitchStatement`:195; `registerImport`:168,173 |
| SwitchStatement | org/xvm/compiler/ast/SwitchStatement.java | `m_labelContinue`:619 | `ensureContinueLabel`:61; `validateImpl`:185 |
| TryStatement | org/xvm/compiler/ast/TryStatement.java | `m_regFinallyException`:539 | `getLabelVar`:104 |
| TypeCompositionStatement | org/xvm/compiler/ast/TypeCompositionStatement.java | `compositions`:3132, `enclosed`:3135 | `registerStructures`:847; `addEnclosed`:362 |
| VariableDeclarationStatement | org/xvm/compiler/ast/VariableDeclarationStatement.java | `m_exprName`:309 | `getLValueExpression`:117 |
| WhileStatement | org/xvm/compiler/ast/WhileStatement.java | `m_labelContinue`:612 | `getContinueLabel`:76 |

**org.xvm.javajit (with builders, registers) (2)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| Scope | org/xvm/javajit/Scope.java | `startLocal`:67, `synthetics`:102 | `allocateJavaSlot`:225; `exit`:260; `allocateSynthetic`:200 |

**org.xvm.runtime (with debug, gc) (33)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| CallChain | org/xvm/runtime/CallChain.java | `m_FAtomic`:770 | `isAtomic`:71,74 |
| ClassComposition | org/xvm/runtime/ClassComposition.java | `m_cRegularFields`:905, `m_fHasOuter`:910, `m_ashFieldNames`:951, `m_methodInit`:954 | `ensureFieldLayoutImpl`:586,677; `getFieldNameArray`:437; `ensureAutoInitializer`:259 |
| ClassTemplate$Construct | org/xvm/runtime/ClassTemplate.java | `listFinalizable`:2225 | `prepareFinalizer`:2411 |
| ClassTemplate | org/xvm/runtime/ClassTemplate.java | `m_templateSuper`:2459, `m_clazzCanonical`:2470 | `getSuper`:181,183; `getCanonicalClass`:199 |
| Container | org/xvm/runtime/Container.java | `m_contextMain`:784, `m_futureTermination`:811 | `ensureServiceContext`:101; `terminateServices`:155 |
| DebugConsole$BreakPoint | org/xvm/runtime/DebugConsole.java | `lambda`:2218 | `matches`:2138 |
| DebugConsole$DebugStash | org/xvm/runtime/DebugConsole.java | `m_mapExpand`:2262, `m_listWatches`:2263 | `ensureExpandMap`:2240; `ensureWatchList`:2257 |
| Fiber | org/xvm/runtime/Fiber.java | `m_mapTokens`:643, `m_listUnhandledEx`:663, `m_mapPendingUncaptured`:680 | `ensureTokens`:119,122; `processUnhandledException`:409; `registerUncapturedRequest`:380 |
| FiberQueue | org/xvm/runtime/FiberQueue.java | `m_aFrame`:13 | `ensureCapacity`:291 |
| Frame | org/xvm/runtime/Frame.java | `m_debug`:103 | `ensureDebugStash`:2055 |
| Frame$StackFrame | org/xvm/runtime/Frame.java | `boundary`:2089 | `getStackFrameArray in Frame`:2195 |
| Frame$VarInfo | org/xvm/runtime/Frame.java | `m_type`:2473, `m_nTypeId`:2474, `m_sVarName`:2477 | `getType`:2559,2560; `getName`:2516; `setName`:2522 |
| NativeContainer | org/xvm/runtime/NativeContainer.java | `m_hOSStorage`:883, `m_hFileStore`:884, `m_hRootDir`:885, `m_hHomeDir`:886, `m_hCurDir`:887, `m_hTmpDir`:888, `m_hProperties`:889 | `ensureOSStorage`:447,456; `ensureFileStore`:470; `ensureRootDir`:484; `ensureHomeDir`:498; `ensureCurDir`:512; `ensureTmpDir`:526; +1 more methods |
| PropertyComposition | org/xvm/runtime/PropertyComposition.java | `m_clzStruct`:372 | `ensureAccess`:134 |
| ServiceContext | org/xvm/runtime/ServiceContext.java | `m_futureShutdown`:2224, `m_mapTransient`:2340, `m_mapCallbacks`:2345 | `requestShutdown`:982; `ensureTransientMap`:281; `ensureCallbackMap`:297 |

**org.xvm.runtime.template (all subpackages) (51)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| xRTAlgorithms | org/xvm/runtime/template/_native/crypto/xRTAlgorithms.java | `m_hAlgorithms`:387 | `ensureAlgorithms`:78 |
| xRTCertificateManager | org/xvm/runtime/template/_native/crypto/xRTCertificateManager.java | `m_typeCanonical`:609 | `getCanonicalType`:103 |
| xRTKeyStore | org/xvm/runtime/template/_native/crypto/xRTKeyStore.java | `m_typeCanonical`:569 | `getCanonicalType`:114 |
| xTerminalConsole | org/xvm/runtime/template/_native/io/xTerminalConsole.java | `m_hConsole`:258 | `ensureConsole`:76 |
| xContainerLinker | org/xvm/runtime/template/_native/mgmt/xContainerLinker.java | `m_hLinker`:320 | `ensureLinker`:85 |
| xCoreRepository | org/xvm/runtime/template/_native/mgmt/xCoreRepository.java | `m_hRepository`:149 | `ensureModuleRepository`:110 |
| xRTNameService | org/xvm/runtime/template/_native/net/xRTNameService.java | `m_typeCanonical`:279 | `getCanonicalType`:77 |
| xRTNetwork | org/xvm/runtime/template/_native/net/xRTNetwork.java | `m_typeCanonical`:228 | `getCanonicalType`:68 |
| xRTNetworkInterface | org/xvm/runtime/template/_native/net/xRTNetworkInterface.java | `m_typeCanonical`:116 | `getCanonicalType`:54 |
| xRTSocket | org/xvm/runtime/template/_native/net/xRTSocket.java | `m_typeCanonical`:441 | `getCanonicalType`:81 |
| xRTRandom | org/xvm/runtime/template/_native/numbers/xRTRandom.java | `m_hRandom`:298 | `ensureDefaultRandom`:207 |
| xRTComponentTemplate | org/xvm/runtime/template/_native/reflect/xRTComponentTemplate.java | `m_typeComponentArray`:397, `m_templateMultiMethod`:398 | `ensureComponentArrayType`:242; `getMultiMethodTemplateComposition`:254 |
| xRTFunction | org/xvm/runtime/template/_native/reflect/xRTFunction.java | `m_typeListMap`:1589 | `ensureListMapType`:1555 |
| xRTMethod | org/xvm/runtime/template/_native/reflect/xRTMethod.java | `m_constEmptyArray`:379 | `ensureEmptyArrayConstant`:345 |
| xRTMethodTemplate | org/xvm/runtime/template/_native/reflect/xRTMethodTemplate.java | `m_clzMethodTemplate`:206 | `ensureMethodTemplateComposition`:185 |
| xRTModuleTemplate | org/xvm/runtime/template/_native/reflect/xRTModuleTemplate.java | `m_typeListMap`:176 | `ensureListMapType`:143 |
| xRTPropertyTemplate | org/xvm/runtime/template/_native/reflect/xRTPropertyTemplate.java | `m_clzPropertyTemplate`:219, `m_clzPropertyArray`:220 | `ensurePropertyTemplateComposition`:183; `ensureArrayComposition`:198 |
| xRTSignature | org/xvm/runtime/template/_native/reflect/xRTSignature.java | `m_typeReturn`:307, `m_typeParam`:308, `m_typeRTReturn`:309, `m_typeRTParam`:310, `m_templateRTReturn`:312, `m_templateRTParam`:313, `m_clzReturnArray`:315, `m_clzParamArray`:316 | `ensureReturnType`:189; `ensureParamType`:212; `ensureRTReturnType`:200; `ensureRTParamType`:223; `ensureRTReturnTemplate`:235; `ensureRTParamTemplate`:246; +2 more methods |
| xRTType | org/xvm/runtime/template/_native/reflect/xRTType.java | `m_typeListMap`:1923, `m_clzRegister`:1924, `m_constructRegister`:1925 | `ensureListMapType`:1761; `makeRegisterHandle`:1673,1674 |
| xLocalClock | org/xvm/runtime/template/_native/temporal/xLocalClock.java | `m_hLocalClock`:324, `m_hUTCClock`:329 | `ensureLocalClock`:150; `ensureUTCClock`:167 |
| xRTConnector | org/xvm/runtime/template/_native/web/xRTConnector.java | `m_typeCanonical`:387, `m_hDefaultNames`:392, `m_hDefaultValues`:393 | `getCanonicalType`:99; `invokeGetDefaultHeaders`:154,155 |
| xRTServer | org/xvm/runtime/template/_native/web/xRTServer.java | `m_typeCanonical`:967 | `getCanonicalType`:129 |
| xArray | org/xvm/runtime/template/collections/xArray.java | `m_hEmptyByteArray`:1056 | `ensureEmptyByteArray`:943 |
| xByteArray | org/xvm/runtime/template/collections/xByteArray.java | `m_clzInt8Array`:227, `m_clzInt16Array`:228, `m_clzInt64Array`:229, `m_clzFloat64Array`:230 | `getInt8ArrayComposition`:195; `getInt16ArrayComposition`:204; `getInt64ArrayComposition`:212; `getFloat64ArrayComposition`:221 |
| xListMap | org/xvm/runtime/template/maps/xListMap.java | `m_constructor`:135 | `ensureConstructor`:131 |
| xFPLiteral$FPNHandle | org/xvm/runtime/template/numbers/xFPLiteral.java | `m_hText`:224 | `getText`:201 |
| xIntLiteral$IntNHandle | org/xvm/runtime/template/numbers/xIntLiteral.java | `m_hText`:472 | `getText`:449 |
| xInjector | org/xvm/runtime/template/reflect/xInjector.java | `m_hInjector`:79 | `ensureInjector`:44 |
| xModule | org/xvm/runtime/template/reflect/xModule.java | `m_typeListMap`:347 | `ensureListMapType`:334 |
| xRef$RefHandle | org/xvm/runtime/template/reflect/xRef.java | `m_sName`:1070 | `getName`:981 |
| xRegEx$RegExHandle | org/xvm/runtime/template/text/xRegEx.java | `m_pattern`:264 | `getPattern`:292 |
| xString$StringHandle | org/xvm/runtime/template/text/xString.java | `m_hash`:325, `m_sValue`:326 | `getHashCode`:365; `getStringValue`:341 |
| xString | org/xvm/runtime/template/text/xString.java | `m_hEmptyStringArray`:446 | `ensureEmptyArray`:432 |

**org.xvm.tool (13)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| ModuleInfo$Node | org/xvm/tool/ModuleInfo.java | `m_resdir`:1067 | `resourceDir in ModuleInfo$DirNode`:1206,1212; `resourceDir in ModuleInfo$FileNode`:1485,1487,1489 |
| ModuleInfo$DirNode | org/xvm/tool/ModuleInfo.java | `m_nodeSrc`:1396 | `parse`:1233 |
| ModuleInfo$FileNode | org/xvm/tool/ModuleInfo.java | `m_source`:1652 | `source`:1527 |
| ModuleInfo | org/xvm/tool/ModuleInfo.java | `moduleName`:2003, `sourceContent`:2010, `sourceTimestamp`:2011, `sourceNode`:2012, `resourceDir`:2014, `resourceTimestamp`:2015, `binaryStatus`:2017, `binaryDir`:2018, `binaryFile`:2019, `binaryTimestamp`:2022 | `getQualifiedModuleName`:432,434,437...; `loadBinaryFile`:656,660,666; `getSourceTimestamp`:561,566; `getSourceTree`:822,824; `getResourceDir`:584; `getResourceTimestamp`:598; +3 more methods |

**org.xvm.type (3)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| Decimal128 | org/xvm/type/Decimal128.java | `m_dec`:522 | `toBigDecimal`:214 |
| Decimal32 | org/xvm/type/Decimal32.java | `m_dec`:413 | `toBigDecimal`:156 |
| Decimal64 | org/xvm/type/Decimal64.java | `m_dec`:445 | `toBigDecimal`:166 |

**org.xvm.util (javatools_utils, with concurrent, converter) (6)**

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| ConstOrdinalList$1 | org/xvm/util/ConstOrdinalList.java | `nodeCur`:251 | `currentNode`:286 |
| ConstOrdinalList$Node | org/xvm/util/ConstOrdinalList.java | `vals`:656, `ofVals`:657 | `createNodes in ConstOrdinalList`:534,535,554...; `ensureFastNode in ConstOrdinalList`:602,603 |
| Lazy$UnsynchronizedLazy | org/xvm/util/Lazy.java | `computed`:517 | `get`:528 |
| PackedInteger | org/xvm/util/PackedInteger.java | `m_bigint`:962 | `getBigInteger`:240; `setBigInteger`:268 |
| AbstractConverterEntry | org/xvm/util/converter/AbstractConverterEntry.java | `key`:20 | `getKey`:72 |

#### Lazy with reset (190)

| Class | Path | Fields:line | Writes outside constructors |
|---|---|---|---|
| EmbeddingSupport | org/xvm/api/EmbeddingSupport.java | `connector`:141 | `ensureConnector`:250; `shutdownInterpreter`:267 |
| ClassStructure | org/xvm/asm/ClassStructure.java | `m_typeFormal`:3794, `m_typeNormalized`:3799 | `addTypeParam`:590,591; `getFormalType`:688; `registerConstants`:3337,3338; `getNormalizedType`:719,745 |
| ConstantPool | org/xvm/asm/ConstantPool.java | 169 fields at :3840-4024, for example `m_valEcstasy`:3840, `m_clzComparable`:3841, `m_clzObject`:3842, `m_clzInner`:3843 | one accessor per field, :2176-2449; `optimize`:3505-3673 |
| FileRepository | org/xvm/asm/FileRepository.java | `cachedModuleStructures`:377 | `storeModule`:162; `validateCache`:243; `ensureModuleCache`:346 |
| MethodStructure | org/xvm/asm/MethodStructure.java | `m_abOps`:3026 | `createCode`:643; `forceAssembly`:1051; `setAbstract`:1091; `disassemble`:1923; `ensureAssembled in MethodStructure$Code`:2630 |
| Scope | org/xvm/asm/Scope.java | `m_scopeChild`:244 | `enter`:37; `exit`:73 |
| VersionTree$Node | org/xvm/asm/VersionTree.java | `kids`:1051 | `ensureChild`:609; `removeChild`:842 |
| IdentityConstant | org/xvm/asm/constants/IdentityConstant.java | `m_component`:800 | `getComponent`:601; `resetCachedInfo`:695 |
| PropertyConstant | org/xvm/asm/constants/PropertyConstant.java | `m_type`:451, `m_constSig`:456, `m_typeConstraint`:461 | `invalidateCache`:278,279...; `getType`:357; `assemble`:422,423; `getSignature`:137; `getConstraintType in FormalTypeChildConstant`:106,114...; `getConstraintType`:111 |
| SingletonConstant | org/xvm/asm/constants/SingletonConstant.java | `m_cfInitialized`:296 | `setHandle`:125; `getInitializationWaiter`:173; `abortInitialization`:187 |
| TypeCollector | org/xvm/asm/constants/TypeCollector.java | `m_listSingle`:464 | `ensureSingle`:104; `ensureMulti`:150 |
| TypeConstant | org/xvm/asm/constants/TypeConstant.java | `m_handle`:8641 | `ensureTypeHandle`:8157; `setContaining`:8305 |
| Label | org/xvm/asm/op/Label.java | `m_action`:167 | `setAction`:142,144; `clear`:153 |
| CaseManager | org/xvm/compiler/ast/CaseManager.java | `m_labelCurrent`:1263 | `validateCase`:390; `endCaseGroup`:685 |
| LambdaExpression | org/xvm/compiler/ast/LambdaExpression.java | `m_collector`:1468 | `collectReturnTypes`:231; `validate`:540; `extractReturnTypes`:705 |
| StatementExpression | org/xvm/compiler/ast/StatementExpression.java | `m_collector`:307 | `collectReturnTypes`:90; `getImplicitTypes`:130; `testFitMulti`:179; `validateMulti`:216 |
| xLazy$LazyHandle | org/xvm/runtime/template/annotations/xLazy.java | `m_setInitFiber`:113 | `registerAssign`:134; `unregisterAssign`:154 |
| Compiler | org/xvm/tool/Compiler.java | `repoLib`:805 | `process`:168; `ensureLibraryRepo`:463 |
| FilterIterator | org/xvm/util/FilterIterator.java | `next`:24 | `hasNext`:42; `next`:53 |

`ConstantPool` accounts for 169 of these. The 16 `ConstantPool` caches in the "never reset" table (for example `m_typeFPNumber`, `org/xvm/asm/ConstantPool.java:3940`) are not cleared by `optimize()`; whether that is deliberate is not clear from the code.

### Init-style methods

71 fields written in `init*`, `configure*`, `load*`, `resolveAddresses`, `build*`, `prepare*` and similar methods (inferred to run once, while the object is being set up).

- **Technicality:** construction is split between the constructor and a second method, often because the value needs other objects that do not exist yet when the constructor runs.
- **Fix:** pass the value to the constructor (factory or builder), or move the field into an object created by the init method.
- **Cost:** low to medium per class; check each one's call order first.

| Class | Field | Location | Writes outside constructors |
|---|---|---|---|
| EmbeddingSupport | `cfgRepo` | org/xvm/api/EmbeddingSupport.java:139 | `configure`:327 |
| EmbeddingSupport | `cfgInjector` | org/xvm/api/EmbeddingSupport.java:140 | `configure`:328 |
| InterpreterConnector | `m_containerMain` | org/xvm/api/InterpreterConnector.java:211 | `loadModule`:60; `join`:180 |
| Component$SimpleCollector | `m_constant` | org/xvm/asm/Component.java:3569 | `resolvedComponent`:3542; `resolvedConstant`:3548 |
| MethodStructure | `m_fInitialized` | org/xvm/asm/MethodStructure.java:3086 | `initialize`:1432,1442 |
| OpCondJump | `m_cExits` | org/xvm/asm/OpCondJump.java:695 | `resolveAddresses`:138 |
| OpJump | `m_cExits` | org/xvm/asm/OpJump.java:124 | `resolveAddresses`:58 |
| OpReturn | `m_ixAllGuard` | org/xvm/asm/OpReturn.java:409 | `resolveAddresses`:48 |
| OpReturn | `m_fCallFinally` | org/xvm/asm/OpReturn.java:410 | `resolveAddresses`:49 |
| OpReturn | `m_nFinallyAddr` | org/xvm/asm/OpReturn.java:411 | `registerJump`:68 |
| VersionTree$1 | `next` | org/xvm/asm/VersionTree.java:120 | `next`:78; `loadNext`:102,110 |
| SignatureConstant | `m_fProperty` | org/xvm/asm/constants/SignatureConstant.java:939 | `resolveGenericTypes`:289 |
| FinallyEnd | `m_nFinallyAddr` | org/xvm/asm/op/FinallyEnd.java:248 | `registerJump`:98 |
| GuardAll | `m_jumps` | org/xvm/asm/op/GuardAll.java:195 | `registerJumps`:96 |
| GuardAll | `m_fDoReturn` | org/xvm/asm/op/GuardAll.java:196 | `registerJumps`:97 |
| GuardStart | `m_aofCatch` | org/xvm/asm/op/GuardStart.java:209 | `resolveAddresses`:123 |
| Jump | `m_fCallFinally` | org/xvm/asm/op/Jump.java:172 | `resolveAddresses`:78 |
| Jump | `m_ixAllGuard` | org/xvm/asm/op/Jump.java:173 | `resolveAddresses`:75 |
| Jump | `m_ixBaseGuard` | org/xvm/asm/op/Jump.java:174 | `resolveAddresses`:76 |
| Jump | `m_nJumpToScope` | org/xvm/asm/op/Jump.java:175 | `resolveAddresses`:77 |
| Jump | `m_cPopGuards` | org/xvm/asm/op/Jump.java:176 | `resolveAddresses`:83 |
| JumpInt | `m_aofCase` | org/xvm/asm/op/JumpInt.java:245 | `resolveAddresses`:101 |
| JumpInt | `m_acExits` | org/xvm/asm/op/JumpInt.java:252 | `resolveAddresses`:108 |
| JumpInt | `m_cDefaultExits` | org/xvm/asm/op/JumpInt.java:253 | `resolveAddresses`:112 |
| JumpVal | `m_ahCase` | org/xvm/asm/op/JumpVal.java:758 | `buildJumpMap`:330 |
| JumpVal | `m_mapJump` | org/xvm/asm/op/JumpVal.java:763 | `buildJumpMap`:331 |
| JumpVal | `m_algorithm` | org/xvm/asm/op/JumpVal.java:768 | `buildJumpMap`:332 |
| JumpVal | `m_typeCond` | org/xvm/asm/op/JumpVal.java:773 | `buildJumpMap`:333 |
| JumpVal_N | `m_atypeColumn` | org/xvm/asm/op/JumpVal_N.java:559 | `buildSmallJumpMaps`:468 |
| JumpVal_N | `m_amapJumpSmall` | org/xvm/asm/op/JumpVal_N.java:565 | `buildSmallJumpMaps`:469 |
| JumpVal_N | `m_alWildcardSmall` | org/xvm/asm/op/JumpVal_N.java:572 | `buildSmallJumpMaps`:470 |
| JumpVal_N | `m_aAlgorithm` | org/xvm/asm/op/JumpVal_N.java:585 | `buildSmallJumpMaps`:471 |
| JumpVal_N | `m_algorithm` | org/xvm/asm/op/JumpVal_N.java:586 | `buildSmallJumpMaps`:472 |
| Label | `m_fBound` | org/xvm/asm/op/Label.java:172 | `build`:102; `clear`:154 |
| LoopEnd | `m_ofJmp` | org/xvm/asm/op/LoopEnd.java:106 | `resolveAddresses`:53 |
| OpSwitch | `m_aofCase` | org/xvm/asm/op/OpSwitch.java:454 | `resolveAddresses`:127 |
| OpSwitch | `m_acExits` | org/xvm/asm/op/OpSwitch.java:461 | `resolveAddresses`:134 |
| OpSwitch | `m_cDefaultExits` | org/xvm/asm/op/OpSwitch.java:462 | `resolveAddresses`:138 |
| NewExpression | `left` | org/xvm/compiler/ast/NewExpression.java:1606 | `prepareConstruction`:365 |
| NewExpression | `type` | org/xvm/compiler/ast/NewExpression.java:1608 | `prepareConstruction`:467 |
| NewExpression | `m_nParentSteps` | org/xvm/compiler/ast/NewExpression.java:1649 | `prepareConstruction`:595 |
| NewExpression | `m_idFormal` | org/xvm/compiler/ast/NewExpression.java:1653 | `prepareConstruction`:329 |
| NewExpression | `m_regFormal` | org/xvm/compiler/ast/NewExpression.java:1657 | `prepareConstruction`:332,341 |
| NewExpression | `m_fFixedSizeArray` | org/xvm/compiler/ast/NewExpression.java:1661 | `prepareConstruction`:573 |
| LambdaBindings$Collector | `method` | org/xvm/compiler/ast/bindings/LambdaBindings.java:54 | `bind`:79 |
| JitConnector | `ts` | org/xvm/javajit/JitConnector.java:218 | `loadModule`:48 |
| JitConnector | `module` | org/xvm/javajit/JitConnector.java:223 | `loadModule`:43 |
| CommonBuilder | `implSize` | org/xvm/javajit/builders/CommonBuilder.java:136 | `build`:170 |
| ClassComposition$FieldInfo | `constInit` | org/xvm/runtime/ClassComposition.java:856 | `createInitializer in ClassStructure`:2855 |
| ClassComposition$FieldInfo | `methodInit` | org/xvm/runtime/ClassComposition.java:857 | `createInitializer in ClassStructure`:2865 |
| Fiber | `m_hAsyncSection` | org/xvm/runtime/Fiber.java:658 | `registerAsyncSection`:445 |
| Fiber | `m_resume` | org/xvm/runtime/Fiber.java:685 | `prepareRun`:296; `registerAsyncSection`:427 |
| ServiceContext$Message | `m_fiber` | org/xvm/runtime/ServiceContext.java:1819 | `createFrame in ServiceContext$OpRequest`:1910; `createFrame in ServiceContext$CallLaterRequest`:2064 |
| xRTCompiler$CompilerAdapter | `m_repoResults` | org/xvm/runtime/template/_native/lang/src/xRTCompiler.java:310 | `reset`:463; `configureResultRepo`:479 |
| xContainerControl | `m_clzControl` | org/xvm/runtime/template/_native/mgmt/xContainerControl.java:235 | `initNative`:57 |
| xCoreRepository | `m_clzRepo` | org/xvm/runtime/template/_native/mgmt/xCoreRepository.java:144 | `initNative`:49 |
| xRTServiceControl | `m_clzControl` | org/xvm/runtime/template/_native/xRTServiceControl.java:143 | `initNative`:49 |
| xRegEx | `m_constructorMatch` | org/xvm/runtime/template/text/xRegEx.java:319 | `initNative`:70 |
| xRegEx | `m_clzMatchStruct` | org/xvm/runtime/template/text/xRegEx.java:324 | `initNative`:69 |
| xRegEx | `m_clzRangeOfInt` | org/xvm/runtime/template/text/xRegEx.java:329 | `initNative`:73 |
| xRegEx | `m_clzRangeArray` | org/xvm/runtime/template/text/xRegEx.java:334 | `initNative`:76 |
| xEnum | `m_listNames` | org/xvm/runtime/template/xEnum.java:444 | `initNative`:74 |
| xEnum | `m_listHandles` | org/xvm/runtime/template/xEnum.java:445 | `initNative`:75 |
| Compiler | `strictLevel` | org/xvm/tool/Compiler.java:803 | `validateOptions`:728,730 |
| ModuleInfo$FileNode | `m_stmtType` | org/xvm/tool/ModuleInfo.java:1662 | `registerNames`:1611 |
| ModuleInfo | `binaryVersion` | org/xvm/tool/ModuleInfo.java:2020 | `loadBinaryFile`:661 |
| ModuleInfo | `binaryContent` | org/xvm/tool/ModuleInfo.java:2021 | `loadBinaryFile`:657,662 |
| ConstBitSet$Node | `jmp` | org/xvm/util/ConstBitSet.java:467 | `createSkips in ConstBitSet`:502 |
| ConstOrdinalList$Node | `val` | org/xvm/util/ConstOrdinalList.java:655 | `createNodes in ConstOrdinalList`:545 |
| ConstOrdinalList$Node | `jmp` | org/xvm/util/ConstOrdinalList.java:658 | `createSkips in ConstOrdinalList`:681 |
| ListSet$SafeIterator | `m_eNext` | org/xvm/util/ListSet.java:988 | `close`:900; `loadNext`:934; `synced`:960 |

### Setters used while building

34 fields written only by `set*` methods (and possibly reset to `null`). Whether the setter is called only before publication is not checked (inferred). `PackedInteger` (finding 3) is the case that was read in code.

- **Fix:** constructor parameter or builder; for `PackedInteger`, compute in the constructors and remove the public setters.
- **Cost:** low where callers are few; check the callers.

| Class | Field | Location | Writes outside constructors |
|---|---|---|---|
| Component | `m_sDoc` | org/xvm/asm/Component.java:3642 | `setDocumentation`:2152 |
| Constant | `m_iPos` | org/xvm/asm/Constant.java:980 | `setPosition`:379 |
| ConstantPool | `m_typeNakedRef` | org/xvm/asm/ConstantPool.java:3835 | `setNakedRefType`:3424 |
| XvmStructure | `m_xsParent` | org/xvm/asm/XvmStructure.java:640 | `setContaining`:120 |
| FSNodeConstant | `m_handle` | org/xvm/asm/constants/FSNodeConstant.java:413 | `setHandle`:230 |
| FileStoreConstant | `m_handle` | org/xvm/asm/constants/FileStoreConstant.java:213 | `setHandle`:115 |
| FinallyEnd | `m_fAdvances` | org/xvm/asm/op/FinallyEnd.java:247 | `setCompletes`:91 |
| Guarded | `m_scope` | org/xvm/asm/op/Guarded.java:58 | `setScope`:43; `clear`:50 |
| Label | `m_label` | org/xvm/asm/op/Label.java:162 | `setLabel`:127; `clear`:152 |
| Fiber | `m_nanoStarted` | org/xvm/runtime/Fiber.java:625 | `setStatus`:197,204 |
| Fiber | `m_hTimeout` | org/xvm/runtime/Fiber.java:630 | `setTimeoutHandle`:86; `clearTimeout`:103 |
| Fiber | `m_ldtTimeout` | org/xvm/runtime/Fiber.java:635 | `setTimeoutHandle`:87; `clearTimeout`:102 |
| Fiber | `m_frameBlocker` | org/xvm/runtime/Fiber.java:691 | `setBlocker`:474 |
| Frame$DeferredGuardAction | `m_ixGuard` | org/xvm/runtime/Frame.java:2463 | `setGuardIndex`:2458 |
| Frame$VarInfo | `m_ref` | org/xvm/runtime/Frame.java:2480 | `setRef`:2594 |
| MainContainer | `m_nResult` | org/xvm/runtime/MainContainer.java:312 | `setResult`:291 |
| ObjectHandle$GenericHandle | `m_owner` | org/xvm/runtime/ObjectHandle.java:637 | `setOwner`:456; `<init> in xService$ServiceHandle`:519 |
| ServiceContext | `m_hService` | org/xvm/runtime/ServiceContext.java:2219 | `setService`:115; `shutdown`:890; `terminate`:936 |
| ServiceContext | `m_hSynchronizedSection` | org/xvm/runtime/ServiceContext.java:2249 | `setSynchronizedSection`:144 |
| ServiceContext | `m_synchronicity` | org/xvm/runtime/ServiceContext.java:2289 | `setSynchronicity`:166; `terminateFiber`:872 |
| ServiceContext | `m_fiberSyncOwner` | org/xvm/runtime/ServiceContext.java:2294 | `setSynchronicity`:165; `terminateFiber`:871 |
| xRTDelegate$DelegateHandle | `m_mutability` | org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java:898 | `setMutability`:913 |
| xRawChannel$ChannelHandle | `m_cPreferredBufferSize` | org/xvm/runtime/template/_native/io/xRawChannel.java:91 | `setPreferredBufferSize`:89 |
| xRTCompiler$CompilerAdapter | `m_listRepos` | org/xvm/runtime/template/_native/lang/src/xRTCompiler.java:308 | `setLibraryRepos`:327; `reset`:462 |
| xRTCompiler$CompilerAdapter | `m_listSources` | org/xvm/runtime/template/_native/lang/src/xRTCompiler.java:309 | `setSourceLocations`:335; `reset`:461 |
| xRTServer$Router | `m_hBinding` | org/xvm/runtime/template/_native/web/xRTServer.java:804 | `setBinding`:831 |
| xRTServer$Router | `m_routeDirect` | org/xvm/runtime/template/_native/web/xRTServer.java:805 | `setDirectRoute`:839 |
| xAtomicInt128$AtomicLongLongHandle | `m_atomicValue` | org/xvm/runtime/template/annotations/xAtomicInt128.java:127 | `setReferentImpl in xAtomicInt128`:116 |
| xAtomicIntNumber$AtomicJavaLongHandle | `m_atomicValue` | org/xvm/runtime/template/annotations/xAtomicIntNumber.java:143 | `setReferentImpl in xAtomicIntNumber`:132 |
| xArray$ArrayHandle | `m_mutability` | org/xvm/runtime/template/collections/xArray.java:988 | `setMutability`:1007 |
| xRef$RefHandle | `m_hReferent` | org/xvm/runtime/template/reflect/xRef.java:1068 | `setReferent in xInject$InjectedHandle`:180; `<init> in xRef$IndexedRefHandle`:1104 |
| PackedInteger | `m_fInitialized` | org/xvm/util/PackedInteger.java:944 | `setLong`:225; `setBigInteger`:269 |
| PackedInteger | `m_fBig` | org/xvm/util/PackedInteger.java:949 | `setBigInteger`:259 |
| PackedInteger | `m_lValue` | org/xvm/util/PackedInteger.java:955 | `setLong`:224; `setBigInteger`:260,263,265 |

### Reset, subclass-constructor and test writes

- **Technicality:** the field is set in a constructor and only cleared later (`close()`, `clear()`, `discard()`), or a subclass constructor assigns a superclass field, or an initializer is overwritten by a constructor.
- **Fix:** pass the value up through `super(...)`; for clear-on-close, keep it mutable or wrap the state.
- **Cost:** low.

| Class | Field | Location | Label | Writes outside constructors |
|---|---|---|---|---|
| Reporting | `destination` | org/xvm/asm/Reporting.java:17 | reset | `<init> in Reporting$Scope`:54; `close in Reporting$Scope`:59 |
| VersionTree | `root` | org/xvm/asm/VersionTree.java:1059 | reset | `clear`:399 |
| Context | `m_ctxOuter` | org/xvm/compiler/ast/Context.java:2601 | reset | `discard`:401 |
| xNanosTimer$TimerHandle$Alarm$Trigger | `m_alarm` | org/xvm/runtime/template/_native/temporal/xNanosTimer.java:477 | reset | `run`:458; `cancel`:472 |
| xRef$RefHandle | `m_frame` | org/xvm/runtime/template/reflect/xRef.java:1071 | reset | `dereference`:1037 |
| HasherReference | `hasher` | org/xvm/util/HasherReference.java:12 | reset | `reset`:37 |
| HasherReference | `referent` | org/xvm/util/HasherReference.java:17 | reset | `reset`:36 |
| Lazy$UnsynchronizedLazy | `supplier` | org/xvm/util/Lazy.java:515 | reset | `get`:527 |
| OpCallable | `m_aArgReturn` | org/xvm/asm/OpCallable.java:1125 | subclass-or-inner-ctor | `<init> in Call_0N`:38; `<init> in Call_1N`:43; `<init> in Call_NN`:40; `<init> in Call_TN`:41 |
| OpInvocable | `m_aArgReturn` | org/xvm/asm/OpInvocable.java:626 | subclass-or-inner-ctor | `<init> in Invoke_0N`:37; `<init> in Invoke_1N`:58; `<init> in Invoke_NN`:45; `<init> in Invoke_TN`:39 |
| TypeCompositionStatement | `qualified` | org/xvm/compiler/ast/TypeCompositionStatement.java:3127 | subclass-or-inner-ctor | `<init> in IncompleteTypeCompositionStatement`:68 |
| SimulatedLinkerContext | `influences` | org/xvm/asm/SimulatedLinkerContext.java:188 | init-and-ctor-write |  |

## Category 1: non-final static fields

All 301 are in `org.xvm.runtime` and its subpackages. `org.xvm.compiler` and `org.xvm.asm` have none left after `d2ecd0a254`.

| Kind | Count | Technicality | Fix | Cost |
|---|---:|---|---|---|
| proven | 9 | assigned only in their initializer | add `final` (and see category 4 for the `ClassTemplate` arrays) | low |
| static-from-ctor | 147 | template singleton assigned from an instance constructor (`if (fInstance) INSTANCE = this;`) | per-container template registry; belongs to `doc/parallel-runtime.md` | high |
| static-setup | 134 | assigned in `initNative()`, once per `NativeContainer` | same | high |
| lazy | 5 | lazily created terminal readers (`DebugConsole`, `xTerminalConsole`) and the `xOSStorage` watch daemon | `Lazy` or holder class | low |
| never-assigned | 4 | declared, never written (always `null`) | delete or make `final` with `null` | low |
| volatile / mutable | 2 | real process-wide state | keep | - |

| Class | Path | Kind | Fields:line | Writes |
|---|---|---|---|---|
| ClassTemplate | org/xvm/runtime/ClassTemplate.java | proven | `VOID`:2428, `THIS`:2429, `OBJECT`:2430, `INT`:2431, `STRING`:2432, `BOOLEAN`:2433, `BYTES`:2434 |  |
| DebugConsole | org/xvm/runtime/DebugConsole.java | lazy | `LINE_READER`:2421, `READER`:2431 | `activate`:83,84 |
| DebugConsole | org/xvm/runtime/DebugConsole.java | mutable | `TERMINAL`:2426 | `activate`:85 |
| Runtime | org/xvm/runtime/Runtime.java | volatile | `s_failureReporter`:202 | `setFailureReporter`:93 |
| Utils | org/xvm/runtime/Utils.java | static-setup | `CONST_HELPER`:1774, `ANNOTATION_TEMPLATE`:1775, `ANNOTATION_TEMPLATE_TEMPLATE`:1776, `ARGUMENT_TEMPLATE`:1777, `RT_PARAMETER_TEMPLATE`:1778, `ANNOTATION_CONSTRUCT`:1779, `ANNOTATION_TEMPLATE_CONSTRUCT`:1780, `ARGUMENT_CONSTRUCT`:1781, `RT_PARAMETER_CONSTRUCT`:1782, `LIST_MAP_CONSTRUCT`:1783, `STRING_VALUE_OF`:1784, `ANNOTATION_ARRAY_TYPE`:1785, `ARGUMENT_ARRAY_TYPE`:1786, `SIG_FREEZE`:1787, `SIG_GET_RESOURCE`:1788, `SIG_INJECT`:1789 | `initNative`:84,85,86... |
| Identity | org/xvm/runtime/template/Identity.java | static-from-ctor | `INSTANCE`:23, `INCEPTION_CLASS`:24 | `<init>`:30,31 |
| Proxy | org/xvm/runtime/template/Proxy.java | static-from-ctor | `INSTANCE`:33 | `<init>`:38 |
| LongBasedBitView | org/xvm/runtime/template/_native/collections/arrays/LongBasedBitView.java | never-assigned | `INSTANCE`:27 |  |
| LongDelegate | org/xvm/runtime/template/_native/collections/arrays/LongDelegate.java | never-assigned | `INSTANCE`:18 |  |
| LongLongDelegate | org/xvm/runtime/template/_native/collections/arrays/LongLongDelegate.java | never-assigned | `INSTANCE`:31 |  |
| xRTBitDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTBitDelegate.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xRTBooleanDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTBooleanDelegate.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xRTCharDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTCharDelegate.java | static-from-ctor | `INSTANCE`:31 | `<init>`:37 |
| xRTDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java | static-from-ctor | `INSTANCE`:43 | `<init>`:49 |
| xRTDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java | static-setup | `DELEGATES`:943 | `initNative`:111 |
| xRTFloat64Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTFloat64Delegate.java | static-from-ctor | `INSTANCE`:30 | `<init>`:36 |
| xRTInt128Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTInt128Delegate.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xRTInt16Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTInt16Delegate.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xRTInt32Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTInt32Delegate.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xRTInt64Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTInt64Delegate.java | static-from-ctor | `INSTANCE`:18 | `<init>`:24 |
| xRTInt8Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTInt8Delegate.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xRTNibbleDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTNibbleDelegate.java | static-from-ctor | `INSTANCE`:20 | `<init>`:26 |
| xRTSlicingDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTSlicingDelegate.java | static-from-ctor | `INSTANCE`:23 | `<init>`:29 |
| xRTStringDelegate | org/xvm/runtime/template/_native/collections/arrays/xRTStringDelegate.java | static-from-ctor | `INSTANCE`:32 | `<init>`:38 |
| xRTUInt128Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTUInt128Delegate.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xRTUInt16Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTUInt16Delegate.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xRTUInt32Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTUInt32Delegate.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xRTUInt64Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTUInt64Delegate.java | static-from-ctor | `INSTANCE`:18 | `<init>`:24 |
| xRTUInt8Delegate | org/xvm/runtime/template/_native/collections/arrays/xRTUInt8Delegate.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xRTViewFromBit | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromBit.java | static-from-ctor | `INSTANCE`:17 | `<init>`:23 |
| xRTViewFromBitToBoolean | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromBitToBoolean.java | static-from-ctor | `INSTANCE`:26 | `<init>`:32 |
| xRTViewFromBitToByte | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromBitToByte.java | static-from-ctor | `INSTANCE`:27 | `<init>`:33 |
| xRTViewFromBitToNibble | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromBitToNibble.java | static-from-ctor | `INSTANCE`:27 | `<init>`:33 |
| xRTViewFromByte | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromByte.java | static-from-ctor | `INSTANCE`:18 | `<init>`:24 |
| xRTViewFromByteToFloat64 | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromByteToFloat64.java | static-from-ctor | `INSTANCE`:25 | `<init>`:31 |
| xRTViewFromByteToInt16 | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromByteToInt16.java | static-from-ctor | `INSTANCE`:24 | `<init>`:30 |
| xRTViewFromByteToInt64 | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromByteToInt64.java | static-from-ctor | `INSTANCE`:26 | `<init>`:32 |
| xRTViewFromByteToInt8 | org/xvm/runtime/template/_native/collections/arrays/xRTViewFromByteToInt8.java | static-from-ctor | `INSTANCE`:24 | `<init>`:30 |
| xRTViewToBit | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBit.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xRTViewToBit | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBit.java | static-setup | `VIEWS`:107 | `initNative`:74 |
| xRTViewToBitFromFloat64 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromFloat64.java | static-from-ctor | `INSTANCE`:36 | `<init>`:42 |
| xRTViewToBitFromInt128 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromInt128.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromInt16 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromInt16.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromInt32 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromInt32.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromInt64 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromInt64.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromInt8 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromInt8.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromNibble | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromNibble.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromUInt128 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromUInt128.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromUInt16 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromUInt16.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromUInt32 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromUInt32.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromUInt64 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromUInt64.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xRTViewToBitFromUInt8 | org/xvm/runtime/template/_native/collections/arrays/xRTViewToBitFromUInt8.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xBasicHashCollector | org/xvm/runtime/template/_native/collections/xBasicHashCollector.java | static-from-ctor | `INSTANCE`:26 | `<init>`:34 |
| xRTAlgorithms | org/xvm/runtime/template/_native/crypto/xRTAlgorithms.java | static-from-ctor | `INSTANCE`:55 | `<init>`:61 |
| xRTCertificateManager | org/xvm/runtime/template/_native/crypto/xRTCertificateManager.java | static-from-ctor | `INSTANCE`:75 | `<init>`:81 |
| xRTDecryptor | org/xvm/runtime/template/_native/crypto/xRTDecryptor.java | static-from-ctor | `INSTANCE`:33 | `<init>`:39 |
| xRTHasher | org/xvm/runtime/template/_native/crypto/xRTHasher.java | static-from-ctor | `INSTANCE`:28 | `<init>`:34 |
| xRTKeyGenerator | org/xvm/runtime/template/_native/crypto/xRTKeyGenerator.java | static-from-ctor | `INSTANCE`:24 | `<init>`:30 |
| xRTKeyStore | org/xvm/runtime/template/_native/crypto/xRTKeyStore.java | static-from-ctor | `INSTANCE`:83 | `<init>`:89 |
| xRTKeyStore | org/xvm/runtime/template/_native/crypto/xRTKeyStore.java | static-setup | `s_typeNamedPassword`:574 | `initNative`:103 |
| xRTSigner | org/xvm/runtime/template/_native/crypto/xRTSigner.java | static-from-ctor | `INSTANCE`:39 | `<init>`:45 |
| xCPDirectory | org/xvm/runtime/template/_native/fs/xCPDirectory.java | static-setup | `s_constructor`:53 | `initNative`:33 |
| xCPFile | org/xvm/runtime/template/_native/fs/xCPFile.java | static-setup | `s_constructor`:53 | `initNative`:33 |
| xCPFileStore | org/xvm/runtime/template/_native/fs/xCPFileStore.java | static-setup | `s_constructor`:155 | `initNative`:46 |
| xOSDirectory | org/xvm/runtime/template/_native/fs/xOSDirectory.java | static-from-ctor | `INSTANCE`:31 | `<init>`:37 |
| xOSDirectory | org/xvm/runtime/template/_native/fs/xOSDirectory.java | static-setup | `s_constructor`:128 | `initNative`:48 |
| xOSFile | org/xvm/runtime/template/_native/fs/xOSFile.java | static-from-ctor | `INSTANCE`:54 | `<init>`:60 |
| xOSFile | org/xvm/runtime/template/_native/fs/xOSFile.java | static-setup | `s_constructor`:503 | `initNative`:76 |
| xOSStorage | org/xvm/runtime/template/_native/fs/xOSStorage.java | static-setup | `s_methodOnEvent`:364 | `initNative`:54 |
| xOSStorage | org/xvm/runtime/template/_native/fs/xOSStorage.java | lazy | `s_daemonWatch`:366 | `ensureWatchDaemon`:255 |
| xRawOSFileChannel | org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java | static-from-ctor | `INSTANCE`:48 | `<init>`:54 |
| xExternalConsole | org/xvm/runtime/template/_native/io/xExternalConsole.java | static-from-ctor | `INSTANCE`:36 | `<init>`:61 |
| xRTBuffer | org/xvm/runtime/template/_native/io/xRTBuffer.java | static-from-ctor | `INSTANCE`:26 | `<init>`:32 |
| xRTBuffer | org/xvm/runtime/template/_native/io/xRTBuffer.java | static-setup | `PROP_RAW_BYTES`:97 | `initNative`:40 |
| xTerminalConsole | org/xvm/runtime/template/_native/io/xTerminalConsole.java | static-from-ctor | `INSTANCE`:47 | `<init>`:53 |
| xTerminalConsole | org/xvm/runtime/template/_native/io/xTerminalConsole.java | lazy | `READER`:228, `TERMINAL`:229 | `ensureLineReader`:187,188 |
| xRTCompiler | org/xvm/runtime/template/_native/lang/src/xRTCompiler.java | static-from-ctor | `INSTANCE`:65 | `<init>`:71 |
| xRTCompiler | org/xvm/runtime/template/_native/lang/src/xRTCompiler.java | static-setup | `GET_MODULE_ID`:498 | `initNative`:78 |
| xContainerControl | org/xvm/runtime/template/_native/mgmt/xContainerControl.java | static-from-ctor | `INSTANCE`:42 | `<init>`:48 |
| xContainerLinker | org/xvm/runtime/template/_native/mgmt/xContainerLinker.java | static-from-ctor | `INSTANCE`:51 | `<init>`:57 |
| xContainerLinker | org/xvm/runtime/template/_native/mgmt/xContainerLinker.java | static-setup | `GET_RESOURCE`:315 | `initNative`:65 |
| xCoreRepository | org/xvm/runtime/template/_native/mgmt/xCoreRepository.java | static-from-ctor | `INSTANCE`:34 | `<init>`:40 |
| xRTNameService | org/xvm/runtime/template/_native/net/xRTNameService.java | static-from-ctor | `INSTANCE`:51 | `<init>`:57 |
| xRTNameService | org/xvm/runtime/template/_native/net/xRTNameService.java | static-setup | `BYTE_ARRAY_ARRAY_TYPE`:281 | `initNative`:63 |
| xRTNetwork | org/xvm/runtime/template/_native/net/xRTNetwork.java | static-from-ctor | `INSTANCE`:38 | `<init>`:44 |
| xRTNetworkInterface | org/xvm/runtime/template/_native/net/xRTNetworkInterface.java | static-from-ctor | `INSTANCE`:29 | `<init>`:35 |
| xRTSocket | org/xvm/runtime/template/_native/net/xRTSocket.java | static-from-ctor | `INSTANCE`:52 | `<init>`:60 |
| xRTRandom | org/xvm/runtime/template/_native/numbers/xRTRandom.java | static-from-ctor | `INSTANCE`:49 | `<init>`:55 |
| xRTClassTemplate | org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java | static-from-ctor | `INSTANCE`:50 | `<init>`:56 |
| xRTClassTemplate | org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java | proven | `NO_TEMPLATES`:573 |  |
| xRTClassTemplate | org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java | static-setup | `CLASS_TEMPLATE_TYPE`:575, `CLASS_TEMPLATE_ARRAY_TYPE`:576, `MULTI_METHOD_ARRAY_TYPE`:577, `METHOD_ARRAY_TYPE`:578, `ANNOTATION_ARRAY_TYPE`:579, `CONTRIBUTION_ARRAY_TYPE`:580, `EMPTY_PARAMETER_ARRAY`:582, `ACTION_TEMPLATE`:584, `CREATE_CONTRIB_METHOD`:585, `CREATE_TYPE_PARAMETERS_METHOD`:586 | `initNative`:65,67,68... |
| xRTComponentTemplate | org/xvm/runtime/template/_native/reflect/xRTComponentTemplate.java | static-from-ctor | `INSTANCE`:38 | `<init>`:44 |
| xRTFileTemplate | org/xvm/runtime/template/_native/reflect/xRTFileTemplate.java | static-from-ctor | `INSTANCE`:53 | `<init>`:59 |
| xRTFileTemplate | org/xvm/runtime/template/_native/reflect/xRTFileTemplate.java | static-setup | `FILE_TEMPLATE_TYPE`:328, `LINK_MODULES_METHOD`:329 | `initNative`:65,66 |
| xRTFunction | org/xvm/runtime/template/_native/reflect/xRTFunction.java | static-from-ctor | `INSTANCE`:49 | `<init>`:55 |
| xRTFunction$FullyBoundHandle | org/xvm/runtime/template/_native/reflect/xRTFunction.java | static-setup | `NO_OP`:1016 | `initNative in xRTFunction`:66 |
| xRTFunction | org/xvm/runtime/template/_native/reflect/xRTFunction.java | static-setup | `NATIVE_NO_OP`:1584, `FUNCTION_ARRAY_TYPE`:1586, `EMPTY_FUNCTION_ARRAY`:1587, `TO_ARRAY`:1595 | `initNative`:63,64,68... |
| xRTMethod | org/xvm/runtime/template/_native/reflect/xRTMethod.java | static-from-ctor | `INSTANCE`:39 | `<init>`:45 |
| xRTMethodTemplate | org/xvm/runtime/template/_native/reflect/xRTMethodTemplate.java | static-from-ctor | `INSTANCE`:32 | `<init>`:38 |
| xRTModuleTemplate | org/xvm/runtime/template/_native/reflect/xRTModuleTemplate.java | static-from-ctor | `INSTANCE`:34 | `<init>`:40 |
| xRTModuleTemplate | org/xvm/runtime/template/_native/reflect/xRTModuleTemplate.java | static-setup | `MODULE_TEMPLATE_TYPE`:174 | `initNative`:48 |
| xRTPackageTemplate | org/xvm/runtime/template/_native/reflect/xRTPackageTemplate.java | static-from-ctor | `INSTANCE`:17 | `<init>`:23 |
| xRTPackageTemplate | org/xvm/runtime/template/_native/reflect/xRTPackageTemplate.java | static-setup | `PACKAGE_TEMPLATE_TYPE`:52 | `initNative`:31 |
| xRTProperty | org/xvm/runtime/template/_native/reflect/xRTProperty.java | static-from-ctor | `INSTANCE`:39 | `<init>`:45 |
| xRTProperty | org/xvm/runtime/template/_native/reflect/xRTProperty.java | static-setup | `EMPTY_PROPERTY_ARRAY`:390 | `initNative`:53 |
| xRTPropertyClassTemplate | org/xvm/runtime/template/_native/reflect/xRTPropertyClassTemplate.java | static-from-ctor | `INSTANCE`:33 | `<init>`:39 |
| xRTPropertyClassTemplate | org/xvm/runtime/template/_native/reflect/xRTPropertyClassTemplate.java | static-setup | `PROPERTY_CLASS_TEMPLATE_COMP`:275 | `initNative`:48 |
| xRTPropertyTemplate | org/xvm/runtime/template/_native/reflect/xRTPropertyTemplate.java | static-from-ctor | `INSTANCE`:30 | `<init>`:36 |
| xRTSignature | org/xvm/runtime/template/_native/reflect/xRTSignature.java | static-from-ctor | `INSTANCE`:37 | `<init>`:43 |
| xRTType | org/xvm/runtime/template/_native/reflect/xRTType.java | static-from-ctor | `INSTANCE`:79 | `<init>`:85 |
| xRTType | org/xvm/runtime/template/_native/reflect/xRTType.java | static-setup | `TYPE_ARRAY_TYPE`:1920, `EMPTY_TYPE_ARRAY`:1921, `PROP_CALCULATE`:1927, `PROP_HASHER`:1928 | `initNative`:93,94,95... |
| xRTTypeTemplate | org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java | static-from-ctor | `INSTANCE`:51 | `<init>`:57 |
| xRTTypeTemplate | org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java | static-setup | `TEMPLATE_ARRAY_TYPE`:581, `CREATE_COMPOSITION_METHOD`:860 | `initNative`:65,68 |
| xLocalClock | org/xvm/runtime/template/_native/temporal/xLocalClock.java | static-from-ctor | `INSTANCE`:39 | `<init>`:45 |
| xLocalClock | org/xvm/runtime/template/_native/temporal/xLocalClock.java | proven | `TIMER`:319 |  |
| xNanosTimer | org/xvm/runtime/template/_native/temporal/xNanosTimer.java | static-from-ctor | `INSTANCE`:40 | `<init>`:46 |
| xRTConnector | org/xvm/runtime/template/_native/web/xRTConnector.java | static-from-ctor | `INSTANCE`:69, `s_sAgent`:382 | `<init>`:75,76 |
| xRTServer | org/xvm/runtime/template/_native/web/xRTServer.java | static-from-ctor | `INSTANCE`:93 | `<init>`:99 |
| xRTServiceControl | org/xvm/runtime/template/_native/xRTServiceControl.java | static-from-ctor | `INSTANCE`:29 | `<init>`:35 |
| xRTServiceControl | org/xvm/runtime/template/_native/xRTServiceControl.java | static-setup | `SERVICE_STATUS`:141 | `initNative`:51 |
| xAtomic | org/xvm/runtime/template/annotations/xAtomic.java | static-from-ctor | `INSTANCE`:34 | `<init>`:40 |
| xAtomic | org/xvm/runtime/template/annotations/xAtomic.java | static-setup | `NUMBER_TEMPLATES`:249 | `initNative`:67 |
| xAtomicIntNumber | org/xvm/runtime/template/annotations/xAtomicIntNumber.java | static-from-ctor | `INSTANCE`:27 | `<init>`:33 |
| xFuture | org/xvm/runtime/template/annotations/xFuture.java | static-from-ctor | `INSTANCE`:42 | `<init>`:50 |
| xFuture | org/xvm/runtime/template/annotations/xFuture.java | static-setup | `TYPE`:43, `COMPLETION`:44 | `initNative`:61,62 |
| xInject | org/xvm/runtime/template/annotations/xInject.java | static-from-ctor | `INSTANCE`:34 | `<init>`:40 |
| BitBasedArray | org/xvm/runtime/template/collections/BitBasedArray.java | never-assigned | `INSTANCE`:12 |  |
| xArray | org/xvm/runtime/template/collections/xArray.java | static-from-ctor | `INSTANCE`:63 | `<init>`:70 |
| xArray | org/xvm/runtime/template/collections/xArray.java | static-setup | `MUTABILITY`:64, `OBJECT_ARRAY_CLZ`:1042, `STRING_ARRAY_CLZ`:1043, `BIT_ARRAY_CLZ`:1044, `BOOLEAN_ARRAY_CLZ`:1045, `BYTE_ARRAY_CLZ`:1046, `CHAR_ARRAY_CLZ`:1047, `CREATE_LIST_SET`:1049, `FILL_FROM_ITERABLE`:1050, `LIST_INDEX_OF`:1051, `CALCULATE_HASH`:1052, `ARRAY_TEMPLATES`:1054 | `initNative`:92,124,125... |
| xBitArray | org/xvm/runtime/template/collections/xBitArray.java | static-from-ctor | `INSTANCE`:31 | `<init>`:37 |
| xByteArray | org/xvm/runtime/template/collections/xByteArray.java | static-from-ctor | `INSTANCE`:32 | `<init>`:38 |
| xNibbleArray | org/xvm/runtime/template/collections/xNibbleArray.java | static-from-ctor | `INSTANCE`:24 | `<init>`:30 |
| xTuple | org/xvm/runtime/template/collections/xTuple.java | static-from-ctor | `INSTANCE`:44, `INCEPTION_CLASS`:45 | `<init>`:52,53 |
| xTuple | org/xvm/runtime/template/collections/xTuple.java | static-setup | `H_VOID`:46 | `initNative`:60 |
| xListMap | org/xvm/runtime/template/maps/xListMap.java | static-from-ctor | `INSTANCE`:30 | `<init>`:36 |
| xBFloat16 | org/xvm/runtime/template/numbers/xBFloat16.java | static-from-ctor | `INSTANCE`:16 | `<init>`:22 |
| xBit | org/xvm/runtime/template/numbers/xBit.java | static-setup | `ZERO`:195, `ONE`:196 | `initNative`:37,38 |
| xCheckedInt16 | org/xvm/runtime/template/numbers/xCheckedInt16.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xCheckedInt32 | org/xvm/runtime/template/numbers/xCheckedInt32.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xCheckedInt64 | org/xvm/runtime/template/numbers/xCheckedInt64.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xCheckedInt8 | org/xvm/runtime/template/numbers/xCheckedInt8.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xCheckedUInt16 | org/xvm/runtime/template/numbers/xCheckedUInt16.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xCheckedUInt32 | org/xvm/runtime/template/numbers/xCheckedUInt32.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xCheckedUInt64 | org/xvm/runtime/template/numbers/xCheckedUInt64.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xCheckedUInt8 | org/xvm/runtime/template/numbers/xCheckedUInt8.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xDec128 | org/xvm/runtime/template/numbers/xDec128.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xDec32 | org/xvm/runtime/template/numbers/xDec32.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xDec64 | org/xvm/runtime/template/numbers/xDec64.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xFPLiteral | org/xvm/runtime/template/numbers/xFPLiteral.java | static-from-ctor | `INSTANCE`:33 | `<init>`:39 |
| xFloat16 | org/xvm/runtime/template/numbers/xFloat16.java | static-from-ctor | `INSTANCE`:16 | `<init>`:22 |
| xFloat32 | org/xvm/runtime/template/numbers/xFloat32.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xFloat64 | org/xvm/runtime/template/numbers/xFloat64.java | static-from-ctor | `INSTANCE`:16 | `<init>`:22 |
| xInt128 | org/xvm/runtime/template/numbers/xInt128.java | static-from-ctor | `INSTANCE`:11 | `<init>`:17 |
| xInt16 | org/xvm/runtime/template/numbers/xInt16.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xInt32 | org/xvm/runtime/template/numbers/xInt32.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xInt64 | org/xvm/runtime/template/numbers/xInt64.java | static-from-ctor | `INSTANCE`:16 | `<init>`:22 |
| xInt8 | org/xvm/runtime/template/numbers/xInt8.java | static-from-ctor | `INSTANCE`:15 | `<init>`:21 |
| xIntLiteral | org/xvm/runtime/template/numbers/xIntLiteral.java | static-from-ctor | `INSTANCE`:41 | `<init>`:47 |
| xIntN | org/xvm/runtime/template/numbers/xIntN.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xNibble | org/xvm/runtime/template/numbers/xNibble.java | static-from-ctor | `INSTANCE`:23 | `<init>`:29 |
| xUInt128 | org/xvm/runtime/template/numbers/xUInt128.java | static-from-ctor | `INSTANCE`:10 | `<init>`:16 |
| xUInt16 | org/xvm/runtime/template/numbers/xUInt16.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xUInt32 | org/xvm/runtime/template/numbers/xUInt32.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xUInt64 | org/xvm/runtime/template/numbers/xUInt64.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xUInt8 | org/xvm/runtime/template/numbers/xUInt8.java | static-from-ctor | `INSTANCE`:19 | `<init>`:25 |
| xUIntN | org/xvm/runtime/template/numbers/xUIntN.java | static-from-ctor | `INSTANCE`:12 | `<init>`:18 |
| xClass | org/xvm/runtime/template/reflect/xClass.java | static-from-ctor | `INSTANCE`:53 | `<init>`:59 |
| xClass | org/xvm/runtime/template/reflect/xClass.java | static-setup | `CLASS_ARRAY_TYPE`:497 | `initNative`:67 |
| xClassTemplate | org/xvm/runtime/template/reflect/xClassTemplate.java | static-from-ctor | `INSTANCE`:21 | `<init>`:27 |
| xEnumValue | org/xvm/runtime/template/reflect/xEnumValue.java | static-from-ctor | `INSTANCE`:20 | `<init>`:26 |
| xEnumeration | org/xvm/runtime/template/reflect/xEnumeration.java | static-from-ctor | `INSTANCE`:31 | `<init>`:37 |
| xInjector | org/xvm/runtime/template/reflect/xInjector.java | static-from-ctor | `INSTANCE`:23 | `<init>`:29 |
| xModule | org/xvm/runtime/template/reflect/xModule.java | static-from-ctor | `INSTANCE`:48 | `<init>`:54 |
| xModule | org/xvm/runtime/template/reflect/xModule.java | static-setup | `MODULE_ARRAY_TYPE`:343, `EMPTY_MODULE_ARRAY`:344, `VERSION_DEFAULT`:345 | `initNative`:63,64,66 |
| xPackage | org/xvm/runtime/template/reflect/xPackage.java | static-from-ctor | `INSTANCE`:47 | `<init>`:53 |
| xPackage | org/xvm/runtime/template/reflect/xPackage.java | static-setup | `LIST_MAP_TYPE`:310, `LIST_MAP_TEMPLATE`:311 | `initNative`:61,63 |
| xRef | org/xvm/runtime/template/reflect/xRef.java | static-from-ctor | `INSTANCE`:62, `INCEPTION_CLASS`:63 | `<init>`:69,70 |
| xRef | org/xvm/runtime/template/reflect/xRef.java | static-setup | `s_sigGet`:1181 | `initNative`:100 |
| xVar | org/xvm/runtime/template/reflect/xVar.java | static-from-ctor | `INSTANCE`:30, `INCEPTION_CLASS`:31 | `<init>`:37,38 |
| xVar | org/xvm/runtime/template/reflect/xVar.java | static-setup | `s_sigSet`:257 | `initNative`:45 |
| xChar | org/xvm/runtime/template/text/xChar.java | static-from-ctor | `INSTANCE`:41 | `<init>`:47 |
| xRegEx | org/xvm/runtime/template/text/xRegEx.java | static-from-ctor | `INSTANCE`:43 | `<init>`:49 |
| xString | org/xvm/runtime/template/text/xString.java | static-from-ctor | `INSTANCE`:46 | `<init>`:52 |
| xString | org/xvm/runtime/template/text/xString.java | static-setup | `EMPTY_STRING`:439, `EMPTY_ARRAY`:440, `ZERO`:441, `ONE`:442, `METHOD_APPEND_TO`:444 | `initNative`:63,64,65... |
| xBoolean | org/xvm/runtime/template/xBoolean.java | static-setup | `TRUE`:18, `FALSE`:19 | `initNative`:30,31 |
| xConst | org/xvm/runtime/template/xConst.java | static-from-ctor | `INSTANCE`:58 | `<init>`:64 |
| xConst | org/xvm/runtime/template/xConst.java | static-setup | `FN_ESTIMATE_LENGTH`:793, `FN_APPEND_TO`:794, `FN_FREEZE`:795, `RANGE_CONSTRUCT`:796, `NIBBLE_CONSTRUCT`:797, `TIME_CONSTRUCT`:798, `DATE_CONSTRUCT`:799, `TIMEOFDAY_CONSTRUCT`:800, `TIMEZONE_CONSTRUCT`:801, `DURATION_CONSTRUCT`:802, `VERSION_CONSTRUCT`:803, `PATH_CONSTRUCT`:804, `HASH_SIG`:806 | `initNative`:90,91,92... |
| xEnum | org/xvm/runtime/template/xEnum.java | static-from-ctor | `INSTANCE`:37 | `<init>`:43 |
| xEnum | org/xvm/runtime/template/xEnum.java | static-setup | `RANGE_TEMPLATE`:441, `RANGE_CTOR`:442 | `initNative`:52,53 |
| xException | org/xvm/runtime/template/xException.java | static-from-ctor | `INSTANCE`:30 | `<init>`:36 |
| xException | org/xvm/runtime/template/xException.java | static-setup | `s_clzDeadlock`:351, `s_clzException`:352, `s_clzIllegalArgument`:353, `s_clzIllegalState`:354, `s_clzInvalidType`:355, `s_clzNotImplemented`:356, `s_clzOutOfBounds`:357, `s_clzOutOfMemory`:358, `s_clzReadOnly`:359, `s_clzSizeLimited`:360, `s_clzStackOverflow`:361, `s_clzTimedOut`:362, `s_clzTypeMismatch`:363, `s_clzUnsupported`:364, `s_clzDivisionByZero`:365, `s_clzPathException`:366, `s_clzFileNotFoundException`:367, `s_clzAccessDeniedException`:368, `s_clzFileAlreadyExistsException`:369, `s_clzIOException`:370, `s_clzIOIllegalUTF`:371 | `initNative`:44,45,46... |
| xNullable | org/xvm/runtime/template/xNullable.java | static-setup | `NULL`:14 | `initNative`:25 |
| xObject | org/xvm/runtime/template/xObject.java | static-from-ctor | `INSTANCE`:14 | `<init>`:21 |
| xObject | org/xvm/runtime/template/xObject.java | static-setup | `CLASS`:15 | `initNative`:28 |
| xOrdered | org/xvm/runtime/template/xOrdered.java | static-setup | `LESSER`:16, `EQUAL`:17, `GREATER`:18 | `initNative`:29,30,31 |
| xService | org/xvm/runtime/template/xService.java | static-from-ctor | `INSTANCE`:47, `INCEPTION_CLASS`:48 | `<init>`:54,55 |
| xService | org/xvm/runtime/template/xService.java | static-setup | `SYNCHRONICITY`:566, `s_setAtomicProperties`:571, `REMAINING_TIME`:576 | `initNative`:68,76,80 |

## Category 4: final but mutable

`static final` arrays and collections. The analyzer classified every use of each field: element writes and mutator calls (`add`, `put`, `remove`, `clear`, `set`, `sort`, views such as `values()`, ...) outside the static initializer count as mutation; passing the field to a method, returning it or storing it elsewhere counts as an escape.

- 26 are empty arrays (`NO_*` and similar); they cannot be changed and are not listed.
- 2 are already immutable (`Map.of`, `Map.ofEntries`).

### Mutated after initialization

| Class | Field | Location | Mutation | Assessment |
|---|---|---|---|---|
| TypeConstant | `s_setRecursions` | org/xvm/asm/constants/TypeConstant.java:8656 | `calculateRelation`:6189 call:add | plain `HashSet` written during type checks; a data race under parallel compiles (finding 4). Use `ConcurrentHashMap.newKeySet()`. Read in code. |
| NativeNames | `reservedMethodName` | org/xvm/javajit/NativeNames.java:21 | `reservedJitNames`:213 call:values | false positive: `values()` is only copied into a new `HashSet` (`NativeNames.java:213`, read in code). Never mutated after the static block; could be `Map.of`/`Map.ofEntries`. |
| xArray | `CONSTRUCTORS` | org/xvm/runtime/template/collections/xArray.java:1038 | `initNative`:103 elemWrite; `initNative`:106 elemWrite; `initNative`:112 elemWrite | filled in `initNative()`, so once per `NativeContainer`; same issue as the category 1 statics. |
| PackedInteger | `CACHE` | org/xvm/util/PackedInteger.java:967 | `valueOf`:101 elemWrite | racy cache of `PackedInteger`s; benign only once `PackedInteger` has final fields (finding 3). Read in code. |
| ShallowSizeOf | `SIZE_BY_CLASS` | org/xvm/util/ShallowSizeOf.java:30 | `instanceOf`:90 call:put | `ConcurrentHashMap` cache; intended. |

### Never mutated after initialization

Every use is a read. These can become `List.of`/`Map.of`/`Set.of` (or stay arrays if indexing speed matters and they stay private). `enum` `values()` copies are listed because they are arrays a caller could write into if they leaked; none do.

| Class | Field | Location | Type | Initializer |
|---|---|---|---|---|
| Assignment | `ASSIGNMENTS` | org/xvm/asm/Assignment.java:305 | `Assignment[]` | Assignment.values() |
| Assignment | `BY_FLAGS` | org/xvm/asm/Assignment.java:325 | `Assignment[]` | array creation |
| Component$Format | `FORMATS` | org/xvm/asm/Component.java:2590 | `Component.Format[]` | Format.values() |
| Component$Composition | `COMPOSITIONS` | org/xvm/asm/Component.java:2671 | `Component.Composition[]` | Composition.values() |
| Constant$Format | `FORMATS` | org/xvm/asm/Constant.java:937 | `Constant.Format[]` | Format.values() |
| Constants$Access | `VALUES` | org/xvm/asm/Constants.java:188 | `Constants.Access[]` | Access.values() |
| ModuleStructure$ModuleType | `MODULE_TYPES` | org/xvm/asm/ModuleStructure.java:1013 | `ModuleStructure.ModuleType[]` | ModuleType.values() |
| Version | `TEXT` | org/xvm/asm/Version.java:613 | `String[]` | array literal |
| UnionTypeConstant | `SpecialFunkies` | org/xvm/asm/constants/UnionTypeConstant.java:700 | `HashSet<String>` | new HashSet |
| Compiler$Stage | `STAGES` | org/xvm/compiler/Compiler.java:551 | `Compiler.Stage[]` | Stage.values() |
| Token$Id | `IDs` | org/xvm/compiler/Token.java:713 | `Token.Id[]` | Id.values() |
| Token$Id | `KEYWORDS` | org/xvm/compiler/Token.java:719 | `Map<String,Token.Id>` | new HashMap |
| Token$Id | `ALL_KEYWORDS` | org/xvm/compiler/Token.java:723 | `Map<String,Token.Id>` | new HashMap |
| Token$Id | `PREFIXES` | org/xvm/compiler/Token.java:728 | `Map<String,Token.Id>` | new HashMap |
| Expression$TypeFit | `BY_ORDINAL` | org/xvm/compiler/ast/Expression.java:3040 | `Expression.TypeFit[]` | TypeFit.values() |
| Expression$TypeFit | `BY_FLAGS` | org/xvm/compiler/ast/Expression.java:3045 | `Expression.TypeFit[]` | array creation |
| ForEachStatement$Plan | `BY_ORDINAL` | org/xvm/compiler/ast/ForEachStatement.java:1293 | `ForEachStatement.Plan[]` | Plan.values() |
| TypeCompositionStatement$Zone | `ZONES` | org/xvm/compiler/ast/TypeCompositionStatement.java:3116 | `TypeCompositionStatement.Zone[]` | Zone.values() |
| xRTNameService | `RECORD_TYPES` | org/xvm/runtime/template/_native/net/xRTNameService.java:285 | `String[]` | array creation |
| ListSet | `INDEX_SIZE` | org/xvm/util/ListSet.java:1166 | `int[]` | array creation |

### Escapes to other code

- 69 compiler `CHILD_FIELDS`-style arrays (`java.lang.reflect.Field[]`, one per node class, for example `org/xvm/compiler/ast/NameExpression.java:3243`) are returned by `getChildFields()`. Every caller only reads them (`AstNode.java:146`, :270, :1874 and `ChildIteratorImpl`, read in code). They are never mutated; `List.of` would make that explicit.

| Class | Field | Location | Escapes through | Assessment |
|---|---|---|---|---|
| BuildInfo | `BUILD_INFO` | org/xvm/asm/BuildInfo.java:17 | `getXdkVersion`:50 call:getProperty; `getXvmVersionMajor`:58 call:getProperty; `getXvmVersionMinor`:66 call:getProperty | `Properties` (a `Hashtable`) read with `getProperty` only; read-only in practice |
| ConstantPool | `NO_TYPES` | org/xvm/asm/ConstantPool.java:3751 | `sigValidator`:2364 arg:ensureSignatureConstant; `sigValidator`:2364 arg:ensureSignatureConstant; `adoptParameters`:81 assign-rhs | alias of the empty `TypeConstant.NO_TYPES` |
| LambdaExpression | `NO_NAMES` | org/xvm/compiler/ast/LambdaExpression.java:1446 | `getParamNames`:168 return; `getImplicitType`:340 CONDITIONAL_EXPRESSION; `calculateTypeFitImpl`:400 CONDITIONAL_EXPRESSION | alias of the empty `Handy.NO_ARGS` |
| Builder | `CDs_Int` | org/xvm/javajit/Builder.java:2865 | `loadConstant`:457 arg:new MultiSlot; `getXvmPrimitiveClasses`:151 CASE | callers not checked (inferred read-only) |
| Builder | `CDs_Long` | org/xvm/javajit/Builder.java:2866 | `loadConstant`:463 arg:new MultiSlot; `getXvmPrimitiveClasses`:152 CASE | callers not checked (inferred read-only) |
| Builder | `CDs_LongLong` | org/xvm/javajit/Builder.java:2867 | `loadConstant`:430 arg:new MultiSlot; `loadConstant`:431 arg:new MultiSlot; `loadConstant`:470 arg:new MultiSlot | callers not checked (inferred read-only) |
| JitConnector | `CLASS_DUMP_LIST` | org/xvm/javajit/JitConnector.java:236 | `invoke0Impl`:91 arg:Arrays.asList | callers not checked (inferred read-only) |
| CommonBuilder | `NO_JIT_LIST` | org/xvm/javajit/builders/CommonBuilder.java:4572 | `generateCode`:4535 arg:Arrays.stream | callers not checked (inferred read-only) |
| Frame | `WAIT_FOR_FUTURE` | org/xvm/runtime/Frame.java:2767 | `createWaitFrame`:252 arg:createNativeFrame; `createWaitFrame`:274 arg:createNativeFrame; `createWaitFrame`:302 arg:createNativeFrame | callers not checked (inferred read-only) |
| Frame | `WAIT_FOR_IO` | org/xvm/runtime/Frame.java:2810 | `createWaitIOFrame`:332 arg:createNativeFrame | callers not checked (inferred read-only) |
| Utils | `WAIT_FOR_RELIEF` | org/xvm/runtime/Utils.java:1794 | `initConstants`:772 arg:frame.createNativeFrame | callers not checked (inferred read-only) |
| DebugProtocol | `COMMANDS` | org/xvm/runtime/debug/DebugProtocol.java:39 | `command`:302 arg:message | callers not checked (inferred read-only) |
| DebugProtocol | `EVENTS` | org/xvm/runtime/debug/DebugProtocol.java:41 | `event`:309 arg:message | callers not checked (inferred read-only) |
| xRTDelegate | `ELEMENT_TYPE` | org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java:941 | `initNative`:118 arg:markNativeMethod | callers not checked (inferred read-only) |
| xBasicHashCollector | `TYPE_HASH_COLLECTOR` | org/xvm/runtime/template/_native/collections/xBasicHashCollector.java:28 | `initNative`:40 arg:markNativeMethod; `initNative`:41 arg:markNativeMethod | callers not checked (inferred read-only) |
| xOSFile | `READ_ONLY` | org/xvm/runtime/template/_native/fs/xOSFile.java:499 | `invokeOpen`:421 assign-rhs | callers not checked (inferred read-only) |
| xOSFile | `WRITE_ONLY` | org/xvm/runtime/template/_native/fs/xOSFile.java:500 | `invokeOpen`:394 assign-rhs | callers not checked (inferred read-only) |
| xOSFile | `READ_WRITE` | org/xvm/runtime/template/_native/fs/xOSFile.java:501 | `invokeOpen`:398 assign-rhs | callers not checked (inferred read-only) |
| xRTCompiler | `STACK_2` | org/xvm/runtime/template/_native/lang/src/xRTCompiler.java:496 | `doCompile`:165 arg:chain.invoke | callers not checked (inferred read-only) |
| xArray | `ELEMENT_TYPE` | org/xvm/runtime/template/collections/xArray.java:1040 | `initNative`:150 arg:markNativeMethod | callers not checked (inferred read-only) |
| LongLong | `ZEROx2` | org/xvm/runtime/template/numbers/LongLong.java:621 | `divrem`:327 return; `divremUnsigned`:375 return | callers not checked (inferred read-only) |
| LongLong | `OVERFLOWx2` | org/xvm/runtime/template/numbers/LongLong.java:622 | `divrem`:301 return; `divremUnsigned`:353 return | callers not checked (inferred read-only) |
| Launcher | `DEBUG_PREFIXES` | org/xvm/tool/Launcher.java:1026 | `stripDebugPrefix`:361 arg:Arrays.stream | callers not checked (inferred read-only) |
| ListMap | `EMPTY` | org/xvm/util/ListMap.java:157 | `empty`:58 TYPE_CAST | shared empty `ListMap`, returned by `ListMap.empty()`; mutable if a caller writes to it (inferred) |
| PackedInteger | `xB_FACTORS` | org/xvm/util/PackedInteger.java:1138 | `getPackedInteger`:365 local-or-field-init; `eatIntegerLiteral`:1653 local-or-field-init | callers not checked (inferred read-only) |
| PackedInteger | `xI_FACTORS` | org/xvm/util/PackedInteger.java:1142 | `getPackedInteger`:367 assign-rhs; `eatIntegerLiteral`:1666 assign-rhs | callers not checked (inferred read-only) |

Non-final static arrays are in category 1: `ClassTemplate.VOID`, `THIS`, `OBJECT`, `INT`, `STRING`, `BOOLEAN`, `BYTES` (`org/xvm/runtime/ClassTemplate.java:2428`-2434) and `xRTClassTemplate.NO_TEMPLATES` (`org/xvm/runtime/template/_native/reflect/xRTClassTemplate.java:573`) are public, non-final and never reassigned (proven), and their contents are never written (inferred).

Shared instance arrays and collections were not inventoried one by one: `org.xvm.asm`, `org.xvm.asm.constants` and `org.xvm.runtime` have 142 `final` instance fields of array or collection type. Most of them are meant to change (child maps, caches, operand stacks); which of them are constant after construction is left to the parallel-compiler and parallel-runtime analyses.

## Fields that are not candidates

- **Compile-stage state (197).** Compiler AST fields written in `validate*`, `resolveNames`, `generate*` and similar stage methods. They belong to one compilation's tree and change as the stages run; whether a given one is set once was not checked.
- **Mutable (341 instance, 1 static).** At least one write in an ordinary method or a counter update; per-package counts are in the table above. Shared-structure fields the parallel analyses need, read in code: `Constant.m_iPos` (`org/xvm/asm/Constant.java:980`, labeled "setter" above) is renumbered by `ConstantPool` on registration and in `optimize()` (`org/xvm/asm/ConstantPool.java:211`, :3501, :3687), so it is not effectively final; `Constant.m_cRefs` (:986) counts references during registration; `Constant.m_iHash` (:975, labeled "lazy") is a racy int hash cache, benign in the way `String.hash` is; `SignatureConstant.m_sigPrev`/`m_refSigPrev`/`m_nCmpPrev` (`org/xvm/asm/constants/SignatureConstant.java:949`-959) are a comparison cache guarded by a `StampedLock`; `Op.m_lStruct` (`org/xvm/asm/Op.java:2049`) holds reachability bits rewritten during dead-code elimination.
- **Volatile (51, one static) and reflective (1).** Mutable on purpose; `Lazy.Bound.value` is written through a `VarHandle`, and the `TypeConstant` fields named above through field updaters (which require non-final volatile fields).
- **Never assigned (25, four static).** `org/xvm/javajit/Ctx.java:47`-64 (`i0`...`oN`, 18 fields) carry extra return values and are written by JIT-generated bytecode (`putfield`, for example `org/xvm/javajit/builders/EnumBuilderSupport.java:313`, read in code), so they must stay non-final. `Constant.m_oValue` (`org/xvm/asm/Constant.java:991`), `InvocationExpression.m_fTupleArg` (`org/xvm/compiler/ast/InvocationExpression.java:3027`), `TemplateExpression.type` (`org/xvm/compiler/ast/TemplateExpression.java:292`) and four `INSTANCE` statics in `org/xvm/runtime/template` (`_native/collections/arrays/LongBasedBitView.java:27`, `LongDelegate.java:18`, `LongLongDelegate.java:31`, `collections/BitBasedArray.java:12`) are never written anywhere and read as their default; they can be deleted or made `final` with the default.

## Recommended order of work

Keep every "add `final`" change in its own commit, separate from any redesign, so a reviewer can check it by compiling.

1. **Shared-structure races that need no redesign.** `TypeConstant.s_setRecursions` (concurrent set), `JumpVal`/`JumpVal_N` jump tables (one volatile immutable object), `PackedInteger` and `Decimal128` (final fields via the constructors). Small, behavior-preserving, and each closes a real gap for parallel use.
2. **Mechanical commit: the 183 proven fields.** Exactly the category 2 list, nothing else. Optionally split runtime/asm (shared objects) from `compiler.ast` (local style).
3. **Mechanical-plus commit: the 150 definite-assignment fields.** Add the missing `-1`/`null` assignments, then `final`. Constants and ops first.
4. **Lazy caches on low-cardinality shared objects.** The `ConstantPool` block (185 fields), `NativeContainer`, `Container`, `ClassTemplate`, `ClassComposition`, `ServiceContext`: convert to `Lazy.Bound` (with `reset()` where invalidated). Measure footprint before doing the same on `Constant`, `PropertyInfo` and handle classes.
5. **Redesigns, each with its own plan:** `Constant`/`Component` two-phase deserialization and registration; the `markReadOnly` builder/immutable split; `clone()` to copy constructors (`Component`, then `ObjectHandle`); runtime statics into a per-container registry (in `doc/parallel-runtime.md`).

Until step 5, the thread-safety argument for library pools, structures and templates has to come from publishing them through a happens-before edge after they are complete, not from `final`.

## Reproducing

The scripts lived in a scratch directory and are not checked in. To redo the analysis:

1. `git worktree add --detach <dir> <commit>`.
2. Run a small `JavacTask` program over `find javatools/src/main/java javatools_utils/src/main/java -name '*.java'` with the version-catalog jars on the class path (jline, commons-cli, gson, acme4j, bouncycastle, slf4j, jetbrains annotations), calling `parse()` and `analyze()` and walking the trees as described under [Method](#method).
3. Insert `final ` at each candidate's type position, compile with `javac -proc:none --release 25 -Xmaxerrs 100000 -XDshould-stop.ifError=FLOW`, revert the fields named by flow errors, and repeat until it compiles.
4. Confirm with `./gradlew :javatools:compileJava :javatools_utils:compileJava --offline -q` and the `compileTestJava` tasks, then `git worktree remove --force <dir>` and `git worktree prune`.
