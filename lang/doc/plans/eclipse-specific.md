# Eclipse IDE Integration Plan

> **Created**: 2026-04-03
> **Status**: Planning
> **Scope**: Bringing XTC language support to Eclipse IDE via LSP4E and TM4E

## Context

Eclipse has two key projects that make LSP-based language support practical:

- **Eclipse LSP4E** (Language Server Protocol for Eclipse): An Eclipse plugin that
  acts as a generic LSP client, mapping LSP responses to Eclipse editor features.
- **Eclipse TM4E** (TextMate for Eclipse): Adds TextMate grammar support to Eclipse,
  providing syntax highlighting from `.tmLanguage.json` files.

Together, these provide a near-complete IDE experience with minimal Eclipse-specific
code. The XTC LSP server is fully protocol-compliant and uses no IDE-specific
extensions, so Eclipse integration should be straightforward.

### Guiding Principle

**Keep logic in Kotlin, minimize Eclipse plugin code.** The LSP server (Kotlin) is
shared across all editors. Every feature implemented there benefits IntelliJ, VS Code,
Eclipse, NetBeans, and any other LSP client. The Eclipse plugin should be a thin shell
-- just enough to register the content type, wire the LSP server, provide TextMate
grammar support, and expose settings.

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

## Eclipse LSP4E Support Overview

### What LSP4E Provides

LSP4E maps LSP protocol responses to Eclipse's editor infrastructure. When a content
type is associated with an LSP server, Eclipse automatically provides:

| LSP Feature | Eclipse UI | LSP4E Support | Notes |
|-------------|-----------|---------------|-------|
| `textDocument/hover` | Yes | Yes | Tooltip on mouse-over |
| `textDocument/completion` | Yes | Yes | Content assist popup (Ctrl+Space) |
| `textDocument/definition` | Yes | Yes | Open Declaration (F3 / Ctrl+Click) |
| `textDocument/references` | Yes | Yes | Search > References |
| `textDocument/documentSymbol` | Yes | Yes | Outline view + Quick Outline (Ctrl+O) |
| `textDocument/formatting` | Yes | Yes | Format (Ctrl+Shift+F) |
| `textDocument/rangeFormatting` | Yes | Yes | Format selected text |
| `textDocument/onTypeFormatting` | Yes | Yes | Auto-indent on trigger characters |
| `textDocument/rename` | Yes | Yes | Rename refactoring (Alt+Shift+R) |
| `textDocument/codeAction` | Yes | Yes | Quick Fix (Ctrl+1) |
| `textDocument/documentHighlight` | Yes | Yes | Mark Occurrences |
| `textDocument/selectionRange` | No | No | Not mapped to Eclipse actions |
| `textDocument/foldingRange` | Yes | Yes | Code folding |
| `textDocument/signatureHelp` | Yes | Yes | Context info during method calls |
| `textDocument/inlayHint` | Yes | Yes | Inline hints (Eclipse 4.27+) |
| `textDocument/documentLink` | Yes | Yes | Hyperlink navigation (Ctrl+Click) |
| `textDocument/semanticTokens` | Yes | Yes | Semantic highlighting (Eclipse 4.25+) |
| `textDocument/codeLens` | Yes | Yes | Inline annotations above declarations |
| `workspace/symbol` | Yes | Yes | Open Type (Ctrl+Shift+T) equivalent |
| `textDocument/publishDiagnostics` | Yes | Yes | Problems view + error annotations |
| `workspace/configuration` | Partial | Yes | Supported; needs explicit settings wiring |

### Key Advantages of Eclipse + LSP4E

1. **TextMate support via TM4E**: Eclipse TM4E supports `.tmLanguage.json` files,
   so our existing TextMate grammar works as-is for fast-paint highlighting.
2. **Mature LSP4E**: LSP4E has been developed since 2017 and handles most LSP
   features, including newer ones like semantic tokens and inlay hints.
3. **Rich extension point system**: Eclipse's plugin architecture makes it easy to
   add content types, launch configurations, and custom views.

### Key Limitations

1. **`selectionRange` not mapped**: Eclipse LSP4E does not map smart selection to
   any keyboard shortcut or action.
2. **`workspace/configuration` passive**: LSP4E responds to `workspace/configuration`
   requests, but settings must be explicitly wired from Eclipse preferences.
3. **Code lens command execution**: Code lens display works, but command execution
   requires an `IHandler` registration for the command ID.
