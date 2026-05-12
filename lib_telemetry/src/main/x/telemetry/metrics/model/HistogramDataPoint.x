/**
 * A single data point in a Histogram metric stream.
 *
 * Observations are grouped into explicit buckets. `explicitBounds` contains the N upper
 * bounds (exclusive of +Inf), and `bucketCounts` contains N+1 entries — the last entry
 * captures all values above the final bound. This invariant is enforced at construction.
 */
const HistogramDataPoint {
    construct(UInt64         timeUnixNano,
              UInt64         count,
              Float64[]      explicitBounds,
              UInt64[]       bucketCounts,
              Attributes attributes = [],
              UInt64?        startTimeUnixNano = Null,
              Float64?       sum               = Null,
              Float64?       min               = Null,
              Float64?       max               = Null,
              Exemplar[]     exemplars         = [],
              DataPointFlags flags             = DataPointFlags.Default) {
        assert:arg bucketCounts.size == explicitBounds.size + 1;
        this.timeUnixNano      = timeUnixNano;
        this.count             = count;
        this.explicitBounds    = explicitBounds;
        this.bucketCounts      = bucketCounts;
        this.attributes        = attributes;
        this.startTimeUnixNano = startTimeUnixNano;
        this.sum               = sum;
        this.min               = min;
        this.max               = max;
        this.exemplars         = exemplars;
        this.flags             = flags;
    }

    Attributes attributes;
    UInt64?        startTimeUnixNano;
    UInt64         timeUnixNano;
    UInt64         count;
    Float64?       sum;
    Float64?       min;
    Float64?       max;
    Float64[]      explicitBounds;
    UInt64[]       bucketCounts;
    Exemplar[]     exemplars;
    DataPointFlags flags;
}
