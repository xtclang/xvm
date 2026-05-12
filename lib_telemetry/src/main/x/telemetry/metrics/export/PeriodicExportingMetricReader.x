import metrics.model.ResourceMetrics;

/**
 * A [MetricReader] that collects metrics from the SDK on a fixed time interval and
 * pushes each batch to a [MetricExporter].
 *
 * Register this reader with an [memory.InMemoryMeterProvider] at construction time and the
 * SDK handles the export loop automatically — no user-level scheduling code is required.
 *
 * Example:
 *
 *     var reader   = new PeriodicExportingMetricReader(new OtlpJsonExporter("http://..."),
 *                                                      interval = Duration.ofSeconds(15));
 *     var provider = new InMemoryMeterProvider(resource = ..., readers = [reader]);
 *
 * The reader may also be triggered manually via [forceFlush] at any time; the next
 * scheduled collection is unaffected.
 */
service PeriodicExportingMetricReader(MetricExporter exporter,
                                      Duration       interval = Duration.ofSeconds(60))
        implements MetricReader {

    @Inject Clock clock;

    private MetricProducer? producer     = Null;
    private Boolean         active       = True;
    private Duration        nextInterval = None;

    /**
     * Called once by the [MeterProvider] during its construction. Stores the producer and
     * kicks off the first scheduled collection cycle.
     *
     * @throws IllegalState if this reader has already been registered with a provider
     */
    @Override
    void register(MetricProducer producer) {
        assert this.producer == Null as "Reader is already registered with a MeterProvider";
        this.producer = producer;
        scheduleNext();
    }

    /**
     * Pull a metrics snapshot from the registered producer and forward it to the exporter.
     * No-op if [shutdown] has been called or if no producer has been registered yet.
     */
    @Override
    void collect() {
        if (!active) {
            return;
        }
        if (MetricProducer p ?= producer) {
            ResourceMetrics[] batch = p.produce();
            if (!batch.empty) {
                MetricExporter.Result result = exporter.export(batch);
                nextInterval = result.retryIn;
            }
        }
    }

    /**
     * Trigger an immediate [collect] then call [MetricExporter.forceFlush] on the
     * underlying exporter. No-op if [shutdown] has been called.
     */
    @Override
    void forceFlush() {
        if (!active) {
            return;
        }
        collect();
        exporter.forceFlush();
    }

    /**
     * Halt all scheduled collections and shut down the underlying [MetricExporter].
     * Subsequent calls to [collect] and [forceFlush] are no-ops.
     */
    @Override
    void shutdown() {
        active = False;
        exporter.shutdown();
    }

    // ----- internal ------------------------------------------------------------------------------

    private void scheduleNext() {
        Duration delay = nextInterval != None ? nextInterval : interval;
        nextInterval = None;
        clock.schedule(interval, () -> {
            if (active) {
                collect();
                scheduleNext();
            }
        });
    }
}
