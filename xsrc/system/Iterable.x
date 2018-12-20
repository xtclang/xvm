/**
 * The Iterable interface allows an object to expose its contents as a series of elements, either
 * for consumption by the caller or by inversion of control by passing an element-consuming
 * function.
 */
interface Iterable<ElementType>
    {
    /**
     * Obtain an iterator over the contents of the Iterable object.
     *
     * @return an Iterator
     */
    Iterator<ElementType> iterator();

    /**
     * Obtain an iterator over a portion of the contents of the Iterable object.
     *
     * @param match  a function that filters which items should be exposed through the iterator
     *
     * @return an iterator that produces elements that match the specified predicate
     */
    Iterator<ElementType> iterator(function Boolean (ElementType) match)
        {
        return new Iterator<ElementType>()
            {
            Iterator<ElementType> iter = iterator();

            @Override
            conditional ElementType next()
                {
                while (ElementType value : iter.next())
                    {
                    if (match(value))
                        {
                        return true, value;
                        }
                    }

                return false;
                }
            };
        }
    }
