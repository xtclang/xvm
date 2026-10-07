# Upstream issues affecting Ecstasy language support

This is the upstream dependency register for `lagergren/errs`, updated on 2026-10-07.
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

Local upstream repair work is now authorized in `~/src/lsp4ij`, on the unpushed branch
`lagergren/local-lsp-repairs`, based on upstream `main` at `4796cf99` (0.21.1-SNAPSHOT).
All ten local commits are listed below, with regression tests. These are local repairs,
not released fixes or permission to remove the bridges here. XVM still uses LSP4IJ 0.21.0;
testing a local build must be explicit. UP17 belongs to IntelliJ Platform, not this repair branch.

| Local LSP4IJ commit | Issue and scope |
| --- | --- |
| `d823fde7` | UP12: apply formatting to the captured document when the editor is absent. |
| `35a3bf97` | UP22: owner-bound diagnostic Alarm; serialize scheduling/disposal and reject retired callbacks/replies. |
| `2701b577` | UP27: batch refresh by connection and feature, with disposal cancellation. |
| `5e19e5f3` | UP01: single-use atomic process lifetime; reject start-after-stop/double-start and reap the owned child. |
| `c68a0819` | UP03, partial: preflight uses the actual destination parent and URI-escaped basename. Generic EDT waiting remains. |
| `1a5005d1` | UP19: reconnect open descendants after directory moves/renames, including Undo. |
| `42d073e5` | UP07: synchronizer-owned diagnostic result IDs, unchanged reports and retained/refreshed lazy fixes. |
| `47b51259` | UP13: overlay project configuration on global defaults without mutating either store. |
| `2ccc5a30` | UP08: preserve explicit nulls in configuration notifications while omitting unrelated optional protocol fields. |
| `15bb34bd` | UP12 follow-up: reconnect closed dirty buffers, guard request snapshots and server lifetimes, and cancel lookup/formatting together. |

The first batch's 14 new regressions fail against unfixed production files. The second batch
adds 11 failing controls for directory connections and diagnostic ownership, then four for
project settings and three for null serialization. The historical `batch2-combined` evidence
contains only the three serialization tests; it is not evidence for the combined repair branch.

A fresh combined run at `2ccc5a30` on October 6 passes **53 tests**, with no failures/errors/skips,
across all nine repairs and existing formatting/settings/restart controls. `buildPlugin` produces
`build/distributions/lsp4ij-0.21.1-SNAPSHOT.zip` in that checkout. It uses upstream's Community
IC 2024.2 baseline and JDK 21 for Gradle 8.6. The rebuilt archive's SHA-256 is
`88fe3a852644d50e70ff8a30662b338a5f761f3af0af247bb7ad543cb5e15d06`.
Local XML/evidence is under `/private/tmp/lsp4ij-repair-evidence/all-nine/`; earlier directories
retain the failing controls and individual repair runs. This is targeted upstream testing,
not full-suite or XVM native acceptance.
In particular, correcting UP03's target makes genuine preflight requests visible where the old
same-path request was short-circuited. Keep our bridges until testing real Move/Undo/Redo with the
local plugin. The Marketplace default remains 0.21.0, so its workarounds remain necessary even
if the local snapshot passes. UP02/UP04–UP06/UP09–UP11/UP14/UP18/UP21/UP25/UP26 are not repaired
by these nine commits; UP20/UP29 concern the test Driver, UP15 the server's LSP4J and UP17 the platform.

### Local LSP4J repairs, 2026-10-07

The separate `~/src/lsp4j` checkout is based on upstream `main` at
`57eeaa40e6b193630679ecf039895863ee715242` (1.1.0-SNAPSHOT). Its local, unpushed branch
is also named `lagergren/local-lsp-repairs`. It contains both recorded LSP4J repairs:

| Local LSP4J commit | Issue and scope |
| --- | --- |
| `b47e8923` | UP06: select nested full/unchanged diagnostic reports by `kind` in both complete report types and partial results. Reuse Gson's map handling and the existing `EitherTypeAdapter`; no application-side adapter registration is needed. |
| `13dfc35b` | UP15: defer typed parameter decoding until the envelope is read, preserve IDs that follow parameters, and report `InvalidParams`. Malformed JSON, including trailing invalid input, remains `ParseError`; notifications receive no response and the next framed request still succeeds. |

The final twelve regression tests were run with only the production repairs removed:
UP06 has **four failures out of six**, and UP15 has **three failures out of six**. Restoring
the exact repairs makes all twelve pass. A forced `./gradlew test --rerun-tasks --no-build-cache`
across all modules then passes **375 tests, zero failures/errors/skips** on JDK 17 with
upstream Gradle 8.6. The count comprises 128 protocol, 181 JSON-RPC, 10 debug protocol,
52 debug JSON-RPC and four Jakarta WebSocket tests; the generator has no separate test sources.
The copied XML, exact command/JDK path and counts are under
`/private/tmp/lsp4j-repairs-2026-10-07/verification.json`, `final-fail-before/` and
`final-all-modules/` in that same evidence directory.

UP15 preserves the existing streaming/deferred parameter decoders and their legacy
positional/list behavior. Only request/notification parameter conversion receives the new
classification; response-result failures are not mislabeled as invalid parameters.

These are upstream-library tests, **not XVM or native-editor replacement acceptance**.
The Ecstasy server still uses released LSP4J 1.0.0, LSP4IJ still uses its bundled library,
and our UP06 adapter and UP15 shipping-version assertion remain. Test a compatible release
or explicitly selected local build before removing either boundary; this checkout is a newer
protocol snapshot, not a change to the production dependency. No upstream push, issue or PR
was made. IntelliJ Platform and VS Code upstream repairs are deferred by the user's instruction.

### Local plugin acceptance, 2026-10-06

