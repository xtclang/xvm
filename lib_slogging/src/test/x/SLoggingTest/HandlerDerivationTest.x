import slogging.Logger;

/**
 * Tests that `Logger.with(...)` and `Logger.withGroup(...)` call into the handler
 * derivation hooks. That hook is the main extra power in the slog handler contract
 * compared with the two-method `LogSink` contract.
 */
class HandlerDerivationTest {

    @Test
    void shouldCallHandlerWithAttributesWhenDerivingLogger() {
        TrackingHandler handler = new TrackingHandler();
        Logger          base    = new Logger(handler);

        Logger derived = base.with(Map:["requestId"="r_1"]);
        derived.info("processing");

        assert handler.withAttributesCalls == 1;
        assert handler.lastAttributes.size == 1;
        assert handler.lastAttributes["requestId"] == "r_1";
        assert handler.records.size == 1;
    }

    @Test
    void shouldCallHandlerWithGroupWhenGroupingLogger() {
        TrackingHandler handler = new TrackingHandler();
        Logger          base    = new Logger(handler);

        Logger grouped = base.withGroup("payments");
        grouped.info("charged", Map:["amount"=1099]);

        assert handler.withGroupCalls == 1;
        assert handler.groups.size == 1;
        assert handler.groups[0] == "payments";
        assert handler.records.size == 1;
    }
}
