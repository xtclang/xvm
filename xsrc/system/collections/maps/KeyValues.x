/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.keys].
 */
class KeyValues<Key, Value>(Map<Key, Value> map)
        implements Collection<Value>
    {
    public/private Map<Key, Value> map;

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
            Iterator<Key> keyIterator = map.keys.iterator();

            @Override
            conditional Value next()
                {
                if (Key key := keyIterator.next())
                    {
                    return map.get(key);
                    }

                return False;
                }
            };
        }

    @Override
    KeyValues remove(Value value)
        {
        verifyMutable();

        map.keys.iterator().untilAny(key ->
            {
            if (Value test := map.get(key))
                {
                if (test == value)
                    {
                    map.remove(key);
                    return True;
                    }
                }
            return False;
            });

        return this;
        }

    @Override
    (KeyValues, Int) removeIf(function Boolean (Value) shouldRemove)
        {
        verifyMutable();

        (_, Int removed) = map.keys.removeIf(key ->
                {
                assert Value value := map.get(key);
                return shouldRemove(value);
                });
        return this, removed;
        }

    @Override
    KeyValues clear()
        {
        verifyMutable();
        map.clear();
        return this;
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
