# Clone-to-Copy Migration Guide

## Overview

This chapter provides a detailed, step-by-step guide to migrating from Java's broken `clone()` mechanism to a proper copy system using `@CopyIgnore` and `Copyable<T>`.

## Is This Migration a Good Idea?

**Yes, absolutely.** Here's why:

| Aspect | Current (clone) | After Migration (copy) |
|--------|-----------------|------------------------|
| Type safety | Returns `Object` | Returns correct type `T` |
| Field control | Copies everything | Respects `@CopyIgnore` |
| Transient fields | Copied (bug!) | Properly reset |
| Compile-time checking | None | Full |
| IDE refactoring | Broken | Works |
| Documentation | None | `@CopyIgnore(reason=...)` |
| Testing | Hard to verify | Easy assertions |

**The migration can be done incrementally** - old `clone()` and new `copy()` can coexist during transition.

## Step-by-Step Migration

### Step 1: Add the Annotation (Day 1)

Create the `@CopyIgnore` annotation:

```java
// File: org/xvm/util/CopyIgnore.java
package org.xvm.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CopyIgnore {
    String reason() default "";
}
```

### Step 2: Add the Interface (Day 1)

Create the `Copyable<T>` interface:

```java
// File: org/xvm/util/Copyable.java
package org.xvm.util;

public interface Copyable<T> {
    T copy();
}
```

### Step 3: Mark Existing Transient Fields (Day 2-3)

Go through each class that has `transient` fields intended for copy semantics and add `@CopyIgnore`:

```java
// Before
public abstract class Constant implements Cloneable {
    private transient int m_cRefs;
    private transient int m_iPos = -1;
    private transient Object m_oValue;
}

// After
public abstract class Constant implements Cloneable, Copyable<Constant> {
    @CopyIgnore(reason = "Reference count starts at 0 in copies")
    private transient int m_cRefs;

    @CopyIgnore(reason = "Position assigned when added to pool")
    private transient int m_iPos = -1;

    @CopyIgnore(reason = "Cached value recomputed in new context")
    private transient Object m_oValue;
}
```

**Files to update:**
- `Constant.java` - 3 transient fields
- `AstNode.java` - stage, cached data
- `Expression.java` - type, const, flags
- `Component.java` - various caches
- `Token.java` - if any cached fields
- All 58+ AST node classes with transient fields

### Step 4: Implement copy() Alongside clone() (Week 1)

Add `copy()` methods that respect `@CopyIgnore`, while keeping `clone()` for compatibility:

```java
public abstract class Constant implements Cloneable, Copyable<Constant> {

    // Keep old clone() for now
    @Override
    @Deprecated
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    // New copy() method
    @Override
    public Constant copy() {
        try {
            Constant that = (Constant) super.clone();
            // Reset @CopyIgnore fields
            that.m_cRefs = 0;
            that.m_iPos = -1;
            that.m_oValue = null;
            return that;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

### Step 5: Create CopyUtils for Complex Cases (Week 1)

For classes with many `@CopyIgnore` fields, use reflection-based utility:

```java
// File: org/xvm/util/CopyUtils.java
package org.xvm.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CopyUtils {
    private static final Map<Class<?>, Field[]> COPY_IGNORE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T copy(T original) {
        if (original == null) return null;

        try {
            // Use clone() for the shallow copy
            T copy = (T) original.getClass().getMethod("clone").invoke(original);

            // Reset @CopyIgnore fields
            for (Field field : getCopyIgnoreFields(original.getClass())) {
                resetField(copy, field);
            }

            return copy;
        } catch (Exception e) {
            throw new RuntimeException("Copy failed for " + original.getClass(), e);
        }
    }

    private static Field[] getCopyIgnoreFields(Class<?> clazz) {
        return COPY_IGNORE_CACHE.computeIfAbsent(clazz, CopyUtils::findCopyIgnoreFields);
    }

    private static Field[] findCopyIgnoreFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(CopyIgnore.class)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields.toArray(new Field[0]);
    }

    private static void resetField(Object obj, Field field) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == int.class) field.setInt(obj, 0);
        else if (type == long.class) field.setLong(obj, 0L);
        else if (type == boolean.class) field.setBoolean(obj, false);
        else if (type == byte.class) field.setByte(obj, (byte) 0);
        else if (type == short.class) field.setShort(obj, (short) 0);
        else if (type == char.class) field.setChar(obj, '\0');
        else if (type == float.class) field.setFloat(obj, 0f);
        else if (type == double.class) field.setDouble(obj, 0d);
        else field.set(obj, null);
    }

    private CopyUtils() {}
}
```

### Step 6: Migrate Callers (Weeks 2-3)

Find all `.clone()` calls and migrate to `.copy()`:

```bash
# Find all clone() calls
grep -rn "\.clone()" javatools/src/main/java/org/xvm/
```

**Before:**
```java
// Parser.java
mark.token = m_token == null ? null : m_token.clone();

// ForStatement.java
block = (StatementBlock) blockOrig.clone();

// InvocationExpression.java
atypeParams = atypeParams.clone();  // Array clone
```

**After:**
```java
// Parser.java - Token should be immutable, but if not:
mark.token = m_token == null ? null : m_token.copy();

