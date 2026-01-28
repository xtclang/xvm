# PLAN: Tree-sitter Integration for XTC Language Support

**Goal**: Use the existing `TreeSitterGenerator` to build a functional LSP with syntax-level
intelligence, without requiring compiler modifications.

**Timeline**: 2-3 weeks for core functionality
**Risk**: Low (additive, no compiler changes)
**Prerequisites**: Working `TreeSitterGenerator` in `lang/dsl/` (already exists)
**Complements**: [Adapter Layer Design](../3-lsp-solution/adapter-layer-design.md) (can add later for semantic features)

---

## Quick Reference: Testing the Grammar

```bash
# Generate, validate, and test grammar in one command
# (dependencies are wired: testTreeSitterParse → validateTreeSitterGrammar
#  → copyGrammarFiles → generateTreeSitter + generateScannerC)
./gradlew :lang:tree-sitter:testTreeSitterParse

# Test a specific file manually
cd lang/tree-sitter/build/generated
../tree-sitter-cli/tree-sitter parse /path/to/file.x

# Run individual stages if needed
./gradlew :lang:dsl:generateTreeSitter      # Generate grammar.js
./gradlew :lang:dsl:generateScannerC        # Generate scanner.c
./gradlew :lang:tree-sitter:validateTreeSitterGrammar  # Compile grammar

# Run full build (includes all generators)
./gradlew build
```

---

## Implementation Status

> **Last Updated**: 2026-01-28

### Completed ✅
- [x] LSP server converted from Java to Kotlin (better DSL support, null safety, coroutines)
- [x] `jtreesitter` dependency added to `lang/lsp-server/build.gradle.kts`
- [x] Tree-sitter parser wrappers implemented (`XtcParser`, `XtcTree`, `XtcNode`)
- [x] Query patterns and engine implemented (`XtcQueries`, `XtcQueryEngine`)
- [x] `TreeSitterAdapter` integrated with existing adapter interface
- [x] Gradle tasks for grammar validation (platform-independent, no brew/npm required)
- [x] Test corpus uses 675+ real XTC files from `lib_*` directories
- [x] Grammar conflicts resolved (lambda, annotation, dangling else, list/map literals)
- [x] Grammar validation passes (`./gradlew :lang:dsl:validateTreeSitterGrammar`)
- [x] **Template string external scanner** - C scanner generated from Kotlin spec (`ScannerSpec.kt`)
- [x] **String interpolation support** - `$"text {expr}"` parses correctly with embedded expressions

### In Progress 🔄
- **Grammar coverage: 593/691 XTC files parse successfully (85.8%)**
- Native library compilation for target platforms

### Grammar Support Status (2026-01-27)

The following features have been added to `TreeSitterGenerator.kt`:

#### Recently Completed ✅

