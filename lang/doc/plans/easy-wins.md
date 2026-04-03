# Easy Wins: LSP & IDE Plugin Improvements

Tracked improvements across VS Code extension, LSP server, and IntelliJ plugin.

## VS Code Extension

### 1. Add JVM args to LSP server launch
- **File:** `vscode-extension/src/extension.ts`
- **Status:** [x] Done
- **What:** Add `-Dapple.awt.UIElement=true`, `-Djava.awt.headless=true`, `-Dxtc.logLevel=INFO` to server launch args (matching IntelliJ)
- **Why:** Missing headless flag can cause macOS dock icon; missing logLevel means no log config

### 2. Add `workspace/configuration` handler
- **File:** `vscode-extension/src/extension.ts`
- **Status:** [x] Done
- **What:** Implement middleware for `workspace/configuration` so LSP server gets `xtc.formatting` settings
- **Why:** Server requests config after init but VS Code never answers — falls back to defaults

### 3. Add `xtc.formatting.*` settings to package.json
- **File:** `vscode-extension/package.json`
- **Status:** [x] Done
- **What:** Add `contributes.configuration` with indentSize, continuationIndentSize, tabSize, insertSpaces, maxLineWidth
- **Why:** Users can't configure formatting preferences

### 4. Add `configurationDefaults` for XTC files
- **File:** `vscode-extension/package.json`
- **Status:** [x] Done
- **What:** Set `editor.formatOnType: true`, `editor.tabSize: 4`, `editor.insertSpaces: true` for `[xtc]` scope
- **Why:** On-type formatting is implemented server-side but VS Code won't use it without this default

### 5. Enable `editor.formatOnType` by default
- **File:** `vscode-extension/package.json`
- **Status:** [x] Done (part of #4)
- **What:** Part of #4 — ensure formatOnType is enabled for XTC files
- **Why:** Trigger characters `\n`, `}`, `;`, `)` are registered but VS Code needs formatOnType enabled

## LSP Server

### 6. Implement linked editing ranges in TreeSitterAdapter
- **Files:** `lsp-server/.../treesitter/TreeSitterAdapter.kt`, `lsp-server/.../XtcLanguageServer.kt`
- **Status:** [x] Done
- **What:** Implemented `getLinkedEditingRanges()` — finds identifier at cursor, returns all same-name identifiers as linked ranges. Advertised `linkedEditingRangeProvider` in server capabilities.
- **Why:** Enables rename-on-type in all editors (VS Code, IntelliJ, etc.)
- **Note:** Same-file text matching only (tree-sitter). Cross-file linked editing requires SemanticModel/XdkAdapter.

## IntelliJ Plugin

### 7. Audit live template contexts
- **File:** `intellij-plugin/src/main/resources/liveTemplates/XTC.xml`
- **Status:** [x] Done (no changes needed)
- **What:** Verified context restrictions. All templates use `OTHER` context, which is correct for TextMate-based languages — custom template contexts require PSI (a full IntelliJ language plugin). Templates won't fire in non-XTC files.
- **Why:** e.g., `module` snippet should not expand inside a method body — but finer-grained context filtering is not possible without PSI.

## Also Fixed (from audit)

### 8. Disable unimplemented inlayHintProvider
- **File:** `lsp-server/.../XtcLanguageServer.kt`
- **Status:** [x] Done
- **What:** Commented out `inlayHintProvider = Either.forLeft(true)` — was advertised but TreeSitterAdapter never implements `getInlayHints()`.

### 9. Fix stale codeLens comment
- **File:** `lsp-server/.../XtcLanguageServer.kt`
- **Status:** [x] Done
- **What:** Moved `codeLensProvider` above "not yet advertised" block; updated comment to reflect it IS implemented (Run action on module declarations).
