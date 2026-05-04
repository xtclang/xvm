# Runtime implementation plan — getting to a demo-worthy round trip

> **Status (2026-05): Stages 1–3 are landed.** The runtime now resolves
> `@Inject Logger logger;` end-to-end; the demo described in §"Definition of
> 'demo worthy'" works as written. **The plan diverged from this document in one
> way worth flagging up front:** there is no separate `RTLogger.java` /
> `xRTLogger`. `BasicLogger` is a `const`, and the runtime constructs it
> directly in `javatools/.../NativeContainer.java` (`ensureLogger` /
> `ensureConst`). The reason — collapsing the service-wrapper indirection so
> per-fiber `MDC` (`SharedContext`) survives injection — is documented as
> question Q-D5 in `../open-questions.md`. The stage descriptions below describe
> the *original* approach for historical context; treat them as background, not
> as instructions for current work. Stage 4 (compiler-side default name)
> remains open.

This historical plan described how to turn the original v0 stub (`@Inject Logger logger;`
parsed but did not resolve) into a **working end-to-end demo**: the Ecstasy line

```ecstasy
@Inject Logger logger;
logger.info("hello {}", ["world"]);
```

producing real output on the platform `Console` via the runtime injector. Every item
here either is on the critical path to that demo, or is needed to call the round trip
"complete" (parameterized messages actually substitute, MDC actually propagates, etc.).

The doc supersedes `../open-questions.md` items 1, 2, 3, 5, 9, 10 by prescribing concrete
fixes; the others remain genuinely open.

## Definition of "demo worthy"

The round trip is demo-worthy when **all** of these are true:

1. A standalone Ecstasy program containing only
   ```ecstasy
   module Demo {
       package log import logging.xtclang.org;
       void run() {
           @Inject log.Logger logger;
           logger.info("hello {}", ["world"]);
       }
   }
   ```
   compiles, runs, and prints a single timestamped line containing `hello world`
   without any explicit `BasicLogger` / `LogSink` wiring.

2. The same demo with `Logger demo = logger.named("com.example"); demo.info(...);`
   produces a logger named `com.example` (output line carries the name). Per-name
   loggers are derived from the injected one, not injected directly — see Stage 1.4
   for why `@Inject("…") Logger` is *not* the chosen API shape.

3. `logger.error("failed: {}", [id], cause=new Exception("boom"));` emits both the
   message line and the exception text.

4. `logger.atInfo().addMarker(AUDIT).addKeyValue("k", v).log("msg")` works through the
   fluent builder.

5. MDC `mdc.put("requestId", id)` shows up alongside any subsequent emission (rendered
   by a sink that cares; default `ConsoleLogSink` would gain a small change to render
   MDC entries).

6. The focused `lib_logging` suite (54 XTC test methods) still compiles cleanly;
   `manualTests/src/main/x/TestLogger.x` invokes both `runInjected()` and the
   slog-shaped `runInjectedSlog()` path successfully.

7. `~/src/platform/kernel/kernel.x` compiles unchanged (it does not use
   `@Inject Logger` yet) AND a small follow-up PR converting one of its
   `console.print($"... Info :")` calls to `logger.info(...)` works end-to-end.

If all seven hold, we can demo `lib_logging` to anyone.

## Critical path

These items are sequenced by hard dependency.

### Stage 1 — Native side: make `@Inject Logger` resolve

Three native files in `javatools_jitbridge/`, plus a registration line in
`nMainInjector`. Mirrors the `TerminalConsole.java` pattern exactly.

#### 1.1 — `RTLogger.java`

`javatools_jitbridge/src/main/java/org/xtclang/_native/logging/RTLogger.java`.