| Feature | Example | Status |
|---------|---------|--------|
| Typedef declarations | `typedef String\|Int as Value` | ✅ With doc comments, annotations |
| Union/intersection types | `Type1 \| Type2`, `Type1 + Type2`, `Type1 - Type2` | ✅ All three operators |
| Range literals | `..<`, `>..`, `>..<`, `..` | ✅ All 4 variants |
| Immutable modifier | `immutable Map`, standalone `immutable` | ✅ In types |
| Conditional keyword | `conditional Int method()` | ✅ In return types |
| Using blocks | `using (val x = expr) { }` | ✅ With resources |
| Enum constructor params | `enum Ordered(String symbol)` | ✅ |
| Enum value arguments | `Lesser("<", Greater)` | ✅ With optional bodies |
| Enum default clause | `enum Foo default(args)` | ✅ |
| Conditional assignment | `val x := expr`, `if (Type x := expr)` | ✅ `:=` operator |
| Typed literals | `Map:[]`, `List:[1,2,3]` | ✅ |
| Switch expressions | `return switch (x) { case A: val; }` | ✅ With expression cases |
| Tuple patterns | `case (Lesser, _):` | ✅ With wildcard `_` |
| Instance new | `expr.new(args)` | ✅ Virtual constructors |
| Tuple expressions | `(True, value)` | ✅ Multi-value returns |
| Method type params | `<T> T method()` | ✅ Before return type |
| Throw expression | `s -> throw new Ex()` | ✅ In lambdas |
| Property accessors | `prop.get()`, `prop.calc()` | ✅ Any accessor name |
| Constructor finally | `construct() {} finally {}` | ✅ |
| Type arg constraints | `<Key, Value extends X>` | ✅ In arguments |
| Types in arguments | `.is(immutable)`, `.as(Type)` | ✅ |
| Method expr body | `Int foo() = 42;` | ✅ |
| Assert with conditions | `assert val x := expr, y` | ✅ Multiple conditions |
| Multiple implements | `class Foo implements A implements B` | ✅ All type decls |
| Labeled statements | `Loop: for (...)` | ✅ For break/continue |
| Constructor delegation | `construct OtherCtor(args);` | ✅ Chaining constructors |
| Local var annotations | `@Volatile Type x = expr;` | ✅ With or without val/var |
| Dual visibility | `public/private Type prop` | ✅ Combined modifiers |
| Tuple destructure for | `for ((K k, V v) : map)` | ✅ In for-each loops |
| Tuple assignment | `(Int x, this.y) = expr;` | ✅ Destructuring assignment |
| Array instantiation | `new Type[size]` | ✅ With size expression |
| Reference operator | `&hasher`, `that.&hasher` | ✅ No-dereference access |
| Non-null type modifier | `Type!` | ✅ No auto-narrowing |
| Array with initializer | `new Type[size](i -> expr)` | ✅ With lambda initializer |
| Conditional incorporates | `incorporates conditional Mixin<...>` | ✅ Conditional mixins |
| Function type params | `function Type(ArgTypes)` | ✅ Just types, no names |
| Conditional function type | `function conditional Type(Args)` | ✅ With conditional keyword |
| Annotations after static | `static @Abstract class` | ✅ Flexible modifier order |
| `this:` variants | `this:struct`, `this:private`, `this:class`, etc. | ✅ All 8 variants |
| `super` expression | `super.method()` | ✅ As expression |
| Tuple conditional decl | `(Type1 x, Type2 y) := expr` | ✅ Tuple destructuring |
| Multi-condition if/while | `if (a, b := expr, c)` | ✅ Comma-separated conditions |
| Not-null assignment | `Type x ?= expr` | ✅ In conditional contexts |
| Named tuple types | `(Boolean found, Int index)` | ✅ For return types |
| Doc comments in enum values | `/** doc */ Successful(True)` | ✅ With trailing commas |
| Trailing comma in enum values | `Val1, Val2,` | ✅ Allowed |
| Static interface | `static interface Foo` | ✅ Nested type modifier |
| Static mixin | `static mixin Foo` | ✅ With constructor params |
| Mixin constructor params | `mixin Foo(Type arg)` | ✅ |
| Context keywords as identifiers | `for (val var : items)` | ✅ var/val as variable names |
| Trailing comma in parameters | `(Type a, Type b,)` | ✅ |
| Default clause for const | `const Foo default(x)` | ✅ Like enum default |
| Property body | `Type prop { get() {...} }` | ✅ With accessor methods |
| Assert expression | `value ?: assert` | ✅ For safe-call-else |
| Anonymous inner class | `new Type() { ... }` | ✅ With class body |
| Local variable visibility | `private Type x = val` | ✅ For captures |
| Multiline template literals | `$\|content\n \|more` | ✅ With continuation lines |
| Multiline plain literals | `\\|content\n \|more` | ✅ With continuation lines |
| Named arguments | `method(name=value)` | ✅ In function calls |
| Local function declarations | `private static Type fn() {}` | ✅ Inside method bodies |
| Switch variable declaration | `switch (Type x = expr)` | ✅ Inline variable in switch |
| Multi-value case patterns | `case 'A', 'B':` | ✅ Comma-separated values |
| Else expression | `expr?.method() : fallback` | ✅ Short-circuit else clause |
| Short-circuit postfix | `expr?` | ✅ Null short-circuit operator |
| TODO expression | `TODO`, `TODO(msg)`, `TODO text` | ✅ Placeholder for unimplemented code |
| Type patterns in case | `case List<String>:` | ✅ Type matching in switch |
| Package doc comments | `/** doc */ package foo {}` | ✅ Doc comments on packages |
| Local fn doc comments | `/** doc */ private Type fn() {}` | ✅ Doc comments on local functions |
| TODO statement | `TODO` (no semicolon) | ✅ Bare TODO as statement |
| Statement expression | `Type x = { return val; };` | ✅ Block as expression initializer |
| Annotations after visibility | `protected @Abstract Type m()` | ✅ Flexible modifier order |
| For loop initializer | `for (Int i = 0; ...)` | ✅ Variable declaration in init |
| Interface without body | `interface Foo extends Bar;` | ✅ Semicolon termination |
| Typed duration literal | `Duration:0S` | ✅ Duration values in typed literals |
| Reference this expression | `&this:service` | ✅ Reference with this variants |
| Angle bracket type lists | `Method<Target, <Params>, <Return>>` | ✅ In type arguments |
| Empty type arguments | `Class<>` | ✅ Wildcard/inferred types |
| Multiple for initializers | `for (Int i = 0, Int c = n; ...)` | ✅ Comma-separated declarations |
| Type decl semicolon | `class Foo extends Bar;` | ✅ Class/mixin/service without body |
| Local typedef | `typedef Type as Alias;` in method | ✅ Type aliases in method bodies |
| Using explicit type | `using (Type x = expr) {}` | ✅ No val/var required |
| Assert without cond | `assert as $"error";` | ✅ Message-only assert |
| Assert expr with msg | `value ?: assert as $"error"` | ✅ Assert expression with message |
| Method TODO body | `Int foo() = TODO text` | ✅ No semicolon after TODO text |
| Try resource decl | `try (Type x = expr) {}` | ✅ Variable declaration in try |
| Generic type patterns | `case List<String>:` | ✅ Type patterns with generics in switch |
| Empty tuple expression | `args = ()` | ✅ Empty tuple as default value |
| Annotation declarations | `annotation Foo into Bar` | ✅ XTC annotation types |
| Annotated new expressions | `new @Mixin Type(args)` | ✅ Annotations on new |
| Named function type params | `function Bool fn(Arg)` | ✅ Function params with names |
| Wildcard expressions | `accumulate(_, sum, _)` | ✅ Partial function application |
| Async call expression | `write^(buffer)` | ✅ `^(` as async invocation token |
| Enum value type arguments | `Colon<Object>(":")` | ✅ Type args in enum values |
| Tuple union type elements | `(Id, IntLiteral\|FPLiteral)` | ✅ Union types in tuples |
| Fall-through switch cases | `case 'A': case 'B': val;` | ✅ Multiple case labels |
| Function variable declaration | `function Int sum(Int, Int) = lambda` | ✅ Named function type as var |
| Safe call expression | `report?($"error")` | ✅ Null-safe invocation with `?(` |
| Type expression arguments | `Iff("debug".defined)` | ✅ Conditional types |
| File/resource literals | `#./CharCats.dat` | ✅ Embedded resources |
| Unicode escapes | `'\u0000'`, `"\U00010000"` | ✅ In char/string literals |
| Named fn type properties | `function Type propName(Args);` | ✅ Function-typed properties |
| Tuple elem val/var | `(val x, Int y) = expr;` | ✅ In tuple assignments |
| Do-while multi-cond | `do {} while (c1, c2 := expr);` | ✅ Multiple conditions |
| For tuple init | `for ((T1 x, T2 y) = e; ...)` | ✅ Tuple assignment in for |
| For multi-update | `for (...; ...; i++, j++)` | ✅ Multiple update exprs |
| Conditional tuple wildcard | `(_, val x) := expr` | ✅ Wildcards and val/var |
| For-each bare identifier | `for (ch : host)` | ✅ Type inferred |

