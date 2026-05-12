/**
 * A no-op [ObservableUpDownCounter] that never invokes registered callbacks.
 */
const NoOpObservableUpDownCounter(InstrumentDescriptor descriptor)
        implements ObservableUpDownCounter {

    @Override
    Closeable observe(MetricCallback callback) {
        return NoOpCloseable.Instance;
    }
}
