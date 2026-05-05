# lib_logging — Design

> **Audience:** anyone reading `lib_logging` source. Architecture and the API↔impl boundary.

## High-level architecture

```
              ┌──────────────────────────────────┐
  user code → │  Logger          (interface)     │  ← public API
              │  Level / Marker / MDC / LogEvent │
              │  LoggingEventBuilder             │
              │  LoggerFactory                   │
              ├──────────────────────────────────┤
              │  BasicLogger / BasicEventBuilder │  ← canonical impl of the API
              │  MessageFormatter                │
              ├──────────────────────────────────┤
              │  LogSink         (interface)     │  ← API ↔ impl boundary
              ├──────────────────────────────────┤
              │  ConsoleLogSink                  │  ← shipped impls
              │  JsonLogSink                      │
              │  CompositeLogSink                 │
              │  HierarchicalLogSink              │
              │  AsyncLogSink                     │
              │  NoopLogSink                     │
              │  MemoryLogSink                   │
              │  (future) configured/destination │
              │           sinks                   │
              └──────────────────────────────────┘
```

User code holds a `Logger`. The `Logger` is a `BasicLogger` that holds a `LogSink`. The
sink is whatever the runtime injected. Everything above the `LogSink` line is sink-agnostic
and stable; everything below is swappable.

This mirrors the layering that makes SLF4J/Logback durable in Java: the public facade
is small, the backend boundary is narrow, and concrete output policy lives behind that
boundary. The Ecstasy version keeps that shape but makes injection the acquisition
mechanism and keeps `LogSink` as the XDK-native extension point.

## Why injection (and why per-name loggers come from `Logger.named(String)`)

Ecstasy already routes `Console` through `@Inject Console console;`; the runtime
controls which `Console` implementation gets wired up. Loggers fit the same shape: the
runtime decides where output goes (in v0, `ConsoleLogSink`; later, possibly a
configured sink graph), and user code is sink-agnostic.

`@Inject Logger logger;` resolves to a single fixed-name root logger — exactly one
supplier (`("logger", loggerType)`) is registered with the injector. Per-name loggers
are *derived* via `Logger.named(String)` rather than acquired through additional
injection sites:

```ecstasy
@Inject Logger logger;
static Logger PaymentLogger = logger.named("payments");
```

This mirrors SLF4J's `LoggerFactory.getLogger(MyClass.class)` idiom one-to-one, and
keeps the injector's resource table as the single registry of allowed injections:
there is one fixed `"logger"` resource, and per-name loggers are derived in XTC rather
than acquired through wildcard injection.

## API surface

### `Logger`

The user-facing facade. Five level methods (`trace`, `debug`, `info`, `warn`, `error`),
each accepting `(message, arguments, cause, marker)` with sensible defaults. Five level
checks (`infoEnabled`, etc.). A polymorphic `log(level, ...)`. A fluent `atInfo()` /
`atLevel(...)` family that returns a `LoggingEventBuilder`.

The signatures intentionally collapse SLF4J's many overloads into one canonical signature
per level using Ecstasy's default-argument support. The fluent builder covers the cases
where the parameter list isn't known statically.

### `Level`

`Trace, Debug, Info, Warn, Error, Off`. `Off` is sink-side only; never emitted.
`severity` field for cheap threshold comparisons.

### `Marker` / `MarkerFactory` / `BasicMarker`

`MarkerFactory.getMarker(name)` returns interned markers; `getDetachedMarker(name)`
returns fresh ones. Markers can have child references (DAG), and `contains` does the
transitive check. The default `BasicMarker` is straight from the SLF4J playbook —
intentionally minimal, the rich filtering is the sink's job.

### `MDC`

Service exposing `put / get / remove / clear / copyOfContextMap`. Sinks that care about
MDC capture `copyOfContextMap` when emitting; the snapshot is included in `LogEvent`.

### `LogEvent`

Immutable `const` record carrying loggerName, level, message (already substituted),
marker, exception, arguments, mdc snapshot, thread/fiber name, timestamp.

### `LogSink`

Two methods: `isEnabled(loggerName, level, marker)` and `log(event)`. This is the
boundary; everything below is replaceable.

### `LoggingEventBuilder`

