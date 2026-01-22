# String Building Modernization Plan

This document catalogs occurrences of legacy string-building patterns in the XVM codebase that could be modernized using Java 8+ features like `String.join()`, `Collectors.joining()`, and `var` type inference.

## Executive Summary

| Pattern | Occurrences | Files Affected |
|---------|-------------|----------------|
| `boolean first` loop pattern | ~70+ | 30 |
| Explicit `StringBuilder` declarations | 217 | ~80 |

---

## Progress

### Completed (29 occurrences)

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
| `javatools/src/main/java/org/xvm/asm/FileStructure.java` | 938 | `getDescription()` | Module list | Easy |
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
| `javatools/src/main/java/org/xvm/compiler/ast/CompositionNode.java` | 541 | `Import.toString()` | Version overrides (different delimiters) | Hard |
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
