/**
 * An implementation of the [Collection] for the [Map.values] property that delegates back to the
 * `Map`.
 */
class MapValues<MapKey, MapValue>(Map<MapKey, MapValue> contents)
        implements Collection<MapValue>
        incorporates conditional MapValuesFreezer<MapKey, MapValue extends Shareable> {
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
     * The [Map] for which this `Value` [Collection] representation exists.
     */
    protected/private Map<MapKey, MapValue> contents;

    /**
     * The type of the [Map.Entry] [Iterator].
     */
    protected typedef Iterator<Map.Entry<MapKey, MapValue>> as EntryIterator;

    /**
     * Iterator that relies on an iterator of entries to produce a corresponding sequence of values.
     */
    protected class ValueIterator(EntryIterator entryIterator)
            implements Iterator<MapValue> {
        @Override
        conditional MapValue next() {
            if (Map.Entry<MapKey, MapValue> entry := entryIterator.next()) {
                return True, entry.value;
            }
            return False;
        }

        @Override
        conditional Int knownSize() = entryIterator.knownSize();

        @Override
        (Iterator<MapValue>, Iterator<MapValue>) bifurcate() {
            (EntryIterator iter1, EntryIterator iter2) = entryIterator.bifurcate();
            return new ValueIterator(iter1), new ValueIterator(iter2);
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
    conditional Int knownSize() = contents.knownSize();

    @Override
    Int size.get() = contents.size;

    @Override
    Boolean empty.get() = contents.empty;

    @Override
    Iterator<MapValue> iterator() = new ValueIterator(contents.iterator());

    @Override
    Collection<MapValue> reify() {
        if (contents.is(immutable)) {
            return this;
        }

        return toArray();
    }

    @Override
    MapValues remove(MapValue value) {
        verifyInPlace();
        for (val entry : contents.iterator()) {
            if (entry.value == value) {
                entry.delete();
                break;
            }
        }
        return this;
    }

    @Override
    (MapValues, Int) removeAll(function Boolean(MapValue) shouldRemove) {
        verifyInPlace();
        (_, Int removed) = contents.removeAll(entry -> shouldRemove(entry.value));
        return this, removed;
    }

    @Override
    MapValues clear() {
        verifyInPlace();
        contents.clear();
        return this;
    }

    // ----- Freezable implementation --------------------------------------------------------------

    /**
     * Mixin that makes MapValues Freezable if Value is Shareable.
     */
    static mixin MapValuesFreezer<MapKey, MapValue extends Shareable>
            into MapValues<MapKey, MapValue>
            implements Freezable {

        @Override
        immutable Freezable + Collection<MapValue> freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            if (contents.is(immutable Map)) {
                return makeImmutable();
            }

            if (inPlace, val freezableMap := contents.is(Freezable)) {
                // the caller indicated that the freeze should occur in-place, so freeze the map;
                // if it freezes in-place, then use this values collection, otherwise get the new
                // values collection for the frozen map
                val frozenMap = freezableMap.freeze(inPlace);
                return &contents == &frozenMap
                        ? this.makeImmutable()
                        : frozenMap.values.as(Freezable + Collection<MapValue>).freeze(True);
            }

            // the MapValue is known to be freezable, so "reify" this collection and freeze it
            return toArray(Constant).as(immutable Freezable + Collection<MapValue>);
        }
    }
}