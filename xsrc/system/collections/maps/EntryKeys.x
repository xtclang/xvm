/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class EntryKeys<KeyType, ValueType>(Map<KeyType, ValueType> map)
        implements Set<KeyType>
    {
    public/private Map<KeyType, ValueType> map;

    @Override
    Int size.get()
        {
        return map.size;
        }

    @Override
    Boolean empty.get()
        {
        return map.empty;
        }

    @Override
    Iterator<Map<KeyType, ValueType>.Entry> iterator()
        {
        return new Iterator()
            {
            Iterator<KeyType> keyIterator = map.keys.iterator(); // TODO verify this is a private prop

            @Override
            conditional Map<KeyType, ValueType>.Entry next()
                {
                if (KeyType key : keyIterator.next())
                    {
                    private CursorEntry<KeyType, ValueType> entry = new CursorEntry(map, key);
                    return true, entry.advance(key);
                    }

                return false;
                }
            };
        }

    @Override
    conditional EntryKeys remove(Map<KeyType, ValueType>.Entry entry)
        {
        verifyMutable();
        return map.remove(entry.key, entry.value), this;
        }

    @Override
    conditional EntryKeys removeIf(
            function Boolean (Map<KeyType, ValueType>.Entry) shouldRemove)
        {
        verifyMutable();

        CursorEntry<KeyType, ValueType>? entry = null;
        if (map.keys.removeIf(key ->
                {
                entry = entry?.advance(key) : new CursorEntry(map,key);
                return shouldRemove(entry.advance(key));
                }))
            {
            return true, this;
            }

        return false;
        }

    @Override
    conditional EntryKeys clear()
        {
        return verifyMutable() && map.clear(), this;
        }

    @Override
    Stream<Entry> stream()
        {
        TODO
        }

    @Override
    EntryKeys clone()
        {
        TODO
        }

    /**
     * Some operations require that the containing Map be Mutable; this method throws an exception
     * if the Map is not Mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not Mutable
     */
    protected Boolean verifyMutable()
        {
        if (map.mutability != Mutable)
            {
            throw new ReadOnly("Map operation requires mutability==Mutable");
            }
        return true;
        }
    }
