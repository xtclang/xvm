/**
 * A no-op [Counter] that discards all recorded values.
 */
const NoOpCounter(InstrumentDescriptor descriptor)
        implements Counter {

    @Override
    Boolean enabled.get() {
        return False;
    }

    @Override
    void add(NumberValue value, Map<String, AnyValue> attributes = []) {}
}
