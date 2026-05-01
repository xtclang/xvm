# lib_logging — Design

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
              │  NoopLogSink                     │
              │  MemoryLogSink                   │
              │  (future) LogbackLogSink         │
              │  (future) NativeSlf4jLogSink     │
              └──────────────────────────────────┘
```

User code holds a `Logger`. The `Logger` is a `BasicLogger` that holds a `LogSink`. The
sink is whatever the runtime injected. Everything above the `LogSink` line is sink-agnostic
and stable; everything below is swappable.

This is the same architecture the SLF4J full guide describes (`slf4j_full_guide.md` at
the repo root, section "Architecture") — it is a deliberate decision to mirror it because
SLF4J got the layering right, and copying it gets us instant familiarity for free.

## Why injection (and why per-name loggers come from `Logger.named(String)`)

Ecstasy already routes `Console` through `@Inject Console console;` — see
`lib_ecstasy/src/main/x/ecstasy/io/Console.x` and `javatools_jitbridge/.../TerminalConsole.java`.
The runtime controls which `Console` impl gets wired up. Loggers fit the same shape:
the runtime decides where output goes (in v0, always `ConsoleLogSink`; later, possibly a
configurable `LogbackLogSink`), and user code is sink-agnostic.

`@Inject Logger logger;` resolves to a single fixed-name root logger — exactly one
supplier (`("logger", loggerType)`) is registered with the injector. Per-name loggers
are *derived* via `Logger.named(String)` rather than acquired through additional
injection sites:

```ecstasy
@Inject Logger logger;
static Logger PaymentLogger = logger.named("payments");
```

This mirrors SLF4J's `LoggerFactory.getLogger(MyClass.class)` idiom one-to-one, and
keeps the injector's resource table as the single registry of allowed injections —
no type-only wildcard fallback, no special-case for `Logger`. See
`RUNTIME_IMPLEMENTATION_PLAN.md` Stage 1.4 for the full rationale.

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
addKeyValue / log`. The `at*()` methods on `Logger` short-circuit to a no-op builder
when the level is disabled, so callers don't pay for argument construction that won't
be used.

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
- A future `FileLogSink` owning a `Writer` — `service`.
- A future `AsyncLogSink` owning a worker queue — `service`.

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
`LogSink` split into two interfaces? See `OPEN_QUESTIONS.md` (item 6).

## Future: `lib_logging_logback`

A separate module providing a configuration-driven sink. Reads a config tree
(programmatic, possibly XML or JSON) describing:

- per-logger thresholds (`com.example.foo` at `Debug`, root at `Info`);
- appenders (Console, File, Rolling, Network, JSON);
- layouts (PatternLayout, JSONLayout);
- filters (level, marker, MDC, regex on message);
- async wrappers.

The whole thing is just a different `LogSink` implementation. User code that already
worked against `lib_logging` keeps working. Detailed sketch in `LOGBACK_INTEGRATION.md`.

## Native bridge — could we wrap real Logback?

Yes, technically. `javatools_jitbridge` already imports `jline` (a third-party Java
library) for the terminal console, proving the bridge can carry external dependencies.
A `RTLogbackSink.java` extending `nService` and registered in
`nMainInjector.addNativeResources()` could wrap `org.slf4j.Logger` directly. SLF4J and
Logback are already in the version catalog (`lang-slf4j`, `lang-logback`), used by the
lang tooling.

We don't recommend this as the primary path — see `NATIVE_BRIDGE.md` for the full
analysis — but it's a feasible escape hatch and worth documenting because it constrains
the design.

## Per-container sink override

Each Ecstasy container has its own injector. Host code that wants a nested
container to use a *different* sink than the host's default does so by
configuring the child container's injector explicitly — there is no
`lib_logging`-side helper for this, by design (see `OPEN_QUESTIONS.md` item 8).

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

## What isn't here yet

- **Compiler-side default name from module** — `@Inject Logger logger;` (no
  `resourceName`) currently gets the fixed-name root logger; users derive per-class
  loggers via `logger.named("...")`. The XTC-compiler change to substitute the
  enclosing module's qualified name is `RUNTIME_IMPLEMENTATION_PLAN.md` Stage 4 and
  remains open.
- **`AsyncLogSink` / `lib_logging_logback` / native bridge** — see
  `OPEN_QUESTIONS.md` items 7 (async) and the `LOGBACK_INTEGRATION.md` /
  `NATIVE_BRIDGE.md` follower-module sketches.
- **Per-container override convenience** — open question 8.

The runtime-side injection wiring lives in
`javatools/src/main/java/org/xvm/runtime/NativeContainer.java` (`ensureLogger`,
`ensureConst`); `BasicLogger` is the `const` returned for `@Inject Logger`. The
earlier interpose service `xRTLogger.java` was removed in favour of constructing
`BasicLogger` directly so MDC fiber-locals survive injection (see Q-D5 in
`OPEN_QUESTIONS.md`). The real `MessageFormatter` is implemented (12 tests in
`MessageFormatterTest`). Tests live in `lib_logging/src/test/x/LoggingTest/` (51
passing as of this commit).


---

_See also [README.md](README.md) for the full doc index and reading paths._
