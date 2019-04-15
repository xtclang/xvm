/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class KeyEntries<KeyType, ValueType>(Map<KeyType, ValueType> map)
        implements Set<Map<KeyType, ValueType>.Entry>
    {
    public/private Map<KeyType, ValueType> map;

    @Override
    Mutability mutability.get()
        {
        return Mutable;
        }

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
                    private CursorEntry<KeyType, ValueType> entry = new CursorEntry(map);
                    return true, entry.advance(key);
                    }

                return false;
                }
            };
        }

    @Override
    KeyEntries remove(Map<KeyType, ValueType>.Entry entry)
        {
        verifyMutable();
        map.remove(entry.key, entry.value);
        return this;
        }

    @Override
    (KeyEntries, Int) removeIf(
            function Boolean (Map<KeyType, ValueType>.Entry) shouldRemove)
        {
        verifyMutable();

        CursorEntry<KeyType, ValueType> entry = new CursorEntry(map);
        (_, Int removed) = map.keys.removeIf(key -> shouldRemove(entry.advance(key)));

        return this, removed;
        }

    @Override
    KeyEntries clear()
        {
        verifyMutable();
        map.clear();
        return this;
        }

    @Override
    Stream<Entry> stream()
        {
        TODO
        }

    @Override
    KeyEntries clone()
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
