import slogging.Attributes;
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
    void shouldCarryAttachedAttributesIntoRecord() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger derived = base.with(Map:[
                "requestId" = "r_42",
                "user"      = "u_3",
        ]);

        derived.info("processing");

        assert handler.records.size == 1;
        Attributes attributes = handler.records[0].attributes;
        assert attributes.size == 2;
        String[] keys = attributes.keys.toArray();
        assert keys[0] == "requestId" && attributes["requestId"] == "r_42";
        assert keys[1] == "user"      && attributes["user"]      == "u_3";
    }

    @Test
    void shouldConcatenateAcrossWithChains() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger withA = base.with(Map:["a"=1]);
        Logger withB = withA.with(Map:["b"=2]);

        withB.info("event");

        assert handler.records.size == 1;
        Attributes attributes = handler.records[0].attributes;
        assert attributes.size == 2;
        String[] keys = attributes.keys.toArray();
        assert keys[0] == "a";
        assert keys[1] == "b";
    }

    @Test
    void shouldCombineAttachedAttributesWithCallTimeExtras() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger reqLog = base.with(Map:["requestId"="r_99"]);

        reqLog.info("processing", Map:["path"="/api"]);

        Attributes attributes = handler.records[0].attributes;
        assert attributes.size == 2;
        String[] keys = attributes.keys.toArray();
        assert keys[0] == "requestId";
        assert keys[1] == "path";
    }

    @Test
    void shouldNotAffectParentLogger() {
        ListHandler handler = new ListHandler();
        Logger      base    = new Logger(handler);

        Logger derived = base.with(Map:["k"="v"]);

        base.info("from base");
        derived.info("from derived");

        assert handler.records[0].attributes.empty;
        assert handler.records[1].attributes.size == 1;
    }
}
