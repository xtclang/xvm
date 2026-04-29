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

## Why named injection

Ecstasy already routes `Console` through `@Inject Console console;` — see
`lib_ecstasy/src/main/x/ecstasy/io/Console.x` and `javatools_jitbridge/.../TerminalConsole.java`.
The runtime controls which `Console` impl gets wired up. Loggers fit the same shape:
the runtime decides where output goes (in v0, always `ConsoleLogSink`; later, possibly a
configurable `LogbackLogSink`), and user code is sink-agnostic.

The injection key is `(TypeConstant, String name)`; for `@Inject Logger`, the empty/default
name resolves to the global default logger. For `@Inject("com.example") Logger`, the name
becomes the logger's name. See `OPEN_QUESTIONS.md` for the wildcard-name resolution
change required in `nMainInjector.supplierOf` — the existing `Resource` map is exact-match.

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

Service holding `@Inject Console console;` and a `rootLevel` threshold (default `Info`).
Emits one line per event in a fixed format: timestamp, thread, padded level, logger name,
message, optional `[marker=NAME]` suffix; appends the exception on a following line.

### `NoopLogSink`

Drops everything. `isEnabled` returns False so callers that respect the check skip all
formatting work. Useful for libraries that want quiet defaults.

### `MemoryLogSink`

Captures events in an array. Test-only; not registered as a default.

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

## What isn't here yet

- The runtime-side injection wiring. The `BasicLogger` and the sink classes exist as
  Ecstasy code, but `@Inject Logger` doesn't actually resolve to one until
  `RTLogger.java` is added in `javatools_jitbridge` and registered in `nMainInjector`.
- The real `MessageFormatter`. Currently a stub that returns the message unchanged.
- Tests. None yet; the `manualTests/` sample is the next step.
- Compiler-side default name from module — open question.
