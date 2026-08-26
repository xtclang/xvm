# ToolConnector — desired Java API, worked mock-up

**Status: PROPOSAL / illustrative.** The Java below is NOT implemented. It targets the
**master** baseline (no `lagergren/lazy-instance` fixes assumed) and mocks up the
`ToolConnector` (née `ToolSupport`, née `LspSupport`) surface as discussed, so the API
lands with the right shape. Method names are proposals aligned with that discussion; the
point is the *shape and the guarantees*, not the spelling.

This is a **general compile + run embedding surface**, not an LSP-specific one. An LSP is one
consumer; so are a build daemon, a test runner, a REPL, the platform, and any tool that already
holds compiled modules in memory and just wants to run them. Sending source strings is one
convenient input; the currency is **compiled modules in a `ModuleRepository`**, and compiling
from source is only one way to obtain one (§3.7 runs already-compiled modules with no compile
step at all).

Audience: the folks building `ToolConnector` + the `Linker.makeFileTemplate(Byte[])`
addition. Everything here is grounded in a working Java proof-of-concept we built on the
reentrancy branch (`org.xvm.api.XtcEngine`) — see "What we already validated" at the end;
each API feature below was exercised end-to-end, so this is a requirements-by-example
document, not speculation.

---

## 1. The model we're aligning on (and the two anti-patterns)

`ToolConnector` uses the `Connector` to stand up **one long-running Ecstasy host app in
container 0**, which then hosts **any number of nested child containers** below it — the
platform/kernel shape. Each `run()` loads+links the target module and spins up a *fresh,
disposable, parent-mediated* nested container inside that running app.

```
NativeContainer (-1)                                        native plane, shared
   └── container 0 : long-running ToolConnector host app    booted ONCE, drives compile/link
          ├── container 1 : run of module A                 one FRESH child per run(), disposable
          ├── container 2 : run of module B                 sibling, created concurrently
          └── container 3 : run of module C
```

### What runs where (and what is actually shared)

- **Runs are THROWAWAYS — a fresh nested child per call.** Test/preview run 1 executes in
  container 1, run 2 in container 2, run 3 in container 3, … — created fresh, run, and disposed.
  It is **container 1, 2, 3, 4 … — NOT 0, 0, 0, 0**. Container 0 (the host) is the *only* thing
  that persists; reusing one container for successive runs is the leak-and-corrupt anti-pattern
  this model exists to avoid. Parallel runs are **siblings** (1, 2, 3 at once), not co-tenants of
  a single container; each is parent-mediated, so runs never leak into one another.
- **What is reused vs thrown away.** REUSED across runs: the host (container 0) AND the compiled
  code / type-system (the `ModuleRepository`, the pool, native templates) — compiled/loaded once,
  run many times. THROWN AWAY: each run's container. "Warm" therefore means a warm host + warm
  compiled code, never a warm run container. Reusing the *repository* across runs (§3.7) is
  correct and cheap; reusing a *container* across runs is the "0, 0, 0, 0" mistake.
- **Compile/link is not a per-request container.** It is the compiler/linker machinery driven
  by the host app in container 0 (parse → assemble → `makeFileTemplate` → link); it produces
  the module, then a child container is created to run it. Parallel compiles are concurrent
  invocations of that machinery in container 0, not separate app containers.
- **Container isolation is APP-LEVEL, not type-system-level.** A child container has its own
  heap, service/fiber set, injection scope, and module singletons — but every child AND the
  compiler **share** the layer underneath: the `ConstantPool` / type system, the `TypeInfo`
  caches keyed on shared constants, the native templates, and the -1 plane. So two runs in
  containers 1 and 2 concurrently build/read `TypeInfo` on the same shared constants, and two
  compiles concurrently register into shared pools. **Separate containers isolate the
  workloads; they do NOT isolate the shared type-system state** — which is why "run stuff at
  once" is only *safe* once that shared substrate is hardened (§4), not merely because each run
  has its own container.

Two things this is deliberately **NOT**:

- **NOT the plain `Connector` API for running user modules.** `Connector` only creates
  container 0; using it to run each request voids the warranty and crashes. `ToolConnector`
  wraps `Connector` correctly — container 0 becomes the *host*, not the *workload*.
- **NOT sibling main containers / a fresh bootstrap per run.** Relaunching main containers
  in one JVM corrupts shared statics (the reuse gist that crashes). Nested children under
  one warm host app are leak-free by construction.

---

## 2. The desired API surface (proposed)

