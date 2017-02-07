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
    Sequence<ElementType> subSequence(Int start, Int end);

    /**
     * Obtain a Sequence of the same length and that contains the same values
     * as this SubSequence, but which has two additional attributes:
     * <ul>
     * <li>First, if this Sequence is a portion of a larger Sequence, then the returned
     *     Sequence will no longer be dependent on the larger Sequence for its storage;</li>
     * <li>Second, if this Sequence is a portion of a larger Sequence, then changes to
     *     the returned Sequence will not be visible in the larger Sequence, and changes
     *     to the larger Sequence will not be visible in the returned Sequence.</li>
     * </ul>
     * The contract is designed to allow for the use of copy-on-write and other lazy
     * semantics to achieve efficiency for both time and space.
     */
    Sequence<ElementType> reify();

    /**
     * Provide an Iterator that will iterate over the contents of the Sequence.
     */
    Iterator<ElementType> iterator()
        {
        return new Iterator<ElementType>()
            {
            private Int i = 0;
            
            conditional ElementType next()
                {
                if (i < Sequence.this.length)
                    {
                    return true, Sequence.this[i++];
                    }
                    
                return false;
                }
            }
        }
    }
