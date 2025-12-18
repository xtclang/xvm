# Bad vs Good - Quick Reference Guide

## The Short Version

This chapter provides side-by-side comparisons of anti-patterns found in the XTC codebase alongside
modern Java equivalents. Use this as a reference when reviewing or writing code.

**Note on annotations:** The "good" examples use JSpecify nullness annotations (`@NonNull`,
`@Nullable`) and other modern idioms (`var`, `final` locals, `@Unmodifiable`). These are shown
as natural background - how competent modern Java looks. See e.g. [jspecify.dev](https://jspecify.dev)

---

## 1. Clone vs Copy

### ❌ Bad: Using Cloneable

```java
public class Constant implements Cloneable {
    private transient int m_cRefs;     // Developer thinks this won't be cloned
    private transient int m_iPos;       // But it IS cloned!

    @Override
    public Object clone() {            // Returns Object - no type safety
        try {
            return super.clone();       // Copies ALL fields including transient
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}

// Caller
Constant copy = (Constant) original.clone();  // Cast required
```

### ✅ Good: Copyable Interface with @CopyIgnore

```java
public class Constant implements Copyable<Constant> {
    @CopyIgnore(reason = "Reference count starts at 0 in copies")
    private int m_cRefs;

    @CopyIgnore(reason = "Position assigned when added to pool")
    private int m_iPos;

    @Override
    public Constant copy() {           // Returns correct type
        // @CopyIgnore fields automatically reset
        return CopyUtils.shallowCopy(this);
    }
}

// Caller
Constant copy = original.copy();  // No cast needed
```

---

## 2. Null vs Optional

### ❌ Bad: Returning null for "not found"

```java
public Component getChild(String name) {
    return m_mapChildren == null ? null : m_mapChildren.get(name);
}

// Caller - must check null everywhere
Component child = parent.getChild("foo");
if (child != null) {
    // Do something
}
```

### ✅ Good: Returning Optional

```java
import org.jspecify.annotations.NonNull;

private @NonNull Map<String, Component> children = Map.of();  // Never null

public Optional<Component> findChild(@NonNull String name) {
    return Optional.ofNullable(children.get(name));
}

// Caller - clear semantics, use var for obvious types
parent.findChild("foo")
    .ifPresent(child -> /* Do something */);

// Or with default - final for local bindings
final var child = parent.findChild("foo")
    .orElseThrow(() -> new NoSuchElementException("Child 'foo' not found"));
```

---

## 3. Object Fields vs Typed Fields

### ❌ Bad: Object field with type checks

```java
public class Expression {
    private Object m_oType;  // Could be TypeConstant OR TypeConstant[]

    public TypeConstant getType() {
        if (m_oType instanceof TypeConstant type) {
            return type;
        }
        TypeConstant[] atype = (TypeConstant[]) m_oType;  // Unchecked cast
        return atype.length == 0 ? null : atype[0];
    }
}
```

### ✅ Good: Sealed type hierarchy

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Unmodifiable;

public sealed interface TypeResult permits SingleType, MultiType, NoType {
    @Unmodifiable @NonNull List<TypeConstant> types();
}

public record SingleType(@NonNull TypeConstant type) implements TypeResult {
    @Override
    public @Unmodifiable @NonNull List<TypeConstant> types() {
        return List.of(type);
    }
}

public record MultiType(@Unmodifiable @NonNull List<TypeConstant> types) implements TypeResult {
    public MultiType { types = List.copyOf(types); }  // Defensive copy
}

public record NoType() implements TypeResult {
    private static final @Unmodifiable List<TypeConstant> EMPTY = List.of();
    @Override
    public @Unmodifiable @NonNull List<TypeConstant> types() { return EMPTY; }
}

// Usage with pattern matching - var and final
final var result = expression.getTypeResult();
final var description = switch (result) {
    case SingleType(var t) -> "single: " + t;
    case MultiType(var ts) -> "multi: " + ts.size();
    case NoType() -> "none";
};
```

---

## 4. Arrays vs Collections

### ❌ Bad: Mutable array returns

```java
public TypeConstant[] getParamTypes() {
    return m_atypeParams;  // Returns internal array - can be modified!
}

// Caller can corrupt internal state
TypeConstant[] types = method.getParamTypes();
types[0] = somethingElse;  // Corrupts method's internal state
```

### ✅ Good: Immutable collection returns

```java
private List<TypeConstant> m_paramTypes = List.of();

public List<TypeConstant> getParamTypes() {
    return m_paramTypes;  // Already immutable, safe to return directly
}

// Caller can't modify
List<TypeConstant> types = method.getParamTypes();
// types.add(something);  // UnsupportedOperationException
// types.set(0, something);  // UnsupportedOperationException
```

---

## 5. Reflection vs Explicit

### ❌ Bad: Reflection-based child discovery

```java
public class ForStatement extends Statement {
    private static final Field[] CHILD_FIELDS =
        fieldsForNames(ForStatement.class, "conds", "block");

    // Child access via reflection - slow, fragile, stringly-typed
}
```

### ✅ Good: Explicit child methods

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class ForStatement extends Statement {
    private final @Nullable Expression conds;
    private final @Nullable StatementBlock block;

    public ForStatement(@Nullable Expression conds, @Nullable StatementBlock block) {
        this.conds = conds;
        this.block = block;
    }

    @Override
    public @NonNull Stream<AstNode> children() {
        return Stream.of(conds, block).filter(Objects::nonNull);
    }

    @Override
    public @NonNull ForStatement withChild(
            final @NonNull AstNode oldChild,
            final @NonNull AstNode newChild) {
        if (oldChild == conds) {
            return new ForStatement((Expression) newChild, block);
        }
        if (oldChild == block) {
            return new ForStatement(conds, (StatementBlock) newChild);
        }
        throw new IllegalArgumentException("Not a child: " + oldChild);
    }
}
```

---

## 6. Error Handling

### ❌ Bad: ErrorListener + return null + exceptions

```java
public Expression validate(Context ctx, TypeConstant type, ErrorListener errs) {
    if (problem) {
        errs.log(new ErrorInfo(...));  // Side effect
        return null;                    // Loses which error caused failure
    }
    if (otherProblem) {
        throw new RuntimeException("Oops");  // Third error mechanism
    }
    return this;
}
```

### ✅ Good: Result type

```java
import org.jspecify.annotations.NonNull;

public sealed interface ValidationResult<T> permits Success, Failure {
    boolean isSuccess();
    @NonNull List<Diagnostic> diagnostics();
}

public record Success<T>(@NonNull T value, @NonNull List<Diagnostic> warnings) implements ValidationResult<T> {
    public Success { warnings = List.copyOf(warnings); }
    @Override public boolean isSuccess() { return true; }
    @Override public @NonNull List<Diagnostic> diagnostics() { return warnings; }
}

public record Failure<T>(@NonNull List<Diagnostic> errors) implements ValidationResult<T> {
    public Failure { errors = List.copyOf(errors); }
    @Override public boolean isSuccess() { return false; }
    @Override public @NonNull List<Diagnostic> diagnostics() { return errors; }
}

public @NonNull ValidationResult<Expression> validate(
        final @NonNull Context ctx,
        final @NonNull TypeConstant type) {
    if (problem) {
        return new Failure<>(List.of(new Diagnostic(...)));
    }
    return new Success<>(this, List.of());
}

// Caller - exhaustive pattern matching
final var result = expr.validate(ctx, type);
switch (result) {
    case Success(var validated, var warnings) -> use(validated);
    case Failure(var errors) -> report(errors);
}
```

---

## 7. Thread Safety

### ❌ Bad: Lazy init without synchronization

```java
private TypeInfo m_cachedTypeInfo;  // Not volatile!

public TypeInfo getTypeInfo() {
    if (m_cachedTypeInfo == null) {  // Race condition
        m_cachedTypeInfo = computeTypeInfo();  // Another race
    }
    return m_cachedTypeInfo;
}
```

### ✅ Good: Proper double-checked locking

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

private volatile @Nullable TypeInfo m_cachedTypeInfo;  // MUST be volatile

public @NonNull TypeInfo getTypeInfo() {
    var info = m_cachedTypeInfo;  // Single volatile read
    if (info != null) {
        return info;
    }

    synchronized (this) {
        info = m_cachedTypeInfo;  // Check again under lock
        if (info != null) {
            return info;
        }
        info = computeTypeInfo();
        m_cachedTypeInfo = info;  // Volatile write
        return info;
    }
}
```

### ✅ Better: Use holder pattern

```java
import org.jspecify.annotations.NonNull;

private static final class TypeInfoHolder {
    // Initialized on first access, thread-safe by JVM guarantee
    static final @NonNull TypeInfo INSTANCE = computeTypeInfo();
}

public @NonNull TypeInfo getTypeInfo() {
    return TypeInfoHolder.INSTANCE;
}
```

---

## 8. Constructors

### ❌ Bad: Constructor does too much

```java
public Parser(Source source, ErrorListener errs) {
    m_source = source;
    m_errorListener = errs;
    m_lexer = new Lexer(source, errs);

    // Constructor does business logic!
    next();  // Reads first token
}
```

### ✅ Good: Constructor only initializes

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class Parser {
    private final @NonNull Source source;
    private final @NonNull ErrorListener errorListener;
    private final @NonNull Lexer lexer;
    private @Nullable Token currentToken;

    private Parser(
            final @NonNull Source source,
            final @NonNull ErrorListener errorListener,
            final @NonNull Lexer lexer) {
        this.source = source;
        this.errorListener = errorListener;
        this.lexer = lexer;
        // No business logic in constructor
    }

    public static @NonNull Parser create(
            final @NonNull Source source,
            final @NonNull ErrorListener errorListener) {
        final var lexer = Lexer.create(source, errorListener);
        final var parser = new Parser(source, errorListener, lexer);
        parser.advance();  // Business logic in method, not constructor
        return parser;
    }
}
```

---

## 9. HashMap Keys

### ❌ Bad: Mutable hashCode

```java
public class Constant {
    private boolean m_fResolved;

    @Override
    public int hashCode() {
        if (!m_fResolved) return 0;  // Changes after resolution!
        return computeHash();
    }
}

// Broken usage
Map<Constant, Value> map = new HashMap<>();
map.put(constant, value);  // hashCode is 0
constant.resolve();         // hashCode changes to something else
map.get(constant);          // Returns null! Key is "lost"
```

### ✅ Good: Stable identity hash

```java
import org.jspecify.annotations.Nullable;

public class Constant {
    private final int m_identityHash = computeIdentityHash();

    @Override
    public int hashCode() {
        return m_identityHash;  // Never changes
    }

    @Override
    public boolean equals(final @Nullable Object o) {
        // Based on identity, not resolved state
        return this == o;
    }
}
```

### ✅ Alternative: Identity map

```java
// Use IdentityHashMap when objects don't have stable equals/hashCode
Map<Constant, Value> map = new IdentityHashMap<>();
```

---

## 10. Iteration

### ❌ Bad: Index-based iteration

```java
TypeConstant[] types = method.getParamTypes();
for (int i = 0; i < types.length; i++) {
    TypeConstant type = types[i];
    // process type
}
```

### ✅ Good: Stream operations

```java
method.getParamTypes().stream()
    .filter(type -> !type.isVoid())
    .map(type -> type.resolveTypedefs())
    .forEach(this::processType);
```

---

## 11. Nullable Collections

### ❌ Bad: Nullable collection fields

```java
private List<Component> m_children;  // Might be null

public List<Component> getChildren() {
    return m_children;  // Caller must null-check
}

// Caller
List<Component> children = parent.getChildren();
if (children != null) {
    for (Component child : children) { ... }
}
```

### ✅ Good: Never-null collections

```java
private List<Component> m_children = List.of();  // Never null

public List<Component> getChildren() {
    return m_children;  // Always safe
}

// Caller - no null check needed
for (var child : parent.getChildren()) {
    ...
}
```

---

## 12. Type Checks

### ❌ Bad: instanceof chains

```java
if (expr instanceof LiteralExpression) {
    LiteralExpression lit = (LiteralExpression) expr;
    return lit.getValue();
} else if (expr instanceof NameExpression) {
    NameExpression name = (NameExpression) expr;
    return name.getName();
} else if (expr instanceof BinaryExpression) {
    // ... etc for 50 expression types
}
```

### ✅ Good: Sealed types with pattern matching

```java
public sealed interface Expression permits LiteralExpression, NameExpression, BinaryExpression {
    // Common methods
}

// Exhaustive switch - compiler ensures all cases handled
return switch (expr) {
    case LiteralExpression lit -> lit.getValue();
    case NameExpression name -> name.getName();
    case BinaryExpression bin -> evaluate(bin);
};
```

---

## 13. Switch Statement Anti-Patterns

The XTC codebase contains **5,180+ case statements** across 216 files, with **40+ explicit fall-through
comments** and countless implicit ones. Many switches span hundreds of lines with fall-throughs in
unexpected places.

### ❌ Bad: Fall-through without indication

```java
// From LiteralConstant.java - fall-through used to group cases
switch (ch) {
    case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':     // base 16
    case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
        if (radix < 16) {
            break EatDigits;  // Labelled break adds to confusion
        }
        // fall through
    case '9': case '8':                                             // base 10
    case '7': case '6': case '5': case '4': case '3': case '2':     // base 8
    case '1': case '0':                                             // base 2
        // ... 20 lines of code
        break;
}
```

**Problems:**
- Fall-through is implicit and easy to miss during review
- Combining fall-through with labelled breaks creates cognitive overload
- No compiler help - adding a new case might accidentally fall through
- 40+ `// fall through` comments scattered throughout the codebase
- Comments are inconsistent: "fall through", "fall-through", "FALL THROUGH"

### ❌ Bad: default in the middle or first

```java
// Pattern seen throughout XTC codebase
switch (format) {
    default:
        throw new IllegalStateException("unsupported format: " + format);
    case IntLiteral:
        // handle int
        break;
    case FPLiteral:
        // handle float
        break;
}
```

**Problems:**
- Unexpected location - readers expect `default` at the end
- Makes it unclear which cases are explicitly handled
- Can accidentally catch new enum values instead of getting compiler warnings

### ❌ Bad: Massive switches with mixed concerns

```java
// Hundreds of lines like this in TypeConstant, ClassStructure, etc.
switch (format) {
    case IntLiteral:
    case FPLiteral:
    case Date:
    case TimeOfDay:
    case Time:
    case Duration:
    case Version:
    case Path:
        break;  // These 8 cases all do... nothing?

    default:
        throw new IllegalStateException("unsupported format: " + format);
}
```

**Problems:**
- Empty cases that fall through to a break are meaningless
- Unclear intent - are these validated elsewhere? Are some missing implementations (TODO)?
- Switch statements grow unboundedly as new cases are added

### ✅ Good: Switch expressions with exhaustiveness

```java
import org.jspecify.annotations.NonNull;

public @NonNull TypeConstant getType(@NonNull Format format) {
    final var pool = getConstantPool();
    return switch (format) {
        case IntLiteral -> pool.typeIntLiteral();
        case FPLiteral  -> pool.typeFPLiteral();
        case Date       -> pool.typeDate();
        case TimeOfDay  -> pool.typeTimeOfDay();
        case Time       -> pool.typeTime();
        case Duration   -> pool.typeDuration();
        case Version    -> pool.typeVersion();
        case Path       -> pool.typePath();
        // No default needed - compiler ensures exhaustiveness with sealed types/enums
    };
}
```

**Benefits:**
- **Exhaustiveness checking**: Compiler errors if you add a new enum value
- **No fall-through possible**: Arrow syntax can't fall through
- **Expression, not statement**: Returns a value, can't forget to return
- **Concise**: No `break` statements needed

### ✅ Good: Pattern matching for complex cases

```java
// Instead of instanceof chains with fall-through logic
public @NonNull String describe(@NonNull TokenValue value) {
    return switch (value) {
        case NoValue()           -> "no value";
        case StringValue(var s)  -> "string: " + s;
        case IntValue(var n) when n.signum() < 0 -> "negative: " + n;
        case IntValue(var n)     -> "positive: " + n;
        case DecimalValue(var d) -> "decimal: " + d;
        case CharValue(var c)    -> "char: " + c;
    };
}
```

### Design Guide: When to Use Switches

| Scenario | Recommendation |
|----------|---------------|
| Enum dispatch | Switch expression with arrows, no default |
| Sealed type dispatch | Switch expression with pattern matching |
| Multiple actions per case | Consider extracting to methods |
| String matching | Consider `Map.of()` lookup instead |
| Integer range checking | Consider `if`/`else` or `IntStream.range()` |
| Fall-through needed | **Redesign** - use explicit method calls |

---

## 14. Labelled Breaks and Complex Loops

The XTC codebase uses **34 labelled breaks** and **19 labelled continues** across 34 files. These
create spaghetti control flow that's nearly impossible to reason about.

### ❌ Bad: Labelled breaks for multi-level exit

```java
// From LiteralConstant.java - actual code
EatDigits: while (true) {
    switch (ch) {
        case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
            if (radix < 16) {
                break EatDigits;  // Exit the while loop!
            }
            // fall through
        case '9': case '8':
        case '7': case '6': case '5': case '4': case '3': case '2':
        case '1': case '0':
            if (bigint == null) {
                lValue = lValue * radix + hexitValue(ch);
            } else {
                bigint = bigint.multiply(BigInteger.valueOf(radix));
            }
            break;  // Exit the switch!

        default:
            break EatDigits;  // Exit the while loop!
    }
    // ... more code
}
```

**Problems:**
- `break` means different things in different contexts
- Reader must trace control flow through nested structures
- Labelled breaks can jump multiple levels - very hard to follow
- Mixing labelled breaks with switch fall-through is cognitive torture
- Labels like `EatDigits`, `NextLayer`, `PossibleSuffix` are scattered throughout

### ❌ Bad: Labelled continues in nested loops

```java
// Pattern found in MethodInfo, TypeInfo, ClassStructure
NextLayer: for (int iThat = 0; iThat < cAdd; ++iThat) {
    MethodBody bodyThat = aAdd[iThat];

    for (int iThis = 0; iThis < cBase; ++iThis) {
        if (bodyThat.equals(aBase[iThis])) {
            if (someCondition) {
                continue;  // Continue inner loop
            }
            continue NextLayer;  // Continue outer loop - totally different!
        }
    }
}
```

**Problems:**
- `continue` and `continue NextLayer` look similar but behave completely differently
- Must track which loop each label refers to
- Refactoring is dangerous - moving code can change which loop is affected

### ✅ Good: Extract to methods for clarity

```java
import org.jspecify.annotations.NonNull;

public @NonNull PackedInteger parseDigits(@NonNull String input, int radix) {
    final var parser = new DigitParser(input, radix);
    return parser.parse();
}

private static class DigitParser {
    private final @NonNull String input;
    private final int radix;
    private int position = 0;

    DigitParser(@NonNull String input, int radix) {
        this.input = input;
        this.radix = radix;
    }

    @NonNull PackedInteger parse() {
        long value = 0;
        BigInteger bigValue = null;

        while (hasMoreDigits()) {
            final int digit = nextDigit();
            if (digit < 0) {
                break;  // Simple, single-level break
            }

            if (bigValue == null) {
                value = value * radix + digit;
                if (value > 0x00FFFFFFFFFFFFFFL) {
                    bigValue = BigInteger.valueOf(value);
                }
            } else {
                bigValue = bigValue.multiply(BigInteger.valueOf(radix))
                                   .add(BigInteger.valueOf(digit));
            }
        }

        return bigValue != null
            ? new PackedInteger(bigValue)
            : new PackedInteger(value);
    }

    private boolean hasMoreDigits() {
        return position < input.length();
    }

    private int nextDigit() {
        final char ch = input.charAt(position++);
        return switch (ch) {
            case '0', '1' -> ch - '0';
            case '2', '3', '4', '5', '6', '7' -> radix >= 8 ? ch - '0' : -1;
            case '8', '9' -> radix >= 10 ? ch - '0' : -1;
            case 'A', 'B', 'C', 'D', 'E', 'F' -> radix == 16 ? ch - 'A' + 10 : -1;
            case 'a', 'b', 'c', 'd', 'e', 'f' -> radix == 16 ? ch - 'a' + 10 : -1;
            case '_' -> nextDigit();  // Skip underscores, get next
            default -> -1;
        };
    }
}
```

**Benefits:**
- No labels needed - single-level control flow
- Each method has one responsibility
- State is explicit in fields, not scattered across variables
- Easy to test `nextDigit()` independently
- Switch expression replaces fall-through cases

### ✅ Good: Use Stream operations for collection processing

```java
// Instead of nested loops with labelled breaks
public Optional<MethodBody> findMatchingBody(
        @NonNull List<MethodBody> candidates,
        @NonNull SignatureConstant signature) {
    return candidates.stream()
        .filter(body -> body.getSignature().equals(signature))
        .findFirst();
}

// Instead of labelled continue for filtering
public @NonNull List<MethodBody> filterBodies(
        @NonNull List<MethodBody> bodies,
        @NonNull Predicate<MethodBody> predicate) {
    return bodies.stream()
        .filter(predicate)
        .filter(body -> !body.isSynthetic())
        .toList();
}
```

### Design Guide: Eliminating Labels

| Pattern | Replacement |
|---------|-------------|
| Labelled break from nested loop | Extract inner loop to method returning `Optional` |
| Labelled continue | Extract to method with early return |
| Break from switch inside loop | Switch expression + simple break |
| Complex state machine | State pattern or explicit state enum |
| Multiple exit conditions | Stream with `takeWhile()` or `findFirst()` |

---

## 15. 1990s Imperative Style

The XTC codebase is written in a deeply imperative style that ignores 25 years of Java evolution.
The compiler package alone has **865 mutable local variable declarations** vs only **17 uses of
`final`** for locals - a 50:1 ratio of bad to good.

### ❌ Bad: Non-final parameters with reassignment

```java
// Pattern found throughout XTC
public TypeConstant resolveType(TypeConstant type, ErrorListener errs) {
    type = type.resolveTypedefs();           // Reassigning parameter!

    if (type.isGeneric()) {
        type = type.resolveGenerics();       // Reassigning again!
    }

    if (type.containsUnresolved()) {
        type = pool().typeObject();          // And again!
    }

    return type;
}
```

**Problems:**
- Can't tell at any point what `type` refers to - original or transformed?
- Debugging is harder - breakpoints show the current value, not the history
- Prevents using `type` in lambdas (must be effectively final)
- Encourages long methods with many transformations
- Makes reasoning about code flow much harder

### ✅ Good: Final parameters, new variables for each step

```java
import org.jspecify.annotations.NonNull;

public @NonNull TypeConstant resolveType(
        @NonNull final TypeConstant type,
        @NonNull final ErrorListener errs) {
    final var resolved = type.resolveTypedefs();

    final var withGenerics = resolved.isGeneric()
        ? resolved.resolveGenerics()
        : resolved;

    return withGenerics.containsUnresolved()
        ? pool().typeObject()
        : withGenerics;
}
```

**Benefits:**
- Each variable has one meaning throughout its scope
- Transformation pipeline is clear
- Can be used in lambdas
- IDE can inline/extract more safely

### ❌ Bad: Mutable accumulator loops

```java
// Pattern seen everywhere in XTC
List<TypeConstant> listTypes = new ArrayList<>();
for (int i = 0; i < aTypes.length; ++i) {
    TypeConstant type = aTypes[i];
    if (!type.isVoid()) {
        type = type.resolveTypedefs();
        listTypes.add(type);
    }
}
return listTypes;
```

**Problems:**
- Mutable list being built imperatively
- Index variable `i` used only to access array
- Variable `type` reassigned inside loop
- Four lines of boilerplate for a filter-map operation

### ✅ Good: Stream pipeline

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Unmodifiable;

public @Unmodifiable @NonNull List<TypeConstant> resolveTypes(
        @NonNull TypeConstant[] types) {
    return Arrays.stream(types)
        .filter(type -> !type.isVoid())
        .map(TypeConstant::resolveTypedefs)
        .toList();
}
```

**Benefits:**
- Immutable result (Java 16+ `toList()`)
- Declarative - says what, not how
- No mutable state
- Can be parallelized with `.parallel()` if needed

### ❌ Bad: Boolean flags controlling flow

```java
// Pattern found in Parser, Lexer, many AST classes
boolean fFound = false;
boolean fError = false;
TypeConstant typeResult = null;

for (Component comp : components) {
    if (comp.isType()) {
        if (fFound) {
            fError = true;
            break;
        }
        fFound = true;
        typeResult = comp.getType();
    }
}

if (fError) {
    return null;
}
return fFound ? typeResult : defaultType;
```

**Problems:**
- Three mutable variables tracking interrelated state
- Complex conditionals based on flag combinations
- Null return for errors (see [Null Handling](../type-safety/null-handling.md))
- Hard to reason about all possible states

### ✅ Good: Early returns or Optional

```java
import org.jspecify.annotations.NonNull;

public @NonNull Optional<TypeConstant> findUniqueType(
        @NonNull List<Component> components) {
    final var types = components.stream()
        .filter(Component::isType)
        .map(Component::getType)
        .toList();

    return switch (types.size()) {
        case 0 -> Optional.empty();
        case 1 -> Optional.of(types.getFirst());
        default -> Optional.empty();  // Multiple found = error
    };
}
```

### ❌ Bad: Variables declared far from use

```java
// XTC pattern - declare everything at top
public void process() {
    TypeConstant type;
    MethodConstant method;
    boolean fValid;
    int cParams;
    List<TypeConstant> listTypes;

    // ... 50 lines later ...

    type = getType();

    // ... 30 more lines ...

    method = findMethod(type);

    // ... etc
}
```

**Problems:**
- Can't see type and initialization together
- Variables might be used uninitialized (nulls hiding)
- Prevents using `final` since assignment is deferred
- 1990s C-style "declare at top of block"

### ✅ Good: Declare at point of use with `final var`

```java
public void process() {
    // ... preliminary work ...

    final var type = getType();

    // ... work with type ...

    final var method = findMethod(type);

    // ... work with method ...
}
```

### ❌ Bad: Nested conditionals instead of guard clauses

```java
// XTC pattern - deep nesting
public TypeConstant validate(Context ctx) {
    if (ctx != null) {
        TypeConstant type = ctx.getTargetType();
        if (type != null) {
            if (type.isResolved()) {
                TypeInfo info = type.ensureTypeInfo();
                if (info != null) {
                    return info.getType();
                }
            }
        }
    }
    return null;
}
```

### ✅ Good: Guard clauses with early return

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public @NonNull Optional<TypeConstant> validate(@Nullable Context ctx) {
    if (ctx == null) {
        return Optional.empty();
    }

    final var type = ctx.getTargetType();
    if (type == null || !type.isResolved()) {
        return Optional.empty();
    }

    final var info = type.ensureTypeInfo();
    return Optional.ofNullable(info)
        .map(TypeInfo::getType);
}
```

### Design Guide: Modern Java Style

| 1990s Pattern | Modern Alternative |
|---------------|-------------------|
| Non-final parameters | `final` parameters, new variables for transforms |
| Mutable accumulator loops | Stream `collect()` or `toList()` |
| Boolean flags | Enums, Optional, or sealed result types |
| Declare-at-top | Declare at point of use with `final var` |
| Deep nesting | Guard clauses with early return |
| Index-based loops | Enhanced for-loop or streams |
| Null checks everywhere | `@NonNull` by default, `Optional` for absence |
| `instanceof` + cast | Pattern matching (`instanceof Type t`) |

### The Ratio Problem

In modern Java code, you should see:
- **90%+ local variables declared `final`** (or using `var` which is effectively final by convention)
- **Parameters always effectively final** (never reassigned)
- **Collections built with streams** where appropriate
- **No labelled breaks or continues**

In XTC compiler code:
- **2% local variables are final** (17 out of 865+ in compiler package)
- **Parameters routinely reassigned**
- **Imperative loops with mutable accumulators everywhere**
- **34 labelled breaks, 19 labelled continues**

This isn't a style preference - it's a **correctness issue**. Mutable variables:
- Can't be captured in lambdas
- Are harder to reason about in concurrent contexts
- Make refactoring dangerous
- Hide bugs where the wrong value is used

---

## 16. The "Void Everything" Anti-Pattern

The XTC codebase has **608+ `public void` methods** in the asm package alone, and **72 `void set*()`
methods** across the codebase. This 1990s-era pattern of returning nothing makes code impossible
to compose, chain, or use in modern functional idioms.

### ❌ Bad: Void setters prevent chaining

```java
// XTC pattern - every setter returns void
public class Component {
    public void setName(String name) {
        this.m_sName = name;
    }

    public void setAccess(Access access) {
        this.m_access = access;
    }

    public void setParent(Component parent) {
        this.m_parent = parent;
    }
}

// Caller must use separate statements for each mutation
Component comp = new Component();
comp.setName("foo");
comp.setAccess(Access.PUBLIC);
comp.setParent(parent);
```

**Problems:**
- Can't chain operations: `comp.setName("foo").setAccess(PUBLIC)` is impossible
- Can't use in stream operations or lambdas easily
- Encourages mutable objects that are configured after construction
- Returns nothing useful even though the operation clearly produces a result

### ✅ Good: Return `this` for fluent APIs (mutable objects)

```java
import org.jspecify.annotations.NonNull;

public class ComponentBuilder {
    private @NonNull String name = "";
    private @NonNull Access access = Access.PRIVATE;
    private @Nullable Component parent;

    public @NonNull ComponentBuilder name(@NonNull String name) {
        this.name = name;
        return this;
    }

    public @NonNull ComponentBuilder access(@NonNull Access access) {
        this.access = access;
        return this;
    }

    public @NonNull ComponentBuilder parent(@Nullable Component parent) {
        this.parent = parent;
        return this;
    }

    public @NonNull Component build() {
        return new Component(name, access, parent);
    }
}

// Caller - fluent chaining
final var comp = new ComponentBuilder()
    .name("foo")
    .access(Access.PUBLIC)
    .parent(parent)
    .build();
```

### ✅ Better: Immutable objects with `with*` methods (copy-on-write)

```java
import org.jspecify.annotations.NonNull;

public record Component(
    @NonNull String name,
    @NonNull Access access,
    @Nullable Component parent
) {
    public @NonNull Component withName(@NonNull String name) {
        return new Component(name, this.access, this.parent);
    }

    public @NonNull Component withAccess(@NonNull Access access) {
        return new Component(this.name, access, this.parent);
    }

    public @NonNull Component withParent(@Nullable Component parent) {
        return new Component(this.name, this.access, parent);
    }
}

// Caller - immutable transformations
final var updated = original
    .withName("newName")
    .withAccess(Access.PUBLIC);
// original is unchanged, updated is the new version
```

### ❌ Bad: Void mutation methods on collections

```java
// XTC pattern
public void addChild(Component child) {
    if (m_children == null) {
        m_children = new ArrayList<>();
    }
    m_children.add(child);
}

// Can't compose
parent.addChild(child1);
parent.addChild(child2);
parent.addChild(child3);
```

### ✅ Good: Return new collection for immutable add

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Unmodifiable;

public record Parent(@Unmodifiable @NonNull List<Component> children) {
    public Parent {
        children = List.copyOf(children);
    }

    public @NonNull Parent withChild(@NonNull Component child) {
        final var newChildren = new ArrayList<>(children);
        newChildren.add(child);
        return new Parent(newChildren);
    }

    public @NonNull Parent withChildren(@NonNull Component... newChildren) {
        final var combined = new ArrayList<>(children);
        combined.addAll(Arrays.asList(newChildren));
        return new Parent(combined);
    }
}

// Caller - composable
final var updated = parent
    .withChild(child1)
    .withChild(child2)
    .withChild(child3);

// Or even better
final var updated = parent.withChildren(child1, child2, child3);
```

### ❌ Bad: Void AST transformation methods

```java
// XTC pattern - transforms in place, returns nothing
public void resolveNames(Context ctx) {
    for (AstNode child : getChildren()) {
        child.resolveNames(ctx);
    }
    // ... resolve this node's names
}

// Can't chain transformations
ast.resolveNames(ctx);
ast.validateTypes(ctx);
ast.generateCode(ctx);
```

### ✅ Good: Return transformed AST (copy-on-write for immutability)

```java
import org.jspecify.annotations.NonNull;

public sealed interface AstNode permits Expression, Statement, Declaration {
    @NonNull AstNode resolveNames(@NonNull Context ctx);
    @NonNull AstNode validateTypes(@NonNull Context ctx);
    @NonNull AstNode optimize();
}

// Implementation returns new node if changed
public record BinaryExpression(
    @NonNull Expression left,
    @NonNull Expression right,
    @NonNull Operator op
) implements Expression {

    @Override
    public @NonNull Expression resolveNames(@NonNull Context ctx) {
        final var newLeft = left.resolveNames(ctx);
        final var newRight = right.resolveNames(ctx);

        // Return same object if nothing changed (optimization)
        if (newLeft == left && newRight == right) {
            return this;
        }
        return new BinaryExpression(newLeft, newRight, op);
    }
}

// Caller - composable pipeline
final var optimized = ast
    .resolveNames(ctx)
    .validateTypes(ctx)
    .optimize();
```

### Why This Matters for LSP

For LSP and any concurrent/interactive use of the compiler:

1. **Void methods imply mutation** - can't run concurrently
2. **Copy-on-write enables snapshots** - multiple versions coexist safely
3. **Chaining enables pipelines** - easier to compose transformations
4. **Return values enable functional style** - can use in streams and lambdas

```java
// With void methods - can't do this
components.stream()
    .map(c -> c.withAccess(Access.PUBLIC))  // Impossible if setAccess returns void
    .toList();

// With return values - easy
final var publicComponents = components.stream()
    .map(c -> c.withAccess(Access.PUBLIC))
    .toList();
```

### Modern Language Comparison

Every modern language has moved away from void-returning mutators:

| Language | Pattern | Example |
|----------|---------|---------|
| Kotlin | Copy with named args | `user.copy(name = "new")` |
| Scala | Case class copy | `user.copy(name = "new")` |
| Rust | Builder pattern / Clone | `User { name: "new", ..user }` |
| Swift | Mutating returns Self | `mutating func setName(_ n: String) -> Self` |
| TypeScript | Spread operator | `{ ...user, name: "new" }` |

Java records with `with*` methods follow this same pattern. The XTC codebase's void methods are
a relic of 1990s Java that blocks all modern idioms.

### Design Guide: When to Return What

| Method Type | Return Type | Example |
|-------------|-------------|---------|
| Setter on mutable builder | `this` (builder type) | `builder.name("x")` returns builder |
| Transformation on immutable | New instance | `record.withName("x")` returns new record |
| Add to collection (mutable) | `this` or `boolean` | `list.add(x)` returns boolean |
| Add to collection (immutable) | New collection | `list.with(x)` returns new list |
| AST transformation | Transformed node | `ast.resolve()` returns (possibly new) AST |
| Validation | `Result<T, Error>` | `validate()` returns success or errors |
| Search | `Optional<T>` | `find()` returns Optional |

**Rule of thumb:** If a method does something, it should return something. Void is for pure
side-effects like logging or I/O - not for object transformations.

---

## 17. Missing Functional Idioms

Modern languages like Kotlin provide standard scope functions (`let`, `apply`, `also`, `run`,
`with`) that enable concise, readable transformations. Java can and should adopt similar patterns.
The XTC codebase uses none of these idioms, leading to verbose, hard-to-compose code.

**Sources:**
- Barbini, U. (2021). *From Objects to Functions: Build Your Software Faster and Safer with
  Functional Programming in Kotlin*. Pragmatic Bookshelf.
- Kotlin Language Specification: [Scope Functions](https://kotlinlang.org/docs/scope-functions.html)

### Kotlin's Scope Functions

| Function | Object reference | Return value | Use case |
|----------|------------------|--------------|----------|
| `let` | `it` | Lambda result | Null checks, transformations |
| `run` | `this` | Lambda result | Object configuration + result |
| `with` | `this` | Lambda result | Grouping calls on object |
| `apply` | `this` | Context object | Object configuration |
| `also` | `it` | Context object | Side effects |

### ❌ Bad: Verbose null handling without `let`

```java
// XTC pattern - nested null checks
TypeConstant type = expr.getType();
if (type != null) {
    TypeInfo info = type.ensureTypeInfo();
    if (info != null) {
        MethodInfo method = info.findMethod("toString");
        if (method != null) {
            return method.getSignature();
        }
    }
}
return null;
```

### ✅ Good: Java approximation of `let` with Optional

```java
import org.jspecify.annotations.NonNull;

// Using Optional as a poor man's "let"
public @NonNull Optional<SignatureConstant> findToStringSignature(
        @Nullable Expression expr) {
    return Optional.ofNullable(expr)
        .map(Expression::getType)
        .map(TypeConstant::ensureTypeInfo)
        .map(info -> info.findMethod("toString"))
        .map(MethodInfo::getSignature);
}
```

### ✅ Better: Define utility functions for common patterns

```java
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin-style scope functions for Java.
 * See: Barbini, "From Objects to Functions" (2021), Chapter 4.
 */
public final class Scope {
    private Scope() {}

    /** Transform if non-null (Kotlin's `?.let`) */
    public static <T, R> @Nullable R let(
            @Nullable T value,
            @NonNull Function<@NonNull T, @Nullable R> transform) {
        return value != null ? transform.apply(value) : null;
    }

    /** Configure and return (Kotlin's `apply`) */
    public static <T> @NonNull T apply(
            @NonNull T value,
            @NonNull Consumer<@NonNull T> block) {
        block.accept(value);
        return value;
    }

    /** Execute with context and return result (Kotlin's `run`) */
    public static <T, R> @NonNull R run(
            @NonNull T value,
            @NonNull Function<@NonNull T, @NonNull R> block) {
        return block.apply(value);
    }

    /** Execute side effect and return original (Kotlin's `also`) */
    public static <T> @NonNull T also(
            @NonNull T value,
            @NonNull Consumer<@NonNull T> block) {
        block.accept(value);
        return value;
    }

    /** Transform non-null or return default (Kotlin's `?: let`) */
    public static <T, R> @NonNull R letOrDefault(
            @Nullable T value,
            @NonNull R defaultValue,
            @NonNull Function<@NonNull T, @NonNull R> transform) {
        return value != null ? transform.apply(value) : defaultValue;
    }
}

// Usage - much cleaner than nested ifs
final var signature = let(expr,
    e -> let(e.getType(),
        t -> let(t.ensureTypeInfo(),
            i -> let(i.findMethod("toString"),
                MethodInfo::getSignature))));
```

### ❌ Bad: Verbose builder configuration

```java
// XTC pattern
Component comp = new Component();
comp.setName("MyClass");
comp.setAccess(Access.PUBLIC);
comp.setParent(parent);
comp.setFormat(Format.CLASS);
comp.setDocumentation("A sample class");
// 5 statements for one conceptual operation
```

### ✅ Good: Using `apply` pattern

```java
// With Scope.apply
final var comp = apply(new ComponentBuilder(), b -> {
    b.name("MyClass");
    b.access(Access.PUBLIC);
    b.parent(parent);
    b.format(Format.CLASS);
    b.documentation("A sample class");
}).build();
```

### ✅ Better: Fluent builder (no utility needed)

```java
final var comp = new ComponentBuilder()
    .name("MyClass")
    .access(Access.PUBLIC)
    .parent(parent)
    .format(Format.CLASS)
    .documentation("A sample class")
    .build();
```

### ❌ Bad: Debugging with temporary variables

```java
// XTC pattern - adding print statements requires restructuring
TypeConstant result = resolveType(type);
System.out.println("Resolved: " + result);  // Debug
return result;
```

### ✅ Good: Using `also` for side effects

```java
// No restructuring needed
return also(resolveType(type), r -> System.out.println("Resolved: " + r));

// Or with method reference
return also(resolveType(type), System.out::println);
```

### Why Java Should Adopt These Patterns

From *From Objects to Functions* (Barbini, 2021):

> "Scope functions are not just syntactic sugar. They fundamentally change how we think about
> transformations. Instead of a sequence of imperative statements, we have a pipeline of
> transformations where data flows through functions."

**Benefits:**
1. **Null safety**: `let` chains naturally short-circuit on null
2. **Expression-oriented**: Everything returns a value, enabling composition
3. **Reduced scope**: Variables are scoped to the lambda, preventing accidental reuse
4. **Self-documenting**: The function name (`let`, `apply`, `also`) communicates intent
5. **Debugging**: `also` allows inserting side effects without restructuring

### The Missing Java Features

Java lacks built-in scope functions because:
- Lambdas were added late (Java 8, 2014)
- No extension functions (can't add methods to existing types)
- Optional is clunky compared to Kotlin's nullable types

But you can still get 80% of the benefit with:
- A small utility class (shown above)
- Consistent use of Optional for nullable returns
- Builder pattern with fluent APIs
- Method references where possible

### Design Guide: Functional Idioms in Java

| Kotlin | Java Equivalent | Use Case |
|--------|-----------------|----------|
| `x?.let { }` | `Optional.ofNullable(x).map()` | Null-safe transform |
| `x.apply { }` | Fluent builder or `Scope.apply()` | Object configuration |
| `x.also { }` | `Scope.also()` | Side effects (logging, debugging) |
| `x.run { }` | `Scope.run()` or lambda | Compute result from context |
| `x ?: default` | `Optional.orElse()` | Default values |
| `x?.let { } ?: default` | `Optional.map().orElse()` | Transform or default |

**Key insight from Barbini:** Treating transformations as data pipelines rather than imperative
mutations makes code more testable, more composable, and easier to reason about - exactly what
you need for LSP and concurrent access.

---

## Quick Reference Table

| Anti-Pattern | Modern Alternative |
|--------------|-------------------|
| `Cloneable` | `Copyable<T>` with `@CopyIgnore` |
| `return null` (not found) | `Optional<T>` |
| `return null` (error) | `Result<T, E>` or sealed type |
| `Object` field | Sealed type hierarchy |
| `T[]` array | `List<T>` with `List.of()`/`List.copyOf()` |
| `fieldsForNames()` reflection | Explicit `children()` method |
| `ErrorListener` parameter | `Result<T, List<Diagnostic>>` |
| Non-volatile lazy init | `volatile` + double-checked locking |
| Constructor with side effects | Factory method + private constructor |
| Mutable `hashCode()` | Identity-based hash or `IdentityHashMap` |
| Index-based loops | Stream operations |
| Nullable collections | Empty collections (`List.of()`) |
| `instanceof` chains | Sealed types with pattern matching |
| Switch fall-through | Switch expressions with arrows |
| `default:` not last | Exhaustive enum/sealed switches (no default) |
| Labelled `break`/`continue` | Extract to methods with early return |
| Non-final parameters | `final` parameters, new variables |
| Mutable locals | `final var` at point of use |
| Boolean flags | Enums, Optional, or sealed result types |
| Declare-at-top | Declare at point of use |
| Deep nesting | Guard clauses with early return |
| `void` setters/mutators | Return `this` or new instance (`with*`) |
| `void` transformations | Return transformed object (copy-on-write) |
| Nested null checks | `Optional.map()` chains or `let()` utility |
| Imperative configuration | `apply()` utility or fluent builders |
| Debug with temp variables | `also()` for transparent side effects |

---

## When In Doubt

1. **Is it mutable?** Make it immutable (records, `List.of()`, `Map.copyOf()`)
2. **Can it be null?** Use `Optional` return or `@Nullable` annotation
3. **Is it thread-safe?** Use `volatile`, immutable structures, or explicit synchronization
4. **Does it use reflection?** Replace with explicit code
5. **Does it return an array?** Return an immutable collection instead
6. **Does it use Object?** Use a sealed type hierarchy
7. **Can it fail?** Use a Result type, not exceptions or return null
8. **Does it use switch fall-through?** Use switch expressions with arrow syntax
9. **Does it use labelled breaks?** Extract to methods with early return
10. **Is the variable reassigned?** Make it `final`, create new variable for each transformation
11. **Is the parameter reassigned?** Make it `final`, use new local variables
12. **Are there nested loops with complex exit?** Use streams or extract to methods
13. **Does it return void?** Return `this` (builder), new instance (immutable), or result type
14. **Is it a chain of null checks?** Use `Optional.map()` or scope functions (`let`, `also`)
