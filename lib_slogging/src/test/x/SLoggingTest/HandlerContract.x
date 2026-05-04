import slogging.Attr;
import slogging.Handler;
import slogging.Level;
import slogging.Record;

/**
 * Minimal slogtest-style contract helpers for third-party handlers.
 *
 * Go ships `testing/slogtest` so backend authors can prove that `WithAttrs` and
 * `WithGroup` are implemented correctly. This helper is the Ecstasy POC equivalent:
 * pass a handler and a snapshot function for the records it emitted.
 */
class HandlerContract {

    /**
     * Verify that `withAttrs` prepends bound attributes to call-time attributes.
     */
    static void assertWithAttrsPrepend(Handler root, function Record[] () records) {
        Handler derived = root.withAttrs([Attr.of("requestId", "r_1")]);
        derived.handle(sample([Attr.of("path", "/checkout")]));

        Record[] captured = records();
        assert captured.size == 1;
        assert captured[0].attrs.size == 2;
        assert captured[0].attrs[0].key   == "requestId";
        assert captured[0].attrs[0].value == "r_1";
        assert captured[0].attrs[1].key   == "path";
    }

    /**
     * Verify that `withGroup` nests subsequent attrs under the group name.
     */
    static void assertWithGroupNests(Handler root, function Record[] () records) {
        Handler grouped = root.withGroup("payments");
        grouped.handle(sample([Attr.of("amount", 1099)]));

        Record[] captured = records();
        assert captured.size == 1;
        assert captured[0].attrs.size == 1;
        assert captured[0].attrs[0].key == "payments";
        assert captured[0].attrs[0].value.is(Attr[]);

        Attr[] children = captured[0].attrs[0].value.as(Attr[]);
        assert children.size == 1;
        assert children[0].key   == "amount";
        assert children[0].value == 1099;
    }

    /**
     * Shared sample record.
     */
    private static Record sample(Attr[] attrs) {
        return new Record(
                time    = new Time("2019-05-22T120123.456Z"),
                message = "event",
                level   = Level.Info,
                attrs   = attrs,
        );
    }
}
