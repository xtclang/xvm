/**
 * An iterator that transforms elements from an Original type to the desired Element type.
 */
class MappedIterator<Element, Original>
        implements Iterator<Element>
    {
    construct(Iterator<Original> iter, function Element transform(Original))
        {
        this.iter      = iter;
        this.transform = transform;
        }

    /**
     * The iterator of original elements to delegate to.
     */
    protected/private Iterator<Original> iter;

    /**
     * The element transformation function (the "mapping" function).
     */
    protected/private function Element transform(Original);

    @Override
    conditional Element next()
        {
        if (Original el := iter.next())
            {
            return True, transform(el);
            }

        return False;
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