SLF4J 2.x fluent builder: `setMessage / addArgument / addMarker / setCause /
addKeyValue / log`, plus lazy `setMessage`, `log`, `addLazyArgument`, and
`addLazyKeyValue` forms. The builder checks the sink at final `log(...)` time after
markers are attached. If disabled, it does not format, snapshot MDC, allocate a
`LogEvent`, or invoke lazy suppliers. Eager argument expressions still run before the
method call, exactly as in Java; use the explicit lazy methods for expensive values.

## Default implementations

### `BasicLogger`

Holds `(name, sink)`. Each emission method:
1. Calls `sink.isEnabled(...)`. If False, return immediately.
2. Calls `MessageFormatter.format(message, arguments)` to substitute placeholders and
   detect the SLF4J throwable-promotion case.
3. Snapshots MDC.
4. Constructs a `LogEvent` and hands it to the sink.

### `ConsoleLogSink`

`const` (not service) holding `@Inject Console console;` and a construct-time `rootLevel`
threshold (default `Info`). Emits one line per event in a fixed format: timestamp, thread,
padded level, logger name, message, optional `[marker=NAME]` suffix; appends the
exception on a following line.

### `NoopLogSink`

`const`. Drops everything. `isEnabled` returns False so callers that respect the check
skip all formatting work. Useful for libraries that want quiet defaults.

### `MemoryLogSink`

`service` (mutable `events[]` shared across fibers). Captures events in an array.
Test-only; not registered as a default.

## Sink type: `const` vs `service`

`BasicLogger` is itself a `const`. Its `sink: LogSink` field therefore requires
implementations to be `Passable`, i.e. either `immutable` (a `const`) or a `service`.
The choice between the two is not arbitrary — it follows a clean rule that mirrors what
both the SLF4J/Logback ecosystem and the surrounding XDK codebases already do:

| Property | use `const` | use `service` |
|---|---|---|
| Has its own mutable state shared across fibers? | no | yes |
| Aggregates events for later inspection? | no | yes |
| Owns external resources (writers, sockets, queues)? | no | yes |
| Pure forwarder over an injected service? | yes | — |
| Configuration fixed at construction? | yes | (typically yes either way) |

Concretely, in this library:

- `NoopLogSink` — `const` (stateless).
- `ConsoleLogSink` — `const` (forwarder over `@Inject Console`, threshold fixed at
  construction). Structurally identical to
  `lib_ecstasy/src/main/x/ecstasy/io/ConsoleAppender.x` and `ConsoleLog.x`, both `class`.
- `MemoryLogSink`, `ListLogSink` — `service` (collect `events[]`).
- `AsyncLogSink` — `service` (owns a bounded queue and drain state).
- `HierarchicalLogSink` — `service` (owns mutable per-prefix level configuration).
- A future `FileLogSink` owning a `Writer` — `service`.

This matches the pattern used elsewhere in the XDK / platform ecosystem:

- `service ErrorLog extends SimpleLog` — `platform/common/src/main/x/common.x:23`,
  collects log entries from many callers.
- `service ConsoleExecutionListener implements ExecutionListener` —
  `xvmrepo/lib_xunit_engine/.../console/ConsoleExecutionListener.x:4`, captures test
  events into a private `Map`. **Structurally identical to `MemoryLogSink`.**
- `service HostManager` with `Map<String, CircularBuffer<Int>> activityHistogram` —
  `platform/host/.../HostManager.x:40`.
- `class ConsoleAppender(Console console, ...)` — `lib_ecstasy/.../ConsoleAppender.x:4`,
  no own mutable state.

Mapping back to Logback: Logback collapses both shapes into one `Appender` interface
(everything is a Java class with a `synchronized log()` method). In Ecstasy the
service/const distinction makes the intent explicit at the type level — and is enforced
by the compiler whenever a sink is held by a `const` such as `BasicLogger`.

Open question: is one-interface-with-two-allowed-impl-shapes the right call, or should
`LogSink` split into two interfaces? See `../open-questions.md` (item 6).

## Future: `lib_logging_logback`

A separate module providing a configuration-driven sink. Reads a config tree
(programmatic, possibly XML or JSON) describing:

