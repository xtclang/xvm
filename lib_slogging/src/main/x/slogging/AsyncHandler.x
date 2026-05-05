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
        return !closed && delegate.enabled(level);
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
     */
    private void drain() {
        while (!queue.empty) {
            Record record = queue[0];
            queue.delete(0);
            delegate.handle(record);
        }
        draining = False;
    }
}
