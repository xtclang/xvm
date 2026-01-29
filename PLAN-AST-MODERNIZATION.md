# XTC Compiler AST Modernization Plan

## Executive Summary

This document provides an analysis of the XTC compiler's AST implementation and a focused plan to **eliminate `clone()` and reflection-based child traversal** using explicit copy constructors.

**Immediate Goal**: Replace reflection-based `clone()` with explicit `copy()` methods using copy constructors.

**Future Goal** (out of scope): Copy-on-write stateless immutable IR where copying is just returning a reference.

---

## Current Status

### Completed Work

**Phase 1: Foundation** - COMPLETE
- Added helper methods to `AstNode`: `copyStatements()`, `copyNodes()`, `copyExpressions()`
- Added copy constructor `AstNode(AstNode original)`
- Added `copy()` method with delegation to `clone()` for backward compatibility

**Phase 2: Core Loop Statements** - COMPLETE
- `ForStatement` - copy constructor with init, conds, update, block
- `WhileStatement` - copy constructor with conds, block
- `ForEachStatement` - copy constructor with lvals, expr, block
- `IfStatement` - copy constructor with conds, stmtThen, stmtElse (uses Optional pattern)
- `StatementBlock` - copy constructor with stmts

**Additional Statement Classes** - COMPLETE (9 new classes)
- `ExpressionStatement` - copies expr child
- `ReturnStatement` - copies exprs list
- `AssertStatement` - copies interval, conds, message
- `AssignmentStatement` - copies lvalue, rvalue
- `SwitchStatement` - copies block (extends ConditionalStatement)
- `TryStatement` - copies resources, block, catches, catchall
- `CatchStatement` - copies target, block
- `VariableDeclarationStatement` - copies type

**Control Flow Statements** - COMPLETE (6 new classes)
- `GotoStatement` - base class with keyword/name tokens, abstract copy()
- `BreakStatement` - extends GotoStatement (no children)
- `ContinueStatement` - extends GotoStatement (no children)
- `LabeledStatement` - copies stmt child
- `CaseStatement` - copies exprs list
- `MultipleLValueStatement` - copies LVals list

**Import/Other Statements** - COMPLETE (1 new class)
- `ImportStatement` - copies cond child

**Expression Classes** - COMPLETE (10 new classes)
- `DelegatingExpression` - base class, copies expr child
- `PrefixExpression` - base class, copies operator/expr
- `LiteralExpression` - no children, copies literal token
- `ParenthesizedExpression` - extends DelegatingExpression
- `ThrowExpression` - copies expr, message children
- `UnaryMinusExpression` - extends PrefixExpression
- `UnaryPlusExpression` - extends PrefixExpression
- `UnaryComplementExpression` - extends PrefixExpression
- `SequentialAssignExpression` - extends PrefixExpression, copies m_fPre

**Base Classes with Copy Support**
- `AstNode` - base copy constructor and helper methods
- `Statement` - copy constructor, `copy()` delegates to `clone()`
- `Expression` - copy constructor, `copy()` delegates to `clone()`
- `ConditionalStatement` - copy constructor for keyword and conds
- `TypeExpression` - copy constructor, covariant `copy()` return type
- `DelegatingExpression` - copy constructor for delegated expr
- `PrefixExpression` - copy constructor for operator/expr

**New Infrastructure**
- `@NotCopied` annotation (`NotCopied.java`) - replaces `transient` keyword for documenting fields that shouldn't be copied (the `transient` keyword had no semantic effect since AstNode is not Serializable)

### Classes with copy() - 31 total
```
# Base classes (7)
AstNode.java (base)
Statement.java (base)
Expression.java (base)
ConditionalStatement.java (base)
TypeExpression.java (base)
DelegatingExpression.java (base)
PrefixExpression.java (base)

# Loop statements (5)
ForStatement.java
WhileStatement.java
ForEachStatement.java
IfStatement.java
StatementBlock.java

# Other statements (14)
ExpressionStatement.java
ReturnStatement.java
AssertStatement.java
AssignmentStatement.java
SwitchStatement.java
TryStatement.java
CatchStatement.java
VariableDeclarationStatement.java
GotoStatement.java
BreakStatement.java
ContinueStatement.java
LabeledStatement.java
CaseStatement.java
MultipleLValueStatement.java
ImportStatement.java

# Expressions (8)
LiteralExpression.java
ParenthesizedExpression.java
ThrowExpression.java
UnaryMinusExpression.java
UnaryPlusExpression.java
UnaryComplementExpression.java
SequentialAssignExpression.java
```

### Classes Still Needing copy() - ~55 remaining

**Statement Subclasses (~6 remaining)**
- `TypedefStatement` (extends ComponentStatement)
- `ComponentStatement` and subclasses (~10 - may not need copy as they represent structures)

