/**
 * A single data point in an ExponentialHistogram metric stream.
 *
 * Buckets use exponential boundaries: base = 2^(2^(-scale)). Larger scale values give
 * more buckets and finer precision; negative scale values coarsen the buckets. Positive
 * and negative observations are tracked in separate [BucketSet]s. Values within
 * [-zeroThreshold, +zeroThreshold] are counted in `zeroCount`.
 */
const ExponentialHistogramDataPoint(UInt64         timeUnixNano,
                                    UInt64         count,
                                    Int            scale,
                                    UInt64         zeroCount,
                                    BucketSet      positive,
                                    BucketSet      negative,
                                    Attributes attributes = [],
                                    UInt64?        startTimeUnixNano = Null,
                                    Float64?       sum               = Null,
                                    Float64?       min               = Null,
                                    Float64?       max               = Null,
                                    Float64        zeroThreshold     = 0.0,
                                    Exemplar[]     exemplars         = [],
                                    DataPointFlags flags             = DataPointFlags.Default) {}
