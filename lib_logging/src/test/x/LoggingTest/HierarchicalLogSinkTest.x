import logging.BasicLogger;
import logging.HierarchicalLogSink;
import logging.Logger;

/**
 * Tests Logback-style longest-prefix level configuration.
 */
class HierarchicalLogSinkTest {

    @Test
    void shouldUseRootLevelWhenNoPrefixMatches() {
        ListLogSink         delegate = new ListLogSink();
        HierarchicalLogSink sink     = new HierarchicalLogSink(delegate, logging.Level.Info);
        Logger              logger   = new BasicLogger("other.component", sink);

        logger.debug("drop");
        logger.info("keep");

        assert delegate.events.size == 1;
        assert delegate.events[0].message == "keep";
    }

    @Test
    void shouldUseLongestMatchingPrefix() {
        ListLogSink         delegate = new ListLogSink();
        HierarchicalLogSink sink     = new HierarchicalLogSink(delegate, logging.Level.Warn);
        Logger              logger   = new BasicLogger("payments.stripe.checkout", sink);

        sink.setLevel("payments", logging.Level.Info);
        sink.setLevel("payments.stripe", logging.Level.Debug);

        logger.debug("debug stripe");
        logger.trace("drop trace");

        assert delegate.events.size == 1;
        assert delegate.events[0].message == "debug stripe";
    }
}
