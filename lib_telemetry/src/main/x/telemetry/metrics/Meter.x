/**
 * Factory for metric instruments, scoped to a single [InstrumentationScope].
 *
 * Obtain a `Meter` from a [MeterProvider]. All instruments created from the same
 * `Meter` share the same instrumentation scope identity and are associated with it
 * in exported [ScopeMetrics].
 */
interface Meter {
    /**
     * The instrumentation scope that identifies the library or component producing
     * measurements through this `Meter`.
     */
    @RO InstrumentationScope scope;

    // ----- synchronous instruments ---------------------------------------------------------------

    /**
     * Creates a [Counter] for recording non-negative increments (e.g. requests served,
     * bytes sent). Backed by [SumData] with `isMonotonic = True`.
     */
    Counter createCounter(InstrumentDescriptor descriptor);

    /**
     * Creates an [UpDownCounter] for recording values that can rise and fall (e.g. active
     * requests, queue depth). Backed by [SumData] with `isMonotonic = False`.
     */
    UpDownCounter createUpDownCounter(InstrumentDescriptor descriptor);

    /**
     * Creates a [Histogram] for recording statistical distributions (e.g. latency, payload
     * size). Backed by [HistogramData] by default; configure a [View] with
     * [Aggregation.Base2ExponentialHistogramAggregation] to produce [ExponentialHistogramData]
     * instead.
     *
     * @param bucketBoundaries  advisory explicit bucket upper bounds; the SDK may use or
     *                          override these. Ignored when a View selects a different
     *                          aggregation for this instrument.
     */
    Histogram createHistogram(InstrumentDescriptor descriptor,
                              Float64[]            bucketBoundaries = []);

    /**
     * Creates a synchronous [Gauge] for recording non-additive absolute values (e.g. CPU
     * utilisation, fan speed). Backed by [GaugeData].
     */
    Gauge createGauge(InstrumentDescriptor descriptor);

    // ----- asynchronous instruments --------------------------------------------------------------

    /**
     * Creates an [ObservableCounter] for reporting monotonically increasing cumulative
     * totals via a callback (e.g. CPU time, page faults). Backed by [SumData] with
     * `isMonotonic = True`.
     */
    ObservableCounter createObservableCounter(InstrumentDescriptor descriptor);

    /**
     * Creates an [ObservableUpDownCounter] for reporting additive snapshot values via a
     * callback (e.g. heap size, queue item count). Backed by [SumData] with
     * `isMonotonic = False`.
     */
    ObservableUpDownCounter createObservableUpDownCounter(InstrumentDescriptor descriptor);

    /**
     * Creates an [ObservableGauge] for reporting non-additive snapshot values via a
     * callback (e.g. ambient temperature, CPU fan speed). Backed by [GaugeData].
     */
    ObservableGauge createObservableGauge(InstrumentDescriptor descriptor);
}