A native service that wraps an Ecstasy `BasicLogger` instance. There is exactly one
registration: `(loggerType, "logger")`. The `$create(Object opts)` factory always
constructs the root logger named `"logger"`; per-name children are obtained from it
via `Logger.named(String)` (see Stage 1.4 for why we don't accept `@Inject("…")`).

- Constructor takes `(String name, LogSink sink)`. The `sink` argument resolves via
  the same injector — this is the second native registration below.
- Implements every method on the Ecstasy `Logger` interface using the `$p` suffix
  convention (`info$p(Ctx, String, Array<Object>, Exception?, Marker?)`, etc.).
- All emission methods delegate to an inner pure-Ecstasy `BasicLogger` constructed
  once per resource name.

#### 1.2 — `RTConsoleLogSink.java`

`javatools_jitbridge/.../logging/RTConsoleLogSink.java`.

The native default sink. Wraps `ConsoleLogSink` (Ecstasy) and is what
`@Inject LogSink defaultSink` resolves to when no application override is registered.
Bootstrap-safe: even if `Console` is not yet registered, falls back to
`System.err.println` so first-line logging during early init can never deadlock.

#### 1.3 — `RTMarkerFactory.java`, `RTMDC.java`

Same pattern, smaller. Both wrap their Ecstasy counterparts. `RTMDC` needs a per-fiber
storage decision — see Stage 2 below; until that's resolved, it holds a per-instance
`HashMap<String, String>` and we accept that MDC is per-container, not per-fiber.

#### 1.4 — Single fixed-name supplier; no wildcard injection

**Resolved against wildcard.** The earlier draft of this plan called for a wildcard
`(loggerType, "*")` fallback so that `@Inject("com.example") Logger logger;` would
resolve to a per-name logger. That shape was prototyped in `NativeContainer` (interpreter
side) and then deliberately removed. The chosen design instead:

- Register exactly one supplier in `NativeContainer.initResources` /
  `nMainInjector.addNativeResources`: `("logger", loggerType)` → root `Logger`.
- Expose `Logger.named(String)` on the public API. Per-name loggers are *derived*, not
  injected: `@Inject Logger logger; Logger payments = logger.named("payments");`.
- `getInjectable` stays untouched. There is no type-only special-case in the runtime;
  the supplier table remains the single registry of allowed injections.

This is the same call-site shape SLF4J users already write in Java
(`LoggerFactory.getLogger(MyClass.class)`), so it does not cost ergonomics relative to
the SLF4J baseline. It does cost the spelling `@Inject("name") Logger logger;`, which
has no actual SLF4J equivalent and was a `lib_logging` invention.

##### Why we rejected wildcard injection

1. **Special-case at the deepest layer of the runtime.** A type-only fallback inside
   `NativeContainer.getInjectable` (or `nMainInjector.supplierOf`) means the supplier
   table no longer answers "what can be injected here?" without consulting a hidden
   per-type bypass.
2. **One customer.** The whole wildcard mechanism was being built for `Logger`. A
   feature that special-cases the runtime for a single library type is hard to justify.
3. **Imagined SLF4J parity.** SLF4J in Java does not use parameterized injection at all
   — its idiom is `LoggerFactory.getLogger(MyClass.class)`. The `@Inject("name")` form
   was a `lib_logging` invention pitched as "SLF4J ergonomics" but is in fact a *more*
   concise spelling than SLF4J actually offers. The cost-vs-benefit didn't pencil out.
4. **The "future generalisation" argument has no second customer.** Promoting wildcard
   to a first-class `Injector` API only pays off if a second library wants type-only
   injection. None is in sight.

##### Cost of the chosen design

One extra line per class that wants a per-name logger:

```ecstasy
@Inject Logger logger;                          // injected once
static Logger PaymentLogger = logger.named("payments"); // derived
```

That line is what every Java SLF4J user writes today. We accept it.

##### Alternatives that were considered and rejected

- **`@Inject(opts="com.example") Logger logger;`** — spelling is awkward and still
  routes a single-supplier lookup with the real identity hidden in `opts`.
- **Compiler-substituted module name** (Stage 4) — useful *complement* (auto-defaults
  the per-module logger name), but doesn't address per-class loggers within a module.
  Track separately if it lands.
- **Pure `LoggerFactory.getLogger(...)` with no `@Inject Logger` at all** — gives up
  the injection ergonomic without buying anything; the per-name problem just moves
  one level out.

#### 1.5 — Registration in `addNativeResources()`

The interpreter-side equivalent is already in place:

```java
xRTLogger    templateLogger = xRTLogger.INSTANCE;
TypeConstant typeLogger     = templateLogger.getCanonicalType();
addResourceSupplier(new InjectionKey("logger", typeLogger),
        (frame, hOpts) -> templateLogger.ensureLogger(frame, "logger", hOpts));
```

For the JIT injector, the analogous addition:

```java
suppliers.put(new Resource(loggerType,        "logger"),  RTLogger::$create);
suppliers.put(new Resource(logSinkType,       "default"), RTConsoleLogSink::$create);
suppliers.put(new Resource(markerFactoryType, "markers"), RTMarkerFactory::$create);
suppliers.put(new Resource(mdcType,           "mdc"),     RTMDC::$create);
```

`loggerType` etc. are resolved via the existing TypeConstant lookup machinery the same
way `consoleType` is. Note the `(loggerType, "logger")` exact-name entry — no wildcard.

**Done state of Stage 1:** `@Inject Logger logger;` returns a working root `Logger`
instance. `Logger.named(String)` derives per-name children. Tests pass. The
`runInjected()` and `runInjectedByName()` paths of `manualTests/.../TestLogger.x` both
print to the console.

### Stage 2 — Make the message actually format

Right now `logger.info("hello {}", ["world"])` emits the literal `hello {}`. Fixing
this is a pure-Ecstasy port of SLF4J's `MessageFormatter.format` state machine.

#### 2.1 — Real `MessageFormatter.format`

Port the algorithm from `org.slf4j.helpers.MessageFormatter` (it's a small, well-
defined state machine). Specifically:

- Walk the `message` character by character.
- Track escape state: `\{` is a literal `{`, `\\{` is a literal backslash followed by a
  placeholder.
- For each unescaped `{}`, consume the next argument's `toString()` and append.
- Excess placeholders left literal; excess arguments dropped.
- If the last argument is an `Exception` and there is no remaining placeholder,
  promote it to the returned `cause`.

#### 2.2 — Tests

Port a subset of SLF4J's `MessageFormatterTest` cases — empty patterns, single
placeholder, multi placeholder, escape handling, throwable promotion, mismatched
counts. ~10 cases is enough to be confident.

#### 2.3 — Wire into BasicLogger

Already wired (`BasicLogger.emit` calls `MessageFormatter.format`). Once the formatter
is real, the wiring works.

**Done state of Stage 2:** `logger.info("hello {}", ["world"])` outputs
`... INFO  Demo: hello world`. The throwable-promotion rule handles
`logger.warn("cleanup failed", [], cause=e)` correctly.

### Stage 3 — Re-enable MDC

#### 3.1 — Decide the scope

Pick one:

- **a) Per-fiber.** Matches Java ThreadLocal MDC. Requires the runtime to give us
  fiber-local storage. The `RTMDC` native impl uses that.
