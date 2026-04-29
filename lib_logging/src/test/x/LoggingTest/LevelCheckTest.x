import logging.BasicLogger;
import logging.Logger;

/**
 * Verifies the level-check fast path: events below the sink's threshold are dropped
 * before any sink-side `log()` work happens, and the per-level `*Enabled` properties
 * reflect the threshold faithfully.
 */
class LevelCheckTest {

    @Test
    void shouldDropEventsBelowThreshold() {
        ListLogSink sink   = new ListLogSink();
        sink.setLevel(logging.Level.Warn);
        Logger      logger = new BasicLogger("threshold", sink);

        logger.trace("ignored");
        logger.debug("ignored");
        logger.info ("ignored");
        logger.warn ("kept");
        logger.error("kept");

        assert sink.events.size == 2;
        assert sink.events[0].level == logging.Level.Warn;
        assert sink.events[1].level == logging.Level.Error;
    }

    @Test
    void shouldReportEnabledFlagsConsistentlyWithThreshold() {
        ListLogSink sink   = new ListLogSink();
        sink.setLevel(logging.Level.Info);
        Logger      logger = new BasicLogger("flags", sink);

        assert !logger.traceEnabled;
        assert !logger.debugEnabled;
        assert  logger.infoEnabled;
        assert  logger.warnEnabled;
        assert  logger.errorEnabled;
    }

    @Test
    void shouldDropEverythingAtOff() {
        ListLogSink sink   = new ListLogSink();
        sink.setLevel(logging.Level.Off);
        Logger      logger = new BasicLogger("silenced", sink);

        logger.trace("nope");
        logger.debug("nope");
        logger.info ("nope");
        logger.warn ("nope");
        logger.error("nope");

        assert sink.events.size == 0;
    }
}