// ForStatement.java
block = blockOrig.copy();  // Returns StatementBlock, not Object!

// InvocationExpression.java - Use Arrays.copyOf for arrays
atypeParams = Arrays.copyOf(atypeParams, atypeParams.length);
```

### Step 7: Handle AstNode Specially (Week 2)

`AstNode.clone()` uses reflection to copy children. Replace with explicit implementation:

```java
// Before (AstNode.java)
@Override
public AstNode clone() {
    AstNode that = (AstNode) super.clone();
    for (Field field : getChildFields()) {
        // ... reflection-based child copying
    }
    return that;
}

// After
@Override
public AstNode copy() {
    AstNode that = shallowCopy();  // Uses CopyUtils, respects @CopyIgnore

    // Copy children explicitly (see reflection-traversal.md for removing reflection)
    for (AstNode child : children()) {
        // Need withChild() or similar to set children on copy
    }

    return that;
}

protected AstNode shallowCopy() {
    return CopyUtils.copy(this);
}
```

### Step 8: Add Tests (Ongoing)

Add tests to verify copy behavior:

```java
@Test
void testCopyIgnoreFieldsReset() {
    Constant original = createConstant();
    original.incrementRefs();  // m_cRefs = 1
    original.setPosition(42);  // m_iPos = 42

    Constant copy = original.copy();

    assertEquals(0, copy.getRefs(), "@CopyIgnore field should reset");
    assertEquals(-1, copy.getPosition(), "@CopyIgnore field should reset");
}

@Test
void testRegularFieldsCopied() {
    Constant original = createStringConstant("test");

    Constant copy = original.copy();

    assertEquals("test", ((StringConstant) copy).getValue());
}

@Test
void testCopyIsIndependent() {
    AstNode original = parseExpression("a + b");
    AstNode copy = original.copy();

    // Modify copy
    ((BinaryExpression) copy).setOperator(Token.MINUS);

    // Original unchanged
    assertEquals(Token.PLUS, ((BinaryExpression) original).getOperator());
}
```

### Step 9: Deprecate clone() (Week 3)

Add `@Deprecated` to all `clone()` methods:

```java
@Override
@Deprecated(forRemoval = true)
public Object clone() {
    return copy();  // Delegate to copy()
}
```

### Step 10: Remove clone() (Week 4+)

Once all callers use `copy()`:

1. Remove `implements Cloneable` from all classes
2. Remove `clone()` methods
3. Remove `@Deprecated` warnings
4. Update documentation

## Migration Order

Migrate in this order to minimize disruption:

1. **Leaf classes first**: `Token`, simple constants
2. **Then structures**: `Parameter`, `Contribution`
3. **Then AST nodes**: Start with leaf expressions, work up
4. **Finally base classes**: `Constant`, `AstNode`, `Component`

## Handling Array Cloning

Arrays use `.clone()` for defensive copying. Replace with `Arrays.copyOf()`:

```java
// Before
TypeConstant[] copy = original.clone();

// After
TypeConstant[] copy = Arrays.copyOf(original, original.length);

// Or for defensive returns
public TypeConstant[] getTypes() {
    return Arrays.copyOf(types, types.length);
}

// Better: return immutable list
public List<TypeConstant> getTypes() {
    return List.of(types);  // Unmodifiable
}
```

## Common Pitfalls

### Pitfall 1: Forgetting Child Copies

```java
// WRONG: Children not copied
@Override
public AstNode copy() {
    return CopyUtils.copy(this);  // Children are shared!
}

// RIGHT: Copy children too
@Override
public AstNode copy() {
    AstNode that = CopyUtils.copy(this);
    that.children = new ArrayList<>();
    for (AstNode child : this.children) {
        that.children.add(child.copy());
    }
    return that;
}
```

### Pitfall 2: Missing @CopyIgnore

```java
// WRONG: Forgot to mark new transient field
private transient TypeInfo m_cachedInfo;  // Will be copied!

// RIGHT: Mark it
@CopyIgnore(reason = "Cache should be recomputed")
private transient TypeInfo m_cachedInfo;
```

### Pitfall 3: Circular References

```java
// Parent-child relationships need special handling
@Override
public AstNode copy() {
    AstNode that = CopyUtils.copy(this);
    for (AstNode child : that.children()) {
        child.setParent(that);  // Re-establish parent links
    }
    return that;
}
```

## Metrics for Success

Track these during migration:

| Metric | Before | After |
|--------|--------|-------|
| `.clone()` calls | ~100 | 0 |
| `Cloneable` implementations | 6 | 0 |
| `@CopyIgnore` fields | 0 | ~200 |
| Copy-related bugs | Unknown | 0 (testable) |
| Transient fields copied incorrectly | ~200 | 0 |

## Summary

The migration:
1. Is absolutely worthwhile (fixes real bugs)
2. Can be done incrementally (old and new coexist)
3. Takes about 4 weeks for the full codebase
4. Results in type-safe, documented, testable copy operations
5. Eliminates the `transient` misconception bugs

**Start with Step 1 (add annotation) today.** Each step can be done independently, and progress is immediately beneficial.
