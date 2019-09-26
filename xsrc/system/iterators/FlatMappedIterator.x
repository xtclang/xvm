/**
 * An iterator that transforms elements from each Original value to zero or more of the desired
 * Element values.
 */
class FlatMappedIterator<Element, Original>
        implements Iterator<Element>
    {
    construct(Iterator<Original> iter, function Iterator<Element> transform(Original))
        {
        this.iterOrig  = iter;
        this.transform = transform;
        }

    /**
     * The iterator of original elements to delegate to.
     */
    protected/private Iterator<Original> iterOrig;

    /**
     * The element transformation function (the "mapping" function).
     */
    protected/private function Iterator<Element> transform(Original);

    /**
     * The most recent result iterator from a flat-map transformation.
     */
    protected/private Iterator<Element>? iterCur;

    @Override
    conditional Element next()
        {
        while (True)
            {
            if (Element el := iterCur?.next())
                {
                return True, el;
                }

            if (Original el := iterOrig.next())
                {
                iterCur = transform(el);
                }
            else
                {
                iterCur = Null;
                return False;
                }
            }
        }

    @Override
    Boolean knownEmpty()
        {
        return (iterCur?.knownEmpty() : True) && iterOrig.knownEmpty();
        }

    @Override
    conditional Int knownSize()
        {
        return False;
        }
    }