4. **No built-in DAP via LSP4E**: Eclipse has its own Debug framework. DAP integration
   would need Eclipse LSP4E's debug support or a custom launch delegate.

---

## 1. Content Type and LSP Server Registration

### Content Type Definition

Eclipse identifies file types via content types. Register `.x` files as `org.xtclang.contenttype.xtc`:

#### `plugin.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
    <!-- Content type for .x files -->
    <extension point="org.eclipse.core.contenttype.contentTypes">
        <content-type
            id="org.xtclang.contenttype.xtc"
            name="XTC Source File"
            base-type="org.eclipse.core.runtime.text"
            file-extensions="x"
            priority="normal">
        </content-type>
    </extension>

    <!-- Associate content type with the generic editor (LSP4E uses this) -->
    <extension point="org.eclipse.ui.editors">
        <editorContentTypeBinding
            contentTypeId="org.xtclang.contenttype.xtc"
            editorId="org.eclipse.ui.genericeditor.GenericEditor">
        </editorContentTypeBinding>
    </extension>

    <!-- File icon for .x files -->
    <extension point="org.eclipse.ui.ide.contentTypeImages">
        <image
            contentTypeId="org.xtclang.contenttype.xtc"
            icon="icons/xtc-icon.png">
        </image>
    </extension>
</plugin>
```

### LSP Server Connection

LSP4E discovers language servers through the `org.eclipse.lsp4e.languageServer`
extension point. Register the XTC server:

```xml
    <!-- LSP server registration via LSP4E -->
    <extension point="org.eclipse.lsp4e.languageServer">
        <server
            id="org.xtclang.lsp.server"
            label="XTC Language Server"
            class="org.xtclang.eclipse.XtcLanguageServerStreamProvider">
        </server>
        <contentTypeMapping
            id="org.xtclang.lsp.mapping"
            contentType="org.xtclang.contenttype.xtc"
            serverId="org.xtclang.lsp.server">
        </contentTypeMapping>
    </extension>
```

### Stream Provider Implementation

LSP4E requires a `StreamConnectionProvider` that starts the server process and
provides stdin/stdout streams:

```java
package org.xtclang.eclipse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;

public class XtcLanguageServerStreamProvider extends ProcessStreamConnectionProvider {

    public XtcLanguageServerStreamProvider() {
        var commands = new ArrayList<String>();
        commands.add(findJavaExecutable());
        commands.add("-jar");
        commands.add(findServerJar());
        setCommands(commands);

        // Working directory for the server process
        setWorkingDirectory(System.getProperty("user.dir"));
    }

    private String findJavaExecutable() {
        var javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            return javaHome + File.separator + "bin" + File.separator + "java";
        }
        return "java";
    }

    private String findServerJar() {
        // Resolution order:
        // 1. Plugin-bundled JAR
        // 2. XDK_HOME environment variable
        // 3. Eclipse preference setting

        // Check plugin bundle location
        var bundle = org.eclipse.core.runtime.Platform.getBundle("org.xtclang.eclipse");
        if (bundle != null) {
            var url = bundle.getEntry("server/lsp-server.jar");
            if (url != null) {
                try {
                    var fileUrl = org.eclipse.core.runtime.FileLocator.toFileURL(url);
                    return fileUrl.getPath();
                } catch (IOException e) {
                    // Fall through to next strategy
                }
            }
        }

        // Check XDK_HOME
        var xdkHome = System.getenv("XDK_HOME");
        if (xdkHome != null) {
            var jarPath = xdkHome + "/tools/lsp-server.jar";
            if (new File(jarPath).exists()) {
                return jarPath;
            }
        }

        // Check Eclipse preferences
        var prefs = org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE
            .getNode("org.xtclang.eclipse");
        var configuredPath = prefs.get("serverJarPath", null);
        if (configuredPath != null && new File(configuredPath).exists()) {
            return configuredPath;
        }

        throw new RuntimeException(
            "XTC LSP server JAR not found. Set XDK_HOME or configure in " +
            "Window > Preferences > XTC > Language Server."
        );
    }

    @Override
    public String toString() {
        return "XTC Language Server: " + super.toString();
    }
}
```

### Priority: **Critical** -- this is the minimum viable integration

---

## 2. Syntax Highlighting with TM4E

### TextMate Grammar Registration

Eclipse TM4E supports `.tmLanguage.json` files directly. Our existing TextMate grammar
(`xtc.tmLanguage.json`) works without modification:

```xml
    <!-- TextMate grammar via TM4E -->
    <extension point="org.eclipse.tm4e.registry.grammars">
        <grammar
            scopeName="source.xtc"
            path="syntaxes/xtc.tmLanguage.json">
        </grammar>
        <scopeNameContentTypeBinding
            scopeName="source.xtc"
            contentTypeId="org.xtclang.contenttype.xtc">
        </scopeNameContentTypeBinding>
    </extension>

    <!-- Language configuration (brackets, comments, auto-close) -->
    <extension point="org.eclipse.tm4e.languageconfiguration.languageConfigurations">
        <languageConfiguration
            contentTypeId="org.xtclang.contenttype.xtc"
            path="language-configuration.json">
        </languageConfiguration>
    </extension>
