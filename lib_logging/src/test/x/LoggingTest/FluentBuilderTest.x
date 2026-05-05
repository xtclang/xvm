import logging.BasicLogger;
import logging.BasicMarker;
import logging.Logger;
import logging.Marker;

/**
 * Tests for the SLF4J 2.x style fluent event builder reachable via `logger.atInfo()`,
 * `logger.atLevel(level)`, etc. Confirms that:
 *   - chaining accumulates state and a final `log(...)` materialises a single event;
 *   - the builder respects the underlying logger's level threshold;
 *   - markers, causes, and arguments survive the round-trip.
 */
class FluentBuilderTest {

    @Test
    void shouldBuildAndEmitInfoEvent() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("fluent", sink);

        logger.atInfo()
              .setMessage("processed")
              .addArgument(42)
              .log();

        assert sink.events.size == 1;
        assert sink.events[0].level == logging.Level.Info;
        assert sink.events[0].message == "processed";
    }

    @Test
    void shouldCarryMarkerAndCauseThroughBuilder() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("fluent.full", sink);
        Marker      audit  = new BasicMarker("AUDIT");
        Exception   cause  = new Exception("network down");

        logger.atError()
              .addMarker(audit)
              .setCause(cause)
              .log("emit failed");

        assert sink.events.size == 1;
        assert sink.events[0].level == logging.Level.Error;
        assert sink.events[0].marker?.name == "AUDIT" : assert;
        assert sink.events[0].exception == cause;
    }

    @Test
    void shouldShortCircuitWhenLevelDisabled() {
        ListLogSink sink   = new ListLogSink();
        sink.setLevel(logging.Level.Warn);
        Logger      logger = new BasicLogger("fluent.disabled", sink);

        logger.atDebug()
              .setMessage("expensive computation result")
              .addArgument("would-have-been-formatted")
              .log();

        assert sink.events.size == 0;
    }
}
