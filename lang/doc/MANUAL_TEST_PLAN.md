# XTC Language Server - Manual Test Plan

This document describes how to manually test every feature implemented in the XTC Language Server and IntelliJ plugin.

## Feature Implementation Status

### Complete Feature Matrix

This matrix shows all LSP features across the three adapter implementations:

1. **Mock Adapter** - Regex-based, no dependencies, for quick testing
2. **Tree-sitter Adapter** - Native incremental parser, production-ready syntax analysis
3. **Compiler Adapter** - Future full compiler integration for semantic analysis

| Feature | LSP Method | Mock | Tree-sitter | Compiler | Notes |
|---------|-----------|:----:|:-----------:|:--------:|-------|
| **Syntax Highlighting** |
| TextMate highlighting | TextMate | ✅ | ✅ | ✅ | Independent of LSP adapter |
| Semantic tokens | `semanticTokens/*` | ❌ | ❌ | 🔮 | Distinguishes field/local/param |
| **Navigation** |
| Go to Definition (same file) | `textDocument/definition` | ✅ | ✅ | 🔮 | |
| Go to Definition (cross-file) | `textDocument/definition` | ❌ | ❌ | 🔮 | Requires import resolution |
| Find References (same file) | `textDocument/references` | ⚠️ | ✅ | 🔮 | Mock: declaration only |
| Find References (cross-file) | `textDocument/references` | ❌ | ❌ | 🔮 | Requires workspace index |
| Document Symbols / Outline | `textDocument/documentSymbol` | ✅ | ✅ | 🔮 | Structure view works |
| Document Highlight | `textDocument/documentHighlight` | ✅ | ✅ | 🔮 | Highlight symbol occurrences |
| Selection Ranges | `textDocument/selectionRange` | ❌ | ✅ | 🔮 | Mock: returns empty (needs AST) |
| Workspace Symbols | `workspace/symbol` | ❌ | ❌ | 🔮 | Cross-file search |
| **Editing** |
| Hover Information | `textDocument/hover` | ✅ | ✅ | 🔮 | Mock/TS: kind+name; Compiler: +types |
| Code Completion (keywords) | `textDocument/completion` | ✅ | ✅ | 🔮 | |
| Code Completion (context-aware) | `textDocument/completion` | ❌ | ✅ | 🔮 | After-dot member completion |
| Code Completion (type-aware) | `textDocument/completion` | ❌ | ❌ | 🔮 | Requires type inference |
| Signature Help | `textDocument/signatureHelp` | ❌ | ✅ | 🔮 | TS: same-file method params |
| Document Links | `textDocument/documentLink` | ✅ | ✅ | 🔮 | Clickable import paths |
| **Diagnostics** |
| Syntax Errors | `textDocument/publishDiagnostics` | ⚠️ | ✅ | 🔮 | Mock: ERROR comments only |
| Semantic Errors | `textDocument/publishDiagnostics` | ❌ | ❌ | 🔮 | Type errors, undefined refs |
| **Refactoring** |
| Rename Symbol (same file) | `textDocument/rename` | ✅ | ✅ | 🔮 | Text-based replacement |
| Rename Symbol (cross-file) | `textDocument/rename` | ❌ | ❌ | 🔮 | Requires workspace index |
| Code Actions (organize imports) | `textDocument/codeAction` | ✅ | ✅ | 🔮 | Sort unsorted imports |
| **Formatting** |
| Format Document | `textDocument/formatting` | ✅ | ✅ | 🔮 | Trailing whitespace + final newline |
| Format Selection | `textDocument/rangeFormatting` | ✅ | ✅ | 🔮 | Range-scoped formatting |
| **Code Intelligence** |
| Folding Ranges | `textDocument/foldingRange` | ✅ | ✅ | 🔮 | Mock: braces; TS: AST nodes |
| Inlay Hints | `textDocument/inlayHint` | ❌ | ❌ | 🔮 | Requires type inference |
| Call Hierarchy | `callHierarchy/*` | ❌ | ❌ | 🔮 | Requires semantic analysis |
| Type Hierarchy | `typeHierarchy/*` | ❌ | ❌ | 🔮 | Requires type resolution |

