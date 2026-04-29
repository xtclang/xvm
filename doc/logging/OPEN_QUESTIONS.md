# Open questions for `lib_logging`

A running list of unresolved design and implementation questions. Each entry includes
the trade-offs as currently understood and a tentative recommendation. None of these
are blockers for the v0 stub; they are blockers for "v0 actually works end-to-end".

## 1. Wildcard-name injection in `nMainInjector`

The runtime injection key is `(TypeConstant, String name)` and resolves by exact match.
For `@Inject Logger logger`, the empty/default name is fine. For
`@Inject("com.example.foo") Logger logger`, the name varies per call site and cannot be
exhaustively enumerated.

**Options:**

- **A. Wildcard match.** Make `nMainInjector.supplierOf` fall back to a wildcard entry
  (`"*"`) when no exact match is found. The factory receives the requested name as
  `opts` and bakes it into the proxy. This is one-method change.
- **B. Generalize Resource resolution.** Make `Resource` keys support type-only entries
  with name passed through. Bigger change, broader blast radius.

**Tentative recommendation:** A.

## 2. Default logger name when no `resourceName` supplied

`@Inject Logger logger;` has no name. What should the resulting logger's name be?

**Options:**

- **A. The literal string `"default"`.** Simple, predictable, but means every
  injection-without-name across an application maps to the same logger.
- **B. The enclosing module's qualified name.** Requires the XTC compiler to substitute
  the name at compile time. More useful for hierarchical logger configuration but is a
  compiler-side change.
- **C. The fully-qualified name of the enclosing class/method.** Like SLF4J's
  `LoggerFactory.getLogger(MyClass.class)`. Strictly the most useful default.

**Tentative recommendation:** A for v0 (no compiler change). Track B/C as a follow-up
once the rest of the wiring is solid.

## 3. MDC scope: per-fiber, per-service, or per-call?

`MDC` carries context that should follow a request through nested calls without being
threaded explicitly. The right scope depends on Ecstasy's concurrency story.

**Options:**

- **A. Per-fiber.** Each fiber sees its own context. Matches Java's `ThreadLocal` MDC
  semantics. Requires runtime support for per-fiber locals.
- **B. Per-service.** Every service instance has its own MDC. Simplest; matches the
  `service` keyword's existing isolation model.
- **C. Explicit propagation.** Loggers carry their own MDC; deriving a logger derives
  the context. This is the slog-style approach.

**Tentative recommendation:** A if Ecstasy gives us per-fiber locals; B if not. C is the
"Logger.with" path documented in `ALTERNATIVE_DESIGN_SLOG_STYLE.md` and would be a
separate API addition rather than a replacement.

## 4. Multiple markers per event

SLF4J 2.x's `LoggingEventBuilder.addMarker` accepts repeated calls and stores all of
them on the event. Our `BasicEventBuilder` currently keeps only the most recently added
one.

**Options:**

- **A. Match SLF4J — list of markers per event.** Requires `LogEvent.marker` →
  `Marker[] markers`. Affects `LogSink` callers everywhere.
- **B. Keep one marker, document the limitation.** Sinks that need multiple markers
  can use child references on a single marker (the `Marker.add(other)` mechanism).

**Tentative recommendation:** A — match SLF4J. The cost is a one-line type change and a
small loop in the few sinks that read it; doing this in v0 is much cheaper than
breaking callers later.

## 5. Throwable promotion: where does it happen?

SLF4J's rule: a trailing `Throwable` argument with no matching placeholder is treated as
the cause. Today our `MessageFormatter.format` returns `(formatted, cause?)` so the
formatter is the right place. But callers can also pass `cause` explicitly. Which wins?

**Options:**

- **A. Explicit `cause` always wins.** If the caller passed `cause=e`, ignore any
  promoted-from-args throwable.
- **B. Promoted always wins.** Surprising; callers expect their explicit `cause` to be
  honoured.
- **C. Error if both supplied.** Programming error.

