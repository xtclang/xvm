/**
 * A `MapAppender` is a subset of the [Map] interface the represents the ability to append to a
 * `Map`. This interface is the equivalent of the [Appender] interface as it is used with the
 * various [Collection]-based interfaces, but tailored to `Map` instead.
 */
interface MapAppender<Key, Value> {
    /**
     * Store a mapping of the specified key to the specified value.
     *
     * @param key    the key to store
     * @param value  the value to associate with the specified key
     *
     * @return the resulting `MapAppender` (usually `this`)
     */
     MapAppender put(Key key, Value value);

    /**
     * Store all of the mappings of the specified keys to the specified values.
     *
     * @param that  a [Map] containing keys and associated values to add to this `MapAppender`
     *
     * @return the resulting `MapAppender` (usually `this`)
     */
    @Concurrent
    MapAppender putAll(Map<Key, Value> that) {
        MapAppender result = this;
        for ((Key key, Value value) : that) {
            result = result.put(key, value);
        }
        return result;
    }
}