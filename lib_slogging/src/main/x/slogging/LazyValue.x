/**
 * Internal carrier for [Attr.lazy] values.
 *
 * Go slog exposes this idea as the `LogValuer` interface on values. The POC keeps the
 * public call shape smaller: callers create `Attr.lazy("key", () -> value)`, and the
 * logger/handler boundary resolves this wrapper after the level check accepts the record.
 */
const LazyValue(ObjectSupplier supplier) {

    /**
     * Evaluate the deferred value.
     */
    Object resolve() = supplier();
}
