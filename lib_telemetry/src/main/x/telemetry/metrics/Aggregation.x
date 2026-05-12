/**
 * Identifies the aggregation function to apply to instrument measurements.
 *
 * Use the singleton constants [Default] and [Drop] and the parameterised nested consts
 * [SumAggregation], [LastValueAggregation], [ExplicitBucketHistogramAggregation], and
 * [Base2ExponentialHistogramAggregation] to build [StreamConfig] instances:
 *
 *     // override a Counter to report the last-recorded value as a Gauge
 *     new StreamConfig(aggregation = Aggregation.LastValue)
 *
 *     // override a Histogram with custom explicit-bucket boundaries
 *     new StreamConfig(aggregation = new Aggregation.ExplicitBucketHistogramAggregation(
 *         boundaries = [1.0, 5.0, 10.0, 50.0]))
 */
const Aggregation {

    // ----- singletons ---------------------------------------------------------------------------

    /** Use the instrument's default aggregation. */
    static Aggregation Default  = new DefaultAggregation();

    /** Discard all measurements — no metric is emitted for this stream. */
    static Aggregation Drop     = new DropAggregation();

    /** Collect the arithmetic sum of all measurements, producing [SumData]. */
    static Aggregation Sum      = new SumAggregation();

    /** Collect only the most recent measurement, producing [GaugeData]. */
    static Aggregation LastValue = new LastValueAggregation();

    // ----- isDrop helper ------------------------------------------------------------------------

    /**
     * Returns `True` when this aggregation is [Drop], allowing callers to test without
     * an `is()` type check.
     */
    Boolean isDrop.get() = False;

    // ----- built-in aggregation types -----------------------------------------------------------

    static const DefaultAggregation  extends Aggregation {}

    static const DropAggregation extends Aggregation {
        @Override Boolean isDrop.get() = True;
    }

    static const SumAggregation extends Aggregation {}

    static const LastValueAggregation extends Aggregation {}

    /**
     * Collects observations into explicit histogram buckets, producing [HistogramData].
     *
     * @param boundaries   upper bounds of each bucket (exclusive of +Inf); the spec
     *                     default is `[0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000,
     *                     2500, 5000, 7500, 10000]`
     * @param recordMinMax whether to record min and max per data point
     */
    static const ExplicitBucketHistogramAggregation(
            Float64[] boundaries   = [0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000],
            Boolean   recordMinMax = True)
            extends Aggregation {}

    /**
     * Collects observations into exponential (base-2) histogram buckets, producing
     * [ExponentialHistogramData].
     *
     * @param maxSize     maximum number of positive/negative range buckets (default 160)
     * @param maxScale    maximum scale factor (default 20)
     * @param recordMinMax whether to record min and max per data point
     */
    static const Base2ExponentialHistogramAggregation(
            Int     maxSize      = 160,
            Int     maxScale     = 20,
            Boolean recordMinMax = True)
            extends Aggregation {}
}
