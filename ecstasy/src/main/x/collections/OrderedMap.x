/**
 * An OrderedMap is an extension to the Map interface that exposes capabilities that are dependent
 * on an ordering of the keys (the entries) in the Map.
 */
interface OrderedMap<Key extends Orderable, Value>
        extends Map<Key, Value>
        extends Sliceable<Key>
    {
    /**
     * An Orderer is a function that compares two keys for order.
     */
    typedef function Ordered (Key, Key) Orderer;

    /**
     * The Orderer used by this Map, if any. `Null` indicates that the Key's natural ordering is
     * used by the OrderedMap.
     */
    @RO Orderer? orderer;

    /**
     * Obtain the first key in the OrderedMap.
     *
     * @return the True iff the Map is not empty
     * @return (conditional) the first key in the OrderedMap
     */
    conditional Key first();

    /**
     * Obtain the last key in the OrderedMap.
     *
     * @return the True iff the Map is not empty
     * @return (conditional) the last key in the OrderedMap
     */
    conditional Key last();

    /**
     * Obtain the key that comes immediately after the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes after the specified key
     * @return (conditional) the next key
     */
    conditional Key next(Key key);

    /**
     * Obtain the key that comes immediately before the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes before the specified key
     * @return (conditional) the previous key
     */
    conditional Key prev(Key key);

    /**
     * Obtain the key that comes at or immediately after the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes at or after the specified
     *         key
     * @return (conditional) the key that was passed in, if it exists in the Map, otherwise the
     *         [next] key
     */
    conditional Key ceiling(Key key);

    /**
     * Obtain the key that comes at or immediately before the specified key in the Map.
     *
     * @param key  a key that may _or may not be_ already present in the Map
     *
     * @return the True iff the Map is not empty and has a key that comes at or before the specified
     *         key
     * @return (conditional) the key that was passed in, if it exists in the Map, otherwise the
     *         [prev] key
     */
    conditional Key floor(Key key);
    }
