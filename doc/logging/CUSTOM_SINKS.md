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

That's it. Two methods. Everything else — message substitution, MDC capture, marker
filtering, level check fast paths — happens in the `BasicLogger` *above* the sink. Sinks
just decide:

1. **`isEnabled`** — should this event even be considered? Called once per log statement
   on the hot path. Must be cheap.
2. **`log`** — the event has been built; emit it. The `LogEvent.message` is already
   substituted; `mdcSnapshot` is captured. Sinks that want to render structured data
   read `event.marker`, `event.exception`, `event.mdcSnapshot`, and (once added)
   `event.keyValues`.

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

Wiring it up — see `OPEN_QUESTIONS.md` for the runtime side; for now, you can construct
a `BasicLogger` directly:

```ecstasy
LogSink sink   = new CountingLogSink();
Logger  logger = new BasicLogger("my.module", sink);
logger.info("hello");
logger.warn("uh oh");
assert sink.counts[Info] == 1;
assert sink.counts[Warn] == 1;
```

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
        if (Marker m ?= event.marker, m.contains(required)) {
            delegate.log(event);
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
        // See STRUCTURED_LOGGING.md for the full sketch — emits one JSON object per line.
    }
}
```

## Things sinks should not do

- **Don't throw.** A sink that throws breaks the program. If your network endpoint is
  down, drop the event or fall back to stderr. Exceptions in `log()` are not the
  caller's problem.
- **Don't block on the hot path.** If the sink is doing something slow (network,
  encryption, disk), wrap it in an `AsyncLogSink` queue (future) rather than serializing
  every log call's caller behind it.
- **Don't snapshot MDC yourself.** The `BasicLogger` already does it before the event
  reaches you. `event.mdcSnapshot` is the immutable map you should render.
- **Don't re-format the message.** It has already been through `MessageFormatter` once
  by the time it reaches the sink. Render `event.message` verbatim.

## Things the runtime is expected to do for you

- **Level check fast path.** `BasicLogger.emit` calls `sink.isEnabled` *before*
  formatting the message, so a disabled-level call costs you exactly one method call
  with cheap arithmetic.
- **MDC snapshot.** Captured in `BasicLogger`, stored in `event.mdcSnapshot`.
- **Throwable promotion.** `MessageFormatter.format` enforces SLF4J's "trailing
  Throwable becomes the cause" rule before the event reaches you.

## Quick checklist for a production-quality sink

- [ ] `isEnabled` is `O(1)` or close to it — no I/O.
- [ ] `log` does not throw under any internal-failure mode.
- [ ] The threshold is configurable at runtime.
- [ ] Output is line-oriented or otherwise framed so `tail -f` is meaningful.
- [ ] The exception is rendered fully — message, stack frames, causes.
- [ ] If asynchronous, a flush mechanism exists for clean shutdown.
