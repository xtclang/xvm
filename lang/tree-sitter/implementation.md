# Tree-sitter Grammar Implementation Notes

This document captures the implementation history, challenges encountered, and solutions
developed while building the XTC tree-sitter grammar.

> **See also**: [PLAN_TREE_SITTER.md](./PLAN_TREE_SITTER.md) for current status and next steps.
> **See also**: [lang/tree-sitter/README.md](../../tree-sitter/README.md) for usage documentation.

---

## Grammar Feature Support

The following XTC language features are supported by the tree-sitter grammar:

### Type System

| Feature | Example | Notes |
|---------|---------|-------|
| Typedef declarations | `typedef String\|Int as Value` | With doc comments, annotations |
| Union types | `Type1 \| Type2` | Binary operator |
| Intersection types | `Type1 + Type2` | Binary operator |
| Difference types | `Type1 - Type2` | Binary operator |
| Immutable modifier | `immutable Map` | In types |
| Conditional keyword | `conditional Int method()` | Return types |
| Non-null type modifier | `Type!` | No auto-narrowing |
| Array types | `Type[]`, `Type?[]` | Including nullable arrays |
| Generic types | `Map<Key, Value>` | With constraints |
| Function types | `function Type(Args)` | Named and unnamed params |
| Tuple types | `(Boolean found, Int index)` | Named elements |

### Declarations

| Feature | Example | Notes |
|---------|---------|-------|
| Module declarations | `module foo.bar { }` | With delegates clause |
| Package declarations | `package foo { }` | With inline imports |
| Class declarations | `class Foo extends Bar { }` | All modifiers |
| Interface declarations | `interface Foo extends Bar;` | Semicolon termination |
| Mixin declarations | `mixin Foo(Type arg) { }` | Constructor params |
| Service declarations | `static service Runner { }` | Static modifier |
| Const declarations | `const Point(Int x, Int y)` | Tuple-style |
| Enum declarations | `enum Status { Active, Inactive }` | With default clause |
| Annotation declarations | `annotation Foo into Bar` | XTC annotation types |
| Typedef declarations | `typedef Type as Alias;` | Local and top-level |

### Expressions

| Feature | Example | Notes |
|---------|---------|-------|
| Tuple expressions | `(True, value)` | Multi-value returns |
| Lambda expressions | `x -> x * 2` | Arrow syntax |
| Throw expressions | `throw new Exception()` | In lambdas |
| Assert expressions | `value ?: assert` | Safe-call-else |
| TODO expressions | `TODO`, `TODO(msg)`, `TODO text` | All variants |
| Else expressions | `expr?.method() : fallback` | Short-circuit |
| Ternary expressions | `cond ? a : b` | Standard |
| Async calls | `write^(buffer)` | `^(` token |
| Safe calls | `obj?.method()`, `obj?(args)` | Null-safe |
| Reference operator | `&hasher`, `that.&hasher` | No-dereference |
| Wildcard expressions | `accumulate(_, sum, _)` | Partial application |

### Statements

| Feature | Example | Notes |
|---------|---------|-------|
| Labeled statements | `Loop: for (...)` | For break/continue |
| Using blocks | `using (val x = expr) { }` | With resources |
| Try-with-resources | `try (Type x = expr) { }` | Variable declaration |
| Switch statements | `switch (x) { case A: ... }` | Statement context |
| Switch expressions | `return switch (x) { case A: val; }` | Expression context |
| For loops | `for (Int i = 0, Int c = n; ...)` | Multiple initializers |
| For-each loops | `for ((K k, V v) : map)` | Tuple destructuring |
| Tuple assignment | `(Int x, this.y) = expr;` | Destructuring |

### Literals

| Feature | Example | Notes |
|---------|---------|-------|
| Template strings | `$"Hello {name}!"` | String interpolation |
| Multiline templates | `$\|Line 1\n \|Line 2` | Continuation lines |
| Typed literals | `Map:[]`, `Duration:0S` | Type prefix |
| File literals | `#./CharCats.dat` | Embedded resources |
| Range literals | `..<`, `>..`, `>..<`, `..` | All 4 variants |
| Unicode escapes | `'\u0000'`, `"\U00010000"` | In char/string |

---

## Implementation Challenges and Solutions

### Challenge 1: String Interpolation

**Problem**: Template strings like `$"Hello {name}!"` require tracking brace depth within strings.

