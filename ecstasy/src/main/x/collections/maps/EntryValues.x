/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.entries].
 */
class EntryValues<Key, Value>(Map<Key, Value> contents)
        implements Collection<Value>
    {
    public/private Map<Key, Value> contents;

    @Override
    Int size.get()
        {
        return contents.size;
        }

    @Override
    Boolean empty.get()
        {
        return contents.empty;
        }

    @Override
    Iterator<Value> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<Key, Value>.Entry> entryIterator = contents.entries.iterator();

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

        contents.entries.iterator().untilAny(entry ->
            {
            if (entry.value == value)
                {
                contents.entries.remove(entry);
                return True;
                }
            return False;
            });

        return this;
        }

    @Override
    (EntryValues, Int) removeAll(function Boolean (Value) shouldRemove)
        {
        verifyMutable();

        (_, Int removed) = contents.entries.removeAll(entry -> shouldRemove(entry.value));

        return this, removed;
        }

    @Override
    EntryValues clear()
        {
        verifyMutable();
        contents.clear();
        return this;
        }

    /**
     * Some operations require that the containing Map be mutable; this method throws an exception
     * if the Map is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not mutable
     */
    protected Boolean verifyMutable()
        {
        if (!contents.inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace == True");
            }
        return True;
        }
    }
