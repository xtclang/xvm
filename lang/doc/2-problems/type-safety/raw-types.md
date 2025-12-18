# Raw Object Types - Why You Cannot Write Code Like This

## The Devastating Truth

The XVM codebase makes extensive use of raw `Object` types where proper generics or sealed types should be used. 
This isn't a minor style issue - **it's a fundamental violation of type safety that makes the code impossible 
to reason about, impossible to refactor safely, and impossible to use in concurrent contexts.**

Let me be absolutely clear: **You cannot write code like this and expect it to work correctly.** 

The compiler provides type checking specifically to catch errors before runtime. By using `Object` 
everywhere, the developers have voluntarily disabled the compiler's ability to help them.

## Real Examples from the Codebase

### Example 1: Expression.java - The Type Storage Nightmare

```java
// Expression.java lines 3097, 3103
/**
 * After validation, contains the type(s) of the expression, stored as either a
 * {@code TypeConstant} or a {@code TypeConstant[]}.
 */
private Object m_oType;  // Could be TypeConstant OR TypeConstant[]!

/**
 * After validation, contains the constant value(s) of the expression, iff the expression is a
 * constant, stored as either a {@code Constant} or a {@code Constant[]}.
 */
private Object m_oConst;  // Could be Constant OR Constant[]!
```

**What this means in practice:**

Every time anyone accesses these fields, they must do runtime type checking:

```java
public TypeConstant getType() {
    checkValidated();

    if (m_oType instanceof TypeConstant type) {
        return type;
    }

    TypeConstant[] atype = (TypeConstant[]) m_oType;  // UNCHECKED CAST!
    return atype.length == 0 ? null : atype[0];
}
```

**Why this is catastrophically bad:**

1. **No compile-time verification**: The compiler cannot verify that code handles both cases
2. **Runtime ClassCastException**: If someone puts the wrong type in, you get a runtime crash
3. **Code duplication**: Every accessor must repeat the `instanceof` check
4. **Semantic confusion**: Is `null` valid? What about an empty array? What about an array with nulls inside?
5. **Refactoring is dangerous**: Rename `TypeConstant` and grep won't find the casts
6. **Testing is insufficient**: You need tests for every possible type combination

**What this should be:**

```java
// Sealed type that explicitly models the possibilities
sealed interface ExpressionTypes permits SingleType, MultipleTypes, VoidType {
    TypeConstant first();
    List<TypeConstant> all();
    int count();
}

record SingleType(TypeConstant type) implements ExpressionTypes {
    @Override public TypeConstant first() { return type; }
    @Override public List<TypeConstant> all() { return List.of(type); }
    @Override public int count() { return 1; }
}

record MultipleTypes(List<TypeConstant> types) implements ExpressionTypes {
    @Override public TypeConstant first() { return types.getFirst(); }
    @Override public List<TypeConstant> all() { return types; }
    @Override public int count() { return types.size(); }
}

record VoidType() implements ExpressionTypes {
    @Override public TypeConstant first() { throw new IllegalStateException("void has no type"); }
    @Override public List<TypeConstant> all() { return List.of(); }
    @Override public int count() { return 0; }
}

// Now the field is type-safe
private ExpressionTypes types;
```

With this design:
- The compiler enforces exhaustive handling
- No runtime casts
- Clear semantics for all cases
- Safe to refactor

### Example 2: Token.java - The Literal Value Void

```java
// Token.java line 41
public Token(long lStartPos, long lEndPos, Id id, Object oValue) {
    m_oValue = oValue;  // Could be String, PackedInteger, Character, BigDecimal, ...
}

// Line 119
public Object getValue() {
    return m_oValue;  // Caller has no idea what type this is!
}
```

Then the code does blind casts based on token type:

```java
// Token.java line 138 - blind cast to String!
Id keywordId = Id.valueByContextSensitiveText((String) getValue());

// Token.java line 149 - another blind cast!
return m_id == Id.IDENTIFIER && Id.valueByContextSensitiveText((String) getValue()) != null;

// Token.java line 173 - and another!
Id id = Id.valueByContextSensitiveText((String) getValue());
```

