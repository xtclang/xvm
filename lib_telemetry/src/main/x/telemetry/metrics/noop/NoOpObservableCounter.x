/**
 * A no-op [ObservableCounter] that never invokes registered callbacks.
 */
const NoOpObservableCounter(InstrumentDescriptor descriptor)
        implements ObservableCounter {

    @Override
    Closeable observe(MetricCallback callback) {
        return NoOpCloseable.Instance;
    }
}
