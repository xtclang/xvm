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
 * # Why this sink is a `const`, not a `service`
 *
 * `ConsoleLogSink` carries no mutable state of its own. Its threshold (`rootLevel`) is
 * fixed at construction; every other piece of "state" is either an injected service
 * reference (`@Inject Console console`) or constructed transiently inside `log()`.
 *
 * That makes it a forwarder, not an event collector — structurally identical to
 * `lib_ecstasy/src/main/x/ecstasy/io/ConsoleAppender.x` and `ConsoleLog.x`, both of
 * which are `class`/`const`-shaped wrappers over an injected `Console`.
 *
 * Sinks that *do* hold mutable shared state (e.g. `MemoryLogSink` collecting events,
 * `AsyncLogSink` owning a worker queue, `HierarchicalLogSink` owning mutable level
 * configuration, or a future `FileLogSink` owning a `Writer`) must remain `service`.
 * The rule of thumb is documented in
 * `doc/logging/design/design.md` ("Sink type: `const` vs `service`").
 *
 * Configuration is intentionally minimal: a single `rootLevel` threshold applied to every
 * logger. Per-logger routing belongs in [HierarchicalLogSink]; multi-destination routing
 * belongs in [CompositeLogSink].
 */
const ConsoleLogSink(Level rootLevel)
        implements LogSink {

    /**
     * No-arg convenience: `new ConsoleLogSink()` resolves to threshold `Info`. Explicit
     * (rather than `Level rootLevel = Info` default on the primary constructor) because
     * cross-module default-argument resolution on `const` constructors does not always
     * synthesise the zero-arg form — callers from a different module otherwise hit
     * "Unresolvable function `construct()`."
     */
    construct() {
        construct ConsoleLogSink(Info);
    }

    @Inject Console console;

    /**
     * Cheap root-threshold check. The default sink intentionally has no per-logger or
     * marker-specific configuration; those belong in richer sinks.
     */
    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    /**
     * Render a `LogEvent` as one human-readable line. This default output is stable
     * enough for demos/tests, not a replacement for a Logback-style layout system.
     */
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

        appendMarkers(buf, event.markers);
        appendMdc(buf, event.mdcSnapshot);
        appendKeyValues(buf, event.keyValues);

        console.print(buf.toString());

        if (Exception e ?= event.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            console.print(e.toString());
        }
    }

    /**
     * Append Logback-style marker text, e.g. `[marker=AUDIT]` or `[markers=AUDIT,SECURITY]`.
     */
    private void appendMarkers(StringBuffer buf, Marker[] markers) {
        if (markers.empty) {
            return;
        }

        buf.append(markers.size == 1 ? " [marker=" : " [markers=");
        Boolean first = True;
        for (Marker marker : markers) {
            first = appendSeparator(buf, first, ",");
            buf.append(marker.name);
        }
        buf.append(']');
    }

    /**
     * Append the MDC snapshot captured before the event reached the sink.
     */
    private void appendMdc(StringBuffer buf, Map<String, String> mdc) {
        if (mdc.empty) {
            return;
        }

        buf.append(" [mdc=");
        Boolean first = True;
        for ((String key, String value) : mdc) {
            first = appendSeparator(buf, first, ",");
            buf.append(key).append('=').append(value);
        }
        buf.append(']');
    }

    /**
     * Append structured key/value pairs in the compact text layout.
     */
    private void appendKeyValues(StringBuffer buf, Map<String, Object> values) {
        if (values.empty) {
            return;
        }

        buf.append(" {");
        Boolean first = True;
        for ((String key, Object value) : values) {
            first = appendSeparator(buf, first, ", ");
            buf.append(key).append('=').append(value.toString());
        }
        buf.append('}');
    }

    /**
     * Append `separator` after the first item in a small rendered list.
     *
     * @return False, so callers can write `first = appendSeparator(...)`.
     */
    private Boolean appendSeparator(StringBuffer buf, Boolean first, String separator) {
        if (!first) {
            buf.append(separator);
        }
        return False;
    }
}
