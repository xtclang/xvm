/**
 * A no-op synchronous [Gauge] that discards all recorded values.
 */
const NoOpGauge(InstrumentDescriptor descriptor)
        implements Gauge {

    @Override
    Boolean enabled.get() {
        return False;
    }

    @Override
    void record(NumberValue value, Map<String, AnyValue> attributes = []) {}
}
