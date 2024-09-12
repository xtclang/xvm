/**
 * Based on the [Collection] pattern using the [Aggregator], this class supports collection of keys
 * and values into a [Map] result.
 *
 * Ideal implementations will provide a very efficient "collector" `Map` from the [init] method that
 * collects contents in a suitable manner (e.g. order may be important, hashing may be possible,
 * etc.), and then use that collected content to produce the desired class and form of `Map` (the
 * "finished product") in the [reduce] method.
 *
 * The default implementation collects data directly into the same `Map` that is returned as the
 * "finished product".
 *
 * @param create  the optional [Map] factory function
 */
const MapCollector<Key, Value, Result extends Map<Key, Value>>
        (function Map<Key,Value>()? create = Null) {
    /**
     * Create the [Map] to use to collect the result.
     *
     * @return the [Map] to use to collect keys and values
     */
    Map<Key, Value> init(Int capacity = 0) = create?() : new ListMap(capacity);

    /**
     * Perform any necessary final transformation of the collector [Map].
     *
     * @param accumulator  the [Map] previously returned from [init]
     *
     * @return the final result
     */
    Result reduce(Map<Key, Value> accumulator) = accumulator.as(Result);
}