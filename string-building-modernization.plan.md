# Code Modernization Plan

This document catalogs occurrences of legacy patterns in the XVM codebase that could be modernized using Java 8+ features. All patterns listed are **local replacements only** - no changes to fields or semantics, trivially equivalent but cleaner.

## Executive Summary

| Pattern | Occurrences | Files Affected | Priority | Status |
|---------|-------------|----------------|----------|--------|
| `boolean first` loop pattern | ~70+ | 30 | Medium | **DONE** |
| Explicit `StringBuilder` declarations | 217 | ~80 | Low | **DONE** |
| `Collections.emptyList()` → `List.of()` | 49 | 22 | **High** | **DONE** |
| `Collections.singletonList()` → `List.of()` | 22 | 10 | **High** | **DONE** |
| `Arrays.asList()` → `List.of()` | 19 | 12 | Medium | **DONE** |
| `System.arraycopy()` → `Arrays.copyOf()` | ~30 | 20 | Medium | 5 done (rest N/A) |
| Lazy list instantiation (`List x = null`) | ~26 | ~15 | Medium | **21 done** (rest N/A) |
| Loop-to-lambda simplifications | 9 | 5 | Medium | **DONE** |

### Bugs Discovered

Two bugs were discovered during this work:

- **FIXED** (this branch): `MarkAndSweepGcSpace.java` arraycopy bug - see `BUG-01-mark-and-sweep-arraycopy.md`
- **Separate PR needed**: `xRef.java` Arrays.asList() crash - see `BUG-02-xref-arrays-aslist.md`

---

## Progress

### Completed (39 occurrences)

| File | Method | Status |
|------|--------|--------|
| `compiler/ast/ImportStatement.java:116` | `getQualifiedNameString()` | Done |
| `compiler/ast/TupleExpression.java:488` | `toString()` | Done |
| `compiler/ast/TupleTypeExpression.java:83` | `toString()` | Done |
| `compiler/ast/ListExpression.java:408` | `toString()` | Done |
| `compiler/ast/ArrayAccessExpression.java:1288` | `toString()` | Done |
| `compiler/ast/MultipleLValueStatement.java:232` | `toString()` | Done |
| `tool/ResourceDir.java:246` | `toString()` | Done |
| `compiler/ast/NamedTypeExpression.java:134` | `getModule()` | Done |
| `compiler/ast/NamedTypeExpression.java:159` | `getName()` | Done |
| `compiler/ast/NamedTypeExpression.java:1063` | `toString()` paramTypes | Done |
| `compiler/ast/MethodDeclarationStatement.java:1138` | `toSignatureString()` typeParams | Done |
| `compiler/ast/MethodDeclarationStatement.java:1159` | `toSignatureString()` returns | Done |
| `compiler/ast/MethodDeclarationStatement.java:1175` | `toSignatureString()` redundant | Done |
| `compiler/ast/MethodDeclarationStatement.java:1189` | `toSignatureString()` params | Done |
| `compiler/ast/ReturnStatement.java:477` | `toString()` | Done |
| `compiler/ast/AnnotationExpression.java:441` | `toString()` | Done |
| `compiler/ast/InvocationExpression.java:3007` | `toString()` | Done |
| `compiler/ast/LambdaExpression.java:1271` | `toSignatureString()` | Done |
| `compiler/ast/TryStatement.java:486` | `toString()` | Done |
| `compiler/ast/FunctionTypeExpression.java:155` | `toString()` returnValues | Done |
| `compiler/ast/FunctionTypeExpression.java:168` | `toString()` paramTypes | Done |
| `asm/constants/SignatureConstant.java:706` | `getValueString()` returns | Done |
| `asm/constants/SignatureConstant.java:725` | `getValueString()` params | Done |
| `asm/Annotation.java:275` | `getValueString()` | Done |
| `asm/constants/ParameterizedTypeConstant.java:1057` | `getValueString()` | Done |
| `compiler/ast/CompositionNode.java:168` | `Extends.toString()` | Done |
| `compiler/ast/CompositionNode.java:235` | `Annotates.toString()` | Done |
| `compiler/ast/CompositionNode.java:315` | `Incorporates.toString()` constraints | Done |
| `compiler/ast/CompositionNode.java:332` | `Incorporates.toString()` args | Done |
| `compiler/ast/CompositionNode.java:559` | `Import.toString()` injects | Done |
| `compiler/ast/NameExpression.java:3271` | `toString()` params | Done |
| `compiler/ast/NewExpression.java:1532` | `toSignatureString()` dimensions | Done |
| `compiler/ast/NewExpression.java:1546` | `toSignatureString()` args | Done |
| `compiler/ast/ForStatement.java:697` | `toString()` init | Done |
| `compiler/ast/ForStatement.java:721` | `toString()` update | Done |
| `compiler/ast/TypeCompositionStatement.java:2991` | `toString()` typeArgs | Done |
| `compiler/ast/TypeCompositionStatement.java:3005` | `toString()` args | Done |
| `compiler/ast/TypeCompositionStatement.java:3037` | `toString()` qualified | Done |
| `compiler/ast/TypeCompositionStatement.java:3050` | `toString()` typeParams | Done |
| `compiler/ast/TypeCompositionStatement.java:3064` | `toString()` constructorParams | Done |
| `compiler/ast/ImportStatement.java:235` | `toString()` qualified | Done |

