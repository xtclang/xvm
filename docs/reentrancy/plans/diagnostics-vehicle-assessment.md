# Is `ErrorListener` the wrong vehicle? A request-scoped `Diagnostics` object, assessed

Assessment date: 2026-09-01. Branch `lagergren/lazy-instance`, HEAD `a57a96d00`. **Every measurement
below is taken from the WORKING TREE, not from `HEAD`**: the ErrorListener campaign itself is
uncommitted (72 modified files, +1618/-504, plus three untracked tests - `AbortIsNotLoggedTest`,
`TypeInfoDiagnosticsReplayTest`, `TypeInfoModeIsExplicitTest`), and `a57a96d00` is a docs-only commit.
Re-run the counts after that work lands. Read-only analysis; no source file was changed to produce it.

Companion reading, in the order it matters: [`../../errorlistener/README.md`](../../errorlistener/README.md)
(the current design), [`../logging-diagnostics-audit.md`](../logging-diagnostics-audit.md) (the 2026-08-23
audit that proposed `DiagnosticEvent`/`DiagnosticSink`; parts of it are now stale and this document says
which), [`../scoped-value.md`](../scoped-value.md), [`../ambient-context-audit.md`](../ambient-context-audit.md),
[`unified-logging-jfr-telemetry.md`](unified-logging-jfr-telemetry.md), and rows 1-3 of
[`global-issue-pr-backlog.md`](global-issue-pr-backlog.md).

---

## Recommendation

**The vehicle is fine; the problems are elsewhere.** `ErrorListener` is already a per-request
diagnostics handle reached by ownership through final object references, which is why it survives every
thread boundary XVM crosses - and the thing genuinely missing is not a richer diagnostics type but a
*request* object (identity, deadline, cancellation) of which diagnostics is the one member that already
works. Introducing a `Diagnostics` type that is threaded through would be a 639-parameter,
1814-argument, 164-file rename that fixes none of the four things actually broken (no per-run listener,
an engine sink that is unattributed and not thread-safe, no owner context on the payload, no
cancellation channel), while reaching it ambiently would rebuild `ConstantPool.getCurrentPool()` and
`ServiceContext.getCurrentContext()` - both of which this branch deleted by name, and both for exactly
this reason.

