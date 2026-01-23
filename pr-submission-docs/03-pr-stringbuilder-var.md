# PR 2: StringBuilder → var Modernization

## Summary

Replace explicit `StringBuilder` type declarations with `var` keyword (Java 10+):
```java
// Before
StringBuilder sb = new StringBuilder();

// After
var sb = new StringBuilder();
```

## Rationale

- Reduces verbosity without losing clarity (type is evident from RHS)
- Consistent with modern Java idioms
- `var` is well-suited for local variables with obvious types
- No semantic change - purely syntactic

## Risk Assessment

**Risk: Extremely Low**
- Purely syntactic transformation
- `var` infers exact same type as explicit declaration
- No behavioral or semantic changes possible

## Statistics

- **Total occurrences**: ~217
- **Files affected**: ~126
- **Modules affected**: javatools, javatools_utils, javatools_backend, javatools_jitbridge, plugin

## Files Changed

### javatools - ASM Package (21 files)

| File | Occurrences |
|------|-------------|
| `asm/Annotation.java` | 2 |
| `asm/ClassStructure.java` | 1 |
| `asm/Component.java` | 2 |
| `asm/CompositeComponent.java` | 1 |
| `asm/ConstantPool.java` | 2 |
| `asm/ErrorListener.java` | 2 |
| `asm/FileStructure.java` | 1 |
| `asm/MethodStructure.java` | 4 |
| `asm/ModuleStructure.java` | 1 |
| `asm/OpCallable.java` | 2 |
| `asm/OpCondJump.java` | 1 |
| `asm/OpGeneral.java` | 1 |
| `asm/OpInPlace.java` | 1 |
| `asm/OpInvocable.java` | 2 |
| `asm/OpTest.java` | 1 |
| `asm/OpVar.java` | 1 |
| `asm/Parameter.java` | 1 |
| `asm/PropertyStructure.java` | 1 |
| `asm/Register.java` | 1 |
| `asm/VersionTree.java` | 1 |
| `asm/XvmStructure.java` | 1 |

### javatools - ASM AST Package (21 files)

| File | Occurrences |
|------|-------------|
| `asm/ast/AssertStmtAST.java` | 1 |
| `asm/ast/BindFunctionAST.java` | 1 |
| `asm/ast/CallableExprAST.java` | 1 |
| `asm/ast/CmpChainExprAST.java` | 1 |
| `asm/ast/ConvertExprAST.java` | 1 |
| `asm/ast/DoWhileStmtAST.java` | 1 |
| `asm/ast/ForEachStmtAST.java` | 1 |
| `asm/ast/ForStmtAST.java` | 1 |
| `asm/ast/IfStmtAST.java` | 1 |
| `asm/ast/ListExprAST.java` | 1 |
| `asm/ast/MapExprAST.java` | 1 |
| `asm/ast/MultiExprAST.java` | 1 |
| `asm/ast/NewExprAST.java` | 1 |
| `asm/ast/RegAllocAST.java` | 1 |
| `asm/ast/ReturnStmtAST.java` | 1 |
| `asm/ast/StmtBlockAST.java` | 1 |
| `asm/ast/SwitchAST.java` | 1 |
| `asm/ast/TemplateExprAST.java` | 1 |
| `asm/ast/TryCatchStmtAST.java` | 1 |
| `asm/ast/TupleExprAST.java` | 1 |
| `asm/ast/WhileStmtAST.java` | 1 |

### javatools - ASM Constants Package (15 files)

| File | Occurrences |
|------|-------------|
| `asm/constants/ArrayConstant.java` | 1 |
| `asm/constants/MapConstant.java` | 1 |
| `asm/constants/MethodBody.java` | 1 |
| `asm/constants/MethodConstant.java` | 3 |
| `asm/constants/MethodInfo.java` | 1 |
| `asm/constants/ModuleConstant.java` | 1 |
| `asm/constants/MultiCondition.java` | 1 |
| `asm/constants/ParamInfo.java` | 1 |
| `asm/constants/ParameterizedTypeConstant.java` | 1 |
| `asm/constants/PropertyBody.java` | 1 |
| `asm/constants/PropertyConstant.java` | 1 |
| `asm/constants/PropertyInfo.java` | 1 |
| `asm/constants/SignatureConstant.java` | 2 |
| `asm/constants/TypeConstant.java` | 1 |
| `asm/constants/TypeInfo.java` | 1 |

### javatools - ASM Op Package (7 files)

| File | Occurrences |
|------|-------------|
| `asm/op/AssertV.java` | 2 |
| `asm/op/FBind.java` | 1 |
| `asm/op/JumpInt.java` | 1 |
| `asm/op/Label.java` | 1 |
| `asm/op/OpSwitch.java` | 1 |
| `asm/op/Redundant.java` | 1 |
| `asm/op/Return_N.java` | 1 |

### javatools - Compiler Package (3 files)

| File | Occurrences |
|------|-------------|
| `compiler/Parser.java` | 3 |
| `compiler/Source.java` | 2 |
| `compiler/Token.java` | 1 |

### javatools - Compiler AST Package (39 files)

