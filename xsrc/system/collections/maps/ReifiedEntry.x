/**
 * An implementation of Map Entry that delegates back to its originating map on behalf of a
 * specific key.
 */
class ReifiedEntry<KeyType, ValueType>(Map<KeyType, ValueType> map, KeyType key)
        implements Map<KeyType, ValueType>.Entry
    {
    protected/private Map<KeyType, ValueType> map;

    @Override
    public/protected KeyType key;

    @Override
    Boolean exists.get()
        {
        return map.contains(key);
        }

    @Override
    ValueType value
        {
        @Override
        ValueType get()
            {
            if (ValueType value : map.get(key))
                {
                return value;
                }
            throw new OutOfBounds();
            }

        @Override
        void set(ValueType value)
            {
            map.put(key, value);
            }
        }

    @Override
    void remove()
        {
        map.keys.remove(key);
        }
    }
