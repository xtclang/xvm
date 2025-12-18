# Arrays vs Collections Anti-Patterns

## The Short Version

The XTC codebase uses raw Java arrays (`TypeConstant[]`, `Expression[]`, etc.) where modern Java would use type-safe collections (`List<TypeConstant>`, `Set<Expression>`). This creates multiple problems: arrays are mutable and can be corrupted, they provide no type safety at compile time, they're awkward to work with, and they can't be made immutable. For an LSP that needs to share data safely across threads, arrays are poison.

## What Is the Problem?

Java arrays have fundamental design problems:

1. **Covariant, not invariant**: `String[]` is a subtype of `Object[]`, which allows runtime `ArrayStoreException`
2. **Mutable**: No way to make an array immutable after creation
3. **No bounds checking at compile time**: Out of bounds errors are runtime-only
4. **Awkward APIs**: Arrays don't have methods like `map`, `filter`, `contains`, `find`
5. **Clone confusion**: `array.clone()` is shallow - elements are shared

## Where Does XTC Do This?

### Type Arrays (Throughout the codebase)

```java
// TypeConstant.java, ClassStructure.java, etc.
TypeConstant[] atypes = new TypeConstant[count];
// Returned from methods
public TypeConstant[] getParamTypes()
// Stored in fields
private TypeConstant[] m_atypeParams;
```

Found in: 73+ locations across 30+ files

### Expression Arrays

```java
// Expression.java and subclasses
Expression[] aexpr = new Expression[count];
// Stored directly
private Expression[] m_aExprArgs;
```

### Method/Property Arrays

```java
// MethodStructure.java, ClassStructure.java
MethodStructure[] methods = new MethodStructure[count];
PropertyStructure[] properties = struct.getProperties();
```

### Annotation Arrays

```java
// TypeInfo.java - constructor takes arrays
Annotation[] aannoClass,
Annotation[] aannoMixin,
```

### The Numbers

| Pattern | Count | Files |
|---------|-------|-------|
| `TypeConstant[]` | 73+ | 30+ |
| `Expression[]` | 50+ | 20+ |
| `Constant[]` | 40+ | 20+ |
| `MethodStructure[]` | 15+ | 10+ |
| `new *Constant[` | 100+ | 50+ |

## Why Does This Block LSP?

### Problem 1: Shared Mutable Arrays

```java
// Compiler code
TypeConstant[] types = getParamTypes();  // Returns internal array
types[0] = somethingElse;                // CORRUPTS original!

// What should happen
List<TypeConstant> types = getParamTypes();  // Returns defensive copy
// Can't accidentally modify original
```

LSP handlers run concurrently. If one handler modifies a shared array, others see corrupted data.

### Problem 2: No Empty Array Safety

```java
// Common pattern
TypeConstant[] types = method.getParamTypes();
if (types.length > 0) {  // Must check
    TypeConstant first = types[0];
}

// With List
method.getParamTypes().stream().findFirst().ifPresent(first -> ...);  // Safe
```

### Problem 3: Covariance Bugs

```java
// This compiles but crashes at runtime
Object[] objects = new String[5];
objects[0] = Integer.valueOf(42);  // ArrayStoreException!

// With generics, this is caught at compile time
List<String> strings = new ArrayList<>();
// strings.add(42);  // Won't compile
```

### Problem 4: Defensive Copying Everywhere

```java
// Current code must do this to be safe
public TypeConstant[] getTypes() {
    return m_types.clone();  // Defensive copy
}

// But callers don't know if they got a copy or original
TypeConstant[] types = getTypes();  // Copy? Original? Who knows!
```

### Problem 5: No Immutable Arrays

```java
// Can't make arrays immutable
public static final TypeConstant[] EMPTY = new TypeConstant[0];
// ...
EMPTY[0] = something;  // Would corrupt the "constant"!

// With collections
public static final List<TypeConstant> EMPTY = List.of();  // Truly immutable
```

## Real Examples from the Codebase

### Example 1: TypeInfo Constructor

```java
// TypeInfo.java
public TypeInfo(
    // ... other params ...
    Annotation[] aannoClass,      // Mutable array
    Annotation[] aannoMixin,      // Mutable array
    // ...
) {
    f_aannoClass = validateAnnotations(aannoClass);
    f_aannoMixin = validateAnnotations(aannoMixin);
}

// What if caller modifies array after construction?
Annotation[] annos = new Annotation[] { a, b };
TypeInfo info = new TypeInfo(..., annos, ...);
annos[0] = c;  // Did we corrupt info.f_aannoClass?
```

### Example 2: Method Parameter Types

```java
// Common pattern
TypeConstant[] atypeParams = method.getParamTypes();
for (int i = 0; i < atypeParams.length; i++) {
    TypeConstant type = atypeParams[i];
    // ... process ...
}

// Modern approach
method.getParamTypes().forEach(type -> {
    // ... process ...
});
```

### Example 3: Building Result Arrays

```java
// Current code
List<TypeConstant> list = new ArrayList<>();
// ... build list ...
return list.toArray(new TypeConstant[0]);  // Convert back to array - why?

// Should just return the List
return List.copyOf(list);  // Immutable, no conversion needed
```

