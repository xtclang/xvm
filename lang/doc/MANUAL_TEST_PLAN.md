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
   - `"Selected adapter: MockAdapter"` - Mock adapter is active
   - `"Selected adapter: MockAdapter (fallback - ...)"` - Tree-sitter failed
4. Also verify: `"semantic tokens ENABLED (23 types, 10 modifiers)"` in the log

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
| 8.5 | Write highlight | Click on `x` in `Int x = 42;` | Declaration site shows as **write** highlight (different color/style from reads) |
| 8.6 | Read highlight | Click on `x` in `return x;` | Usage site shows as **read** highlight |
| 8.7 | Assignment write | Click on `age` in `age = newAge;` | Assignment target shows as **write** highlight |

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
| 10.6 | Consecutive line comments | Add 3+ consecutive `//` comments | Fold arrow appears; comments collapse as one region |
| 10.7 | Non-adjacent comments | Add `//` comments separated by code | Each group folds independently |

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
**Status:** ✅ Done (organize imports + remove unused imports)
**Works with:** Both adapters

**How to trigger:**
- *IntelliJ:* Alt+Enter on an import line, or lightbulb icon in gutter
- *VS Code:* Ctrl+. (Quick Fix menu), or click lightbulb icon

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 12.1 | Organize imports (unsorted) | Add unsorted imports: `import b; import a;`, trigger code action | Imports sorted alphabetically |
| 12.2 | No action (already sorted) | With sorted imports, open code actions | No "Organize Imports" offered |
| 12.3 | No action (single import) | With 1 import, open code actions | No action offered |
| 12.4 | Remove unused import | Add `import foo.Unused;` where `Unused` is never referenced, trigger code action | "Remove unused import 'Unused'" action offered |
| 12.5 | Used import not flagged | Add `import foo.Bar;` and use `Bar` in code, trigger code action | No "Remove unused import" for `Bar` |

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

### 13a. On-Type Formatting (Auto-Indent)

**LSP Method:** `textDocument/onTypeFormatting`
**Status:** ✅ Done
**Works with:** Tree-sitter adapter only

The LSP server uses tree-sitter AST context to auto-indent as you type. Trigger characters
are `Enter`, `}`, and `;`. This is strictly better than regex-based TextMate indentation
because it understands nesting depth, continuation lines, and string literals.

**How it works:** Automatic — indentation is adjusted immediately when you type a trigger
character. No manual action needed.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13a.1 | Indent after `{` in class | Type `class Foo {` then Enter | New line indented +4 from class keyword |
| 13a.2 | Indent after `{` in method | Inside a class, type `void foo() {` then Enter | New line indented +4 from method |
| 13a.3 | Indent after `{` in if | Inside a method, type `if (True) {` then Enter | New line indented +4 from if |
| 13a.4 | Outdent `}` for class | Type `}` to close a class body | `}` aligns with the `class` keyword |
| 13a.5 | Outdent `}` for method | Type `}` to close a method body | `}` aligns with the method declaration |
| 13a.6 | Outdent `}` for if block | Type `}` to close an if block | `}` aligns with the `if` keyword |
| 13a.7 | Maintain indent after statement | After `x = 1;` press Enter | New line at same indent level |
| 13a.8 | Continuation + `{` | Type `implements Closeable {` then Enter | Body indent from declaration start (+4), not from continuation (+8) |
| 13a.9 | Module body indent | Type `module myapp {` then Enter | New line indented +4 |
| 13a.10 | No indent inside string | Press Enter inside a string literal | No indentation adjustment |
| 13a.11 | Nested constructs (3+ levels) | Class > method > if > Enter after `{` | Correct cumulative indent (e.g. 12 for 3 levels) |
| 13a.12 | After `}` line | Press Enter after a `}` line | New line at same indent as `}` |
| 13a.13 | Large file performance | Open a `.x` file > 1000 lines, type normally | < 5ms per formatting request (check LSP log) |
| 13a.14 | Doc comment continuation | Type `/**` then Enter | New line gets ` * ` prefix aligned with `/**` |
| 13a.15 | Doc comment mid-line | Press Enter on a ` * existing text` line inside `/** */` | New line gets ` * ` prefix |
| 13a.16 | Indented doc comment | Inside a class (indent 4), type `/**` then Enter | New line gets `     * ` (4 spaces + ` * `) |
| 13a.17 | Block comment continuation | Type `/*` then Enter | New line gets ` * ` prefix |
| 13a.18 | No continuation after `*/` | Press Enter after a `*/` line | Normal indentation (no ` * ` prefix) |

---

### 13b. Code Style Settings (IntelliJ)

