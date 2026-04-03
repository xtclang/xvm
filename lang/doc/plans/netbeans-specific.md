# NetBeans IDE Integration Plan

> **Created**: 2026-04-03
> **Status**: Planning
> **Scope**: Bringing XTC language support to Apache NetBeans via the existing LSP server

## Context

Apache NetBeans has built-in LSP client support since version 12 (2020). Because
the XTC language server is a standard LSP implementation communicating over stdio,
much of the integration is straightforward -- NetBeans discovers the server, connects,
and maps LSP responses to its native UI. This plan identifies what works out of the
box, what needs a NetBeans module (plugin), and what server-side changes are required.

### Guiding Principle

**Keep logic in Kotlin, minimize Java module code.** The LSP server (Kotlin) is shared
across all editors. Every feature implemented there benefits IntelliJ, VS Code, NetBeans,
Eclipse, and any other LSP client. The NetBeans module should be a thin shell -- just
enough to register the LSP server, associate it with `.x` files, provide TextMate syntax
highlighting (if supported), and expose settings.

---

## Current State of the XTC LSP Server

The server advertises these capabilities (from `XtcLanguageServer.buildServerCapabilities()`):

| LSP Feature | Server Status | Description |
|-------------|---------------|-------------|
| `textDocument/hover` | Done | Tooltip with type/doc info |
| `textDocument/completion` | Done | Code completion (`.`, `:`, `<` triggers) |
| `textDocument/definition` | Done | Go-to-definition |
| `textDocument/references` | Done | Find all references (same file + workspace) |
| `textDocument/documentSymbol` | Done | Outline / breadcrumbs |
| `textDocument/formatting` | Done | Whole-document formatting |
| `textDocument/rangeFormatting` | Done | Format selected range |
| `textDocument/onTypeFormatting` | Done | Auto-indent on Enter, `}`, `;`, `)` |
| `textDocument/rename` | Done | Rename with prepare support |
| `textDocument/codeAction` | Done | Quick fixes (organize imports, auto-import, etc.) |
| `textDocument/documentHighlight` | Done | Highlight occurrences under cursor |
| `textDocument/selectionRange` | Done | Smart expand/shrink selection |
| `textDocument/foldingRange` | Done | Code folding regions |
| `textDocument/signatureHelp` | Done | Parameter hints (`(`, `,` triggers) |
| `textDocument/inlayHint` | Done | Inline type/param hints |
| `textDocument/documentLink` | Done | Clickable import paths |
| `textDocument/semanticTokens/full` | Done | Semantic highlighting (23 token types, 10 modifiers) |
| `textDocument/codeLens` | Done | Run action on module declarations |
| `workspace/symbol` | Done | Workspace-wide symbol search |
| `textDocument/publishDiagnostics` | Done | Syntax error reporting |
| `workspace/configuration` | Done | Pulls `xtc.formatting` settings from client |

---

## NetBeans LSP Support Overview

### Built-in LSP Client (since NetBeans 12)

NetBeans includes a generic LSP client in the `ide.lsp.client` module. It provides:

- Automatic mapping of LSP responses to NetBeans editor features
- Server process lifecycle management (start/stop/restart)
- stdio, socket, and named pipe transports
- File type association via MIME types
- Project-scoped server instances

### What NetBeans LSP Client Supports

| LSP Feature | NetBeans Support | Notes |
|-------------|-----------------|-------|
| `textDocument/hover` | Yes | Shows in tooltip popup |
| `textDocument/completion` | Yes | Integrates with code completion popup |
| `textDocument/definition` | Yes | Ctrl+Click / Go to Declaration |
| `textDocument/references` | Yes | Find Usages |
| `textDocument/documentSymbol` | Yes | Navigator window + breadcrumbs |
| `textDocument/formatting` | Yes | Format Document action |
| `textDocument/rangeFormatting` | Yes | Format Selection action |
| `textDocument/onTypeFormatting` | Partial | Basic support; some trigger characters may not fire |
| `textDocument/rename` | Yes | Rename refactoring dialog |
| `textDocument/codeAction` | Yes | Hint/lightbulb actions |
| `textDocument/documentHighlight` | Yes | Mark occurrences |
| `textDocument/foldingRange` | Yes | Code folding |
| `textDocument/signatureHelp` | Partial | Parameter tooltips; less polished than VS Code |
| `textDocument/inlayHint` | Yes (17+) | Inline hints (NetBeans 17+) |
| `textDocument/semanticTokens` | Yes (18+) | Semantic highlighting (NetBeans 18+) |
| `textDocument/codeLens` | Yes (18+) | Inline lens annotations (NetBeans 18+) |
| `textDocument/selectionRange` | No | Not mapped to any NetBeans action |
| `textDocument/documentLink` | Partial | Hyperlinks work but rendering varies |
| `workspace/symbol` | Yes | Go to Symbol dialog |
| `workspace/configuration` | Partial | Supported but requires explicit wiring |
| `textDocument/publishDiagnostics` | Yes | Error/warning annotations + Tasks window |

