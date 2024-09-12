import Map.Entry;

/**
 * A simple implementation of [Entry] that delegates back to its originating [Map] on behalf of a
 * specific key.
 */
class KeyEntry<Key, Value>(Map<Key, Value> map, Key key)
        implements Entry<Key, Value>
        incorporates conditional StringableEntry<Key extends Stringable, Value extends Stringable> {
    // ----- Entry interface -----------------------------------------------------------------------

    @Override
    public/protected @Final Key key;

    @Override
    Boolean exists.get() {
        return map.contains(key);
    }

    @Override
    Value value {
        @Override
        Value get() {
            if (Value value := map.get(key)) {
                return value;
            }
            throw new OutOfBounds($"Entry does not exist: \"{key}\"");
        }

        @Override
        void set(Value value) {
            verifyInPlace();
            map.put(key, value);
        }
    }

    @Override
    void delete() {
        verifyInPlace();
        map.remove(key);
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The originating `Map`.
     */
    protected Map<Key, Value> map;

    /**
     * Verify that the containing `Map`'s mutability is non-persistent.
     *
     * @return `True` iff the `Map` supports [Map.inPlace] modification
     *
     * @throws ReadOnly iff the `Map` is not `inPlace==True`
     */
    protected Boolean verifyInPlace() {
        if (!map.inPlace) {
            throw new ReadOnly("Entry modification requires inPlace==True");
        }
        return True;
    }
}