#### Recently Completed (String Interpolation) ✅

| Feature | Example | Notes |
|---------|---------|-------|
| String interpolation | `$"text {expr}"` | External scanner generates from `ScannerSpec.kt` |

---

## String Interpolation Strategy

### Implementation ✅ COMPLETE

String interpolation and multiline templates are now fully supported:
- `$"Hello {name}!"` - Single-line template strings
- `$|Line 1\n |Line 2` - Multiline template strings with `|` continuation

### Stateless Scanner Design

The scanner is **STATELESS** - it uses tree-sitter's `valid_symbols` to determine context.
Tree-sitter's grammar uses **different external tokens** for single-line vs multiline templates:

```
Grammar structure:
  template_string_literal: '$"' + SINGLELINE_CONTENT* + SINGLELINE_END
  multiline_template_literal: '$|' + MULTILINE_CONTENT* + MULTILINE_END

Scanner checks valid_symbols:
  - SINGLELINE_* valid → in single-line template, end at "
  - MULTILINE_* valid → in multiline template, end at newline without |
```

### External Token Types

| Token Type | Context | Purpose |
|------------|---------|---------|
| `SINGLELINE_CONTENT` | `$"..."` | String content in single-line template |
| `SINGLELINE_EXPR_START` | `$"..."` | `{` in single-line template |
| `SINGLELINE_END` | `$"..."` | `"` end delimiter |
| `MULTILINE_CONTENT` | `$\|...\|` | String content in multiline template |
| `MULTILINE_EXPR_START` | `$\|...\|` | `{` in multiline template |
| `MULTILINE_END` | `$\|...\|` | End when no `\|` continuation |
| `TEMPLATE_EXPR_END` | Both | `}` end of expression |

### Scanner Files

```
lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/
├── ScannerSpec.kt          # Token definitions and rules
└── ScannerCGenerator.kt    # Generates scanner.c from spec

lang/dsl/build/generated/src/
└── scanner.c               # Generated C code for tree-sitter
```

### Key Design Decisions

1. **Stateless**: No state to serialize/deserialize. Tree-sitter's grammar tracks context.

2. **Separate tokens**: Different tokens for single-line vs multiline allows stateless detection.

3. **Regular start tokens**: `$"` and `$|` are regular grammar tokens (not external).
   Tree-sitter's lexer matches them, then calls the external scanner for content.

4. **Error recovery guard**: Scanner returns false when all tokens are valid (error recovery mode).

---

#### Lower Priority

| Feature | Example | Notes |
|---------|---------|-------|
| `@:` syntax | `@:annotate` | Annotation with colon |
| Dir/path literals | `./`, `../` | Special literals |
| Binary file literal | `#file.bin` | Resource embedding |

> **Ground Truth**: The authoritative sources for XTC syntax are:
> - `javatools/src/main/java/org/xvm/compiler/Lexer.java` - Token definitions
> - `javatools/src/main/java/org/xvm/compiler/Parser.java` - Grammar rules
> - `javatools/src/main/java/org/xvm/compiler/Token.java` - Token enum with all keywords/operators

### Blocked ⏸️
- Native library compilation (requires C compiler toolchain)
- Full TreeSitterAdapter testing (requires native library)

---

## Adapter Architecture

The LSP server uses a pluggable adapter pattern to support different parsing backends:

