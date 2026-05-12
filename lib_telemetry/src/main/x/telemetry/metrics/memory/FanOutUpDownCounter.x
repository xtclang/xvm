/**
 * Routes `add()` calls to multiple [UpDownCounter] delegates when more than one [View]
 * matches an instrument.
 */
service FanOutUpDownCounter(InstrumentDescriptor descriptor, UpDownCounter[] delegates)
        implements UpDownCounter {

    @Override Boolean enabled.get() = True;

    @Override
    void add(NumberValue value, Map<String, AnyValue> attributes = []) {
        for (UpDownCounter c : delegates) {
            c.add(value, attributes);
        }
    }
}
