/**
 * Based on the collection pattern using the [Aggregator], this class supports collection of keys
 * and values into `Map` results.
 *
 * @param create  the optional Map factory function
 */
const MapCollector<Key, Value, Result extends Map<Key, Value>>
        (function Map<Key,Value>()? create = Null) {
    /**
     * Create the Map to use to collect the result.
     *
     * @return the map to use to collect keys and values
     */
    Map<Key, Value> init(Int capacity = 0) = create?() : new ListMap(capacity);

    /**
     * Perform any necessary final transformation of the collector Map.
     *
     * @param accumulator  the Map previously returned from [init]
     *
     * @return the final result
     */
    Result reduce(Map<Key, Value> accumulator) = accumulator.as(Result);
}