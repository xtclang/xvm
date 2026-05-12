/**
 * An asynchronous instrument that reports monotonically increasing cumulative totals.
 *
 * The callback receives the cumulative total since the measurement started — not a
 * delta. Use for values that only go up and are most naturally read from the system
 * as a running total (e.g. CPU time consumed, total page faults). Backed by a
 * [SumData] with `isMonotonic = True`.
 */
interface ObservableCounter {
    @RO InstrumentDescriptor descriptor;

    /**
     * Registers `callback` for collection. Returns a [Closeable] that unregisters it.
     */
    Closeable observe(MetricCallback callback);
}
