/**
 * An in-memory [Counter] that routes measurements to a pluggable [Aggregator].
 *
 * The aggregator determines the output type: the default is [SumAggregator] producing
 * [SumData], but a [View] can override it to any aggregation.
 */
service InMemoryCounter
        implements Counter {

    construct(InstrumentDescriptor descriptor, Aggregator aggregator, StreamConfig? config = Null) {
        this.descriptor = descriptor;
        this.aggregator = aggregator;
        this.viewConfig = config;
    }

    @Override InstrumentDescriptor descriptor;
    @Override Boolean enabled.get() = True;

    private Aggregator    aggregator;
    private StreamConfig? viewConfig;

    @Override
    void add(NumberValue value, Map<String, AnyValue> attributes = []) {
        aggregator.record(value, filterAndFreeze(attributes));
    }

    // ----- internal helpers ----------------------------------------------------------------------

    private Attributes filterAndFreeze(Attributes attrs) {
        StreamConfig? cfg = viewConfig;
        if (cfg == Null) {
            return attrs.is(immutable) ? attrs : attrs.makeImmutable();
        }
        Attributes result = StreamConfig.filterAttributes(cfg, attrs);
        return result.is(immutable) ? result : result.makeImmutable();
    }
}
