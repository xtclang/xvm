/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class EntryKeys<KeyType, ValueType>(Map<KeyType, ValueType> map)
        implements Set<KeyType>
    {
    public/private Map<KeyType, ValueType> map;

    @Override
    MutabilityConstraint mutability.get()
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
            Iterator<Map<KeyType, ValueType>.Entry> entryIterator = map.entries.iterator(); // TODO verify this is a private prop

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
    // TODO GG this @Override signature (and others like it) didn't cause the compiler to complain
    // conditional EntryKeys remove(Map<KeyType, ValueType>.Entry entry)
    conditional EntryKeys remove(KeyType key)
        {
        return verifyMutable() && map.remove(key), this;
        }

    @Override
    conditional EntryKeys removeIf(function Boolean (KeyType) shouldRemove)
        {
        verifyMutable();

        if (map.entries.removeIf(entry ->
                {
                return shouldRemove(entry.key);
                }))
            {
            return True, this;
            }

        return False;
        }

    @Override
    conditional EntryKeys clear()
        {
        return verifyMutable() && map.clear(), this;
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