### Not Applicable (reclassified)

The following were incorrectly classified as string-building patterns. They are actually **parsing loop control patterns** that use `boolean first` to control whether to expect delimiters (commas, dots) between tokens during parsing:

| File | Line | Actual Purpose |
|------|------|----------------|
| `compiler/Parser.java` | 1743 | Controls DOT expectation in qualified name parsing |
| `compiler/Parser.java` | 4685 | Controls COMMA expectation in type parameter parsing |
| `compiler/Parser.java` | 5076 | Controls COMMA expectation in version override parsing |

---

## Part 1: Boolean First Loop Pattern

### Pattern Description

The codebase extensively uses this pattern for building comma/token-separated strings:

```java
boolean first = true;
for (Item item : items) {
    if (first) {
        first = false;
    } else {
        sb.append(", ");
    }
    sb.append(item);
}
```

### Modern Alternatives

1. **`String.join(delimiter, collection)`** - For simple string collections
2. **`stream.collect(Collectors.joining(delimiter))`** - For transformations
3. **`StringJoiner`** - For prefix/suffix control

### All Occurrences by File

#### ASM Constants and Core (10 occurrences)

| File | Line | Method | Building | Difficulty |
|------|------|--------|----------|------------|
| `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java` | 1057 | `getValueString()` | Generic type parameters `Type<P1, P2>` | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java` | 706 | `getValueString()` | Return types `(T1, T2)` | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java` | 725 | `getValueString()` | Parameter types | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/asm/FileStructure.java` | 938 | `getDescription()` | Module list | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/asm/Annotation.java` | 275 | `getValueString()` | Annotation parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/asm/Component.java` | 666 | Iterator class | Iterator state (not string) | N/A |

#### Compiler Parser (3 occurrences - NOT APPLICABLE)

These are parsing loop control patterns, not string building. See "Not Applicable" section above.

| File | Line | Method | Building | Difficulty |
|------|------|--------|----------|------------|
| `javatools/src/main/java/org/xvm/compiler/Parser.java` | 1743 | `parseQualifiedName()` | ~~Qualified names~~ Parser loop control | N/A |
| `javatools/src/main/java/org/xvm/compiler/Parser.java` | 4685 | - | ~~Qualified names~~ Parser loop control | N/A |
| `javatools/src/main/java/org/xvm/compiler/Parser.java` | 5076 | - | ~~Qualified names~~ Parser loop control | N/A |

#### AST Expression Classes (50+ occurrences)

| File | Line(s) | Method | Building | Difficulty |
|------|---------|--------|----------|------------|
| `javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java` | 3271 | `toString()` | Type parameters | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/CompositionNode.java` | 168, 235, 315, 332, 559 | Multiple | Annotation args, type constraints, composition args, injection parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/CompositionNode.java` | 541 | `Import.toString()` | Version overrides (different delimiters) | ~~Hard~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/ImportStatement.java` | 116 | `getQualifiedNameString()` | Qualified names | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/ImportStatement.java` | 242 | `toString()` | Import statements | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/AnnotationExpression.java` | 441 | `toString()` | Annotation arguments | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java` | 1271 | `toString()` | Lambda parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java` | 3007 | `toString()` | Method invocation arguments | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java` | 1532 | `toString()` | Array dimensions `Type[d1, d2]` | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java` | 1546 | `toString()` | Constructor arguments | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java` | 488 | `toString()` | Tuple elements `(e1, e2)` | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java` | 134 | `getModule()` | Module names | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java` | 159 | `getName()` | Type names | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java` | 1084 | `toString()` | Generic type parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java` | 1138 | `toSignatureString()` | Type parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java` | 1159 | `toSignatureString()` | Method parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java` | 1175 | `toSignatureString()` | Return parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java` | 1189 | `toSignatureString()` | Throws clause | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/TryStatement.java` | 486 | `toString()` | Exception handlers | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/FunctionTypeExpression.java` | 155 | `toString()` | Function return types | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/FunctionTypeExpression.java` | 168 | `toString()` | Function parameters | ~~Medium~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/TupleTypeExpression.java` | 83 | `toString()` | Tuple type elements | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/ListExpression.java` | 408 | `toString()` | List elements | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java` | 2991 | `toString()` | Class modifiers | Hard |
| `javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java` | 3005 | `toString()` | Type parameters | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java` | 3037 | `toString()` | Constructor parameters | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java` | 3050 | `toString()` | Base classes | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java` | 3064 | `toString()` | Implemented interfaces | Medium |
| `javatools/src/main/java/org/xvm/compiler/ast/ReturnStatement.java` | 477 | `toString()` | Return values | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java` | 1288 | `toString()` | Array indices | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/MultipleLValueStatement.java` | 232 | `toString()` | Assignment targets | ~~Easy~~ Done |
| `javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java` | 697, 721 | `toString()` | Loop variables and conditions | Medium |

#### Utility and Tool Classes (1 occurrence)

| File | Line | Method | Building | Difficulty |
|------|------|--------|----------|------------|
| `javatools/src/main/java/org/xvm/tool/ResourceDir.java` | 246 | `toString()` | Resource directory paths | ~~Easy~~ Done |

#### Test Classes (4 occurrences - Different Pattern)

| File | Line | Notes |
|------|------|-------|
| `javatools/src/test/java/org/xvm/runtime/gc/SimpleCanaryGcTest.java` | 203 | GC state tracking, not string building |
| `javatools/src/test/java/org/xvm/runtime/gc/CanaryBuddyGcTest.java` | 220 | GC state tracking |
| `javatools/src/test/java/org/xvm/runtime/gc/CanaryCohortGcTest.java` | 221 | GC state tracking |
| `javatools/src/test/java/org/xvm/runtime/gc/PhantomGcTest.java` | 200 | GC state tracking |

### Difficulty Classification

- **Easy** (~18 remaining, 7 done): Simple comma/dot joining of string elements, direct `String.join()` replacement
- **Medium** (~35 occurrences): Elements need transformation (e.g., calling `toString()` or `getValueString()`), use `stream().map().collect(Collectors.joining())`
- **Hard** (~10 occurrences): Complex conditional logic inside loop, special formatting, or mixed content types
- **N/A** (3 occurrences): Parser.java cases were incorrectly classified - they are parsing loop control, not string building

### Example Modernizations

**Easy - Direct replacement:**
```java
// Before
boolean first = true;
for (String name : names) {
    if (first) first = false; else sb.append(".");
    sb.append(name);
}

