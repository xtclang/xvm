# IntelliJ IDEA-Specific Enhancements Plan

> **Created**: 2026-04-03
> **Status**: Planning
> **Scope**: Features that require IntelliJ plugin code (not just LSP server changes)

## Context

The XTC IntelliJ plugin currently delegates most intelligence to the out-of-process
LSP server via LSP4IJ. This plan identifies **IDEA-specific** enhancements that cannot
be provided by the LSP server alone -- things that require native IntelliJ APIs,
LSP4IJ configuration, or plugin-side code.

The goal is to reach parity with the Java development experience in IntelliJ.

---

## 1. Run Configuration: XDK Direct Mode

### Problem

Today the only practical run mode is Gradle (`./gradlew runXtc --module=...`).
This is clunky for several reasons:
- Requires a full Gradle project structure
- Slow startup (Gradle daemon, configuration phase, dependency resolution)
- Inappropriate for standalone `.x` files or Maven-resolved XDK artifacts
- Users who installed the XDK or pulled it from Maven Central shouldn't need Gradle

The existing "direct XTC" mode (`xtc run <module>`) assumes `xtc` is on PATH,
which is fragile and doesn't leverage a project-local or Maven-cached XDK.

### Solution: XDK Run Mode

Add a third execution mode that invokes `org.xvm.tool.Launcher` directly via
the JBR (IntelliJ's bundled JDK), pointing at a resolved `javatools.jar` and
the project's compiled `.xtc` modules.

**Command equivalent:**
```bash
java -jar <xdk>/javatools/javatools.jar run -L <xdk>/lib -L <project>/build -M <method> <module> [args]
```

#### Implementation

**New fields in `XtcRunConfiguration`:**
- `executionMode: ExecutionMode` enum: `GRADLE`, `XDK`, `CLI`
- `xdkPath: String` -- path to XDK installation (auto-detected or manual)
- `modulePath: List<String>` -- additional `-L` library paths
- `xtcFilePath: String` -- path to compiled `.xtc` file (optional, for single-file runs)

**XDK resolution strategy** (in order):
1. Project-level setting (persisted in `.idea/xtc.xml`)
2. `XDK_HOME` environment variable
3. Gradle `xdk/build/install/xdk/` if project has XDK subproject
4. Maven local repo `~/.m2/repository/org/xtclang/xdk/<version>/`
5. Manual path from run configuration UI

**Settings editor changes:**
Replace the single "Use Gradle" checkbox with a radio group:
```
Execution mode:
  (*) Gradle (recommended for Gradle projects)
  ( ) XDK Direct (uses javatools.jar, fastest startup)  
  ( ) CLI (requires 'xtc' on PATH)

[XDK path: _________________________ ] [Auto-detect]
[Module path: ______________________ ] [+] [-]
```

**XDK auto-detection** should be a project-level service (`XdkLocatorService`)
that caches the resolved XDK path and exposes it to run configurations, the LSP
client, and future compiler integration.

#### Files to modify
| File | Change |
|------|--------|
| `run/XtcRunConfiguration.kt` | Add `ExecutionMode` enum, `xdkPath`, `modulePath` fields, `createXdkCommandLine()` |
| `run/XtcRunSettingsEditor.kt` | Radio group for mode, XDK path field with browse button, module path list |
| `run/XtcRunConfigurationProducer.kt` | Auto-detect mode based on project type (Gradle project → GRADLE, else → XDK) |
| New: `settings/XdkLocatorService.kt` | Project service for XDK path resolution and caching |
| New: `settings/XdkSettingsConfigurable.kt` | Project-level settings page under Languages & Frameworks > XTC |

#### Priority: **High** -- core usability improvement

---

## 2. LSP4IJ Configuration & Tuning

### What LSP4IJ gives us for free (no code needed)

LSP4IJ automatically maps these LSP features to IntelliJ UI -- we just need the
server to implement them (most already done):

| LSP Feature | IntelliJ UI | Server Status |
|------------|-------------|---------------|
| `textDocument/completion` | Code completion popup | Done |
| `textDocument/hover` | Quick Documentation (Ctrl+Q) | Done |
| `textDocument/definition` | Go to Declaration (Ctrl+B) | Done |
| `textDocument/references` | Find Usages (Alt+F7) | Done |
| `textDocument/documentSymbol` | Structure tool window, breadcrumbs | Done |
| `textDocument/formatting` | Reformat Code (Ctrl+Alt+L) | Done |
| `textDocument/onTypeFormatting` | Auto-indent on Enter | Done |
| `textDocument/foldingRange` | Code folding (Ctrl+Shift+Minus) | Done |
| `textDocument/documentHighlight` | Highlight usages under cursor | Done |
| `textDocument/rename` | Rename refactoring (Shift+F6) | Done |
| `textDocument/codeAction` | Alt+Enter intentions | Done |
| `textDocument/signatureHelp` | Parameter info (Ctrl+P) | Done |
| `textDocument/selectionRange` | Extend/Shrink selection (Ctrl+W) | Done |
| `textDocument/documentLink` | Clickable import paths | Done |
| `textDocument/semanticTokens` | Semantic highlighting | Done |
| `textDocument/publishDiagnostics` | Error/warning annotations | Done |
| `textDocument/codeLens` | Inline annotations above code | Stub |
| `textDocument/inlayHint` | Type/param hints inline | Stub |

### What we should configure in LSP4IJ

#### 2a. Semantic Token Color Mapping

LSP4IJ supports a `semanticTokensColorProvider` extension point that maps LSP
semantic token types to IntelliJ's `TextAttributesKey`. Without this, semantic
tokens fall back to generic defaults.

**Register in `plugin.xml`:**
```xml
<lsp4ij:semanticTokensColorProvider
    serverId="xtcLanguageServer"
    class="org.xtclang.idea.lsp.XtcSemanticTokensColorProvider"/>
```

**Implementation maps XTC-specific tokens:**
| LSP Token Type | IntelliJ Color Key | Visual Effect |
|---------------|-------------------|---------------|
| `keyword` | `DefaultLanguageHighlighterColors.KEYWORD` | Bold blue |
| `type` | `DefaultLanguageHighlighterColors.CLASS_NAME` | Teal |
| `interface` | `DefaultLanguageHighlighterColors.INTERFACE_NAME` | Teal italic |
| `enum` | `DefaultLanguageHighlighterColors.CLASS_NAME` | Teal |
| `enumMember` | `DefaultLanguageHighlighterColors.STATIC_FIELD` | Purple italic |
| `function` | `DefaultLanguageHighlighterColors.FUNCTION_CALL` | Yellow |
| `method` | `DefaultLanguageHighlighterColors.FUNCTION_CALL` | Yellow |
| `parameter` | `DefaultLanguageHighlighterColors.PARAMETER` | Gray underline |
| `variable` | `DefaultLanguageHighlighterColors.LOCAL_VARIABLE` | Default |
| `property` | `DefaultLanguageHighlighterColors.INSTANCE_FIELD` | Purple |
| `decorator` | `DefaultLanguageHighlighterColors.METADATA` | Yellow annotation |
| `string` | `DefaultLanguageHighlighterColors.STRING` | Green |
| `number` | `DefaultLanguageHighlighterColors.NUMBER` | Blue |
| `comment` | `DefaultLanguageHighlighterColors.LINE_COMMENT` | Gray |

**Modifier handling:**
- `declaration` modifier → bold
- `static` modifier → italic
- `readonly` modifier → italic (for `const`)
- `deprecated` modifier → strikethrough

This replaces the TextMate grammar as the primary highlighter once connected to
the LSP server, giving us the same quality as Java's semantic highlighting.

#### Priority: **High** -- biggest visual quality improvement

#### 2b. Completion Item Customization

LSP4IJ allows customizing how completion items render via the
`completionItemResolver` extension point. We can:
- Add XTC-specific icons for module/class/mixin/service/const/enum types
- Format type signatures in the detail popup
- Control sort order (e.g., locals before keywords)

#### Priority: **Medium**

---

## 3. Native IntelliJ Extensions (No LSP Required)

These features are purely IDEA-side and don't need LSP server changes.

### 3a. Commenter

**What:** Enables Ctrl+/ (line comment) and Ctrl+Shift+/ (block comment).

**Implementation:** Single class + plugin.xml registration.

```xml
<lang.commenter language="Ecstasy"
    implementationClass="org.xtclang.idea.XtcCommenter"/>
```

```kotlin
class XtcCommenter : Commenter {
    override fun getLineCommentPrefix() = "// "
    override fun getBlockCommentPrefix() = "/*"
    override fun getBlockCommentSuffix() = "*/"
    override fun getCommentedBlockCommentPrefix() = null
    override fun getCommentedBlockCommentSuffix() = null
}
```

**Effort:** Trivial (15 minutes)
**Priority:** **Critical** -- basic editing essential, users expect this

### 3b. Brace Matcher

**What:** Jump between matching `{}`, `()`, `[]`, `<>` with Ctrl+Shift+M.
Highlights matching brace when cursor is adjacent.

```xml
<lang.braceMatcher language="Ecstasy"
    implementationClass="org.xtclang.idea.XtcBraceMatcher"/>
```

Note: TextMate may already handle some of this. If so, this is a no-op. Verify
before implementing.

**Effort:** Trivial
**Priority:** **High** -- fundamental navigation

### 3c. Live Templates (Code Snippets)

**What:** Java-style code templates triggered by abbreviation + Tab:

| Abbreviation | Expansion |
|-------------|-----------|
| `cls` | `class $NAME$ { $END$ }` |
| `iface` | `interface $NAME$ { $END$ }` |
| `svc` | `service $NAME$ { $END$ }` |
| `mod` | `module $NAME$ { $END$ }` |
| `meth` | `$TYPE$ $NAME$($PARAMS$) { $END$ }` |
| `prop` | `$TYPE$ $NAME$;` |
| `fori` | `for (Int i : 0 ..< $LIMIT$) { $END$ }` |
| `fore` | `for ($TYPE$ $NAME$ : $ITER$) { $END$ }` |
| `if` | `if ($COND$) { $END$ }` |
| `ife` | `if ($COND$) { $END$ } else { $END2$ }` |
| `try` | `try { $END$ } catch ($TYPE$ e) { $END2$ }` |
| `sout` | `@Inject Console console; console.print($END$);` |
| `todo` | `// TODO $END$` |

**Implementation:** XML file in `resources/liveTemplates/XTC.xml` + registration
in `plugin.xml` via `<defaultLiveTemplates>`.

**Effort:** Small (1-2 hours for a good set)
**Priority:** **High** -- big productivity boost

### 3d. File Templates

**What:** "New > XTC Module/Class/Interface/Service" in project tree context menu.

Templates:
- **XTC Module** → `module $NAME$ { }` in `$NAME$.x`
- **XTC Class** → `class $NAME$ { }` in `$NAME$.x`  
- **XTC Interface** → `interface $NAME$ { }` in `$NAME$.x`
- **XTC Service** → `service $NAME$ { }` in `$NAME$.x`
- **XTC Mixin** → `mixin $NAME$ into $BASE$ { }` in `$NAME$.x`
- **XTC Enum** → `enum $NAME$ { $VALUES$ }` in `$NAME$.x`
- **XTC Const** → `const $NAME$($PARAMS$);` in `$NAME$.x`

**Implementation:** Template files in `resources/fileTemplates/` +
`<internalFileTemplate>` in `plugin.xml` + action in `<actions>`.

**Effort:** Small-medium
**Priority:** **High** -- important for discoverability

### 3e. Line Markers (Gutter Icons)

**What:** Gutter icons for:
- **Run** icon next to `module` declarations with a `run()` method
- **Override** arrow next to methods that override a parent method
- **Implements** arrow next to interface method implementations

The run marker is feasible now (just pattern-match `module` keyword).
Override/implements markers require semantic info from the compiler adapter.

**Implementation:**
```xml
<codeInsight.lineMarkerProvider language="Ecstasy"
    implementationClass="org.xtclang.idea.XtcRunLineMarkerProvider"/>
```

For the run marker, detect `module` declarations and add a green play icon that
creates/runs an `XtcRunConfiguration`.

**Effort:** Medium (run marker easy, override markers need LSP support)
**Priority:** **Medium** -- nice visual polish, run marker is very useful

### 3f. TODO Highlighting

IntelliJ's TODO tool window works by scanning comments for patterns.
This should work automatically via TextMate/semantic tokens comment detection,
but verify it works for XTC `//` and `/* */` comments.

**Priority:** **Low** -- likely already works

---

## 4. Project-Level Settings

### 4a. XTC SDK Configuration Page

**What:** A "Languages & Frameworks > XTC" settings page where users configure:
- XDK installation path (with auto-detect)
- Default execution mode (Gradle / XDK / CLI)
- LSP server log level
- Whether to enable semantic tokens vs TextMate highlighting

This is the project-level counterpart to the per-run-configuration XDK path.

```xml
<projectConfigurable instance="org.xtclang.idea.settings.XtcSettingsConfigurable"
    id="xtc.settings" displayName="XTC"
    parentId="language"/>
```

**Priority:** **Medium** -- needed for XDK mode to work well

### 4b. Color Scheme Page

**What:** A dedicated "Editor > Color Scheme > XTC" page where users can customize
the colors for XTC semantic tokens (types, methods, properties, annotations, etc.).

LSP4IJ may provide a default page, but a custom one ensures all XTC-specific token
types are listed with good defaults and previews.

```xml
<colorSettingsPage implementation="org.xtclang.idea.style.XtcColorSettingsPage"/>
```

**Priority:** **Medium** -- important for visual customization

---

## 5. DAP (Debug Adapter Protocol) Fixes

### Current State

The DAP server is registered in `plugin.xml` but **does not work**:
- `xtc-dap-server.jar` is not built or packaged into the plugin
- `XtcDebugAdapterDescriptor` uses `System.getProperty("java.home")` instead of
  `JavaProcessCommandBuilder` (will pick wrong JDK on some setups)

### Fixes Needed

1. **Build the DAP server JAR** -- add fatJar task to dap-server module
2. **Package it** -- add copy task in intellij-plugin's build to put it in `bin/`
3. **Fix JDK resolution** -- use `JavaProcessCommandBuilder` like the LSP server
4. **Test end-to-end** -- breakpoints, step-in/out, variable inspection

#### Priority: **High** -- debugging is a core IDE feature

---

## 6. Advanced Features (Future)

These require significant LSP server or compiler work but should be planned.

### 6a. Inlay Hints (Type Annotations)

Show inferred types inline:
```
val x = getValue()  // shows `: String` after `x` as gray inline text
```

Requires the XDK compiler adapter to provide type information. The LSP
`textDocument/inlayHint` protocol is already stubbed in the server.

### 6b. Call Hierarchy / Type Hierarchy

IntelliJ's Ctrl+Alt+H (call hierarchy) and Ctrl+H (type hierarchy) windows.
LSP protocols `callHierarchy/*` and `typeHierarchy/*` are stubbed in the server.
Requires workspace-wide semantic analysis.

### 6c. Postfix Completion

IDEA-specific feature where typing `.if` after an expression wraps it:
```
x > 0.if  →  if (x > 0) { | }
list.for  →  for (val item : list) { | }
val.null  →  if (val != Null) { | }
```

Requires a `postfixTemplate` extension point registration. These are purely
client-side transformations.

### 6d. Smart Enter (Complete Statement)

Ctrl+Shift+Enter to complete a statement:
- After `if (cond` → adds `) { }`
- After `class Foo` → adds ` { }`  
- After `method()` → adds `;`

Requires a `lang.smartEnterProcessor` extension.

---

## 7. XDK Resolution & Module Path (Cross-Plugin Concern)

This is the most architecturally significant feature. It affects IntelliJ, VS Code,
and the LSP server. The goal is Java-JDK-like behavior: the plugin knows which XDK
version is needed, resolves it automatically, and uses it for compilation and execution.

### 7a. XDK as a Project SDK (like JDK in Java)

In Java IntelliJ, every project has a configured JDK. We need the same for XDK:

**Project Structure > SDKs > XDK:**
- Users can register XDK installations (like JDKs)
- Each project selects an XDK version
- The plugin auto-detects XDK from the project's Gradle configuration

**Auto-detection priority:**
1. `build.gradle.kts` — parse `xdkDistribution(...)` or XDK plugin config for version
2. Gradle wrapper — query `./gradlew :xdk:properties` for the resolved XDK path
3. `XDK_HOME` environment variable
4. Maven local repo `~/.m2/repository/org/xtclang/xdk/<version>/`
5. Maven Central — download on demand (like Gradle downloading a JDK)
6. Manual configuration in project settings

**Implementation:**
```xml
<sdkType implementation="org.xtclang.idea.sdk.XdkSdkType"/>
<projectSdkSetupValidator
    implementation="org.xtclang.idea.sdk.XdkSetupValidator"/>
```

`XdkSdkType` extends `SdkType` and provides:
- `suggestHomePaths()` — scans standard locations
- `isValidSdkHome()` — checks for `javatools/javatools.jar` and `lib/ecstasy.xtc`
- `getVersionString()` — reads version from XDK
- `suggestSdkName()` — "XDK <version>"

#### Priority: **High** -- foundational for everything else

### 7b. XTC Module Path Awareness

The LSP server needs to know the XTC module path (`-L` paths) to provide:
- Cross-module go-to-definition
- Import resolution from library modules
- Stale binary detection and recompilation triggers

**Where the module path comes from:**
- XDK `lib/` directory (standard library: `ecstasy.xtc`, `collections.xtc`, etc.)
- Project build output (`build/xtc/` or similar)
- Declared dependencies (resolved via Gradle or Maven)

**How to communicate it to the LSP server:**
The LSP `workspace/configuration` request already exists. Extend it:

```json
{
    "section": "xtc.modulePath",
    "value": [
        "/path/to/xdk/lib",
        "/path/to/project/build/xtc"
    ]
}
```

The IntelliJ plugin's `XtcLanguageClient` would resolve these paths from the
project's XDK SDK and Gradle model, then serve them via `workspace/configuration`.

The VS Code extension does the same via `settings.json`:
```json
{
    "xtc.modulePath": ["${config:xtc.xdkPath}/lib"]
}
```

### 7c. Stale Binary Detection & Recompilation

When a `.x` source file changes, the corresponding `.xtc` binary may be stale.
The plugin should:

1. **Detect staleness** — compare `.x` modification time vs `.xtc` modification time
2. **Show warning** — gutter icon or diagnostic: "Module binary is stale"
3. **Offer recompilation** — code action: "Recompile module" that invokes:
   - Gradle mode: `./gradlew :compileXtc`
   - XDK mode: `java -jar javatools.jar build -L ... <module>.x`
4. **Auto-recompile on save** (optional, user setting)

**Architecture decision:** Where does recompilation happen?

| Layer | Pros | Cons |
|-------|------|------|
| LSP server | Cross-editor, one implementation | Server needs access to XDK jars |
| Plugin (IntelliJ) | Access to project model, Gradle | IDEA-specific, duplicate for VS Code |
| Plugin (VS Code) | TypeScript task provider | VS Code-specific, duplicate |

**Recommended:** Recompilation requests go through the LSP server via a custom
`xtc/compile` request. The server receives the module path and XDK location from
`workspace/configuration`, and invokes `org.xvm.tool.Launcher` programmatically
(it's on the classpath in the XDK adapter). This keeps the logic in Kotlin and
works for all editors.

```
LSP custom request: xtc/compile
  Parameters: { uri: "file:///project/src/mymodule.x" }
  Server action: invoke Launcher.launch(["build", "-L", modulePath, uri])
  Response: { success: true, diagnostics: [...] }
```

This is where the XDK adapter (`adapter.xdk.XdkAdapter`) would gain real
functionality -- it would wrap the XTC compiler for on-demand compilation,
producing both diagnostics and compiled `.xtc` output.

### 7d. Module-Aware Navigation

Once the LSP server knows the module path, it can:
- Resolve imports to their source files across modules
- Provide cross-module go-to-definition (jump into library source)
- Show module dependencies in document symbols
- Validate that imported types actually exist in the module path

This requires the `WorkspaceIndex` to scan not just the project's `.x` files
but also the `.xtc` compiled modules on the module path (reading their symbol
tables).

#### Priority: **High** (resolution) → **Medium** (recompilation) → **Medium** (navigation)

---

## 8. Comprehensive LSP4IJ / IntelliJ Platform Audit

Features available in IntelliJ/LSP4IJ that we should evaluate:

### LSP4IJ Extension Points (Not Yet Used)

| Extension Point | What It Does | Should We Use It? |
|----------------|-------------|-------------------|
| `semanticTokensColorProvider` | Map LSP tokens → IntelliJ colors | **Yes** -- Section 2a |
| `completionItemResolver` | Customize completion rendering | Maybe -- add XTC type icons |
| `hoverContentProvider` | Customize hover popup | No -- LSP hover is sufficient |
| `codeActionProvider` | Customize quick-fix rendering | No -- LSP code actions work fine |
| `documentHighlightProvider` | Customize highlight colors | No -- defaults are fine |
| `workspaceSymbolProvider` | Customize workspace search | No -- LSP provides this |
| `inlayHintProvider` | Customize inlay hint rendering | Later -- when XDK adapter provides types |
| `fileTypeMappingProvider` | Dynamic file type detection | No -- `*.x` mapping is static |
| `workspaceFolderProvider` | Customize workspace folders | Maybe -- for multi-module XTC projects |

### IntelliJ Platform Extension Points (Not Yet Used)

| Extension Point | What It Does | Should We Use It? | Priority |
|----------------|-------------|-------------------|----------|
| `lang.commenter` | Toggle line/block comments | **Yes** | Critical |
| `lang.braceMatcher` | Brace navigation (Ctrl+Shift+M) | **Yes** | High |
| `defaultLiveTemplates` | Code snippets | **Yes** | High |
| `internalFileTemplate` | New File templates | **Yes** | High |
| `sdkType` | XDK as project SDK | **Yes** | High |
| `runLineMarkerContributor` | Run gutter icons | **Yes** | Medium |
| `codeInsight.lineMarkerProvider` | Override/implement arrows | Later | Medium |
| `colorSettingsPage` | Color scheme customization | **Yes** | Medium |
| `projectConfigurable` | Settings page | **Yes** | Medium |
| `lang.smartEnterProcessor` | Complete statement (Ctrl+Shift+Enter) | Later | Low |
| `codeInsight.postfixTemplate` | Postfix completion | Later | Low |
| `breadcrumbsProvider` | Navigation breadcrumbs | No -- LSP documentSymbol handles this |
| `codeInsight.parameterNameHints` | Parameter name inlay hints | Later | Low |
| `lang.foldingBuilder` | Code folding | No -- LSP foldingRange handles this |
| `lang.findUsagesProvider` | Find usages grouping | No -- LSP references handles this |
| `inspectionToolProvider` | Static analysis inspections | Later | Low |
| `refactoring.extractMethodHandler` | Extract method refactoring | Later -- needs compiler | Low |
| `lang.documentationProvider` | Quick Documentation (Ctrl+Q) | No -- LSP hover handles this |
| `lang.formatter` | Formatter | No -- LSP formatting handles this |
| `completion.contributor` | Completion | No -- LSP completion handles this |
| `gotoDeclarationHandler` | Go to declaration | No -- LSP definition handles this |
| `psi.referenceContributor` | Reference resolution | No -- LSP references handles this |

### Key Insight

Most code intelligence features are handled by the LSP server through LSP4IJ.
The IntelliJ plugin should focus on:

1. **Things LSP can't do:** SDK management, run configurations, project wizards,
   live templates, file templates, commenter, brace matcher
2. **Things that enhance LSP:** Semantic token colors, line markers, color scheme
3. **Things that bridge IDE ↔ LSP:** XDK path resolution, module path, workspace
   configuration, recompilation triggers

We should NOT reimplement in the plugin what LSP already provides (completion,
formatting, folding, references, etc.).

---

## Implementation Order

| Phase | Items | Effort | Impact |
|-------|-------|--------|--------|
| **Phase 1: Essentials** | 3a Commenter, 3b BraceMatcher | Trivial | High -- basic editing |
| **Phase 2: Productivity** | 3c LiveTemplates, 3d FileTemplates | Small | High -- everyday workflow |
| **Phase 3: Run/Debug** | 1 XDK Run Mode, 5 DAP Fixes | Medium | High -- core IDE features |
| **Phase 4: Visual** | 2a SemanticTokenColors, 4b ColorScheme | Medium | High -- professional look |
| **Phase 5: XDK/Module** | 7a XDK SDK Type, 7b Module Path, 4a Settings | Medium-Large | High -- foundational |
| **Phase 6: Navigation** | 3e LineMarkers (run), 7d Module Navigation | Medium | Medium |
| **Phase 7: Compiler** | 7c Recompilation, XDK adapter integration | Large | High |
| **Phase 8: Advanced** | 6a-d InlayHints, Hierarchy, Postfix, SmartEnter | Large | Medium |

| Phase | Items | Effort | Impact |
|-------|-------|--------|--------|
| **Phase 1: Essentials** | 3a Commenter, 3b BraceMatcher | Trivial | High -- basic editing |
| **Phase 2: Productivity** | 3c LiveTemplates, 3d FileTemplates | Small | High -- everyday workflow |
| **Phase 3: Run/Debug** | 1 XDK Run Mode, 5 DAP Fixes | Medium | High -- core IDE features |
| **Phase 4: Visual** | 2a SemanticTokenColors, 4b ColorScheme | Medium | High -- professional look |
| **Phase 5: Navigation** | 3e LineMarkers (run), 4a XTC Settings | Medium | Medium |
| **Phase 6: Advanced** | 6a-d InlayHints, Hierarchy, Postfix, SmartEnter | Large | Medium |

---

## Key Files Reference

| Component | Path |
|-----------|------|
| Plugin manifest | `lang/intellij-plugin/src/main/resources/META-INF/plugin.xml` |
| LSP factory | `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageServerFactory.kt` |
| LSP client | `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt` |
| Run config | `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt` |
| DAP factory | `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt` |
| Code style | `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/` |
| Build file | `lang/intellij-plugin/build.gradle.kts` |
| XDK runner | `javatools/src/main/java/org/xvm/tool/Runner.java` |
| XDK launcher | `javatools/src/main/java/org/xvm/tool/Launcher.java` |
| XDK dist layout | `xdk/build/install/xdk/{bin,javatools,lib}/` |
