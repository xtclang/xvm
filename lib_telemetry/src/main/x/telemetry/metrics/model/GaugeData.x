/**
 * The data for a Gauge metric stream.
 *
 * A Gauge samples and reports the last observed value within each collection interval.
 * Values are not accumulated across intervals and there is no temporality associated
 * with a Gauge.
 */
const GaugeData(NumberDataPoint[] dataPoints = []) {}
