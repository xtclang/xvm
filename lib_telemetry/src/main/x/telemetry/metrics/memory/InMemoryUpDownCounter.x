/**
 * An in-memory [UpDownCounter] that routes measurements to a pluggable [Aggregator].
 */
service InMemoryUpDownCounter
        implements UpDownCounter {

    construct(InstrumentDescriptor descriptor,
              Aggregator            agg,
              StreamConfig?         config = Null) {
        this.descriptor = descriptor;
        this.aggregator = agg;
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

    private Attributes filterAndFreeze(Attributes attrs) {
        StreamConfig? cfg = viewConfig;
        if (cfg == Null) {
            return attrs.is(immutable) ? attrs : attrs.makeImmutable();
        }
        Attributes result = StreamConfig.filterAttributes(cfg, attrs);
        return result.is(immutable) ? result : result.makeImmutable();
    }
}