// After
sb.append(String.join(".", names));
```

**Medium - Stream with transformation:**
```java
// Before
boolean first = true;
for (TypeConstant type : types) {
    if (first) first = false; else sb.append(", ");
    sb.append(type.getValueString());
}

// After
sb.append(types.stream()
    .map(TypeConstant::getValueString)
    .collect(Collectors.joining(", ")));
```

---

## Part 2: Explicit StringBuilder Declarations

### Pattern Description

The codebase uses explicit type declarations:
```java
StringBuilder sb = new StringBuilder();
```

When modern Java allows:
```java
var sb = new StringBuilder();
```

### Total: 217 occurrences across ~80 files

All of these are **trivially easy** to modernize - it's a mechanical find-and-replace operation.

### All Occurrences by Module

#### plugin (1 occurrence)
- `plugin/src/main/java/org/xtclang/plugin/XtcPluginUtils.java:277`

#### javatools_jitbridge (1 occurrence)
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/Exception.java:74`

#### javatools_backend (2 occurrences)
- `javatools_backend/src/main/java/org/xvm/xec/ecstasy/Const.java:42` (string literal, not actual code)
- `javatools_backend/src/main/java/org/xvm/xtc/CPool.java:271`

#### javatools_utils (20 occurrences)

| File | Lines |
|------|-------|
| `javatools_utils/src/main/java/org/xvm/util/PackedInteger.java` | 513 |
| `javatools_utils/src/main/java/org/xvm/util/ConsoleLog.java` | 93 |
| `javatools_utils/src/main/java/org/xvm/util/Handy.java` | 206, 627, 830, 1160 |
| `javatools_utils/src/test/java/org/xvm/util/SetTest.java` | 532, 594, 662, 713, 800, 924, 973, 1015, 1078, 1125 |
| `javatools_utils/src/test/java/org/xvm/util/PackedIntegerTest.java` | 38, 46, 55, 64, 217 |
| `javatools_utils/src/test/java/org/xvm/util/HandyTest.java` | 94, 110, 118, 168, 176, 211, 219, 412, 430 |

#### javatools (193 occurrences)

**ASM Package (~60 occurrences):**
| File | Lines |
|------|-------|
| `asm/Annotation.java` | 267, 335 |
| `asm/ModuleStructure.java` | 790 |
| `asm/VersionTree.java` | 355 |
| `asm/Register.java` | 453 |
| `asm/PropertyStructure.java` | 746 |
| `asm/Version.java` | 213 |
| `asm/OpInPlace.java` | 190 |
| `asm/OpVar.java` | 181 |
| `asm/OpCondJump.java` | 293 |
| `asm/OpGeneral.java` | 183 |
| `asm/Component.java` | 2117, 3170 |
| `asm/FileStructure.java` | 932 |
| `asm/CompositeComponent.java` | 482 |
| `asm/XvmStructure.java` | 524 |
| `asm/OpTest.java` | 235 |
| `asm/ConstantPool.java` | 2801, 3779 |
| `asm/Parameter.java` | 407 |
| `asm/MethodStructure.java` | 2017, 2364, 2792, 2852 |
| `asm/ClassStructure.java` | 3378 |
| `asm/ErrorListener.java` | 316, 346 |
| `asm/constants/SignatureConstant.java` | 693, 917 |
| `asm/constants/ArrayConstant.java` | 263 |
| `asm/constants/PropertyInfo.java` | 1465 |
| `asm/constants/TypeConstant.java` | 6535 |
| `asm/constants/ModuleConstant.java` | 274 |
| `asm/constants/PropertyBody.java` | 444 |
| `asm/constants/TypeInfo.java` | 2207 |
| `asm/constants/ParameterizedTypeConstant.java` | 1002 |
| `asm/constants/MethodConstant.java` | 319, 401, 626 |
| `asm/constants/MultiCondition.java` | 258 |
| `asm/constants/ParamInfo.java` | 110 |
| `asm/constants/MethodBody.java` | 467 |
| `asm/constants/MethodInfo.java` | 1219 |
| `asm/constants/MapConstant.java` | 290 |
| `asm/constants/UnresolvedNameConstant.java` | 66 |
| `asm/constants/PropertyConstant.java` | 429 |
| `asm/OpInvocable.java` | 272, 287 |
| `asm/OpCallable.java` | 141, 156 |
| `asm/op/Redundant.java` | 40 |
| `asm/op/FBind.java` | 209 |
| `asm/op/JumpInt.java` | 175 |
| `asm/op/Label.java` | 76 |
| `asm/op/Return_N.java` | 123 |
| `asm/op/AssertV.java` | 113, 209 |
| `asm/op/OpSwitch.java` | 229 |

