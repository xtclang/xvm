/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.keys].
 */
class KeyValues<KeyType, ValueType>(Map<KeyType, ValueType> map)
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
            Iterator<KeyType> keyIterator = map.keys.iterator();

            @Override
            conditional ValueType next()
                {
                if (KeyType key : keyIterator.next())
                    {
                    return map.get(key);
                    }

                return false;
                }
            };
        }

    @Override
    conditional KeyValues remove(ValueType value)
        {
        verifyMutable();

        Boolean modified = map.keys.iterator().untilAny(key ->
            {
            if (ValueType test : map.get(key))
                {
                if (test == value)
                    {
                    assert map.remove(key);
                    return true;
                    }
                }
            return false;
            });

        return modified, this;
        }

    @Override
    conditional KeyValues removeIf(function Boolean (ValueType) shouldRemove)
        {
        verifyMutable();

        return map.keys.removeIf(key ->
                {
                assert ValueType value : map.get(key);
                return shouldRemove(value);
                }), this;
        }

    @Override
    conditional KeyValues clear()
        {
        return verifyMutable() && map.clear(), this;
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
