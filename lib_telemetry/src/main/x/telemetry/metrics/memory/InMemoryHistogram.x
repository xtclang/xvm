/**
 * An in-memory [Histogram] that routes measurements to a pluggable [Aggregator].
 *
 * The [advisoryBucketBoundaries] are exposed for advisory purposes. A [View] may
 * override the aggregation entirely (e.g. to [SumAggregator] or
 * [Base2ExponentialHistogramAggregator]).
 *
 * Note: histogram aggregators fold all observations into a single data point,
 * regardless of attribute set. Per-attribute-set accumulation is not yet supported
 * for histograms.
 */
service InMemoryHistogram
        implements Histogram {

    construct(InstrumentDescriptor descriptor,
              Aggregator           aggregator,
              Float64[]            bounds) {
        this.descriptor               = descriptor;
        this.aggregator               = aggregator;
        this.advisoryBucketBoundaries = bounds;
    }

    @Override
    public/private InstrumentDescriptor descriptor;

    @Override
    Boolean enabled.get() = True;

    @Override
    public/private Float64[] advisoryBucketBoundaries;

    private Aggregator aggregator;

    @Override
    void record(NumberValue value, Map<String, AnyValue> attributes = []) {
        aggregator.record(value, attributes);
    }
}