- **b) Per-service-instance.** Cheap to implement; semantically wrong for typical
  request-scoped use, because anything inside a non-trivial fiber tree shares a single
  MDC service, so concurrent requests would step on each other.

Recommendation: **(a)** if the runtime can support it — there is a known pattern in
the JVM bridge for context-local state. If not, ship (b) for v0 with a clear "this is
known-incorrect for concurrent use" warning in the doc, and revisit before any real
production user picks this up.

#### 3.2 — Re-enable `@Inject MDC mdc;` in `BasicLogger`

The injection was removed in v0 because the runtime didn't register the resource. Once
Stage 1 lands, restore the field and replace the `new HashMap<String, String>()` in
`emit()` with `mdc.copyOfContextMap`.

#### 3.3 — Render MDC in `ConsoleLogSink`

One-liner: append `[mdc=k1=v1,k2=v2]` to the formatted line if `event.mdcSnapshot` is
non-empty.

**Done state of Stage 3:** `mdc.put("requestId", id)` followed by
`logger.info("dispatch")` produces a line containing `[mdc=requestId=...]`.

### Stage 4 — Compiler-side default name (optional but high-impact)

Today `@Inject Logger logger;` (no `resourceName`) resolves to the root logger
literally named `"logger"` — the field-name fallback. Many SLF4J users find the
enclosing module/class name more useful as the default. The fix is a small XTC
compiler change.

#### 4.1 — Substitute the enclosing module name

When the compiler sees `@Inject Logger logger;` with `resourceName == Null`,
substitute the enclosing module's qualified name as the resource name. This is one
location in the compiler; the dispatch on type `Logger` is the only special case.

#### 4.2 — Document

Update `../usage/injected-logger-example.md` and `../usage/slf4j-parity.md` to reflect the implicit-
naming behaviour.

**Done state of Stage 4:** `@Inject Logger logger;` inside `module PaymentService`
emits lines tagged `PaymentService:`. The cheat sheet in
`../usage/injected-logger-example.md` becomes accurate without `@Inject("PaymentService")`.

This stage is **optional for the demo**. Without it, callers write
`@Inject Logger logger; Logger paymentLogger = logger.named("PaymentService");`. With
it, `@Inject Logger logger;` alone is already named after the enclosing module.

