# Lexer/Parser Thread Safety

## The Short Version

The XTC Lexer and Parser classes maintain extensive mutable state that makes them inherently single-threaded. An LSP server needs to parse multiple files concurrently as the user types, but the current design requires one parser instance per file with no way to share parsing infrastructure safely.

## What Is the Problem?

A parser needs to track its current position in the source code, the current token, lookahead buffers, and error state. In the XTC compiler, this state is stored in instance fields that are mutated throughout parsing:

```java
// Parser.java - mutable parsing state
private Token m_tokenPutBack;      // Put-back buffer
private Token m_tokenPrev;         // Previous token
private Token m_token;             // Current token
private Token m_doc;               // Documentation token
private StatementBlock m_root;     // Parse result
private boolean m_fDone;           // Completion flag
private boolean m_fAvoidRecovery;  // Error recovery flag
private SafeLookAhead m_lookAhead; // Lookahead state
```

Every call to `next()`, `peek()`, or any parsing method mutates this state.

## Where Does XTC Do This?

### Parser State (Parser.java:5719-5770)

```java
public class Parser {
    private final Source m_source;              // Line 5719
    private ErrorListener m_errorListener;      // Line 5724 - NOT final!
    private final Lexer m_lexer;                // Line 5729
    private Token m_tokenPutBack;               // Line 5734
    private Token m_tokenPrev;                  // Line 5739
    private Token m_token;                      // Line 5744
    private Token m_doc;                        // Line 5749
    private StatementBlock m_root;              // Line 5755
    private boolean m_fDone;                    // Line 5760
    private boolean m_fAvoidRecovery;           // Line 5765
    private SafeLookAhead m_lookAhead;          // Line 5770
```

Note that `m_errorListener` isn't even final - it can change during parsing!

### Lexer State (Lexer.java:2850-2860)

```java
public class Lexer {
    private final Source m_source;               // Line 2850
    private final ErrorListener m_errorListener; // Line 2855
    private boolean m_fWhitespace;               // Line 2860
```

The Lexer is simpler but still maintains mutable whitespace tracking state.

### Source State (Source.java)

```java
public class Source {
    // The actual source text plus position tracking
    private final char[] m_ach;
    private int m_of;        // Current offset - MUTABLE
    private int m_cLines;    // Line count - MUTABLE
```

The `Source` class tracks the current read position, making it impossible to share across threads.

## Why Does This Block LSP?

### Problem 1: No Concurrent Parsing

An LSP server receives requests like:
- User types in file A
- User hovers over symbol in file B
- Background indexing of file C

With mutable parser state, you cannot parse these concurrently. Options are:
1. **Serial processing**: One file at a time (too slow for responsive IDE)
2. **Parser per request**: Create new Lexer/Parser for each operation (expensive)
3. **Parser pool**: Pre-create parsers and check them out (complex, still limited)

### Problem 2: No Incremental Parsing

When the user types a single character, ideally you'd re-parse only the affected region. But the Parser has no support for this:

```java
// No way to say "re-parse lines 50-60 only"
// Must start from scratch every time
Parser parser = new Parser(source, errorListener);
Statement stmt = parser.parseSource();  // Parses ENTIRE file
```

### Problem 3: Error State Leaks

The `ErrorListener` is passed to both Lexer and Parser and accumulates errors. There's no isolation:

```java
// Errors from parsing file A leak into file B if same listener reused
Parser parserA = new Parser(sourceA, sharedListener);
parserA.parseSource();  // Logs errors

Parser parserB = new Parser(sourceB, sharedListener);  // Still has A's errors!
```

### Problem 4: No Parse Cancellation

LSP needs to cancel in-progress parsing when the user types again (invalidates previous request). The Parser has no cancellation mechanism:

```java
// No way to interrupt
parser.parseSource();  // Runs to completion or error

// What we need:
parser.parseSource(cancellationToken);  // Respects cancellation
```

## The Scale of the Problem

| Parser Component | Mutable Fields | Thread-Safe | Shareable |
|------------------|----------------|-------------|-----------|
| Source | 2 (offset, lines) | No | No |
| Lexer | 1 (whitespace) | No | No |
| Parser | 11 fields | No | No |
| ErrorListener | Varies | No | No |
| Token | Cached values | Partially | After creation |

## What Should Be Done Instead?

### Solution 1: Immutable Source with Position Object

