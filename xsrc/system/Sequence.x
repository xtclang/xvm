interface Sequence<ElementType>
        extends UniformIndexed<Int, ElementType>
        extends Iterable<ElementType>
    {
    /**
     * The length of the Sequence, which is the number of elements in the Sequence.
     */
    @ro Int length;

    /**
     * Returns a SubSequence of this Sequence. The SubSequence is backed by this
     * Sequence, which means that changes made to the SubSequence will be visible
     * through this Sequence.
     *
     * @param start  first index to include in the SubSequence, inclusive
     * @param end    last index to include in the SubSequence, exclusive
     */
    Sequence<ElementType> subSequence(Int start, Int end)
        {
        return new TODO
        }

    /**
     * Obtain a Sequence of the same length and that contains the same values
     * as this SubSequence. Changes to the returned Sequence are not visible
     * through this SubSequence, and subsequent changes to this SubSequence
     * are not visible through the returned Sequence.
     */
    Sequence<ElementType> reify()
        {
        return this;
        }

    Iterator<ElementType> iterator()
        {
        return new TODO
        }
    }
