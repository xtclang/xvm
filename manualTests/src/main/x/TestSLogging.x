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

    import slog.AsyncHandler;
    import slog.Attr;
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
        runDerivedLoggersAndLazyAttrs();
        runHandlerPrimitives();
        runContextBinding();
    }

    /**
     * Real runtime injection of `slogging.Logger`. The same resource name `logger`
     * works because the injector also keys by requested type.
     */
    void runInjectedLogger() {
        console.print("--- injected slog Logger: levels, attrs, lazy messages ---");

        @Inject Logger logger;
        assert logger.infoEnabled;
        assert !logger.debugEnabled;

        logger.info("hello", [Attr.of("style", "slog")]);

        @Volatile Int lazyCalls = 0;
        logger.debug(() -> {
            ++lazyCalls;
            return "this debug message must not be built";
        });
        assert lazyCalls == 0;

        logger.info(() -> {
            ++lazyCalls;
            return "lazy info message built after the Info check";
        }, [Attr.of("scenario", "injected")]);
        assert lazyCalls == 1;

        try {
            failingOperation();
        } catch (Exception e) {
            logger.error("operation failed", [Attr.of("operation", "manual")], cause=e);
        }

        Level notice = new Level(2, "NOTICE");
        logger.log(notice, "custom level", [Attr.of("severity", notice.severity)]);
        logger.logAt(Level.Warn, () -> "source-aware lazy message",
                "TestSLogging.x", 64, [Attr.of("source", "explicit")]);
    }

    /**
     * The central slog idea: derive loggers with attrs/groups instead of named logger
     * categories and MDC. Also exercises `Attr.lazy`, the POC equivalent of Go
     * `LogValuer`.
     */
    void runDerivedLoggersAndLazyAttrs() {
        console.print("--- derived loggers, groups, and lazy attrs ---");

        MemoryHandler capture = new MemoryHandler();
        Logger        root    = new Logger(capture);
        LazyCounter   counter = new LazyCounter();

        Logger payments = root.with([
                Attr.of  ("requestId", "r_slog"),
                Attr.lazy("bound", () -> counter.value("bound-value")),
        ]).withGroup("payments");

        payments.info("charged", [
                Attr.lazy ("payload", () -> counter.value("payload-value")),
                Attr.group("card", [Attr.of("last4", "4242")]),
                Attr.of   ("amount", 1099),
        ]);

        assert counter.calls == 2;
        assert capture.records.size == 1;

        Record record = capture.records[0];
        assert record.message == "charged";
        assert record.attrs[0].key == "requestId";
        assert record.attrs[1].key == "bound";
        assert record.attrs[1].value.as(String) == "bound-value";
        assert record.attrs[2].key == "payments";

        Attr[] paymentAttrs = record.attrs[2].value.as(Attr[]);
        assert paymentAttrs[0].key == "payload";
        assert paymentAttrs[0].value.as(String) == "payload-value";
        assert paymentAttrs[1].key == "card";
        assert paymentAttrs[2].key == "amount";
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
        asyncLogger.info("async record", [Attr.of("queued", True)]);
        async.flush();
        assert asyncTarget.records.size == 1;

        Logger textLogger = new Logger(new TextHandler(Level.Debug));
        textLogger.debug("text handler debug", [Attr.of("visible", True)]);

        Logger jsonLogger = new Logger(new JSONHandler(
                new HandlerOptions(Level.Debug, ["secret"])));
        jsonLogger.info("json handler redaction", [
                Attr.of("secret", "redact-me"),
                Attr.of("visible", "ok"),
        ]);

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
        Logger        bound   = root.with([Attr.of("context", "bound")]);

        using (context.bind(bound)) {
            Logger current = context.currentOr(root);
            current.info("from bound context");
        }

        assert capture.records.size == 1;
        assert capture.records[0].attrs[0].key == "context";
        assert capture.records[0].attrs[0].value.as(String) == "bound";
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
