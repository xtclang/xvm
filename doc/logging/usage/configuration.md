# Logging configuration and dynamic backends

Logging configuration belongs below the facade. Application/library code should keep
using `@Inject Logger logger;`; the host/container decides which sinks or handlers are
active, where output goes, which fields are redacted, and which logger categories are
debug-enabled.

This is exactly the SLF4J/Logback split: SLF4J is the facade, Logback is the configured
backend. The Ecstasy shape should preserve that split.

## Java Logback baseline

A typical Logback config combines:

- appenders: where output goes;
- layouts/encoders: how events are rendered;
- loggers: category-specific level and appender rules;
- root: default level and destinations;
- scan/reload: optional config-file hot reload.

```xml
<configuration scan="true" scanPeriod="30 seconds">
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d %-5level [%thread] %logger - %msg %kvp%n</pattern>
    </encoder>
  </appender>

  <appender name="JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/app.jsonl</file>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
  </appender>

  <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>8192</queueSize>
    <appender-ref ref="JSON"/>
  </appender>

  <logger name="com.acme.payments" level="DEBUG" additivity="false">
    <appender-ref ref="ASYNC_JSON"/>
    <appender-ref ref="STDOUT"/>
  </logger>

  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

The important point is that caller code does not mention any of this. It just logs.

## Proposed Ecstasy JSON shape

Ecstasy does not need to copy Logback XML. A JSON or Ecstasy-native config object is a
better fit for the XDK because the backend graph is already typed and programmatic.

```json
{
  "version": 1,
  "logging": {
    "rootLevel": "Info",
    "sinks": [
      {
        "name": "console",
        "type": "console",
        "format": "text"
      },
      {
        "name": "json",
        "type": "json",
        "destination": {"type": "file", "path": "logs/app.jsonl"},
        "redactedKeys": ["authorization", "password", "token"],
        "fields": {
          "time": "time",
          "level": "severity",
          "logger": "logger",
          "message": "message",
          "mdc": "mdc",
          "markers": "markers",
          "exception": "exception",
          "source": "source"
        }
      },
      {
        "name": "async-json",
        "type": "async",
        "delegate": "json",
        "capacity": 8192,
        "onOverflow": "drop-newest"
      }
    ],
    "loggers": [
      {
        "name": "com.acme.payments",
        "level": "Debug",
        "sinks": ["async-json", "console"]
      }
    ]
  }
}
```

This is the same information as the Logback XML, but the names line up with XDK
objects: sinks, levels, destinations, redaction, field names, and logger-name routing.

## How this maps to `lib_logging`

The current POC already has the backend building blocks:

| Config concept | POC type |
|---|---|
| Console appender | `ConsoleLogSink` |
| JSON encoder / JSON appender | `JsonLogSink` + `JsonLogSinkOptions` |
| Async appender | `AsyncLogSink` |
| Multiple appenders | `CompositeLogSink` |
| Per-logger level tree | `HierarchicalLogSink` |
| Test/list appender | `MemoryLogSink` |

A config loader can build the sink graph from JSON:

```ecstasy
LogSink console = new ConsoleLogSink(Info);

LogSink json = new JsonLogSink(new JsonLogSinkOptions(
        Info,
        ["authorization", "password", "token"]));

LogSink asyncJson = new AsyncLogSink(json, 8192);

LogSink paymentsFanout = new CompositeLogSink([asyncJson, console]);

