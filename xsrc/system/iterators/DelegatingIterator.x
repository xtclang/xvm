// TODO GG use delegation

/**
 * An iterator that delegates to another iterator.
 */
class DelegatingIterator<Element>(Iterator<Element> iter)
        implements Iterator<Element>
    {
    protected/private Iterator<Element> iter;

    @Override
    conditional Element next()
        {
        return iter.next();
        }

    @Override
    @RO Boolean distinct.get()
        {
        return iter.distinct;
        }

    @Override
    conditional collections.Orderer sortedBy()
        {
        return iter.sortedBy();
        }

    @Override
    Boolean knownEmpty()
        {
        return iter.knownEmpty();
        }

    @Override
    conditional Int knownSize()
        {
        return iter.knownSize();
        }
    }