The staged plan in [section 7](#7-staged-plan) starts with a change that is independently valuable and
independently verifiable and that needs no new type at all: **give a run its own listener.**

### The three-way split, up front

| A `Diagnostics` object fixes this | Stays broken either way | Just fixed - do not undo |
|---|---|---|
| A named home for request identity, deadline and cancellation (but its subject is the *request*, not diagnostics - §2.4) | The two sinks: a library pool is owned by no request (§3.1) | `log()` returns `void`; abort is asked separately (§4) |
| A named home for suppression *reasons* (achievable on the existing interface - §2.3) | `BLACKHOLE`: a mode flag on a fused build-and-validate call (§3.2) | Zero `errs == null` paths (§4) |
| A single construction point for decorator fan-out (ergonomics, not capability - §2.5) | `ErrorList` is not thread-safe, so the engine sink races under parallel compiles (§3.3) | Ownership reached by object reference, not thread (§4) |
| | Runtime failures travel on a second channel (`f_runtimeFailure` -> `join()`) and stdout on a third (§3.4) | `TypeInfo` carries and replays its own diagnostics (§4) |
| | The LSP does not call the compiler at all, and drops `textDocument.version` in its own code (§3.5) | `ResolutionCollector.getErrorListener` and `XvmStructure.setErrorListener` deleted (§4) |

---

## 1. What "a request-scoped `Diagnostics`" would have to be

The proposal has three separable claims, and they have different answers:

1. *a value type carrying more than a sink* - request id, phase, module, owner, cancellation;
2. *genuinely threaded through* - available to compiler, runtime and subsystems;
3. *configurable at the top-level call site, with `ErrorListener` obtainable from it*.

Claim 3 is uncontroversial and is described in §6. Claim 1 is largely achievable without a new type
(§2.2). **Claim 2 is the whole question**, and §5 is about it.

There are exactly three ways to carry such a thing, and the choice is not a matter of taste:

| carriage | what it is | verdict |
|---|---|---|
| **parameter** | what `ErrorListener errs` already is - 639 declarations, 1814 argument positions | already done; a type swap buys nothing structural |
| **owner field** | what `ConstantPool.m_errs` and `Container.f_errs` already are - 2 owners, 66 read sites | already done; the two-sink problem is a property of *which* owner, not of the type |
| **ambient** | `ThreadLocal` / `ScopedValue` | this is the seductive reading of "available anywhere", and §5 shows it cannot work here |

---

## 2. What a `Diagnostics` object would actually fix

### 2.1 Attribution under parallel compiles and runs: already fixed, by construction

This is the claim most worth deflating, because it sounds like the strongest one.

Attribution is solved today by **lexical capture at the host boundary**: the host constructs one
listener per request, so the listener object *is* the request identity. `XtcEngine.compile(mine, units)`
(`javatools/src/main/java/org/xvm/api/XtcEngine.java:197`) hands that object down, and every owner it
reaches holds it in a final field. `XtcEngineTest.parallelCompilesDoNotShareAListener`
(`javatools/src/test/java/org/xvm/api/XtcEngineTest.java:109-149`) pins it: A's failure does not reach
B's listener and does not fail B.

The mechanism is `ConstantPool.register`:

```java
if (constant.getContaining() != this) {
    constant = (T) constant.adoptedBy(this);
}
```

A compile that references `ecstasy.collections.Map` registers it into its *own* pool, so
`getConstantPool().getErrorListener()` resolves to the compiling pool. Pinned by
`ConstantAdoptionListenerTest` (`javatools/src/test/java/org/xvm/asm/ConstantAdoptionListenerTest.java`).

A request-id field would add *inspectability* - a diagnostic could say which request it belongs to
without the host having to know which listener it handed in. That is real but small: it is a payload
property (§2.2), not a carriage property.

### 2.2 Structured context on the payload: real, and achievable without a new vehicle

`ErrorInfo` carries severity, code, params, `Source`, start/end positions and one optional
`XvmStructure` (`javatools/src/main/java/org/xvm/asm/ErrorListener.java:203-460`). There is **no**
request id, correlation id, or document version anywhere in the tree - a repo-wide grep for
`requestId|correlationId|documentVersion|docVersion` over `javatools/src/main/java` and `lang/`
returns **0 hits**. The `JfrErrorListener.DiagnosticEvent`
(`javatools/src/main/java/org/xvm/asm/JfrErrorListener.java:97-105`) therefore has three fields:
severity, code, message. A profile cannot filter by module or container.

This is the audit's strongest finding and it survives the rework. But note what it needs: **fields on
the event**, not a new object in the call graph. And `ErrorListener` is a `@FunctionalInterface` with
one abstract method and nine defaults, so it can grow

```java
default DiagnosticContext context() { return DiagnosticContext.NONE; }
```

without touching a single one of the 639 parameter declarations or breaking a single lambda host. The
owner that is asked already knows the module and the pool; it can stamp the context at `log` time.

The one real burden: every decorator (`Slf4jErrorListener`, `JfrErrorListener`, `TeeErrorListener`,
`BranchedErrorListener`) must forward `context()`, and one that forgets loses attribution silently.
`DecoratingErrorListenerTest` is the place to gate that.

### 2.3 Named suppression: real, and also achievable on the existing interface

Backlog row 2 ("Explicit diagnostic probes instead of silent `BLACKHOLE`") is still a live and correct
complaint. `ErrorListener.BLACKHOLE` occurs **142** times in main source - up from the audit's 80,
*deliberately*, because the rework converted silent `null`-coalescing into explicit `BLACKHOLE`.
Explicit beats implicit, but "explicit and unexplained" is still not "explicit and named".

`branch(AstNode)` and `suppressCascade()` already exist and already carry the right semantics
(collect, and only `merge()` promotes). Giving them a reason string - `branch(node, "candidate-ranking")`,
`probe("speculative-fit")` - is a one-parameter change on an existing method, plus a source-shape gate.
It does not need a new type.

### 2.4 Cancellation: genuinely missing, and genuinely not a diagnostics concern

This is the one place where the "richer object" argument has real force, and it is worth stating
precisely because the conclusion is *not* "so build a Diagnostics object".

An LSP must cancel a stale compile when the buffer changes. Today it cannot, through two independent
gaps:

- `XtcEngine.TeeErrorListener.isAbortDesired()` (`XtcEngine.java:588-591`) delegates **only to the
  engine's own `ErrorList`**, never to the caller's sink. So README §5.3's remedy - "a host that wants
  to participate wraps a real `ErrorList`" - is true on the CLI path and **not true through
  `XtcEngine.compile`**. A host cannot stop an engine compile by any means.
- `XtcEngine.RunControl.kill()` (`XtcEngine.java:491`) documents its own limitation: it cancels the
  completion future so the caller stops waiting, but does not unwind a running fiber.

The honest reading: what is missing is a **request/session object** carrying identity, a deadline and a
cancellation token. Diagnostics is one member of such an object - and the member that already works.
Overloading `isAbortDesired()` (which also means "too many errors") to mean "the host cancelled" would
re-fuse two questions §5.2 of the README just separated.

### 2.5 Fan-out to JFR/slf4j: already composable; a factory is ergonomics

`Slf4jErrorListener` and `JfrErrorListener` are decorators that wrap and forward
(`javatools/src/main/java/org/xvm/asm/`), and `TeeErrorListener` shows observation without disturbing
the primary. A `Diagnostics` builder would centralise "give me a listener with slf4j + JFR + a
collector", which is convenience. It changes no capability.

---

## 3. What it would not fix. Bluntly.

### 3.1 The two sinks are an ownership problem, and no type fixes ownership

`compile(listener, ...)` covers one compile; `builder().diagnosticSink(...)` covers work no compile
owns. The engine sink is installed in exactly **two** places:

- `XtcEngine.java:114-115` - `NativeContainer.create(runtime, repoLibrary, diagnosticSink)`, so every
  nested run container inherits it;
- `XtcEngine.java:343` - `struct.getConstantPool().setErrorListener(diagnosticSink)` on the `ecstasy`
  and `turtle` library pools, inside `prelinkSystemLibraries`, which runs **once per compile**
  (`XtcEngine.java:233`).

`ConstantPool.setErrorListener` has exactly **2** main-source call sites in the whole tree
(`compiler/Compiler.java:136` and `api/XtcEngine.java:343`). The field is the only non-final
`ErrorListener` slot left in main source (`ConstantPool.java:82`, `private volatile`); the other 12
owner fields are final.

The reason they cannot merge is not the type of the sink. It is that a shared library pool has no
request to belong to. Giving it a per-compile `Diagnostics` writes per-request state onto shared state,
which is the thing the rework removed. There are exactly three ways out, and a `Diagnostics` type picks
none of them for you:

1. **"who asks wins"** - pass the caller's listener instead of consulting the pool. 60 no-argument
   `ensureTypeInfo()` calls remain. E35 D argues against converting them, and I agree with the
   argument: it would route corrupt-library faults into user-facing diagnostics with no source
   position in the user's file, which for an LSP is worse than silence. Adoption (§2.1) already
   delivers the guarantee for every case a user can trigger.
2. **ambient** - see §5. No.
3. **fan-out** - the library pool's listener becomes a broadcaster over the engine's live requests, and
   what it emits is *labelled unattributed*. This is the option nobody has written down, and I think it
   is the right one: a fault in a shared library genuinely belongs to no document, and an LSP should
   render it as a workspace diagnostic rather than a squiggle on line 1. That is a change to what the
   engine installs, not to what the interface is.

**Where I would change backlog row 1:** it says `ErrorListener` "should not be the primary diagnostic
authority". On the evidence, the authority model is now correct - two owners, both final-or-nearly,
both reached by reference. What is wrong is that one of the two owners (a shared library pool) is not a
request, and no `DiagnosticContext` record changes that.

### 3.2 `BLACKHOLE` would be reproduced verbatim as `Diagnostics.silent()`

Confirmed, and the current work already established why: `ensureTypeInfo` fuses "compute the metadata"
(idempotent, cacheable, should never report) with "validate the type" (produces diagnostics owned by
whoever asked). README §8.3 is right that the listener parameter is a mode flag in disguise. Passing a
`Diagnostics.silent()` at the same sites for the same reason changes the spelling and nothing else.

The actual fix is already half-applied and is a *shape* change, not a *type* change:
`TypeConstant.typeInfo()` (`constants/TypeConstant.java:1701`) is the question form,
`ensureTypeInfo(errs)` the assertion form, and `TypeInfo.diagnostics()` / `replayDiagnostics(errs)`
(`constants/TypeInfo.java:138,160`) let every later caller be told without duplication. All 14
`ensureTypeInfo(BLACKHOLE)` sites migrated; `typeInfo()` now has 12 call sites and
`TypeInfoModeIsExplicitTest` gates the idiom out.

### 3.3 `ErrorList` is not thread-safe, and the engine sink is the one that gets hit concurrently

`ErrorList.log` (`javatools/src/main/java/org/xvm/asm/ErrorList.java:74-92`) mutates a `HashSet<String>`,
an `ArrayList<ErrorInfo>`, an `int` and a `Severity` field with no synchronization and no volatile.

That is safe for the per-compile primary: a grep for `parallelStream|ExecutorService|new Thread(|ForkJoinPool`
across `org/xvm/compiler`, `org/xvm/asm` and `org/xvm/tool` returns **0** - a single compile is
single-threaded. It is **not** safe for the engine sink, because `ConstantPool.register` is explicitly
built for concurrent access (`ConstantPool.java:394-452`, a `RegistrationCompletion` record with a
`Thread owner` and a blocking `done().get()` at `:441`), so parallel compiles resolving library types
can log into one shared sink at once. A host that passes `new ErrorList()` as `diagnosticSink` has a
data race today.

This is a real bug, it is worth fixing, and it has nothing to do with the interface.

### 3.4 Diagnostics still travel on three channels

- the listener;
- `Container.recordRuntimeFailure` -> `f_runtimeFailure` (`runtime/Container.java:743,1043`) ->
  surfaced through `join()` / `RunControl.error()`. README §6 defends this: it carries a Java stack
  trace an `ErrorInfo` cannot;
- 25 remaining `System.err`/`printStackTrace` sites (down from 53), plus `tool/Console` and
  `javajit/Ctx.log`, which is called from generated bytecode.

`XtcEngine`'s own class javadoc names this as the open item (`XtcEngine.java:92-100`): "An LSP
ultimately wants ONE diagnostic/logging channel spanning the whole compile-to-run pipeline, with
request correlation." Merging those channels is a routing change - decide what feeds what - and the
`unified-logging-jfr-telemetry.md` plan already specifies the shape. A new diagnostics type does not
route anything.

### 3.5 The LSP blocker is not in `javatools` at all

This is the finding that most changes the answer to "is it worth it", so it is stated with its
evidence.

`lang/lsp-server` is Kotlin on LSP4J. It contains **zero** references to `XtcEngine`, `ErrorListener`,
`org.xvm.asm` or `org.xvm.compiler`. Its diagnostics come from tree-sitter syntax errors only
(`src/main/kotlin/org/xvm/lsp/adapter/treesitter/TreeSitterAdapter.kt:299,315-334,1508-1528`). The
compiler-backed adapter is a stub:

```kotlin
// lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt:19-31
@WorkInProgress("Awaiting full compiler integration")
...
override fun compile(uri: String, content: String) = CompilationResult.success(uri, emptyList())
```

And the version problem the audit attributes to `ErrorListener` exists **inside the LSP's own code**,
upstream of anything javatools does: `XtcTextDocumentService` never reads
`params.textDocument.version`, `didChange` takes `changes.first().text` (full-text sync assumed), and
`XtcLanguageServer.publishDiagnostics` (`server/XtcLanguageServer.kt:641-647`) constructs the **2-arg**
`PublishDiagnosticsParams(uri, diagnostics)`, so no version is ever sent to the client. There is no
debounce and no stale-compile cancellation. `diagnosticProvider` is commented out at `:433`.

So: "an LSP client cannot know which document version a message belongs to" is true, and it would still
be true with a perfect `DiagnosticContext`, because the version is dropped two layers above.
`XdkAdapter` is an empty socket shaped exactly like `XtcEngine.compile(ErrorListener, SourceUnit...)`.
**Filling it in is the LSP work; redesigning the sink is not.**

---

## 4. What was just fixed. Do not re-import it.

A rewrite is the most likely way to lose these, because each looks like an obvious "improvement" to
someone starting from a blank `Diagnostics` interface:

| property | where | what a rewrite would be tempted to do |
|---|---|---|
| `log` returns `void`; `isAbortDesired()` is the only control-flow question | README §5, `AbortIsNotLoggedTest` | give `report()` a return value again, so one boolean means both "recorded" and "stop" |
| `ErrorListener.RUNTIME` never throws from inside `log` | README §5.2 | make a "strict" mode throw, so the same diagnostic behaves differently by sink |
| **0** `errs == null` paths (was 28, then 12) | README §9.1, verified by grep today | a nullable `Diagnostics` field, or a `null` -> `NONE` coalescing helper, re-imports it at all 639 sites |
| listener reached by ownership, never ambiently | README §2.1 | a `Diagnostics.current()` convenience - see §5 |
| `Container.f_errs` final, inheritance resolved in the constructor | README §9.1 | a settable session field on the container |
| `ResolutionCollector.getErrorListener` deleted (a callback interface being *asked* for a sink) | CONTINUATION | `interface X { default Diagnostics diagnostics() { return NONE; } }` is the identical mistake |
| `XvmStructure.setErrorListener` deleted (it mutated the *parent's* state) | README §1.3 | a `setDiagnostics` on a structure |
| `TypeInfo` carries and replays its own diagnostics | README §8.4, `TypeInfoDiagnosticsReplayTest` | rebuild-on-ask, which is what the memoization bug was |

Current measurements, taken today on `a57a96d00` over `javatools/src/main/java`:

| | |
|---|---|
| lines mentioning `ErrorListener` | 1224, in 164 files |
| `ErrorListener` parameter declarations | 639 (616 named `errs`) |
| `errs` in an argument position | 1814 (2789 `errs` tokens total) |
| `getErrorListener()` read sites | 66 |
| `ConstantPool.setErrorListener` call sites | **2** main, 4 test |
| `ErrorListener`-typed fields | 14: 12 final, 1 `volatile` (`ConstantPool.m_errs`), 1 builder slot |
| `ErrorList`-typed fields | 4 |
| `ErrorListener.BLACKHOLE` | 142 |
| `new ErrorList(` | 6 |
| `errs == null` | **0** |
| no-argument `ensureTypeInfo()` / `typeInfo()` | 60 / 12 |
| request id, correlation id, document version | **0 - the concept does not exist** |

That 1814 is the churn number for a type swap, and it is the number to weigh against §2's benefits.

---

## 5. The crux: how would it be carried?

### 5.1 The ambient reading rebuilds two things this branch deleted by name

`ConstantPool.getCurrentPool()` **and its whole thread-local** are gone: `withPool(`, `getCurrentPool`,
`assertCurrentPool` and `s_tloPool` return **0 hits** in `javatools/src/main/java` today. The
ambient-context audit's "Recommended Work Order" item 6 has been completed.

The runtime did the same thing, and left the reasoning in place:

```java
// javatools/src/main/java/org/xvm/runtime/ServiceContext.java:174-181
// NOTE: the public ambient accessor `getCurrentContext()` has been REMOVED (MA4 closure). It
// returned "whatever fiber happens to be bound on the calling thread", which is the same
// hidden-ownership hazard this branch deleted from the ConstantPool (`getCurrentPool()`): the
// observing thread is frequently not the one the caller means, and the value can simply be null.
// ... DisplayPurityTest bans the accessor by name so it cannot quietly come back.
```

A `Diagnostics.current()` is that accessor with a different name.

### 5.2 The boundaries, enumerated

| # | boundary | site | what an explicit hand-off costs |
|---|---|---|---|
| 1 | **fiber -> XVM worker pool** | `ServiceContext.execute` (`:357-366`) forks to `Container.schedule` (`Container.java:259-268`) -> `Runtime.submitService` (`Runtime.java:126-129`) -> `f_executorXVM`, a fixed pool sized to `availableProcessors()` | **Near zero.** `Message` (`ServiceContext.java:1591-1605`) already copies `f_fiberCaller`, `f_fnCaller`, `f_iCallerId`, `f_iCallerPC` and `f_mapTokens` from the caller's frame. One more reference field is one assignment at `:1604` and one read at `nextFiber():468`. |
| 2 | **nested inline execution** | `ServiceContext.java:358`: service B routinely runs *on service A's thread*, which is why `drainWork()` (`:301-303`, `:332`) does **save/restore, not set/clear** | Free for an explicit field; **fatal for a naive `ScopedValue`**, which would need to be a stack. |
| 3 | **response delivery** | `processResponses()` is called from the `finally` at `ServiceContext.java:337` on a *different* context's thread; `Response.run()` (`:2010-2020`) completes the caller's future there | Real work. `Response` carries almost no caller metadata beyond `f_fiberCaller`. Anything ambient read here is wrong **by construction**. |
| 4 | **IO virtual threads** | `Container.scheduleIO` (`Container.java:287-296`) creates the future on the service thread and completes it on an `IOWorker@` virtual thread; **15** call sites (`xOSFile` x6, `xRTSocket` x3, `xRTNameService` x3, `xRawOSFileChannel` x2, `xRTConnector`, `xRTCertificateManager` x2) | Capture at `scheduleIO` time, not at `complete` time: one field on the closure. |
| 5 | **the shared static `Timer`** | `xLocalClock.java:372` - `private static final Timer TIMER`, **one thread for the whole JVM**, shared by LocalClock alarms, NanosTimer alarms and service wake-ups. Alarm bodies run there and re-enter via `context.callLater(...)` (`xLocalClock.java:270`, `xNanosTimer.java:389`, `ServiceContext.java:2042-2049`) | Already solved, and solved *this* way: `WeakCallback` (`runtime/WeakCallback.java`) captures the owning `ServiceContext` at schedule time, because there is nothing on the Timer thread to inherit from. |
| 6 | **foreign HTTP threads** | `xRTServer.RequestHandler.handle` (`:690-704`) is entered from the JDK `HttpServer` cached pool (`:257-271`) and calls `postRequest(null, ...)` - `frame == null`, so the `Message` carries **no** caller fiber and **no** tokens | The hardest case: there is no upstream to inherit from at all. An ambient scheme has nothing to read; an explicit scheme has to be told by the route registration. |
| 7 | **filesystem watch daemon** | `xOSStorage.WatchServiceDaemon` (`:301-331`), its own `Thread`, calls `callLater` at `:373` | Same as 5: capture at registration. |
| 8 | **JIT class loading** | `ModuleLoader.findClass` -> `typeSystem.genClass` -> `defineClass` (`javajit/ModuleLoader.java:85-107`) is **synchronous on whichever thread first touched the class**; `Xvm.java:413` guards TypeSystem construction with a `synchronized` that documents a deadlock-on-recursion hazard | The JIT is the one place ambient *works* - `JitConnector.java:80` binds `ScopedValue.where(Ctx.Current, new Ctx(xvm, container))` around the whole run and class loading never leaves the thread. It is simultaneously the counterexample: `Ctx` does not survive a service hop. |
| 9 | **`CompletableFuture` generally** | ~75 main-source sites, 6 distinct "completed by a foreign thread" mechanisms; `xFuture.java` alone has 15 `whenComplete` sites each of which posts new work | Each continuation must capture. This is what "does not follow work onto another thread" costs in practice. |
| 10 | **LSP request dispatch** | `XtcLanguageServer.supplyAsync` (`server/XtcLanguageServer.kt:191-204`) runs every request on the **common ForkJoinPool**; `WorkspaceIndexer.kt:56-94` fans out on its own pool | Kotlin side; capture in the lambda. |

Ambient slots that remain in main source, for completeness: 2 `ScopedValue`
(`TypeConstant.s_context:8590`, `javajit.Ctx.Current:66`) and 6 `ThreadLocal`
(`ServiceContext.s_tloContext:2186`, `ConstantPool.f_tlolistDeferred:4464` and
`SYNTHESIS_WINDOWS:4492`, `MultiMethodStructure.s_tloIgnoreNative:499`,
`TypeConstant.m_tloInProgress:8546`, `TypeParameterConstant.f_tloReEntry:274`). **Every one is a
recursion guard or a lexical binding mechanism. None is an owner lookup.** Two source-shape guards
already treat `ThreadLocal` as suspect state (`ConstantAdoptionValidator.java:160`,
`OwnershipDiagnostics.java:621`), and `xRTServerTest.java:27-33` asserts the TLS key manager holds no
`ThreadLocal` field.

### 5.3 `ScopedValue`, assessed honestly

`ScopedValue` is strictly better than `ThreadLocal`: structured, restored on scope exit, cannot leak
past its scope, and cheaper to read. Those are real properties and `scoped-value.md` gets them right.

But the property that matters here is the one it does **not** have: **a `ScopedValue` binding does not
follow work onto another thread.** Structured child tasks inherit it; a `submit()` to a shared pool,
a `whenComplete` callback, a `TimerTask`, and a JDK `HttpServer` handler do not. Boundaries 1, 3, 4, 5,
6, 7, 9 and 10 above are exactly those cases, and boundary 2 (nested inline execution) additionally
requires the binding to behave as a stack.

The decisive point is that the current design has **already paid** for all of this and gets it for
free: a listener is reached from `frame.container().getErrorListener()`, and `Frame.f_context` and
`ServiceContext.f_container` are both `final` fields (`Frame.java:87,1372`). It works on the Timer
thread, on an IO virtual thread, inside an HTTP handler, and inside a JIT-loaded class, because it
travels with the object graph rather than with the thread. `ServiceContext.java:753-761` is a working
example: a listener call from inside an interpreter `Op`.

### 5.4 If a request context is ever wanted, the mechanism already exists

`Fiber.m_mapTokens` (`runtime/Fiber.java:58-155,663-671`) is a map of `SharedContext.Token` handles -
Ecstasy's own request-scoped ambient context - and it is propagated across service calls by being
**copied onto the `Message`** (`ServiceContext.java:1604`), not by a thread-local. That is the pattern
to imitate for anything Java-side that must follow a fiber. It is explicit hand-off, it already
crosses every boundary in §5.2, and it is the existence proof that the codebase's answer to "follow the
work" is a field on the message.

---

## 6. Backwards compatibility: `diagnostics.listener()`

The proposed bridge - a `Diagnostics` owns an `ErrorListener`, `diagnostics.listener()` hands one out,
existing signatures keep compiling, migration is per-subsystem - **works, and is the right shape, but
only for the half of the design that lives at the boundary.** Where it breaks down:

1. **The bridge is one-way.** A subsystem migrated to take `Diagnostics` cannot call the 639 unmigrated
   methods without unwrapping to a listener, and a subsystem still taking `ErrorListener` cannot get
   back to the `Diagnostics`. So during migration the tree has two currencies and a lossy conversion in
   one direction. With 1814 argument positions across 164 files, "per-subsystem" means that state
   persists for a long time.
2. **The context is lost exactly where it is wanted.** If `Diagnostics` carries the request id and
   `listener()` yields a bare `ErrorListener`, then everything below the migrated boundary emits
   context-free diagnostics - which is the status quo. To avoid that, the listener handed out must
   itself carry the context, at which point the context lives on the listener and the `Diagnostics`
   wrapper is not carrying anything the listener does not.
3. **Branching has to be mirrored.** `branch()` / `merge()` / `suppressCascade()` are used throughout
   the compiler and a `Diagnostics` needs the same operations, or the two currencies diverge in
   behaviour, not just in type.
4. **Two "silent" values appear.** `ErrorListener.BLACKHOLE` and `Diagnostics.silent()` would both
   exist for the duration, and the source-shape gates that currently police `BLACKHOLE`
   (`TypeInfoModeIsExplicitTest`) would need twins.

The version of this that does **not** break down is the inversion: **put the object at the boundary and
leave the listener as the currency.** A host constructs a request, the request constructs the listener
that carries its identity, and `ErrorListener` grows default methods (`context()`,
`isCancellationRequested()`) so deep code can ask for more without a single signature changing. That is
compatible with every one of the 639 parameters on day one, and it keeps exactly one currency.

---

## 7. Staged plan

Each step is independently valuable, independently verifiable, and does not depend on the next one
landing. No step introduces a new vehicle.

### Step 1 - give a run its own listener (do this one first)

**Why it is worth doing alone.** `engine.compile(mine, units)` gives a host the compile's diagnostics.
`engine.run(result, module)` gives it nothing per-request: `NestedContainer.createForHost`
(`runtime/NestedContainer.java:61-65`) takes no listener, so the run inherits the engine-lifetime sink
shared by every run. The driving use case - "plug into everything that happens during an LSP compile
*and* run" - is half-unserved today, and this is the half. The CLI has the same hole from the other
end: `Runner.createBaseConnector` (`tool/Runner.java:354`) constructs `new InterpreterConnector(repo)`,
the one-argument overload that defaults to `ErrorListener.RUNTIME`, so the launcher's `m_errors` never
reaches the `NativeContainer` even though the two-argument seam exists
(`api/InterpreterConnector.java:54-58`).

**What it is.** Add the listener parameter to `NestedContainer.createForHost`; add
`XtcEngine.run(ErrorListener, CompileResult, String)` and the matching `start`; pass `m_errors` in
`Runner.createBaseConnector`. This is E35 step A's remainder, and E35 already says so: "This also gives
`XtcEngine.run`/`start` a place to put a host listener, which they currently have no way to supply."

**Proof.** A test mirroring `parallelCompilesDoNotShareAListener`: two concurrent runs with two
listeners, each hearing only its own. Mutation check: drop the parameter through and the test fails.

**Cost.** One parameter on one factory, two overloads, one CLI line.

### Step 2 - make the engine sink safe and honest

Two independent defects, one PR. (a) `ErrorList` is not thread-safe (§3.3) and the engine sink is the
one that can be written by parallel compiles; either make the engine wrap whatever the builder is given
in a serializing decorator, or document that the sink must be. (b) Diagnostics arriving on the engine
sink genuinely belong to no request; mark them so, rather than leaving a host to guess.

**Proof.** N parallel compiles all touching a deliberately broken library, asserting no lost or
duplicated entries in a collecting sink. Red before, green after.

### Step 3 - context on the payload, not in the plumbing

`ErrorInfo` gains an optional owner descriptor (module name, pool identity, container id, phase),
stamped by the owner that was asked. `ErrorListener` gains `default DiagnosticContext context()`.
`JfrErrorListener.DiagnosticEvent` gains the fields, so a JFR profile can finally filter by module.
This is backlog row 1's payload half, delivered without its vehicle half.

**Proof.** A library diagnostic names the library module; a compile diagnostic names the compiled
module; a decorator that drops `context()` fails `DecoratingErrorListenerTest`.

### Step 4 - name the suppression

`branch(node, reason)` / `probe(reason)`, plus a source-shape gate that a bare `ErrorListener.BLACKHOLE`
outside a named allowlist fails - the same gating technique `TypeInfoModeIsExplicitTest` already uses.
This is backlog row 2, on the existing interface. It does **not** attempt to remove `BLACKHOLE` from
the `ensureTypeInfo` family, because §3.2 says that is a different (already half-done) refactor.

### Step 5 - fill in `XdkAdapter`, and fix the version handling in the LSP

Wire `lang/lsp-server`'s `XdkAdapter.compile` to `XtcEngine.compile(ErrorListener, SourceUnit...)`;
read `params.textDocument.version`; publish with the 3-arg `PublishDiagnosticsParams`; debounce and
drop stale results. This is where the "LSP support" use case actually is, and none of it needs a
javatools redesign.

### Step 6 - only if steps 1-5 leave a real gap: a request object at the boundary

If the residue is identity, deadline and cancellation - and I believe it will be - then add a
`CompileRequest` / session type **at `XtcEngine`'s boundary only**, owning the listener it hands down.
`request.listener()` is what crosses into `javatools`, so nothing below the boundary changes. Give
cancellation its own explicit channel rather than overloading `isAbortDesired()`; separately, decide
whether `TeeErrorListener.isAbortDesired()` should consult the caller's sink (§2.4) - today it does
not, and README §5.3 reads as though it does.

### Not recommended

- Threading a `Diagnostics` type through the 639 parameters. 1814 argument positions, 164 files, an
  enormous conflict surface against master, and §3 is the list of things that stay broken afterwards.
- Reaching it ambiently. §5.
- Deleting the second sink. §3.1.
- Converting the remaining 60 no-argument `ensureTypeInfo()` calls. E35 D's argument stands.

---

## 8. Where I agree and disagree with backlog rows 1-3

**Row 1, "Request-owned diagnostic session".** Agree with the *evidence* - `ErrorListener` has no
request id, owner id, container id, document version, phase, related spans, or Java cause, and that is
still exactly true (§2.2). Disagree with the *conclusion* that `ErrorListener` "should not be the
primary diagnostic authority": since the audit was written, the authority model became two owners
reached by final reference, which is the correct shape and is what makes parallel isolation a property
of ownership rather than of timing. **Reframe row 1 as "add request context to the diagnostic payload
and a request object at the engine boundary"** - steps 3 and 6 - and it becomes a change nothing has to
be rewritten for. Note also that several of the row's supporting citations are now stale: `log` no
longer returns a boolean, `RUNTIME` no longer throws from inside `log`, and `Parser` no longer swaps its
listener (the field is final).

**Row 2, "Explicit diagnostic probes instead of silent `BLACKHOLE`".** Agree, with one correction and
one caveat. The correction: the count is 142, not 80, and it rose *because* the rework made implicit
silence explicit - so the metric to watch is not the count but whether each site names a reason. The
caveat: the `ensureTypeInfo` family must be excluded from this row, because there the parameter is a
mode flag on a fused operation (§3.2) and the fix is the `typeInfo()` / `ensureTypeInfo(errs)` split,
which is already in.

**Row 3, "Stop deep `ErrorList` authority".** Agree, and it got cheaper: the audit found 10
main-source `new ErrorList(` sites; there are now **6**, and the arbitrary construction constants are
gone (`DEFAULT_MAX_ERRORS`, `firstError()`, `unlimited()`). The two that still matter for the driving
use cases are `xRTCompiler.CompilerAdapter` (`runtime/template/_native/lang/src/xRTCompiler.java:320`),
where a *running Ecstasy program* compiles a module into a private list instead of reporting to
`frame.container().getErrorListener()`, and `javajit.Linker.errors` - `JitConnector` has no
`ErrorListener` plumbing at all. Both are one-parameter fixes of the same shape as step 1, and I would
attach them to it.
