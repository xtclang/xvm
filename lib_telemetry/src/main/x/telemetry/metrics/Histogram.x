/**
 * A synchronous instrument that records arbitrary values for statistical aggregation.
 *
 * Use for latencies, sizes, or any measurement where the distribution matters (e.g.
 * request duration, response payload size). Backed by [HistogramData].
 *
 * `advisoryBucketBoundaries` provides a hint to the SDK about preferred bucket
 * boundaries; the SDK may use or ignore it.
 */
interface Histogram {
    @RO InstrumentDescriptor descriptor;

    /**
     * `False` when the SDK has disabled this instrument; callers may skip measurement
     * work entirely when this is `False`.
     */
    @RO Boolean enabled;

    /**
     * Suggested explicit bucket upper bounds for this histogram. The SDK may override.
     */
    @RO Float64[] advisoryBucketBoundaries;

    /**
     * Records a single observation. `value` should be non-negative.
     */
    void record(NumberValue value, Map<String, AnyValue> attributes = []);
}
