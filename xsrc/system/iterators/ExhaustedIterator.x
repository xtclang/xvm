/**
 * An iterator that has already been exhausted.
 */
const ExhaustedIterator<Element>
        implements Iterator<Element>
    {
    @Override
    conditional Element next()
        {
        return False;
        }

    @Override
    @RO Boolean distinct.get()
        {
        return True;
        }

    @Override
    Boolean knownEmpty()
        {
        return True;
        }

    @Override
    conditional Int knownSize()
        {
        return True, 0;
        }
    }
