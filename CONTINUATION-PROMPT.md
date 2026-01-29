# XTC Compiler AST Modernization - Continuation Prompt

## Context

This is ongoing work on branch `lagergren/ccl2` to modernize the XTC compiler AST infrastructure.

## End Goals (Priority Order)

1. **Eliminate all `clone()` calls** - Replace reflection-based cloning with explicit `copy()` methods using copy constructors
2. **Eliminate all `CHILD_FIELDS` reflection** - Replace with explicit visitor pattern for child iteration
3. **Maintain semantic equivalence with master** - All copy behavior must match original clone() semantics
4. **Use `@ComputedState` annotation** - Replace meaningless `transient` keyword for computed/cached state
5. **Add `@ChildNode` annotation** - Mark child node fields with index and optional description
6. **Add explicit typed getters** - Provide typed access to child fields with Optional for nullable, @NotNull for non-null
7. **Prefer empty collections over null** - Where semantically equivalent, convert null to empty collections

## Completed Work

### Copy Constructors - COMPLETE
All 53+ AST classes now have copy constructors and `copy()` methods.

### Stream-Based Copy Patterns - COMPLETE
All copy constructors with list copying now use clean stream-based pattern:
```java
this.list = original.list == null ? null
        : original.list.stream().map(T::copy).collect(Collectors.toCollection(ArrayList::new));
```

### @NotNull Annotations - COMPLETE
Added `@NotNull` to list fields with null-to-empty conversion in primary constructors:
- NewExpression.args, TupleTypeExpression.paramTypes
- SwitchExpression.cond/contents, TemplateExpression.exprs
- CmpChainExpression.expressions, FunctionTypeExpression.paramTypes
- ArrayAccessExpression.indexes, ReturnStatement.exprs
- ForStatement.init, ForStatement.update
- ConditionalStatement.conds, AssertStatement.conds
- TupleExpression.exprs, MapExpression.keys/values

### Modern Collection Patterns - IN PROGRESS
Replaced legacy `Collections.emptyList()`/`Collections.singletonList()` with `List.of()`:
- ForStatement, ConditionalStatement, AssertStatement, TupleExpression
- ForEachStatement, ListExpression, NamedTypeExpression
- MethodDeclarationStatement, SwitchStatement, TypeCompositionStatement
- StageMgr, AnonInnerClass, VersionOverride, IgnoredNameExpression
- CompositionNode, InvocationExpression, MapExpression

Added convenience constructors:
- `StatementBlock()` - no-arg constructor for empty blocks
- `MapExpression(type, lEndPos)` - constructor for empty maps
- `Break.withNarrow(node, mapNarrow, label)` - factory method with empty mapAssign

### Array Clone Modernization - COMPLETE
Replaced all `array.clone()` calls with explicit `Arrays.copyOf(array, array.length)`:
- Expression.java (3 replacements)
- TernaryExpression.java (1 replacement)
- InvocationExpression.java (5 replacements)
- ReturnStatement.java (1 replacement)
- LambdaExpression.java (1 replacement)
- ForEachStatement.java (1 replacement)
- SwitchExpression.java (1 replacement)
- AssignmentStatement.java (1 replacement)
- CaseManager.java (1 replacement)
- ConvertExpression.java (3 replacements)

**Total: 18 array clone() calls replaced** - All for shallow array copies (TypeConstant[], Assignable[], ExprAST[])

### @ComputedState Annotation - COMPLETE
Created and applied across all AST classes with transient fields (29+ classes total).

### Clone Elimination - COMPLETE
Removed all Cloneable interfaces and clone() methods from compiler code:
- **AstNode**: Removed `Cloneable` interface and `clone()` method; added `Copyable<AstNode>` interface
- **Token**: Removed `Cloneable`; added copy constructor and `copy()` method
- **Source** (compiler): Removed `Cloneable`; added copy constructor and `copy()` method
- **MethodStructure.Source** (nested class): Removed `Cloneable`; added copy constructor and `copy()` method
- **Parser**: Updated to use `Token.copy()` instead of `clone()`
- **Expression.testFitMultiExhaustive()**: Updated to use `copy()` instead of `clone()`
- **PropertyDeclarationStatement**: Updated to use `copy()` instead of `clone()`

Created `Copyable<T>` interface in `org.xvm.compiler.ast`:
```java
public interface Copyable<T extends Copyable<T>> {
    T copy();
}
```

**Note**: Cloneable remains in ASM layer (Constant, Component, Parameter, ObjectHandle) - out of scope per plan Appendix D.

### @ChildNode Annotation - COMPLETE
Created annotation and applied to ALL child node fields across 44+ AST classes:
```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface ChildNode {
    int index();
    String description() default "";
}
```

