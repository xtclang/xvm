import metrics.model.AggregationTemporality;
import metrics.model.Metric;
import metrics.model.NumberDataPoint;
import metrics.model.SumData;

/**
 * An in-memory [ObservableUpDownCounter] that invokes registered callbacks on each
 * collection cycle and produces cumulative [SumData] from the reported measurements.
 */
service InMemoryObservableUpDownCounter(InstrumentDescriptor descriptor, StreamConfig? viewConfig = Null)
        implements ObservableUpDownCounter, Collectable, InMemoryObservableInstrument {

    private MetricCallback?[] slots  = new Array<MetricCallback?>();
    private Int               nextId = 0;

    @Override
    Closeable observe(MetricCallback callback) {
        Int id = nextId++;
        while (slots.size <= id) {
            slots += [Null];
        }
        slots[id] = callback;
        return new InMemoryUnregistration(this, id);
    }

    @Override
    void unobserve(Int id) {
        if (id < slots.size) {
            slots[id] = Null;
        }
    }

    @Override
    conditional Metric collectMetric() {
        @Volatile NumberDataPoint[] points = new Array();
        UInt64        now = MemoryClock.nowNanos();
        StreamConfig? cfg = viewConfig;
        for (MetricCallback? slot : slots) {
            if (MetricCallback cb ?= slot) {
                cb(m -> {
                    Attributes attrs;
                    if (StreamConfig obs_cfg ?= cfg) {
                        attrs = StreamConfig.filterAttributes(obs_cfg, m.attributes).makeImmutable();
                    } else {
                        attrs = m.attributes.is(immutable) ? m.attributes : m.attributes.makeImmutable();
                    }
                    points.add(new NumberDataPoint(now, m.value, attrs));
                });
            }
        }
        if (points.empty) {
            return False;
        }
        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new SumData(points, AggregationTemporality.Cumulative, False));
    }
}
