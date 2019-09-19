/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class KeyEntries<Key, Value>(Map<Key, Value> map)
        implements Set<Map<Key, Value>.Entry>
    {
    public/private Map<Key, Value> map;

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
    Iterator<Map<Key, Value>.Entry> iterator()
        {
        return new Iterator()
            {
            Iterator<Key> keyIterator = map.keys.iterator();

            @Override
            conditional Map<Key, Value>.Entry next()
                {
                if (Key key := keyIterator.next())
                    {
                    private CursorEntry<Key, Value> entry = new CursorEntry(map);
                    return true, entry.advance(key);
                    }

                return false;
                }
            };
        }

    @Override
    KeyEntries remove(Map<Key, Value>.Entry entry)
        {
        verifyMutable();
        map.remove(entry.key, entry.value);
        return this;
        }

    @Override
    (KeyEntries, Int) removeIf(
            function Boolean (Map<Key, Value>.Entry) shouldRemove)
        {
        verifyMutable();

        CursorEntry<Key, Value> entry = new CursorEntry(map);
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