**Applied to all classes with CHILD_FIELDS:**
- Base expression classes: BiExpression (expr1, expr2), PrefixExpression (expr), DelegatingExpression (expr), SyntheticExpression (expr)
- Type expressions: BiTypeExpression, AnnotatedTypeExpression, ArrayTypeExpression, FunctionTypeExpression, NamedTypeExpression, NullableTypeExpression, DecoratedTypeExpression, BadTypeExpression, TupleTypeExpression
- Control flow: IfStatement, ForStatement, ForEachStatement, WhileStatement, SwitchStatement, SwitchExpression, TryStatement, CatchStatement
- Expressions: TernaryExpression, ThrowExpression, ListExpression, TupleExpression, MapExpression, TemplateExpression, LambdaExpression, StatementExpression, NotNullExpression, NonBindingExpression, ArrayAccessExpression, NameExpression, InvocationExpression, AnnotationExpression, CmpChainExpression, NewExpression, FileExpression
- Statements: CaseStatement, ReturnStatement, AssertStatement, AssignmentStatement, LabeledStatement, ExpressionStatement, ImportStatement, VariableDeclarationStatement, TypedefStatement
- Declarations: MethodDeclarationStatement, PropertyDeclarationStatement, TypeCompositionStatement, Parameter, VersionOverride
- CompositionNode and all inner classes: Extends, Annotates, Incorporates, Delegates, Import, Default
- StatementBlock

**Classes that inherit annotations from parents (no separate annotations needed):**
- BiExpression subclasses: AsExpression, ElvisExpression, RelOpExpression, CmpExpression, CondOpExpression, IsExpression, ElseExpression
- PrefixExpression subclasses: UnaryMinusExpression, UnaryPlusExpression, UnaryComplementExpression, SequentialAssignExpression
- DelegatingExpression subclasses: LabeledExpression, ParenthesizedExpression

**Classes with no child nodes (correctly have no annotations):**
- BreakStatement, ContinueStatement, GotoStatement
- KeywordTypeExpression, VariableTypeExpression, LiteralExpression
- Base classes: Expression, Statement, TypeExpression, ComponentStatement

### Visitor Pattern - COMPLETE (Infrastructure)
Created `AstVisitor<R>` interface with visit methods for all concrete AST node types:
```java
public interface AstVisitor<R> {
    // Specific visit methods (with @NotNull parameters)
    default R visit(@NotNull ForStatement stmt) { return visitStatement(stmt); }
    default R visit(@NotNull WhileStatement stmt) { return visitStatement(stmt); }
    // ... one method per concrete node type

    // Category fallback methods
    default R visitStatement(@NotNull Statement stmt) { return visitNode(stmt); }
    default R visitExpression(@NotNull Expression expr) { return visitNode(expr); }
    default R visitTypeExpression(@NotNull TypeExpression type) { return visitNode(type); }
    default R visitCompositionNode(@NotNull CompositionNode node) { return visitNode(node); }
    default R visitNode(@NotNull AstNode node) { return null; }
}
```

**Completed:**
- Created `AstVisitor.java` with visit methods for all concrete AST types
- Added abstract `accept(AstVisitor<R>)` method to `AstNode`
- Implemented `accept()` in ALL concrete AST classes (~75 classes)

### Explicit Typed Getters - IN PROGRESS
Added explicit typed getters with Optional/NotNull annotations to AST classes.

**Pattern:**
- Nullable fields → `Optional<T>` return type
- Non-null fields → `@NotNull` annotation on getter
- Nullability determined by constructor semantics (direct method calls on field = non-null)

**Completed (23 classes modified in this session):**

| Class | Getters Added |
|-------|---------------|
| LambdaExpression | `getParameters()` (Optional), `getParameterNameExpressions()` (Optional), `getOperator()`, `getBody()` (@NotNull) |
| MapExpression | `getTypeExpression()` (@NotNull), `getKeys()` (@NotNull), `getValues()` (@NotNull) |
| ArrayAccessExpression | `getArrayExpression()`, `getIndexes()` (@NotNull) |
| StatementExpression | `getBody()` (@NotNull) |
| WhileStatement | `getBlock()` (@NotNull) |
| SwitchExpression | `getKeyword()`, `getCondition()` (@NotNull), `getContents()` (@NotNull) |
| ListExpression | `getTypeExpression()` (Optional), `getExpressions()` (@NotNull) |
| ForEachStatement | `getBlock()` (@NotNull) |
| NotNullExpression | `getExpression()` (@NotNull), `getOperator()` (@NotNull) |
| BiTypeExpression | `getType1()` (@NotNull), `getOperator()` (@NotNull), `getType2()` (@NotNull) |
| TemplateExpression | `getTypeExpression()` (Optional), `getExpressions()` (@NotNull) |
| AnnotationExpression | `getTypeExpression()` (Optional), `getArguments()` (Optional) |

