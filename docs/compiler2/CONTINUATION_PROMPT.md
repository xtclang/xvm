# Continuation Prompt for Compiler2 Development

Copy and paste this prompt to continue the session:

---

I'm continuing work on the XVM compiler2 project - a Roslyn-style incremental compiler with green/red tree architecture.

**Branch:** `lagergren/comp2`
**Working Directory:** `/Users/marcus/src/xvm-compiler2`

## What Was Completed

1. **Green/Red Tree Foundation** - Immutable green nodes with interning, red tree wrapper with parent navigation
2. **GreenLexer** - Completely immutable lexer using `TokenResult(token, nextLexer)` pattern
3. **GreenParser** - Full expression parsing (15 precedence levels) including member access, invocation, index, conditional, assignment
4. **CompilationContext** - Unified context with source tracking, diagnostics, SLF4J-style logging
5. **All tests passing** (40+ tests)

## Key Design Principles
- NO reflection anywhere
- NO clone() - use copy-on-write with `withChild()`
- Immutable green tree (no parent pointers)
- Disposable red tree (parent pointers computed on demand)
- Single unified context for lexing, parsing, compilation

## Important Files
- `GreenLexer.java` - immutable lexer
- `GreenParser.java` - parser with full expression support
- `CompilationContext.java` - unified context
- `GreenNode.java` - base class for all green nodes
- `SyntaxNode.java` - red tree wrapper

## Session State
See `/Users/marcus/src/xvm-compiler2/docs/compiler2/SESSION_STATE.md` for full details.

## Run Tests
```bash
cd /Users/marcus/src/xvm-compiler2
./gradlew javatools:test --tests "org.xvm.compiler2.*"
```

Please read SESSION_STATE.md first, then let me know what you'd like to work on next. Potential next steps:
- More statement types (if, while, for, var)
- Declaration parsing (classes, methods)
- Type expression parsing
- Incremental reparsing
- Semantic analysis

---