### Key Limitations

1. **No TextMate grammar support**: NetBeans does not support TextMate grammars natively.
   Syntax highlighting must use either semantic tokens (preferred) or a NetBeans
   `Lexer` implementation for fast-paint coloring during server startup.
2. **`selectionRange` not mapped**: Smart Expand/Shrink Selection from LSP is not wired
   to any NetBeans keyboard shortcut or action.
3. **`onTypeFormatting` partial**: NetBeans may not relay all trigger characters reliably;
   Enter works but `}`, `;`, `)` may require testing.
4. **`workspace/configuration` passive**: NetBeans LSP client does respond to
   `workspace/configuration` requests, but the settings must be wired explicitly
   from a NetBeans options panel.
5. **No DAP integration**: NetBeans has its own debugger infrastructure but no built-in
   DAP client. A separate NetBeans DAP client module would be needed.

---

## 1. Minimal NetBeans Module: LSP Server Registration

### How NetBeans LSP Registration Works

NetBeans discovers LSP servers through the `LanguageServerProvider` SPI. A module
registers a provider that tells NetBeans: "for files with MIME type `text/x-xtc`,
start this LSP server process."

### Implementation

#### Module Structure

```
lang/netbeans-module/
  src/main/java/org/xtclang/netbeans/
    XtcLanguageServerProvider.java
    XtcMimeResolver.java
    XtcOptionsPanel.java           (Phase 2)
  src/main/resources/
    META-INF/
      MANIFEST.MF
    org/xtclang/netbeans/
      Bundle.properties
```

#### MIME Type Registration

NetBeans identifies file types by MIME type. Register `.x` files as `text/x-xtc`:

```java
package org.xtclang.netbeans;

import org.netbeans.api.annotations.common.StaticResource;
import org.openide.filesystems.MIMEResolver;

@MIMEResolver.ExtensionRegistration(
    displayName = "#XtcResolver",
    mimeType = "text/x-xtc",
    extension = {"x"},
    position = 200
)
@MIMEResolver.NamespaceRegistration(
    displayName = "#XtcResolver",
    mimeType = "text/x-xtc",
    checkedExtension = {"x"}
)
public class XtcMimeResolver {
    @StaticResource
    public static final String ICON = "org/xtclang/netbeans/xtc-icon.png";
}
```

With a `Bundle.properties`:
```properties
XtcResolver=XTC Source Files
```

#### LSP Server Provider

```java
package org.xtclang.netbeans;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.lsp.client.spi.LanguageServerProvider;
import org.openide.util.Lookup;

@MimeRegistration(mimeType = "text/x-xtc", service = LanguageServerProvider.class)
public class XtcLanguageServerProvider implements LanguageServerProvider {

    @Override
    public LanguageServerDescription startServer(Lookup projectContext) {
        try {
            // Locate the LSP server JAR bundled with the module
            var serverJar = findServerJar();

            // Locate Java executable
            var javaHome = System.getProperty("java.home");
            var javaExe = javaHome + "/bin/java";

            // Start the LSP server process
            var process = new ProcessBuilder(javaExe, "-jar", serverJar)
                .redirectErrorStream(false)
                .start();

            InputStream in = process.getInputStream();
            OutputStream out = process.getOutputStream();

            return LanguageServerDescription.create(in, out, process);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start XTC Language Server", e);
        }
    }

    private String findServerJar() {
        // Resolution order:
        // 1. Module-bundled JAR (inside the .nbm)
        // 2. XDK_HOME/tools/lsp-server.jar
        // 3. User-configured path from Options panel

        // For bundled JAR, extract from module classloader
        var bundledUrl = getClass().getClassLoader().getResource("server/lsp-server.jar");
        if (bundledUrl != null) {
            return bundledUrl.getPath();
        }

        // Fall back to XDK_HOME
        var xdkHome = System.getenv("XDK_HOME");
        if (xdkHome != null) {
            var xdkJar = xdkHome + "/tools/lsp-server.jar";
            if (new java.io.File(xdkJar).exists()) {
                return xdkJar;
            }
        }

        throw new RuntimeException(
            "XTC LSP server JAR not found. Set XDK_HOME or install the XTC NetBeans module."
        );
    }
}
```

