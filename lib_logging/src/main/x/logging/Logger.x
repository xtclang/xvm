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
 *      @Inject Logger logger;                        // root logger
 *      Logger log = logger.named("com.example.foo"); // named logger
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
     * `name` is the full logger name, not a suffix appended to this logger's name.
     * Implementations may intern by name; `BasicLogger` does so when it was created by a
     * `LoggerRegistry` and otherwise returns a fresh logger.
     */
    Logger named(String name);

    /**
     * Cheap `Trace` level check.
     */
    @RO Boolean traceEnabled;
    /**
     * Cheap `Debug` level check. Use this before constructing expensive debug
     * arguments; `{}` formatting is lazy, but argument expressions are still evaluated
     * by the caller before the method call.
     */
    @RO Boolean debugEnabled;
    /**
     * Cheap `Info` level check.
     */
    @RO Boolean infoEnabled;
    /**
     * Cheap `Warn` level check.
     */
    @RO Boolean warnEnabled;
    /**
     * Cheap `Error` level check.
     */
    @RO Boolean errorEnabled;

    /**
     * Cheap level check. Equivalent to the per-level shortcuts above; the explicit form is
     * preferred when the level is determined dynamically.
     */
    Boolean isEnabled(Level level, Marker? marker = Null);

    // ---- Per-level emission -------------------------------------------------------------------

    /**
     * Emit a `Trace` event using SLF4J `{}` placeholder formatting.
     */
    void trace(String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    /**
     * Emit a `Debug` event using SLF4J `{}` placeholder formatting.
     */
    void debug(String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    /**
     * Emit an `Info` event using SLF4J `{}` placeholder formatting.
     */
    void info (String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    /**
     * Emit a `Warn` event using SLF4J `{}` placeholder formatting.
     */
    void warn (String message,
               Object[]   arguments = [],
               Exception? cause     = Null,
               Marker?    marker    = Null);

    /**
     * Emit an `Error` event using SLF4J `{}` placeholder formatting.
     */
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

    /**
     * Emit at a runtime-chosen level with explicit source metadata.
     *
     * This mirrors the source-aware entry point on the slog-shaped library and gives a
     * future compiler/runtime call-site capture feature a stable lowering target. Normal
     * application code should keep using [log] / [info] / [atInfo] until such sugar exists.
     */
    void logAt(Level      level,
               String     message,
               String     sourceFile,
               Int        sourceLine,
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
    /**
     * Begin a fluent log event at `Debug` level.
     */
    LoggingEventBuilder atDebug();
    /**
     * Begin a fluent log event at `Info` level.
     */
    LoggingEventBuilder atInfo();
    /**
     * Begin a fluent log event at `Warn` level.
     */
    LoggingEventBuilder atWarn();
    /**
     * Begin a fluent log event at `Error` level.
     */
    LoggingEventBuilder atError();
    /**
     * Begin a fluent log event at a runtime-selected level.
     */
    LoggingEventBuilder atLevel(Level level);
}