```

### How TextMate + Semantic Tokens Interact

TM4E provides instant syntax highlighting when a file is opened (fast-paint).
Once the LSP server connects and provides semantic tokens, LSP4E overlays richer
token-level coloring on top of the TextMate base:

1. **File opens** -> TM4E highlights keywords, strings, comments (instant)
2. **LSP server connects** -> Semantic tokens override with type-aware coloring
3. **User edits** -> TM4E updates instantly; semantic tokens update after a short delay

This is the same layering strategy used by VS Code and IntelliJ (via the TextMate plugin).

### Files to Bundle

Copy these from the build output into the Eclipse plugin's resources:

| Source | Plugin Location | Purpose |
|--------|-----------------|---------|
| `lang/generated-examples/xtc.tmLanguage.json` | `syntaxes/xtc.tmLanguage.json` | TextMate grammar |
| `lang/generated-examples/language-configuration.json` | `language-configuration.json` | Brackets, comments, auto-close |

### Priority: **High** -- users expect instant syntax highlighting

---

## 3. Workspace/Configuration Bridge

### Problem

The LSP server requests formatting settings via `workspace/configuration` (section
`"xtc.formatting"`). LSP4E can respond to these requests, but the settings must be
backed by Eclipse preferences.

### Solution

#### Preference Page

Create an Eclipse preference page under Window > Preferences > XTC:

```java
package org.xtclang.eclipse;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;

public class XtcPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    public XtcPreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("XTC Language Settings");
    }

    @Override
    public void createFieldEditors() {
        // Formatting settings
        addField(new IntegerFieldEditor(
            "xtc.formatting.indentSize",
            "Indent size:",
            getFieldEditorParent()));
        addField(new IntegerFieldEditor(
            "xtc.formatting.continuationIndentSize",
            "Continuation indent size:",
            getFieldEditorParent()));
        addField(new BooleanFieldEditor(
            "xtc.formatting.insertSpaces",
            "Insert spaces (not tabs)",
            getFieldEditorParent()));
        addField(new IntegerFieldEditor(
            "xtc.formatting.maxLineWidth",
            "Maximum line width:",
            getFieldEditorParent()));

        // Server settings
        addField(new StringFieldEditor(
            "xtc.xdkPath",
            "XDK installation path:",
            getFieldEditorParent()));
        addField(new StringFieldEditor(
            "xtc.serverJarPath",
            "LSP server JAR path (override):",
            getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing to initialize
    }
}
```

Register in `plugin.xml`:

```xml
    <extension point="org.eclipse.ui.preferencePages">
        <page
            id="org.xtclang.eclipse.preferences"
            name="XTC"
            class="org.xtclang.eclipse.XtcPreferencePage">
        </page>
    </extension>

    <extension point="org.eclipse.core.runtime.preferences">
        <initializer class="org.xtclang.eclipse.XtcPreferenceInitializer"/>
    </extension>
```

#### Preference Initializer

```java
package org.xtclang.eclipse;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class XtcPreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault("xtc.formatting.indentSize", 4);
        store.setDefault("xtc.formatting.continuationIndentSize", 8);
        store.setDefault("xtc.formatting.insertSpaces", true);
        store.setDefault("xtc.formatting.maxLineWidth", 120);
        store.setDefault("xtc.xdkPath", "");
        store.setDefault("xtc.serverJarPath", "");
    }
}
```

#### Wiring Preferences to workspace/configuration

LSP4E's `workspace/configuration` handler reads from Eclipse's workspace settings.
To bridge our preference page values into the LSP response, implement an
`IPreferenceChangeListener` that updates the workspace settings whenever the user
changes XTC preferences, or override the configuration handler in the stream
provider:

```java
// In the stream provider or a dedicated initializer, register a listener
// that converts Eclipse preferences to the format the LSP server expects.
// LSP4E responds to workspace/configuration requests with values from the
// workspace resource settings or a custom InitializationOptions object.

