import maps.CopyableMap;

/**
 * `ListSet` is an implementation of a [Set] on top of an [Array] to maintain the order of
 * insertion. It leverages the [ListMap] as its underlying storage and data management around an
 * `Array`, and it converts that [Map] interface to a `Set` interface by extending [MapSet] and
 * delegating to the underlying `ListMap`. If the `Element` type is [Hashable], then the underlying
 * `ListMap` will automatically index the contents using the [ListMapIndex] hashed data structure,
 * resulting in `O(1)` behavior for most operations. Otherwise, behavior is `O(n)`, because it will
 * rely on sequential scans of the underlying `Array`.
 */
class ListSet<Element>
        extends MapSet<Element>
        implements Replicable {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the `ListSet` with an optionally-specified capacity.
     *
     * This is the [Replicable] constructor.
     *
     * @param initCapacity  (optional) initial capacity of the `ListSet`
     */
    @Override
    construct(Int initCapacity = 0) {
        construct MapSet(new ListMap(initCapacity));
    }

    /**
     * Construct a `ListSet` that contains an initial set of values.
     *
     * The `ListSet` uses an [Array] internally to store its values. If the passed [Iterable] object
     * is an `Array`, it will be used as-is, to avoid making an unnecessary copy. There are two
     * considerations to be aware of in this case:
     *
     * * If the `ListSet` is constructed with an `Array` that is
     *   [Mutable](Array.Mutability.Mutable), mutations to the `ListSet` will modify the `Array`;
     *   furthermore, any changes to the `Array` will have undefined
     *
     * * Conversely, if the `ListSet` is constructed with an `Array` that is **not** `Mutable`, then
     *   the `ListSet` will not be [inPlace](Collection.inPlace), meaning that every mutation will
     *   create a new copy of the `ListSet` containing that new mutation
     *
     * @param values  initial values to store in the `ListSet`
     */
    construct(Iterable<Element> values) {
        Element[] array;
        Boolean   inPlace;
        if (values.is(Element[])) {
            array   = values;
            inPlace = values.mutability == Mutable;
        } else {
            array   = values.toArray(Mutable);
            inPlace = True;
        }
        ListMap<Element, Nullable> map = new ListMap(array, Null, inPlace);
        construct MapSet(map);
    }

    /**
     * Construct the `ListSet` as a duplicate of another `ListSet`. Regardless of the mutability of
     * the `ListSet` being copied from, this new `ListSet` will be [inPlace] and mutable.
     *
     * This is the [Duplicable] constructor.
     *
     * @param that  the `ListSet` object to duplicate from
     */
    @Override
    construct(ListSet that) {
        super(that);
    }

    /**
     * Construct a `ListSet` that uses the specified [ListMap] for storage.
     *
     * @param map  the [ListMap] to use for storage
     */
    protected construct(ListMap<Element, Nullable> map) {
        construct MapSet(map);
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected ListSet ensureMapSet(CopyableMap<Element, Nullable> map) {
        return &map == &contents
                ? this
                : new ListSet(map.as(ListMap<Element, Nullable>));
    }
}