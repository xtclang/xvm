/**
 * Corresponds to `org.slf4j.simple.SimpleLogger`'s output behaviour (slf4j-simple) and
 * Logback's `ch.qos.logback.core.ConsoleAppender` paired with a basic `PatternLayout`.
 * The role is the same: a no-config sink that puts events on stdout/stderr in a fixed,
 * human-readable line format.
 *
 * The default sink: writes one line per event to the platform `Console`.
 *
 * Format:
 *
 *      2026-04-29T11:23:45.012Z [thread-name] INFO  com.example.foo: hello world
 *
 * If the event has an exception, the stack trace is appended on subsequent lines.
 *
 * Configuration is intentionally minimal: a single `rootLevel` threshold applied to every
 * logger. Per-logger / per-marker filtering is the job of richer sinks (see
 * `docs/LOGBACK_INTEGRATION.md`).
 */
service ConsoleLogSink
        implements LogSink {

    @Inject Console console;

    /**
     * The threshold below which events are dropped. Defaults to `Info` so production
     * code is not flooded with debug output by accident.
     */
    public/private Level rootLevel = Info;

    /**
     * Adjust the root level at runtime.
     */
    void setRootLevel(Level level) {
        rootLevel = level;
    }

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        StringBuffer buf = new StringBuffer();
        buf.append(event.timestamp.toString())
           .append(" [").append(event.threadName).append("] ")
           .append(event.level.name.leftJustify(5, ' '))
           .append(' ')
           .append(event.loggerName)
           .append(": ")
           .append(event.message);

        if (Marker m ?= event.marker) {
            buf.append(" [marker=").append(m.name).append(']');
        }

        console.print(buf.toString());

        if (Exception e ?= event.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            console.print(e.toString());
        }
    }
}