// Alternatively, pass settings via initialization options:
@Override
public Object getInitializationOptions(URI rootUri) {
    var store = Activator.getDefault().getPreferenceStore();
    var settings = new com.google.gson.JsonObject();

    var formatting = new com.google.gson.JsonObject();
    formatting.addProperty("indentSize", store.getInt("xtc.formatting.indentSize"));
    formatting.addProperty("continuationIndentSize",
        store.getInt("xtc.formatting.continuationIndentSize"));
    formatting.addProperty("insertSpaces",
        store.getBoolean("xtc.formatting.insertSpaces"));
    formatting.addProperty("maxLineWidth",
        store.getInt("xtc.formatting.maxLineWidth"));
    settings.add("xtc.formatting", formatting);

    return settings;
}
```

### Server-Side Impact

If Eclipse passes settings via `initializationOptions` rather than `workspace/configuration`,
the server would need a small change to read formatting config from init options as a
fallback. Currently the server only reads from `workspace/configuration`. A minimal
server-side change:

```kotlin
// In XtcLanguageServer.initialize(), after building capabilities:
val initOptions = params.initializationOptions
if (initOptions is Map<*, *>) {
    val formatting = initOptions["xtc.formatting"] as? Map<*, *>
    if (formatting != null) {
        editorFormattingConfig = FormattingConfig(
            indentSize = (formatting["indentSize"] as? Number)?.toInt() ?: 4,
            continuationIndentSize = (formatting["continuationIndentSize"] as? Number)?.toInt() ?: 8,
            insertSpaces = formatting["insertSpaces"] as? Boolean ?: true,
            maxLineWidth = (formatting["maxLineWidth"] as? Number)?.toInt() ?: 120,
        )
        adapter.editorFormattingConfig = editorFormattingConfig
        logger.info("initialize: formatting config from initializationOptions: {}", editorFormattingConfig)
    }
}
```

This change is backward-compatible and benefits any LSP client that prefers
`initializationOptions` over `workspace/configuration`.

### Priority: **Medium** -- formatting works with defaults, but custom settings improve quality

---

## 4. Code Lens Command Handling

### Problem

The server provides code lenses with command `"xtc.runModule"`. LSP4E renders the
lens text, but clicking it requires Eclipse to know how to handle the command.

### Solution

Register a command handler via Eclipse's Commands framework:

```xml
    <extension point="org.eclipse.ui.commands">
        <command
            id="org.xtclang.eclipse.runModule"
            name="Run XTC Module">
        </command>
    </extension>

    <extension point="org.eclipse.ui.handlers">
        <handler
            commandId="org.xtclang.eclipse.runModule"
            class="org.xtclang.eclipse.RunModuleHandler">
        </handler>
    </extension>