**Provider:** IntelliJ plugin (`XtcLanguageCodeStyleSettingsProvider`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `editor.tabSize` / `editor.insertSpaces`)

Code Style settings for XTC appear under Settings > Editor > Code Style > Ecstasy.

**How to access:**
- *IntelliJ:* Settings/Preferences > Editor > Code Style > Ecstasy

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13b.1 | Settings page exists | Open Settings > Editor > Code Style | "Ecstasy" appears in the language list |
| 13b.2 | Default indent size | Open Code Style > Ecstasy > Tabs and Indents | Indent: 4, Continuation indent: 8, Tab size: 4, Use tab character: unchecked |
| 13b.3 | Code preview | Look at the preview pane | XTC code sample with classes, methods, switch/case |
| 13b.4 | Change indent size | Set indent to 2, look at preview | Preview re-indents with 2-space indent |
| 13b.5 | Change continuation indent | Set continuation indent to 4 | Preview adjusts `implements` line indent |
| 13b.6 | Tab character toggle | Check "Use tab character" | Preview switches from spaces to tabs |
| 13b.7 | Right margin | Check the right margin value | Should default to 120 |
| 13b.8 | Settings persist | Change indent to 3, close and reopen Settings | Indent still shows 3 |
| 13b.9 | Reset to defaults | Click "Reset" or "Set from..." > "Ecstasy" | Values revert to 4/8/4/false |

---

### 13c. Code Style → LSP Server Round-Trip (IntelliJ)

**Provider:** `XtcLanguageClient` (workspace/configuration) + `XtcLanguageServer`
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code falls back to LSP `FormattingOptions`)

IntelliJ Code Style settings are forwarded to the LSP server via `workspace/configuration`
at startup. Changes are pushed via `workspace/didChangeConfiguration`. The server uses
these settings for on-type formatting when no project-level `xtc-format.toml` is present.

**How to verify the config flow:**
1. Open an IntelliJ instance running the XTC plugin
2. Check the LSP server log for `workspace/configuration: editor formatting config:` — this
   confirms the server received the settings

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 13c.1 | Default config flows to server | Open a `.x` file, check LSP log | Log shows `editor formatting config: XtcFormattingConfig(indentSize=4, ...)` |
| 13c.2 | Custom indent flows to server | Set Code Style indent to 2, restart LSP server | Log shows `indentSize=2` |
| 13c.3 | 2-space indent affects formatting | Set Code Style indent to 2, type `module Foo {` then Enter | New line indented by 2 spaces (not 4) |
| 13c.4 | Nested indent with custom config | Set indent to 3, type class inside module, then method, press Enter after `{` | Indent at 9 (3 * 3 levels) |
| 13c.5 | No TextMate interference | Open a `.x` file with custom indent | Syntax highlighting works normally (no white background, correct colors) |
| 13c.6 | VS Code fallback | Open same project in VS Code with `editor.tabSize: 2` | On-type formatting uses 2-space indent from LSP FormattingOptions |

**How to restart the LSP server** (to pick up changed Code Style settings):
- *IntelliJ:* Open the LSP4IJ Language Servers panel → right-click "XTC Language Server" → Restart

**Note:** The `workspace/didChangeConfiguration` notification is sent when IntelliJ detects
a configuration change, which should propagate settings without a manual server restart.
If settings don't update immediately, restart the server as a workaround.

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
| 15.4 | Import navigation | Ctrl+Click on an import path whose type exists in the workspace | Navigates to the source file of the imported type |
| 15.5 | Unresolved import | Ctrl+Click on an import path not in the workspace | Shows tooltip but does not navigate |

**Note:** Import navigation (target resolution) is implemented via the workspace index. When the index is populated, Ctrl+Click on an import navigates to the imported type's source file. If the type is not indexed, the link shows a tooltip only.

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

> **NOTE:** With IntelliJ 2026.1+ (JBR 25), this test is no longer relevant since the
> IDE always ships with a Java 25+ runtime. The LSP server uses IntelliJ's JBR directly
> via `JavaProcessCommandBuilder`.

---

### 16. Comment Toggling (IntelliJ)

**Provider:** IntelliJ plugin (`XtcCommenter`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `language-configuration.json` for this)

This is a client-side editing feature — the LSP server handles comment *formatting*
(alignment, continuation on Enter), but toggling comment delimiters is purely an IDE action.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 16.1 | Line comment | Place cursor on a line, press Ctrl+/ | `// ` inserted at start of line |
| 16.2 | Uncomment | On a `//`-commented line, press Ctrl+/ | `// ` removed |
| 16.3 | Multi-line comment | Select 3 lines, press Ctrl+/ | All 3 lines get `// ` prefix |
| 16.4 | Multi-line uncomment | Select 3 commented lines, press Ctrl+/ | `// ` removed from all 3 |
| 16.5 | Block comment | Select text, press Ctrl+Shift+/ | Selection wrapped in `/* */` |
| 16.6 | Block uncomment | With cursor inside `/* */`, press Ctrl+Shift+/ | `/* */` removed |

