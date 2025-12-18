# Reflection-Based Child Traversal

## The Problem

Every AST node class in XVM uses **reflection** to discover and traverse its child nodes. This is done through a `CHILD_FIELDS` array that stores `java.lang.reflect.Field` objects, populated at class load time using `fieldsForNames()`.

**This is one of the most architecturally unsound decisions in the codebase.** It creates:
- Performance overhead on every traversal
- Runtime errors instead of compile-time errors
- Brittle code that breaks silently when fields are renamed
- Inability to use modern Java features like sealed types

## The Pattern

Every AST node follows this pattern:

```java
// ForEachStatement.java
public class ForEachStatement extends ConditionalStatement {
    // Child fields (names must exactly match string literals below)
    protected List<AstNode> conds;
    protected StatementBlock block;

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    // Static initialization uses REFLECTION to find fields BY NAME
    private static final Field[] CHILD_FIELDS =
        fieldsForNames(ForEachStatement.class, "conds", "block");
}
```

### The `fieldsForNames` Implementation

```java
// AstNode.java line 1865-1901
protected static Field[] fieldsForNames(Class clz, String... names) {
    if (names == null || names.length == 0) {
        return NO_FIELDS;
    }

    Field[] fields = new Field[names.length];
    NextField: for (int i = 0, c = fields.length; i < c; ++i) {
        Class clzTry = clz;
        NoSuchFieldException eOrig = null;
        while (clzTry != null) {
            try {
                Field field = clzTry.getDeclaredField(names[i]);  // REFLECTION!
                // ... validation ...
                fields[i] = field;
                continue NextField;
            } catch (NoSuchFieldException e) {
                // Walk up inheritance hierarchy
                clzTry = clzTry.getSuperclass();
            }
        }
    }
    return fields;
}
```

### How Clone Uses It

```java
// AstNode.java clone() method
@Override
public AstNode clone() {
    AstNode that = (AstNode) super.clone();

    for (Field field : getChildFields()) {  // Iterate reflective Fields
        Object oVal;
        try {
            oVal = field.get(this);  // REFLECTION: get field value
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        if (oVal != null) {
            if (oVal instanceof AstNode node) {
                AstNode nodeNew = node.clone();
                that.adopt(nodeNew);
                oVal = nodeNew;
            } else if (oVal instanceof List list) {
                // Clone list contents...
            }

            try {
                field.set(that, oVal);  // REFLECTION: set field value
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
    return that;
}
```

## Why This Is Catastrophically Bad

### Problem 1: Compile-Time Safety Destroyed

The field names are **strings**. If you rename a field, the string doesn't change, and you get a runtime error:

```java
// Before
protected Expression expr;
private static final Field[] CHILD_FIELDS = fieldsForNames(Foo.class, "expr");

// After refactoring (someone renames the field)
protected Expression expression;  // Renamed!
private static final Field[] CHILD_FIELDS = fieldsForNames(Foo.class, "expr");  // STILL "expr"!

// Result: NoSuchFieldException at class load time
// Or worse: field silently not found in superclass, wrong field used
```

**IDE refactoring tools cannot help you.** When you rename a field, the IDE doesn't know to update the string literal.

### Problem 2: Performance Overhead

Every field access through reflection is **10-100x slower** than direct field access:

```java
// Direct field access (nanoseconds)
AstNode child = node.expr;

// Reflective field access (microseconds)
Field field = fields[0];
field.setAccessible(true);  // Security check
AstNode child = (AstNode) field.get(node);  // Boxing, type checking
```

In the clone operation, this happens for **every field of every node** in potentially large AST trees.

### Problem 3: Type Safety Lost

The `getChildFields()` return type is `Field[]` - raw reflection objects. There's no compile-time guarantee that:
- The fields actually exist
- The fields are the right type (AstNode or List<AstNode>)
- The field names are spelled correctly
- The field count matches expectations

### Problem 4: Every AST Class Has Boilerplate

There are **58+ AST node classes** with this pattern:

```java
// Every. Single. Class. Has. This.
@Override
protected Field[] getChildFields() {
    return CHILD_FIELDS;
}

private static final Field[] CHILD_FIELDS = fieldsForNames(ThisClass.class, "field1", "field2", ...);
```

That's 116+ lines of boilerplate that could be zero with proper design.

### Problem 5: No Exhaustive Iteration

You can't use `switch` or pattern matching on children:

```java
// Can't do this
for (var child : node.children()) {
    switch (child) {
        case Expression e -> process(e);
        case Statement s -> process(s);
        // Compiler can't verify exhaustiveness
    }
}
```

Because `children()` returns a dynamic iterator based on reflection, not a typed structure.

### Problem 6: Cannot Optimize