| File | Occurrences |
|------|-------------|
| `compiler/ast/AnnotationExpression.java` | 1 |
| `compiler/ast/AnonInnerClass.java` | 1 |
| `compiler/ast/ArrayTypeExpression.java` | 1 |
| `compiler/ast/AssertStatement.java` | 2 |
| `compiler/ast/AssignmentStatement.java` | 1 |
| `compiler/ast/CaseStatement.java` | 1 |
| `compiler/ast/CmpChainExpression.java` | 1 |
| `compiler/ast/CompositionNode.java` | 5 |
| `compiler/ast/Context.java` | 1 |
| `compiler/ast/ExpressionStatement.java` | 1 |
| `compiler/ast/ForStatement.java` | 1 |
| `compiler/ast/FunctionTypeExpression.java` | 1 |
| `compiler/ast/GotoStatement.java` | 1 |
| `compiler/ast/IfStatement.java` | 1 |
| `compiler/ast/ImportStatement.java` | 2 |
| `compiler/ast/InvocationExpression.java` | 2 |
| `compiler/ast/LambdaExpression.java` | 2 |
| `compiler/ast/MapExpression.java` | 1 |
| `compiler/ast/MethodDeclarationStatement.java` | 2 |
| `compiler/ast/NameExpression.java` | 1 |
| `compiler/ast/NameResolver.java` | 1 |
| `compiler/ast/NamedTypeExpression.java` | 3 |
| `compiler/ast/NewExpression.java` | 2 |
| `compiler/ast/Parameter.java` | 1 |
| `compiler/ast/PrefixExpression.java` | 1 |
| `compiler/ast/PropertyDeclarationStatement.java` | 2 |
| `compiler/ast/ReturnStatement.java` | 1 |
| `compiler/ast/StatementBlock.java` | 1 |
| `compiler/ast/SwitchExpression.java` | 1 |
| `compiler/ast/SwitchStatement.java` | 1 |
| `compiler/ast/TemplateExpression.java` | 2 |
| `compiler/ast/ThrowExpression.java` | 1 |
| `compiler/ast/ToIntExpression.java` | 1 |
| `compiler/ast/TryStatement.java` | 1 |
| `compiler/ast/TypeCompositionStatement.java` | 5 |
| `compiler/ast/TypedefStatement.java` | 1 |
| `compiler/ast/VariableDeclarationStatement.java` | 1 |
| `compiler/ast/WhileStatement.java` | 1 |

### javatools - JavaJIT Package (2 files)

| File | Occurrences |
|------|-------------|
| `javajit/BuildContext.java` | 1 |
| `javajit/Xvm.java` | 2 |

### javatools - Runtime Package (9 files)

| File | Occurrences |
|------|-------------|
| `runtime/ClassComposition.java` | 1 |
| `runtime/DebugConsole.java` | 15 |
| `runtime/FiberQueue.java` | 1 |
| `runtime/Frame.java` | 3 |
| `runtime/ServiceContext.java` | 1 |
| `runtime/template/_native/crypto/xRTCertificateManager.java` | 2 |
| `runtime/template/_native/reflect/xRTFunction.java` | 1 |
| `runtime/template/text/xString.java` | 1 |

### javatools - Test Files (1 file)

| File | Occurrences |
|------|-------------|
| `src/test/java/org/xvm/compiler/SourceTest.java` | 3 |

### javatools_utils (6 files)

| File | Occurrences |
|------|-------------|
| `src/main/java/org/xvm/util/ConsoleLog.java` | 1 |
| `src/main/java/org/xvm/util/Handy.java` | 4 |
| `src/main/java/org/xvm/util/PackedInteger.java` | 1 |
| `src/test/java/org/xvm/util/HandyTest.java` | 9 |
| `src/test/java/org/xvm/util/PackedIntegerTest.java` | 5 |
| `src/test/java/org/xvm/util/SetTest.java` | 10 |

### javatools_backend (1 file)

| File | Occurrences |
|------|-------------|
| `src/main/java/org/xvm/util/NonBlockingHashMap.java` | 1 |

### javatools_jitbridge (1 file)

| File | Occurrences |
|------|-------------|
| `src/main/java/org/xtclang/ecstasy/Exception.java` | 1 |

### plugin (1 file)

| File | Occurrences |
|------|-------------|
| `src/main/java/org/xtclang/plugin/XtcPluginUtils.java` | 1 |

## Diff Example

```diff
 @Override
 public String toString() {
-    StringBuilder sb = new StringBuilder();
+    var sb = new StringBuilder();
     sb.append("Return(");
     // ... rest of method
     return sb.toString();
 }
```

## Verification

```bash
# Run tests to verify
./gradlew test

# Build to verify compilation
./gradlew build
```

## Recreating This PR

This change can be done mechanically with a regex replacement:

```bash
# Pattern to match (with proper escaping for sed)
# StringBuilder\s+(\w+)\s*=\s*new\s+StringBuilder\s*\(

# Replace with:
# var $1 = new StringBuilder(
```

Or use IDE refactoring:
1. Find: `StringBuilder (\w+) = new StringBuilder(`
2. Replace: `var $1 = new StringBuilder(`

**Note**: Ensure no static fields are changed (only local variables should use `var`).

## Source Commits

- `21b1a2389` - Main StringBuilder modernization (116 files)
- `6f0dda0df` - Remaining StringBuilder modernization (10 files)

## PR Description Template

```markdown
## Summary

Modernize local `StringBuilder` declarations to use `var` keyword:
```java
// Before
StringBuilder sb = new StringBuilder();

// After
var sb = new StringBuilder();
```

This is a purely syntactic change - `var` infers the exact same type.

## Changes

- ~217 `StringBuilder` → `var` replacements
- ~126 files across 5 modules

## Test plan

- [x] All existing tests pass
- [x] Build succeeds
- [x] No behavioral changes (purely syntactic transformation)
```
