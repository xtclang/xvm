import slogging.Attr;
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
    void shouldGroupOnlySubsequentAttrs() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler).with([Attr.of("env", "prod")]);
        Logger      grouped = base.withGroup("payments");

        grouped.info("charged", [Attr.of("amount", 1099)]);

        // Matches Go slog ordering: attrs bound before WithGroup stay outside the group;
        // attrs supplied after WithGroup are nested under the group.
        assert handler.records.size == 1;
        Attr[] attrs = handler.records[0].attrs;
        assert attrs.size == 2;
        assert attrs[0].key == "env";
        assert attrs[1].key == "payments";
        assert attrs[1].value.is(Attr[]);

        Attr[] paymentAttrs = attrs[1].value.as(Attr[]);
        assert paymentAttrs.size == 1;
        assert paymentAttrs[0].key   == "amount";
        assert paymentAttrs[0].value == 1099;
    }

    @Test
    void shouldPutAttrsBoundAfterGroupInsideGroup() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);
        Logger      grouped = base.withGroup("payments").with([Attr.of("currency", "SEK")]);

        grouped.info("charged", [Attr.of("amount", 1099)]);

        Attr[] attrs = handler.records[0].attrs;
        assert attrs.size == 1;
        assert attrs[0].key == "payments";
        assert attrs[0].value.is(Attr[]);

        Attr[] paymentAttrs = attrs[0].value.as(Attr[]);
        assert paymentAttrs.size == 2;
        assert paymentAttrs[0].key == "currency";
        assert paymentAttrs[1].key == "amount";
    }
}
