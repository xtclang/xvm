import slogging.Attr;
import slogging.Handler;
import slogging.Level;
import slogging.Record;

/**
 * Test-only handler that captures every emitted record in a list. Used as a stand-in
 * for a "real" handler so tests can assert exactly what the `Logger` chose to emit, in
 * order. Mirrors the `ListLogSink` helper in `LoggingTest`.
 *
 * `rootLevel` defaults to `Debug` so by default every emitted record is captured.
 *
 * Modelled as a `service` because it carries mutable state (the records list) shared
 * across the fiber-under-test and the assertion code. See
 * `doc/logging/design/design.md` ("Sink type: `const` vs `service`").
 */
service ListHandler
        implements Handler {

    public/private Level rootLevel = slogging.Level.Debug;

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
    Handler withAttrs(Attr[] attrs) = this;

    @Override
    Handler withGroup(String name) = this;

    void setLevel(Level level) {
        rootLevel = level;
    }

    void reset() {
        recordList.clear();
    }
}