**AST Package (~30 occurrences):**
| File | Lines |
|------|-------|
| `asm/ast/TryCatchStmtAST.java` | 80 |
| `asm/ast/ConvertExprAST.java` | 98 |
| `asm/ast/AssertStmtAST.java` | 91 |
| `asm/ast/IfStmtAST.java` | 101 |
| `asm/ast/NewExprAST.java` | 170 |
| `asm/ast/TemplateExprAST.java` | 69 |
| `asm/ast/MapExprAST.java` | 85 |
| `asm/ast/TupleExprAST.java` | 44 |
| `asm/ast/StmtBlockAST.java` | 96 |
| `asm/ast/BindFunctionAST.java` | 114 |
| `asm/ast/DoWhileStmtAST.java` | 65 |
| `asm/ast/ForEachStmtAST.java` | 106 |
| `asm/ast/ReturnStmtAST.java` | 95 |
| `asm/ast/MultiExprAST.java` | 74 |
| `asm/ast/WhileStmtAST.java` | 83 |
| `asm/ast/ListExprAST.java` | 74 |
| `asm/ast/ForStmtAST.java` | 96 |
| `asm/ast/RegAllocAST.java` | 144 |
| `asm/ast/SwitchAST.java` | 333 |
| `asm/ast/CmpChainExprAST.java` | 117 |
| `asm/ast/CallableExprAST.java` | 80 |

**Compiler AST Package (~60 occurrences):**
| File | Lines |
|------|-------|
| `compiler/ast/ForStatement.java` | 692 |
| `compiler/ast/CmpChainExpression.java` | 555 |
| `compiler/ast/GotoStatement.java` | 156 |
| `compiler/ast/PropertyDeclarationStatement.java` | 767, 793 |
| `compiler/ast/MultipleLValueStatement.java` | 228 |
| `compiler/ast/ArrayAccessExpression.java` | 1283 |
| `compiler/ast/MapExpression.java` | 426 |
| `compiler/ast/ReturnStatement.java` | 464 |
| `compiler/ast/CaseStatement.java` | 139 |
| `compiler/ast/ConvertExpression.java` | 323 |
| `compiler/ast/TypeCompositionStatement.java` | 249, 1413, 2903, 2977, 3082 |
| `compiler/ast/ListExpression.java` | 404 |
| `compiler/ast/TupleTypeExpression.java` | 79 |
| `compiler/ast/TemplateExpression.java` | 137, 264 |
| `compiler/ast/Context.java` | 1706 |
| `compiler/ast/FunctionTypeExpression.java` | 142 |
| `compiler/ast/TryStatement.java` | 480 |
| `compiler/ast/AssignmentStatement.java` | 1100 |
| `compiler/ast/CompositionNode.java` | 107, 162, 221, 298, 528 |
| `compiler/ast/MethodDeclarationStatement.java` | 1120, 1210 |
| `compiler/ast/ExpressionStatement.java` | 112 |
| `compiler/ast/AnonInnerClass.java` | 347 |
| `compiler/ast/NamedTypeExpression.java` | 132, 157, 1055 |
| `compiler/ast/VariableDeclarationStatement.java` | 310 |
| `compiler/ast/TupleExpression.java` | 483 |
| `compiler/ast/NewExpression.java` | 1511, 1564 |
| `compiler/ast/ThrowExpression.java` | 394 |
| `compiler/ast/InvocationExpression.java` | 252, 3000 |
| `compiler/ast/PrefixExpression.java` | 209 |
| `compiler/ast/IfStatement.java` | 360 |
| `compiler/ast/ArrayTypeExpression.java` | 124 |
| `compiler/ast/LambdaExpression.java` | 1268, 1290 |
| `compiler/ast/AssertStatement.java` | 369, 627 |
| `compiler/ast/TypedefStatement.java` | 96 |
| `compiler/ast/Parameter.java` | 75 |
| `compiler/ast/WhileStatement.java` | 716 |
| `compiler/ast/ToIntExpression.java` | 257 |
| `compiler/ast/AnnotationExpression.java` | 433 |
| `compiler/ast/SwitchStatement.java` | 407 |
| `compiler/ast/SwitchExpression.java` | 336 |
| `compiler/ast/NameExpression.java` | 3252 |
| `compiler/ast/StatementBlock.java` | 554 |
| `compiler/ast/ImportStatement.java` | 115, 232 |
| `compiler/ast/NameResolver.java` | 133 |