- per-logger thresholds (`com.example.foo` at `Debug`, root at `Info`);
- appenders (Console, File, Rolling, Network, JSON);
- layouts (PatternLayout, JSONLayout);
- filters (level, marker, MDC, regex on message);
- async wrappers.

The whole thing is just a different `LogSink` implementation. User code that already
worked against `lib_logging` keeps working. Detailed sketch in `../future/logback-integration.md`.

## Per-container sink override

Each Ecstasy container has its own injector. Host code that wants a nested
container to use a *different* sink than the host's default does so by
configuring the child container's injector explicitly — there is no
`lib_logging`-side helper for this, by design (see `../open-questions.md` item 8).

The pattern matches how every other injectable resource in the XDK is
overridden per child container:

```ecstasy
import logging.Logger;
import logging.LogSink;
import logging.MemoryLogSink;

// Inside host code that's about to spawn a child container:
LogSink childSink = new MemoryLogSink();   // or any other LogSink

// When constructing the child container, pass an injector that resolves
// `("logger", Logger)` against `childSink` instead of the host's default.
// The exact host API depends on which container-construction helper the host
// uses; the principle is the same everywhere — replace the resource supplier
// for the `Logger` injection key.
```

In practice, host runtimes typically expose a `withResource(...)` /
`addResourceSupplier(...)` style API on the container's injector; the host
adds an entry `("logger", Logger) → new BasicLogger(name, childSink)` before
the child container starts running. The child container's `@Inject Logger`
resolves to that supplier; the host's loggers are unaffected.

We deliberately do not ship a `ContainerLoggingConfig.set(child, sink)`
convenience because:

1. The container/injector API is the right place for *all* per-container
   resource overrides; every library inventing its own helper would create N
   parallel ways to do the same thing.
2. Real-world usage of this is rare — most programs run with a single sink
   per process. Adding a helper now would lock in an API for a use case we
   haven't seen demand for yet.

If a future user needs this often enough that a helper is worth it, the
helper lives in the host runtime's injector library, not here.

## Non-goals (v0)

Items deliberately *not* in scope for the base POC of `lib_logging`.

- **Distributed tracing context propagation.** `MDC` carries strings; that's
  it. A future tracing library can write its trace ID into MDC and let the
  logging library carry it, but `lib_logging` itself does not implement
  trace/span propagation.
- **Configuration file format.** The default sink has one knob (`rootLevel`)
  set at construction, and `JsonLogSinkOptions` covers JSON field names,
  inclusion, threshold, and redaction. A real config story (XML / programmatic /
  hot reload) belongs to the future Logback-style backend;
  `../future/logback-integration.md` sketches it.
- **Automatic call-site source capture.** `Logger.logAt(...)` gives the runtime/compiler
  a stable lowering target and sinks can render `sourceFile` / `sourceLine`, but the
  compiler does not yet synthesize those arguments for ordinary `logger.info(...)`
  calls.

## What isn't here yet

- **Compiler-side exact default names** — the interpreter runtime now falls back from
  the field name `"logger"` to the caller namespace for canonical `logging.Logger`
  injections. A compiler pass can still improve this by emitting the exact
  module/class logger name as the resource name.
- **External Logback-style configuration loader** — the base backend
  primitives (`AsyncLogSink`, `CompositeLogSink`, `HierarchicalLogSink`, `JsonLogSink`)
  are in this module. XML/JSON config loading, rolling files, network destinations, and
  filters remain explicit follow-up modules; see `../future/logback-integration.md`.
- **Per-container override convenience** — open question 8.

The temporary interpreter-side injection wiring lives in
`javatools/src/main/java/org/xvm/runtime/NativeContainer.java` (`ensureLogger`,
`ensureConst`); `BasicLogger` is the `const` returned for `@Inject Logger` so MDC
fiber-locals survive injection. The real `MessageFormatter` is implemented (12 tests in
`MessageFormatterTest`). Tests live in `lib_logging/src/test/x/LoggingTest/` (66
passing as of this commit).

---

Previous: [`../cloud-integration.md`](../cloud-integration.md) | Next: [`why-slf4j-and-injection.md`](why-slf4j-and-injection.md) → | Up: [`../README.md`](../README.md)
