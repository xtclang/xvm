import slogging.Attr;
import slogging.Logger;
import slogging.TextHandler;

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
    void shouldNotMutateAttrsOnGroupedLogger() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler).with([Attr.of("env", "prod")]);
        Logger      grouped = base.withGroup("payments");

        grouped.info("charged", [Attr.of("amount", 1099)]);

        // The grouped logger forwards the same attrs the parent carried, plus the call's.
        // The "namespace" effect is something the *handler* applies on render — the
        // record's attrs are unchanged.
        assert handler.records.size == 1;
        Attr[] attrs = handler.records[0].attrs;
        assert attrs.size == 2;
        assert attrs[0].key == "env";
        assert attrs[1].key == "amount";
    }
}
