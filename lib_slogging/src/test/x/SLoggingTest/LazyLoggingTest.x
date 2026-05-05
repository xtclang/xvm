import slogging.Attr;
import slogging.Logger;

/**
 * Verifies slog-shaped lazy logging semantics. The logger must perform the handler
 * enabled check before it invokes lazy message suppliers or `Attr.lazy` suppliers.
 */
class LazyLoggingTest {

    @Test
    void shouldNotEvaluateLazyMessageWhenLevelDisabled() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        @Volatile Int calls = 0;

        handler.setLevel(slogging.Level.Info);
        logger.debug(() -> {
            ++calls;
            return "expensive debug message";
        });

        assert calls == 0;
        assert handler.records.empty;
    }

    @Test
    void shouldEvaluateLazyMessageWhenEnabled() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        @Volatile Int calls = 0;

        logger.info(() -> {
            ++calls;
            return "expensive info message";
        });

        assert calls == 1;
        assert handler.records.size == 1;
        assert handler.records[0].message == "expensive info message";
    }

    @Test
    void shouldNotEvaluateLazyAttrWhenLevelDisabled() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        @Volatile Int calls = 0;

        handler.setLevel(slogging.Level.Info);
        logger.debug("payload", [
                Attr.lazy("json", () -> {
                    ++calls;
                    return "expensive";
                }),
        ]);

        assert calls == 0;
        assert handler.records.empty;
    }

    @Test
    void shouldEvaluateLazyAttrWhenEnabled() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        LazyCounter counter = new LazyCounter();

        logger.info("payload", [
                Attr.lazy("json", () -> {
                    return counter.value("expensive");
                }),
        ]);

        assert counter.calls == 1;
        assert handler.records.size == 1;
        assert handler.records[0].attrs.size == 1;
        assert handler.records[0].attrs[0].key == "json";
        assert handler.records[0].attrs[0].value.as(String) == "expensive";
    }
}
