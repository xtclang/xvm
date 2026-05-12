/**
 * A single named metric including all of its recorded data points.
 *
 * The `data` field is one of the five OTel metric data types. The actual type
 * determines the instrument semantics: Gauge, Sum, Histogram, ExponentialHistogram,
 * or Summary (legacy).
 */
const Metric(String     name,
             String     description,
             String     unit,
             MetricData data) {}