```

```java
package org.xtclang.eclipse;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class RunModuleHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // Extract module URI from command arguments
        // (passed by LSP4E from the code lens command arguments)
        var moduleUri = event.getParameter("uri");
        if (moduleUri == null) {
            return null;
        }

        // Launch in Eclipse console
        var xdkHome = resolveXdkPath();
        var javaHome = System.getProperty("java.home");
        var command = String.format(
            "%s/bin/java -jar %s/javatools/javatools.jar run -L %s/lib %s",
            javaHome, xdkHome, xdkHome, moduleUri
        );

        // Use Eclipse ExternalToolBuilder or ProcessBuilder
        // to launch in an Eclipse Console view
        return null;
    }

    private String resolveXdkPath() {
        var store = Activator.getDefault().getPreferenceStore();
        var path = store.getString("xtc.xdkPath");
        if (path != null && !path.isEmpty()) {
            return path;
        }
        var envPath = System.getenv("XDK_HOME");
        if (envPath != null) {
            return envPath;
        }
        throw new RuntimeException("XDK path not configured. Set in Window > Preferences > XTC.");
    }
}
```

**Note**: LSP4E maps code lens command IDs to Eclipse command IDs. The server's
command `"xtc.runModule"` needs to match the registered Eclipse command ID, or
LSP4E needs a mapping layer. Check LSP4E documentation for the exact mapping
convention -- it may require the command ID to match exactly or use a prefix.

### Priority: **Medium** -- nice productivity feature, not blocking

---

## 5. Launch Configuration (Run/Debug)

### Run Configuration for XTC Modules

Eclipse uses Launch Configurations for running and debugging. Create a custom launch
configuration type for XTC modules:

```xml
    <extension point="org.eclipse.debug.core.launchConfigurationTypes">
        <launchConfigurationType
            id="org.xtclang.eclipse.launchType"
            name="XTC Application"
            delegate="org.xtclang.eclipse.XtcLaunchDelegate"
            modes="run,debug"
            sourceLocatorId="org.eclipse.debug.core.sourceLocator"
            sourcePathComputerId="org.eclipse.debug.core.sourcePathComputer">
        </launchConfigurationType>
    </extension>

    <extension point="org.eclipse.debug.ui.launchConfigurationTypeImages">
        <launchConfigurationTypeImage
            configTypeID="org.xtclang.eclipse.launchType"
            icon="icons/xtc-run.png">
        </launchConfigurationTypeImage>
    </extension>

    <extension point="org.eclipse.debug.ui.launchConfigurationTabGroups">
        <launchConfigurationTabGroup
            id="org.xtclang.eclipse.launchTabGroup"
            type="org.xtclang.eclipse.launchType"
            class="org.xtclang.eclipse.XtcLaunchTabGroup">
        </launchConfigurationTabGroup>
    </extension>
```

```java
package org.xtclang.eclipse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;

public class XtcLaunchDelegate implements ILaunchConfigurationDelegate {

    @Override
    public void launch(ILaunchConfiguration config, String mode,
            ILaunch launch, IProgressMonitor monitor) throws CoreException {

        var moduleName = config.getAttribute("xtc.module", "");
        var methodName = config.getAttribute("xtc.method", "run");
        var xdkPath = config.getAttribute("xtc.xdkPath", resolveXdkPath());

        var javaHome = System.getProperty("java.home");
        var cmdLine = new String[] {
            javaHome + "/bin/java",
            "-jar", xdkPath + "/javatools/javatools.jar",
            "run",
            "-L", xdkPath + "/lib",
            "-M", methodName,
            moduleName
        };

        var process = Runtime.getRuntime().exec(cmdLine);
        // Wire process streams to Eclipse Console view
    }

