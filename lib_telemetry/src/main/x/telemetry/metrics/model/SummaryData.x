/**
 * The data for a Summary metric stream.
 *
 * Summary is a legacy type for compatibility with Prometheus-style summaries. Prefer
 * [HistogramData] or [ExponentialHistogramData] for new instrumentation.
 */
const SummaryData(SummaryDataPoint[] dataPoints = []) {}
