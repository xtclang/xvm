# XtcEngine POC on master — status and known gaps

Branch: `lagergren/xtc-engine-poc`, based on `origin/master` `82683bcd2`.
Goal: prove a warm, in-process compile+run engine works on master, so a Gradle plugin (and an
LSP server) can stop forking a JVM per invocation.

## What is proven (3 green tests, `org.xvm.api.XtcEngineTest`)

1. **Compile Ecstasy source held in a String** — no files, no CLI.
2. **Run the compiled module in a nested container**, in the same JVM, and await event-driven
   completion.
3. **One warm engine serves repeated compile+run cycles** — the runtime bootstrap is paid once.
4. **Diagnostics stream to the caller's own `ErrorListener`** as they are produced, and a failed
   compile is self-describing (the result always carries diagnostics).

Gate: `./gradlew xdk:installDist` then `./gradlew :javatools:test :javatools_utils:test` — both
green. (Run them SEPARATELY; in one invocation the test's module reads race installDist's writes.)

## What was changed on master (deliberately minimal — 5 files)

| File | Change |
|---|---|
| `MainContainer` | capture the `callLater` future that was being discarded; add `futureResult()` |
| `Container` | add `runModule(String, ObjectHandle...)` returning the completion future |
| `NestedContainer` | add `createForHost(...)`; add a parent injection fallback **only** for host containers (`f_hProvider == null`), else a host-run module gets no `Console` |
| `FileStructure` | **master bug fix** — see below |
| `api/XtcEngine.java` | new: the engine itself (compile pipeline + run + `RunControl` + JFR events) |

Deliberately NOT lifted from the reference branch: pool-publication fencing
(`markRuntimePublished`), the INSTANCE-removal/owner-lazy work, sealing, display purity. None is
needed for the POC.

## Master bug found by the POC (deterministic, with a red proof)

`FileStructure.getErrorListener()` read the ambient `ConstantPool.getCurrentPool()` and
dereferenced it **without a null check**. That thread-local is null on any thread with no pool
bound — i.e. every Java host thread driving the runtime — so the embedding path NPE'd inside a
*diagnostic accessor*:

```
NullPointerException: Cannot invoke "ConstantPool.getErrorListener()" because "poolCurrent" is null
  at FileStructure.getErrorListener(FileStructure.java:1397)
  at TypeConstant.ensureTypeInfo(TypeConstant.java:1659)
  at Container.findModuleMethod(Container.java:252)
```

Fixed here with a null guard (one line). The deeper fix is to pass the pool EXPLICITLY rather than
consult an ambient thread-local — this is a concrete instance of the ambient-ownership hazard.
**Worth filing as a master bug**: the reproduction is simply "call the embedding API from a Java
thread", and `XtcEngineTest` is the red proof.

## Known gaps (to address after review)

1. **A module's `run()` return value does not reach the host.** The completion future yields the
   invocation's return TUPLE, and that tuple comes back EMPTY even for `Int run() { return 42; }`.
   `RunControl.result()` therefore returns empty. Compile, run, and completion all work; only the
   return-value plumbing in `Container.runModule` is unfinished. `result()` already unwraps a
   `TupleHandle` correctly once the value is populated.
2. **`RunControl.kill()` does not force-unwind a running fiber.** It cancels the caller's wait and
   releases the container; real cancellation needs `Container.terminate(ServiceContext)` wired to a
   cooperative check.
3. **`NativeFunctionHandle` binds to `xRTFunction.INSTANCE.f_container`** on master (a process-static
   template instance). Fine for a single native plane; it is the static-INSTANCE ownership hazard,
   and would need attention before multiple planes or heavy concurrency.
4. **No `compile(Path...)` source-tree entry point yet** — only in-memory sources. This is what the
   Gradle plugin's warm-compile phase and an LSP workspace both need next.
5. **No pool-publication fence.** Master has none, so a long-running host doing many compiles/runs
   over one shared pool is exposed to the interning/publication races documented separately. The POC
   does sequential work and does not hit them; a production plugin should be re-checked under
   parallel load.
