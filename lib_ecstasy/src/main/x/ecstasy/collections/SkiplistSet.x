/**
 * The SkiplistSet is an Set implementation using a "Skip List" data structure.
 *
 * A skip list is a data structure that has logN average time for data retrieval, insertion,
 * update, and deletion. It behaves like a balanced binary tree, yet without the costs normally
 * associated with maintaining a balanced structure.
 */
class SkiplistSet<Element extends Orderable>
        extends OrderedMapSet<Element>
        implements OrderedSet<Element>
        implements Replicable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a SkiplistSet, with an optional initial capacity and an optional non-natural
     * ordering.
     *
     * @param initialCapacity  the initial capacity, in terms of the number of expected elements
     * @param orderer          the Orderer for this Set, or `Null` to use natural order
     */
    @Override
    construct(Int initialCapacity = 0, Orderer? orderer = Null)
        {
        construct OrderedMapSet(new SkiplistMap<Element, Nullable>(initialCapacity, orderer));
        }

    /**
     * Copy constructor.
     *
     * @param that  the SkiplistSet to copy from
     */
    @Override
    construct(SkiplistSet<Element> that)
        {
        super(that);
        }
    }