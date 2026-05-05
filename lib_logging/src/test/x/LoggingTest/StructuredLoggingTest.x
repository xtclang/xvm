import logging.BasicLogger;
import logging.Logger;

/**
 * Bare-minimum SLF4J 2.x structured-logging test: verifies that
 * `LoggingEventBuilder.addKeyValue(...)` actually flows through to a `LogEvent.keyValues`
 * map that sinks can read. Mirrors SLF4J's KV-on-builder behaviour without taking on its
 * full `KeyValuePair` data model — for v0 a `Map<String, Object>` is enough.
 */
class StructuredLoggingTest {

    @Test
    void shouldCarrySingleKeyValueThroughBuilder() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("kv.single", sink);

        logger.atInfo()
              .addKeyValue("requestId", "r_42")
              .log("payment processed");

        assert sink.events.size == 1;
        assert sink.events[0].message == "payment processed";
        assert sink.events[0].keyValues.size == 1;
        assert sink.events[0].keyValues.getOrNull("requestId").as(String) == "r_42";
    }

    @Test
    void shouldCarryMultipleKeyValuesAndPreserveOrder() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("kv.many", sink);

        Int amount = 100;
        logger.atInfo()
              .addKeyValue("requestId", "r_99")
              .addKeyValue("userId",    "u_7")
              .addKeyValue("amount",    amount)
              .log("checkout completed");

        assert sink.events.size == 1;
        assert sink.events[0].keyValues.size == 3;

        // ListMap preserves insertion order; this matches SLF4J's KeyValuePair list ordering.
        String[] orderedKeys = sink.events[0].keyValues.keys.toArray();
        assert orderedKeys[0] == "requestId";
        assert orderedKeys[1] == "userId";
        assert orderedKeys[2] == "amount";

        assert sink.events[0].keyValues.getOrNull("amount").as(Int) == 100;
    }

    @Test
    void shouldEmitEmptyKeyValuesWhenBuilderHasNone() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("kv.empty", sink);

        logger.atInfo().log("no extras");

        assert sink.events.size == 1;
        assert sink.events[0].keyValues.empty;
    }

    @Test
    void shouldOverwriteValueOnDuplicateKey() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("kv.dup", sink);

        Int first  = 1;
        Int second = 2;
        logger.atInfo()
              .addKeyValue("attempt", first)
              .addKeyValue("attempt", second)
              .log("retry");

        assert sink.events.size == 1;
        assert sink.events[0].keyValues.size == 1;
        assert sink.events[0].keyValues.getOrNull("attempt").as(Int) == 2;
    }
}
