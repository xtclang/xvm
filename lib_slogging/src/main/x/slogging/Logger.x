/**
 * Corresponds to `log/slog.Logger` (`go.dev/src/log/slog/logger.go`). The user-facing
 * concrete logger.
 *
 * Unlike the SLF4J-shaped sibling library — where [logging.Logger] is an interface and
 * [logging.BasicLogger] is its `const` implementation — `Logger` here is concrete from
 * the start. Polymorphism lives at the [Handler] boundary; the `Logger` itself is a
 * thin combinator that builds a [Record] and forwards it.
 *
 * `Logger` is a `const` so:
 *   - methods run on the caller's fiber (no service-boundary hop on the hot path);
 *   - derivation via [with] / [withGroup] is just construction, no mutation;
 *   - it is `Passable`, so it can flow across fibers (fields of services, parameters
 *     of service calls).
 *
 * # The user-facing API
 *
 *      logger.debug("computed", [Attr.of("ms", 12)]);
 *      logger.info("processing", [Attr.of("path", req.path)]);
 *      logger.warn("retrying", [Attr.of("attempt", 3)]);
 *      logger.error("failed", [Attr.of("err", e)]);
 *      logger.log(NOTICE, "user signed in", [...]);
 *
 *      Logger reqLog = logger.with([
 *              Attr.of("requestId", req.id),
 *              Attr.of("user",      req.userId),
 *      ]);
 *
 *      Logger groupLog = logger.withGroup("payments");
 *      groupLog.info("charged", [Attr.of("amount", 1099)]);
 *      // text handler: 2026-04-29 INFO charged payments.amount=1099
 *      // json handler: {"level":"INFO","msg":"charged","payments":{"amount":1099}}
 *
 * # Skeleton status
 *
 * The `log` method is implemented (level-check fast path, attr concatenation, record
 * construction, forward to handler). Source-location capture is not. There is no
 * `LogAttrs(ctx, level, msg, attrs...)` form yet — the per-level methods cover the same
 * ground in v0; `ctx`-style propagation is the open Q-D6.b discussion in
 * `OPEN_QUESTIONS.md`.
 */
const Logger(Handler handler, Attr[] attrs)
        implements Orderable {

    @Inject Clock clock;

    /**
     * Convenience: no-arg, wires a fresh [TextHandler] and empty attrs. This is the
     * constructor the runtime would call for `@Inject Logger logger;` once the native
     * resource supplier is registered (parallel to the SLF4J library's
     * `NativeContainer.ensureLogger`).
     *
     * Explicit delegating constructors (rather than `attrs = []` default on the
     * primary form) for the same reason as the SLF4J side: cross-module
     * default-argument resolution on `const` constructors does not always synthesise
     * the shorter form. See `lib_logging.BasicLogger`.
     */
    construct() {
        construct Logger(new TextHandler(), []);
    }

    /**
     * Convenience: handler only, empty attrs.
     */
    construct(Handler handler) {
        construct Logger(handler, []);
    }

    @RO Boolean debugEnabled.get() = handler.enabled(Level.Debug);
    @RO Boolean infoEnabled.get()  = handler.enabled(Level.Info);
    @RO Boolean warnEnabled.get()  = handler.enabled(Level.Warn);
    @RO Boolean errorEnabled.get() = handler.enabled(Level.Error);

    Boolean enabled(Level level) = handler.enabled(level);

    void debug(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Debug, message, extra, cause);

    void info(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Info, message, extra, cause);

    void warn(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Warn, message, extra, cause);

    void error(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Error, message, extra, cause);

    /**
     * Open-level emission. Use for custom levels.
     */
    void log(Level level, String message, Attr[] extra = [], Exception? cause = Null) {
        if (!handler.enabled(level)) {
            return;
        }
        Attr[] merged = extra.empty ? attrs : (attrs + extra).toArray(Constant);
        handler.handle(new Record(
                time      = clock.now,
                message   = message,
                level     = level,
                attrs     = merged,
                exception = cause,
        ));
    }

    /**
     * Return a derived logger that carries the supplied attributes plus this one's.
     * Equivalent to `slog.Logger.With(...)`. The handler is also asked to derive
     * (`handler.withAttrs(more)`) so structured handlers can pre-resolve.
     */
    Logger with(Attr[] more) {
        if (more.empty) {
            return this;
        }
        Attr[] combined = attrs.empty ? more.toArray(Constant) : (attrs + more).toArray(Constant);
        return new Logger(handler.withAttrs(more), combined);
    }

    /**
     * Return a derived logger whose subsequent attributes are namespaced under
     * `groupName`. Equivalent to `slog.Logger.WithGroup(...)`.
     */
    Logger withGroup(String groupName) {
        return new Logger(handler.withGroup(groupName), attrs);
    }
}
