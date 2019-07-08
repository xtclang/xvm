/**
 * An implementation of Map Entry that delegates back to its originating map on behalf of a
 * specific key.
 */
class ReifiedEntry<KeyType, ValueType>
        implements Map<KeyType, ValueType>.Entry
        incorporates Stringer
    {
    public construct(Map<KeyType, ValueType> map, KeyType key)
        {
        this.map = map;
        this.key = key;
        }

    protected construct(Map<KeyType, ValueType> map)
        {
        this.map = map;
        }

    protected/private Map<KeyType, ValueType> map;

    @Override
    @Unassigned
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
            if (ValueType value := map.get(key))
                {
                return value;
                }
            throw new OutOfBounds();
            }

        @Override
        void set(ValueType value)
            {
            verifyNotPersistent();
            map.put(key, value);
            }
        }

    @Override
    void remove()
        {
        map.keys.remove(key);
        }

    /**
     * Verify that the containing Map's mutability is non-persistent.
     *
     * @return True
     *
     * @throws ReadOnly if the Map's mutability is persistent
     */
    protected Boolean verifyNotPersistent()
        {
        if (map.mutability.persistent)
            {
            throw new ReadOnly("Map operation requires mutability.persistent==False");
            }
        return True;
        }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return estimateStringLength(key) + 1 + estimateStringLength(value);

        static Int estimateStringLength(Object o)
            {
            return o.is(Stringable)
                ? o.estimateStringLength()
                : 8; // completely arbitrary estimate
            }
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        if (key.is(Stringable))
            {
            // TODO CP remove ".as(Stringable)"
            key.as(Stringable).appendTo(appender);
            }
        else
            {
            appender.add(key.toString());
            }

        appender.add('=');

        if (value.is(Stringable))
            {
            value.as(Stringable).appendTo(appender);
            }
        else
            {
            appender.add(value.toString());
            }
        }
    }
