/**
 * An implementation of the [Map.entries] and [Map.values] API that delegates back to the map and
 * to the map's [Map.keys] set.
 */
mixin KeySetBasedMap<Key, Value>
        into Map<Key, Value> {

    @Override
    conditional Orderer? ordered() = keys.ordered();

    @Override
    conditional Int knownSize() = keys.knownSize();

    @Override
    @RO Int size.get() = keys.size;

    @Override
    @RO Boolean empty.get() = keys.empty;

    @Override
    Iterator<Entry<Key, Value>> iterator() {
        return new EntryIterator(keys.iterator());

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
            Entry<Key, Value>[] toArray(Array.Mutability? mutability = Null) {
                // entries must be reified
                Entry<Key, Value>[] result = new Entry<Key, Value>[](knownSize() ?: 0); // mutable
                if (fromService() && (mutability == Null || mutability == Constant)) {
                    // each Entry returned across a service boundary needs to be a proxy (not
                    // frozen), since each Entry holds a reference back to the Map (and we do not
                    // want to freeze the Map)
                    loop: for (Entry<Key, Value> entry : this) {
                        entry = entry.reify();
                        result[loop.count] = &entry.proxyAs(Entry<Key, Value>);
                    }
                    // an array of service proxies must be "frozen" to cross the service boundary;
                    // the proxies are unaffected by the freeze() because they are already Passable
                    mutability = Constant;
                } else {
                    loop: for (Entry<Key, Value> entry : this) {
                        result[loop.count] = entry.reify();
                    }
                }
                return result.toArray(mutability, True);
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
    @Lazy Collection<Entry<Key, Value>> entries.calc() = new MapEntries(this);

    @Override
    @Lazy Collection<Value> values.calc() = new MapValues(this);

    // ----- internal helpers ----------------------------------------------------------------------

    /**
     * Some operations require that the containing Map be mutable; this method throws an exception
     * if the Map is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly iff the Map does not support in-place modification
     */
    protected Boolean verifyInPlace() {
        if (!inPlace) {
            throw new ReadOnly("Map operation requires inPlace == True");
        }
        return True;
    }
}