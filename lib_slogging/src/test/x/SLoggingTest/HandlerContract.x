import slogging.AnyValue;
import slogging.Attributes;
import slogging.Handler;
import slogging.Level;
import slogging.Record;

/**
 * Minimal slogtest-style contract helpers for third-party handlers.
 *
 * Go ships `testing/slogtest` so backend authors can prove that `WithAttributes` and
 * `WithGroup` are implemented correctly. This helper is the Ecstasy POC equivalent:
 * pass a handler and a snapshot function for the records it emitted.
 */
class HandlerContract {

    /**
     * Verify that `withAttributes` prepends bound attributes to call-time attributes.
     */
    static void assertWithAttributesPrepend(Handler root, function Record[] () records) {
        Handler derived = root.withAttributes(Map:["requestId"="r_1"]);
        derived.handle(sample(Map:["path"="/checkout"]));

        Record[] captured = records();
        assert captured.size == 1;
        Attributes attributes = captured[0].attributes;
        assert attributes.size == 2;
        String[] keys = attributes.keys.toArray();
        assert keys[0] == "requestId";
        assert attributes["requestId"] == "r_1";
        assert keys[1] == "path";
    }

    /**
     * Verify that `withGroup` nests subsequent attributes under the group name.
     */
    static void assertWithGroupNests(Handler root, function Record[] () records) {
        Handler grouped = root.withGroup("payments");
        grouped.handle(sample(Map:["amount"=1099]));

        Record[] captured = records();
        assert captured.size == 1;
        Attributes attributes = captured[0].attributes;
        assert attributes.size == 1;
        AnyValue paymentsValue = attributes["payments"] ?: assert;
        assert paymentsValue.is(Map<String, AnyValue>);

        Map<String, AnyValue> children = paymentsValue.as(Map<String, AnyValue>);
        assert children.size == 1;
        assert children["amount"] == 1099;
    }

    /**
     * Shared sample record.
     */
    private static Record sample(Attributes attributes) {
        return new Record(
                timestamp  = new Time("2019-05-22T120123.456Z"),
                message    = "event",
                level      = Level.Info,
                attributes = attributes,
        );
    }
}
