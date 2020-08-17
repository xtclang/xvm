/**
 * An implementation of the Set for the [Map.keys] property that delegates back to the map and
 * to the map's [Map.entries] collection.
 */
class EntryKeys<MapKey, MapValue>(Map<MapKey, MapValue> contents)
        implements Set<MapKey>
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
    Iterator<MapKey> iterator()
        {
        return new Iterator()
            {
            Iterator<Map<MapKey, MapValue>.Entry> entryIterator = contents.entries.iterator();

            @Override
            conditional MapKey next()
                {
                if (Map<MapKey, MapValue>.Entry entry := entryIterator.next())
                    {
                    return True, entry.key;
                    }

                return False;
                }
            };
        }

    @Override
    EntryKeys remove(MapKey key)
        {
        verifyInPlace();
        contents.remove(key);
        return this;
        }

    @Override
    (EntryKeys, Int) removeAll(function Boolean (MapKey) shouldRemove)
        {
        verifyInPlace();

        (_, Int removed) = contents.entries.removeAll(entry -> shouldRemove(entry.key));
        return this, removed;
        }

    @Override
    EntryKeys clear()
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
