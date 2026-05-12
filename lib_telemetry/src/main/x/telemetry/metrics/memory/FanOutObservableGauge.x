/**
 * Routes `observe()` registrations to multiple [InMemoryObservableGauge] delegates
 * when more than one [View] matches an instrument.
 */
service FanOutObservableGauge(InstrumentDescriptor       descriptor,
                               InMemoryObservableGauge[]  delegates)
        implements ObservableGauge {


    @Override
    Closeable observe(MetricCallback callback) {
        Closeable[] handles = new Array();
        for (InMemoryObservableGauge obs : delegates) {
            handles += [obs.observe(callback)];
        }
        return new CompositeCloseable(handles.makeImmutable());
    }
}