**Expression Subclasses (~37 remaining)**
- Type Expressions (~12): `NamedTypeExpression`, `ArrayTypeExpression`, `TupleTypeExpression`, `FunctionTypeExpression`, `NullableTypeExpression`, `AnnotatedTypeExpression`, `BiTypeExpression`, `DecoratedTypeExpression`, `KeywordTypeExpression`, `VariableTypeExpression`, `BadTypeExpression`, `ModuleTypeExpression`
- Binary/Relational (~9): `RelOpExpression`, `CondOpExpression`, `CmpExpression`, `CmpChainExpression`, `BiExpression`, `AsExpression`, `IsExpression`, `ElvisExpression`, `ElseExpression`
- Invocation/Access (~5): `InvocationExpression`, `NewExpression`, `ArrayAccessExpression`, `NameExpression`, `IgnoredNameExpression`
- Literals/Values (~5): `ListExpression`, `MapExpression`, `TupleExpression`, `TemplateExpression`, `FileExpression`
- Other (~6): `LambdaExpression`, `TernaryExpression`, `NotNullExpression`, `NonBindingExpression`, `SwitchExpression`, `StatementExpression`

**Other AST Nodes (~12 remaining)**
- `Parameter`, `AnnotationExpression`, `CompositionNode`, `VersionOverride`
- `AnonInnerClass`, `CaseManager`, `Context`, `NameResolver`, `StageMgr` (may not need copy)

---

## Next Steps (Recommended Order)

### Immediate Priority: Classes that call clone()
These classes explicitly call clone() and should be converted first to enable testing:

1. **RelOpExpression** - calls clone() at line 434
2. **StatementExpression** - calls clone() at lines 117, 164
3. **LambdaExpression** - calls clone() at lines 709, 731, 860-865
4. **NewExpression** - calls clone() at lines 150-159, 374, 1135, 1159, 1239
5. **NamedTypeExpression** - calls clone() at lines 982-989

### Phase 3a: Binary/Relational Expressions (simpler structure)
- `BiExpression` (base class for binary ops)
- `RelOpExpression`, `CondOpExpression`, `CmpExpression`, `CmpChainExpression`
- `AsExpression`, `IsExpression`, `ElvisExpression`, `ElseExpression`

### Phase 3b: Type Expressions
- Start with `NamedTypeExpression` (most commonly used)
- Then remaining type expressions

### Phase 3c: Invocation/Access Expressions
- `NameExpression`, `InvocationExpression`, `NewExpression`, `ArrayAccessExpression`

### Phase 4: Update Call Sites
Once all expression classes have copy(), replace clone() calls with copy() in validation loops

---

## Design Patterns Established

### Copy Constructor Pattern
```java
protected MyClass(@NotNull MyClass original) {
    super(Objects.requireNonNull(original));

    // Copy non-child structural fields (immutable, safe to share)
    this.keyword = original.keyword;  // Token is immutable

    // Deep copy child fields
    this.child = original.child == null ? null : original.child.copy();
    this.children = copyStatements(original.children);

    // Adopt copied children
    adopt(this.child);
    adopt(this.children);

    // @NotCopied fields start fresh (transient compilation state)
}

@Override
public MyClass copy() {
    return new MyClass(this);
}
```

### Optional Pattern for Null-Safe Copying
```java
// Using Optional for cleaner null handling
this.stmtThen = Optional.ofNullable(original.stmtThen).map(Statement::copy).orElse(null);
this.stmtElse = Optional.ofNullable(original.stmtElse).map(Statement::copy).orElse(null);

// Adopt using Optional
getThen().ifPresent(this::adopt);
getElse().ifPresent(this::adopt);
```

### Optional-Returning Getters
```java
public Optional<Statement> getThen() {
    return Optional.ofNullable(stmtThen);
}

public Optional<Statement> getElse() {
    return Optional.ofNullable(stmtElse);
}
```

### Covariant Return Types
Each class overrides `copy()` with its own return type:
```java
// In Statement base class
public Statement copy() { return (Statement) clone(); }

// In ForStatement
@Override
public ForStatement copy() { return new ForStatement(this); }

// In TypeExpression
@Override
public TypeExpression copy() { return (TypeExpression) clone(); }
```

### @NotCopied Annotation
Replaces `transient` keyword (which had no runtime effect since AST is not Serializable):
```java
@NotCopied private Label m_labelContinue;
@NotCopied private List<Break> m_listShorts;
@NotCopied private Register m_reg;
```

---

## Part 1: Why Clone Exists

### 1.1 The Validation Loop Problem

Clone is primarily used in **validation loops** for iterative dataflow analysis. The compiler needs to determine **definite assignment** - which variables are guaranteed to be assigned at any point. In loops, this creates a chicken-and-egg problem:

```java
for (;;) {
    if (first) {
        x = 1;      // x assigned here
    }
    print(x);       // Is x definitely assigned? Depends on previous iteration!
    first = false;
}
```

