import logging.BasicLogger;
import logging.Logger;

/**
 * Tests explicit source-location capture in the SLF4J-shaped facade.
 */
class SourceLocationTest {

    @Test
    void shouldPopulateSourceFieldsWhenUsingLogAt() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("source.test", sink);

        logger.logAt(logging.Level.Warn, "slow payment", "PaymentService.x", 77);

        assert sink.events.size == 1;
        assert sink.events[0].sourceFile == "PaymentService.x";
        assert sink.events[0].sourceLine == 77;
    }

    @Test
    void shouldLeaveSourceFieldsEmptyForNormalLogCalls() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("source.normal", sink);

        logger.info("normal");

        assert sink.events[0].sourceFile == Null;
        assert sink.events[0].sourceLine == -1;
    }
}