**Tentative recommendation:** A. Document explicitly.

## 6. Service vs class for sinks

Most `LogSink` implementations want to be services (mutable state — counters, queues,
file handles). Should the contract require it?

**Options:**

- **A. Recommend, don't require.** `LogSink` is an interface; implementations choose.
- **B. Require.** `interface LogSink extends Service` (or whatever the Ecstasy
  equivalent is).

**Tentative recommendation:** A. Forcing service-hood breaks the trivial cases
(`NoopLogSink` doesn't need it; testing helpers may not need it). Users who build
stateful sinks will reach for `service` naturally.

## 7. Async / batched sinks

The default sinks are synchronous. A real production system wants async to keep slow
I/O off the caller path. When and how do we ship one?

**Options:**

- **A. Ship `AsyncLogSink` as a wrapper in `lib_logging`.** Caller does
  `new AsyncLogSink(new ConsoleLogSink())`. Worker fiber drains a bounded queue.
- **B. Defer to `lib_logging_logback`.** That module is the natural home for the
  async-appender story; keeping `lib_logging` minimal is good.

**Tentative recommendation:** B. Ship the basics in v0; async lives in the configurable
backend.

## 8. Configuration override per container

Ecstasy's container model allows nested containers each with their own injector. A
guest module nested in a host could in principle want a different logger sink than the
host. Today the `nMainInjector.suppliers` map is per-instance, which is the right
foundation. The question is the *user-facing API*: how does a host install a different
sink for one nested container?

**Options:**

- **A. Document that the host configures its child container's injector explicitly.**
  No API addition.
- **B. Provide a helper, e.g. `ContainerLoggingConfig.set(child, sink)`.**

**Tentative recommendation:** A for v0. Revisit if there's demand.

## 9. Where does the runtime live?

The `BasicLogger` and the sink classes are pure Ecstasy. The runtime piece — the actual
`@Inject Logger` resolution — needs Java code in `javatools_jitbridge`. The directory
layout question:

- `javatools_jitbridge/src/main/java/org/xtclang/_native/logging/RTLogger.java`
- `javatools_jitbridge/src/main/java/org/xtclang/_native/logging/RTLogSink.java`
- Registration in `javatools_jitbridge/src/main/java/org/xtclang/_native/mgmt/nMainInjector.java`

Convention to confirm with a maintainer; mirrors `_native/io/TerminalConsole.java`.

## 10. Default sink: pure-Ecstasy or a thin native bootstrap?

The default `ConsoleLogSink` is written in Ecstasy and goes through `@Inject Console`.
Bootstrap-wise, this means the sink resolution has to happen *after* `Console` is
registered. This is fine in practice (both are registered in `addNativeResources`) but
it's an ordering constraint worth noting.

If for any reason `ConsoleLogSink` cannot be resolved during early-runtime logging,
should there be a tiny native fallback that `System.err.println`s? SLF4J does this with
`SubstituteLoggerFactory`.

**Tentative recommendation:** Yes, a tiny native fallback. It's a few lines and removes
an entire class of "logger isn't ready yet" failure mode.

## 11. LogEvent immutability and `Object[]` arguments

`LogEvent` is a `const`. The `arguments: Object[]` field, however, holds caller-supplied
references. If the caller mutates the array between `Logger.info(...)` returning and an
async sink consuming the event, the sink sees the mutation.

**Options:**

- **A. Defensive copy at the `BasicLogger.emit` boundary.** One allocation per emission.
- **B. Document that callers must not mutate the array.** Match SLF4J's posture (which
  is the same).

**Tentative recommendation:** B for v0. Reconsider if async sinks become common.

## 12. Compiler/tooling support for log statements

SLF4J has `slf4j-tools` that lints for things like `info("count: " + n)` (eagerly
formatted instead of using `{}`) and missing exception arguments. Would Ecstasy benefit?

**Tentative recommendation:** Out of scope for `lib_logging`. If we want it, it's an
XTC compiler/linter feature, not a library feature.
