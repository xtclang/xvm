/**
 * A single data point in a Summary metric stream.
 *
 * Summary is a legacy type for compatibility with Prometheus-style summaries. New
 * instrumentation should prefer [HistogramData] or [ExponentialHistogramData] instead.
 *
 * `quantileValues` must be sorted in ascending order of quantile value.
 */
const SummaryDataPoint(UInt64            timeUnixNano,
                       UInt64            count,
                       Float64           sum,
                       Attributes attributes    = [],
                       UInt64?           startTimeUnixNano = Null,
                       ValueAtQuantile[] quantileValues    = [],
                       DataPointFlags    flags             = DataPointFlags.Default) {}
