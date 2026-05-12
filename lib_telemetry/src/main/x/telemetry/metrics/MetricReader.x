/**
 * An SDK component that sits between a [MeterProvider]'s in-memory state and a
 * [MetricExporter].
 *
 * A `MetricReader` is registered with a [MeterProvider] at construction time. The provider
 * calls [register] exactly once to supply the reader with a [MetricProducer] — the
 * handle it uses to pull accumulated metrics from the provider's in-memory state.
 *
 * The SDK ships one standard implementation: [export.PeriodicExportingMetricReader], which
 * collects and exports on a configurable time interval. A single reader instance MUST NOT
 * be registered with more than one provider.
 */
interface MetricReader {

    /**
     * Called once by the [MeterProvider] during its construction. Implementations store the
     * producer reference and start whatever collection schedule they implement.
     *
     * @throws IllegalState if the reader has already been registered with a provider
     */
    void register(MetricProducer producer);

    /**
     * Pull a metrics snapshot from the registered [MetricProducer] and forward it to the
     * underlying transport. No-op if [shutdown] has been called.
     */
    void collect();

    /**
     * Trigger an immediate [collect] and forward the result to the underlying exporter's
     * [MetricExporter.forceFlush]. No-op if [shutdown] has been called.
     */
    void forceFlush();

    /**
     * Halt all scheduled work and release resources. After this call returns, subsequent
     * [collect] and [forceFlush] invocations are no-ops. The underlying [MetricExporter]
     * is shut down.
     *
     * This method MUST be called at most once per reader instance.
     */
    void shutdown();
}
