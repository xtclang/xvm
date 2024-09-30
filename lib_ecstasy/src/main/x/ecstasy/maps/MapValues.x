/**
 * An implementation of the [Collection] for the [Map.values] property that delegates back to the
 * `Map`.
 */
class MapValues<Key, Value>(Map<Key, Value> contents)
        implements Collection<Value>
        incorporates conditional MapValuesFreezer<Key, Value extends Shareable> {
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
     * The [Map] for which this `Value` [Collection] representation exists.
     */
    protected/private Map<Key, Value> contents;

    /**
     * The type of the [Map.Entry] [Iterator].
     */
    protected typedef Iterator<Map.Entry<Key, Value>> as EntryIterator;

    /**
     * Iterator that relies on an iterator of entries to produce a corresponding sequence of values.
     */
    protected class ValueIterator(EntryIterator entryIterator)
            implements Iterator<Value> {
        @Override
        conditional Value next() {
            if (Map.Entry<Key, Value> entry := entryIterator.next()) {
                return True, entry.value;
            }
            return False;
        }

        @Override
        conditional Int knownSize() = entryIterator.knownSize();

        @Override
        (Iterator<Value>, Iterator<Value>) bifurcate() {
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
    Iterator<Value> iterator() = new ValueIterator(contents.iterator());

    @Override
    Collection<Value> reify() {
        if (contents.is(immutable)) {
            return this;
        }

        return toArray();
    }

    @Override
    MapValues remove(Value value) {
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
    (MapValues, Int) removeAll(function Boolean(Value) shouldRemove) {
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
    static mixin MapValuesFreezer<Key, Value extends Shareable>
            into MapValues<Key, Value>
            implements Freezable {

        @Override
        immutable Freezable + Collection<Value> freeze(Boolean inPlace = False) {
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
                        : frozenMap.values.as(Freezable + Collection<Value>).freeze(True);
            }

            // the Value is known to be freezable, so "reify" this collection and freeze it
            return toArray(Constant).as(immutable Freezable + Collection<Value>);
        }
    }
}