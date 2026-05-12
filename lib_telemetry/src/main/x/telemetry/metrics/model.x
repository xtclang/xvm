/**
 * The OpenTelemetry metrics data model.
 *
 * This package contains the wire-format types produced by the SDK and consumed by
 * exporters: [Metric], [ResourceMetrics], [ScopeMetrics], the per-instrument data
 * containers ([SumData], [GaugeData], [HistogramData], [ExponentialHistogramData],
 * [SummaryData]), their data points, [Exemplar]s, and the [AggregationTemporality]
 * enum. The corresponding instrument and SDK types live in the parent `metrics`
 * package.
 */
package model {
    /**
     * A typedef to represent valid metric data types.
     */
    typedef GaugeData | SumData | HistogramData | ExponentialHistogramData | SummaryData as MetricData;
}
