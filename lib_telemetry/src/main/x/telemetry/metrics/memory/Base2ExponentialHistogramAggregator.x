import metrics.model.AggregationTemporality;
import metrics.model.BucketSet;
import metrics.model.ExponentialHistogramData;
import metrics.model.ExponentialHistogramDataPoint;
import metrics.model.Metric;

/**
 * Aggregates measurements into an exponential (base-2) histogram, producing
 * [ExponentialHistogramData] on each collection.
 *
 * Positive and negative observations are tracked in separate sparse bucket maps using the
 * span-based scale-reduction algorithm. When the span of either side exceeds [maxBuckets],
 * the scale is reduced by one until the constraint is satisfied.
 */
service Base2ExponentialHistogramAggregator(InstrumentDescriptor   descriptor,
                                             Int                    maxBuckets   = DefaultMaxBuckets,
                                             Int                    maxScale     = DefaultScale,
                                             AggregationTemporality temporality  = DefaultAggregationTemporality,
                                             Boolean                recordMinMax = True)
        implements Aggregator, Collectable, MetricDefaults {

    construct(InstrumentDescriptor   descriptor,
              Int                    maxBuckets   = DefaultMaxBuckets,
              Int                    maxScale     = DefaultScale,
              AggregationTemporality temporality  = AggregationTemporality.Delta,
              Boolean                recordMinMax = True) {
        this.descriptor     = descriptor;
        this.maxBuckets     = maxBuckets;
        this.scale          = maxScale < -10 ? -10 : maxScale > 20 ? 20 : maxScale;
        this.temporality    = temporality;
        this.recordMinMax   = recordMinMax;
        this.startTimeNanos = MemoryClock.nowNanos();
        this.positiveCounts = new ecstasy.maps.ListMap();
        this.negativeCounts = new ecstasy.maps.ListMap();
    }

    private InstrumentDescriptor   descriptor;
    private Int                    scale;
    private Int                    maxBuckets;
    private AggregationTemporality temporality;
    private Boolean                recordMinMax;
    private UInt64                 startTimeNanos = 0;
    private UInt64                 count          = 0;
    private Float64?               sum            = Null;
    private Float64?               min            = Null;
    private Float64?               max            = Null;
    private UInt64                 zeroCount      = 0;
    private Map<Int, UInt64>       positiveCounts;
    private Map<Int, UInt64>       negativeCounts;
    private Int                    positiveMinIdx = Int.MaxValue;
    private Int                    positiveMaxIdx = Int.MinValue;
    private Int                    negativeMinIdx = Int.MaxValue;
    private Int                    negativeMaxIdx = Int.MinValue;

    static Int DefaultMaxBuckets = 160;
    static Int DefaultScale      = 20;

    @Override
    void record(NumberValue value, Attributes attributes) {
        Float64 fv = asFloat64(value);

        count += 1;
        sum    = (sum ?: 0.0) + fv;
        if (recordMinMax) {
            if (Float64 mn ?= min) { min = fv < mn ? fv : mn; } else { min = fv; }
            if (Float64 mx ?= max) { max = fv > mx ? fv : mx; } else { max = fv; }
        }

        if (fv > 0.0) {
            Int idx = bucketIndex(fv, scale);
            increment(positiveCounts, idx);
            if (idx < positiveMinIdx) { positiveMinIdx = idx; }
            if (idx > positiveMaxIdx) { positiveMaxIdx = idx; }
        } else if (fv < 0.0) {
            Int idx = bucketIndex(-fv, scale);
            increment(negativeCounts, idx);
            if (idx < negativeMinIdx) { negativeMinIdx = idx; }
            if (idx > negativeMaxIdx) { negativeMaxIdx = idx; }
        } else {
            zeroCount += 1;
        }

        while (positiveSpan() > maxBuckets || negativeSpan() > maxBuckets) {
            reduceScale();
        }
    }

    @Override
    conditional Metric collectMetric() {
        if (count == 0) {
            return False;
        }
        UInt64 endNanos = MemoryClock.nowNanos();
        Float64? reportMin = recordMinMax ? min : Null;
        Float64? reportMax = recordMinMax ? max : Null;
        var point = new ExponentialHistogramDataPoint(
            endNanos, count, scale, zeroCount,
            toBucketSet(positiveCounts),
            toBucketSet(negativeCounts),
            [], startTimeNanos, sum, reportMin, reportMax);

        if (temporality == AggregationTemporality.Delta) {
            count          = 0;
            sum            = Null;
            min            = Null;
            max            = Null;
            zeroCount      = 0;
            positiveCounts = new ecstasy.maps.ListMap();
            negativeCounts = new ecstasy.maps.ListMap();
            positiveMinIdx = Int.MaxValue;
            positiveMaxIdx = Int.MinValue;
            negativeMinIdx = Int.MaxValue;
            negativeMaxIdx = Int.MinValue;
            startTimeNanos = endNanos;
        }

        return True, new Metric(descriptor.name, descriptor.description ?: "",
                descriptor.unit ?: "1", new ExponentialHistogramData([point], temporality));
    }

    // ----- span helpers --------------------------------------------------------------------------

    private Int positiveSpan() = positiveCounts.empty ? 0 : positiveMaxIdx - positiveMinIdx + 1;
    private Int negativeSpan() = negativeCounts.empty ? 0 : negativeMaxIdx - negativeMinIdx + 1;

    // ----- bucket index --------------------------------------------------------------------------

    private static Int bucketIndex(Float64 value, Int scale) {
        UInt64 bits      = new UInt64(value.bits);
        Int    biasedExp = ((bits >> 52) & 0x7FF).toInt();
        UInt64 mantissa  = bits & 0x000F_FFFF_FFFF_FFFF;

        if (biasedExp == 0) {
            biasedExp = 1;
            mantissa  = 0;
        }
        Int exp = biasedExp - 1023;

        if (scale >= 0) {
            Int    topBits  = (mantissa >> (52 - scale)).toInt();
            UInt64 lowMask  = (UInt64:1 << (52 - scale)) - 1;
            Boolean exactPow = (mantissa & lowMask) == 0;
            return ((exp << scale) | topBits) - (exactPow ? 1 : 0);
        } else {
            Int    nshift   = -scale;
            Boolean exactPow = ((exp & ((1 << nshift) - 1)) == 0) && mantissa == 0;
            return (exp >> nshift) - (exactPow ? 1 : 0);
        }
    }

    // ----- scale reduction -----------------------------------------------------------------------

    private void reduceScale() {
        scale          -= 1;
        positiveCounts  = mergeBuckets(positiveCounts);
        negativeCounts  = mergeBuckets(negativeCounts);
        if (!positiveCounts.empty) {
            positiveMinIdx = positiveMinIdx >> 1;
            positiveMaxIdx = positiveMaxIdx >> 1;
        }
        if (!negativeCounts.empty) {
            negativeMinIdx = negativeMinIdx >> 1;
            negativeMaxIdx = negativeMaxIdx >> 1;
        }
    }

    private static Map<Int, UInt64> mergeBuckets(Map<Int, UInt64> buckets) {
        Map<Int, UInt64> merged = new ecstasy.maps.ListMap();
        for ((Int idx, UInt64 cnt) : buckets) {
            Int newIdx = idx >> 1;
            UInt64 prev;
            if (prev := merged.get(newIdx)) {
                merged.put(newIdx, prev + cnt);
            } else {
                merged.put(newIdx, cnt);
            }
        }
        return merged;
    }

    // ----- BucketSet construction ----------------------------------------------------------------

    private static BucketSet toBucketSet(Map<Int, UInt64> buckets) {
        if (buckets.empty) {
            return new BucketSet();
        }
        Int minIdx = Int.MaxValue;
        Int maxIdx = Int.MinValue;
        for (Int idx : buckets.keys) {
            if (idx < minIdx) { minIdx = idx; }
            if (idx > maxIdx) { maxIdx = idx; }
        }
        UInt64[] counts = new Array<UInt64>(maxIdx - minIdx + 1, 0);
        for ((Int idx, UInt64 cnt) : buckets) {
            counts[idx - minIdx] = cnt;
        }
        return new BucketSet(minIdx, counts);
    }

    // ----- helpers -------------------------------------------------------------------------------

    private void increment(Map<Int, UInt64> counts, Int idx) {
        UInt64 prev;
        if (prev := counts.get(idx)) {
            counts.put(idx, prev + 1);
        } else {
            counts.put(idx, 1);
        }
    }

    private static Float64 asFloat64(NumberValue value) =
        value.is(Float64) ? value.as(Float64) : value.as(Int).toFloat64();
}
