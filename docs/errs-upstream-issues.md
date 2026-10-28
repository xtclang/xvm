# Upstream issues affecting Ecstasy language support

This is the upstream dependency register for `lagergren/errs`, updated on 2026-10-18.
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

Upstream pull requests and issues filed so far are listed under
[Upstream pull requests](#upstream-pull-requests-2026-10-18), and each affected register row links
its pull request. Filing further reports remains a separate, explicitly authorized action.
[XVM PR #653](https://github.com/xtclang/xvm/pull/653) is our process-lifecycle fix, not an LSP4IJ
upstream report. LSP4IJ #888 is a different IDE-freeze issue and must not be used as the tracking
issue for UP01.

Local upstream repair work is now authorized in `~/src/lsp4ij`, on the unpushed branch
`lagergren/local-lsp-repairs`, based on upstream `main` at `4796cf99` (0.21.1-SNAPSHOT).
The first ten local commits are listed below, with regression tests; the additional October 7
batch is recorded separately below. These are local repairs,
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
if the local snapshot passes. The later repairs below extend this historical checkpoint;
UP20/UP29 concern the test Driver, UP15 the server's LSP4J and UP17 the platform.

### Additional local LSP4IJ repairs, 2026-10-07

The same unpushed branch now contains separate issue-specific repairs and regression tests for
the remaining LSP4IJ-owned entries. Follow-up commits retain corrections found during combined
testing. None changes XVM's released dependency or removes a production bridge.

| Issue | Local implementation and remaining boundary |
| --- | --- |
| UP02 | Native pre-save `willSave` notifications preserve the save reason and captured saved text. Unsupported `willSaveWaitUntil` is no longer advertised; applying its returned edits before persistence remains unimplemented. |
| UP03 | Resource renames honor the destination directory and escaped basename, preserve children, and handle collisions before mutation. Generic before-change VFS preflight still waits on the UI thread; general transaction rollback is not implemented. |
| UP04 | Workspace/rename edits validate document versions, connection and document lifetimes, modification stamps, newly opened targets, and confirmation requirements before mutation. Versions cannot collide after reopening a document on the same connection. |
| UP05 | Dynamic external watch roots have shared leases, scoped refresh, missing-root discovery and disposal. Registration replacement cannot reuse an older refresh generation. No filesystem-root traversal is introduced. |
| UP09 | Open/change/close notifications share ordered dispatch. Folding replies validate their original document lifetime without taking a read lock in the reply callback. |
| UP10 | Signature help uses current overload/parameter metadata, including offset labels and absent parameter metadata. |
| UP11 | Resolved actions apply in guarded native Undo commands. Resolve and command dispatch stay on the original connection, off the UI thread; command fallback edits use the same ownership checks. Completion includes the follow-up command response. |
| UP12 | Temporary formatting connections are released when their last scope ends; existing connections and documents opened or adopted by another feature retain ownership. |
| UP14 | Dependency analysis invalidates cached and pending semantic results even when consumer text is unchanged. Cache lookup is atomic with cancellation; nonphysical PSI retains stamp-based caching. |
| UP18 | Native snippet expansion respects `InsertTextMode.AsIs`, exact whitespace, placeholder navigation and Undo. |
| UP19 follow-up | Individual file Rename/Move now shares the deferred reconnection used for directories. The old URI is retired before reconnecting current editor content; no document connection is awaited under the VFS write lock. |
| UP21 | Only transaction-owned closed buffers are persisted after apply, Undo and Redo. Resource recreation resolves the current buffer again; unrelated drafts and current editor ownership are preserved. |
| UP25 | Static LSP 3.18 document-content providers resolve supported non-file URIs to read-only views, with refresh, cancellation, coalescing and connection disposal. Native X254 assertions pass on October 8; the released dependency still needs its fallback. |
| UP26 | Inline requests forward explicit invocation and the selected completion's actual insertion text/range, including decorated lookup items. Native X257 assertions pass after the October 8 session-restart repair, but the neighboring X256 typing failure blocks replacement acceptance. |

UP02 and UP03 are partial repairs. A synchronous save callback cannot safely emulate
`willSaveWaitUntil` by blocking the UI, and generic VFS preflight needs an asynchronous native
refactoring entry point. Do not remove those constraints based on the other fixes.

Combined testing exposed two lock-order problems: folding response validation initially took
a read lock while a typed handler held the write lock; a file-rename callback then waited for
document reconnection under the VFS write lock. Their captured thread dumps are retained under
`/private/tmp/lsp4ij-remaining-2026-10-07/`. The aborted runs are not passing evidence, even where
Gradle's test logger printed a success summary before reporting the terminated worker.

Final validation at local head `614a4787629fc75afbf667dde1c8f04cf7e7f900`:

- The full upstream suite discovered 611 tests: **608 passed, 3 existing ignored tests,
  zero failures or errors**, across 119 JUnit XML suites. The ignored tests are the two
  Docker Bake inline-completion cases and the Perl multiple-item inline-completion case,
  already marked unstable when run with the full suite. No new exclusion was added.
- The forced run used `./gradlew test --rerun-tasks --no-build-cache --max-workers=2`
  with JDK 21 and the project's IntelliJ 2024.2 baseline. A temporary Gradle init script
  set each test task's `java.io.tmpdir` to `file('build/repair-test-tmp').canonicalPath`
  to avoid macOS `/var` versus `/private/var` VFS path mismatches. The configuration cache
  was reused. XML receipts and counts are retained in
  `/private/tmp/lsp4ij-remaining-2026-10-07/final-full-suite/`.
- `buildPlugin prepareSandbox` also passed. The resulting
  `~/src/lsp4ij/build/distributions/lsp4ij-0.21.1-SNAPSHOT.zip` has SHA-256
  `e5e79a040ef1652b2450ac739fd42b43d33c494630604ecc6082bde23e9435f7`.
- These are upstream fixture tests and a plugin build, not XVM GUI replacement acceptance.
  The branch and artifact remain local and unpushed.

The local plugin remains an experimental replacement. Every production bridge still requires
its individual native acceptance gate below, including unchanged-consumer refresh, real
Move/Undo/Redo, closed-file formatting, and the two currently partial native feature scenarios.

### Save All notification continuation, 2026-10-08

Local LSP4IJ `30a5fea4` moves `willSave` dispatch to the native save-veto callback, always
returning permission to save. The earlier listener covered individual saves but missed both
manual and automatic Save All on IntelliJ 2024.2. Both missing-notification tests fail before
this repair. Registered-extension discovery, save ordering and synchronizer disposal pass
21 focused tests; the full upstream build then passes **613 tests, with 3 existing ignored
tests and no failures/errors**. `buildPlugin prepareSandbox` also passes. XML is retained under
`/private/tmp/lsp4ij-save-2026-10-08/`.

UP02 asynchronous `willSaveWaitUntil` remains disabled. A native regression demonstrates that
a save veto returns to its caller with an unsaved document; asynchronously retrying it later
would break save-before-Build/Run ordering. UP03 generic asynchronous VFS preflight and rollback
also remain open. The user confirmed that IntelliJ Platform changes remain deferred and that
local dependency acceptance should continue. Existing XVM constraints and guarded file-operation
entry points remain installed; neither issue is declared fully repaired.

Shipping constraint reaffirmed October 8: neither the LSP4J nor LSP4IJ source checkout may become
a prerequisite for the Ecstasy plugins. Keep released dependency defaults and repository-owned
server/client compatibility code. The optional local builds below are upstream experiments;
their acceptance does not itself authorize removing a production bridge or changing a pin.

### Reply and virtual-document lifecycle acceptance, 2026-10-08

Native acceptance against the optional local LSP4IJ build exposed two additional problems.
Reply validation accessed PSI from a transport thread. The first repair (`bd0bdf6b`) moved
validation into a read action, but full-suite acceptance exposed a deadlock when an on-type
formatter consumed the reply while holding a write action. Local `81229358` instead validates
captured document/VFS state without acquiring a read lock. It uses the document's source stamp,
so committing the same dirty text does not discard the reply. Formatting still rejects changed
source, but no longer cancels just because reopening a closed buffer publishes diagnostics.

Local `1712c0b2` closes virtual-library editors under a write action before invalidating their
files. Routing and pending requests retire immediately; background disposal never waits for the
EDT. This removes the invalid-PSI error observed during native server restart.

The final upstream suite executes **622 tests: 619 pass, 3 existing skips, no failures/errors**.
The earlier full-suite run's quick-fix highlighting timeout remains recorded; that test passes
in isolation and in the subsequent full suite. The write-lock deadlock run was terminated and
is not counted as successful. XML and the focused regression receipts are under
`/private/tmp/lsp4ij-reply-lifecycle-2026-10-08/`; the deadlock dump is
`/private/tmp/lsp4ij-reply-write-lock-2026-10-08.txt`.

Native `run-10005489111273893996` passes X254's virtual-library gate: actual navigation,
read-only contents, monikers, server refresh and reopening after restart. START/X255/X256 also
pass, with zero IDE errors. X139 save formatting passes in `run-998605254663663043` and
`run-9561513593504862699`. None of these experiments removes the released dependency's
fallback or its recorded partial coverage.

X257 then exposed a missing platform session-restart hook: a popup's new selection could be
accepted without reaching the provider again. Local `58f5e2ac` restarts suggestions for direct
invocation and lookup changes. Two contract assertions fail before the fix; all six enabled
inline tests pass afterward, with the three existing ignored tests unchanged.
`run-1350057583691146504` passes the native X257 assertions for invocation, alternatives,
cycling, exact insertion/Undo and the selected popup text/range, plus X258; zero IDE errors.
The shared catalog still labels X257 partial for the shipping 0.21.0 limitation. Subsequent
reports expose opt-in replacement-gate results separately from those catalog statuses.

The combined experimental `run-1508827283781484369` passes START, X139, X181/X185/X202/X213,
X254's additional native assertions and X255, then fails X256 at its actual 30-second deadline
waiting for continued-typing ghost text. The server completed that request in about 217 ms;
there were zero IDE errors. This remains an unresolved local-upstream acceptance failure,
not a pass, and prevents declaring the experimental inline replacement ready.

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
protocol snapshot, not a change to the production dependency. Both repairs were later filed
upstream: UP06 as LSP4J #990, fixed by #991, and UP15 as #996, fixed by #997 with a different
design than `13dfc35b` (see [UP15](#up15-malformed-parameters-are-not-malformed-json)). IntelliJ
Platform and VS Code upstream repairs are deferred by the user's instruction.

### Optional LSP4J integration acceptance, 2026-10-08

A temporary init script resolves the locally published 1.1.0-SNAPSHOT only for the experiment;
no repository or version-catalog change is retained. The repaired library passes **51 XVM
protocol/lifecycle tests and 82 packaged stdio/process tests**, with no failures/errors/skips.
The stdio bad-parameter assertion temporarily expects `InvalidParams`; normal requests after
the error still succeed. Hash comparisons verify that the packaged server contains the repaired
message decoder and diagnostic report class from the local publication.

LSP4IJ also passes its full **622-test suite (619 pass, 3 existing skips)** with that library
bundled. The native combined run above bypasses only our `DiagnosticReportJson` registration;
configuration and other compatibility adapters remain enabled. Its diagnostic/lazy-refactoring
cases pass without ambiguous-union failures. The X256 failure is retained separately.

Both the JSON adapter registration and the shipping `ParseError` assertion are restored
byte-for-byte before released-dependency checks. XML, class hashes and restoration hashes are
under `/private/tmp/lsp4j-local-acceptance-2026-10-08/`. The first two temporary init scripts
failed dependency resolution before tests ran; their stale XML is not acceptance evidence.

### Released-dependency verification, 2026-10-08

The restored shipping configuration passes **82 packaged stdio/process tests and 23 client
compatibility tests**, with zero failures/errors/skips. The latter exercise diagnostic JSON,
guarded rename preflight, startup document messages and lazy code-action messages. The server
JAR's decoder and diagnostic-report class match the cached released LSP4J 1.0.0 classes.

Native `run-6190916343008780922` uses released LSP4IJ 0.21.0, no local plugin override and no
replacement gates. START plus X103/X130, X139, X181/X185/X202/X213 and X255/X256/X258 pass;
X254/X257 retain their two explicit shipping limitations. There are **zero failures and zero
IDE errors**. In particular, the experimental continued-typing failure does not reproduce in
this released-dependency run. All production bridges remain installed; no fork is required.

XML, the native report and packaged-class hashes are copied under
`/private/tmp/lsp-shipping-acceptance-2026-10-08/`. SpotlessCheck and `git diff --check` pass.

### Local plugin acceptance, 2026-10-06

XVM checkpoint `6ece0efab` adds the explicit `-Plsp4ijPlugin=/absolute/built/plugin/directory`
override, using Gradle `localPlugin` and Starter's local installation. The Marketplace pin stays
0.21.0. See the [command and input contract](../lang/intellij-plugin/testing.md#lsp4ij-in-tests).
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

## Upstream pull requests (2026-10-18)

Fifty LSP4IJ pull requests had been filed from `lagergren/lsp4ij` by 2026-10-26, and all are merged:
ten on 2026-10-25 and 2026-10-26 (UP30-UP37, UP40 and UP42), and the last,
[#1771](https://github.com/redhat-developer/lsp4ij/pull/1771) (UP14's other half), on 2026-10-27. Eight LSP4J pull requests have been filed from `lagergren/lsp4j`; five are merged (#991, #999, #1003, #1004 and #1005), and #997 (UP15), #1008, #1004's follow-up, and the draft #1009 (UP41's LSP4J part) are open. The latest LSP4J release is still 1.0.0 (2026-02-10). The latest
LSP4IJ release is still 0.21.0 (2026-08-31), so merged fixes are on upstream `main` only. The next is
0.30.0: `main` is 0.30.0-SNAPSHOT since 2026-10-28 (`873f439e`), and the one open item left on its
milestone is #1651 (our #1788, #1790 and #1791 merged into it the same day). XVM pins
0.21.0, and every bridge stays until a release contains the fix and native acceptance passes with
the bridge removed.

| Pull request | Entry | State | Scope |
| --- | --- | --- | --- |
| [#1757](https://github.com/redhat-developer/lsp4ij/pull/1757) | UP30 | Merged 2026-10-26 (`b135672e`), unreleased | Pause DAP debug sessions: `DAPDebugProcess` implements `startPausing()`, which pauses the first thread the debug adapter reports, and each child session's. XVM's X308 passes natively in IntelliJ with a plugin built from it and fails with one built from `main` (`ef2b18c3`). |
| [#1759](https://github.com/redhat-developer/lsp4ij/pull/1759) | UP32 | Merged 2026-10-26 (`78e4a2fe`), unreleased | Breakpoints that don't suspend as DAP logpoints; the "Evaluate and log" expression evaluated and logged at stops; a breakpoint that doesn't suspend continues the program instead of leaving it stopped while IntelliJ shows it running. Natively, with XVM's adapter (no logpoints), `main` leaves the program stuck and the pull request logs `21` and runs it to its exit. |
| [#1763](https://github.com/redhat-developer/lsp4ij/pull/1763) | UP37 | Merged 2026-10-26 (`6a31cee0`), unreleased | A DAP expression code fragment is physical when IntelliJ asks for one, as for an expression editor's document, so IntelliJ 2026.2's inline logpoint editor no longer fails `LocalInspectionsPass`'s assertion with an IDE error. Natively, a breakpoint with Suspend unchecked and an expression to log reports the error with a plugin built from `main` and none with one built from the pull request. Found while testing #1759; independent of it. It also ends the Document/PSI mismatch of an edited Evaluate Expression (X320). |
| [#1761](https://github.com/redhat-developer/lsp4ij/pull/1761) | UP33 | Merged 2026-10-26 (`50d7defb`), unreleased | The exception hint from `exceptionInfo` when the adapter supports it: the exception id (or type name), description and the first ten lines of the stack; the stopped event's text otherwise. Not checked natively: XVM's adapter doesn't answer `exceptionInfo` yet (R9). It overlaps the hint code that #1758 and #1759 change; whichever lands second needs a small rebase. |
| [#1760](https://github.com/redhat-developer/lsp4ij/pull/1760) | UP34 | Merged 2026-10-26 (`4c777ba5`), unreleased | A Condition column for exception breakpoint filters with `supportsCondition`, kept with the filter settings and sent as `filterOptions` to an adapter with `supportsExceptionFilterOptions`. Not checked natively. |
| [#1768](https://github.com/redhat-developer/lsp4ij/pull/1768) | UP35 | Merged 2026-10-26 (`42f1f16e`), unreleased | Lazy DAP variables (`presentationHint.lazy`) show their placeholder and a "Show value" link, which asks for the variable's `variablesReference` and shows the first variable in its place; no variable, or a failed request, shows the error and keeps the link. Not checked natively until the adapter makes computed properties lazy (R10). |
| [#1769](https://github.com/redhat-developer/lsp4ij/pull/1769) | UP36 | Merged 2026-10-26 (`9fab871f`), unreleased | A "Show Values as Hexadecimal" toggle in the Variables and Watches views' menu, for an adapter with `supportsValueFormattingOptions`; while it is on, `variables`, `evaluate` and `setVariable` carry the format `{hex: true}`. Merges cleanly with #1768 in either order. Not checked natively until the adapter formats values (R10). |
| [#1770](https://github.com/redhat-developer/lsp4ij/pull/1770) | UP42 | Merged 2026-10-26 (`642d0e9d`), unreleased | A variable with more than 100 indexed children is read a page at a time: its named children first, if it has any, then 100 indexed ones at a time (`filter`, `start`, `count`) behind IntelliJ's own `...` node (`XCompositeNode.tooManyChildren(remaining, addNextChildren)`); a failed `variables` request shows its error in the node. Checked natively against XVM's X318 at `97504678` (2026-10-26, `run-9236570344305345016`): a page of 100, the `...` node with 150 more, and all 250 once opened. |
| [#1771](https://github.com/redhat-developer/lsp4ij/pull/1771) | UP14, other files' changes | Merged 2026-10-27 (`7fa1794f`), unreleased | Hover, definition, declaration, type definition, implementation, references, completion, rename and the hierarchy prepares no longer reuse an answer after another file changes: `AbstractLSPDocumentFeatureSupport` takes `cancelWhenOtherFileModified`, which also outdates a cached result when the project's `PsiModificationTracker` count changes, and those ten supports opt in. Features driven by the file itself or by refresh requests keep the file stamp. Its new `FeatureCacheOtherFileChangeTest` fails without the fix for hover and definition; 502 related tests pass. Not checked natively: `XtcLanguageClient`'s retirement hides the bug. |
| [#1758](https://github.com/redhat-developer/lsp4ij/pull/1758) | UP31 | Merged 2026-10-26 (`ab49a207`), unreleased | Skip a DAP exception hint on an editor closed since the stop: `doShowErrorHint()` returns for a disposed editor, instead of failing with `DisposalException` "Editor is already disposed". Its new test fails without the check. Natively, an XVM X307 that closes its file right after the stop reports the IDE error with a plugin built from `main` (`ef2b18c3`) and none with one built from the pull request. |
| [#1766](https://github.com/redhat-developer/lsp4ij/pull/1766) | UP40 | Merged 2026-10-25 (`44485010`), unreleased | LSP4IJ announces `supportsInvalidatedEvent` and handles the DAP `invalidated` event: `threads` refreshes the threads panel, `stacks` loads the stopped stacks' frames again, which LSP4IJ keeps from the stop, and the Frames, Variables and Watches views are rebuilt (`XDebugSession.rebuildViews()`). Not checked natively: XVM's adapter sends `invalidated` only with bounded evaluation's tier 2, not built yet. `DAPClientInvalidatedTest`, 6 tests; `./gradlew check` 749 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. |
| [#1696](https://github.com/redhat-developer/lsp4ij/pull/1696) | UP01 | Merged 2026-10-14 (`12703a33`), unreleased | Prevent orphaned server processes during startup. The maintainer [suggested](https://github.com/redhat-developer/lsp4ij/pull/1696#discussion_r4272352746) a separate exception type, so a rejection by a stopped provider can never reach an error popup. [Our reply](https://github.com/redhat-developer/lsp4ij/pull/1696#discussion_r4307891867) explains that it cannot today: the stop cancels the attempt's `initializeFuture`, so the wrapper's error handler never runs, as a test confirms. The exception type is ready on the unpushed branch `pr-up01-stopped` (`2a19bb28`) if they still want it. |
| [#1699](https://github.com/redhat-developer/lsp4ij/pull/1699) | UP22 | Merged 2026-10-16 (`eb45e3fc`), unreleased | Stop pull diagnostics after document disposal; publication is serialized with disposal. |
| [#1700](https://github.com/redhat-developer/lsp4ij/pull/1700) | UP27 | Merged 2026-10-16 (`180a1b0f`), unreleased | Batch workspace editor refreshes per connection and feature, cancelled with their client. |
| [#1698](https://github.com/redhat-developer/lsp4ij/pull/1698) | UP04, versions | Merged 2026-10-19 (`ff834ce1`), unreleased | Validate document versions before applying workspace and rename edits. Both maintainer review comments are addressed in `c08cc9f8`: the three reads it adds are cancellable, and the validation moved to `LSPIJUtils.validateWorkspaceEditVersions`. Request snapshots and connection lifetimes are deliberately left out. |
| [#1707](https://github.com/redhat-developer/lsp4ij/pull/1707) | UP12 | Merged 2026-10-19 (`2680f3b1`), unreleased | Format documents with no open editor, connecting closed buffers for the request only. CodeRabbit's idle-shutdown comment is fixed in `d40215e5`, with a regression test. |
| [#1710](https://github.com/redhat-developer/lsp4ij/pull/1710) | UP08 | Merged 2026-10-19 (`cc8b5d1e`), unreleased | Keep explicit null settings in `workspace/didChangeConfiguration`; other messages still omit unset optional fields. |
| [#1718](https://github.com/redhat-developer/lsp4ij/pull/1718) | UP04, snapshots | Merged 2026-10-19 (`f75e403a`), unreleased | The follow-up #1698 left out. It checks rename and `workspace/applyEdit` replies against state captured at request time: the connection, plus the opened documents' synchronizers and modification stamps. It refuses an unversioned rename reply after typing during the request, a version from a closed document applied to its reopened document (versions now keep increasing across reopen on one connection), and an edit reaching a disposed client. All three tests fail on `main` and pass with the fix. Documents that were never synchronized keep #1698's behavior. Code-action edits are not covered. |
| [#1720](https://github.com/redhat-developer/lsp4ij/pull/1720) | UP03, file-name URIs | Merged 2026-10-19 (`20df5666`), unreleased | `LSPFileListener.getUri` resolved a file name as a URI reference, so `willRenameFiles` new URIs and `didRenameFiles` old URIs broke for names with a space or `%` (an exception, and no notification), `#` (a fragment) or `:` (a scheme). The name is now one escaped path segment, as `File.toURI` escapes it. Both new tests fail on `main`. Upstream #1690 fixed the move destination, the other half of our local UP03 fix. |
| [#1721](https://github.com/redhat-developer/lsp4ij/pull/1721) | #1719 part 2 | Merged 2026-10-19 (`523f397e`), unreleased | `LSPDiagnosticsApplier` dropped diagnostics published while `GROUP_ID` was -1, before the first highlighting registered `LSPDiagnosticsPassFactory`, and nothing applied them until the set changed. The refresh is now kept and run when the factory registers. #1717's fixture refresh workaround is removed. The new test fails on `main`. |
| [#1723](https://github.com/redhat-developer/lsp4ij/pull/1723) | Pull `relatedDocuments` (fixes #1722) | Merged 2026-10-19 (`d9d45d84`), unreleased; #1722 closed | `refreshPullDiagnostic` applies the related documents of both report kinds; before, it dropped them from a full report. `RelatedDocumentsTypeAdapterFactory`, registered in `JSONUtils.configureCompatibilityAdapters`, lets LSP4J 1.0.0 parse such replies until a release includes LSP4J #991. With neither part, or only the handling fix, all three new tests fail; with only the adapter, the full-report test fails; with both, all pass. 579 tests, 0 failures. |
| [#1724](https://github.com/redhat-developer/lsp4ij/pull/1724) | UP09, notification order | Merged 2026-10-19 (`fff54b49`), unreleased | `sendNotification` waited only for the initialized server, and a `CompletableFuture` completes its dependents in reverse order of registration, so notifications sent during initialization reached the server last to first. `didOpen` was sent on the common pool, not through `sendNotification`, and raced `didChange` and `didClose`. Each notification is now chained on the previous one, and `didOpen` goes through `sendNotification`. All three new tests fail on `main`; with either half alone at least two fail. 583 tests, 0 failures. Not included: a document connected during a write action requests `didOpen` only when a feature needs it, so a `didChange` or `didClose` before that has no `didOpen`; `didChangeConfiguration` and `didChangeWorkspaceFolders` bypass the dispatcher. |
| [#1725](https://github.com/redhat-developer/lsp4ij/pull/1725) | UP09, folding lines | Merged 2026-10-19 (`2a35aa6d`), unreleased | A folding range with a start line after the last line, or a negative end line, threw `IndexOutOfBoundsException` in `LSPFoldingRangeBuilder`, and no range of the file was folded in that pass. Lines are now clamped in both directions, and LSP lets clients ignore such ranges. Both new tests fail on `main`. The rest of UP09's folding bridge, stale replies, is mostly not an upstream defect: the cache is keyed to the file stamp and an edit cancels the folding pass. Requests sent before a queued `didChange` (only `onTypeFormatting` flushes) are unfiled. |
| [#1726](https://github.com/redhat-developer/lsp4ij/pull/1726) | UP21 | Merged 2026-10-19 (`88b9ece3`), unreleased; Undo and Redo added in `e1955178` after the maintainer asked, and his question about completion answered (completion applies its edits with `applyEdits`, which does not save) | Workspace edits changed documents without an editor in memory only, so the server read the old file until auto-save. This is #1225, fixed by #1235, kept by `54bfc7ac` and switched off for every workspace edit by `0c905196`, which added the `will*` file operations applied during a VFS event. Such documents are saved again once the edit is applied, unless they had unsaved changes before it; the `will*` path stays unsaved. Both save tests fail on `main`. After review, `e1955178` saves the same documents after Undo and Redo through two global undoable actions around the edit; the new test fails with the actions registered but not saving. 586 tests, 0 failures. |
| [#1727](https://github.com/redhat-developer/lsp4ij/pull/1727) | CI flake from #1717 | Merged 2026-10-19 (`6015d80a`), unreleased; CodeRabbit's nitpick addressed in `3c04a98a`: the test cancels only once the pending load has created its request (`LSPLazyCodeActions.hasRequest()`, `@TestOnly`) | `testCodeActionsLoadAgainAfterARequestWasCancelledBeforeItWasSent` failed on `main` after #1721 merged (Ubuntu, "server must stop"). `stop()` reports `stopped` only if nothing started the server again during the shutdown; calling `getInitializedServer()` after `stop()` reproduces the failure. The test accepts `stopped` or `starting`; after CodeRabbit's review, `23a133f9` holds `initialize` (`MockLanguageServer.holdInitialize()`) until the request is cancelled instead of relying on a delay. 580 tests, 0 failures. |
| [#1728](https://github.com/redhat-developer/lsp4ij/pull/1728) | Document lifecycle (#1724's follow-up) | Merged 2026-10-20 (rebased, `360df023`..`f827bb0d`), unreleased; CodeRabbit's findings fixed in `f252ce85` (the changes that follow a captured text's open are sent from a pooled thread, not under `documentsLock`, where waiting for read access could deadlock), `2006122e` (a failed open reaches the caller as a failure, not a connected server), `aebe573c` (no `didChange` or `didClose` after a failed `didOpen`), `72dac938` (a flush before the open opens the document, so on-type formatting finds its text) and `fc7c86ba` (`getInitializedServer()` passes on a failed initialization) | A document connected during a write action requests `didOpen` only when a feature needs it; until then its changes went out as `didChange` before any `didOpen`, a later `didOpen` with a captured text sent older text after newer changes (the server kept `first` while the editor showed `second`), and `didClose` was sent without a `didOpen` and twice. Changes now wait for `didOpen`, which either contains or precedes them; `didClose` is sent once and only after `didOpen`; nothing follows it. A package-private `sendNotification(initializedServer, fn)` lets the synchronizer queue under its monitor without calling the synchronized `start()`. All four new tests fail on `main`; two existing tests adapted. 589 tests, 0 failures. CodeRabbit's review hit its limit and was requested again. |
| [#1730](https://github.com/redhat-developer/lsp4ij/pull/1730) | #1719 part 1, UP07 second half | Merged 2026-10-19 (`6726994c`), unreleased; #1719 closed; CodeRabbit's finding fixed in `17a9c420`: a list that repeats one diagnostic and drops another is a change | An unchanged diagnostics update rebuilt the lazy quick fixes and cancelled their pending `textDocument/codeAction` requests, while the editor kept the old objects. `update` now builds the map only when the set changed (`isDiagnosticsChanged` compares the diagnostics as sets, every field). The new tests fail on `main` (2 requests instead of 1). 587 tests, 0 failures. |
| [#1731](https://github.com/redhat-developer/lsp4ij/pull/1731) | UP21 remainder | Merged 2026-10-19 (`bdae7b99`), unreleased; CodeRabbit's finding fixed in `a3dfb595`: the `did*` notifications of the operation wait for the save | The documents without an editor that a `willRenameFiles`, `willCreateFiles` or `willDeleteFiles` edit changes were left unsaved, since `AbstractLSPFileListener` applies those edits during the VFS event, where nothing can be saved. They are now saved after the event with `invokeLater`, and the operation's `did*` notifications are sent after that save, so the server reads the edited text when it is told of the rename. Undo and Redo of the operation do not save them. |
| [#1732](https://github.com/redhat-developer/lsp4ij/pull/1732) | CI flake from #1721 | Merged 2026-10-20 (`6e4f9a80`) | `testDiagnosticsPublishedBeforeThePassIsRegisteredAreShown` failed twice on `main`, on macOS only (`expected:<[published early]> but was:<[]>`). `OpenedDocument.updateDiagnostics` stores the diagnostics before it requests their refresh, and the test waited for the stored diagnostics, so it could register the pass factory with nothing pending. The test now waits for the pending refresh (`LSPDiagnosticsApplier.hasPendingRefreshes()`, `@TestOnly`) and then for the shown diagnostics. With the refresh request delayed by a second, the old test fails as on CI and the new one passes; with #1721's fix reverted the new test fails. 595 tests, 0 failures. |
| [#1733](https://github.com/redhat-developer/lsp4ij/pull/1733) | UP11 | Merged 2026-10-20 (`c743ecd2`), unreleased; CodeRabbit's `DeleteFile`/`CreateFile` finding fixed in `8989b36a` (validation also checks their targets, for rename and `workspace/applyEdit` too); its request to refuse targets without captured state declined, as #1718's documented boundary | A code action resolved when it is invoked had its edit applied undo-transparently after the intention's command, so Undo also undid what came before it, and to text changed since the resolve request. The edit is now applied in a command of its own, named after the action, and validated against a `WorkspaceEditSnapshot` captured before the request, as for rename and `workspace/applyEdit`. `ResolvedCodeActionTest` fails on `main` for both; `WebFilterQuickFixTest`'s resolved edit had a wrong version 0 and now sends an unversioned edit. 607 tests, 0 failures. |
| [#1734](https://github.com/redhat-developer/lsp4ij/pull/1734) | UP10 remainder | Merged 2026-10-20 (`94f48a75`), unreleased | Feature: advertises `signatureInformation.activeParameterSupport`, highlights a signature's own `activeParameter`, and renders each overload from the latest signature help (matched by label) instead of the reply that opened the popup. Three new `LSPParameterInfoHandlerTest` cases fail on `main`. 607 tests, 0 failures. |
| [#1735](https://github.com/redhat-developer/lsp4ij/pull/1735) | UP07 first half | Merged 2026-10-20 (`6ad24b50`), unreleased | Pulls never sent `previousResultId`, so a server could not answer with an `unchanged` report. The synchronizer keeps the result ID of the last report it applied, full or unchanged, and sends it with the next pull; a discarded report does not change it. `PullResultIdTest` fails on `main` (`expected:<result-1> but was:<null>`). 604 tests, 0 failures. |
| [#1736](https://github.com/redhat-developer/lsp4ij/pull/1736) | #1724's remainder | Merged 2026-10-20 (`9d1e5cd7`), unreleased | `didChangeConfiguration` and `didChangeWorkspaceFolders` bypassed the notification queue: a configuration change while the server initialized was sent before the `initialize` response, and a newly found folder reached the server after the `didOpen` of the document that found it. Both now go through `sendNotification`; the folder is queued before the `didOpen`. `WorkspaceNotificationOrderTest` fails on `main` in 3 of 3 runs for both. 605 tests, 0 failures. |
| [#1738](https://github.com/redhat-developer/lsp4ij/pull/1738) | Fix for #1729 | Merged 2026-10-20 (`4ac15244`), unreleased; CodeRabbit's full review (requested by Angelo) found that the pending changes were sent only to the servers matched so far, none while the matching was still running; fixed in `03f6af66` by sending them to every started server that has the file open (616 tests, 0 failures) | A request for an opened document now waits until the changes made before it have been dispatched (`DocumentContentSynchronizer.getChangesSent()`); changes still waiting for the commit are sent then, from a pooled thread, since `connect` holds `documentsLock` and sending takes the wrapper's monitor. A caller holding a write action, as `charTyped` does in #1729's folding example, does not wait for `didOpen`, so `LanguageServiceAccessor.getLanguageServers` sends the pending changes on the calling thread when it can read the document, as on-type formatting already did, and the request waits only for their dispatch. A failed `didChange` does not fail the request. `RequestAfterChangeTest` (pooled thread and write action) fails on `main`, where the hover reaches the server while the `didChange` is held. In unit-test mode a change is sent at once, so the tests cover the wait for its dispatch, not a change waiting for its commit. 616 tests, 0 failures. |
| [#1729](https://github.com/redhat-developer/lsp4ij/issues/1729) | Issue: requests before a pending `didChange` | Closed 2026-10-20 by #1738 | Changes are sent once the document is committed and the dispatcher writes them, but requests wait for neither, so a request right after an edit can be answered for the previous text. Only `onTypeFormatting` calls `flushPendingChanges()`. Asks whether document requests should wait for pending changes, centrally where they wait for `didOpen`; offers a pull request. |
| [#1739](https://github.com/redhat-developer/lsp4ij/pull/1739) | UP03, resource renames | Merged 2026-10-20 (`25f7b3cc`, `9fa2e61c`), unreleased; Angelo asked whether a refused rename stops the whole edit, and how VS Code behaves: it does, as in VS Code, which reports `applied: false` without rolling back; CodeRabbit's three findings fixed in `0f0f4c1b` and accepted: the self-nesting check now uses the destination's directory (a rename to its own URI, and a Windows case-only directory rename, were refused), and a refused rename no longer throws. It stops the edit, which still saves the earlier editorless changes, and `workspace/applyEdit` answers `applied: false` with the reason, through a new internal overload that the `will*` listener uses too (an exception there reached IntelliJ's VFS event, which logs it as an error) | `LSPIJUtils.applyRenameFile` renamed a `RenameFile` resource inside its own directory and ignored its options, although LSP4IJ advertises the `rename` resource operation. On `main` a move to another directory renamed the file in place and dropped the edits for its new URI, and a rename onto an existing file failed (logged only), after which the edits for the new URI changed the file that was already there. The resource now moves to the directory of `newUri` (created if missing, as by VS Code), `overwrite` and `ignoreIfExists` are honored, and a rename that cannot be applied stops the edit. Ported from our local `d99567e3`, with case-only renames and missing directories added. A rename that also moves reaches the server as two renames, since the VFS has no combined operation. `WorkspaceResourceRenameTest`: 8 of its first 10 tests fail on `main`; 14 tests after review. 628 tests, 0 failures. |
| [#1740](https://github.com/redhat-developer/lsp4ij/issues/1740) | Issue: UP03's UI wait | Open | `a0730c2f` sends the `will*` requests from `prepareChange` with a 5 s timeout, and documents that IntelliJ calls it on a background thread. That holds for refresh events only: `will*` requests are sent for the other events, for which IntelliJ 2024.2's `AsyncEventSupport` calls `prepareChange` in the write action that changes the file. On `main`, with `willRenameFiles` answered after 2 s, a rename's write action on the EDT took 2,129 ms and a move's 2,048 ms. Suggests sending the request before the write action, from the rename and move entry points, and offers a pull request. |
| [#1742](https://github.com/redhat-developer/lsp4ij/pull/1742) | UP05, fix for #1741 | Merged 2026-10-20 (`8f550f08`), unreleased; CodeRabbit's four findings fixed in `85bbcac3` and accepted (links above a root followed, cheap refreshes between full passes, registrations tied to their connection, normalized glob bases); a macOS CI race fixed in `523b22f9`: an explicit `refresh()` no longer joins a pass that started before it | `FileSystemWatcherManager` gives the explicit roots outside the project (a `RelativePattern` base, an absolute glob prefix) to a new `FileSystemWatchRoots`, which watches each natively (`LocalFileSystem.replaceWatchedRoots`), loads its directories and refreshes them every 2 seconds, observing a missing root through its nearest existing parent; one cancellable read per directory, no symbolic links. Pure globs and roots inside the project are unchanged. `stop()` now drops the stopped server's watchers, a late registration callback of a stopped connection adds none, and `dispose()` releases the roots. Ported from our local `85da59ab`/`d2ce17f7`/`2096d5e6`, with roots inside the project excluded. `WatchedFilesTest` (2) fails on `main`; `FileSystemWatchRootsTest` adds 12. Native watching does not run in tests, so they show the refresh. 628 tests, 0 failures. |
| [#1741](https://github.com/redhat-developer/lsp4ij/issues/1741) | Issue: UP05, and watchers after a restart | Closed 2026-10-20 by #1742 | Changes in a watched directory outside the project are never reported: nothing asks IntelliJ to watch or load it. Separately, `stop()` clears `dynamicRegistrations` without running their unregistration, so a stopped server's watchers stay active and the restarted server is told of changes matching them. Reproductions with the mock server for both; proposes the design of #1742 and asks whether a 2-second refresh is acceptable. |
| [#1743](https://github.com/redhat-developer/lsp4ij/issues/1743) | Feature request: UP02 | Closed 2026-10-20 by #1746 | LSP4IJ advertises `willSave` and `willSaveWaitUntil` (`new SynchronizationCapabilities(TRUE, TRUE, TRUE)`, whose parameters are willSave, willSaveWaitUntil and didSave) but sends neither. Quotes LSP for both, and sketches `willSave` from `beforeDocumentSaving` (the save reason is open) and `willSaveWaitUntil` as a bounded, cancellable wait in IntelliJ's Actions on Save with version-checked edits, since waiting in the save's write action would freeze the UI. Suggests no longer advertising the hooks until then, as our client's override does. No pull request yet. |
| [#1744](https://github.com/redhat-developer/lsp4ij/issues/1744) | Feature request: UP25 | Closed 2026-10-20, implemented by #1749 | LSP 3.18's proposed `workspace/textDocumentContent` and its refresh are unsupported, although LSP4J 1.0.0 has their types and service methods (`@ProtocolDraft`) and VS Code's client implements them. Lists the work: the capability and schemes, read-only virtual files for the schemes, `didOpen` for language features, refresh, and release on stop. No pull request yet. |
| [#1746](https://github.com/redhat-developer/lsp4ij/pull/1746) | UP02, implements #1743 (feature) | Merged 2026-10-20 (`ca60a68c`), unreleased; `LSPSupport.md` lists both requests as supported with a *Will Save* section, as the maintainer asked | `LSPDocumentSaveListener.beforeDocumentSaving` sends the pending changes, then `willSave` (reason `Manual`) and `willSaveWaitUntil` to servers that ask for them, and waits at most 1.5 s in all, as VS Code waits for save participants. Edits that arrive in time are applied in a command of their own and saved with the document, before `didSave`; later ones are dropped, and after three timeouts the request stops for that document. Actions on Save, proposed in #1743, run after the save (`ActionsOnSaveManager.processSavedDocuments`), so the PR waits in `beforeDocumentSaving`, as LSP4E's `documentAboutToBeSaved` does (5 s default). `WillSaveTest`: 3 of 4 fail on `main`. 618 tests, 0 failures. |
| [#1745](https://github.com/redhat-developer/lsp4ij/pull/1745) | Fix for #1709 | Merged 2026-10-20 (`e600c5b2`), unreleased; the open pull requests were rebased onto it with `git rebase -X renormalize` | Adds `.gitattributes` (`* text=auto eol=lf`, `*.bat text eol=crlf`) and renormalizes the 557 CRLF files to LF; `git diff --ignore-cr-at-eol` against `main` lists only `.gitattributes`. A `.git-blame-ignore-revs` entry would follow the merge. 614 tests, 0 failures, as on `main`. |
| [#1747](https://github.com/redhat-developer/lsp4ij/pull/1747) | Follow-up to #1745 | Merged 2026-10-20 (`a0169499`) | Adds `.git-blame-ignore-revs` with `e600c5b2`, so `git blame` attributes the 557 normalized files to their real authors once `blame.ignoreRevsFile` is set, as it now is in the local checkout. |
| [#1748](https://github.com/redhat-developer/lsp4ij/pull/1748) | [xvm #396](https://github.com/xtclang/xvm/issues/396) | Merged 2026-10-20 (`2e93c5e5`) | Adds *Ecstasy Language Support* to the README's "Who is using LSP4IJ?", linked to its Marketplace page (the plugin is in the alpha channel). |
| [#1749](https://github.com/redhat-developer/lsp4ij/pull/1749) | UP25, implements #1744 (feature) | Merged 2026-10-20 (`e3a9b11e`), unreleased | Supports LSP 3.18's `workspace/textDocumentContent` and its refresh: a connection-owned `DocumentContentProvider` fetches the documents of the schemes the server lists, statically or by dynamic registration, before navigation (`CancellationSupport.execute`) and `window/showDocument`, and keeps them in read-only `DocumentContentFile`s that keep the server's URI. Review: dynamic registration added at Angelo's request (unregistering a scheme closes its documents), a registration from a stopped connection ignored, and the reload and retirement moved to `ModalityState.nonModal()` (CodeRabbit); conflicts after the LF normalization resolved; `LSPSupport.md` updated. A `showDocument` without a selection no longer goes unanswered. |
| [#1750](https://github.com/redhat-developer/lsp4ij/pull/1750) | Build | Merged 2026-10-20 (`47388ced`) | Gradle 9.8.1, and the GitHub Actions moved off Node.js 20; the plugin still targets 2024.2 and Java 17. CodeRabbit's workflow-credentials finding was withdrawn: it predates the pull request, and `actions/checkout` v6+ keeps the token out of the workspace. |
| [#1751](https://github.com/redhat-developer/lsp4ij/pull/1751) | Found while rebasing #1749 | Merged 2026-10-20 (`0c0f4bf9`), unreleased | Deadlock between `LanguageServerWrapper.stop()`, which holds the wrapper's monitor and then takes `documentsLock`, and a document connecting at the same time, which asked for its `didOpen` under `documentsLock` and so reached the synchronized `start()`. With `stop()` on the EDT, IntelliJ froze. `connectDocument` now asks for the `didOpen` outside the lock; `ConnectDuringStopTest` checks the lock order without hanging. |
| [LSP4J #997](https://github.com/eclipse-lsp4j/lsp4j/pull/997) | UP15, fix for LSP4J #996 | Open; CI passes on all four builds, including `testOlderJavas`. Angelo [asked](https://github.com/eclipse-lsp4j/lsp4j/pull/997#issuecomment-6287991949) whether int instead of string ids cause it; [our reply](https://github.com/eclipse-lsp4j/lsp4j/pull/997#issuecomment-6288028637): no, a string id gets the same -32700. [A follow-up](https://github.com/eclipse-lsp4j/lsp4j/pull/997#issuecomment-6288328449) separates numeric ids beyond the `int` range: `nextInt()` throws `NumberFormatException`, and `StreamMessageProducer` logs and drops the request without a response (LSP4J `main`, probed); offered a separate issue | When converting `params` fails while `MessageTypeAdapter.read` reads the message, a failure without an `IOException` cause (a value of the wrong type, not malformed JSON) is reported as `InvalidParams`. Before that, `read` reads the rest of the message, so an `id` after `params` is kept, and only from the message's own members. The deferred conversion, used when `params` precedes `method`, reports `InvalidParams` too. Malformed JSON, trailing input and an unconvertible response `result` stay `ParseError`. Our October 7 repair (`13dfc35b`) read `params` into a `JsonElement` first; a JMH benchmark showed that roughly doubled parse time for structured parameters (completion request 0.75 → 1.28 µs, 1000 file events 123 → 239 µs), so #997 converts while streaming as before, with identical allocation per message. `InvalidParamsTest`: four of seven tests fail on `main`. Full build: 374 tests, 0 failures. |
| [LSP4J #999](https://github.com/eclipse-lsp4j/lsp4j/pull/999) | Build | Merged 2026-10-20 (`52acfd58`) | Upgrades LSP4J's build to Gradle 9.8.1, as #1750 does for LSP4IJ. |
| [LSP4J #1003](https://github.com/eclipse-lsp4j/lsp4j/pull/1003) | Fix for LSP4J #963 | Merged 2026-10-22 (`7d43692c`), unreleased | `RemoteEndpoint.consume` logs "Unknown message type" with the message it got, instead of the misspelled text without it. |
| [LSP4J #1005](https://github.com/eclipse-lsp4j/lsp4j/pull/1005) | Found on #1003's CI | Merged 2026-10-22 (`cca66eb4`), unreleased | `WebSocketMessageConsumer` could take its sending flag after another thread had sent the queued message, pass `null` to `sendText` and never release the flag, so it sent nothing again; this failed `MockConnectionTest.testManyConcurrentNotifications` on macos-15-intel. `handleNextMessage` now releases the flag when the queue is empty, checks the queue again, and releases the flag when `sendText` throws. |
| [LSP4J #1004](https://github.com/eclipse-lsp4j/lsp4j/pull/1004) | DAP `seq` order | Merged 2026-10-24 (`eba67ff2`), unreleased | `DebugRemoteEndpoint` sends its messages one at a time through a sequencer: a notification or a response takes its `seq` just before it is written, and a request took it when it was created, under the sequencer's lock until it was written. Jonah merged it, [would accept](https://github.com/eclipse-lsp4j/lsp4j/pull/1004#discussion_r4365742270) a change that treats the three message types alike, and asked that `MessageConsumer` not change. |
| [LSP4J #1008](https://github.com/eclipse-lsp4j/lsp4j/pull/1008) | Follow-up of #1004 | Open (2026-10-28) | `RemoteEndpoint.request()` sends a request through a new `protected sendRequest(requestMessage, recordRequest)`; `DebugRemoteEndpoint`'s sequencer gives the request its `seq`, records it and writes it, so all three message types take their `seq` in one place and #1004's `synchronized` block goes away. `MessageConsumer` does not change. `./gradlew build`: 373 tests pass. |
| [LSP4J #1009](https://github.com/eclipse-lsp4j/lsp4j/pull/1009) | UP41, LSP4J part | Draft (2026-10-28) | `DebugRemoteEndpoint.sendCancelNotification()` sends DAP's `cancel` request with the cancelled request's `seq` as `requestId`, instead of LSP's `$/cancelRequest` notification, which a DAP endpoint writes as an `event` that no adapter knows; an incoming `cancel` cancels the request it names and still reaches the local endpoint, and the `$/cancelRequest` event is still understood. A draft because it changes the wire: it asks Jonah Graham about older LSP4J peers, an adapter's own reverse requests (DAP defines `cancel` only from client to adapter), and a local endpoint without `cancel`. `./gradlew build`: 374 tests pass. |
| [LSP4J #996](https://github.com/eclipse-lsp4j/lsp4j/issues/996) | Issue: wrong parameter types | Open; #997 fixes it | A request with valid JSON but a parameter of the wrong type is answered with `ParseError` (-32700) instead of `InvalidParams` (-32602), as JSON-RPC 2.0 §5.1 specifies. If its `id` follows `params`, it is reported as a notification and gets no response, against §5. Records the wire responses for each member order, the cause in `MessageTypeAdapter.read`, and #453 as an earlier case of the misleading code. |
| [LSP4J #991](https://github.com/eclipse-lsp4j/lsp4j/pull/991) | Fix for LSP4J #990 | Merged 2026-10-19 (`e64042cf`), unreleased; approved after `d059bc85` applied the maintainer's diamond-operator suggestion | `RelatedDocumentsMapAdapter` reads each `relatedDocuments` value by its `kind`, as `DocumentDiagnosticReportTypeAdapter` does for the report itself. It is used on the three `relatedDocuments` fields. The four new tests fail on `main` with "Ambiguous Either type" and pass with the fix. Full build: 367 tests, 0 failures. The ECA check passes since 2026-10-19 (Eclipse account linked to GitHub ID `lagergren`). GitHub holds the "Validate" workflow, as for any first-time contributor, until a maintainer approves it. |
| [LSP4J #990](https://github.com/eclipse-lsp4j/lsp4j/issues/990) | Issue: pull `relatedDocuments` | Closed by #991; the description records that `DocumentDiagnosticReportPartialResult` fails the same way and that #991 covers it | LSP4J 1.0.0, and `main`, cannot parse a `textDocument/diagnostic` report with `relatedDocuments`: "Ambiguous Either type". The map values are `Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>` with no `kind` check, unlike the top-level report. In LSP4IJ's test harness, such a pull never completed. LSP4IJ also ignored the related documents of a *full* report (issue [#1722](https://github.com/redhat-developer/lsp4ij/issues/1722)). [#1723](https://github.com/redhat-developer/lsp4ij/pull/1723) fixes both on the LSP4IJ side, with a temporary compatibility adapter until an LSP4J release includes #991. |
| [#1719](https://github.com/redhat-developer/lsp4ij/issues/1719) | Issue: diagnostics updates | Closed 2026-10-19: part 2 fixed by #1721, part 1 by #1730 | Filed from #1717's investigation, not fixed there. An unchanged diagnostics update still rebuilds and cancels the lazy code actions the editor uses. `LSPDiagnosticsApplier` drops diagnostics published before the first highlighting registers its pass factory (`GROUP_ID` is -1), until the set changes. |
| [#1717](https://github.com/redhat-developer/lsp4ij/pull/1717) | CI flakes, and #1697 | Merged 2026-10-19 (`4dbdf09d`), unreleased | Fixes every intermittent CI failure we found. (1) #1698's `testDidOpenVersionCannotAuthorizeTextChangedSinceCapture`: the editor's `selectionChanged` handler can reconnect the file with the typed text first, so the test now uses a file without an editor. (2) #1700's `EditorRefreshBatchTest` left a debounced daemon restart pending on the reused light project; it now disposes its `LSPFileSupport`. (3) `WebFilterQuickFixTest` (#1697), which predates our PRs: a product race in `LSPLazyCodeActions`. Parallel highlighting passes both loaded the same quick fixes, and the second load cancelled the first request before it was sent. A stored cancelled request then broke the quick fix for good ("Unable to highlight after 1000 retries", or no code action). The check and the load are now serialized, and `cancel()` clears a retired request. Stress run, 150 repetitions: `main` had 2 failures and 8 double loads, the fix none. The code-action fixture also highlights until the diagnostics are shown. |
| [#1716](https://github.com/redhat-developer/lsp4ij/pull/1716) | UP14, cache race | Merged 2026-10-19 (`bca8ffbb`), unreleased | Serialize reading the cached feature request with cancellation: `getValidLSPFuture()` and `getFeatureData()` could return `null` for a valid request, and `isValidLSPFuture()` could throw. UP14's invalidation on every diagnostics push is not filed: LSP's refresh requests already clear the caches on `main`, and our server sends them. A full IntelliJ run on 0.21.0 hit the race on 2026-10-28: the breadcrumbs got no document symbol request and IntelliJ reported an `IllegalArgumentException` from `awaitWithCheckCanceled` (`run-7875402505096583343`). |
| [#1715](https://github.com/redhat-developer/lsp4ij/pull/1715) | UP26 | Merged 2026-10-19 (`04bce87f`), unreleased | Send `Invoked` for an explicit invocation instead of always `Automatic`, send the popup's selected item, request again when it changes, and stop reusing the response for a different context at the same caret. |
| [#1712](https://github.com/redhat-developer/lsp4ij/pull/1712) | UP10, rendering | Merged 2026-10-18 (`b7d8d9b9`), unreleased | Show the signature label when a signature has no `parameters` (it threw a `NullPointerException`), and include the escaped parameter name in the single-parameter presentation (it rendered `<b></b>`). After review, it also drops the `, ` separator from that presentation and lets an out-of-range current parameter fall back to the first one, as LSP specifies; both were found by tests the maintainer asked for. Per-overload `activeParameter` and refreshing metadata on retrigger are left out: LSP4IJ does not advertise `activeParameterSupport`, so honoring it would be a new feature, not a fix. |
| [#1711](https://github.com/redhat-developer/lsp4ij/pull/1711) | UP18 | Merged 2026-10-19 (`76df99e4`), unreleased | Honor `InsertTextMode.AsIs` for snippet completions, which LSP4IJ already advertises, and advertise `AdjustIndentation` as the client default. |
| [#1708](https://github.com/redhat-developer/lsp4ij/pull/1708) | UP19 and its follow-up | Merged 2026-10-19 (`73c10c9c`), unreleased | Reconnect open documents after directory moves and renames; a file rename reconnects after the write action instead of waiting under it. |
| fork branch `fix/stale-workspace-edits` (`181a9b41`), no pull request | UP04, snapshots | Superseded by #1698 and #1718 | The first version of rejecting stale workspace and rename edits through request snapshots. #1698 (versions) and #1718 (request snapshots) carry its work, reworked after review and merged, so no pull request will be opened from it. The branch is still on the fork. |

[Issue #1697](https://github.com/redhat-developer/lsp4ij/issues/1697) tracks an intermittent
`WebFilterQuickFixTest` failure seen in CI while #1696 was under review; it is a test flake, not a
register entry. [Issue #1709](https://github.com/redhat-developer/lsp4ij/issues/1709) proposed a `.gitattributes`, on the premise
that with `core.autocrlf=input` Git converts an edited CRLF file to LF, so that a small edit shows as
a rewrite of the whole file. Re-tested on 2026-10-20 with Git 2.54, that premise is wrong: a one-line
edit that keeps a file's endings stages as one line under every `core.autocrlf` setting, since Git
does not auto-convert a file stored with CRLF. The whole-file diff came from edits that wrote LF into
CRLF files (557 of them on `main` `9d1e5cd7`), so edits to those files must preserve their bytes.
Option 1 (`* -text`) would not prevent that, and no pull request was opened for it; a
[correction](https://github.com/redhat-developer/lsp4ij/issues/1709#issuecomment-6289336781) on
the issue says so. Only normalizing to LF would: that is pull request
[#1745](https://github.com/redhat-developer/lsp4ij/pull/1745), merged 2026-10-20 (`e600c5b2`), and [#1747](https://github.com/redhat-developer/lsp4ij/pull/1747) lets
`git blame` skip it. An earlier version of this note said the local checkout's `.git/info/attributes`
held `* -text`; there is no such file, and only the global `core.autocrlf=input` applies.

UP13 is not filed: LSP4IJ keeps server configuration global by design and leaves other sources
to clients that override `createSettings`, as ours does (see the register).

Upstream has also fixed parts of UP03 on its own, both unreleased:
[`a0730c2f`](https://github.com/redhat-developer/lsp4ij/commit/a0730c2f) moved the will-create, will-delete and will-rename requests into
`prepareChange` with a timeout, and [`9d2036fe`](https://github.com/redhat-developer/lsp4ij/commit/9d2036fe) sends the new path in
will-rename requests. Checked against `main` `9d1e5cd7` on 2026-10-20: our local `LSPFileMovePreflightTest` passes there (3 of 3), so
the preflight destination needs nothing more. The rest is filed: resource renames in
[#1739](https://github.com/redhat-developer/lsp4ij/pull/1739), and the UI wait, which remains for an IDE rename or move because
IntelliJ calls `prepareChange` for those in the write action, in issue
[#1740](https://github.com/redhat-developer/lsp4ij/issues/1740).

The pull requests are rebased onto upstream `main` and squashed, and differ from the local commits
where review found problems. The
[LSP4IJ #1698 review](https://github.com/redhat-developer/lsp4ij/pull/1698#discussion_r4291521233)
asked for cancellable reads instead of `ReadAction.compute`, which can freeze the editor. #1707 therefore
drops the local branch's read around `LanguageServerWrapper.connect`: the didOpen text is captured
before that read, so it did not keep the text and listener on one revision, and with #1698 it could
deadlock against a disconnect. #1708 drops two read wrappers that run on the EDT, which already has
read access. On the unpushed local branch, `2096d5e6` makes the `FileSystemWatchRoots` walk and
`WorkspaceEditSnapshot.capture` cancellable; the `connect` read is still there. The XVM plugin's
own long reads were fixed the same way in `223518e24`.

## Upstream fixes prepared (2026-10-20)

Each fix is a local commit on a branch off the upstream default branch (intellij-community `master`
`441b046f2`, vscode `main` `34c443e2`), with an upstream-style test that fails before it, except UP17's,
which is a speedup. On 2026-10-28, UP24 was filed as [IJPL-259881](https://youtrack.jetbrains.com/issue/IJPL-259881) and opened as [#3727](https://github.com/JetBrains/intellij-community/pull/3727) (the search before filing missed [IJPL-29917](https://youtrack.jetbrains.com/issue/IJPL-29917), "AsyncDocumentFormattingService breaks undo", and [IJPL-223024](https://youtrack.jetbrains.com/issue/IJPL-223024); JetBrains linked both, our comments on them point at #3727, and its description names all three), and UP17 as [#3728](https://github.com/JetBrains/intellij-community/pull/3728) for the existing [IJPL-259475](https://youtrack.jetbrains.com/issue/IJPL-259475), the console freeze with the same stacks; each is cherry-picked onto `master` `cdc10bf305a5` from the fork `lagergren/intellij-community`, and its tests were not run on that `master`, which runs them only under a full Bazel build. UP20 and UP29 were filed as [IJPL-259883](https://youtrack.jetbrains.com/issue/IJPL-259883) and [IJPL-259884](https://youtrack.jetbrains.com/issue/IJPL-259884) and opened as [#3729](https://github.com/JetBrains/intellij-community/pull/3729) and [#3730](https://github.com/JetBrains/intellij-community/pull/3730); `master` had not changed their files. UP16 was opened as [microsoft/vscode#344760](https://github.com/microsoft/vscode/pull/344760) for the existing #219122, whose two Cuts during a watch task's rebuild throw the same TreeError from `itemsCopied`; `main` had not changed its four files, and its tests were not rerun there. The native checks ran IntelliJ IDEA 2026.2.3
(262.10968.63) through IDE Starter with the change's classes swapped into a copy of the installation,
and our matching bridge disabled; receipts are kept in the session scratchpad (`native/receipts`).

| Entry | Branch and commit | Fix | Native check, without and with the change |
| --- | --- | --- | --- |
| UP16 | vscode `fix/explorer-cut-paste-stale-nodes` `4dd451ed` | `itemsCopied` repaints the tree's current item for each cut resource and skips the ones it no longer shows; Paste resets its move flag before clearing the cut. Two unit tests; the Explorer suite passes (193). | The `--explorer-move-probe --refresh-during-move` probe throws `Data tree node not found` from `itemsCopied` on VS Code 1.140.0 and on Code - OSS 1.144.0 built from `main`; on Code - OSS built from the branch it completes Move, Undo, Redo and contents without it, twice. |
| UP17 | intellij-community `interval-tree-bulk-removal` `9e4fa930d` | A large removal defers the tree's `maxEnd` correction and recomputes it once, instead of after each node. | Replacing three quarters of an 880,000-character document: 343/1,240/5,085 ms with 20k/40k/80k markers (4,446 ms in bulk mode) without it, 49/62/61 ms (49 ms) with it. 30 editing, rename, move, format, folding and Undo/Redo cases pass with it, with no IDE errors. |
| UP20 | intellij-community `driver-edt-modality` `2c07e3ae4` | `EdtCallDispatcher` queues an EDT call again when a modal dialog opens before it starts; only the first copy that runs performs it. | With `restorePopupFocus` back on `OnDispatcher.EDT`: the stock Driver hung in X167's Rename dialog for 14.5 minutes (RMI thread in `Invoker.invoke` → `invokeAndWaitRelaxed`, EDT in a modal `RefactoringDialog`); with the change X164–X168 pass in 3 of 3 runs, and START_FOCUS passes. |
| UP24 | intellij-community `async-format-redo` `e7f0801a0` | The asynchronous result is applied in a "Reformat Code" command in the requesting command's group, not undo-transparently. | With `XtcFormattingService`'s write command removed: X249 and X250 fail at Redo on the stock IDE ("action is disabled", and the Redo text never appears) and pass with the change, with X132 and X139. |
| UP29 | intellij-community `driver-waitfor-once` `5fe70a336` | `waitFor` checks each observation once and uses a monotonic deadline; `waitAny` returns the elements of the successful observation. | With `awaitUi` delegating to Driver's `waitFor`: `UiWaitsTest`'s "a successful readiness observation is not checked again" fails on driver-sdk 262.10968.63 and passes with the change; X137, X146 and X259 pass both ways (the race is rare). |

Drafts of the YouTrack tickets, the PR descriptions and the comment for #219122 are in
`~/src/upstream-drafts/`, one file each (2026-10-28). The JetBrains PRs need the ticket IDs first; Driver
commits upstream use the YouTrack project `AT`, which may not be public, so the Driver tickets may go
to `IJPL`. The UP17 probe's decorated replacement started with about 36,000 of its 80,000 highlighters
installed in both runs (626 and 540 ms), so it does not measure the decorated case; its readiness check
needs fixing separately.

## LSP4IJ DAP features prepared (2026-10-22)

IntelliJ's side of debugger features that XVM's plan needs (R9 and R10 in
[remaining work](remaining.md#r9r13-debugger-features-beyond-r7)) and LSP4IJ's DAP client lacks. Each is a
local commit in `~/src/lsp4ij` on its own branch off upstream `main` (`ef2b18c3`), with tests. UP32, UP33
and UP34 are filed as pull requests (2026-10-22, [below](#upstream-pull-requests-2026-10-18)), UP33 and UP34
merged on 2026-10-26; UP35 and UP36, rebased onto `main` `f0efa5f4`, are filed as #1768 and #1769 (2026-10-26). UP32 was first checked natively before the adapter had logpoints, so it stopped
at a breakpoint whose Suspend is unchecked. With X304's breakpoint so set, logging its hit and `n`, a plugin
built from `main` printed `Breakpoint reached: DebugStops.x:11` and left the program stopped while the
session showed it running (`run-6266382029699247857`); with one built from UP32 the console also showed `21`
and the program ran to exit code 42 (`run-6896064008597954264`, a temporary local case, not committed). That
run reported an IDE error, "can't inspect non-physical file: DAPExpressionCodeFragment.txt", whose stack has
no LSP4IJ or XVM frames (UP37, since fixed by #1763).

Since R9 (`5b2686a2e`, `edeab8f5b`), the Ecstasy debug adapter offers logpoints, exception filter conditions
and `exceptionInfo`, so UP32, UP34 and UP33 were checked natively against it on 2026-10-23, in IntelliJ IDEA
2026.2.3, each with a temporary local case (not committed) and a plugin built from its pull request:

- UP32 (#1759, built with #1763's fix on a local scratch branch, since a breakpoint that logs shows IntelliJ
  2026.2's logpoint editor): DebugLogs' line 8 with Suspend unchecked, "Breakpoint hit" and `sum` to log. The
  adapter logged `Breakpoint reached at DebugLogs.x:8` and `6` without the program stopping, and it ran to exit
  code 6, with no IDE error (`run-10639172617723907439`).
- UP34 (#1760): DebugTypes stopped before its first throw; All Exceptions then got the condition
  `IllegalArgument` through the Exception Breakpoints panel's settings, and Uncaught Exceptions was turned off.
  The caught `IllegalState` did not stop the program, which stopped at `DebugTypes.check:16`, where the
  `IllegalArgument` is thrown, and failed with exit code 1 (`run-13613178393671792334`). With a plugin built
  from `main`, which sends no condition, the same setup stopped at line 14, the caught `IllegalState`
  (`run-9967040128816040351`).
- UP33 (#1761 at `7f1a5224`): X307's hint read "Exception has occurred: IllegalArgument", `bad 5`, then
  `at check(Int) (DebugThrows.x:12)` and the rest of the stack, a line each, `...` after ten
  (`run-14674276171465489822`, no IDE error). With `main` the hint had no stack (`run-9967040128816040351`),
  and UP31's "Editor is already disposed" occurred there too. The stack shows the embedding runner's own
  frames (`TaskRegistry`, `runner.x`), and the call stack had them too; since `ad8268260` neither has them.

The three pull requests' descriptions give these checks since 2026-10-23, in place of their "not checked
natively" sentences; #1761's also describes `8bc5d28d`'s timeout and line breaks and `7f1a5224`'s test.

The adapter offers lazy variables since R10a (`1d88e086d`, 2026-10-26), and UP35 (#1768) works natively
with it: X316's lazy variables have a Show value link and no children, and clicking them shows their values
(`run-2879359689441688077`, `run-13880544694745259179`). The adapter formats values since R10b (`cb551d4c2`),
and UP36 (#1769 at `62acabd6`) works natively with it: X317's toggle shows its integers as `0xFF` and back
(`run-5016431580533175464`).

| Entry | Branch and commit | Feature | Tests |
| --- | --- | --- | --- |
| UP32 | `feat/dap-logpoints` `82dbb3e9`, then `05360b0d` and `f2a38e58` | A breakpoint whose Suspend is unchecked is sent as a DAP logpoint: its "Breakpoint hit" message and its "Evaluate and log" expression, in braces, become `logMessage`. At a stop, the expression is evaluated in the stopped frame and logged by IntelliJ, where LSP4IJ passed `null`; when `breakpointReached()` returns false, as for a breakpoint that doesn't suspend, the program is resumed instead of staying stopped while IntelliJ shows it running. | `DAPLogpointTest` (real IntelliJ breakpoints; fails without the change, every `logMessage` null) and `DAPClientBreakpointReachedTest` (5). `05360b0d`, after CodeRabbit's review of #1759, prints to the debug console why a stop at a breakpoint could not be handled, such as a failed `continue`, which the first commit dropped; the fifth test covers it. `f2a38e58` splits `DAPClient.stopped()`, which handled a stop five levels deep, into smaller methods without changing what it does; X304–X307 pass natively with a plugin built from it. `./gradlew check`: 748 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. |
| UP33 | `feat/dap-exception-info` `04164852`, then `8bc5d28d` and `7f1a5224` | With `supportsExceptionInfoRequest`, an exception stop's hint asks `exceptionInfo` and shows the exception id (or its type name), its description and the first ten lines of its stack; otherwise, or if the request fails, the stopped event's text as before. `DAPSupport.md` marks ExceptionInfo supported. After CodeRabbit's review of #1761, `8bc5d28d` gives `exceptionInfo` a one-second timeout, after which the hint falls back to the stopped event's text, and shows the hint's lines as lines: the hint renders HTML, so `showErrorHint()` escapes the text and turns line breaks into `<br>`. `7f1a5224`, after a later CodeRabbit request for a test through `stopped()`, moves the exception branch of `stopped()` to a static `showExceptionHint()` with the display as a parameter. | `DAPClientExceptionHintTest` (7) and `DAPClientExceptionStopTest`. `./gradlew check`: 748 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. |
| UP34 | `feat/dap-exception-filter-conditions` `a4f73d29` | The Exception Breakpoints panel gains a Condition column for filters with `supportsCondition` (an empty cell shows `conditionDescription`); conditions are kept with the server's filter settings and sent as `filterOptions` when the server has `supportsExceptionFilterOptions`. The breakpoint handler keeps each server's capabilities. | `DAPExceptionFilterConditionsTest` (2; the first fails without the handler change). `./gradlew check`: 745 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. |
| UP35 | `feat/dap-lazy-variables` `83a0e92d` | A variable with `presentationHint.lazy` shows its placeholder and a "Show value" link; clicking asks for its `variablesReference` and shows the first variable in its place, with its children. | `DAPLazyValueEvaluatorTest` (4); `DAPValue`'s use of it is not covered. `./gradlew check`: 747 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. |
| UP36 | `feat/dap-value-formats` `b0a76151` | With `supportsValueFormattingOptions`, a "Show Values as Hexadecimal" toggle in the Variables and Watches menu (`XDebugger.ValueGroup`) makes the session's `variables`, `evaluate` and `setVariable` requests carry `format {hex: true}`, and rebuilds the views. | `DAPValueFormatTest` (2); that the requests carry the format is not covered. `./gradlew check`: 745 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. |

## Register

“Bridged” means this branch has a local workaround, whose regression must pass before its
removal. “Constrained” means we deliberately avoid advertising behavior the host cannot perform.
“Open” means the externally visible defect remains. Test references identify removal gates,
not a claim that every referenced suite was rerun for this documentation change.

| ID / owner / status | Defect or missing behavior | Local handling and evidence | Removal gate |
| --- | --- | --- | --- |
| **UP01 — LSP4IJ — bridged**; upstream [#1696](https://github.com/redhat-developer/lsp4ij/pull/1696) merged, unreleased | Process `stop()` can precede `start()`; later startup creates a child that subsequent stop does not reap. | [Provider](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) uses `ConnectionLifetime`. Real child reproduction and separate server EOF bug are documented in the [lifecycle diagnosis](errs-lsp-process-lifecycle.md). | Upstream atomically owns start/stop and rejects post-stop startup; `ConnectionLifetimeTest` and packaged process regressions still pass without the guard. |
| **UP02 — LSP4IJ — constrained**; feature request [#1743](https://github.com/redhat-developer/lsp4ij/issues/1743), implemented by [#1746](https://github.com/redhat-developer/lsp4ij/pull/1746), merged, unreleased | Native synchronization does not dispatch the advertised server save hooks, including `willSaveWaitUntil`. | [Client capabilities](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt), [provider](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) and [settings](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurable.kt) disable these hooks/server save formatting. Native Actions on Save remains available. | Observe real pre-save requests, apply version-checked edits before persistence, then enable the UI/capabilities and extend X139. |
| **UP03 — LSP4IJ — bridged; generic UI wait open**; upstream `main` has partial fixes ([`a0730c2f`](https://github.com/redhat-developer/lsp4ij/commit/a0730c2f), [`9d2036fe`](https://github.com/redhat-developer/lsp4ij/commit/9d2036fe)), unreleased; file-name escaping in [#1720](https://github.com/redhat-developer/lsp4ij/pull/1720) merged, unreleased; resource renames fixed in [#1739](https://github.com/redhat-developer/lsp4ij/pull/1739), merged, unreleased; UI wait reported as [#1740](https://github.com/redhat-developer/lsp4ij/issues/1740) | Native file rename/move preflight happens too late; resource rename handling ignores changed parents. The VFS listener also requests old-path → same-path preflight for moves and waits on the EDT. | Guarded native entry points preserve edits/Undo. `PreflightedRenames` now completes no-op requests and exact already-approved physical operations locally. Real unowned renames still use upstream preflight. Eight endpoint/launcher regressions and the focused native run pass. Original 6–11-second wait stacks remain recorded. | Native upstream Rename/Move passes X103/X130, including consumers, resources, Undo/Redo and refusals, without our entry points or UI-thread waits. |
| **UP04 — LSP4IJ — bridged**; upstream [#1698](https://github.com/redhat-developer/lsp4ij/pull/1698) merged (versions only), unreleased; request snapshots in [#1718](https://github.com/redhat-developer/lsp4ij/pull/1718) merged, unreleased | Rename and generic `workspace/applyEdit` can apply stale edits without checking transmitted document versions/epochs. | [Rename handler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameHandler.kt), [snapshot guard](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt) and [client edit handler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) validate inside the write command. Unsupported generic resource/snippet/confirmation edits are refused. | Equivalent upstream ownership/version checks pass stale/closed/reopened document tests and X144; supported Rename/Move remains atomic and undoable. |
| **UP05 — LSP4IJ — bridged**; upstream issue [#1741](https://github.com/redhat-developer/lsp4ij/issues/1741) and fix [#1742](https://github.com/redhat-developer/lsp4ij/pull/1742), merged, unreleased | Dynamic filesystem watcher registrations do not alone establish/refresh unknown or missing external VFS roots while the IDE stays focused. | [CompilerRootWatches](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerRootWatches.kt) owns roots and their disposal; `CompilerRootWatchesTest` and X124 cover resources. | Upstream observes creation/deletion/change under configured external roots while focused and releases watchers on disposal. |
| **UP06 — LSP4J, bundled by LSP4IJ — bridged; local repair tested**; LSP4J fix [#991](https://github.com/eclipse-lsp4j/lsp4j/pull/991) merged (`e64042cf`), unreleased; LSP4IJ [#1723](https://github.com/redhat-developer/lsp4ij/pull/1723) merged, unreleased, parses these reports with its own adapter until LSP4J releases #991, so the next LSP4IJ release, 0.30.0, is all XVM waits for | `relatedDocuments` diagnostic unions are not reliably decoded by their `kind` discriminator. | [DiagnosticReportJson](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticReportJson.kt) supplies the adapter; `DiagnosticReportJsonTest` checks full/unchanged reports. Local LSP4J `b47e8923` passes direct regression tests; see the [repair receipt](#local-lsp4j-repairs-2026-10-07). | Correct full/unchanged wire round trips without the adapter in the actual bundled client library. Checked against a plugin built from LSP4IJ's `main` (2026-10-28): `configureCompatibilityAdapters` registers #1723's `RelatedDocumentsTypeAdapterFactory`, which the client's Gson applies, so `DiagnosticReportJson` goes with 0.30.0 (`cf21a1ed54` on the local branch `lagergren/lsp4ij-0.30.0`, its test now `RelatedDiagnosticReportsTest`). |
| **UP07 — LSP4IJ — bridged**; second half (lazy fixes replaced by an equal report) fixed by upstream [#1730](https://github.com/redhat-developer/lsp4ij/pull/1730), merged, unreleased; `previousResultId` in [#1735](https://github.com/redhat-developer/lsp4ij/pull/1735), merged, unreleased | Automatic pulls omit `previousResultId`. Equal full reports also replace/cancel lazy fixes without refreshing the annotations that own them. | [DiagnosticResultMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt) owns result IDs; [DiagnosticQuickFixes](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixes.kt) gives delivered full reports a client-only data revision and restores original data on outgoing action requests. Lifecycle, upstream equality and opaque-data tests cover both. | Upstream must own IDs/retirement and retain equivalent lazy fixes or refresh their annotations. Verify X181 → X185 plus diagnostic/action Undo/Redo before removing either bridge. |
| **UP08 — LSP4IJ — bridged**; upstream [#1710](https://github.com/redhat-developer/lsp4ij/pull/1710) merged, unreleased | Normal Gson omission of explicit null configuration fields prevents reset-to-discovery from reaching the server. | [ConfigurationJson](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConfigurationJson.kt) preserves explicit nulls; `ConfigurationJsonTest` covers the wire representation. | Reset-to-discovery survives native settings and transport round trips without the adapter. |
| **UP09 — LSP4IJ — bridged**; upstream [#1724](https://github.com/redhat-developer/lsp4ij/pull/1724) (notification order) and [#1725](https://github.com/redhat-developer/lsp4ij/pull/1725) (folding lines outside the document) merged, unreleased | Startup document messages can arrive out of order; folding replies can outlive the editor that requested them. | [DocumentStartupMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DocumentStartupMessages.kt) serializes messages and retires stale responses; matching unit tests cover open/change/close ownership. | Upstream passes startup typing and close/reopen regressions without the transport bridge. |
| **UP10 — LSP4IJ — bridged**; upstream [#1712](https://github.com/redhat-developer/lsp4ij/pull/1712) merged (rendering), unreleased; per-overload `activeParameter` and retrigger metadata in [#1734](https://github.com/redhat-developer/lsp4ij/pull/1734), merged, unreleased | Parameter Info retains old overload metadata after retrigger, ignores per-overload active parameters and renders absent parameter metadata as an empty signature. | [XtcParameterInfoHandler](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcParameterInfoHandler.kt) uses current metadata and preserves the label without inventing a highlighted argument. | Native signature cases, including X20 and retrigger/constructor variants, preserve the label and highlight the correct current argument without this handler. |
| **UP11 — LSP4IJ — bridged**; upstream [#1733](https://github.com/redhat-developer/lsp4ij/pull/1733) merged, unreleased | Resolved code-action edits use an undo-transparent path without our version-checked write command. | [CodeActionMessages](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CodeActionMessages.kt) routes selection through a supported client command. Resolving/listing never applies an edit. | X105/X122 import/member generation passes stale refusal and native Undo/Redo without the bridge. |
| **UP12 — LSP4IJ — bridged**; upstream [#1707](https://github.com/redhat-developer/lsp4ij/pull/1707) merged, unreleased | Formatting dereferences an absent editor and fails to reconnect closed dirty buffers before querying their contents. | [XtcFormattingService](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt) connects the current buffer and checks text/version before applying edits. Local `15bb34bd` passes closed-file native save with this override bypassed; shipping 0.21.0 still needs it. | A released repair preserves absent-editor, snapshot, cancellation and server-lifetime guards; repeat native save acceptance. Retain the separate UP24 command ownership and resolve closed-buffer connection retention before removing the whole override. |
| **UP13 — LSP4IJ — by design; not filed** | Default `createSettings` sends only the global configuration. On review (2026-10-18) this is LSP4IJ's design, not a defect: its settings page stores server configuration only in the global store, the project store holds trace, error reporting and debug settings, and the project listener re-sends configuration when those change. Clients supply other sources by overriding `createSettings`. | [XtcLanguageClient](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) overrides `createSettings` and reads the store that owns the compiler graph, because the Ecstasy project settings page writes project configuration. | None: the override is the supported extension point and stays. Its comment no longer carries a `TODO LSP4IJ` marker. |
| **UP14 — LSP4IJ — bridged**; cache race in upstream [#1716](https://github.com/redhat-developer/lsp4ij/pull/1716) merged, unreleased; other files' changes in our [#1771](https://github.com/redhat-developer/lsp4ij/pull/1771), merged 2026-10-27 (`7fa1794f`), unreleased | Semantic caches keyed only to PSI stamps retain old results when a dependency changes but the consumer text does not. | [XtcLanguageClient](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) retires completed semantic results on compiler analysis updates; X146 observes untouched-consumer refresh. | All negotiated native providers reflect dependency edits without editing the consumer or our invalidation bridge. |
| **UP15 — LSP4J — release limitation; local repair tested**; upstream issue [#996](https://github.com/eclipse-lsp4j/lsp4j/issues/996) and fix [#997](https://github.com/eclipse-lsp4j/lsp4j/pull/997), open | Valid JSON with a wrongly typed parameter is classified as `ParseError` rather than `InvalidParams`. | [XdkStdioTest](../lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt) records the actual library result and then exercises normal semantic requests. No production parser fork. Local LSP4J `13dfc35b` passes classification and transport recovery tests; see the [repair receipt](#local-lsp4j-repairs-2026-10-07). | Adopt and verify a compatible repaired dependency; change the assertion to `ResponseErrorCode.InvalidParams`, retaining reader recovery. |
| **UP16 — VS Code — open**; upstream issue [microsoft/vscode#219122](https://github.com/microsoft/vscode/issues/219122) (same TreeError, open); our [microsoft/vscode#344760](https://github.com/microsoft/vscode/pull/344760) (2026-10-28, `f077679f4ae` from the fork `lagergren/vscode`, on `main` `915c6faed9c`), open; local fix verified natively 2026-10-20 (see [upstream fixes](#upstream-fixes-prepared-2026-10-20)) | Paste repaints obsolete Cut tree nodes after an Explorer refresh; cleanup throws and skips resetting move/copy state. | [Isolated reproduction and traces](errs-integration-plan.md#x130-isolated-host-defect-and-harness-focus-correction-2026-10-01), [probe](../lang/vscode-extension/src/test/explorer-move.ts) and X130. Reproduced without Ecstasy or an LSP server. No host patch or exception suppression. | Upstream reconciles/guards stale repaint targets and always resets cleanup state. Both the controlled standalone reproduction and native X130 must pass on the repaired release. |
| **UP17 — IntelliJ Platform — open**; the existing freeze report [IJPL-259475](https://youtrack.jetbrains.com/issue/IJPL-259475), whose console stacks end in the same `maxEnd` correction; our intellij-community [#3728](https://github.com/JetBrains/intellij-community/pull/3728) (2026-10-28, `9c340ebf0973`, on `master` `cdc10bf305a5`), open; local fix verified natively 2026-10-20 (see [upstream fixes](#upstream-fixes-prepared-2026-10-20)) | Removing many ranges in one document edit repeatedly traverses temporarily invalid interval subtrees on the EDT. Large-file replacement freezes the UI. | [Isolated marker probe](../lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LargeFileProbe.kt), [native driver](../lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LargeFileEditing.kt) and [measurements](errs-integration-plan.md#l82-large-file-intellij-freeze-investigation-2026-10-01). Reproduced with unattached platform documents; bulk-update mode does not remove the cost. No production tree patch or discarded highlights. | A platform repair passes the plain-marker scaling control and actual decorated-editor replacement, preserving marker validity and UI responsiveness. A smaller workload or replacement before highlights arrive does not satisfy this gate. |
| **UP18 — LSP4IJ — constrained**; upstream [#1711](https://github.com/redhat-developer/lsp4ij/pull/1711) merged, unreleased | Native snippet expansion adds source indentation even when the completion requests `InsertTextMode.AsIs`, despite advertising both modes. | [XtcClientFeatures](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) advertises AdjustIndentation as its default and sole supported mode. The server supplies relative template indentation. X150's first native trace (`run-9591212347662100514`) records the original capabilities, AsIs reply and doubled indentation; no IDE errors occurred. With the constraint, final native X150 passes (`run-10874571275660252562`), including exact text, snippet stops and insertion Undo. | Upstream passes the insertion mode through snippet construction/expansion. Remove the constraint only after X150 preserves exact source, placeholder navigation and Undo without it. |
| **UP19 — LSP4IJ — bridged**; upstream [#1708](https://github.com/redhat-developer/lsp4ij/pull/1708) merged, unreleased | Moving/renaming a directory leaves its open descendants connected under their old URIs. Undo can leave two opened-document entries referring to the same VirtualFile. | [DirectoryDocumentMoves](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMoves.kt) retires affected connections before paths change and asynchronously reconnects current editor buffers. X162 originally timed out after Undo; X118/X161/X162/X163 now pass, including post-Redo unsaved edits. Details below. | Upstream reconnects open descendants on directory Move/Rename/Undo/Redo, preserving unsaved text and one current synchronizer. Remove the bridge only after X162 and connection lifecycle tests pass without it. |
| **UP20 — IntelliJ test Driver — bridged**; filed as [IJPL-259883](https://youtrack.jetbrains.com/issue/IJPL-259883) (2026-10-28, project `AT` is not public); our intellij-community [#3729](https://github.com/JetBrains/intellij-community/pull/3729) (`173cc67e32e2`, on `master` `cdc10bf305a5`), open; local fix verified natively 2026-10-20 (see [upstream fixes](#upstream-fixes-prepared-2026-10-20)) | An EDT remote call captures modality, then posts its actual call separately; a newly opened modal dialog can indefinitely defer that call. | Test-only popup focus observations/activation use a bounded Swing dispatch from the default remote dispatcher. X165 stalled before submission while the compiler was idle; saved thread dumps identify `Invoker.invoke`/`LaterInvocator` and `restorePopupFocus`. Native rename now also observes focus after its dialog opens. Details below. | Driver dispatch survives a modal transition between its state capture and invocation; selected native Rename and focus-recovery cases pass without the bridge. |
| **UP21 — LSP4IJ — bridged**; upstream [#1726](https://github.com/redhat-developer/lsp4ij/pull/1726) and [#1731](https://github.com/redhat-developer/lsp4ij/pull/1731) merged, unreleased (apply, Undo and Redo, and the `will*` edits of file operations; Undo and Redo of those do not save) | WorkspaceEdit changes closed document buffers without saving or synchronizing them to the server. Compiler queries continue reading the old disk contents. | `ClosedRefactoringDocuments` saves only edited, closed buffers after the transaction and native Undo/Redo; pre-existing unsaved closed buffers refuse before application. X169 exposed the problem while X161's unchanged class name concealed it. | Upstream keeps closed-file content visible to the server across apply/Undo/Redo without our bridge; X169 verifies exact disk text and current diagnostics. |
| **UP22 — LSP4IJ — fixed upstream**; our [#1699](https://github.com/redhat-developer/lsp4ij/pull/1699), merged 2026-10-16 (`eb45e3fc`), unreleased | A document-change callback can restart a failed connection, dispose its own synchronizer, then create a pull-diagnostic Alarm owned by that disposed synchronizer. | X202 exposed `DocumentContentSynchronizer.sendDidChangeEvents` → connection restart → `getDebouncePullDiagnosticsAlarm`. The triggering report-copy exception was our UP07 bridge bug and is fixed locally; no patch to the upstream disposal path is installed. Details below. | #1699 creates the Alarm with its synchronizer, under a private parent, and schedules pull diagnostics under the lock that disposal takes, so a document change after transport failure creates nothing under a disposed parent. Gate: a release with #1699; then native reconnect and typing acceptance. |
| **UP23 — VS Code — open**; filed as [microsoft/vscode#344765](https://github.com/microsoft/vscode/issues/344765), fix in our [microsoft/vscode#344772](https://github.com/microsoft/vscode/pull/344772) (`a19bc2add97`, on `main` `915c6faed9c`), open (2026-10-28) | VS Code 1.140.0 applies requested file moves in their supplied order after the Ecstasy participant's edits, and Undo replays inverse moves in forward order. Parent-first overlapping moves fail with `EntryNotFound` after partial application; child-first moves apply, but Undo fails with `target already exists`. | No host workaround: the participant cannot replace, reorder or veto host-owned moves. The compiler proposal normalizes both orders to child-first, which IntelliJ applies as one transaction. X218 remains an explicit failed assertion (`run-VfDauh`, `run-S7CfWP`, `run-5flalU`, `run-J054rk`, `run-sDtdSi`); the `// TODO VSCODE: UP23` marker is in [rename-proposal.ts](../lang/vscode-extension/src/rename-proposal.ts). [Details below](#up23-overlapping-vs-code-file-moves-retain-the-hosts-order). | Host Undo reverses both levels for dependent moves, with regression coverage for repeated Undo/Redo; or a host API replaces the transaction, or a separate Ecstasy-owned Move command has complete undo ownership. |
| **UP24 — IntelliJ Platform, used by LSP4IJ — bridged**; filed as [IJPL-259881](https://youtrack.jetbrains.com/issue/IJPL-259881) (2026-10-28), a duplicate of the older [IJPL-29917](https://youtrack.jetbrains.com/issue/IJPL-29917) (2024) and [IJPL-223024](https://youtrack.jetbrains.com/issue/IJPL-223024) (LSP formatting), which JetBrains linked to it and we commented on; our intellij-community [#3727](https://github.com/JetBrains/intellij-community/pull/3727) (2026-10-28, `4db73c9a1892`, on `master` `cdc10bf305a5`; renamed to IJPL-29917, the oldest of the three tickets, its commit `0f3164145877` reworded with no change to the code), open; local fix verified natively 2026-10-20 (see [upstream fixes](#upstream-fixes-prepared-2026-10-20)) | `IU-262.10968.63` applies asynchronous formatting through `AsyncDocumentFormattingSupportImpl` with `CommandProcessor.runUndoTransparentAction`, the path LSP4IJ 0.21.0 uses. Standalone Reformat produces the correct text and Undo restores the original, but no Redo follows. | [XtcFormattingService](../lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt) applies asynchronous replies in a named EDT write command after cancellation, project/file lifetime, revision and original-text checks; the EDT never waits for the reply, and synchronous Save keeps the platform command. `run-12440566880428489550` failed X249/X250 at Redo; `run-14744074393597958558` passes START and X249/X250/X139/X132 with zero IDE failures. [Details below](#up24-asynchronous-intellij-formatting-loses-redo). | Native standalone formatting and range/save/closed-document acceptance retain history without the asynchronous write-command path on a repaired platform. |
| **UP25 — LSP4IJ — constrained**; feature request [#1744](https://github.com/redhat-developer/lsp4ij/issues/1744), implemented by [#1749](https://github.com/redhat-developer/lsp4ij/pull/1749), merged, unreleased | No LSP 3.18 document-content provider or refresh handler. | Matching read-only file fallback and direct library monikers; X254 executes those assertions with explicit partial status. [Details below](#up25-lsp4ij-has-no-lsp-318-library-content-provider). | Upstream virtual URI resolution, read-only views, refresh and disposal pass native X254 before advertising the capability. Nightly check (2026-10-27, LSP4IJ 0.21.1-20261027-160212 with `-Plsp4ijChannel=nightly`): LSP4IJ declares `workspace/textDocumentContent` itself, so library navigation opens the server's `ecstasy-library:` documents, read-only, without files; X5, X35, X101, X125, X267 and X268 pass once the playbook reads them so (`8d845d167e`, `3b7c450765`), and the UP25 replacement gate passes (`run-3971918243094114974`). |
| **UP26 — LSP4IJ — constrained**; upstream [#1715](https://github.com/redhat-developer/lsp4ij/pull/1715) merged, unreleased | Inline requests always use Automatic and omit selected popup context. | Native unique suggestions work; X257 has protocol-only explicit alternatives/selection. [Details below](#up26-lsp4ij-inline-completion-loses-invocation-and-popup-selection-context). | Forward invocation kind and selection, then pass native X257. Nightly check (2026-10-27): the UP26 replacement gate passes with LSP4IJ 0.21.1-20261027-160212 (`run-13611061193587205138`); it failed once in a full run (`run-3971918243094114974`). |
| **UP27 — LSP4IJ — bridged; long continuation passes**; upstream [#1700](https://github.com/redhat-developer/lsp4ij/pull/1700) merged, unreleased | Semantic-token and code-lens refresh fan out across connected files on the application executor, exceeding IntelliJ's concurrent non-blocking read-action limit in a long session. | `EditorRefresh` replaces that fan-out with one coalesced read/UI pass per connection and feature, retaining LSP4IJ's rendering bridges. X146 adds a many-document refresh burst; X259 retains toggling/restart acceptance. Focused and long continuation runs pass with zero IDE errors/freeze dumps. Original errors remain recorded. | Remove the bridge when upstream provides bounded batch refresh and connection-lifetime cancellation, then repeat the many-file stress and long native acceptance. |
| **UP28 — vscode-languageclient — bridged**; lost promise fixed upstream in [#1867](https://github.com/microsoft/vscode-languageserver-node/pull/1867), unreleased; unhandled `stop()` reported as [#1307](https://github.com/microsoft/vscode-languageserver-node/issues/1307), fixed by our [#1896](https://github.com/microsoft/vscode-languageserver-node/pull/1896), merged 2026-10-21, unreleased | Closing the transport during initialization clears the internal startup promise before `start()` returns it. The lost promise can reject unhandled while the returned promise resolves, and default error handlers display duplicate notifications. | The Ecstasy client observes both the initiating and idempotent in-flight `start()` promises, requires Running, and owns one actionable startup notification. X272 checks command rejection, one visible failure, no unhandled rejection, offline export and recovery; `run-W9g4f3` passes X269–X272. | Upstream captures and always returns/observes the original startup promise, including transport closure. Remove the double observation only after X272 passes without it. |
| **UP29 — IntelliJ test Driver — bridged**; filed as [IJPL-259884](https://youtrack.jetbrains.com/issue/IJPL-259884) (2026-10-28); our intellij-community [#3730](https://github.com/JetBrains/intellij-community/pull/3730) (`2c3ca84317cc`, on `master` `cdc10bf305a5`), open; local fix verified natively 2026-10-20 (see [upstream fixes](#upstream-fixes-prepared-2026-10-20)) | `waitFor` checks a successful predicate again after leaving its loop. A changed connection state can then produce an immediate false timeout; its deadline also uses wall-clock time. | Test-only `UiWaits` accepts each observation once and uses a monotonic deadline. Five regressions cover changing readiness, pending reads, actual timeout, cancellation and nullable values. Restart/refresh X137/X146/X259 pass with the UP27 bridge disabled. | Driver accepts the successful observation without another remote read and uses elapsed monotonic time; the regression and restart selection pass without our polling implementation. |
| **UP30 — LSP4IJ — constrained**; fixed by our [#1757](https://github.com/redhat-developer/lsp4ij/pull/1757), merged 2026-10-26, unreleased | `DAPDebugProcess` does not implement `startPausing()`, in 0.21.0 and on `main`, so IntelliJ's Pause Program never sends DAP `pause`; DAPSupport.md lists Pause as unsupported. | No bridge: XVM's IntelliJ X308 is recorded as not implemented, with this reason. The language server answers `pause` and shows a thread while the program runs; VS Code's X308 passes. With a plugin built from #1757, IntelliJ's X308 passes natively; with one built from `main` (`ef2b18c3`), it times out. | After a release with the fix: restore IntelliJ's X308 case, set its coverage to full, and pass it natively. Nightly check (2026-10-27): IntelliJ's X308, new in `09ffee515b`, pauses through Pause Program and passes with LSP4IJ 0.21.1-20261027-160212 (`run-7834140860152509548`). |
| **UP31 — LSP4IJ — bridged**; fixed by our [#1758](https://github.com/redhat-developer/lsp4ij/pull/1758), merged 2026-10-26, unreleased | After a stop with the reason `exception`, `DAPClient.stopped()` takes the first editor of the stopped file and schedules `showErrorHint()` on it one second later, "to be sure that scroll of the editor has occurred"; neither the scheduled task nor `doShowErrorHint()` checks that the editor is still open or the session still stopped. An editor closed within that second fails with `DisposalException` "Editor is already disposed", an IDE error (0.21.0 and `main` `ef2b18c3`). | Test-only: IntelliJ's X307 waits until the hint shows (`DebugSessionUi.hintShown()`) before it resumes and closes the file. Without the wait, X307 passed and the playbook task failed on the IDE error (`run-3784560206800358628`); with it, X280–X285, X287, X289, X304–X307 and X310 pass with no IDE errors (`run-1497514308845821898`). The fix returns from `doShowErrorHint()` for a disposed editor; `DAPClientErrorHintTest` fails without it with that `DisposalException`. Natively, a local X307 that closes its file right after the stop and waits three seconds fails on the IDE error with a plugin built from `main` (`run-8599930350279268487`) and passes without IDE errors with one built from the fix (`run-6504581333662180801`). | A release that skips the hint for a disposed editor: X307 without the wait, natively, with no IDE errors. |
| **UP32 — LSP4IJ — fixed upstream**; our [#1759](https://github.com/redhat-developer/lsp4ij/pull/1759), merged 2026-10-26, unreleased | LSP4IJ sends only a breakpoint's condition: "Evaluate and log" and Suspend are ignored, so there are no logpoints. A breakpoint whose Suspend is unchecked stops in the debug adapter; `breakpointReached()` then returns false and LSP4IJ ignores that, so the program stays stopped while IntelliJ shows it running. | None: IntelliJ's X311 and X313 check only the condition, X313 its watches too (`TODO LSP4IJ: UP32` in its case). With a plugin built from #1759 (and #1763), a breakpoint with Suspend unchecked logs through XVM's adapter, which has logpoints since R9, and the program runs on (`run-10639172617723907439`, [prepared features](#lsp4ij-dap-features-prepared-2026-10-22)). | Adoption: after a release with it and R9's logpoints in the adapter, a shared logpoint case passes natively in IntelliJ. |
| **UP33 — LSP4IJ — fixed upstream**; our [#1761](https://github.com/redhat-developer/lsp4ij/pull/1761), merged 2026-10-26, unreleased | LSP4IJ never sends `exceptionInfo`, so an exception stop's hint has only the stopped event's text. | None: XVM's adapter answers `exceptionInfo` since R9. With a plugin built from #1761, X307's hint shows the exception's stack (`run-14674276171465489822`); with `main`, only the stopped event's text. | Adoption: after a release and R9's `exceptionInfo`, X307's IntelliJ hint shows the stack. Nightly check (2026-10-27): IntelliJ's X312, new in `09ffee515b`, reads the hint's exception, description and stack and passes with LSP4IJ 0.21.1-20261027-160212 (`run-15063466997126294370`). |
| **UP34 — LSP4IJ — fixed upstream**; our [#1760](https://github.com/redhat-developer/lsp4ij/pull/1760), merged 2026-10-26, unreleased | LSP4IJ sends only the enabled exception filters' ids: a filter's condition (`supportsCondition`, `filterOptions`) cannot be given, so exception breakpoints by type are unavailable. | None: X312 is not implemented in IntelliJ. With a plugin built from #1760, All Exceptions conditioned on `IllegalArgument` stops only there, past the caught `IllegalState` (`run-13613178393671792334`); with `main`, at the caught one (`run-9967040128816040351`). VS Code passes X312. | Adoption: after a release and R9, a shared case stops only at the exception type given as the condition. Nightly check (2026-10-27): IntelliJ's X312, new in `09ffee515b`, stops only at the condition's class and passes with LSP4IJ 0.21.1-20261027-160212 (`run-15063466997126294370`). |
| **UP35 — LSP4IJ — fixed upstream**; our [#1768](https://github.com/redhat-developer/lsp4ij/pull/1768), merged 2026-10-26, unreleased | `presentationHint.lazy` is ignored: a lazy variable shows the adapter's placeholder and a child to expand. | Since R10a (`1d88e086d`) XVM's computed properties and `toString()` are lazy variables; IntelliJ's X316 expands one to read its value, tagged TODO LSP4IJ: UP35. With a plugin built from #1768, X316 clicks the Show value link instead, natively (2026-10-26). | Adoption: after a release with it, X316 through the link only, its 0.21.0 path and tag removed. |
| **UP36 — LSP4IJ — fixed upstream**; our [#1769](https://github.com/redhat-developer/lsp4ij/pull/1769), merged 2026-10-26, unreleased | LSP4IJ never asks for a value format, so hexadecimal values (`ValueFormat.hex`) cannot be chosen. | None: since R10b (`cb551d4c2`) the adapter formats values; IntelliJ's X317 checks them only when LSP4IJ has the toggle, tagged TODO LSP4IJ: UP36. With a plugin built from #1769, it does, natively (2026-10-26). | Adoption: after a release with it, X317's hexadecimal check always on, and its tag removed. |
| **UP37 — LSP4IJ — fixed upstream**; our [#1763](https://github.com/redhat-developer/lsp4ij/pull/1763), merged 2026-10-26, unreleased | `DAPDebuggerEditorsProvider.createExpressionCodeFragment(project, text, context, isPhysical)` ignores `isPhysical`, and `DAPExpressionCodeFragment.isPhysical()` always returns false. IntelliJ 2026.2's `createDocument()` asks for a physical fragment, and its logpoint editor (`XLogpointHighlighter` in `XLogpointEditor.kt`) runs `LocalInspectionsPass` on the "Evaluate and log" expression, which asserts `file.isPhysical()`: "can't inspect non-physical file: FILE; LightVirtualFile: /DAPExpressionCodeFragment.txt". IntelliJ IDEA 2026.2.3 ships `-ea` in `idea.vmoptions`, so users see it as an IDE error, not only tests. The stack has no LSP4IJ or XVM frames; it is LSP4IJ's fragment that breaks the contract. A second symptom (2026-10-26, R10e's X320): `PsiDocumentManagerBase.getCachedDocument` returns null for a non-physical file, so `getDocument` takes the LightVirtualFile's document and, once an edit is uncommitted, finds it longer than the PSI and throws "Document/PSI mismatch" (from `BackgroundHighlighter`): one character typed in Evaluate Expression, or a chosen completion, is an IDE error. | X320 closes its completion lookup unchosen with 0.21.0, tagged TODO LSP4IJ: UP37; with a plugin built from `main` it inserts the chosen name, with no IDE error (`run-8742366318524741203`). Otherwise none: seen in the UP32 checks with a breakpoint that logs `n`, with plugins built from `main` (`run-6266382029699247857`) and from UP32 (`run-6896064008597954264`); no shared case sets a log expression. IntelliJ shows the inline logpoint editor only for a breakpoint whose Suspend is unchecked: a suspending one with the same expression reports nothing on `main` (`run-4529688658410320836`). The fix (`fix/dap-physical-expression-fragment`, `e869c476`) passes the fragment's `isPhysical` through; with it, the Suspend-unchecked breakpoint reports no error (`run-695486991974889100`, where X304 then times out as expected without UP32). Its two new tests fail without it; LSP4IJ's `./gradlew check` runs 745 tests, 3 skipped, apart from the 7 macOS-only failures `main` has too. | A release whose fragment honors `isPhysical`: a breakpoint's log expression shows in IntelliJ 2026.2 with no IDE error, and X320 inserts its chosen completions. |
| **UP38 — VS Code — by design; not filed** | VS Code's debugger never asks for a value format: no request carries `format`, and `supportsValueFormattingOptions` appears only in `debugProtocol.d.ts` (checked against `~/src/vscode`). Its maintainers closed the hexadecimal request [microsoft/vscode#28025](https://github.com/microsoft/vscode/issues/28025) as completed in 2020: debug extensions contribute Variables view context menu items (`debug/variables/context`) and refresh the values with the `invalidated` event. | Since R10b (`cb551d4c2`), the extension's own Show Values as Hexadecimal in the Variables and Watch views' menus sets the session's format with `xtc/valueFormat`, and the adapter sends `invalidated`; IntelliJ's side is UP36. | None: the extension point is VS Code's supported way. |
| **UP39 — VS Code — open**; upstream feature request [microsoft/vscode#110067](https://github.com/microsoft/vscode/issues/110067), "Implement Debugger Modules View", open since 2020 | VS Code has no Modules view and never sends the DAP `modules` request; `supportsModulesRequest` appears only in `debugProtocol.d.ts`. `loadedSources` is supported, in the Loaded Scripts view. | None: R12's loaded modules can be offered through `loadedSources` in VS Code, or left out there. | A release with a Modules view that sends `modules`. |
| **UP40 — LSP4IJ — fixed upstream**; our [#1766](https://github.com/redhat-developer/lsp4ij/pull/1766), merged 2026-10-25, unreleased | LSP4IJ does not announce `supportsInvalidatedEvent` in `initialize`, so debug adapters do not send the DAP `invalidated` event, and does not handle it: after an evaluation that changes the stopped program, the Frames, Variables and Watches views keep the old values until the next stop (0.21.0 and `main`). `DAPSupport.md` listed it as unsupported. | None yet: bounded evaluation's tier 2 (lang/doc/plans/plan-bounded-evaluation.md) will send `invalidated` after an evaluation with side effects, which VS Code handles; until a release has #1766, IntelliJ shows the new values at the next stop. | Adoption: after a release with it and tier 2, a shared case whose console evaluation changes a variable shows the new value in IntelliJ without a step. |
| **UP41 — IntelliJ Platform, then LSP4IJ — open; IntelliJ part not filed; LSP4J part our draft [LSP4J #1009](https://github.com/eclipse-lsp4j/lsp4j/pull/1009)** (2026-10-24, cause 2026-10-26) | An IntelliJ evaluation that runs code cannot be cancelled. `XEvaluationCallback` has no obsolescence (`XValueCallback` has no `isObsolete()`, `XEvaluationCallbackBase` adds none), and the Evaluate dialog neither cancels an evaluation when it closes or evaluates again nor offers to stop one, so no evaluator learns that its result is abandoned. LSP4IJ never sends DAP `cancel` either; LSP4J's cancellation sends LSP's `$/cancelRequest` notification, which a DAP endpoint writes as an `event`, but LSP4IJ cancels no DAP request today. Our draft LSP4J #1009 (2026-10-28) makes `DebugRemoteEndpoint` send `cancel` with the request's `seq` as `requestId` instead. | None: the server ends a run at its two-second limit, and at a resume, step or stop. X314 checks cancel in VS Code only. | An IJPL ticket for an evaluation's obsolescence or cancel; LSP4J #1009 merged and released, so that a cancelled DAP request sends `cancel`; then an LSP4IJ evaluator that cancels an abandoned evaluation, to an adapter with `supportsCancelRequest`; then X314's cancel in IntelliJ. |
| **UP42 — LSP4IJ — fixed upstream**; our [#1770](https://github.com/redhat-developer/lsp4ij/pull/1770), merged 2026-10-26, unreleased | LSP4IJ asks for all of a variable's children at once, with no `filter`, `start` or `count`, and ignores `indexedVariables` (0.21.0 and `main`): a large array shows only what the adapter gives a read of all its elements, which for XVM is the first 100 and how many more. | None: XVM's adapter answers such a read with its first 100 elements and a last entry saying how many more; VS Code pages by `indexedVariables`. IntelliJ's X318 checks that, tagged TODO LSP4IJ: UP42. | A fix that reads a large variable a page at a time, as IntelliJ's own `tooManyChildren(remaining, addNextChildren)` link does; then X318 reads `[200..249]` in IntelliJ. |
| **UP43 — the adapters' practice; not filed** (2026-10-26: vscode-js-debug, CodeLLDB, debugpy and java-debug all send `start` 0-based, so a change in LSP4IJ would break them) | DAP measures a CompletionItem's `start` in the client's column base, within the request's text. LSP4IJ declares `columnsStartAt1`, but `DAPLookupElementDecorator.getStartOffset()` inserts at `start` as a 0-based offset into the expression document, and VS Code 1.140's console takes it as a 0-based index into the text for `filterText`. A 1-based `start`, as the spec asks, inserts one character late in IntelliJ. | XVM's adapter sends `length` only, as `XtcDebugServer.completions` says (`0e0d7e2c4`): LSP4IJ then starts at the word at the caret, and VS Code replaces `length` characters before the cursor. | None expected: the clients and adapters agree on a 0-based `start`. |
| **UP44 — LSP4IJ — fixed upstream**; our [#1773](https://github.com/redhat-developer/lsp4ij/pull/1773), merged 2026-10-27, unreleased | `DAPDebugProcess.runToPosition()` registers a temporary breakpoint and resumes, and `resume(context, position)` removes that breakpoint once the `continue` request is answered (`client.continue_(threadId)`, then `unregisterTemporaryBreakpoint`), not when the program stops there, in 0.21.0 and on `main` (`42f1f16e`). An adapter answers `continue` as the program resumes, so a program that takes longer to reach the cursor than that round trip runs past it: Run to Cursor stops only on lines the program reaches within a few milliseconds. VS Code 1.140 removes its temporary breakpoint when the session stops (`runTo`). | None: XVM's adapter installs and removes breakpoints as asked. X323's program counts to 3,000,000 before the cursor's line: VS Code stops there; IntelliJ's case checks that the program runs past it to its exit, tagged TODO LSP4IJ: UP44. With the cursor two statements after the stop, IntelliJ stopped there (`run-12684204973180075528`); after the count, it ran to its exit (`run-12868842670597168794`). | A release with #1773, which keeps the temporary breakpoint until the program next stops and forgets it when the session ends, as VS Code does: with a plugin built from it (`9971b1cd`), IntelliJ's X323 stops at the cursor after the count and then runs to its exit (`run-879197117312965926`). Then X323 is full in IntelliJ. |
| **UP45 — LSP4IJ — fixed upstream**; our [#1774](https://github.com/redhat-developer/lsp4ij/pull/1774), merged 2026-10-27, unreleased | LSP4IJ never sends `loadedSources` or `modules` and has no view for them, in 0.21.0 and on `main` (`42f1f16e`): neither request appears in its sources. | None: XVM's adapter answers both since R12, which VS Code's Loaded Scripts view shows (X326); IntelliJ's X326 is not implemented. | A release with #1774, whose Loaded Sources and Modules tabs show what the adapter reports: with a plugin built from it (`e6bd8913`), a temporary IntelliJ X326 found both tabs, 562 sources with the program's file and the bundled `Collection.x`, and the program's module as its own (`run-15025768559611284996`). Then IntelliJ's X326 is implemented. Nightly check (2026-10-27): IntelliJ's X326, new in `09ffee515b`, reads both views and passes with LSP4IJ 0.21.1-20261027-160212 (`run-15063466997126294370`). |
| **UP46 — LSP4IJ — fixed upstream**; our [#1780](https://github.com/redhat-developer/lsp4ij/pull/1780), merged 2026-10-27 (`0d3c64de`), unreleased | LSP4IJ has no function breakpoints: it never sends `setFunctionBreakpoints`, in 0.21.0 and on `main` (`e9f8bb42`), whose sources name neither the request nor `supportsFunctionBreakpoints`; its breakpoints are lines, disassembly instructions and exception filters. | None: XVM's adapter installs function breakpoints since R9 (X328), which VS Code's Breakpoints view adds; IntelliJ's X328 exists but is not implemented in the catalog. | A release with #1780 (`d870dd93`), whose DAP Function Breakpoint type, added from the Breakpoints dialog's "+", sends `setFunctionBreakpoints` to adapters that advertise them and matches their stops by `hitBreakpointIds`. Native check (2026-10-27): with a plugin built from it, IntelliJ's X328 stops in `Entered.twice` below `Entered.run`, the breakpoint verified, and runs to its exit (`run-11586456472789120644`); again at `bef74868`, rebased onto `main` with #1752 and numbering its requests per server (`run-551361467612578401`). Then IntelliJ's X328 is made full. |
| **UP47 — LSP4IJ — fixed upstream**; our [#1778](https://github.com/redhat-developer/lsp4ij/pull/1778), merged 2026-10-27, unreleased | LSP4IJ has no Reset Frame for a DAP session: it never sends `restartFrame` and ignores `supportsRestartFrame` and a frame's `canRestart`, in 0.21.0 and on `main` (`42f1f16e`, and `d9ab07d7`), whose sources name none of them; IntelliJ's drop-frame handler is not implemented for DAP. | None: XVM's adapter restarts frames since R11 (X329), which VS Code's Call Stack view offers; IntelliJ's X329 is not implemented. | A release with #1778 (`62c4a53e`), whose `DAPDropFrameHandler` offers Reset Frame on a frame with `canRestart` when the adapter advertises `supportsRestartFrame`, sends `restartFrame`, and resumes the session until the `restart` stop; its `DAPDropFrameHandlerTest` passes. Native check (2026-10-27): with a plugin built from it, IntelliJ 2026.2.3's Reset Frame action is offered on inner() and outer() and hidden on run(), whose `canRestart` is false, and resetting outer() stops on its first line; X329, X306 and X327 pass (`run-17331679936087029005`, `7493591240`). Then IntelliJ's X329, which exists, is made full. |
| **UP48 — LSP4IJ — fixed upstream**; our [#1782](https://github.com/redhat-developer/lsp4ij/pull/1782), merged 2026-10-28 (`c7955a13`), unreleased | LSP4IJ has no data breakpoints: it never sends `dataBreakpointInfo` or `setDataBreakpoints`, in 0.21.0 and on `main` (`42f1f16e`), whose sources name neither nor `supportsDataBreakpoints`. | None: XVM's adapter installs data breakpoints on an object's fields since R9 (X330), which VS Code's Break on Value Change adds; IntelliJ's X330 exists but is not implemented in the catalog. | Our #1782 (`bf392a08`, on `main` `0d3c64de`) offers Break on Value Change on a DAP value of the Variables and Watches views when the adapter advertises `supportsDataBreakpoints`, asks `dataBreakpointInfo`, and sends `setDataBreakpoints`; a breakpoint whose data ID cannot persist is removed when its session ends. IntelliJ 2026.2's split debugger gives the views frontend proxies of the values (`FrontendXNamedValue`), so the action finds the DAP value through `XDebuggerTreeBackendOnlyActionBase.getSelectedValue` (2025.3 and later, by reflection). Its `DAPBreakOnValueChangeSessionTest` runs a debug session attached to a DAP server in the test's process: the action, on the `DAPValue` of a field, asks `dataBreakpointInfo` with its container, name and frame, sends `setDataBreakpoints`, and the breakpoint goes with the session. Native check (2026-10-27): with a plugin built from it, IntelliJ's X330 adds `Counter.count`, verified, stops after each of the two changes, runs to its exit, and the breakpoint goes with the session (`run-15563293796254899407`, `b6191ffbce`). Gate: a release with #1782, and IntelliJ's X330 made full. |
| **UP49 — VS Code and LSP4IJ — open**; VS Code feature request [microsoft/vscode#166450](https://github.com/microsoft/vscode/issues/166450), "Debugging: Support single-thread execution requests", open since 2022; LSP4IJ none (2026-10-27) | Neither editor sends DAP's `singleThread`: VS Code's `continue`, `next`, `stepIn` and `stepOut` always resume every thread, even for an adapter that advertises `supportsSingleThreadExecutionRequests` (a maintainer confirmed it in [#189313](https://github.com/microsoft/vscode/issues/189313)), and LSP4IJ's sources (`42f1f16e`) never name it. | Not built: R11's last item, continuing, stepping or pausing one fiber while the others stay stopped, would need a stop that lets one service run while the rest stay held, which no editor could reach (decided 2026-10-27). | An editor that sends `singleThread`; then R11's last item is built and checked there. |
| **UP50 — LSP4IJ — fixed upstream**; our [#1781](https://github.com/redhat-developer/lsp4ij/pull/1781), merged 2026-10-28 (`7469aab9`), unreleased; related issue [#1432](https://github.com/redhat-developer/lsp4ij/issues/1432), "DAP debugger variables are not showing inline", open (2026-10-27) | IntelliJ shows no inline values for Ecstasy in a DAP session. LSP4IJ never sends `textDocument/inlineValue`, and its `refreshInlineValues` does nothing, in 0.21.0 and on `main` (`f22304a4`). Its own scan of the editor's TextMate tokens (`HighlighterDebugVariablePositionProvider`) places none of the frame's variables. And it leaves the frame's Locals group collapsed (`DAPValueGroup` restores an expansion but never expands), while IntelliJ computes inline data only for the variables it shows: `DAPValue.computeInlineDebuggerData` is never called until the group is expanded. | None needed: XVM's server answers inline values since L79 (X331), which VS Code uses. IntelliJ's X331 exists but is not implemented in the catalog. | Our #1781 (`f76a4fd5`, on `main` `0d3c64de`) asks the stopped file's language server, places each variable at its last lookup before the stop, falls back to the token scan, and expands the locals' group. With a plugin built from it, IntelliJ draws `n = 2` and `before = 3` and X331 passes (`run-1069392384661177323`, again on the rebased head `run-4604772112093360200`). Gate: a release with #1781, and IntelliJ's X331 made full. |
| **UP51 — LSP4IJ — open; not filed** (2026-10-28) | LSP4IJ never sends `textDocument/moniker`: it declares no moniker capability and has no feature or view that uses one, in 0.21.0 and on `main` (`7469aab9`). Its `LSPSupport.md` lists monikers as unsupported, and no LSP4IJ issue or pull request mentions them. IntelliJ itself has no moniker view or navigation for them to feed. | None needed: XVM's server advertises `monikerProvider` and answers since L74/L75, whatever the client declares. IntelliJ's X31 and X252–X254 pass by sending `textDocument/moniker` themselves through LSP4IJ's LSP4J endpoint (`ClientProtocol.kt`), which is protocol coverage, not native; VS Code's cases send it the same way, and neither editor has a moniker view. | An LSP4IJ feature that asks for monikers and uses them in IntelliJ, such as navigating from a library reference to its declaration in another project's server; then a native IntelliJ case. Not worth filing before such a use exists. |
| **UP52 — LSP4IJ — fixed upstream**; our [#1785](https://github.com/redhat-developer/lsp4ij/pull/1785), merged 2026-10-28 (`02faa149`), unreleased; checked natively (2026-10-28) | LSP4IJ declares document-link support but never sends `documentLink/resolve` (0.21.0 and `main`, `7469aab9`), and its Ctrl+Click ignores a link without a `target` (`LSPDocumentLinkGotoDeclarationHandler`). XVM's server takes any `documentLink` client capability as leave to defer targets (`XtcLanguageServer.kt:539`, `textCapabilities?.documentLink != null`), and then answers each link with a `data` handle and no target (`XtcTextDocumentService.kt:1260`). LSP's document-link client capabilities have no field that says a client resolves, so the server cannot tell. | None. X131 passes in IntelliJ because its driver sends `documentLink/resolve` itself, so it does not show the native click. An HTTP(S) link in a comment very likely opens nothing in IntelliJ; manual §15 ("Cmd+Click activates them") would show it. | Our #1785 (`e5e5608a`, on `main` `7469aab9`) resolves a clicked link without a target when the server has `resolveProvider`, which XVM's server sets exactly when it defers targets; its `LSPDocumentLinkGotoDeclarationHandlerTest` fails without it. Checked natively (2026-10-28, IntelliJ 2026.2.3, X131 `a521e2ebbf`): the link's `textDocument/documentLink` result has `data` and no target; with 0.21.0, Go to Declaration at the link says "Cannot find declaration to go to" and opens nothing (`run-2626450875688172472`, `run-2838196313120719418`); with a plugin built from `e5e5608a`, it sends `documentLink/resolve` and opens https://xtclang.org/ (`run-8879176847449743226`, replacement gate UP52 passed). Gate: a release with #1785 (merged as `02faa149`); then X131's `TODO LSP4IJ: UP52` step passes without the gate. |
| **UP53 — LSP4IJ — open; not filed** (2026-10-28) | LSP4IJ has no linked editing: it never sends `textDocument/linkedEditingRange` and has no code for it (0.21.0 and `main`, `7469aab9`); its `LSPSupport.md` lists it as unsupported. XVM's server always advertises `linkedEditingRangeProvider` (`XtcLanguageServer.kt:1097`) and links a local's declaration with its uses. | None: IntelliJ's X133 passes by sending the request through LSP4IJ's LSP4J endpoint (`ParityPlatform.kt:1119`), protocol coverage only. IntelliJ users get no rename-on-type. | An LSP4IJ feature that edits the linked ranges together while typing; then a native IntelliJ X133. |
| **UP54 — LSP4IJ — fixed upstream**; our [#1786](https://github.com/redhat-developer/lsp4ij/pull/1786), merged 2026-10-28 (`0616567a`), unreleased | When IntelliJ formats several ranges of a file at once, LSP4IJ formats only the first: `AbstractLSPFormattingService.getFormattingRange()` takes `ranges.get(0)` and drops the rest (0.21.0 and `main`, `7469aab9`). It never sends LSP 3.18's `textDocument/rangesFormatting`, which XVM's server supports (`rangesSupport`, `XtcLanguageServer.kt:972`; `XtcTextDocumentService.kt:1803`). | None, and our `XtcFormattingService` (UP12's bridge) does the same: `request.formattingRanges.firstOrNull()` (line 44). No case formats several ranges. Our LSP4IJ [#1784](https://github.com/redhat-developer/lsp4ij/pull/1784) documents the limit (UP61). | Our #1786 (`0343267f`, on `main` `e301a971`) sends one `rangesFormatting` to a server with `rangesSupport`, declares `rangesSupport` itself, and otherwise sends one `rangeFormatting` per range, formatting two ranges whose edits overlap again as one, as VS Code does; its `RangesFormattingTest` fails without it. Not checked natively: our bridge takes over formatting and still takes the first range. Gate: a release with #1786 (merged as `0616567a`), our bridge sending every range while it exists, then a native case that formats two separate ranges. |
| **UP55 — LSP4IJ — fixed upstream**; our [#1787](https://github.com/redhat-developer/lsp4ij/pull/1787), merged 2026-10-28 (`0ecbcdeb`), unreleased | LSP4IJ never sends a hit condition for a line breakpoint: `DAPBreakpointHandlerBase.addSourceBreakpoint` sets only `condition` and `logMessage` (0.21.0 and `main`, `7469aab9`). Function and data breakpoints send `hitCondition` (#1780, #1782). XVM's adapter supports hit conditions since R9 (`supportsHitConditionalBreakpoints`). | None. `remaining.md`'s R9 item puts IntelliJ's hit counts under UP32, but our #1759 (UP32) added logpoints only. | Our #1787 (`f273e8ca`, on `main` `e301a971`) gives a DAP line breakpoint a `Hit condition`, kept with its properties and changed in the Breakpoints dialog, as function and data breakpoints have, and sends it as `hitCondition`; its `DAPBreakpointHitConditionTest` covers the request, persistence and the panel. Gate: a release with #1787 (merged as `0ecbcdeb`); then a native IntelliJ hit-count case. |
| **UP56 — LSP4IJ — fixed upstream, bridged**; our [#1791](https://github.com/redhat-developer/lsp4ij/pull/1791), merged 2026-10-28 (`116a18d1`), unreleased | LSP4IJ ignores DAP's `exited` event: `DAPClient` has no `exited()` (0.21.0 and `main`, `7469aab9`), and `DAPSupport.md` lists it as unsupported. The program's exit code never reaches IntelliJ through LSP4IJ: the console shows the debug adapter's exit code, often 143. | XVM's in-process transport, `LanguageServerDebugProcessHandler`, reads `exited` from the session's messages and ends its process with that exit code (`LanguageServerDebugProcessHandler.kt:101–119`). | Our #1791 (2026-10-28, `a1aceff5`): `DAPClient.exited()` puts the exit code on the session's process handler (`DAPDebugProcess.PROGRAM_EXIT_CODE`), and the handler `DebugAdapterDescriptor.startServer()` creates shows "Process finished with exit code N" with it (`DAPProcessTerminatedListener`, its `DAPProcessTerminatedListenerTest`); Angelo left the choice between that and a process handler that ends with the program's exit code to us, and it stays. CodeRabbit's finding, a child session's exit code replacing its parent's, was fixed in it first (`f295ec5a`). Gate: a release with #1791 (merged as `116a18d1`); then our handler, which `startServer()` does not create, takes the exit code from `PROGRAM_EXIT_CODE` instead of reading `exited`, and a debug run's exit code still shows in IntelliJ. |
| **UP57 — LSP4IJ — fixed upstream**; our [#1790](https://github.com/redhat-developer/lsp4ij/pull/1790), merged 2026-10-28 (`436a4db5`), unreleased; no effect on XVM | `workspace/inlineValue/refresh` does nothing: `LanguageClientImpl.refreshInlineValues()` returns at once, and LSP4IJ declares no `workspace.inlineValue.refreshSupport` (0.21.0 and `main`, `7469aab9`). | None needed: XVM's server sends the refresh only to a client that declares it (`XtcLanguageServer.kt:588`), so LSP4IJ never gets one, and IntelliJ asks for inline values again at each stop (UP50). | Our #1790 (2026-10-28, `9c823a00`, with UP58) declares `refreshSupport`; a refresh drops the inline values of each stopped DAP session's current frame and rebuilds its views, which asks the servers for them again. After a release with it, XVM's server sends LSP4IJ the refresh too. |
| **UP58 — LSP4IJ — fixed upstream**; our [#1790](https://github.com/redhat-developer/lsp4ij/pull/1790), merged 2026-10-28 (`436a4db5`), unreleased; no effect on XVM | LSP4IJ has no LSP 3.18 `workspace/foldingRange/refresh`: no `refreshFoldingRanges()`, and no `workspace.foldingRange.refreshSupport` declared (0.21.0 and `main`, `7469aab9`). | None needed: XVM's server sends it only to a client that declares it (`XtcLanguageServer.kt:600`), and IntelliJ asks for folding ranges again after each edit. | Our #1790 (with UP57) declares `refreshSupport`; a refresh evicts the folding ranges of the server's open documents and IntelliJ's folding stamp (`EditorFeatureType.FOLDING`), as the code lens, inlay hint and semantic token refreshes do. Against `main`, its test fails with `UnsupportedOperationException`. After a release with it, XVM's server sends LSP4IJ the refresh too. |
| **UP59 — LSP4IJ — fixed upstream**; our [#1788](https://github.com/redhat-developer/lsp4ij/pull/1788), merged 2026-10-28 (`44675cb3`), unreleased; no effect on XVM (2026-10-28) | LSP4IJ never sends `workspaceSymbol/resolve` and declares no `workspace.symbol.resolveSupport` (0.21.0 and `main`, `7469aab9`); `LSPSupport.md` lists it as unsupported. | None needed: without that declaration XVM's server sends each symbol's full location (`resolvesWorkspaceSymbolRange`), computed eagerly. | Our #1788 (2026-10-28, `3618c252`; CodeRabbit's finding fixed in `717b298c`: the implementations search skips an element without a text range) declares `resolveSupport` with `location.range`; opening a URI-only symbol of a server with `resolveProvider` sends `workspaceSymbol/resolve` under a cancellable progress and opens the resolved range, and such a symbol no longer throws in `getTextRange()`. Gate: a release with #1788 (merged as `44675cb3`, for 0.30.0); then XVM's server sends LSP4IJ URI-only symbols, and a native Go to Symbol case opens one at its range. |
| **UP60 — LSP4IJ — fixed upstream**; our [#1789](https://github.com/redhat-developer/lsp4ij/pull/1789), merged 2026-10-28 (`f4e57c05`), unreleased | LSP4IJ resolves an inlay hint only when it has no label, or to get the command of a clicked label part, and declares no `inlayHint.resolveSupport` properties (0.21.0 and `main`, `7469aab9`). Correction (2026-10-28): this was first recorded as having no effect on XVM, which was wrong. LSP4IJ showed only the plain-string tooltips of a hint's label parts, and dropped the hint's own `InlayHint.tooltip` and every `MarkupContent` tooltip, so XVM's Markdown tooltips, which its server puts on the hint, never showed in IntelliJ. | None: XVM's inlay tooltips do not show in IntelliJ with 0.21.0. Without a declared `tooltip` property XVM's server sends them with the hints (`resolvesInlayHintTooltip`). | Our #1789 (merged as `f4e57c05`) shows the hint's tooltip, or else its parts' tooltips one per line, renders Markdown and escapes plain text. A lazy tooltip resolve is not possible: IntelliJ's declarative inlays take the tooltip when the hints are collected, so LSP4IJ still declares no `tooltip` resolve, and XVM's server keeps sending tooltips with the hints. Gate: a release with #1789, then a native case that shows an inlay hint's tooltip. |
| **UP61 — LSP4IJ docs — fixed upstream**; our [#1784](https://github.com/redhat-developer/lsp4ij/pull/1784), merged 2026-10-28 (`e301a971`) | LSP4IJ's support lists disagreed with its code: `LSPSupport.md` marked `inlayHint/resolve` unsupported, though it is resolved in UP60's two cases; listed `textDocument/pullDiagnostics` as unsupported, though that link is `textDocument/diagnostic`'s section, marked supported; listed `textDocument/publishDiagnostics` twice; lacked `workspace/foldingRange/refresh` and `textDocument/rangesFormatting` (UP54, UP58); and linked `textDocument/formatting` to Document Color. `DAPSupport.md` marked the `thread` event unsupported, though `DAPClient.thread()` updates the Threads panel, and lacked the `cancel` request (UP41). | None needed: UP51–UP60 were checked against LSP4IJ's code, not its docs. #1784 also says that only the first of several ranges is formatted. | None: #1784 merged as `e301a971`, and the lists are read on `main`, so no release is needed. |
| **UP62 — VS Code — open**; the existing feature request [microsoft/vscode#17977](https://github.com/microsoft/vscode/issues/17977), "Preserve undo/redo buffer when saving a file 'as'", open since 2017, which also covers renames; commented 2026-10-28 (2026-10-28) | Moving or renaming an open file replaces its text model, so Undo in the moved file no longer reaches edits made in it before the move. `TextFileEditorModelManager` keeps only a snapshot of a dirty model's text before the move and resolves a new model at the target after it, and `ModelService` keeps a disposed model's undo stack only under its old URI (`main` `915c6faed9c`). One workspace edit's file and text edits do share one undo group (`BulkEditService` passes one `UndoRedoGroup` to both). | Rename refuses an edit that moves a file while refactoring auto-save is off (`remaining.md`, the Rename item), because Undo could not restore the moved file's earlier edits. | VS Code carries a model's edit stack over to its new URI ([microsoft/vscode#17977](https://github.com/microsoft/vscode/issues/17977)); then Rename runs with auto-save off and keeps the moved file's history. |
| **UP63 — LSP4IJ — fixed upstream**; our [#1793](https://github.com/redhat-developer/lsp4ij/pull/1793), merged 2026-10-28 (`2afdaf45`), unreleased | LSP4IJ bundles LSP4J's LSP modules at 1.0.0 and its debug (DAP) modules at 0.21.0: `dac4b175` moved `lsp4jVersion` to 1.0.0 for inline completion, and `lsp4jDebugVersion` stayed at the 0.21.0 that `93be941d` had aligned with JetBrains' LSP API (0.21.0 and `main`, `44675cb3`). LSP4J releases all its modules at one version, so `org.eclipse.lsp4j.jsonrpc.debug` 0.21.0 runs on `org.eclipse.lsp4j.jsonrpc` 1.0.0, LSP4IJ's DAP model stays at DAP 1.60 where 1.0.0 implements 1.70, and no fix to LSP4J's DAP endpoint since 0.21.0 reaches IntelliJ: our LSP4J #1004 (merged), #1008 and #1009 (UP41) among them. | None: XVM's debug adapter runs in its language server, with its own LSP4J; only IntelliJ's client side bundles these jars. | Our #1793 (`f264366d`) has the debug modules use `lsp4jVersion`, and drops `lsp4jDebugVersion`; in LSP4J 1.0.0 `DataBreakpointInfoArguments.frameId` is an `Integer`, so two of our #1782's test assertions compare it as one. Its 933 tests pass, 198 of them DAP, but the 7 "outside allowed roots" failures `main` has on macOS. Merged as `2afdaf45`, for 0.30.0. Gate: an LSP4IJ release with it, so that an LSP4J release with #1004, #1008 or #1009 can reach IntelliJ. |

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
expected negative-test notifications are cleared between cases.

Upstream status (2026-10-18): the lost startup promise is fixed by another contributor's
[#1867](https://github.com/microsoft/vscode-languageserver-node/pull/1867), merged 2026-09-29 but not in a release; the latest,
`vscode-languageclient` 10.1.2, is from 2026-09-25. The double `start()` observation stays until a
release contains it and X272 passes without it. The unawaited `stop()` during failed initialization
is still on `main`: the client is still Starting, so `stop()` rejects with "Client is not running
and can't be stopped" and nothing handles it. It is reported as [#1307](https://github.com/microsoft/vscode-languageserver-node/issues/1307), where the
maintainer invited a pull request; [our comment](https://github.com/microsoft/vscode-languageserver-node/issues/1307#issuecomment-6253116299)
confirms it on `main` and proposes releasing the half-started connection without `stop()`, with a
test based on #1867's crash-on-initialize server. [#1896](https://github.com/microsoft/vscode-languageserver-node/pull/1896) handles that rejection. It keeps
calling `stop()`, because the Node client's `shutdown` override terminates the server process even
though the base `shutdown` refuses a starting client; its test shows that a server which refuses
initialization is ended this way, so it is not left running. The description lists why #1867,
#1862, #1863, #1895, #1782, #1310, #1559, #1185 and #1262 do not cover it. Our `stop()` override,
which catches every rejection, stays until a release contains the fix. Pull requests there require Microsoft's contributor license
agreement. The duplicate notifications are configuration (`initializationFailedHandler`), not a
defect to report.

Upstream status (2026-10-21): the maintainer (dbaeumer) merged #1896 on 2026-10-21 as
`49328700`. Neither it nor #1867 is in a release yet: `vscode-languageclient` 10.1.2, from
2026-09-25, is still the latest. #1307 is still open, since the merge did not close it. Both
workarounds, the double `start()` observation and the `stop()` override, stay until a release
contains both fixes and X272 passes without them.

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

The October 7 [local LSP4J repair](#local-lsp4j-repairs-2026-10-07) addressed the
classification by reading `params` into a `JsonElement` before converting them. A JMH benchmark
showed that this roughly doubled parse time for structured parameters, so the upstream fix,
[#997](https://github.com/eclipse-lsp4j/lsp4j/pull/997) for issue
[#996](https://github.com/eclipse-lsp4j/lsp4j/issues/996), keeps converting while streaming. Only
after a conversion fails does it tell a wrong type (no `IOException` cause) from malformed JSON,
and read the rest of the message to keep an `id` that follows `params`. A message that converts
allocates exactly as on `main`. The shipping assertion above still describes the released
dependency, not either repair.

## Follow-up discipline

- [x] File the LSP4IJ and LSP4J reports from the recorded evidence, with the user's authorization
  for each: see [Upstream pull requests](#upstream-pull-requests-2026-10-18). Our EOF cleanup bug was
  kept out of the LSP4IJ startup race report (#1696).
- [ ] File the VS Code (UP16, UP23), IntelliJ Platform (UP17, UP24) and IntelliJ test Driver (UP20,
  UP29) reports. Fixes for UP16, UP17, UP20, UP24 and UP29 are prepared and verified natively (see
  [upstream fixes](#upstream-fixes-prepared-2026-10-20)); filing waits for the user's YouTrack tickets
  and a reply on microsoft/vscode#219122. UP23 has no fix.
- [ ] Recheck UP15 on a repaired LSP4J release, containing
  [#997](https://github.com/eclipse-lsp4j/lsp4j/pull/997), and require `InvalidParams` for typed-parameter
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
Our [#1699](https://github.com/redhat-developer/lsp4ij/pull/1699), merged 2026-10-16 (`eb45e3fc`), is that repair: the Alarm is created with the
synchronizer, and scheduling and publication are serialized with disposal. UP22 stays on the
list until restart and typing validation pass on a release with it.


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
Filed as [microsoft/vscode#344765](https://github.com/microsoft/vscode/issues/344765) (2026-10-28), after no existing report was found. On `main` (`915c6faed9c`),
`FileUndoRedoElement._reverse()` in `bulkFileEdits.ts` still performs the inverse operations in their
original order, and `RenameOperation.perform()` still collects its inverse moves in that order.
Our [microsoft/vscode#344772](https://github.com/microsoft/vscode/pull/344772) undoes the operations, and a rename's moves, in reverse order, so that Redo keeps the
original order; its two new `bulkFileEdits.test.ts` cases fail without it, and 344 tests pass with it.
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

**Upstream implementation (2026-10-20).** [#1749](https://github.com/redhat-developer/lsp4ij/pull/1749),
merged as `e3a9b11e` and not yet released, covers everything listed above: the capability, with
dynamic registration; reading a scheme's documents before navigation and `window/showDocument`;
read-only editors that keep the server's URI and are opened on the server under it; refresh into the
same document; and disposal when the server stops or unregisters the scheme. IntelliJ keeps the
fallback until a release with #1749 passes X254 natively with the capability negotiated.

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
