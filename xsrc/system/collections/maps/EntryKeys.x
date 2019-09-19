/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class EntryKeys<Key, Value>(Map<Key, Value> map)
        implements Set<Key>
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
    Iterator<Key> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<Key, Value>.Entry> entryIterator = map.entries.iterator();

            @Override
            conditional Key next()
                {
                if (Map<Key, Value>.Entry entry := entryIterator.next())
                    {
                    return True, entry.key;
                    }

                return False;
                }
            };
        }

    @Override
    EntryKeys remove(Key key)
        {
        verifyMutable();
        map.remove(key);
        return this;
        }

    @Override
    (EntryKeys, Int) removeIf(function Boolean (Key) shouldRemove)
        {
        verifyMutable();

        (_, Int removed) = map.entries.removeIf(entry -> shouldRemove(entry.key));
        return this, removed;
        }

    @Override
    EntryKeys clear()
        {
        verifyMutable();
        map.clear();
        return this;
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
        return True;
        }
    }
