/**
 * Routes `observe()` registrations to multiple [InMemoryObservableCounter] delegates
 * when more than one [View] matches an instrument. Each delegate applies its own
 * [StreamConfig] attribute filter during collection.
 */
service FanOutObservableCounter(InstrumentDescriptor         descriptor,
                                InMemoryObservableCounter[]  delegates)
        implements ObservableCounter {

    @Override
    Closeable observe(MetricCallback callback) {
        Closeable[] handles = new Array();
        for (InMemoryObservableCounter obs : delegates) {
            handles.add(obs.observe(callback));
        }
        return new CompositeCloseable(handles.makeImmutable());
    }
}
