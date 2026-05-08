import slogging.Level;
import slogging.Logger;

/**
 * Verifies slog-shaped lazy logging semantics. The logger must perform the handler
 * enabled check before it invokes lazy message suppliers.
 */
class LazyLoggingTest {

    @Test
    void shouldNotEvaluateLazyMessageWhenLevelDisabled() {
        ListHandler handler = new ListHandler();
        Logger      logger  = new Logger(handler);
        @Volatile Int calls = 0;

        handler.setLevel(Level.Info);
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
}
