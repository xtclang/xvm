/**
 * An iterator that delegates to a sequence of iterators, as if they were together a single
 * iterator.
 */
class CompoundIterator<Element>
        implements Iterator<Element>
    {
    construct(Iterator<Element> iter1, Iterator<Element> iter2)
        {
        iter = iter1;
        tail = iter2;
        }

    protected/private Iterator<Element>  iter;
    protected/private Iterator<Element>? tail;

    /**
     * Add an iterator to the end of this iterator.
     *
     * @param iterator  the Iterator to add
     */
    void add(Iterator<Element> iter)
        {
        if (tail == Null)
            {
            tail = iter;
            }
        else
            {
            val nonNullTail = tail ?: assert; // TODO CP assumptions project - get rid of this val
            if (nonNullTail.is(CompoundIterator))
                {
                nonNullTail.add(iter);
                }
            else
                {
                tail = new CompoundIterator(nonNullTail, iter);
                }
            }
        }

    @Override
    conditional Element next()
        {
        if (Element el := iter.next())
            {
            return True, el;
            }

        if (tail == Null)
            {
            return False;
            }
        else
            {
            iter = tail ?: assert;  // TODO CP assumptions project - get rid of assert
            tail = Null;
            return iter.next();
            }
        }

    @Override
    @RO Boolean distinct.get()
        {
        return tail == Null && iter.distinct;
        }

    @Override
    conditional collections.Orderer sortedBy()
        {
        // TODO GG - assertion failure
        // return tail == Null
        //         ? iter.sortedBy()
        //         : False;

        if (tail == Null)
            {
            return iter.sortedBy();
            }

        return False;
        }

    @Override
    Boolean knownEmpty()
        {
        return iter.knownEmpty() && (tail?.knownEmpty() : True);
        }

    @Override
    conditional Int knownSize()
        {
        if (Int size1 := iter.knownSize())
            {
            if (tail == Null)
                {
                return True, size1;
                }

            if (Int size2 := tail?.knownSize())  // TODO CP assumptions project - get rid of '?'
                {
                return True, size1 + size2;
                }
            }

        return False;
        }
    }
