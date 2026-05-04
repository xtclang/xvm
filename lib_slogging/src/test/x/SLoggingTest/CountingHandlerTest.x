import slogging.Level;
import slogging.Logger;

/**
 * Demonstrates that a custom slog `Handler` can be plugged in and observed
 * end-to-end, parallel to `LoggingTest.CountingSinkTest`.
 */
class CountingHandlerTest {

    @Test
    void shouldCountPerLevel() {
        CountingHandler handler = new CountingHandler();
        Logger          logger  = new Logger(handler);

        logger.info ("a");
        logger.info ("b");
        logger.warn ("c");
        logger.error("d");

        assert handler.counts[Level.Info]  == 2;
        assert handler.counts[Level.Warn]  == 1;
        assert handler.counts[Level.Error] == 1;
    }
}
