/**
 * A Sequence represents an ordered sequence of values that can be identified, accessed, and
 * manipulated using a zero-based `Int` index. Sequences are one of the most common types of
 * basic data structures in programming; for example, arrays are sequences.
 */
interface Sequence<ElementType>
        extends UniformIndexed<Int, ElementType>
        extends Iterable<ElementType>
        extends Stringable
    {
    /**
     * Provide an Iterator that will iterate over the contents of the Sequence.
     *
     * @return a new Iterator that will iterate over the contents of this Sequence
     */
    @Override
    Iterator<ElementType> iterator()
        {
        return new Iterator()
            {
            private Int i = 0;

            @Override
            conditional ElementType next()
                {
                if (i < Sequence.this.size)
                    {
                    return True, Sequence.this[i++];
                    }
                return False;
                }
            };
        }

    /**
     * Look for the specified `value` starting at the specified index.
     *
     * @param value    the value to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return a conditional return of the location of the index of the specified value, or
     *         False if the value could not be found
     */
    conditional Int indexOf(ElementType value, Int startAt = 0)
        {
        for (Int i = startAt.maxOf(0), Int last = size - 1; i < last; ++i)
            {
            if (this[i] == value)
                {
                return True, i;
                }
            }
        return False;
        }

    /**
     * Look for the specified `value` starting at the specified index.
     *
     * @param value    the value to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return a conditional return of the location of the index of the specified value, or
     *         False if the value could not be found
     */
    conditional Int lastIndexOf(ElementType value, Int startAt = Int.maxvalue)
        {
        for (Int i = (size-1).minOf(startAt); i >= 0; --i)
            {
            if (this[i] == value)
                {
                return True, i;
                }
            }
        return False;
        }

    /**
     * Look for the specified `value` (in the optional `range`, if specified), and
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
     * @param value  the value to search for
     * @param range  the range (inclusive) of the sequence to search within
     *
     * @return a conditional return of the location of the index of the specified value, or
     *         False if the value could not be found
     */
    conditional Int indexOf(ElementType value, Range<Int> range)
        {
        Int size  = this.size;
        Int first = range.lowerBound;
        Int last  = range.upperBound;
        if (first < 0 || last >= size)
            {
            throw new OutOfBounds();
            }

        if (range.reversed)
            {
            for (Int i = last; i >= first; --i)
                {
                if (this[i] == value)
                    {
                    return True, i;
                    }
                }
            }
        else
            {
            for (Int i = first; i <= last; ++i)
                {
                if (this[i] == value)
                    {
                    return True, i;
                    }
                }
            }

        return False;
        }

    /**
     * Obtain the contents of the Sequence as an Array.
     *
     * @param mutability  the requested Mutability of the resulting array
     *
     * @return an array of elements from this sequence
     */
    ElementType[] to<ElementType[]>(VariablyMutable.Mutability mutability = Persistent)
        {
        return new Array<ElementType>(mutability, this);
        }

    /**
     * Returns a sub-sequence of this Sequence. The new Sequence will likely be backed by this
     * Sequence, which means that if this Sequence is mutable, changes made to this Sequence may be
     * visible through the new Sequence, and vice versa; if that behavior is not desired, [reify]
     * the value returned from this method.
     *
     * @param range  the range of indexes of this sequence to obtain a slice for; note that the top
     *               end of the range is _inclusive_, such that the range `0..size-1` represents
     *               the entirety of the Sequence
     *
     * @return a slice of this sequence corresponding to the specified range of indexes
     *
     * @throws OutOfBounds  if the specified range exceeds either the lower or upper bounds of
     *                      this sequence
     */
    @Op("[..]")
    Sequence slice(Range<Int> range);

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
    Sequence reify()
        {
        // this must be overridden by any implementation that can represent a slice of another
        // sequence
        return this;
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int capacity = 2; // allow for "[]"
        if (ElementType.is(Type<Stringable>))
            {
            for (ElementType v : this)
                {
                capacity += v.estimateStringLength() + 2; // allow for ", "
                }
            }
        else
            {
            for (ElementType v : this)
                {
                if (v.is(Stringable))
                    {
                    capacity += v.estimateStringLength() + 2;
                    }
                else
                    {
                    capacity += 2;
                    }
                }
            }

        return capacity;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add('[');

        if (ElementType.is(Type<Stringable>))
            {
            Append:
            for (ElementType v : this)
                {
                if (!Append.first)
                    {
                    appender.add(", ");
                    }

                v.appendTo(appender);
                }
            }
        else
            {
            Append:
            for (ElementType v : this)
                {
                if (!Append.first)
                    {
                    appender.add(", ");
                    }

                if (v.is(Stringable))
                    {
                    v.appendTo(appender);
                    }
                else
                    {
                    v.to<String>().appendTo(appender);
                    }
                }
            }
        appender.add(']');
        }
    }
