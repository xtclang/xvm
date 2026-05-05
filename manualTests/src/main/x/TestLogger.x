/**
 * Manual test that exercises lib_logging end-to-end.
 *
 * The unit tests under `lib_logging/src/test/x/LoggingTest/` pin down the API surface by
 * constructing `BasicLogger` directly against an in-memory sink. That works fine for
 * verifying the API, but it does not exercise the **runtime injection** path
 * (`@Inject Logger logger;`) or the per-name derivation pattern
 * (`logger.named("MyService")`) — both of which depend on runtime wiring. This module
 * drives those paths explicitly.
 *
 * The three sections of `run()` line up one-for-one with the three patterns user code
 * is allowed to use:
 *   - `runDirect`      — explicit `new BasicLogger(...)`. Works without the runtime.
 *   - `runInjected`    — `@Inject Logger logger;`. Resolves to the default logger.
 *   - `runInjectedByName` — `@Inject Logger logger; Logger named = logger.named(...)`.
 *                          Per-name derivation, the SLF4J `getLogger(class)` analogue.
 */
module TestLogger {
    package log import logging.xtclang.org;
    package slog import slogging.xtclang.org;
    import log.BasicLogger;
    import log.BasicMarker;
    import log.ConsoleLogSink;
    import log.Logger;
    import log.LogSink;
    import log.Marker;
    import log.MDC;
    import slog.Attr   as SAttr;
    import slog.Logger as SLogger;

    @Inject Console console;

    void run() {
        console.print("--- runDirect (no injection) ---");
        runDirect();

        console.print("--- runInjected (default logger via @Inject) ---");
        runInjected();

        console.print("--- runInjectedByName (per-name via Logger.named) ---");
        runInjectedByName();

        console.print("--- runMDC (per-fiber context) ---");
        runMDC();

        console.print("--- runInjectedSlog (slog-shaped logger via @Inject) ---");
        runInjectedSlog();
    }

    /**
     * Drives the library by constructing a `BasicLogger` over a `ConsoleLogSink`
     * explicitly. Works today.
     */
    void runDirect() {
        LogSink sink   = new ConsoleLogSink();
        Logger  logger = new BasicLogger("TestLogger.direct", sink);

        logger.info ("hello, {}", ["world"]);
        logger.warn ("disk space low: {} MB", [42]);

        try {
            failingOperation();
        } catch (Exception e) {
            logger.error("operation failed", cause=e);
        }

        if (logger.debugEnabled) {
            logger.debug("this won't appear at the default Info threshold");
        }

        // Marker.
        Marker audit = new BasicMarker("AUDIT");
        logger.info("user signed in", marker=audit);

        // Fluent builder.
        logger.atInfo()
              .addMarker(audit)
              .addKeyValue("requestId", "r_42")
              .log("payment processed");
    }

    /**
     * Default-logger injection. The runtime registers the familiar `"logger"` resource
     * name and, for canonical `logging.Logger`, falls back to a BasicLogger named from
     * the caller namespace when the compiler supplied only that default field name.
     */
    void runInjected() {
        @Inject Logger logger;
        logger.info("hello from the injected logger");
    }

    /**
     * Per-name logger via `Logger.named(String)`. Mirrors SLF4J's
     * `LoggerFactory.getLogger(MyClass.class)` idiom: inject the default logger once,
     * derive named children at the call site (or store them as fields on the enclosing
     * class). The derived logger shares this logger's sink, so all configuration applied
     * to the default logger flows through to its descendants.
     *
     * Bare-essentials demo target: the message must print as `hello world` (formatted by
     * `MessageFormatter`), not `hello {}` (raw). The logger-name column on the resulting
     * line should read `Demo`.
     */
    void runInjectedByName() {
        @Inject Logger logger;
        Logger demo = logger.named("Demo");
        demo.info("hello {}", ["world"]);
    }

    void failingOperation() {
        throw new Exception("boom");
    }

    /**
     * MDC end-to-end via runtime injection. Demonstrates that:
     *   - `@Inject MDC` resolves through the native supplier;
     *   - `mdc.put` bindings appear on subsequent log lines via `[mdc=…]`;
     *   - `mdc.remove` and `mdc.clear` take effect from the next emission onward.
     */
    void runMDC() {
        @Inject Logger logger;
        @Inject MDC    mdc;

        mdc.put("requestId", "r_42");
        mdc.put("user",      "u_7");
        console.print($"  [diagnostic] mdc.copyOfContextMap = {mdc.copyOfContextMap}");

        // (A) Through the runtime-injected BasicLogger const. This is intentionally
        // not a service wrapper; keeping the call on this fiber is what makes MDC
        // visible to BasicLogger.emit().
        logger.info("via @Inject Logger (BasicLogger const)");

        // (B) Through a directly-constructed BasicLogger (no service wrapper).
        Logger direct = new BasicLogger("MDC.direct", new ConsoleLogSink());
        direct.info("via direct BasicLogger");

        mdc.clear();
    }

    /**
     * Runtime injection for the slog-shaped library. This proves that the native
     * injector can resolve the same resource name (`logger`) by requested type:
     * `logging.Logger` and `slogging.Logger` are both injectable.
     */
    void runInjectedSlog() {
        @Inject SLogger logger;

        SLogger payments = logger.with([SAttr.of("requestId", "r_slog")])
                                 .withGroup("payments");
        payments.info("charged", [SAttr.of("amount", 1099)]);
    }
}
