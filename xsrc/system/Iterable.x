/**
 * Iterable interface
 */
interface Iterable<ElementType>
    {
    /**
     * @return an Iterator
     */
    Iterator<ElementType> iterator();

    /**
     * @return an iterator that produces elements that match the specified predicate
     */
    Iterator<ElementType> iterator(function Boolean (ElementType) match)
        {
        return new Iterator<ElementType>()
            {
            Iterator iter = iterator();

            conditional ElementType next()
                {
                while (ElementType value : iter.next())
                    {
                    if (match(value))
                        {
                        return (true, value);
                        }
                    }

                return (false);
                }
            }
        }

    /**
     * TODO
     */
    Void forEach(function Void(ElementType) process)
        {
        for (ElementType value : this)
            {
            process(value);
            }
        }
    }
