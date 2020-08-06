/**
 * An implementation of Map Entry that delegates back to its originating map on behalf of a
 * specific key.
 */
class ReifiedEntry<Key, Value>
        implements Map<Key, Value>.Entry
        incorporates text.Stringer
    {
    public construct(Map<Key, Value> map, Key key)
        {
        this.map = map;
        this.key = key;
        }

    protected construct(Map<Key, Value> map)
        {
        this.map = map;
        }

    protected/private Map<Key, Value> map;

    @Override
    @Unassigned
    public/protected Key key;

    @Override
    Boolean exists.get()
        {
        return map.contains(key);
        }

    @Override
    Value value
        {
        @Override
        Value get()
            {
            if (Value value := map.get(key))
                {
                return value;
                }
            throw new OutOfBounds();
            }

        @Override
        void set(Value value)
            {
            verifyNotPersistent();
            map.put(key, value);
            }
        }

    @Override
    void delete()
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
        if (!map.inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace==True");
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
    Appender<Char> appendTo(Appender<Char> buf)
        {
        if (key.is(Stringable))
            {
            // TODO CP remove ".as(Stringable)"
            key.as(Stringable).appendTo(buf);
            }
        else
            {
            buf.addAll(key.toString());
            }

        buf.add('=');

        return value.is(Stringable)
                ? value.as(Stringable).appendTo(buf)
                : buf.addAll(value.toString());
        }
    }
