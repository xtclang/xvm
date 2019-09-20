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
    Boolean knownDistinct() // TODO GG if method has "@RO" it should give an intelligent error msg
        {
        return iter.knownDistinct();
        }

    @Override
    conditional collections.Orderer knownOrder()
        {
        return iter.knownOrder();
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
