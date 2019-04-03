/**
 * An implementation of Entry that can be used as a cursor over any number of keys, and
 * delegates back to the map for its functionality.
 */
class CursorEntry<KeyType, ValueType>
        extends ReifiedEntry<KeyType, ValueType>
    {
    construct(Map<KeyType, ValueType> map, KeyType key)
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
    CursorEntry advance(KeyType key)
        {
        this.key = key;
        return this;
        }

    @Override
    Entry reify()
        {
        return new ReifiedEntry(map, key);
        }
    }
