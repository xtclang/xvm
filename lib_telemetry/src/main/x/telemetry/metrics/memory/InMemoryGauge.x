/**
 * An in-memory [Gauge] that routes measurements to a pluggable [Aggregator].
 */
service InMemoryGauge
        implements Gauge {

    construct(InstrumentDescriptor descriptor,
              Aggregator            aggregator,
              StreamConfig?         config = Null) {
        this.descriptor = descriptor;
        this.aggregator = aggregator;
        this.viewConfig = config;
    }

    @Override
    public/private InstrumentDescriptor descriptor;

    @Override
    Boolean enabled.get() = True;

    private Aggregator    aggregator;
    private StreamConfig? viewConfig;

    @Override
    void record(NumberValue value, Map<String, AnyValue> attributes = []) {
        aggregator.record(value, filterAndFreeze(attributes));
    }

    private Attributes filterAndFreeze(Attributes attrs) {
        StreamConfig? cfg = viewConfig;
        if (cfg == Null) {
            return attrs.is(immutable) ? attrs : attrs.makeImmutable();
        }
        Attributes result = StreamConfig.filterAttributes(cfg, attrs);
        return result.is(immutable) ? result : result.makeImmutable();
    }
}
