/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.keys].
 */
class KeyValues<Key, Value>(Map<Key, Value> contents)
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
            Iterator<Key> keyIterator = this.KeyValues.contents.keys.iterator();

            @Override
            conditional Value next()
                {
                if (Key key := keyIterator.next())
                    {
                    return this.KeyValues.contents.get(key);
                    }

                return False;
                }
            };
        }

    @Override
    KeyValues remove(Value value)
        {
        verifyMutable();

        contents.keys.iterator().untilAny(key ->
            {
            if (Value test := contents.get(key))
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
    (KeyValues, Int) removeAll(function Boolean (Value) shouldRemove)
        {
        verifyMutable();

        (_, Int removed) = contents.keys.removeAll(key ->
                {
                assert Value value := contents.get(key);
                return shouldRemove(value);
                });
        return this, removed;
        }

    @Override
    KeyValues clear()
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
