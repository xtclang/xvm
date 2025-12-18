# Constructors That Do Too Much

## The Principle

A constructor should do one thing: **initialize the object to a valid state**. That means:
- Assign parameters to fields
- Set default values
- Establish invariants

A constructor should **never**:
- Perform I/O
- Call virtual methods
- Do significant computation
- Start threads
- Register callbacks
- Modify global state
- Call methods that might fail in complex ways

**Why?** Because constructors are special:
1. You can't return a value (only `this` or throw)
2. The object isn't fully constructed until the constructor returns
3. Subclass constructors haven't run yet
4. Exception handling is awkward
5. Testing is difficult

## Real Examples from the Codebase

### Example 1: Parser Constructor Primes Token Stream

```java
// Parser.java line 56-71
private Parser(Source source, ErrorListener errs, Lexer lexer) {
    if (source == null) {
        throw new IllegalArgumentException("Source required");
    }

    if (errs == null) {
        throw new IllegalArgumentException("ErrorListener required");
    }

    m_source        = source;
    m_errorListener = errs;
    m_lexer         = lexer;

    // prime the token stream
    next();  // <-- THIS IS THE PROBLEM
}
```

**What `next()` does**: It reads from the lexer, which reads from the source, which may involve I/O.

**Why this is bad:**

1. **Constructor does I/O**: If the source is backed by a file, the constructor performs file I/O
2. **Partial construction on failure**: If `next()` throws, the Parser is partially constructed
3. **Side effects in constructor**: Changes state of the lexer
4. **Can't be mocked easily**: For testing, you want to provide a pre-built token stream
5. **Forces immediate work**: Caller might want to defer parsing

**What it should be:**

```java
// Constructor only assigns fields
private Parser(Source source, ErrorListener errs, Lexer lexer) {
    this.source = requireNonNull(source, "Source required");
    this.errorListener = requireNonNull(errs, "ErrorListener required");
    this.lexer = requireNonNull(lexer);
    // NO call to next() here
}

// Separate initialization
public Parser initialize() {
    next();  // Prime the stream
    return this;
}

// Or use a static factory
public static Parser create(Source source, ErrorListener errs) {
    Parser parser = new Parser(source, errs, new Lexer(source, errs));
    parser.next();  // Prime after construction
    return parser;
}
```

### Example 2: Lexer Constructor Creates Token

Looking at the Lexer, it likely has similar issues where the constructor starts processing:

```java
// Typical anti-pattern (conceptual)
public Lexer(Source source, ErrorListener errors) {
    this.source = source;
    this.errors = errors;
    this.position = 0;

    // Start lexing immediately
    this.currentToken = scanNextToken();  // BAD: I/O + computation in constructor
}
```

**Why this is bad:**

1. If source is large, constructor takes a long time
2. If there's a syntax error on the first token, constructor throws
3. Can't create a Lexer and then decide not to use it
4. Can't create a Lexer for testing without real source

### Example 3: Complex Validation in Constructor

```java
// Conceptual pattern found in AST nodes
public TypeExpression(Token token, List<Parameter> params) {
    this.token = token;
    this.params = params;

    // Validate in constructor
    if (!isValidTypeExpression(token, params)) {  // BAD
        throw new IllegalArgumentException("Invalid type expression");
    }

    // Compute derived values
    this.resolvedType = resolveType(token, params);  // BAD
}
```

**Why this is bad:**

1. Validation logic is hard to test in isolation
2. Can't create objects for negative test cases
3. Complex logic that might change is embedded in constructor
4. Resolving types might require context not yet available

### Example 4: Registration in Constructor

```java
// Anti-pattern (conceptual)
public Component(Parent parent) {
    this.parent = parent;

    // Register with parent in constructor
    parent.addChild(this);  // BAD: modifies another object
}
```

**Why this is bad:**

1. Parent now has reference to partially-constructed child
2. If rest of constructor fails, parent has invalid reference
3. Can't create Component without a parent (for testing)
4. Creates bidirectional dependency in constructor

## Why This Matters for Concurrency and Testing

### Testing Impact

With work in constructor:
```java
@Test
void testParserHandlesEmptySource() {
    // Can't create parser without it reading the source!
    Parser parser = new Parser(emptySource, errors);  // Already did work

    // What if I want to test the parser's state BEFORE it reads anything?
    // Can't do it.
}
```

With deferred work:
```java
@Test
void testParserInitialState() {
    Parser parser = new Parser(source, errors);  // Just creates object
    assertNull(parser.currentToken());  // Can test initial state

    parser.initialize();  // Now prime it
    assertNotNull(parser.currentToken());  // Test after priming
}
```