**Compiler Core (~10 occurrences):**
| File | Lines |
|------|-------|
| `compiler/Token.java` | 352 |
| `compiler/Source.java` | 85, 513 |
| `compiler/Lexer.java` | 400, 1212 |
| `compiler/Parser.java` | 151, 3660, 5592 |

**Runtime Package (~25 occurrences):**
| File | Lines |
|------|-------|
| `runtime/DebugConsole.java` | 755, 1020, 1043, 1200, 1360, 1448, 1872, 1890, 1969, 2004, 2005, 2018, 2182, 2202, 2296 |
| `runtime/ClassComposition.java` | 826 |
| `runtime/Fiber.java` | 493 |
| `runtime/FiberQueue.java` | 121 |
| `runtime/Frame.java` | 1979, 2008, 2186 |
| `runtime/NativeContainer.java` | 226 |
| `runtime/ServiceContext.java` | 1551 |
| `runtime/template/_native/reflect/xRTFunction.java` | 602 |
| `runtime/template/_native/crypto/xRTCertificateManager.java` | 562, 577 |
| `runtime/template/text/xString.java` | 389 |

**JavaJIT Package (3 occurrences):**
| File | Lines |
|------|-------|
| `javajit/Xvm.java` | 597, 666 |
| `javajit/BuildContext.java` | 455 |

**Test Classes (3 occurrences):**
| File | Lines |
|------|-------|
| `src/test/java/org/xvm/compiler/SourceTest.java` | 20, 88, 99 |

---

## Modernization Strategy

### Phase 1: StringBuilder → var (Low Risk, High Volume)
**Effort: ~2 hours**
**Risk: Minimal**

This is a safe, mechanical transformation. Can be done with IDE refactoring or a simple script:
```bash
# Conceptual - actual implementation would need careful handling
sed -i 's/StringBuilder \(\w\+\) = new StringBuilder/var \1 = new StringBuilder/g'
```

### Phase 2: Simple First-Loop Patterns (Medium Risk, Easy Cases)
**Effort: ~4 hours**
**Risk: Low - primarily toString() methods**

Target the ~25 "Easy" cases first:
- Direct string collections
- Dot-separated qualified names
- Simple tuple/list elements

### Phase 3: Medium Complexity Patterns
**Effort: ~8 hours**
**Risk: Medium - needs careful testing**

Target the ~35 "Medium" cases:
- Elements requiring transformation
- Multiple parameters in method signatures
- Generic type parameters

### Phase 4: Complex Patterns (Optional)
**Effort: ~4 hours**
**Risk: Higher - complex logic**

The ~10 "Hard" cases may be better left as-is if:
- They have complex conditional logic
- The loop body does more than just append
- Readability would decrease with streams

---

## Recommended Priority

1. **Immediate**: StringBuilder → var (217 occurrences) - Zero risk, improves consistency
2. **Short-term**: Easy first-loop patterns (~25 occurrences) - Low risk, good improvement
3. **Medium-term**: Medium first-loop patterns (~35 occurrences) - Moderate effort
4. **Optional**: Hard patterns (~10 occurrences) - Evaluate case-by-case

---

## Notes

- Test classes (GC tests) use `boolean first` for state tracking, not string building - these should be excluded
- Some occurrences in `javatools_backend` are string literals being generated, not actual code patterns
- The `CPool.java:271` declaration is a static final field, which should remain explicit

---

## Part 3: Collections Factory Methods Modernization

### 3.1 `Collections.emptyList()` → `List.of()`

**Total: 49 occurrences across 22 files**

`List.of()` (Java 9+) is clearer, more idiomatic, and lighter weight than `Collections.emptyList()`. Both return immutable empty lists.

#### All Occurrences

| File | Count |
|------|-------|
| `asm/ConstantPool.java` | 3 |
| `asm/ClassStructure.java` | 7 |
| `asm/constants/AbstractDependantChildTypeConstant.java` | 1 |
| `asm/constants/TypeConstant.java` | 9 |
| `asm/constants/ParameterizedTypeConstant.java` | 1 |
| `asm/constants/TypeCollector.java` | 2 |
| `asm/constants/PropertyClassTypeConstant.java` | 2 |
| `asm/constants/RelationalTypeConstant.java` | 1 |
| `asm/Component.java` | 2 |
| `compiler/Parser.java` | 6 |
| `compiler/ast/ListExpression.java` | 2 |
| `compiler/ast/AnonInnerClass.java` | 2 |
| `compiler/ast/ForStatement.java` | 2 |
| `compiler/ast/CompositionNode.java` | 1 |
| Plus 8 more files with single occurrences |

**Example transformation:**
```java
// Before
return Collections.emptyList();

// After
return List.of();
```

### 3.2 `Collections.singletonList()` → `List.of()`

**Total: 22 occurrences across 10 files**

`List.of(element)` is more readable and consistent with modern Java idiom.

#### All Occurrences

