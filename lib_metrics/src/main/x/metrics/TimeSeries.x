import ecstasy.collections.Aggregator;

/**
 * A bounded, time-bucketed rolling window of values. The [Value] type is either known to have a
 * default value or the default value must be specified.
 *
 * Samples are added via [add] with an explicit timestamp. The container retains a rolling window of
 * the size that covers the [retention] time period; once full, the oldest bucket is overwritten as
 * time advances.
 *
 * Example: a "requests per minute" counter sampled every minute, retained for one day.
 *
 *     TimeSeries<Int> rpm = new TimeSeries(Minute, Day);
 *     rpm.add(clock.now, currentRequestCount);
 *     ...
 *     Int[] lastHour    = rpm.query(Minute, 60);                  // raw per-minute values
 *     Int[] perHourSums = rpm.query(Hour,   24, new agg.Sum());   // hourly sums
 */
class TimeSeries<Value> {
    @Inject Clock clock;

    construct(Duration resolution, Duration retention, Value? defaultValue = Null) {
        assert retention >= resolution as "retention must be not smaller than resolution";

        this.resolution = resolution;
        this.capacity   = (retention / resolution).toInt64();

        Value unit;
        if (defaultValue == Null && !Value.is(Type<Nullable>)) {
            if (Class clazz  := Value.fromClass(),
                      unit   := clazz.defaultValue()) {} else {
                throw new IllegalArgument("defaultValue must be specified");
            }
        } else {
            unit = defaultValue.as(Value);
        }
        this.values       = new Value[capacity](unit);
        this.defaultValue = unit;
    }

    /**
     * The smallest unit of time over which samples are aggregated.
     */
    public/private Duration resolution;

    /**
     * The number of consecutive samples retained.
     */
    public/private Int capacity;

    /**
     * The default value to use for "missing" slots.
     */
    public/private Value defaultValue;

    /**
     * The time representing the end of the retained samples window.
     */
    Time endTime.get() = newestIndex < 0
            ? clock.now
            : new Time(resolution.picoseconds * newestIndex);

    /**
     * The ring buffer of sample values. A slot for which no sample exists within the currently
     * retained window holds the `defaultValue`.
     */
    private Value[] values;

    /**
     * The absolute index of the most recently touched sample, or `-1` before any [add].
     */
    private Int newestIndex = -1;

    /**
     * Place the specified value into the slot for the `timestamp`. For the same timestamp slot
     * the last sample value wins. Values older than the retained window are silently dropped.
     */
    void add(Time timestamp, Value value) {
        Int index = indexOf(timestamp);

        if (index > newestIndex) {
            Int firstStale = (newestIndex + 1).notLessThan(index - capacity + 1);
            for (Int i : firstStale..<index) {
                values[i % capacity] = defaultValue;
            }
            values[index % capacity] = value;
            newestIndex = index;
            return;
        }

        if (index <= newestIndex - capacity) {
            return;
        }

        values[index % capacity] = value;
    }

    /**
     * Collect up to [count] aggregated values in chronological order (oldest first) ending at the
     * index for [endTime] (or, when [endTime] is `Null`, at the most recently touched sample).
     *
     * The [rate] should be greater than the [resolution]; each returned slot spans `rate/resolution`
     * underlying samples. When [folder] is `Null`, the slot's value is the most recent non-default
     * value in its window (snapshot semantics). When [folder] is provided, the values in each
     * window are folded via that aggregator. A slot value is equal to `defaultValue` when none of
     * its underlying samples received any value.
     *
     * @return the array of no more than `count` values
     * @return the timestamp of the oldest sample
     */
    (immutable Value[] data, Time oldest) query(
            Duration rate, Int count, Aggregator<Value, Value>? folder = Null, Time? endTime = Null) {
        assert:arg count > 0 as "count must be positive";
        assert:arg rate >= resolution as "the rate ({rate}) must be at least the resolution ({resolution})";

        Int valuesPerEntry = (rate / resolution).toInt64();
        Int endIndex       = endTime == Null ? newestIndex : indexOf(endTime).minOf(newestIndex);

        if (newestIndex < 0) {
            return [], new Time(0);
        }

        count = count.minOf(capacity/valuesPerEntry);

        Value[] data = new Value[count](defaultValue);
        for (Int i : 0..<count) {
            Int hi = endIndex - (count - 1 - i) * valuesPerEntry;
            Int lo = hi - valuesPerEntry + 1;
            data[i] = foldRange(lo, hi, folder);
        }
        return data.makeImmutable(), new Time(resolution.picoseconds * (newestIndex - count));
    }

    /**
     * Fold the values in a `lo..hi` range into a single value, or `defaultValue` if no slot in the
     * range holds a sample.
     */
    private Value foldRange(Int lo, Int hi, Aggregator<Value, Value>? folder) {
        lo = lo.notLessThan(newestIndex - capacity + 1);
        hi = hi.minOf(newestIndex);
        if (lo > hi) {
            return defaultValue;
        }

        if (Aggregator<Value, Value> agg ?= folder) {
            Appender<Value> acc = agg.init(hi - lo + 1);
            Boolean         any = False;
            for (Int i : lo..hi) {
                Value v = values[i % capacity];
                if (v != defaultValue) {
                    acc.add(v);
                    any = True;
                }
            }
            return any ? agg.reduce(acc) : defaultValue;
        } else {
            // get the latest non-default value
            for (Int i : hi..lo) {
                Value v = values[i % capacity];
                if (v != defaultValue) {
                    return v;
                }
            }
            return defaultValue;
        }
    }

    /**
     * The absolute index for the given timestamp — a monotonic count of `resolution`-sized
     * intervals since the Unix epoch. Used for window math and ordering comparisons; the value
     * grows unboundedly with wall-clock time.
     */
    private Int indexOf(Time timestamp) =
            (timestamp.epochPicos / resolution.picoseconds.toInt128()).toInt64();
}
