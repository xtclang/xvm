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
- [x] The opt-in IntelliJ `projectLifecycle` check failed most runs, at "closed project's pending
  reply retires" or at "Close must overlap a pending native request"; it also failed on
  `ef1491a6c`, before the R6–R7 work (`run-15132038242161374573`). Fixed in `3813e27a9`: requests
  still waiting when a connection ends are cancelled (LSP4J left them waiting forever, also the
  plugin's own `xtc/*` requests), and the check sends its edits before its requests. It passed five
  runs in a row afterwards, among them `run-13148533765814685610` and `run-9366023975714933137`.

- [ ] Two playbook runs at once corrupt the language server's fat jar (2026-10-24): each rebuilt it
  while the other copied it, and the IntelliJ sandbox's `xtc-lsp-server.jar` and the plugin zip kept
  the broken copy ("bad zipfile offset"; the server fails with `NoClassDefFoundError`) as up to date,
  since the jar's fingerprint matched the good one. Deleting the sandbox copy and running `buildPlugin`
  again mended it. Run one playbook at a time until the copy is guarded.
- [ ] `:lang:intellij-plugin:testCompilerPlaybook` does not always run the cases asked for
  (2026-10-24 to 26): with the configuration cache reused it ran an earlier selection with an earlier
  catalog, and with `--no-configuration-cache` each run took the case list of the run before it, so
  a selection ran once it was sent twice. Check `selectedCases` in the run's `results.json`. The
  property is read as `providers.gradleProperty("intellijPlaybookCases")` in the included `lang`
  build; the cause is not found.

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

Closed 2026-10-22 for VS Code (`6312fd242`, `ccc0695d5`, `44d121779`; shared X285 in VS Code `run-ZuRYb6`),
and for IntelliJ with R7's fourth step (`69c2ff2f8`).

- [x] Placeholder half: no synthetic breakpoints or threads; launch and attach fail with the reason;
  only the configuration handshake is advertised (`XtcDebugServerTest`, `eba208bb3`).
- [x] Connect DAP `launch` to the run session service: sessions live in the language server, over
  its connection (`xtc/debugStart`, `xtc/debugMessage`, `xtc/debugEnd`), and launch through the
  service `xtc/run` uses (`ccc0695d5`).
- [x] No-debug execution through the same service: a `noDebug` launch takes the same path, and an
  embedded run passes `assert:debug` instead of hanging in the console debugger (`6312fd242`).
- [x] Program output and completion events: `output`, then `exited` and `terminated`; a failed build
  reports its diagnostics and fails the launch.
- [x] `disconnect` and `terminate`: both stop the program, also while it still builds.
- [x] Advertise only the requests that work: `configurationDone` and `terminate`.
- [x] The session owns the program it launched: `terminate`, `disconnect`, ending the session and
  the server's shutdown stop it, and its exit ends the session.

### R7: runtime debugger bridge

The first end-to-end case works in VS Code (`41cb8b60c`, `e73878550`, `bb016ce17`; shared X304 in VS Code
`run-iwSxfQ`): a breakpoint stops the program, the call stack shows, and Continue runs it on. The
three commits are separate [integration bursts](errs-integration-plan.md#r6r7-integration-bursts-2026-10-22).

- [x] A runtime debugger per run, for hosts without a terminal: `SessionDebugger`, set through
  `EmbeddingSupport.runMethod()` before the program starts, with the runner's own code left alone
  (`41cb8b60c`).
- [x] Initial suspension: a launch's `stopOnEntry` stops at the first line of the program's own
  modules (`5a7bf8def`, `48f00ef8c`; shared X309 in VS Code; IntelliJ's configuration has no option).
- [x] Real, verified breakpoints, installed before the program's first line (`41cb8b60c`, `e73878550`).
- [x] Exception stops: every thrown exception, or those no frame will catch, at the frame that throws,
  with filters in both editors, Uncaught Exceptions on by default (`bf0b25af4`, `9d2c2f738`; shared
  X307).
- [x] Fibers and threads: a stop holds every fiber that reaches a line; `threads` lists those that
  wait.
- [x] Stacks: immutable snapshots, with file and line per frame.
- [x] Scopes and variables: a frame's locals, an object's fields and an array's elements, read on
  the waiting fiber's thread (`51cccbfbf`, `05e3ff076`; shared X305).
- [x] Stepping over, into and out of calls (`769d6f34d`, `a78d736f5`; shared X306).
- [x] Pause, at the next line a fiber reaches (`5a7bf8def`, `48f00ef8c`; shared X308 in VS Code).
  IntelliJ's Pause Program waits on LSP4IJ (UP30).
- [x] Watches and hover on variables and paths such as `point.x` or `ints[1]`, answered without
  running code (the console debugger's `WO` and `WR`; `2e4a81205`, `c966674fc`; shared X310).
  IntelliJ's debugger hover is checked by hand only (X310's manual row).
- [x] Bounded evaluation's tier 1: watches, hovers, the console, conditions and logpoint parts on
  expressions with Ecstasy's literals and operators, computed by the runtime's own native operations
  without running code; an operation that would run code is refused, naming the method
  (`fda3dc3e8`, `0540055e9`; shared X313, its logpoint VS Code only, UP32).
- [ ] Bounded evaluation's tier 2: calls and properties on the stopped fiber, with the decided
  deadlines, cancellation, side effects announced by `invalidated`, and the opt-in to run the whole
  program (the console's `E`, `EM` and `WE`; lang/doc/plans/plan-bounded-evaluation.md). In five
  steps, R7r to R7v:
  - [x] R7r, the runtime runs code on the stopped fiber (`d77fb9b51`): `SessionDebugger.run()`, the
    fiber waiting again in the same stop, exceptions and compile errors as answers, no stops inside,
    the fibers it calls running, one run at a time.
  - [x] R7s, the runtime's deadline and cancel, and resume and terminate during a run (`72c7bdbbc`):
    `run()` takes the limit, its answer fails at once with the reason, and the code unwinds from every
    line it reaches, also through a `catch`; the stop's clock no longer stays frozen when the program
    is stopped for good during a run.
  - [x] R7t, the server and worker (`68491020b`): a console line that tier 1 refuses runs as code
    for at most two seconds, DAP `cancel` ends it by its request's `seq`, also before it starts, and
    `invalidated` follows once code has run; worker protocol 10.
  - [x] R7u, the editors and shared X314 (`03a422f6c`): VS Code's Debug Console and IntelliJ's Evaluate
    Expression run code; X314 checks a call's effect, the time limit and the exit in both, and cancel
    and `invalidated` in VS Code, as LSP4IJ has neither (UP40, UP41).
  - [ ] R7v, the `evaluationRunsProgram` opt-in.
- [ ] Source maps tied to source revisions: breakpoints match by file and line, not by the build's
  source revision.
- [x] Terminating a stopped program ends it where it is (`41cb8b60c`).
- [ ] Tests: source revisions, with the source maps above.
- [x] Tests: stale handles, reads and evaluations asked for as the program resumes, and multiple
  service contexts (`550a4c01b`, four `SessionDebuggerTest` cases). An evaluation that the resume
  abandoned failed without a reason, a `NoSuchElementException`; it now says the program resumed first.
- [x] A resume/stop race: `SessionDebuggerTest.whileStoppedAFiberOfAnotherServiceWaitsAtItsNextLine`
  hung once (2026-10-23) in `Control.close()`, in the runner's `killTask`. The cause was the runtime's:
  a service with a fiber that ended while a call it made was still out failed `terminate()`'s
  assertion, with assertions on, and so never completed its shutdown, and `Container.kill()` waited
  forever (`ab9fbddeb`, with `EmbeddingSupportTest`'s deterministic case). Tests that close with a time
  limit no longer do it in try-with-resources, whose second close waited without one.
- [x] A closed run left its service fibers running: a run ended when `run()` returned, and the runner
  then dropped its container, so a spinner, or a server that `run()` started, ran on in the worker,
  where Stop could not reach it. Seen once (2026-10-23): `pauseStopsARunningProgramAtTheNextLineItReaches`
  paused in another test's spinner. Now, as with `xtc run` (decided 2026-10-23), a run lasts until its
  services have nothing left to do, and its container is then killed (`1473c7e89`;
  `EmbeddingSupportTest`'s `nothingOfARunStillRunsOnceItIsClosed` and
  `aRunEndsWhenItsServicesHaveNothingLeftToDo`).
- [x] IntelliJ debug sessions (R7 step 4): LSP4IJ's DAP client, chosen 2026-10-22, through a process
  handler that carries the session over the language server connection (`69c2ff2f8`; X304 in
  IntelliJ `run-6846671860304618586`). LSP4IJ's DAP API is experimental, which Plugin Verifier reports
  without failing the build.

### R9–R13: debugger features beyond R7

Planned in [the execution plan](../lang/doc/plans/plan-embedded-execution.md#additive-delivery-and-acceptance),
after the R8 gate unless one is chosen to gate the release. The console debugger's commands they
come from are in parentheses.

- [x] R9: conditional breakpoints (`BC`), hit counts and logpoints (`5b2686a2e`, `edeab8f5b`; shared
  X311). Conditions are `Boolean` expressions since R7's tier 1 (X313). IntelliJ checks the condition;
  its hit counts and logpoints: UP32.
- [x] R9: the call stack and `exceptionInfo`'s stack trace without the embedding runner's frames
  (`TaskRegistry`, `runner.x`), seen in IntelliJ's hint with #1761; the call stack had them too, in both
  editors, under the program's (`ad8268260`, `48dabb5a5`; X304 and X307 check the whole stack).
- [ ] R9: function breakpoints, which stop when a method is entered.
- [x] R9: exception breakpoints by type (`BE+ <exception>`), and `exceptionInfo` for the editors'
  exception views (`5b2686a2e`, `edeab8f5b`; shared X312 in VS Code). IntelliJ: UP34 (filter
  conditions) and UP33 (`exceptionInfo`).
- [ ] R9: data breakpoints, which stop when a property or field changes.
- [ ] R10: value formats (`V`) and identity codes (`DI`). IntelliJ's hexadecimal toggle: UP36; in VS Code,
  our extension contributes the menu item, by design (UP38).
- [ ] R10: a value's `toString()` on demand (`DS`), through R7's evaluation.
- [ ] R10: computed properties as lazy variables; only fields show now. IntelliJ: UP35.
- [ ] R10: paging of large arrays and collections, in place of the first 100 elements.
- [ ] R10: changing a variable's value while stopped (`setVariable`).
- [ ] R10: completions in the Debug Console.
- [ ] R11: Run to Cursor in both editors (`SL`); VS Code's sets a temporary breakpoint, so check
  whether it already works.
- [ ] R11: choosing which call on a line to step into (`stepInTargets`).
- [ ] R11: restarting a frame (`T`).
- [ ] R11: continuing, stepping or pausing one fiber while the others stay stopped.
- [ ] R12: every service and fiber in Threads, with their containers (`VF`), and thread events.
- [ ] R12: frames whose source file is not on the machine, such as library code, through the
  `source` request from the bundled XDK's sources.
- [ ] R12: the loaded modules (`modules`, `loadedSources`). VS Code has no Modules view (UP39), only
  Loaded Scripts for `loadedSources`.
- [ ] R13: Debug for Gradle-mode and command-line runs.
- [ ] R13: debugging tests.
- [ ] R13: attaching to a program that already runs.

Not planned: reverse execution, disassembly, instruction breakpoints, memory access and
`runInTerminal`; the console debugger's own terminal views, command history, repeated steps and
breakpoint list, which the editors replace.

### R8: runtime release gate

- [ ] Repeated compile/run/stop/debug soak, with memory and process checks, on every supported
  platform.
- [ ] A JVM that makes more than one runtime keeps up to two that are done with, about 115 MB each,
  since the runtime holds a container's state in Java statics (heap dump, 2026-10-23). The static
  templates (`xConst.INSTANCE` and the other `INSTANCE = this`, `xEnum.RANGE_TEMPLATE`) hold the
  newest runtime's native container, and a static set once (`xRTSignature.PARAM_ARRAY`) the first
  one's, which later runtimes then use too. A closed program's pending alarm stays in the shared
  `xLocalClock.TIMER` until it is due, an hour for `EmbeddingSupportTest`'s, with its runtime.
  Nothing calls `Runtime.shutdownXVM()`, so a runtime's worker threads stay as well. The servers make
  one runtime per process (`EmbeddingSupport.instance()`), so it costs them little; the `javatools`
  test JVM carries two, 190–240 MB of its 512 MB, and peaks at 275 MB.
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

### LSP4IJ: pull requests open

- [ ] UP30 ([#1757](https://github.com/redhat-developer/lsp4ij/pull/1757)): LSP4IJ's `DAPDebugProcess` did not implement `startPausing()` (0.21.0 and
  `main`), so IntelliJ's Pause Program did nothing for any DAP session. The pull request pauses the
  first thread the adapter reports, and each child session's; natively, X308 passes with it and
  fails on `main` without it. Gate after a release: X308 in IntelliJ with coverage full, and its
  IntelliJ case restored.
- [x] X304 on LSP4IJ `main` is not an LSP4IJ regression. Its console seemed to lose what the program
  prints after Resume, but that was our probe's race: it read the console before the exit code, so a
  program that ended in between was reported without its last output. R7n's probe fix (`c966674fc`)
  reads the exit code first; with it, X304 and X307 pass twice in a row with a plugin built from `main`
  (`ef2b18c3`; `run-11462216125559745352`, `run-9056817084446612838`). Nothing to file.
- [ ] UP31 ([#1758](https://github.com/redhat-developer/lsp4ij/pull/1758)): after an exception stop, LSP4IJ shows the exception in the editor one
  second later, on the editor it found at the stop, without checking that it is still open; an
  editor closed in that second fails with `DisposalException` "Editor is already disposed" (0.21.0
  and `main`). X307 waits for the hint before it resumes and closes its file. The pull request skips
  the hint for a disposed editor; its test fails without the check, and natively an X307 that closes
  its file right after the stop reports the IDE error on `main` and none with the fix. Gate after a
  release: X307 without the wait, and no IDE errors.

- [ ] UP40 ([#1766](https://github.com/redhat-developer/lsp4ij/pull/1766), `feat/dap-invalidated`, 2026-10-23): LSP4IJ neither announces
  `supportsInvalidatedEvent` nor handles the DAP `invalidated` event, so after an evaluation that changes
  the stopped program its views keep the old values. The pull request refreshes the threads, loads the
  stopped stacks again and rebuilds the views. Not checked natively until tier 2 sends the event. Gate
  after a release: a shared case whose console evaluation changes a variable shows it in IntelliJ.

### LSP4IJ: DAP features ready locally, not filed

IntelliJ's side of R9 and R10, each a local commit in `~/src/lsp4ij` on a branch off `main` (`ef2b18c3`),
with tests ([details](errs-upstream-issues.md#lsp4ij-dap-features-prepared-2026-10-22)). UP32, UP33 and UP34
are filed, and pass natively against our adapter since R9 (2026-10-23); the others wait for their R10 adapter
work, a native check, and the user's go-ahead to file them.

- [ ] UP32 ([#1759](https://github.com/redhat-developer/lsp4ij/pull/1759), `feat/dap-logpoints` at `f2a38e58`): breakpoints that don't suspend as DAP logpoints; the
  "Evaluate and log" expression logged at stops; such a breakpoint resumes instead of leaving the
  program stopped while IntelliJ shows it running.
- [ ] UP33 ([#1761](https://github.com/redhat-developer/lsp4ij/pull/1761), `feat/dap-exception-info` at `7f1a5224`): the exception hint from `exceptionInfo`.
- [ ] UP34 ([#1760](https://github.com/redhat-developer/lsp4ij/pull/1760), `feat/dap-exception-filter-conditions` `a4f73d29`): conditions on exception filters, sent
  as `filterOptions`.
- [ ] UP35 `feat/dap-lazy-variables` (`83a0e92d`): lazy variables behind a "Show value" link.
- [ ] UP41, no fix written yet (2026-10-24): LSP4IJ never sends DAP `cancel` (0.21.0, and `main`), so an
  IntelliJ evaluation that runs code cannot be ended before its time limit. Found by X314, which
  checks cancel in VS Code only.
- [ ] UP36 `feat/dap-value-formats` (`b0a76151`): a "Show Values as Hexadecimal" toggle.
- [ ] UP37 ([#1763](https://github.com/redhat-developer/lsp4ij/pull/1763), `fix/dap-physical-expression-fragment`
  `e869c476`): `DAPExpressionCodeFragment` ignored the `isPhysical` that IntelliJ asks for, so IntelliJ
  2026.2's inline logpoint editor fails its inspection pass's assertion on a breakpoint's "Evaluate and
  log" expression, an IDE error with the `-ea` IntelliJ ships. The pull request honors `isPhysical`.
  Gate after a release: a breakpoint with Suspend unchecked and a log expression, with no IDE error.

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
- [ ] Keep #693 in sync with new `lagergren/errs` commits that are not debugger work (last synchronized
  2026-10-23: `eddd77ee9` as `c7fedbe53`, `ab9fbddeb` as `e618f7a22`, without its `SessionDebuggerTest`
  change, `1473c7e89` as `a6c3f519d`, `89e519d99` as `78e0a9deb`, and #717's three fixes as `076907bda`
  (its test only, since #693 stopped at an unterminated string already), `3ce7fc147` and `18a8d910e`,
  and #718's `getRuntime()` review changes `dc12e9390` and `db624ebbc` as `d7701ed06` and `5a68503df`;
  the other commits since are debugger work and its records).
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
- [ ] Candidate for master on its own, apart from the debugger: native `toString()` for `String`,
  `Char` and the integer types, or a native `String` `+` for them. A `String`'s `+`
  (`xString.invokeAdd`) calls `Utils.callToString()`, which runs Ecstasy code for these
  (`String.toString()`, `Stringable.toString()`), so every string join of every program pays an
  Ecstasy call; found by tier 1's refusal of `s + i` (`fda3dc3e8`). It would also let tier 1 join strings.
  In master pull request [#722](https://github.com/xtclang/xvm/pull/722) (2026-10-24,
  `lagergren/native-to-string`, `12d691d3e`): `Int8` to `UInt64`, `Nibble`, `Char` and `String`, and `markNativeMethod()`, which found
  no `toString()` to mark in a const class, since `Const` and its `Stringable` come with no
  contribution. 200,000 iterations of five joins: about 16.3 s on master, 0.14 to 0.3 s with it. Not
  on this branch.
- [ ] Candidate for master on its own, apart from the debugger and the `toString()` one above:
  `ab9fbddeb`'s `ServiceContext` change and its `EmbeddingSupportTest` case, which master has unchanged
  (its `terminate()` and `shutdown()` assert the same, and the test uses only `EmbeddingSupport`).
  `Container.kill()` hangs, with assertions on, once a service has a fiber that ended with a call still
  out. It is on #693 since 2026-10-23 (`e618f7a22`), and in master pull request
  [#716](https://github.com/xtclang/xvm/pull/716) (2026-10-23, `lagergren/fix-embedded-run-end`), with
  `1473c7e89` below, written for master: without the `SessionDebuggerTest` change and the server
  README's line, which master's README has no place for.
- [ ] Candidates for master on their own, found by a scan of this branch's fixes outside `lang/` against
  master (2026-10-23), each reproduced on master:
  - `1473c7e89`: an embedded run ended when `run()` returned, and the services it left at work ran on
    with nothing able to stop them; now it lasts until they have nothing left to do, as `xtc run` does,
    and is then killed. Master has the runner and `EmbeddingSupport` (#703); the `xtc/runExited`
    README line stays here. On #693 since 2026-10-23 (`a6c3f519d`); in master pull request #716 with
    `ab9fbddeb`.
  - One hunk of `7b9b5856a`, a `break` in `Lexer.eatStringChars()`: `xcc` hangs forever on a file that
    ends inside a string, template or `|` literal, since the repeated error is dropped as a duplicate
    and never reaches the error limit (master's `Lexer.java:1397`). The errs test uses errs-only APIs
    and needs writing again. In master pull request [#717](https://github.com/xtclang/xvm/pull/717)
    (2026-10-23), with a new `LexerTest` case.
  - `814153a42`: two diagnostics with the same code and place whose parameters hash alike are taken for
    duplicates, and the second is dropped (master's `ErrorListener.java:318`); one line, and a test
    written again without errs' Site API. In #717 with the lexer fix, with a new `ErrorListTest`.
  - `6312fd242`: an embedded run that reaches `assert:debug` reads the host's standard input;
    only third-party hosts reach it on master. In master pull request
    [#718](https://github.com/xtclang/xvm/pull/718) (2026-10-23, `lagergren/fix-embedded-assert-debug`),
    its `javatools` part with the `EmbeddingSupportTest` case.
  - Found by R7r (`d77fb9b51`, 2026-10-23), in `EvalCompiler`, which master's console debugger uses
    for `E`: a method's parameter is unresolvable until something has shown it, an `@Inject` variable
    is captured with its `Ref`'s type, so no method of it compiles, and a lambda that does not compile
    stays in its class. The `@Inject` one reproduces on master's console: `E random.int(1)` fails
    with "Could not find a matching method or function "int" for type "@Inject Ref<Random>""; `E` of
    a call without a value, such as `console.print("hi")`, fails there either way, since the console
    compiles `E` as a lambda that returns a value. The parameter one does not show there, since the
    console shows a frame's variables before `E` runs, and nothing showed what the lambdas left
    behind break. Waiting for #718, see the item below: pull request
    [#721](https://github.com/xtclang/xvm/pull/721) was closed on 2026-10-24, since its test needs
    #718's runtime debugger, which master does not have yet.
  - `7e475dc98`, found while validating R7s (2026-10-23): a weak map's key and entry sets leave
    collected keys out of their iterators but not out of `toArray()`, which returns null for them, so
    `Container.terminateServices()` fails with "Cannot invoke ServiceContext.requestShutdown() because
    services[i] is null" once one of its services is collected; an embedded run's close then fails, or
    its end is never reported. Master's `javatools_utils` files are the same, and the commit's new
    `WeakHasherMapTest` case fails on master `7a79c6ef0` as it did here. In master pull request
    [#720](https://github.com/xtclang/xvm/pull/720) (2026-10-24, `lagergren/fix-weak-map-arrays`),
    and on #693 since 2026-10-24 (`a5e25c725`).
  - Found by the native `toString()` test (2026-10-23): `new Nibble(Bit[])` makes 0 for every value,
    and so does `Nibble`'s table, and with it `Nibble.of()`, `IntLiteral.toNibble()` and
    `Char.isNibble()`; `xConstrainedInteger.constructFromBits()` packs the bits into a byte's high
    bits, and `Nibble`'s mask drops them. Its `bits` failed and `Nibble(String)` overflowed for the
    same reason, sizes counted in whole bytes. All three in master pull request
    [#723](https://github.com/xtclang/xvm/pull/723) (2026-10-24, `lagergren/fix-nibble-from-bits`),
    tested with asserts in `manualTests`' `numbers.x`, which CI runs. `Nibble(Byte[])` still fails: the library's own constructors disagree on
    its size.
  - Found through #723's callers (2026-10-24): `lib_json`'s `Lexer` dropped every `\u` escape, so it
    could not read back the control characters its `Printer` writes. In master pull request
    [#724](https://github.com/xtclang/xvm/pull/724) (`lagergren/fix-json-unicode-escape`), with
    surrogate pairs read as one `Char`, and independent of #723.
  - Found with #724, not fixed: `Char.appendEscaped()` writes the first three hexits of a `\u`
    escape as 0 (`codepoint & 0xF000 >>> 24` is `codepoint & 0`); on master
    `0x2028.toChar().quoted()` is `'\u0008'`.
  The rest is on master already (#672, #703 and others), withdrawn, or part of the stacked pull requests.
- [ ] Once #718 is on master, file the `EvalCompiler` fixes above again, as a new pull request or
  by reopening [#721](https://github.com/xtclang/xvm/pull/721). The branch
  `lagergren/fix-debugger-eval-captures` (`f3069b4fb`, 2026-10-24) has the fix and its test, and is
  based on #718's branch: rebase it onto master, which leaves only its own commit. The test, an
  `EmbeddingSupportTest` case, sets a debugger of its own on the run's connector with #718's
  `InterpreterConnector.setDebugger()`, and compiles `n + 1`, `random.int(1)` and `nope` with
  `EvalCompiler` where the run stopped at `assert:debug`; with master's `EvalCompiler` it fails all
  three ways. The console cannot be driven from `manualTests` instead: the run task only inherits
  Gradle's standard input, which is empty in CI.
- [x] A compiler crash on both master and this branch: a file that ends in `$"abc{x`, `"abc\` or `'\`
  failed with `NoSuchElementException` from `Source.next`, through `Lexer.eatTemplateExpression`,
  `eatStringChars` and `eatCharLiteral`. Fixed here (`92d87bb08`), in master pull request #717, and on #693.
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
