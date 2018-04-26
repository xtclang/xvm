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
    @RO Boolean distinct.get()
        {
        return true;
        }

    /**
     * The "union" operator.
     */
    @Override
    @Op("|") conditional Set<ElementType> addAll(Set!<ElementType> values);

    /**
     * The "symmetric difference" operator.
     */
    @Override
    @Op("^") conditional Set<ElementType> removeAll(Set!<ElementType> values);

    /**
     * The "union" operator.
     */
    @Override
    @Op("&") conditional Set<ElementType> retainAll(Set!<ElementType> values);
    }
