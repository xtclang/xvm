# XTC Language Server - Manual Test Plan

This document describes how to manually test every feature implemented in the XTC Language Server and IntelliJ plugin.

## Feature Implementation Status

> See [PLAN_IDE_INTEGRATION.md](plans/PLAN_IDE_INTEGRATION.md) for the canonical feature implementation matrix comparing Mock, Tree-sitter, and Compiler adapter capabilities.

---

## Pre-Test Setup

### 1. Build with Specific Adapter

> **Note:** All `./gradlew :lang:*` commands require `-PincludeBuildLang=true -PincludeBuildAttachLang=true` when run from the project root.

```bash
# Build with tree-sitter adapter (recommended for full functionality)
./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter

# Or build with mock adapter (default, no native dependencies)
./gradlew :lang:lsp-server:build -Plsp.adapter=mock
```

### 2. Launch in Your Editor

**IntelliJ:**
```bash
./gradlew :lang:runIntellijPlugin
```

**VS Code:**
```bash
# Build and install the extension
cd lang/vscode-extension
npm install && npm run compile
npx vsce package          # creates xtc-language-*.vsix
code --install-extension xtc-language-*.vsix

# Ensure the LSP server fat JAR exists
ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar

# Open a folder with .x files
code /path/to/xtc-project
```

The extension starts the LSP server automatically when a `.x` file is opened.
Requires `JAVA_HOME` or `XTC_JAVA_HOME` pointing to Java 25+.

### 3. Verify Which Adapter is Active

**IntelliJ:**
1. Open IntelliJ → Help → Show Log in Finder/Explorer
2. Search for `"Selected adapter:"` or `"XTC LSP Server started"`
3. You should see one of:
   - `"Selected adapter: TreeSitterAdapter"` - Tree-sitter is active
   - `"Selected adapter: MockXtcCompilerAdapter"` - Mock adapter is active
   - `"Selected adapter: MockXtcCompilerAdapter (fallback - ...)"` - Tree-sitter failed

**VS Code:**
1. Open Output panel (Ctrl+Shift+U / Cmd+Shift+U)
2. Select "XTC Language Server" from the dropdown
3. Look for `"Backend: TreeSitter"` or `"Backend: Mock"`

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

**Note:** Semantic tokens Tier 1 (declaration-site classification, type refs, annotations, calls) is implemented. Tier 2+ (distinguishing field vs local vs parameter at usage sites) requires compiler integration.

---

### 2. Hover Information

**LSP Method:** `textDocument/hover`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Hover mouse over a symbol (or Ctrl+Q for Quick Documentation)
- *VS Code:* Hover mouse over a symbol

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

**How to trigger:**
- *IntelliJ:* Ctrl+Space (Basic Completion), or type and wait for auto-popup
- *VS Code:* Ctrl+Space, or type and wait for auto-popup

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
**Status:** ✅ Done (same-file + cross-file via workspace index)
**Works with:** Both adapters (cross-file: tree-sitter only)

**How to trigger:**
- *IntelliJ:* Ctrl+Click on a symbol, or Ctrl+B, or F12
- *VS Code:* Ctrl+Click on a symbol, or F12

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 4.1 | Class reference | Ctrl+Click `Person` in return type | Jumps to `class Person` |
| 4.2 | Method reference | Ctrl+Click `getName` call | Jumps to method |
| 4.3 | Property reference | Ctrl+Click `name` in `return name;` | Jumps to property |
| 4.4 | Cross-file type | Ctrl+Click on a type defined in another file | Jumps to definition in other file |

**Note:** Cross-file definition uses workspace index fallback (prefers type declarations). Import-path-based resolution is not yet implemented.

---

### 5. Find References

**LSP Method:** `textDocument/references`
**Status:** ⚠️ Partial
**Works with:** Tree-sitter (Mock limited)

**How to trigger:**
- *IntelliJ:* Alt+F7 (Find Usages), or right-click → Find Usages, or Shift+F12
- *VS Code:* Shift+F12, or right-click → Find All References

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 5.1 | Find class usages | Right-click `Person` → Find Usages / Find All References | ⚠️ decl only | ✅ |
| 5.2 | Find method usages | Right-click `getName` → Find Usages / Find All References | ⚠️ decl only | ✅ |
| 5.3 | Find property usages | Right-click `name` → Find Usages / Find All References | ⚠️ decl only | ✅ |

**Mock limitation:** Returns only the declaration, not actual usages.

---

### 6. Document Structure / Outline

