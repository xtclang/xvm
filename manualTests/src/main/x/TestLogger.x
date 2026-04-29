/**
 * Manual test that exercises lib_logging end-to-end.
 *
 * The unit tests under `lib_logging/src/test/x/LoggingTest/` pin down the API surface
 * by constructing `BasicLogger` directly against an in-memory sink. That works fine for
 * verifying the API, but it does not exercise the **runtime injection** path
 * (`@Inject Logger logger;`) — and there are limits to what xunit can express about
 * injectability. This module is the place to drive that path explicitly.
 *
 * Today, until `RTLogger.java` lands in `javatools_jitbridge` and registers a
 * `Logger` factory in `nMainInjector`, `@Inject Logger logger` won't actually resolve.
 * The fallback in the meantime — and the reference shape user code will write — is to
 * construct a `BasicLogger` directly, which is what the `runDirect` method does. Once
 * the runtime side lands, `runInjected` will start working with no source change here.
 */
module TestLogger {
    package log import logging.xtclang.org;

    @Inject Console console;

    void run() {
        console.print("--- runDirect ---");
        runDirect();

        console.print("--- runInjected (will be a no-op until runtime-side is wired) ---");
        try {
            runInjected();
        } catch (Exception e) {
            console.print($"runInjected not yet supported: {e.text}");
        }
    }

    /**
     * Drives the library by constructing a `BasicLogger` over a `ConsoleLogSink`
     * explicitly. Works today.
     */
    void runDirect() {
        log.LogSink sink   = new log.ConsoleLogSink();
        log.Logger  logger = new log.BasicLogger("TestLogger.direct", sink);

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
        log.Marker audit = new log.BasicMarker("AUDIT");
        logger.info("user signed in", marker=audit);

        // Fluent builder.
        logger.atInfo()
              .addMarker(audit)
              .addKeyValue("requestId", "r_42")
              .log("payment processed");
    }

    /**
     * Drives the library through `@Inject Logger logger;`. This is what user code is
     * meant to write; it depends on runtime work that hasn't happened yet.
     */
    void runInjected() {
        @Inject log.Logger logger;
        logger.info("hello from the injected logger");
    }

    void failingOperation() {
        throw new Exception("boom");
    }
}
