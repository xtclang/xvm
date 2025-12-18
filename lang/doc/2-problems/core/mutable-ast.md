# The Mutable AST Problem

## The Core Issue

The XVM compiler's AST (Abstract Syntax Tree) is **fully mutable**. Nodes can be modified at any point during compilation:
- Fields can be changed
- Children can be replaced
- Parents can be reassigned
- Compilation state is stored directly in nodes

**This design makes it impossible to:**
1. Have multiple concurrent compilations
2. Query the AST while compilation is in progress
3. Safely share AST nodes between threads
4. Implement incremental compilation
5. Build an LSP server that responds while compiling

## The Mutation Points

### Field Mutation

```java
// AstNode.java
public abstract class AstNode {
    private AstNode m_parent;           // Mutable
    private Stage m_stage = Stage.Initial;  // Mutable

    protected void setParent(AstNode parent) {
        this.m_parent = parent;  // Can change at any time
    }

    protected void setStage(Stage stage) {
        if (stage.compareTo(m_stage) > 0) {
            m_stage = stage;  // Changes during compilation
        }
    }
}
```

### Expression Type Storage

```java
// Expression.java
public abstract class Expression extends AstNode {
    private Object m_oType;   // Set during validation
    private Object m_oConst;  // Set during validation
    private TypeFit m_fit;    // Set during validation
    private int m_nFlags;     // Modified throughout

    protected void finishValidation(..., TypeConstant[] atypeActual, ...) {
        // MUTATES the expression during validation
        m_oType = ...;
        m_oConst = ...;
    }
}
```

### Child Replacement

```java
// AstNode.java
public void replaceChild(AstNode nodeOld, AstNode nodeNew) {
    ChildIterator children = children();
    while (children.hasNext()) {
        if (children.next() == nodeOld) {
            children.replaceWith(nodeNew);  // MUTATES the AST!
            return;
        }
    }
}
```

### Statement Block Mutation

```java
// StatementBlock.java
public class StatementBlock extends Statement {
    private List<Statement> stmts;  // Mutable list

    public void addStatement(int index, Statement stmt) {
        adopt(stmt);
        stmts.add(index, stmt);  // MUTATES during compilation
    }
}
```

## Why Mutability Is Catastrophic

### Problem 1: Can't Query While Compiling

LSP needs to answer "what's the type of this variable?" while the user is typing. But:

```java
// Thread 1: User requests hover info
TypeConstant type = expr.getType();  // Returns current m_oType

// Thread 2: Compilation running
expr.finishValidation(...);  // Changes m_oType!

// Thread 1 now has: stale type, or partially updated type, or null
```

### Problem 2: Validation Destroys Original

When validation runs, it **overwrites** the AST with resolved information:

```java
// Before validation
NameExpression name = ...;
// name.m_oType is null
// name.m_resolved is null

// After validation
name.validate(ctx, type, errs);
// name.m_oType is now TypeConstant
// name.m_resolved is now MethodConstant
// Original "unresolved" state is GONE
```

**If validation fails and we need to retry with different assumptions, we must CLONE the entire subtree first** (see [Clone Usage](../clone/clone-usage.md)).

### Problem 3: Thread Safety Is Impossible

```java
// This code is NOT thread-safe
public class Expression {
    private Object m_oType;  // No synchronization

    public TypeConstant getType() {
        if (m_oType instanceof TypeConstant type) {
            return type;  // Race: another thread could change m_oType between check and return
        }
        return ((TypeConstant[]) m_oType)[0];  // Race: could be changed to non-array
    }

    protected void finishValidation(..., TypeConstant[] atypeActual) {
        m_oType = atypeActual.length == 1 ? atypeActual[0] : atypeActual;  // Race!
    }
}
```

### Problem 4: Clone Required for Every Experiment

The compiler clones AST subtrees to try different interpretations:

```java
// ForStatement.java
for (AstNode cond : condOrig) {
    conds.add(cond.clone());  // Must clone to preserve original
}
block = (StatementBlock) blockOrig.clone();  // Must clone body too
```

This is:
- Expensive (deep clone of tree)
- Error-prone (clone bugs, transient field issues)
- Memory-intensive (multiple copies)

### Problem 5: No History

Once a node is modified, previous state is lost:

```java
// Can't answer: "what was this expression's type before coercion?"
// Can't answer: "what were the alternative interpretations?"
// Can't answer: "what caused this error?"
```

## What Modern Compilers Do

### Red-Green Trees (Roslyn)

Microsoft's Roslyn compiler uses **immutable red trees** backed by **shared green trees**:

```
Green Tree (immutable, shared):
┌─────────────────────────────────┐
│  GreenBinaryExpression          │
│  ├── GreenLiteral(5)            │
│  ├── GreenOperator(+)           │
│  └── GreenLiteral(3)            │
└─────────────────────────────────┘
         │
         │ (wraps)
         ▼
Red Tree (immutable, position-aware):
┌─────────────────────────────────┐
│  RedBinaryExpression            │
│  ├── position: 0                │
│  ├── parent: ...                │
│  └── green: GreenBinaryExpr     │
└─────────────────────────────────┘
```