**Already had getters (no changes needed):**
- ForStatement - `getInit()`, `getConds()`, `getUpdate()`, `getBlock()`
- IfStatement - `getThen()` (Optional), `getElse()` (Optional)
- DelegatingExpression - `getUnderlyingExpression()`
- NameExpression - `getLeftExpression()`, `getTrailingTypeParams()`
- TupleExpression - `getTypeExpression()`, `getExpressions()`
- BiExpression - `getExpression1()`, `getOperator()`, `getExpression2()`
- PrefixExpression - `getOperator()`, `getExpression()`
- TernaryExpression - `getCondition()`, `getThenExpression()`, `getElseExpression()`
- AssignmentStatement - `getLValue()`, `getOp()`, `getRValue()`
- ReturnStatement - `getExpressions()`
- CaseStatement - `getExpressions()`, `getLabel()`
- ImportStatement - `getCondition()` (Optional)
- TryStatement - `getKeyword()`, `getResources()` (Optional), `getBlock()` (@NotNull), `getCatches()` (Optional), `getFinally()` (Optional)
- AssertStatement - `getKeyword()`, `getInterval()` (Optional), `getConditions()`, `getMessage()` (Optional)
- LabeledStatement - `getLabel()`, `getStatement()`
- CatchStatement - `getTarget()`, `getBlock()`
- SwitchStatement - `getBlock()`
- NewExpression - `getLeft()` (Optional), `getTypeExpression()` (Optional), `getArguments()` (@NotNull), `getBody()` (Optional), `getAnon()` (Optional)
- InvocationExpression - `getExpression()`, `getArguments()`
- VariableDeclarationStatement - `getTypeExpression()`

## Remaining Work

### 1. Add Explicit Child Getters to Remaining Classes
Still need getters for:
- TypeCompositionStatement (complex - many child fields)
- MethodDeclarationStatement
- PropertyDeclarationStatement
- CompositionNode and inner classes
- Parameter
- Various type expressions (FunctionTypeExpression, etc.)

### 2. Continue Modern Collection Patterns
Still have ~16 `Collections.` usages remaining in:
- AstNode.java (emptyMap, singletonList)
- Context.java (emptyMap)
- AssertStatement.java (addAll - legitimate usage)
- NameResolver.java (emptyIterator - no direct replacement)

### 3. Migrate Usages to Visitor Pattern
1. Migrate `StageMgr` to use visitors instead of `children()` iterator
2. Migrate `introduceParentage()` to use visitor pattern
3. Migrate `discard()` to use visitor pattern
4. Migrate other `children()` usages

### 4. Final Cleanup (CHILD_FIELDS removal)
1. ~~Remove `Cloneable` interface from `AstNode`~~ - DONE
2. ~~Remove `clone()` method~~ - DONE
3. Remove `children()` iterator (after all usages migrated)
4. Remove `fieldsForNames()` and `getChildFields()` methods
5. Remove `CHILD_FIELDS` arrays from all classes
6. Remove `ChildIterator` and `ChildIteratorImpl` inner classes

## Key Technical Details

### Semantic Equivalence Rules
- **CHILD_FIELDS fields**: Deep copied via `copy()` recursively
- **All other fields**: Shallow copied (same reference)
- **transient keyword**: Has NO effect on clone (purely documentation)
- **Custom clone() overrides**: Must be replicated exactly (e.g., LambdaExpression nulls m_lambda)

### Annotations
- `@ComputedState` - For computed/cached state fields (formerly transient)
- `@ChildNode(index, description)` - For child node fields (replaces CHILD_FIELDS)
- `@NotNull` - For collection fields guaranteed non-null

### Getter Patterns
- Nullable fields: `public Optional<T> getField() { return Optional.ofNullable(field); }`
- Non-null fields: `@NotNull public T getField() { return field; }`
- Determine nullability from constructor semantics (direct field dereference = non-null)

## Recent Commits

```
[pending] Add explicit typed getters to AST classes (23 files)
359b100cd Add AstVisitor interface and accept() methods to all AST classes
248a9c18e Remove Cloneable/clone() from compiler, add Copyable interface
6f6321b36 Replace array.clone() with Arrays.copyOf() across AST classes
04f7d0a84 Modernize AST with List.of() patterns and convenience constructors
a2fbe38b3 Update plan to mark @ChildNode annotation phase as complete
```

## Suggested Next Step

1. ~~Begin visitor pattern implementation~~ - DONE (AstVisitor interface and accept() methods complete)
2. ~~Replace AstNode clone() call sites with copy()~~ - DONE
3. ~~Add explicit child getters~~ - IN PROGRESS (~25+ classes done, ~15 remaining)
4. **Migrate StageMgr** - Replace `children()` usage with visitor pattern
5. **Remove reflection infrastructure** - Once all usages migrated, remove CHILD_FIELDS arrays

## Build Verification

**IMPORTANT**: Always verify changes with a full build from the project root:
```bash
# Step 1: Clean (must run alone, never combined with other tasks)
./gradlew clean

# Step 2: Full build
./gradlew build

# Step 3 (optional): Run manual tests for additional verification
./gradlew manualTests
```

To verify current state of modernization:
```bash
# Count classes with CHILD_FIELDS (should all have @ChildNode now)
grep -l "CHILD_FIELDS" javatools/src/main/java/org/xvm/compiler/ast/*.java | wc -l

# Verify all CHILD_FIELDS classes have @ChildNode
for f in $(grep -l "CHILD_FIELDS" javatools/src/main/java/org/xvm/compiler/ast/*.java); do
  if ! grep -q "@ChildNode" "$f"; then echo "Missing: $f"; fi
done

# Verify no Cloneable in compiler
grep -r "implements.*Cloneable" javatools/src/main/java/org/xvm/compiler/
```
