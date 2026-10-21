# Remaining work

Everything still open on `lagergren/errs`, as one list to tick off. Checked on 2026-10-21
against [the integration plan](errs-integration-plan.md),
[the execution plan](../lang/doc/plans/plan-embedded-execution.md),
[the upstream register](errs-upstream-issues.md), [the PR roadmap](../pr-roadmap.md), and the live
state of every upstream issue and pull request named here. When you tick an item, add the commit,
PR or run that closes it.

Closed and not repeated here: L1–L72 (L61–L67 within their bounded scope), L74–L76, L80, L81,
L83, UI1–UI7, AST1–AST5, PLAT1–PLAT3, R1–R5. Cases those closures refuse on purpose are
documented refusals, not open tasks; the few worth revisiting are under
[beyond the current scope](#beyond-the-current-scope).

## 1. Stale documentation

- [x] `lang/doc/plans/plan-embedded-execution.md`: R3 ticked. Its last condition, a `javatools`
  test of the runtime part, is `EmbeddingSupportTest`, merged with #703 (`bdb674af5`); the
  integration plan closed R3 on 2026-10-19.
- [x] `errs-upstream-issues.md`: LSP4J #999 (Gradle 9.8.1) recorded as merged on 2026-10-20
  (`52acfd58`).
- [x] `errs-upstream-issues.md`: LSP4IJ #1744 (UP25) recorded as closed on 2026-10-20.
- [x] `errs-integration-plan.md`: a 2026-10-21 Marketplace follow-up after the 2026-10-20
  paragraph: #711, the two publishers, #710 assigned to Cam, and the vsce 4.0.0 `--oidc` caveat.
- [x] `errs-integration-plan.md`, the R1–R8 section: the inventory is now the current catalog (299
  cases then; 296 since X273–X275 were retired with the color prototype), with the October 7 284-case
  run as the latest full-catalog run.

## 2. LSP features

### L73: server commands and edit application

- [ ] Route the embedded Run commands through `workspace/executeCommand` to the R2–R5 build/run
  service, without assembling a CLI command or compiling again in the protocol handler.
- [ ] Negotiate `workspace/applyEdit`, with failure handling, for any server-run action that edits.
- [ ] Add a shared acceptance case for each server command, in both editors.
- Rule kept: no generic command registry or placeholder handler without a named user action.

### L77: colors (removed)

- [x] Decided: no Ecstasy color type is coming for now, so document colors are out of the editor
  target. They put swatches and a picker beside color values; they never colored the code.
- [x] Removed the prototype, its flag, X273–X275 and their docs; the theme list and X277's theme
  screenshot moved to the highlighting cases, and `b7cb7df88`'s protocol regression moved to
  `FileEventDuringAnalysisTest` (2026-10-21).
- [x] Mirrored into #693 as `40f2c385e`, with the R3 tick as `8bb4a7d10` (2026-10-21).
- [x] Removed the prototype bullet from #693's "Known follow-ups" (2026-10-21).

### L78: notebooks (deferred)

- [ ] Deferred by decision on 2026-10-05. If taken up: define what a cell is in terms of XTC modules,
  then implement `notebookDocument/didOpen`, `didChange`, `didSave`, `didClose` and the notebook
  synchronization capabilities.

### L79: debug inline values (after R7)

- [ ] `textDocument/inlineValue`, from the R7 debugger's suspended frames.
- [ ] `workspace/inlineValue/refresh`.
- [ ] A shared acceptance case in both editors.

### L82: completion evidence and release gates

- [ ] Agree response-time and memory budgets per supported hardware and platform. The current
  receipts define provisional local budgets only.
- [ ] Prolonged-session acceptance (editing, restart, process leaks) on each supported platform.
- [ ] Cross-platform and remote acceptance: Windows, Linux, and restricted or remote workspaces.
- [ ] An uninterrupted full-catalog VS Code run. The latest 284-case run: 281 pass; X130 (UP16),
  X218 (UP23) and X273 fail. X273 tested the color prototype and is retired with it.
- [ ] For each host limitation, either a released fix with its acceptance passing, or a recorded
  release exception: UP16, UP23 (already accepted as an exception), UP25, UP26, UP17.
- [ ] IntelliJ bulk replacement in a heavily decorated large file (UP17): after a platform fix,
  pass the decorated 20,000-method X145 replacement without the EDT freeze.
- [ ] At closure, update together: this list, the integration plan's checklist, the capability
  matrix, the playbook, the AST/API ownership record and the extraction map.

### Test harness stability

- [x] Popup inspection race: a popup that disappears between its presence check and its row read,
  keeping the document-change guard and never replaying an accepted edit. The rows are read and a
  row is chosen in one IDE step each, so a closed popup reads as absent and the wait reopens it
  through the guard (`803e8a7af`; `focusRecovery` `run-10337819264986324821`, 31 cases
  `run-8746776659092611335`). Mirrored into #693 as `b7d5d1ad2`.
- [ ] Reproduce the full-run IntelliJ X105 intention-popup timeout with its preceding UI state. It
  hasn't recurred in the 277- and 284-case runs, but it was never diagnosed. Tried on 2026-10-21 with
  the 79 cases that ran before it in `run-8615971651239916856`: X105 passes on the current code, with
  the UP07 equal-report bridge off (`run-1679585523095307991`), and with that bridge off and fixture
  files opened before the graph switch, as before `4065e8e6c` (`run-4408482987482729041`). Not
  reproduced; the trigger is still unknown.

### Beyond the current scope

Only if a real need comes up; each is a documented refusal today.

- [x] Rename: fixtures for every unsupported composition route (Implicit, Union, SansCode, Field,
  Native) before enabling source-owned routes; binary and native contracts stay read-only.
  Done 2026-10-21: adopted interface properties (SansCode) now rename; union-typed property reads
  stay refused (they need per-receiver alternatives); Implicit, Field and Native stay refused.
- [ ] Rename: VS Code edited-file resource history with refactoring auto-save off, instead of the
  current pre-edit refusal. Investigated 2026-10-21, no clean implementation: an extension cannot put
  a move and its text edits into one undo group, `isRefactoring` saves only when the setting is on,
  and VS Code moves a dirty file by recreating its model, which drops the old model's text Undo.
  Writing the moved file as create plus delete would itself save it. The refusal stays.
  - [ ] The refusal only works for Rename. In an Explorer move (`willRenameFiles`) the thrown
    refusal is only logged, so the move goes ahead without the Ecstasy edits; and Explorer moves
    never auto-save, so the setting does not protect them.
  - [ ] X103 applies an edit-and-move with a plain `applyEdit` and, after Undo, never checks the
    moved member's text at its old path, which is the documented failure.
- [x] Rename: host discovery of external repositories beyond the explicit `sourceModules`. Done
  2026-10-21: `xtc/discoverConsumers` and **Add consumers from folders** in both editors; discovered
  modules join the graph only through `sourceModules`.
- [x] L63 wider extraction (2026-10-21): bitwise `&` (only `&name` stays refused), locals declared
  with `val`/`var` that never change, and statement runs that declare their own locals or write
  fields.
- [x] L63 mutable captures (2026-10-21): a local changed before the selection is passed by its
  current value (X184 is now positive), and statements that write one local return it to the caller
  (X231 is now positive). This also fixed a hole: a local named with `&` counted as unchanging.
- [ ] L63 broader inline: inlining every read of a constant, or inlining and removing a single-use
  member. `XdkRename.Plan` relocates each original range to one place, so N copies need their own
  proof, like member inline's single copy.
- [ ] L63 global safe delete: deleting public or protected members needs an explicit closed-world or
  consumer-manifest decision first; the proof itself is not the obstacle.
- [x] Remove the leftover `System.err.println("DECL owner ...")` in `XdkMissingDeclarations.kt`
  (from `b970b7f56`, also on #693); it wrote to stderr whenever a method was not a direct class
  member (`33558e8ad`; mirrored into #693 as `b5015fc13`, where `:lang:lsp-server:test` passes 2,374
  tests with the 2 existing skips).

## 3. Embedded run and debugging

### R5 gaps

- [x] IntelliJ gutter Run icon for `.x` files: document line highlighters on the Run lens lines,
  from a new `xtc/runLenses` request, with no PSI needed; clicking one runs the module as the lens
  does (`53c9d3eede`, IntelliJ X280 `run-8190292223384484870`).
- [x] A shared case with a real Gradle build: X303 builds and runs a project made by `xtc init` or
  the IntelliJ wizard's creator in Gradle mode (`7a28e49bd6`, VS Code `run-2VWzIc`, IntelliJ
  `run-6946269300745727450`). IntelliJ's Build Project in Gradle mode is not driven.
- [x] Renaming a root whose editor is still open: discovery drops the open buffer whose file is gone
  when a file on disk has its text (`5ab3a005fc`, X301 in both editors).
- [x] An IDE move of a discovered root into another directory: allowed when the module uses no
  resources and the destination is inside the discovery folders (`a152acfca3`, X302 in VS Code).
- [x] The name and dependency fallbacks of a renamed root whose header doesn't parse: discovery
  records a file added with the exact text of a removed one, and keeps its last parse
  (`54bfdb7d22`).

### R6: DAP lifecycle on real run sessions

- [x] Placeholder half: no synthetic breakpoints or threads; launch and attach fail with the reason;
  only the configuration handshake is advertised (`XtcDebugServerTest`, `eba208bb3`).
- [ ] Connect DAP `launch` to the run session service.
- [ ] No-debug execution through the same service.
- [ ] Program output and completion events.
- [ ] `disconnect` and `terminate`.
- [ ] Advertise only the requests that work.
- [ ] Decide and implement how the DAP facade's shutdown relates to the run it owns.

### R7: runtime debugger bridge

- [ ] Session-scoped runtime hooks and initial suspension.
- [ ] Real, verified breakpoints and exception stops.
- [ ] Fibers and threads.
- [ ] Stacks, scopes and variables.
- [ ] Stepping.
- [ ] Bounded evaluation.
- [ ] Source maps tied to source revisions.
- [ ] Tests: source revisions, stale handles, resume/stop races, multiple service contexts.

### R8: runtime release gate

- [ ] Repeated compile/run/stop/debug soak, with memory and process checks, on every supported
  platform.
- [ ] Parity between IntelliJ and VS Code.
- [ ] Logs that correlate build ID, queue job, artifact revision, worker PID and generation, and run
  ID.
- [ ] Playbook and capability docs brought up to date.
- [ ] Each runtime PR validated on its own.
- Known limit kept: JIT runs are unsupported.

## 4. Upstream fixes

### LSP4IJ: merged on `main`, waiting for a release

XVM pins LSP4IJ 0.21.0 (`gradle/libs.versions.toml`, `lang-intellij-lsp4ij`), still the latest
release (2026-08-31). Each workaround stays until a release contains the fix and its removal gate
passes with the workaround disabled. The gates are in
[the register](errs-upstream-issues.md#register).

- [ ] Watch for the next LSP4IJ release and bump the pin.
- [ ] UP01 (#1696): remove the `ConnectionLifetime` guard in `XtcLspServerSupportProvider`; gate:
  `ConnectionLifetimeTest`, the packaged process regressions, START/STARTUP and
  START/START_PROJECTS.
- [ ] UP02 (#1746): enable `willSaveWaitUntil` and server save formatting; gate: real pre-save
  requests apply version-checked edits before persistence, and X139 extended.
- [ ] UP04 (#1698, #1718): remove the rename snapshot guard and client edit validation; gate:
  stale, closed and reopened document tests and X144.
- [ ] UP05 (#1742): remove `CompilerRootWatches`; gate: changes under external roots observed while
  focused, watchers released on disposal, X124.
- [ ] UP07 (#1730, #1735): remove `DiagnosticResultMessages` and the quick-fix data revision; gate:
  X181–X185, X202, X213 and diagnostic/action Undo/Redo.
- [ ] UP08 (#1710): remove `ConfigurationJson`'s null handling; gate: X266, X268, CFG1–CFG3.
- [ ] UP09 (#1724, #1725, #1728, #1736): remove `DocumentStartupMessages`; gate: startup typing and
  close/reopen regressions.
- [ ] UP10 (#1712, #1734): remove `XtcParameterInfoHandler`; gate: X20 and the retrigger and
  constructor signature cases.
- [ ] UP11 (#1733): remove the `CodeActionMessages` routing; gate: X105, X122, stale refusal and
  native Undo/Redo.
- [ ] UP12 (#1707): narrow the `XtcFormattingService` override; gate: absent-editor, snapshot,
  cancellation and server-lifetime guards, X138 and X139. The UP24 command ownership stays.
- [ ] UP14 (#1716): remove the semantic-result retirement in `XtcLanguageClient`; gate: X146.
- [ ] UP14: decide whether to file the invalidation-on-diagnostics half, which isn't filed.
- [ ] UP18 (#1711): advertise `AsIs` again; gate: X150 keeps exact source, placeholders and Undo.
- [ ] UP19 (#1708): remove `DirectoryDocumentMoves`; gate: X118, X161, X162, X163 and the
  connection lifecycle tests.
- [ ] UP21 (#1726, #1731): remove `ClosedRefactoringDocuments`; gate: X169 (exact disk text and
  current diagnostics).
- [ ] UP22 (#1699): no workaround to remove; gate: native reconnect and typing acceptance.
- [ ] UP25 (#1749): advertise `workspace/textDocumentContent` to IntelliJ; gate: X254 without the
  read-only file fallback.
- [ ] UP26 (#1715): gate: X257 with explicit invocation and selection forwarded.
- [ ] UP27 (#1700): remove `EditorRefresh`; gate: the many-file X146 burst and the long
  X137/X146/X259 run.
- [ ] UP03 (#1720, #1739): narrow the guarded Rename/Move entry points; gate: X103 and X130.
- [ ] UP03: follow LSP4IJ #1740 (generic UI wait during the `will*` requests), still open.
- UP13 stays: the `createSettings` override is LSP4IJ's supported extension point.

### LSP4J

- [ ] UP15: get LSP4J #997 reviewed and merged. @pisv was asked on 2026-10-20; no reply yet.
- [ ] UP15: after an LSP4J release with #997, adopt it and change `XdkStdioTest`'s assertion to
  `ResponseErrorCode.InvalidParams`, keeping reader recovery.
- [ ] UP06: after an LSP4J release with #991 (latest is still 1.0.0, 2026-02-10) and an LSP4IJ
  release with #1723, remove the `DiagnosticReportJson` adapter; gate: full and unchanged reports
  round-trip in the bundled client.

### vscode-languageclient

- [x] UP28: #1896 (fix for #1307) merged by the maintainer on 2026-10-21 (`49328700`); #1307 is
  still open.
- [ ] UP28: after a release with #1867 and #1896 (the latest is still 10.1.2, 2026-09-25), remove
  the double observation of `start()` and the `stop()` override; gate: X272.

### Not filed yet: fixes ready locally

Each is a local commit with an upstream-style test, verified natively on 2026-10-20. Nothing is
pushed. The drafts of the tickets, PR descriptions and the #219122 comment were written to the
2026-10-20 session's scratchpad (`up16/`, `up17/`, `up24/`, `up-driver/`).

- [ ] UP17 (IntelliJ Platform): file a YouTrack ticket (IJPL), then a PR from intellij-community
  `interval-tree-bulk-removal` (`9e4fa930d`).
- [ ] UP17: fix the probe's readiness check. Its decorated replacement started with about 36,000
  of 80,000 highlighters installed, so it never measured the decorated case.
- [ ] UP24 (IntelliJ Platform): ticket, then a PR from `async-format-redo` (`e7f0801a0`).
- [ ] UP20 (IntelliJ test Driver): ticket (project `AT`, or `IJPL` if `AT` isn't public), then a PR
  from `driver-edt-modality` (`2c07e3ae4`).
- [ ] UP29 (IntelliJ test Driver): ticket, then a PR from `driver-waitfor-once` (`5fe70a336`).
- [ ] UP16 (VS Code): comment on microsoft/vscode#219122 (no activity since 2024), then a PR from
  `fix/explorer-cut-paste-stale-nodes` (`4dd451ed`) in `~/src/vscode`.
- [ ] After each is fixed and released: remove the matching test or editor workaround and pass its
  gate (UP16: X130 and the standalone reproduction; UP17: the decorated X145; UP20: Rename and focus
  recovery cases; UP24: formatting history without the write command; UP29: restart X137/X146/X259
  without `UiWaits`).
- UP23 (VS Code overlapping-move Undo) has no fix; X218 stays a failed assertion and an accepted
  exception unless the host gains an API for it.

## 5. Pull requests and the release

- [ ] #693 (the complete reference PR): reviews. It's for building and testing the whole product,
  and likely won't merge as it stands.
- [ ] Keep #693 in sync with new `lagergren/errs` commits (last synchronized 2026-10-21).
- [ ] Reconcile the stacked branches with what actually merged: #685 and #683 were closed unmerged,
  and #685's contracts now live in #693.
- [ ] Submit, when authorized: compilation-owned syntax and semantic facts
  (`errs/compiler-semantic-facts-20261009`).
- [ ] Then incomplete-source, cursor and declaration analysis
  (`errs/compiler-partial-analysis-20261009`).
- [ ] Then the compiler-backed LSP and editor integrations.
- [ ] Build and test each extracted PR on its own; the integrated branch passing doesn't prove a
  slice.
- [ ] Release 0.5.0, the agreed boundary for the breaking listener and embedding API, with migration
  and recompilation notes.
- [ ] Settle the reconciliation inventory in the roadmap's
  [remaining work outside the prepared branches](../pr-roadmap.md#remaining-work-outside-the-five-prepared-branches).
- Reviewers when submitting: ggleyzer, cpurdy, thegridman.

## 6. VS Code Marketplace

All steps and their details are in [#710](https://github.com/xtclang/xvm/issues/710), assigned to
Cam; the publishing job is [#711](https://github.com/xtclang/xvm/pull/711).

- [ ] Marcus: add Cam as Owner on the `xtclang` and `xtclang-org` publishers (#710 step 0).
- [ ] Cam: delete `xtclang-org` and its TXT record (step 1).
- [ ] Cam: check that both are Owners of `xtclang` (step 2).
- [ ] Cam: settle the display name (step 3).
- [ ] Cam: redo the DNS proof for `xtclang` (`_visual-studio-marketplace-xtclang`), and give Marcus
  DNS access (step 4).
- [ ] Cam: find the trusted publishing setting (step 5), or set up the Azure identity (step 5b).
- [ ] Marcus: move #711 to a vsce with microsoft/vscode-vsce#1337 (4.0.1-1 or later) for OIDC, or to
  `azure/login` with `--azure-credential`, whichever step 5 leads to.
- [ ] Marcus: install #711's `vscode-marketplace-<sha>` VSIX in a fresh profile on macOS, Windows
  and Linux, and review the Marketplace metadata in `package.json`.
- [ ] Agree how the first upload happens (step 6), merge #711, set `VSCODE_MARKETPLACE_PUBLISH`, and
  run the first-publish checks in #710.
- [ ] About six months after the first publish: check that `xtclang` shows as verified, and apply
  again if the request was turned down.
