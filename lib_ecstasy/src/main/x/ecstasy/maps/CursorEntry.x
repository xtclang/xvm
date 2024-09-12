import Map.Entry;

/**
 * A simple implementation of [Entry] that delegates back to its originating [Map] on behalf of a
 * specific key.
 */
class CursorEntry<Key, Value>
        implements Entry<Key, Value>
        incorporates conditional StringableEntry<Key extends Stringable, Value extends Stringable> {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `CursorEntry` that has not yet been primed with a `Key`.
     *
     * @param map  the containing `Map`
     */
    construct (Map<Key, Value> map) {
        this.map = map;
    }

    /**
     * Construct a `CursorEntry` for the specified `Key`.
     *
     * @param map  the containing `Map`
     * @param key  the `Entry`'s `Key`
     */
    construct (Map<Key, Value> map, Key key) {
        this.map = map;
        this.key = key;
    }

    // ----- Entry interface -----------------------------------------------------------------------

    @Override
    public/protected @Unassigned Key key;

    @Override
    Boolean exists.get() = map.contains(key);

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

    @Override
    Entry<Key, Value> reify() = new KeyEntry(map, key);

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The originating map.
     */
    protected Map<Key, Value> map;

    /**
     * Advance the cursor to the next key.
     *
     * @param key  the new `Entry` key in the `Map` to advance to
     *
     * @return this `CursorEntry`
     */
    public CursorEntry advance(Key key) {
        this.key = key;
        return this;
    }

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