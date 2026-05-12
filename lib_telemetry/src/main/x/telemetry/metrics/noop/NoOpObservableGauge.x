/**
 * A no-op [ObservableGauge] that never invokes registered callbacks.
 */
const NoOpObservableGauge(InstrumentDescriptor descriptor)
        implements ObservableGauge {

    @Override
    Closeable observe(MetricCallback callback) {
        return NoOpCloseable.Instance;
    }
}