The compiler must reason about what's true at the **start of the second iteration**, which depends on what happened in the **first iteration**, which it hasn't validated yet.

### 1.2 The Clone-and-Retry Solution

From `ForStatement.validateImpl()`:

```java
// Hold onto original context to track assignment changes
Context                 ctxOrig    = ctx;
Map<String, Assignment> mapLoopAsn = new HashMap<>();  // Assumptions about loop

while (true) {
    // 1. CLONE the original AST nodes
    conds = new ArrayList<>(cConds);
    for (AstNode cond : condsOrig) {
        conds.add(cond.clone());           // Clone conditions
    }
    block = (StatementBlock) blockOrig.clone();  // Clone body

    // 2. Apply current assumptions and validate
    ctx = ctxOrig.enter();
    ctx.merge(mapLoopAsn, mapLoopArg);
    // ... validate the cloned nodes (MUTATES them) ...

    // 3. Check if assumptions were wrong
    ctx.prepareJump(ctxOrig, mapAsnAfter, mapArgAfter);
    if (!mapAsnAfter.equals(mapLoopAsn)) {
        // Assumptions changed! Discard clones and retry
        mapLoopAsn = mapAsnAfter;
        for (AstNode cond : conds) {
            cond.discard(true);
        }
        continue;  // TRY AGAIN with new assumptions
    }

    // 4. Success! Discard originals, keep validated clones
    for (AstNode cond : condsOrig) {
        cond.discard(true);
    }
    break;
}
```

**Why clone?** Validation is **destructive** - it mutates AST nodes (resolves types, allocates registers, stores narrowed type info). If assumptions turn out wrong, we can't "un-validate", so we clone first, validate the clone, and discard if wrong.

### 1.3 The Problem with Current Clone

The current `AstNode.clone()` uses **reflection**:

```java
public AstNode clone() {
    AstNode that = (AstNode) super.clone();
    for (Field field : getChildFields()) {
        Object oVal = field.get(this);       // REFLECTION - slow, no type safety
        if (oVal instanceof AstNode node) {
            field.set(that, node.clone());   // REFLECTION - slow, no type safety
        } else if (oVal instanceof List list) {
            // ... more reflection ...
        }
    }
    return that;
}
```

Problems:
- Runtime reflection overhead on every clone
- No compile-time type safety (field names are strings)
- Not GraalVM/native-image friendly
- Hard to reason about what gets copied

---

## Part 2: Remaining Work

### Phase 3: Expression Subclasses (~45 classes)

Priority order:
1. Expressions that explicitly call clone(): `RelOpExpression`, `LambdaExpression`, `NewExpression`
2. Type expressions: `NamedTypeExpression`, `ArrayTypeExpression`, etc.
3. Remaining expressions

### Phase 4: Update Call Sites

Replace all `clone()` calls with `copy()`:
- Validation loops in `ForStatement`, `WhileStatement`, `ForEachStatement`
- Expression cloning in `RelOpExpression`, `LambdaExpression`, `NewExpression`
- Other scattered uses (~25 additional call sites)

### Phase 5: Cleanup

1. Remove `Cloneable` interface from `AstNode`
2. Remove `clone()` method (or keep as deprecated alias)
3. Replace remaining `transient` keywords with `@NotCopied`
4. Optionally remove `CHILD_FIELDS` reflection

---

## Appendix A: Files with Clone Calls to Update

### Validation Loop Clones (Primary Target)
- `ForStatement.java:328,332,334`
- `WhileStatement.java:235,237`
- `ForEachStatement.java:298-299`

### Expression Clones
- `RelOpExpression.java:434`
- `StatementExpression.java:117,164`
- `LambdaExpression.java:709,731,860-865`
- `NewExpression.java:150-159,374,1135,1159,1239`
- `NamedTypeExpression.java:982-989`
- `AssertStatement.java:564`

---

## Appendix B: CHILD_FIELDS Reference

Quick reference for implementing copy constructors - shows which fields are children:

```
ForStatement:        init, conds, update, block
WhileStatement:      conds, block
ForEachStatement:    lvals, conds, block
IfStatement:         conds, stmtThen, stmtElse
SwitchStatement:     conds, block
StatementBlock:      stmts
TryStatement:        resources, block, catches, catchall
CatchStatement:      target, block
AssertStatement:     interval, conds, message
ReturnStatement:     exprs
AssignmentStatement: lvalue, lvalueExpr, rvalue
ExpressionStatement: expr
VariableDeclarationStatement: type

InvocationExpression: expr, args
NewExpression:        left, type, args, anon
LambdaExpression:     params, body
NameExpression:       left, params
RelOpExpression:      expr1, expr2
TernaryExpression:    cond, exprThen, exprElse
```

(Full list in source files - search for `CHILD_FIELDS = fieldsForNames`)
