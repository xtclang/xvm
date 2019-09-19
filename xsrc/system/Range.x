/**
 * A range is an interval whose values are known to be sequential. A range adds some capabilities,
 * including the ability to union two adjoining ranges, and to iterate over the values in the range.
 */
mixin Range<Element extends immutable Sequential>
        into Interval<Element>
        implements Iterable<Element>
    {
    /**
     * A RangeIterator is an Iterator that knows when it is reaching the end of its range.
     */
    interface RangeIterator
            extends Iterator<Element>
        {
        @RO Boolean hasNext;
        }

    /**
     * The size of a Range is defined as the number of Sequential elements represented by the range.
     *
     * Consider these examples:
     * * The size of ['a'..'a'] is 1
     * * The size of ['a'..'z'] is 26
     * * The size of ['z'..'a'] is 26
     */
    @Override
    Int size.get()
        {
        return lowerBound.stepsTo(upperBound) + 1;
        }

    /**
     * Obtain an iterator over all of the values in the range. Note that the values are iterated
     * in the order that the range was specified, so if the range was specified with a higher
     * value first, then the values from the iterator will be in descending order.
     */
    @Override
    RangeIterator iterator()
        {
        if (reversed)
            {
            return new RangeIterator()
                {
                private Element nextValue = upperBound;

                @Override
                public/private Boolean hasNext = true;

                @Override
                conditional Element next()
                    {
                    if (hasNext)
                        {
                        Element value = nextValue;
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
            return new RangeIterator()
                {
                private Element nextValue = lowerBound;

                @Override
                public/private Boolean hasNext = true;

                @Override
                conditional Element next()
                    {
                    if (hasNext)
                        {
                        Element value = nextValue;
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

    void forEach(function void(Element) process)
        {
        if (reversed)
            {
            Element value = upperBound;
            do
                {
                process(value);
                value = value.prevValue();
                }
            while (value >= lowerBound);
            }
        else
            {
            Element value = lowerBound;
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
    void forEachExclusive(function void(Element) process)
        {
        if (reversed)
            {
            Element value = upperBound;
            while (value > lowerBound)
                {
                process(value);
                value = value.prevValue();
                }
            }
        else
            {
            Element value = lowerBound;
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
    Boolean adjoins(Range that)
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
    conditional Range union(Range that)
        {
        if (!this.adjoins(that))
            {
            return false;
            }

        return true, this.lowerBound.minOf(that.lowerBound) .. this.upperBound.maxOf(that.upperBound);
        }
    }