| File | Count |
|------|-------|
| `compiler/Parser.java` | 11 |
| `compiler/ast/ForEachStatement.java` | 2 |
| `compiler/ast/AstNode.java` | 1 |
| `compiler/ast/AnnotationExpression.java` | 1 |
| `compiler/ast/NamedTypeExpression.java` | 1 |
| `compiler/ast/MethodDeclarationStatement.java` | 1 |
| `compiler/ast/TypeCompositionStatement.java` | 2 |
| `compiler/ast/StageMgr.java` | 1 |
| `asm/ConstantPool.java` | 1 |
| `runtime/ObjectHandle.java` | 1 |

**Example transformation:**
```java
// Before
return Collections.singletonList(item);

// After
return List.of(item);
```

### 3.3 `Arrays.asList()` → `List.of()`

**Total: 19 occurrences across 12 files**

For fixed-size lists created from varargs, `List.of()` is more idiomatic and explicitly immutable.

**Note:** Only applicable when the resulting list is not modified. `Arrays.asList()` allows `.set()` while `List.of()` does not.

#### All Occurrences

| File | Count |
|------|-------|
| `asm/MethodStructure.java` | 4 |
| `asm/constants/SignatureConstant.java` | 2 |
| `asm/ClassStructure.java` | 1 |
| `asm/constants/PropertyInfo.java` | 1 |
| `asm/constants/ParameterizedTypeConstant.java` | 1 |
| `asm/constants/TypeConstant.java` | 1 |
| `compiler/Parser.java` | 2 |
| `compiler/ast/AstNode.java` | 2 |
| `compiler/ast/TupleExpression.java` | 1 |
| `compiler/ast/ReturnStatement.java` | 1 |
| `runtime/template/reflect/xRef.java` | 1 |
| `tool/LauncherOptions.java` | 2 |

**Example transformation:**
```java
// Before
return Arrays.asList(a, b, c);

// After
return List.of(a, b, c);
```

---

## Part 4: Array Copy Modernization

### `System.arraycopy()` → `Arrays.copyOf()`

**Total: ~30 candidates for modernization**

Many `System.arraycopy()` calls follow the pattern:
```java
T[] newArray = new T[newSize];
System.arraycopy(oldArray, 0, newArray, 0, oldArray.length);
```

This can be simplified to:
```java
T[] newArray = Arrays.copyOf(oldArray, newSize);
```

#### Prime Candidates (copy from index 0 to index 0)

| File | Line | Current Pattern | Status |
|------|------|-----------------|--------|
| `asm/Parameter.java` | 140 | Annotation array resize | N/A (adds element after) |
| `asm/MethodStructure.java` | 1580 | Annotation array resize | N/A (adds element after) |
| `asm/MethodStructure.java` | 2468 | Op array resize | **DONE** |
| `asm/MethodStructure.java` | 2569 | Op array resize | **DONE** |
| `asm/constants/MethodInfo.java` | 300 | MethodBody array merge | N/A (merges two arrays) |
| `asm/constants/PropertyInfo.java` | 1277 | PropertyBody chain resize | N/A (adds element after) |
| `asm/constants/AllCondition.java` | 122, 154 | Condition array manipulation | N/A (adds element after) |
| `asm/Version.java` | 506 | Version parts trimming | **DONE** |
| `asm/VersionTree.java` | 885 | Node array resize | **DONE** |
| `asm/ast/SwitchAST.java` | 89 | BinaryAST array resize | **DONE** |
| `runtime/FiberQueue.java` | 284 | Frame array resize | N/A (conditional) |
| `runtime/template/collections/xTuple.java` | 286, 296, 331, 341, 562 | Tuple manipulation | N/A (add/merge after) |

**Example transformation:**
```java
// Before
int[] newState = new int[state.length * 2];
System.arraycopy(state, 0, newState, 0, state.length);
state = newState;

// After
state = Arrays.copyOf(state, state.length * 2);
```

#### NOT Candidates

These use `System.arraycopy()` with non-zero offsets or for partial copies, which cannot be replaced:
- Copies within the same array (shifting elements)
- Copies to a specific offset in the destination
- Partial array copies starting from non-zero index

---

## Part 5: Lazy List Instantiation (Needs Further Investigation)

**Pattern:**
```java
List<X> items = null;
// ... later in a loop ...
if (items == null) {
    items = new ArrayList<>();
}
items.add(item);
// Later: return items; or if (items != null) { ... }
```

**Why this is an antipattern:**
1. **Modern GC is fast** - Short-lived small objects are collected efficiently. Allocation cost is negligible.
2. **Cognitive overhead** - Every reader must trace through null checks
3. **Bug-prone** - Easy to forget a null check
4. **Prevents functional composition** - Can't chain operations on potentially-null lists

**Modern approach:**
```java
// Just allocate upfront - it's fine
List<X> items = new ArrayList<>();
// ... populate in loop ...
// items is never null, may be empty - and that's fine
```

**Occurrences found:** ~26 in compiler/asm directories (local variables only)

**Status:** 21 modernized across 11 files. Remaining patterns either:
- Use helper methods that pass list through (MethodStructure.addSingleton)
- Have null-vs-empty semantic meaning in API contracts (Parser.java parsing methods)
- Use `startList()` or `ensureList()` helper for copy-on-write patterns (MethodInfo, PropertyInfo, TypeCompositionStatement)

