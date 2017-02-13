/**
 * An iterator over a sequence of elements.
 */
interface Iterator<ElementType>
    {
    /**
     * @rerun true if the iterator has more available elements; false otherwise
     */
    Boolean hasNext();

    /**
     * Get a next element.
     *
     * @return a tuple of (true, nextValue) or (false) if no elements are available
     */
    conditional ElementType next();

    /**
     * Perform the specified action for all remaining elements in the iterator.
     *
     * @param consume  an action to perform on each element
     */
    Void forEach(function Void consume(ElementType))
        {
        while (ElementType value : next())
            {
            consume(value);
            }
        }
    /**
     * Perform the specified action for all remaining elements in the iterator, allowing for
     * a possibility to stop the iteration at any time.
     *
     * @param consume  an action to perform on each element; if the action returns false, the
     *                 method returns immediately and no more elements are iterated over
     */
    Void forEach(function Boolean consume(ElementType))
        {
        while (ElementType value : next())
            {
            if (!consume(value))
                {
                return;
                }
            }
        }
    }