```java
package org.xvm.api;

// PROPOSED — not implemented. One resident connector per tool process.
public interface ToolConnector extends AutoCloseable {

    // ----- construction ---------------------------------------------------------------
    // Boots the native plane + the container-0 host app ONCE from a single module path.
    // The caller NEVER names turtle (mack) or the native bridge (_native) — the connector
    // resolves the bootstrap modules internally, exactly as it already must to boot at all.
    static ToolConnector open(ModuleRepository libraries);
    static ToolConnector open(Path... modulePath);

    // ----- compile --------------------------------------------------------------------
    // Compile from WHEREVER - an in-memory buffer, an on-disk tree, or precompiled bytes. The
    // API assumes NO local compilation environment; a Path is one convenient source, not the
    // model. Output goes WHEREVER - an in-memory FileTemplate you hold until you sync, or
    // straight to disk. Neither in-memory nor to-disk is privileged; the caller does whatever
    // fits. Multi-module resolves cross references among the submitted set.
    //
    // Granularity is a whole module TODAY, but the shape must NOT preclude METHOD-LEVEL compile
    // later - an external incremental driver (e.g. the Kotlin incremental compiler) will decide
    // what to recompile and re-invoke this API at whatever granularity it wants. The one hard
    // invariant, across every source/sink/granularity, is that compiling MUST NOT mutate or
    // corrupt shared/global runtime state (see §4). That single property is what makes repeated,
    // concurrent, and (future) incremental invocation POSSIBLE at all - see "Capabilities to keep
    // open" below.
    //
    // DESIGN: the API never accepts a mutable collection. Inputs are immutable value records
    // passed as varargs (or plain scalars); outputs are immutable lists. Nothing handed to or
    // returned from this API can be aliased or mutated behind the owner's back - a Map<K,V>
    // parameter would invite exactly that and buys nothing over the varargs form.
    CompileResult compile(String moduleName, String source);   // content in
    CompileResult compile(SourceUnit... units);                // content in, multi-module
    CompileResult compileTree(Path moduleRoot);                // convenience: a module tree is intrinsically files

    // ----- run (nested child, event-driven) -------------------------------------------
    // Run a module's entry point as a NESTED child under the host app, straight from an in-memory
    // ModuleRepository - no serialization. The modules can come from ANYWHERE: a fresh compile, a
    // repository loaded from disk, one populated by hand, or a FileTemplate/Byte[] image you
    // dropped in. ALREADY-COMPILED in-memory modules run identically, with no re-compilation -
    // compiling from source is just one way to obtain a repository, not a precondition of run().
    // Returns a future that completes with the result, or EXCEPTIONALLY with the thrown Ecstasy
    // exception - awaitable with a deadline, never a bare int + idle poll. Runs are independent
    // nested children (§1): any number can be in flight in PARALLEL or issued in SEQUENCE, anytime.
    CompletableFuture<RunResult> run(ModuleRepository modules, String moduleName,
                                     String method, Object... args);
    // convenience for the compile-then-run case (a CompileResult IS a repository + diagnostics):
    default CompletableFuture<RunResult> run(CompileResult r, String moduleName,
                                             String method, Object... args) {
        return run(r.modules(), moduleName, method, args);
    }

    @Override void close();   // tears down the host app + native plane

    // ----- value types ----------------------------------------------------------------
    record SourceUnit(String moduleName, String source) {}

    // The post-compile state IS a ModuleRepository. Run modules straight from it, or flush it -
    // FULLY or PARTIALLY - into any SINK repository via the existing repository API. Disk is not
    // special: a DirRepository is the disk sink, a FileRepository a single-file sink, a
    // BuildRepository a memory sink; so "flush to disk / a stream / another repo, all or a named
    // subset" is just ModuleRepository.storeModule across repos - there is NO disk-specific
    // method because an .xtc is only a serialized byte stream and its destination is the
    // caller's business.
    interface CompileResult {
        boolean ok();
        List<Diagnostic> diagnostics();      // immutable; errors + warnings, source-anchored
        ModuleRepository modules();          // in-memory repo of the compiled modules
    }

    record Diagnostic(Severity severity, String code, String message,
                      String source, int line, int column) {}
    record RunResult(int exitCode, ObjectHandle value) {}
}
```

The single new runtime primitive this leans on is the one Gene is adding — and note it is an
**Ecstasy** method, not Java: `FileTemplate` is `ecstasy.reflect.FileTemplate`, whose own
contents are already `@RO immutable Byte[] contents`. `Byte[]` here is the Ecstasy byte
buffer (Ecstasy has no lowercase `byte`; `Byte` is the value type), i.e. the compiled
module's binary image — the same bytes that would otherwise be a `.xtc` on disk:

