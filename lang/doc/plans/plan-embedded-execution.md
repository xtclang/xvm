# Embedded compilation, Run and future debugging

Status: accepted architecture and delivery plan, 2026-09-28. A first delivery of embedded build and
Run in both IDEs landed on `lagergren/errs` on 2026-10-15 ([below](#first-delivery-2026-10-15)); it
covers parts of R1–R5. Debugging (R6/R7) and the release gate (R8) are not implemented. The audit in
the next section describes the code before that delivery.

## First delivery (2026-10-15)

Embedded mode is the IDE counterpart of building and running with Gradle, like IntelliJ's *Build and
run using* choice. Each IDE chooses per folder or project: automatic (Gradle exactly when the root
has a Gradle build, embedded otherwise), embedded, or Gradle. Decisions taken for this delivery:

| Question | Decision |
|----------|----------|
| Default mode | Embedded, also with a Gradle build, whose own settings embedded builds and runs say do not apply (2026-10-16; previously automatic, which remains selectable) |
| Unsaved buffers | Builds and runs compile the editor's text, saved or not, and neither client saves files (R2, 2026-10-19; until then both saved first). VS Code saves before the task as `task.saveBeforeRun` says, and IntelliJ before a run, not a build |
| Run host | Execution worker JVMs started by the language server, one per running program and up to four at once; one idle worker is kept for the next run (2026-10-16; previously one worker running one program at a time) |
| Debugging | Not now. VS Code debug launches run without the debugger; IntelliJ keeps DAP disabled |
| Compiler queue | Builds reuse the server's serialized compile queue, not a second compiler |

| Area | Delivered |
|------|-----------|
| Protocol | `xtc/build`, `xtc/run`, `xtc/stopRun`, `xtc/runnableTargets`; `xtc/runOutput` and `xtc/runExited` notifications for a client-chosen run ID ([server README](../../lsp-server/README.md#embedded-build-and-run)) |
| Build | [XdkBuild](../../lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkBuild.kt) compiles the module's dependency order into `build/xtc/embedded/`, with per-module outcomes, skipped consumers of failed dependencies and attributable diagnostics |
| Runnable targets | Found on the compiler worker from the module's type, including methods its annotations contribute, or, for a module that does not compile, from the `run` it declares: a `run` method with at most one required parameter. Run lenses appear only on such modules; no user code runs |
| Execution | [RunWorkerProcess](../../lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt) starts [RunWorker](../../lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt) lazily over JSON-lines stdio, reuses it and replaces it after death; stray worker output becomes events, never protocol frames; a worker must report the server's protocol and XDK before it receives anything, its output is batched and bounded, and it reports its heap and threads after each run |
| Embedding API | `EmbeddingSupport.runMethod` selects an entry method with the CLI runner's rule and passes exact arguments; [InterpreterControl](../../../javatools/src/main/java/org/xvm/api/InterpreterControl.java) no longer starts the runner's HTTP host; [runner.x](../../../lib_runner/src/main/x/runner.x) tasks take a method and arguments |
| VS Code | `xtc.build.mode`; Build/Rebuild (`Cmd`/`Ctrl+F9`), Run lens, editor title Run, `Ctrl+Shift+R`/`Ctrl+Shift+F10`, tasks and `F5` follow it; runs stream into a task terminal and closing it stops the run ([README](../../vscode-extension/README.md#building-and-running)) |
| IntelliJ | Build, Execution, Deployment › Ecstasy; a project task runner for Build/Rebuild with Build tool window reports; run configurations with *Run with*; a process handler giving native console, Stop and Rerun; an action for the Run lens command ([README](../../intellij-plugin/README.md#running-your-application)) |

Known limits: up to four programs at a time per server (a fifth run reports that four programs
are already running); IntelliJ Build Module builds
every module; `.x` files have no IntelliJ gutter Run icon because there is no PSI parser; older
IntelliJ configurations store a module name from a `\w+` regex, so qualified module names need the
new *Module source* field. The commits and validation are recorded in the
[integration plan](../../../docs/errs-integration-plan.md#embedded-build-and-run-r2r5-2026-10-15).

## What the code already does

| Area | Evidence | Consequence |
|---|---|---|
| Compiler Run lens | [XdkAdapter](../../lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt) creates `xtc.runModule` for module symbols | A module declaration alone currently qualifies; this is not proof of a runnable entry point or a successful build |
| IntelliJ Run | [producer](../../intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationProducer.kt) uses a module-name regex; [configuration](../../intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt) launches Gradle or `xtc` | Run shortcuts/configurations bypass the compiler session and unsaved artifact snapshot; the regex does not preserve qualified module names |
| VS Code Run | [command](../../vscode-extension/src/commands.ts) ignores the supplied source URI; [task provider](../../vscode-extension/src/task-provider.ts) builds shell command strings | Both clients need a common launch request, including source/workspace identity and argument arrays |
| Embedding execution | [EmbeddingSupport](../../../javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) caches a connector and returns a `Control` per run | Reusable VM hosting is already the intended foundation; configuration is singleton and cannot be changed after startup |
| Interpreter control | [InterpreterControl](../../../javatools/src/main/java/org/xvm/api/InterpreterControl.java) loads the runner once, copies an artifact into its native container and invokes task registration/start | Compiler ASTs need not be retained for execution; startup currently also invokes runner `run`, starting its HTTP service |
| Runner | [runner.x](../../../lib_runner/src/main/x/runner.x) maintains task IDs and creates a fresh lightweight child container per task | Reuse the runtime/runner, while giving each normal rerun fresh application state. The Java embedding path should call task methods directly without starting HTTP |
| Current execution limits | Runner invokes `container.invoke("run", ())`; custom injections are rejected; the [external console](../../../javatools/src/main/java/org/xvm/runtime/template/_native/io/xExternalConsole.java) reads host-supplied input since 2026-10-16 | Entry-point arguments, input, output channels, asynchronous completion/stop and bounded shutdown need an explicit host API |
| Repeat-run regressions | [EmbeddingSupportTest](../../../javatools/src/test/java/org/xvm/api/EmbeddingSupportTest.java) runs modules with a console, by name more than once, with a supplied root directory and with a failure, and closes a suspended run, each within a time limit; it also runs changed modules and dependencies, closes runs as they finish, twice and from two threads at once, and bounds what repeated runs add to the runtime's constant pools (R1) | These checks came from the removed main-based `LspTest` and now run with `:javatools:test`; its run timings and native pool sizes were only printed, and are now [measured and asserted](#additive-delivery-and-acceptance) |
| JIT execution | [JitControl](../../../javatools/src/main/java/org/xvm/api/JitControl.java) is unsupported; embedding selects the interpreter | First delivery targets interpreter execution; do not advertise JIT parity |
| DAP | [XtcDebugServer](../../dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServer.kt) is a placeholder: breakpoints are reported unverified with the reason, launch and attach fail with it, and there are no threads | A transport stub is not debugging. Breakpoints stay unverified until a runtime bridge confirms them |
| Interpreter debugger | [Debugger](../../../javatools/src/main/java/org/xvm/runtime/Debugger.java) has frame/PC hooks; [ServiceContext](../../../javatools/src/main/java/org/xvm/runtime/ServiceContext.java) returns the global `DebugConsole.INSTANCE` | A real DAP bridge needs session-scoped debugger ownership and suspension/control APIs; parsing console output is not a suitable integration |

## Recommended ownership and process model

Use one compilation service contract and one execution service contract, shared by both IDEs and
the future DAP adapter. The service contract is independent of the transport and IDE UI.

```mermaid
flowchart LR
    IDE[IDE Run button / shortcut / configuration] --> LSP[LSP workspace coordinator]
    LSP --> COMP[Serialized compiler worker]
    COMP --> BUILD[Immutable build artifact and source revision]
    BUILD --> EXEC[Supervised persistent execution worker]
    EXEC --> VM[EmbeddingSupport / lib_runner]
    VM --> APP[Fresh application container per run]
    DAP[Future DAP adapter] --> EXEC
    EXEC --> EVENTS[Run output, state and completion events]
    EVENTS --> IDE
```

Prefer a separate, lazily started execution-worker JVM per workspace/XDK configuration. Reuse it
across runs; do not launch a JVM, CLI or Gradle task for each ordinary editor Run. The coordinator
owns its process and connection. First implementation permits one active run per worker, with
explicit stop/restart behavior. Add concurrent sessions only after isolation and scheduling tests.

This is an architectural recommendation, not a claim that the existing runtime is unsafe in all
in-process hosts. An in-process prototype can prove the common API. For the shipped IDE, a separate
worker permits recovery from an unresponsive application, excessive native-pool growth or debugger
failure without losing compiler diagnostics. A lightweight child container is not an OS security
boundary. Existing compiler serialization remains intact; application execution never blocks the
compiler queue, JSON-RPC thread or IDE UI thread.

The worker uses the same pinned, full bundled XDK and version handshake as the compiler. A changed
XDK/injector/runtime configuration requires a new worker generation. Normal rerun creates a new
child container and uses the latest successful requested build. Running the exact previous artifact
is a separate explicit action. Hot reload into a live container is not implied by rerun.

Use a framed private control connection with separate application-output events. Do not let user
console output enter LSP/DAP framing. Stdio pipes are sufficient for an owned child; a local socket
for additional DAP clients needs scoped credentials and lifecycle ownership. No runner HTTP listener
is required for an IDE session. EOF, project close and parent death terminate owned work; cancellation
of one request must not accidentally abandon a process or terminate an unrelated run.

Keep the first coordinator implementation as a protocol-neutral Kotlin package in `lang`. A worker
entry point can be packaged with existing artifacts. Extract a small shared library only when the
LSP and DAP have actual shared code to consume; avoid creating several new Gradle modules now.
Java runtime/compiler contracts stay in javatools, without introducing Kotlin there.

## Build and launch contract

Names below describe proposed API roles, not existing public classes.

- `BuildRequest`: workspace/module identity, captured source versions, dependency/XDK revisions,
  compile options and cancellation. Run must compile the exact snapshot selected by the user,
  including unsaved buffers according to one documented policy in both clients.
- `BuildResult`: attributable diagnostics, success/failure/cancellation, source manifest and immutable
  emitted artifact/dependency closure. A last successful artifact is never silently substituted when
  the requested sources fail. A valid cache hit satisfies compile-before-run; a new compilation is
  not required when every relevant input is unchanged.
- `RunnableTarget`: compiler-proven module/entry-point identity, declaration location, accepted
  argument shape and build revision. XTC's default convention is `run`, not an assumed Java `main`.
  Align compiler inspection with the runtime's actual callable/default-argument rules. An arbitrary
  `.x` member file is not an independent program; resolve its owning module. Unsupported entry
  points produce a clear reason. Revalidate the target when launch begins.
- `RunRequest`: target/build handle, argument array, working-directory/resource policy, console/input
  channels, run mode and restart policy. Avoid nullable positional option lists and comma/shell parsing.
- `RunSession`: immutable ID and worker generation, observable state, asynchronous completion,
  result/exception, ordered output and stop/close operations. Distinguish queued, building, starting,
  running, stopping, exited, failed and cancelled. Publish exactly one terminal outcome. `Control`
  compatibility can be retained behind the richer asynchronous facade.

Use the existing [XdkDependency](../../lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDependency.kt)
artifact/revision/source-index ownership as the starting point. Extend it deliberately for an entire
build manifest and debugger source mapping. No compiler `ModuleStructure`, AST, TypeInfo, constant
pool or mutable repository crosses the worker boundary. Emit/copy bytes on the compiler worker;
open fresh runtime-owned structures in the execution worker. Runtime exceptions must be associated
with the launched artifact and source revision, not whatever buffer text happens to be current.

Incremental compilation is a future implementation behind `BuildRequest`, with explicit dependency
invalidation and cache keys. It does not require a different Run API. A current run pins its artifact
closure; editor changes affect the next build, not its running code or breakpoint source mapping.

Library/program entry-point distinctions should govern CodeLens, gutter buttons, context Run and
keyboard Run through the same target query. IntelliJ Run configurations retain user arguments and
working-directory choices; its Run console and Stop/Rerun actions wrap `RunSession`. Use Community
platform APIs and LSP4IJ only. VS Code uses the same requests and renders output through its task or
debug console. Neither client reconstructs the compiler invocation. Keep an explicitly selected
Gradle build/task path for generated inputs and user build automation; it is not the normal embedded
Run implementation. Build-model integration must supply those inputs before compilation.

First prove the service with embedded Run. A future DAP `launch` (including no-debug launch) uses
the same BuildResult and RunSession, then attaches runtime controls. The IDE transport may eventually
route both Run and Debug through DAP; that should not change compilation or execution ownership.

## Debugging boundary

Introduce a runtime debugger implementation behind the existing `Debugger` interface with explicit
session/container ownership. Define how fibers/services map to DAP threads, where they suspend,
which runtime thread may inspect frames, and how immutable stack/variable snapshots are obtained.
Support source/PC maps bound to artifact revisions; send unverified breakpoint reasons when a
location cannot be installed. Breakpoints must be installed before an initially suspended launch
resumes. Invalidate variable/frame handles on resume, rerun and worker replacement.

Deliver launch, termination and output before stepping; then implement breakpoints, exception stops,
stack traces, scopes, variables and stepping. Evaluation may execute user code: define side effects,
cancellation and timeouts instead of treating it as ordinary hover. Runtime values, heap objects and
thread state stay separate from immutable LSP semantic snapshots. Debug inline values (L79) follow
this bridge; they cannot be inferred from compiler type hints.

## Implications for the current compiler branch

1. Preserve captured source/dependency revisions, compilation cancellation, structured diagnostics
   and emitted artifacts as independently consumable results. Rename and incremental build must
   invalidate target/build handles when module identity, source paths or dependencies change.
2. Keep compile-only operations independent of `ensureConnector()`/runtime initialization. Do not
   use the runtime constant pool as a compiler snapshot cache or share mutable structures with it.
3. Expose passive compiler entry-point/source facts only where needed. Runtime sessions, queues,
   resource providers and debugger objects do not belong in AST nodes.
4. Treat L73 server commands as a thin route to the service; do not build a second compiler in an
   execute-command handler. L75 source content/revisions and L81 cancellation/progress apply to Run
   and Debug as well as editor queries.
5. The current L62 work stays compiler-focused. Its constructor-call provenance is relevant later
   to source mapping, but adds no execution lifecycle or runtime initialization. Do not enlarge
   this PR to deliver the runner or DAP implementation without a separate decision.

## Additive delivery and acceptance

- [x] **R1 — Runtime API baseline and repeat-run tests.** Convert the standalone probe's useful
  assertions into bounded tests: same-name changed artifacts, different dependencies, success,
  exceptions, exit results, immediate stop, stop/finish races, close twice, repeated runs and pool
  growth. Separate unsupported injections/JIT/input from verified interpreter support. Measure cold
  and warm latency. Exercise no HTTP listener/port collision in the embedding path.
  *Partial (2026-10-15):* worker regressions cover success, exit codes, exceptions, named methods,
  missing methods, arguments with spaces and Unicode, stop then reuse, and no HTTP listener. Changed
  artifacts, races, double close, pool growth and latency remain.
  *Completed 2026-10-19:* `EmbeddingSupportTest` covers the rest. A module changed under the same
  name runs its new code, and an unchanged module runs with the current version of its dependency.
  Closing a run as it ends on its own, closing it twice and closing it from two threads at once end
  it once and leave no console registered. The tests found two bugs in `InterpreterControl.close()`.
  A close as the run ended on its own failed with the runner's "Task N has terminated": the run could
  end after the close saw it running and before the runner got the kill, which is what Stop does to a
  program waiting for input, and the worker logged the failure. And a second close returned at once
  while the run was still going, which RunWorker's Stop and its run thread could do. Now a kill that
  finds the task gone, which the runner's `taskStatus` confirms, is not a failure, and a second close
  returns when the first is done. Two constant pools outlive a run: the native container's, which
  every run shares, and the runner's. After a module's first run, running it again, changed under
  the same name, or running other modules whose type arguments are classes of their own adds nothing
  to either. A type argument from the core library adds the types made with it to the native pool
  once (14 constants for two such types), and the test allows at most 100. Additional injections and
  custom injectors are refused with `UnsupportedOperationException`. JIT runs are not supported
  (`useJit()` is false), and console input is verified (X287).
  Latency on the development Mac, not asserted since it depends on the machine: the first run takes
  about 1.7 s, most of it starting the interpreter and the runner, and a warm run of a trivial module
  about 0.15 s. Compiling one takes about 0.75 s the first time and 70–130 ms after that. Both rely
  on the configured XDK repository, which keeps the modules it has loaded. A caller that also
  passes a new XDK repository as input loads them again, and a warm run then takes about 0.8 s and
  a compile 0.5 s. The worker passes only its build output, and `EmbeddingSupportTest` now does
  too, which took the class from 46 s to 12 s.
- [x] **R2 — Build results and runnable targets.** Reuse workspace snapshots/artifacts; prove entry
  eligibility and module ownership, arguments/defaults, unsaved compile-before-run, compile failure,
  stale target rejection, renamed roots and correct source revisions. Do not execute user code in
  target discovery. These passive compiler APIs can be extracted before runtime UI integration.
  *Partial (2026-10-15):* builds report per-module outcomes and diagnostics and skip consumers of
  failed dependencies; runnable targets come from registered module structures without running
  code.
  *Snapshots and reuse (2026-10-16):* a build compiles the text captured at the request, through
  editor analysis's module-closure compile; modules whose sources and dependency revisions are
  unchanged reuse the earlier artifact (`reused`), including one the editor analysis compiled; every
  artifact has a SHA-256 revision; `manifest.json` records compiled source hashes and dependency
  revisions; a failed module's old artifact is removed. A run of a module that no longer declares the
  entry method is refused by the runner's entry check before user code runs. Still open: arguments
  and defaults proven against the entry method before the build, renamed-root handling beyond the
  current graph, and dedicated stale-target tests.
  *Unsaved editor text (2026-10-19):* neither client saves before a build or run any more; the build
  compiles the text the server holds. IntelliJ commits its documents and waits for LSP4IJ's pending
  changes before it sends the request, since a request can reach the server before an edit's
  `didChange` (LSP4IJ #1729), which a save never covered. The editors still save on their own:
  VS Code before a task as `task.saveBeforeRun` says (`always` by default), and IntelliJ's default
  runner before every run. Shared X281 and X289 check it in both editors, VS Code with
  `task.saveBeforeRun` set to `never`: the build fails on the unsaved broken library and leaves it
  unsaved, and the third run runs the unsaved edit.
  *Stale targets (2026-10-19):* a run of a method that its module no longer declares builds the
  module and is refused by the runner's entry check, and the earlier artifact's method does not run;
  a run of a module renamed in its source is refused before a build as an unknown source module
  (`EmbeddedExecutionTest`). The renamed module's earlier artifact stays in `build/xtc/embedded/lib`,
  but nothing runs it: a build writes or removes only the artifacts of the modules it builds.
  *Arguments and defaults (decided 2026-10-19):* they are proven on the module the run's build has
  just produced, by the runner's entry check before any user code runs, not before the build: only
  the built module is certain to be the one that runs, and an earlier check could use only editor
  analysis that may be older. The check applies the command-line runner's rule
  (`EmbeddingSupport.entryMethod` and `passesArguments`; `EmbeddingSupportTest` for entry selection,
  optional argument arrays and defaults, exact arguments and missing methods; `EmbeddedExecutionTest`
  for arguments, a named method, a missing method and a method the module no longer declares).
  *Renamed roots (2026-10-19):* discovery already followed a root file renamed or moved on disk, and
  builds find modules in the current graph, but a run target saved with the old root URI (a VS Code
  task or Rerun, an IntelliJ configuration's *Module source*) failed as an unknown source module,
  even with the module's name unchanged, since both clients sent only the URI. `xtc/run` now takes
  the name as `moduleName` beside the URI, and the server uses it when the URI no longer names a
  module; a module renamed in its source is still found by its URI. IntelliJ's Run lens also brings
  a reused configuration's name and source up to date. `EmbeddedExecutionTest` renames a discovered
  root on disk and runs it by the old URI with its name and by the new URI, and refuses the old URI
  alone; shared X291 runs a saved target again after the rename in both editors. Not covered:
  renaming a root whose editor is still open (that buffer stays a source under the old path, and
  builds report a duplicate module until it is closed); an IDE move of a discovered root into
  another directory, which the rename work refuses; and the fallbacks for the name and dependencies
  of a renamed root whose header does not parse, which are keyed by the old path. Closed 2026-10-19.
- [ ] **R3 — Asynchronous embedded execution.** Extend Java/lib_runner entry selection and argument
  passing, session completion, stop, output/input and resource ownership. Preserve caller-owned
  directories and dispose only session-owned temporary resources. Structured exception/exit events
  replace textual parsing. No per-run CLI or new VM.
  *Partial (2026-10-15):* entry selection, exact arguments, completion, stop and ordered output work
  through lib_runner tasks in one runtime, with structured exit events.
  *Console input (2026-10-16):* `Console.readLine()` reads lines both clients send with
  `xtc/runInput`; input typed during the build is held until the program starts, and the end of
  input or a stop gives a waiting `readLine` an empty line (X287). Every part listed above now
  works. The box stays open until the runtime part has a `javatools` test of its own for extraction;
  JIT runs remain unsupported.
- [x] **R4 — Persistent worker supervision.** Add configuration/generation handshake, private IPC,
  startup/stop deadlines, bounded output/backpressure, memory measurement and safe idle recycling.
  Verify worker death during a run, restart after crash, parent death, IDE close, XDK change and
  no leaked task/console/process. Bound retained artifacts and native-pool growth independently.
  *Partial (2026-10-15):* a lazily started worker over private JSON-lines stdio, reused across runs,
  replaced after death and shut down with the server; it exits when the server's pipe closes.
  *2026-10-16:* one worker per running program, up to four at once, so programs run side by side;
  startup and stop deadlines (a worker not ready within 60 s, or a program still running 10 s after
  Stop, is ended and its run says why, without touching other runs); one idle worker is kept for the
  next run and exits after 10 minutes idle, any other when its run ends (`RunWorkerSupervisionTest`
  with a stand-in worker, and two real programs at once in `EmbeddedExecutionTest`).
  *2026-10-18:* a worker receives nothing until its `ready` reports the protocol version and the
  bundled XDK's fingerprint the server expects; one that reports another, or none (a first-protocol
  worker), is ended and its run says to restart the language server. The server's configuration is
  fixed for its lifetime, so a new generation is a new server; the check catches a worker started
  from replaced files. Output is batched (sent 20 ms after it is printed, or once 16,384 characters
  wait) and bounded (no more waits), and the server forwards it synchronously, so a program printing
  faster than the client reads waits in its print (`OutputBatchesTest`; 5,000 prints arrive whole
  and in order in far fewer events in `EmbeddedExecutionTest`); VS Code keeps the last million
  characters of a run's text. After each run the worker reports its heap in use after a collection
  and its threads; the server logs them and does not reuse a worker keeping more than 512 MB or 200
  threads (a real worker keeps about 137 MB and 21 threads after a run). Ending a worker no longer
  leaves its runs open when its closed output stream fails the server's read.
  *Completed 2026-10-18:* after each run the worker also reports the runtime's own pool threads and
  the consoles and runs it still holds for ended programs, counted before the run's exit so the next
  run is not among them. Each is bounded on its own: a worker that still holds a console or run,
  whose runtime pool grew, or whose heap after a run grew more than 128 MB since its first run (what
  runs retain, such as artifacts) is not used again, beside the 512 MB and 200-thread totals
  (`RunWorkerSupervisionTest`). Over 25 mixed runs (exit codes, arguments, an exception, console
  input, Stop) one real worker serves every run, holds no console or run afterwards, keeps its
  pool threads, and its heap after a run (about 137 MB) grows by less than 1 MB. A killed language server
  process takes its worker with it, and an editor closing the server's input ends its runs and
  workers (`EmbeddedExecutionTest`). Runner tasks are not counted on their own; a leak of them would
  show in that heap. The R8 soak extends this across platforms and debug sessions.
  *2026-10-19:* the worker's own output timer started on a program's first print, from one of the
  runtime's pool threads, and joined their thread group, which is what the worker counts as the pool.
  The first program that printed after a silent first run thus looked like pool growth and retired a
  healthy worker, so the next run waited about 2 s for a new one. The timer thread has a group of its
  own (`OutputBatchesTest`), and the pool count is the runtime's pool alone: one thread per core,
  started by the first run.
- [x] **R5 — Both IDE Run integrations.** Route lens/gutter/context/shortcut/configuration actions
  through the same target and session API. Test arguments with spaces/Unicode, unsaved sources,
  missing entry points, compile errors, output, Stop, Rerun and repeated runs in shared scenarios.
  Assert worker reuse and distinct child sessions. Verify IntelliJ without Ultimate functionality.
  *Partial (2026-10-15):* both IDEs route lens, context, shortcut, task/configuration and Build actions
  through the shared requests, with Stop and Rerun, using Community and LSP4IJ APIs only. IntelliJ
  unit tests cover mode resolution, configuration migration and the run lifecycle; the VS Code
  headless suite checks command registration and the build mode command. Shared native playbook
  cases X280–X285 cover Run lenses, builds, arguments/exit codes, Stop, Rerun, failed builds and
  debug launches in both editors; X288 switches between the embedded and Gradle modes beside a
  Gradle build (with a stub wrapper, so no Gradle build runs) and X289 runs a module again,
  unchanged and after an unsaved edit. A real Gradle build and repeated-run soak are not driven by
  shared cases.
  *Completed 2026-10-20:* shared X292 runs a module twice whose static service counts the calls made
  to it: each run prints `calls=1,2`, so no run goes on from the program state of the run before it.
  `EmbeddedExecutionTest` checks the same with one worker serving both runs; the editors cannot see
  which worker ran a program, and worker reuse over 25 mixed runs was already asserted there. Shared
  X293 runs, from a run target, a module without a `run` method and one whose `run` requires two
  parameters: both build, and each run is refused with the reason and exit code 1 before user code
  runs. IntelliJ runs the shared cases in IntelliJ IDEA 2026.2.3 with Ultimate features disabled.
  Not covered: `.x` files have no IntelliJ gutter Run icon, since there is no PSI parser (the Run
  lens, context and shortcut actions run them); X288 drives Gradle mode with a stub wrapper, not a
  real Gradle build; repeated-run soak belongs to R8.
- [ ] **R6 — Honest DAP lifecycle.** Remove synthetic verified breakpoints/threads, connect launch,
  no-debug execution, output, completion, disconnect and terminate to the same session service.
  Advertise only functioning requests. DAP facade shutdown must follow an explicit run ownership policy.
  The placeholder half is done: synthetic breakpoints and threads are gone, launch and attach fail with
  the reason, and only the configuration handshake is advertised (`XtcDebugServerTest`). The session
  service connection is not.
- [ ] **R7 — Runtime debugger bridge.** Implement session-scoped hooks, initial suspension, real
  breakpoints/exception stops, fibers/threads, stacks/scopes/variables, stepping and bounded
  evaluation. Test source revisions, stale handles, resume/stop races and multiple service contexts.
- [ ] **R8 — Runtime release gate.** Repeated compile/run/stop/debug cycles and memory/process soak
  on supported platforms; client parity; logs correlating build ID, queue job, artifact revision,
  worker PID/generation and run ID; current playbook/capability docs and independent PR validation.

R1 establishes the baseline; R2 can proceed alongside compiler hardening. R3 precedes R4/R5;
R6 shares the established service, followed by R7. R8 closes production acceptance. Current
compiler-only tests and the LSP playbook do not establish any of these runtime gates.
