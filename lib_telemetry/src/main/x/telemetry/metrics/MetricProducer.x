import metrics.model.ResourceMetrics;

/**
 * A source of aggregated metric data that a [MetricReader] can pull from.
 *
 * The SDK in-memory state (see [memory.InMemoryMeterProvider]) implements this interface.
 * Future non-SDK sources (e.g. bridged Prometheus metrics) may also implement it and be
 * registered with a [MetricReader].
 */
interface MetricProducer {
    /**
     * Produce a snapshot of currently-aggregated metrics.
     */
    ResourceMetrics[] produce();
}
