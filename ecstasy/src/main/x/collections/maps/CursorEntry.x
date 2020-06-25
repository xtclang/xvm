/**
 * An implementation of Entry that can be used as a cursor over any number of keys, and
 * delegates back to the map for its functionality.
 */
class CursorEntry<Key, Value>
        extends ReifiedEntry<Key, Value>
    {
    construct(Map<Key, Value> map)
        {
        construct ReifiedEntry(map);
        }

    construct(Map<Key, Value> map, Key key)
        {
        construct ReifiedEntry(map, key);
        }

    /**
     * Specify the new "cursor key" for this Entry.
     *
     * @param key  the new key for this Entry
     *
     * @return this Entry
     */
    CursorEntry advance(Key key)
        {
        this.key = key;
        return this;
        }

    @Override
    Map<Key, Value>.Entry reify()
        {
        return new ReifiedEntry(map, key);
        }
    }
