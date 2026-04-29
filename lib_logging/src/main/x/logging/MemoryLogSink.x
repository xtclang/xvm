/**
 * Corresponds to Logback's `ch.qos.logback.core.read.ListAppender` — the standard
 * test-helper appender that captures events in an in-memory list for later assertion.
 *
 * A test-oriented sink: captures every emitted event in an in-memory list. Suitable for unit
 * tests that want to assert on what a logger emitted.
 *
 *      MemoryLogSink sink = new MemoryLogSink();
 *      Logger logger = new BasicLogger("test", sink);
 *      logger.info("processed {}", 42);
 *      assert sink.events.size == 1;
 *      assert sink.events[0].level == Info;
 */
class MemoryLogSink
        implements LogSink {

    /**
     * Threshold below which events are dropped. Defaults to `Trace` so tests see everything.
     */
    public/private Level rootLevel = Trace;

    /**
     * The captured events, in emission order.
     */
    public/private LogEvent[] events = new LogEvent[];

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void log(LogEvent event) {
        events.add(event);
    }

    /**
     * Discard all captured events.
     */
    void reset() {
        events.clear();
    }
}
