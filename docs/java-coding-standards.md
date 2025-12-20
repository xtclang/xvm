# Java Coding Standards for XVM/XTC

**Version:** 1.0
**Last Updated:** 2025-12-20
**Purpose:** This document establishes modern Java coding standards for the XVM compiler and runtime, based on the AST refactoring plan and analysis of the existing codebase.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Local Variable Declarations with `var`](#local-variable-declarations-with-var)
3. [String Construction Patterns](#string-construction-patterns)
4. [Final Fields and Immutability](#final-fields-and-immutability)
5. [Arrays vs Lists](#arrays-vs-lists)
6. [Ternary Operator Hygiene](#ternary-operator-hygiene)
7. [Null Safety Patterns](#null-safety-patterns)
8. [Stream Collectors vs Boolean Flags](#stream-collectors-vs-boolean-flags)
9. [Collection Immutability](#collection-immutability)
10. [Objects Utility Methods](#objects-utility-methods)
11. [Generic Types and Type Safety](#generic-types-and-type-safety)

---

## Introduction

### Why These Standards Matter

The XVM compiler is evolving toward modern Java patterns that enable:
- **Copy-on-write transformations** for incremental compilation
- **Thread-safe compilation** for parallel processing
- **LSP/IDE support** with cached semantic information
- **Maintainable code** that's easier to understand and debug

These standards support the architectural goals outlined in `ast-refactoring-plan.md`:
- Eliminating reflection-based child access
- Preparing for immutable AST nodes
- Improving type safety
- Reducing defensive programming overhead

### How to Use This Document

Each section contains:
1. **The Rule** - What to do
2. **Motivation** - Why it matters
3. **BAD Example** - Real or constructed antipattern
4. **GOOD Example** - The correct approach
5. **How to Identify** - Search patterns to find violations

---

## Local Variable Declarations with `var`

### The Rule

Use `var` for local variable declarations when the type is verbose or clearly evident from the right-hand side. Do NOT use `var` when the type would be unclear to readers.

### Motivation

- Reduces verbosity in generic-heavy code
- Improves readability by focusing on the variable name and value
- Encourages meaningful variable names
- Modern Java idiom (available since Java 10)

### BAD Example

```java
// Unclear type - what does process() return?
var result = process();  // ❌ Type not obvious

// Primitive where explicit type is clearer
var count = 0;  // ❌ Is this int, long, short?
```

### GOOD Example

```java
// Constructor calls - type is obvious
var sb = new StringBuilder();  // ✅ Clearly StringBuilder
var list = new ArrayList<String>();  // ✅ Clearly ArrayList<String>
var pool = getConstantPool();  // ✅ Method name indicates type

// Verbose generic types
var map = new HashMap<IdentityConstant, TypeInfo>();  // ✅ Avoids repetition
TypeConstant type = resolveType(name);  // ✅ Keep explicit when clarity matters

// Primitives - be explicit
int count = 0;  // ✅ Clear intent
long timestamp = System.currentTimeMillis();  // ✅ Explicit type
```

### How to Identify

Search for:
- Repeated generic type declarations: `Map<K,V> map = new HashMap<K,V>()`
- Complex nested generics: `List<Map<String, List<TypeConstant>>>`
- Constructor assignments with obvious types

---

## String Construction Patterns

### The Rule

**For simple concatenation in loops:** Use `Collectors.joining()` with streams instead of `StringBuilder` with `boolean isFirst` flag.

**For complex formatting:** Use `StringBuilder` with `var` declaration, or `String.format()` for templates.

### Motivation

- `Collectors.joining()` is declarative and eliminates the `isFirst` pattern
- Reduces boilerplate and potential for off-by-one errors
- More functional style that's easier to test and reason about
- The `boolean isFirst` pattern is a code smell indicating imperative thinking

### BAD Example

```java
// Antipattern: boolean first flag in loop
StringBuilder sb = new StringBuilder();
boolean first = true;
for (Parameter param : typeParams) {
    if (first) {
        first = false;  // ❌ Boilerplate
    } else {
        sb.append(", ");
    }
    sb.append(param.toTypeParamString());
}
```

### GOOD Example

```java
// GOOD pattern using Collectors.joining()
sb.append(args.stream()
    .map(Object::toString)
    .collect(Collectors.joining(", ")));

// For complex cases with StringBuilder - use var
var sb = new StringBuilder();
sb.append('<');
sb.append(typeParams.stream()
    .map(Parameter::toTypeParamString)
    .collect(Collectors.joining(", ")));
sb.append('>');
```

### How to Identify

```bash
# Find boolean first patterns
grep -n "boolean.*[Ff]irst.*=" *.java

# Find StringBuilder with loops
grep -A5 "StringBuilder.*sb" *.java | grep "for ("
```

---

## Final Fields and Immutability

### The Rule

**Make fields `final` whenever possible.** Fields that are set only in the constructor and never reassigned should be declared `final`.

For lazily-computed values that prevent `final`, use the `Lazy<T>` utility class.

### Motivation

- Final fields enable true immutability
- Compiler can optimize final field access
- Clearly communicates intent: "this value doesn't change"
- Prepares codebase for copy-on-write transformations
- Thread-safe without synchronization

### BAD Example

```java
// Mutable fields that are never reassigned after construction
public class BiExpression extends Expression {
    private Expression expr1;  // ❌ Not final but never changes
    private Token operator;    // ❌ Not final but never changes
    private Expression expr2;  // ❌ Not final but never changes
}
```

### GOOD Example

```java
// Immutable by design
public class BiExpression extends Expression {
    private final Expression expr1;  // ✅ Cannot be reassigned
    private final Token operator;    // ✅ Cannot be reassigned
    private final Expression expr2;  // ✅ Cannot be reassigned
}

// Lazy initialization with Lazy<T> utility
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::computeTypeInfo);

public TypeInfo getTypeInfo() {
    return typeInfo.get();  // Thread-safe memoization
}
```

---

## Arrays vs Lists

### The Rule

**Prefer `List<T>` over `T[]` for all internal APIs.** Only use arrays at the bridge/runtime boundary where required for native interop.

### Motivation

- Arrays are mutable and expose internal state
- Lists have better API and work with streams/collectors
- Immutable lists (`List.of()`, `List.copyOf()`) prevent accidental modification
- Bridge/runtime requires arrays for indexed access protocol, but internal code doesn't

### BAD Example

```java
// Exposing mutable array
private Annotation[] m_annotations;

public Annotation[] getAnnotations() {
    return m_annotations;  // ❌ Caller can modify!
}
```

### GOOD Example

```java
// Internal storage as immutable list
private final List<Annotation> annotations;

public List<Annotation> getAnnotations() {
    return annotations;  // ✅ Already immutable
}

// Constructor accepts List and makes defensive copy
public MethodStructure(..., List<Parameter> params) {
    this.params = params == null ? List.of() : List.copyOf(params);
}

// For bridge compatibility, provide deprecated array method
@Deprecated  // TODO: Remove once bridge updated
public Parameter[] getParamArray() {
    return params.toArray(Parameter[]::new);
}
```

---

## Ternary Operator Hygiene

### The Rule

**Avoid ternary operators (`? :`) when:**
1. Either branch contains method calls that could return null
2. Nesting ternaries (always refactor to if-else)
3. Either branch has side effects
4. The condition itself is complex

**Prefer ternary operators for:**
- Simple selections with clear types: `int max = (a > b) ? a : b`
- Null coalescing with provably non-null values
- Boolean flag selection: `String label = isEnabled ? "ON" : "OFF"`

### Motivation

The ternary operator combines dataflow and control flow in a single expression, which is problematic when type and null hygiene is poor.

### BAD Example

```java
// Nested ternaries - impossible to debug
return m_fArg1Null ? new IsNull(arg2, argResult) :
       m_fArg2Null ? new IsNull(arg1, argResult) :
       new CmpVal(...);  // ❌ Three-way nested
```

### GOOD Example

```java
// Simple max/min selection
int max = (a > b) ? a : b;  // ✅ Clear, no null risk

// Refactor nested ternaries to if-else
Op op;
if (m_fArg1Null) {
    op = fWhenTrue ? new JumpNull(arg2, label) : new JumpNotNull(arg2, label);
} else if (m_fArg2Null) {
    op = fWhenTrue ? new JumpNull(arg1, label) : new JumpNotNull(arg1, label);
} else {
    op = fWhenTrue ? new JumpEq(...) : new JumpNeq(...);
}

// Null coalescing with Objects utility
TypeConstant effectiveType = Objects.requireNonNullElse(typeExplicit, typeInferred);
```

---

## Null Safety Patterns

### The Rule

1. **Never use `null` for collections** - Use empty collections instead
2. **Annotate nullability** - Use `@NotNull` and `@Nullable`
3. **Validate at API boundaries** - Use `Objects.requireNonNull()`
4. **Immutable by default** - Store collections as immutable from construction

### BAD Example

```java
// Lazy list initialization - null or empty confusion
List<Annotation> listAnnos = null;  // ❌ Is null different from empty?

for (Contribution contrib : getContributionsAsList()) {
    if (listAnnos == null) {
        listAnnos = new ArrayList<>();  // ❌ Lazy init mid-loop
    }
    listAnnos.add(anno);
}
```

### GOOD Example

```java
// Always start with empty collection
List<Annotation> annos = new ArrayList<>();  // ✅ Never null

for (Contribution contrib : getContributionsAsList()) {
    annos.add(anno);  // ✅ No null check needed
}

// Constructor validation
public ThrowExpression(Token keyword, ...) {
    this.keyword = Objects.requireNonNull(keyword);  // ✅ Fail fast
}

// Nullable parameters with defaults
public NameExpression(
        @Nullable Expression left,
        @NotNull Token name,
        @Nullable List<TypeExpression> params) {
    this.left = left;  // Null allowed
    this.name = Objects.requireNonNull(name);
    this.params = params == null ? List.of() : List.copyOf(params);  // ✅ Never null
}
```

---

## Stream Collectors vs Boolean Flags

### The Rule

**When building strings from collections:** Use `stream().map(...).collect(Collectors.joining(...))` instead of StringBuilder with `boolean isFirst` flag.

### GOOD Example

```java
// From NameExpression.java
sb.append(params.stream()
    .map(Object::toString)
    .collect(Collectors.joining(", ")));

// From ImportStatement.java
return qualifiedName.stream()
    .map(Token::getValueText)
    .collect(Collectors.joining("."));

// Filtering and collecting
List<Error> errors = children.stream()
    .filter(Node::hasError)
    .map(Node::getError)
    .toList();  // ✅ Java 16+ convenience method
```

---

## Collection Immutability

### The Rule

**Store collections as immutable** using `List.of()`, `List.copyOf()`, or `Collections.unmodifiableList()`.

**Return unmodifiable collections** from getters, annotated with `@Unmodifiable`.

### GOOD Example

```java
public class MethodStructure {
    private final List<Parameter> params;

    public MethodStructure(..., List<Parameter> params) {
        this.params = params == null ? List.of() : List.copyOf(params);
    }

    @NotNull
    @Unmodifiable
    public List<Parameter> getParams() {
        return params;  // ✅ Already immutable, safe to return
    }
}

// For modification, create new instance (copy-on-write style)
private static List<Parameter> addParameter(List<Parameter> existing, Parameter newParam) {
    var updated = new ArrayList<>(existing);
    updated.add(newParam);
    return List.copyOf(updated);  // ✅ Returns immutable
}
```

---

## Objects Utility Methods

### The Rule

**Use `Objects` utility methods** for common null-related operations:
- `Objects.requireNonNull(obj)` - Validate non-null
- `Objects.requireNonNull(obj, "message")` - With custom message
- `Objects.requireNonNullElse(obj, defaultValue)` - Null coalescing
- `Objects.requireNonNullElseGet(obj, supplier)` - Lazy default

### GOOD Example

```java
// Constructor validation
public ThrowExpression(Token keyword, ...) {
    this.keyword = Objects.requireNonNull(keyword);  // ✅ Concise
}

// Null coalescing with default
this.assignments = Objects.requireNonNullElse(assignments, Map.of());

// Lazy default computation
return Objects.requireNonNullElseGet(type, this::inferType);
```

---

## Generic Types and Type Safety

### The Rule

1. **Never use raw types** - Always provide generic type arguments
2. **Never use double-cast type erasure** - `(List<T>) (List) obj` is unsafe
3. **Parameterize helper methods** - Make utility methods generic
4. **Never use `Object` as a generic escape hatch**

### BAD Example

```java
// Double-cast type erasure
List<Statement> stmts = (List<Statement>) (List) init;  // ❌ Unsafe

// Raw Iterator cast
return (Iterator) m_list.iterator();  // ❌ Loses type

// Object as map key type
Map<Object, PropertyInfo> mapVirtProps;  // ❌ Key can be anything
```

### GOOD Example

```java
// Proper generics in method signatures
protected <T extends AstNode> boolean tryReplaceInList(
        T oldChild, T newChild, List<T> list) {  // ✅ Type-safe
    int index = list.indexOf(oldChild);
    if (index >= 0) {
        list.set(index, newChild);
        return true;
    }
    return false;
}

// Sealed interface for heterogeneous keys
public sealed interface NestedId
        permits PropertyNestedId, MethodNestedId, DeepNestedId {}

Map<NestedId, PropertyInfo> mapVirtProps;  // ✅ Type-safe
```

---

## Summary Checklist

When writing or reviewing code, check:

- [ ] Used `var` only when type is obvious from RHS
- [ ] Used `Collectors.joining()` instead of `boolean isFirst` loops
- [ ] Made fields `final` wherever possible
- [ ] Used `List<T>` instead of `T[]` for internal APIs
- [ ] Avoided nested ternaries and null-risky ternaries
- [ ] Started collections as empty, never null
- [ ] Added `@NotNull` and `@Nullable` annotations
- [ ] Used `Objects.requireNonNull()` in constructors
- [ ] Returned immutable collections from getters
- [ ] Never used raw types or double-cast type erasure
- [ ] Parameterized utility methods with proper generics

---

## References

- [AST Refactoring Plan](ast-refactoring-plan.md)
- [Effective Java, 3rd Edition](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/) - Josh Bloch
- [Java Language Specification - Local Variable Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.4)

---

**Note:** This is a living document. As the refactoring progresses and new patterns emerge, update this document to reflect current best practices.
