import metrics.model.AggregationTemporality;
import metrics.model.HistogramData;
import metrics.model.HistogramDataPoint;
import metrics.model.Metric;

/**
 * Aggregates measurements into explicit histogram buckets, producing [HistogramData]
 * on each collection.
 */
service ExplicitBucketHistogramAggregator(InstrumentDescriptor   descriptor,
                                           Float64[]              bounds,
                                           AggregationTemporality temporality,
                                           Boolean                recordMinMax = True)
        implements Aggregator, Collectable {

    construct(InstrumentDescriptor   descriptor,
              Float64[]              bounds,
              AggregationTemporality temporality,
              Boolean                recordMinMax = True) {
        this.descriptor    = descriptor;
        this.bounds        = bounds;
        this.temporality   = temporality;
        this.recordMinMax  = recordMinMax;
        this.startTimeNanos = MemoryClock.nowNanos();
        this.bucketCounts  = new Array<UInt64>(bounds.size + 1, 0);
    }

    private InstrumentDescriptor   descriptor;
    private Float64[]              bounds;
    private AggregationTemporality temporality;
    private Boolean                recordMinMax;
    private UInt64   startTimeNanos;
    private UInt64   count = 0;
    private Float64? sum   = Null;
    private Float64? min   = Null;
    private Float64? max   = Null;
    private UInt64[] bucketCounts;

    @Override
    void record(NumberValue value, Attributes attributes) {
        Float64 fv = asFloat64(value);
        count += 1;
        sum    = (sum ?: 0.0) + fv;
        if (recordMinMax) {
            if (Float64 m ?= min) { min = fv < m ? fv : m; } else { min = fv; }
            if (Float64 m ?= max) { max = fv > m ? fv : m; } else { max = fv; }
        }
        bucketCounts[bucketIndex(fv, bounds)] += 1;
    }

    @Override
    conditional Metric collectMetric() {
        if (count == 0) {
            return False;
        }
        UInt64 endNanos = MemoryClock.nowNanos();
        Float64? reportMin = recordMinMax ? min : Null;
        Float64? reportMax = recordMinMax ? max : Null;
        var point = new HistogramDataPoint(endNanos, count, bounds, bucketCounts.toArray(),
                                           [], startTimeNanos, sum, reportMin, reportMax);
        if (temporality == AggregationTemporality.Delta) {
            count          = 0;
            sum            = Null;
            min            = Null;
            max            = Null;
            bucketCounts   = new Array<UInt64>(bounds.size + 1, 0);
            startTimeNanos = endNanos;
        }
        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new HistogramData([point], temporality));
    }

    private static Int bucketIndex(Float64 value, Float64[] bounds) {
        for (Int i : 0 ..< bounds.size) {
            if (value <= bounds[i]) {
                return i;
            }
        }
        return bounds.size;
    }

    private static Float64 asFloat64(NumberValue value) =
        value.is(Float64) ? value.as(Float64) : value.as(Int).toFloat64();
}