**Solution**: External scanner with stateless design:
- Different external tokens for single-line (`$"..."`) vs multiline (`$|...|`) templates
- Scanner checks `valid_symbols` to determine context
- No state serialization needed - tree-sitter's grammar tracks context

### Challenge 2: TODO Freeform Text

**Problem**: `TODO message text` consumes everything to end-of-line, but this conflicts with expression contexts.

**Solution**: Hybrid approach with two external tokens:
- `_todo_freeform_text`: Consumes to EOL (for statements)
- `_todo_freeform_until_semi`: Stops at `;` (for switch expressions)
- `'TODO'` is an internal keyword matched by tree-sitter's lexer
- External scanner called at the space position after TODO

**Switch disambiguation**:
```
// Statement context: freeform consumes to EOL
case '+': TODO ReservedString
          break;

// Expression context: freeform stops at ';'
case Info: TODO new DBValue();
```

The bare `TODO` in `expression_case_clause` is given `prec(-500)` so `switch_statement` is preferred.

### Challenge 3: Type vs Expression Ambiguity

**Problem**: Many contexts are ambiguous between types and expressions:
- `class Foo` - is `Foo` a type name or identifier?
- `Map<K, V>` - could be generic type or comparison

**Solution**: GLR parsing with explicit conflict declarations:
```javascript
conflicts: $ => [
    [$._expression, $.type_name],
    [$._expression, $.qualified_type_name],
    [$.generic_type, $.member_type],
    // ... 49 total conflicts
]
```

### Challenge 4: Nullable Array Types

**Problem**: `Type?[]` (nullable array of Type) conflicts with `Type?[index]` (safe index access).

**Solution**: Tokenize `?[` as a single token when in expression context:
```javascript
safe_index_expression: $ => prec(MEMBER_ACCESS, seq(
    $._expression,
    token.immediate('?['),
    $._expression,
    ']',
)),
```

### Challenge 5: Statement Blocks in Templates

**Problem**: `{{if (cond) {...}}}` in multiline templates contains nested braces with `|` continuations.

**Solution**: External scanner tracks brace depth and `|` continuation:
```c
// In scanner.c
if (valid_symbols[MULTILINE_STMT_BLOCK]) {
    // Count braces, handle | at line starts
    // Return when outer }} reached
}
```

---

## Coverage Improvement Path

The grammar coverage improved from 9% to 100% through 94 incremental steps:

| Milestone | Coverage | Key Changes |
|-----------|----------|-------------|
| Initial | 9% (62/675) | Basic declarations and expressions |
| Step 3 | 21% (144/675) | Added typedef, union/intersection types |
| Step 6 | 60.5% (419/692) | Else expression, short-circuit, TODO |
| Step 8 | 66.0% (457/692) | Doc comments, statement expressions |
| Step 12 | 70.7% (489/692) | Angle bracket type lists, empty type args |
| Step 17 | 73.6% (509/692) | Generic type patterns in switch |
| Step 19 | 79.2% (548/692) | Annotation declarations |
| Step 25 | 82.1% (568/692) | Fixed ternary vs postfix `?` conflict |
| Step 32 | 86.0% (595/692) | Conditional tuple wildcards, for-each bare identifier |
| Step 35 | 85.8% (593/691) | String interpolation external scanner |
| Step 45 | 91.8% (634/691) | Star imports, trailing commas |
| Step 57 | 95.9% (663/691) | Using bare expressions, property body defaults |
| Step 68 | 98.0% (677/691) | Method parameter annotations, switch val/var |
| Step 82 | 99.9% (690/691) | Doc comment as standalone class member |
| Step 94 | **100%** (692/692) | TODO freeform in shorthand function bodies |

---

## Error Patterns Encountered

Common parse failures during development:

| Pattern | Example | Solution |
|---------|---------|----------|
| Star imports | `import web.*;` | Added `token.immediate('.*')` |
| Trailing commas | `[A, B, C, ]` | Made comma optional before `]` |
| Import in type body | `interface I { import X; }` | Added to `_class_member` |
| Module properties | `@Inject Console console;` | Added to `module_body` |
| Static service | `static service Runner` | Added `static` to service modifiers |
| Assert variants | `?: assert:bounds as msg` | Added all assert keywords |
| Multiple extends | `extends A, B` | Changed to repeat |
| Safe index | `arr?[index]` | Added `?[` token |
| Template shorthand | `{name=}` | Added shorthand expression rule |
| Module delegates | `module delegates Type(expr)` | Added `delegates_clause` |

