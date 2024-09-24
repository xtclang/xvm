/**
 * An implementation of the [Collection] for the [Map.entries] property that delegates back to the
 * `Map`.
 */
class MapEntries<Key, Value>(Map<Key, Value> contents)
        implements Collection<Entry>
        incorporates conditional MapEntriesFreezer<Key extends Shareable, Value extends Shareable> {
    // ----- constructors --------------------------------------------------------------------------

    construct(Map<Key, Value> contents) {
        this.contents = contents;
    } finally {
        if (contents.is(immutable)) {
            makeImmutable();
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The [Map] for which this `entries` [Collection] representation exists.
     */
    protected/private Map<Key, Value> contents;

    /**
     * The type of the [Map.Entry].
     */
    protected typedef Map.Entry<Key, Value> as Entry;

    /**
     * Iterator that relies on an iterator of entries to produce a corresponding sequence of values.
     */
    protected class EntryIterator(Iterator<Entry> entryIterator)
            implements Iterator<Entry> {
        @Override
        conditional Entry next() {
            if (Entry entry := entryIterator.next()) {
                return True, entry.reify();
            }
            return False;
        }

        @Override
        conditional Int knownSize() = entryIterator.knownSize();

        @Override
        (Iterator<Entry>, Iterator<Entry>) bifurcate() {
            (Iterator<Entry> iter1, Iterator<Entry> iter2) = entryIterator.bifurcate();
            return new EntryIterator(iter1), new EntryIterator(iter2);
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


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    @RO Boolean inPlace.get() = contents.inPlace;

    @Override
    conditional Orderer? ordered() {
        if (val orderer := contents.ordered()) {
            return True, contents.orderByKey(orderer?) : Null;
        }
        return False;
    }

    @Override
    conditional Int knownSize() = contents.knownSize();

    @Override
    Int size.get() = contents.size;

    @Override
    Boolean empty.get() = contents.empty;

    @Override
    Iterator<Entry> iterator() = new EntryIterator(contents.iterator());

    @Override
    Collection<Entry> reify() {
        if (contents.is(immutable)) {
            return this;
        }

        return toArray();
    }

    @Override
    MapEntries remove(Entry value) {
        verifyInPlace();
        value.delete();
        return this;
    }

    @Override
    (MapEntries, Int) removeAll(function Boolean(Entry) shouldRemove) {
        verifyInPlace();
        (_, Int removed) = contents.removeAll(shouldRemove);
        return this, removed;
    }

    @Override
    MapEntries clear() {
        verifyInPlace();
        contents.clear();
        return this;
    }

    // ----- Freezable implementation --------------------------------------------------------------

    /**
     * Mixin that makes `MapEntries` Freezable if `Key` and `Value` are Shareable.
     */
    static mixin MapEntriesFreezer<Key extends Shareable, Value extends Shareable>
            into MapEntries<Key, Value>
            implements Freezable {

        @Override
        immutable Freezable + Collection<Entry> freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            if (contents.is(immutable Map)) {
                return makeImmutable();
            }

            if (inPlace, val freezableMap := contents.is(Freezable)) {
                // the caller indicated that the freeze should occur in-place, so freeze the map;
                // if it freezes in-place, then use this entries collection, otherwise get the new
                // entries collection for the frozen map
                val frozenMap = freezableMap.freeze(inPlace);
                return &contents == &frozenMap
                        ? this.makeImmutable()
                        : frozenMap.entries.as(Freezable + Collection<Entry>).freeze(True);
            }

            // the Entry is known to be freezable, so "reify" this collection and freeze it
            return toArray(Constant).as(immutable Freezable + Collection<Entry>);
        }
    }
}