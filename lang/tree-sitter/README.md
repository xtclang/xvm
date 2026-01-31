# Tree-sitter Grammar for XTC

This package provides a tree-sitter grammar for the XTC language, enabling fast, incremental parsing
for syntax highlighting, code navigation, and IDE features.

## Quick Start

```bash
# Generate, validate, and test grammar in one command
./gradlew :lang:tree-sitter:testTreeSitterParse

# Parse a specific file manually (from project root)
cd lang/tree-sitter/build/generated
../tree-sitter-cli/tree-sitter parse /path/to/file.x

# Check for errors only
../tree-sitter-cli/tree-sitter parse /path/to/file.x 2>&1 | grep -E "(ERROR|MISSING)"
```

## Grammar Generation

The grammar is generated from the XTC language model in `lang/dsl/`:

| Task | Description |
|------|-------------|
| `./gradlew :lang:dsl:generateTreeSitter` | Generate `grammar.js` from `XtcLanguage.kt` |
| `./gradlew :lang:dsl:generateScannerC` | Generate `scanner.c` from `ScannerSpec.kt` |
| `./gradlew :lang:tree-sitter:validateTreeSitterGrammar` | Compile and validate grammar |
| `./gradlew :lang:tree-sitter:testTreeSitterParse` | Test against 692 XTC files from `lib_*` |

## Directory Structure

```
lang/
├── tree-sitter/
│   ├── README.md                # This file
│   ├── implementation.md        # Implementation history and details
│   └── build/
│       ├── generated/           # Run tree-sitter CLI from HERE
│       │   ├── grammar.js       # Generated grammar
│       │   ├── src/parser.c     # Generated parser
│       │   └── src/scanner.c    # Generated external scanner
│       └── tree-sitter-cli/
│           └── tree-sitter      # Auto-downloaded CLI binary
└── dsl/
    └── src/main/
        ├── kotlin/org/xtclang/tooling/
        │   ├── generators/TreeSitterGenerator.kt
        │   └── scanner/
        │       ├── ScannerSpec.kt          # Token definitions (single source of truth)
        │       ├── CCodeDsl.kt             # Kotlin DSL for C code generation
        │       └── ScannerCGeneratorDsl.kt # DSL-based scanner.c generator
        └── resources/templates/
            └── grammar.js.template         # Grammar template
```

## Scanner Generator Architecture

The external scanner (`scanner.c`) is generated from Kotlin using a DSL-based code generator.

### DSL-Based Generator

`ScannerCGeneratorDsl.kt` uses a Kotlin DSL (`CCodeDsl.kt`) to generate C code with:

- **Automatic indentation** - No manual spacing in templates
- **Reusable patterns** - `emitToken()`, `handleEscape()`, `debugBlock {}`
- **Type-safe construction** - Kotlin compiler catches structural errors
- **Composable builders** - Small functions build complex C code

Example:

```kotlin
private fun generateExprEndHandling() = cCode {
    ifBlock("in_expr && peek(lexer) == '}'") {
        advance()
        emitToken("TEMPLATE_EXPR_END")
    }
}
```

Benefits of the DSL approach:

1. **No string escaping issues** - C quotes and backslashes handled by helpers
2. **Consistent formatting** - DSL enforces structure
3. **Easier refactoring** - Extract common patterns into reusable functions
4. **Better IDE support** - Kotlin tooling understands the structure

## Architecture

### External Scanner

Tree-sitter's external scanner handles tokens that cannot be expressed with regular expressions:

| Token Type | Context | Purpose |
|------------|---------|---------|
| `_singleline_content` | `$"..."` | String content in single-line template |
| `_singleline_expr_start` | `$"..."` | `{` opening expression in template |
| `_singleline_end` | `$"..."` | `"` closing delimiter |
| `_multiline_content` | `$\|...\|` | String content in multiline template |
| `_multiline_expr_start` | `$\|...\|` | `{` opening expression in template |
| `_multiline_end` | `$\|...\|` | End when no `\|` continuation |
| `_template_expr_end` | Both | `}` closing expression |
| `_multiline_stmt_block` | `$\|...\|` | `{{...}}` statement blocks |
| `_singleline_stmt_block` | `$"..."` | `{{...}}` statement blocks |
| `_todo_freeform_text` | Statement | `TODO message` to end of line |
| `_todo_freeform_until_semi` | Expression | `TODO message` stops at `;` |

### Stateless Scanner Design