    private String resolveXdkPath() {
        // Same resolution as in RunModuleHandler
        var store = Activator.getDefault().getPreferenceStore();
        var path = store.getString("xtc.xdkPath");
        if (path != null && !path.isEmpty()) {
            return path;
        }
        return System.getenv("XDK_HOME");
    }
}
```

### Priority: **Medium** -- useful but users can run from terminal initially

---

## 6. Feature Matrix: Eclipse vs Other IDEs

| Feature | VS Code | IntelliJ | Eclipse | NetBeans | Notes |
|---------|---------|----------|---------|----------|-------|
| **TextMate highlighting** | Yes | Yes | Yes (TM4E) | No | Eclipse uses TM4E plugin |
| **Semantic tokens** | Yes | Yes | Yes (4.25+) | Yes (18+) | Standard LSP |
| **Hover / Quick Doc** | Yes | Yes | Yes | Yes | Standard LSP |
| **Code completion** | Yes | Yes | Yes | Yes | Standard LSP |
| **Go-to-definition** | Yes | Yes | Yes | Yes | Standard LSP |
| **Find references** | Yes | Yes | Yes | Yes | Standard LSP |
| **Document symbols / outline** | Yes | Yes | Yes | Yes | Standard LSP |
| **Document formatting** | Yes | Yes | Yes | Yes | Standard LSP |
| **Range formatting** | Yes | Yes | Yes | Yes | Standard LSP |
| **On-type formatting** | Yes | Yes | Yes | Partial | Eclipse handles all triggers |
| **Rename** | Yes | Yes | Yes | Yes | Standard LSP |
| **Code actions** | Yes | Yes | Yes | Yes | Standard LSP |
| **Document highlight** | Yes | Yes | Yes | Yes | Standard LSP |
| **Selection range** | Yes | Yes | No | No | Not mapped in Eclipse or NetBeans |
| **Folding** | Yes | Yes | Yes | Yes | Standard LSP |
| **Signature help** | Yes | Yes | Yes | Partial | Eclipse has good support |
| **Inlay hints** | Yes | Yes | Yes (4.27+) | Yes (17+) | Newer Eclipse versions |
| **Document links** | Yes | Yes | Yes | Partial | Standard LSP |
| **Code lens** | Yes | Yes | Yes | Yes (18+) | Standard LSP |
| **Workspace symbols** | Yes | Yes | Yes | Yes | Standard LSP |
| **Diagnostics** | Yes | Yes | Yes | Yes | Standard LSP |
| **workspace/configuration** | Yes | Yes | Partial | Partial | Needs explicit wiring |
| **Snippets** | Yes (JSON) | Yes (XML) | No* | Manual | *Eclipse templates are separate |
| **Comment toggling** | Auto | Plugin | Auto** | Auto | **TM4E handles this |
| **Run configuration** | Task provider | Plugin | Plugin | Manual | Eclipse launch config |
| **Debug (DAP)** | Planned | Planned | Possible | No | Eclipse has DAP support in LSP4E |
| **New File wizard** | N/A | Plugin | Plugin | Manual | Eclipse wizard framework |

### Key Takeaway for Eclipse

Eclipse gets approximately **90% of the XTC development experience** from LSP4E + TM4E
with minimal plugin code. The key advantage over NetBeans is TM4E support, which means
our existing TextMate grammar works as-is for fast-paint highlighting. The main gaps are:

- Code snippets (Eclipse has its own template system, not wired to LSP snippets)
- Smart selection (`selectionRange`) -- not mapped
- Run/debug integration (needs launch configuration plugin)

---

## 7. Server-Side Changes for Eclipse Compatibility

### 7a. Initialization Options Fallback (Recommended)

As described in Section 3, adding a fallback to read formatting config from
`initializationOptions` benefits Eclipse and any client that struggles with
`workspace/configuration`. This is a small, backward-compatible change:

**File**: `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt`

```kotlin
// Add to initialize() method, after buildServerCapabilities():
@Suppress("UNCHECKED_CAST")
val initOptions = params.initializationOptions
if (initOptions is Map<*, *>) {
    val formatting = initOptions["xtc.formatting"] as? Map<*, *>
    if (formatting != null) {
        val config = FormattingConfig(
            indentSize = (formatting["indentSize"] as? Number)?.toInt() ?: 4,
            continuationIndentSize = (formatting["continuationIndentSize"] as? Number)?.toInt() ?: 8,
            insertSpaces = formatting["insertSpaces"] as? Boolean ?: true,
            maxLineWidth = (formatting["maxLineWidth"] as? Number)?.toInt() ?: 120,
        )
        editorFormattingConfig = config
        adapter.editorFormattingConfig = config
        logger.info("initialize: formatting config from initializationOptions: {}", config)
    }
}
```

This runs before the `workspace/configuration` request, so if both are provided,
`workspace/configuration` will override `initializationOptions` (which is the
correct precedence -- runtime settings override initial settings).

### 7b. No Other Server-Side Changes Needed

The LSP server uses standard protocol methods throughout. No Eclipse-specific
protocol extensions or workarounds are required. The server's behavior with
Eclipse LSP4E has been validated at the protocol level:

- Full text sync (`TextDocumentSyncKind.Full`) -- simplest and most compatible
- Standard capability advertisement -- no experimental capabilities used
- Standard semantic token types from the LSP specification
- Standard code action kinds and code lens commands

---

## 8. Packaging and Distribution

### Eclipse Plugin Structure

Eclipse plugins are packaged as OSGi bundles distributed via p2 update sites.

#### Plugin Project Layout

```
lang/eclipse-plugin/
  META-INF/
    MANIFEST.MF
  plugin.xml
  icons/
    xtc-icon.png
    xtc-run.png
  syntaxes/
    xtc.tmLanguage.json
  language-configuration.json
  server/
    lsp-server.jar               (copied from lsp-server build)
  src/
    org/xtclang/eclipse/
      Activator.java
      XtcLanguageServerStreamProvider.java
      XtcPreferencePage.java
      XtcPreferenceInitializer.java
      RunModuleHandler.java       (Phase 2)
      XtcLaunchDelegate.java      (Phase 3)
      XtcLaunchTabGroup.java      (Phase 3)
  build.gradle.kts
