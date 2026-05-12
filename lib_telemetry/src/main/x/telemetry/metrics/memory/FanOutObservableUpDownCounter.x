/**
 * Routes `observe()` registrations to multiple [InMemoryObservableUpDownCounter] delegates
 * when more than one [View] matches an instrument.
 */
service FanOutObservableUpDownCounter(InstrumentDescriptor              descriptor,
                                       InMemoryObservableUpDownCounter[] delegates)
        implements ObservableUpDownCounter {


    @Override
    Closeable observe(MetricCallback callback) {
        Closeable[] handles = new Array();
        for (InMemoryObservableUpDownCounter obs : delegates) {
            handles += [obs.observe(callback)];
        }
        return new CompositeCloseable(handles.makeImmutable());
    }
}
