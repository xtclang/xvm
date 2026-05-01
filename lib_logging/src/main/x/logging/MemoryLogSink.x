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
 *
 * # Why this sink is a `service`, not a `const`
 *
 * It accumulates events. The `events[]` array is mutated on every `log()` call, and
 * the same instance is typically shared across many fibers (the logger under test plus
 * the assertion code that reads back the events). That is structurally identical to
 * `service ConsoleExecutionListener` in `lib_xunit_engine` and `service ErrorLog` in
 * `platform/common` — both are stateful event collectors. See
 * `doc/logging/DESIGN.md` ("Sink type: `const` vs `service`") for the full rule.
 */
service MemoryLogSink
        implements LogSink {

    /**
     * Threshold below which events are dropped. Defaults to `Trace` so tests see everything.
     */
    public/private Level rootLevel = Trace;

    /**
     * Mutable backing storage for captured events. Internal — must not escape the service
     * boundary, because Ecstasy forbids returning a mutable array from a service call.
     * External callers read [events] instead, which returns an immutable snapshot.
     */
    private LogEvent[] eventList = new LogEvent[];

    /**
     * The captured events, in emission order. Each access returns a fresh immutable
     * snapshot of the current state — the backing array is internal so it can stay
     * mutable for `add` / `clear`, while crossing the service boundary safely.
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

    /**
     * Discard all captured events.
     */
    void reset() {
        eventList.clear();
    }
}
