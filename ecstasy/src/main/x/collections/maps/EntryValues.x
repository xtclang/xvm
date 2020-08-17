/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.entries].
 */
class EntryValues<MapKey, MapValue>(Map<MapKey, MapValue> contents)
        implements Collection<MapValue>
    {
    public/private Map<MapKey, MapValue> contents;

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
    Iterator<MapValue> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<MapKey, MapValue>.Entry> entryIterator = contents.entries.iterator();

            @Override
            conditional MapValue next()
                {
                if (Map<MapKey, MapValue>.Entry entry := entryIterator.next())
                    {
                    return True, entry.value;
                    }

                return False;
                }
            };
        }

    @Override
    EntryValues remove(MapValue value)
        {
        verifyInPlace();

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
    (EntryValues, Int) removeAll(function Boolean (MapValue) shouldRemove)
        {
        verifyInPlace();

        (_, Int removed) = contents.entries.removeAll(entry -> shouldRemove(entry.value));

        return this, removed;
        }

    @Override
    EntryValues clear()
        {
        verifyInPlace();
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
    protected Boolean verifyInPlace()
        {
        if (!contents.inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace == True");
            }
        return True;
        }
    }