**Legend:**
- ✅ **Implemented** - Working in current builds
- ⚠️ **Limited** - Partial implementation with known limitations
- ⏳ **Possible** - Can be implemented with current infrastructure
- ❌ **Not Possible** - Cannot implement without additional infrastructure
- 🔮 **Planned** - Will be possible once compiler adapter is complete

### Adapter Capability Summary

| Capability | Mock | Tree-sitter | Compiler |
|------------|:----:|:-----------:|:--------:|
| **Dependencies** | None | Native lib | XTC compiler |
| **Parse Speed** | Fast (regex) | Very fast (native) | Slower (full parse) |
| **Error Tolerance** | None | Excellent | Good |
| **Incremental Updates** | No | Yes | Partial |
| **Symbol Detection** | Top-level | Nested scopes | Full AST |
| **Type Information** | No | No | Yes |
| **Cross-file Analysis** | No | No | Yes |
| **Semantic Validation** | No | No | Yes |
| **Rename** | Same-file (text) | Same-file (AST) | Cross-file |
| **Code Actions** | Organize imports | Organize imports | Quick fixes |
| **Formatting** | Trailing WS + newline | Trailing WS + newline | Full formatter |
| **Folding** | Brace matching | AST node boundaries | AST nodes |
| **Signature Help** | No | Same-file methods | Cross-file |
| **Document Highlight** | Text matching | AST identifiers | Semantic |
| **Selection Ranges** | No | AST walk-up chain | AST walk-up |
| **Document Links** | Import regex | Import AST nodes | Resolved URIs |
| **Production Ready** | Testing only | Yes | Future |

---

## Pre-Test Setup

### 1. Build with Specific Adapter

```bash
# Build with tree-sitter adapter (recommended for full functionality)
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter

# Or build with mock adapter (default, no native dependencies)
./gradlew :lang:lsp-server:build -Plsp.adapter=mock

# Run IntelliJ with the plugin
./gradlew :lang:runIntellijPlugin
```

### 2. Verify Which Adapter is Active

**Check IntelliJ Logs:**
1. Open IntelliJ → Help → Show Log in Finder/Explorer
2. Search for `"Selected adapter:"` or `"XTC LSP Server started"`
3. You should see one of:
   - `"Selected adapter: TreeSitterAdapter"` - Tree-sitter is active
   - `"Selected adapter: MockXtcCompilerAdapter"` - Mock adapter is active
   - `"Selected adapter: MockXtcCompilerAdapter (fallback - ...)"` - Tree-sitter failed

### 3. Create Test File

Create a file named `TestModule.x` with this content:

```xtc
module TestModule {
    class Person {
        String name;
        Int age;

        String getName() {
            return name;
        }

        void setAge(Int newAge) {
            age = newAge;
        }
    }

    interface Greeter {
        void greet();
    }

    service UserService {
        Person createUser(String name) {
            return new Person();
        }
    }

    // Test: ERROR markers are detected as diagnostics
    // ERROR: This is a test error message
}
```

---

## Test Cases by Feature

### 1. Syntax Highlighting (TextMate)

**Provider:** TextMate grammar (NOT tree-sitter or LSP)
**Status:** ✅ Done
**Works with:** Both adapters (independent of LSP)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 1.1 | Keywords | Look at `module`, `class`, `interface`, `service`, `return` | Different color from identifiers |
| 1.2 | Types | Look at `String`, `Int`, `Person` | Type color |
| 1.3 | Strings | Add `"hello"` literal | String color |
| 1.4 | Comments | Add `// comment` and `/* block */` | Comment color |
| 1.5 | Numbers | Add `42`, `3.14` | Number color |

**Note:** Semantic tokens (distinguishing field vs local vs parameter) is TODO.

---

### 2. Hover Information

**LSP Method:** `textDocument/hover`
**Status:** ✅ Done
**Works with:** Both adapters

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 2.1 | Class hover | Hover over `Person` in declaration | Shows `class Person` |
| 2.2 | Method hover | Hover over `getName` | Shows `method getName` |
| 2.3 | Property hover | Hover over `name` property | Shows `property name` |
| 2.4 | Interface hover | Hover over `Greeter` | Shows `interface Greeter` |
| 2.5 | Service hover | Hover over `UserService` | Shows `service UserService` |

---

### 3. Code Completion

