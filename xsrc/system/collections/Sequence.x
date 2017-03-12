interface Sequence<ElementType>
        extends UniformIndexed<Int, ElementType>
        extends Iterable<ElementType>
    {
    /**
     * The size of the Sequence, which is the number of elements in the Sequence.
     */
    @ro Int size;

    /**
     * Returns a sub-sequence of this Sequence. The new Sequence will likely be backed by this
     * Sequence, which means is this Sequence is mutable, that changes made to this Sequence may be
     * visible through the new Sequence, and vice versa; if that behavior is not desired, {@link
     * reify} the value returned from this method.
     */
    @op Sequence<ElementType> slice(Range<Int> range);

    /**
     * Obtain a Sequence of the same length and that contains the same values as this Sequence, but
     * which has two additional attributes:
     *
     * * First, if this Sequence is a portion of a larger Sequence, then the returned Sequence will
     *   no longer be dependent on the larger Sequence for its storage;
     * * Second, if this Sequence is a portion of a larger Sequence, then changes to the returned
     *   Sequence will not be visible in the larger Sequence, and changes to the larger Sequence
     *   will not be visible in the returned Sequence.
     *
     * The contract is designed to allow for the use of copy-on-write and other lazy semantics to
     * achieve efficiency for both time and space.
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
