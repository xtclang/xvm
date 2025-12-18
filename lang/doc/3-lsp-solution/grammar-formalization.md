# Grammar Formalization and External Tooling

## Overview

This chapter analyzes the feasibility of formalizing XTC's grammar for use with external parsing tools such as Tree-sitter, ANTLR4, or other language infrastructure. The conclusion: **XTC's context sensitivity makes pure grammar-based approaches extremely challenging, but hybrid approaches are feasible.**

## XTC's Context Sensitivity

XTC is not a context-free language. The parser requires semantic information to correctly parse many constructs.

### Context-Sensitive Keywords

The following keywords are context-sensitive—they function as keywords in certain positions but can be used as identifiers elsewhere:

| Keyword | Context | Example as Identifier |
|---------|---------|----------------------|
| `var` | Variable declaration | `Int var = 5;` (valid identifier) |
| `val` | Value declaration | `String val = "x";` (valid identifier) |
| `extends` | Type composition | `Int extends = 1;` (valid identifier) |
| `implements` | Type composition | `Boolean implements = true;` |
| `delegates` | Delegation | `Object delegates = null;` |
| `incorporates` | Mixin | `List incorporates = [];` |
| `into` | Mixin target | `Map into = new HashMap();` |

**Parser code evidence** (`Parser.java:381-383`):
```java
// the keywords below require "match()" to extract them, because they are context
// sensitive
Token keyword;
if ((keyword = match(Id.EXTENDS)) != null) {
```

And from `Parser.java:803`:
```java
// var and val are not reserved keywords; they are context sensitive types
```

### Whitespace-Sensitive Operators

XTC uses whitespace to disambiguate operators:

**Ternary vs NotNull** (`Parser.java:1341`):
```java
// Note the unusual requirement for whitespace before the '?' operator,
// which differentiates this form of expression from the NotNullExpression
if (!name.hasTrailingWhitespace() && peek(Id.COLON) && peek().hasTrailingWhitespace()) {
```

This means:
- `a ? b : c` — ternary expression (whitespace before `?`)
- `a?.b` — null-safe access (no whitespace)

### Template Literals with Embedded Expressions

Template literals (`$"..."`) contain embedded XTC expressions within `{}`:

```ecstasy
$"The value is {computeValue() + offset}"
```

The lexer switches context when it encounters `{` inside a template literal:
- Outside `{}`: string literal mode
- Inside `{}`: full expression mode (recursive parsing)

This requires the lexer to track nesting depth and coordinate with the parser.

### Token Peeling

XTC uses a "token peeling" mechanism where operators like `>>>` can be split during parsing:

**Token.java:210-280** shows `peel()` method:
```java
// Allow a token to be "peeled off" the front of this token
// Example: >>> can become > + >> when parsing nested generics
case USHR:
    newId = Id.SHR;  // >>> becomes >> after peeling >
    break;
```

This handles cases like `List<List<Int>>` where `>>` must be parsed as two `>` tokens.

## Why Pure Grammar Approaches Fail

### Tree-sitter Limitations

Tree-sitter generates fast, incremental parsers from declarative grammars. However:

1. **No semantic context**: Tree-sitter cannot express "this identifier is a keyword only in position X"
2. **No lookahead-based token splitting**: Token peeling requires semantic knowledge
3. **No mode switching**: Template literal parsing requires lexer state
4. **Whitespace significance**: Tree-sitter grammars typically ignore whitespace

**Verdict**: A Tree-sitter grammar could parse ~80% of XTC correctly but would fail on context-sensitive constructs.

### ANTLR4 Limitations

ANTLR4 is more powerful than Tree-sitter:

1. **Semantic predicates**: Can express context-sensitive rules
2. **Lexer modes**: Can handle template literals
3. **Token rewriting**: Could potentially handle token peeling

However:
- Semantic predicates require embedding Java/C# code
- Complex predicates slow down parsing significantly
- Maintaining parity with the real parser is difficult

**Verdict**: An ANTLR4 grammar is feasible but would require significant predicate code and ongoing maintenance.

### LLVM-based Approaches

LLVM's infrastructure (Clang-style parsing) is irrelevant here—LLVM is a compiler backend, not a parser generator. However, LLVM-based tools like `clangd` use hand-written parsers, which is what XTC already has.

## Practical Approaches

### Approach 1: Use the Existing Parser (Recommended)

The XTC parser already handles all context sensitivity correctly. The adapter layer approach documented in [Adapter Layer Design](./adapter-layer-design.md) extracts clean data structures after parsing.

**Pros**:
- 100% correctness guaranteed
- No maintenance burden for grammar parity
- Already implemented and tested

**Cons**:
- Requires running the XTC compiler
- JVM dependency

### Approach 2: Hybrid Tree-sitter + Semantic Pass

Create a "best effort" Tree-sitter grammar for syntax highlighting and basic structure, then use the real parser for semantic accuracy.

```
┌────────────────────────────────────────────────────────────────┐
│                         IDE / Editor                           │
│  ┌─────────────────────────────┐ ┌──────────────────────────┐  │
│  │    Tree-sitter Grammar      │ │     XTC LSP Server       │  │
│  │  (fast, approximate)        │ │  (accurate, semantic)    │  │
│  └──────────────┬──────────────┘ └────────────┬─────────────┘  │
│                 │                              │                │
│         Syntax highlighting           Diagnostics, completion  │
│         Basic folding                 Go-to-definition         │
│         Bracket matching              Type information         │
└────────────────────────────────────────────────────────────────┘
```

