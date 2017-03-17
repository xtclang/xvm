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
     * @param process  an action to perform on each element
     */
    Void forEach(function Void (ElementType) process)
        {
        while (ElementType value : next())
            {
            process(value);
            }
        }

    /**
     * Perform the specified action for all remaining elements in the iterator, allowing for
     * a possibility to stop the iteration at any time.
     *
     * @param process  an action to perform on each element; if the action returns false, the
     *               iterator is considered "short-circuited", the method returns immediately
     *               and no more elements are iterated over
     *
     * @return true iff the iteration completed without short-circuiting; otherwise false if the
     *         iterator was short-circuited
     */
    Boolean whileEach(function Boolean process(ElementType))
        {
        while (ElementType value : next())
            {
            if (!process(value))
                {
                return false;
                }
            }
        return true;
        }

    /**
     * Perform the specified action for all remaining elements in the iterator, allowing for
     * a possibility to stop the iteration at any time.
     *
     * @param process  an action to perform on each element; if the action returns true, the
     *                 iterator is considered "short-circuited", the method returns immediately
     *                 and no more elements are iterated over
     *
     * @return true iff the iterator was short-circuited; otherwise false if the iteration
     *         completed without short-circuiting
     */
    Boolean untilAny(function Boolean process(ElementType))
        {
        while (ElementType value : next())
            {
            if (process(value))
                {
                return true;
                }
            }
        return false;
        }
    }
