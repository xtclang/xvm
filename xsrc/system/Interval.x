/**
 * An interval specifies a lower bound and an upper bound.
 */
const Interval<Element extends Orderable>
        incorporates conditional Range<Element extends Sequential>
    {
    construct(Element first, Element last)
        {
        if (first > last)
            {
            lowerBound = last;
            upperBound = first;
            reversed   = true;
            }
        else
            {
            lowerBound = first;
            upperBound = last;
            reversed   = false;
            }
        }

    /**
     * The starting bound of the interval.
     */
    Element first.get()
        {
        return reversed ? upperBound : lowerBound;
        }

    /**
     * The ending bound of the interval.
     */
    Element last.get()
        {
        return reversed ? lowerBound : upperBound;
        }

    /**
     * The lower bound of the interval.
     */
    Element lowerBound;

    /**
     * The upper bound of the interval.
     */
    Element upperBound;

    /**
     * Reversed is true if the interval was specified from its upper bound to its lower bound.
     */
    Boolean reversed;

    /**
     * Create a new interval in the reverse order of this interval.
     */
    Interval! reverse()
        {
        return reversed ? lowerBound..upperBound : upperBound..lowerBound;
        }

    /**
     * Determine if the specified value exists within this interval.
     */
    Boolean contains(Element value)
        {
        return lowerBound <= value && upperBound >= value;
        }

    /**
     * This interval contains that interval iff every value within that interval is also in this interval.
     */
    Boolean contains(Interval that)
        {
        return this.lowerBound <= that.lowerBound && this.upperBound >= that.upperBound;
        }

    /**
     * That interval contains this interval iff every value within this interval is also in that interval.
     */
    Boolean isContainedBy(Interval that)
        {
        return that.contains(this);
        }

    /**
     * Two intervals overlap iff there exists at least one value that is within both intervals.
     */
    Boolean overlaps(Interval that)
        {
        return this.upperBound >= that.lowerBound && this.lowerBound <= that.upperBound;
        }

    /**
     * The intersection of this interval and that interval is the interval that contains all of the values
     * that exist within both this interval and that interval.
     */
    conditional Interval intersection(Interval that)
        {
        if (!this.overlaps(that))
            {
            return false;
            }

        return true, new Interval(this.lowerBound.maxOf(that.lowerBound), this.upperBound.minOf(that.upperBound));
        }

    /**
     * Two intervals that are contiguous or overlap can be joined together to form a larger interval.
     */
    conditional Interval union(Interval that)
        {
        if (!this.overlaps(that))
            {
            return false;
            }

        return true, new Interval(this.lowerBound.minOf(that.lowerBound), this.upperBound.maxOf(that.upperBound));
        }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int estimate = 2;
        if (Element.is(Type<Stringable>))
            {
            estimate += lowerBound.estimateStringLength()
                      + upperBound.estimateStringLength();
            }
        else
            {
// TODO GG : COMPILER-56: Could not find a matching method or function "estimateStringLength" for type "Ecstasy:Interval.Element + Ecstasy:Stringable". ("x.estimateStringLength()")
//            estimate += lowerBound.is(Stringable) ? lowerBound.estimateStringLength() : 4;
//            estimate += upperBound.is(Stringable) ? upperBound.estimateStringLength() : 4;
            estimate += 8;
            }
        return estimate;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        if (Element.is(Type<Stringable>))
            {
            lowerBound.appendTo(appender);
            appender.add("..");
            upperBound.appendTo(appender);
            }
        else
            {
            (lowerBound.is(Stringable) ? lowerBound : lowerBound.toString()).appendTo(appender);
            appender.add("..");
            (upperBound.is(Stringable) ? upperBound : upperBound.toString()).appendTo(appender);
            }
        }
    }