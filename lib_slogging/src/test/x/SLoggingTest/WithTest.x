import slogging.Attr;
import slogging.Logger;

/**
 * Tests the slog `With(...)` derivation idiom: a logger derived from another carries
 * the parent's attributes plus its own, and these flow into every record emitted via
 * the derived logger.
 *
 * No direct equivalent in `lib_logging` (SLF4J uses MDC for this). The closest test in
 * the SLF4J library is `MDCTest.shouldAttachSnapshotToLogEvent`.
 */
class WithTest {

    @Test
    void shouldCarryAttachedAttrsIntoRecord() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger derived = base.with([
                Attr.of("requestId", "r_42"),
                Attr.of("user",      "u_3"),
        ]);

        derived.info("processing");

        assert handler.records.size == 1;
        Attr[] attrs = handler.records[0].attrs;
        assert attrs.size == 2;
        assert attrs[0].key == "requestId" && attrs[0].value == "r_42";
        assert attrs[1].key == "user"      && attrs[1].value == "u_3";
    }

    @Test
    void shouldConcatenateAcrossWithChains() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger withA = base.with([Attr.of("a", 1)]);
        Logger withB = withA.with([Attr.of("b", 2)]);

        withB.info("event");

        assert handler.records.size == 1;
        Attr[] attrs = handler.records[0].attrs;
        assert attrs.size == 2;
        assert attrs[0].key == "a";
        assert attrs[1].key == "b";
    }

    @Test
    void shouldCombineAttachedAttrsWithCallTimeExtras() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger reqLog = base.with([Attr.of("requestId", "r_99")]);

        reqLog.info("processing", [Attr.of("path", "/api")]);

        Attr[] attrs = handler.records[0].attrs;
        assert attrs.size == 2;
        assert attrs[0].key == "requestId";
        assert attrs[1].key == "path";
    }

    @Test
    void shouldNotAffectParentLogger() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger derived = base.with([Attr.of("k", "v")]);

        base.info("from base");
        derived.info("from derived");

        assert handler.records[0].attrs.empty;
        assert handler.records[1].attrs.size == 1;
    }
}
