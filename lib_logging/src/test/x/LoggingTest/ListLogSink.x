import logging.Level;
import logging.LogEvent;
import logging.LogSink;
import logging.Marker;

/**
 * A test-only sink that captures every event in a linked list. Used by the test cases
 * here as a stand-in for a "real" sink — it lets tests assert exactly what the
 * `Logger` chose to emit, in order, without any formatting nondeterminism.
 *
 * `rootLevel` defaults to `Trace` so by default every emitted event is captured.
 *
 * Modelled as a `service` because it carries mutable state (`events[]`) shared across
 * the fiber-under-test and the assertion code. See `doc/logging/DESIGN.md`
 * ("Sink type: `const` vs `service`").
 */
service ListLogSink
        implements LogSink {

    public/private Level rootLevel = logging.Level.Trace;

    /**
     * Mutable backing list. Internal — Ecstasy forbids returning a mutable array from a
     * service call, so we expose [events] as an immutable snapshot below.
     */
    private LogEvent[] eventList = new LogEvent[];

    /**
     * Immutable snapshot of the captured events, in emission order. Each access copies.
     */
    @RO LogEvent[] events.get() {
        return eventList.toArray(Constant);
    }

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        eventList.add(event);
    }

    void setLevel(Level level) {
        rootLevel = level;
    }

    void reset() {
        eventList.clear();
    }
}