XVM checkpoint `6ece0efab` adds the explicit `-Plsp4ijPlugin=/absolute/built/plugin/directory`
override, using Gradle `localPlugin` and Starter's local installation. The Marketplace pin stays
0.21.0. See the [command and input contract](../lang/intellij-plugin/TESTING.md#lsp4ij-in-tests).
Compilation and configuration-cache reuse pass. ZIP metadata extraction in the Gradle plugin
invalidated the cache with deleted temporary paths, so the override consumes upstream's existing
`prepareSandbox` directory instead; no custom binary extraction task is added.

The native report verifies loaded 0.21.1-SNAPSHOT and records directory-content SHA-256
`c43708c3fbd6b503076047e49546e817d58a6568b3845ec663a3f9cc21d2f925` at local upstream head
`2ccc5a30`. This differs from the distribution ZIP checksum because it hashes sorted relative
filenames and file contents, without ZIP metadata.

| Receipt | Executed acceptance | Result |
| --- | --- | --- |
| `run-12526622727281847735` | CFG1–CFG3, X130, X136, X138/X139, X146, X162, X181–X185, X259 | 15 shared cases plus START pass; zero IDE errors |
| `run-13248317061307858623` | Edits and close/reopen during startup | START/STARTUP pass; zero IDE errors |
| `run-10384868562973039188` | Two projects, independent children, disposal and surviving-project requests | START/START_PROJECTS pass; zero IDE errors |

These runs establish integration compatibility with the repaired build, including formatting/save,
directory Move/Undo/Redo, settings replacement/reset, quick-fix discovery, dependency refresh and
connection disposal. **XVM's release workarounds remain enabled.** Therefore the results do not
independently prove each upstream implementation can replace its bridge. The 53 upstream tests do
exercise upstream implementations directly; both forms of evidence are needed, and neither is a
full upstream suite or long desktop soak.

Remove bridges one at a time only after the chosen release contains the corresponding repair and
the register's native gate passes without that bridge. UP12 removal must also preserve our current
text/version guard; UP13 must retain Ecstasy's build-model augmentation; UP07 requires both result-ID
and lazy-fix behavior. UP03's generic UI-thread wait remains open even on the local build. No
production workaround was removed, no dependency default changed, and nothing was pushed upstream.

### Individual bridge isolation, 2026-10-06

Each experiment removes only the named bridge against local upstream `2ccc5a30`, then restores
the exact original source before the next experiment. The directory hash remains
`c43708c3fbd6b503076047e49546e817d58a6568b3845ec663a3f9cc21d2f925`. Other release bridges stay
enabled, so these are individual replacement tests, not a bridge-free client or an upstream release.

| Bypassed bridge | Native receipt | Observed result |
| --- | --- | --- |
| UP08 configuration null serializer | `run-8451509337335907324` | START, X266/X268 and CFG1–CFG3 pass. |
| UP13 project/global settings merge; Ecstasy build-model augmentation retained | `run-11755227473631525514` | START, X136/X263/X266 and CFG1–CFG3 pass. |
| UP19 directory document reconnection | `run-15122368369408409885` | START, X118/X161/X162/X163 pass, including Move/Undo/Redo and continued unsaved editing. |
| UP27 bounded refresh | `run-10049174372853142451` | START, X137/X146/X259 pass after repairing the test Driver's UP29 false timeout. The original failed run is retained. |
| UP07 diagnostic result IDs and lazy-fix revision transport | `run-6029084721418368820` | START, X181–X185/X202/X213 pass, including selected quick fixes, closed companion edits and Undo/Redo. |
| UP12 formatting-task override | `run-5841657495710292898` | START/X138 pass; X139 fails on the closed dirty file after its actual 30-second deadline. Open-file save formatting passes. |
| UP01 process-lifetime guard | `run-3342461100631013271`, `run-11321889258000442887` | START/STARTUP and START/START_PROJECTS pass: startup editing/close/reopen and independent two-project disposal. |

All these runs record zero IDE errors. At this October 6 checkpoint, UP12 is **not** an accepted replacement. Its local repair
prevents the absent-editor dereference, but `findFormattingServer` only selects an existing
server through `processLanguageServers`; it does not reconnect a closed document. The trace has
`didClose(ClosedServiceSave.x)`, then formatting with no intervening `didOpen`. Our server's
formatting handler intentionally uses synchronized open content and returns no edits without it;
the dirty closed file remains unformatted. Our override uses `getLanguageServers(file, ...)`,
which connects the current buffer before requesting edits. Upstream needs an equivalent content
ownership path for this client contract, as well as the retained text/version and UP24 Redo guards,
before this override can be removed. The nullable-editor unit regression alone did not establish
closed-file save acceptance. No production workaround is removed by this experiment.

Local evidence is under `lang/intellij-plugin/build/reports/upstream-isolation-2026-10-06/`.
Each issue retains original/probe source, the exact patch and hashes, and copied native JSON.
UP19/UP12 also retain the originally requested patches and the formatter's reviewed blank-line
normalization. An extra passing X137/X146/X259 run (`run-1240742388975151681`) occurred while
UP19 was still bypassed; it is recorded under UP19 and is not UP27 replacement evidence.
UP03 remains only a destination-preflight repair, not a generic UI-wait fix. UP22 has no XVM
bridge to bypass; ordinary restart/typing acceptance is not a forced transport-failure test.
All bypasses are restored byte-for-byte before the full released-dependency acceptance run.

Released-dependency `run-11949889098742489342` subsequently completes all 277 shared scenarios
in one IntelliJ process: 275 pass, X254/X257 remain partial for UP25/UP26, zero failures and zero
IDE errors; START also passes. It uses shipping 0.21.0 with all production bridges restored,
including the UP12 override. This is baseline acceptance, not acceptance of an upstream-only
client. UP23 still fails in VS Code's full attempt; no known host limitation is relabeled a pass.

### UP12 closed-buffer follow-up, 2026-10-07

Local commit `15bb34bd` addresses the missing content connection exposed by the October 6
experiment. Formatting now connects the current buffer before choosing a server and applies
edits to the request's text snapshot, without requiring an editor. Text and modification-stamp
checks reject intervening edits, including an edit followed by restoration of the old text.
Operation-owned cancellation covers both server lookup and formatting; stopped/replaced server
replies cannot apply. No-server lookup completes without edits, and repeated formatting reuses
the document connection while issuing a fresh request. Existing formatter overrides and
range-formatting preference are retained.

Seven strengthened regressions fail against the preceding production implementation. The final
combined upstream selection passes **60 tests**, zero failures/errors/skips, including all earlier
repair regressions and formatting/settings/restart controls. The headless close fixture verifies
the real `didClose` message after delivering the public editor-close event; it does not substitute
for the separate native close-and-save test. XML and fail-before evidence remain in
`/private/tmp/lsp4ij-up12-2026-10-07/`.

Native `run-17693623788789126287` passes START and X31/X127/X138/X139, with zero IDE errors,
on IntelliJ 2026.2.3 and local LSP4IJ 0.21.1-SNAPSHOT. X139 closes an unsaved tab and invokes
native Save All; both the open and closed buffers receive the expected indentation. The entire
Ecstasy formatting-task override was bypassed for this test, so the upstream path owns this
result. Twelve harness polling/failure/navigation controls also pass without skips.

The loaded plugin directory SHA-256 is
`b6660b25264cf3a847b655791ec70c8b80f3146f3f2853c0131fd8b0c822b9b1`;
the distribution ZIP SHA-256 is
`20849d5318e18b89372c7c4ceabe0cdb15c3e8438d9611d89dd8998fff3a517f`.
The exact original/probe source, patch, hashes, native JSON and JUnit XML are retained in
`lang/intellij-plugin/build/reports/upstream-isolation-2026-10-07/UP12/`.
The shipping override was restored byte-for-byte before released-dependency acceptance.

**Keep the production bridge.** The Marketplace dependency remains 0.21.0. This selection
validates save formatting, not standalone Format's UP24 Undo/Redo behavior, which the local
repair does not change. There is also a connection-lifetime follow-up: a reconnected closed
buffer can remain synchronized until a later editor close, file deletion, server retirement or
project disposal. Repeated formatting reuses one synchronizer per URI, but formatting many
different closed files can retain many connections. Safe transient ownership must account for
another feature or editor opening the same document; an unconditional disconnect is unsafe.
No upstream publication or production dependency change is part of this checkpoint.

## Register

“Bridged” means this branch has a local workaround, whose regression must pass before its
removal. “Constrained” means we deliberately avoid advertising behavior the host cannot perform.
“Open” means the externally visible defect remains. Test references identify removal gates,
not a claim that every referenced suite was rerun for this documentation change.

| ID / owner / status | Defect or missing behavior | Local handling and evidence | Removal gate |
| --- | --- | --- | --- |
| **UP01 — LSP4IJ — bridged** | Process `stop()` can precede `start()`; later startup creates a child that subsequent stop does not reap. | [Provider](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) uses `ConnectionLifetime`. Real child reproduction and separate server EOF bug are documented in the [lifecycle diagnosis](errs-lsp-process-lifecycle.md). | Upstream atomically owns start/stop and rejects post-stop startup; `ConnectionLifetimeTest` and packaged process regressions still pass without the guard. |
| **UP02 — LSP4IJ — constrained** | Native synchronization does not dispatch the advertised server save hooks, including `willSaveWaitUntil`. | [Client capabilities](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt), [provider](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) and [settings](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurable.kt) disable these hooks/server save formatting. Native Actions on Save remains available. | Observe real pre-save requests, apply version-checked edits before persistence, then enable the UI/capabilities and extend X139. |
| **UP03 — LSP4IJ — bridged; generic UI wait open** | Native file rename/move preflight happens too late; resource rename handling ignores changed parents. The VFS listener also requests old-path → same-path preflight for moves and waits on the EDT. | Guarded native entry points preserve edits/Undo. `PreflightedRenames` now completes no-op requests and exact already-approved physical operations locally. Real unowned renames still use upstream preflight. Eight endpoint/launcher regressions and the focused native run pass. Original 6–11-second wait stacks remain recorded. | Native upstream Rename/Move passes X103/X130, including consumers, resources, Undo/Redo and refusals, without our entry points or UI-thread waits. |
| **UP04 — LSP4IJ — bridged** | Rename and generic `workspace/applyEdit` can apply stale edits without checking transmitted document versions/epochs. | [Rename handler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameHandler.kt), [snapshot guard](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt) and [client edit handler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) validate inside the write command. Unsupported generic resource/snippet/confirmation edits are refused. | Equivalent upstream ownership/version checks pass stale/closed/reopened document tests and X144; supported Rename/Move remains atomic and undoable. |
| **UP05 — LSP4IJ — bridged** | Dynamic filesystem watcher registrations do not alone establish/refresh unknown or missing external VFS roots while the IDE stays focused. | [CompilerRootWatches](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerRootWatches.kt) owns roots and their disposal; `CompilerRootWatchesTest` and X124 cover resources. | Upstream observes creation/deletion/change under configured external roots while focused and releases watchers on disposal. |
| **UP06 — LSP4J, bundled by LSP4IJ — bridged; local repair tested** | `relatedDocuments` diagnostic unions are not reliably decoded by their `kind` discriminator. | [DiagnosticReportJson](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticReportJson.kt) supplies the adapter; `DiagnosticReportJsonTest` checks full/unchanged reports. Local LSP4J `b47e8923` passes direct regression tests; see the [repair receipt](#local-lsp4j-repairs-2026-10-07). | Correct full/unchanged wire round trips without the adapter in the actual bundled client library. |
| **UP07 — LSP4IJ — bridged** | Automatic pulls omit `previousResultId`. Equal full reports also replace/cancel lazy fixes without refreshing the annotations that own them. | [DiagnosticResultMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt) owns result IDs; [DiagnosticQuickFixes](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixes.kt) gives delivered full reports a client-only data revision and restores original data on outgoing action requests. Lifecycle, upstream equality and opaque-data tests cover both. | Upstream must own IDs/retirement and retain equivalent lazy fixes or refresh their annotations. Verify X181 → X185 plus diagnostic/action Undo/Redo before removing either bridge. |
| **UP08 — LSP4IJ — bridged** | Normal Gson omission of explicit null configuration fields prevents reset-to-discovery from reaching the server. | [ConfigurationJson](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConfigurationJson.kt) preserves explicit nulls; `ConfigurationJsonTest` covers the wire representation. | Reset-to-discovery survives native settings and transport round trips without the adapter. |
| **UP09 — LSP4IJ — bridged** | Startup document messages can arrive out of order; folding replies can outlive the editor that requested them. | [DocumentStartupMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DocumentStartupMessages.kt) serializes messages and retires stale responses; matching unit tests cover open/change/close ownership. | Upstream passes startup typing and close/reopen regressions without the transport bridge. |
| **UP10 — LSP4IJ — bridged** | Parameter Info retains old overload metadata after retrigger, ignores per-overload active parameters and renders absent parameter metadata as an empty signature. | [XtcParameterInfoHandler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcParameterInfoHandler.kt) uses current metadata and preserves the label without inventing a highlighted argument. | Native signature cases, including X20 and retrigger/constructor variants, preserve the label and highlight the correct current argument without this handler. |
| **UP11 — LSP4IJ — bridged** | Resolved code-action edits use an undo-transparent path without our version-checked write command. | [CodeActionMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CodeActionMessages.kt) routes selection through a supported client command. Resolving/listing never applies an edit. | X105/X122 import/member generation passes stale refusal and native Undo/Redo without the bridge. |
| **UP12 — LSP4IJ — bridged** | Formatting dereferences an absent editor and fails to reconnect closed dirty buffers before querying their contents. | [XtcFormattingService](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt) connects the current buffer and checks text/version before applying edits. Local `15bb34bd` passes closed-file native save with this override bypassed; shipping 0.21.0 still needs it. | A released repair preserves absent-editor, snapshot, cancellation and server-lifetime guards; repeat native save acceptance. Retain the separate UP24 command ownership and resolve closed-buffer connection retention before removing the whole override. |
| **UP13 — LSP4IJ — bridged** | Default `createSettings` reads global settings despite subscribing to both global and project stores. | [XtcLanguageClient](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) merges stores with project precedence. | Upstream merges/reset settings correctly; native project/global/inheritance acceptance passes. |
| **UP14 — LSP4IJ — bridged** | Semantic caches keyed only to PSI stamps retain old results when a dependency changes but the consumer text does not. | [XtcLanguageClient](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) retires completed semantic results on compiler analysis updates; X146 observes untouched-consumer refresh. | All negotiated native providers reflect dependency edits without editing the consumer or our invalidation bridge. |
| **UP15 — LSP4J — release limitation; local repair tested** | Valid JSON with a wrongly typed parameter is classified as `ParseError` rather than `InvalidParams`. | [XdkStdioTest](../lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt) records the actual library result and then exercises normal semantic requests. No production parser fork. Local LSP4J `13dfc35b` passes classification and transport recovery tests; see the [repair receipt](#local-lsp4j-repairs-2026-10-07). | Adopt and verify a compatible repaired dependency; change the assertion to `ResponseErrorCode.InvalidParams`, retaining reader recovery. |
| **UP16 — VS Code — open** | Paste repaints obsolete Cut tree nodes after an Explorer refresh; cleanup throws and skips resetting move/copy state. | [Isolated reproduction and traces](errs-integration-plan.md#x130-isolated-host-defect-and-harness-focus-correction-2026-10-01), [probe](../lang/vscode-extension/src/test/explorer-move.ts) and X130. Reproduced without Ecstasy or an LSP server. No host patch or exception suppression. | Upstream reconciles/guards stale repaint targets and always resets cleanup state. Both the controlled standalone reproduction and native X130 must pass on the repaired release. |
| **UP17 — IntelliJ Platform — open** | Removing many ranges in one document edit repeatedly traverses temporarily invalid interval subtrees on the EDT. Large-file replacement freezes the UI. | [Isolated marker probe](../lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LargeFileProbe.kt), [native driver](../lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LargeFileEditing.kt) and [measurements](errs-integration-plan.md#l82-large-file-intellij-freeze-investigation-2026-10-01). Reproduced with unattached platform documents; bulk-update mode does not remove the cost. No production tree patch or discarded highlights. | A platform repair passes the plain-marker scaling control and actual decorated-editor replacement, preserving marker validity and UI responsiveness. A smaller workload or replacement before highlights arrive does not satisfy this gate. |
| **UP18 — LSP4IJ — constrained** | Native snippet expansion adds source indentation even when the completion requests `InsertTextMode.AsIs`, despite advertising both modes. | [XtcClientFeatures](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) advertises AdjustIndentation as its default and sole supported mode. The server supplies relative template indentation. X150's first native trace (`run-9591212347662100514`) records the original capabilities, AsIs reply and doubled indentation; no IDE errors occurred. With the constraint, final native X150 passes (`run-10874571275660252562`), including exact text, snippet stops and insertion Undo. | Upstream passes the insertion mode through snippet construction/expansion. Remove the constraint only after X150 preserves exact source, placeholder navigation and Undo without it. |
| **UP19 — LSP4IJ — bridged** | Moving/renaming a directory leaves its open descendants connected under their old URIs. Undo can leave two opened-document entries referring to the same VirtualFile. | [DirectoryDocumentMoves](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMoves.kt) retires affected connections before paths change and asynchronously reconnects current editor buffers. X162 originally timed out after Undo; X118/X161/X162/X163 now pass, including post-Redo unsaved edits. Details below. | Upstream reconnects open descendants on directory Move/Rename/Undo/Redo, preserving unsaved text and one current synchronizer. Remove the bridge only after X162 and connection lifecycle tests pass without it. |
| **UP20 — IntelliJ test Driver — bridged** | An EDT remote call captures modality, then posts its actual call separately; a newly opened modal dialog can indefinitely defer that call. | Test-only popup focus observations/activation use a bounded Swing dispatch from the default remote dispatcher. X165 stalled before submission while the compiler was idle; saved thread dumps identify `Invoker.invoke`/`LaterInvocator` and `restorePopupFocus`. Native rename now also observes focus after its dialog opens. Details below. | Driver dispatch survives a modal transition between its state capture and invocation; selected native Rename and focus-recovery cases pass without the bridge. |

| **UP21 — LSP4IJ — bridged** | WorkspaceEdit changes closed document buffers without saving or synchronizing them to the server. Compiler queries continue reading the old disk contents. | `ClosedRefactoringDocuments` saves only edited, closed buffers after the transaction and native Undo/Redo; pre-existing unsaved closed buffers refuse before application. X169 exposed the problem while X161's unchanged class name concealed it. | Upstream keeps closed-file content visible to the server across apply/Undo/Redo without our bridge; X169 verifies exact disk text and current diagnostics. |

| **UP22 — LSP4IJ — open** | A document-change callback can restart a failed connection, dispose its own synchronizer, then create a pull-diagnostic Alarm owned by that disposed synchronizer. | X202 exposed `DocumentContentSynchronizer.sendDidChangeEvents` → connection restart → `getDebouncePullDiagnosticsAlarm`. The triggering report-copy exception was our UP07 bridge bug and is fixed locally; no patch to the upstream disposal path is installed. Details below. | A document change after transport failure safely retires the old callback without creating resources under a disposed parent. Native reconnect/typing acceptance must accompany an upstream lifecycle fix. |


| ID / owner / status | Defect or missing behavior | Local handling and evidence | Removal gate |
| --- | --- | --- | --- |
| **UP25 — LSP4IJ — constrained** | No LSP 3.18 document-content provider or refresh handler. | Matching read-only file fallback and direct library monikers; X254 executes those assertions with explicit partial status. [Details below](#up25-lsp4ij-has-no-lsp-318-library-content-provider). | Upstream virtual URI resolution, read-only views, refresh and disposal pass native X254 before advertising the capability. |
| **UP26 — LSP4IJ — constrained** | Inline requests always use Automatic and omit selected popup context. | Native unique suggestions work; X257 has protocol-only explicit alternatives/selection. [Details below](#up26-lsp4ij-inline-completion-loses-invocation-and-popup-selection-context). | Forward invocation kind and selection, then pass native X257. |
| **UP27 — LSP4IJ — bridged; long continuation passes** | Semantic-token and code-lens refresh fan out across connected files on the application executor, exceeding IntelliJ's concurrent non-blocking read-action limit in a long session. | `EditorRefresh` replaces that fan-out with one coalesced read/UI pass per connection and feature, retaining LSP4IJ's rendering bridges. X146 adds a many-document refresh burst; X259 retains toggling/restart acceptance. Focused and long continuation runs pass with zero IDE errors/freeze dumps. Original errors remain recorded. | Remove the bridge when upstream provides bounded batch refresh and connection-lifetime cancellation, then repeat the many-file stress and long native acceptance. |
| **UP28 — vscode-languageclient — bridged** | Closing the transport during initialization clears the internal startup promise before `start()` returns it. The lost promise can reject unhandled while the returned promise resolves, and default error handlers display duplicate notifications. | The Ecstasy client observes both the initiating and idempotent in-flight `start()` promises, requires Running, and owns one actionable startup notification. X272 checks command rejection, one visible failure, no unhandled rejection, offline export and recovery; `run-W9g4f3` passes X269–X272. | Upstream captures and always returns/observes the original startup promise, including transport closure. Remove the double observation only after X272 passes without it. |
| **UP29 — IntelliJ test Driver — bridged** | `waitFor` checks a successful predicate again after leaving its loop. A changed connection state can then produce an immediate false timeout; its deadline also uses wall-clock time. | Test-only `UiWaits` accepts each observation once and uses a monotonic deadline. Five regressions cover changing readiness, pending reads, actual timeout, cancellation and nullable values. Restart/refresh X137/X146/X259 pass with the UP27 bridge disabled. | Driver accepts the successful observation without another remote read and uses elapsed monotonic time; the regression and restart selection pass without our polling implementation. |

### UP29: a successful readiness observation was checked again (2026-10-06)

While isolating UP27, `run-14660696367669321710` reported a 45-second connection-start timeout
after only 2,739 ms in X137. It recorded no IDE errors or compiler crash. Inspection of pinned
Driver SDK 262.10968.63's `waits.kt` found that `waitFor` calls its checker both in the loop
condition and again after leaving the loop. A remote predicate can return true, then false as
the connection changes, without exhausting the deadline. This is separate from UP20's modal
dispatch problem and is not a defect in the Ecstasy compiler or LSP4IJ.

The regression's predicate succeeds exactly once: it fails against the old implementation and
passes when the successful result is returned immediately. `UiWaits` now uses monotonic elapsed
time and fresh observations for pending conditions. Genuine expiry still throws `WaitForException`;
getter failures and cancellation propagate without retrying actions. The polling loop does not
change Driver's remote-call timeout or replay edits. Five polling and four existing fatal-failure
tests pass without skips. Fixed native `run-10049174372853142451` passes START/X137/X146/X259
with zero IDE errors, with our UP27 refresh bridge disabled against local LSP4IJ `2ccc5a30`.
The failed run and fail-before XML remain in the local isolation evidence; no upstream issue
or PR has been filed.

### UP28: initialization promise lifetime (2026-10-06)

Source inspection of installed `vscode-languageclient` 10.1.2 identifies the race in
`BaseLanguageClient.start()` and `handleConnectionClosed()`: `start()` creates `_onStart`, awaits
initialization and later returns the field; the close handler clears that field meanwhile. The
captured rejection function can then reject a promise no caller receives. X272's deliberately
invalid `-Xmx1K` JVM reproduces this with no compiler execution. The failing UI receipt
`run-FpkLou` also shows multiple initialization/connection popups.

The workaround uses public idempotent `start()` calls, without editing dependency files or reading
private fields. Both promises get handlers immediately. The supported initialization-failure hook
disables the default duplicate balloons, while the Ecstasy startup handler records the full error
and offers Settings, Logs and Export actions once. Transport details remain in Output. The old
instance-level `stop` monkey patch is now a normal subclass override, retaining protection against
the library's unawaited stop during failed initialization. `// TODO VSCODE: UP28` records removal.

The final focused run checks the actual visible notification as well as the command outcome;
expected negative-test notifications are cleared between cases. No upstream issue or PR was filed.

## UP17: local workaround assessment (2026-10-05)

No safe plugin-level repair was identified in the pinned IntelliJ 262.10968.63 APIs. The affected
range-tree update and ancestor recalculation are internal to platform-owned documents/markup
models. `DocumentImpl` is final and owns its marker trees in private final fields; there is no
public replacement strategy for that update. LSP4IJ submits semantic `HighlightInfo` objects to
the platform highlighting pass, which owns the resulting highlighters. A local LSP4IJ build alone
does not replace the affected platform algorithm.

The earlier plain-document control already rules out compiler queueing and LSP refresh fan-out.
Bulk-update mode still took about five seconds with 80,000 markers, and `replaceString` already
trims unchanged prefixes/suffixes. Clearing highlighting would reduce functionality; splitting one
replacement into arbitrary edits would change document-event and marker behavior and would not
repair ordinary user edits. Neither is adopted as a workaround. Reflective tree replacement or
runtime bytecode patches are also outside this local plugin repair.

Retain UP17 as open, with the existing standalone probe and decorated-editor acceptance gate.
A real platform repair or verified fixed IDE version remains the next step; no new runtime code
or GUI rerun was needed for this API assessment. This is not a claim that every possible local
mitigation has been exhausted, nor a release exception approved by the user.


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

Rechecked on 2026-10-05: [1.0.0 remains the latest release](https://github.com/eclipse-lsp4j/lsp4j/releases),
which this branch already uses. The same catch/classification is present in
[`MessageTypeAdapter` at upstream commit `57eeaa40e6b193630679ecf039895863ee715242`](https://github.com/eclipse-lsp4j/lsp4j/blob/57eeaa40e6b193630679ecf039895863ee715242/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/json/adapters/MessageTypeAdapter.java).
There is no released dependency upgrade that removes this limitation.

The October 7 [local LSP4J repair](#local-lsp4j-repairs-2026-10-07) now addresses the
classification on an unpushed branch. The shipping assertion above still describes the
released dependency, not that repaired checkout.

## Follow-up discipline

- [ ] Prepare minimal upstream reports from the recorded evidence; obtain authorization before
  submitting them. Keep our EOF cleanup bug distinct from the LSP4IJ startup race.
- [ ] Recheck UP15 on a repaired LSP4J release and require `InvalidParams` for typed-parameter
  failures while retaining `ParseError` for actual invalid JSON syntax.
- [ ] Repair/report UP16 independently of compiler work; keep X130 failed whenever it reproduces.
  VS Code upstream repair work is currently deferred by the user's instruction.
- [ ] Prepare UP17's plain-document reproduction and native freeze profile for JetBrains. Verify
  a platform repair before closing L82; investigate editor decoration cost separately from compiler time.
  IntelliJ Platform upstream repair work is currently deferred by the user's instruction.
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

The October 5 full-catalog continuation exposed X60's older expectation that even closed rename
targets stay unsaved. Its corrected assertion checks persisted closed consumers before opening
them and separately preserves the open editor's unsaved-buffer check. This aligns the test with
the UP21 contract rather than weakening that contract.

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


### UP07 explicit-graph quick-fix transition (2026-10-04)

The destination-import selection `run-4191909342836480788` passes START/X209–X212, then X213
receives a valid `Create public method 'missing' in 'Other'` reply but never shows the native
intention list. The server trace records overlapping diagnostic refreshes and cancelled code-action
requests after replacing the discovered fixture graph with X213's explicit three-module graph.
Earlier cases' broken caller tabs and inspected dependency tabs were still open; each participated
in refresh. No IDE internal failure or compiler crash is recorded. This is evidence of the existing
delivery race, not evidence that the missing-method proof failed.

The harness now extends the workspace cases' existing tab isolation to every independent
`discovered` case: it closes preceding fixture tabs before configuring/opening the next case.
Isolation alone passes X213 but still loses X214's popup in `run-4352512895301591077`.
Explicit-graph cases now install the graph before opening their caller, and verify initial fixture
text instead of rewriting it. Discovery cases retain server startup before workspace notifications.
The popup still uses native actions and the modification-stamp guard prohibits replaying edits.
This does not repair or claim acceptance for normal editing with unrelated broken files open
through graph replacement. Keep that production transition in UP07's follow-up/native acceptance
checklist. The final destination-import receipt is recorded in the integration plan.

The corrected setup passes START and X122/X209–X215 in `run-4588144426201480586`, zero IDE
failures, Ultimate disabled. X214 includes native atomic Undo/Redo of its module-root import and
companion method. The accepted IDE log contains no ERROR/SEVERE or ProcessCanceledException
markers. The broader production graph-replacement acceptance obligation above remains open.

The post-rebase full run `run-15431938389766717360` exposes the equivalent discovery setup
problem at X185. The first diagnostic quick-fix request returns an empty result before the correct
scenario graph is installed; a later caret request returns the missing-method action, but the native
popup never appears. No IDE error is recorded. Discovery setup now also installs its graph before
opening its target. A cold selected run starts the server using the common graph's valid consumer
first. This is fixture isolation, not a fix for UP07's production graph-change/cache lifecycle.

### UP23: overlapping VS Code file moves retain the host's order

VS Code 1.140.0 applies requested file moves in their supplied order after the Ecstasy participant's
additional edits. In X218, moving `App/tools` before independently moving `App/tools/Box.x` removes
the child's old path. The host reports `EntryNotFound` and returns false after partial application
(`run-VfDauh`, 2026-10-04). Child-first applies successfully, but Undo then restores the child
before the parent; the newly created parent directory causes `target already exists`
(`run-S7CfWP`). Both native failures remain failures in X218. This is distinct from UP16's Explorer Cut highlighting failure.

The full 264-case VS Code run `run-5flalU` on 2026-10-05 reproduces the same native Undo defect:
263 cases pass and X218 fails after forward movement. No failure is suppressed or reclassified;
X130 independently passes this run. The observed `target already exists` error is the same UP23
ordering problem, not renewed compiler proof failure.

After the master rebase, `run-J054rk` on the same VS Code 1.140.0 baseline completes all 267
cases with 266 passes and the identical X218 failure. It remains an explicit failed assertion.

The October 6 full attempt `run-sDtdSi` passes X1–X217, then reproduces the same child-first
Undo failure in X218. The source/resource consistency wait reaches its real 30-second deadline,
so the current harness aborts that IDE and records 59 cases as not run. Do not replay the partial
transaction or downgrade this to a pass; any continuation uses fresh fixtures in a new process.
X130 passes independently in this run. This is separate from the repaired X148 configuration
invalidation timeout recorded in the integration plan.

The compiler proposal normalizes both input orders to child-first and coalesces inherited child
paths. IntelliJ applies that complete ordered transaction. VS Code's participant cannot replace,
reorder or veto host-owned moves. X218 submits the child first to reach and expose the Undo defect;
this is not passing VS Code acceptance. The installed `workbench.desktop.main.js` confirms that
`Swo._reverse()` calls inverse operations in forward order; `ALe.perform()` also retains the
original rename-entry order. Reversal must reverse both levels for dependent moves, with regression
coverage for repeated Undo/Redo.
Do not retry a partially applied transaction. A host API for replacing the transaction, or a
separate Ecstasy-owned Move command with complete undo ownership, is required to remove this limit.
The marker is in `lang/vscode-extension/src/rename-proposal.ts`.


### UP24: asynchronous IntelliJ formatting loses Redo

IntelliJ `IU-262.10968.63` applies asynchronous formatting through
`AsyncDocumentFormattingSupportImpl.FormattingRequestImpl.runAndAwaitTask` using
`CommandProcessor.runUndoTransparentAction`. Inspection of the pinned platform bytecode and native
X249/X250 show the same result: standalone Reformat produces the correct text and Undo restores the
original, but no Redo change follows. This is the platform path used by LSP4IJ 0.21.0 and our existing
UP12 formatting bridge; it is not a compiler formatting failure or a focus timeout.

`XtcFormattingService` now applies asynchronous replies in a named write command on the EDT. It
checks cancellation, project/file lifetime, document revision and original text before application.
The compiler request remains asynchronous; the EDT never waits for its reply. Synchronous formatting
continues through the platform's existing command, preserving Save behavior. Equal output creates no
new command. No compiler or AST changes are involved. The workaround has a searchable
`// TODO LSP4IJ: UP24` comment; this label identifies the integration boundary, not ownership of the
platform implementation.

Evidence:

- `run-12440566880428489550`: ten selected cases and START passed; X249/X250 failed specifically at
  Redo. Formatting, diagnostics, repeat-query idempotence and Undo had passed. No IDE failures.
- `run-14744074393597958558`: START and X249/X250/X139/X132 pass with zero IDE failures. The two new
  cases assert exact Format/Undo/Redo/Undo text; X139 retains native Save All for a closed document,
  and X132 retains range/save coverage.

Status: locally bridged. Remove the asynchronous write-command path only after native standalone
formatting and range/save/closed-document acceptance retain history without it on a repaired platform.
This run does not establish an upstream release fix or a general audit of all async formatters.


### UP25: LSP4IJ has no LSP 3.18 library content provider

LSP4IJ 0.21.0 does not advertise `workspace.textDocumentContent`, register virtual content
providers, or implement `workspace/textDocumentContent/refresh`. Its LSP4J 1.0.0 dependency
contains the protocol types; that alone does not implement the editor feature. VS Code's
language client already registers a read-only content provider and handles refresh.

L75 negotiates virtual `ecstasy-library` views only with supporting clients. IntelliJ keeps
matching read-only files and gains the same artifact-backed declaration monikers. The marker
is in `XtcClientFeatures.initializeParams`. This is a supported fallback, not a failed native
virtual-document acceptance. Upstream support must cover URI resolution, read-only editor
content, refresh, document synchronization and disposal before changing the IntelliJ capability.

Selected receipt: IntelliJ `run-16728311754487527222` executes X254 and passes protected-file,
matching-source, direct-moniker and no-formatting assertions. It reports partial for virtual
content/refresh, with zero IDE failures. VS Code `run-e4dCrP` passes its virtual provider path,
including native typing refusal. Both also pass X31/X101/X158/X252/X253.

### UP26: LSP4IJ inline completion loses invocation and popup-selection context

LSP4IJ 0.21.0's `LSPInlineCompletionSupport.createInlineCompletionContext` always sends
`Automatic`. Its provider recognizes `DirectCall` only to skip debounce; it does not forward
`Invoked` or `selectedCompletionInfo`. Unique compiler ghost suggestions work, but native
explicit cycling through ambiguous alternatives and extending a selected popup item cannot
exercise the server's complete contract. X257 marks these assertions as protocol-only/partial
in IntelliJ; VS Code uses its native alternative selection as well.

Upstream repair: carry the triggering event and selected lookup range/text in the request,
then map them to the standard context. Keep selection ranges identical and require insertions
to extend the selected item. Native X257 must pass before removing the partial marker.
No production workaround broadens Automatic requests into speculative alternatives.

Source: [LSP4IJ support at 0.21.0](https://github.com/redhat-developer/lsp4ij/blob/0.21.0/src/main/java/com/redhat/devtools/lsp4ij/features/inlineCompletion/LSPInlineCompletionSupport.java).


## UP27: native refresh overload during a long session

IntelliJ continuation `run-5080670791887423538` (2026-10-05, LSP4IJ 0.21.0) records two
`SubmissionTracker.preventTooManySubmissions` errors. The first has 11 similar active read actions
from `LanguageClientImpl.refreshSemanticTokensForAllOpenedFiles`; the second has 42 from
`refreshCodeLensForAllOpenedFiles`. Both go through `EditorFeatureManager.refreshEditorFeature`.
The report's independent `ideFailures` gate fails even when individual feature assertions pass.

Inspection of the installed 0.21.0 bytecode confirms the manager already coalesces by file,
feature and cache-clearing flag. It submits each distinct file to the application executor and
returns without exposing that completion to the client. Adding the same coalescing key again,
serializing only calls that schedule work, or suppressing the IDE error would not establish a bound.

Local repair: `EditorRefresh` submits a single non-blocking read action for each connection/feature,
coalescing repeated requests at that boundary rather than per file. Each pass visits connected
documents but prepares rendering only for live editors. It reuses upstream `EditorFeature`
implementations for cache invalidation, code vision, declarative hints and semantic tokens; no
reflection implementation or global IDE service is copied. Disposed clients/projects expire work,
and editors closed before UI publication are skipped. Scheduling acknowledgement does not expose
superseded internal tasks as failed JSON-RPC refreshes. Compiler inlay hints remain enabled.

X146 now opens 16 additional real documents, temporarily raises the sandbox IDE's tab limit, then
issues 64 refresh requests for each of the three features while a write action delays their work.
The existing untouched-consumer hint assertions run afterward in both directions. X259 covers
hint toggling and retiring a connection during restart. Focused run `run-11532602109763278599`
passes both cases and 14 refactoring cases plus startup, with zero IDE errors. The subsequent
172-case continuation `run-13878115092192383073` also passes (two pre-existing partial cases),
with zero IDE errors/freeze dumps. No suppression was installed. An independent X185 popup-harness
failure in the first segment is fixed and recorded; acceptance is across continuations, not one
uninterrupted full run. Upstream removal remains open.

## UP03 continuation: native VFS preflight blocks the UI

The same long run records two independent freeze alerts and seven freeze-dump groups lasting
6 or 11 seconds. Their EDT stacks wait in `AbstractLSPFileListener.applyWorkspaceEdit` via
`beforeVfsChange`. The first also contains `XtcRenameEdit.apply` and `XtcFileOperations.submit`:
our guarded operation has already obtained a proposal, but its physical VFS move invokes the
upstream preflight listener again while the write command owns the UI thread.

The saved wire trace shows the Move listener requesting the same old/new local path, including
directory URIs that differ only by a trailing slash. Upstream passes `VFileMoveEvent.oldPath` to
the rename-name resolver, so there is no real target in this request. This also occurs on native
Undo/Redo. The resulting no-op still waits behind compiler work while holding the UI thread.

`PreflightedRenames` decorates the public JSON-RPC endpoint factory. A request consisting entirely
of local no-op moves completes immediately with no additional edit. While `XtcRenameEdit` applies
an approved physical operation, a connection-local `ScopedValue` also recognizes that exact
old/new pair. Its lifetime ends automatically on return or exception; it does not leak to another
client or an unrelated worker. The existing version/graph/path guards run before entering it.
All notifications, including did-rename/watch notifications, are forwarded unchanged. Real
unowned renames, mixed batches, non-file URIs and invalid URIs retain normal server handling.
No capabilities or generic VFS listeners are disabled.

Eight unit regressions cover a permanently pending remote reply, the actual production launcher,
service-proxy dispatch, scope nesting/failure cleanup, client/thread separation, malformed/mixed
requests, notifications
and cancellation. Focused native run `run-11532602109763278599` passes START and all 16 selected
cases, with zero IDE errors, including Move/Undo/Redo and the refresh stress. Its server trace still
receives 14 will-rename requests and 84 did-rename notifications: file-operation support remains active.
The later 172-case continuation also has zero IDE errors/freeze dumps, including the same-path
Move/Undo/Redo paths under longer-session load. This is a narrow local repair, not a claim that
upstream's generic UI wait has disappeared: a genuine basename rename
replayed outside the approved scope still takes that path. Keep the original dump artifacts under
`XtcCompilerPlaybook-run-5080670791887423538/log/threadDumps-freeze-*` and the long-session gate.
This is separate from UP17's large-file interval-tree cost and UP27's refresh scheduling error.
