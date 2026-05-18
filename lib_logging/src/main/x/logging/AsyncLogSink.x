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
     *
     * Two safety properties live here, both worth understanding before changing this code.
     *
     * 1. **`draining` must be reset on every exit, including exceptions.**
     *    The whole point of this wrapper is to insulate callers from a misbehaving
     *    delegate. If a delegate `log(event)` throws, we still need `draining = False`
     *    afterwards — otherwise `log()` will keep enqueueing events but never schedule
     *    another drain (because it sees `draining == True`), the queue grows to
     *    `capacity`, and from then on every event is silently dropped via
     *    `droppedCount`. The `try/finally` guarantees the flag is cleared.
     *
     * 2. **Per-event failure is contained.**
     *    `LogSink.log` is contractually non-throwing (see `LogSink.x`), but a buggy
     *    custom sink can still throw. We catch per event so that one bad event
     *    increments `droppedCount` and the rest of the batch still drains. The
     *    alternative — letting the exception escape and rely on (1) to recover — would
     *    drop every queued event behind the throwing one. That's a bigger surprise to
     *    operators than a single counter tick.
     *
     * The loop uses a swap-out batch instead of `queue.delete(0)` per event. `delete(0)`
     * is O(n) on `Array`, so the original loop was O(n²) per drain. The swap takes the
     * mutable buffer in one step, then iterates it; new events arriving during the inner
     * loop land in a fresh `queue` and are picked up by the outer `while`. Service-fiber
     * serialisation means no other `log()` call can run between our read of `queue` and
     * our reassignment, so no event is lost.
     */
    private void drain() {
        try {
            while (!queue.empty) {
                LogEvent[] batch = queue;
                queue = new LogEvent[];
                for (LogEvent event : batch) {
                    try {
                        delegate.log(event);
                    } catch (Exception e) {
                        ++droppedCount;
                    }
                }
            }
        } finally {
            draining = False;
        }
    }
}