**LSP Method:** `textDocument/documentSymbol`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Alt+7 (Structure tool window), Ctrl+F12 (File Structure popup)
- *VS Code:* Ctrl+Shift+O (Go to Symbol in File), or Outline panel in sidebar

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 6.1 | Structure view | IntelliJ: Alt+7; VS Code: Outline panel | Hierarchical outline |
| 6.2 | File structure popup | IntelliJ: Ctrl+F12; VS Code: Ctrl+Shift+O | Popup with all symbols |
| 6.3 | Breadcrumbs | Look at editor top (VS Code) or bottom (IntelliJ) | `TestModule > Person > getName` |

---

### 7. Diagnostics / Error Detection

**LSP Method:** `textDocument/publishDiagnostics`
**Status:** ⚠️ Partial
**Works with:** Different behavior per adapter

**How to trigger:** Diagnostics appear automatically as you type (push-based).
- *IntelliJ:* Red/yellow squiggly underlines; Alt+Enter for quick fixes; F2 to jump to next error
- *VS Code:* Red/yellow squiggly underlines; Ctrl+Shift+M (Problems panel); F8 to jump to next error

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

### 8. Document Highlight

**LSP Method:** `textDocument/documentHighlight`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Click on any identifier — other occurrences highlight automatically
- *VS Code:* Click on any identifier — other occurrences highlight automatically

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 8.1 | Class highlight | Click on `Person` | All `Person` occurrences highlighted |
| 8.2 | Property highlight | Click on `name` | All `name` occurrences highlighted |
| 8.3 | Method highlight | Click on `getName` | All `getName` occurrences highlighted |
| 8.4 | No highlight on whitespace | Click on empty space | No highlights |

---

### 9. Selection Ranges (Smart Select)

**LSP Method:** `textDocument/selectionRange`
**Status:** ✅ Done (tree-sitter only)
**Works with:** Tree-sitter adapter

**How to trigger:**
- *IntelliJ:* Ctrl+W (Expand) / Ctrl+Shift+W (Shrink)
- *VS Code:* Shift+Alt+Right (Expand) / Shift+Alt+Left (Shrink)

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 9.1 | Expand from identifier | Place cursor on `name`, expand | ❌ | ✅ selects `name` → `String name` → class body → class → module |
| 9.2 | Expand from method body | Place cursor inside `return name;`, expand | ❌ | ✅ selects statement → method body → method → class |
| 9.3 | Shrink back | After expanding, shrink | ❌ | ✅ reverses the chain |

---

### 10. Folding Ranges

**LSP Method:** `textDocument/foldingRange`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Click the fold/unfold arrows in the editor gutter (left margin); Ctrl+Shift+Minus (fold all) / Ctrl+Shift+Plus (unfold all)
- *VS Code:* Click fold arrows in gutter; Ctrl+Shift+[ (fold) / Ctrl+Shift+] (unfold); Ctrl+K Ctrl+0 (fold all) / Ctrl+K Ctrl+J (unfold all)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 10.1 | Class fold | Click fold arrow next to `class Person {` | Class body collapses |
| 10.2 | Method fold | Click fold arrow next to `String getName() {` | Method body collapses |
| 10.3 | Import fold | Add 3+ import statements, fold | Import block collapses |
| 10.4 | Nested fold | Fold method inside class | Method folds independently |
| 10.5 | Fold all | Ctrl+Shift+Minus / Ctrl+K Ctrl+0 | All regions collapse |

---

### 11. Rename Symbol

**LSP Method:** `textDocument/prepareRename` + `textDocument/rename`
**Status:** ✅ Done (same-file only)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Shift+F6 on an identifier, or right-click → Refactor → Rename
- *VS Code:* F2 on an identifier, or right-click → Rename Symbol

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 11.1 | Rename class | Place cursor on `Person`, press F2/Shift+F6, type `Employee` | All `Person` occurrences renamed |
| 11.2 | Rename method | Place cursor on `getName`, rename to `fetchName` | All occurrences updated |
| 11.3 | Rename property | Place cursor on `name`, rename to `fullName` | All occurrences updated |
| 11.4 | Prepare rename | Press F2/Shift+F6 on `Person` | Identifier range highlighted, old name shown |
| 11.5 | Cancel rename | Press Escape during rename | No changes applied |

---

### 12. Code Actions

**LSP Method:** `textDocument/codeAction`
**Status:** ✅ Done (organize imports)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Alt+Enter on an import line, or lightbulb icon in gutter
- *VS Code:* Ctrl+. (Quick Fix menu), or click lightbulb icon

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 12.1 | Organize imports (unsorted) | Add unsorted imports: `import b; import a;`, trigger code action | Imports sorted alphabetically |
| 12.2 | No action (already sorted) | With sorted imports, open code actions | No "Organize Imports" offered |
| 12.3 | No action (single import) | With 1 import, open code actions | No action offered |

---

