# Go `log/slog` → `lib_slogging` mapping

This document is the slog-shaped companion to
[`slf4j-parity.md`](slf4j-parity.md). It maps the Go `log/slog` API to the Ecstasy
POC and calls out the differences that are intentional.

Official reference: Go [`log/slog`](https://pkg.go.dev/log/slog).

## Acquiring a logger

| Go `log/slog` | `lib_slogging` |
|---|---|
| `slog.Default()` / top-level `slog.Info(...)` | Not modelled in v0. Ecstasy should prefer injected resources over process-global defaults. |
| `slog.New(handler)` | `new Logger(handler)` |
| `logger.With("requestId", id)` | `logger.with([Attr.of("requestId", id)])` |
| `logger.WithGroup("payments")` | `logger.withGroup("payments")` |
| `slog.NewTextHandler(w, opts)` | `new TextHandler(level)` or `new TextHandler(new HandlerOptions(...))`; output goes through `@Inject Console`. |
| `slog.NewJSONHandler(w, opts)` | `new JSONHandler(level)` or `new JSONHandler(new HandlerOptions(...))`; renders through `lib_json` and writes through `@Inject Console`. |
| framework/request context | `LoggerContext.bind(logger)` / `LoggerContext.currentOr(root)` |

Runtime `@Inject slogging.Logger logger;` is wired in this branch. The native injector
resolves resources by `(name, requested type)`, so both `logging.Logger logger` and
`slogging.Logger logger` can use the familiar default resource name `logger`.

Important distinction: Go `slog` has `Logger.With(...)` and `Logger.WithGroup(...)`,
but it does **not** have SLF4J/Logback-style hierarchical logger names. `WithGroup`
qualifies attribute keys for output; it is not the same thing as a logger category such
as `com.acme.payments.stripe` with a Logback longest-prefix level rule.

## `Logger` methods

| Go `log/slog` | `lib_slogging` |
|---|---|
| `logger.Debug(msg, args...)` | `logger.debug(message, [Attr.of("k", v)])` |
| `logger.Info(msg, args...)` | `logger.info(message, attrs)` |
| `logger.Warn(msg, args...)` | `logger.warn(message, attrs)` |
| `logger.Error(msg, args...)` | `logger.error(message, attrs, cause=e)` |
| `logger.Log(ctx, level, msg, args...)` | `logger.log(level, message, attrs)` |
| `logger.LogAttrs(ctx, level, msg, attrs...)` | Same `logger.log(level, message, attrs)` path; attrs are already typed. |
| `logger.Enabled(ctx, level)` | `logger.enabled(level)` |
| `AddSource` output | `logger.logAt(level, message, sourceFile, sourceLine, attrs)` |
| lazy message helpers used by wrappers | `logger.debug(() -> expensiveMessage(), attrs)` and the same shape for `info` / `warn` / `error` / `log` / `logAt` |

The intentional call-shape differences are small:

| Difference | Rationale |
|---|---|
| Ecstasy uses explicit `Attr.of("key", value)` values instead of alternating `"key", value` pairs. | This removes Go's bad-key case and keeps attrs typed before they reach the handler. |
| Common logging calls do not carry a context parameter. | Ecstasy can use `LoggerContext` (`SharedContext<Logger>`) for framework/request propagation without adding a context argument to every log call. |
| `error(...)` accepts `cause=` as well as attrs. | `Exception` is first-class in Ecstasy, and handlers/tests often need direct access to the cause. |

## `Attr`

| Go `log/slog` | `lib_slogging` |
|---|---|
| `slog.String("user", user)` | `Attr.of("user", user)` |
| `slog.Int("count", count)` | `Attr.of("count", count)` |
| `slog.Bool("audit", true)` | `Attr.of("audit", True)` |
| `slog.Any("err", err)` | `Attr.of("err", e)` or `cause=e` |
| `slog.Group("user", ...)` | `Attr.group("user", [...])` |
| values implementing `slog.LogValuer` | `Attr.lazy("key", () -> expensiveValue)` |

Go has many typed helper functions because it has no common `Object` parent. Ecstasy
does, so a single `Attr.of` is enough for the POC. A production handler can still
render strings, numbers, booleans, times, durations, exceptions, and groups differently.

`Attr.lazy` is the POC's `LogValuer` equivalent. `Logger` calls `Handler.enabled(level)`
first, then resolves lazy attrs before constructing the `Record`. Because `Attr` is a
`const`, the supplier must capture passable state: immutable values and service
references are fine; mutating an ordinary local variable from inside the supplier is not.

## `Handler`

| Go `log/slog.Handler` | `lib_slogging.Handler` |
|---|---|
| `Enabled(context.Context, Level) bool` | `enabled(Level)` |
| `Handle(context.Context, Record) error` | `handle(Record)` |
| `WithAttrs([]Attr) Handler` | `withAttrs(Attr[])` |
| `WithGroup(string) Handler` | `withGroup(String)` |

The extra derivation methods are the key architectural difference from
`lib_logging.LogSink`. They let a handler pre-bind attrs or a group once when a
logger is derived, instead of doing that work on every record. The shipped handlers use
`BoundHandler` for the default semantics, and tests include a small
`HandlerContract` helper modelled on Go's `testing/slogtest`.

`AsyncHandler` can wrap any handler when output is slow:

```ecstasy
Logger logger = new Logger(new AsyncHandler(new JSONHandler()));
```

## No markers or hierarchical names

Two SLF4J/Logback concepts are intentionally not part of the slog API:

| SLF4J / Logback concept | Go slog way | `lib_slogging` way | What you lose |
|---|---|---|---|
| `LoggerFactory.getLogger("com.acme.payments")` plus Logback prefix config | `slog.New(handler).With("component", "com.acme.payments")` | `logger.with([Attr.of("component", "com.acme.payments")])` | No built-in logger name field and no standard longest-prefix level tree. A handler can filter attrs, but that is custom policy. |
| `MarkerFactory.getMarker("AUDIT")` and marker filters | `logger.Info("...", "audit", true)` | `logger.info("...", [Attr.of("audit", True)])` | No marker identity, marker containment DAG, or marker-specific enabled check. |

That trade-off is deliberate. The slog model keeps one structured channel (`Attr`) and
lets handlers decide how to render or filter it. The cost is that Java/Logback teams do
not get direct equivalents for marker filters or category-prefix configuration.

## Context and source metadata

The default Ecstasy style is explicit:

```ecstasy
Logger reqLog = logger.with([Attr.of("requestId", req.id)]);
reqLog.info("processing", [Attr.of("path", req.path)]);
```

When framework code needs a request logger to flow through code that does not accept a
logger parameter, bind it with `LoggerContext`:

```ecstasy
LoggerContext context = new LoggerContext();

using (context.bind(reqLog)) {
    worker.process^();
}

Logger log = context.currentOr(logger);
log.info("inside worker");
```

Source metadata is explicit for now:

```ecstasy
logger.logAt(Level.Info, "processing", "PaymentService.x", 42,
        [Attr.of("requestId", req.id)]);
```

That fills `Record.sourceFile` and `Record.sourceLine`, and `JSONHandler` renders it
under the `"source"` object. Automatic call-site capture can target this method later.

## Levels

| Go `log/slog` | `lib_slogging` |
|---|---|
| `slog.LevelDebug` (`-4`) | `Level.Debug` |
| `slog.LevelInfo` (`0`) | `Level.Info` |
| `slog.LevelWarn` (`4`) | `Level.Warn` |
| `slog.LevelError` (`8`) | `Level.Error` |
| custom `slog.Level(2)` | `new Level(2, "NOTICE")` |

This is one place where the slog model is materially different from SLF4J:
levels are open. That is more extensible, but less canonical.

## Message formatting

There is no slog equivalent to SLF4J's `MessageFormatter`. This is intentional:

```ecstasy
// SLF4J-shaped
logger.info("processed {} records", [count]);

// slog-shaped
logger.info("processed records", [Attr.of("count", count)]);
```

The slog design keeps the human message stable and puts variable data in attrs. Reusing
`lib_logging.MessageFormatter` in `lib_slogging` would add positional arguments next
to attrs and remove the model's main simplification. If migration sugar is useful
later, it should live in an adapter module, not in the core slog API.

## Deferred or intentionally omitted

| Area | Current branch behavior |
|---|---|
| Process-global functions | Not modelled. Ecstasy should prefer injected resources over `slog.Info(...)`-style globals. |
| Output destinations | `TextHandler` and `JSONHandler` write to injected `Console`; a production backend can add stream/file/socket/HTTP handlers. |
| Automatic source capture | `logAt(...)` is explicit today and is the method future compiler/runtime sugar can target. |
| Handler prefix caching | `BoundHandler` provides correct derivation semantics. High-performance handlers can override `withAttrs` / `withGroup` to cache serialized prefixes. |


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