```
┌─────────────────────────────────────────────────────────────┐
│                     XtcLanguageServer                       │
│            (takes XtcCompilerAdapter via constructor)       │
└────────────────────────────┬────────────────────────────────┘
                             │
               ┌─────────────┴─────────────┐
               │    XtcCompilerAdapter     │  ← Interface
               └─────────────┬─────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ MockXtc-      │    │ TreeSitter-   │    │ (Future)      │
│ Compiler-     │    │ Adapter       │    │ Compiler-     │
│ Adapter       │    │               │    │ Adapter       │
│               │    │               │    │               │
│ - Regex-based │    │ - Tree-sitter │    │ - Full XTC    │
│ - For testing │    │ - Syntax only │    │   compiler    │
└───────────────┘    └───────────────┘    └───────────────┘
```

### Current Adapter Implementations

| Adapter | File | Description | Use Case |
|---------|------|-------------|----------|
| `MockXtcCompilerAdapter` | `adapter/MockXtcCompilerAdapter.kt` | Regex-based parsing | Testing, fallback |
| `TreeSitterAdapter` | `adapter/TreeSitterAdapter.kt` | Tree-sitter parsing | Syntax intelligence |
| `XtcCompilerAdapterFull` | `adapter/XtcCompilerAdapterFull.kt` | Extended interface | Future compiler integration |

### Switching Adapters

The adapter is selected at **build time** via a Gradle property. The launcher reads this from
the embedded `lsp-version.properties` file.

**Build with Mock adapter (default):**
```bash
./gradlew :lang:lsp-server:build
```

**Build with Tree-sitter adapter:**
```bash
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter
```

**Set default in gradle.properties:**
```properties
# In lang/lsp-server/gradle.properties (create if needed)
lsp.adapter=mock
# or
lsp.adapter=treesitter
```

**How it works:**
1. Build property `lsp.adapter` is written to `lsp-version.properties` in the JAR
2. Launcher reads property at startup and creates the appropriate adapter
3. Server logs the active backend prominently:
   ```
   ========================================
   XTC Language Server v1.0.0
   Backend: Tree-sitter (syntax-aware)
   Built: 2026-01-21T15:30:00Z
   ========================================
   ```

**To enable Tree-sitter mode:**
1. Build the native library: `./gradlew :lang:dsl:buildTreeSitterLibrary`
2. Copy to resources: `lang/lsp-server/src/main/resources/native/<platform>/`
3. Build with tree-sitter: `./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter`

---

## Testing the Tree-sitter Functionality

### 1. Grammar Validation (No Native Library Needed)

```bash
# Generate grammar from XtcLanguage.kt
./gradlew :lang:dsl:generateTreeSitter

# Validate grammar compiles (downloads tree-sitter CLI automatically)
./gradlew :lang:dsl:validateTreeSitterGrammar

# Test parsing against 675+ XTC files from lib_* directories
./gradlew :lang:dsl:testTreeSitterParse
```

### 2. Direct Tree-sitter CLI Testing

```bash
# Parse a specific file
cd lang/dsl/build/generated
../tree-sitter-cli/tree-sitter parse /path/to/file.x

# Show parse tree with S-expressions
../tree-sitter-cli/tree-sitter parse /path/to/file.x

# Highlight with the generated highlights.scm
../tree-sitter-cli/tree-sitter highlight /path/to/file.x
```

### 3. Unit Tests for Tree-sitter Wrappers

```bash
./gradlew :lang:lsp-server:test
```

### 4. Testing in IntelliJ with LSP4IJ

The IntelliJ plugin uses the LSP server via LSP4IJ. To test tree-sitter functionality:

**Step 1: Build the plugin with tree-sitter backend**
```bash
# Build LSP server with tree-sitter
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter

# Build the IntelliJ plugin (it bundles the fat JAR)
./gradlew :lang:intellij-plugin:buildPlugin
```

**Step 2: Install and run**
1. Open IntelliJ IDEA
2. Go to Settings → Plugins → Install from disk
3. Select `lang/intellij-plugin/build/distributions/xtc-intellij-plugin-*.zip`
4. Restart IntelliJ

**Step 3: Verify the backend**
1. Open View → Tool Windows → Language Servers (LSP4IJ)
2. Open any `.x` file to trigger LSP server start
3. Check the server logs - you should see:
   ```
   ========================================
   XTC Language Server v1.0.0
   Backend: Tree-sitter (syntax-aware)
   ========================================
   ```
4. If you see `Backend: Mock (regex-based)`, the native library is missing

**Step 4: Test features**
With tree-sitter active, verify these work:
- **Document Outline**: View → Tool Windows → Structure (shows symbols)
- **Hover**: Mouse over identifiers (shows type/declaration info)
- **Completions**: Type in a method body (Ctrl+Space for keywords/locals)
- **Go-to-Definition**: Ctrl+Click on identifier (same-file navigation)
- **Find References**: Right-click → Find Usages (same-file)
- **Syntax Errors**: Introduce a syntax error (red squiggly line)

### 5. Comparing Mock vs Tree-sitter

Build and install both versions to compare:

```bash
# Build with mock (default)
./gradlew :lang:lsp-server:build
cp lang/lsp-server/build/libs/*-all.jar /tmp/xtc-lsp-mock.jar

# Build with tree-sitter
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter
cp lang/lsp-server/build/libs/*-all.jar /tmp/xtc-lsp-treesitter.jar
```

