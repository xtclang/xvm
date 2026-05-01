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
 * a future `FileLogSink` owning a `Writer`, a future `AsyncLogSink` owning a worker
 * queue) must remain `service`. The rule of thumb is documented in
 * `doc/logging/design.md` ("Sink type: `const` vs `service`").
 *
 * Configuration is intentionally minimal: a single `rootLevel` threshold applied to every
 * logger. Per-logger / per-marker filtering is the job of richer sinks (see
 * `doc/logging/logback-integration.md`).
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

        if (!event.markers.empty) {
            buf.append(" [marker");
            buf.append(event.markers.size == 1 ? "=" : "s=");
            Boolean firstMarker = True;
            for (Marker m : event.markers) {
                if (!firstMarker) {
                    buf.append(',');
                }
                firstMarker = False;
                buf.append(m.name);
            }
            buf.append(']');
        }

        if (!event.mdcSnapshot.empty) {
            buf.append(" [mdc=");
            Boolean firstMdc = True;
            for ((String k, String v) : event.mdcSnapshot) {
                if (!firstMdc) {
                    buf.append(',');
                }
                firstMdc = False;
                buf.append(k).append('=').append(v);
            }
            buf.append(']');
        }

        if (!event.keyValues.empty) {
            buf.append(" {");
            Boolean first = True;
            for ((String k, Object v) : event.keyValues) {
                if (!first) {
                    buf.append(", ");
                }
                first = False;
                buf.append(k).append('=').append(v.toString());
            }
            buf.append('}');
        }

        console.print(buf.toString());

        if (Exception e ?= event.exception) {
            // TODO(impl): pretty-print stack frames; for now rely on Exception.toString().
            console.print(e.toString());
        }
    }
}