---

### 17. Live Templates (IntelliJ)

**Provider:** IntelliJ plugin (`liveTemplates/XTC.xml`)
**Status:** ✅ Done
**Works with:** IntelliJ only (VS Code uses `snippets/xtc.json` separately)

Live templates are code snippets triggered by abbreviation + Tab. Press Ctrl+J to
see all available templates. The same snippets are available in VS Code (type the
prefix and select from the completion popup).

**Available templates:**

| Prefix | Expansion | Category |
|--------|-----------|----------|
| `mod` | `module name { }` | Declaration |
| `cls` | `class MyClass { }` | Declaration |
| `iface` | `interface MyInterface { }` | Declaration |
| `svc` | `service MyService { }` | Declaration |
| `mix` | `mixin MyMixin into Base { }` | Declaration |
| `enu` | `enum MyEnum { Value1, Value2 }` | Declaration |
| `con` | `const MyConst(params);` | Declaration |
| `pkg` | `package json import json.xtclang.org;` | Declaration |
| `meth` | `void myMethod() { }` | Method |
| `run` | `void run() { @Inject Console console; }` | Method |
| `runa` | `void run(String[] args=[]) { ... }` | Method |
| `construct` | `construct(params) { }` | Method |
| `prop` | `String name;` | Property |
| `roprop` | `@RO Boolean empty.get() = size == 0;` | Property |
| `lazy` | `private @Lazy String value.calc() { }` | Property |
| `if` | `if (condition) { }` | Control flow |
| `ife` | `if (condition) { } else { }` | Control flow |
| `ifv` | `if (Value value := get(key)) { }` | Control flow |
| `fori` | `for (Int i : 0 ..< count) { }` | Control flow |
| `forr` | `for (Int x : 1..100) { }` | Control flow |
| `fore` | `for (Element item : collection) { }` | Control flow |
| `while` | `while (condition) { }` | Control flow |
| `switch` | `switch (value) { case 0: }` | Control flow |
| `try` | `try { } catch (Exception e) { }` | Control flow |
| `using` | `using (resource) { }` | Control flow |
| `assert` | `assert condition;` | Control flow |
| `assertm` | `assert condition as "message";` | Control flow |
| `sout` | `@Inject Console console; console.print();` | Common |
| `print` | `console.print();` | Common |
| `inject` | `@Inject Console console;` | Common |
| `lambda` | `(params) -> expr` | Common |
| `cond` | `conditional Value find() { }` | Common |
| `doc` | `/** description */` | Comment |
| `todo` | `// TODO` | Comment |
| `webapp` | Full @WebApp module with web service | Skeleton |
| `websvc` | `@WebService("/") service { @Get ... }` | Web |
| `hello` | Complete Hello World module | Skeleton |

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 17.1 | Module template | Type `mod` + Tab | Expands to `module name { }` with cursor on name |
| 17.2 | Class template | Type `cls` + Tab | Expands to `class MyClass { }` |
| 17.3 | For loop (range) | Type `fori` + Tab | Expands to `for (Int i : 0 ..< count) { }` |
| 17.4 | For loop (inclusive) | Type `forr` + Tab | Expands to `for (Int x : 1..100) { }` |
| 17.5 | For-each | Type `fore` + Tab | Expands to `for (Element item : collection) { }` |
| 17.6 | Console print | Type `sout` + Tab | Expands to `@Inject Console console;` + `console.print();` |
| 17.7 | If conditional assign | Type `ifv` + Tab | Expands to `if (Value value := get(key)) { }` |
| 17.8 | Hello World | Type `hello` + Tab | Expands to complete Hello World module |
| 17.9 | WebApp skeleton | Type `webapp` + Tab | Expands to full @WebApp module with web service |
| 17.10 | Template list | Press Ctrl+J in editor | Shows all XTC templates with descriptions |
| 17.11 | Tab navigation | Type `meth` + Tab, fill return type, Tab, fill name | Cursor moves through variables in order |
| 17.12 | Inject template | Type `inject` + Tab | Expands to `@Inject Type name;` |
| 17.13 | Mixin template | Type `mix` + Tab | Expands to `mixin Name into Base { }` |
| 17.14 | Conditional method | Type `cond` + Tab | Expands to `conditional Value find() { }` |

