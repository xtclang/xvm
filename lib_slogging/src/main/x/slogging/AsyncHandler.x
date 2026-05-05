/**
 * Async wrapper for a slog [Handler].
 *
 * Records are fully constructed before they enter this handler, so delayed emission
 * preserves attrs, groups, source metadata, and exceptions exactly as the caller
 * produced them.
 */
service AsyncHandler(Handler delegate, Int capacity)
        implements Handler {

    /**
     * Convenience: bounded queue with room for 1024 records.
     */
    construct(Handler delegate) {
        construct AsyncHandler(delegate, 1024);
    }

    private Record[] queue = new Record[];
    private Boolean  draining = False;
    private Boolean  closed   = False;

    /**
     * Count of records dropped because the queue was full or closed.
     */
    public/private Int droppedCount = 0;

    /**
     * Current queue depth. Intended for tests and operational probes.
     */
    @RO Int pending.get() = queue.size;

    @Override
    Boolean enabled(Level level) {
        return delegate.enabled(level);
    }

    @Override
    void handle(Record record) {
        if (closed || queue.size >= capacity) {
            ++droppedCount;
            return;
        }

        queue.add(record);
        if (!draining) {
            draining = True;
            drain^();
        }
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        return attrs.empty ? this : new AsyncHandler(delegate.withAttrs(attrs), capacity);
    }

    @Override
    Handler withGroup(String name) {
        return name == "" ? this : new AsyncHandler(delegate.withGroup(name), capacity);
    }

    /**
     * Synchronously drain currently queued records.
     */
    void flush() {
        drain();
    }

    /**
     * Stop accepting new records. By default this drains pending records first.
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
     * The same two safety properties as `lib_logging.AsyncLogSink.drain` apply here —
     * see that file's `drain` doc-comment for the full rationale. Briefly:
     *
     * 1. `draining` must be cleared on every exit path (`try/finally`) so a delegate
     *    exception cannot strand the wrapper in the "drain already scheduled" state,
     *    after which `handle()` would silently drop everything via `droppedCount`.
     * 2. Per-record failure is contained so one bad record does not prevent the rest
     *    of the batch from draining.
     *
     * The swap-out batch (`batch = queue; queue = new Record[]`) replaces the original
     * O(n) `queue.delete(0)` per record. Service-fiber serialisation guarantees the
     * swap is atomic with respect to other `handle()` calls.
     */
    private void drain() {
        try {
            while (!queue.empty) {
                Record[] batch = queue;
                queue = new Record[];
                for (Record record : batch) {
                    try {
                        delegate.handle(record);
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
