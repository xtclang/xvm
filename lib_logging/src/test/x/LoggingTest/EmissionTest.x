import logging.BasicLogger;
import logging.Logger;

/**
 * End-to-end emission tests using `BasicLogger` over a `ListLogSink`. Verifies that
 * each per-level method routes the right `Level` and that the sink receives the
 * correct event content.
 */
class EmissionTest {

    @Test
    void shouldRouteInfo() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("emission.test", sink);

        logger.info("hello");

        assert sink.events.size == 1;
        assert sink.events[0].level == logging.Level.Info;
        assert sink.events[0].loggerName == "emission.test";
        assert sink.events[0].message == "hello";
    }

    @Test
    void shouldRouteAllLevels() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("all.levels", sink);

        logger.trace("t");
        logger.debug("d");
        logger.info ("i");
        logger.warn ("w");
        logger.error("e");

        assert sink.events.size == 5;
        assert sink.events[0].level == logging.Level.Trace;
        assert sink.events[1].level == logging.Level.Debug;
        assert sink.events[2].level == logging.Level.Info;
        assert sink.events[3].level == logging.Level.Warn;
        assert sink.events[4].level == logging.Level.Error;
    }

    @Test
    void shouldCarryException() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("with.cause", sink);
        Exception   boom   = new Exception("boom");

        logger.error("failure", cause=boom);

        assert sink.events.size == 1;
        assert sink.events[0].exception == boom;
    }
}
