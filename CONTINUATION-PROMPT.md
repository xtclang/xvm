# XTC Compiler AST Modernization - Continuation Prompt

## Context

This is ongoing work on branch `lagergren/ccl2` to modernize the XTC compiler AST infrastructure.

## End Goals (Priority Order)

1. **Eliminate all `clone()` calls** - Replace reflection-based cloning with explicit `copy()` methods using copy constructors
2. **Eliminate all `CHILD_FIELDS` reflection** - Replace with explicit visitor pattern for child iteration
3. **Maintain semantic equivalence with master** - All copy behavior must match original clone() semantics
4. **Use `@ComputedState` annotation** - Replace meaningless `transient` keyword for computed/cached state
5. **Add `@ChildNode` annotation** - Mark child node fields with index and optional description
6. **Prefer empty collections over null** - Where semantically equivalent, convert null to empty collections

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

## Remaining Work

### 1. Continue Modern Collection Patterns
Still have ~16 `Collections.` usages remaining in:
- AstNode.java (emptyMap, singletonList)
- Context.java (emptyMap)
- AssertStatement.java (addAll - legitimate usage)
- NameResolver.java (emptyIterator - no direct replacement)

### 2. Implement Visitor Pattern
1. Design visitor interface hierarchy
2. Implement `accept()` methods on AST nodes
3. Migrate `StageMgr` to use visitors
4. Migrate parent setup to use visitors
5. Remove `CHILD_FIELDS` reflection infrastructure

### 3. Final Cleanup
1. Remove `Cloneable` interface from `AstNode`
2. Remove `clone()` method
3. Remove `fieldsForNames()` and `getChildFields()` methods
4. Remove `CHILD_FIELDS` arrays from all classes

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

## Recent Commits

```
[pending] Replace array.clone() with Arrays.copyOf() across AST classes
04f7d0a84 Modernize AST with List.of() patterns and convenience constructors
a2fbe38b3 Update plan to mark @ChildNode annotation phase as complete
8aa8c99b5 Complete @ChildNode annotations across all AST classes
3d75dabe4 Add @ChildNode annotation and apply @ComputedState across AST classes
50089d50d Modernize AST copy constructors with stream patterns and @NotNull
```

## Suggested Next Step

1. **Begin visitor pattern implementation** - Design and implement the visitor interface to replace CHILD_FIELDS iteration
2. **Replace AstNode clone() call sites with copy()** - Migrate remaining clone() usages to explicit copy()
3. **Remove reflection infrastructure** - Once visitor pattern is in place, remove CHILD_FIELDS arrays

To verify current state:
```bash
# Count classes with CHILD_FIELDS (should all have @ChildNode now)
grep -l "CHILD_FIELDS" javatools/src/main/java/org/xvm/compiler/ast/*.java | wc -l

# Verify all CHILD_FIELDS classes have @ChildNode
for f in $(grep -l "CHILD_FIELDS" javatools/src/main/java/org/xvm/compiler/ast/*.java); do
  if ! grep -q "@ChildNode" "$f"; then echo "Missing: $f"; fi
done
```
