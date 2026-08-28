# XtcEngine POC on master — status, and how it relates to the LSP

Branch: `lagergren/xtc-engine-poc` (PR #546), based on `master`.

**Goal:** prove a warm, in-process compile+run engine works on `master`, so tooling stops forking a
JVM and re-bootstrapping the runtime for every operation.

---

## Why this is (essentially) all that was missing for the LSP

The LSP is **already built**, in `lang/`: `lsp-server` (full lsp4j protocol, `publishDiagnostics`,
document services), `dap-server`, `intellij-plugin`, `tree-sitter` grammar, and the VS Code
extension. Language intelligence is served through a pluggable **`Adapter`**:

| Adapter | Backend | Status |
|---|---|---|
| `MockAdapter` | regex | testing/fallback |
| `TreeSitterAdapter` | tree-sitter | **works today** — "syntax-aware (~80% LSP features)" |
| `XdkAdapter` | **XDK compiler** | **STUB** — *"all methods log but return empty results"* |

So the editor half, the protocol half, and the syntactic half are done. The one hole is the
**compiler-backed adapter** — the thing that needs to actually *compile* a buffer and hand back real
diagnostics, and to *run* a module. That is exactly what this POC supplies:

- `compile(...)` over in-memory buffers → structured `Diagnostic`s (severity, **code**, message,
  source, line), streamed to the caller's `ErrorListener` as produced → feeds `publishDiagnostics`.
- `start(...)` → a `RunControl` for running/debugging a module from the editor.

**In short: `lang` owns the LSP; this owns compile-and-run. Together they close the `XdkAdapter`
stub.** What remains beyond that is genuinely semantic (hover, type inference, cross-file
navigation) — the compiler-API surface the adapter would query — plus the small items in
"Known gaps" below.

---

## What is proven — 11 green tests (`org.xvm.api.XtcEngineTest`)

| Test | Proves |
|---|---|
| `compilesAndRunsAModuleHeldInAString` | compile from a `String` + run in one JVM, no CLI |
| `theRunResultReachesTheHost` | the module's `Int run()` value reaches the host, exactly |
| `aVoidRunCompletesWithNoResult` | "returned nothing" ≠ "failed" |
| `aThrowingRunCompletesExceptionally` | the **failure path**: a throwing run surfaces its error |
| `oneWarmEngineServesRepeatedCompileAndRunCycles` | repeated compile→run on ONE warm engine |
| `compilesMultipleModulesInOneRequest` | several modules per request, all runnable |
| `compiledModulesCanBeSyncedToDiskAndReloaded` | `writeTo(dir)` → reload via a plain `DirRepository` |
| `reportsCompileDiagnosticsAndStreamsThemToTheCaller` | diagnostics stream to the caller's sink |
| `theEngineIsUsableThroughTheToolApiContractAlone` | the `ToolApi` contract is sufficient by itself |
| `compilesAModuleFromDisk` | **`compile(Path...)`** - on-disk source compiles and runs identically |
| `aPathThatIsNotAModuleIsADiagnosticNotACrash` | a bad path is a diagnostic, not an exception |

Gate: `./gradlew xdk:installDist`, then `./gradlew :javatools:test :javatools_utils:test` — green.
Run them as **separate invocations**; in one invocation the test's module reads race
`installDist`'s writes.

---

## What changed on master (deliberately minimal)

| File | Change |
|---|---|
| `MainContainer` | capture the `callLater` future that was being **discarded**; expose `futureResult()` |
| `Container` | `runModule(...)` returning the completion future, asking for the return value the method declares |
| `NestedContainer` | `createForHost(...)`; parent injection fallback **only** for host containers |
| `FileStructure` | **master bug fix** — ambient-pool NPE (below) |
| `DirRepository` | **master bug fix** — synchronize the scan cache (also standalone as PR #547) |
| `api/ToolApi.java` | new — the named CONTRACT a tool embeds |
| `api/XtcEngine.java` | new — the engine; one implementation of `ToolApi` |

Deliberately **not** lifted from the reference branch: pool-publication fencing, INSTANCE removal,
sealing, display purity. None is needed here.

---

## Master bugs found and fixed on the way

1. **`FileStructure.getErrorListener()` NPE'd on the embedding path.** It read the ambient
   `ConstantPool.getCurrentPool()` and dereferenced it with no null check; that thread-local is null
   on any thread with no pool bound — i.e. every Java host thread — so the API died inside a
   *diagnostic accessor*. Null-guarded here; the real fix is passing the pool explicitly.
2. **`DirRepository`'s scan cache was not thread-safe** — unsynchronized `HashMap`/`TreeMap` rebuilt
   with `clear()`+`put()`, plus a live `keySet()` view handed to callers. Proven red with a
   `ConcurrentModificationException`. Submitted standalone as **PR #547**.
3. **A module's `run()` return value never reached the host** — two separate defects:
   `callLater` hardcodes `cReturns = 0` (so the future completed with an empty tuple), and the
   native instantiate-and-run op ignored the caller-designated return slot in favour of `A_STACK`.
   Both fixed, both covered by tests.

---

## Modes: what works today

| Mode | Status | Notes |
|---|---|---|
| **Sequential compile** | ✅ proven | multiple modules per request too |
| **Sequential run** | ✅ proven | results and failures both surface |
| **Interleaved compile+run, one warm engine** | ✅ proven | the actual point of the API |
| **Parallel compile** | ⚠️ unblocked, untested | both known blockers now closed: `DirRepository` here/#547, and the static compiler counters already on master via #538 |
| **Parallel run** | ❌ | needs the shared-pool publication work; the deep one |

---

## Known gaps

1. ~~No `compile(Path...)` source-tree entry point.~~ **DONE.** `ModuleInfo` walks a module file or
   directory into the same `TypeCompositionStatement` the in-memory path produces, so both entry
   points now share ONE pipeline - the load-bearing stage order exists in a single place. A path that
   is not a module produces a diagnostic rather than an exception (`ModuleInfo` signals bad input by
   throwing `IllegalArgumentException`; an LSP must not die because someone opened the wrong
   directory). `compile(...)` propagates the `IOException` that `ModuleRepository.storeModule`
   declares, rather than wrapping it in an unchecked exception.
2. **Diagnostics carry `line` but no column/end position.** LSP wants a `Range` to underline. Needs
   checking whether `ErrorInfo` carries start/end offsets.
3. **No incremental or cancellable compile.** An LSP recompiles per keystroke; today each call is a
   whole-module compile. `RunControl.kill()` likewise cancels the caller's wait but does not unwind
   a running fiber.
4. **`NativeFunctionHandle` binds to `xRTFunction.INSTANCE.f_container`** on master (a process-static
   template instance) — fine for a single native plane.
5. **No pool-publication fence.** Sequential work is fine; the shared pool grows slowly
   (~1–2 constants per distinct-typed run, measured separately), which matters for an all-day daemon
   more than for a build invocation.
