# Logging API cross-reference

This page maps the Ecstasy POC types to the well-known APIs they intentionally
mirror. Use it when reading the source: the point is not "we copied Java" or "we
copied Go", but "we reused the smallest familiar shape, then adapted acquisition,
services, consts, and fiber context to Ecstasy."

Primary references:

- SLF4J API docs: [`org.slf4j.Logger`](https://www.slf4j.org/api/org/slf4j/Logger.html),
  [`LoggerFactory`](https://www.slf4j.org/api/org/slf4j/LoggerFactory.html),
  [`Marker`](https://www.slf4j.org/api/org/slf4j/Marker.html),
  [`MarkerFactory`](https://www.slf4j.org/api/org/slf4j/MarkerFactory.html),
  [`MDC`](https://www.slf4j.org/api/org/slf4j/MDC.html),
  [`LoggingEventBuilder`](https://www.slf4j.org/apidocs/org/slf4j/spi/LoggingEventBuilder.html),
  [`MessageFormatter`](https://www.slf4j.org/api/org/slf4j/helpers/MessageFormatter.html),
  [`Level`](https://www.slf4j.org/api/org/slf4j/event/Level.html),
  [`LoggingEvent`](https://www.slf4j.org/api/org/slf4j/event/LoggingEvent.html).
- Go `log/slog` package docs: [`log/slog`](https://pkg.go.dev/log/slog).

## SLF4J-shaped `lib_logging`

| Ecstasy implementation | Prior-art API | Same idea | Ecstasy difference |
|---|---|---|---|
| [`Logger`](../../lib_logging/src/main/x/logging/Logger.x) | SLF4J [`Logger`](https://www.slf4j.org/api/org/slf4j/Logger.html) | Per-level methods, level checks, parameterized `{}` messages, markers, fluent `atInfo()` family. | Ecstasy collapses SLF4J's many overloads into default arguments. Acquisition is `@Inject Logger logger;` plus `logger.named("full.name")`, not a static classpath binding. |
| [`BasicLogger`](../../lib_logging/src/main/x/logging/BasicLogger.x) | SLF4J binding logger implementations such as `SimpleLogger` / Logback logger | One canonical implementation funnels all call shapes through a single emission path. | It is a `const` over a `LogSink`, so calls run on the caller's fiber and can see `MDC` `SharedContext` state. |
| [`LoggerFactory`](../../lib_logging/src/main/x/logging/LoggerFactory.x), [`LoggerRegistry`](../../lib_logging/src/main/x/logging/LoggerRegistry.x) | SLF4J [`LoggerFactory`](https://www.slf4j.org/api/org/slf4j/LoggerFactory.html) / `ILoggerFactory` | Name-keyed logger acquisition and identity-stable lookup. | The factory is an injected service escape hatch, not the primary acquisition path. The registry is scoped to one sink/container instead of JVM-global classpath state. |
| [`Level`](../../lib_logging/src/main/x/logging/Level.x) | SLF4J [`org.slf4j.event.Level`](https://www.slf4j.org/api/org/slf4j/event/Level.html) | `Trace`, `Debug`, `Info`, `Warn`, `Error`. | Adds sink-side `Off` and exposes integer `severity` for cheap threshold checks. |
| [`Marker`](../../lib_logging/src/main/x/logging/Marker.x), [`BasicMarker`](../../lib_logging/src/main/x/logging/BasicMarker.x), [`MarkerFactory`](../../lib_logging/src/main/x/logging/MarkerFactory.x) | SLF4J [`Marker`](https://www.slf4j.org/api/org/slf4j/Marker.html), [`MarkerFactory`](https://www.slf4j.org/api/org/slf4j/MarkerFactory.html) | Named event tags, child-marker containment, detached vs interned markers, multiple markers through the fluent API. | `Marker` also extends XDK `Freezable`, `Stringable`, and `Hashable` so markers can ride on `const LogEvent`s and cross service boundaries. |
| [`MDC`](../../lib_logging/src/main/x/logging/MDC.x) | SLF4J [`MDC`](https://www.slf4j.org/api/org/slf4j/MDC.html) | Request-scoped string map captured on each event. | Acquired by `@Inject MDC mdc;` and backed by `SharedContext<immutable Map>` for per-fiber semantics instead of Java static thread-local access. |
| [`MessageFormatter`](../../lib_logging/src/main/x/logging/MessageFormatter.x) | SLF4J [`MessageFormatter`](https://www.slf4j.org/api/org/slf4j/helpers/MessageFormatter.html) | `{}` substitution, escape handling, and trailing-exception promotion. | Returns `(String, Exception?)`; an explicit `cause=` on the logger call wins over a promoted trailing exception. |
| [`LoggingEventBuilder`](../../lib_logging/src/main/x/logging/LoggingEventBuilder.x), [`BasicEventBuilder`](../../lib_logging/src/main/x/logging/BasicEventBuilder.x) | SLF4J [`LoggingEventBuilder`](https://www.slf4j.org/apidocs/org/slf4j/spi/LoggingEventBuilder.html) | Fluent `setMessage`, `addArgument`, `addMarker`, `setCause`, `addKeyValue`, `log`. | Supplier/lazy overloads are not implemented yet. The level check runs at `log(...)` so marker-aware sinks can participate. |
| [`LogEvent`](../../lib_logging/src/main/x/logging/LogEvent.x) | SLF4J [`LoggingEvent`](https://www.slf4j.org/api/org/slf4j/event/LoggingEvent.html), Logback `ILoggingEvent` | Immutable event payload carrying logger name, level, message, markers, cause, arguments, MDC, and key/value pairs. | Ecstasy uses a `const`, with `Marker[] markers` plus `marker` convenience accessor and a `Map<String, Object>` for structured KV pairs. |
| [`LogSink`](../../lib_logging/src/main/x/logging/LogSink.x) | SLF4J provider boundary / Logback appender concept | Backend SPI: cheap enabled check plus event emission. | One interface allows both `const` forwarders and `service` stateful sinks; the `const`/`service` choice is documented in [`design/design.md`](design/design.md). |
| [`ConsoleLogSink`](../../lib_logging/src/main/x/logging/ConsoleLogSink.x), [`NoopLogSink`](../../lib_logging/src/main/x/logging/NoopLogSink.x), [`MemoryLogSink`](../../lib_logging/src/main/x/logging/MemoryLogSink.x) | `slf4j-simple`, `slf4j-nop`, Logback `ListAppender`-style test capture | Minimal default output, silent output, and test capture. | Shipped as tiny Ecstasy sinks so the base library is useful without a Logback-style backend. |

## slog-shaped `lib_slogging`

| Ecstasy implementation | Prior-art API | Same idea | Ecstasy difference |
|---|---|---|---|
| [`Logger`](../../lib_slogging/src/main/x/slogging/Logger.x) | Go [`slog.Logger`](https://pkg.go.dev/log/slog#Logger) | A logger holds a handler; per-level methods create records and forward them. `with` / `withGroup` derive handlers. | Ecstasy `Logger` is a `const` and is injectable via `@Inject slogging.Logger logger;`. It omits Go's `context.Context` parameter from per-call APIs. |
| [`LoggerContext`](../../lib_slogging/src/main/x/slogging/LoggerContext.x) | Go [`context.Context`](https://pkg.go.dev/context) as used by `log/slog` | Optional request-scoped propagation for the active logger. | Uses Ecstasy `SharedContext<Logger>` instead of threading `context.Context` through every logging call. Explicit logger passing remains the default recommendation. |
| [`Handler`](../../lib_slogging/src/main/x/slogging/Handler.x), [`BoundHandler`](../../lib_slogging/src/main/x/slogging/BoundHandler.x) | Go [`slog.Handler`](https://pkg.go.dev/log/slog#Handler) | `enabled`, `handle`, `withAttrs`, `withGroup`; derivation lets handlers pre-resolve context. | Ecstasy drops the `context.Context` parameter and `error` return for now; `BoundHandler` supplies the default derivation semantics for simple handlers. |
| [`Record`](../../lib_slogging/src/main/x/slogging/Record.x) | Go [`slog.Record`](https://pkg.go.dev/log/slog#Record) | One immutable record carries time, level, message, source, and attrs. | Adds an explicit `Exception?` slot because Ecstasy exceptions are first-class and tests can assert on them directly. |
| [`Attr`](../../lib_slogging/src/main/x/slogging/Attr.x) | Go [`slog.Attr`](https://pkg.go.dev/log/slog#Attr), [`slog.Group`](https://pkg.go.dev/log/slog#Group) | All structured data is key/value attrs; groups are nested attrs. | Uses `Object` values instead of Go's `slog.Value` kind union. Handlers inspect value types directly. |
| [`Level`](../../lib_slogging/src/main/x/slogging/Level.x) | Go [`slog.Level`](https://pkg.go.dev/log/slog#Level) | Open integer severity line with canonical `Debug`, `Info`, `Warn`, `Error`. | Stores a display `label` alongside severity for simple rendering. |
| [`TextHandler`](../../lib_slogging/src/main/x/slogging/TextHandler.x) | Go [`TextHandler`](https://pkg.go.dev/log/slog#TextHandler) | Human-readable `key=value` output and group prefixing. | Writes through `@Inject Console`; escaping/options are intentionally minimal in this POC. |
| [`JSONHandler`](../../lib_slogging/src/main/x/slogging/JSONHandler.x) | Go [`JSONHandler`](https://pkg.go.dev/log/slog#JSONHandler) | One line-delimited JSON object per record, nested groups, structured exceptions. | Renders through XDK `lib_json` and writes through injected `Console`; handler options/output streams are future backend work. |
| [`MemoryHandler`](../../lib_slogging/src/main/x/slogging/MemoryHandler.x), [`NopHandler`](../../lib_slogging/src/main/x/slogging/NopHandler.x) | Go test/discard handler patterns | Capture records for assertions or drop records entirely. | Small Ecstasy convenience handlers; not direct Go stdlib types. |

## No slog counterpart for `MessageFormatter`

`lib_logging.MessageFormatter` intentionally has no `lib_slogging` equivalent. SLF4J
uses a templated message plus positional arguments:

```ecstasy
logger.info("processed {} records", [count]);
```

Go `log/slog` uses a completed message plus structured attrs:

```ecstasy
logger.info("processed records", [Attr.of("count", count)]);
```

Reusing `MessageFormatter` inside `lib_slogging` would be architecturally wrong for
the core API because it would add a second structured-data channel (positional
arguments) to the design whose main advantage is having exactly one channel (`Attr`).
If migration sugar is useful later, it should live in an adapter/helper module that
makes the compromise explicit, for example `lib_slogging_slf4j_adapter`.

## Call-shape comparison

| Intent | Java / Go prior art | Ecstasy POC |
|---|---|---|
| Named logger | `LoggerFactory.getLogger(PaymentService.class)` | `@Inject Logger root; Logger log = root.named("PaymentService");` |
| SLF4J-style formatted message | `log.info("processed {}", count)` | `log.info("processed {}", [count])` |
| SLF4J-style structured event | `log.atInfo().addKeyValue("id", id).log("processed")` | `log.atInfo().addKeyValue("id", id).log("processed")` |
| Request context with MDC | `MDC.put("requestId", id)` | `@Inject MDC mdc; mdc.put("requestId", id);` |
| slog-style derived logger | `logger.With("requestId", id)` | `Logger reqLog = logger.with([Attr.of("requestId", id)]);` |
| slog-style context logger | `logger.LogAttrs(ctx, level, msg, attrs...)` | `using (loggerContext.bind(reqLog)) { ... }` plus `loggerContext.currentOr(root)` |
| slog-style structured event | `logger.Info("processed", "count", count)` | `logger.info("processed", [Attr.of("count", count)]);` |

## What to keep in mind while reviewing

- `lib_logging` optimizes for **JVM familiarity and Logback-style operational
  extension**. It keeps separate concepts for message args, markers, MDC, and
  structured key/value pairs because SLF4J users already know those concepts.
- `lib_slogging` optimizes for **conceptual economy**. Everything structured is an
  `Attr`; categories and request context are not special.
- Both designs put real deployment behavior behind one extension point:
  `LogSink` for the SLF4J shape, `Handler` for the slog shape.
- The biggest Ecstasy-specific change in both designs is acquisition: the long-term
  shape should be container-controlled injection, not global static configuration.


---

_See also [README.md](README.md) for the full doc index and reading paths._
