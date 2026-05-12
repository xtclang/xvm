import metrics.model.Metric;

/**
 * An SDK component that accumulates instrument measurements and produces a [Metric]
 * snapshot on demand.
 *
 * Each [Aggregator] instance corresponds to one output stream (one [View] match) for one
 * instrument. The instrument wrapper filters attributes and calls [record]; the meter's
 * collection mechanism calls [collectMetric] during each collection cycle.
 *
 * Concrete implementations also implement [memory.Collectable] so they can be registered
 * in the meter's collection list.
 */
interface Aggregator {
    /**
     * Accumulate a single measurement. `attributes` are already filtered and immutable —
     * the calling instrument applies the [StreamConfig] attribute filter before
     * forwarding here.
     */
    void record(NumberValue value, Attributes attributes);

    /**
     * Snapshot the current state and return a [Metric], or `Null` if no measurements
     * have been recorded since the last call (Delta mode) or since construction
     * (Cumulative mode).
     */
    conditional Metric collectMetric();
}