---

## TODO Deviations from Reference Parser

The tree-sitter grammar required source file modifications for `TODO freeform text`:

| File | Original | Changed To |
|------|----------|------------|
| `ConcurrentHasherMap.x` | `TODO copy the partitions` | `TODO("copy the partitions");` |
| `Float128.x` | `TODO return 0.0` | `TODO;  // return 0.0` |
| `Float32.x` | `TODO think this through` | `TODO;  // think this through` |
| `Float64.x` | `TODO think this through` | `TODO;  // think this through` |
| `BFloat16.x` | `TODO think this through` | `TODO;  // think this through` |
| `FloatN.x` | `TODO return 0.0` | `TODO;  // return 0.0` |
| `DecN.x` | `TODO return 0.0` | `TODO;  // return 0.0` |
| `LinkedList.x` | `TODO check interval...` | `TODO("check interval...");` |
| `Char.x` | `TODO CharCombineClass.dat` | `TODO("CharCombineClass.dat");` |
| `NullableFormat.x` | `TODO CP think this through` | `TODO("CP think this through");` |
| `Client.x` | `TODO new DBMap<...>()` | `TODO;  // new DBMap<...>()` |
| `ModuleGenerator.x` | `TODO AnnotatingComposition` | `TODO("AnnotatingComposition");` |
| `JsonProcessorStore.x` | `TODO process the changes` | `TODO("process the changes");` |
| `UriTemplate.x` | `TODO ReservedString` | `TODO("ReservedString");` |
| `http.x` | `TODO check char...` | `TODO("check char...");` |
| `ChainBundle.x` | `TODO find a converter...` | `TODO("find a converter...");` |
| `Http1Request.x` | `TODO multi-part body...` | `TODO("multi-part body...");` |
| `TypeTemplate.x` | `TODO <=>` | `TODO("<=>");` |
| `Type.x` | `TODO <=>` | `TODO("<=>");` |

> **Note**: These changes were later made unnecessary by the hybrid TODO token approach,
> which correctly handles TODO freeform text via the external scanner.

---

## Conflict Optimization (2026-01-30)

The grammar conflicts were optimized from 133 to 49:

**Before**: 133 conflicts declared, ~84 marked as "unnecessary" by tree-sitter
**After**: 49 conflicts declared, 0 warnings

The remaining 49 conflicts are genuinely required for GLR parsing:
- Expression vs type ambiguities
- Pattern matching conflicts
- Tuple/function type parameter conflicts
- Conditional type disambiguation

---

## External Scanner Architecture

The tree-sitter external scanner is generated from Kotlin source files using a code generator.
This section documents the architecture in detail.

### File Locations

```
lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/
├── ScannerSpec.kt          # Token definitions and rules (single source of truth)
├── CCodeDsl.kt             # Kotlin DSL for generating C code
└── ScannerCGeneratorDsl.kt # Generates scanner.c using the DSL

lang/dsl/build/generated/src/
└── scanner.c               # Generated C code (~560 lines)
```

### Design Philosophy: Stateless Scanner

The scanner is completely stateless. Instead of maintaining parser state, it:
- Uses tree-sitter's `valid_symbols` array to determine context
- Single-line templates (`$"..."`) have different tokens than multiline (`$|...|`)
- Checks which tokens are valid to know what type of content to scan
- This approach avoids complex state management and is more maintainable

### Token Types (11 Total)

Defined in `ScannerSpec.kt` as an enum:

**Template Tokens:**
| Token | Description |
|-------|-------------|
| `SINGLELINE_CONTENT` | Content inside `$"..."` |
| `SINGLELINE_EXPR_START` | `{` for expressions in single-line template |
| `SINGLELINE_END` | `"` closing quote |
| `MULTILINE_CONTENT` | Content inside `$\|...\|` |
| `MULTILINE_EXPR_START` | `{` for expressions in multiline template |
| `MULTILINE_END` | End of multiline (no `\|` continuation) |

**Expression Tokens:**
| Token | Description |
|-------|-------------|
| `TEMPLATE_EXPR_END` | `}` closing brace (both template types) |

**Statement Block Tokens:**
| Token | Description |
|-------|-------------|
| `SINGLELINE_STMT_BLOCK` | `{{...}}` patterns in single-line template |
| `MULTILINE_STMT_BLOCK` | `{{...}}` patterns in multiline template |

