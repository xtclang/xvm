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
state that concurrent nested children **share** — the `ConstantPool`, `TypeInfo`/lazy caches,
native-injection singletons, and JIT-name caches — is safe under concurrent read+late-write.
On stock master it is not: those are the same shared-state races tracked in issue **#541** /
the same-JVM reuse defects (issue **543**). Sequential one-shot use is fine (it's what the
CLI does); the moment §3.5 runs modules *at once*, unsynchronized `ConstantPool` mutation and
`INSTANCE`/singleton corruption surface as non-deterministic crashes.

The `lagergren/lazy-instance` branch is exactly the fix for this substrate: a `ConstantPool`
runtime-publication fence + read mirror, thread-scoped synthesis windows, native-injection
first-wins publication, single-root enforcement, and the frozen constant families. **So the
branch hardening is the foundation `ToolConnector` needs to keep its concurrency promise — not
a competing effort.** The recommendation is to build `ToolConnector` on top of that substrate
(or fold the substrate fixes in), rather than ship the API over the unhardened pools and
rediscover 543 under load.

### 4.1 Defects `ToolConnector` CANNOT design around — must be corrected in the runtime

No API shape avoids these: they live **below the container boundary**, in state shared by every
nested child *and* the compiler (the pool, the type system, native templates). A cleaner API
just reaches them faster. Each must be fixed in `runtime/`/`asm/`, not in `ToolConnector`.
"Fixed" = done on `lagergren/lazy-instance`; "OPEN on master" = still a landmine on stock master.

| # | Defect (where) | Why the API can't dodge it | Status |
| --- | --- | --- | --- |
| 1 | **Racy `ConstantPool.f_listConst` — a plain `ArrayList` read at runtime** while late constants register (TypeInfo synthesis) | every run/compile reads the shared pool; concurrent read+append on a bare ArrayList = torn reads / `ArrayIndexOutOfBounds` / spurious crashes | **Fixed** (publication fence + volatile read mirror); OPEN on master |
| 2 | **Constructor-published native-template `INSTANCE` / static owner metadata** | process-global mutable statics shared by all containers → last-writer-wins, wrong-container handles | **Fixed** (`NativeTemplates`, owner-local, no `INSTANCE = this`); OPEN on master |
| 3 | **Native-injection singleton duplicate-service races** (OSStorage/Algorithms) | concurrent first-injection from two runs creates duplicate services on the shared plane | **Fixed** (resolved-only first-wins publish); OPEN on master |
| 4 | **Split `SingletonConstant` lifecycle fields** | separate handle/fiber/future fields → impossible mixed init snapshots under concurrent init | **Fixed** (`AtomicReference<InitState>`); OPEN on master |
| 5 | **`TypeInfo` / lazy metadata caches on shared constants** | two runs build/read the same `TypeInfo` concurrently | **Fixed** (thread-scoped synthesis windows + adjacent work); OPEN on master |
| 6 | **`MethodStructure.markNative()` non-atomic transition** (plain `m_fNative`, false-window) | a racing `getOps()` sees `native=false, code=null` → "neither native nor compiled" | **Identified, not yet fixed** (branch should-fix: volatile flag + guard); OPEN on master |
| 7 | **Frozen-code / op-address link caches** (mutable decoded op links, shared) | resolved before runtime exposure by convention only | **Fixed** for the interpreter (link-before-`getOps` + guard); JIT residue **parked to JIT rebase** |
| 8 | **JIT-name caches `m_sJitName`** (owner-bearing name cached on a shared constant) | per-`Xvm` unique suffix cached against a shared constant → wrong name across containers | **Parked to JIT rebase** (J21-J24); OPEN everywhere |
| 9 | **Injector wait-cycle deadlock** (fiber wait-graph never resolves) | reachable single-JVM under load; needs cycle detection | **OPEN — issue #541** (design-level) |

Two things the `ToolConnector` model *does* dodge by construction, worth noting so they are not
re-litigated: **sibling-main relaunch static corruption** (it never relaunches container 0 — one
host app, nested children; that is the whole point) and **container-0 leak** (nested children are
disposable/parent-mediated). Those are design wins of the model; rows 1-9 are not — they are
substrate and must be corrected.

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
