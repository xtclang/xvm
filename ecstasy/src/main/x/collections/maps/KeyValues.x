/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.keys].
 */
class KeyValues<MapKey, MapValue>(Map<MapKey, MapValue> contents)
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
            Iterator<MapKey> keyIterator = this.KeyValues.contents.keys.iterator();

            @Override
            conditional MapValue next()
                {
                if (MapKey key := keyIterator.next())
                    {
                    return this.KeyValues.contents.get(key);
                    }

                return False;
                }
            };
        }

    @Override
    KeyValues remove(MapValue value)
        {
        verifyInPlace();

        contents.keys.iterator().untilAny(key ->
            {
            if (MapValue test := contents.get(key))
                {
                if (test == value)
                    {
                    contents.remove(key);
                    return True;
                    }
                }
            return False;
            });

        return this;
        }

    @Override
    (KeyValues, Int) removeAll(function Boolean (MapValue) shouldRemove)
        {
        verifyInPlace();

        (_, Int removed) = contents.keys.removeAll(key ->
                {
                assert MapValue value := contents.get(key);
                return shouldRemove(value);
                });
        return this, removed;
        }

    @Override
    KeyValues clear()
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
