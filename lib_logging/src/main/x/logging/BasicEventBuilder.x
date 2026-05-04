/**
 * Corresponds to `org.slf4j.spi.DefaultLoggingEventBuilder` — the SLF4J 2.x default
 * implementation of the fluent builder. Accumulates state, then materializes a single
 * call to the underlying logger.
 *
 * Default `LoggingEventBuilder` implementation. Accumulates state then forwards to the
 * underlying `Logger`'s `log(...)` method.
 */
class BasicEventBuilder(BasicLogger logger, Level level)
        implements LoggingEventBuilder {

    private String?             message   = Null;
    private Object[]            args      = new Object[];
    private Marker[]            markers   = new Marker[];
    private Exception?          cause     = Null;
    private Map<String, Object> keyValues = new ListMap();

    /**
     * Replace the pending message pattern. Mirrors SLF4J
     * `LoggingEventBuilder.setMessage`.
     */
    @Override
    LoggingEventBuilder setMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * Append one positional `{}` argument.
     */
    @Override
    LoggingEventBuilder addArgument(Object value) {
        args.add(value);
        return this;
    }

    /**
     * Attach one marker. Repeated calls produce a multi-marker event.
     */
    @Override
    LoggingEventBuilder addMarker(Marker marker) {
        markers.add(marker);
        return this;
    }

    /**
     * Attach an explicit cause. This wins over SLF4J throwable promotion from
     * `arguments`.
     */
    @Override
    LoggingEventBuilder setCause(Exception cause) {
        this.cause = cause;
        return this;
    }

    /**
     * Attach one structured key/value pair for structured sinks. Duplicate keys use
     * last-value-wins semantics in the current `Map` representation.
     */
    @Override
    LoggingEventBuilder addKeyValue(String key, Object value) {
        keyValues.put(key, value);
        return this;
    }

    /**
     * Emit using the message set by `setMessage`, if one was supplied.
     */
    @Override
    void log() {
        if (String m ?= message) {
            logger.emitWith(level, m, frozen(args), cause, frozenMarkers(), frozenKVs());
        }
    }

    /**
     * Emit using `message` as the final message pattern.
     */
    @Override
    void log(String message) {
        logger.emitWith(level, message, frozen(args), cause, frozenMarkers(), frozenKVs());
    }

    /**
     * Convenience: append one argument and emit.
     */
    @Override
    void log(String format, Object arg) {
        args.add(arg);
        logger.emitWith(level, format, frozen(args), cause, frozenMarkers(), frozenKVs());
    }

    /**
     * Convenience: append two arguments and emit.
     */
    @Override
    void log(String format, Object arg1, Object arg2) {
        args.add(arg1);
        args.add(arg2);
        logger.emitWith(level, format, frozen(args), cause, frozenMarkers(), frozenKVs());
    }

    /**
     * Convenience: append all supplied arguments and emit.
     */
    @Override
    void log(String format, Object[] args) {
        for (Object arg : args) {
            this.args.add(arg);
        }
        logger.emitWith(level, format, frozen(this.args), cause, frozenMarkers(), frozenKVs());
    }

    /**
     * Materialise the accumulated markers as an immutable, freezing each marker so it can
     * cross the sink service boundary. Empty case returns the canonical empty constant.
     */
    private Marker[] frozenMarkers() {
        if (markers.empty) {
            return [];
        }
        Marker[] frozen = new Array<Marker>(markers.size);
        for (Marker m : markers) {
            frozen.add(m.freeze());
        }
        return frozen.toArray(Constant);
    }

    /**
     * Materialise the accumulated structured key/value map as a const-mode (immutable) map
     * so the resulting `LogEvent` can cross the sink boundary. SLF4J's reference impl
     * realises a `KeyValuePair` list at the same point; here it's a `Map<String, Object>` on
     * `LogEvent.keyValues`.
     */
    private Map<String, Object> frozenKVs() {
        if (keyValues.empty) {
            return [];
        }
        ListMap<String, Object> snapshot = new ListMap();
        for ((String k, Object v) : keyValues) {
            snapshot.put(k, v);
        }
        return snapshot.makeImmutable();
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