**Tree-sitter would handle**:
- Syntax highlighting (even if imperfect for edge cases)
- Bracket matching
- Basic code folding
- Indentation hints

**LSP server would handle**:
- All semantic features
- Accurate diagnostics
- Symbol resolution

**Implementation notes for Tree-sitter grammar**:

```javascript
// tree-sitter-xtc/grammar.js (sketch)
module.exports = grammar({
  name: 'xtc',

  // Treat context-sensitive keywords as identifiers
  // Let semantic highlighting fix them
  rules: {
    source_file: $ => repeat($._definition),

    _definition: $ => choice(
      $.module_declaration,
      $.class_declaration,
      $.interface_declaration,
      // ...
    ),

    // Context-sensitive keywords are just identifiers in the grammar
    identifier: $ => /[a-zA-Z_][a-zA-Z0-9_]*/,

    // Template literals - approximate handling
    template_literal: $ => seq(
      '$"',
      repeat(choice(
        $.template_chars,
        $.template_substitution
      )),
      '"'
    ),

    template_substitution: $ => seq(
      '{',
      $.expression,  // This won't be perfectly accurate
      '}'
    ),
  }
});
```

**Pros**:
- Fast highlighting in editor
- Works offline without JVM
- Good enough for visual feedback

**Cons**:
- Highlighting will be wrong for some edge cases
- Two parsing systems to maintain
- Complexity in IDE integration

### Approach 3: TextMate Grammar (Simpler Alternative)

For syntax highlighting only, a TextMate grammar (regex-based) is simpler than Tree-sitter and sufficient for most needs.

**TextMate grammar excerpt**:
```json
{
  "scopeName": "source.xtc",
  "patterns": [
    {"include": "#comments"},
    {"include": "#strings"},
    {"include": "#keywords"},
    {"include": "#types"}
  ],
  "repository": {
    "keywords": {
      "match": "\\b(module|class|interface|service|const|enum|mixin|if|else|while|for|return|throw)\\b",
      "name": "keyword.control.xtc"
    },
    "context-sensitive-keywords": {
      "comment": "These might be keywords OR identifiers - highlight as keywords in likely positions",
      "match": "\\b(extends|implements|incorporates|delegates|into)\\b(?=\\s+[A-Z])",
      "name": "keyword.other.xtc"
    }
  }
}
```

**Pros**:
- Very simple to implement
- Works in VSCode, TextMate, Sublime, etc.
- No external dependencies

**Cons**:
- Regex-based, so limited accuracy
- No structural understanding (can't match brackets, etc.)

## Formalization Feasibility Summary

| Approach | Feasibility | Accuracy | Effort | Recommendation |
|----------|------------|----------|--------|----------------|
| Tree-sitter alone | Low | ~80% | 3-4 weeks | Not recommended |
| ANTLR4 alone | Medium | ~95% | 6-8 weeks | Not recommended |
| Tree-sitter + LSP | High | 100%* | 2-3 weeks | Good for highlighting |
| TextMate + LSP | High | 100%* | 1 week | **Recommended** |
| LSP only (real parser) | Very High | 100% | Already done | **Best approach** |

*\*Semantic accuracy from LSP; syntax highlighting approximate*

## Recommendation

1. **Don't try to fully formalize XTC's grammar** in a context-free form—it's not context-free.

2. **Use TextMate grammar for syntax highlighting** in IDEs. It's simple, widely supported, and good enough for visual feedback.

3. **Rely on the LSP server for semantic accuracy**. The adapter layer approach gives you correct parsing without grammar maintenance.

4. **If Tree-sitter is required** (e.g., for Neovim), create an approximate grammar with the understanding that:
   - Some tokens will be mis-highlighted
   - The LSP server provides the authoritative view
   - Edge cases are acceptable for syntax highlighting

## ANTLR4 Grammar Fragment (Reference Only)

For those who want to attempt ANTLR4, here's a sketch of how context-sensitivity would be handled:

```antlr
// XTCParser.g4 (NOT RECOMMENDED - for reference only)
grammar XTC;

// Semantic predicate for context-sensitive keywords
@members {
    private boolean isInCompositionContext = false;
}

typeComposition
    : {isInCompositionContext = true;}
      compositionClause*
      {isInCompositionContext = false;}
    ;

compositionClause
    : EXTENDS typeExpression      // 'extends' as keyword
    | IMPLEMENTS typeExpression   // 'implements' as keyword
    | DELEGATES typeExpression    // etc.
    ;

// Context-sensitive keyword - acts as identifier outside composition
identifier
    : IDENTIFIER
    | {!isInCompositionContext}? EXTENDS
    | {!isInCompositionContext}? IMPLEMENTS
    | {!isInCompositionContext}? DELEGATES
    ;

// Lexer rules - keywords are always lexed, parser decides meaning
EXTENDS : 'extends';
IMPLEMENTS : 'implements';
DELEGATES : 'delegates';
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]*;
```

This works but becomes unwieldy as more context-sensitive features are added.

## Conclusion

XTC's grammar cannot be cleanly formalized because it is inherently context-sensitive. The practical solution is:

1. **TextMate/Tree-sitter for approximate highlighting** (fast, editor-native)
2. **LSP server with real parser for semantic accuracy** (correct, complete)

This hybrid approach gives the best of both worlds without the maintenance burden of keeping a formal grammar in sync with the actual parser.