## What Should Be Done Instead?

### Solution 1: Use List<T> Instead of T[]

```java
// Before
private TypeConstant[] m_atypeParams;

public TypeConstant[] getParamTypes() {
    return m_atypeParams == null ? TypeConstant.NO_TYPES : m_atypeParams;
}

// After
private List<TypeConstant> paramTypes = List.of();

public List<TypeConstant> getParamTypes() {
    return paramTypes;  // Already immutable, safe to return directly
}
```

### Solution 2: Use Immutable Collections

```java
// Use List.of() and List.copyOf()
public record MethodSignature(
    String name,
    List<TypeConstant> paramTypes,  // Immutable
    TypeConstant returnType
) {
    public MethodSignature {
        paramTypes = List.copyOf(paramTypes);  // Defensive copy on construction
    }
}
```

### Solution 3: Use Stream Operations

```java
// Before
TypeConstant[] types = method.getParamTypes();
TypeConstant[] resolved = new TypeConstant[types.length];
for (int i = 0; i < types.length; i++) {
    resolved[i] = types[i].resolveTypedefs();
}

// After
List<TypeConstant> resolved = method.getParamTypes().stream()
    .map(TypeConstant::resolveTypedefs)
    .toList();  // Returns immutable list in Java 16+
```

### Solution 4: Define Empty Constants Properly

```java
// Before (unsafe)
public static final TypeConstant[] NO_TYPES = new TypeConstant[0];

// After (safe)
public static final List<TypeConstant> NO_TYPES = List.of();

// Or use a dedicated type
public sealed interface TypeList permits EmptyTypeList, PopulatedTypeList {
    int size();
    TypeConstant get(int index);
    Stream<TypeConstant> stream();
}

public record EmptyTypeList() implements TypeList {
    public static final EmptyTypeList INSTANCE = new EmptyTypeList();
    @Override public int size() { return 0; }
    @Override public TypeConstant get(int index) { throw new IndexOutOfBoundsException(); }
    @Override public Stream<TypeConstant> stream() { return Stream.empty(); }
}
```

### Solution 5: For Performance-Critical Code

When arrays are genuinely needed for performance:

```java
public class TypeArray {
    private final TypeConstant[] types;

    private TypeArray(TypeConstant[] types) {
        this.types = types;  // Takes ownership
    }

    public static TypeArray of(TypeConstant... types) {
        return new TypeArray(types.clone());  // Defensive copy on creation
    }

    public TypeConstant get(int index) {
        return types[index];  // Safe - internal array not exposed
    }

    public int size() {
        return types.length;
    }

    public Stream<TypeConstant> stream() {
        return Arrays.stream(types);
    }

    // No way to get the internal array
}
```

## Migration Path

### Phase 1: Add Collection Getters (Week 1)

Keep array fields but add List-returning methods:

```java
// Add alongside existing array methods
public List<TypeConstant> getParamTypeList() {
    return m_atypeParams == null ? List.of() : List.of(m_atypeParams);
}

// Deprecate array method
@Deprecated
public TypeConstant[] getParamTypes() {
    return m_atypeParams == null ? NO_TYPES : m_atypeParams.clone();
}
```

### Phase 2: Update Internal Storage (Week 2)

Change fields from arrays to Lists:

```java
// Before
private TypeConstant[] m_atypeParams;

// After
private List<TypeConstant> m_paramTypes = List.of();
```

### Phase 3: Update Callers (Week 3)

Replace array iteration with collection operations:

```java
// Before
for (int i = 0; i < types.length; i++) { ... }

// After
for (TypeConstant type : types) { ... }
// Or
types.forEach(type -> ...);
```

### Phase 4: Remove Array Methods (Week 4)

After all callers updated, remove deprecated array-returning methods.

## Common Migration Patterns

| Array Pattern | Collection Equivalent |
|---------------|----------------------|
| `new T[0]` | `List.of()` |
| `new T[] {a, b}` | `List.of(a, b)` |
| `array.clone()` | `List.copyOf(list)` |
| `array.length` | `list.size()` |
| `array[i]` | `list.get(i)` |
| `for (T t : array)` | `for (T t : list)` or `list.forEach(...)` |
| `Arrays.stream(array)` | `list.stream()` |
| `list.toArray(new T[0])` | Just use the list |

## Summary

Arrays in the XTC codebase create:
- **Mutability hazards**: Can't safely share between threads
- **Type safety holes**: Runtime errors instead of compile-time
- **API awkwardness**: Manual loops instead of stream operations
- **Defensive copy confusion**: Unclear ownership

**The fix:**
1. Replace `T[]` fields with `List<T>`
2. Use `List.of()` and `List.copyOf()` for immutability
3. Return collections, not arrays
4. Use stream operations for transformations
5. Wrap performance-critical arrays in safe containers

This is standard Java best practice since Java 8 (2014). Every major Java codebase uses collections over arrays for 
application-level code. Arrays are only appropriate for low-level, performance-critical inner loops where 
profiling proves they're needed.