```
// PROPOSED addition to the Ecstasy Linker (Gene), Ecstasy signature:
FileTemplate makeFileTemplate(immutable Byte[] contents);
```

That is the whole in-memory-compile story, and it lives inside the container-0 host app: the
Java `compile(...)` assembles the module to its binary image (a `Byte[]`), hands it to the
host app, which calls `makeFileTemplate(bytes)` to get a linkable `FileTemplate` — **no `.xtc`
on disk**. It is the in-memory analogue of "write `.xtc` then load it from a repository": our
`XtcEngine.assemble()` does exactly that disk round-trip today, and `makeFileTemplate` collapses
it into one in-memory call. On the Java `ToolConnector` surface the compiled module lives in the
result `ModuleRepository`; `run(...)` links its `FileTemplate` from there directly, and a flush
only serializes when the caller sends it to a repository that writes bytes.

### On the XTC `Source` type

`org.xvm.compiler.Source` (the parsed-source representation the compiler consumes) is an
**implementation detail**, not part of the public surface. The public input is a plain value
record — `SourceUnit(moduleName, source)` (or a tree `Path`) — which the API converts into a
`Source` internally. Keeping the compiler's `Source`/AST types out of the public API decouples
LSP-facing callers from compiler internals that will churn, and lets the future method-level
path reuse the same conversion. If a caller ever already holds a parsed form, feeding it in is an
internal optimization, never a public contract. So: **we care, but we handle it inside the
implementation.**

### Capabilities the design must keep open (even if not implemented now)

The API must foreclose none of these. Each stays possible for one reason: the compile/run
primitives do not corrupt shared global state (§4) — that single invariant is what makes
open-ended, repeated, concurrent, and incremental use *safe*, and therefore possible.

| Capability | Kept possible by | Status |
| --- | --- | --- |
| Compile from ANYWHERE — buffer, tree, or bytes; **no local-env assumption** | input is content (`SourceUnit` / a stream), not a path; `Path` overloads are conveniences over the content core | in-memory + tree now |
| Output ANYWHERE, fully or partially — memory, disk, stream, another repo | the result **is** a `ModuleRepository`; sinks are repositories; disk = `DirRepository` | now |
| **Method-level / incremental** compile later | granularity is not baked in; an external driver (Kotlin incremental) decides *what* to recompile and re-invokes; the API exposes stable module identity and must not assume whole-module-only | future — shape must not preclude it |
| Run ARBITRARY modules, in **parallel or sequence, anytime** | each `run()` is an independent nested child; the connector imposes no ordering/serialization; concurrency is bounded only by §4's hardening, not by API design | now (needs §4) |

---

## 3. Worked mock-ups — "exactly what would work"

All of these are the **client** code an LSP / tool would write. Illustrative, against
master + the proposed API.

### 3.1 The LSP edit→compile→diagnostics loop (the hot path)

```java
// once, at server start — boots the warm host app a single time
ToolConnector tc = ToolConnector.open(xdkModulePath);

// on every keystroke / didChange — in-memory, no disk, warm
ToolConnector.CompileResult r = tc.compile(
        new SourceUnit("EditorBuffer", currentBufferText));

if (!r.ok()) {
    for (var d : r.diagnostics()) {
        publishDiagnostic(uri, d.line(), d.column(), d.severity(), d.message());
    }
}
// r.modules() holds runnable templates if the caller wants to run/preview
```

Needs from the API: **in-memory compile** (no `.xtc` round-trip), **structured
diagnostics** (line/column/severity, not stderr text), and a **warm connector** so the
platform boot + system-library link is paid once, not per keystroke.

### 3.2 Compile → run, with a real failure object

```java
var r = tc.compile(new SourceUnit("Hello", """
        module Hello {
            void run() {
                @Inject Console console;
                console.print("hello from a nested child");
            }
        }
        """));

if (r.ok()) {
    // run straight from the compiled repository, by module name - no serialization
    tc.run(r, "Hello", "run")
      .orTimeout(30, TimeUnit.SECONDS)
      .whenComplete((res, err) -> {
          if (err != null) {
              // err carries the THROWN Ecstasy exception's text — not a bare int
              reportRunFailure(err);
          } else {
              reportRunSuccess(res.exitCode(), res.value());
          }
      });
}
```

Needs: **`run()` returns an event-driven `CompletableFuture`** that completes normally with
the result and **exceptionally with the thrown exception**. (On master, `callLater` at the
service-context boundary already produces exactly this future — it's just not surfaced to a
Java caller. This is a small surface addition, not new capability.)

### 3.3 Multiple modules in one request

