/**
 * A no-op [Histogram] that discards all recorded values.
 */
const NoOpHistogram(InstrumentDescriptor descriptor)
        implements Histogram {

    @Override
    Boolean enabled.get() {
        return False;
    }

    @Override
    Float64[] advisoryBucketBoundaries.get() {
        return [];
    }

    @Override
    void record(NumberValue value, Map<String, AnyValue> attributes = []) {}
}
