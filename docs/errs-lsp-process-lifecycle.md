# LSP process accumulation: diagnosis and fix

Gene reported several `xtc-lsp-server.jar` JVMs still running after IntelliJ quit, with parent
PID 1. That is unintended. An operating-system child does not automatically terminate with
its parent: the IDE must stop it, or the server must notice disconnection and exit.

The investigation found two reproducible lifecycle failures, plus an older `master` exit
handler defect. These are independent of the compiler embedding and AST work. We cannot
identify Gene's exact sequence of IDE restarts or cancellations from the process listing alone.

## Server transport closure did not release resources

`XtcLanguageServerLauncher.launchStdio()` waited for LSP4J's listener, then returned without
closing the adapter or its executor. EOF means there can be no further shutdown/exit message;
waiting for one at that point leaves resources alive.

The shipped Tree-sitter adapter owns a `WorkspaceIndexer` with a fixed pool of non-daemon
threads. After indexing even one file, those workers remain indefinitely. A real packaged
Tree-sitter JVM survived closing its stdin for more than 75 seconds. Its thread dump showed
`DestroyJavaVM` waiting while `pool-1-thread-1` and `pool-1-thread-2` waited in
`LinkedBlockingQueue.take()`. The workspace scan had already completed. This reproduces
the mechanism needed for a process to survive the IDE's death; it is not compiler work
continuing in the background.

LSP4J's default cached dispatcher also has non-daemon threads. With compiler/mock mode,
that alone delays exit after EOF, even without the Tree-sitter indexer.

The fix:

- The launcher owns its dispatch executor and shuts it down in `finally`.
- The server implements idempotent `close()`, used by protocol shutdown, exit and transport
  termination. Closing does not falsely record a successful LSP shutdown request.
- The production entry point exits after transport termination: status 0 after a shutdown
  request, otherwise status 1. An explicit exit also terminates if adapter cleanup throws.
- Cleanup closes the document service before the adapter, and still attempts adapter cleanup
  if document-service cleanup fails. Repeated cleanup does not double-close native parsers.

`launchStdio()` itself remains usable in-process: it releases resources without terminating
the host JVM. Only the production entry point supplies the process-exit callback.

## IntelliJ cancellation could precede process startup

LSP4IJ creates a provider on a pooled thread and publishes it before calling `start()`.
Cancellation can call `stop()` during that interval. In its OS process provider, `stop()`
sets a permanent `stopped` flag, but `start()` does not check that flag. A later stop returns
immediately, leaving the newly created process alive.

