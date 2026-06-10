import slogging.AnyValue;
import slogging.Attributes;
import slogging.Logger;

/**
 * Tests `Logger.withGroup(name)` — slog's namespace-prefix derivation. A grouped logger
 * produces records whose attributes the *handler* renders under the supplied prefix.
 *
 * No equivalent exists in `lib_logging` (SLF4J's hierarchical logger names cover a
 * different concern — the *logger's identity*, not the *attributes' namespace*).
 */
class WithGroupTest {

    @Test
    void shouldReturnDistinctLoggerInstance() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);
        Logger      grouped = base.withGroup("payments");

        // The Logger is a const; "distinctness" here is about API shape: the two refer
        // to different logger instances even when the underlying handler is the same.
        assert &base != &grouped;
    }

    @Test
    void shouldGroupOnlySubsequentAttributes() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler).with(Map:["env"="prod"]);
        Logger      grouped = base.withGroup("payments");

        grouped.info("charged", Map:["amount"=1099]);

        // Matches Go slog ordering: attributes bound before WithGroup stay outside the group;
        // attributes supplied after WithGroup are nested under the group.
        assert handler.records.size == 1;
        Attributes attributes = handler.records[0].attributes;
        assert attributes.size == 2;
        String[] keys = attributes.keys.toArray();
        assert keys[0] == "env";
        assert keys[1] == "payments";
        AnyValue paymentsValue = attributes["payments"] ?: assert;
        assert paymentsValue.is(Map<String, AnyValue>);

        Map<String, AnyValue> paymentAttributes = paymentsValue.as(Map<String, AnyValue>);
        assert paymentAttributes.size == 1;
        assert paymentAttributes["amount"] == 1099;
    }

    @Test
    void shouldPutAttributesBoundAfterGroupInsideGroup() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);
        Logger      grouped = base.withGroup("payments").with(Map:["currency"="SEK"]);

        grouped.info("charged", Map:["amount"=1099]);

        Attributes attributes = handler.records[0].attributes;
        assert attributes.size == 1;
        AnyValue paymentsValue = attributes["payments"] ?: assert;
        assert paymentsValue.is(Map<String, AnyValue>);

        Map<String, AnyValue> paymentAttributes = paymentsValue.as(Map<String, AnyValue>);
        assert paymentAttributes.size == 2;
        String[] paymentKeys = paymentAttributes.keys.toArray();
        assert paymentKeys[0] == "currency";
        assert paymentKeys[1] == "amount";
    }
}
