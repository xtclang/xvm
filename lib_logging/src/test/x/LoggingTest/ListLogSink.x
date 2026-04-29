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
 */
class ListLogSink
        implements LogSink {

    public/private Level      rootLevel = logging.Level.Trace;
    public/private LogEvent[] events    = new LogEvent[];

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        events.add(event);
    }

    void setLevel(Level level) {
        rootLevel = level;
    }

    void reset() {
        events.clear();
    }
}
