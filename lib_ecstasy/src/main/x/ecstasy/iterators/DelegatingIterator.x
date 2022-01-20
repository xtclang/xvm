/**
 * An iterator that delegates to another iterator.
 */
class DelegatingIterator<Element>(Iterator<Element> iter)
        implements Iterator<Element>
    {
    /**
     * The iterator to delegate to.
     */
    protected/private Iterator<Element> iter;

    @Override
    conditional Element next()
        {
        return iter.next();
        }

    @Override
    Boolean knownDistinct()
        {
        return iter.knownDistinct();
        }

    @Override
    conditional Orderer knownOrder()
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
