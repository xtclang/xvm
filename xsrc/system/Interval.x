/**
 * An interval is a range whose values are known to be sequential. An interval adds some
 * capabilities, including the ability to union two adjoining ranges, and to iterate over the
 * values in the interval.
 */
mixin Interval<Element extends immutable Sequential>
        into Range<Element>
        implements Iterable<Element>
    {
    /**
     * A IntervalIterator is an Iterator that knows when it is reaching the end of its interval.
     */
    interface IntervalIterator
            extends Iterator<Element>
        {
        @RO Boolean hasNext;
        }

    /**
     * The size of an Interval is defined as the number of Sequential elements represented by the
     * interval.
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
     * Obtain an iterator over all of the values in the interval. Note that the values are iterated
     * in the order that the interval was specified, so if the interval was specified with a higher
     * value first, then the values from the iterator will be in descending order.
     */
    @Override
    IntervalIterator iterator()
        {
        if (reversed)
            {
            return new IntervalIterator()
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
            return new IntervalIterator()
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

    /**
     * Execute the specified function for each element in the Interval.
     *
     * @param process  the function to execute on each element in the Interval
     */
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
     * This is the same as the {@link forEach} method, except that the last value in the Interval is
     * considered to be exclusive of the interval. This means that a hypothetical interval of `0..0`
     * would not have any values processed by this method.
     *
     * @param process  the function to call with each value from the interval
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
     * Two intervals adjoin iff the union of all of the values from both intervals forms a single
     * contiguous Interval.
     */
    Boolean adjoins(Interval that)
        {
        if (this.upperBound < that.lowerBound)
            {
            // this interval precedes that interval
            return this.upperBound.nextValue() == that.lowerBound;
            }
        else if (this.lowerBound > that.upperBound)
            {
            // this interval follows that interval
            return this.lowerBound.prevValue() == that.upperBound;
            }
        else
            {
            return true;
            }
        }

    /**
     * Two intervals that are contiguous or overlap can be joined together to form a larger
     * interval.
     */
    conditional Interval union(Interval that)
        {
        if (!this.adjoins(that))
            {
            return false;
            }

        return true, this.lowerBound.minOf(that.lowerBound) .. this.upperBound.maxOf(that.upperBound);
        }
    }
