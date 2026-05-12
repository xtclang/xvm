import metrics.model.AggregationTemporality;
import metrics.model.ResourceMetrics;
import metrics.model.ScopeMetrics;

/**
 * An in-memory [MeterProvider] suitable for unit tests and local development.
 *
 * **Direct collection (tests):** Call [collect] after exercising code under test to obtain a
 * [ResourceMetrics] snapshot containing every measurement recorded across all meters since
 * the last call. No readers are required for this workflow.
 *
 * **Reader-based collection (production):** Construct the provider with one or more
 * [MetricReader] instances. The provider registers itself as the [MetricProducer] with each
 * reader during construction, allowing readers to drive collection on their own schedule.
 * Use [export.PeriodicExportingMetricReader] to push metrics to an exporter automatically.
 *
 * The `temporality` parameter controls how synchronous additive instruments ([Counter],
 * [UpDownCounter], [Histogram]) accumulate measurements:
 *
 *  - `Delta` (default): each collection cycle returns only measurements recorded since the
 *    previous call. Accumulators reset after every collection.
 *  - `Cumulative`: accumulators never reset. Each collection cycle returns the running total
 *    since the meter was created. Use this when targeting Prometheus.
 *
 * Asynchronous instruments ([ObservableCounter], [ObservableUpDownCounter]) always use
 * cumulative temporality per the OTel specification. [Gauge] instruments have no
 * temporality concept.
 */
service InMemoryMeterProvider
        implements MeterProvider, MetricProducer {

    construct(Resource               resource    = Empty,
              AggregationTemporality temporality = AggregationTemporality.Delta,
              MetricReader[]         readers     = [],
              View[]                 views       = []) {
        this.resource    = resource;
        this.temporality = temporality;
        this.readers     = readers;
        this.views       = views;
    } finally {
        for (MetricReader reader : readers) {
            reader.register(this);
        }
    }

    private Resource               resource;
    private AggregationTemporality temporality;
    private MetricReader[]         readers;
    private View[]                 views;
    private InMemoryMeter[]        meters  = new Array();
    private Boolean                active  = True;

    /**
     * Returns a new [InMemoryMeter] scoped to the given instrumentation scope identity
     * and registers it for collection via [collect].
     */
    @Override
    Meter getMeter(InstrumentationScope scope) {
        if (!active) {
            return new noop.NoOpMeter(scope);
        }
        InMemoryMeter        meter = new InMemoryMeter(scope, temporality, views);
        meters.add(meter);
        return meter;
    }

    /**
     * Snapshot all accumulated measurements and return them grouped by resource and scope.
     * In Delta mode, clears each instrument accumulator so subsequent calls return only new
     * measurements. In Cumulative mode, accumulators are not cleared.
     *
     * This method is also the body of [MetricProducer.produce], so registered readers call
     * it through the `MetricProducer` interface.
     */
    ResourceMetrics[] collect() {
        ScopeMetrics[] scopeMetrics = new Array();
        for (InMemoryMeter meter : meters) {
            scopeMetrics.add(meter.collect());
        }
        return [new ResourceMetrics(resource, scopeMetrics.makeImmutable())];
    }

    /**
     * Implements [MetricProducer.produce] by delegating to [collect].
     */
    @Override
    ResourceMetrics[] produce() = collect();

    /**
     * Trigger an immediate collection cycle on every registered [MetricReader], which in
     * turn pushes the snapshot to each reader's underlying exporter.
     */
    @Override
    void forceFlush() {
        if (active) {
            for (MetricReader reader : readers) {
                reader.forceFlush();
            }
        }
    }

    /**
     * Shut down all registered [MetricReader]s and prevent new [Meter] instances from being
     * created. After this call, [getMeter] returns a no-op [Meter].
     */
    @Override
    void shutdown() {
        active = False;
        for (MetricReader reader : readers) {
            reader.shutdown();
        }
    }
}