**Why this is catastrophically bad:**

1. The caller must "know" what type to expect based on the token ID
2. If token ID is wrong, or value is wrong type, ClassCastException at runtime
3. No documentation of which token IDs have which value types
4. Easy to add new token type and forget to handle it somewhere

**What this should be:**

```java
// Sealed type for token values
sealed interface TokenValue permits
    NoValue, StringValue, IntegerValue, DecimalValue, CharValue {

    static TokenValue none() { return NoValue.INSTANCE; }
    static TokenValue of(String s) { return new StringValue(s); }
    static TokenValue of(PackedInteger i) { return new IntegerValue(i); }
    // etc.
}

record NoValue() implements TokenValue {
    static final NoValue INSTANCE = new NoValue();
}
record StringValue(String value) implements TokenValue {}
record IntegerValue(PackedInteger value) implements TokenValue {}
record DecimalValue(BigDecimal value) implements TokenValue {}
record CharValue(char value) implements TokenValue {}

// Token now has proper type
public record Token(long startPos, long endPos, Id id, TokenValue value) {

    public @Nullable String stringValue() {
        return value instanceof StringValue sv ? sv.value() : null;
    }
}
```

### Example 3: Map<Object, ...> - The Key Chaos

This is pervasive throughout TypeInfo and TypeConstant:

```java
// TypeInfo.java line 75
Map<Object, ParamInfo> mapTypeParams,  // Key is String OR NestedIdentity!

// TypeInfo.java lines 86-87
Map<Object, PropertyInfo> mapVirtProps,   // Key is ???
Map<Object, MethodInfo> mapVirtMethods,   // Key is ???

// The comment reveals the madness (TypeInfo.java line 128-131):
// mapTypeParams has two types of entries:
//  - actual generic types keyed by the generic type name
//  - exploded Ref types keyed by the corresponding NestedIdentity
f_fHasGenerics = mapTypeParams.keySet().stream().anyMatch(k -> k instanceof String);
```

**What this means:**

- A single Map can have keys of completely different types
- You must use `instanceof` to figure out what you're dealing with
- `equals()` and `hashCode()` behavior depends on runtime types
- If someone puts the wrong key type in, the map will "work" but produce wrong results

**Why this is catastrophically bad:**

1. **HashMap contract violation**: HashMap relies on `hashCode()` and `equals()` being consistent. Mixing key types makes this unpredictable.

2. **Type erasure hides bugs**: At runtime, `Map<Object, ParamInfo>` is just `Map`. You can put anything in.

3. **Iteration is a minefield**:
```java
for (Object key : mapTypeParams.keySet()) {
    if (key instanceof String name) {
        // handle name case
    } else if (key instanceof NestedIdentity nid) {
        // handle nested identity case
    } else {
        // What goes here? This will be hit if someone adds a third key type
        throw new RuntimeException("Unexpected key type: " + key.getClass());
    }
}
```

**What this should be:**

```java
// Separate maps for different key types
public record TypeParams(
    Map<String, ParamInfo> byName,
    Map<NestedIdentity, ParamInfo> byNested
) {
    public @Nullable ParamInfo get(String name) {
        return byName.get(name);
    }

    public @Nullable ParamInfo get(NestedIdentity nid) {
        return byNested.get(nid);
    }

    public boolean hasGenerics() {
        return !byName.isEmpty();
    }
}
```

Or better yet, just use a sealed key type:

```java
sealed interface ParamKey permits NameKey, NestedKey {
    // proper equals/hashCode
}
record NameKey(String name) implements ParamKey {}
record NestedKey(NestedIdentity id) implements ParamKey {}

Map<ParamKey, ParamInfo> mapTypeParams;  // Now type-safe!
```

### Example 4: AstNode.clone() - Reflection + Object = Disaster

