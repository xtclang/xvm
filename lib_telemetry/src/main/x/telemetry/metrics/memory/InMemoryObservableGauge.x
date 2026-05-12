import metrics.model.GaugeData;
import metrics.model.Metric;
import metrics.model.NumberDataPoint;

/**
 * An in-memory [ObservableGauge] that invokes registered callbacks on each collection
 * cycle and produces [GaugeData] from the reported measurements.
 */
service InMemoryObservableGauge(InstrumentDescriptor descriptor, StreamConfig? viewConfig = Null)
        implements ObservableGauge, Collectable, InMemoryObservableInstrument {

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
        @Volatile NumberDataPoint[] points = [];
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
                    points += [new NumberDataPoint(now, m.value, attrs)];
                });
            }
        }
        if (points.empty) {
            return False;
        }
        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new GaugeData(points));
    }
}
