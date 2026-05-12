/**
 * Routes `record()` calls to multiple [Gauge] delegates when more than one [View]
 * matches an instrument.
 */
service FanOutGauge(InstrumentDescriptor descriptor, Gauge[] delegates)
        implements Gauge {

    @Override Boolean enabled.get() = True;

    @Override
    void record(NumberValue value, Map<String, AnyValue> attributes = []) {
        for (Gauge g : delegates) {
            g.record(value, attributes);
        }
    }
}
