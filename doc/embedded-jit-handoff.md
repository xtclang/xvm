# Preserved embedded JIT implementation

The backup branch [`archive/embedded-jit-ownership`](https://github.com/xtclang/xvm/tree/archive/embedded-jit-ownership)
preserves the JIT work removed from
`lagergren/constant-pool-ownership` on 2026-09-24. Its eventual integration target is the branch
named `JIT`. Neither `JIT` nor its remote tracking ref was changed. This branch is retained on
`origin` as a backup, with no pull request. The extracted implementation is commit `7a27437b4`;
[`patches/embedded-jit.patch`](patches/embedded-jit.patch) is the standalone source diff.

## Origin and commit boundaries

| Source | Preserved contribution |
|---|---|
| `c884779d6c14a75db3184fb44fc54b3253b6c9cb` — Enable embedded JIT execution and default manual tests to DIRECT | Core JIT session/backend implementation, reusable Xvm, request containers, consoles/injections, bounded control lifecycle, and five JIT lifecycle tests |
| `d5947a903` — Reuse owned embedding sessions for Gradle DIRECT execution | Required interpreter session/control and Gradle service foundation; not itself part of the JIT restoration delta |
| `d7cf58526` — Extract a host-independent embedding runtime adapter | Later location/interface of the adapter now carrying JIT request dispatch |
| `d04acf31c` — Expose opt-in PERSISTENT execution in the Gradle plugin | Documentation and explicit rejection of JIT in the separate persistent worker; DIRECT remained the experimental embedded JIT path |
| Uncommitted ownership audit based on `99a46e385` | JIT callable conversion destination, generated-name/cache invalidation and regressions; selected-pool clarification in module preparation |
| Later resource audit documentation (`9b51e0f23`, `6fea82130`) | Clarification that interpreter resource cleanup does not establish JIT resource parity |

The original `c884779d6` is mixed: it also fixes interpreter service-context cleanup, nested
repository resolution and file-template behavior, and changes manual-test defaults. Those parts
remain on the interpreter branch. Do not revert or cherry-pick that whole commit to split the work.

This archive has two deliberately separate commits above the removal commit:

1. **Snapshot pending interpreter ownership and listener work for JIT handoff.** This records the
   exact local compiler/interpreter prerequisites used by the extracted changes. It includes the
   unfinished constant-pool audit fixes and the final ErrorListener migration from `lagergren/errs`.
   It is a preservation base, not a submission-sized change; do not port the whole snapshot to `JIT`.
2. **Preserve extracted embedded JIT implementation and handoff.** This restores only the extracted
   JIT machinery, its focused tests and documentation. Review this commit against its parent to see
   the implementation boundary. The source-only restoration is also saved as
   [`patches/embedded-jit.patch`](patches/embedded-jit.patch), relative to that snapshot base.

The removal commit on the interpreter branch is `d8f8b66a9`; the archive snapshot base is
`5aefbed2dd4ec73700a1f3f43cb8be2f56f62907`. The saved patch applies to that base and was
applied, reversed to an empty diff, and reapplied successfully before committing.

The master-targeted branch keeps public embedding signatures as placeholders:
`RunRequest.Backend.JIT`, `EmbeddingSupport.ensureConnector(Backend)` and
`EmbeddingSupport.create(repository, jitBridge)`. Calls selecting JIT throw
`UnsupportedOperationException("Will be implemented separately in the JIT branch")` before
loading modules or starting a runtime. The restoration replaces those guards with the preserved
implementation and replaces their three rejection tests with the JIT execution coverage.

## What the machinery does

### Session and runtime ownership

`EmbeddingSupport` creates a JIT connector and `Xvm` lazily on the first JIT request. A session
reuses native metadata, generated native classes and the resource-only template loader.
Application type systems may be reused while reachable; this is not a permanent application cache.
Each request uses a fresh application container. Compiling through the same API starts neither
execution backend. Closing the session closes outstanding controls before its JIT template loader.

`Xvm` and `NativeTypeSystem` own the template loader, accept an explicit bridge path and close it
with checked `IOException` propagation. Templates must be read and augmented by the JIT loader;
putting unaugmented bridge classes on the application's classpath bypasses required generation.

### Request preparation and invocation

`JitConnector` serializes and reloads a private application definition before linking, so preparing
one request does not mutate the caller's repository module. If linking selects a reusable type
system, entry discovery uses that type system's actual module and constant pool. The added
constructor accepts the session Xvm and a request writer instead of creating another runtime.

`JitControl` validates a single entry method accepting no arguments or `String[]`, and returning
`void` or `Int`. Native resources are initialized before discovery constructs TypeInfo, matching
the launcher order and avoiding incomplete constructor metadata. Invocation selects the generated
ordinary or primitive entry signature as appropriate. `Control.result()` retains the full 64-bit
result; an uncaught Ecstasy or Java execution failure reports diagnostics instead of success.
The existing ATTACHED launcher remains supported by the connector's invocation path.

### Request resources and shutdown

`TerminalConsole` writes to the request's writer; embedded input is explicitly unsupported.
`nMainInjector` receives that console and the request's String/String-array injections. The host
retains ownership of the writer. Request containers and injection maps are not reused.

Each `JitControl` owns its execution thread. `join()` waits for it. `close()` supplies the common
default budget and `close(Duration)` allows an override, interrupts and waits for termination.
If the budget expires, the control continues to report a running worker and the session refuses
reuse. The template loader remains open under a worker that has not stopped. Java cannot safely
force arbitrary generated/native code to stop inside the host JVM.

### Gradle adapter

`IsolatedDirectExecutor` dispatches an explicitly selected JIT request through the same embedding
session; it does not repeatedly invoke Java launchers. The build service owns that session for one
build. JIT xUnit is rejected. PERSISTENT remains interpreter-only. The archive restores the explicit
small-float JIT task's DIRECT selection for experiments; the interpreter branch uses ATTACHED.
No automatic JIT build/check/CI dependency is introduced.

### Additional JIT pool fixes

The source audit found owner-sensitive JIT state in shared constant classes, so these hunks are
preserved here even though their files are outside `javajit`:

- `MethodBody.asFunctionType`: register unchanged functions as well as constructed method-callable
  types into the explicit destination pool. Returning the original function previously ignored it.
- `TypeConstant`: clear a generated JIT name after adoption into a new pool.
- `ParameterizedTypeConstant`: discard the cached callable JIT type when its owner changes.
- `PropertyConstant` and `MethodConstant`: invalidate generated member names after owner changes;
  the method's associated cached type is recomputed too.
- `ConstantOwnershipTest` and `DestinationOwnershipTest`: verify the destination pool, reset cached
  names/callable types, and preserve the original owner's state.

The interpreter branch still has the shared metadata, copied AST/register, native bootstrap and
request-diagnostic ownership work. Removing JIT hunks does not certify the JIT ownership audit as
closed. Port the small JIT resets onto `JIT`'s actual cache/adoption mechanisms.

## Prerequisites and porting

Do not merge this whole branch into `JIT`. Compare the restoration commit with its parent, then
port its hunks onto `JIT`, reconciling newer APIs and code generation. In particular:

- The extraction uses the owned `Control` lifecycle, `RunRequest`, session failure handling and
  the host-independent plugin adapter. Bring compatible foundations first or adapt to `JIT`'s APIs.
- The archive snapshot uses the final non-null ErrorListener contract, including void logging.
  Check listener signatures on the target; do not bring unrelated compiler/LSP work wholesale.
- The pool reset hunks depend on common adoption/reset hooks in the snapshot. Confirm source
  constants stay intact and legitimate root-owned singleton handles remain shared through their
  owning containers before broadening reuse.
- Replace the placeholder rejection tests when enabling this backend. Keep interpreter tests and
  the ATTACHED launcher regression coverage.
- Keep PR 8 separate from manual-test defaults and from the PERSISTENT worker. Update the execution
  documentation after the target implementation passes its own checks.

The full preserved feature notes are in [`jit-embedding.md`](jit-embedding.md). The submission plan
on the interpreter branch marks PR 8 deferred to `JIT`; it is not a dependency of PR 9's rollout.

## Validation and remaining limitations

The pre-extraction combined branch had passed the five JIT lifecycle cases:

- `jitReusesRuntimeWithFreshStaticsArgumentsAndConsoles`
- `jitFailureDoesNotContaminateTheNextRequest`
- `jitInjectionsBelongToEachRequest`
- `jitSessionCloseStopsItsWorkerAndPreservesInterruption`
- `jitFailedCloseDoesNotPretendItsWorkerStopped`

The shutdown cases use explicit latches and known worker states, not elapsed-time assertions.
Focused pool tests inspect adoption without executing generated code. Preservation is checked by
applying and reversing the saved patch against its exact snapshot. Those results do not validate
integration with the newer `JIT` branch. Repeat the focused cases after porting, then explicitly run
the known-working small-float module through both DIRECT JIT and ATTACHED JIT.

The backend is incomplete: allowlisted/skipped methods can emit placeholder bodies returning
ordinary default values. A successful exit code or assertions inside skipped code are not proof
of execution. Do not use the full manual suite as an assumed JIT conformance gate. Dependency
linking, reflection/nested xUnit, asynchronous service completion, concurrent requests, filesystem
and network resources, request-owned code-generation diagnostics and strict executed-placeholder
failures need separate work. Interpreter native-resource cleanup does not cover these JIT paths.

## Extraction verification on the interpreter branch

The current API placeholder tests passed (3 cases, no skips). The Java suite reported 528 cases,
0 failures/errors and 40 pre-existing disabled cases; the plugin suite passed 26 cases and the
selected XDK lifecycle/ownership/signature suites passed 23, all without skips. All 21 sequential
interpreter modules completed. The explicit small-float tasks completed under DIRECT interpreter
and ATTACHED JIT. Spotless passed after correcting five extra blank lines in pending test files.
Repeating the small-float tasks reused the configuration cache and passed. These checks validate
the removal and remaining interpreter tree, including its pending ownership work; they do not
replace porting and testing the preserved backend against `JIT`.
