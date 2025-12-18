# Null Handling Chaos

## The Scale of the Problem

The XVM codebase has **357 occurrences of `return null`** in the compiler packages alone. Each one represents:
- A potential `NullPointerException` waiting to happen
- Lost error information
- Ambiguous semantics (is null "not found" or "error"?)
- Code that must defensively check for null everywhere

## The Three Meanings of Null

In the XVM codebase, `null` is used to mean at least three different things:

### Meaning 1: "Not Found"

```java
// ConstantPool.java
public Constant getConstant(int index) {
    return index < 0 || index >= m_listConst.size()
        ? null  // "Not found" - index out of bounds
        : m_listConst.get(index);
}
```

### Meaning 2: "Error Occurred"

```java
// Expression.java
public Expression validate(Context ctx, TypeConstant type, ErrorListener errs) {
    if (problem) {
        errs.log(new ErrorInfo(...));
        return null;  // "Error" - validation failed
    }
    return this;
}
```

### Meaning 3: "Not Applicable"

```java
// TypeInfo.java
public TypeConstant getExtends() {
    return f_typeExtends;  // Can be null if type doesn't extend anything
}
```

**The caller cannot distinguish these cases.** All they see is `null`.

## Real Examples

### Example 1: Type Resolution

```java
// From TypeConstant.java
public TypeConstant resolveTypedefs() {
    return this;  // or null on error? Who knows!
}

// Caller must guess
TypeConstant resolved = type.resolveTypedefs();
if (resolved == null) {
    // Is this an error?
    // Or does the type simply not have typedefs?
    // Should I report an error?
    // Should I use the original type?
}
```

### Example 2: Component Lookup

```java
// From Component.java
public Component getChild(String name) {
    // Returns null if child not found
    return m_mapChildren == null ? null : m_mapChildren.get(name);
}

// Caller
Component child = parent.getChild("foo");
if (child == null) {
    // Child doesn't exist? Or name is wrong? Or internal error?
}
```

### Example 3: Method Return

```java
// From Expression.java
public TypeConstant getType() {
    checkValidated();

    if (m_oType instanceof TypeConstant type) {
        return type;
    }

    TypeConstant[] atype = (TypeConstant[]) m_oType;
    return atype.length == 0 ? null : atype[0];  // Null for void!
}
```

Here `null` means "void expression" - a legitimate semantic meaning, not an error.

## The Null Check Explosion

Because null can mean anything, code is littered with defensive checks:

```java
// Typical method body
TypeConstant type = expr.getType();
if (type != null) {
    TypeInfo info = type.ensureTypeInfo(errs);
    if (info != null) {
        MethodInfo method = info.findMethod(name);
        if (method != null) {
            TypeConstant returnType = method.getReturnType();
            if (returnType != null) {
                // Finally can do something!
            }
        }
    }
}
```

This is the **"pyramid of doom"** - nested null checks that obscure the actual logic.

## Why This Is Catastrophic for LSP

An LSP server needs to:
1. **Always return something** - can't just fail silently
2. **Explain failures** - show error messages to user
3. **Provide partial results** - even with errors
4. **Never crash** - robust against any input

With null returns:
- Don't know if operation failed
- Don't know WHY it failed
- Can't provide partial results
- One null causes cascade of failures

## The Solution: Optional and Result Types

### Solution 1: Optional for "Not Found"

```java
// Before
public Component getChild(String name) {
    return m_mapChildren == null ? null : m_mapChildren.get(name);
}

// After
public Optional<Component> getChild(String name) {
    if (m_mapChildren == null) {
        return Optional.empty();
    }
    return Optional.ofNullable(m_mapChildren.get(name));
}

// Caller - clear semantics
parent.getChild("foo")
    .ifPresent(child -> process(child));

// Or with default
Component child = parent.getChild("foo")
    .orElseThrow(() -> new NoSuchElementException("Child 'foo' not found"));
```

### Solution 2: Result Type for Operations That Can Fail

```java
// Define result type
public sealed interface LookupResult<T> permits Found, NotFound, Error {
    boolean isSuccess();
}

public record Found<T>(T value) implements LookupResult<T> {
    @Override public boolean isSuccess() { return true; }
}

public record NotFound<T>(String name) implements LookupResult<T> {
    @Override public boolean isSuccess() { return false; }
}

public record Error<T>(String message, Throwable cause) implements LookupResult<T> {
    @Override public boolean isSuccess() { return false; }
}

// Usage
public LookupResult<Component> findChild(String name) {
    if (m_mapChildren == null) {
        return new NotFound<>(name);
    }
    Component child = m_mapChildren.get(name);
    if (child == null) {
        return new NotFound<>(name);
    }
    return new Found<>(child);
}

// Caller - exhaustive handling
switch (parent.findChild("foo")) {
    case Found(var child) -> process(child);
    case NotFound(var name) -> reportMissing(name);
    case Error(var msg, var cause) -> reportError(msg);
}
```