| Feature | Mock | Tree-sitter |
|---------|------|-------------|
| Symbol detection | Regex (basic) | AST-based (accurate) |
| Nested symbols | ❌ Limited | ✅ Full hierarchy |
| Syntax errors | ❌ Basic patterns | ✅ Precise location |
| Error recovery | ❌ None | ✅ Continues parsing |
| Performance | Fast | Fast (incremental)

---

## Executive Summary

We already have a `TreeSitterGenerator` that produces a complete Tree-sitter grammar from
`XtcLanguage.kt`. This plan turns that grammar into a working LSP server that provides:

- Document symbols (outline)
- Go-to-definition (same file, cross-file by name)
- Find references
- Completion (keywords, locals, visible names)
- Syntax error reporting
- Code folding
- Hover (show declaration)

This gives us **~70% of LSP functionality** without touching the compiler. The remaining 30%
(type inference, semantic errors) can be added later via the compiler adapter.

### Why Tree-sitter First?

| Approach | Time to Working LSP | Semantic Features | Risk |
|----------|--------------------|--------------------|------|
| Tree-sitter only | 2-3 weeks | Syntax-level | Low |
| Compiler adapter | 6-8 weeks | Full | Medium |
| Both (hybrid) | 3-4 weeks initial | Syntax now, semantic later | Low |

Tree-sitter provides immediate value while we plan the deeper compiler integration.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         LSP Server                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │
│  │  Hover   │ │ Complete │ │ GoToDef  │ │ DocumentSymbols  │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────────┬─────────┘   │
│       └────────────┴────────────┴────────────────┘             │
│                              │                                  │
│                    ┌─────────▼─────────┐                        │
│                    │   XtcQueryEngine  │                        │
│                    │     (Kotlin)      │                        │
│                    └─────────┬─────────┘                        │
│                              │                                  │
├──────────────────────────────┼──────────────────────────────────┤
│                    ┌─────────▼─────────┐                        │
│                    │  Tree-sitter JNI  │                        │
│                    │  (jtreesitter)    │                        │
│                    └─────────┬─────────┘                        │
│                              │                                  │
├──────────────────────────────┼──────────────────────────────────┤
│                    ┌─────────▼─────────┐                        │
│                    │  tree-sitter-xtc  │                        │
│                    │ (compiled grammar)│                        │
│                    └───────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Validate the Generated Grammar ✅ COMPLETE

### 1.1 Existing Infrastructure

The grammar generation already exists:

- **Gradle task**: `./gradlew :lang:dsl:generateTreeSitter`
- **Output**: `lang/dsl/build/generated/grammar.js` and `highlights.scm`
- **Source**: `TreeSitterGenerator.kt` and `LanguageModelCli.kt`

### 1.2 Gradle Tasks for Grammar Testing ✅

Tasks implemented in `lang/dsl/build.gradle.kts`:

- `downloadTreeSitterCliGz` - Downloads platform-specific CLI binary
- `extractTreeSitterCli` - Extracts using Java's GZIPInputStream
- `validateTreeSitterGrammar` - Validates grammar compiles
- `testTreeSitterParse` - Tests parsing 675+ XTC files

### 1.3 Grammar Conflicts Resolved ✅

The following conflicts have been fixed in `TreeSitterGenerator.kt`:

1. **Lambda expression**: `identifier ->` - fixed with `prec(18, ...)`
2. **Annotation arguments**: `@foo(...)` - fixed with `prec.left(...)`
3. **Dangling else**: `if (a) if (b) x else y` - fixed with `prec.right(...)`
4. **Expression conflicts**: Binary/unary/call - fixed with explicit `conflicts` array
5. **List vs map literal**: `[]` - fixed with conflict declaration

**Deliverables:**
- [x] `downloadTreeSitterCli` task downloads CLI binary
- [x] `validateTreeSitterGrammar` task confirms grammar compiles
- [x] `testTreeSitterParse` task parses sample `.x` files
- [x] All tasks work on macOS (x64/arm64) and Linux (x64/arm64)

---

## Phase 2: JVM Integration (In Progress)

### 2.1 How Tree-sitter Works with JVM

Tree-sitter is a C library. To use it from Kotlin/Java, you need:

1. **A compiled grammar** - your `grammar.js` compiled to a shared library (`.so`/`.dylib`)
2. **JVM bindings** - a Java library that calls the C code via JNI

### 2.2 One-Time Grammar Compilation

```bash
# Validate and generate parser source
./gradlew :lang:dsl:validateTreeSitterGrammar

# Build shared library
./gradlew :lang:dsl:buildTreeSitterLibrary

# Result: libtree-sitter-xtc.so (Linux) or .dylib (macOS)
# Commit to lang/lsp-server/src/main/resources/native/
```

### 2.3 JVM Binding Library ✅

