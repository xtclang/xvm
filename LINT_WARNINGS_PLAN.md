# Java Lint Warnings Elimination Plan

Compiled with `-Xlint:all` via `-Porg.xtclang.java.lint=true -Porg.xtclang.java.warningsAsErrors=false -Porg.xtclang.java.maxWarnings=1000`.

**Current: 307 warnings** (11 in `javatools_utils`, 296 in `javatools`)
**Original: 357 warnings** — 50 fixed so far

| Category | Original | Current | Tier | Difficulty |
|----------|----------|---------|------|------------|
| `[fallthrough]` | 102 | 102 | 4 | Involved |
| `[unchecked]` | 75 | 75 | 3 | Moderate |
| `[this-escape]` | 68 | 68 | 5 | Hard |
| `[rawtypes]` | 68 | 62 | 3 | Moderate |
| `[try]` | 18 | **0** | 1 | Trivial |
| `[cast]` | 13 | **0** | 1 | Trivial |
| `[serial]` | 8 | **0** | 2 | Easy |
| `[overrides]` | 3 | 3 | 3 | Moderate |
| `[static]` | 2 | **0** | 1 | Trivial |

---

## Tier 1: TRIVIAL -- DONE (33 warnings fixed)

Fixed in commit `b8d36c5ca`:
- `[static]` (2): Changed instance-qualified static calls to class-qualified in `xLocalClock.java`
- `[cast]` (13): Removed redundant casts across 8 files
- `[try]` (18): Added `@SuppressWarnings("try")` for intentional scoped try-with-resources

---

## Tier 2: EASY -- DONE (11 warnings fixed)

### `[serial]` Missing serialVersionUID / non-serializable field (8 warnings → 0)

All `[serial]` warnings came from classes that inherit `Serializable` through their superclass
hierarchy but are never actually serialized. The root cause is that `java.lang.Throwable`
implements `Serializable` (a JDK 1.1 design decision for RMI), so every `Exception`,
`RuntimeException`, and `Error` subclass is automatically `Serializable` whether intended or not.

**Exception classes** — suppressed with `@SuppressWarnings("serial")`:
- `SegFault` — extends `Error`
- `CompilerException` — extends `LauncherException` → `RuntimeException`
- `LauncherException` — extends `RuntimeException`
- `WrapperException` — extends `Exception` (inner class of `ExceptionHandle`)
- `RangeException` — extends `ArithmeticException` (inner class of `Decimal`; also carried a
  non-serializable `Decimal` field that would have thrown `NotSerializableException` at runtime)

**Collection classes** — suppressed with `@SuppressWarnings("serial")`:
- `ROEntry` — extends `AbstractMap.SimpleEntry` (inner class of `MapConstant`)

**IdentityArrayList** — rewritten with composition (3 `[rawtypes]` warnings also eliminated):
- Changed from `extends ArrayList<E>` to `extends AbstractList<E>` with internal `ArrayList<E>`
  delegate. `AbstractList` is not `Serializable`, so no suppression needed. Previously, the class
  inherited `Serializable`, `Cloneable`, and `RandomAccess` from `ArrayList`, none of which were
  used at any call site. Only used in `CompositeComponent.java`, stored as `List<Component>`.

**Existing `serialVersionUID` declarations investigated (2 found, both must stay):**
- `NonBlockingHashMapLong` and `NonBlockingHashMap` in `javatools_backend` both have active
  `writeObject()`/`readObject()` implementations — genuine serialization.

---

## Tier 3: MODERATE (need understanding, some refactoring) -- 143 warnings remaining

### 3a. `[rawtypes]` Raw generic type usage (65 warnings)

Add proper type parameters. Hotspots:

| File | Count | Key raw types |
|------|-------|---------------|
| `AstNode.java` | 14 | `List`, `Map`, `Collection`, `Iterator`, `ListIterator`, `ArrayList`, `Class` |
| `ServiceContext.java` | 13 | `CompletableFuture`, `Response`, `Enum`, `EnumMap`, `WeakReference` |
| `Fiber.java` | 8 | `CompletableFuture` |
| `CaseManager.java` | 4 | `Comparable` |
| `Frame.java` | 4 | `CompletableFuture` |
| `xOSFile.java` | 4 | `CompletableFuture` |
| `ObjectHandle.java` | 2 | `CompletableFuture` |
| `NativeTypeSystem.java` | 2 | `Class` |
| `TypeInfo.java` | 2 | `Entry` |
| `SwitchAST.java` | 2 | `Iterator` |
| `JumpVal_N.java` | 2 | `Map`, `List` |
| `xFuture.java` | 2 | `CompletableFuture` |
| 8 more files | 1 each | Various |

**Strategy:** `CompletableFuture` raw usage (28 warnings across 6 files) is the biggest cluster.
Parameterize as `CompletableFuture<ObjectHandle>` starting from Frame, then Fiber, then ServiceContext.