### Stage 5 — Validation: platform repo migration

The end-to-end test of "demo worthy" is migrating real code.

#### 5.1 — Pick three files in `~/src/platform`

`kernel/kernel.x`, `auth/OAuthProvider.x`, `host/HostManager.x` are the highest-
density `console.print($"... Info :")` files. See `../usage/platform-and-examples-adaptation.md`.

#### 5.2 — Migrate each

Mechanical: replace `console.print($"{common.logTime($)} Info : ...")` with
`logger.info("...")`. Drop the `common.logTime` helper after the last caller is
gone.

#### 5.3 — Confirm output

Run the platform; confirm log lines look as expected. Capture before/after samples for
the demo.

**Done state of Stage 5:** the platform repo's logging looks identical from outside
(maybe slightly cleaner); from inside, every log site is now an `@Inject Logger`
call. The before/after diff is the demo.

## Open questions still open after this plan

The plan above closes runtime-side gaps. These items remain genuinely unresolved and
need a decision separately:

- **Multiple markers per event** (`../open-questions.md` #4). API-level: should we change
  `LogEvent.marker: Marker?` to `LogEvent.markers: Marker[]` and update the fluent
  builder to accumulate? Cheap to do now, breaks callers later.
- **Service vs class for sinks** (`../open-questions.md` #6). Currently the recommendation
  is "don't require service"; the question is whether to formalize that in the
  interface signature.
- **Async / batched sinks** (`../open-questions.md` #7). Defer to `lib_logging_logback`?
  Or ship a simple `AsyncLogSink` wrapper here?
- **Per-container override convenience** (`../open-questions.md` #8). Probably "no
  helper, document the pattern" — but worth deciding before there are users.
- **Defensive copy of caller `Object[]`** (`../open-questions.md` #11). Now that args are
  frozen-on-the-way-into-the-event for builder calls, the per-level methods (which
  accept caller-supplied arrays) might still see mutation. Decide: copy in
  `BasicLogger.emit` always, or document caller-must-not-mutate.
- **Lazy logging lambdas** (`../future/lazy-logging.md`). When do we add the
  `info(function String () messageFn, ...)` overloads? Easy to add; question is
  timing.
- **Structured `keyValues` field on `LogEvent`** (`../usage/structured-logging.md`). Add now or
  with the first sink that consumes them?

## Recommended sequencing

```
Stage 1.1 RTLogger.java                   ┐
Stage 1.2 RTConsoleLogSink.java           ├─ same PR, ~half a day
Stage 1.3 RTMarkerFactory + RTMDC         │
Stage 1.4 wildcard resolution             │
Stage 1.5 register in nMainInjector       ┘
                  │
                  ▼   demo runs but message says "hello {}"
Stage 2 MessageFormatter port + tests   ── ~half a day
                  │
                  ▼   demo says "hello world"
Stage 3 MDC re-enable + render          ── ~2 hours
                  │
                  ▼   full v0.1 round trip
Stage 4 compiler-side default name      ── ~half a day, separate PR
                  │
                  ▼   feels like SLF4J
Stage 5 platform migration              ── ~1-2 days, follow-up PR
```

Total to demo-worthy: **2-3 working days**, split across one runtime PR and one
follow-up that polishes message formatting and MDC. The compiler change and the
platform migration are independent and can land later.

## What this plan does *not* cover

- The future `lib_logging_logback` module (`../future/logback-integration.md`). That's a
  separate, larger project for a configurable hierarchical backend.
- The native-Logback bridge approach (`../future/native-bridge.md`). Documented as feasible but
  not the primary path.
- Slog-style alternative API (`ALTERNATIVE_DESIGN_SLOG_STYLE.md`). Documented as a
  thinkable alternative but not pursued.
- Performance tuning (allocation, async). Premature; revisit when the platform repo
  has been on `lib_logging` for long enough that we have data.

## Owner-readable bullet summary

If you want this in three lines:

- **Two days of Java work** in `javatools_jitbridge` makes `@Inject Logger` work.
- **Half a day of Ecstasy work** ports the SLF4J `{}` substitution and turns
  `hello {}` into `hello world`.
- **Optional half day of compiler work** turns `@Inject Logger` into the
  module-scoped logger SLF4J users expect, instead of always falling back to
  `"default"`.

After that, the platform-repo migration PR is the demo.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
