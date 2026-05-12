/**
 * A dense set of bucket counts for an ExponentialHistogram.
 *
 * `offset` is the signed index of the first occupied bucket. The bucket at position `i`
 * in the `counts` array has index `offset + i` and covers the range
 * (base^(offset+i), base^(offset+i+1)] where base = 2^(2^(-scale)).
 */
const BucketSet(Int      offset = 0,
                UInt64[] counts = []) {}
