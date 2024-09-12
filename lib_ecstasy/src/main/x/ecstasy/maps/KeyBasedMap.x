/**
 * An abstract base class for [Map] implementations that are key-based. For a read-only `Map`, the
 * only methods which must be implemented are [get] and [iterateKeys], although others (like [size],
 * [empty], and [contains]) must often be implemented for performance reasons. For a minimal
 * read/write `Map`, the [put] and [remove] methods must also be implemented.
 */
@Abstract class KeyBasedMap<Key, Value>
        implements Map<Key, Value> {

    @Override
    conditional Int knownSize() = False;

    @Override
    @RO Int size.get() {
        // sub-classes concerned with performance should implement this method efficiently
        return keyIterator().count();
    }

    @Override
    @RO Boolean empty.get() {
        // sub-classes concerned with performance should implement this method efficiently
        using (val iter = iterator()) {
            return !iter.next();
        }
    }

    @Override
    @Abstract conditional Value get(Key key);

    @Override
    Iterator<Entry<Key, Value>> iterator() {
        return new EntryIterator(keyIterator());

        /**
         * Iterator that relies on an iterator of keys to produce a corresponding sequence of entries.
         */
        class EntryIterator(Iterator<Key> keyIterator)
                implements Iterator<Entry<Key, Value>> {

            @Override
            conditional Entry<Key, Value> next() {
                if (Key key := keyIterator.next()) {
                    private CursorEntry<Key, Value> entry = new CursorEntry(this.Map);
                    return True, entry.advance(key);
                }
                return False;
            }

            @Override
            Boolean knownDistinct() {
                return True;
            }

            @Override
            conditional Int knownSize() {
                return keyIterator.knownSize();
            }

            @Override
            (Iterator<Entry<Key, Value>>, Iterator<Entry<Key, Value>>) bifurcate() {
                (Iterator<Key> iter1, Iterator<Key> iter2) = keyIterator.bifurcate();
                return new EntryIterator(iter1), new EntryIterator(iter2);
            }
        }
    }

    @Override
    @Lazy public/private Set<Key> keys.calc() = new MapKeys<Key, Value>(this);

    @Override
    @Lazy public/private Collection<Value> values.calc() = new MapValues<Key, Value>(this);

    @Override
    @Lazy public/private Collection<Entry<Key, Value>> entries.calc() = new MapEntries<Key, Value>(this);

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Sub-classes must provide an [Iterator] over the keys in the [Map] by implementing this
     * method. If the `Map` is read/write, then the `Iterator` must be able to survive changes to
     * the contents of the Map during iteration; specifically, changes to the most recently iterated
     * key, such as the corresponding value being changed, or the key being removed from the `Map`.
     *
     * @return an `Iterator` over the keys in the `Map`
     */
    protected @Abstract Iterator<Key> keyIterator();
}