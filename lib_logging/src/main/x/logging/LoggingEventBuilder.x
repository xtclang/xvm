/**
 * Corresponds to `org.slf4j.spi.LoggingEventBuilder` (SLF4J 2.x). The default SLF4J impl is
 * `org.slf4j.spi.DefaultLoggingEventBuilder`; we ship `BasicEventBuilder` in the same role.
 *
 * Fluent log-event builder, mirroring `org.slf4j.spi.LoggingEventBuilder` from SLF4J 2.x.
 *
 * The builder accumulates state then materializes a single `LogEvent` on `log(...)`. The
 * level check happens at `log(...)`, after markers have been attached, so marker-aware
 * sinks still get a chance to enable or suppress the event. If the sink rejects the event,
 * no formatting, MDC snapshot, or `LogEvent` allocation happens.
 *
 * This is deliberately a v0 trade-off: the builder stores values passed to
 * `addArgument` / `addKeyValue`, so caller expressions have already been evaluated by
 * the time those methods run. Future lazy overloads can add supplier-valued arguments;
 * see `doc/logging/future/lazy-logging.md`.
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
