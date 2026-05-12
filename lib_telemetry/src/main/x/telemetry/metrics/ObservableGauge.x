/**
 * An asynchronous instrument that reports non-additive snapshot values.
 *
 * Use for measurements that represent a current reading with no additive semantics
 * (e.g. ambient temperature, CPU fan speed). Backed by [GaugeData].
 */
interface ObservableGauge {
    @RO InstrumentDescriptor descriptor;

    /**
     * Registers `callback` for collection. Returns a [Closeable] that unregisters it.
     */
    Closeable observe(MetricCallback callback);
}