A direct probe against the installed **LSP4IJ 0.21.0** reproduced this with a real Java child:
`provider.stop(); provider.start(); provider.stop()` left the child alive. The probe explicitly
reaped its child afterward. The same relevant code is present in **0.19.4**, used by local
`origin/master`; that older binary has not separately been executed for this investigation.
See the upstream [0.19.4 provider](https://github.com/redhat-developer/lsp4ij/blob/0.19.4/src/main/java/com/redhat/devtools/lsp4ij/server/OSProcessStreamConnectionProvider.java)
and [wrapper startup/shutdown](https://github.com/redhat-developer/lsp4ij/blob/0.19.4/src/main/java/com/redhat/devtools/lsp4ij/LanguageServerWrapper.java).

The XTC provider now uses a small, synchronized `ConnectionLifetime`: a stopped provider can
never start, repeated starts cannot replace its process handle, stop waits for an in-flight
start, and failed startup releases a partially created process. LSP4IJ creates a new provider
for a restart, so making one provider's stop final preserves restart behavior. Startup also
rejects an already disposed project. No process registry, recurring polling or broad process
kill is involved.

The old provider comment claimed duplicate processes were harmless and linked to
[LSP4IJ #888](https://github.com/redhat-developer/lsp4ij/issues/888). That issue concerns an
IDE freeze, not this lifecycle race. The misleading comment has been removed.

## Why VS Code can appear unaffected

The locally installed `vscode-languageclient` 10.1.1 has an additional fallback in
`LanguageClient.shutdown()` / `checkProcessDied()`: after shutdown it waits two seconds,
checks whether its non-detached child still exists and terminates it. XTC's extension delegates
deactivation to that client's stop method. This can conceal the shared server's failure to
exit by itself, and is consistent with the reported difference between the editors. It does
not establish that every VS Code crash/disconnection path is immune; EOF cleanup belongs in
the server regardless of which client happens to reap it.

## Existing master defect and extraction boundary

The inspected local `origin/master` is **4a1eae6f7**. Its `XtcLanguageServer.exit()` only logs;
it does not terminate the JVM. The `errs` branch had already fixed explicit protocol exit in
**ddc0b063d**, but transport EOF still leaked resources. Do not cherry-pick that large earlier
commit wholesale just to obtain its exit behavior.

The standalone master extraction follows these boundaries:

1. Carry the provider lifetime guard and its tests, without the unrelated LSP4IJ/IDE upgrade.
2. Carry launcher executor ownership, EOF cleanup and production process exit.
3. Add the small shutdown-status/exit-callback behavior missing on master and idempotent
   adapter cleanup. Master's server lacks the compiler-settings holder and document-service
   lifecycle added on `errs`; adapt those few lines to master's resource ownership.
4. Carry the transport and child-JVM regressions. Master has no `compilerStdioTest` task;
   wire its normal `test` task to `fatJar` and run the shipped Tree-sitter and mock backends.
   Compiler coverage remains appropriate on `errs`.

No embedding API, AST, semantic-model, rename-proof, native playbook or dependency upgrade
belongs in this extraction. The branch below is published in
[PR #653](https://github.com/xtclang/xvm/pull/653), with review requested from `ggleyzer`.
The provider workaround and regressions are isolated in **8e976f868**; server cleanup, process
exit and regressions are in **eefc1b8e6**. See the integration plan for the verified commit map.

### Published master branch

`lagergren/fix-lsp-process-lifecycle` contains one commit, **cf54e2a19**, based on freshly fetched
`origin/master` **ce3ab1d81**. Its separate worktree is `build/lsp-process-lifecycle`; the main
checkout remains on `lagergren/errs`. PR #653 targets `master` and contains only this fix.

The diff is **nine files, +476/−24 lines**: four production files (+91/−24), four regression
files (+377), and eight lines of normal Gradle test wiring. It keeps master's IntelliJ 2026.1
and LSP4IJ 0.19.4. Master uses final atomic holders for close/shutdown state because it lacks
the compiler-settings lifecycle holder from `errs`; it needs no document-service cleanup hook.

Independent validation passed **426 LSP tests** with three existing skips and **29 IntelliJ
tests** with zero skips, including all **20 new lifecycle regressions**. The ten child-JVM
cases cover Tree-sitter and mock; the other ten cover transport cleanup and provider races.
Root Spotless and Kotlin checks pass. A forced 14-test server lifecycle rerun also passes
with configuration-cache reuse. No native IDE acceptance run is claimed for this extraction.

## Validation

Before the fix, the new child-JVM suite ran **15 cases with 9 failures**: every EOF variant
failed across Tree-sitter, compiler and mock. The six explicit-exit cases already passed on
`errs`. After the fix, **15/15 pass with zero skips**:

- EOF before initialization;
- EOF after initialization, workspace indexing and opening a document;
- successful shutdown followed by EOF without an exit notification;
- exit without shutdown;
- shutdown followed by exit.

The process tests assert exit before fallback cleanup, so forcibly killing a leaked child
cannot make a test pass. Backend selection is overridden through a resource directory ahead
of the packaged JAR; a Tree-sitter fallback to mock is explicitly rejected.

The focused server run passed **38 tests, zero skips**, including EOF/read failure cleanup,
one-time native cleanup and exit despite cleanup failure. Provider tests exercise cancellation
before startup, concurrent starts, stop during startup and partial-start failure. All **32 IntelliJ
unit tests pass, zero skips**, including real OS-provider process cancellation and reaping. Installed
IDE validation of restart/project-close behavior remains a separate acceptance check; these
results do not claim the entire native playbook has passed.

Existing orphaned JVMs are not adopted by a newly installed plugin. The fix prevents new
instances following these paths; already running old servers still require explicit cleanup.