#### `MANIFEST.MF`

```
Manifest-Version: 1.0
OpenIDE-Module: org.xtclang.netbeans
OpenIDE-Module-Layer: org/xtclang/netbeans/layer.xml
OpenIDE-Module-Localizing-Bundle: org/xtclang/netbeans/Bundle.properties
OpenIDE-Module-Specification-Version: 0.1.0
OpenIDE-Module-Module-Dependencies: org.netbeans.modules.lsp.client > 1.0,
    org.netbeans.modules.editor.mimelookup > 1.0
```

### Priority: **Critical** -- this is the minimum viable integration

---

## 2. Syntax Highlighting Strategy

### Problem

NetBeans does not support TextMate grammars. The existing `xtc.tmLanguage.json` used
by VS Code and IntelliJ (via the TextMate plugin) cannot be reused directly.

### Solution: Semantic Tokens as Primary Highlighter

Since NetBeans 18, the LSP client supports `textDocument/semanticTokens`. Our server
advertises 23 token types and 10 modifiers. This gives us rich highlighting that is
actually better than TextMate grammar-based highlighting:

| Semantic Token Type | Visual Appearance |
|--------------------|--------------------|
| `keyword` | Bold blue |
| `type`, `class` | Teal |
| `interface` | Teal italic |
| `enum` | Teal |
| `enumMember` | Purple italic |
| `function`, `method` | Yellow |
| `parameter` | Gray |
| `variable` | Default text |
| `property` | Purple |
| `decorator` | Yellow (annotation) |
| `string` | Green |
| `number` | Blue |
| `comment` | Gray |
| `operator` | Default |
| `modifier` | Bold blue (like keyword) |

Modifiers affect styling: `declaration` = bold, `static` = italic, `deprecated` =
strikethrough, `readonly` = italic.

### Fallback: Simple NetBeans Lexer

During LSP server startup (before semantic tokens are available), files will appear
unstyled. To provide instant highlighting, implement a lightweight NetBeans `Lexer`
that tokenizes XTC keywords, strings, comments, and numbers:

```java
package org.xtclang.netbeans;

import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public enum XtcTokenId implements TokenId {
    KEYWORD("keyword"),
    IDENTIFIER("identifier"),
    STRING("string"),
    NUMBER("number"),
    COMMENT("comment"),
    OPERATOR("operator"),
    WHITESPACE("whitespace"),
    ERROR("error");

    private final String category;

    XtcTokenId(String category) {
        this.category = category;
    }

    @Override
    public String primaryCategory() {
        return category;
    }

    public static Language<XtcTokenId> language() {
        return LANGUAGE;
    }

    private static final Language<XtcTokenId> LANGUAGE =
        new LanguageHierarchy<XtcTokenId>() {
            @Override
            protected Collection<XtcTokenId> createTokenIds() {
                return Arrays.asList(XtcTokenId.values());
            }

            @Override
            protected Lexer<XtcTokenId> createLexer(LexerRestartInfo<XtcTokenId> info) {
                return new XtcLexer(info);
            }

            @Override
            protected String mimeType() {
                return "text/x-xtc";
            }
        }.language();

    private static final Set<String> KEYWORDS = Set.of(
        "module", "package", "class", "interface", "service", "mixin",
        "const", "enum", "typedef", "import", "extends", "implements",
        "incorporates", "delegates", "into", "if", "else", "for",
        "while", "do", "switch", "case", "default", "return", "break",
        "continue", "try", "catch", "finally", "using", "throw",
        "assert", "new", "this", "super", "is", "as", "construct",
        "conditional", "static", "public", "private", "protected",
        "abstract", "virtual", "override", "formal", "inject",
        "lazy", "volatile", "val", "var", "void", "Boolean", "Int",
        "String", "Null", "True", "False"
    );
}
```

This lexer provides basic keyword/string/comment/number highlighting within
milliseconds of opening a file, before the LSP server connects. Once semantic
tokens arrive, they override the lexer colors with richer type-aware highlighting.

