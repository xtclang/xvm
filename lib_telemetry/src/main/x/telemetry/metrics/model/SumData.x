/**
 * The data for a Sum metric stream.
 *
 * When `isMonotonic` is `True`, values must be non-decreasing (counter resets are
 * allowed). `aggregationTemporality` controls whether each reported value covers only
 * the current interval (Delta) or the full sum since the stream started (Cumulative).
 */
const SumData(NumberDataPoint[]      dataPoints,
              AggregationTemporality aggregationTemporality,
              Boolean                isMonotonic) {}
