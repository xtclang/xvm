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