The scanner is **stateless** - it uses tree-sitter's `valid_symbols` array to determine context:

```
Grammar structure:
  template_string_literal: '$"' + SINGLELINE_CONTENT* + SINGLELINE_END
  multiline_template_literal: '$|' + MULTILINE_CONTENT* + MULTILINE_END

Scanner checks valid_symbols:
  - SINGLELINE_* valid → in single-line template, end at "
  - MULTILINE_* valid → in multiline template, end at newline without |
```

Key design decisions:
1. **No state serialization** - Tree-sitter's grammar tracks context
2. **Separate tokens for single-line vs multiline** - Enables stateless detection
3. **Regular start tokens** - `$"` and `$|` matched by lexer, then scanner handles content
4. **Error recovery guard** - Scanner returns false when all tokens valid

### TODO Token Hybrid Approach

The `TODO` keyword requires special handling because `TODO freeform text` consumes to end-of-line:

- `'TODO'` is an **internal keyword** (matched by tree-sitter's lexer)
- `_todo_freeform_text` is an **external token** (matches text after TODO to EOL)
- `_todo_freeform_until_semi` is an **external token** for expression context (stops at `;`)

This mimics the Java `Lexer.java` behavior where `eatSingleLineComment()` handles freeform text.

### GLR Conflicts

The grammar uses 49 conflict declarations for genuinely ambiguous XTC constructs:
- Expression vs type ambiguities (`_expression` vs `type_name`)
- Pattern matching conflicts
- Tuple/function type parameter conflicts
- Conditional type disambiguation

## Coverage

**100% coverage** - All 692 XTC files from `lib_*` directories parse successfully.

## Native Library Build

The tree-sitter grammar must be compiled into a native shared library (`.dylib`, `.so`, or `.dll`) for use
by the JVM-based LSP server. We use **Zig** for cross-compilation, enabling builds for all platforms from
any development machine.

### Why Zig for Cross-Compilation?

Traditional C/C++ cross-compilation requires platform-specific toolchains:
- macOS → Xcode Command Line Tools
- Linux → GCC/Clang with appropriate sysroot
- Windows → MSVC or MinGW
- Cross-platform → Docker containers, CI matrix builds, or complex SDK installations

**Zig solves this** by bundling a complete C/C++ toolchain with cross-compilation support for all major platforms:
- Single ~45MB download works everywhere
- No SDK, sysroot, or platform-specific setup required
- Deterministic builds across host platforms
- Drop-in replacement for `cc`/`clang`/`gcc`

This means a developer on macOS can build Windows and Linux binaries locally, without Docker or CI.

### Native Library Outputs

| Platform | Zig Target | Output File | Architecture |
|----------|------------|-------------|--------------|
| darwin-arm64 | `aarch64-macos` | `libtree-sitter-xtc.dylib` | Mach-O arm64 |
| darwin-x64 | `x86_64-macos` | `libtree-sitter-xtc.dylib` | Mach-O x86_64 |
| linux-x64 | `x86_64-linux-gnu` | `libtree-sitter-xtc.so` | ELF x86-64 |
| linux-arm64 | `aarch64-linux-gnu` | `libtree-sitter-xtc.so` | ELF aarch64 |
| windows-x64 | `x86_64-windows-gnu` | `libtree-sitter-xtc.dll` | PE32+ x86-64 |

Each library exports the `tree_sitter_xtc` function symbol, which the JVM loads via Java's Foreign Function & Memory API.

### Building Native Libraries

Pre-built libraries are committed to source control. The build **verifies** they are up-to-date
but does **not** auto-rebuild (to avoid downloading Zig in CI).

```bash
# Verify pre-built libraries are up-to-date (FAILS if stale)
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate

# Rebuild ALL platforms (downloads Zig, cross-compiles, updates resources)
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources

# Build for a specific platform only
./gradlew :lang:tree-sitter:buildNativeLibrary_linux_x64
```

If `ensureNativeLibraryUpToDate` fails with a "STALE" error, run `copyAllNativeLibrariesToResources`
to rebuild, then commit the updated libraries.

### Gradle Tasks

| Task | Description |
|------|-------------|
| `ensureNativeLibraryUpToDate` | **Verify** pre-built library matches grammar inputs (fails if stale) |
| `copyAllNativeLibrariesToResources` | Rebuild all platforms and copy to resources (downloads Zig) |
| `buildAllNativeLibraries` | Build for all 5 platforms (downloads Zig) |
| `buildNativeLibrary_<platform>` | Cross-compile for specific platform |
| `checkNativeLibraryStaleness` | Report if pre-built libraries need updating |
| `downloadZig` | Download Zig compiler (called automatically by build tasks) |
| `extractZig` | Extract Zig from archive (pure Java) |

### How It Works

1. **Grammar Generation**: `grammar.js` and `scanner.c` are generated from `XtcLanguage.kt` and `ScannerSpec.kt`
2. **Tree-sitter Generate**: The tree-sitter CLI compiles `grammar.js` → `parser.c`
3. **Zig Compilation**: Zig compiles `parser.c` + `scanner.c` → shared library
4. **Resource Bundling**: Libraries are copied to `src/main/resources/native/<platform>/`
5. **JAR Packaging**: The lsp-server JAR includes native libraries for the current platform

### Pre-built Libraries

Pre-built libraries are committed to source control at:
```
tree-sitter/src/main/resources/native/
├── darwin-arm64/
│   ├── libtree-sitter-xtc.dylib
│   ├── libtree-sitter-xtc.inputs.sha256
│   └── libtree-sitter-xtc.version
├── darwin-x64/
├── linux-arm64/
├── linux-x64/
└── windows-x64/
```

Each platform directory includes:
- The native library file
- `.inputs.sha256` - Hash of `grammar.js` + `scanner.c` for staleness detection
- `.version` - Build metadata (git commit, timestamp, compiler used)

## LSP Integration

The tree-sitter grammar powers the LSP server's syntax features:

| Feature | Implementation |
|---------|----------------|
| Document symbols | `XtcQueryEngine.findAllDeclarations()` |
| Go-to-definition | `XtcQueryEngine.findDeclarationAt()` |
| Find references | `XtcQueryEngine.findAllIdentifiers()` |
| Syntax errors | Parse tree ERROR nodes |
| Completions | Keywords + visible declarations |

See `lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/` for implementation.

### Native Library Loading

The `TreeSitterAdapter` uses `XtcParser` to load the native library and parse XTC source code:

```
┌─────────────────────────────────────────────────────────────────┐
│                    TreeSitterAdapter                             │
│  (implements XtcCompilerAdapter for LSP)                        │
└────────────────────────────┬────────────────────────────────────┘
                             │ uses
┌────────────────────────────▼────────────────────────────────────┐
│                      XtcParser                                   │
│  - Extracts native library from JAR resources                   │
│  - Loads via Java Foreign Function & Memory API                 │
│  - Wraps jtreesitter Parser with XTC Language                   │
└────────────────────────────┬────────────────────────────────────┘
                             │ loads
┌────────────────────────────▼────────────────────────────────────┐
│              libtree-sitter-xtc.{dylib,so,dll}                  │
│  - Exports: tree_sitter_xtc() → Language pointer                │
│  - Exports: tree_sitter_xtc_external_scanner_*() functions      │
└─────────────────────────────────────────────────────────────────┘
```

**Loading sequence:**

1. `XtcParser` detects the current platform (OS + architecture)
2. Looks for `/native/<platform>/libtree-sitter-xtc.<ext>` in JAR resources
3. Extracts to a temp file (required for `System.load()`)
4. Creates a `SymbolLookup` via Java's Foreign Function API
5. Calls `Language.load(symbols, "tree_sitter_xtc")` to get the language
6. Creates a `Parser` instance configured with the XTC language

**Adapter switching:**

The LSP server supports multiple adapters, selected via Gradle property:

```bash
# Use tree-sitter adapter (syntax-level intelligence)
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=treesitter

# Use mock adapter (regex-based, no native dependencies)
./gradlew :lang:intellij-plugin:runIde -Plsp.adapter=mock
```

The adapter is configured at build time in `lsp-server/build.gradle.kts` and written to
`lsp-version.properties` in the JAR.

## Reference

The authoritative sources for XTC syntax are:

| File | Purpose |
|------|---------|
| `javatools/src/main/java/org/xvm/compiler/Lexer.java` | Token definitions |
| `javatools/src/main/java/org/xvm/compiler/Parser.java` | Grammar rules |
| `javatools/src/main/java/org/xvm/compiler/Token.java` | Keyword/operator enums |

See [PLAN_TREE_SITTER.md](../doc/plans/PLAN_TREE_SITTER.md) for implementation status and next steps.
See [implementation.md](implementation.md) for detailed implementation history.
