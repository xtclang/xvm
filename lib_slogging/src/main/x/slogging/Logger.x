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
 *   - derivation via [with] / [withGroup] is just handler construction, no mutation;
 *   - it is `Passable`, so it can flow across fibers (fields of services, parameters
 *     of service calls).
 *
 * # The user-facing API
 *
 *      logger.debug("computed", [Attr.of("ms", 12)]);
 *      logger.debug(() -> expensiveMessage(), [Attr.lazy("payload", () -> payloadJson)]);
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
 * # POC status
 *
 * The `log` method is implemented (level-check fast path, lazy supplier resolution,
 * record construction, forward to handler), and [logAt] provides explicit source metadata
 * for callers or future compiler/runtime sugar. Runtime injection for
 * `@Inject slogging.Logger logger;` is registered by `NativeContainer`. There is no
 * `LogAttrs(ctx, level, msg, attrs...)` form; [LoggerContext] is the Ecstasy-shaped
 * optional context helper.
 */
const Logger(Handler handler)
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
        construct Logger(new TextHandler());
    }

    /**
     * Compatibility convenience: handler plus pre-bound attrs. The attrs are handed to
     * [Handler.withAttrs] immediately, which is the slog contract and what allows a
     * handler to pre-render or cache its own derived state.
     */
    construct(Handler handler, Attr[] attrs) {
        construct Logger(attrs.empty ? handler : handler.withAttrs(attrs));
    }

    /**
     * Cheap `Debug` enabled check. Mirrors Go's `Logger.Enabled(ctx, slog.LevelDebug)`
     * and the SLF4J-shaped library's `debugEnabled` property.
     */
    @RO Boolean debugEnabled.get() = handler.enabled(Level.Debug);

    /**
     * Cheap `Info` enabled check. Use for multi-statement guarded work; for one-line
     * expensive values, prefer `logger.info(() -> "...", [Attr.lazy("k", () -> v)])`.
     */
    @RO Boolean infoEnabled.get()  = handler.enabled(Level.Info);

    /**
     * Cheap `Warn` enabled check. Delegates directly to the active handler.
     */
    @RO Boolean warnEnabled.get()  = handler.enabled(Level.Warn);

    /**
     * Cheap `Error` enabled check. Delegates directly to the active handler.
     */
    @RO Boolean errorEnabled.get() = handler.enabled(Level.Error);

    /**
     * Runtime-level enabled check for custom levels. Equivalent to Go
     * `Logger.Enabled(ctx, level)`, minus the `context.Context` parameter.
     */
    Boolean enabled(Level level) = handler.enabled(level);

    /**
     * Emit a `Debug` record. The message is already a complete human-readable string;
     * structured values belong in `extra` attrs rather than `{}` placeholders. See
     * `doc/logging/usage/slog-parity.md` § "Message formatting".
     */
    void debug(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Debug, message, extra, cause);

    /**
     * Emit a `Debug` record whose message is computed after the handler's enabled check.
     */
    void debug(MessageSupplier message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Debug, message, extra, cause);

    /**
     * Emit an `Info` record.
     */
    void info(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Info, message, extra, cause);

    /**
     * Emit an `Info` record whose message is computed after the handler's enabled check.
     */
    void info(MessageSupplier message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Info, message, extra, cause);

    /**
     * Emit a `Warn` record.
     */
    void warn(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Warn, message, extra, cause);

    /**
     * Emit a `Warn` record whose message is computed after the handler's enabled check.
     */
    void warn(MessageSupplier message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Warn, message, extra, cause);

    /**
     * Emit an `Error` record. `cause` is an Ecstasy convenience; the Go slog idiom is
     * usually `slog.Any("err", err)`. Either form can be rendered by a handler.
     */
    void error(String message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Error, message, extra, cause);

    /**
     * Emit an `Error` record whose message is computed after the handler's enabled check.
     */
    void error(MessageSupplier message, Attr[] extra = [], Exception? cause = Null) =
            log(Level.Error, message, extra, cause);

    /**
     * Open-level emission. Use for custom levels.
     *
     * The enabled check is first. If the handler rejects `level`, no record is
     * constructed and no attrs are merged. This mirrors Go slog's
     * `Handler.Enabled` fast path.
     */
    void log(Level level, String message, Attr[] extra = [], Exception? cause = Null) {
        emit(level, message, extra, cause, Null, -1);
    }

    /**
     * Open-level emission with lazy message construction.
     */
    void log(Level level, MessageSupplier message, Attr[] extra = [], Exception? cause = Null) {
        emit(level, message, extra, cause, Null, -1);
    }

    /**
     * Open-level emission with explicit source metadata.
     *
     * Go `slog` can populate source information when configured with `AddSource`. This
     * POC cannot ask the compiler/runtime for the current call site yet, so `logAt`
     * provides a precise and testable explicit API. A later compiler helper can lower
     * caller-site sugar into this method without changing the handler contract.
     */
    void logAt(Level level, String message, String sourceFile, Int sourceLine,
               Attr[] extra = [], Exception? cause = Null) {
        emit(level, message, extra, cause, sourceFile, sourceLine);
    }

    /**
     * Open-level emission with explicit source metadata and lazy message construction.
     */
    void logAt(Level level, MessageSupplier message, String sourceFile, Int sourceLine,
               Attr[] extra = [], Exception? cause = Null) {
        emit(level, message, extra, cause, sourceFile, sourceLine);
    }

    /**
     * Common emission path used by [log] and [logAt]. The handler owns any attributes
     * attached by derived loggers; the record receives only call-time extras.
     */
    private void emit(Level level, String message, Attr[] extra, Exception? cause,
                      String? sourceFile, Int sourceLine) {
        if (!handler.enabled(level)) {
            return;
        }
        handler.handle(new Record(
                time       = clock.now,
                message    = message,
                level      = level,
                attrs      = Attr.resolveAll(extra),
                exception  = cause,
                sourceFile = sourceFile,
                sourceLine = sourceLine,
        ));
    }

    /**
     * Lazy-message equivalent of [emit]. Both message and lazy attrs resolve only after
     * the handler accepts the level, matching Go slog's `Enabled` fast path and
     * `LogValuer`-style value resolution.
     */
    private void emit(Level level, MessageSupplier message, Attr[] extra, Exception? cause,
                      String? sourceFile, Int sourceLine) {
        if (!handler.enabled(level)) {
            return;
        }
        handler.handle(new Record(
                time       = clock.now,
                message    = message(),
                level      = level,
                attrs      = Attr.resolveAll(extra),
                exception  = cause,
                sourceFile = sourceFile,
                sourceLine = sourceLine,
        ));
    }

    /**
     * Return a derived logger that carries the supplied attributes. Equivalent to
     * `slog.Logger.With(...)`.
     *
     * The attrs are not stored in `Logger`; they live in the derived [Handler]. That is
     * the key slog design point: a handler can pre-resolve attrs once at derivation time
     * instead of merging or rendering them on every emission.
     *
     * This is the slog replacement for SLF4J MDC in the core API: instead of hidden
     * fiber-local state, code passes around a logger that visibly carries request or
     * component attrs. See `doc/logging/usage/slog-parity.md`.
     */
    Logger with(Attr[] more) {
        if (more.empty) {
            return this;
        }
        return new Logger(handler.withAttrs(more));
    }

    /**
     * Return a derived logger whose subsequent attributes are namespaced under
     * `groupName`. Equivalent to `slog.Logger.WithGroup(...)`.
     *
     * The grouping state lives in the derived handler. Text handlers dot-flatten
     * (`payments.amount`), while JSON handlers nest (`{"payments":{"amount":...}}`).
     */
    Logger withGroup(String groupName) {
        if (groupName == "") {
            return this;
        }
        return new Logger(handler.withGroup(groupName));
    }
}
