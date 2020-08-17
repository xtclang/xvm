/**
 * An implementation of Entry that can be used as a cursor over any number of keys, and
 * delegates back to the map for its functionality.
 */
class CursorEntry<MapKey, MapValue>
        extends ReifiedEntry<MapKey, MapValue>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Map<MapKey, MapValue> map)
        {
        construct ReifiedEntry(map);
        }

    construct(Map<MapKey, MapValue> map, MapKey key)
        {
        construct ReifiedEntry(map, key);
        }


    // ----- internal ------------------------------------------------------------------------------

    @Override
    @Unassigned public/protected MapKey key;

    /**
     * Specify the new "cursor key" for this Entry.
     *
     * @param key  the new key for this Entry
     *
     * @return this Entry
     */
    CursorEntry advance(MapKey key)
        {
        this.key = key;
        return this;
        }


    // ----- Entry interface -----------------------------------------------------------------------

    @Override
    Map<MapKey, MapValue>.Entry reify()
        {
        return new ReifiedEntry(map, key);
        }
    }
