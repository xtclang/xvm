/**
 * Logback-style async wrapper for any [LogSink].
 *
 * The caller pays only the normal [isEnabled] check and the enqueue operation. A
 * background fiber drains the queue and invokes the delegate sink. The [LogEvent] has
 * already captured MDC and source metadata before it reaches this sink, so delayed
 * emission preserves the caller's context.
 *
 * This is the POC equivalent of Logback's `AsyncAppender`:
 *
 *      <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
 *        <queueSize>8192</queueSize>
 *        <appender-ref ref="JSON"/>
 *      </appender>
 *
 * Ecstasy equivalent:
 *
 *      LogSink sink = new AsyncLogSink(new JsonLogSink(), capacity=8192);
 */
service AsyncLogSink(LogSink delegate, Int capacity) implements LogSink {

    /**
     * Convenience: bounded queue with room for 1024 events.
     */
    construct(LogSink delegate) {
        construct AsyncLogSink(delegate, 1024);
    }

    /**
     * Events waiting for the background drain fiber.
     */
    private LogEvent[] queue = new LogEvent[];

    /**
     * True while a drain fiber has been scheduled or is running.
     */
    private Boolean draining = False;

    /**
     * True after [close] has been called.
     */
    private Boolean closed = False;

    /**
     * Count of events dropped because the queue was full or the sink was closed.
     */
    public/private Int droppedCount = 0;

    /**
     * Current queue depth. Intended for tests and operational probes.
     */
    @RO Int pending.get() = queue.size;

    @Override
    Boolean isEnabled(String loggerName, Level level, Marker? marker = Null) {
        return delegate.isEnabled(loggerName, level, marker);
    }

    @Override
    void log(LogEvent event) {
        if (closed || queue.size >= capacity) {
            ++droppedCount;
            return;
        }

        queue.add(event);
        if (!draining) {
            draining = True;
            drain^();
        }
    }

    /**
     * Synchronously drain all currently queued events.
     */
    void flush() {
        drain();
    }

    /**
     * Stop accepting new events. By default this drains pending events first.
     */
    void close(Boolean flush = True) {
        closed = True;
        if (flush) {
            drain();
        } else {
            droppedCount += queue.size;
            queue.clear();
            draining = False;
        }
    }

    /**
     * Drain the queue on this service's fiber.
     */
    private void drain() {
        while (!queue.empty) {
            LogEvent event = queue[0];
            queue.delete(0);
            delegate.log(event);
        }
        draining = False;
    }
}