```java
var r = tc.compile(
        new SourceUnit("Core",  coreSource),
        new SourceUnit("App",   appSourceThatImportsCore));   // App imports Core

// both compile together into one repository; cross-module references among the batch resolve
tc.run(r, "App", "run").join();   // run any of them by name
```

Needs: **multi-module compile** in one call, resolving references among the submitted set.

### 3.4 A real module directory tree (not just a buffer)

```java
// a module is normally a directory of .x files with nested packages/classes
var r = tc.compileTree(Path.of("/ws/myproject/src/main/x/myModule"));
```

Needs: **source-directory-tree compile**. Real modules and the whole XDK build are trees,
not single strings; a tree entry point (walking the dir into the module's node tree) is the
difference between "toy" and "usable for actual projects."

### 3.5 Concurrent runs over one warm connector

```java
// many previews / test runs in flight at once, all nested children of one host app
List<CompletableFuture<RunResult>> inflight = testModuleNames.stream()
        .map(name -> tc.run(r, name, "run"))
        .toList();
CompletableFuture.allOf(inflight.toArray(CompletableFuture[]::new)).join();
```

Needs: **fully concurrent nested runs** that don't leak and don't corrupt each other. This
is the headline `ToolConnector` promise — and the one with a hard prerequisite (§4).

### 3.6 Flush the compiled repository — fully or partially, to any sink

```java
// r.modules() IS a ModuleRepository. Flush it (all, or a chosen subset) into any sink repo.
// Disk is not special - a DirRepository is the disk sink; there is no writeToDisk() method.
if (userClickedBuild) {
    ModuleRepository compiled = r.modules();
    var sink = new DirRepository(outputDir.toFile(), /*readOnly*/ false);   // or any repository
    for (String name : compiled.getModuleNames()) {   // pick a subset here for a PARTIAL flush
        sink.storeModule(compiled.loadModule(name));
    }
}
```

### 3.7 Run modules you ALREADY have compiled and loaded — no compile step

`run()` takes a `ModuleRepository`, so anything holding already-compiled modules works — no
source, no re-compilation. Compiling from strings is one convenience, not the model.

```java
// (a) run a module already on disk as .xtc
tc.run(new DirRepository(libDir, /*readOnly*/ true), "AlreadyBuilt", "run").join();

// (b) run a module you hold only as bytes (its .xtc image) - drop it into a repo and run
ModuleRepository mem = new BuildRepository();
mem.storeModule(linker.makeFileTemplate(moduleBytes).getModule());   // Byte[] image -> module
tc.run(mem, "FromBytes", "run").join();

// (c) reuse a warm repository across many runs - compiled/loaded ONCE, run repeatedly,
//     in parallel or sequence, without re-parsing or re-compiling anything
ModuleRepository warm = r.modules();          // or any repo you keep around
tc.run(warm, "Service", "start").join();
tc.run(warm, "Service", "healthCheck").join();
```

Needs: **run keyed on a `ModuleRepository`, not on a source-compile result** — so pre-compiled,
disk-loaded, and hand-populated modules are first-class, and a warm repository is reused across
runs rather than recompiled.

---

## 4. The one hard prerequisite: concurrency safety of shared runtime/ASM state

`ToolConnector`'s "fully concurrent, long-running, not-leaking" guarantee is only real if the
state that concurrent nested children **and the container-0 compiler** share is safe under
concurrent read + late-write. **On stock master it is not.** The analysis below is against
**master** (file:line), because that is where `ToolConnector` is being built — not against any
`lagergren/lazy-instance` improvement. Sequential one-shot use is fine (it is what the CLI does);
the moment §3.5 runs modules *at once*, or container 0 compiles *at once*, these break. The API
cannot design around any of them — they live **below the container boundary**, in state shared by
every child and the compiler. Each must be corrected in `runtime/`/`asm/`/`compiler/`.

### 4.0 Priority — which are CRITICAL, and what each capability requires

Two INDEPENDENT axes. A single-threaded long-running host still needs the leak fixed; a short
concurrent burst still needs the races fixed. Pick the capabilities you want and fix that column.

| To support this capability | you MUST fix (all CRITICAL) | why |
| --- | --- | --- |
| **Long-running JVM** — a host that stays up (even sequential, one run at a time) | **#2** shared pool never evicts | without it the heap climbs forever → **OOM**, regardless of concurrency. This is the long-running blocker, and it is *not* a concurrency bug. |
| **Concurrent / overlapping runs** — parallel nested children | **#1** racy `f_listConst` read, **#3** `INSTANCE` statics, **#4** injection-singleton races, **#5** `SingletonConstant` lifecycle, **#6** `TypeInfo` build races | shared-state read+late-write with no synchronization → non-deterministic **crashes / corruption** |
| **Concurrent compiles in container 0** | **#8** static compiler counters, **#9** shared library pool, **#10** compiler AST/`Context` state, **#11** shared `ErrorList` | compiler is single-thread-single-request → **miscompile / crash**. **Interim: serialize compiles** and this column can wait. |
| Nice-to-have hardening (not blocking) | **#7** `markNative` (rare), MA5 leaky getters (aliasing-only) | low-probability / aliasing-only |

**So, minimally:** to ship even a *sequential* long-running host you MUST do **#2**. To let it run
things *concurrently* you add **#1, #3-#6**. To let it *compile* concurrently you add **#8-#11** (or
serialize compiles and defer them). Everything in §4.3 is already safe — leave it alone.

