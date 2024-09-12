/**
 * An implementation of the Set for the [Map.keys] property that delegates its operations to the
 * [Map].
 */
class MapKeys<MapKey, MapValue>(Map<MapKey, MapValue> contents)
        implements Set<MapKey>
        incorporates conditional MapKeysFreezer<MapKey extends Shareable> {
    // ----- constructors --------------------------------------------------------------------------

    construct(Map<MapKey, MapValue> contents) {
        this.contents = contents;
    } finally {
        if (contents.is(immutable)) {
            makeImmutable();
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The [Map] for which this `Key` [Set] representation exists.
     */
    protected/private Map<MapKey, MapValue> contents;

    /**
     * The type of the [Map.Entry] [Iterator].
     */
    protected typedef Iterator<Map.Entry<MapKey, MapValue>> as EntryIterator;

    /**
     * Iterator that relies on an iterator of entries to produce a corresponding sequence of keys.
     */
    protected class KeyIterator(EntryIterator entryIterator)
            implements Iterator<MapKey> {
        @Override
        conditional MapKey next() {
            if (Map.Entry<MapKey, MapValue> entry := entryIterator.next()) {
                return True, entry.key;
            }
            return False;
        }

        @Override
        Boolean knownDistinct() = True;

        @Override
        conditional Int knownSize() = entryIterator.knownSize();

        @Override
        (Iterator<MapKey>, Iterator<MapKey>) bifurcate() {
            (EntryIterator iter1, EntryIterator iter2) = entryIterator.bifurcate();
            return new KeyIterator(iter1), new KeyIterator(iter2);
        }
    }

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

    // ----- Set interface -------------------------------------------------------------------------

    @Override
    @RO Boolean inPlace.get() = contents.inPlace;

    @Override
    conditional Orderer? ordered() = contents.ordered();

    @Override
    conditional Int knownSize() = contents.knownSize();

    @Override
    Int size.get() = contents.size;

    @Override
    Boolean empty.get() = contents.empty;

    @Override
    Iterator<MapKey> iterator() = new KeyIterator(contents.iterator());

    @Override
    Boolean contains(MapKey value) = contents.contains(value);

    @Override
    Boolean containsAll(Collection<MapKey> values) {
        for (MapKey key : values) {
            if (!contains(key)) {
                return False;
            }
        }
        return True;
    }

    @Override
    Set<MapKey> reify() {
        if (contents.is(immutable)) {
            return this;
        }

        // specific Map implementations using this class should override this method to provide a
        // more efficient reified data structure
        return new ListSet(toArray());
    }

    @Override
    MapKeys remove(MapKey key) {
        verifyInPlace();
        contents.remove(key);
        return this;
    }

    @Override
    (MapKeys, Int) removeAll(function Boolean(MapKey) shouldRemove) {
        verifyInPlace();
        (_, Int removed) = contents.removeAll(entry -> shouldRemove(entry.key));
        return this, removed;
    }

    @Override
    MapKeys clear() {
        verifyInPlace();
        contents.clear();
        return this;
    }

    // ----- Freezable implementation --------------------------------------------------------------

    /**
     * Mixin that makes MapKeys Freezable if both Key is Shareable.
     */
    static mixin MapKeysFreezer<MapKey extends Shareable>
            into MapKeys<MapKey>
            implements Freezable {

        @Override
        immutable Freezable + Set<MapKey> freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            if (contents.is(immutable Map)) {
                return makeImmutable();
            }

            if (inPlace, val freezableMap := contents.is(Freezable)) {
                // the caller indicated that the freeze should occur in-place, so freeze the map;
                // if it freezes in-place, then use this key set, otherwise get the new key set for
                // the frozen map
                val frozenMap = freezableMap.freeze(inPlace);
                return &contents == &frozenMap
                        ? this.makeImmutable()
                        : frozenMap.keys.as(Freezable + Set<MapKey>).freeze(True);
            }

            // the MapKey is known to be freezable, so "reify" this set into a separate freezable
            // set and freeze it there
            return new ListSet<MapKey>(toArray(Constant)).freeze(inPlace=True);
        }
    }
}