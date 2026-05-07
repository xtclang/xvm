import slogging.Logger;
import slogging.LoggerContext;

/**
 * Tests the optional `SharedContext` helper for request-scoped sloggers.
 */
class LoggerContextTest {

    @Test
    void shouldExposeLoggerInsideUsingScope() {
        LoggerContext context = new LoggerContext();
        ListHandler   handler = new ListHandler();
        Logger        logger  = new Logger(handler).with(Map:["requestId"="r_1"]);

        assert !context.current();

        using (context.bind(logger)) {
            assert Logger current := context.current();
            current.info("inside");
        }

        assert handler.records.size == 1;
        assert handler.records[0].attrs.contains("requestId");
        assert handler.records[0].attrs["requestId"] == "r_1";
        assert !context.current();
    }

    @Test
    void shouldReturnFallbackWhenNoLoggerIsBound() {
        LoggerContext context  = new LoggerContext();
        ListHandler   handler  = new ListHandler();
        Logger        fallback = new Logger(handler);

        context.currentOr(fallback).info("fallback");

        assert handler.records.size == 1;
        assert handler.records[0].message == "fallback";
    }
}
