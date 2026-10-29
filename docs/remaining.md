# Remaining work

Everything still open on `lagergren/errs`, as one list to tick off. Checked on 2026-10-26
against [the integration plan](errs-integration-plan.md),
[the execution plan](../lang/doc/plans/plan-embedded-execution.md),
[the upstream register](errs-upstream-issues.md), [the PR roadmap](../pr-roadmap.md), and the live
state of every upstream issue and pull request named here. When you tick an item, add the commit,
PR or run that closes it.

Closed and not repeated here: L1–L72 (L61–L67 within their bounded scope), L74–L76, L80, L81,
L83, UI1–UI7, AST1–AST5, PLAT1–PLAT3, R1–R5. R6 and R7 are closed too; their sections below
stay as records. Cases those closures refuse on purpose are documented refusals, not open tasks;
the few worth revisiting are under [beyond the current scope](#beyond-the-current-scope).

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

- [x] Closed (decided 2026-10-27), with nothing to build:
  - Run reaches the R2–R5 build/run service without a command line or a second compile: the module
    lens names the editors' own `xtc.runModule`, which in embedded mode sends `xtc/run` to the
    service (X280–X303 in both editors).
  - The one server command that edits, the legacy code action's apply, is advertised only to a
    client with `workspace/applyEdit` and without code-action literals. A refusal or a 10-second
    wait fails the request with the client's reason (`CapabilityNegotiationTest`).
  - Not built: a server-run Run command for LSP clients without their own `xtc.runModule`, such as
    Neovim, Helix or Zed, whose lens has no command they can run. It waits until one is needed.
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

- [x] `textDocument/inlineValue` (2026-10-27; shared X331): the parameters and locals of the method or
  lambda where the program is stopped, at each occurrence up to the stopped line, as lookups by name
  that the editor makes in the stopped frame; not fields, nor variables declared further on.
- [x] `workspace/inlineValue/refresh`, after a compile, to a client that takes it.
- [x] A shared acceptance case: X331 passes in VS Code, which shows no values for Ecstasy without it
  (`debug.inlineValues` on `auto`). IntelliJ's is not implemented: released LSP4IJ draws no inline
  values for Ecstasy (UP50); with our LSP4IJ #1781, merged 2026-10-28, it passes.

### L82: completion evidence and release gates

- [ ] Agree response-time and memory budgets per supported hardware and platform. The current
  receipts define provisional local budgets only.
- [ ] Prolonged-session acceptance (editing, restart, process leaks) on each supported platform.
- [ ] Cross-platform and remote acceptance: Windows, Linux, and restricted or remote workspaces.
- [x] An uninterrupted full-catalog VS Code run: `run-Pc161s` (2026-10-28, `5e6a0f2391`) ran all
  333 cases in one window, 332 passed, and X218 failed on UP23, the exception already accepted. On
  `vscode-languageclient` 11.0.0 (2026-10-29) the full catalog passed 332 again, X218 failing, in
  three windows: keys typed by hand reached the test window in X167 and X222, which then timed out
  and passed when run again (`run-l2txtQ`, `run-gvT2Bt`, `run-CirjOs`).
- [ ] For each host limitation, either a released fix with its acceptance passing, or a recorded
  release exception: UP16, UP23 (already accepted as an exception), UP25, UP26, UP17.
- [ ] IntelliJ bulk replacement in a heavily decorated large file (UP17): after a platform fix,
  pass the decorated 20,000-method X145 replacement without the EDT freeze.
- [ ] At closure, update together: this list, the integration plan's checklist, the capability
  matrix, the playbook, the AST/API ownership record and the extraction map.

### Test harness stability

- [ ] X259 fails with LSP4IJ 0.21.0 at its last step: once the library is back to `Int`, the
  consumer's hints don't follow (`run-1660337404068710181`, twice); it passes with the nightly
  (`run-481780523334095669`). It then passed in both full 0.21.0 runs of 2026-10-28
  (`run-7875402505096583343`, `run-17527198196716738382`), so it is intermittent; find the race.
- [ ] X74 and X150 each stopped a full IntelliJ run, with the nightly, and pass alone with both
  LSP4IJs: X150's popup inspection saw the source change under it ("Before: cl; now: clhis lo").
  Both passed in the three full IntelliJ runs of 2026-10-28.
- [ ] X257 and X324 failed only in the full nightly run of 2026-10-28 (`run-793652569316991787`).
  X257 (the UP26 gate) found no Invoked inline completion request for its document in the LSP
  console, though the alternative showed; it passes alone (`run-266543211336496817`). X324's library
  frame had no source position, though the read-only copy of `Collection.x` it named was written
  36 s before; it passes alone, after X323, and after X304-X322 (`run-6424025512923475166`).
- [ ] X312 failed once, in the full run on the 0.30.0 branch (`run-11663425380226624296`,
  2026-10-28): the program stopped at the exception its filter names, but no exception hint showed
  in 30 s, and no hint label was showing at all. It passed in the 70-case run before
  (`run-6838369535683503954`) and in the rerun after (`run-10037495088984883693`). LSP4IJ shows the
  hint in the first editor of the stop's file 1 s after `exceptionInfo` answers, or after 1 s
  without an answer, and hides it for another hint or Escape; which step failed is unknown.
- [x] The server never deleted its `xtc-library-sources-*` temporary directories
  (`XdkLibrarySources`); 2,068 had piled up by 2026-10-28. A JVM shutdown hook now deletes the
  directory when the server exits (`4cf5450acd`; `LibraryCopiesCleanupTest` in compilerStdioTest).
  A killed server still leaves its directory, as the playbook's does when IDE Starter closes the IDE.
- [x] X283 failed once in a full run (`run-17527198196716738382`) when keystrokes typed in another
  window reached its editor: the test IDE takes the focus to click a Run lens. It passes alone
  (`run-891840089927042241`). Don't type elsewhere during an IntelliJ run.
- [x] The server re-announced every library document a client fetched after each dependency change
  (`ReadOnlyDocuments.refresh()`), though a URI's text can't change, and LSP4IJ fetched them all
  again; in X146 that filled the LSP console. Decided 2026-10-28: no refresh is needed, since a
  library URI carries its revisions; removed (`622d0a506f`).

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

- [x] Two playbook runs at once corrupted the language server's fat jar (2026-10-24): each rebuilt it
  while the other copied it, and the IntelliJ sandbox's `xtc-lsp-server.jar` and the plugin zip kept
  the broken copy ("bad zipfile offset"; the server fails with `NoClassDefFoundError`) as up to date,
  since the jar's fingerprint matched the good one. Fixed in `05b538e55`: the fat jar, every copy of
  it (VS Code's, the IntelliJ sandbox's, `runIde`'s) and the plugin zip's are checked, and one that
  fails is deleted and fails the build, which the next build makes again. Two builds at once can
  still fail, with that message; they do not wait for each other.
- [x] `:lang:intellij-plugin:testCompilerPlaybook` seemed not to run the cases asked for
  (2026-10-24 to 26), a selection running only once it was sent twice. It always ran them. The
  script that read each run's results took the run directory changed last, and
  `pruneCompilerPlaybookReports`, which deletes older runs' payloads after each run, changes those
  directories, so the script read an older run. The run's `results.json` dates it; the pruner ranks
  runs by their `.completed` files, which it leaves alone. `8777c067c` recorded the fault as the
  build's, and `70cdd636f`'s message says its run before selected an earlier list; both were this
  misreading (found 2026-10-26).

### Beyond the current scope

Only if a real need comes up; each is a documented refusal today.

- [x] Rename: fixtures for every unsupported composition route (Implicit, Union, SansCode, Field,
  Native) before enabling source-owned routes; binary and native contracts stay read-only.
  Done 2026-10-21: adopted interface properties (SansCode) now rename; union-typed property reads
  stay refused (they need per-receiver alternatives); Implicit, Field and Native stay refused.
- [ ] Rename: VS Code edited-file resource history with refactoring auto-save off, instead of the
  current pre-edit refusal. Investigated 2026-10-21, no clean implementation: `isRefactoring` saves
  only when the setting is on, and VS Code moves a dirty file by recreating its model, which drops
  the old model's text Undo (UP62, the existing [microsoft/vscode#17977](https://github.com/microsoft/vscode/issues/17977), commented 2026-10-28). Writing the moved
  file as create plus delete would itself save it. The refusal stays. Corrected 2026-10-28: one
  workspace edit's move and text edits do share one undo group (`BulkEditService`).
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

Closed 2026-10-26 in both editors, with source revisions (`030f4255e0`). In IntelliJ, the cases
that are partial or not implemented wait on LSP4IJ 0.21.0's limits, UP30–UP42.

The first end-to-end case works in VS Code (`41cb8b60c`, `e73878550`, `bb016ce17`; shared X304 in VS Code
`run-iwSxfQ`): a breakpoint stops the program, the call stack shows, and Continue runs it on. The
three commits are separate [integration bursts](errs-integration-plan.md#r6r7-integration-bursts-2026-10-22).

- [x] A runtime debugger per run, for hosts without a terminal: `SessionDebugger`, set through
  `EmbeddingSupport.runMethod()` before the program starts, with the runner's own code left alone
  (`41cb8b60c`).
- [x] Initial suspension: a launch's `stopOnEntry` stops at the first line of the program's own
  modules (`5a7bf8def`, `48f00ef8c`; shared X309), and IntelliJ's configuration asks for it with its
  checkbox "Stop at the program's first line" (`df6c1a873`).
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
- [x] Bounded evaluation's tier 2: calls and properties on the stopped fiber, with the decided
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
  - [x] R7v, the `evaluationRunsProgram` opt-in (`70cdd636f`): a launch option, a checkbox in IntelliJ's
    configuration, that lets the program's other fibers run, without stopping, while console code does,
    and wait again at their next line; `invalidated` is then for all areas. Shared X315.
- [x] Source maps tied to source revisions (`f53883083`): a debugged run keeps the text each file was
  built from, and once a file is edited, saved or not, a breakpoint is placed on the line it was
  built with, matched line by line with whitespace at a line's ends ignored, and a frame shows on its
  line now. A breakpoint on a line changed since is unverified with the reason. Shared X321, full in
  IntelliJ.
- [x] Terminating a stopped program ends it where it is (`41cb8b60c`).
- [x] Tests: source revisions, with the source maps above (`f53883083`: `CompiledSourcesTest`, and an
  `EmbeddedExecutionTest` case that edits a debugged file and fails without the mapping).
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

Editor batch (2026-10-27), for X280–X330 after `819e2ac754`: VS Code passes all of them
(`run-OFR39r`, X326 in `run-NGgnBV` once `modules` took no arguments), and IntelliJ passes its 44
implemented cases among them with released LSP4IJ 0.21.0 (`run-15774751429931474506`), 13 at their
recorded partial coverage.

- [x] R9: conditional breakpoints (`BC`), hit counts and logpoints (`5b2686a2e`, `edeab8f5b`; shared
  X311). Conditions are `Boolean` expressions since R7's tier 1 (X313). IntelliJ checks the condition;
  its hit counts and logpoints: UP32.
- [x] R9: the call stack and `exceptionInfo`'s stack trace without the embedding runner's frames
  (`TaskRegistry`, `runner.x`), seen in IntelliJ's hint with #1761; the call stack had them too, in both
  editors, under the program's (`ad8268260`, `48dabb5a5`; X304 and X307 check the whole stack).
- [x] R9: function breakpoints, which stop when a method is entered (2026-10-27; shared X328). One
  names a method, alone or after its class's or module's, as the call stack names it, such as
  `twice` or `Point.move`, and stops at the first line of each of the program's methods of that
  name, not the libraries', with the reason `function breakpoint` and a condition and hit count as
  a line's has. It is installed at the first such method's line, or says that no method has the
  name, also when set before the program starts (worker protocol 20). VS Code adds them in its
  Breakpoints view; IntelliJ has none (UP46), so X328 is not implemented there.
- [x] R9: exception breakpoints by type (`BE+ <exception>`), and `exceptionInfo` for the editors'
  exception views (`5b2686a2e`, `edeab8f5b`; shared X312 in VS Code). IntelliJ: UP34 (filter
  conditions) and UP33 (`exceptionInfo`).
- [x] R9: data breakpoints, which stop when a property or field changes (2026-10-27; shared X330).
  `dataBreakpointInfo` names a field of a mutable object in the Variables view; a local, an
  element, a computed property or an immutable object's field cannot be watched. Once the field's
  value has changed, by what its variable shows, the program stops with the reason `data
  breakpoint` at the next line that a fiber of the object's service reaches, and says from what to
  what, such as `Counter.count changed from 0 to 2`. The check runs at each line of that service,
  without a hook in the runtime's writes, and only writes are offered (worker protocol 22). VS Code
  adds them with Break on Value Change; IntelliJ has none (UP48), so X330 is not implemented there.
- [x] R10: value formats (`V`) and identity codes (`DI`) (`cb551d4c2`, R10b): integers in hexadecimal when
  a request's DAP `format` asks, or the session's own `xtc/valueFormat`, which VS Code's Variables and
  Watch views' Show Values as Hexadecimal sets, since VS Code never sends a format (UP38); a mutable
  object's value ends with its identity code (worker protocol 13). Shared X317; IntelliJ's hexadecimal
  toggle is UP36 (#1769). An unsigned 64-bit value of 2^63 or more no longer shows as negative.
- [x] R10: a value's `toString()` on demand (`DS`), through R7's evaluation, and computed properties
  as lazy variables (`1d88e086d`, R10a): an object's fields are followed by its public computed
  properties and its `toString()`, `(...)`, whose reads run the getter or `toString()` on the stopped
  fiber for at most a second (worker protocol 12, `presentationHint.lazy`). Shared X316; IntelliJ
  clicks LSP4IJ's Show value link (UP35, #1768), or with 0.21.0 expands the variable to its value.
- [x] R10: paging of large arrays, in place of the first 100 elements (`00dee9671`, R10c): an array's
  variable has `indexedVariables`, and a `variables` request's `filter`, `start` and `count` reach the
  program, which reads only that part (worker protocol 14); VS Code shows ranges of 100. Shared X318;
  IntelliJ with LSP4IJ 0.21.0 shows the first 100 and how many more, and pages through IntelliJ's own
  `...` node once LSP4IJ releases #1770 (UP42). Other
  collections, such as a `Map`, show their fields: their entries would take running their code.
- [x] R10: changing a variable's value while stopped (`setVariable`) (`3a060bd39`, R10d): a local, a
  field or an array element takes the value of an expression that a watch evaluates, a literal of its
  type, so no code runs; a value of another type, `this`, a field of an immutable object, a computed
  property and what a `Ref` holds are refused with the reason (worker protocol 15). Shared X319, full
  in IntelliJ through LSP4IJ's own Set Value.
- [x] R10: completions in the Debug Console (`8178cd03d`, R10e): the name typed at the cursor
  completes to the frame's variables, `this` and its members, its private ones too, or, after a
  dot, to the public fields, properties and methods of the value before it, evaluated as a watch is,
  so no code runs (worker protocol 16). Items have no `start` (UP43). Shared X320, partial in
  IntelliJ: with 0.21.0, editing Evaluate Expression's expression fails IntelliJ's Document/PSI
  check (UP37), so it checks the names offered; with #1763 it also checks the text a chosen one
  makes.
- [x] R11: Run to Cursor in both editors (`SL`) (2026-10-27; shared X323). Both editors add a
  temporary breakpoint and continue, which the server already handled: `EmbeddedExecutionTest` sends
  that sequence, on a line in a loop, and the program stops there once and then runs to its exit.
  VS Code passes X323, whose program counts to 3,000,000 before the cursor's line. IntelliJ's is
  partial: LSP4IJ removes the temporary breakpoint once the continue request is answered, so the
  program runs past the cursor (UP44).
- [x] R11: choosing which call on a line to step into (`stepInTargets`) (2026-10-27; shared X327).
  `SessionDebugger.stepInTargets` lists the calls that the line where a fiber waits makes, from its
  ops, in the order of its code, each named by what it calls (`twice(Int)`, `new Point(Int, Int)`);
  `stepInto` steps into one: the step ends at the first line that code reaches while its frame makes
  that call, or at the next line if the line does not make it, and falls back to a plain step into
  if the frame returns first. `OpInvocable.getInvokedMethod` and `OpCallable.getCalledMethod` name a
  call's target; a call of a function that a variable holds is not offered. The worker answers
  `stepInTargets` and `stepInto` (protocol 19), and the adapter `stepInTargets` and `stepIn` with a
  `targetId`. VS Code offers it as Step Into Target, IntelliJ as Smart Step Into, which LSP4IJ 0.21.0
  sends as `stepInTargets`.
- [x] R11: restarting a frame (`T`) (2026-10-27; shared X329). The fiber leaves the frame and those
  above it, without running more of their code or their `finally` blocks, and the frame's caller
  makes the call again, with the values its arguments hold now, as the console's `T` does through
  the runtime's `R_RESET`; the program stops at the call's first line with the reason `restart`.
  A frame says whether it can (`canRestart`): not when its caller is native, the host's or a run's,
  nor when it or a frame above it is native or has a continuation, nor at an exception's stop
  (worker protocol 21). VS Code offers it in the Call Stack view. IntelliJ offers Reset Frame through
  our LSP4IJ #1778 (UP47, merged 2026-10-27, unreleased): X329 passes there with a plugin built from
  it, and stays not implemented in the catalog until a release has it.
- [ ] R11: continuing, stepping or pausing one fiber while the others stay stopped. Not built
  (decided 2026-10-27): it needs DAP's `singleThread`, which neither editor sends (UP49: VS Code
  [#166450](https://github.com/microsoft/vscode/issues/166450), open since 2022; LSP4IJ none),
  and a stop that lets one service run while the rest stay held, which no editor could reach.
- [x] R12: every service and fiber in Threads, with their containers (`VF`) (2026-10-27; shared
  X325). At a stop, `threads` lists the fibers that wait at a line first, as before, then the other
  fibers of the program's services, in its own container and those nested in it, in the order the
  services were made: each named by its service, after its container's module if that is not the
  stopped fiber's, with what it does (waiting, paused, not started, running, as in a native call, or
  ending). A fiber that waits otherwise than at a line, is paused or ends has its stack, without
  variables; one that runs has none, since its frames change as they would be read. The runtime
  gains `ServiceContext.getContainer()`, `getName()` and `getId()`, `Fiber.getContext()` and
  `Container.getParent()` for it. Found: listing every container of the runtime took in the other
  programs it runs, as the tests' shared interpreter does; only the stopped program's count now.
  Not done: thread events. A fiber starts for every call to a service, so an event for each would
  cost every call of a debugged program, and both editors ask for the threads at each stop. Known
  limit: the caller of a synchronous call to an idle service shows as running, without a stack, for
  as long as the call takes: the runtime marks a fiber waiting only once it blocks.
- [x] R12: frames whose source file is not on the machine, such as library code (2026-10-27; shared
  X324). A frame of a bundled XDK library's code is in the library's bundled source, at its line:
  the read-only copy that navigation opens too, which is the source the library's code was compiled
  from. The library records the source directory it was built in, which a user's machine does not
  have. Each frame carries its module and its source path in the module (worker protocol 17). Found
  and fixed: a frame of any other module's code had the program's source directory with the class's
  source path, a file that does not exist, since the debugger took the source directory from the
  program's module rather than the class's own. Not through the `source` request: a file, the one
  navigation opens, also gives the editors their language features there. VS Code and IntelliJ
  pass X324.
- [x] R12: the loaded modules (`modules`, `loadedSources`) (2026-10-27; shared X326). The debugger
  lists the program's modules as the runtime linked them: its own, which `addModule` made known,
  and the libraries it uses, each with its version, its recorded source directory and its classes'
  source paths, read once per module (worker protocol 18). `modules` answers them a page at a time,
  `isUserCode` for the program's own; `loadedSources` answers their files, a bundled library's in
  its bundled source, as its frames are, which `XdkLibrarySources` now writes without parsing its
  declarations, since a library has hundreds. VS Code shows them in Loaded Scripts; it has no
  Modules view (UP39). IntelliJ has neither (UP45), so X326 is not implemented there.
- [ ] R13: Debug for Gradle-mode and command-line runs.
- [ ] R13: debugging tests, which needs the editors to run tests first.
- [ ] R13: attaching to a program that already runs.
  Design for all three (2026-10-27) in
  [the execution plan's R13](../lang/doc/plans/plan-embedded-execution.md#additive-delivery-and-acceptance):
  a debug agent in the program's JVM, which `Runner` and `TestRunner` install through a new
  `DebuggerProvider` SPI for `--debug`, speaks the worker protocol's debug commands over a socket
  to a new `RemoteRuns` in the language server; Gradle-mode Debug connects back, `xec` listens for
  an attach.

Not planned: reverse execution, disassembly, instruction breakpoints, memory access and
`runInTerminal`; the console debugger's own terminal views, command history, repeated steps and
breakpoint list, which the editors replace.

### R8: runtime release gate

- [x] Repeated compile/run/stop/debug soak, with memory and process checks, on macOS and Linux
  (2026-10-27).
  *Soak (2026-10-26, `0d6e890bad`):* `EmbeddedExecutionTest`'s soak repeats rounds of four runs
  on one worker: an edited module built and run to its exit, a run stopped while it runs, a debug
  session continued from a breakpoint after a watch and a cancelled console run, and one
  terminated while console code runs. One worker serves them all and keeps no run, console,
  debugger, console code, session or compiled sources; the runtime's pool keeps its size, and
  closing ends the worker process. The suite runs five rounds, `-Plsp.soakRounds` more; 50 passed
  three times on macOS. It found that the worker counted a JDK thread started from a pool thread,
  the common pool's delay scheduler, as runtime pool growth, which retired a healthy worker; the
  worker now reports the pool's own size. Memory is checked by counts only. The worker no longer
  calls `System.gc()` (`2668896b26`): it reports the heap the JVM left after its own latest
  collections, and the server keeps its 512 MB limit on that but dropped the 128 MB growth rule,
  since two such figures say nothing about what runs retain. The tests that waited for the
  collector walk the object graph instead (`77e3c4b7c5`).
  *Editors (2026-10-26, `718ddd656e`):* shared X322 repeats runs, stopped runs and debug sessions of
  one module from each editor; each ends, and afterwards no run or debug session is open. It
  passes in VS Code and IntelliJ.
  *Linux (2026-10-27, `6b8f5bbe03`):* arm64 Linux with JDK 25, in a Podman container on the Mac, as
  a user other than root, with one test fork. `javatools`' 686 tests pass (42 skipped). With
  `--init` on a quiet host, all 2401 `lsp-server` tests pass but the 2 that are always skipped,
  with the soak at 50 rounds (56 s), in 34 minutes. The runs found:
  - As root, the tests of files without write permission failed, since root writes them anyway;
    they skip where such a file stays writable (`e4a8e6847e`).
  - When Gradle was the container's PID 1, which does not reap orphans, the worker of a killed
    server exited but stayed a zombie, so "a worker exits when the language server process dies"
    timed out waiting for it to end. With an init process that reaps (`podman run --init`) it
    passes. The test now kills a worker that outlives its server and shows what the
    worker was doing (`6b8f5bbe03`), and a worker whose input ends stops its runs on threads of
    their own and exits within 5 s even if a program does not stop (`c474566892`).
  - With the Mac's load average at 37–75 on 10 cores, `EmbeddedExecutionTest` took 667 s, against
    50 s on a quiet host, and time limits failed: `XdkOnTypeFormattingTest`'s 20 ms (32 ms), and the runtime's 1 s limit on
    a lazy `toString()` (`SessionDebugger.ON_DEMAND`) in the console test. That test then left its
    program stopped on the class's shared worker, so three later tests failed with it. Alone, and
    with its class on a quiet host, the console test passes.
- Not planned (decided 2026-10-27): the soak on Windows. CI runs the `lang` checks on
  `ubuntu-latest` only: a dispatch can add `windows-latest`, which builds without them
  (`commit.yml`). There is no Windows machine to run them on.
- [x] A JVM that makes more than one runtime kept up to two that it was done with, about 115 MB each
  (`474af84f08`, 2026-10-26). Three things held one:
  - A pending alarm on the JVM-wide Java timer. The `Timer` service belongs to the native
    container, so `xNanosTimer.TimerHandle.Alarm` kept the runtime until the alarm was due.
  - 27 values that 13 templates cached in statics on first use, such as
    `xRTSignature.PARAM_ARRAY`, from the first runtime, which every later runtime used too.
  - `FullyBoundHandle.NO_OP`, made from the first runtime when its class was loaded.

  `EmbeddingSupport.shutdownInterpreter()` (for tests) shuts a connector down: its timers' alarms
  are cancelled and its executors end. The cached values are fields of each runtime's templates,
  and `NO_OP` is made by each runtime; the embedding and debugger tests shut their interpreter down.
  `RuntimeRetentionTest` checks both without waiting for the collector. The `javatools` test JVM now
  holds one runtime at a time: its peak live heap is down from 275 to 226 MB, and from 242 to 193 MB
  at the end. Kept: the about 130 statics a template sets when it is made, such as its `INSTANCE`,
  still hold the newest runtime until another is made; the servers make one runtime per process.
- [x] Parity between IntelliJ and VS Code (2026-10-27, at `718ddd656e`). VS Code passes all 34 run
  and debug cases, X280–X285, X287–X289, X291–X294, X301 and X303–X322. IntelliJ runs 32 of them,
  all but X308 and X312, which it does not implement: 21 pass and 11 are partial, each with its
  recorded limit. X287, X288 and X303 are partial because of the test driver; X311, X313–X318 and
  X320 because of LSP4IJ 0.21.0 (UP32–UP42). X308 and X312 wait on LSP4IJ fixes (UP30, UP33,
  UP34) that are merged on its main branch but not released.
- [x] Logs that correlate build ID, queue job, artifact revision, worker PID and generation, and run
  ID (`a87b63d9d`, 2026-10-26). A build's ID is its compiler queue job's, which the trace gives as
  `id`; the build line gives each module with the start of its artifact's revision. The run's
  start names its build and artifact, and the worker's lines name the run, the worker's PID and
  its generation, which counts the workers the server started; `lsp-server/README.md` says how to
  follow a run. `EmbeddedExecutionTest` checks one run's lines against each other and the trace.
  Kept: the editors' own logs do not give these IDs.
- [x] Focused Gradle test tasks (`e91b5e58cb`, `30d9685ff3`, 2026-10-26): `graphQueryTest`,
  `emissionTest`, `dependencyHostTest`, `sourceGraphHostTest`, `embeddingConsumerTest` and
  `:javatools:errorListenerTest` each run what a doc's command used to list with `--tests`.
- [x] Playbook and capability docs brought up to date (`3ac199f192`, 2026-10-26): R6 and R7 are
  complete in both editors, the debug server's capabilities are listed, the catalog is given as
  324 cases, X1–X322, and X308's and X312's IntelliJ limits name the LSP4IJ fixes.
- [x] Each runtime PR validated on its own (2026-10-27). #716, #717, #718, #724, #731 and #693, each
  rebased onto master `ddaa3d0a4`, pass CI on their own heads. #730's fix is now part of #724,
  and #730 is closed.
- Known limit kept: JIT runs are unsupported.

## 4. Upstream fixes

### LSP4IJ: merged on `main`, waiting for a release

XVM pins LSP4IJ 0.21.0 (`gradle/libs.versions.toml`, `lang-intellij-lsp4ij`), still the latest
release (2026-08-31). Each workaround stays until a release contains the fix and its removal gate
passes with the workaround disabled. The gates are in
[the register](errs-upstream-issues.md#register).

- [ ] Watch for the next LSP4IJ release and bump the pin.
- [x] Checked against LSP4IJ's nightly 0.21.1-20261027-160212 (`main` `0d3c64de`, 2026-10-27), with
  `-Plsp4ijChannel=nightly` (`4ca6f5993d`): IntelliJ's X308 (UP30), X312 (UP33, UP34), X326 (UP45),
  X328 (UP46) and X329 (UP47) pass, made full for the runs; X308, X312 and X326 are new
  (`09ffee515b`). The UP25 and UP26 replacement gates pass. What the nightly changes, and the playbook
  now takes: library sources open as the server's read-only documents without files (#1749;
  `8d845d167e`, `3b7c450765`); the console loses X146's versions to their traffic, now read from
  LSP4IJ's synchronizer (`ddcc6fda9d`); a restart reopens documents with new versions (`b499c339fb`).
  A full run's IDE was killed by macOS in IDE Starter's thread dump of every minute; the playbook
  takes none on macOS (`ff06c86d0b`). A full run stopped in turn at X74, X150 and X285 (the IDE
  crash); X74 and X150 pass alone. Before the bump: pass a full run.
- [x] Full IntelliJ run on the nightly 0.21.1-20261028-080211 (built from `7469aab9`, with UP48 and
  UP50), 2026-10-28, at `c2a9b296d5` with the playbook fixes before it (`run-793652569316991787`):
  317 passed, 13 partial, 1 not implemented, 3 failed. X308, X312, X326 and X328-X331 pass, made
  full for the run, and so do X259, X74 and X150. The UP25 gate passes. Failed: X323, as its UP44
  gate does once #1773 is in; X257 (the UP26 gate) and X324, which fail only in the full run (see
  [test harness stability](#test-harness-stability)). No IDE exception.
- [ ] The bump is prepared on the local branch `lagergren/lsp4ij-0.30.0` (35 commits on errs
  `4e3b1b65e7`, tip `a67965ede2`, 2026-10-28). It pins 0.30.0 and removes, each in its own commit,
  the bridges 0.30.0 makes unnecessary; the items below name them. It is checked against a plugin
  built from LSP4IJ `main` `44675cb3` with #1790 and #1791 before their CodeRabbit fixes. Full
  IntelliJ run at `56c699eb5a` (`run-11663425380226624296`): 307 passed, 4 partial (X286-X288,
  X303), 1 not implemented (X302), 3 failed, and X312's timeout stopped it before X313-X331. X5 and
  X131 failed on two playbook races, fixed in `92eefb10c9` and `3e6a466b7e`; with them X5, X131 and
  X312-X331 pass (`run-10037495088984883693`; X314 partial for UP41). X312 is under [test harness
  stability](#test-harness-stability). The plugin's 113 unit tests pass. Native cases made full:
  X308, X311-X313, X315-X318, X320, X323, X326 and X328-X331 (UP30-UP37, UP40, UP42, UP44-UP48,
  UP50, UP55); X307 checks the exception hint's text; X131 opens a link with Ctrl+Click (UP52,
  `e317d03c11`, which also retires the replacement gates); X132 formats two ranges, X131 opens a
  symbol with Go to Symbol and X42 shows inlay tooltips (UP54, UP59, UP60, `a901032102`). The server
  now omits a signature's `parameters` without their metadata, which X20 needs with LSP4IJ's handler
  (`df11487151`); VS Code's X20 expects that, and X15-X20 pass in VS Code (`a67965ede2`). Kept on
  purpose: UP03, UP04's rename guard, UP12, UP13 and both halves of UP14. `verifyPlugin` resolves
  LSP4IJ from the Marketplace, so it can check the branch only after the release; against 0.21.0 it
  fails on #1786's `LSPFormattingParams` constructor. The internal API list is reviewed against the
  candidate's source (`c81995303f`). To do after the release: build against it, pass `verifyPlugin`
  and a full native run, and merge the branch.
- [ ] UP01 (#1696): remove the `ConnectionLifetime` guard in `XtcLspServerSupportProvider`; gate:
  `ConnectionLifetimeTest`, the packaged process regressions, START/STARTUP and
  START/START_PROJECTS. On the 0.30.0 branch: `9ca54e5fe4`; `ConnectionProcessTest` drives the
  provider.
- [ ] UP02 (#1746): enable `willSaveWaitUntil` and server save formatting; gate: real pre-save
  requests apply version-checked edits before persistence, and X139 extended. On the 0.30.0 branch:
  `97cdb4e25d`, its settings texts `d5d64a8caf`.
- [ ] UP04 (#1698, #1718): remove the rename snapshot guard and client edit validation; gate: stale,
  closed and reopened document tests and X144. On the 0.30.0 branch: `2be956ad8c` removes the
  client's edit validation (`applyEdit`); the rename guard stays (`XtcRenameEdit`,
  `PreflightedRenames`).
- [ ] UP05 (#1742): remove `CompilerRootWatches`; gate: changes under external roots observed while
  focused, watchers released on disposal, X124. On the 0.30.0 branch: `26e2f7440e`.
- [ ] UP07 (#1730, #1735): remove `DiagnosticResultMessages` and the quick-fix data revision; gate:
  X181–X185, X202, X213 and diagnostic/action Undo/Redo. On the 0.30.0 branch: `9afed877da`.
- [ ] UP08 (#1710): remove `ConfigurationJson`'s null handling; gate: X266, X268, CFG1–CFG3.
  On the 0.30.0 branch: `d8ad697b7f`.
- [ ] UP09 (#1724, #1725, #1728, #1736): remove `DocumentStartupMessages`; gate: startup typing and
  close/reopen regressions. On the 0.30.0 branch: `2be956ad8c`.
- [ ] UP10 (#1712, #1734): remove `XtcParameterInfoHandler`; gate: X20 and the retrigger and
  constructor signature cases. On the 0.30.0 branch: `10b92e3803`, with the server's `df11487151`
  for X20.
- [ ] UP11 (#1733): remove the `CodeActionMessages` routing; gate: X105, X122, stale refusal and
  native Undo/Redo. On the 0.30.0 branch: `58782992f6`.
- [ ] UP12 (#1707): narrow the `XtcFormattingService` override; gate: absent-editor, snapshot,
  cancellation and server-lifetime guards, X138 and X139. The UP24 command ownership stays.
- [ ] UP14 (#1716): remove the semantic-result retirement in `XtcLanguageClient`; gate: X146.
- [ ] UP14's other half ([#1771](https://github.com/redhat-developer/lsp4ij/pull/1771), merged 2026-10-27,
  unreleased, `7fa1794f`): hover, definition, declaration, type
  definition, implementation, references, completion, rename and the hierarchy prepares reuse a
  completed answer for the same offset while the file's own stamp is unchanged, so another file's
  edit leaves them stale until the file is edited or another offset is asked for. The pull request
  also outdates them when the project's PSI modification count changes; its test fails without the
  fix for hover and definition. Gate after a release: `XtcLanguageClient`'s retirement shrinks to
  what #1771 leaves, answers that change after an analysis without any file changing, and the
  features still on the file stamp; X146 passes.
- [ ] UP18 (#1711): advertise `AsIs` again; gate: X150 keeps exact source, placeholders and Undo.
  On the 0.30.0 branch: `06acfabc96`.
- [ ] UP19 (#1708): remove `DirectoryDocumentMoves`; gate: X118, X161, X162, X163 and the
  connection lifecycle tests. On the 0.30.0 branch: `4d7a8a361a`.
- [ ] UP21 (#1726, #1731): remove `ClosedRefactoringDocuments`; gate: X169 (exact disk text and
  current diagnostics). On the 0.30.0 branch: `1172c649d8`; `XtcRenameEdit` still refuses an unsaved
  closed target.
- [ ] UP22 (#1699): no workaround to remove; gate: native reconnect and typing acceptance.
- [ ] UP25 (#1749): advertise `workspace/textDocumentContent` to IntelliJ; gate: X254 without the
  read-only file fallback. On the 0.30.0 branch: `e1ca98fdb0`; X254's checks `56c699eb5a`.
- [ ] UP26 (#1715): gate: X257 with explicit invocation and selection forwarded.
  On the 0.30.0 branch: `12ac9ec06b`; X257 waits for the Invoked request (`357045d859`).
- [ ] UP27 (#1700): remove `EditorRefresh`; gate: the many-file X146 burst and the long
  X137/X146/X259 run. On the 0.30.0 branch: `bba29c4b4d`.
- [ ] UP03 (#1720, #1739): narrow the guarded Rename/Move entry points; gate: X103 and X130.
- [ ] UP03: follow LSP4IJ #1740 (generic UI wait during the `will*` requests), still open.
- [ ] UP30 ([#1757](https://github.com/redhat-developer/lsp4ij/pull/1757), merged 2026-10-26): `DAPDebugProcess` did not implement `startPausing()`,
  so IntelliJ's Pause Program did nothing for any DAP session; X308 passes natively with it. Gate:
  X308 in IntelliJ with coverage full, and its IntelliJ case restored.
- [ ] UP33 ([#1761](https://github.com/redhat-developer/lsp4ij/pull/1761), merged 2026-10-26): the exception hint from `exceptionInfo`. Gate: X307's
  hint shows the exception and its stack without the runner's frames.
- [ ] UP34 ([#1760](https://github.com/redhat-developer/lsp4ij/pull/1760), merged 2026-10-26): conditions on exception filters, sent as
  `filterOptions`. Gate: X312 in IntelliJ.
- [ ] UP37 ([#1763](https://github.com/redhat-developer/lsp4ij/pull/1763), merged 2026-10-26): `DAPExpressionCodeFragment` honors the `isPhysical`
  that IntelliJ asks for, so IntelliJ 2026.2's inline logpoint editor no longer fails its inspection
  pass's assertion, and an edit of Evaluate Expression's expression, a typed character or a chosen
  completion, no longer fails IntelliJ's Document/PSI check (found by R10e's X320). Checked natively
  with R10e: with a plugin built from `main`, X320's chosen completions make `box.count`, with no IDE
  error (`run-8742366318524741203`). Gate: a breakpoint with Suspend unchecked and a log expression,
  with no IDE error; X320 in IntelliJ through the inserted text, its 0.21.0 path and UP37 tags
  removed.
- [ ] UP40 ([#1766](https://github.com/redhat-developer/lsp4ij/pull/1766), merged 2026-10-25): LSP4IJ announces `supportsInvalidatedEvent` and
  handles `invalidated`, refreshing the threads, the stopped stacks and the views. Gate: X314 and X315
  full in IntelliJ, their `invalidated` checks restored.
- [ ] UP31 ([#1758](https://github.com/redhat-developer/lsp4ij/pull/1758), merged 2026-10-26): an exception hint on an editor closed
  since the stop no longer fails with `DisposalException`. Gate: X307 without its wait for the hint, and
  no IDE errors.
- [ ] UP32 ([#1759](https://github.com/redhat-developer/lsp4ij/pull/1759), merged 2026-10-26): breakpoints that don't suspend as DAP logpoints,
  their "Evaluate and log" expression logged. Gate: X311's and X313's logpoints in IntelliJ, their UP32
  tags removed.
- [ ] UP36 ([#1769](https://github.com/redhat-developer/lsp4ij/pull/1769), merged 2026-10-26): a "Show Values as Hexadecimal" toggle,
  checked natively with R10b (`run-5016431580533175464`). Gate: X317's hexadecimal check always on,
  its UP36 tag removed.
- [ ] UP35 ([#1768](https://github.com/redhat-developer/lsp4ij/pull/1768), merged 2026-10-26, `42f1f16e`): lazy variables
  behind a "Show value" link, checked natively with R10a. Gate: X316 in IntelliJ through the link
  only, its 0.21.0 path and UP35 tag removed.
- [ ] UP42 ([#1770](https://github.com/redhat-developer/lsp4ij/pull/1770), merged 2026-10-26, `642d0e9d`): a variable with more
  than 100 indexed children read a page at a time behind IntelliJ's own `...` node, checked natively
  with R10c (`run-9236570344305345016`). Gate: X318 through the `...` node only, its 0.21.0 path and
  UP42 tag removed.
- UP13 stays: the `createSettings` override is LSP4IJ's supported extension point.

### LSP4IJ: pull requests open

- [x] X304 on LSP4IJ `main` is not an LSP4IJ regression. Its console seemed to lose what the program
  prints after Resume, but that was our probe's race: it read the console before the exit code, so a
  program that ended in between was reported without its last output. R7n's probe fix (`c966674fc`)
  reads the exit code first; with it, X304 and X307 pass twice in a row with a plugin built from `main`
  (`ef2b18c3`; `run-11462216125559745352`, `run-9056817084446612838`). Nothing to file.

### LSP4IJ: LSP4J's debug modules at 0.21.0 (UP63): our #1793, merged

- [ ] UP63 (2026-10-28): LSP4IJ bundles LSP4J's LSP modules at 1.0.0 and its debug modules at 0.21.0,
  so no fix to LSP4J's DAP endpoint since 0.21.0 reaches IntelliJ, our LSP4J #1004, #1008 and #1009
  among them, and LSP4IJ's DAP model stays at DAP 1.60. Our
  [#1793](https://github.com/redhat-developer/lsp4ij/pull/1793) (merged 2026-10-28, unreleased, `2afdaf45`)
  bundles them at LSP4J's one version. UP41's LSP4J part needs it to reach IntelliJ.

### IntelliJ Platform and LSP4IJ: evaluations cannot be cancelled (UP41): LSP4J part our draft #1009

- [ ] UP41 (2026-10-24, cause found 2026-10-26): an IntelliJ evaluation that runs code cannot be ended
  before its time limit; X314 checks cancel in VS Code only. LSP4IJ never sends DAP `cancel`, but the
  gap is IntelliJ's first: `XDebuggerEvaluator.XEvaluationCallback` has no obsolescence
  (`XValueCallback` has no `isObsolete()`, and `XEvaluationCallbackBase` adds none), and the Evaluate
  dialog neither cancels an evaluation when it closes or evaluates again nor offers to stop one, so an
  evaluator never learns that its result is no longer wanted. LSP4IJ could only cancel at a resume,
  step or stop, where our server ends the run itself. To do: an IntelliJ Platform ticket (IJPL) for an
  evaluation's obsolescence or cancel; then the LSP4J part, our draft [LSP4J #1009](https://github.com/eclipse-lsp4j/lsp4j/pull/1009)
  (2026-10-28): `DebugRemoteEndpoint.sendCancelNotification()` sends DAP `cancel` with the request's
  `seq` as `requestId` (LSP4J sent LSP's `$/cancelRequest` notification, which a DAP endpoint writes
  as an `event`), and an incoming `cancel` cancels the request it names; it waits on the maintainer's
  view of older LSP4J peers and of an adapter's own requests. Then the LSP4IJ part: an evaluator that
  cancels its request, to an adapter with `supportsCancelRequest`, once IntelliJ abandons it. LSP4IJ
  cancels no DAP request today.

### LSP4IJ: Run to Cursor removes its breakpoint too early (UP44): our #1773, merged

- [ ] UP44 (2026-10-27): `DAPDebugProcess.runToPosition()` registers a temporary breakpoint and
  resumes, and `resume(context, position)` removes it once the `continue` request is answered, not
  when the program stops there (0.21.0 and `main`, `42f1f16e`). A program that takes longer to reach
  the cursor than that round trip runs past it: with the cursor two statements after the stop,
  IntelliJ stopped there; after a count to 3,000,000 it ran to its exit. VS Code 1.140 removes its
  temporary breakpoint when the session stops. Fixed by our
  [#1773](https://github.com/redhat-developer/lsp4ij/pull/1773) (merged 2026-10-27, unreleased): the
  temporary breakpoint stays until the program next stops, and the session's end forgets it; with a
  plugin built from it, and again from `main` at `e9f8bb42` (`run-11586456472789120644`), X323 stops
  at the cursor in IntelliJ. To do: after a release with it, make X323
  full in IntelliJ, its UP44 gate removed. The nightly 0.21.1-20261028-080211 has it: there X323
  fails with its gate's message, "UP44 is fixed" (`run-793652569316991787`).

### LSP4IJ: no loaded sources or modules (UP45): our #1774, merged

- [ ] UP45 (2026-10-27): LSP4IJ never sends `loadedSources` or `modules`, and has no view for them
  (0.21.0 and `main`, `42f1f16e`). XVM's adapter answers both since R12, which VS Code shows in
  Loaded Scripts (X326). Implemented by our
  [#1774](https://github.com/redhat-developer/lsp4ij/pull/1774) (merged 2026-10-27, unreleased): Loaded Sources and
  Modules tabs, added once the adapter reports any; with a plugin built from it, a temporary X326
  found both, with the program's sources and modules. To do: after a release with it, implement
  IntelliJ's X326.

### LSP4IJ: no function breakpoints (UP46): our #1780, merged

- [ ] UP46 (2026-10-27): LSP4IJ has no function breakpoints: it never sends `setFunctionBreakpoints`
  (0.21.0 and `main`, `42f1f16e`); its breakpoints are lines, disassembly instructions and
  exception filters. XVM's adapter installs them since R9 (X328), which VS Code's Breakpoints view
  adds. Implemented by our [#1780](https://github.com/redhat-developer/lsp4ij/pull/1780) (merged
  2026-10-27 as `0d3c64de`, unreleased): a DAP Function Breakpoint type, added from the Breakpoints dialog's "+", sent
  with `setFunctionBreakpoints` and matched by `hitBreakpointIds`. Checked natively: with a plugin
  built from it, IntelliJ's X328 stops in `Entered.twice` and runs to its exit
  (`run-11586456472789120644`), and again at `bef74868`, rebased onto `main` with #1752
  (`run-551361467612578401`). To do: after a release with it, make IntelliJ's X328 full.

### LSP4IJ: no restart frame (UP47): our #1778, merged

- [ ] UP47 (2026-10-27): LSP4IJ never sends `restartFrame`, and ignores `supportsRestartFrame` and a
  frame's `canRestart` (0.21.0 and `main`, `42f1f16e`), so IntelliJ's Reset Frame is not offered in
  a DAP session. XVM's adapter restarts frames since R11 (X329), which VS Code's Call Stack view
  offers. Fixed by our [#1778](https://github.com/redhat-developer/lsp4ij/pull/1778) (merged
  2026-10-27, unreleased, `62c4a53e`): a DAP drop-frame handler that offers Reset Frame on a frame that can restart,
  sends `restartFrame`, and resumes the session until the `restart` stop. Checked natively
  (2026-10-27): with a plugin built from it, IntelliJ's X329 resets outer() through the Reset Frame
  action, which is hidden on run(), and passes (`run-17331679936087029005`). To do: after a
  release with it, make IntelliJ's X329 full.

### VS Code and LSP4IJ: no single-thread execution (UP49): open upstream

- [ ] UP49 (2026-10-27): neither editor sends `singleThread` with `continue` or a step, so they
  always resume every fiber; VS Code's request
  [#166450](https://github.com/microsoft/vscode/issues/166450) is open, and LSP4IJ has none. R11's
  last item waits on it. To do: build it once an editor sends `singleThread`.

### LSP4IJ: no inline values for Ecstasy (UP50): our #1781, merged

- [ ] UP50 (2026-10-27): IntelliJ shows no inline values for Ecstasy. LSP4IJ never sends
  `textDocument/inlineValue` (0.21.0 and `main`, `f22304a4`); its scan of the editor's TextMate tokens
  places none of the frame's variables; and it leaves the frame's Locals group collapsed, while
  IntelliJ computes inline data only for the variables it shows. LSP4IJ issue
  [#1432](https://github.com/redhat-developer/lsp4ij/issues/1432) reports the same; its maintainer
  answered that inline values are an LSP feature. Fixed by our
  [#1781](https://github.com/redhat-developer/lsp4ij/pull/1781) (merged 2026-10-28, unreleased, `7469aab9`): it asks
  the language server, falls back to the token scan, and expands the locals' group; with a plugin
  built from it, IntelliJ draws the values and X331 passes (`run-4604772112093360200`). To do: after a
  release with it, make IntelliJ's X331 full.

### LSP4IJ: no data breakpoints (UP48): our #1782, merged

- [ ] UP48 (2026-10-27): LSP4IJ never sends `dataBreakpointInfo` or `setDataBreakpoints` (0.21.0 and
  `main`, `42f1f16e`), so IntelliJ has no breakpoint on a variable's value in a DAP session. XVM's
  adapter installs them since R9 (X330), which VS Code's Break on Value Change adds. Fixed by our
  [#1782](https://github.com/redhat-developer/lsp4ij/pull/1782) (merged 2026-10-28, unreleased,
  `c7955a13`): Break on Value Change in the Variables view asks `dataBreakpointInfo` and adds
  a DAP data breakpoint, which it sends with `setDataBreakpoints`; one that cannot persist goes with
  its session. In IntelliJ 2026.2 the action finds the DAP value behind the split debugger's
  frontend proxy through `XDebuggerTreeBackendOnlyActionBase`. Its `DAPBreakOnValueChangeSessionTest`
  runs a debug session against a DAP server in the test's process. With a plugin built from it,
  IntelliJ's X330 passes (`run-15563293796254899407`, `b6191ffbce`). To do: after a release with
  it, make IntelliJ's X330 full.

### A DAP completion's `start` (UP43): not filed

- UP43 (2026-10-26): DAP measures a completion item's `start` in the client's column base, but the
  adapters send it 0-based and LSP4IJ and VS Code read it so, although both declare
  `columnsStartAt1`: vscode-js-debug and CodeLLDB as an index into the text, debugpy as a column in
  the request's line, java-debug always 0; lldb-dap sends only `length`, and Delve has no
  completions (their sources, read 2026-10-26). Converting by the column base in LSP4IJ would move
  their insertions one character to the left, so nothing is filed. XVM's adapter sends `length`
  only, which both editors replace before the cursor (`0e0d7e2c4`).

### LSP4IJ: no monikers (UP51): not filed

- UP51 (2026-10-28): LSP4IJ never sends `textDocument/moniker` (0.21.0 and `main`, `7469aab9`), and
  IntelliJ has no view or navigation that uses monikers; no LSP4IJ issue or pull request mentions
  them. XVM's server answers them since L74/L75, and IntelliJ's X31 and X252–X254 pass by sending
  the request through LSP4IJ's LSP4J endpoint, a protocol check rather than native coverage; VS Code
  has no moniker view either. Nothing is filed until IntelliJ has a use for them.

### LSP4IJ: document links without a target (UP52): our #1785, merged

- [ ] UP52 (2026-10-28): LSP4IJ declares document links but never resolves one, and its Ctrl+Click
  ignores a link without a target (0.21.0 and `main`, `7469aab9`). XVM's server defers every link's
  target to `documentLink/resolve` for any client that declares document links, so an HTTP(S) link
  in a comment very likely opens nothing in IntelliJ. X131 resolves links itself, so it can't show
  this. Fixed by our [#1785](https://github.com/redhat-developer/lsp4ij/pull/1785) (merged 2026-10-28, unreleased, `02faa149`):
  a clicked link without a target is resolved when the server has `resolveProvider`. Checked natively
  in IntelliJ's X131 (2026-10-28, `a521e2ebbf`): 0.21.0 opens nothing
  (`run-2626450875688172472`), and a plugin built from #1785 resolves the link and opens it
  (`run-8879176847449743226`, gate UP52 passed). To do: after a release with it,
  make X131's UP52 step pass without the gate.

### LSP4IJ: no linked editing (UP53): not filed

- UP53 (2026-10-28): LSP4IJ never sends `textDocument/linkedEditingRange`, so IntelliJ users get no
  rename-on-type for locals, which XVM's server answers. IntelliJ's X133 sends the request itself,
  a protocol check rather than native coverage.

### LSP4IJ: only the first of several ranges is formatted (UP54): our #1786, merged

- [ ] UP54 (2026-10-28): when IntelliJ formats several ranges of a file at once, LSP4IJ formats the
  first and drops the rest, and never sends LSP 3.18's `rangesFormatting`, which XVM's server
  supports. Our `XtcFormattingService` takes the first range too. Fixed in LSP4IJ by our
  [#1786](https://github.com/redhat-developer/lsp4ij/pull/1786) (merged 2026-10-28, unreleased, `0616567a`): one
  `rangesFormatting` to a server with `rangesSupport`, otherwise one `rangeFormatting` per range.
  To do: make `XtcFormattingService` send every range too; after a release with #1786, a native case that
  formats two separate ranges.

### LSP4IJ: no hit counts on line breakpoints (UP55): our #1787, merged

- [ ] UP55 (2026-10-28): LSP4IJ's line breakpoints send a condition and a log message, never a hit
  condition; function and data breakpoints do (#1780, #1782). XVM's adapter supports hit conditions
  since R9. The R9 item above puts IntelliJ's hit counts under UP32, but our #1759 added logpoints
  only. Fixed by our [#1787](https://github.com/redhat-developer/lsp4ij/pull/1787) (merged 2026-10-28,
  unreleased, `0ecbcdeb`): a DAP line breakpoint has a `Hit condition`, sent as `hitCondition`. To do:
  after a release with it, a native IntelliJ hit-count case.

### LSP4IJ: the `exited` event (UP56): bridged, our #1791, merged

- [ ] UP56 (2026-10-28): LSP4IJ ignores DAP's `exited` event, so the program's exit code never reaches
  IntelliJ through it. XVM's `LanguageServerDebugProcessHandler` reads `exited` itself and ends its
  process with that exit code. Our [#1791](https://github.com/redhat-developer/lsp4ij/pull/1791) (merged
  2026-10-28, unreleased, `116a18d1`) puts the exit code on the session's process handler
  (`DAPDebugProcess.PROGRAM_EXIT_CODE`) and shows it when the adapter's process ends; CodeRabbit's
  finding, a child session's exit code replacing its parent's, was fixed in it first. To do: after a
  release with it, have our handler take the exit code from that key instead of reading `exited`.

### LSP4IJ: inline-value and folding-range refreshes (UP57, UP58): our #1790, merged

- UP57, UP58 (2026-10-28): LSP4IJ declares neither inline-value nor (LSP 3.18) folding-range
  refresh. XVM's server sends each only to a client that declares it, so nothing is lost in
  IntelliJ. Our [#1790](https://github.com/redhat-developer/lsp4ij/pull/1790) (merged 2026-10-28,
  unreleased, `436a4db5`) declares both and handles them; CodeRabbit's finding, a race on a stack
  frame's variables context, was fixed in it first.

### LSP4IJ: no workspace symbol resolve (UP59): our #1788, merged

- [ ] UP59 (2026-10-28): LSP4IJ resolves no workspace symbol, so XVM's server sends it each
  symbol's full location. Our [#1788](https://github.com/redhat-developer/lsp4ij/pull/1788) (merged
  2026-10-28, unreleased, `44675cb3`) declares `resolveSupport` for `location.range` and resolves a URI-only symbol when it
  is opened. To do: after a release with it, a native Go to Symbol case that opens a URI-only symbol
  at its range.

### LSP4IJ: inlay hint tooltips never showed (UP60): our #1789, merged

- [ ] UP60 (2026-10-28): LSP4IJ showed only the plain-string tooltips of a hint's label parts, so
  XVM's Markdown tooltips, which its server puts on the hint, never showed in IntelliJ. This was
  first recorded as having no effect on XVM, which was wrong. Fixed by our
  [#1789](https://github.com/redhat-developer/lsp4ij/pull/1789) (merged 2026-10-28, unreleased,
  `f4e57c05`): the hint's tooltip, or else its parts', with Markdown rendered. LSP4IJ still resolves
  no tooltip lazily, which IntelliJ's declarative inlays don't allow, so XVM's server keeps sending
  tooltips with the hints. To do: after a release with it, a native case that shows an inlay hint's
  tooltip.

### LSP4IJ: its support lists disagreed with its code (UP61): our #1784, merged

- [x] UP61 (2026-10-28): LSP4IJ's `LSPSupport.md` and `DAPSupport.md` marked some supported
  features as unsupported, listed pull diagnostics under two names and
  `textDocument/publishDiagnostics` twice, and left out others. Fixed by our
  [#1784](https://github.com/redhat-developer/lsp4ij/pull/1784) (merged 2026-10-28, `e301a971`).
  The lists are read on `main`, so no release is needed.

### LSP4J

- [ ] UP15: get LSP4J #997 reviewed and merged. @pisv was asked on 2026-10-20; no reply yet.
- [ ] UP15: after an LSP4J release with #997, adopt it and change `XdkStdioTest`'s assertion to
  `ResponseErrorCode.InvalidParams`, keeping reader recovery.
- [ ] UP06: after an LSP4IJ release with #1723 (0.30.0), remove the `DiagnosticReportJson` adapter;
  gate: full and unchanged reports round-trip in the bundled client. No LSP4J release is needed:
  #1723 registers its own adapter in `configureCompatibilityAdapters`, which the client's Gson applies
  (checked 2026-10-28 against LSP4IJ's `main`; done on the local branch `lagergren/lsp4ij-0.30.0`,
  `fe12afc23e`).

### vscode-languageclient

- [x] UP28: #1896 (fix for #1307) merged by the maintainer on 2026-10-21 (`49328700`); #1307 is
  still open.
- [x] UP28: `vscode-languageclient` 11.0.0 (2026-10-22) has #1867 and #1896; the extension takes it
  in `40013e5306` (2026-10-29), without the double observation of `start()` or the `stop()`
  override. X272 passes on 11.0.0 without them and fails on 10.1.2 without them.

### Not filed yet: fixes ready locally

Each is a local commit with an upstream-style test, verified natively on 2026-10-20. Nothing is
pushed. The drafts of the tickets, PR descriptions and the #219122 comment are in
`~/src/upstream-drafts/`, outside the repository, one file each (written again 2026-10-28: the
2026-10-20 session's scratchpad held only harnesses). Filing needs, besides: a YouTrack account for
the JetBrains tickets, forks `lagergren/intellij-community` and `lagergren/vscode`, each project's
contributor agreement, and the branches rebased. UP17's commit message quotes 14.6 s with 80,000
highlighters in an editor, which its probe never measured: check or drop it before filing.

- [ ] UP17 (IntelliJ Platform): our [#3728](https://github.com/JetBrains/intellij-community/pull/3728) (open, 2026-10-28) for the existing
  [IJPL-259475](https://youtrack.jetbrains.com/issue/IJPL-259475), a console freeze whose stacks end in the same `maxEnd` correction. To do: get it merged.
- [ ] UP17: fix the probe's readiness check. Its decorated replacement started with about 36,000
  of 80,000 highlighters installed, so it never measured the decorated case.
- [ ] UP24 (IntelliJ Platform): filed as [IJPL-259881](https://youtrack.jetbrains.com/issue/IJPL-259881), a duplicate of the older
  [IJPL-29917](https://youtrack.jetbrains.com/issue/IJPL-29917) and [IJPL-223024](https://youtrack.jetbrains.com/issue/IJPL-223024); our [#3727](https://github.com/JetBrains/intellij-community/pull/3727) (open, 2026-10-28). To do: get it
  merged.
- [ ] UP20 (IntelliJ test Driver): filed as [IJPL-259883](https://youtrack.jetbrains.com/issue/IJPL-259883); our [#3729](https://github.com/JetBrains/intellij-community/pull/3729) (open, 2026-10-28).
  To do: get it merged.
- [ ] UP29 (IntelliJ test Driver): filed as [IJPL-259884](https://youtrack.jetbrains.com/issue/IJPL-259884); our [#3730](https://github.com/JetBrains/intellij-community/pull/3730) (open, 2026-10-28).
  To do: get it merged.
- [ ] UP16 (VS Code): our [microsoft/vscode#344760](https://github.com/microsoft/vscode/pull/344760) (open, 2026-10-28) for the existing
  microsoft/vscode#219122. To do: get it merged.
- [ ] After each is fixed and released: remove the matching test or editor workaround and pass its
  gate (UP16: X130 and the standalone reproduction; UP17: the decorated X145; UP20: Rename and focus
  recovery cases; UP24: formatting history without the write command; UP29: restart X137/X146/X259
  without `UiWaits`).
- UP23 (VS Code overlapping-move Undo) has no fix; X218 stays a failed assertion and an accepted
  exception unless the host gains an API for it. Filed as [microsoft/vscode#344765](https://github.com/microsoft/vscode/issues/344765), fixed by our
  [microsoft/vscode#344772](https://github.com/microsoft/vscode/pull/344772) (2026-10-28). To do: get it merged; after a release, X218 passes with child-first moves.

## 5. Pull requests and the release

- [ ] #693 (the complete reference PR): reviews. It's for building and testing the whole product,
  and likely won't merge as it stands.
- [ ] Keep #693 in sync with new `lagergren/errs` commits, the debugger's included since 2026-10-28,
  when the user decided that #693 takes everything that completes the LSP and DAP implementation,
  distilled per feature, without tracking documents or plans. On 2026-10-28 #693 was also regrouped
  into 70 commits on master `6c025c0426`, without its plans, every commit compiling, with
  [the regroup map](errs-693-regroup-2026-10-28.tsv) from each new commit to the old ones (see "#693
  regrouped and rebased onto master" in
  [the integration plan](errs-integration-plan.md#693-regrouped-and-rebased-onto-master-2026-10-28));
  its last commit, the rename proof fix, is errs `a8264753db`. On 2026-10-29 #693 was rebased onto
  master `636b29d80b` (#742), dropping its copy of #742, as 70 commits (`51c0b48aed`), and took
  `vscode-languageclient` 11.0.0 without UP28's workarounds as its commit 71 (`4a5f624b67`, errs
  `40013e5306`). Last synchronized 2026-10-28: the
  debugger track through `a521e2ebbf`, as 22 feature commits and two of #693's own,
  `9b3b802eb2..6f5f5fde1b` before the regroup (see "The debugger track in #693" in
  [the integration plan](errs-integration-plan.md#the-debugger-track-in-693-2026-10-28)). Before
  that, the same day, the two library document fixes `622d0a506f` and `4cf5450acd` as `9e90dd1e13` and
  `b80a36e050`, the second without errs' `file()` and `copy()`, which came with the debugger's library
  frames, and with an import #693 lacked; XdkLibrarySourcesTest, LibraryContentProtocolTest and
  LibraryCopiesCleanupTest pass there. Before them, `f1d2060f29..3137736743`, 15 commits, each a clean cherry-pick: the discovery race fix
  `f71c9cc0a5` as `3137736743`; the focused test tasks `bdab767b8c` and `021948fcdb`; the LSP4IJ
  channel option and the nightly's playbook fixes `4ca6f5993d`, `4918127129`, `8d845d167e`,
  `ddcc6fda9d`, `0952a3f0c9`, `3b7c450765`, `ff06c86d0b` and `b499c339fb`; and the full runs' fixes
  `a82a762d19`, `95b4cfa2f1`, `5e6a0f2391` and `c2a9b296d5`. The lsp-server tests and the IntelliJ
  and VS Code playbooks compile; XdkResourcesTest (3) and the new errorListenerTest (68) pass. A
  survey of every errs commit since 2026-10-16 that #693 lacks found nothing else to take: the
  others are debugger work and its records, already in #693 in another form (the close races as
  `75af7a168c`, the build-logic fields), for code #693 doesn't have (the `f_errs` alignment, X31's
  `inlineValueProvider`), or the 2026-10-16 file-event fixes and their revert, which cancel out.
  Before that, 2026-10-27: the semantic token history's synchronization `efdcff889c` as `f1d2060f29`, its
  SemanticTokenReportsTest passing; before that, after both were rebased onto master `bcd8e30d58`: R8's fixes that are not debugger
  work are the pool count, the graph-walk tests and the worker's heap without `System.gc()` as
  `9eae4bfa50`, `1feca90e99` and `34b3ef3878`, then the read-only test skips `c730053240` as
  `c5adf2518e`, a worker's exit within 5 s `c68bbcba08` as `fa879b8b7f`, with `RunWorker` by hand,
  its test's thread dump `49d4f0dd5a` as `a2d059ddf9`, `FileRepository`'s temporary file
  `02b9109102` as `18b27fe115`, a fresh worker after a failed test `06dacc9c91` as `ce5dfab340`,
  and the test warnings `3321ab391b` as `63f03a4bac`. Before that, 2026-10-26, after both were
  rebased onto master `5130e4211`, see the
  [integration plan](errs-integration-plan.md): the torn-jar check `143594eec` as `12170a7c1`, the
  AGENTS.md inline-comment rule `b15c44ab7` as `00f7289c0`, and the plan's part of the R5 record
  `362aaafc4` as `f863ffabd`; #720's reviewed weak map came from master. Before that, 2026-10-23,
  in the old history: `eddd77ee9` as `c7fedbe53`, `ab9fbddeb` as `e618f7a22`, without its `SessionDebuggerTest`
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
- [x] Full playbook runs, 2026-10-28:
  - VS Code 1.140.0 at `5e6a0f2391` (`run-Pc161s`): 332 passed; X218 fails as UP23 records, now as
    an assertion that names UP23, not a timeout that stopped the run (`a82a762d19`). Its
    dependencies passed: `:lsp-server:test` (2,414, 2 skipped) and `compilerStdioTest` (81).
  - IntelliJ 2026.2.3 with LSP4IJ 0.21.0 at `c2a9b296d5` (`run-17527198196716738382`): 310 passed,
    15 partial, 8 not implemented; X283 failed once, to stray keystrokes, and passes alone. No IDE
    exception. The run before it (`run-7875402505096583343`) failed X303, now fixed, and hit UP14's
    cache race in LSP4IJ 0.21.0 (#1716, unreleased): IntelliJ's breadcrumbs got no document symbol
    request and reported an `IllegalArgumentException` from `awaitWithCheckCanceled`.
  - Fixed on the way: X303's root kept `.gradle` and `gradle` from X129 and the compiler-import
    cases, in both editors (`95b4cfa2f1`, `5e6a0f2391`, `c2a9b296d5`); the server reported a module
    container that moved away during discovery as an invalid `xtc.compiler` setting, seen in X130
    (`f71c9cc0a5`).
  - The nightly run is under [LSP4IJ](#lsp4ij-merged-on-main-waiting-for-a-release).
- [ ] Release 0.5.0, the agreed boundary for the breaking listener and embedding API, with migration
  and recompilation notes.
- [ ] Settle the reconciliation inventory in the roadmap's
  [remaining work outside the prepared branches](../pr-roadmap.md#remaining-work-outside-the-five-prepared-branches).
- [x] Declined for master (2026-10-26): native `toString()` for `String`,
  `Char` and the integer types, or a native `String` `+` for them. A `String`'s `+`
  (`xString.invokeAdd`) calls `Utils.callToString()`, which runs Ecstasy code for these
  (`String.toString()`, `Stringable.toString()`), so every string join of every program pays an
  Ecstasy call; found by tier 1's refusal of `s + i` (`fda3dc3e8`). It would also let tier 1 join strings.
  In master pull request [#722](https://github.com/xtclang/xvm/pull/722) (2026-10-24,
  `lagergren/native-to-string`, `12d691d3e`): `Int8` to `UInt64`, `Nibble`, `Char` and `String`, and `markNativeMethod()`, which found
  no `toString()` to mark in a const class, since `Const` and its `Stringable` come with no
  contribution. 200,000 iterations of five joins: about 16.3 s on master, 0.14 to 0.3 s with it. Not
  on this branch. Closed on 2026-10-26 after review: the interpreter keeps natural Ecstasy code wherever
  it can, and speed is the JIT's job. The closing comment notes `markNativeMethod()`'s gap through
  `Const`, which nothing reaches today.
- [x] Candidate for master on its own, apart from the debugger and the `toString()` one above:
  `ab9fbddeb`'s `ServiceContext` change and its `EmbeddingSupportTest` case, which master has unchanged
  (its `terminate()` and `shutdown()` assert the same, and the test uses only `EmbeddingSupport`).
  `Container.kill()` hangs, with assertions on, once a service has a fiber that ended with a call still
  out. It is on #693 since 2026-10-23 (`e618f7a22`), and in master pull request
  [#716](https://github.com/xtclang/xvm/pull/716) (2026-10-23, `lagergren/fix-embedded-run-end`), with
  `1473c7e89` below, written for master: without the `SessionDebuggerTest` change and the server
  README's line, which master's README has no place for. Merged as #716 on 2026-10-28 (`22ec660f1f`).
- [ ] Candidates for master on their own, found by a scan of this branch's fixes outside `lang/` against
  master (2026-10-23), each reproduced on master. State on 2026-10-29: #716, #717, #718, #720, #723,
  #731 and #735 are merged; #719, #721 and #722 are closed, and #730 is closed with its fix in #724,
  which is the one still open; `814153a42` stays on this branch.
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
    written again without errs' Site API. Dropped from #717 on 2026-10-26, as decided on #677: the
    compiler logs many errors it never reports, and a UID with the parameters as text would render
    them for each, while a collision needs the same code, place and parameter hash. Stays on this
    branch only.
  - `6312fd242`: an embedded run that reaches `assert:debug` reads the host's standard input;
    only third-party hosts reach it on master. In master pull request
    [#718](https://github.com/xtclang/xvm/pull/718) (2026-10-23, `lagergren/fix-embedded-assert-debug`),
    its `javatools` part with the `EmbeddingSupportTest` case. Its alternative,
    [#719](https://github.com/xtclang/xvm/pull/719) (2026-10-23, from ggleyzer), turns the console
    debugger off with a runtime flag that embedding connectors clear. Both fix the hang: a program run
    through `EmbeddingSupport` with a standard input that stays open waits at `Enter command:` on
    master and ends with either (2026-10-26). Neither PR's test catches it, since the Gradle test JVM's
    standard input is at its end; master's console debugger then returns without clearing the
    runtime's "debugger is active" flag, which stays true. The feedback on #719 says so, and that it has
    no debugger a host supplies. On 2026-10-26 Gene accepted #718's approach if it also turns the
    debugger off globally, as #719 does: #718 now has the system property
    `org.xvm.runtime.debugger=off` (`6de116655`, with `RuntimeDebuggerTest`), drops the comment he
    asked about, keeps the console debugger once chosen, as Cam asked, and keeps `getRuntime()`, with
    the reason (a point an instrumentation agent or test can intercept) in its thread. Rebased onto
    master and re-requested; #719 closes once #718 is merged.
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
    and on #693 since 2026-10-24 (`a5e25c725`). After review (2026-10-26, `8c457fe25`, here
    `7fe6450b8`): `toArray()` fills one array, copied only when collected keys were left out, and the
    test clears the map's weak reference itself instead of waiting for `System.gc()`. Not yet on #693.
  - Found by the native `toString()` test (2026-10-23): `new Nibble(Bit[])` makes 0 for every value,
    and so does `Nibble`'s table, and with it `Nibble.of()`, `IntLiteral.toNibble()` and
    `Char.isNibble()`; `xConstrainedInteger.constructFromBits()` packs the bits into a byte's high
    bits, and `Nibble`'s mask drops them. Its `bits` failed and `Nibble(String)` overflowed for the
    same reason, sizes counted in whole bytes. All three in master pull request
    [#723](https://github.com/xtclang/xvm/pull/723) (2026-10-24, `lagergren/fix-nibble-from-bits`),
    tested with asserts in `manualTests`' `numbers.x`, which CI runs. Merged on 2026-10-26 as
    `80bf83f4d`, with the review's wording of its comments. `Nibble(Byte[])` still failed, and so did
    a `Nibble`'s `toByteArray()`: a bit array makes bytes only from whole bytes, and the runtime took
    `f_cNumBits / 8` bytes. In master pull request [#731](https://github.com/xtclang/xvm/pull/731)
    (2026-10-26, `lagergren/fix-nibble-bytes`): one byte whose low four bits hold the value, both
    ways, and a byte with a high bit set is out of bounds; asserts in `numbers.x`' `testNibble()`.
  - Found through #723's callers (2026-10-24): `lib_json`'s `Lexer` dropped every `\u` escape, so it
    could not read back the control characters its `Printer` writes. In master pull request
    [#724](https://github.com/xtclang/xvm/pull/724) (`lagergren/fix-json-unicode-escape`), with
    surrogate pairs read as one `Char`, and independent of #723. After review (2026-10-26,
    `8a37676ac`): `eatHexits()` is a local function of `eatString()`, with shorter comments.
  - Found with #724: `Char.appendEscaped()` writes the first three hexits of a `\u` escape as 0.
    Shifts and `&` share a precedence level, so `codepoint & 0xF000 >>> 24` is
    `(codepoint & 0xF000) >>> 24`. On master `0x2028.toChar().quoted()` is `'\u0008'`. In master
    pull request [#730](https://github.com/xtclang/xvm/pull/730) (2026-10-26,
    `lagergren/fix-char-escape-hexits`), with a `testEscapes()` in `literals.x`. Its expected texts
    are each two literals: the compiler reads a backslash, a `u` and four hexits as a character
    anywhere in the source, also after an escaped backslash, as `doc/x.md` specifies. Issue
    [#729](https://github.com/xtclang/xvm/issues/729) asks whether that should change.
  The rest is on master already (#672, #703 and others), withdrawn, or part of the stacked pull requests.
- [x] The `EvalCompiler` fixes above, filed again on 2026-10-27 as
  [#735](https://github.com/xtclang/xvm/pull/735), once #718 let a host set its own debugger; the
  closed [#721](https://github.com/xtclang/xvm/pull/721) points to it.
  `lagergren/fix-debugger-eval-captures`, rebased onto master, has only its own commit
  (`312846cf9d`). Its `EmbeddingSupportTest` case sets a debugger of its own on the run's connector
  with `InterpreterConnector.setDebugger()`, and compiles `n + 1`, `random.int(1)` and `nope` with
  `EvalCompiler` where the run stopped at `assert:debug`; with master's `EvalCompiler` it fails all
  three ways, checked again on master `bcd8e30d58`. `:javatools:test` passes 460 (42 skipped). The
  console cannot be driven from `manualTests` instead: the run task only inherits Gradle's standard
  input, which is empty in CI.
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
- [ ] Marcus: install the Marketplace pre-release VSIX in a fresh profile on macOS, Windows and
  Linux, and review the Marketplace metadata in `package.json`. CI builds it, as the
  `vscode-marketplace-<sha>` artifact, only while `VSCODE_MARKETPLACE_PUBLISH` is true; locally,
  `:lang:vscode-extension:packageMarketplaceExtension` builds it.
- [x] Merge #711 (2026-10-26, `5130e4211`): `commit.yml` packages and uploads the Marketplace
  pre-release only when the repository variable `VSCODE_MARKETPLACE_PUBLISH` is true, so with it
  unset CI runs as on master before. Its last CI run passed with those steps skipped.
- [ ] Agree how the first upload happens (step 6), set `VSCODE_MARKETPLACE_PUBLISH`, and run the
  first-publish checks in #710.
- [ ] About six months after the first publish: check that `xtclang` shows as verified, and apply
  again if the request was turned down.
