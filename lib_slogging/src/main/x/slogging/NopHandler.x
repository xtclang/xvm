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
 * pure forwarder. See `doc/logging/design.md` ("Sink type: `const` vs `service`").
 */
const NopHandler
        implements Handler {

    @Override Boolean enabled(Level level)        = False;
    @Override void    handle(Record record)       {}
    @Override Handler withAttrs(Attr[] attrs)     = this;
    @Override Handler withGroup(String name)      = this;
}
