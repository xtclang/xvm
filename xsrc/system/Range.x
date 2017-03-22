/**
 * A range is an interval whose values are known to be sequential. A range adds some capabilities,
 * including the ability to union two adjoining ranges, and to iterate over the values in the range.
 */
@auto mixin Range<ElementType>
        into Interval<Sequential>
        implements Iterable<ElementType>
    {
    /**
     * Obtain an iterator over all of the values in the range. Note that the values are iterated
     * in the order that the range was specified, so if the range was specified with a higher
     * value first, then the values from the iterator will be in descending order.
     */
    @Override
    Iterator<ElementType> iterator()
        {
        if (reversed)
            {
            return new Iterator<ElementType>()
                {
                private ElementType nextValue = upperBound;
                private Boolean done = false;

                conditional ElementType next()
                    {
                    if (done)
                        {
                        return false;
                        }

                    ElementType value = nextValue;
                    if (value == lowerBound || !(nextValue : value.prev()))
                        {
                        done = true;
                        }

                    return true, value;
                    }
                }
            }
        else
            {
            return new Iterator<ElementType>()
                {
                private ElementType nextValue = lowerBound;
                private Boolean done = false;

                conditional ElementType next()
                    {
                    if (done)
                        {
                        return false;
                        }

                    ElementType value = nextValue;
                    if (value == upperBound || !(nextValue : value.next()))
                        {
                        done = true;
                        }

                    return true, value;
                    }
                }
            }
        }

    @Override
    Void forEach(function Void(ElementType) process)
        {
        if (reversed)
            {
            ElementType value = upperBound;
            do
                {
                process(value);
                value = value.prevValue();
                }
            while (value >= lowerBound)
            }
        else
            {
            ElementType value = lowerBound;
            do
                {
                process(value);
                value = value.nextValue();
                }
            while (value <= upperBound)
            }
        }

    /**
     * This is the same as the {@link forEach} method, except that the last value in the Range is
     * considered to be exclusive of the range. This means that a hypothetical range of {@code 0..0}
     * would not have any values processed by this method.
     *
     * @param process  the function to call with each value from the range
     */
    Void forEachExclusive(function Void(ElementType) process)
        {
        if (reversed)
            {
            ElementType value = upperBound;
            while (value > lowerBound)
                {
                process(value);
                value = value.prevValue();
                }
            }
        else
            {
            ElementType value = lowerBound;
            while (value < upperBound)
                {
                process(value);
                value = value.nextValue();
                }
            }
        }

    /**
     * Two ranges adjoin iff the union of all of the values from both ranges forms a single
     * contiguous Range.
     */
    Boolean adjoins(Range<ElementType> that)
        {
        if (this.upperBound < that.lowerBound)
            {
            // this range precedes that range
            return this.upperBound.nextValue == that.lowerBound;
            }
        else if (this.lowerBound > that.upperBound)
            {
            // this range follows that range
            return this.lowerBound.prevValue == that.upperBound;
            }
        else
            {
            return true;
            }
        }

    /**
     * Two ranges that are contiguous or overlap can be joined together to form a larger range.
     */
    @Override
    conditional Range<ElementType> union(Range<ElementType> that)
        {
        if (!this.adjoins(that))
            {
            return false;
            }

        return true, this.lowerBound.minOf(that.lowerBound) .. this.upperBound.maxOf(that.upperBound);
        }
    }