```java
// AstNode.java clone() method (line 220-258)
for (Field field : getChildFields()) {
    Object oVal;
    try {
        oVal = field.get(this);  // Gets Object, loses type
    } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
    }

    if (oVal != null) {
        if (oVal instanceof AstNode node) {
            AstNode nodeNew = node.clone();
            // ...
        } else if (oVal instanceof List list) {  // RAW LIST!
            for (AstNode node : (List<AstNode>) list) {  // UNCHECKED CAST!
                listNew.add(node.clone());
            }
        } else {
            throw new IllegalStateException("unsupported container type: " + oVal.getClass().getSimpleName());
        }
    }
}
```

**What's happening:**
1. Reflection loses all type information
2. Raw `List` (no generic parameter)
3. Unchecked cast to `List<AstNode>` - could crash at runtime
4. If someone adds a `Set<AstNode>` field, it silently breaks

**Why this is catastrophically bad:**

This method will appear to work in testing, then crash in production when:
- A new field type is added
- A List contains non-AstNode elements
- A field has an unexpected subtype

### Example 5: The Double-Cast - Complete Type System Bypass

This is the most insidious pattern. When the compiler won't let you cast directly, you go
through raw types to **completely disable the type system**:

```java
// Parser.java line 1572 - DOUBLE CAST TO BYPASS TYPE CHECKING
return new ForStatement(keyword, (List<Statement>) (List) init, conds, update, parseStatementBlock());

// TypeCompositionStatement.java line 938 - AGAIN!
bifurcator.collectMatchingComponents(condCur, (List<Component>) (List) componentList);

// TypeCompositionStatement.java line 1295 - AND AGAIN!
bifurcator.collectMatchingComponents(null, (List<Component>) (List) componentList);
```

**What `(List<Statement>) (List) init` actually does:**

1. `init` has type `List<AstNode>` (or similar)
2. `(List) init` casts to **raw `List`**, erasing type parameter
3. `(List<Statement>)` casts raw `List` to `List<Statement>`
4. The compiler can't check this because raw types opt out of generics!

This is **deliberate circumvention** of Java's type system. The developer knows the types
don't match, so they use the raw type as a "type laundering" step.

**Why this is catastrophically bad:**

```java
// What the compiler sees:
List<Statement> statements = (List<Statement>) (List) init;  // "Looks fine!"

// What might actually happen at runtime:
for (Statement s : statements) {  // ClassCastException! init contained AstNode, not Statement
    s.compile();
}
```

The error doesn't happen at the cast - it happens later when you iterate, making debugging
a nightmare.

### Example 6: NewExpression.java - Generic Clone That Isn't

```java
// NewExpression.java line 1231-1241
private <T extends AstNode> List<T> clone(List<? extends AstNode> list) {
    if (list == null || list.isEmpty()) {
        return (List<T>) list;  // UNCHECKED! T could be anything
    }

    List listCopy = new ArrayList<>(list.size());  // RAW LIST!
    for (AstNode node : list) {
        listCopy.add(node.clone());  // Adding AstNode to raw List
    }
    return listCopy;  // Returning raw List as List<T> - UNCHECKED!
}
```

**The problem:**

```java
// This compiles fine:
List<Statement> stmts = clone(someExpressionList);  // T = Statement

// But at runtime:
Statement s = stmts.get(0);  // ClassCastException! It's actually an Expression!
```

The method signature promises `List<T>` but the implementation just dumps everything into
a raw `List` and returns it. The type parameter `T` is a complete lie.

### Example 7: The values() Map<String, Object> Cesspool

The `Launcher` class and its subclasses use a `Map<String, Object>` to store command-line
options. Every retrieval requires a blind cast:

```java
// Launcher.java line 1037 - RAW LIST
List list = (List) oVal;

// Launcher.java line 1070 - ADDALL ON RAW TYPES
ArrayList list = v == null ? new ArrayList() : (ArrayList) v;
list.addAll((List) value);  // Raw List addAll to raw ArrayList!

// Compiler.java line 673 - BLIND CAST FROM OBJECT
public List<File> getModulePath() {
    List<File> path = (List<File>) values().get("L");  // Hope it's actually List<File>!
    return path == null ? Collections.emptyList() : path;
}

// Runner.java line 442 - SAME PATTERN
List<String> listArgs = (List<String>) values().get(ArgV);  // Trust me bro

// Runner.java line 452 - NOW IT'S A MAP!
return (Map<String, String>) values().getOrDefault("I", Collections.emptyMap());
```

**The values() method returns `Map<String, Object>` - a type-unsafe hellhole.**

Every caller must:
1. Know the magic string key ("L", "M", "I", etc.)
2. Know what type to cast to
3. Hope nothing else stored a different type with that key

**What this should be:**

```java
// Type-safe options class
public record CompilerOptions(
    List<File> modulePath,
    List<File> inputLocations,
    @Nullable File outputLocation,
    @Nullable String version,
    List<String> arguments
) {
    public static CompilerOptions parse(String[] args) {
        // Parsing logic that produces typed values directly
    }
}
```

## The Numbers

| Pattern | Count in Codebase | Risk Level |
|---------|-------------------|------------|
| `private Object m_` fields | 4 | Critical - type confusion |
| `Map<Object, ` declarations | 60+ | Critical - key type chaos |
| `(List<X>) (List)` double-cast | 3+ | **Critical** - deliberate type bypass |
| `(List<X>) values().get()` | 15+ | **Critical** - blind cast from Object |
| `(String) getValue()` casts | 10+ | High - unchecked casts |
| Raw `List` usage | 7+ | High - type erasure |
| `instanceof` checks | 364 | Medium - missing exhaustiveness |

## Why This Happens: Imperative Laziness

The developers chose `Object` because:

1. **"It's easier"** - Don't have to think about types
2. **"It works"** - Until it doesn't, at runtime
3. **"I'll know what type it is"** - Famous last words
4. **"Java generics are annoying"** - No, they protect you

This is **imperative programmer thinking**: "I'll just put stuff in and pull stuff out." It treats Java as if it were a dynamically typed language.

## The Reality Check

### What the Compiler Could Tell You (But Can't)

If you used proper types, the compiler would:
- Catch missing case handling at compile time
- Prevent putting wrong types into collections
- Enable IDE refactoring
- Make code self-documenting

### What You Get Instead

- Runtime ClassCastExceptions in production
- Silent data corruption when wrong types sneak in
- Brittle code that breaks when anything changes
- Hours of debugging to find type mismatches
- No IDE support for navigation or refactoring

## The Fix: Use the Type System

### Step 1: Define What You Actually Have

If a field can hold "A or B", create:
```java
sealed interface AOrB permits A, B {}
```

### Step 2: Use Generics Correctly

If a Map has String keys:
```java
Map<String, Value> map;  // Not Map<Object, Value>
```

If keys can be multiple types, separate them or use a sealed key type.

### Step 3: Eliminate Object Fields

Every `Object` field should become:
- A specific type if it's always that type
- A sealed interface if it's one of several types
- An Optional if it might be null

### Step 4: Remove Unchecked Casts

Every `(Type) value` cast should be replaced with:
- Pattern matching that handles all cases
- A method that returns the correct type
- A sealed type that the compiler can verify

## Summary

Using `Object` where you should use proper types:
- Disables compile-time checking
- Creates runtime crashes
- Makes code impossible to understand
- Prevents safe refactoring
- Causes bugs that are nearly impossible to debug

**There is no excuse for this in 2025.** Java has:
- Generics (since 2004!)
- Sealed types (since 2021)
- Pattern matching (since 2021)
- Records (since 2020)

Every tool exists to write type-safe code. The XVM codebase simply doesn't want to use them.

The result is code that:
- Cannot be safely modified
- Cannot be understood by new developers
- Cannot be used for LSP (requires type information)
- Cannot be trusted to work correctly

**This is technical debt measured in developer-years.**