**Benefits:**
- Green trees are immutable and can be shared
- Red trees provide position/parent info without mutation
- Incremental: change one token, reuse most of tree

### Immutable AST + Semantic Model

Many modern compilers separate syntax from semantics:

```java
// Syntax tree: immutable, represents what user wrote
public record BinaryExpression(
    Expression left,
    Token operator,
    Expression right,
    SourceRange range
) implements Expression {}

// Semantic model: separate, stores analysis results
public class SemanticModel {
    private final Map<Expression, TypeConstant> types;
    private final Map<NameExpression, Symbol> bindings;

    public TypeConstant typeOf(Expression expr) {
        return types.get(expr);
    }
}
```

**Benefits:**
- Syntax tree is never modified
- Can have multiple semantic models for same syntax
- Thread-safe by construction
- No cloning needed

## The XVM Solution

### Option 1: Separate Semantic Model

```java
// Keep AST immutable
public abstract class Expression {
    // NO m_oType, m_oConst, m_fit fields
    // Just syntax information
}

// Store validation results separately
public class ValidationContext {
    private final Map<Expression, TypeConstant> types = new IdentityHashMap<>();
    private final Map<Expression, Constant> constants = new IdentityHashMap<>();

    public void recordType(Expression expr, TypeConstant type) {
        types.put(expr, type);
    }

    public TypeConstant typeOf(Expression expr) {
        return types.get(expr);
    }
}

// Validation doesn't mutate
public ValidationResult validate(Expression expr, ValidationContext ctx) {
    TypeConstant type = computeType(expr);
    ctx.recordType(expr, type);  // Store in context, not in node
    return new ValidationSuccess(type);
}
```

### Option 2: Immutable AST with Copy-on-Write

```java
// Records are immutable
public record BinaryExpression(
    Expression left,
    Token operator,
    Expression right,
    SourceRange range,
    Optional<TypeConstant> type  // Optional - set via withType()
) implements Expression {

    // "Modification" creates new node
    public BinaryExpression withType(TypeConstant type) {
        return new BinaryExpression(left, operator, right, range, Optional.of(type));
    }

    // Children also immutable
    public BinaryExpression withLeft(Expression newLeft) {
        return new BinaryExpression(newLeft, operator, right, range, type);
    }
}
```

### Option 3: Builder Pattern for Mutation Phase

```java
// Mutable builder during parsing
public class BinaryExpressionBuilder {
    private Expression left;
    private Token operator;
    private Expression right;
    private TypeConstant type;

    public BinaryExpressionBuilder left(Expression e) { left = e; return this; }
    public BinaryExpressionBuilder type(TypeConstant t) { type = t; return this; }

    public BinaryExpression build() {
        return new BinaryExpression(left, operator, right, type);  // Immutable result
    }
}

// After parsing, freeze into immutable form
BinaryExpression expr = builder.build();
// expr is now immutable, can be shared
```

## Migration Path

### Phase 1: Add Semantic Model Class

```java
public class SemanticModel {
    private final Map<AstNode, Object> nodeData = new IdentityHashMap<>();

    public void setType(Expression expr, TypeConstant type) {
        nodeData.put(expr, type);
    }

    public TypeConstant getType(Expression expr) {
        return (TypeConstant) nodeData.get(expr);
    }
}
```

### Phase 2: Pass SemanticModel Through Validation

```java
// Old signature
Expression validate(Context ctx, TypeConstant type, ErrorListener errs);

// New signature
ValidationResult validate(Context ctx, TypeConstant type, SemanticModel model);
```

### Phase 3: Migrate Type Storage

```java
// Before (mutates node)
protected void finishValidation(..., TypeConstant[] atypeActual, ...) {
    m_oType = atypeActual.length == 1 ? atypeActual[0] : atypeActual;
}

// After (stores in model)
protected void finishValidation(..., TypeConstant[] types, SemanticModel model) {
    model.setType(this, types);
}
```

### Phase 4: Remove Mutable Fields

Once all code uses SemanticModel:

```java
public abstract class Expression {
    // DELETE:
    // private Object m_oType;
    // private Object m_oConst;
    // private TypeFit m_fit;
}
```

### Phase 5: Make AST Nodes Records

```java
// Before
public class BinaryExpression extends Expression {
    protected Expression expr1;
    protected Token operator;
    protected Expression expr2;
}

// After
public record BinaryExpression(
    Expression left,
    Token operator,
    Expression right,
    SourceRange range
) implements Expression {}
```

## Summary

The mutable AST design:
- Prevents concurrent access
- Destroys original state during validation
- Requires expensive cloning for speculative parsing
- Makes thread safety impossible
- Blocks incremental compilation
- Prevents LSP implementation

**The fix is fundamental**: Separate syntax (immutable) from semantics (stored separately). This is not optional for modern tooling - it's a prerequisite.

Every modern compiler/IDE that supports:
- Background compilation
- Hover/completion during typing
- Incremental updates
- Error tolerance

...uses immutable ASTs with separate semantic models. XVM's mutable AST is a 1990s design that simply cannot support 2025s requirements.
