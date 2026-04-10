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
2. Understand, isolate, and where possible reduce experimental LSP4IJ DAP API usage.
3. Keep alpha-channel publication moving while making the eventual stable release less risky.

## Current Findings

### Deprecated API usages

#### 1. `ReadAction.compute(ThrowableComputable)`

Location:
- [XtcEditorStartupActivity.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEditorStartupActivity.kt#L69)

Current use:
- `ReadAction.compute { PsiManager.getInstance(project).findFile(virtualFile) }`

Why this matters:
- Verifier reports this API as deprecated and potentially removable in a future IDE.

Likely fix:
- Replace with the current recommended read-action API, likely one of:
  - `ReadAction.compute<T, Throwable> { ... }` successor if still available under a non-deprecated shape
  - `ReadAction.nonBlocking(...)`
  - `readAction { ... }`
- Choose the simplest replacement that preserves synchronous usage in this logging path.

Risk:
- Low. This is diagnostic-only startup logging.

Priority:
- High, because it is a direct deprecation with a straightforward likely fix.

#### 2. `CodeStyleSettingsManager.getCurrentSettings()`

Location:
- [XtcLanguageClient.kt](/Users/marcus/src/xtclang3/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt#L58)

Current use:
- fallback from `mainProjectCodeStyle` to `currentSettings`

Why this matters:
- Verifier reports `currentSettings` as deprecated.

Likely fix:
- Revisit the correct fallback chain for 2026.1 APIs.
- Prefer explicit project-level or scheme-level settings access rather than deprecated
  implicit current settings.
- Confirm the replacement does not regress formatting config propagation to the LSP.

Risk:
- Medium. This affects runtime formatting settings sent to the LSP server.

Priority:
- High.

### Experimental API usages

All current experimental findings are concentrated in the DAP integration.

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

Why this matters:
- Experimental APIs can change without normal compatibility guarantees.

What this probably means in practice:
- This is currently a conscious dependency on LSP4IJ's DAP extension layer.
- These findings may not be removable without either:
  - switching to a stable alternative API, if one now exists
  - isolating the experimental dependency surface further
  - accepting the experimental risk until LSP4IJ stabilizes the DAP API

Risk:
- Medium to high for future compatibility.
- Low immediate release blocker risk if alpha-only and tested against the target IDE.

Priority:
- Medium.

## Proposed Work Plan

### Phase 1: Eliminate deprecated usages

1. Replace deprecated `ReadAction.compute(...)` usage in startup logging.
2. Replace deprecated code style fallback in `XtcLanguageClient`.
3. Re-run:
   - `:lang:intellij-plugin:verifyPlugin`
4. Confirm deprecated findings drop to zero.

### Phase 2: Audit the DAP experimental surface

1. Check current LSP4IJ DAP API/docs/changelog for stable replacements.
2. Determine whether `DebugAdapterDescriptorFactory` is still the intended API for 2026.1.
3. If no stable replacement exists:
   - document this as an accepted alpha/stable risk decision
   - minimize the code surface touching experimental APIs
4. If a stable replacement exists:
   - migrate `XtcDebugAdapterFactory`
   - re-run verifier

### Phase 3: Release gating decision

Before broader rollout:

1. Deprecated findings should be zero.
2. Experimental findings should be either:
   - removed, or
   - explicitly accepted with rationale and owner.

## Suggested Acceptance Criteria

- `verifyPlugin` completes with:
  - zero deprecated API findings
- any remaining experimental findings are:
  - limited to DAP integration
  - documented in release notes / internal risk tracking

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
