/**
 * Routes `record()` calls to multiple [Histogram] delegates when more than one [View]
 * matches an instrument.
 */
service FanOutHistogram(InstrumentDescriptor descriptor,
                        Histogram[]          delegates,
                        Float64[]            bucketBoundaries)
        implements Histogram {

    @Override Boolean   enabled.get()                  = True;
    @Override Float64[] advisoryBucketBoundaries.get() = bucketBoundaries;

    @Override
    void record(NumberValue value, Map<String, AnyValue> attributes = []) {
        for (Histogram h : delegates) {
            h.record(value, attributes);
        }
    }
}
