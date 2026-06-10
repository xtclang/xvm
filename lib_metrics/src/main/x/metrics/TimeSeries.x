import ecstasy.collections.Aggregator;

/**
 * A bounded, time-bucketed rolling window of values.
 *
 * Samples are added via [add] with an explicit timestamp; each lands in a fixed-duration bucket of
 * size [resolution] aligned to the epoch. The container retains a rolling window of length
 * [retention]; once full, the oldest bucket is overwritten as time advances.
 *
 * Within a single bucket the latest sample wins (snapshot semantics) — to fold values across
 * buckets at coarser rates, pass an [Aggregator] to [query].
 *
 * Example: a "requests per minute" counter sampled every minute, retained for one day.
 *
 *     TimeSeries<Int> rpm = new TimeSeries(Minute, Day);
 *     rpm.add(clock.now, currentRequestCount);
 *     ...
 *     Int?[] lastHour    = rpm.query(Minute, 60);                  // raw per-minute values
 *     Int?[] perHourSums = rpm.query(Hour,   24, new agg.Sum());   // hourly sums
 */
class TimeSeries<Value> {
    construct(Duration resolution, Duration retention) {
        assert retention >= resolution as "retention must be at not smaller than resolution";

        this.resolution = resolution;
        this.capacity   = (retention / resolution).toInt64();
        this.buckets    = new Bucket<Value>?[capacity];
    }

    /**
     * The smallest unit of time over which samples are aggregated.
     */
    public/private Duration resolution;

    /**
     * The number of consecutive buckets retained.
     */
    public/private Int capacity;

    /**
     * The ring buffer of buckets. A `Null` slot means no sample landed in that bucket within the
     * currently retained window.
     */
    private Bucket<Value>?[] buckets;

    /**
     * The absolute index of the most recently touched bucket, or `-1` before any [add].
     */
    private Int newestIndex = -1;

    /**
     * Place the specified [value] into the bucket containing the [timestamp]. Within a bucket,
     * last-write-wins. Values older than the retained window are silently dropped.
     */
    void add(Time timestamp, Value value) {
        Int index = indexOf(timestamp);

        if (index > newestIndex) {
            Int firstStale = (newestIndex + 1).notLessThan(index - capacity + 1);
            for (Int i : firstStale..<index) {
                buckets[i % capacity] = Null;
            }
            buckets[index % capacity] = new Bucket<Value>(index, value);
            newestIndex = index;
            return;
        }

        if (index <= newestIndex - capacity) {
            return;
        }

        buckets[index % capacity] = createBucket(index, value);
    }

    /**
     * Return [count] aggregated values in chronological order (oldest first) ending at the bucket
     * containing [endTime] (or, when [endTime] is `Null`, at the most recently touched bucket).
     *
     * The [rate] must be a multiple of [resolution]; each returned slot spans `rate/resolution`
     * underlying buckets. When [folder] is `Null`, the slot's value is the most recent non-Null
     * bucket in its window (snapshot semantics). When [folder] is provided, the non-null bucket
     * values in each window are folded via that aggregator. A slot is `Null` when none of its
     * underlying buckets received any sample.
     */
    Value?[] query(Duration rate, Int count, Time? endTime = Null,
                   Aggregator<Value, Value>? folder = Null) {
        assert:arg count > 0 as "count must be positive";
        assert:arg rate >= resolution as "the rate ({rate}) must be at least the resolution ({resolution})";

        Value?[] values = new Value?[count];
        if (newestIndex < 0) {
            return values;
        }

        Int bucketsPerEntry = (rate / resolution).toInt64();
        Int endIndex        = endTime == Null ? newestIndex : indexOf(endTime);

        for (Int i : 0..<count) {
            Int hi = endIndex - (count - 1 - i) * bucketsPerEntry;
            Int lo = hi - bucketsPerEntry + 1;
            values[i] = foldRange(lo, hi, folder);
        }
        return values;
    }

    /**
     * Fold the buckets in a `lo..hi` range into a single value, or `Null` if no bucket in the range
     * holds a sample.
     */
    private Value? foldRange(Int lo, Int hi, Aggregator<Value, Value>? folder) {
        lo = lo.notLessThan(newestIndex - capacity + 1);
        hi = hi.minOf(newestIndex);
        if (lo > hi) {
            return Null;
        }

        if (Aggregator<Value, Value> agg ?= folder) {
            Appender<Value> acc = agg.init(hi - lo + 1);
            Boolean         any = False;
            for (Int i : lo..hi) {
                if (Bucket<Value> b ?= buckets[i % capacity], b.index == i) {
                    acc.add(b.value);
                    any = True;
                }
            }
            return any ? agg.reduce(acc) : Null;
        } else {
            Value? last = Null;
            for (Int i : lo..hi) {
                if (Bucket<Value> b ?= buckets[i % capacity], b.index == i) {
                    last = b.value;
                }
            }
            return last;
        }
    }

    /**
     * The absolute bucket index containing the given timestamp — a monotonic count of
     * `resolution`-sized intervals since the Unix epoch. Used for window math and ordering
     * comparisons; the value grows unboundedly with wall-clock time.
     */
    private Int indexOf(Time timestamp) =
            (timestamp.epochPicos / resolution.picoseconds.toInt128()).toInt64();

    /**
     * Map a timestamp to its ring-buffer slot in `0..<capacity` range.
     */
    private Int bucketOf(Time timestamp) = indexOf(timestamp) % capacity;

    /**
     * Subclassing support.
     *
     * Note: we could use a virtual child const instead if the TimeSeries were a service.
     */
    protected Bucket<Value> createBucket(Int index, Value value) = new Bucket(index, value);

    /**
     * A single bucket.
     *
     * @param index  the absolute index of this bucket
     * @param value  the value (possibly aggregated)
     */
    static const Bucket<Value>(Int index, Value value);
}
