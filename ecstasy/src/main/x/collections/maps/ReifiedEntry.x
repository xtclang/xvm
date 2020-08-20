/**
 * A simple implementation of [Map.Entry] that delegates back to its originating map on behalf of a
 * specific key.
 */
class ReifiedEntry<MapKey, MapValue>
        implements Map<MapKey, MapValue>.Entry
        incorporates conditional EntryStringer<MapKey extends Stringable, MapValue extends Stringable>
        // TODO EntryFreezer
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ReifiedEntry for the specified key of the specified map.
     *
     * @param map  the Map that this Entry will act as a part of
     * @param key  the Key that this Entry represents
     */
    construct(Map<MapKey, MapValue> map, MapKey key)
        {
        this.map = map;
        this.key = key;
        }

    /**
     * A constructor designed for sub-classes, such as the [CursorEntry].
     *
     * @param map  the Map that this Entry will act as a part of
     */
    protected construct(Map<MapKey, MapValue> map)
        {
        this.map = map;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The Map that "contains" this entry.
     */
    protected/private Map<MapKey, MapValue> map;

    /**
     * Verify that the containing Map's mutability is non-persistent.
     *
     * @return True iff the Map supports in-place modification
     *
     * @throws ReadOnly iff the Map does not support in-place modification
     */
    protected Boolean verifyInPlace()
        {
        if (!map.inPlace)
            {
            throw new ReadOnly("Map Entry modification requires inPlace==True");
            }
        return True;
        }


    // ----- Entry interface -----------------------------------------------------------------------

    @Override
    public/protected MapKey key;

    @Override
    Boolean exists.get()
        {
        return map.contains(key);
        }

    @Override
    MapValue value
        {
        @Override
        MapValue get()
            {
            if (MapValue value := map.get(key))
                {
                return value;
                }
            throw new OutOfBounds();
            }

        @Override
        void set(MapValue value)
            {
            verifyInPlace();
            map.put(key, value);
            }
        }

    @Override
    void delete()
        {
        verifyInPlace();
        map.keys.remove(key);
        }
    }
