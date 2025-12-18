# Why Java's Clone is Broken by Design

## The Short Version

Java's `clone()` mechanism, introduced in Java 1.0, is one of the worst-designed features in the language. It looks like it should work, it compiles without errors, but it creates subtle bugs that are nearly impossible to track down. The XVM codebase uses `clone()` extensively, and this is a major source of bugs and architectural problems.

**This matters for LSP because**: If we can't safely copy AST nodes, we can't have multiple concurrent operations (hover + completion + diagnostics) working on the same code.

## Why Clone is Fundamentally Broken

### Problem 1: The Interface Lies to You

```java
public interface Cloneable {
    // This interface is EMPTY. There is no clone() method here.
}
```

You would expect an interface called "Cloneable" to have a `clone()` method, right? It doesn't. The `clone()` method is defined on `Object`, not on `Cloneable`. This means:

- You cannot call `clone()` through a `Cloneable` reference
- The compiler cannot verify that your class actually implements cloning correctly
- There is zero type safety

**Why this is terrible**: You can implement `Cloneable` and never provide a working `clone()` method. The compiler won't complain. You'll get a runtime exception instead.

### Problem 2: You Can't Actually Call Clone

```java
// This doesn't compile!
Cloneable c = getSomeCloneableObject();
Object copy = c.clone();  // Error: clone() has protected access in Object
```

The `clone()` method on `Object` is `protected`, which means you can only call it from within the class or its subclasses. So even though your object implements `Cloneable`, you can't call `clone()` on it unless the class explicitly overrides `clone()` to be `public`.

**Why this is terrible**: The whole point of an interface is to define a contract that callers can use. `Cloneable` fails at this basic requirement.

### Problem 3: The Exception is Useless

```java
public Object clone() {
    try {
        return super.clone();
    } catch (CloneNotSupportedException e) {
        // This exception will NEVER be thrown if you implement Cloneable
        throw new IllegalStateException(e);
    }
}
```

If a class implements `Cloneable`, calling `super.clone()` will never throw `CloneNotSupportedException`. But because it's a checked exception, you must catch it anyway. Every single `clone()` implementation has this useless try-catch block.

**In the XVM codebase**, you'll find this pattern in:
- `AstNode.java` line 212-218
- `Token.java` line 434-438
- `Constant.java` line 313-318
- `Component.java` line 1954-1959

It's pure boilerplate noise.

### Problem 4: Shallow Copy Creates Hidden Sharing

`Object.clone()` performs a **shallow copy**: primitive fields are copied, but reference fields just copy the pointer, not the object it points to.

```java
class Container implements Cloneable {
    private List<String> items = new ArrayList<>();

    public Object clone() {
        return super.clone();  // items list is SHARED between original and clone!
    }
}

Container original = new Container();
original.getItems().add("hello");

Container copy = (Container) original.clone();
copy.getItems().add("oops");  // This also adds "oops" to the original!

System.out.println(original.getItems());  // Prints: [hello, oops]
```

**Why this is bad**: The original and the clone share the same mutable `items` list. Modifying one modifies the other. This is the source of countless bugs.

**In the XVM codebase**: This is exactly what happens with `transient` fields (see [Transient Misconception](./transient-misconception.md)). The `clone()` method copies the reference to cached data, so the original and clone share caches. When one invalidates its cache, it corrupts the other.

### Problem 5: Constructors Are Bypassed

`Object.clone()` creates a new object **without calling any constructor**. The JVM literally allocates memory and copies bytes.

```java
class Validated implements Cloneable {
    private final int value;

    public Validated(int value) {
        if (value < 0) throw new IllegalArgumentException("value must be non-negative");
        this.value = value;
    }

    // If someone clones an object with value=-1 somehow,
    // clone() will happily copy it - no validation!
}
```

**Why this is terrible**:
1. Constructor invariants are not enforced
2. `final` fields can't be modified after `super.clone()` (you can only copy them)
3. Any initialization logic in the constructor is skipped

