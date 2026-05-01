/**
 * Test-oriented handler — captures every emitted record in an in-memory list for
 * later assertion. Direct equivalent of `lib_logging`'s `MemoryLogSink`, and
 * structurally identical to `slog`'s `slogtest`-style helpers in Go.
 *
 *      MemoryHandler h = new MemoryHandler();
 *      Logger logger = new Logger(h);
 *      logger.info("processed", [Attr.of("count", 42)]);
 *      assert h.records.size == 1;
 *      assert h.records[0].level == Level.Info;
 *
 * # Why this handler is a `service`
 *
 * It accumulates records. The backing array is mutated on every `handle()` call and
 * the same instance is shared across the logger-under-test fiber and the assertion
 * fiber. Same reasoning as `lib_logging`'s `MemoryLogSink`. See
 * `doc/logging/design.md` ("Sink type: `const` vs `service`").
 */
service MemoryHandler
        implements Handler {

    public/private Level rootLevel = Level.Debug;

    /**
     * Mutable backing storage. Internal — Ecstasy forbids returning a mutable array
     * from a service call, so we expose [records] as an immutable snapshot below.
     */
    private Record[] recordList = new Record[];

    /**
     * Immutable snapshot of the captured records, in emission order. Each access
     * copies — same pattern as `lib_logging.MemoryLogSink.events`.
     */
    @RO Record[] records.get() {
        return recordList.toArray(Constant);
    }

    @Override
    Boolean enabled(Level level) {
        return level.severity >= rootLevel.severity;
    }

    @Override
    void handle(Record record) {
        recordList.add(record);
    }

    @Override
    Handler withAttrs(Attr[] attrs) {
        // Share the same underlying recordList so derived loggers' records show up in
        // the same place — matches slogtest behaviour and what tests intuitively want.
        return this;
    }

    @Override
    Handler withGroup(String name) = this;

    /**
     * Discard all captured records.
     */
    void reset() {
        recordList.clear();
    }
}
