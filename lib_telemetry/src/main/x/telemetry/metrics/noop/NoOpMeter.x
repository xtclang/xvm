/**
 * A no-op [Meter] whose factory methods return no-op instruments that discard all
 * recorded values and never invoke callbacks.
 */
const NoOpMeter(InstrumentationScope scope)
        implements Meter {

    @Override
    Counter createCounter(InstrumentDescriptor descriptor) {
        return new NoOpCounter(descriptor);
    }

    @Override
    UpDownCounter createUpDownCounter(InstrumentDescriptor descriptor) {
        return new NoOpUpDownCounter(descriptor);
    }

    @Override
    Histogram createHistogram(InstrumentDescriptor descriptor,
                              Float64[]            bucketBoundaries = []) {
        return new NoOpHistogram(descriptor);
    }

    @Override
    Gauge createGauge(InstrumentDescriptor descriptor) {
        return new NoOpGauge(descriptor);
    }

    @Override
    ObservableCounter createObservableCounter(InstrumentDescriptor descriptor) {
        return new NoOpObservableCounter(descriptor);
    }

    @Override
    ObservableUpDownCounter createObservableUpDownCounter(InstrumentDescriptor descriptor) {
        return new NoOpObservableUpDownCounter(descriptor);
    }

    @Override
    ObservableGauge createObservableGauge(InstrumentDescriptor descriptor) {
        return new NoOpObservableGauge(descriptor);
    }
}
