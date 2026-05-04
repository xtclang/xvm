# Writing a custom `LogSink`

`LogSink` is the API/impl boundary for `lib_logging`. Anything below it is replaceable
without touching caller code. This document walks through writing a custom sink end to
end.

## The contract

```ecstasy
interface LogSink {
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null);
    void    log(LogEvent event);
}
```

That's it. Everything else — message substitution, MDC capture, marker filtering,
and disabled-level fast paths — happens in `BasicLogger` above the sink.

| Method | Responsibility |
|---|---|
| `isEnabled` | Hot-path threshold/category check. It runs once per log statement and must stay cheap. |
| `log` | Emit the already-built event. `LogEvent.message` is substituted, `mdcSnapshot` is captured, and structured data is available in `markers`, `exception`, `keyValues`, and `mdcSnapshot`. |

## `const` or `service`?

`BasicLogger` is a `const` and holds its sink in a `LogSink` field. Ecstasy requires
that field to be `Passable`, so every implementation must be either `immutable` (a
`const`) or a `service`. Pick one of the two:

- **`const`** — for stateless forwarders / pure adapters. Configuration is fixed at
  construction (e.g. a `rootLevel` value passed in once). The sink owns no mutable state
  shared across fibers. Examples in `lib_logging`: `NoopLogSink`, `ConsoleLogSink`.
  Cheap to construct, cheap to pass; methods run on the caller's fiber.
- **`service`** — for sinks that genuinely have shared mutable state: an event buffer,
  a counter map, an open file handle, a worker queue. The service mailbox handles
  concurrent ingress for free. Examples: `MemoryLogSink`, `ListLogSink` (test sinks),
  the `FileLogSink`/`HierarchicalLogSink`/`TeeLogSink` examples below.

If you find yourself reaching for `synchronized` blocks, you want a `service`. If your
sink is "given a `Console`/`Writer`/`Function`, format and forward" — you want a
`const`. The full rule, with reference examples from the wider XDK / platform
ecosystem, is in `../design/design.md` ("Sink type: `const` vs `service`").

## A worked example: a counting sink

The simplest non-trivial sink — counts events per level. Useful for metrics export.

```ecstasy
service CountingLogSink
        implements LogSink {

    public/private Map<Level, Int> counts = new HashMap();

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return True;
    }

    @Override
    void log(LogEvent event) {
        counts.process(event.level, e -> {
            e.value = e.exists ? e.value + 1 : 1;
            return Null;
        });
    }
}
```

Wiring it up directly in tests or examples is just constructor injection:

```ecstasy
CountingLogSink sink   = new CountingLogSink();
Logger          logger = new BasicLogger("my.module", sink);
logger.info("hello");
logger.warn("uh oh");
assert sink.counts[Info] == 1;
assert sink.counts[Warn] == 1;
```

In a hosted application, the same sink becomes the active backend by registering it as
the `Logger` resource supplier for that container. The application code still receives
`@Inject Logger logger;`; only the host's resource map changes.

## A more realistic example: writing to a file

```ecstasy
service FileLogSink
        implements LogSink {

    @Inject FileSystem fs;

    public/private Level rootLevel = Info;

    private File   file;
    private Writer writer;

    construct(Path path, Level rootLevel = Info) {
        this.rootLevel = rootLevel;
        this.file      = fs.fileFor(path).ensure();
        this.writer    = file.appender();
    }

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        StringBuffer buf = new StringBuffer();
        buf.append(event.timestamp.toString())
           .append(' ')
           .append(event.level.name.padRight(' ', 5))
           .append(' ')
           .append(event.loggerName)
           .append(": ")
           .append(event.message)
           .append('\n');
        writer.print(buf.toString());
        if (Exception e ?= event.exception) {
            writer.print(e.toString()).print('\n');
        }
    }
}
```

Notes:
- `fs` is injected — same pattern as `Console`. The sink doesn't pick file paths; the
  caller does.
- The constructor opens the file once. Sinks are expected to be services, so the
  writer state is service-local and thread-safe.
- The `level.severity >= rootLevel.severity` pattern is the canonical level filter.

## Per-logger thresholds (logback-style)

Many sinks want different thresholds for different logger names — the classic
`com.example.foo at DEBUG, root at INFO` pattern.

```ecstasy
service HierarchicalLogSink
        implements LogSink {

    @Inject Console console;

    public/private Map<String, Level> perLogger = new HashMap();
    public/private Level              rootLevel = Info;

    void setLevel(String prefix, Level level) {
        perLogger.put(prefix, level);
    }

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= effectiveLevel(loggerName).severity;
    }

    @Override
    void log(LogEvent event) {
        // ...same as ConsoleLogSink, with thresholds applied.
    }

    /**
     * Walk the logger name from longest prefix to shortest, returning the first match.
     * "com.example.foo.bar" matches "com.example.foo" before "com.example".
     */
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
}
```

This is roughly what Logback's `LoggerContext` does internally. Wrapping it as a sink
keeps the rest of the library unaware.

## A composite (tee) sink

```ecstasy
service TeeLogSink(LogSink[] sinks)
        implements LogSink {

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        for (LogSink sink : sinks) {
            if (sink.isEnabled(loggerName, level, marker)) {
                return True;
            }
        }
        return False;
    }

    @Override
    void log(LogEvent event) {
        for (LogSink sink : sinks) {
            if (sink.isEnabled(event.loggerName, event.level, event.marker)) {
                sink.log(event);
            }
        }
    }
}
```

`TeeLogSink([new ConsoleLogSink(), new FileLogSink(/var/log/app.log)])` is the moral
equivalent of attaching multiple Logback appenders to one logger.

## Filtering by marker

Markers exist precisely so that sinks can decide based on category, not on message text.

```ecstasy
service MarkerFilteringLogSink(LogSink delegate, Marker required)
        implements LogSink {

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return marker?.contains(required) : False;
    }

    @Override
    void log(LogEvent event) {
        for (Marker m : event.markers) {
            if (m.contains(required)) {
                delegate.log(event);
                return;
            }
        }
    }
}
```

Pair this with a `TeeLogSink` to e.g. send everything to the console but only
`AUDIT`-marked events to a separate audit file.

## Structured / JSON output

```ecstasy
service JsonLineLogSink
        implements LogSink {

    @Inject Console console;
    public/private Level rootLevel = Info;

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        // See ../usage/structured-logging.md for the full sketch — emits one JSON object per line.
    }
}
```

## Operational rules

| Rule | Reason |
|---|---|
| Keep `isEnabled` close to `O(1)`. | It runs before every enabled or disabled log event; no I/O belongs there. |
| Do not let normal backend failures escape from `log`. | Logging should not break the caller. Drop, buffer, or fall back to stderr when a remote endpoint or file is unavailable. |
| Do not block callers on slow output. | Network, encryption, and disk-heavy paths should sit behind an async wrapper once that Tier 3 sink exists. |
| Render `event.message` as-is. | `MessageFormatter` has already performed `{}` substitution and trailing-exception promotion. |
| Render `event.mdcSnapshot`; do not read `MDC` again. | `BasicLogger` captured the immutable per-fiber context before the event crossed the sink boundary. |
| Frame production output. | Line-oriented or otherwise framed output keeps `tail -f`, log shippers, and cloud collectors predictable. |

The sink can also rely on three upstream guarantees: disabled events are filtered
before formatting, MDC is captured in the event, and SLF4J-style trailing exceptions
have already been promoted to `event.exception`.


---

_See also [../README.md](../README.md) for the full doc index and reading paths._
