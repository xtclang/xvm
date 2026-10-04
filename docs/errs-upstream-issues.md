# Upstream issues affecting Ecstasy language support

This is the upstream dependency register for `lagergren/errs`, updated on 2026-10-04.
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
| **UP07 — LSP4IJ — bridged** | Automatic pulls omit `previousResultId`. Equal full reports also replace/cancel lazy fixes without refreshing the annotations that own them. | [DiagnosticResultMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt) owns result IDs; [DiagnosticQuickFixes](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixes.kt) gives delivered full reports a client-only data revision and restores original data on outgoing action requests. Lifecycle, upstream equality and opaque-data tests cover both. | Upstream must own IDs/retirement and retain equivalent lazy fixes or refresh their annotations. Verify X181 → X185 plus diagnostic/action Undo/Redo before removing either bridge. |
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
| **UP19 — LSP4IJ — bridged** | Moving/renaming a directory leaves its open descendants connected under their old URIs. Undo can leave two opened-document entries referring to the same VirtualFile. | [DirectoryDocumentMoves](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMoves.kt) retires affected connections before paths change and asynchronously reconnects current editor buffers. X162 originally timed out after Undo; X118/X161/X162/X163 now pass, including post-Redo unsaved edits. Details below. | Upstream reconnects open descendants on directory Move/Rename/Undo/Redo, preserving unsaved text and one current synchronizer. Remove the bridge only after X162 and connection lifecycle tests pass without it. |
| **UP20 — IntelliJ test Driver — bridged** | An EDT remote call captures modality, then posts its actual call separately; a newly opened modal dialog can indefinitely defer that call. | Test-only popup focus observations/activation use a bounded Swing dispatch from the default remote dispatcher. X165 stalled before submission while the compiler was idle; saved thread dumps identify `Invoker.invoke`/`LaterInvocator` and `restorePopupFocus`. Native rename now also observes focus after its dialog opens. Details below. | Driver dispatch survives a modal transition between its state capture and invocation; selected native Rename and focus-recovery cases pass without the bridge. |

| **UP21 — LSP4IJ — bridged** | WorkspaceEdit changes closed document buffers without saving or synchronizing them to the server. Compiler queries continue reading the old disk contents. | `ClosedRefactoringDocuments` saves only edited, closed buffers after the transaction and native Undo/Redo; pre-existing unsaved closed buffers refuse before application. X169 exposed the problem while X161's unchanged class name concealed it. | Upstream keeps closed-file content visible to the server across apply/Undo/Redo without our bridge; X169 verifies exact disk text and current diagnostics. |

| **UP22 — LSP4IJ — open** | A document-change callback can restart a failed connection, dispose its own synchronizer, then create a pull-diagnostic Alarm owned by that disposed synchronizer. | X202 exposed `DocumentContentSynchronizer.sendDidChangeEvents` → connection restart → `getDebouncePullDiagnosticsAlarm`. The triggering report-copy exception was our UP07 bridge bug and is fixed locally; no patch to the upstream disposal path is installed. Details below. | A document change after transport failure safely retires the old callback without creating resources under a disposed parent. Native reconnect/typing acceptance must accompany an upstream lifecycle fix. |

## UP19: directory moves retain old document connections

LSP4IJ 0.21.0's [LSPFileListener.onFileRenameAfter](https://github.com/redhat-developer/lsp4ij/blob/0.21.0/src/main/java/com/redhat/devtools/lsp4ij/LSPFileListener.java)
disconnects/reconnects the event's exact file. A directory move changes descendant VirtualFile paths
without generating corresponding per-file events. The old synchronizer still owns its old URI;
opening the moved source creates another connection. On Undo both entries refer to the same
restored VirtualFile. This is separate from UP03's preflight and resource-edit problems.

In `run-12819902949651520401`, X162 restored paths and settings correctly but timed out waiting
45 seconds for a unique document connection. The server trace had no `didClose` for the original
source on Move and no `didOpen` after Undo. This was a connection bookkeeping failure, not compiler
analysis taking 45 seconds.

The client-scoped VFS listener captures open descendants before paths change. It calls the wrapper's
own `disconnect(URI, boolean)` with server shutdown disabled, retaining upstream synchronizer
disposal, `didClose` and diagnostic cleanup. That method is package-private with no public
equivalent in the pinned release, so a single cached reflective method is isolated here; no internal
maps or locks are accessed. A compatibility test fails on a changed/inaccessible signature.
Reconnection uses the public `LanguageServiceAccessor` after the VFS transaction, current unsaved
buffers and current paths. It never waits for transport or compilation under the IDE write lock.
Callbacks check disposal, subsequent moves and closed tabs before retaining a connection.

Four unit tests cover the pinned method, overlapping directory events, unrelated paths/properties
and leaving individual-file events to upstream. Selected native run `run-16019291377503835349`
passes START and X118/X161/X162/X163 with no recorded IDE errors or internal compiler errors.
X162 completes in 5,851 ms; its trace now closes/opens the source at every Move/Undo/Redo and
sends the subsequent unsaved changes to the final URI. X163 also verifies continued editing.
This receipt does not establish exhaustive rapid-edit/multi-window lifecycle acceptance.

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