### 4.1 What breaks on MASTER — run side (concurrent / consecutive runs)

| # | Broken design on master (file:line) | Concrete failure | Kind |
| --- | --- | --- | --- |
| 1 | **`ConstantPool.f_listConst` is a plain `ArrayList`** (`ConstantPool.java:3954`) read directly by `getConstant(int){ f_listConst.get(i) }` (`:84`) — no fence, no volatile, no mirror | run A on service-thread-1 reads constant #500 mid-op while run B's lazy `TypeInfo` synthesis on service-thread-2 appends #900 and the ArrayList reallocs its backing array → thread-1 gets a torn/null slot → `ArrayIndexOutOfBounds`/`NullPointerException` mid-execution, non-deterministic | **CRASH** |
| 2 | **Shared pool never evicts** — `f_listConst` only grows (cleared only on reset/disassembly, `:2957`); `TypeInfo` caches on shared `TypeConstant`s are never released on container disposal | a day-long host runs 50,000 distinct user modules; each novel type touched during a run (`Array<UserClass>`, `function void()`, parameterized types) is interned into the persistent library/native pool and its `TypeInfo` cached there; the run's container is GC'd but its interned types + TypeInfo stay → heap climbs monotonically → **OOM** | **LEAK (unbounded)** |
| 3 | **Constructor-published native-template `INSTANCE` statics** (`NativeContainer.java`, 18 refs on master) | two containers init a native template concurrently; the second's ctor sets `INSTANCE = this`, so the first container's handles now resolve against the *second* container's template → wrong-container behavior / `ClassCastException` | **CRASH / corruption** |
| 4 | **Native-injection singletons created without first-wins** (OSStorage/Algorithms) | run A and run B both `@Inject Storage` for the first time at once → two `Storage` services on the shared plane → duplicate/leaked service, inconsistent state | **corruption / leak** |
| 5 | **Split `SingletonConstant` lifecycle fields** (separate handle/fiber/future) | two runs trigger the same module singleton init concurrently → one observes a half-set handle+future combination → NPE or double-init | **CRASH** |
| 6 | **`TypeInfo` build races on shared constants** (no synthesis-window discipline) | two runs build `TypeInfo` for the same shared type at once → an interleaved, partially-populated `TypeInfo` is published → missing-method / wrong-layout failure | **CRASH** |
| 7 | **`MethodStructure.markNative()` non-atomic** (plain `m_fNative`, false-window) | rare: a run triggers native marking while another reads `getOps()` → "neither native nor compiled" | **CRASH (rare)** |

### 4.2 What breaks on MASTER — compile side (container 0 does compiles TOO)

