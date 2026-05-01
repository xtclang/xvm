/**
 * Corresponds, role-wise, to a typical SLF4J binding's `Logger` implementation —
 * e.g. `org.slf4j.simple.SimpleLogger` (slf4j-simple), `ch.qos.logback.classic.Logger`
 * (Logback). Like `org.slf4j.helpers.AbstractLogger`, this class collapses many caller-
 * facing overloads down to a single emission path.
 *
 * Default `Logger` implementation: a thin forwarder over a `LogSink`.
 *
 * Holds only the logger's name and a reference to the active sink. All level checks, formatting,
 * MDC capture, and event construction happens here so that sinks themselves remain dumb.
 *
 * `BasicLogger` is a `const`, not a `class` or `service`. Method calls therefore execute on
 * the caller's fiber, which is essential for [MDC]: a service wrapper would create a new
 * fiber per call, and `mdc.copyOfContextMap` invoked from inside that fiber would not see
 * tokens registered by the caller. Being a `const` also lets the runtime hand a
 * `BasicLogger` straight back from `@Inject Logger logger;` — no bridge service is needed.
 *
 * User code does not normally instantiate this directly; it is what the runtime hands you in
 * response to `@Inject Logger logger;` and what `LoggerFactory.getLogger` returns.
 */
const BasicLogger(String name, LogSink sink, LoggerRegistry? registry)
        implements Logger {

    /**
     * Convenience: name only. Wires a fresh [ConsoleLogSink] and no registry. This is
     * the constructor the runtime calls for `@Inject Logger logger;`.
     */
    construct(String name) {
        construct BasicLogger(name, new ConsoleLogSink(), Null);
    }

    /**
     * Convenience: `(name, sink)`. No registry — `named(child)` allocates fresh on each
     * call. Used by tests and ad-hoc construction.
     *
     * The convenience forms are explicit (rather than `registry = Null` defaults on the
     * primary constructor) because cross-module default-argument resolution on `const`
     * constructors does not always synthesise the shorter form: callers from a
     * different module hit "Unresolvable function `construct(String, LogSink)`"
     * otherwise.
     */
    construct(String name, LogSink sink) {
        construct BasicLogger(name, sink, Null);
    }

    @Inject Clock clock;
    @Inject MDC   mdc;

    @Override
    @RO Boolean traceEnabled.get() = sink.isEnabled(name, Trace);
    @Override
    @RO Boolean debugEnabled.get() = sink.isEnabled(name, Debug);
    @Override
    @RO Boolean infoEnabled.get()  = sink.isEnabled(name, Info);
    @Override
    @RO Boolean warnEnabled.get()  = sink.isEnabled(name, Warn);
    @Override
    @RO Boolean errorEnabled.get() = sink.isEnabled(name, Error);

    @Override
    Boolean isEnabled(Level level, Marker? marker = Null) {
        return sink.isEnabled(name, level, marker);
    }

    @Override
    Logger named(String name) {
        // `named` is a *replacement*, not a concatenation: the caller supplies the full
        // logger name they want. This matches how SLF4J users typically write
        // `LoggerFactory.getLogger("com.example.PaymentService")` and the existing
        // `NamedLoggerTest` contract. With a registry attached, repeated calls with the
        // same name return the same instance.
        if (LoggerRegistry r ?= registry) {
            return r.ensure(name);
        }
        return new BasicLogger(name, sink);
    }

    @Override
    void trace(String message, Object[] arguments = [], Exception? cause = Null, Marker? marker = Null) {
        emit(Trace, message, arguments, cause, marker);
    }

    @Override
    void debug(String message, Object[] arguments = [], Exception? cause = Null, Marker? marker = Null) {
        emit(Debug, message, arguments, cause, marker);
    }

    @Override
    void info(String message, Object[] arguments = [], Exception? cause = Null, Marker? marker = Null) {
        emit(Info, message, arguments, cause, marker);
    }

    @Override
    void warn(String message, Object[] arguments = [], Exception? cause = Null, Marker? marker = Null) {
        emit(Warn, message, arguments, cause, marker);
    }

    @Override
    void error(String message, Object[] arguments = [], Exception? cause = Null, Marker? marker = Null) {
        emit(Error, message, arguments, cause, marker);
    }

    @Override
    void log(Level level, String message, Object[] arguments = [], Exception? cause = Null, Marker? marker = Null) {
        emit(level, message, arguments, cause, marker);
    }

    @Override
    LoggingEventBuilder atTrace() = builderFor(Trace);
    @Override
    LoggingEventBuilder atDebug() = builderFor(Debug);
    @Override
    LoggingEventBuilder atInfo()  = builderFor(Info);
    @Override
    LoggingEventBuilder atWarn()  = builderFor(Warn);
    @Override
    LoggingEventBuilder atError() = builderFor(Error);
    @Override
    LoggingEventBuilder atLevel(Level level) = builderFor(level);

    /**
     * Construct an event and hand it to the sink. The fast-path level check is the first thing
     * that happens — when disabled, no formatting work is done.
     *
     * The single-marker entry point used by per-level methods (`info`/`warn`/...) wraps the
     * optional `marker` into a `Marker[]` so the rest of the pipeline only deals with one
     * shape. Multi-marker events come through [BasicEventBuilder], which calls [emitWith]
     * directly with an already-frozen `Marker[]`.
     */
    private void emit(Level level, String message, Object[] arguments, Exception? cause, Marker? marker) {
        Marker[] markers = [];
        if (Marker m ?= marker) {
            // See the freeze rationale on [emitWith].
            markers = [m.freeze()];
        }
        emitWith(level, message, arguments, cause, markers, []);
    }

    /**
     * Internal entry point used by [BasicEventBuilder] to deliver an event that may carry
     * structured key/value pairs and/or multiple markers. Per-level methods on `Logger`
     * funnel through [emit] which wraps a single optional marker into a one-element array
     * before calling here, so this is the one method that sees the full multi-marker shape.
     *
     * Service-boundary semantics: Ecstasy only lets immutable values, `const` values,
     * service references, or already-frozen `Freezable`s cross from one service to
     * another. `BasicMarker` is a mutable class (it accumulates child references via
     * `add`), so handing a caller-supplied marker straight to a sink — when the sink IS
     * a service (e.g. `service MemoryLogSink`) — fails with "mutable object cannot be
     * used as an argument to a service call". The two callers ([emit] above and
     * [BasicEventBuilder.frozenMarkers]) are both responsible for freezing their markers
     * before they reach this method, so we can pass `markers` to the sink as-is. The
     * sink-side `isEnabled` fast path therefore sees frozen markers too.
     */
    void emitWith(Level level, String message, Object[] arguments, Exception? cause,
                  Marker[] markers, Map<String, Object> keyValues) {
        // v0 policy on `arguments`: no defensive copy. Per `open-questions.md` Q11 the
        // caller is contractually required not to mutate the array between the return of
        // `info(...)` and any (possibly async) sink consuming the resulting `LogEvent`.
        // Matches SLF4J's posture; `BasicEventBuilder` already freezes its accumulating
        // `args` before calling here, so the fluent path is safe by construction.
        // Reconsider if/when async sinks become a default.

        // The SPI's level check is single-marker (the first one is treated as "primary"),
        // matching SLF4J 1.x's `Logger.isEnabledFor(Marker)`. Sinks that want richer
        // multi-marker filtering inspect `event.markers` directly inside `log()`.
        Marker? primary = markers.empty ? Null : markers[0];
        if (!sink.isEnabled(name, level, primary)) {
            return;
        }
        (String formatted, Exception? promoted) = MessageFormatter.format(message, arguments);
        Exception? finalCause = cause ?: promoted;
        sink.log(new LogEvent(
                loggerName  = name,
                level       = level,
                message     = formatted,
                timestamp   = clock.now,
                markers     = markers,
                exception   = finalCause,
                arguments   = arguments,
                mdcSnapshot = mdc.copyOfContextMap,
                threadName  = "",            // TODO(runtime): expose current-fiber identity
                keyValues   = keyValues,
        ));
    }

    private LoggingEventBuilder builderFor(Level level) {
        return new BasicEventBuilder(this, level);
    }
}