UP16 reproduces again in full VS Code 1.140.0 playbook `run-qsTyyb` (2026-10-02).
X130's Move/Undo/Redo/resource assertions completed, but Explorer Cut cleanup raised the same
stale-tree-node error. The case remains failed. This receipt does not establish an upstream repair.


## UP20: test Driver focus calls race with a newly opened modal dialog

IntelliJ 2026.2.3 / Driver 262.10968.63 run `run-6126665495647161203` passed START and
X164, then stopped at X165's Rename dialog before changing its name. Prepare Rename took about
1 ms. Live `jcmd` dumps showed the compiler worker waiting on an empty job queue, the IDE EDT
pumping modal events, the test worker waiting in `restorePopupFocus` → `hasFocus`, and the IDE's
RMI thread waiting in `Invoker.invoke` → `ApplicationImpl.invokeAndWaitRelaxed` → `LaterInvocator`.
The saved `rename-ide-threads.txt` belongs to that failed run. The test IDE was terminated after
capturing evidence; the failed suite is not accepted coverage.

Inspection of the pinned Driver bytecode shows two dispatches: Swing `invokeAndWait` captures
`ModalityState.current()`, then an IntelliJ `invokeAndWaitRelaxed` queues the operation with that
state. A dialog opening between them makes the operation wait until it closes. The driver cannot
submit the dialog while waiting for that same operation. This is a test Driver race, not an LSP4IJ
rename deadlock or a compiler queue stall.

The test-only probe performs focus reads and activation as one bounded Swing task, called through
Driver's default dispatcher. These operations do not edit documents or access PSI. Timeout cancels
an unstarted task; completion returns immediately rather than sleeping for the timeout. Native
Rename also checks focus with its real modal dialog open. No mouse input, automatic cancellation
or replay of the rename is introduced. The requested `TODO LSP4IJ: UP20` marker identifies the
actual IntelliJ Driver owner. Repaired `run-907034856191389577` passes START and X164–X168;
X165 finishes in 1,642 ms. Dedicated `run-7549109475022481665` passes START and START_FOCUS
(completion/signature focus recovery and guards against replaying completed insertion/rename).
JUnit reports two passing suite tests, zero failures/errors/skips. Both runs report no IDE failures
and their saved IDE/server logs contain no internal-error markers. Formatting checks pass.
This covers the repaired focus path; it does not establish that every Driver EDT call is immune
to modality races.

## UP21: closed refactoring documents are invisible to the compiler

IntelliJ `run-8269964736389146880` passed X161/X163/X170/X171/X172 but failed X169 after
applying the correct document edits. `App.x` imported `util.Parcel`, while the closed class file
still had `class Box` on disk. No server document connection supplied its edited buffer. The
visible diagnostic stayed at `COMPILER-36`; successful document-text assertions alone missed it.

Inspection of the pinned LSP4IJ 0.21.0 `LSPIJUtils.applyWorkspaceEdit` confirms it calls
`applyEdits(null, document, edits, false)`, disabling document persistence. The local bridge is
scoped to the affected closed documents in the guarded refactoring transaction. It refuses
pre-existing unsaved closed buffers that the server could not have included in its proof, leaves
open editor buffers under ordinary LSP synchronization, and records persistence at the final
step of each Undo/Redo direction. It never performs Save All. Native move assertions now inspect
closed files on disk as well as IntelliJ documents and compiler diagnostics.