```java
// Immutable source text
public record SourceText(String content, String path, int[] lineOffsets) {
    public char charAt(int offset) {
        return content.charAt(offset);
    }

    public Position position(int offset) {
        // Binary search lineOffsets for line number
        return new Position(line, column, offset);
    }
}

// Separate position tracking
public record LexerPosition(int offset, boolean afterWhitespace) {}
```

### Solution 2: Functional Lexer

```java
// Lexer as pure function
public record LexerResult(Token token, LexerPosition nextPosition) {}

public class Lexer {
    public static LexerResult nextToken(SourceText source, LexerPosition position) {
        // Pure function - no mutable state
        // Returns token AND next position
    }
}
```

### Solution 3: Parser with Explicit State

```java
// Parser state passed explicitly
public record ParserState(
    LexerPosition lexerPos,
    Token current,
    Token previous,
    List<Token> lookahead
) {}

public class Parser {
    public static ParseResult<Statement> parseStatement(
        SourceText source,
        ParserState state
    ) {
        // Returns new state, doesn't mutate
    }
}

public record ParseResult<T>(T result, ParserState nextState, List<Diagnostic> errors) {}
```

### Solution 4: Thread-Local Parser Instances (Quick Fix)

For immediate improvement without major refactoring:

```java
public class ParserFactory {
    private static final ThreadLocal<Parser> PARSER_CACHE = new ThreadLocal<>();

    public static ParseResult parse(Source source) {
        Parser parser = PARSER_CACHE.get();
        if (parser == null) {
            parser = new Parser();
            PARSER_CACHE.set(parser);
        }
        parser.reset(source);  // Need to add reset() method
        return parser.parseSource();
    }
}
```

### Solution 5: Incremental Parser Design

For proper LSP support, use an incremental parsing architecture:

```java
public class IncrementalParser {
    // Parse tree with edit tracking
    private SyntaxTree tree;

    public void applyEdit(TextEdit edit) {
        // Find affected nodes
        List<SyntaxNode> affected = tree.findAffected(edit.range());

        // Re-parse only affected region
        LexerPosition start = tree.positionBefore(affected.get(0));

        // Parse and splice into tree
        tree.replaceNodes(affected, reparseRegion(start, edit));
    }
}
```

## Migration Path

### Phase 1: Add Reset Method (Day 1)

```java
// Add to Parser.java
public void reset(Source source) {
    this.m_source = source;
    this.m_tokenPutBack = null;
    this.m_tokenPrev = null;
    this.m_token = null;
    this.m_doc = null;
    this.m_root = null;
    this.m_fDone = false;
    this.m_fAvoidRecovery = false;
    this.m_lookAhead = null;
    this.m_lexer.reset(source);
}
```

### Phase 2: Add Cancellation (Week 1)

```java
public void setCancellationToken(CancellationToken token) {
    this.m_cancellation = token;
}

// Check periodically during parsing
private void checkCancellation() {
    if (m_cancellation != null && m_cancellation.isCancelled()) {
        throw new ParseCancelledException();
    }
}
```

### Phase 3: Parser Pool (Week 2)

```java
public class ParserPool {
    private final BlockingQueue<Parser> available = new LinkedBlockingQueue<>();
    private final int maxParsers;

    public Parser acquire() throws InterruptedException {
        Parser parser = available.poll();
        return parser != null ? parser : createParser();
    }

    public void release(Parser parser) {
        parser.reset();
        available.offer(parser);
    }
}
```

### Phase 4: Functional Core (Long-term)

Gradually refactor parsing methods to pure functions that return new state rather than mutating fields.

## The Numbers

| Issue | Count | Impact |
|-------|-------|--------|
| Mutable Parser fields | 11 | No concurrent parsing |
| Mutable Lexer fields | 1+ | No shared lexing |
| Mutable Source fields | 2 | No shared source access |
| Cancellation points | 0 | Can't cancel long parses |
| Reset methods | 0 | Can't reuse parsers |
| Thread-safe parse methods | 0 | Must create new instances |

## Summary

The Lexer and Parser are fundamentally designed for single-threaded, one-shot use:
- Create instance
- Parse entire file
- Discard instance

LSP requires:
- Concurrent parsing of multiple files
- Incremental re-parsing on edit
- Cancellation of stale requests
- Reusable parsing infrastructure

The current design provides none of these. Solutions range from quick fixes (thread-local, pooling) to proper redesign (functional parsing, incremental trees).
