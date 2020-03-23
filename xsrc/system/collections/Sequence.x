/**
 * A Sequence represents an ordered sequence of values that can be identified, accessed, and
 * manipulated using a zero-based `Int` index. Sequences are one of the most common types of
 * basic data structures in programming; for example, arrays are sequences.
 */
interface Sequence<Element>
        extends UniformIndexed<Int, Element>
        extends Iterable<Element>
        extends Stringable
    {
    /**
     * Provide an Iterator that will iterate over the contents of the Sequence.
     *
     * @return a new Iterator that will iterate over the contents of this Sequence
     */
    @Override
    Iterator<Element> iterator()
        {
        return new Iterator()
            {
            private Int i = 0;

            @Override
            conditional Element next()
                {
                if (i < this.Sequence.size)
                    {
                    return True, this.Sequence[i++];
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
     * @return True iff this sequence contains the `value`, at or after the `startAt` index
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int indexOf(Element value, Int startAt = 0)
        {
        for (Int i = startAt.maxOf(0), Int last = size - 1; i <= last; ++i)
            {
            if (this[i] == value)
                {
                return True, i;
                }
            }
        return False;
        }

    /**
     * Look for the specified `value` starting at the specified index and searching backwards.
     *
     * @param value    the value to search for
     * @param startAt  the index to start searching backwards from (optional)
     *
     * @return True iff this sequence contains the `value`, at or before the `startAt` index
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int lastIndexOf(Element value, Int startAt = Int.maxvalue)
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
     * Look for the specified `value` (in the optional `interval`, if specified), and return the
     * index of the value if it is found.
     *
     * To search backwards for the "last index" of a value, use the optional interval parameter to
     * indicate the search direction, for example:
     *
     *   if (sequence.size > 0 && (Int index : sequence.indexOf(value, sequence.size-1 .. 0)))
     *       {
     *       // found the last occurrence of "value" at location "index"
     *       }
     *
     * @param value     the value to search for
     * @param interval  the interval (inclusive) of the sequence to search within
     *
     * @return True iff this sequence contains the `value` inside the specified interval of indexes
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int indexOf(Element value, Interval<Int> interval)
        {
        Int size  = this.size;
        Int first = interval.lowerBound;
        Int last  = interval.upperBound;
        if (first < 0 || last >= size)
            {
            throw new OutOfBounds();
            }

        if (interval.reversed)
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
     * Determine if `this` sequence _contains_ `that` sequence, and at what index `that` sequence
     * first occurs.
     *
     * @param that     a sequence to look for within this sequence
     * @param startAt  (optional) the first index to search from
     *
     * @return True iff this sequence contains that sequence, at or after the `startAt` index
     * @return (conditional) the index at which the specified sequence of values was found
     */
    conditional Int indexOf(Sequence! that, Int startAt = 0)
        {
        Int count = that.size;
        startAt = startAt.maxOf(0);
        if (count == 0)
            {
            return startAt > size ? False : (True, startAt);
            }

        Element first = that[0];
        Next: for (Int i = startAt, Int last = this.size - count; i <= last; ++i)
            {
            if (this[i] == first)
                {
                for (Int i2 = i + 1, Int last2 = i + count - 1; i2 <= last2; ++i2)
                    {
                    if (this[i2] != that[i2])
                        {
                        continue Next;
                        }
                    }
                return True, i;
                }
            }

        return False;
        }

    /**
     * Determine if `this` sequence _contains_ `that` sequence, and at what index `that` sequence
     * last occurs.
     *
     * @param that     a sequence to look for within this sequence
     * @param startAt  (optional) the index to start searching backwards from
     *
     * @return True iff this sequence contains that sequence, at or before the `startAt` index
     * @return (conditional) the index at which the specified sequence of values was found
     */
    conditional Int lastIndexOf(Sequence! that, Int startAt = Int.maxvalue)
        {
        Int count = that.size;
        startAt = startAt.minOf(this.size-count);
        if (count == 0)
            {
            return startAt < 0 ? False : (True, startAt);
            }

        Element first = that[0];
        Next: for (Int i = startAt; i >= 0; --i)
            {
            if (this[i] == first)
                {
                for (Int i2 = i + 1, Int last2 = i + count - 1; i2 <= last2; ++i2)
                    {
                    if (this[i2] != that[i2])
                        {
                        continue Next;
                        }
                    }
                return True, i;
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
    @Override
    Element[] toArray(VariablyMutable.Mutability mutability = Persistent)
        {
        return new Array<Element>(mutability, this);
        }

    /**
     * Returns a sub-sequence of this Sequence. The new Sequence will likely be backed by this
     * Sequence, which means that if this Sequence is mutable, changes made to this Sequence may be
     * visible through the new Sequence, and vice versa; if that behavior is not desired, [reify]
     * the value returned from this method.
     *
     * @param interval  the range of indexes of this sequence to obtain a slice for; note that
     *                  the top end of the interval is _inclusive_, such that the interval
     *                  `0..size-1` represents the entirety of the Sequence
     *
     * @return a slice of this sequence corresponding to the specified range of indexes
     *
     * @throws OutOfBounds  if the specified range exceeds either the lower or upper bounds of
     *                      this sequence
     */
    @Op("[..]")
    Sequence slice(Interval<Int> interval);

    /**
     * Returns a sub-sequence of this Sequence. The new Sequence will likely be backed by this
     * Sequence, which means that if this Sequence is mutable, changes made to this Sequence may be
     * visible through the new Sequence, and vice versa; if that behavior is not desired, [reify]
     * the value returned from this method.
     *
     * @param firstInclusive  the index of the first element from this sequence that will be
     *                        included in the resulting sub-sequence
     * @param lastExclusive   the index of the first element from this sequence that will NOT be
     *                        included in the resulting sub-sequence
     *
     * @return a slice of this sequence corresponding to the specified range of indexes
     *
     * @throws OutOfBounds  if the specified range exceeds either the lower or upper bounds of
     *                      this sequence
     */
    // TODO CP Sequence slice(Int firstInclusive, Int lastExclusive);

    /**
     * Determine if `this` sequence _starts-with_ `that` sequence. A sequence `this` of at least `n`
     * elements "starts-with" another sequence `that` of exactly `n` elements iff, for each index
     * `[0..n)`, the element at the index in `this` sequence is equal to the element at the same
     * index in `that` sequence.
     *
     * @param that  a sequence to look for at the beginning of this sequence
     *
     * @return True iff this sequence starts-with that sequence
     */
    Boolean startsWith(Sequence that) // TODO GG allow "Sequence!"
        {
        Int count = that.size;
        if (count == 0)
            {
            return True;
            }

        if (this.size < count)
            {
            return False;
            }

        for (Int i = 0; i < count; ++i)
            {
            if (this[i] != that[i])
                {
                return False;
                }
            }

        return True;
        }

    /**
     * Determine if `this` sequence _ends-with_ `that` sequence. A sequence `this` of `m` elements
     * "ends-with" another sequence `that` of `n` elements iff `n <= m` and, for each index `i`
     * in the range `[0..n)`, the element at the index `m-n+i` in `this` sequence is equal to the
     * element at index `i` in `that` sequence.
     *
     * @param that  a sequence to look for at the end of this sequence
     *
     * @return True iff this sequence end-with that sequence
     */
    Boolean endsWith(Sequence that) // TODO GG allow "Sequence!"
        {
        Int count = that.size;
        if (count == 0)
            {
            return True;
            }

        Int offset = this.size - count;
        if (offset < 0)
            {
            return False;
            }

        for (Int i = 0; i < count; ++i)
            {
            if (this[offset+i] != that[i])
                {
                return False;
                }
            }

        return True;
        }

    /**
     * Obtain a Sequence that represents the revers order of this Sequence. This is likely to create
     * a new Sequence.
     *
     * @return a Sequence that is in the reverse order as this Sequence
     */
    Sequence reverse()
        {
        Int count = size;
        return count <= 1 ? this : slice(count-1..0);
        }

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
        if (Element.is(Type<Stringable>))
            {
            for (Element v : this)
                {
                capacity += v.estimateStringLength() + 2; // allow for ", "
                }
            }
        else
            {
            for (Element v : this)
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

        if (Element.is(Type<Stringable>))
            {
            Append:
            for (Element v : this)
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
            for (Element v : this)
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
                    v.toString().appendTo(appender);
                    }
                }
            }
        appender.add(']');
        }
    }