### Concurrency Impact

```java
// Thread 1
Parser parser = new Parser(source, errors);  // Starts reading tokens
// At this point, parser is being constructed AND reading from source

// Thread 2
source.update(newContent);  // Updates source while parser is reading!

// Result: Race condition, possible data corruption
```

With deferred work:
```java
// Thread 1
Parser parser = new Parser(source, errors);  // Just assigns fields
// Construction is complete, object is valid

// Thread 2
source.update(newContent);  // Safe, parser hasn't started reading

// Thread 1 (later)
parser.initialize();  // Now start reading
```

### Exception Handling Impact

```java
// With work in constructor
try {
    Parser parser = new Parser(badSource, errors);
} catch (IOException e) {
    // Parser doesn't exist, can't clean up
    // What partial state was modified?
    // Did the Lexer read some bytes?
}
```

With factory method:
```java
public static Parser create(Source source, ErrorListener errors) throws IOException {
    Lexer lexer = Lexer.create(source, errors);  // Can fail, Parser doesn't exist yet
    Parser parser = new Parser(source, errors, lexer);  // Just assigns
    parser.initialize();  // Can fail, but Parser exists for cleanup
    return parser;
}

try {
    Parser parser = Parser.create(badSource, errors);
} catch (final IOException e) {
    // Clean, predictable failure
}
```

## The Correct Pattern

### Simple Constructor

```java
public class Parser {
    private final Source source;
    private final ErrorListener errors;
    private final Lexer lexer;
    private Token currentToken;
    private boolean initialized;

    // Constructor ONLY assigns fields
    public Parser(Source source, ErrorListener errors, Lexer lexer) {
        this.source = requireNonNull(source);
        this.errors = requireNonNull(errors);
        this.lexer = requireNonNull(lexer);
    }

    // Separate initialization
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Already initialized");
        }
        currentToken = lexer.next();
        initialized = true;
    }

    // Or make initialization automatic but lazy
    public Token currentToken() {
        ensureInitialized();
        return currentToken;
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
```

### Static Factory Method

```java
public class Parser {
    // Private constructor - only assigns
    private Parser(Source source, ErrorListener errors, Lexer lexer) {
        this.source = source;
        this.errors = errors;
        this.lexer = lexer;
    }

    // Public factory - does all the work
    public static Parser create(Source source, ErrorListener errors) {
        Lexer lexer = new Lexer(source, errors);
        Parser parser = new Parser(source, errors, lexer);
        parser.currentToken = lexer.next();  // Prime after construction
        return parser;
    }

    // Factory for testing
    public static Parser forTesting(Token... tokens) {
        MockLexer lexer = new MockLexer(tokens);
        return new Parser(null, ErrorListener.BLACKHOLE, lexer);
    }
}
```

### Builder Pattern for Complex Construction

```java
public class Parser {
    private Parser(Builder builder) {
        this.source = builder.source;
        this.errors = builder.errors;
        this.lexer = builder.lexer;
    }

    public static class Builder {
        private Source source;
        private ErrorListener errors = ErrorListener.BLACKHOLE;
        private Lexer lexer;

        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        public Builder errors(ErrorListener errors) {
            this.errors = errors;
            return this;
        }

        public Builder lexer(Lexer lexer) {
            this.lexer = lexer;
            return this;
        }

        public Parser build() {
            if (lexer == null) {
                lexer = new Lexer(source, errors);
            }
            Parser parser = new Parser(this);
            parser.initialize();
            return parser;
        }
    }
}

// Usage
Parser parser = new Parser.Builder()
    .source(source)
    .errors(errorListener)
    .build();
```

## Summary

| Don't Do This in Constructor | Do This Instead |
|------------------------------|-----------------|
| I/O operations | Factory method or init() |
| Virtual method calls | Direct field assignment |
| Complex computation | Lazy initialization |
| Registration with other objects | Separate registration method |
| Starting threads | Factory method |
| Validation that might fail | Factory method with Result type |
| Anything that takes significant time | Defer to separate method |

**The rule is simple**: After `new Foo(...)` returns, `foo` should be a valid object in its initial state. Nothing more, nothing less.

This isn't Java-specific advice. It's fundamental object-oriented design that appears in every serious programming book. The XVM codebase violates this principle repeatedly, making the code:
- Hard to test
- Hard to understand
- Prone to subtle bugs
- Impossible to use in concurrent contexts
- Difficult to refactor