The JIT compiler **cannot inline or optimize** reflective code paths. Every `field.get()` and `field.set()` is a full method call with:
- Access checks
- Type checks
- Boxing/unboxing
- No escape analysis

### Problem 7: Maintenance Nightmare

When you add a new child field, you must:
1. Add the field declaration
2. Add the field name to the `CHILD_FIELDS` array string list
3. Make sure the spelling matches exactly
4. Make sure the order matches (if order matters anywhere)
5. Run tests to verify you didn't break anything

There's no compiler to tell you if you forgot step 2.

## The Scale of the Problem

| Aspect | Count |
|--------|-------|
| AST node classes | 58+ |
| `CHILD_FIELDS` declarations | 58+ |
| `getChildFields()` overrides | 58+ |
| String literals for field names | 150+ |
| Potential typo/mismatch bugs | 150+ |
| Reflective field accesses per clone | O(n) where n = tree size |

## What It Should Be Instead

### Option 1: Abstract Method (Zero Reflection)

```java
public abstract class AstNode {
    /**
     * Returns all child nodes. Subclasses implement directly.
     */
    public abstract List<AstNode> children();
}

// Each subclass implements directly - no reflection, no strings
public class ForEachStatement extends ConditionalStatement {
    protected List<AstNode> conds;
    protected StatementBlock block;

    @Override
    public List<AstNode> children() {
        List<AstNode> result = new ArrayList<>();
        result.addAll(conds);
        if (block != null) result.add(block);
        return result;
    }
}
```

**Benefits:**
- Compile-time type checking
- IDE refactoring works
- JIT can inline
- No reflection overhead

### Option 2: Sealed Types + Records (Modern Java)

```java
public sealed interface Statement permits
    ForEachStatement, ForStatement, WhileStatement, IfStatement, ... {

    List<AstNode> children();
}

public record ForEachStatement(
    List<AstNode> conditions,
    StatementBlock block
) implements Statement {

    @Override
    public List<AstNode> children() {
        var result = new ArrayList<AstNode>(conditions);
        if (block != null) result.add(block);
        return result;
    }
}
```

**Benefits:**
- Exhaustive pattern matching
- Immutable by default
- Automatic equals/hashCode
- No clone needed (records are values)

### Option 3: Visitor Pattern

```java
public interface AstVisitor<R> {
    R visit(ForEachStatement stmt);
    R visit(ForStatement stmt);
    R visit(WhileStatement stmt);
    // ... one method per node type
}

public abstract class AstNode {
    public abstract <R> R accept(AstVisitor<R> visitor);
}

public class ForEachStatement extends Statement {
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

// Usage - compiler enforces all cases handled
class NodeCounter implements AstVisitor<Integer> {
    @Override
    public Integer visit(ForEachStatement stmt) {
        return 1 + stmt.conds.stream().mapToInt(c -> c.accept(this)).sum()
                 + stmt.block.accept(this);
    }
    // ...
}
```

**Benefits:**
- Type-safe traversal
- Compiler enforces handling all node types
- Can return values
- Can accumulate state

## Migration Path

### Step 1: Add Abstract `children()` Method

Add to `AstNode`:
```java
public abstract List<AstNode> children();
```

### Step 2: Implement in Each Subclass

For each of the 58 classes, add:
```java
@Override
public List<AstNode> children() {
    // Direct implementation, no reflection
}
```

### Step 3: Update Clone to Use `children()`

```java
@Override
public AstNode clone() {
    AstNode that = (AstNode) super.clone();

    // Use the abstract method, not reflection
    for (AstNode child : children()) {
        AstNode childClone = child.clone();
        // ... need a way to set children too
    }
    return that;
}
```

### Step 4: Add Abstract `withChildren()` for Immutable Updates

```java
public abstract AstNode withChildren(List<AstNode> newChildren);
```

### Step 5: Delete Reflection Infrastructure

- Remove `CHILD_FIELDS` from all classes
- Remove `getChildFields()` method
- Remove `fieldsForNames()` helper
- Remove `ChildIteratorImpl` that uses reflection

**Estimated effort**: 2-3 days for a developer familiar with the codebase.

## Summary

The reflection-based child traversal:
- Uses strings to reference fields (no compile-time checking)
- Is 10-100x slower than direct access
- Cannot be optimized by JIT
- Requires boilerplate in every AST class
- Breaks silently when fields are renamed
- Prevents modern Java patterns (sealed types, records)

**This is a textbook example of using the hardest possible solution to a simple problem.** 
The "simple" solution of just implementing an abstract method is:
- Faster
- Safer
- More maintainable
- Works with IDEs
- Works with modern Java

There is no benefit to the reflection approach. It likely exists because it was a clever way of avoid adding code 
everywhere in many locations. 