### Problem 6: Inheritance is a Minefield

If a parent class's `clone()` doesn't call `super.clone()`, all subclasses break:

```java
class Base implements Cloneable {
    public Base clone() {
        return new Base();  // WRONG! Doesn't call super.clone()
    }
}

class Derived extends Base {
    private int extra;

    public Derived clone() {
        Derived copy = (Derived) super.clone();  // ClassCastException!
        // super.clone() returns a Base, not a Derived
        copy.extra = this.extra;
        return copy;
    }
}
```

**Why this is terrible**: The rule "always call `super.clone()`" is enforced only by convention. There's no compiler check. If you forget, or if some parent class in a library gets it wrong, subclasses silently break.

## The XVM Clone Implementations

The codebase has 6 classes that implement `Cloneable`:

| Class | Location | Used For |
|-------|----------|----------|
| `AstNode` | `compiler/ast/AstNode.java:71` | All 80+ AST node types |
| `Token` | `compiler/Token.java:19` | Parser backtracking |
| `Contribution` | `asm/Component.java:2535` | Contribution copying |
| `Source` | `asm/MethodStructure.java:2689` | Source info copying |
| `Parameter` | `asm/Parameter.java:29` | Parameter copying |
| `ObjectHandle` | `runtime/ObjectHandle.java:47` | Runtime handle copying |

Every one of these suffers from the problems above. The `AstNode.clone()` is particularly problematic because it uses **reflection** to find child fields (see [Reflection Traversal](../data-structures/reflection-traversal.md)).

## What Should Be Used Instead

### Option 1: Copy Constructor

```java
public class Token {
    private final Id id;
    private final long position;
    private final Object value;

    // Copy constructor - explicit, clear, type-safe
    public Token(Token other) {
        this.id = other.id;
        this.position = other.position;
        this.value = other.value;  // Note: still shallow for value
    }
}
```

### Option 2: Static Factory Method

```java
public class Token {
    public static Token copyOf(Token other) {
        return new Token(other.id, other.position, other.value);
    }
}
```

### Option 3: Records (Java 16+)

```java
public record Token(Id id, long position, Object value) {
    // Records are immutable - no copying needed!
    // "Modification" creates a new instance:
    public Token withPosition(long newPos) {
        return new Token(id, newPos, value);
    }
}
```

### Option 4: Copyable Interface with Copy-on-Write

```java
public interface Copyable<T> {
    T copy();  // Returns same type, not Object
}

public class Token implements Copyable<Token> {
    private final Id id;
    private final long position;
    private final Object value;

    @Override
    public Token copy() {
        return new Token(this.id, this.position, this.value);
    }

    // Copy-on-write "setters" - only allocate when something changes
    public Token withId(final Id newId) {
        return newId == this.id ? this : new Token(newId, this.position, this.value);
    }

    public Token withPosition(final long newPosition) {
        return newPosition == this.position ? this : new Token(this.id, newPosition, this.value);
    }

    public Token withValue(final Object newValue) {
        return newValue == this.value ? this : new Token(this.id, this.position, newValue);
    }
}
```

**Key insight**: You often don't need to copy everything. Copy-on-write setters:
- Return `this` if nothing changed (no allocation)
- Only create a new instance when the value actually differs
- Enable structural sharing in immutable data structures
- Work well with reference equality checks for change detection

## Summary

Java's `clone()` mechanism:
- Has a marker interface with no methods
- Requires a protected method to be overridden as public
- Forces catching an exception that's never thrown
- Does shallow copies that create hidden sharing bugs
- Bypasses constructors and validation
- Breaks in inheritance hierarchies

**For LSP**, this means we cannot safely copy AST nodes without introducing subtle bugs. The solution is to either:
1. Build an adapter layer that extracts data into clean, immutable structures (Track A)
2. Replace `clone()` with explicit copy mechanisms throughout the codebase (Track B)

Both approaches are covered in later chapters.