Repaired IntelliJ `run-14394987299477639656` passes START and all six selected move cases
(X161/X163/X169–X172), including exact closed-file disk content, clean diagnostics and native
Undo/Redo. JUnit reports one passing test without failures/errors/skips; the IDE has zero failures
or internal-error log markers. This upstream integration workaround is separate from Ecstasy compiler issue [#667](https://github.com/xtclang/xvm/issues/667).


### UP07 equal-full-report continuation (2026-10-04)

Pinned LSP4IJ 0.21.0 `LSPDiagnosticsForServer.update` always replaces its lazy quick-fix map
and cancels the previous actions. `LSPDiagnosticUtils.isDiagnosticsChanged` can simultaneously
return false for identical diagnostic values, leaving old annotations with canceled actions.
The previous-result-ID bridge avoids ordinary repeated full reports, but cannot avoid a new full
report after graph replacement or an overlapping initial pull.

X185 passed alone but failed after X181 twice (native runs `run-3835897731901718389` and
`run-14888381295818547127`), despite a completed server action reply in about 226–232 ms.
There was no IDE exception or compiler hang. The initial selected-text driver was corrected to
a caret; the sequence still failed, establishing that caret correction alone was insufficient.

`DiagnosticQuickFixes` copies a nonempty full report and wraps only its diagnostic data in a
client-local presentation revision. Diagnostic text/ranges are unchanged, and every delivered full
report makes upstream refresh annotations and their actions. Outgoing code-action contexts restore
the original opaque server data. The helper has no mutable fields or additional lifecycle cache.
The actual upstream equality function and opaque-data round trips pass in the 12-test focused
plugin gate. The full 89-test IntelliJ unit suite passes, and native
`run-16733856986922464734` passes START/X122/X181/X185–X188 together with no IDE failures.
The sequence X181 → X185 exercises the previously failing transition. Correction: `f3de29b57`.

This acceptance covers the automatic document-pull path. Workspace/related-document report
replacement needs separate native coverage before claiming the bridge covers every diagnostic
delivery route; keep that in UP07's removal/acceptance checklist.


### UP06/UP07 companion-report copy correction (2026-10-04)

Native X202 failed before applying its closed-companion quick fix. The original selected run
`run-1260617210379511142` and focused `run-11561455227684652292` restarted the connection and
reported a disposed synchronizer. The temporary disposal probe in `run-1466707445175582203`
identified the earlier cause: `DiagnosticQuickFixes.incoming` round-tripped an already decoded
`RelatedFullDocumentDiagnosticReport` through `JSONUtils.getLsp4jGson()`. That shared Gson lacks
our UP06 discriminator, so `relatedDocuments` threw `JsonParseException: Ambiguous Either type`.
The message reader terminated and LSP4IJ tried to recover the connection.

This trigger was a bug in our UP07 bridge, not an Ecstasy compiler failure or proof that the
server crashed. The bridge now copies the root diagnostic items into an explicit typed report
and preserves the already decoded companion reports and result ID. It does not mutate the input
or redecode the related union. A regression containing both full and unchanged companion reports
fails before the correction with the exact exception above. Workspace/related-document quick-fix
refresh beyond this report-preservation path remains a separate UP07 acceptance obligation.

### UP22: pull-diagnostic Alarm created after synchronizer disposal

The same runs expose a separate LSP4IJ 0.21.0 lifecycle defect. A pending PSI-commit callback enters
`DocumentContentSynchronizer.sendDidChangeEvents`; its `sendNotification` calls
`LanguageServerWrapper.start`, which detects the failed reader and disposes the old client and
synchronizer. The callback then continues into `processPullDiagnosticIfNeeded` and lazily creates
`Alarm(POOLED_THREAD, this)`. IntelliJ rejects registering the Alarm under its disposed parent.
Pinned bytecode and the recorded disposal/exception stacks establish this sequence. Checking only
the Alarm after creation cannot protect the constructor.

The upstream repair must retire callbacks when their synchronizer is disposed, including after
sending a notification that can restart the connection, and coordinate lazy resource creation with
disposal. Our report-copy fix removes this reproduction's trigger; it does not repair the upstream
lifecycle. There is no reflective patch, exception suppression or new production workaround here.
Keep UP22 open pending independent restart/typing validation on a repaired upstream version.


After the copy correction, all 90 IntelliJ unit tests pass without failures/errors/skips. Native
`run-18176000411609180309` passes START and X181/X185/X195/X197/X201–X204 with Ultimate disabled,
one server start, no IDE failures and no ambiguous-union/disposed-parent log entries. X202 applies
the closed-companion repair and completes diagnostic Undo/Redo/Undo in 3,989 ms. The intermediate
`run-3698675823455864971` had no IDE failures and applied the edit, but its harness tried to inspect
the hidden caller through an active-tab-only locator; the driver now selects the appropriate tab
for each assertion without replaying the action. These are selected receipts, not a full-catalog run
or closure of UP22.


### UP07 native cancellation during cross-module quick-fix discovery (2026-10-04)

Native `run-6617035480724888358` passes START/X201/X202/X205/X206, then aborts X209 before
applying an edit. Overlapping root pulls deliver several full reports for the same caller result
(`384/376/369` in the saved client trace). LSP4IJ cancels old lazy fixes while refreshing annotations;
`LSPLazyCodeActions.loadCodeActionsFor` raises `ProcessCanceledException` through the synchronous
remote `ShowIntentionActions` call. The server continues replying correctly: code-action request
411 returns the public method proposal in about 288 ms server time. No IDE error or server restart
is recorded. Related reports in this failure contain only an empty library report, so this is not
evidence of the still-open related/workspace nonempty-report refresh gap.

The native driver now queues intention discovery as an ordinary UI action (`now = false`), allowing
the IDE to handle cancellation normally. Its existing bounded popup wait can reopen only an
unapplied inspection after checking the document stamp; accepting an edit stays outside that retry.
It neither suppresses IDE failures nor treats an absent action as success. This is a harness
correction, not a new product bridge or a claim that all UP07 delivery routes are fixed.

The corrected native run `run-14965910820534049086` passes START/X201/X202/X205/X206/X209–X212,
zero IDE failures, one server start and no ProcessCanceledException or severe-error log markers.
Exact cross-module edits and diagnostic Undo/Redo pass with Ultimate disabled. This selected
acceptance preserves the failed receipt and does not close the broader UP07 delivery-route audit.
