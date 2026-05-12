/**
 * Routes `add()` calls to multiple [Counter] delegates when more than one [View] matches
 * an instrument. Each delegate is an [InMemoryCounter] registered independently in the
 * meter's collection list, so collection is transparent.
 */
service FanOutCounter(InstrumentDescriptor descriptor, Counter[] delegates)
        implements Counter {

    @Override Boolean enabled.get() = True;

    @Override
    void add(NumberValue value, Map<String, AnyValue> attributes = []) {
        for (Counter c : delegates) {
            c.add(value, attributes);
        }
    }
}
