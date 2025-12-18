# The Real Solution: Immutability and Explicit Copies

## The Goal: Immutability

The end goal is **not better copying** - it's **no copying at all**.

```java
// IDEAL: Immutable class with final fields
public final class Token {
    private final Id id;
    private final long position;
    private final @Nullable Object value;

    public Token(final Id id, final long position, final @Nullable Object value) {
        this.id = id;
        this.position = position;
        this.value = value;
    }

    // Copy-on-write for "modifications"
    public Token withPosition(final long newPosition) {
        return newPosition == this.position
            ? this
            : new Token(this.id, newPosition, this.value);
    }

    // Getters only - no setters
    public Id id() { return id; }
    public long position() { return position; }
    public @Nullable Object value() { return value; }
}
```

**When everything is immutable, you don't copy - you share.**

## But We Have Legacy Mutable Code

During migration, when you must copy mutable objects, the solution is **explicit copy methods** - not reflection magic, not annotations, not `Unsafe`.

### The Simple Copyable Interface

```java
public interface Copyable<T> {
    /**
     * Creates an independent copy. The implementation must be EXPLICIT
     * about what gets copied and what gets reset.
     */
    T copy();
}
```

### Explicit Copy Methods (Correct)

```java
public class Constant implements Copyable<Constant> {
    private final Format format;
    private final Object value;

    // Cached/computed fields - NOT copied
    private int refCount;
    private int position = -1;
    private @Nullable Object cachedResult;

    @Override
    public Constant copy() {
        // EXPLICIT about every field
        final var copy = new Constant(this.format, this.value);
        // refCount stays 0 (default)
        // position stays -1 (default)
        // cachedResult stays null (default)
        return copy;
    }
}
```

The copy method is **readable** - anyone can see exactly what gets copied.

### Arrays: Use Standard Library Methods

```java
// WRONG: manual loop or clone()
int[] copy = (int[]) original.clone();

// CORRECT: Arrays.copyOf
int[] copy = Arrays.copyOf(original, original.length);

// For objects, same thing
Token[] copy = Arrays.copyOf(tokens, tokens.length);

// For deep copy when elements are mutable (avoid this situation!)
Token[] deepCopy = Arrays.stream(tokens)
    .map(Token::copy)
    .toArray(Token[]::new);
```

### Collections: Use Immutable Copies

```java
// WRONG: mutable copy
List<Token> copy = new ArrayList<>(original);

// CORRECT: immutable copy
List<Token> copy = List.copyOf(original);

// For maps
Map<String, Token> copy = Map.copyOf(original);

// For sets
Set<Token> copy = Set.copyOf(original);
```

## Why NOT Reflection-Based Copying

A reflection-based `CopyUtils` with `@CopyIgnore` annotations is **wrong**:

| Problem | Why It's Bad |
|---------|--------------|
| Uses `sun.misc.Unsafe` | Internal API, breaks in newer Java, unsafe |
| Reflection is slow | 10-100x slower than direct field access |
| Not explicit | Hidden behavior based on annotations |
| Bypasses constructors | Violates class invariants |
| Hard to debug | Stack traces are unreadable |
| Breaks with obfuscation | Field names change |
| Doesn't compose | Nested objects need special handling |

**Rule: If you can't read the copy logic by looking at the code, it's wrong.**

## Migration Path

### Phase 1: Document Intent with Comments

Before changing any code, document what fields should NOT be copied:

```java
public abstract class Constant implements Cloneable {
    // Core data - MUST be copied
    private final Format format;
    private final Object value;

    // Computed/cached - should NOT be copied
    private transient int m_cRefs;      // Reset to 0
    private transient int m_iPos = -1;  // Reset to -1
    private transient Object m_oValue;  // Reset to null
```

### Phase 2: Write Explicit Copy Methods

Replace `clone()` with explicit `copy()`:

```java
public abstract class Constant implements Copyable<Constant> {
    @Override
    public Constant copy() {
        return new ConcreteConstant(this.format, this.value);
        // m_cRefs, m_iPos, m_oValue are NOT copied - they use defaults
    }
}
```

### Phase 3: Make Fields Final

Once copying is explicit, make fields final:

```java
public abstract class Constant {
    private final Format format;
    private final Object value;

    // These become method-local or computed on demand
    // private int m_cRefs;   // GONE - tracked externally
    // private int m_iPos;    // GONE - tracked in ConstantPool
    // private Object m_oValue; // GONE - computed when needed
```

### Phase 4: Eliminate Copying Entirely

With immutable objects, copying becomes unnecessary:

```java
// Before: defensive copy
public Token getToken() {
    return this.token.copy();  // Caller might mutate it
}

// After: immutable, just return it
public Token token() {
    return this.token;  // Can't mutate, safe to share
}
```

## Examples of Correct Explicit Copies

### Simple Value Object

```java
public final class Position implements Copyable<Position> {
    private final int line;
    private final int column;

    @Override
    public Position copy() {
        return new Position(this.line, this.column);
    }
}
```

### Object with Arrays

```java
public final class TokenSequence implements Copyable<TokenSequence> {
    private final Token[] tokens;

    @Override
    public TokenSequence copy() {
        return new TokenSequence(Arrays.copyOf(this.tokens, this.tokens.length));
    }
}
```

### Object with Collections

```java
public final class SymbolTable implements Copyable<SymbolTable> {
    private final Map<String, Symbol> symbols;

    @Override
    public SymbolTable copy() {
        return new SymbolTable(Map.copyOf(this.symbols));
    }
}
```

### Object with Nested Copyable

```java
public final class ClassStructure implements Copyable<ClassStructure> {
    private final String name;
    private final List<MethodStructure> methods;  // MethodStructure is Copyable

    @Override
    public ClassStructure copy() {
        final var copiedMethods = this.methods.stream()
            .map(MethodStructure::copy)
            .toList();
        return new ClassStructure(this.name, copiedMethods);
    }
}
```

## The Destination: Records

For new code, use Java records - immutability is built-in:

```java
public record Token(Id id, long position, @Nullable Object value) {
    // Automatic: final fields, equals, hashCode, toString

    // Copy-on-write for modifications
    public Token withPosition(final long newPosition) {
        return newPosition == this.position
            ? this
            : new Token(id, newPosition, value);
    }
}
```

## Summary

| Approach | Verdict                                                          |
|----------|------------------------------------------------------------------|
| `clone()` | ❌ Broken - shallow copy, wrong semantics                         |
| Reflection + @CopyIgnore | ❌ Over-engineered, slow, fragile, implicit                       |
| Explicit `copy()` methods | ✅ Readable, correct, debuggable                                  |
| Immutable + copy-on-write | ✅ **Best** - no copying needed                                   |
| Java records | ✅ **Ideal** for new code, not appropriate for exactly everything |

**The goal is immutability. Explicit copying is a migration step, not the destination.**
