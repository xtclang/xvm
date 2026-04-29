# Runtime implementation plan — getting to a demo-worthy round trip

This document is the actionable plan that turns the v0 stub (`@Inject Logger logger;`
parses but does not resolve) into a **working end-to-end demo**: the Ecstasy line

```ecstasy
@Inject Logger logger;
logger.info("hello {}", ["world"]);
```

producing real output on the platform `Console` via the runtime injector. Every item
here either is on the critical path to that demo, or is needed to call the round trip
"complete" (parameterized messages actually substitute, MDC actually propagates, etc.).

The doc supersedes `OPEN_QUESTIONS.md` items 1, 2, 3, 5, 9, 10 by prescribing concrete
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

2. The same demo with `@Inject("com.example") log.Logger logger;` resolves to a
   logger named `com.example` (output line carries the name).

3. `logger.error("failed: {}", [id], cause=new Exception("boom"));` emits both the
   message line and the exception text.

4. `logger.atInfo().addMarker(AUDIT).addKeyValue("k", v).log("msg")` works through the
   fluent builder.

5. MDC `mdc.put("requestId", id)` shows up alongside any subsequent emission (rendered
   by a sink that cares; default `ConsoleLogSink` would gain a small change to render
   MDC entries).

6. The 18 unit tests still pass; one new manualTest invokes `runInjected()` and
   succeeds (today the `runInjected()` path catches the unresolved-injection
   exception).

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

A native service that wraps an Ecstasy `BasicLogger` instance. The `$create(Object
opts)` factory receives the resource name (the string passed in `@Inject("foo")
Logger`) and bakes it into the constructed logger:

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

#### 1.4 — Wildcard-name resolution in `nMainInjector`

`javatools_jitbridge/.../mgmt/nMainInjector.java`. Today `supplierOf(Resource)` does
exact `(TypeConstant, String)` lookup. Modify to:

- First try the exact key.
- If miss and the type is `Logger`, fall back to a wildcard entry registered as
  `(loggerType, "*")`. The factory receives the requested resource name as `opts` and
  produces a logger of that name.

Eight or ten lines of new Java. Opts to make it generic over types if the same need
arises elsewhere, but for v0 a `Logger`-specific fallback is fine.

#### 1.5 — Registration in `addNativeResources()`

The single-line additions:

```java
suppliers.put(new Resource(loggerType,        "*"),       RTLogger::$create);
suppliers.put(new Resource(logSinkType,       "default"), RTConsoleLogSink::$create);
suppliers.put(new Resource(markerFactoryType, "markers"), RTMarkerFactory::$create);
suppliers.put(new Resource(mdcType,           "mdc"),     RTMDC::$create);
```

`loggerType` etc. are resolved via the existing TypeConstant lookup machinery the same
way `consoleType` is.

**Done state of Stage 1:** `@Inject Logger logger;` returns a working `Logger`
instance. Tests still pass. The `runInjected()` path of `manualTests/.../TestLogger.x`
runs without throwing.

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

Today `@Inject Logger logger;` (no `resourceName`) resolves to a logger named
`"default"`. Every SLF4J user instinctively expects the logger to be named after the
enclosing module/class. The fix is a small XTC compiler change.

#### 4.1 — Substitute the enclosing module name

When the compiler sees `@Inject Logger logger;` with `resourceName == Null`,
substitute the enclosing module's qualified name as the resource name. This is one
location in the compiler; the dispatch on type `Logger` is the only special case.

#### 4.2 — Document

Update `INJECTED_LOGGER_EXAMPLE.md` and `SLF4J_PARITY.md` to reflect the implicit-
naming behaviour.

**Done state of Stage 4:** `@Inject Logger logger;` inside `module PaymentService`
emits lines tagged `PaymentService:`. The cheat sheet in
`INJECTED_LOGGER_EXAMPLE.md` becomes accurate without `@Inject("PaymentService")`.

This stage is **optional for the demo**. Without it, the demo writes
`@Inject("PaymentService") Logger logger;` explicitly. With it, the demo is one line
shorter and matches SLF4J muscle memory.

### Stage 5 — Validation: platform repo migration

The end-to-end test of "demo worthy" is migrating real code.

#### 5.1 — Pick three files in `~/src/platform`

`kernel/kernel.x`, `auth/OAuthProvider.x`, `host/HostManager.x` are the highest-
density `console.print($"... Info :")` files. See `PLATFORM_AND_EXAMPLES_ADAPTATION.md`.

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

- **Multiple markers per event** (`OPEN_QUESTIONS.md` #4). API-level: should we change
  `LogEvent.marker: Marker?` to `LogEvent.markers: Marker[]` and update the fluent
  builder to accumulate? Cheap to do now, breaks callers later.
- **Service vs class for sinks** (`OPEN_QUESTIONS.md` #6). Currently the recommendation
  is "don't require service"; the question is whether to formalize that in the
  interface signature.
- **Async / batched sinks** (`OPEN_QUESTIONS.md` #7). Defer to `lib_logging_logback`?
  Or ship a simple `AsyncLogSink` wrapper here?
- **Per-container override convenience** (`OPEN_QUESTIONS.md` #8). Probably "no
  helper, document the pattern" — but worth deciding before there are users.
- **Defensive copy of caller `Object[]`** (`OPEN_QUESTIONS.md` #11). Now that args are
  frozen-on-the-way-into-the-event for builder calls, the per-level methods (which
  accept caller-supplied arrays) might still see mutation. Decide: copy in
  `BasicLogger.emit` always, or document caller-must-not-mutate.
- **Lazy logging lambdas** (`LAZY_LOGGING.md`). When do we add the
  `info(function String () messageFn, ...)` overloads? Easy to add; question is
  timing.
- **Structured `keyValues` field on `LogEvent`** (`STRUCTURED_LOGGING.md`). Add now or
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

- The future `lib_logging_logback` module (`LOGBACK_INTEGRATION.md`). That's a
  separate, larger project for a configurable hierarchical backend.
- The native-Logback bridge approach (`NATIVE_BRIDGE.md`). Documented as feasible but
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
