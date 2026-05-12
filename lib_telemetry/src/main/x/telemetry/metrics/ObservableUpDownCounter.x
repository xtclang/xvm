/**
 * An asynchronous instrument that reports additive values that can rise or fall.
 *
 * The callback reports an absolute snapshot value, not a delta. Use for values that
 * are cheapest to read as a current total rather than an increment (e.g. process heap
 * size, number of items in a lock-free queue). Backed by a [SumData] with
 * `isMonotonic = False`.
 */
interface ObservableUpDownCounter {
    @RO InstrumentDescriptor descriptor;

    /**
     * Registers `callback` for collection. Returns a [Closeable] that unregisters it.
     */
    Closeable observe(MetricCallback callback);
}
