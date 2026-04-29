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
 * User code does not normally instantiate this directly; it is what the runtime hands you in
 * response to `@Inject Logger logger;` and what `LoggerFactory.getLogger` returns.
 */
class BasicLogger(String name, LogSink sink)
        implements Logger {

    @Inject Clock clock;
    // MDC is intentionally not injected here in v0: the runtime side does not yet
    // register the MDC resource, and tests construct loggers directly. Once
    // `nMainInjector` registers MDC, replace the empty-map snapshot below with
    // `mdc.copyOfContextMap`. See doc/logging/OPEN_QUESTIONS.md (entry 3).

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
     */
    private void emit(Level level, String message, Object[] arguments, Exception? cause, Marker? marker) {
        if (!sink.isEnabled(name, level, marker)) {
            return;
        }
        (String formatted, Exception? promoted) = MessageFormatter.format(message, arguments);
        Exception? finalCause = cause ?: promoted;
        sink.log(new LogEvent(
                loggerName  = name,
                level       = level,
                message     = formatted,
                timestamp   = clock.now,
                marker      = marker,
                exception   = finalCause,
                arguments   = arguments,
                mdcSnapshot = new HashMap<String, String>(),
                threadName  = "",            // TODO(runtime): expose current-fiber identity
        ));
    }

    private LoggingEventBuilder builderFor(Level level) {
        return new BasicEventBuilder(this, level);
    }
}
