/**
 * Corresponds to `org.slf4j.spi.DefaultLoggingEventBuilder` — the SLF4J 2.x default
 * implementation of the fluent builder. Accumulates state, then materializes a single
 * call to the underlying logger.
 *
 * Default `LoggingEventBuilder` implementation. Accumulates state then forwards to the
 * underlying `Logger`'s `log(...)` method.
 */
class BasicEventBuilder(Logger logger, Level level)
        implements LoggingEventBuilder {

    private String?    message  = Null;
    private Object[]   args     = new Object[];
    private Marker?    marker   = Null;
    private Exception? cause    = Null;

    @Override
    LoggingEventBuilder setMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    LoggingEventBuilder addArgument(Object value) {
        args.add(value);
        return this;
    }

    @Override
    LoggingEventBuilder addMarker(Marker marker) {
        // TODO(api): SLF4J supports multiple markers per event. For now we keep the most
        //            recently added one; document this until the data model is decided.
        this.marker = marker;
        return this;
    }

    @Override
    LoggingEventBuilder setCause(Exception cause) {
        this.cause = cause;
        return this;
    }

    @Override
    LoggingEventBuilder addKeyValue(String key, Object value) {
        // TODO(api): structured KV requires a per-event map; sinks ignoring KV must still
        //            accept the call. Wire through to LogEvent in a future iteration.
        return this;
    }

    @Override
    void log() {
        if (String m ?= message) {
            logger.log(level, m, frozen(args), cause, marker);
        }
    }

    @Override
    void log(String message) {
        logger.log(level, message, frozen(args), cause, marker);
    }

    @Override
    void log(String format, Object arg) {
        args.add(arg);
        logger.log(level, format, frozen(args), cause, marker);
    }

    @Override
    void log(String format, Object arg1, Object arg2) {
        args.add(arg1);
        args.add(arg2);
        logger.log(level, format, frozen(args), cause, marker);
    }

    @Override
    void log(String format, Object[] args) {
        for (Object arg : args) {
            this.args.add(arg);
        }
        logger.log(level, format, frozen(this.args), cause, marker);
    }

    /**
     * Convert the mutable accumulating buffer to a constant-mode (immutable) array so
     * it can cross service boundaries on its way to the sink. Equivalent to SLF4J's
     * `EventArgArray` materialisation step.
     */
    private static Object[] frozen(Object[] mutable) {
        return mutable.toArray(Constant);
    }
}