### Priority: **High** -- users expect syntax highlighting immediately

---

## 3. Workspace/Configuration Bridge

### Problem

The LSP server requests formatting configuration via `workspace/configuration` with
section `"xtc.formatting"`. In IntelliJ, `XtcLanguageClient` responds with Code Style
settings. NetBeans needs equivalent wiring.

### Solution

The NetBeans LSP client supports `workspace/configuration` requests. To respond with
proper formatting settings, wire the NetBeans editor settings to the LSP configuration
response.

#### Option 1: Use NetBeans Editor Settings Directly

NetBeans stores editor preferences (indent size, tab/spaces) per MIME type. The LSP
client can read these and respond to `workspace/configuration`:

```java
// In a custom WorkspaceConfigurationProvider or by extending the LSP client behavior
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import javax.swing.text.Document;
import java.util.prefs.Preferences;

public class XtcConfigurationProvider {

    public Map<String, Object> getFormattingConfig() {
        var prefs = MimeLookup.getLookup(MimePath.parse("text/x-xtc"))
            .lookup(Preferences.class);

        var config = new HashMap<String, Object>();
        config.put("indentSize",
            prefs.getInt(SimpleValueNames.INDENT_SHIFT_WIDTH, 4));
        config.put("continuationIndentSize",
            prefs.getInt("continuationIndentSize", 8));
        config.put("insertSpaces",
            !prefs.getBoolean(SimpleValueNames.EXPAND_TABS, true));
        config.put("maxLineWidth",
            prefs.getInt(SimpleValueNames.TEXT_LIMIT_WIDTH, 120));
        return config;
    }
}
```

#### Option 2: Custom Options Panel

For XTC-specific settings (continuation indent, max line width), create a NetBeans
Options panel:

```java
@OptionsPanelController.SubRegistration(
    location = "Editor",
    displayName = "#XtcOptionsName",
    keywords = "#XtcOptionsKeywords",
    keywordsCategory = "Editor/XTC"
)
public class XtcOptionsPanelController extends OptionsPanelController {
    // Panel with fields for:
    // - Indent size (default: 4)
    // - Continuation indent size (default: 8)
    // - Insert spaces (default: true)
    // - Max line width (default: 120)
    // - XDK path (for server resolution)
    // - Log level
}
```

### Server-Side Changes Needed: None

The server already handles `workspace/configuration` generically. It requests section
`"xtc.formatting"` and parses `indentSize`, `continuationIndentSize`, `insertSpaces`,
and `maxLineWidth` from whatever the client returns. This protocol is IDE-agnostic.

### Priority: **Medium** -- formatting works with defaults, but user settings improve quality

---

## 4. Code Lens Support

### Problem

The server provides `textDocument/codeLens` with "Run" actions on module declarations.
NetBeans 18+ supports code lens rendering, but clicking the lens needs to invoke a
command. The command `xtc.runModule` must be handled client-side.

### Solution

Register a command handler in the NetBeans module that responds to `xtc.runModule`:

```java
// The LSP server sends code lenses with command "xtc.runModule" and argument = module URI.
// NetBeans LSP client dispatches command execution to registered handlers.

public class XtcCommandHandler {
    public void handleRunModule(String moduleUri) {
        // Open a terminal and run:
        //   java -jar <xdk>/javatools/javatools.jar run -L <xdk>/lib <module>
        // Or for Gradle projects:
        //   ./gradlew runXtc --module=<module>

        var xdkHome = resolveXdkPath();
        var terminal = IOProvider.getDefault().getIO("XTC Run", false);
        var cmd = String.format(
            "%s/bin/java -jar %s/javatools/javatools.jar run -L %s/lib %s",
            System.getProperty("java.home"), xdkHome, xdkHome, moduleUri
        );
        // Execute via ExternalExecution API
    }
}
```

NetBeans LSP client versions before 18 will simply ignore code lenses, which is
acceptable graceful degradation.

### Priority: **Medium** -- nice productivity feature, not blocking

---

## 5. File Type Icon and Project Integration

### File Icon

Register an icon for `.x` files in the layer:

```xml
<!-- layer.xml -->
<filesystem>
    <folder name="Loaders">
        <folder name="text">
            <folder name="x-xtc">
                <attr name="iconBase"
                      stringvalue="org/xtclang/netbeans/xtc-icon.png"/>
                <attr name="SystemFileSystem.localizingBundle"
                      stringvalue="org.xtclang.netbeans.Bundle"/>
            </folder>
        </folder>
    </folder>
</filesystem>
```

