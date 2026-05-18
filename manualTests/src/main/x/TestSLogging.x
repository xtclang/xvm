/**
 * Manual end-to-end scenario for the Go `log/slog`-shaped `lib_slogging` POC.
 *
 * Run this next to `TestLogging` to compare the two proposed call shapes. This file
 * exercises injected slog logger acquisition, attrs, derived loggers, groups, lazy
 * message/attr suppliers, explicit source metadata, context binding, custom levels,
 * and the handler backend SPI.
 */
module TestSLogging {
    package slog import slogging.xtclang.org;

    import slog.Attributes;
    import slog.AsyncHandler;
    import slog.HandlerOptions;
    import slog.JSONHandler;
    import slog.Level;
    import slog.Logger;
    import slog.LoggerContext;
    import slog.MemoryHandler;
    import slog.NopHandler;
    import slog.Record;
    import slog.TextHandler;

    @Inject Console console;

    void run() {
        console.print("=== TestSLogging: slog-shaped logging ===");
        runInjectedLogger();
        runDerivedLoggersAttrs();
        runHandlerPrimitives();
        runContextBinding();
    }

    /**
     * Real runtime injection of `slogging.Logger`. The same resource name `logger`
     * works because the injector also keys by requested type.
     */
    void runInjectedLogger() {
        console.print("--- injected slog Logger: levels, attributes, lazy messages ---");

        @Inject Logger logger;
        assert logger.infoEnabled;
        assert !logger.debugEnabled;

        logger.info("hello", ["style"="slog"]);

        @Volatile Int lazyCalls = 0;
        logger.debug(() -> {
            ++lazyCalls;
            return "this debug message must not be built";
        });
        assert lazyCalls == 0;

        logger.info(() -> {
            ++lazyCalls;
            return "lazy info message built after the Info check";
        }, ["scenario"="injected"]);
        assert lazyCalls == 1;

        try {
            failingOperation();
        } catch (Exception e) {
            logger.error("operation failed", ["operation"="manual"], cause=e);
        }

        Level notice = new Level(2, "NOTICE");
        logger.log(notice, "custom level", ["severity"=notice.severity]);
        logger.logAt(Level.Warn, () -> "source-aware lazy message",
                "TestSLogging.x", 64, ["source"="explicit"]);
    }

    /**
     * The central slog idea: derive loggers with attributes/groups instead of named logger
     * categories and MDC.
     */
    void runDerivedLoggersAttrs() {
        console.print("--- derived loggers and groups ---");

        MemoryHandler capture = new MemoryHandler();
        Logger        root    = new Logger(handler=capture);

        Logger payments = root.with(["requestId"="r_slog"]).withGroup("payments");

        Attributes attributes = Map:["card"=["last4"="4242"], "amount"=1099];
        payments.info("charged", attributes);

        assert capture.records.size == 1;

        Record record = capture.records[0];
        assert record.message == "charged";
        assert record.attributes["requestId"] == "r_slog";
        assert var paymentAttrs := record.attributes.get("payments");
        assert paymentAttrs.is(Attributes);
        assert paymentAttrs["amount"] == 1099;
        assert var cardAttrs := paymentAttrs.get("card");
        assert cardAttrs.is(Attributes);
        assert cardAttrs["last4"] == "4242";
    }

    /**
     * Handler-side equivalents of Logback-style backend composition: memory capture,
     * async wrapping, text/JSON rendering, redaction, and no-op output.
     */
    void runHandlerPrimitives() {
        console.print("--- slog handler primitives ---");

        MemoryHandler asyncTarget = new MemoryHandler();
        AsyncHandler  async       = new AsyncHandler(asyncTarget, 8);
        Logger        asyncLogger = new Logger(async);
        asyncLogger.info("async record", ["queued"=True]);
        async.flush();
        assert asyncTarget.records.size == 1;

        Logger textLogger = new Logger(new TextHandler(Level.Debug));
        textLogger.debug("text handler debug", ["visible"=True]);

        Logger jsonLogger = new Logger(new JSONHandler(
                new HandlerOptions(Level.Debug, ["secret"])));
        jsonLogger.info("json handler redaction", ["secret"="redact-me", "visible"="ok"]);

        Logger noop = new Logger(new NopHandler());
        assert !noop.errorEnabled;
        noop.error("dropped by no-op handler");
    }

    /**
     * Optional implicit context helper for framework/request propagation.
     */
    void runContextBinding() {
        console.print("--- LoggerContext binding ---");

        MemoryHandler capture = new MemoryHandler();
        Logger        root    = new Logger(capture);
        LoggerContext context = new LoggerContext();
        Logger        bound   = root.with(["context"="bound"]);

        using (context.bind(bound)) {
            Logger current = context.currentOr(root);
            current.info("from bound context");
        }

        assert capture.records.size == 1;
        assert var value := capture.records[0].attributes.get("context");
        assert value.is(String) && value == "bound";
    }

    void failingOperation() {
        throw new Exception("boom");
    }

    /**
     * Service-backed lazy counter. `Attr.lazy` stores the supplier in a `const` attr, so
     * captured mutable state must be passable; a service reference is the right manual
     * test stand-in for a real provider object.
     */
    service LazyCounter {
        public/private Int calls = 0;

        String value(String result) {
            ++calls;
            return result;
        }
    }
}
