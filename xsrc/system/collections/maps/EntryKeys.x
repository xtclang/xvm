/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class EntryKeys<KeyType, ValueType>(Map<KeyType, ValueType> map)
        implements Set<KeyType>
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
    Iterator<KeyType> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<KeyType, ValueType>.Entry> entryIterator = map.entries.iterator();

            @Override
            conditional KeyType next()
                {
                if (Map<KeyType, ValueType>.Entry entry : entryIterator.next())
                    {
                    return True, entry.key;
                    }

                return False;
                }
            };
        }

    @Override
    EntryKeys remove(KeyType key)
        {
        verifyMutable();
        map.remove(key);
        return this;
        }

    @Override
    (EntryKeys, Int) removeIf(function Boolean (KeyType) shouldRemove)
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
    Stream<KeyType> stream()
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
        return True;
        }
    }
