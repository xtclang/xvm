# Logback-style integration

`lib_logging` v0 ships a single, intentionally minimal default sink: `ConsoleLogSink`.
For real-world deployment we expect a Logback-equivalent backend — configuration-driven,
multi-appender, hierarchical — to live in a separate module. This document sketches how
that would be shaped.

It is intentionally *forward-looking*. Nothing here is implemented yet; the point is to
make sure today's API choices don't preclude tomorrow's backend.

## What "Logback-style" means

For SLF4J users, "Logback" means a specific bundle of features beyond the SLF4J facade:

| Feature | Description |
|---|---|
| **Per-logger thresholds** | `com.example.foo` at `DEBUG`, root at `INFO`. The threshold lookup walks the logger-name hierarchy. |
| **Multi-appender** | The same event can fan out to console, file, network, syslog, etc. |
| **Layouts** | Pluggable output formatters: `PatternLayout`, `JSONLayout`, `LogstashEncoder`, etc. |
| **Filters** | Per-appender pre-emit decisions based on level, marker, MDC, regex, etc. |
| **Configuration files** | `logback.xml` (or programmatic). Reload on change. |
| **Async appenders** | A queue + worker thread so producer threads aren't blocked by slow I/O. |
| **Context-aware MDC rendering** | MDC values appear in formatted output without the message having to mention them. |
| **Rolling file appenders** | Time-based, size-based, or composite rotation. |

The goal: a future `lib_logging_logback` module ships every one of those.

## The architectural fit

`lib_logging` already separates:

```
Logger ──→ LogSink ──→ (whatever)
```

A logback-style backend slots into the `LogSink` slot. From the caller's perspective
nothing changes — `@Inject Logger logger;` still produces a `Logger`, the methods are
the same, the events are the same. The backend just happens to be much smarter about
what it does with them.

Conceptually the backend itself decomposes into a smaller hierarchy:

```
ConfiguredLogSink         (the LogSink the runtime injects)
   │
   ├── LoggerContext      (the per-logger threshold tree, "INFO at root, DEBUG at com.example.foo")
   │
   └── Appender[]         (each appender = its own "where does the event go" target)
        │
        ├── Filter[]      (pre-emit predicates: level, marker, MDC, regex)
        │
        ├── Layout        (LogEvent → bytes, e.g. PatternLayout, JSONLayout)
        │
        └── Output        (Console, File, RollingFile, Network, ...)
```

This isn't novel — it's exactly Logback's mental model. The advantage of writing it as
an Ecstasy module rather than a wrapper around the JVM Logback library is that it gets
to use Ecstasy's own primitives (services for thread-safety, fibers for async, the file
abstraction from `lib_ecstasy`) instead of needing the bridge story discussed in
`../future/native-bridge.md`.

## Sketch — the public API of `lib_logging_logback`

```ecstasy
module logback.xtclang.org {
    package log import logging.xtclang.org;

    /**
     * The configurable sink that user code wires up via `@Inject log.LogSink`.
     */
    service ConfiguredLogSink
            implements log.LogSink {
        // populated by Configurator
    }

    /**
     * Programmatic configuration entry point. Equivalent to Logback's JoranConfigurator.
     */
    service Configurator {
        Configurator setRootLevel(log.Level level);
        Configurator setLevel(String loggerPrefix, log.Level level);
        Configurator addAppender(Appender appender);
        Configurator clear();
    }

    interface Appender {
        @RO String name;
        Boolean    isEnabled(log.LogEvent event);
        void       append(log.LogEvent event);
    }

    interface Layout {
        String render(log.LogEvent event);
    }

    interface Filter {
        Decision decide(log.LogEvent event);
        enum Decision { Accept, Neutral, Deny }
    }

    // shipped appenders
    service ConsoleAppender    (String name, Layout layout, Filter[] filters = []) implements Appender;
    service FileAppender       (String name, Path file, Layout layout, Filter[] filters = []) implements Appender;
    service RollingFileAppender(String name, Path file, RollingPolicy policy, Layout layout, Filter[] filters = []) implements Appender;
    service AsyncAppender      (String name, Appender delegate, Int queueSize = 1024) implements Appender;

    // shipped layouts
    service PatternLayout(String pattern) implements Layout;
    service JsonLayout                    implements Layout;

    // shipped filters
    service LevelFilter (log.Level threshold)            implements Filter;
    service MarkerFilter(log.Marker required)            implements Filter;
    service MDCFilter   (String key, String valuePrefix) implements Filter;
}
```

## Sketch — programmatic configuration

```ecstasy
module MyApp {
    package log     import logging.xtclang.org;
    package logback import logback.xtclang.org;

    void run() {
        @Inject logback.Configurator config;

        logback.PatternLayout text = new logback.PatternLayout(
            "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");

        logback.JsonLayout json = new logback.JsonLayout();

        config
            .setRootLevel(log.Info)
            .setLevel("com.example.payments", log.Debug)
            .addAppender(new logback.ConsoleAppender("STDOUT", text))
            .addAppender(new logback.RollingFileAppender(
                name   = "FILE",
                file   = Path:/var/log/app.log,
                policy = new logback.SizeBasedRollingPolicy(maxSize = 100M, maxBackups = 10),
                layout = json,
                filters = [new logback.MarkerFilter(MarkerFactory.getMarker("AUDIT"))]
            ));
    }
}
```

