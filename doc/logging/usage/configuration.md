# Logging configuration and dynamic backends

Logging configuration belongs below the facade. Application/library code should keep
using `@Inject Logger logger;`; the host/container decides which sinks or handlers are
active, where output goes, which fields are redacted, and which logger categories are
debug-enabled.

This is exactly the SLF4J/Logback split: SLF4J is the facade, Logback is the configured
backend. The Ecstasy shape should preserve that split.

## Configuration prior art to account for

Logback is the best-known SLF4J backend, but it is not the only operational model that
reviewers will recognize. The XDK design should be understandable to teams coming from
these systems too:

| System | Why it matters | What the XDK design should preserve |
|---|---|---|
| [Logback](https://logback.qos.ch/manual/configuration.html) | The default backend in many SLF4J deployments and the mental model most Java teams mean by "configured logging". | Named loggers, root level, appenders, encoders, async appenders, reload, MDC, markers. |
| [Apache Log4j 2](https://logging.apache.org/log4j/2.x/manual/configuration.html) | The other major JVM logging framework. It has XML, JSON, YAML, and properties configuration, appenders, layouts, filters, async logging, and custom plugins. | The same backend graph should be expressible without requiring Logback XML: loggers, appenders/sinks, layouts/formatters, filters, async wrappers, and JSON output. |
| [Spring Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html) | Not a logging backend, but a very common deployment experience. Operators set `logging.level.*`, `logging.file.*`, and `logging.config` through properties, env vars, or command-line args. | The XDK should have simple override keys for the common cases and a structured config file for the full backend graph. |
| [java.util.logging](https://docs.oracle.com/en/java/javase/26/docs/api/java.logging/java/util/logging/LogManager.html) | The JDK built-in logger. Many libraries still emit through it, and bridges are common. | Hierarchical names and handler/formatter concepts are familiar, but the XDK should avoid JUL's global `LogManager` and flat properties-only limitations. |
| [Commons Logging](https://commons.apache.org/logging/index.html), JBoss Logging, and bridges | Facades exist because libraries should not force an application backend. | `@Inject Logger` should give Ecstasy the same library/application separation without classpath discovery. |
| [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) and cloud appenders | This is how many SLF4J/Logback services produce structured JSON for Elastic, Cloud Logging, CloudWatch, or Azure Monitor. | JSON and cloud output must be first-class backend concerns, not message parsing hacks. |

For Go, the comparison point is different. [`log/slog`](https://pkg.go.dev/log/slog)
does not define a `logback.xml`-style configuration file. A Go program usually parses
its own flags, environment variables, or config file, constructs a handler graph in
`main`, and installs it with `slog.SetDefault`. Dynamic levels are represented by
`slog.LevelVar`; redaction and field rewriting are represented by
`HandlerOptions.ReplaceAttr`; fanout is a handler wrapper such as `slog.MultiHandler`
or a third-party/custom handler.

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

## Recommended XDK configuration inputs

The POC docs use JSON for the full backend graph. XML is familiar from Logback, but JSON
fits the XDK better because it maps directly to typed Ecstasy config objects and to the
same JSON tooling used for service configuration. A production container should accept
these inputs in a predictable order:

1. Built-in defaults: console output, root `Info`, no cloud sink.
2. Packaged `logging.json` or `slogging.json` next to the application.
3. External file from `--logging.config=...`, `--slogging.config=...`,
   `XTC_LOGGING_CONFIG`, or `XTC_SLOGGING_CONFIG`.
4. Environment/property overrides for deployment-specific values.
5. Command-line overrides for the last-mile operator change.

For the eventual single XDK API, only the `logging.*` namespace should remain. While
this branch contains both POC libraries, the examples use `logging.*` for
`lib_logging` and `slogging.*` for `lib_slogging` so reviewers can compare them without
name collisions.

Common property overrides should look like this:

```properties
logging.config=/etc/acme/logging.json
logging.rootLevel=Info
logging.logger.com.acme.payments.level=Debug
logging.logger.com.acme.audit.level=Info
logging.sink.file-json.path=/var/log/acme/payments.jsonl
logging.sink.cloud.provider=gcp
logging.sink.cloud.project=acme-prod
logging.sink.cloud.service=payments
logging.sink.async-cloud.capacity=8192
logging.redactedKeys=authorization,password,token
```

The same settings should be accepted as environment variables in deployment systems that
prefer env configuration:

```bash
XTC_LOGGING_CONFIG=/etc/acme/logging.json
XTC_LOGGING_ROOT_LEVEL=Info
XTC_LOGGING_LOGGER_COM_ACME_PAYMENTS_LEVEL=Debug
XTC_LOGGING_SINK_CLOUD_PROVIDER=gcp
XTC_LOGGING_SINK_CLOUD_PROJECT=acme-prod
XTC_LOGGING_SINK_CLOUD_SERVICE=payments
XTC_LOGGING_REDACTED_KEYS=authorization,password,token
```

Command-line overrides should be reserved for operational changes:

```bash
xtc run app.xtc \
  --logging.config=/etc/acme/logging.json \
  --logging.level.root=Warn \
  --logging.level.com.acme.payments=Debug \
  --logging.sink.cloud.provider=gcp \
  --logging.sink.cloud.project=acme-prod
```

The slog-shaped POC uses the same deployment idea with handler vocabulary:

```properties
slogging.config=/etc/acme/slogging.json
slogging.rootLevel=Info
slogging.rule.component.com.acme.payments.level=Debug
slogging.handler.file-json.path=/var/log/acme/payments.jsonl
slogging.handler.cloud.provider=aws
slogging.handler.cloud.logGroup=/acme/payments
slogging.handler.async-cloud.capacity=8192
slogging.attr.service=payments
```

```bash
xtc run app.xtc \
  --slogging.config=/etc/acme/slogging.json \
  --slogging.level.root=Info \
  --slogging.rule.component.com.acme.payments=Debug \
  --slogging.handler.cloud.provider=aws
```

The rule is: JSON describes the graph; properties, env vars, and command-line args patch
the graph. They should not become a second full configuration language.

## What is implemented vs proposed

The examples below deliberately mix shipped POC types with the next layer that would
make logging a production XDK subsystem. The boundary should be explicit:

| Layer | Status in this branch | Recommended XDK home |
|---|---|---|
| Caller facade: `Logger`, levels, message formatting/attrs, MDC/context, markers where applicable | Implemented in `lib_logging` and `lib_slogging`. | The winning API becomes the first-class `lib_logging` module. The losing POC should not remain a peer facade. |
| Backend SPI: `LogSink` for `lib_logging`, `Handler` for `lib_slogging` | Implemented. | Stays in core `lib_logging`, because custom backends need the SPI without importing a config system. |
| Base backend primitives with no deployment dependencies | Implemented: `ConsoleLogSink`, `JsonLogSink`, `CompositeLogSink`, `HierarchicalLogSink`, `AsyncLogSink`, `MemoryLogSink`; slog equivalents include `TextHandler`, `JSONHandler`, `AsyncHandler`, `MemoryHandler`, `BoundHandler`. | Stays in core `lib_logging` if the dependency footprint remains small. |
| File destinations, rolling files, cloud destinations, provider clients | Not implemented here. The examples use names such as `FileJsonLogSink`, `CloudLogSink`, and `CloudHandler` as sketches. | Optional first-class XDK backend libraries, for example `lib_logging_file` and `lib_logging_cloud`, or provider-specific modules such as `lib_logging_gcp`, `lib_logging_aws`, and `lib_logging_azure` if dependencies are heavy. |
| JSON config schema, config parser, env/property/CLI override merge, file watching, hot reload services | Not implemented here. The examples use `LoggingConfig`, `SLoggingConfig`, `ConfiguredLogSink`, and `ConfiguredHandler` as sketches. | A first-class XDK config library, for example `lib_logging_config`, importing `logging`, `json`, and whichever backend modules it can instantiate. |
| Runtime defaults: automatic logger naming and automatic source-location capture | Partially sketched. The native injection POC exists; automatic naming/source capture remain compiler/runtime work. | Runtime/compiler, not the logging library itself. The library already has explicit fields/methods for the lowered values. |

If the slog-shaped API wins, the module split should be the same after renaming the
chosen facade to `lib_logging`: core logger/handler types in `lib_logging`, config
loading in `lib_logging_config`, and optional file/cloud handlers in backend libraries.

## Log4j 2 and Spring Boot comparison

Log4j 2 proves that XML is not required for a serious backend. The same appender/logger
graph can be written in JSON:

```json
{
  "Configuration": {
    "Appenders": {
      "Console": {
        "name": "STDOUT",
        "PatternLayout": {"pattern": "%d [%t] %p %c - %m%n"}
      },
      "File": {
        "name": "JSON",
        "fileName": "logs/app.jsonl",
        "JsonTemplateLayout": {}
      }
    },
    "Loggers": {
      "Logger": {
        "name": "com.acme.payments",
        "level": "debug",
        "AppenderRef": [{"ref": "JSON"}, {"ref": "STDOUT"}]
      },
      "Root": {
        "level": "info",
        "AppenderRef": {"ref": "STDOUT"}
      }
    }
  }
}
```

Spring Boot proves that operators also need simple overrides for common cases:

```properties
logging.level.root=warn
logging.level.com.acme.payments=debug
logging.file.name=/var/log/acme/app.log
logging.config=classpath:logback-spring.xml
```

The Ecstasy proposal should copy that operational convenience without copying Spring or
Logback internals. A minimal XDK equivalent is:

```properties
logging.level.root=Warn
logging.level.com.acme.payments=Debug
logging.sink.file-json.path=/var/log/acme/app.jsonl
logging.config=/etc/acme/logging.json
```

That is enough for "turn on debug for this component now" while the JSON file remains
the source of truth for multi-sink, cloud, redaction, and formatter decisions.

## How this maps to `lib_logging`

The current POC already has the backend building blocks:

| Config concept | Status | Type / future home |
|---|---|---|
| Console appender | Implemented | `ConsoleLogSink` in `lib_logging` |
| JSON encoder / JSON appender | Implemented for console-style JSON lines | `JsonLogSink` + `JsonLogSinkOptions` in `lib_logging` |
| Async appender | Implemented | `AsyncLogSink` in `lib_logging` |
| Multiple appenders | Implemented | `CompositeLogSink` in `lib_logging` |
| Per-logger level tree | Implemented | `HierarchicalLogSink` in `lib_logging` |
| Test/list appender | Implemented | `MemoryLogSink` in `lib_logging` |
| File output / rolling file output | Proposed | `FileJsonLogSink` or `RollingFileLogSink` in `lib_logging_file` |
| Cloud output | Proposed | `CloudLogSink` plus provider clients in `lib_logging_cloud` or provider modules |
| Config JSON loader and override merge | Proposed | `LoggingConfig` and loader services in `lib_logging_config` |
| Dynamic reload wrapper | Proposed | `ConfiguredLogSink` / `ReloadableLogSink` in `lib_logging_config` |

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

## Self-contained `lib_logging` dynamic backend sketch

This example shows the production shape reviewers should expect from the
SLF4J/Logback-shaped path:

- root level is `Info`;
- `com.acme.payments` is set to `Debug`;
- payment logs tee to a local file and a cloud logging sink;
- audit logs can be added dynamically without changing caller code;
- the active backend can be rebuilt from `logging.json` and swapped at runtime.

Everything in this sketch that implements `LogSink` uses the already-shipped extension
point. `ConsoleLogSink`, `JsonLogSink`, `CompositeLogSink`, `HierarchicalLogSink`, and
`AsyncLogSink` exist today. `FileJsonLogSink`, `CloudLogSink`, `CloudLogClient`,
`LoggingConfig`, and `ConfiguredLogSink` are proposed first-class backend/config types;
they should not be added to application code.

```json
{
  "version": 1,
  "logging": {
    "rootLevel": "Info",
    "sinks": [
      {"name": "console", "type": "console"},
      {
        "name": "file-json",
        "type": "file",
        "path": "logs/payments.jsonl",
        "format": "json",
        "redactedKeys": ["authorization", "password", "token"]
      },
      {
        "name": "cloud",
        "type": "cloud",
        "provider": "gcp",
        "project": "acme-prod",
        "service": "payments",
        "resource": {"type": "k8s_container"},
        "traceKeys": {"trace": "traceId", "span": "spanId"}
      },
      {
        "name": "async-cloud",
        "type": "async",
        "capacity": 8192,
        "delegate": "cloud"
      },
      {
        "name": "payments-tee",
        "type": "composite",
        "sinks": ["file-json", "async-cloud", "console"]
      }
    ],
    "loggers": [
      {"name": "com.acme.payments", "level": "Debug", "sinks": ["payments-tee"]},
      {"name": "com.acme.audit", "level": "Info", "sinks": ["async-cloud"]}
    ]
  }
}
```

The proposed cloud sink API shape is deliberately small:

```ecstasy
interface CloudLogClient {
    void write(CloudLogEntry entry);
}

const CloudLogEntry(
        String              provider,
        String              severity,
        String              message,
        Map<String, Object> payload,
        Map<String, String> labels = [],
        String?             trace  = Null,
        String?             spanId = Null,
        );

const CloudLogSink(CloudLogClient client, String provider, String service)
        implements LogSink {

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return True;
    }

    @Override
    void log(LogEvent event) {
        Map<String, Object> payload = new ListMap();
        payload.put("logger",  event.loggerName);
        payload.put("message", event.message);
        payload.putAll(event.keyValues);
        payload.put("mdc", event.mdcSnapshot);

        Map<String, String> labels = new ListMap();
        for (Marker marker : event.markers) {
            labels.put(marker.name, "true");
        }

        client.write(new CloudLogEntry(
                provider,
                event.level.name,
                event.message,
                payload,
                labels,
                trace  = event.mdcSnapshot.getOrNull("traceId"),
                spanId = event.mdcSnapshot.getOrNull("spanId")));
    }
}
```

A future `lib_logging_config` module would build and own the dynamic graph:

```ecstasy
service ConfiguredLogSink(LogSink initial)
        implements LogSink {
    private LogSink current = initial;

    void reload(LoggingConfig config, CloudLogClient cloud) {
        LogSink fileJson = new FileJsonLogSink(config.file("file-json"));
        LogSink cloudSink = new CloudLogSink(cloud, config.provider, config.service);
        LogSink asyncCloud = new AsyncLogSink(cloudSink, config.capacity("async-cloud"));
        LogSink tee = new CompositeLogSink([fileJson, asyncCloud, new ConsoleLogSink()]);

        HierarchicalLogSink next = new HierarchicalLogSink(tee, config.rootLevel);
        next.setLevel("com.acme.payments", Debug);
        next.setLevel("com.acme.audit",    Info);

        current = next;
    }

    void addAuditCloudSink(CloudLogClient auditCloud) {
        LogSink audit = new AsyncLogSink(new CloudLogSink(auditCloud, "gcp", "audit"));
        // A production implementation would rebuild a named sink graph here; this sketch
        // shows the operation belongs to the backend service, not caller code.
        current = new CompositeLogSink([current, audit]);
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

The cloud client is provider-specific, but the sink boundary is not:

- **GCP Cloud Logging:** map `Level` to `severity`, `message` to the displayed message,
  MDC/key-values to JSON payload, markers to labels, and trace/span IDs from MDC.
- **AWS CloudWatch Logs:** emit one JSON event to a log group/log stream or stdout for
  the platform collector; keep level, logger, request ID, and trace ID as indexed fields.
- **Azure Monitor / Application Insights:** map level to severity, message to trace text,
  MDC/key-values/markers to custom properties, and trace/span IDs to operation context.

The caller sees none of this:

```ecstasy
@Inject Logger logger;
@Inject MDC    mdc;

mdc.put("requestId", request.id);
mdc.put("traceId",   request.traceId);

logger.named("com.acme.payments.stripe")
      .atInfo()
      .addKeyValue("paymentId", payment.id)
      .addKeyValue("amount",    payment.amount)
      .log("payment processed");
```

## How this maps to Go slog / `lib_slogging`

Go `log/slog` does not define a standard configuration-file format comparable to
`logback.xml`. Go programs usually construct a `Handler` graph from application config
or environment variables. The standard API provides `Handler`, `HandlerOptions`,
`TextHandler`, `JSONHandler`, `WithAttrs`, and `WithGroup`; configuration is ordinary Go
code around those pieces.

The POC already has the slog-shaped core pieces:

| Config concept | Status | Type / future home |
|---|---|---|
| Logger facade and attrs | Implemented | `slogging.Logger`, `Attr`, `Record`, `Level` in `lib_slogging` |
| Handler SPI | Implemented | `Handler` and `BoundHandler` in `lib_slogging` |
| Text and JSON output | Implemented | `TextHandler`, `JSONHandler`, `HandlerOptions` in `lib_slogging` |
| Async wrapper | Implemented | `AsyncHandler` in `lib_slogging` |
| Test/discard handlers | Implemented | `MemoryHandler`, `NopHandler` in `lib_slogging` |
| Request logger context | Implemented | `LoggerContext` in `lib_slogging` |
| Handler fanout | Proposed | `CompositeHandler` in core `lib_logging` if slog wins, or in `lib_logging_config` if kept config-only |
| File output / rolling file output | Proposed | `FileJSONHandler` or `RollingFileHandler` in `lib_logging_file` |
| Cloud output | Proposed | `CloudHandler` plus provider clients in `lib_logging_cloud` or provider modules |
| Attr-based dynamic level routing | Proposed | `AttrLevelRoutingHandler` in `lib_logging_config` if slog wins |
| Config JSON loader and override merge | Proposed | `SLoggingConfig`-equivalent config objects in `lib_logging_config` if slog wins |

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

## Self-contained `lib_slogging` dynamic handler sketch

The slog-shaped version is the same backend idea with different vocabulary:

- configure a handler graph instead of a sink graph;
- use attrs for service/component/request context;
- tee records to file and cloud through a composite handler;
- apply level rules by attr matching, not logger-name prefix matching.

As above, the core handler API is implemented. `JSONHandler`, `TextHandler`,
`AsyncHandler`, `HandlerOptions`, `Attr`, and `Logger.with(...)` exist today.
`CompositeHandler`, `FileJSONHandler`, `CloudHandler`, `AttrLevelRoutingHandler`, and
the JSON/property/CLI config loader are proposed additions. If the slog shape wins,
they should graduate under `lib_logging` / `lib_logging_config`, not remain under a
permanent `lib_slogging` name.

```json
{
  "version": 1,
  "slogging": {
    "rootLevel": "Info",
    "handlers": [
      {
        "name": "file-json",
        "type": "file",
        "path": "logs/payments.jsonl",
        "format": "json",
        "redactedKeys": ["authorization", "password", "token"]
      },
      {
        "name": "cloud",
        "type": "cloud",
        "provider": "aws",
        "logGroup": "/acme/payments",
        "service": "payments",
        "traceKeys": {"trace": "traceId", "span": "spanId"}
      },
      {
        "name": "async-cloud",
        "type": "async",
        "capacity": 8192,
        "delegate": "cloud"
      },
      {
        "name": "tee",
        "type": "composite",
        "handlers": ["file-json", "async-cloud"]
      }
    ],
    "rules": [
      {"match": {"component": "com.acme.payments"}, "level": "Debug"},
      {"match": {"audit": true}, "level": "Info"}
    ],
    "context": {
      "attrs": [
        {"key": "service", "value": "payments"}
      ]
    }
  }
}
```

The proposed handler additions:

```ecstasy
const CompositeHandler(Handler[] handlers)
        implements Handler {

    @Override
    Boolean enabled(Level level) {
        for (Handler handler : handlers) {
            if (handler.enabled(level)) {
                return True;
            }
        }
        return False;
    }

    @Override
    void handle(Record record) {
        for (Handler handler : handlers) {
            if (handler.enabled(record.level)) {
                handler.handle(record);
            }
        }
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        return new CompositeHandler(handlers.map(h -> h.withAttrs(attrs)));
    }

    @Override
    Handler withGroup(String name) {
        return new CompositeHandler(handlers.map(h -> h.withGroup(name)));
    }
}

const CloudHandler(CloudLogClient client, String provider, String service)
        implements Handler {

    @Override
    Boolean enabled(Level level) = True;

    @Override
    void handle(Record record) {
        Map<String, Object> payload = new ListMap();
        payload.put("message", record.message);
        for (Attr attr : record.attrs) {
            payload.put(attr.key, attr.value);
        }

        client.write(new CloudLogEntry(
                provider,
                record.level.label,
                record.message,
                payload,
                labels = ["service" = service],
                trace  = payload.getOrNull("traceId")?.toString(),
                spanId = payload.getOrNull("spanId")?.toString()));
    }

    @Override
    Handler withAttrs(Attr[] attrs) = new BoundHandler(this, attrs);

    @Override
    Handler withGroup(String name) = new BoundHandler(this, name);
}
```

Dynamic reload again uses a stable handler:

```ecstasy
service ConfiguredHandler(Handler initial)
        implements Handler {
    private Handler current = initial;

    void reload(SLoggingConfig config, CloudLogClient cloud) {
        Handler fileJson = new FileJSONHandler(config.file("file-json"));
        Handler cloudHandler = new AsyncHandler(
                new CloudHandler(cloud, config.provider, config.service),
                config.capacity("async-cloud"));

        Handler tee = new CompositeHandler([fileJson, cloudHandler]);
        current = new AttrLevelRoutingHandler(tee, config.rules, config.rootLevel)
                .withAttrs([Attr.of("service", config.service)]);
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

Caller code is still simple:

```ecstasy
@Inject slogging.Logger logger;

slogging.Logger payments = logger.with([
        Attr.of("component", "com.acme.payments"),
        Attr.of("requestId", request.id),
        Attr.of("traceId",   request.traceId),
]);

payments.info("payment processed", [
        Attr.of("paymentId", payment.id),
        Attr.of("amount",    payment.amount),
]);
```

This is cloud-compatible for the same reason as the `lib_logging` version: every field
that cloud providers care about is present as structured data. The trade-off is routing:
the slog-shaped backend can route by attrs, but it does not provide Logback's
standardized hierarchical logger category model.

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
