/**
 * Handler derivation wrapper used by shipped handlers that do not maintain their own
 * cached prefix representation.
 *
 * Go slog puts the power of `Logger.With` and `Logger.WithGroup` at the handler
 * boundary: a derived logger contains a derived handler. A production handler can
 * override [withAttributes] / [withGroup] to pre-render JSON fragments, allocate a cached text
 * prefix, or fold attributes into a backend-specific context object. `BoundHandler`
 * supplies the default implementation for handlers that only need correct semantics.
 *
 * The wrapper composes in the same order as Go slog:
 *
 *      logger.with(Map:["env"="prod"])
 *            .withGroup("payments")
 *            .info("charged", Map:["amount"=1099]);
 *
 * yields `env=prod` outside the `payments` group, while:
 *
 *      logger.withGroup("payments")
 *            .with(Map:["env"="prod"])
 *            .info("charged", Map:["amount"=1099]);
 *
 * yields both `env` and `amount` inside the `payments` group.
 */
const BoundHandler(Handler delegate, Attributes attributes = [], String? groupName = Null)
        implements Handler {

    /**
     * Fast-path level check. Bound attributes and groups never affect enablement.
     */
    @Override
    Boolean enabled(Level level) = delegate.enabled(level);

    /**
     * Apply the bound attributes/group to the record, then forward to the delegate.
     */
    @Override
    void handle(Record record) {
        Attributes merged = merge(attributes, record.attributes);
        if (String group ?= groupName) {
            if (!merged.empty) {
                ListMap<String, AnyValue> wrapped = new ListMap();
                wrapped.put(group, merged);
                merged = wrapped.makeImmutable();
            }
        }

        delegate.handle(new Record(
                timestamp  = record.timestamp,
                message    = record.message,
                level      = record.level,
                attributes = merged,
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
    Handler withAttributes(Attributes attributes)
            = attributes.empty ? this : new BoundHandler(delegate=this, attributes=attributes);

    /**
     * Stack another group derivation around this handler.
     */
    @Override
    Handler withGroup(String name)
            = name.empty ? this : new BoundHandler(delegate=this, groupName=name);

    /**
     * Immutable concatenation helper preserving insertion order.
     */
    private Attributes merge(Attributes first, Attributes second) {
        if (first.empty) {
            return second;
        }
        if (second.empty) {
            return first;
        }
        ListMap<String, AnyValue> result = new ListMap(first.size + second.size);
        result.putAll(first);
        result.putAll(second);
        return result.makeImmutable();
    }
}
