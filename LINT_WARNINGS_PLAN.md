# Java Lint Warnings Elimination Plan

Compiled with `-Xlint:all` via `-Porg.xtclang.java.lint=true -Porg.xtclang.java.warningsAsErrors=false -Porg.xtclang.java.maxWarnings=1000`.

**Total: 357 warnings** (21 in `javatools_utils`, 336 in `javatools`)

| Category | Count | Tier | Difficulty |
|----------|-------|------|------------|
| `[fallthrough]` | 102 | 4 | Involved |
| `[unchecked]` | 75 | 3 | Moderate |
| `[this-escape]` | 68 | 5 | Hard |
| `[rawtypes]` | 68 | 3 | Moderate |
| `[try]` | 18 | 1 | Trivial |
| `[cast]` | 13 | 1 | Trivial |
| `[serial]` | 8 | 2 | Easy |
| `[overrides]` | 3 | 3 | Moderate |
| `[static]` | 2 | 1 | Trivial |

---

## Tier 1: TRIVIAL (mechanical, zero semantic risk) -- 33 warnings

### 1a. `[static]` Static method qualified by expression (2 warnings)

Change `instance.staticMethod()` to `ClassName.staticMethod()`.

| File | Line | Detail |
|------|------|--------|
| `xLocalClock.java` | 179 | static method should be qualified by `xInt64` |
| `xLocalClock.java` | 183 | static method should be qualified by `xInt64` |

### 1b. `[cast]` Redundant casts (13 warnings)

Remove the cast -- the compiler already knows the type.

| File | Line | Cast to |
|------|------|---------|
| `ClassComposition.java` | 312 | `IdentityConstant` |
| `ClassComposition.java` | 354 | `PropertyConstant` |
| `ClassComposition.java` | 396 | `PropertyConstant` |
| `ClassComposition.java` | 541 | `T` |
| `PropertyComposition.java` | 206 | `IdentityConstant` |
| `PropertyComposition.java` | 231 | `PropertyConstant` |
| `PropertyComposition.java` | 253 | `PropertyConstant` |
| `ArrayConstant.java` | 212 | `ArrayConstant` |
| `Container.java` | 278 | `IdentityConstant` |
| `EnumValueConstant.java` | 93 | `EnumValueConstant` |
| `MapConstant.java` | 252 | `MapConstant` |
| `MethodStructure.java` | 1436 | `SingletonConstant` |
| `SingletonConstant.java` | 164 | `SingletonConstant` |

### 1c. `[try]` Unused auto-closeable resource (18 warnings)

These are intentional try-with-resources used for scoping (e.g., `var ignore = lock()`).
Fix by adding `@SuppressWarnings("try")` to the enclosing method.

| File | Line |
|------|------|
| `MethodStructure.java` | 641 |
| `TypeConstant.java` | 1942 |
| `ServiceContext.java` | 309 |
| `FileStructure.java` | 182 |
| `Container.java` | 101 |
| `NativeContainer.java` | 100 |
| `NativeTypeSystem.java` | 99 |
| `xContainerControl.java` | 107 |
| `xRTServer.java` | 654 |
| `xOSStorage.java` | 295 |
| `MainContainer.java` | 176 |
| `JitConnector.java` | 52 |
| `TypeParameterConstant.java` | 212 |
| `Compiler.java` (tool) | 315 |
| `Compiler.java` (compiler) | 155 |
| `Compiler.java` (compiler) | 192 |
| `Compiler.java` (compiler) | 236 |
| `Compiler.java` (compiler) | 280 |

---

## Tier 2: EASY (straightforward, low risk) -- 8 warnings

### 2. `[serial]` Missing serialVersionUID / non-serializable field

Add `@Serial private static final long serialVersionUID = 1L;` or `@SuppressWarnings("serial")`.

| File | Line | Class | Detail |
|------|------|-------|--------|
| `IdentityArrayList.java` | 12 | `IdentityArrayList` | missing serialVersionUID |
| `ObjectHandle.java` | 663 | `ExceptionHandle.WrapperException` | missing serialVersionUID |
| `MapConstant.java` | 354 | `ROEntry` | missing serialVersionUID |
| `Launcher.java` | 963 | `LauncherException` | missing serialVersionUID |
| `Decimal.java` | 868 | `RangeException` | missing serialVersionUID |
| `Decimal.java` | 873 | `RangeException` | non-transient non-serializable field |
| `CompilerException.java` | 10 | `CompilerException` | missing serialVersionUID |
| `SegFault.java` | 6 | `SegFault` | missing serialVersionUID |

---

## Tier 3: MODERATE (need understanding, some refactoring) -- 146 warnings

### 3a. `[rawtypes]` Raw generic type usage (68 warnings)

Add proper type parameters. Hotspots:

| File | Count | Key raw types |
|------|-------|---------------|
| `AstNode.java` | 14 | `List`, `Map`, `Collection`, `Iterator`, `ListIterator`, `ArrayList`, `Class` |
| `ServiceContext.java` | 13 | `CompletableFuture`, `Response`, `Enum`, `EnumMap`, `WeakReference` |
| `Fiber.java` | 8 | `CompletableFuture` |
| `CaseManager.java` | 4 | `Comparable` |
| `Frame.java` | 4 | `CompletableFuture` |
| `xOSFile.java` | 4 | `CompletableFuture` |
| `IdentityArrayList.java` | 3 | `List`, `ListIterator` |
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
| `ListMap.java` | 3 | internal casts for `EMPTY_ARRAY_LIST` |
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

## Tier 4: INVOLVED (needs careful control-flow review) -- 102 warnings

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

## Tier 5: HARD (architectural, risk of subtle breakage) -- 68 warnings

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

1. **Tier 1** -- 33 quick wins, zero risk, builds momentum
2. **Tier 2** -- 8 more easy ones
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
