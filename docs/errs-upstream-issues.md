# Upstream issues affecting Ecstasy language support

This is the upstream dependency register for `lagergren/errs`, audited on 2026-10-01.
It complements the [implementation plan](errs-integration-plan.md) and
[manual playbook](../lang/doc/manual-test-plan.md). Local fixes do not mean an upstream
release contains the repair. The entries below record the branch's source inspection,
reproductions and compatibility boundaries; they are not a fresh audit of every upstream HEAD.

**LSP4IJ** is the IntelliJ plugin. **LSP4J** is the Java protocol/JSON-RPC library, used by
both our server and LSP4IJ. They are separate projects. The branch pins LSP4IJ 0.21.0 and
server LSP4J 1.0.0; the VS Code X130 reproduction uses VS Code 1.140.0.

Keep the requested `// TODO LSP4IJ:` prefix for the Java client/library integration family,
including an explicit underlying LSP4J or IntelliJ Platform owner where applicable. Use `// TODO VSCODE:` for
VS Code host defects. Each marker includes the stable UP identifier below. The marker must
explain the removal condition; a dependency version bump alone does not justify removing it.

```bash
rg -n 'TODO (LSP4IJ|VSCODE):' lang
```

No upstream issue/PR link has been recorded for these entries. Reporting remains a separate,
explicitly authorized action. [XVM PR #653](https://github.com/xtclang/xvm/pull/653) is our
process-lifecycle fix, not an LSP4IJ upstream report. LSP4IJ #888 is a different IDE-freeze
issue and must not be used as the tracking issue for UP01.

## Register

“Bridged” means this branch has a local workaround, whose regression must pass before its
removal. “Constrained” means we deliberately avoid advertising behavior the host cannot perform.
“Open” means the externally visible defect remains. Test references identify removal gates,
not a claim that every referenced suite was rerun for this documentation change.

| ID / owner / status | Defect or missing behavior | Local handling and evidence | Removal gate |
| --- | --- | --- | --- |
| **UP01 — LSP4IJ — bridged** | Process `stop()` can precede `start()`; later startup creates a child that subsequent stop does not reap. | [Provider](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) uses `ConnectionLifetime`. Real child reproduction and separate server EOF bug are documented in the [lifecycle diagnosis](errs-lsp-process-lifecycle.md). | Upstream atomically owns start/stop and rejects post-stop startup; `ConnectionLifetimeTest` and packaged process regressions still pass without the guard. |
| **UP02 — LSP4IJ — constrained** | Native synchronization does not dispatch the advertised server save hooks, including `willSaveWaitUntil`. | [Client capabilities](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt), [provider](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) and [settings](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurable.kt) disable these hooks/server save formatting. Native Actions on Save remains available. | Observe real pre-save requests, apply version-checked edits before persistence, then enable the UI/capabilities and extend X139. |
| **UP03 — LSP4IJ — bridged** | Native file rename/move preflight happens too late; resource rename handling changes the basename but ignores a changed parent URI. | [File rename](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileRenameHandler.kt), [Move](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileMoveHandler.kt) and [resource edits](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt) preflight old paths and apply names/parents in one guarded undo command. | Native upstream Rename/Move passes X103/X130, including consumers, resources, Undo/Redo and refusals, without our entry points. |
| **UP04 — LSP4IJ — bridged** | Rename and generic `workspace/applyEdit` can apply stale edits without checking transmitted document versions/epochs. | [Rename handler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameHandler.kt), [snapshot guard](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt) and [client edit handler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) validate inside the write command. Unsupported generic resource/snippet/confirmation edits are refused. | Equivalent upstream ownership/version checks pass stale/closed/reopened document tests and X144; supported Rename/Move remains atomic and undoable. |
| **UP05 — LSP4IJ — bridged** | Dynamic filesystem watcher registrations do not alone establish/refresh unknown or missing external VFS roots while the IDE stays focused. | [CompilerRootWatches](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerRootWatches.kt) owns roots and their disposal; `CompilerRootWatchesTest` and X124 cover resources. | Upstream observes creation/deletion/change under configured external roots while focused and releases watchers on disposal. |
| **UP06 — LSP4J, bundled by LSP4IJ — bridged** | `relatedDocuments` diagnostic unions are not reliably decoded by their `kind` discriminator. | [DiagnosticReportJson](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticReportJson.kt) supplies the adapter; `DiagnosticReportJsonTest` checks full/unchanged reports. | Correct full/unchanged wire round trips without the adapter in the actual bundled client library. |
| **UP07 — LSP4IJ — bridged** | Automatic diagnostic pulls omit `previousResultId`; unchanged results must also retain quick-fix data. | [DiagnosticResultMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt) owns result IDs; `DiagnosticResultMessagesTest` verifies lifecycle behavior. | Upstream owns IDs, retirement and unchanged quick fixes; remove only after diagnostic/action regressions pass. |
| **UP08 — LSP4IJ — bridged** | Normal Gson omission of explicit null configuration fields prevents reset-to-discovery from reaching the server. | [ConfigurationJson](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConfigurationJson.kt) preserves explicit nulls; `ConfigurationJsonTest` covers the wire representation. | Reset-to-discovery survives native settings and transport round trips without the adapter. |
| **UP09 — LSP4IJ — bridged** | Startup document messages can arrive out of order; folding replies can outlive the editor that requested them. | [DocumentStartupMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DocumentStartupMessages.kt) serializes messages and retires stale responses; matching unit tests cover open/change/close ownership. | Upstream passes startup typing and close/reopen regressions without the transport bridge. |
| **UP10 — LSP4IJ — bridged** | Parameter Info retains old overload metadata after retrigger, ignores per-overload active parameters and renders absent parameter metadata as an empty signature. | [XtcParameterInfoHandler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcParameterInfoHandler.kt) uses current metadata and preserves the label without inventing a highlighted argument. | Native signature cases, including X20 and retrigger/constructor variants, preserve the label and highlight the correct current argument without this handler. |
| **UP11 — LSP4IJ — bridged** | Resolved code-action edits use an undo-transparent path without our version-checked write command. | [CodeActionMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CodeActionMessages.kt) routes selection through a supported client command. Resolving/listing never applies an edit. | X105/X122 import/member generation passes stale refusal and native Undo/Redo without the bridge. |
| **UP12 — LSP4IJ — bridged** | Formatting accepts a nullable editor but dereferences it after the asynchronous reply, producing notification floods during save/closed-file formatting. | [XtcFormattingService](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt) applies against the captured document with a current-text check. | Upstream handles absent/retired editors; native formatting/save and closed-document regressions pass without the override. |
| **UP13 — LSP4IJ — bridged** | Default `createSettings` reads global settings despite subscribing to both global and project stores. | [XtcLanguageClient](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) merges stores with project precedence. | Upstream merges/reset settings correctly; native project/global/inheritance acceptance passes. |
| **UP14 — LSP4IJ — bridged** | Semantic caches keyed only to PSI stamps retain old results when a dependency changes but the consumer text does not. | [XtcLanguageClient](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) retires completed semantic results on compiler analysis updates; X146 observes untouched-consumer refresh. | All negotiated native providers reflect dependency edits without editing the consumer or our invalidation bridge. |
| **UP15 — LSP4J — open** | Valid JSON with a wrongly typed parameter is classified as `ParseError` rather than `InvalidParams`. | [XdkStdioTest](../lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt) records the actual library result and then exercises normal semantic requests. No production parser fork. Details below. | Repair upstream parameter-decoding classification; change the assertion to `ResponseErrorCode.InvalidParams`, retaining reader recovery. |
| **UP16 — VS Code — open** | Paste repaints obsolete Cut tree nodes after an Explorer refresh; cleanup throws and skips resetting move/copy state. | [Isolated reproduction and traces](errs-integration-plan.md#x130-isolated-host-defect-and-harness-focus-correction-2026-10-01), [probe](../lang/vscode-extension/src/test/explorer-move.ts) and X130. Reproduced without Ecstasy or an LSP server. No host patch or exception suppression. | Upstream reconciles/guards stale repaint targets and always resets cleanup state. Both the controlled standalone reproduction and native X130 must pass on the repaired release. |
| **UP17 — IntelliJ Platform — open** | Removing many ranges in one document edit repeatedly traverses temporarily invalid interval subtrees on the EDT. Large-file replacement freezes the UI. | [Isolated marker probe](../lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LargeFileProbe.kt), [native driver](../lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LargeFileEditing.kt) and [measurements](errs-integration-plan.md#l82-large-file-intellij-freeze-investigation-2026-10-01). Reproduced with unattached platform documents; bulk-update mode does not remove the cost. No production tree patch or discarded highlights. | A platform repair passes the plain-marker scaling control and actual decorated-editor replacement, preserving marker validity and UI responsiveness. A smaller workload or replacement before highlights arrive does not satisfy this gate. |
| **UP18 — LSP4IJ — constrained** | Native snippet expansion adds source indentation even when the completion requests `InsertTextMode.AsIs`, despite advertising both modes. | [XtcClientFeatures](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) advertises AdjustIndentation as its default and sole supported mode. The server supplies relative template indentation. X150's first native trace (`run-9591212347662100514`) records the original capabilities, AsIs reply and doubled indentation; no IDE errors occurred. With the constraint, final native X150 passes (`run-10874571275660252562`), including exact text, snippet stops and insertion Undo. | Upstream passes the insertion mode through snippet construction/expansion. Remove the constraint only after X150 preserves exact source, placeholder navigation and Undo without it. |

## UP15: malformed parameters are not malformed JSON

The added packaged-server request is syntactically valid JSON:

```json
{"jsonrpc":"2.0","id":1,"method":"textDocument/hover","params":{"textDocument":{"uri":"file:///Stdio.x"},"position":{"line":"not-a-number","character":0}}}
```

LSP4J 1.0.0's `MessageTypeAdapter` catches `JsonSyntaxException`, `MalformedJsonException`
and `EOFException` together and creates a `MessageIssue` with `ResponseErrorCode.ParseError`.
A numeric-field decoding failure therefore follows the same path as invalid JSON syntax.
The test originally expected `ResponseErrorCode.InvalidParams` and observed
`ResponseErrorCode.ParseError`. The assertion output renders their wire values as `-32602`
and `-32700`; neither code needs a locally hardcoded numeric literal.

The regression deliberately records that current classification, then checks hover, definition,
references and highlights through the same real server connection. It also checks
`MethodNotFound` for an unknown method. Passing this regression establishes reader recovery,
not correct error classification. The underlying defect is shared LSP4J transport behavior,
not an Ecstasy parser/compiler error and not specific to IntelliJ.

## Follow-up discipline

- [ ] Prepare minimal upstream reports from the recorded evidence; obtain authorization before
  submitting them. Keep our EOF cleanup bug distinct from the LSP4IJ startup race.
- [ ] Recheck UP15 on a repaired LSP4J release and require `InvalidParams` for typed-parameter
  failures while retaining `ParseError` for actual invalid JSON syntax.
- [ ] Repair/report UP16 independently of compiler work; keep X130 failed whenever it reproduces.
- [ ] Prepare UP17's plain-document reproduction and native freeze profile for JetBrains. Verify
  a platform repair before closing L82; investigate editor decoration cost separately from compiler time.
- [ ] On each relevant dependency upgrade, rerun the removal gates before deleting a bridge.
  Record the upstream issue/PR, first fixed version and replacement validation here.
- [ ] Keep this register, source TODO identifiers and the L81/L82 acceptance status synchronized.

The branch's own progress-creation deadline fix is not an upstream defect: our former
`orTimeout` completed the transport future and lost the opportunity to retire a late successful
client registration. Likewise, broad multi-window, performance and prolonged lifecycle acceptance
remain our test obligations; they should not be filed as upstream bugs without evidence.
