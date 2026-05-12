import ecstasy.maps.ListMap;

import metrics.model.GaugeData;
import metrics.model.Metric;
import metrics.model.NumberDataPoint;

/**
 * Retains the most recent measurement for each attribute set, producing [GaugeData] on
 * each collection.
 *
 * When multiple measurements are recorded with the same attribute set, only the last one
 * is retained. Each distinct attribute set produces its own [NumberDataPoint].
 */
service LastValueAggregator(InstrumentDescriptor descriptor)
        implements Aggregator, Collectable {

    private Map<Attributes, NumberValue> latestValues = new ListMap();

    @Override
    void record(NumberValue value, Attributes attributes) {
        latestValues.put(attributes, value);
    }

    @Override
    conditional Metric collectMetric() {
        if (latestValues.empty) {
            return False;
        }
        UInt64 now = MemoryClock.nowNanos();
        NumberDataPoint[] points = new Array();
        for ((Attributes attrs, NumberValue v) : latestValues) {
            points.add(new NumberDataPoint(now, v, attrs));
        }
        latestValues = new ListMap();
        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new GaugeData(points));
    }
}
