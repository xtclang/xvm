/**
 * Handler derivation wrapper used by shipped handlers that do not maintain their own
 * cached prefix representation.
 *
 * Go slog puts the power of `Logger.With` and `Logger.WithGroup` at the handler
 * boundary: a derived logger contains a derived handler. A production handler can
 * override [withAttrs] / [withGroup] to pre-render JSON fragments, allocate a cached text
 * prefix, or fold attributes into a backend-specific context object. `BoundHandler`
 * supplies the default implementation for handlers that only need correct semantics.
 *
 * The wrapper composes in the same order as Go slog:
 *
 *      logger.with([Attr.of("env", "prod")])
 *            .withGroup("payments")
 *            .info("charged", [Attr.of("amount", 1099)]);
 *
 * yields `env=prod` outside the `payments` group, while:
 *
 *      logger.withGroup("payments")
 *            .with([Attr.of("env", "prod")])
 *            .info("charged", [Attr.of("amount", 1099)]);
 *
 * yields both `env` and `amount` inside the `payments` group.
 */
const BoundHandler(Handler delegate, Attr[] attrs, String? groupName)
        implements Handler {

    /**
     * Derive a handler with pre-bound attributes.
     */
    construct(Handler delegate, Attr[] attrs) {
        construct BoundHandler(delegate, attrs.toArray(Constant), Null);
    }

    /**
     * Derive a handler that groups subsequent attributes under `groupName`.
     */
    construct(Handler delegate, String groupName) {
        construct BoundHandler(delegate, [], groupName);
    }

    /**
     * Fast-path level check. Bound attrs and groups never affect enablement.
     */
    @Override
    Boolean enabled(Level level) = delegate.enabled(level);

    /**
     * Apply the bound attrs/group to the record, resolve any [Attr.lazy] values now
     * that the logger has already passed the level check, then forward to the delegate.
     */
    @Override
    void handle(Record record) {
        Attr[] merged = merge(Attr.resolveAll(attrs), Attr.resolveAll(record.attrs));
        if (String group ?= groupName) {
            merged = merged.empty
                    ? []
                    : [Attr.group(group, merged)].toArray(Constant);
        }

        delegate.handle(new Record(
                time       = record.time,
                message    = record.message,
                level      = record.level,
                attrs      = merged,
                exception  = record.exception,
                sourceFile = record.sourceFile,
                sourceLine = record.sourceLine,
                threadName = record.threadName,
        ));
    }

    /**
     * Stack another attribute derivation around this handler. The wrapper order is what
     * preserves the difference between `With(...).WithGroup(...)` and
     * `WithGroup(...).With(...)`.
     */
    @Override
    Handler withAttrs(Attr[] more) {
        return more.empty ? this : new BoundHandler(this, more);
    }

    /**
     * Stack another group derivation around this handler.
     */
    @Override
    Handler withGroup(String name) {
        return name == "" ? this : new BoundHandler(this, name);
    }

    /**
     * Immutable concatenation helper for passable records.
     */
    private Attr[] merge(Attr[] first, Attr[] second) {
        return first.empty
                ? second.toArray(Constant)
                : second.empty
                    ? first.toArray(Constant)
                    : (first + second).toArray(Constant);
    }
}
