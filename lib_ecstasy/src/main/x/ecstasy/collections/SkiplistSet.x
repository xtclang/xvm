import maps.CopyableMap;

/**
 * An implementation of a [Set] that is backed by a [SkiplistMap], which implementats the "skip
 * list" data structure.
 *
 * A skip list is a data structure that has logN average time for data retrieval, insertion,
 * update, and deletion. It behaves like a balanced binary tree, yet without the costs normally
 * associated with maintaining a balanced structure.
 */
class SkiplistSet<Element extends Orderable>
        extends OrderedMapSet<Element>
        implements OrderedSet<Element>
        implements Replicable {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `SkiplistSet`, with an optional initial capacity and an optional non-natural
     * ordering.
     *
     * @param initialCapacity  the initial capacity, in terms of the number of expected elements
     * @param orderer          (optional) the Orderer for this Set, or `Null` to use natural order
     */
    @Override
    construct(Int initialCapacity = 0, Orderer? orderer = Null) {
        construct OrderedMapSet(new SkiplistMap<Element, Nullable>(initialCapacity, orderer));
    }

    /**
     * Copy constructor.
     *
     * @param that  the `SkiplistSet` to copy from
     */
    @Override
    construct(SkiplistSet<Element> that) {
        super(that);
    }

    /**
     * Construct a `SkiplistSet` that uses the specified [SkiplistMap] for storage.
     *
     * @param map  the [SkiplistMap] to use for storage
     */
    protected construct(SkiplistMap<Element, Nullable> map) {
        construct MapSet(map);
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected SkiplistSet ensureMapSet(CopyableMap<Element, Nullable> map) {
        return &map == &contents
                ? this
                : new SkiplistSet(map.as(SkiplistMap<Element, Nullable>));
    }
}