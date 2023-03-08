/**
 * A simple implementation of [Map.Entry] that delegates back to its originating map on behalf of a
 * specific key.
 */
mixin KeyEntry<Key, Value>
        into Map<Key, Value>.Entry
        incorporates conditional EntryStringer<Key extends Stringable, Value extends Stringable>
        // TODO EntryFreezer
    {
    /**
     * Construct a "cursor" KeyEntry.
     */
    construct()
        {
        this.reified = False;
        }

    /**
     * Construct a reified KeyEntry for the specified key of the specified map.
     *
     * @param key  the Key that this Entry represents
     */
    construct(Key key)
        {
        this.key     = key;
        this.reified = True;
        }

    @Override
    public/protected @Unassigned Key key;

    /**
     * Specifies whether this KeyEntry can be return by the 'reify' method.
     */
    protected Boolean reified;

    /**
     * The underlying map.
     */
    protected Map<Key, Value> map.get()
        {
        return outer.as(Map<Key, Value>);
        }


    // ----- KeyEntry API --------------------------------------------------------------------------

    /**
     * Specify the new "cursor key" for this Entry.
     *
     * @param key  the new key for this Entry
     *
     * @return this Entry
     */
    KeyEntry advance(Key key)
        {
        assert !reified;
        this.key = key;
        return this;
        }


    // ----- Entry interface -----------------------------------------------------------------------

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
            throw new OutOfBounds($"Entry missing: \"{key}\"");
            }

        @Override
        void set(Value value)
            {
            verifyInPlace();
            map.put(key, value);
            }
        }

    @Override
    void delete()
        {
        verifyInPlace();
        map.keys.remove(key);
        }

    @Override
    Map<Key, Value>.Entry reify()
        {
        return reified ? this : TODO("The incorporating class must implement 'reify' method");
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Verify that the containing Map's mutability is non-persistent.
     *
     * @return True iff the Map supports in-place modification
     *
     * @throws ReadOnly iff the Map does not support in-place modification
     */
    protected Boolean verifyInPlace()
        {
        if (!map.inPlace)
            {
            throw new ReadOnly("Map Entry modification requires inPlace==True");
            }
        return True;
        }
    }