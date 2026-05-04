import slogging.Logger;
import slogging.NopHandler;

/**
 * Tests the shipped discard handler. Mirrors `lib_logging.NoopLogSink`: disabled level
 * checks prevent records from being constructed or emitted.
 */
class NopHandlerTest {

    @Test
    void shouldReportAllLevelsDisabled() {
        Logger logger = new Logger(new NopHandler());

        assert !logger.debugEnabled;
        assert !logger.infoEnabled;
        assert !logger.warnEnabled;
        assert !logger.errorEnabled;
    }
}
