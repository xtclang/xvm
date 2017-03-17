/**
 * An iterator over a sequence of elements.
 */
interface Iterator<ElementType>
    {
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
     * @param match  an action to perform on each element; if the action returns true, the
     *               iterator is considered "short-circuited", the method returns immediately
     *               and no more elements are iterated over
     *
     * @return true iff the iterator was short-circuited; otherwise false if the iteration
     *         completed without short-circuiting
     */
    Boolean matchAny(function Boolean match(ElementType))
        {
        while (ElementType value : next())
            {
            if (match(value))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Perform the specified action for all remaining elements in the iterator, allowing for
     * a possibility to stop the iteration at any time.
     *
     * @param match  an action to perform on each element; if the action returns false, the
     *               iterator is considered "short-circuited", the method returns immediately
     *               and no more elements are iterated over
     *
     * @return true iff the iteration completed without short-circuiting; otherwise false if the
     *         iterator was short-circuited
     */
    Boolean matchAll(function Boolean match(ElementType))
        {
        while (ElementType value : next())
            {
            if (!match(value))
                {
                return false;
                }
            }
        return true;
        }
    }
