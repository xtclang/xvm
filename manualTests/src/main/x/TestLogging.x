/**
 * Manual end-to-end scenario for the SLF4J-shaped `lib_logging` POC.
 *
 * The unit tests prove the individual pieces; this module is the executable story a
 * reviewer can run to see the real injection path and the major API features together:
 * injected logger/MDC, named loggers, formatting, markers, lazy suppliers, fluent
 * structured events, explicit source metadata, and Logback-style backend primitives.
 */
module TestLogging {
    package log import logging.xtclang.org;

    import log.AsyncLogSink;
    import log.BasicLogger;
    import log.CompositeLogSink;
    import log.ConsoleLogSink;
    import log.HierarchicalLogSink;
    import log.JsonLogSink;
    import log.JsonLogSinkOptions;
    import log.Level;
    import log.LogEvent;
    import log.Logger;
    import log.LoggerRegistry;
    import log.LogSink;
    import log.Marker;
    import log.MarkerFactory;
    import log.MDC;
    import log.MemoryLogSink;
    import log.NoopLogSink;

    @Inject Console console;

    void run() {
        console.print("=== TestLogging: SLF4J-shaped logging ===");
        runInjectedLogger();
        runMdcAndNamedLogger();
        runMarkersAndFluentBuilder();
        runBackendPrimitives();
        runRegistry();
    }

    /**
     * Real runtime injection of `logging.Logger`. The default injected logger uses the
     * familiar resource name `logger` and is backed by `ConsoleLogSink`.
     */
    void runInjectedLogger() {
        console.print("--- injected Logger: levels, formatting, lazy messages ---");

        @Inject Logger logger;
        assert logger.infoEnabled;
        assert !logger.debugEnabled;

        logger.info("hello, {}", ["world"]);
        logger.warn("disk space low: {} MB", [42]);

        @Volatile Int lazyCalls = 0;
        logger.debug(() -> {
            ++lazyCalls;
            return "this debug message must not be built";
        });
        logger.atDebug()
              .addLazyArgument(() -> {
                  ++lazyCalls;
                  return "this debug argument must not be built";
              })
              .log("debug {}");
        assert lazyCalls == 0;

        logger.info(() -> {
            ++lazyCalls;
            return "lazy info message built after the Info check";
        });
        assert lazyCalls == 1;

        try {
            failingOperation();
        } catch (Exception e) {
            logger.error("operation failed for {}", ["manual"], cause=e);
        }

        logger.log(Level.Warn, "runtime-selected level {}", ["Warn"]);
        logger.logAt(Level.Info, () -> "source-aware lazy message",
                "TestLogging.x", 74);
    }

    /**
     * Injected MDC plus `Logger.named(...)`, the Ecstasy equivalent of deriving a
     * per-class/per-component SLF4J logger.
     */
    void runMdcAndNamedLogger() {
        console.print("--- injected MDC and named logger ---");

        @Inject Logger logger;
        @Inject MDC    mdc;

        Logger payments = logger.named("manual.logging.payments");

        mdc.put("requestId", "r_manual");
        mdc.put("user",      "u_7");
        payments.info("handling payment {}", ["p_123"]);

        assert mdc.copyOfContextMap.getOrNull("requestId") == "r_manual";

        mdc.remove("user");
        assert !mdc.copyOfContextMap.contains("user");
        mdc.clear();
    }

    /**
     * Marker DAGs and the SLF4J 2.x fluent builder against a memory sink so the manual
     * scenario asserts the payload, not just the console output.
     */
    void runMarkersAndFluentBuilder() {
        console.print("--- markers and fluent structured event builder ---");

        MemoryLogSink sink    = new MemoryLogSink();
        Logger        logger  = new BasicLogger("manual.logging.fluent", sink);
        MarkerFactory markers = new MarkerFactory();
        Marker        audit   = markers.getMarker("AUDIT");
        Marker        security = markers.getMarker("SECURITY");
        Exception     cause   = new Exception("manual cause");

        security.add(audit);
        assert markers.exists("AUDIT");
        assert security.contains(audit);

        logger.atInfo()
              .addMarker(security)
              .setCause(cause)
              .addArgument("checkout")
              .addLazyArgument(() -> "lazy-arg")
              .addKeyValue("requestId", "r_fluent")
              .addLazyKeyValue("payload", () -> "lazy-payload")
              .log("operation {} {}");

        assert sink.events.size == 1;
        LogEvent event = sink.events[0];
        assert event.level == Level.Info;
        assert event.message == "operation checkout lazy-arg";
        assert event.marker?.contains(audit) : assert;
        assert event.exception == cause;
        assert event.arguments[1].as(String) == "lazy-arg";
        assert event.keyValues.getOrNull("requestId").as(String) == "r_fluent";
        assert event.keyValues.getOrNull("payload").as(String) == "lazy-payload";
    }

    /**
     * The backend layer that gives this SLF4J-shaped facade Logback-style operational
     * power: fanout, hierarchical level config, async wrapping, JSON/redaction, and nop.
     */
    void runBackendPrimitives() {
        console.print("--- Logback-style backend primitives ---");

        MemoryLogSink first  = new MemoryLogSink();
        MemoryLogSink second = new MemoryLogSink();
        Logger fanout = new BasicLogger("manual.logging.fanout",
                new CompositeLogSink([first, second]));
        fanout.warn("fanout event");
        assert first.events.size == 1;
        assert second.events.size == 1;

        MemoryLogSink       hierarchicalTarget = new MemoryLogSink();
        HierarchicalLogSink hierarchy          = new HierarchicalLogSink(
                hierarchicalTarget, Level.Warn);
        hierarchy.setLevel("manual.logging.debug", Level.Debug);

        Logger muted = new BasicLogger("manual.logging.other.Component", hierarchy);
        Logger debug = new BasicLogger("manual.logging.debug.Component", hierarchy);
        muted.info("hidden by root Warn level");
        debug.debug("visible through prefix Debug override");
        assert hierarchicalTarget.events.size == 1;
        assert hierarchicalTarget.events[0].message == "visible through prefix Debug override";

        MemoryLogSink asyncTarget = new MemoryLogSink();
        AsyncLogSink  async       = new AsyncLogSink(asyncTarget, 8);
        Logger        asyncLogger = new BasicLogger("manual.logging.async", async);
        asyncLogger.info("async event");
        async.flush();
        assert asyncTarget.events.size == 1;

        Logger jsonLogger = new BasicLogger("manual.logging.json",
                new JsonLogSink(new JsonLogSinkOptions(Level.Debug, ["secret"])));
        jsonLogger.atInfo()
                  .addKeyValue("secret", "redact-me")
                  .addKeyValue("visible", "ok")
                  .log("json sink redaction demo");

        Logger noop = new BasicLogger("manual.logging.noop", new NoopLogSink());
        assert !noop.errorEnabled;
        noop.error("dropped by no-op sink");
    }

    /**
     * Name-keyed logger interning, corresponding to SLF4J `ILoggerFactory` semantics.
     */
    void runRegistry() {
        console.print("--- logger registry interning ---");

        LoggerRegistry registry = new LoggerRegistry(new MemoryLogSink());
        Logger         one      = registry.ensure("manual.logging.registry");
        Logger         two      = registry.ensure("manual.logging.registry");
        Logger         child    = one.named("manual.logging.registry.child");
        Logger         child2   = registry.ensure("manual.logging.registry.child");

        assert &one == &two;
        assert &child == &child2;
        child.info("registry child event");
    }

    void failingOperation() {
        throw new Exception("boom");
    }
}