The compiler was written for **one compile at a time**. If container 0 compiles concurrently (or
overlaps a compile with a run's lazy `TypeInfo` build), master breaks:

| # | Broken design on master (file:line) | Concrete failure | Kind |
| --- | --- | --- | --- |
| 8 | **Plain `static` compiler counters, non-atomic** — `s_nLabelCounter` (`ConditionalStatement.java:80`), `s_nCounter` (`ElseExpression.java:220`, `ElvisExpression.java:316`), `m_counter` (`MethodDeclarationStatement.java:1265`) | two `didChange` events compile at once; both read `s_nCounter == 41` and both emit a synthetic name suffixed `41` → two distinct lambdas/labels collide → verify error / wrong dispatch | **MISCOMPILE** |
| 9 | **Concurrent compiles hit the same shared library pool** — each injects NakedRef and registers constants into shared loaded modules, reading/writing the row-1 `f_listConst` ArrayList | same torn-ArrayList race as row 1, now on the *library* pool during link/resolve | **CRASH / corruption** |
| 10 | **Compiler AST/`Context` state assumes single-thread, single-request** — `Context` name/narrow/assign maps, `NameResolver` staged state, `LambdaExpression`/`InvocationExpression` caches, break/continue lists, `CaseManager` labels (see `compiler-ast-context-mutation-audit.md`) | a future incremental recompile reusing a cached AST while a run walks the same nodes, or two parallel compiles of one module → corrupted resolution state | **MISCOMPILE / CRASH** |
| 11 | **Shared `ErrorList`/diagnostic sink** funnelled from concurrent compiles | interleaved/lost diagnostics; a plain list mutated from two compile threads | **corruption of output** |

### 4.3 Verified NOT a problem in the nested model (checked on master — don't chase these)

- **Compositions are per-container** — `f_mapCompositions` is a per-`Container` `ConcurrentHashMap`
  (`Container.java:745` on master). Each nested container builds its own; a disposed container's go
  with it. No cross-container residue (that was the *sibling-main* `relocateConst` path, which the
  model avoids).
- **Container/service registries are safe** — `Runtime.f_containers` is a synchronized `WeakHashMap`
  (`Runtime.java:144`) so disposed run containers auto-evict; `f_setServices` is a
  `ConcurrentWeakHasherMap` set and `f_setFibers` a `ConcurrentSkipListSet`, so lifecycle churn does
  not `CME`. The MA5 "leaky getters" are therefore aliasing-only (a caller *could* mutate the live
  set), not a crash — low severity.
- **Sibling-main relaunch corruption** — avoided by construction (nested throwaways, container 0 not
  relaunched).

**Must-fix set for `ToolConnector` on master:** the run-side crashers (1, 3, 5, 6) and the **leak (2)**;
plus the compile-side set (8-11) if compiles run concurrently — **until 8-11 land, container 0 must
serialize compiles.** Row 7 (`markNative`) and the JIT-name / #541 items round it out. `lazy-instance`
already fixes 1-6 (proof they are real and a reference implementation); master has none of it.

### 4.4 This is a DESIGN problem, not a locking problem — and `ConstantPool` is the worst of it

Rows 1-11 are not eleven unrelated bugs. They are **one design stance repeated**: process-global,
mutable state that is written in place *after* it has been shared and is being read, with the
invariants ("resolve before exposing", "don't register after publish") enforced by *convention*
instead of by *types*. `INSTANCE = this`, `static int s_nCounter`, `TypeInfo` caches that grow on
shared constants forever — all the same shape.

**`ConstantPool` is the most serious instance of it — the single most brittle, unsafe structure in
the system.** One process-shared, mutable, *untyped* `ArrayList<Constant>` (`ConstantPool.java:3954`)
sits at the center of everything — compiler, linker, serializer, runtime execution, and JIT all
mutate and read the *same* pool with no phase boundary. It is unsynchronized (row 1), unbounded and
never evicted (row 2, the leak), and reached from execution by raw integer index (`getConstant(int)`)
then **cast at runtime** (`getConstant(int, Class<T>){ type.cast(...) }`). Nothing about that
structure makes an illegal use *impossible*; it makes every illegal use *available*. A structure this
central should be the most locked-down type in the codebase; instead it is the least.

**The fix is NOT to sprinkle `synchronized` on every method.** That is the Java-1.0 reflex and here it
is actively wrong:
- It **does not fix the leak** — a lock around `f_listConst` still grows forever. #2 is not a race; no
  lock evicts anything.
- It **does not fix wrong-owner sharing** — a lock around `INSTANCE = this` still hands container A
  container B's template. You serialized the corruption, you did not remove it.
- It **defeats the whole point** — the goal is concurrent runs/compiles; a global lock on the shared
  pool serializes them back to one-at-a-time. Pure contention for nothing.
- It **guards a bad shape instead of fixing it.** Locks make mutate-after-publish "not crash" while
  leaving the data mutable, shared, and unbounded.

The right shape is to **make the shared thing immutable and the mutable thing owned** — then almost no
locks are needed, because immutable data is trivially thread-safe and owned data is never contended:

1. **Phase separation / freeze-on-publish.** Build the pool / type system / code in a *single-owner
   mutable* form during compile+link; then **freeze an immutable snapshot** and publish *that* for
   concurrent runtime reads. Immutable → lock-free by definition. (`lazy-instance`'s read-mirror +
   publication fence is a *retrofit* of this; the real target is a `FrozenPool` the runtime can only
   read.)
2. **Ownership instead of globals.** No `INSTANCE`, no `static` counters — per-container/per-request
   state, passed explicitly. A wrong-container handle becomes *unrepresentable*, not merely *unlikely*.
3. **Bounded lifecycle.** Tie interned per-run types / `TypeInfo` to the run's scope and release on
   disposal. This is what fixes #2, and no lock can.

**And use the Java type system instead of bypassing it.** A large part of *why* these bugs are
runtime crashes rather than compile errors is that the code throws away static typing and defers
everything to runtime — an untyped `ArrayList<Constant>` cast per access, `instanceof` ladders and
unchecked casts where a **sealed interface hierarchy** would give exhaustiveness and make illegal
variants impossible, opcode operands passed as bare `int` indices. It is written like Python with a
JVM underneath. That has two concrete costs here:
- **Bugs that should be caught at compile time become runtime casts/crashes.** The mutate-after-publish
  and wrong-shape errors above are exactly the class the compiler *would* reject if the mutable-builder
  and immutable-runtime forms were *different types* (a `MutablePool` you can register into vs a
  `FrozenPool` you can only read) — the illegal call would not compile. Runtime casting makes the
  invariant invisible to `javac`, so it can only fail in production.
- **No exhaustiveness, easy silent drift.** `sealed` constant/op/type hierarchies (see this repo's
  `sealed-hierarchy-audit.md` and `generics-api-audit.md`) let the compiler force every `switch` to
  handle every case; the current `instanceof`/`getClass()` style silently does the wrong thing when a
  new variant appears. That is how "row 7" ("neither native nor compiled") and adjacent state-machine
  gaps hide.

So: **the runtime should stop sharing mutable state and start using the type system to make illegal
states unrepresentable** — freeze-on-publish, ownership, sealed hierarchies, typed pools. Locks are a
last resort at the one narrow publish boundary, not the fix for a mutate-after-publish, untyped,
process-global design. The irony is that **Ecstasy the language is built on precisely this discipline**
— `immutable`/`const`, service isolation, no shared mutable state, no globals — and the runtime that
implements it should be held to its own gospel.

### 4.5 Further instability risks the lazy-instance audits surfaced

Beyond rows 1-11, these will bite the `ToolConnector` model and are worth naming:

- **JIT mode is a whole additional hazard class.** If runs execute via the **JIT** rather than the
  interpreter, add the entire J21-J24 set (`jit-runtime-report.md`): generated `<clinit>` statics
  (`$INSTANCE`, injected resources) *donated from the first container to every later one*, ambient
  `Ctx.get()` capture that binds the first owner, classloaders that are **not parallel-capable** plus a
  racy `loadedClasses` registry, and JIT codegen writing **owner-bearing state into shared ASM**. All
  **unfixed everywhere** (parked for the JIT rebase). This is a *mode* choice and the riskiest one — a
  JIT-backed ToolConnector is materially less safe than an interpreter-backed one today.
- **Concurrent compiles mutate shared ASM during link** (beyond the static counters, row 8):
  `FileStructure.linkModules` calls `registerConstants(fileTop.m_pool)`, `setFingerprintOrigin`, and
  `merge`/`replaceChild` on shared library `FileStructure`s. Two compiles linking the same library at
  once corrupt the shared structures — another reason container 0 must serialize compiles.
- **Non-volatile manual lazy caches** (~20 in `runtime/asm`; see `manual-lazy-cache-audit.md`):
  double-checked lazy init without `volatile`. Under concurrent runs a thread can observe a
  half-initialized object (the JMM allows the reference write to be seen before the field writes). A
  distinct hazard from the pool race; the fix is `volatile`/final-holder idioms, not a lock.
- **Poisoned cache on failure — CHECKED, NOT a hazard (verified on master).** The worry was that a
  run/compile throwing *mid-build* of a shared `TypeInfo` could cache a partial and serve it to every
  later run on the long-lived host. Verified false: the `TypeInfo` cache is exception-safe and
  self-healing — `m_typeinfo` is `volatile` (master `:8233`); `setTypeInfo` is a monotonic CAS
  (`rank(new) > rank(cached)`, master `:1946`) so a partial can never overwrite a complete one;
  `isComplete` (`rank == 3`, master `:2033`) excludes the placeholder and any incomplete info, so a
  failed build's residue triggers a *rebuild* on the next request rather than being served; and serious
  errors call `invalidateTypeInfo()` ("don't cache it", master `:1821`). A bad input does not
  permanently poison the shared cache. (`ConstantPool.f_mapRefTypes` is likewise `computeIfAbsent`, which
  stores nothing on a throwing build. The non-volatile *other* lazy caches above remain a separate,
  visibility-only concern — not a poisoning one.)

### 4.6 Requests, diagnostics, and logging — an API requirement AND a current hazard

An LSP/daemon issues many overlapping **requests** (compiles and runs). Each needs its **own**
diagnostic and log stream; the current runtime does the opposite, which is both an API gap and a
stability bug:

- **Today it is shared/ambient/global.** A shared `ErrorList` funnels diagnostics from whatever is
  compiling (row 11); the runtime reaches for diagnostics via **ambient `ServiceContext.getCurrentContext()`**
  reads (`Argument.java:51`, `OpVar.java:115`, `Utils.java:473` — one of which NPEs on a null ambient,
  MA4); and there are `BLACKHOLE`/swallowed-error paths (`logging-diagnostics-audit.md`). Under
  concurrent requests these **interleave, get attributed to the wrong request, or vanish** — and a
  swallowed error on a long-running host lets corruption accumulate silently.
- **What the API must do instead:** every `compile(...)`/`run(...)` carries a **request-scoped**
  diagnostic sink — no shared `ErrorList`, no ambient current-context reads (pass `Frame`/context
  explicitly), no `BLACKHOLE`. A single **unified channel per request** spans the whole pipeline: the
  compile's `ErrorListener`, the run's unhandled-exception handler, and engine logging all feed one sink
  **tagged by request id**, so the host gets a coherent, ordered, source-anchored stream and can tell
  which request each line belongs to. Diagnostics are **structured** (severity/code/message/source/
  line/column), never stderr text. Logging is per-request or per-engine, never a process-global static
  logger that cannot be correlated and races.

This is why the `Diagnostic` record and a `DiagnosticSink` seam belong in the API from the start (our
`XtcEngine` already returns structured `Diagnostic`s per request); retrofitting request-correlation onto
a shared/ambient diagnostic path after the fact is exactly the kind of thing that does not retrofit.

---

## 5. What we already validated (so this isn't speculation)

We built a working Java proof-of-concept, `org.xvm.api.XtcEngine`, that implements this exact
surface on the reentrancy branch — one warm native plane, in-memory compile, nested-child runs
returning completion futures, multi-module, and disk sync. It is a *Java-side hand-roll* (it
hosts children under -1 via `NestedContainer.createForHost` rather than via a proper container-0
host app), which is precisely why `ToolConnector`'s container-0 model is the correct successor —
but it proved every requirement above end-to-end:

| API feature (§2/§3) | Validated by |
| --- | --- |
| warm plane booted once, reused | `XtcEngine` boots `Runtime` + `NativeContainer` once; multiple runs reuse it |
| in-memory compile, no disk | in-process parse→compile→**assemble** pipeline (the round-trip `makeFileTemplate(Byte[])` replaces) |
| turtle/bridge resolved internally | compile replicates the Launcher's `prelinkSystemLibraries` + NakedRef injection; caller passes one module path |
| `run()` → event-driven future w/ exception | `MainContainer.futureResult()` / `Container.runModule` surface the `callLater` `CompletableFuture` (present on master too) |
| nested child per run, one host plane | `NestedContainer.createForHost` + native-plane injection fallback; two modules over one plane, clean |
| multi-module compile | compiles a batch together into one repository (PoC input being migrated to `compile(SourceUnit...)`) |
| compiled state = a repository, flushable fully/partially | the PoC's `BuildRepository` holds the compiled modules; `writeTo(dir)` validated the repo→`DirRepository` (disk) flush |
| structured diagnostics | `compile` returns `Diagnostic(severity, code, message, source, line)` |
| concurrency safety | the branch's publication fence / thread-scoped windows / first-wins singletons (the §4 prerequisite) |

Gaps we did NOT close (and that `ToolConnector` should own): the proper container-0 host-app
model (vs our -1 hand-roll), a `compile(Path...)` source-**tree** entry point (we did in-memory
buffers only), and one unified diagnostic sink spanning compile+run.

---

## 6. Asks, in one place

1. `ToolConnector` run/compile as in §2, with **container 0 as the host app** and nested
   children per run (the model we agree on).
2. `run(...)` returns a **`CompletableFuture` carrying the thrown exception** — the `callLater`
   future surfaced to Java, not a bare int + poll.
3. `Linker.makeFileTemplate(Byte[])` (Gene) so **in-memory compile → runnable template** needs
   no disk. 👍 already on the list.
4. **Multi-module** compile and a **source-directory-tree** compile entry point.
5. **Turtle/native-bridge bootstrap resolved internally** — the caller configures one module
   path and never passes `-L` flags for the prototype/bridge.
6. Build it on the **hardened shared-state substrate** (§4) so the concurrency/no-leak promise
   holds under real load.