```

#### MANIFEST.MF

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: XTC Language Support
Bundle-SymbolicName: org.xtclang.eclipse;singleton:=true
Bundle-Version: 0.1.0.qualifier
Bundle-Activator: org.xtclang.eclipse.Activator
Bundle-Vendor: XTC Language
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.lsp4e;bundle-version="0.15.0",
 org.eclipse.tm4e.registry;bundle-version="0.6.0",
 org.eclipse.tm4e.languageconfiguration;bundle-version="0.6.0",
 org.eclipse.debug.core,
 org.eclipse.debug.ui,
 org.eclipse.jface.text,
 org.eclipse.core.contenttype
Bundle-RequiredExecutionEnvironment: JavaSE-21
Bundle-ActivationPolicy: lazy
Export-Package: org.xtclang.eclipse
```

#### Activator

```java
package org.xtclang.eclipse;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "org.xtclang.eclipse";
    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }
}
```

#### Build with Gradle

Building Eclipse plugins with Gradle requires either the `bnd-platform` plugin or
Eclipse Tycho (Maven-based). For consistency with the rest of the XVM project
(Gradle-based), use `bnd-platform` or package the JAR manually:

```kotlin
// lang/eclipse-plugin/build.gradle.kts (sketch)
plugins {
    java
}

dependencies {
    // Eclipse Platform APIs (provided at runtime, compileOnly)
    compileOnly("org.eclipse.platform:org.eclipse.core.runtime:3.31.0")
    compileOnly("org.eclipse.platform:org.eclipse.ui:3.205.0")
    compileOnly("org.eclipse.platform:org.eclipse.jface:3.34.0")
    compileOnly("org.eclipse.platform:org.eclipse.debug.core:3.21.0")
    compileOnly("org.eclipse.platform:org.eclipse.debug.ui:3.18.0")
    // LSP4E and TM4E (provided at runtime)
    compileOnly("org.eclipse.lsp4e:org.eclipse.lsp4e:0.24.0")
    compileOnly("org.eclipse.tm4e:org.eclipse.tm4e.registry:0.10.0")
    compileOnly("org.eclipse.tm4e:org.eclipse.tm4e.languageconfiguration:0.10.0")
}

// Copy LSP server fat JAR into plugin
val copyLspServer by tasks.registering(Copy::class) {
    from(project(":lang:lsp-server").tasks.named("shadowJar"))
    into(layout.buildDirectory.dir("resources/main/server"))
    rename { "lsp-server.jar" }
}

// Copy TextMate grammar and language configuration
val copyGrammar by tasks.registering(Copy::class) {
    from(project(":lang:dsl").layout.buildDirectory.dir("generated"))
    include("xtc.tmLanguage.json", "language-configuration.json")
    into(layout.buildDirectory.dir("resources/main/syntaxes"))
}

tasks.named("processResources") {
    dependsOn(copyLspServer, copyGrammar)
}
```

#### Distribution Options

| Method | Pros | Cons |
|--------|------|------|
| **Eclipse Marketplace** | Discoverable via Help > Eclipse Marketplace | Requires EF account, review |
| **p2 update site** | Standard Eclipse install, auto-updates | Need to host the p2 repo |
| **GitHub releases (.jar)** | Simple | Manual install via dropins/ folder |
| **Bundled with XDK** | Zero-install for XDK users | Larger distribution |

**Recommended**: Start with GitHub releases (users drop the JAR into Eclipse's
`dropins/` folder), then set up a p2 update site for proper version management.

---

## 9. Eclipse-Specific Quirks and Workarounds

### 9a. Content Assist Trigger Characters

Eclipse's content assist is triggered by Ctrl+Space by default. LSP4E forwards
trigger characters (`.`, `:`, `<`) but Eclipse may require explicit configuration
in the preferences to enable auto-activation. Users may need to add `.:<` to
Window > Preferences > General > Text Editors > Content Assist > Auto activation
triggers.

**Workaround**: Document this in the plugin's installation instructions, or register
an auto-activation trigger programmatically via the Eclipse preference API.

### 9b. On-Type Formatting Delay

Eclipse may introduce a slight delay before forwarding on-type formatting requests.
This can make auto-indent feel sluggish compared to VS Code. There is no workaround
beyond ensuring the server responds quickly (which it does -- tree-sitter formatting
is sub-millisecond).

