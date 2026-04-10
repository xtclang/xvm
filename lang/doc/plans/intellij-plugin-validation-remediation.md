# IntelliJ Plugin Validation Remediation Plan

This document tracks JetBrains Marketplace / Plugin Verifier findings for the
XTC IntelliJ plugin and the work needed to reduce or eliminate them before
broader release rollout.

Current verified plugin version at time of writing:

- `0.4.4-SNAPSHOT.20260410170959`
- Channel: `alpha`
- IDE target verified: `IU-261.23567.28`

## Goals

1. Remove deprecated IntelliJ Platform API usages reported by Plugin Verifier.
2. Eliminate avoidable experimental API findings from the published plugin.
3. Keep alpha-channel publication moving while making the eventual stable release less risky.

## Status Summary

Current status after the first alpha publication hardening pass:

- Deprecated Plugin Verifier findings: `0`
- Experimental Plugin Verifier findings: `0`
- DAP registration: disabled in the published plugin until the feature is implemented
- Searchable options: enabled for release-grade ZIP/publish builds

Remaining work is now mostly operational/documentation work rather than verifier cleanup.

## Completed Fixes

### Deprecated API usages

#### 1. `ReadAction.compute(ThrowableComputable)`

Location:
- [XtcEditorStartupActivity.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEditorStartupActivity.kt#L69)

Original use:
- `ReadAction.compute { PsiManager.getInstance(project).findFile(virtualFile) }`

Fix applied:
- replaced with `ApplicationManager.getApplication().runReadAction { ... }`

Result:
- deprecated verifier finding removed

Risk:
- Low. This is diagnostic-only startup logging.

#### 2. `CodeStyleSettingsManager.getCurrentSettings()` and `getSettings(Project)`

Location:
- [XtcLanguageClient.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt#L58)

Original use:
- fallback from `mainProjectCodeStyle` to `currentSettings`
- intermediate fallback attempt used deprecated `getSettings(project)`

Fix applied:
- replaced with `com.intellij.application.options.CodeStyle.getProjectOrDefaultSettings(project)`

Result:
- deprecated verifier finding removed
- formatting settings lookup still works without deprecated APIs

Risk:
- Medium. This affects runtime formatting settings sent to the LSP server.

### Experimental API usages

All original experimental findings were concentrated in the dormant DAP integration.

Primary location:
- [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt)

Specific reported usages:

#### Experimental class usage

- `DebugAdapterDescriptorFactory`
  - import and inheritance in [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt#L17)
  - class declaration in [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt#L32)

#### Experimental constructor usage

- `DebugAdapterDescriptorFactory.<init>()`
  - implicit superclass construction in [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt#L32)

#### Experimental method usages

- `createDebugAdapterDescriptor(...)`
  - override in [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt#L35)
- `isDebuggableFile(...)`
  - override in [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt#L43)
- `getServerDefinition()`
  - used indirectly through `serverDefinition` in [XtcDebugAdapterFactory.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt#L40)

Fix applied:
- removed DAP classes from the published plugin source set
- commented out the `debugAdapterServer` registration in
  [plugin.xml](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/resources/META-INF/plugin.xml)
- left a `TODO` marker so the intended registration is preserved for later implementation

Result:
- all experimental verifier findings removed from the published plugin

Risk:
- Medium to high for future compatibility.
- Low immediate release blocker risk if alpha-only and tested against the target IDE.

## Remaining Work

### 1. DAP reintroduction plan

Before re-enabling DAP:

1. implement the feature end-to-end
2. confirm whether LSP4IJ exposes a stable DAP API for the target IDE/plugin versions
3. only restore the `debugAdapterServer` registration when verifier stays clean or the remaining risk is explicitly accepted

### 2. Searchable options log-noise documentation

`buildSearchableOptions` currently emits a large amount of headless IDE noise, including:

- daemon discovery warnings
- cancelled background Marketplace/UI requests
- theme deprecation warnings from bundled JetBrains themes
- JCEF/headless warnings

These do not currently fail the build or indicate plugin verifier problems. This should be documented in the plugin README so developers do not misread a successful build as failed.

### 3. Semantic token color-quality tuning

The current IntelliJ sandbox diagnostics confirm that semantic tokens are active and contributing to editor coloring:

- the IDE launches with `xtc.lsp.semanticTokens=true`
- the LSP server receives `textDocument/semanticTokens/full` requests
- the server returns semantic token payloads for XTC source files
- the editor output shows LSP semantic token markup/categories rather than pure TextMate-only highlighting

The remaining issue is therefore not "semantic tokens missing", but rather:

- weak/default color mappings for some token categories in the active scheme
- token kind/modifier choices that may be too generic for important Ecstasy constructs

Follow-up work:

1. inspect representative source snippets in the LSP4IJ Semantic Tokens Inspector
2. capture the emitted token types/modifiers for weak-looking constructs such as:
   - return types
   - interface/class names
   - method declarations
   - decorators/annotations
3. compare those emitted categories with the actual intended Ecstasy semantics
4. tighten server-side token classification where needed so important constructs map to stronger categories
5. if necessary, add or document LSP4IJ semantic-token color customizations for XTC-oriented development

Success criteria:

- high-value constructs in XTC source are visually distinct in IntelliJ without relying on accidental scheme behavior
- semantic-token-driven coloring remains stable in both sandbox `runIde` and installed ZIP/plugin builds

### 4. Sandbox color-scheme stability

The `runIde` sandbox previously persisted a broken user-derived color scheme entry (`_@user_Default`) that caused washed-out/white-editor rendering even when semantic tokens were active.

Mitigations now in place:

- `:lang:intellij-plugin:clean` deletes the actual `.intellijPlatform` sandbox used by `runIde`
- sandbox startup scrubs persisted `_@user_...` color-scheme overrides instead of hardcoding Darcula or another explicit theme

Follow-up:

1. verify this remains stable across several clean/relaunch cycles
2. only add stronger sandbox appearance forcing if IntelliJ recreates broken scheme state again

### 5. CI stabilization

Keep verifying that the release-grade path continues to run with:

- `-Plsp.buildSearchableOptions=true`
- timestamped Marketplace snapshot publish versions
- JetBrains alpha-channel publication via `JETBRAINS_TOKEN`

## Suggested Acceptance Criteria

- `verifyPlugin` completes with:
  - zero deprecated API findings
- zero experimental API findings in the published plugin
- searchable-options build noise is documented so it does not cause false alarms

## Commands

Build and verify:

```bash
./gradlew \
  -PincludeBuildLang=true \
  -PincludeBuildAttachLang=true \
  -Plsp.buildSearchableOptions=true \
  :lang:intellij-plugin:buildPlugin \
  :lang:intellij-plugin:verifyPlugin
```

Marketplace alpha publish:

```bash
./gradlew \
  -PincludeBuildLang=true \
  -PincludeBuildAttachLang=true \
  -PenablePublish=true \
  -Plsp.buildSearchableOptions=true \
  :lang:intellij-plugin:publishPlugin
```
