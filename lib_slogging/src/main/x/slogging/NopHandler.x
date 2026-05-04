/**
 * Corresponds to slog's idiomatic "discard" handler — typically constructed as
 * `slog.New(slog.NewTextHandler(io.Discard, nil))` in Go, but a single-purpose type is
 * cleaner in Ecstasy.
 *
 * Stateless — drops every record. `enabled` always returns `False` so callers that
 * respect the check can skip building attribute arrays entirely.
 *
 * # Why this handler is a `const`
 *
 * Identical reasoning to `lib_logging`'s `NoopLogSink`: no state, no shared mutation,
 * pure forwarder. See `doc/logging/design/design.md` ("Sink type: `const` vs `service`").
 */
const NopHandler
        implements Handler {

    /**
     * Always disabled so caller-side fast paths skip record construction.
     */
    @Override
    Boolean enabled(Level level) = False;

    /**
     * Deliberately drops the record. In normal use this should not be reached because
     * `enabled` returns `False`, but it remains a no-op for defensive simplicity.
     */
    @Override
    void handle(Record record) {}

    /**
     * Deriving a no-op handler is still no-op.
     */
    @Override
    Handler withAttrs(Attr[] attrs) = this;

    /**
     * Grouping a no-op handler is still no-op.
     */
    @Override
    Handler withGroup(String name) = this;
}
