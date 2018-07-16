/**
 * A Sequence represents an ordered sequence of values that can be identified, accessed, and
 * manipulated using a zero-based {@code Int} index. Sequences are one of the most common types of
 * basic data structures in programming; for example, arrays are sequences.
 */
interface Sequence<ElementType>
        extends UniformIndexed<Int, ElementType>
        extends Iterable<ElementType>
    {
    /**
     * The size of the Sequence, which is the number of elements in the Sequence.
     */
    @RO Int size;

    /**
     * Returns a sub-sequence of this Sequence. The new Sequence will likely be backed by this
     * Sequence, which means that if this Sequence is mutable, changes made to this Sequence may be
     * visible through the new Sequence, and vice versa; if that behavior is not desired, {@link
     * reify} the value returned from this method.
     *
     * @param range  the range of indexes of this sequence to obtain a slice for; note that the top
     *               end of the range is _inclusive_, such that the range {@code 0..size-1}
     *               represents the entirety of the Sequence
     *
     * @return a slice of this sequence corresponding to the specified range of indexes
     *
     * @throws BoundsException if the specified range exceeds either the lower or upper bounds of
     *         this sequence
     */
    @Op Sequence<ElementType> slice(Range<Int> range);

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
     *
     * @return a reified sequence
     */
    Sequence<ElementType> reify()
        {
        // this must be overridden by any implementation that can represent a slice of another
        // sequence
        return this;
        }

    /**
     * Provide an Iterator that will iterate over the contents of the Sequence.
     *
     * @return a new Iterator that will iterate over the contents of this Sequence
     */
    @Override
    Iterator<ElementType> iterator()
        {
        return new Iterator<ElementType>()
            {
            private Int i = 0;

            @Override
            conditional ElementType next()
                {
                if (i < Sequence.this.size)
                    {
                    return true, Sequence.this[i++];
                    }
                return false;
                }
            };
        }

    /**
     * Look for the specified {@code value} (in the optional {@code range}, if specified), and
     * return the index of the value if it is found.
     *
     * To search backwards for the "last index" of a value, use the optional range parameter to
     * indicate the search direction, for example:
     *
     *   if (sequence.size > 0 && (Int index : sequence.indexOf(value, sequence.size-1 .. 0)))
     *       {
     *       // found the last occurrence of "value" at location "index"
     *       }
     *
     *
     * @param value  the value to search for
     * @param range  the range (inclusive) of the sequence to search within (optional)
     *
     * @return a conditional return of the location of the index of the specified value, or
     *         false if the value could not be found
     */
    conditional Int indexOf(ElementType value, Range<Int>? range = null)
        {
        Int size = this.size;
        Int first;
        Int last;
        Int increment = 1;
        if (range == null)
            {
            if (size == 0)
                {
                return false;
                }

            first = 0;
            last  = size - 1;
            }
        else
            {
            first = range.lowerBound;
            last  = range.upperBound;
            if (first < 0 || last >= size)
                {
                throw new BoundsException();
                }

            if (range.reversed)
                {
                Int temp  = first;
                first     = last;
                last      = temp;
                increment = -1;
                }
            }

        Int i = first;
        while (true)
            {
            if (this[i] == value)
                {
                return true, i;
                }
            if (i == last)
                {
                return false;
                }
            i += increment;
            }
        }
    }
