import logging.BasicLogger;
import logging.Logger;

/**
 * Demonstrates that a custom `LogSink` can be plugged in and observed end-to-end.
 * The sink is the per-level `CountingSink` defined alongside; this test class drives
 * it through a `BasicLogger` and asserts the counts come out right.
 */
class CountingSinkTest {

    @Test
    void shouldCountPerLevel() {
        CountingSink sink   = new CountingSink();
        Logger       logger = new BasicLogger("counter", sink);

        logger.info ("a");
        logger.info ("b");
        logger.warn ("c");
        logger.error("d");

        assert sink.counts[logging.Level.Info]  == 2;
        assert sink.counts[logging.Level.Warn]  == 1;
        assert sink.counts[logging.Level.Error] == 1;
    }
}
