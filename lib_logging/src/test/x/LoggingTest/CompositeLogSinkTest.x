import logging.BasicLogger;
import logging.CompositeLogSink;
import logging.Logger;

/**
 * Tests multi-appender fan-out.
 */
class CompositeLogSinkTest {

    @Test
    void shouldFanOutToAllEnabledDelegates() {
        ListLogSink first  = new ListLogSink();
        ListLogSink second = new ListLogSink();
        Logger      logger = new BasicLogger("composite.test",
                new CompositeLogSink([first, second]));

        logger.warn("fanout");

        assert first.events.size == 1;
        assert second.events.size == 1;
        assert first.events[0].message == "fanout";
        assert second.events[0].message == "fanout";
    }

    @Test
    void shouldSkipDisabledDelegates() {
        ListLogSink first  = new ListLogSink();
        ListLogSink second = new ListLogSink();
        second.setLevel(logging.Level.Error);

        Logger logger = new BasicLogger("composite.filtered",
                new CompositeLogSink([first, second]));

        logger.warn("only first");

        assert first.events.size == 1;
        assert second.events.empty;
    }
}