HierarchicalLogSink configured = new HierarchicalLogSink(paymentsFanout, Info);
configured.setLevel("com.acme.payments", Debug);
```

Dynamic reload should be handled by a stable service owned by the host container. The
shape is:

```ecstasy
service ReloadableLogSink(LogSink initial)
        implements LogSink {
    private LogSink current = initial;

    void replace(LogSink next) {
        current = next;
    }

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return current.isEnabled(loggerName, level, marker);
    }

    @Override
    void log(LogEvent event) {
        current.log(event);
    }
}
```

The runtime injects `BasicLogger(..., reloadable)`. When `logging.json` changes, the
host parses the new file, builds a new `LogSink` graph, and calls `reloadable.replace`.
Existing injected loggers keep working because their sink object is stable.

Adding a sink dynamically is the same operation: build a new graph containing the new
sink and swap it into the reloadable wrapper. A richer `ConfiguredLogSink` service could
also expose direct methods:

```ecstasy
configured.addSink("audit-json", new AsyncLogSink(new JsonLogSink(auditOptions)));
configured.attachSink("com.acme.audit", "audit-json");
configured.setLevel("com.acme.audit", Info);
```

Those methods belong in a future configuration module, not in the caller-facing
`Logger` API.

## How this maps to Go slog / `lib_slogging`

Go `log/slog` does not define a standard configuration-file format comparable to
`logback.xml`. Go programs usually construct a `Handler` graph from application config
or environment variables. The standard API provides `Handler`, `HandlerOptions`,
`TextHandler`, `JSONHandler`, `WithAttrs`, and `WithGroup`; configuration is ordinary Go
code around those pieces.

The equivalent Ecstasy JSON for the slog-shaped POC would configure handlers instead of
sinks:

```json
{
  "version": 1,
  "slogging": {
    "handler": {
      "type": "async",
      "capacity": 8192,
      "delegate": {
        "type": "json",
        "rootLevel": "Info",
        "redactedKeys": ["authorization", "password", "token"],
        "includeSource": true,
        "fields": {
          "time": "time",
          "level": "severity",
          "message": "message",
          "source": "source",
          "exception": "exception"
        }
      }
    },
    "context": {
      "attrs": [
        {"key": "service", "value": "payments"}
      ]
    }
  }
}
```

Programmatic construction looks like this:

```ecstasy
Handler json = new JSONHandler(new HandlerOptions(
        Level.Info,
        ["authorization", "password", "token"]));

Handler async = new AsyncHandler(json, 8192);
Logger  logger = new Logger(async)
        .with([Attr.of("service", "payments")]);
```

Dynamic reload uses the same stable-wrapper idea:

```ecstasy
service ReloadableHandler(Handler initial)
        implements Handler {
    private Handler current = initial;

    void replace(Handler next) {
        current = next;
    }

    @Override
    Boolean enabled(Level level) = current.enabled(level);

    @Override
    void handle(Record record) {
        current.handle(record);
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        return new BoundHandler(this, attrs);
    }

    @Override
    Handler withGroup(String name) {
        return new BoundHandler(this, name);
    }
}
```

The important difference from Logback is category routing. slog does not have a
first-class logger-name hierarchy. You can still configure handlers by attrs:

```json
{
  "rules": [
    {"match": {"component": "com.acme.payments"}, "level": "Debug"}
  ]
}
```

but that is an application/backend convention, not a built-in Logback-compatible
longest-prefix logger tree.

## Equivalence table

| Logback idea | `lib_logging` equivalent | Go slog / `lib_slogging` equivalent |
|---|---|---|
| `<appender>` | `LogSink` implementation | `Handler` implementation |
| `<appender-ref>` fanout | `CompositeLogSink` | Handler fanout wrapper; Go 1.26 adds `slog.MultiHandler`, and an Ecstasy `CompositeHandler` would be the same shape. |
| `<logger name="...">` | `HierarchicalLogSink.setLevel(name, level)` | Attr-based handler rule, e.g. `component == "..."`; not built into slog. |
| `<root level="INFO">` | `rootLevel` in `ConsoleLogSink`, `JsonLogSinkOptions`, or `HierarchicalLogSink` | `HandlerOptions.rootLevel` |
| `AsyncAppender` | `AsyncLogSink` | `AsyncHandler` |
| JSON encoder | `JsonLogSink` | `JSONHandler` |
| `%X{requestId}` MDC rendering | `MDC` rendered by a sink | `Logger.with([Attr.of("requestId", id)])` or `LoggerContext` |
| `MarkerFilter` | marker-aware `LogSink` | attr filter such as `audit == true` |
| `scan="true"` reload | `ReloadableLogSink.replace(...)` in a config module | `ReloadableHandler.replace(...)` in a config module |

## Recommendation

For the canonical `lib_logging` path, use an Ecstasy JSON configuration file or typed
Ecstasy config object rather than Logback XML. Keep the model familiar:

- named sinks/appenders;
- root level;
- per-logger overrides;
- async wrappers;
- JSON/text formatting options;
- redaction;
- reload by replacing a stable sink service's delegate.

For the slog-shaped path, document that configuration is handler-graph construction.
It can be driven by the same JSON file style, but it should not pretend to have Logback
logger categories unless the handler explicitly implements attr-based routing.

The user-facing rule stays the same in both designs: caller code depends on `Logger`;
configuration code owns sinks/handlers.


---

_See also [structured-logging.md](structured-logging.md), [custom-sinks.md](custom-sinks.md),
and [custom-handlers.md](custom-handlers.md)._
