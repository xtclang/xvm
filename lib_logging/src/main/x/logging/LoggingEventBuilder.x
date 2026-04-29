/**
 * Corresponds to `org.slf4j.spi.LoggingEventBuilder` (SLF4J 2.x). The default SLF4J impl is
 * `org.slf4j.spi.DefaultLoggingEventBuilder`; we ship `BasicEventBuilder` in the same role.
 *
 * Fluent log-event builder, mirroring `org.slf4j.spi.LoggingEventBuilder` from SLF4J 2.x.
 *
 * The builder accumulates state then materializes a single `LogEvent` on `log(...)`. When the
 * underlying logger is _not_ enabled at the chosen level, the builder is a no-op and all
 * accumulated state is discarded — this is the whole point of the design: callers don't pay
 * for arguments and cause-chains that are never going to be emitted.
 *
 * Example:
 *
 *      logger.atInfo()
 *            .addMarker(MarkerFactory.getMarker("AUDIT"))
 *            .addKeyValue("requestId", req.id)
 *            .setCause(e)
 *            .log("payment {} succeeded for {}", paymentId, customer);
 */
interface LoggingEventBuilder {

    /**
     * Set (or replace) the message pattern. Equivalent to passing the message to `log(...)`.
     */
    LoggingEventBuilder setMessage(String message);

    /**
     * Append an argument used to substitute the next `{}` placeholder in the message.
     */
    LoggingEventBuilder addArgument(Object value);

    /**
     * Attach a marker. Repeated calls add multiple markers.
     */
    LoggingEventBuilder addMarker(Marker marker);

    /**
     * Attach an exception to the event.
     */
    LoggingEventBuilder setCause(Exception cause);

    /**
     * Attach a key/value pair, intended for structured-logging sinks. Sinks that don't
     * understand structured KV pairs (e.g. `ConsoleLogSink`) may render them as `key=value`
     * after the message or simply ignore them.
     */
    LoggingEventBuilder addKeyValue(String key, Object value);

    /**
     * Materialize and emit. After `log` is called the builder must not be reused.
     */
    void log();

    /**
     * Convenience: same as `setMessage(message).log()`.
     */
    void log(String message);

    /**
     * Convenience: same as `setMessage(format).addArgument(arg).log()`.
     */
    void log(String format, Object arg);

    /**
     * Convenience: same as `setMessage(format).addArgument(arg1).addArgument(arg2).log()`.
     */
    void log(String format, Object arg1, Object arg2);

    /**
     * Convenience: append all `args` then `log()`.
     */
    void log(String format, Object[] args);
}
