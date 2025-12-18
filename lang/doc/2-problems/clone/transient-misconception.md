# The Transient Misconception

## The Short Version

The XVM codebase uses Java's `transient` keyword in way that seems to think it means "don't copy this field when cloning." It doesn't. `transient` only affects **serialization**. Every `transient` field gets copied by `clone()` just like any other field. This fundamental misunderstanding has created bugs throughout the codebase.

## What Transient Actually Means

The `transient` keyword was designed for Java serialization (turning objects into bytes to save or transmit). When an object is serialized:

- Non-transient fields are written to the byte stream
- Transient fields are **skipped** - they become default values (null, 0, false) when deserialized

```java
class User implements Serializable {
    private String username;           // Saved to stream
    private transient String password; // NOT saved - security feature
}
```

**That's all `transient` does.** It has absolutely nothing to do with cloning.

## What Clone Actually Does

`Object.clone()` performs a **bitwise copy** of the object. It copies every field, including:
- Private fields
- Final fields
- **Transient fields**

```java
class CachedValue implements Cloneable {
    private String data;
    private transient String cache;  // Developer thinks: "won't be cloned"

    public Object clone() {
        return super.clone();  // WRONG: cache IS cloned!
    }
}
```

## The XVM Misconception in Action

### The Pattern (Found Throughout the Codebase)

```java
// Constant.java - typical pattern
public abstract class Constant implements Cloneable {
    private transient int m_cRefs;    // "transient" = don't copy, right? WRONG!
    private transient int m_iPos;     // These ARE all copied by clone()!
    private transient Object m_oValue;

    public Object clone() {
        try {
            Constant that = (Constant) super.clone();
            // Developer thinks transient fields are null/0 here
            // They're actually copied from 'this'!
            return that;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

### What the Developer Intended

The naming and comments make the intent clear:
- `m_cRefs` - reference count, should be 0 in a fresh copy
- `m_iPos` - position in constant pool, should be -1 in a fresh copy
- `m_oValue` - cached computed value, should be null in a fresh copy

The developer clearly thought marking these as `transient` would make `clone()` skip them.

### What Actually Happens

1. `clone()` copies ALL fields, including transient ones
2. The clone has the SAME reference count, position, and cached value as the original
3. When the clone is added to a different constant pool, it has stale position data
4. When the clone's reference count is modified, it's starting from the wrong value

## Specific Bugs This Creates

### Bug 1: Shared Caches

```java
// AstNode.java has transient fields for cached data
private transient TypeConstant m_typeResolved;

// After cloning:
AstNode original = parseExpression();
original.validate();  // Sets m_typeResolved

AstNode copy = original.clone();
// copy.m_typeResolved points to SAME TypeConstant as original
// If that TypeConstant is mutated, both are affected
```

### Bug 2: Wrong Reference Counts

```java
// Constant.java
Constant original = pool.getConstant("hello");
original.incrementRefs();  // m_cRefs = 1

Constant copy = original.clone();
// copy.m_cRefs = 1 (should be 0 for a new copy!)

copy.incrementRefs();  // m_cRefs = 2 (but this constant is only referenced once)
```

### Bug 3: Stale Position Data

```java
// When a constant is registered in a pool, it gets a position
Constant original = ...;
pool.register(original);  // Sets m_iPos = 42

Constant copy = original.clone();
// copy.m_iPos = 42 (wrong! copy isn't in any pool yet)

otherPool.register(copy);  // May not update m_iPos if it sees non-negative value
```

## The "Transient Fields ARE Being Copied" Discovery

A careful read of `Constant.adoptedBy()` reveals the developer partially understood the problem:

```java
// Constant.java
public Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) this.clone();
    that.m_cRefs = 0;  // Manually reset after clone!
    // But m_iPos and m_oValue are NOT reset!
    return that;
}
```

**This is the smoking gun.** If `transient` worked as the developer assumed, there would be no need to manually reset `m_cRefs`. The fact that it's manually reset proves they discovered (for one field) that `clone()` copies transient fields.

But they didn't fix the fundamental misunderstanding - they just added a patch for one field and left the others broken.

## The Scale of the Problem

| Class | Transient Fields | Purpose | Bug Status |
|-------|------------------|---------|------------|
| `Constant` | `m_cRefs`, `m_iPos`, `m_oValue` | Caching | Only `m_cRefs` reset |
| `AstNode` | `m_stage`, cached types | Compilation state | Not reset |
| `Expression` | `m_oType`, `m_oConst` | Validation results | Not reset |
| `Component` | Various caches | Structure caches | Not reset |

There are approximately **200+ transient fields** across the codebase. Every single one is being copied by `clone()`.

## Why This Happened: Imperative Thinking

This bug reveals a deeper problem: **the developers are thinking imperatively, not declaratively**.

Imperative thinking: "I'll mark this field `transient` and the system will know not to copy it."

The reality: Java's `clone()` and `transient` are two completely unrelated features. `transient` is a hint for serialization. `clone()` copies bytes. There is no magical connection between them.

This is what happens when you:
1. Don't read documentation carefully
2. Assume language features work the way you want them to
3. Don't have tests that verify cloning behavior
4. Don't use static analysis tools that would catch this

## The Solution: @CopyIgnore Annotation

Since `transient` doesn't work for our purposes, we need an annotation that actually does what we want:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CopyIgnore {
    /**
     * The default value to use in copies. Empty string means null/0/false.
     */
    String defaultValue() default "";
}
```

Then implement copy methods that respect it:

```java
public interface Copyable<T> {
    T copy();
}

public abstract class Constant implements Copyable<Constant> {
    @CopyIgnore private int m_cRefs;
    @CopyIgnore private int m_iPos = -1;
    @CopyIgnore private Object m_oValue;

    @Override
    public Constant copy() {
        // Framework-provided copy that respects @CopyIgnore
        return CopyUtils.copy(this);
    }
}
```

See [CopyIgnore Solution](./copyignore-solution.md) for the complete implementation.

## Summary

| What They Thought | What's Actually True |
|-------------------|---------------------|
| `transient` = "don't clone" | `transient` = "don't serialize" |
| Clone skips transient fields | Clone copies ALL fields |
| No manual reset needed | Must reset every transient field manually |
| Marking transient is enough | Need custom copy logic |

**The fix is not to find a clever way to make `transient` work with `clone()`. The fix is to stop using `clone()` entirely and implement proper copy mechanisms that do what we actually need.**

This is covered in the [Clone-to-Copy Migration Guide](./migration.md).
