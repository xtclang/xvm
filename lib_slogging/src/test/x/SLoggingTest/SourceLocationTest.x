import slogging.Attr;
import slogging.Level;
import slogging.Logger;

/**
 * Tests explicit source-location capture via `Logger.logAt(...)`.
 */
class SourceLocationTest {

    @Test
    void shouldPopulateSourceFieldsWhenUsingLogAt() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);

        logger.logAt(Level.Warn, "slow payment", "PaymentService.x", 77,
                [Attr.of("elapsedMs", 451)]);

        assert handler.records.size == 1;
        assert handler.records[0].sourceFile == "PaymentService.x";
        assert handler.records[0].sourceLine == 77;
        assert handler.records[0].attrs[0].key == "elapsedMs";
    }

    @Test
    void shouldLeaveSourceFieldsEmptyForNormalLogCalls() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);

        logger.info("normal");

        assert handler.records[0].sourceFile == Null;
        assert handler.records[0].sourceLine == -1;
    }
}
