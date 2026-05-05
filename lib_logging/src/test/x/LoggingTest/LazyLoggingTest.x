import logging.BasicLogger;
import logging.Logger;

/**
 * Verifies lazy logging semantics: supplier bodies must run only after the sink accepts the
 * level/primary-marker check. This covers the Kotlin-style direct-message API and the
 * SLF4J 2.x-style fluent builder.
 */
class LazyLoggingTest {

    @Test
    void shouldNotEvaluateLazyMessageWhenLevelDisabled() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("lazy.message.disabled", sink);
        @Volatile Int calls = 0;

        sink.setLevel(logging.Level.Info);
        logger.debug(() -> {
            ++calls;
            return "expensive debug message";
        });

        assert calls == 0;
        assert sink.events.empty;
    }

    @Test
    void shouldEvaluateLazyMessageWhenEnabled() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("lazy.message.enabled", sink);
        @Volatile Int calls = 0;

        logger.info(() -> {
            ++calls;
            return "expensive info message";
        });

        assert calls == 1;
        assert sink.events.size == 1;
        assert sink.events[0].message == "expensive info message";
    }

    @Test
    void shouldNotEvaluateLazyBuilderValuesWhenLevelDisabled() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("lazy.builder.disabled", sink);
        @Volatile Int argCalls = 0;
        @Volatile Int kvCalls  = 0;

        sink.setLevel(logging.Level.Info);
        logger.atDebug()
              .addLazyArgument(() -> {
                  ++argCalls;
                  return "argument";
              })
              .addLazyKeyValue("requestId", () -> {
                  ++kvCalls;
                  return "r_42";
              })
              .log("value {}");

        assert argCalls == 0;
        assert kvCalls  == 0;
        assert sink.events.empty;
    }

    @Test
    void shouldEvaluateLazyBuilderValuesWhenEnabled() {
        ListLogSink sink   = new ListLogSink();
        Logger      logger = new BasicLogger("lazy.builder.enabled", sink);
        @Volatile Int argCalls = 0;
        @Volatile Int kvCalls  = 0;

        logger.atInfo()
              .addLazyArgument(() -> {
                  ++argCalls;
                  return "argument";
              })
              .addLazyKeyValue("requestId", () -> {
                  ++kvCalls;
                  return "r_42";
              })
              .log("value {}");

        assert argCalls == 1;
        assert kvCalls  == 1;
        assert sink.events.size == 1;
        assert sink.events[0].message == "value argument";
        assert sink.events[0].arguments[0].as(String) == "argument";
        assert sink.events[0].keyValues.getOrNull("requestId").as(String) == "r_42";
    }
}
