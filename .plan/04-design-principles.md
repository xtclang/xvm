# Design Principles for Refactoring

## Immutability
- All List fields should be immutable (use List.of(), List.copyOf(), .toList())
- Never expose mutable lists
- Constructors validate and copy to immutable

## Null Safety
- Use @NotNull annotations on parameters
- Use Objects.requireNonNull() in constructors
- Default to List.of() instead of null
- Never accept null for collection parameters

## Naming Conventions
- m_listConst* for List fields (was m_aconst* for arrays)
- listParams, listReturns for local variables

## Constructor Patterns
```java
public Foo(@NotNull List<T> items) {
    this.items = Objects.requireNonNull(items, "items required");
    // or for copying: List.copyOf(items)
}

// Convenience constructor with defaults
public Foo() {
    this(List.of());
}
```

## API Migration
- List version is primary implementation
- Array version @Deprecated, delegates to List version
- Eventually remove array versions
