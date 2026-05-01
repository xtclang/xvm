import slogging.Attr;
import slogging.Level;
import slogging.Logger;

/**
 * Tests that the handler's `enabled(level)` fast-path drops events below threshold.
 * Mirrors `LoggingTest.LevelCheckTest`.
 */
class LevelCheckTest {

    @Test
    void shouldDropRecordsBelowThreshold() {
        ListHandler handler = new ListHandler();
        handler.setLevel(Level.Warn);
        Logger logger = new Logger(handler);

        logger.debug("d");
        logger.info ("i");
        logger.warn ("w");
        logger.error("e");

        assert handler.records.size == 2;
        assert handler.records[0].level == Level.Warn;
        assert handler.records[1].level == Level.Error;
    }

    @Test
    void shouldReportEnabledFlagsConsistentlyWithThreshold() {
        ListHandler handler = new ListHandler();
        handler.setLevel(Level.Info);
        Logger logger = new Logger(handler);

        assert !logger.debugEnabled;
        assert  logger.infoEnabled;
        assert  logger.warnEnabled;
        assert  logger.errorEnabled;
    }
}
