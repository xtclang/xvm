/**
 * An iterator that has already been exhausted.
 */
const ExhaustedIterator<Element>
        implements Iterator<Element>
        implements Markable
    {
    @Override
    conditional Element next()
        {
        return False;
        }

    @Override
    Boolean knownDistinct()
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


    // ----- Markable ------------------------------------------------------------------------------

    @Override
    Object mark()
        {
        return Null;
        }

    @Override
    void restore(Object mark, Boolean unmark = False)
        {
        }

    @Override
    void unmark(Object mark)
        {
        }
    }