### 9c. Semantic Token Theme Mapping

Eclipse's color theme may not match VS Code's defaults for semantic token types. For
example, `decorator` tokens may not have a distinct color in Eclipse's default theme.

**Workaround**: Provide a recommended Eclipse color theme configuration or document
which colors to customize in Window > Preferences > General > Appearance > Colors and
Fonts > Semantic Highlighting.

### 9d. Multiple Workspace Folders

Eclipse workspaces can contain multiple projects. LSP4E sends all project roots as
workspace folders. The XTC LSP server handles multiple workspace folders correctly
(it indexes all of them), so this should work out of the box. However, starting one
LSP server instance per Eclipse project (rather than per workspace) is the default
LSP4E behavior -- verify whether our server handles multiple instances gracefully.

### 9e. File Encoding

Eclipse may use platform-specific file encoding by default (e.g., Cp1252 on Windows).
XTC files should be UTF-8. The content type registration should specify encoding:

```xml
<content-type
    id="org.xtclang.contenttype.xtc"
    name="XTC Source File"
    base-type="org.eclipse.core.runtime.text"
    file-extensions="x"
    default-charset="UTF-8"
    priority="normal">
</content-type>
```

---

## 10. Debug Adapter (DAP) Integration (Future)

Eclipse LSP4E includes experimental DAP support. When the XTC DAP server is ready,
it can be registered similarly to the LSP server:

```xml
    <extension point="org.eclipse.lsp4e.debug.debugAdapterServer">
        <server
            id="org.xtclang.eclipse.dap"
            label="XTC Debug Adapter"
            class="org.xtclang.eclipse.XtcDapStreamProvider">
        </server>
    </extension>
```

This is lower priority because:
1. The DAP server itself needs more implementation work
2. Eclipse's DAP support in LSP4E is still maturing
3. Users can debug via Gradle tasks in the terminal initially

### Priority: **Low** -- blocked by DAP server readiness

---

## 11. Implementation Order

| Phase | Items | Effort | Impact |
|-------|-------|--------|--------|
| **Phase 1: Bootstrap** | Content type + LSP4E server registration + TM4E grammar | Small | Critical -- complete LSP experience |
| **Phase 2: Settings** | Preference page + workspace/configuration wiring | Small | Medium -- formatting customization |
| **Phase 3: Server tweak** | `initializationOptions` fallback in LSP server | Trivial | Medium -- benefits all clients |
| **Phase 4: Run** | Launch configuration for XTC modules + code lens handler | Medium | Medium -- workflow integration |
| **Phase 5: Polish** | File icon, New File wizard, code templates | Small | Low -- professional feel |
| **Phase 6: Packaging** | p2 update site, Eclipse Marketplace submission | Small | Medium -- discoverability |
| **Phase 7: Debug** | DAP integration (when DAP server is ready) | Medium | High -- core IDE feature |

**Total estimated effort**: 2-4 developer-days for Phase 1-2 (usable integration).
Phase 3-5 add another 2-3 days. Phase 6-7 depend on external factors.

### Minimum Viable Integration (Phase 1 only)

With just the content type, LSP4E registration, and TM4E grammar (~150 lines of
Java + XML configuration), Eclipse users get:

- TextMate syntax highlighting (instant, via TM4E)
- Semantic token highlighting (richer, once server connects)
- All LSP features (hover, completion, definition, references, symbols, formatting,
  rename, code actions, folding, diagnostics, signature help, inlay hints, code lens)
- Language configuration (brackets, comments, auto-close, indentation rules)
- Workspace symbol search
- Document links
- On-type formatting

This is a remarkably complete IDE experience for ~150 lines of code, because the
LSP server does all the heavy lifting.

---

## Key Files Reference

| Component | Path |
|-----------|------|
| LSP server (shared) | `lang/lsp-server/` |
| LSP server entry point | `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt` |
| Semantic token legend | `lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/SemanticTokenEncoder.kt` |
| TextMate grammar (generated) | `lang/generated-examples/xtc.tmLanguage.json` |
| Language configuration (generated) | `lang/generated-examples/language-configuration.json` |
| IntelliJ plugin (comparison) | `lang/intellij-plugin/` |
| VS Code extension (comparison) | `lang/vscode-extension/` |
| DAP server (future) | `lang/dap-server/` |
| XDK distribution layout | `xdk/build/install/xdk/{bin,javatools,lib}/` |