### Solution 3: Validation Result Type

```java
// For validation operations
public sealed interface ValidationResult permits Valid, Invalid {
    List<Diagnostic> getDiagnostics();
}

public record Valid(Expression expr, List<Diagnostic> warnings) implements ValidationResult {
    @Override
    public List<Diagnostic> getDiagnostics() { return warnings; }
}

public record Invalid(List<Diagnostic> errors) implements ValidationResult {
    @Override
    public List<Diagnostic> getDiagnostics() { return errors; }
}

// Before
public Expression validate(Context ctx, TypeConstant type, ErrorListener errs) {
    if (problem) {
        errs.log(...);
        return null;
    }
    return this;
}

// After
public ValidationResult validate(Context ctx, TypeConstant type) {
    if (problem) {
        return new Invalid(List.of(new Diagnostic(...)));
    }
    return new Valid(this, List.of());
}
```

## Migration Strategy

### Phase 1: Add @Nullable/@NonNull Annotations

Use JSpecify or Checker Framework annotations to document current behavior:

```java
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public @Nullable Component getChild(String name) {
    // Now explicitly documented as nullable
}

public @NonNull String getName() {
    // Guaranteed non-null
}
```

### Phase 2: Enable Static Analysis

Add NullAway or Checker Framework to build:

```groovy
// build.gradle.kts
dependencies {
    errorprone("com.uber.nullaway:nullaway:0.10.18")
}
```

This will flag:
- Null dereferences
- Missing null checks
- Inconsistent annotations

### Phase 3: Convert "Not Found" to Optional

```java
// Find methods that return null for "not found"
// Convert to Optional

// Before
public TypeInfo findTypeInfo(TypeConstant type) {
    return cache.get(type);  // null if not cached
}

// After
public Optional<TypeInfo> findTypeInfo(TypeConstant type) {
    return Optional.ofNullable(cache.get(type));
}
```

### Phase 4: Convert Error Returns to Result Types

```java
// Find methods that return null for "error"
// Convert to Result type

// Before
public Expression validate(...) {
    if (error) return null;
    return this;
}

// After
public ValidationResult validate(...) {
    if (error) return new Invalid(...);
    return new Valid(this);
}
```

### Phase 5: Use Empty Collections Instead of Null

```java
// Before
public List<Component> getChildren() {
    return m_children;  // Might be null!
}

// After
public List<Component> getChildren() {
    return m_children != null ? m_children : List.of();
}

// Or better - never store null
private List<Component> m_children = new ArrayList<>();

public List<Component> getChildren() {
    return List.copyOf(m_children);  // Never null, defensive copy
}
```

## Common Patterns to Replace

| Current Pattern | Replacement |
|-----------------|-------------|
| `return null;` (not found) | `return Optional.empty();` |
| `return null;` (error) | `return new Failure(...);` |
| `if (x == null) return null;` | Use `Optional.map()` |
| `x != null ? x.foo() : null` | `Optional.ofNullable(x).map(X::foo)` |
| `List<T> list` (nullable) | `List<T> list = List.of()` |
| `Map<K,V> map` (nullable) | `Map<K,V> map = Map.of()` |

## The Numbers

| Pattern | Count | Impact |
|---------|-------|--------|
| `return null` | 357 | Silent failures |
| `if (x == null)` checks | 1000+ | Code bloat |
| `@Nullable` annotations | 0 | No documentation |
| Methods returning Optional | ~5 | Rare good practice |
| Result types | 0 | No error information |

## Summary

The null chaos means:
- Can't distinguish "not found" from "error" from "not applicable"
- Every method call requires defensive null checks
- Error information is lost
- LSP cannot provide useful error messages
- NullPointerExceptions are waiting to happen

**The fix:**
1. Document current nullability with annotations
2. Enable static analysis
3. Convert to Optional for "not found"
4. Convert to Result types for "error"
5. Use empty collections instead of null collections

This is not optional for a modern codebase. Every serious Java project uses these patterns now.
