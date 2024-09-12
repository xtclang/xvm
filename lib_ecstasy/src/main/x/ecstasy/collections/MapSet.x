import maps.CopyableMap;

/**
 * The MapSet is simple implementation of the [Set] interface that uses an underlying [Map] instance
 * as its storage.
 */
class MapSet<Element>
        implements Duplicable
        implements Set<Element>
        incorporates conditional MapSetFreezer<Element extends Shareable>
        incorporates conditional SetHasher<Element extends Hashable> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `MapSet` that provides `Set` capabilities by delegating to the specified `Map`.
     *
     * @param map   the [CopyableMap] to construct the `MapSet` on top of
     */
    construct(CopyableMap<Element, Nullable> map) {
        contents = map;
    } finally {
        if (contents.is(immutable)) {
            makeImmutable();
        }
    }

    /**
     * Construct the `MapSet` as a duplicate of another `MapSet`. The expectation is that the new
     * `MapSet` will be [inPlace] mutable if the underlying [Map] supports mutability.
     *
     * This is the [Duplicable] constructor.
     *
     * @param that  the [Duplicable] `MapSet` object to duplicate from
     */
    @Override
    construct(MapSet that) {
        this.contents = that.contents.duplicate();
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying [CopyableMap], whose keys are the contents of the `Set`.
     */
    protected CopyableMap<Element, Nullable> contents;

    // ----- Collection methods --------------------------------------------------------------------

    @Override
    @RO Boolean inPlace.get() = contents.inPlace;

    @Override
    conditional Orderer? ordered() = contents.keys.ordered();

    @Override
    conditional Int knownSize() = contents.knownSize();

    @Override
    Boolean empty.get() = contents.empty;

    @Override
    Int size.get() = contents.size;

    @Override
    Iterator<Element> iterator() = contents.keys.iterator();

    @Override
    Boolean contains(Element value) = contents.contains(value);

    @Override
    Boolean containsAll(Collection<Element> values) = contents.keys.containsAll(values);

    @Override
    Element[] toArray(Array.Mutability? mutability = Null) = contents.keys.toArray(mutability);

    @Override
    @Op("+") MapSet add(Element value) = ensureMapSet(contents.put(value, Null));

    @Override
    MapSet addAll(Iterator<Element> iter) {
        if (inPlace) {
            return super(iter);
        }

        ListSet<Element> listSet = new ListSet(iter.toArray());
        return ensureMapSet(contents.putAll(listSet.as(MapSet).contents));
    }

    @Override
    conditional MapSet addIfAbsent(Element value) {
        if (val resultingMap := contents.putIfAbsent(value, Null)) {
            return True, ensureMapSet(resultingMap);
        }

        return False;
    }

    @Override
    @Op("-") MapSet remove(Element value) = ensureMapSet(contents.remove(value));

    @Override
    @Op("-") MapSet removeAll(Iterable<Element> values) = ensureMapSet(contents.removeAll(values));

    @Override
    conditional MapSet removeIfPresent(Element value) {
        if (val resultingMap := contents.remove(value, Null)) {
            return True, ensureMapSet(resultingMap);
        }

        return False;
    }

    @Override
    (MapSet, Int) removeAll(function Boolean(Element) shouldRemove) {
        (val resultingMap, Int count) = contents.removeAll(entry -> shouldRemove(entry.key));
        return ensureMapSet(resultingMap), count;
    }

    @Override
    MapSet clear() = ensureMapSet(contents.clear());

    // ----- Freezable interface -------------------------------------------------------------------

    /**
     * Conditional [Freezable] implementation.
     *
     * Warning: The [freeze] method contract as specified below is that the same type is returned,
     * i.e. MapSet is frozen to a MapSet, ListSet is frozen to a ListSet, and HashSet is frozen to a
     * HashSet, etc. When subclassing [MapSet], this contract must be upheld, or the [freeze] method
     * signature of the subclass must be overridden to widen the contract to allow the `freeze`
     * method to return a [ListSet], because the implementation of [MapSetFreezer.freeze] returns a
     * `ListSet` instead of an instance of the subclass when the [MapSet.contents] map is not
     * [Freezable].
     */
    static mixin MapSetFreezer<Element extends Shareable>
            into MapSet<Element>
            implements Freezable {

        @Override
        immutable MapSetFreezer<Element> freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            if (inPlace, val freezableMap := contents.is(Freezable)) {
                return ensureMapSet(freezableMap.freeze(inPlace=True)).makeImmutable();
            }

            // just use a light-weight ListSet in lieu of making a copy of the Map and this MapSet
            return new ListSet(toArray(Constant)).freeze(inPlace=True);
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Subclasses that can wrap a persistent (i.e. not-in-place) `Map` **must** override this method
     * to replace the `new MapSet` code below with the code necessary to wrap the passed `Map` when
     * it is not the same `Map` instance as is stored in the [contents] property.
     */
    protected MapSet ensureMapSet(CopyableMap<Element, Nullable> map) {
        return &map == &contents
                ? this
                : new MapSet(map);
    }
}