---

### 18. Code Lens (Run Actions)

**LSP Method:** `textDocument/codeLens`
**Status:** ✅ Done
**Works with:** Tree-sitter adapter (both IntelliJ and VS Code)

Code lenses appear as inline annotations above module declarations. LSP4IJ (IntelliJ)
and VS Code render them automatically from the LSP server response — no plugin code needed.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 18.1 | Run lens on module | Open a `.x` file with `module myapp { }` | "▶ Run myapp" appears above the module declaration |
| 18.2 | No lens on class | Open a file with only `class Foo { }` (no module) | No code lens appears |
| 18.3 | Lens position | Check the lens annotation position | Aligned with the `module` keyword line |
| 18.4 | Multiple files | Open two `.x` files with different modules | Each shows its own module's Run lens |

---

### 19. Semantic Tokens

**LSP Method:** `textDocument/semanticTokens/full`
**Status:** ✅ Done (enabled by default)
**Works with:** Tree-sitter adapter (both IntelliJ and VS Code)

Semantic tokens layer on top of TextMate highlighting, providing AST-aware coloring
that TextMate's regex patterns cannot achieve. The server logs `semantic tokens ENABLED`
at startup to confirm they're active.

**How to verify:**
- *IntelliJ:* Open a `.x` file — types, methods, properties, and annotations should
  have distinct colors. Check LSP server log for `semantic tokens ENABLED`.
- *VS Code:* Same — semantic tokens are automatically layered on top of TextMate.

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 19.1 | Types colored distinctly | Open file with `String name;` and `Int count;` | `String` and `Int` have type color (different from `name`/`count`) |
| 19.2 | Methods vs properties | Open file with `void foo()` and `String name;` | `foo` has method color, `name` has property color |
| 19.3 | Annotations as decorators | Add `@Override` or `@Inject` | Annotation name has decorator color |
| 19.4 | Deprecated strikethrough | Add `@Deprecated class Old {}` | `Old` shown with strikethrough |
| 19.5 | new Foo() as type | Write `new Person()` | `Person` colored as type, not method |
| 19.6 | Method call coloring | Write `getName()` | `getName` colored as method call |
| 19.7 | Static modifier | Add `static void helper()` | `helper` may show italic (static modifier) |
| 19.8 | Enum members | Write `enum Color { Red, Green, Blue }` | `Red`, `Green`, `Blue` colored as enum members |
| 19.9 | Parameter highlighting | Write `void foo(Int count)` | `count` has parameter color |
| 19.10 | Namespace coloring | `module myapp` declaration | `myapp` colored as namespace |
| 19.11 | Server log confirmation | Check LSP server log at startup | Shows `semantic tokens ENABLED (23 types, 10 modifiers)` |

---

## Future Enhancements

### 20. Linked Editing Ranges

Linked editing ranges enable rename-on-type: when the cursor is on an identifier,
all same-name occurrences in the file are highlighted and edited simultaneously.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 20.1 | Basic linked editing | Place cursor on a variable name used multiple times in a method → trigger linked editing (Ctrl+Shift+F2 in VS Code, or via LSP) | All occurrences highlighted; typing renames all simultaneously |
| 20.2 | Single occurrence | Place cursor on identifier used only once | No linked editing ranges returned (need 2+ occurrences) |
| 20.3 | Parameter name | Place cursor on a method parameter name used in the body | Parameter declaration and all uses linked |
| 20.4 | Class name | Place cursor on a class name that appears in the file | All same-name occurrences linked (same-file, text-based) |
| 20.5 | Non-identifier | Place cursor on a keyword or literal | No linked editing ranges |

> **Adapter support**: TreeSitter (same-file text matching). Cross-file linked editing requires compiler/SemanticModel.

### Semantic Tokens Phase 2+ (TODO)

Phase 1 (Tier 1+) is implemented and enabled by default. Future phases:
- **Tier 2**: Heuristic usage-site tokens (UpperCamelCase type detection, broader property/variable classification)
- **Tier 3** (compiler): Override tree-sitter tokens with compiler-resolved classifications

### Cross-File References (TODO)

Cross-file go-to-definition and workspace symbols are implemented via the workspace index.
Import link navigation is now implemented (resolves import paths to file URIs via workspace index).
Still remaining:
- Cross-file find-references (workspace-wide name search)
- Cross-file rename refactoring

### Full Compiler Integration (TODO)

Planned features once compiler adapter is implemented:
- Semantic error detection
- Type inference in hover and inlay hints
- Accurate completion filtering (type-aware)
- Cross-file rename refactoring
- Diagnostic-driven quick fixes and refactorings
