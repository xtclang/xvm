import ecstasy.maps.ListMap;

import metrics.model.AggregationTemporality;
import metrics.model.Metric;
import metrics.model.NumberDataPoint;
import metrics.model.SumData;

/**
 * Accumulates measurements into a sum, producing [SumData] on each collection.
 *
 * Measurements are grouped by attribute set: observations that share the same attributes
 * accumulate into a single [NumberDataPoint]. Each distinct attribute set produces its own
 * data point in the exported [Metric].
 *
 * In **Delta** mode accumulators reset after each [collectMetric] call.
 * In **Cumulative** mode accumulators are never reset — each collect returns the full
 * running total since the aggregator was created.
 */
service SumAggregator(InstrumentDescriptor   descriptor,
                       Boolean                isMonotonic,
                       AggregationTemporality temporality)
        implements Aggregator, Collectable {

    private UInt64                   startTimeNanos = MemoryClock.nowNanos();
    private Map<Attributes, Float64> accumulator    = new ListMap();

    @Override
    void record(NumberValue value, Attributes attributes) {
        Float64 fv = asFloat64(value);
        Float64 prev;
        if (prev := accumulator.get(attributes)) {
            accumulator.put(attributes, prev + fv);
        } else {
            accumulator.put(attributes, fv);
        }
    }

    @Override
    conditional Metric collectMetric() {
        if (accumulator.empty) {
            return False;
        }
        UInt64 endNanos = MemoryClock.nowNanos();
        NumberDataPoint[] points = new Array();
        for ((Attributes attrs, Float64 sum) : accumulator) {
            points.add(new NumberDataPoint(endNanos, sum, attrs, startTimeNanos));
        }
        if (temporality == AggregationTemporality.Delta) {
            accumulator    = new ListMap();
            startTimeNanos = endNanos;
        }
        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new SumData(points, temporality, isMonotonic));
    }

    private static Float64 asFloat64(NumberValue value) =
        value.is(Float64) ? value.as(Float64) : value.as(Int).toFloat64();
}