### Project Type Recognition

For Gradle-based XTC projects, NetBeans already supports Gradle projects natively.
No special XTC project type is needed -- the module simply adds language support
to any project containing `.x` files.

For non-Gradle XTC projects (standalone `.x` files), NetBeans can open individual
files and the LSP server handles them via the workspace folder mechanism.

### Priority: **Low** -- cosmetic improvement

---

## 6. Feature Matrix: NetBeans vs Other IDEs

This matrix shows which XTC features work in each IDE, assuming the same LSP server:

| Feature | VS Code | IntelliJ | NetBeans | Notes |
|---------|---------|----------|----------|-------|
| **Syntax highlighting (TextMate)** | Yes | Yes (TextMate plugin) | No | NetBeans needs lexer or semantic tokens |
| **Syntax highlighting (semantic tokens)** | Yes | Yes (LSP4IJ) | Yes (18+) | Recommended for NetBeans |
| **Hover / Quick Doc** | Yes | Yes | Yes | Standard LSP |
| **Code completion** | Yes | Yes | Yes | Standard LSP |
| **Go-to-definition** | Yes | Yes | Yes | Standard LSP |
| **Find references** | Yes | Yes | Yes | Standard LSP |
| **Document symbols / outline** | Yes | Yes | Yes | Standard LSP |
| **Document formatting** | Yes | Yes | Yes | Standard LSP |
| **Range formatting** | Yes | Yes | Yes | Standard LSP |
| **On-type formatting** | Yes | Yes | Partial | Enter works; `}`,`;`,`)` needs testing |
| **Rename** | Yes | Yes | Yes | Standard LSP with prepare |
| **Code actions** | Yes | Yes | Yes | Standard LSP |
| **Document highlight** | Yes | Yes | Yes | Standard LSP |
| **Selection range** | Yes | Yes | No | Not mapped in NetBeans |
| **Folding** | Yes | Yes | Yes | Standard LSP |
| **Signature help** | Yes | Yes | Partial | Basic tooltip support |
| **Inlay hints** | Yes | Yes | Yes (17+) | Requires NetBeans 17+ |
| **Document links** | Yes | Yes | Partial | Hyperlink support varies |
| **Code lens** | Yes | Yes | Yes (18+) | Requires NetBeans 18+ |
| **Workspace symbols** | Yes | Yes | Yes | Standard LSP |
| **Diagnostics** | Yes | Yes | Yes | Standard LSP |
| **workspace/configuration** | Yes | Yes | Partial | Needs explicit wiring |
| **Snippets / live templates** | Yes (JSON) | Yes (XML) | Manual | NetBeans code templates via Options |
| **Comment toggling (Ctrl+/)** | Auto | Plugin | Auto | NetBeans infers from `//` in files |
| **Run configuration** | Task provider | Plugin | Manual | Could add NetBeans action |
| **Debug (DAP)** | Planned | Planned | No | NetBeans has no standard DAP client |
| **New File templates** | N/A | Plugin | Manual | Could add NetBeans template |

### Key Takeaway

NetBeans gets approximately **85% of the XTC development experience** purely from the
LSP server with zero custom code beyond the server registration. The remaining 15% is:
- Fast-paint syntax highlighting (needs lexer or wait for semantic tokens)
- Code snippets (manual setup via NetBeans code templates)
- Run/debug integration (needs custom actions or manual terminal use)
- Smart selection (not supported by NetBeans LSP client)

---

## 7. Server-Side Changes for NetBeans Compatibility

### 7a. On-Type Formatting Trigger Robustness

**Issue**: NetBeans may not send `textDocument/onTypeFormatting` for all trigger
characters. The server should gracefully handle cases where only `\n` is triggered.

**Change needed**: None. The server already handles missing triggers gracefully --
the trigger character is used to decide what formatting to apply, and `\n` alone
covers the most important case (auto-indent after open brace).

### 7b. Semantic Token Color Mapping Documentation

**Issue**: NetBeans maps LSP semantic token types to its own color categories. The
mapping may not match VS Code's defaults.

**Recommendation**: Document the expected color mapping so users can customize their
NetBeans color scheme. Our semantic token types are all from the standard LSP set,
so NetBeans should handle them correctly by default.

### 7c. Code Lens Command Registration