**TODO Tokens:**
| Token | Description |
|-------|-------------|
| `TODO_FREEFORM_TEXT` | Free text after TODO keyword (consumes to EOL) |
| `TODO_FREEFORM_UNTIL_SEMI` | Free text stopping at `;` (for expressions) |

### Generated C Code Structure

The generated `scanner.c` contains:

**Required Tree-sitter API Functions:**
```c
void *tree_sitter_xtc_external_scanner_create(void)
void tree_sitter_xtc_external_scanner_destroy(void *payload)
unsigned tree_sitter_xtc_external_scanner_serialize(void *payload, char *buffer)
void tree_sitter_xtc_external_scanner_deserialize(void *payload, const char *buffer, unsigned length)
bool tree_sitter_xtc_external_scanner_scan(void *payload, TSLexer *lexer, const bool *valid_symbols)
```

**Helper Functions:**
| Function | Description |
|----------|-------------|
| `advance()` | Consume next character |
| `at_eof()` | Check end of file |
| `peek()` | Look at current character |
| `is_hspace()` | Check for space or tab |
| `skip_multiline_continuation()` | Skip whitespace and `\|` marker |
| `scan_stmt_block()` | Track brace depth in `{{...}}` blocks |
| `scan_todo_to_eol()` | Consume to end of line |
| `scan_todo_freeform_text()` | Match TODO text |
| `scan_todo_freeform_until_semi()` | Match TODO until `;` |

### Main Scan Function Logic

1. Determine context from `valid_symbols` (single-line vs multiline)
2. Handle TODO freeform text (space + message)
3. Handle expression end (`}`)
4. Single-line template scanning (content until `{` or `"`)
5. Multiline template scanning (content until `{` or newline without `|`)
6. Track statement blocks `{{...}}` with brace depth counting
7. Handle escape sequences (`\\` and next character)
8. Handle string literals in code blocks

### Complex Pattern Handling

**Statement Blocks in Templates `{{...}}`:**
- Tracks nested brace depth
- Handles string/char literals (don't count braces inside quotes)
- Handles escape sequences
- For multiline templates, respects `|` continuation markers
- Matches entire block as a single token

**TODO Freeform Text:**
- Two variants depending on context
- Checks for space after TODO keyword
- Distinguishes between statement (consume to EOL) and expression (consume to `;`)
- Lookahead logic to decide which token to emit

**Template Content:**
- Single-line: stops at `{` (expression start) or `"` (template end)
- Multiline: stops at `{` (expression start) or newline without continuation
- Both handle escape sequences

### Gradle Integration

**dsl/build.gradle.kts:**
- `generateScannerC` task runs `ScannerCGeneratorDslKt.main()`
- Depends on `generateTreeSitter` task first
- Outputs to `build/generated/src/scanner.c`
- Declared input: Kotlin source files
- Declared output: scanner.c file

**tree-sitter/build.gradle.kts:**
- `copyGrammarFiles` task copies scanner.c from DSL project
- Integrates with native library build process
- Part of the broader tree-sitter grammar validation and compilation

### Regeneration

```bash
./gradlew :lang:dsl:generateScannerC
```

### Special Features

- **Debug Support**: Generated code includes `#define SCANNER_DEBUG 1` with debug `fprintf` statements
- **Error Recovery**: Checks if both single-line and multiline tokens are valid simultaneously (error recovery mode)
- **Configuration Cache Compatible**: No state serialization needed (stateless design)
- **Character Escaping**: Proper C escaping for special characters (quotes, backslashes, newlines)
- **Template Awareness**: Understands both single-line (`$"..."`) and multiline (`$|...|`) template syntax

---

## Test Files for Specific Patterns

```bash
# Test specific files (timing shown by default)
./gradlew :lang:tree-sitter:testTreeSitterParse \
  -PtestFiles="UriTemplate,TypeTemplate,Client,Random,BFloat16,Float128"

# To disable timing output
./gradlew :lang:tree-sitter:testTreeSitterParse -PshowTiming=false
```

| File | Patterns Tested |
|------|-----------------|
| UriTemplate.x | TODO in switch statements with break |
| TypeTemplate.x | TODO in switch statements with fall-through |
| Client.x | Bare TODO in switch expressions |
| Random.x | Shorthand function body with TODO freeform |
| BFloat16.x | Bare TODOs and freeform TODOs |
| Float128.x | Bare TODOs and freeform TODOs |