**Modernized files:**
- ClassStructure.java (3 patterns)
- MethodInfo.java (3 patterns)
- PropertyInfo.java (1 pattern)
- PropertyStructure.java (4 patterns)
- TypeConstant.java (4 patterns)
- Component.java (1 pattern)
- VersionTree.java (1 pattern)
- DynamicFormalConstant.java (1 pattern)
- InvocationExpression.java (1 pattern)
- TypeCompositionStatement.java (1 pattern)
- xConst.java (1 pattern)

---

## Part 6: Patterns Already Done Well

### 6.1 Index-based List Iteration

The codebase already uses index-based loops appropriately - all occurrences examined need the index for:
- Returning the found index position
- Parallel iteration over multiple lists
- Reverse iteration (`for (int i = size-1; i >= 0; i--)`)
- Array assignment at position i
- Printing index in debug output

No unnecessary index-based iteration was found.

### 6.2 `.isEmpty()` Usage

The codebase already uses `.isEmpty()` extensively (370+ occurrences). The 14 occurrences of `.length == 0` for arrays are correct - arrays don't have `.isEmpty()`.

---

## Part 7: Loop-to-Lambda Simplifications

### Pattern Description

Several loops in the codebase can be further simplified using Java Stream API for counting, searching, and conditional checks. These are local transformations with identical semantics.

### 7.1 Counting Loops → `stream().filter().count()`

| File | Line | Current Pattern | Status |
|------|------|-----------------|--------|
| `asm/constants/TypeConstant.java` | 6330-6336 | Count ENUMVALUE children | **DONE** |

**Example transformation:**
```java
// Before
int c = 0;
for (Component child : clzEnum.children()) {
    if (child.getFormat() == Component.Format.ENUMVALUE) {
        ++c;
    }
}
return c;

// After
return (int) clzEnum.children().stream()
    .filter(child -> child.getFormat() == Component.Format.ENUMVALUE)
    .count();
```

### 7.2 Conditional Checks → `stream().anyMatch()` / `noneMatch()`

| File | Line | Current Pattern | Status |
|------|------|-----------------|--------|
| `asm/constants/TypeConstant.java` | 828-832 | Check if any ENUMVALUE child matches | **DONE** |

**Example transformation:**
```java
// Before
for (Component child : clzThis.children()) {
    if (child.getFormat() == Component.Format.ENUMVALUE &&
            child.getIdentityConstant().getType().isA(that)) {
        return false;
    }
}
return true;

// After
return clzThis.children().stream()
    .noneMatch(child -> child.getFormat() == Component.Format.ENUMVALUE &&
                        child.getIdentityConstant().getType().isA(that));
```

### 7.3 Find-First Loops → `stream().filter().findFirst()`

| File | Line | Current Pattern | Status |
|------|------|-----------------|--------|
| `asm/constants/TypeConstant.java` | 6387-6392 | Find first ENUMVALUE child | **DONE** |
| `asm/MethodStructure.java` | 254-262 | Find annotation by class | **DONE** |
| `asm/Component.java` | 489-499 | Find contribution by composition | **DONE** |
| `asm/constants/MethodInfo.java` | 1085-1092 | Get access from first body | **DONE** |

### 7.5 AnyMatch Patterns → `stream().anyMatch()`

| File | Line | Current Pattern | Status |
|------|------|-----------------|--------|
| `compiler/ast/AstNode.java` | 1015-1020 | Check for LabeledExpression | **DONE** |

### 7.4 Collection/Filter Loops → `stream().map().filter().collect()`

| File | Line | Current Pattern | Status |
|------|------|-----------------|--------|
| `asm/Component.java` | 1575-1586 | Collect non-null children | **DONE** |
| `asm/MethodStructure.java` | 735-746 | Collect unresolved type params | **DONE** |

**Example transformation:**
```java
// Before
for (Component child : clzEnum.children()) {
    if (child.getFormat() == Component.Format.ENUMVALUE) {
        return pool.ensureSingletonConstConstant(child.getIdentityConstant());
    }
}
return null;

// After
return clzEnum.children().stream()
    .filter(child -> child.getFormat() == Component.Format.ENUMVALUE)
    .findFirst()
    .map(child -> pool.ensureSingletonConstConstant(child.getIdentityConstant()))
    .orElse(null);
```

### NOT Candidates for Conversion

The following patterns should remain as loops (stream version would be more complex):
- `EnumValueConstant.java:63-73` - Ordinal tracking with stateful counting
- `EnumValueConstant.java:170-183` - ADD operation with "find next" state tracking
- `EnumValueConstant.java:193-205` - SUB operation with "find previous" state tracking

---

## Updated Recommended Priority

1. **High**: `Collections.emptyList()` → `List.of()` (49 occurrences) - Zero risk, clearer idiom
2. **High**: `Collections.singletonList()` → `List.of()` (22 occurrences) - Zero risk, clearer idiom
3. **Medium**: `Arrays.asList()` → `List.of()` (19 occurrences) - Verify immutability first
4. **Medium**: `System.arraycopy()` → `Arrays.copyOf()` (~30 occurrences) - Simple pattern match
5. **Medium**: Lazy list instantiation (~26 occurrences) - Simplifies null handling
6. **Medium**: Loop-to-lambda simplifications (9 occurrences) - More readable stream operations
7. **Low**: `StringBuilder` → `var` (217 occurrences) - Stylistic preference
8. **Low**: Remaining `boolean first` patterns - Evaluate case-by-case