**LSP Method:** `textDocument/completion`
**Status:** ⚠️ Partial (Mock not context-aware)
**Works with:** Both adapters (tree-sitter better)

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 3.1 | Keyword completion | Type `cla` + Ctrl+Space | ✅ | ✅ |
| 3.2 | Type completion | Type `Str` + Ctrl+Space | ✅ | ✅ |
| 3.3 | Document symbols | Type `Per` + Ctrl+Space | ✅ | ✅ |
| 3.4 | Built-in types | Type `Int` + Ctrl+Space | ✅ | ✅ |
| 3.5 | After dot (member) | Type `person.` + Ctrl+Space | ❌ | ✅ |
| 3.6 | Context filtering | Inside method vs class level | ❌ | ⚠️ |

---

### 4. Go to Definition

**LSP Method:** `textDocument/definition`
**Status:** ✅ Done (same-file only)
**Works with:** Both adapters

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 4.1 | Class reference | Ctrl+Click `Person` in return type | Jumps to `class Person` |
| 4.2 | Method reference | Ctrl+Click `getName` call | Jumps to method |
| 4.3 | Property reference | Ctrl+Click `name` in `return name;` | Jumps to property |

**Limitation:** Cross-file navigation TODO (requires import resolution).

---

### 5. Find References

**LSP Method:** `textDocument/references`
**Status:** ⚠️ Partial
**Works with:** Tree-sitter (Mock limited)

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 5.1 | Find class usages | Right-click `Person` → Find Usages | ⚠️ decl only | ✅ |
| 5.2 | Find method usages | Right-click `getName` → Find Usages | ⚠️ decl only | ✅ |
| 5.3 | Find property usages | Right-click `name` → Find Usages | ⚠️ decl only | ✅ |

**Mock limitation:** Returns only the declaration, not actual usages.

---

### 6. Document Structure / Outline

**LSP Method:** `textDocument/documentSymbol`
**Status:** ✅ Done
**Works with:** Both adapters

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 6.1 | Structure view | View → Tool Windows → Structure (Alt+7) | Hierarchical outline |
| 6.2 | File structure popup | Ctrl+F12 | Popup with all symbols |
| 6.3 | Breadcrumbs | Look at editor bottom | `TestModule > Person > getName` |

---

### 7. Diagnostics / Error Detection

**LSP Method:** `textDocument/publishDiagnostics`
**Status:** ⚠️ Partial
**Works with:** Different behavior per adapter

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 7.1 | Syntax error (missing brace) | Delete a `}` | ❌ | ✅ |
| 7.2 | Unmatched braces | Add `{` without `}` | ⚠️ | ✅ |
| 7.3 | ERROR comment marker | Add `// ERROR: message` | ✅ | N/A |
| 7.4 | WARN comment marker | Add `// WARN: message` | ✅ | N/A |
| 7.5 | Semantic error (undefined var) | Use undefined variable | ❌ | ❌ |

