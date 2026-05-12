/**
 * A no-op [UpDownCounter] that discards all recorded values.
 */
const NoOpUpDownCounter(InstrumentDescriptor descriptor)
        implements UpDownCounter {

    @Override
    Boolean enabled.get() {
        return False;
    }

    @Override
    void add(NumberValue value, Map<String, AnyValue> attributes = []) {}
}
