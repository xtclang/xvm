/**
 * Corresponds to `org.slf4j.Logger`. The same role is played by
 * `ch.qos.logback.classic.Logger` (Logback's concrete class), `org.apache.logging.log4j.Logger`
 * (Log4j 2), and `java.util.logging.Logger` (JUL).
 *
 * The user-facing logging facade.
 *
 * Modeled directly on `org.slf4j.Logger`: per-level methods (`trace`, `debug`, `info`, `warn`,
 * `error`), parameterized messages with `{}` placeholders, optional `Exception` cause, optional
 * `Marker`, and the SLF4J 2.x fluent event builder via `atInfo()` / `atLevel(...)`.
 *
 * Acquisition is by injection:
 *
 *      @Inject Logger logger;                        // anonymous logger
 *      @Inject("com.example.foo") Logger logger;     // named logger
 *
 * or via the `LoggerFactory` static accessor.
 *
 * Implementations are free, but the canonical implementation in this module is a thin
 * forwarder over a `LogSink` (see `BasicLogger`); user code never sees it directly.
 */
interface Logger {

    /**
     * The logger's name. Sinks use this for routing decisions.
     */
    @RO String name;

    /**
     * Return a logger with the supplied `name` that shares this logger's sink. Mirrors the
     * SLF4J idiom `LoggerFactory.getLogger(MyClass.class)` declared once at the top of a
     * class:
     *
     *      @Inject Logger logger;                                 // root logger, fixed name
     *      static Logger PaymentLogger = logger.named("payments"); // per-class derivative
     *
     * Implementations are free to intern by name (and `BasicLogger` may grow that later);
     * for v0 each call returns a fresh `Logger`.
     */
    Logger named(String name);

    @RO Boolean traceEnabled;
    @RO Boolean debugEnabled;
    @RO Boolean infoEnabled;
    @RO Boolean warnEnabled;
    @RO Boolean errorEnabled;

    /**
     * Cheap level check. Equivalent to the per-level shortcuts above; the explicit form is
     * preferred when the level is determined dynamically.
     */
    Boolean isEnabled(Level level, Marker? marker = Null);

    // ---- Per-level emission -------------------------------------------------------------------

    void trace(String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    void debug(String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    void info (String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    void warn (String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    void error(String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    /**
     * Emit at a runtime-chosen level. Equivalent to `if (level == Info) info(...) else if ...`.
     */
    void log(Level      level,
             String     message,
             Object[]   arguments = [],
             Exception? cause     = Null,
             Marker?    marker    = Null);

    // ---- Fluent (SLF4J 2.x style) builder ------------------------------------------------------

    /**
     * Begin a fluent log event at `Trace` level. Example:
     *
     *      logger.atTrace()
     *            .addMarker(MarkerFactory.getMarker("INTERNAL"))
     *            .addKeyValue("user", userId)
     *            .setCause(e)
     *            .log("processing failed for {}", input);
     */
    LoggingEventBuilder atTrace();
    LoggingEventBuilder atDebug();
    LoggingEventBuilder atInfo();
    LoggingEventBuilder atWarn();
    LoggingEventBuilder atError();
    LoggingEventBuilder atLevel(Level level);
}
