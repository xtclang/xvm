/**
 * ListSet is an implementation of a Set on top of an Array to maintain the order of
 * insertion.
 */
class ListSet<Element extends Hashable>
         implements Set<Element>
         implements MutableAble, PersistentAble, ImmutableAble
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Int initCapacity = 0)
        {
        TODO
        }

    construct(Element[] elements)
        {
        TODO
        }

    @Override
    Int size.get()
        {
        TODO;
        }

    @Override
    Iterator<Element> iterator()
        {
        TODO;
        }

    /**
     * Obtain a shallow clone of the Collection. The definition of "shallow clone" is that the new
     * collection will contain the same element references, but changes made to this collection
     * through the Collection interface will not appear in the returned collection, nor will changes
     * made to the returned collection through the Collection interface appear in this collection.
     *
     * @return a Collection such that changes to this Collection do not appear in that collection,
     *         nor vice versa
     */
    @Override
    ListSet clone()
        {
        TODO;
        }

    @Override
    ListSet ensureMutable()
        {
        TODO;
        }

    @Override
    ListSet ensurePersistent(Boolean inPlace = false)
        {
        TODO;
        }

    @Override
    immutable ListSet<Element> ensureImmutable(Boolean inPlace = false)
        {
        TODO;
        }
    }