---

## Part 8: PR Submission Strategy

### Overview

All modernization work is **COMPLETE** on branch `lagergren/sb-simplify`. The changes span **149 files** with **~1542 insertions and ~1168 deletions**.

### ⚠️ Commit Mixing Problem

The existing commits are **NOT cleanly separated**. Two commits mix multiple change types:
- `7278a9e54` - **MIXED**: Collections factory methods + Boolean First patterns
- `e0dca6acc` - **MIXED**: Arrays.asList() + System.arraycopy()

**Cherry-picking won't produce clean single-purpose PRs.** See detailed docs for extraction instructions.

### 7 Single-Purpose PRs

| PR # | Single Purpose | Files | Cherry-pickable? | Status |
|------|----------------|-------|------------------|--------|
| 1 | `Collections.emptyList/singletonList()` → `List.of()` | ~32 | ❌ Extract | Pending |
| 2 | `Arrays.asList()` → `List.of()` | ~12 | ⚠️ Partial | Pending |
| 3 | `StringBuilder` → `var` | ~126 | ✅ Yes | Pending |
| 4 | `boolean first` → `Collectors.joining()` | ~23 | ✅ Patch | **SUBMITTED** [PR #376](https://github.com/xtclang/xvm/pull/376) |
| 5 | `System.arraycopy()` → `Arrays.copyOf()` | 5 | ❌ Extract | Pending |
| 6 | Lazy list `null` → upfront allocation | ~11 | ✅ Yes | Pending |
| 7 | Loop → Stream API | ~5 | ✅ Yes | Pending |

### Detailed Documentation

Full PR documentation with exact file lists, diff examples, and recreate instructions:

```
pr-submission-docs/
├── 00-pr-submission-strategy.md   # Overview, commit mixing, extraction patterns
├── 01-pr-collections-empty-singleton.md
├── 02-pr-arrays-aslist.md
├── 03-pr-stringbuilder-var.md
├── 05-pr-arraycopy.md
├── 06-pr-lazy-list.md
├── 07-pr-loop-to-lambda.md
├── PATCHES-README.md
├── PR-01-collections-empty-singleton.patch
├── PR-02-arrays-aslist.patch
├── PR-03-stringbuilder-var.patch
├── PR-05-arraycopy.patch
├── PR-06-lazy-list.patch
├── PR-07-loop-to-lambda.patch
└── done/
    ├── 04-pr-boolean-first-loop.md
    └── PR-04-boolean-first-loop.patch
```

### Pure Commits (Safe to Cherry-pick)

```
✅ StringBuilder: 21b1a2389, 6f0dda0df
✅ Lazy List: c53d6ecde, 1e1561967
✅ Loop-to-Lambda: 8530f5b56, 026695f31, 6a539dbe4
✅ Boolean First (partial): 0b043626d, 87daf8b66
```

### Mixed Commits (Need Manual Extraction)

```
❌ 7278a9e54 - Collections + Boolean First (use grep to separate)
❌ e0dca6acc - Arrays.asList + arraycopy (use grep to separate)
```

### Commit Mapping

Current commits on `lagergren/sb-simplify` branch (12 total):

| Commit | Primary Category | Secondary |
|--------|-----------------|-----------|
| `0b043626d` | Boolean First Loop (PR 4) | - |
| `87daf8b66` | Boolean First Loop (PR 4) | - |
| `7278a9e54` | Collections (PR 1) | Boolean First (PR 4) |
| `868a7ca15` | Arrays.asList (PR 2) | - |
| `e0dca6acc` | arraycopy (PR 5) | Arrays.asList (PR 2) |
| `21b1a2389` | StringBuilder (PR 3) | - |
| `c53d6ecde` | Lazy List (PR 6) | - |
| `8530f5b56` | Loop-to-Lambda (PR 7) | - |
| `026695f31` | Loop-to-Lambda (PR 7) | - |
| `6a539dbe4` | Loop-to-Lambda (PR 7) | - |
| `1e1561967` | Lazy List (PR 6) | - |
| `6f0dda0df` | StringBuilder (PR 3) | - |

### Recreating PRs from Scratch

If context is lost, use the detailed documentation in `pr-submission-docs/` to:

1. Identify exact files and patterns for each PR
2. Apply transformations mechanically using the documented patterns
3. Verify with `./gradlew test` and `./gradlew build`

### Suggested Submission Order

Since PRs have no dependencies, they can be submitted in parallel. Suggested order for sequential review:

1. **PR 2** (StringBuilder) - Largest but trivially mechanical
2. **PR 1** (Collections) - Clear pattern, familiar idiom
3. **PR 4** (arraycopy) - Smallest, quick review
4. **PR 6** (Loop-to-Lambda) - Small, localized
5. **PR 5** (Lazy List) - Requires understanding context
6. **PR 3** (Boolean First) - Most transformation complexity