Using official tree-sitter bindings:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.tree-sitter:jtreesitter:0.25.3")
}
```

### 2.4 XtcParser Wrapper ✅

Implemented in `org.xvm.lsp.treesitter` package:

- `XtcParser.kt` - Parser wrapper with incremental parsing support
- `XtcTree.kt` - Parsed tree wrapper
- `XtcNode.kt` - Node wrapper with navigation
- `XtcQueries.kt` - S-expression query patterns
- `XtcQueryEngine.kt` - Query execution engine

**Deliverables:**
- [x] jtreesitter dependency added
- [x] `XtcParser` wrapper class
- [ ] Native libraries compiled for target platforms
- [ ] Test parsing a real `.x` file end-to-end

---

## Phase 3: Query Engine ✅ COMPLETE

### 3.1 Query Patterns ✅

Defined in `XtcQueries.kt`:

```kotlin
object XtcQueries {
    val TYPE_DECLARATIONS = """
        (class_declaration name: (type_name) @name) @declaration
        (interface_declaration name: (type_name) @name) @declaration
        ...
    """.trimIndent()

    val METHOD_DECLARATIONS = "..."
    val PROPERTY_DECLARATIONS = "..."
    val IDENTIFIERS = "..."
    val VARIABLE_DECLARATIONS = "..."
    val IMPORTS = "..."
}
```

### 3.2 Query Engine ✅

Implemented in `XtcQueryEngine.kt` with methods:
- `findAllDeclarations(tree, uri)`
- `findTypeDeclarations(tree, uri)`
- `findMethodDeclarations(tree, uri)`
- `findPropertyDeclarations(tree, uri)`
- `findAllIdentifiers(tree, name, uri)`
- `findImports(tree)`
- `findDeclarationAt(tree, line, column, uri)`

**Deliverables:**
- [x] Query patterns for all major XTC constructs
- [x] `XtcQueryEngine` with find methods
- [ ] Tests for query accuracy (requires grammar coverage improvements)

---

## Phase 4: LSP Features ✅ IMPLEMENTED (Pending Testing)

### TreeSitterAdapter ✅

The `TreeSitterAdapter` class implements all basic LSP features:

```kotlin
class TreeSitterAdapter : XtcCompilerAdapter, Closeable {
    override fun compile(uri: String, content: String): CompilationResult
    override fun findSymbolAt(uri: String, line: Int, column: Int): SymbolInfo?
    override fun getHoverInfo(uri: String, line: Int, column: Int): String?
    override fun getCompletions(uri: String, line: Int, column: Int): List<CompletionItem>
    override fun findDefinition(uri: String, line: Int, column: Int): Location?
    override fun findReferences(uri: String, line: Int, column: Int, includeDeclaration: Boolean): List<Location>
}
```

**Deliverables:**
- [x] `compile()` with syntax error detection
- [x] `findSymbolAt()` for symbol lookup
- [x] `getHoverInfo()` for hover
- [x] `getCompletions()` with keywords, types, and locals
- [x] `findDefinition()` for same-file navigation
- [x] `findReferences()` for same-file references
- [ ] Integration tests with real `.x` files

---

## Phase 5: Cross-File Support (Pending)

### 5.1 Workspace Index

Need to implement `WorkspaceIndex` for cross-file symbol tracking.

**Deliverables:**
- [ ] `WorkspaceIndex` with file tracking
- [ ] Cross-file go-to-definition
- [ ] Workspace symbol search
- [ ] Incremental re-indexing on file change

---

## Grammar Coverage Progress

The grammar validates and now supports many XTC language features. Coverage improved from 9% to 73.1% (506/692 files).

### Remaining Parse Failures (98 files)

Current status: **593/691 files (85.8%)** parse successfully.

**Failing files by library:**

| Library | Files | Example Errors |
|---------|-------|----------------|
| lib_xenia | 7 | SessionImpl.x, ChainBundle.x, CookieBroker.x |
| lib_jsondb | 10 | Catalog.x, TxManager.x, Client.x |
| lib_json | 10 | Schema.x, ObjectOutputStream.x, mappings.x |
| lib_web | 15 | http.x, MediaType.x, Protocol.x |
| lib_xunit_engine | 7 | discovery.x, models.x, utils.x |
| lib_ecstasy | 10 | TypeTemplate.x, StringBuffer.x, Char.x |
| lib_xml | 7 | Parser.x, Attribute.x, Document.x |
| lib_sec | 5 | Group.x, Principal.x, Entitlement.x |
| lib_oodb | 4 | oodb.x, DBProcessor.x, model/User.x |
| lib_convert | 3 | Base64Format.x, CodecFormat.x, Registry.x |
| lib_crypto | 2 | NamedPassword.x, CertificateManager.x |
| Others | 18 | Various files |

**Common error patterns:**

| Error Type | Count | Example |
|------------|-------|---------|
| `MISSING "module"` | ~15 | Type params with `module` keyword |
| `MISSING identifier` | ~10 | Complex type expressions |
| `MISSING ";"` | ~3 | Statement termination edge cases |
| `ERROR` (various) | ~70 | Other grammar constructs |

### Improvement Path

1. ✅ Added missing grammar rules to `TreeSitterGenerator.kt`
2. ✅ Regenerate and validate grammar
3. ✅ Coverage improved from 21% (144/675) to 60.5% (419/692)
4. ✅ Implemented else expression pattern `expr?.method() : fallback`
5. ✅ Implemented short-circuit postfix `expr?`, TODO expression, type case patterns
6. ✅ Target exceeded: 60%+ of XTC files parsing successfully
7. ✅ Implemented doc comments for packages, local functions, TODO statement, statement expressions
8. ✅ Coverage improved from 60.5% to 66.0% (457/692)
9. ✅ Implemented flexible modifier order, for initializer, interface semicolon, typed literals, reference expressions
10. ✅ Coverage improved from 66.0% to 67.8% (469/692)
11. ✅ Implemented angle bracket type lists, empty type args, multiple for initializers, type decl semicolons
12. ✅ Coverage improved from 67.8% to 70.7% (489/692)
13. ✅ Implemented local typedef, using explicit type, assert without conditions, assert expr with message
14. ✅ Implemented method TODO body without semicolon, try resource declarations
15. ✅ Coverage improved from 70.7% to 73.1% (506/692)
16. ✅ Implemented generic type patterns in switch `case List<String>:`, empty tuple expression `()`
17. ✅ Coverage improved from 73.1% to 73.6% (509/692)
18. ✅ Implemented annotation declarations (`annotation Foo into Bar implements Baz`)
19. ✅ Coverage improved from 73.6% to 79.2% (548/692)
20. ✅ Implemented annotated new expressions `new @Mixin Type(args)`, named function type params
21. ✅ Coverage improved from 79.2% to 80.2% (555/692)
22. ✅ Implemented wildcard expressions (`_` for partial application), async call `^(`, enum type args
23. ✅ Implemented tuple union types, fall-through switch cases, function variable declarations
24. ✅ Fixed ternary vs postfix `?` conflict
25. ✅ Coverage improved from 80.2% to 82.1% (568/692)
26. ✅ Implemented safe call `?(`, types with expression args `Iff(expr)`, file literals `#./file.dat`
27. ✅ Fixed Unicode escapes in char/string literals (`\uXXXX`, `\UXXXXXXXX`)
28. ✅ Coverage improved from 82.1% to 84.5% (585/692)
29. ✅ Implemented named function type properties, tuple elements with val/var
30. ✅ Implemented do-while multi-conditions, for tuple initializers, for multi-update expressions
31. ✅ Implemented conditional tuple wildcards/val/var, for-each bare identifier
32. ✅ Coverage improved from 84.5% to 86.0% (595/692)
33. ✅ String interpolation `$"text {expr}"` - external scanner with stateless design
34. ✅ Multiline templates `$|line\n |continuation` - scanner detects end via valid_symbols
35. ✅ Coverage: 593/691 files (85.8%) parse successfully
36. 🔄 Next: Investigate remaining 98 failures (module keyword, identifier issues)

---

## What This Plan Does NOT Cover

These features require compiler integration (Phase 2 - Adapter Layer):

| Feature | Why Compiler Needed |
|---------|---------------------|
| Type inference | `val x = foo()` - need to resolve `foo()` return type |
| Semantic errors | Type mismatches, missing methods, etc. |
| Smart completion | Members of a type require type resolution |
| Rename refactoring | Need to know which references are semantic matches |
| Import organization | Need to resolve qualified names |

The Tree-sitter approach gets you working LSP features quickly. Add the compiler adapter
later to enhance these features with semantic information.

---

## Dependencies

### External

| Dependency | Version | Purpose |
|------------|---------|---------|
| tree-sitter CLI | 0.22.6 | Grammar validation (auto-downloaded by Gradle task) |
| jtreesitter | 0.25.3 | JVM bindings (Maven Central: `io.github.tree-sitter:jtreesitter`) |
| lsp4j | 0.21.0+ | LSP protocol implementation (already in lsp-server) |

**Build requirements:**
- Tree-sitter CLI is **auto-downloaded** by `downloadTreeSitterCli` task - no manual install
- No Rust/Cargo/npm/brew required (platform-independent gzip extraction)
- Compiled grammar binary (`.so`/`.dylib`) is committed to repo for runtime

**Language:** All code is **Kotlin** - the LSP server was converted from Java to Kotlin for better
DSL support, null safety, and coroutines. Bytecode targets JDK 21 for IntelliJ 2025.1 compatibility.

### Internal (Already Exists)

| Component | Location | Notes |
|-----------|----------|-------|
| `TreeSitterGenerator` | `lang/dsl/.../generators/` | Generates `grammar.js` and `highlights.scm` |
| `XtcLanguage.kt` | `lang/dsl/.../XtcLanguage.kt` | Complete language model (60+ AST concepts) |
| `generateTreeSitter` task | `lang/dsl/build.gradle.kts` | Gradle task to run generator |
| `LanguageModelCli` | `lang/dsl/.../LanguageModelCli.kt` | CLI interface for generators |
| `lang/lsp-server/` | `lang/lsp-server/` | LSP server module (integrate tree-sitter here) |

---

## Success Criteria

### Phase 1-2 Complete
- [x] `tree-sitter generate` succeeds (grammar validates)
- [ ] Native library compiled for all platforms
- [ ] Kotlin test can parse `.x` file and traverse AST

### Phase 3-4 Complete
- [x] Query engine implemented
- [x] TreeSitterAdapter implements all basic LSP methods
- [ ] Document symbols shows class/method outline (requires working native lib)
- [ ] Go-to-definition works for local variables
- [ ] Find references works within same file
- [ ] Completion shows keywords and locals

### Phase 5 Complete
- [ ] Go-to-definition works across files
- [ ] Workspace symbol search works
- [ ] Performance acceptable (<100ms for typical operations)

---

## File Structure (Actual - Kotlin)

```
lang/
├── dsl/                              # Grammar generation
│   ├── build.gradle.kts              #   Has tree-sitter Gradle tasks
│   ├── build/
│   │   ├── generated/                #   Output: grammar.js, highlights.scm
│   │   └── tree-sitter-cli/          #   Downloaded CLI binary
│   └── src/main/kotlin/
│       └── org/xtclang/tooling/
│           ├── XtcLanguage.kt        #   Language model definition
│           └── generators/
│               └── TreeSitterGenerator.kt
│
└── lsp-server/                       # LSP server (all Kotlin)
    ├── build.gradle.kts              #   Has jtreesitter dependency
    └── src/
        ├── main/kotlin/org/xvm/lsp/
        │   ├── adapter/
        │   │   ├── XtcCompilerAdapter.kt      # Interface
        │   │   ├── MockXtcCompilerAdapter.kt  # Regex-based (testing)
        │   │   ├── TreeSitterAdapter.kt       # Tree-sitter based
        │   │   └── XtcCompilerAdapterFull.kt  # Extended interface
        │   ├── model/
        │   │   ├── CompilationResult.kt
        │   │   ├── Diagnostic.kt
        │   │   ├── Location.kt
        │   │   └── SymbolInfo.kt
        │   ├── server/
        │   │   ├── XtcLanguageServer.kt
        │   │   └── XtcLanguageServerLauncher.kt
        │   └── treesitter/
        │       ├── XtcParser.kt          # Parser wrapper
        │       ├── XtcTree.kt            # Tree wrapper
        │       ├── XtcNode.kt            # Node wrapper
        │       ├── XtcQueries.kt         # Query patterns
        │       └── XtcQueryEngine.kt     # Query execution
        ├── main/resources/native/        # Native libs (to be added)
        │   ├── linux-x86_64/libtree-sitter-xtc.so
        │   ├── darwin-x86_64/libtree-sitter-xtc.dylib
        │   └── darwin-aarch64/libtree-sitter-xtc.dylib
        └── test/kotlin/org/xvm/lsp/
            ├── adapter/MockXtcCompilerAdapterTest.kt
            ├── model/CompilationResultTest.kt
            ├── model/DiagnosticTest.kt
            ├── server/XtcLanguageServerTest.kt
            └── BytecodeVersionTest.kt
```

---

## Next Steps

1. **Improve Grammar Coverage** - Add missing XTC features to `TreeSitterGenerator.kt`
2. **Build Native Library** - Compile grammar for all platforms
3. **Enable TreeSitterAdapter** - Switch launcher to use tree-sitter
4. **IDE Integration** - VS Code extension, IntelliJ plugin per [PLAN_IDE_INTEGRATION.md](./PLAN_IDE_INTEGRATION.md)
5. **Compiler Adapter** - Add semantic features (see [xtc-language-support-research](https://github.com/xtclang/xtc-language-support-research))

---

## Reference: Source Files for Grammar

> **Important**: The Lexer and Parser Java files are the **single source of truth** for XTC syntax.
> Any grammar documentation may be outdated.

### Authoritative Sources (Ground Truth)

| File | Purpose | Key Methods/Content |
|------|---------|---------------------|
| `javatools/src/main/java/org/xvm/compiler/Lexer.java` | Tokenization | `eatToken()`, `eatStringChars()`, `eatTemplateLiteral()`, `eatMultilineLiteral()` |
| `javatools/src/main/java/org/xvm/compiler/Parser.java` | Grammar rules | All `parse*()` methods, BNF in Javadoc comments |
| `javatools/src/main/java/org/xvm/compiler/Token.java` | Token definitions | `enum Id` with all keywords, operators, literals |

### Supplementary Reference (May Be Outdated - Verify Against Lexer/Parser)

| File | Description | Notes |
|------|-------------|-------|
| `doc/bnf.x` | BNF grammar file | ~1300 lines. **Not guaranteed to be current** - verify rules against Parser.java before using. |
| `doc/x.md` | Language specification (DRAFT-20180913) | Design document with BNF-like grammar. **Not guaranteed to be current** - verify against Lexer/Parser. |

### Token Categories from Token.java

**Operators (with special semantics):**
- Range: `..` `>..<` `..<` `>..`
- Assignment: `=` `+=` `-=` `*=` `/=` `%=` etc.
- Member access: `.` `?.`
- Lambda: `->`
- Async: `^(`

**Keywords (context-sensitive marked with *):**
- Declarations: `module`* `package`* `class`* `interface`* `mixin`* `service`* `const`* `enum`* `annotation`
- Modifiers: `public` `private` `protected` `static` `abstract` `immutable`
- Control: `if` `else` `for` `while` `do` `switch` `case` `default` `break` `continue` `return`
- Types: `val`* `var`* `void` `conditional` `typedef` `function`
- Special: `this`* `super`* `outer`* and variants like `this:class`* `this:module`*
- Assert variants: `assert` `assert:rnd` `assert:arg` `assert:bounds` `assert:TODO` `assert:once` `assert:test` `assert:debug`
