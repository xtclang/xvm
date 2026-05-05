/**
 * Corresponds to `org.slf4j.spi.DefaultLoggingEventBuilder` — the SLF4J 2.x default
 * implementation of the fluent builder. Accumulates state, then materializes a single
 * call to the underlying logger.
 *
 * Default `LoggingEventBuilder` implementation. Accumulates state then forwards to the
 * underlying `Logger`'s `log(...)` method.
 *
 * # Lazy values
 *
 * Both positional `{}` arguments and structured key/value pairs can be supplied either
 * eagerly (the value is already computed) or lazily (a function that runs only after the
 * level/marker check accepts the event). Lazy entries are stored as a [LazyValue]
 * wrapper inside the same `args` / `keyValues` collections — there are no parallel
 * boolean flag arrays to keep in sync. The resolver at `frozenArgs()` / `frozenKVs()`
 * does `value.is(LazyValue) ? lv.resolve() : value`. This is also why
 * `addLazyArgument` / `addLazyKeyValue` are explicit method names rather than overloads
 * of `addArgument` / `addKeyValue`: in Ecstasy a `function Object()` is itself an
 * `Object`, so an overload that accepted a supplier could not be reliably distinguished
 * from one that accepted the supplier as a literal value.
 */
class BasicEventBuilder(BasicLogger logger, Level level)
        implements LoggingEventBuilder {

    private String?             message     = Null;
    private MessageSupplier?    lazyMessage = Null;
    private Object[]            args        = new Object[];
    private Marker[]            markers     = new Marker[];
    private Exception?          cause       = Null;
    private Map<String, Object> keyValues   = new ListMap();

    /**
     * Replace the pending message pattern. Mirrors SLF4J
     * `LoggingEventBuilder.setMessage`.
     */
    @Override
    LoggingEventBuilder setMessage(String message) {
        this.message = message;
        this.lazyMessage = Null;
        return this;
    }

    /**
     * Replace the pending message with a lazily computed message.
     */
    @Override
    LoggingEventBuilder setMessage(MessageSupplier message) {
        this.message = Null;
        this.lazyMessage = message;
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
     * Append one lazily computed positional `{}` argument. The supplier runs only after
     * the sink accepts the event; see the class doc comment for why this is a separate
     * method rather than an overload of `addArgument`.
     */
    @Override
    LoggingEventBuilder addLazyArgument(ObjectSupplier value) {
        args.add(new LazyValue(value));
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
     * Attach one lazily computed structured key/value pair. Stored as a [LazyValue]
     * inside the same `keyValues` map; resolved at `frozenKVs()` once the event is
     * confirmed enabled.
     */
    @Override
    LoggingEventBuilder addLazyKeyValue(String key, ObjectSupplier value) {
        keyValues.put(key, new LazyValue(value));
        return this;
    }

    /**
     * Emit using the message set by `setMessage`, if one was supplied.
     */
    @Override
    void log() {
        if (String m ?= message) {
            emit(m);
        } else if (MessageSupplier supplier ?= lazyMessage) {
            emit(supplier);
        }
    }

    /**
     * Emit using `message` as the final message pattern.
     */
    @Override
    void log(String message) {
        emit(message);
    }

    /**
     * Emit using a lazily computed final message pattern.
     */
    @Override
    void log(MessageSupplier message) {
        emit(message);
    }

    /**
     * Convenience: append one argument and emit.
     */
    @Override
    void log(String format, Object arg) {
        addArgument(arg);
        emit(format);
    }

    /**
     * Convenience: append two arguments and emit.
     */
    @Override
    void log(String format, Object arg1, Object arg2) {
        addArgument(arg1);
        addArgument(arg2);
        emit(format);
    }

    /**
     * Convenience: append all supplied arguments and emit.
     */
    @Override
    void log(String format, Object[] args) {
        for (Object arg : args) {
            addArgument(arg);
        }
        emit(format);
    }

    /**
     * Emit an eager message after the level/marker check. Lazy args and key/value suppliers are
     * intentionally resolved only after this check succeeds.
     */
    private void emit(String message) {
        Marker[] frozenMarkers = frozenMarkers();
        if (enabled(frozenMarkers)) {
            logger.emitWith(level, message, frozenArgs(), cause, frozenMarkers, frozenKVs());
        }
    }

    /**
     * Emit a lazy message after the level/marker check. This is the builder equivalent of
     * SLF4J 2.x `log(Supplier<String>)` and Kotlin logging's lambda block.
     */
    private void emit(MessageSupplier message) {
        Marker[] frozenMarkers = frozenMarkers();
        if (enabled(frozenMarkers)) {
            logger.emitWith(level, message(), frozenArgs(), cause, frozenMarkers, frozenKVs());
        }
    }

    /**
     * Check the sink using the first marker as the primary marker, matching [BasicLogger.emitWith].
     */
    private Boolean enabled(Marker[] markers) {
        Marker? primary = markers.empty ? Null : markers[0];
        return logger.isEnabled(level, primary);
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
     * so the resulting `LogEvent` can cross the sink boundary. Lazy entries (stored as
     * [LazyValue] wrappers) are resolved here, after the level/marker check has confirmed
     * the event will actually be emitted. SLF4J's reference impl realises a `KeyValuePair`
     * list at the same point; here it's a `Map<String, Object>` on `LogEvent.keyValues`.
     */
    private Map<String, Object> frozenKVs() {
        if (keyValues.empty) {
            return [];
        }
        ListMap<String, Object> snapshot = new ListMap();
        for ((String k, Object v) : keyValues) {
            snapshot.put(k, resolve(v));
        }
        return snapshot.makeImmutable();
    }

    /**
     * Convert the mutable accumulating buffer to a constant-mode (immutable) array so
     * it can cross service boundaries on its way to the sink. Lazy entries are resolved
     * here for the same reason as in [frozenKVs]. Equivalent to SLF4J's `EventArgArray`
     * materialisation step.
     */
    private Object[] frozenArgs() {
        if (args.empty) {
            return [];
        }

        Object[] snapshot = new Array<Object>(args.size);
        for (Object value : args) {
            snapshot.add(resolve(value));
        }
        return snapshot.toArray(Constant);
    }

    /**
     * Unwrap a [LazyValue] if present; pass plain values through unchanged. Centralises
     * the discrimination so both `frozenArgs` and `frozenKVs` share one rule.
     */
    private static Object resolve(Object value) {
        if (LazyValue lazy := value.is(LazyValue)) {
            return lazy.resolve();
        }
        return value;
    }
}
