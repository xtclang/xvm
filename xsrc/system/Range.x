/**
 * A range is an interval whose values are known to be sequential. A range adds some capabilities,
 * including the ability to union two adjoining ranges, and to iterate over the values in the range.
 */
mixin Range<ElementType extends Sequential>
        into Interval<ElementType>
        implements Iterable<ElementType>
    {
    interface RangeIterator<ElementType>
            extends Iterator<ElementType>
        {
        @RO Boolean hasNext;
        }

    /**
     * Obtain an iterator over all of the values in the range. Note that the values are iterated
     * in the order that the range was specified, so if the range was specified with a higher
     * value first, then the values from the iterator will be in descending order.
     */
    @Override
    RangeIterator<ElementType> iterator()
        {
        if (reversed)
            {
            return new RangeIterator<ElementType>()
                {
                private ElementType nextValue = upperBound;

                @Override
                public/private Boolean hasNext = true;

                @Override
                conditional ElementType next()
                    {
                    if (hasNext)
                        {
                        ElementType value = nextValue;
                        if (value == lowerBound)
                            {
                            hasNext = false;
                            }
                        else
                            {
                            nextValue = value.prevValue();
                            }
                        return true, value;
                        }
                    else
                        {
                        return false;
                        }
                    }
                };
            }
        else
            {
            return new RangeIterator<ElementType>()
                {
                private ElementType nextValue = lowerBound;

                @Override
                public/private Boolean hasNext = true;

                @Override
                conditional ElementType next()
                    {
                    if (hasNext)
                        {
                        ElementType value = nextValue;
                        if (value == upperBound)
                            {
                            hasNext = false;
                            }
                        else
                            {
                            nextValue = value.nextValue();
                            }
                        return true, value;
                        }
                    else
                        {
                        return false;
                        }
                    }
                };
            }
        }

    void forEach(function void(ElementType) process)
        {
        if (reversed)
            {
            ElementType value = upperBound;
            do
                {
                process(value);
                value = value.prevValue();
                }
            while (value >= lowerBound);
            }
        else
            {
            ElementType value = lowerBound;
            do
                {
                process(value);
                value = value.nextValue();
                }
            while (value <= upperBound);
            }
        }

    /**
     * This is the same as the {@link forEach} method, except that the last value in the Range is
     * considered to be exclusive of the range. This means that a hypothetical range of {@code 0..0}
     * would not have any values processed by this method.
     *
     * @param process  the function to call with each value from the range
     */
    void forEachExclusive(function void(ElementType) process)
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
            return this.upperBound.nextValue() == that.lowerBound;
            }
        else if (this.lowerBound > that.upperBound)
            {
            // this range follows that range
            return this.lowerBound.prevValue() == that.upperBound;
            }
        else
            {
            return true;
            }
        }

    /**
     * Two ranges that are contiguous or overlap can be joined together to form a larger range.
     */
    conditional Range<ElementType> union(Range<ElementType> that)
        {
        if (!this.adjoins(that))
            {
            return false;
            }

        return true, this.lowerBound.minOf(that.lowerBound) .. this.upperBound.maxOf(that.upperBound);
        }
    }