## Sketch — file-based configuration

For parity with Logback's `logback.xml`, the module could ship an XML or JSON loader.
A possible JSON shape (more idiomatic for Ecstasy than XML):

```json
{
  "rootLevel": "INFO",
  "loggers": {
    "com.example.payments": "DEBUG",
    "com.example.silly":    "OFF"
  },
  "appenders": [
    {
      "name": "STDOUT",
      "type": "ConsoleAppender",
      "layout": { "type": "PatternLayout", "pattern": "%d [%thread] %-5level %logger - %msg%n" }
    },
    {
      "name": "FILE",
      "type": "RollingFileAppender",
      "file": "/var/log/app.log",
      "policy": { "type": "SizeBased", "maxSize": "100M", "maxBackups": 10 },
      "layout": { "type": "JsonLayout" },
      "filters": [
        { "type": "MarkerFilter", "marker": "AUDIT" }
      ]
    }
  ]
}
```

Loading this via `lib_json`:

```ecstasy
@Inject FileSystem        fs;
@Inject json.Schema       schema;        // (sketch — actual API depends on lib_json)
@Inject logback.Configurator config;

File   configFile = fs.fileFor(Path:/etc/myapp/logging.json);
String text       = configFile.contents.toString();
LoggingConfig cfg = schema.deserialize(LoggingConfig, text);
config.applyConfig(cfg);
```

## Hot reload

Logback's auto-reload watches the config file and rebuilds the logger context on change.
The Ecstasy equivalent would use the file-system change-notification API to do the same.
The atomic swap is on the `Configurator` side; once it commits a new config, every
existing `Logger` reference (since it goes through the injected sink) sees the new
behaviour on the next call.

## Per-logger lookup

The longest-prefix-match lookup illustrated in `../usage/custom-sinks.md` is the engine:

```ecstasy
private Level effectiveLevel(String loggerName) {
    String name = loggerName;
    while (True) {
        if (Level lvl := perLogger.get(name)) {
            return lvl;
        }
        Int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return rootLevel;
        }
        name = name[0 ..< dot];
    }
}
```

This is what `LoggerContext.getLogger(name)` does in Logback, just in fewer lines
because we don't have to maintain a tree object.

## Async appenders

```ecstasy
service AsyncAppender(String name, Appender delegate, Int queueSize)
        implements Appender {

    private Queue<LogEvent> queue = new BlockingQueue<LogEvent>(queueSize);

    construct(String name, Appender delegate, Int queueSize) {
        // start a worker fiber that drains the queue into the delegate
        worker.runAsync(drainLoop);
    }

    @Override
    void append(log.LogEvent event) {
        if (!queue.offer(event)) {
            // queue full: drop or block — configurable via policy
        }
    }

    private void drainLoop() {
        while (running) {
            log.LogEvent event = queue.take();
            delegate.append(event);
        }
    }
}
```

This is the Ecstasy-native equivalent of Logback's `AsyncAppender`. Fibers replace
threads; otherwise the design is identical.

## Migration story for SLF4J + Logback users

A team running an SLF4J + Logback service today and porting to Ecstasy + `lib_logging` +
`lib_logging_logback`:

| In Java + Logback | In Ecstasy + lib_logging_logback |
|---|---|
| `org.slf4j.LoggerFactory.getLogger(MyClass.class)` | `@Inject Logger logger;` (or `LoggerFactory.getLogger(MyClass)`) |
| `logback.xml` in classpath root | `logging.json` (or programmatic) loaded by `Configurator` |
| `<root level="INFO">` | `config.setRootLevel(Info)` |
| `<logger name="com.example.foo" level="DEBUG"/>` | `config.setLevel("com.example.foo", Debug)` |
| `<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">` | `new ConsoleAppender("STDOUT", layout)` |
| `<encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">` | `new PatternLayout(pattern)` |
| `<filter class="ch.qos.logback.classic.filter.LevelFilter">` | `new LevelFilter(threshold)` |
| `MDC.put("requestId", id)` | `mdc.put("requestId", id)` |
| `org.slf4j.MarkerFactory.getMarker("AUDIT")` | `markers.getMarker("AUDIT")` |

Caller code is unchanged. Configuration is structurally similar but expressed in
Ecstasy idiom.

## Out of scope for `lib_logging_logback` v1

These are explicitly later:

- SMTP appender (email on errors).
- DBAppender (write to RDBMS).
- Sift appender (separate appender per MDC value).
- TurboFilters (filters that run *before* the level check; meaningful only for very
  high-volume systems).

These exist in Java Logback. They are infrequently used and can be added incrementally
without API changes.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