**Notes:**
- Mock: Detects `// ERROR:` and `// WARN:` comment markers (testing convenience)
- Tree-sitter: Real syntax error detection via parsing (doesn't use comment markers by design)
- Comment markers: N/A for tree-sitter because it focuses on real parse errors
- Semantic errors: Requires compiler adapter (future)

---

## Adapter Comparison Summary

| Aspect | Mock Adapter | Tree-sitter Adapter | Compiler Adapter |
|--------|:------------:|:-------------------:|:----------------:|
| **Dependencies** | None | Native library | XTC compiler |
| **Performance** | Fast (regex) | Very fast (native) | Moderate |
| **Error tolerance** | None (literal match) | Excellent | Good |
| **Symbol detection** | Top-level only | Nested scopes | Full AST |
| **Completion** | Keywords + symbols | Keywords + imports | Type-aware |
| **Find references** | Declaration only | All in file | All in workspace |
| **Rename** | Same-file (text) | Same-file (AST) | Cross-file |
| **Code actions** | Organize imports | Organize imports | Quick fixes + refactorings |
| **Formatting** | Trailing WS removal | Trailing WS removal | Full formatter |
| **Folding ranges** | Brace matching | AST nodes | AST nodes |
| **Signature help** | None | Same-file methods | Cross-file overloads |
| **Document highlight** | Text matching | AST identifiers | Semantic (R/W) |
| **Selection ranges** | None (empty) | AST walk-up chain | AST walk-up chain |
| **Document links** | Import regex | Import AST nodes | Resolved file URIs |
| **Diagnostics** | Comment markers only | Syntax errors | Semantic errors |
| **Type information** | None | None | Full |
| **Incremental updates** | No | Yes | Partial |
| **Cross-file analysis** | No | No | Yes |
| **Recommended for** | Quick testing | Production syntax | Full IDE experience |

---

## Troubleshooting

### Tree-sitter Not Loading

If you see `"fallback - tree-sitter native lib missing"` in logs:

```bash
# 1. Verify native library exists
ls lang/tree-sitter/src/main/resources/native/darwin-arm64/  # macOS ARM
ls lang/tree-sitter/src/main/resources/native/linux-x64/     # Linux

# 2. Rebuild if missing
./gradlew :lang:tree-sitter:buildAllNativeLibraries
./gradlew :lang:tree-sitter:copyAllNativeLibrariesToResources

# 3. Verify not stale
./gradlew :lang:tree-sitter:ensureNativeLibraryUpToDate
```

### LSP Not Connecting

1. Check LSP4IJ plugin is installed in IntelliJ
2. Check `.x` files are associated with XTC language
3. Look for errors in: Help → Show Log in Finder/Explorer
4. Try: File → Invalidate Caches / Restart

### No Syntax Highlighting

```bash
# Verify TextMate bundle is present
ls lang/intellij-plugin/build/idea-sandbox/*/plugins/intellij-plugin/lib/textmate/
# Should contain: xtc.tmLanguage.json, package.json, language-configuration.json
```

---

---

## Out-of-Process LSP Server Tests

The LSP server runs as a separate Java process (requires Java 23+). These tests verify
the process management and health monitoring.

### Prerequisites

- Java 23+ installed and available via `JAVA_HOME` or on PATH
- XTC project with `.x` files

### Test: Server Startup

```bash
./gradlew :lang:intellij-plugin:runIde
```

**Expected in console:**
```
[XTC-LSP] XTC Language Server v0.4.4-SNAPSHOT
[XTC-LSP] Backend: Tree-sitter
[XTC-LSP] TreeSitterAdapter ready: native library loaded and verified
[XTC-LSP] XtcParser health check PASSED: parsed test module successfully
```

**Expected in IDE:**
- Notification: "XTC Language Server Started - Out-of-process server (v..., adapter=treesitter)"

### Test: Health Check

1. Open an `.x` file in the IDE
2. Look for console output:
   - `Native library: extracted libtree-sitter-xtc.dylib to ...`
   - `Native library: successfully loaded XTC tree-sitter grammar (FFM API)`
   - `XtcParser health check PASSED`

### Test: Crash Recovery

1. Find the LSP server process: `ps aux | grep xtc-lsp-server`
2. Kill it: `kill -9 <pid>`
3. Verify notification appears: "XTC Language Server Crashed"
4. Click "Restart Server"
5. Verify server restarts (new notification)

### Test: Version Display

1. After LSP starts, check notification shows correct version
2. Version should NOT be "?" - should show actual version like "v0.4.4-SNAPSHOT"

### Test: Native Library Not Found

1. Temporarily rename/remove native libraries from JAR
2. Start IDE
3. Verify error notification about native library
4. Verify fallback to mock adapter (or fail-fast error)

### Test: Java Version Too Low

1. Set `JAVA_HOME` to Java 21 installation
2. Unset `XTC_JAVA_HOME`
3. Start IDE
4. Verify error: "No Java 23+ runtime found"

---

## Future Enhancements

### Semantic Tokens (TODO)

Will replace TextMate with LSP semantic tokens to distinguish:
- Field vs parameter vs local variable
- Type name vs variable name
- Declaration vs reference

### Cross-File Navigation (TODO)

Requires:
- Import resolution
- Module dependency tracking
- Workspace-wide symbol index

### Full Compiler Integration (TODO)

Planned features once compiler adapter is implemented:
- Semantic error detection
- Type inference in hover and inlay hints
- Accurate completion filtering (type-aware)
- Cross-file rename refactoring
- Diagnostic-driven quick fixes and refactorings