### 13. Document Formatting

**LSP Method:** `textDocument/formatting` + `textDocument/rangeFormatting`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger (full document):**
- *IntelliJ:* Ctrl+Alt+L (Reformat Code)
- *VS Code:* Shift+Alt+F (Format Document)

**How to trigger (selection only):**
- *IntelliJ:* Select text, then Ctrl+Alt+L
- *VS Code:* Select text, then Ctrl+K Ctrl+F (Format Selection)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13.1 | Remove trailing whitespace | Add spaces at end of a line, format | Trailing whitespace removed |
| 13.2 | Insert final newline | Remove final newline from file, format | Final newline added |
| 13.3 | Range format | Select 2-3 lines with trailing spaces, format selection | Only selected lines cleaned |
| 13.4 | No-op on clean file | Format a file with no trailing whitespace | No changes |

---

### 14. Signature Help

**LSP Method:** `textDocument/signatureHelp`
**Status:** ✅ Done (tree-sitter only, same-file)
**Works with:** Tree-sitter adapter

**How to trigger:**
- *IntelliJ:* Type `(` after a method name, or press Ctrl+P inside argument list
- *VS Code:* Type `(` after a method name, or press Ctrl+Shift+Space inside argument list

| # | Test | Steps | Mock | Tree-sitter |
|---|------|-------|:----:|:-----------:|
| 14.1 | Show params on `(` | Type `createUser(` | ❌ | ✅ shows `String name` |
| 14.2 | Active param on `,` | Type `method(arg1,` | ❌ | ✅ highlights second param |
| 14.3 | No help outside call | Place cursor on a variable | ❌ | ✅ returns null (no popup) |

---

### 15. Document Links

**LSP Method:** `textDocument/documentLink`
**Status:** ✅ Done
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Import paths appear as clickable links (Ctrl+Click)
- *VS Code:* Import paths appear as clickable underlined text (Ctrl+Click)

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 15.1 | Import link | Add `import ecstasy.text.String;` | Path is clickable/underlined |
| 15.2 | Tooltip | Hover over import path | Shows `import ecstasy.text.String` tooltip |
| 15.3 | Multiple imports | Add 3 import statements | All paths are links |

**Note:** Links are not resolvable (target is null) until the compiler adapter provides file resolution.

---

## Adapter Comparison Summary

> See [PLAN_IDE_INTEGRATION.md](plans/PLAN_IDE_INTEGRATION.md) for the canonical adapter comparison matrix.

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

**IntelliJ:**
```bash
# Verify TextMate bundle is present
ls lang/intellij-plugin/build/idea-sandbox/*/plugins/intellij-plugin/lib/textmate/
# Should contain: xtc.tmLanguage.json, package.json, language-configuration.json
```

**VS Code:**
1. Check the extension is installed: Extensions panel → search "XTC"
2. Verify `.x` files are associated: look for "XTC" in the status bar language indicator
3. If missing: `code --install-extension lang/vscode-extension/xtc-language-*.vsix`

### VS Code LSP Not Starting

1. Open Output panel → select "XTC Language Server"
2. If no output channel exists, the extension failed to activate
3. Check `JAVA_HOME` or `XTC_JAVA_HOME` points to Java 25+
4. Verify the fat JAR exists: `ls lang/lsp-server/build/libs/xtc-lsp-server-*-all.jar`
5. Try Developer Tools: Help → Toggle Developer Tools → Console tab

---

---

## Out-of-Process LSP Server Tests

The LSP server runs as a separate Java process (requires Java 25+). These tests verify
the process management and health monitoring.

### Prerequisites

- Java 25+ installed and available via `JAVA_HOME` or on PATH
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
4. Verify error: "No Java 25+ runtime found"

---

## Future Enhancements

### Semantic Tokens Phase 2+ (TODO)

Phase 1 (Tier 1) is implemented: declarations, type references, annotations, call/member
expressions, and modifiers are classified via `SemanticTokenEncoder`. Future phases:
- **Tier 2**: Heuristic usage-site tokens (UpperCamelCase type detection, broader property/variable classification)
- **Tier 3** (compiler): Override tree-sitter tokens with compiler-resolved classifications

### Cross-File References (TODO)

Cross-file go-to-definition and workspace symbols are implemented via the workspace index.
Still remaining:
- Cross-file find-references (workspace-wide name search)
- Import resolution (resolve import paths to file URIs)
- Cross-file rename refactoring

### Full Compiler Integration (TODO)

Planned features once compiler adapter is implemented:
- Semantic error detection
- Type inference in hover and inlay hints
- Accurate completion filtering (type-aware)
- Cross-file rename refactoring
- Diagnostic-driven quick fixes and refactorings