**Issue**: The server sends code lenses with command `"xtc.runModule"`. NetBeans
needs to know how to handle this command. If no handler is registered, the lens
text will appear but clicking it will do nothing.

**Change needed**: None on the server side. The NetBeans module must register a
command handler (see Section 4).

### Server-Side Changes Needed: None

The LSP server is fully protocol-compliant and does not use any VS Code or IntelliJ
specific extensions. All features use standard LSP protocol methods.

---

## 8. Packaging and Distribution

### NetBeans Module (.nbm) Packaging

NetBeans plugins are distributed as `.nbm` files (NetBeans Module archives).

#### Build Setup

Add a new Gradle subproject:

```
lang/netbeans-module/
  build.gradle.kts
  src/main/java/...
  src/main/resources/...
```

The build should:
1. Compile the Java sources
2. Copy `lsp-server.jar` (fat JAR) into the module's `server/` resource directory
3. Package as `.nbm` using the NetBeans Ant or Gradle tooling

```kotlin
// lang/netbeans-module/build.gradle.kts (sketch)
plugins {
    java
}

dependencies {
    // NetBeans Platform APIs (provided by the IDE at runtime)
    compileOnly("org.netbeans.api:org-netbeans-modules-editor-mimelookup:RELEASE220")
    compileOnly("org.netbeans.api:org-netbeans-modules-lsp-client:RELEASE220")
    compileOnly("org.netbeans.api:org-openide-util:RELEASE220")
    compileOnly("org.netbeans.api:org-openide-util-lookup:RELEASE220")
}

// Copy LSP server JAR into module resources
val copyLspServer by tasks.registering(Copy::class) {
    from(project(":lang:lsp-server").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("resources/main/server"))
    rename { "lsp-server.jar" }
}

tasks.named("processResources") {
    dependsOn(copyLspServer)
}
```

#### Distribution Options

| Method | Pros | Cons |
|--------|------|------|
| **Apache NetBeans Plugin Portal** | Discoverable via Tools > Plugins | Requires Apache account, review process |
| **Update center URL** | Self-hosted, immediate publishing | Users must add URL manually |
| **GitHub releases (.nbm)** | Simple, version-controlled | Manual install only |
| **Bundled with XDK** | Zero-install for XDK users | Larger XDK distribution |

**Recommended**: Start with GitHub releases, then publish to the NetBeans Plugin Portal
once the integration is stable.

---

## 9. Implementation Order

| Phase | Items | Effort | Impact |
|-------|-------|--------|--------|
| **Phase 1: Bootstrap** | MIME registration + LSP server provider + module packaging | Small | Critical -- makes everything else work |
| **Phase 2: Highlighting** | Simple lexer for fast-paint + verify semantic tokens work | Small-Medium | High -- usable syntax coloring |
| **Phase 3: Settings** | Options panel for formatting config + workspace/configuration wiring | Small | Medium -- formatting quality |
| **Phase 4: Polish** | File icon, code lens command handler, code templates | Small | Medium -- professional feel |
| **Phase 5: Run** | Run action for XTC modules (terminal-based) | Medium | Medium -- workflow integration |

**Total estimated effort**: 3-5 developer-days for Phase 1-3 (usable integration).
Phase 4-5 add another 2-3 days for polish.

### Minimum Viable Integration (Phase 1 only)

With just the MIME resolver and LSP server provider (~100 lines of Java + manifest),
NetBeans users get:

- All LSP features (hover, completion, definition, references, symbols, formatting,
  rename, code actions, folding, diagnostics, signature help)
- Semantic token highlighting (NetBeans 18+)
- Inlay hints (NetBeans 17+)
- Code lenses (NetBeans 18+, display only -- no click action)
- Workspace symbol search

This is a remarkably small amount of code for a near-complete IDE experience.

---

## Key Files Reference

| Component | Path |
|-----------|------|
| LSP server (shared) | `lang/lsp-server/` |
| LSP server entry point | `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt` |
| Semantic token legend | `lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/SemanticTokenEncoder.kt` |
| TextMate grammar (reference) | `lang/generated-examples/xtc.tmLanguage.json` |
| Language configuration (reference) | `lang/generated-examples/language-configuration.json` |
| IntelliJ plugin (comparison) | `lang/intellij-plugin/` |
| VS Code extension (comparison) | `lang/vscode-extension/` |
| DAP server (future) | `lang/dap-server/` |
| XDK distribution layout | `xdk/build/install/xdk/{bin,javatools,lib}/` |
