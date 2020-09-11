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
     * Determine the first value that could exist in the range. Note that the value may not actually
     * exist in the range, because the lower and upper bound may preclude it, as would occur in an
     * integer range of `(0, 1)`, for example.
     *
     * @return the first value that would exist in the range, assuming at least one value exists in
     *         the range
     */
    Element effectiveFirst.get()
        {
        return descending ? effectiveUpperBound : effectiveLowerBound;
        }

    /**
     * Determine the last value that could exist in the range. Note that the value may not actually
     * exist in the range, because the lower and upper bound may preclude it, as would occur in an
     * integer range of `(0, 1)`, for example.
     *
     * @return the last value that would exist in the range, assuming at least one value exists in
     *         the range
     */
    Element effectiveLast.get()
        {
        return descending ? effectiveLowerBound : effectiveUpperBound;
        }

    /**
     * Determine the lowest value that could exist in the range. Note that the value may not
     * actually exist in the range, because the upper bound may preclude it, as would occur in an
     * integer range of `(0, 1)`, for example.
     *
     * @return the lowest value that could exist in the range
     */
    Element effectiveLowerBound.get()
        {
        return lowerExclusive ? lowerBound.nextValue() : lowerBound;
        }

    /**
     * Determine the highest value that could exist in the range. Note that the value may not
     * actually exist in the range, because the lower bound may preclude it, as would occur in an
     * integer range of `(0, 1)`, for example.
     *
     * @return the highest value that could exist in the range
     */
    Element effectiveUpperBound.get()
        {
        return upperExclusive ? upperBound.prevValue() : upperBound;
        }

    /**
     * The size of an Interval is defined as the number of steps from the lower bound of the
     * interval to the higher bound of the interval.
     *
     * Consider these examples:
     * * The size of ['a'..'a'] is 1
     * * The size of ['a'..'a') is 0
     * * The size of ['a'..'b'] is 2
     * * The size of ['a'..'b') is 1
     * * The size of ['a'..'z'] is 26
     * * The size of ['a'..'z') is 25
     * * The size of ['z'..'a'] is 26
     * * The size of ['z'..'a') is 25
     */
    @Override
    Int size.get()
        {
        try
            {
            Element lo = effectiveLowerBound;
            Element hi = effectiveUpperBound;
            return switch (lo <=> hi)
                {
                case Lesser : lo.stepsTo(hi) + 1;
                case Equal  : 1;
                case Greater: 0;
                };
            }
        catch (OutOfBounds e)
            {
            return 0;
            }
        }

    /**
     * Obtain the specified element from the interval.
     */
    @Op("[]") Element getElement(Int index)
        {
        assert:bounds 0 <= index < size;
        return effectiveFirst.skip(descending ? -index : index);
        }

    /**
     * Obtain an iterator over all of the values in the interval. Note that the values are iterated
     * in the order that the interval was specified, so if the interval was specified with a higher
     * value first, then the values from the iterator will be in descending order.
     */
    @Override
    IntervalIterator iterator()
        {
        Element lo;
        Element hi;
        try
            {
            lo = effectiveLowerBound;
            hi = effectiveUpperBound;
            }
        catch (OutOfBounds e)
            {
            return new IntervalIterator()
                {
                @Override
                public Boolean hasNext.get()
                    {
                    return False;
                    }

                @Override
                conditional Element next()
                    {
                    return False;
                    }
                };
            }

        if (descending)
            {
            return new IntervalIterator()
                {
                private Element nextValue = hi;
                private Element lastValue = lo;

                @Override
                public/private Boolean hasNext = nextValue >= lastValue;

                @Override
                conditional Element next()
                    {
                    if (hasNext)
                        {
                        Element value = nextValue;
                        if (value == lastValue)
                            {
                            hasNext = False;
                            }
                        else
                            {
                            nextValue = value.prevValue();
                            }
                        return True, value;
                        }
                    else
                        {
                        return False;
                        }
                    }
                };
            }
        else
            {
            return new IntervalIterator()
                {
                private Element nextValue = lo;
                private Element lastValue = hi;

                @Override
                public/private Boolean hasNext = nextValue <= lastValue;

                @Override
                conditional Element next()
                    {
                    if (hasNext)
                        {
                        Element value = nextValue;
                        if (value == lastValue)
                            {
                            hasNext = False;
                            }
                        else
                            {
                            nextValue = value.nextValue();
                            }
                        return True, value;
                        }
                    else
                        {
                        return False;
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
        iterator().forEach(process);
        }

    /**
     * Two intervals adjoin iff the union of all of the values from both intervals forms a single
     * contiguous Interval.
     */
    @Override
    Boolean adjoins(Interval that)
        {
        return switch (that.lowerBound <=> this.upperBound, that.upperBound <=> this.lowerBound)
            {
            case (Greater, _      ): that.effectiveLowerBound.prevValue() <= this.effectiveUpperBound; // TODO handle OutOfBounds
            case (_      , Lesser ): that.effectiveUpperBound.nextValue() >= this.effectiveLowerBound; // TODO handle OutOfBounds
            case (Lesser , Greater): True;                                       // between bounds
            case (Lesser , Equal  ): !this.lowerExclusive | !that.upperExclusive;// at lower bound
            case (Equal  , Greater): !this.upperExclusive | !that.lowerExclusive;// at upper bound
            case (Equal  , Equal  ): True;                                       // zero length!
            };
        }
    }
