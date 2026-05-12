import metrics.model.Metric;

/**
 * Internal interface implemented by every in-memory instrument so [InMemoryMeter] can
 * collect all instruments in a single pass without caring about their concrete type.
 */
interface Collectable {
    /**
     * Snapshot and return a [Metric] for this collection cycle, or `Null` if no
     * measurements have been recorded since the last call.
     */
    conditional Metric collectMetric();
}
