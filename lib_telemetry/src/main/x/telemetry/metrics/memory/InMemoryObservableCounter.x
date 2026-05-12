import metrics.model.AggregationTemporality;
import metrics.model.Metric;
import metrics.model.NumberDataPoint;
import metrics.model.SumData;

/**
 * An in-memory [ObservableCounter] that invokes registered callbacks on each
 * collection cycle and produces cumulative [SumData] from the reported measurements.
 */
service InMemoryObservableCounter(InstrumentDescriptor descriptor, StreamConfig? viewConfig = Null)
        implements ObservableCounter, Collectable, InMemoryObservableInstrument {

    private MetricCallback?[] slots  = new Array();
    private Int               nextId = 0;

    /**
     * Registers `callback` for collection. The callback is invoked on each [collectMetric]
     * call. Returns a [Closeable] that unregisters the callback when closed.
     */
    @Override
    Closeable observe(MetricCallback callback) {
        Int id = nextId++;
        while (slots.size <= id) {
            slots.add(Null);
        }
        slots[id] = callback;
        return new InMemoryUnregistration(this, id);
    }

    /**
     * Removes the callback previously registered under `id`. Called by the [Closeable]
     * returned from [observe].
     */
    @Override
    void unobserve(Int id) {
        if (id < slots.size) {
            slots[id] = Null;
        }
    }

    /**
     * Invokes all registered callbacks, collects their measurements, and returns a
     * [Metric] containing cumulative [SumData], or `Null` if no measurements were
     * reported by any callback.
     */
    @Override
    conditional Metric collectMetric() {
        @Volatile NumberDataPoint[] points = new Array();
        StreamConfig?               cfg    = viewConfig;
        UInt64                      now    = MemoryClock.nowNanos();

        for (MetricCallback? slot : slots) {
            if (MetricCallback cb ?= slot) {
                cb(m -> {
                    Attributes attrs;
                    if (StreamConfig obs_cfg ?= cfg) {
                        attrs = StreamConfig.filterAttributes(obs_cfg, m.attributes).makeImmutable();
                    } else {
                        attrs = m.attributes.makeImmutable();
                    }
                    points.add(new NumberDataPoint(now, m.value, attrs));
                });
            }
        }
        if (points.empty) {
            return False;
        }
        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new SumData(points, AggregationTemporality.Cumulative, True));
    }
}
