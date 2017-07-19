/**
 * A Set is a container data structure that represents a group of _distinct values_. While the Set's
 * interface is identical to that of the Collection, its default behavior is subtly different.
 */
interface Set<ElementType>
        extends Collection<ElementType>
    {
    // ----- read operations -----------------------------------------------------------------------

    /**
     * A Set is always composed of distinct values.
     */
    @Override
    Boolean distinct.get()
        {
        return true;
        }
    }
