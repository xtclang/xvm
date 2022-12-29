/**
 * An iterator that is limited to the specified number of elements.
 */
class LimitedIterator<Element>
        extends DelegatingIterator<Element>
    {
    construct(Iterator<Element> iter, Int remain)
        {
        this.remain = remain;
        construct DelegatingIterator(iter);
        }

    protected/private Int remain;

    @Override
    conditional Element next()
        {
        if (remain > 0, Element el := iter.next())
            {
            --remain;
            return True, el;
            }

        return False;
        }

    @Override
    Boolean knownEmpty()
        {
        return remain == 0 || iter.knownEmpty();
        }

    @Override
    conditional Int knownSize()
        {
        if (remain == 0)
            {
            return True, 0;
            }

        if (Int size := iter.knownSize())
            {
            return True, Int.minOf(size, remain);
            }

        return False;
        }
    }