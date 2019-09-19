/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.entries].
 */
class EntryValues<Key, Value>(Map<Key, Value> map)
        implements Collection<Value>
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
    Iterator<Value> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<Key, Value>.Entry> entryIterator = map.entries.iterator();

            @Override
            conditional Value next()
                {
                if (Map<Key, Value>.Entry entry := entryIterator.next())
                    {
                    return True, entry.value;
                    }

                return False;
                }
            };
        }

    @Override
    EntryValues remove(Value value)
        {
        verifyMutable();

        map.entries.iterator().untilAny(entry ->
            {
            if (entry.value == value)
                {
                map.entries.remove(entry);
                return True;
                }
            return False;
            });

        return this;
        }

    @Override
    (EntryValues, Int) removeIf(function Boolean (Value) shouldRemove)
        {
        verifyMutable();

        (_, Int removed) = map.entries.removeIf(entry -> shouldRemove(entry.value));

        return this, removed;
        }

    @Override
    EntryValues clear()
        {
        verifyMutable();
        map.clear();
        return this;
        }

    @Override
    Stream<Value> stream()
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
