/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.entries].
 */
class EntryValues<KeyType, ValueType>(Map<KeyType, ValueType> map)
        implements Collection<ValueType>
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
    Iterator<ValueType> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<KeyType, ValueType>.Entry> entryIterator = map.entries.iterator();

            @Override
            conditional ValueType next()
                {
                if (Map<KeyType, ValueType>.Entry entry : entryIterator.next())
                    {
                    return True, entry.value;
                    }

                return False;
                }
            };
        }

    @Override
    conditional EntryValues remove(ValueType value)
        {
        verifyMutable();

        Boolean modified = map.entries.iterator().untilAny(entry ->
            {
            if (entry.value == value)
                {
                assert map.entries.remove(entry);
                return True;
                }
            return False;
            });

        return modified, this;
        }

    @Override
    conditional EntryValues removeIf(function Boolean (ValueType) shouldRemove)
        {
        return verifyMutable() && map.entries.removeIf(entry -> shouldRemove(entry.value)), this;
        }

    @Override
    conditional EntryValues clear()
        {
        return verifyMutable() && map.clear(), this;
        }

    @Override
    Stream<ValueType> stream()
        {
        TODO
        }

    @Override
    EntryValues clone()
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