### 3b. `[unchecked]` Unchecked casts and conversions (75 warnings)

Many overlap with `rawtypes`. Fix together per-file.

| File | Count | Key patterns |
|------|-------|-------------|
| `ServiceContext.java` | 22 | `Response` constructor, `assignFutureResult`, `createWaitFrame`, raw `CompletableFuture.complete()` |
| `Fiber.java` | 5 | `CompletableFuture` casts |
| `TransientThreadLocal.java` | 4 | `(T) map.get(this)` -- unavoidable, suppress |
| `Context.java` | 4 | `Map.putAll` with raw maps |
| `LambdaExpression.java` | 4 | raw `Stream.allMatch()` |
| `TypeInfo.java` | 4 | `entrySet().toArray(Entry[])` with raw Entry |
| `AstNode.java` | 3 | generic child replacement utility |
| `ListMap.java` | **0** | **Rewritten:** stored `ArrayList<Entry>` instead of `ArrayList<SimpleEntry>`, eliminated `EMPTY_ARRAY_LIST` sentinel, raw casts, and assert-only unmodifiable bug |
| `TypeCompositionStatement.java` | 3 | unchecked casts to generic types |
| 14 more files | 1-2 each | Various |

### 3c. `[overrides]` equals without hashCode (3 warnings)

| File | Line | Class |
|------|------|-------|
| `Register.java` | 16 | `Register` |
| `Register.java` | 591 | `ShadowRegister` |
| `ChildInfo.java` | 18 | `ChildInfo` |

**Strategy:** Implement `hashCode()` consistent with the `equals()` contract.

---

## Tier 4: INVOLVED (needs careful control-flow review) -- 102 warnings remaining

### 4. `[fallthrough]` Switch case fall-through

Each must be audited: intentional fall-through gets `// fall through` comment; accidental gets `break;`.

| File | Count |
|------|-------|
| `ClassStructure.java` | 19 |
| `Parser.java` | 9 |
| `NameResolver.java` | 7 |
| `TerminalTypeConstant.java` | 7 |
| `Lexer.java` | 6 |
| `ClassTemplate.java` | 5 |
| `TypeCompositionStatement.java` | 5 |
| `CondOpExpression.java` | 3 |
| `Decimal.java` | 3 |
| `Expression.java` | 3 |
| `InvocationExpression.java` | 3 |
| `NameExpression.java` | 3 |
| `ServiceContext.java` | 3 |
| `Utils.java` | 3 |
| `JumpVal_N.java` | 2 |
| `xRTTypeTemplate.java` | 2 |
| `xTerminalConsole.java` | 2 |
| 15 more files | 1 each |

---

## Tier 5: HARD (architectural, risk of subtle breakage) -- 68 warnings remaining

### 5. `[this-escape]` Constructor this-escape

Constructor calls an overridable method before `this` is fully initialized.

**Common patterns:**
- **Op subclasses** (OpGeneral, OpCondJump, OpIndex, OpTest, OpInPlace, OpPropInPlace, OpVar):
  Constructors call `readOp()` during deserialization. Fix by making `readOp()` final, or using
  a static factory + private constructor.
- **AST expression wrappers** (TraceExpression, SyntheticExpression, UnpackExpression, PackExpression,
  ToIntExpression, ConvertExpression): Constructors call `adopt()`. Fix by making adopt final.
- **Compiler front-end** (Parser, Lexer): Constructor calls virtual methods.
- **Runtime** (Container, NativeContainer, ClassTemplate): Inheritance chains with init logic.

| File | Count |
|------|-------|
| `OpCondJump.java` | 4 |
| `OpTest.java` | 4 |
| `Parser.java` | 4 |
| `FileStructure.java` | 3 |
| `OpGeneral.java` | 3 |
| `OpIndex.java` | 3 |
| `OpInPlace.java` | 3 |
| `OpPropInPlace.java` | 3 |
| `PackedInteger.java` | 3 |
| `BuildContext.java` | 2 |
| `MethodDeclarationStatement.java` | 2 |
| `NativeContainer.java` | 2 |
| `TypeCompositionStatement.java` | 2 |
| `xRef.java` | 2 |
| 20 more files | 1 each |

---

## Recommended Execution Order

1. **Tier 1** -- DONE (33 warnings)
2. **Tier 2** -- DONE (11 warnings: 8 serial + 3 rawtypes from IdentityArrayList)
3. **Tier 4** (`fallthrough`) -- Single-file, self-contained audits; largest category
4. **Tier 3** (`rawtypes` + `unchecked` + `overrides`) -- Fix together per-file
5. **Tier 5** (`this-escape`) -- Highest risk, most architectural judgment needed

## How to Reproduce

```bash
./gradlew clean
./gradlew javatools_utils:compileJava javatools:compileJava \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=1000
```
