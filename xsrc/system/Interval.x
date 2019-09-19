/**
 * An interval specifies a lower bound and an upper bound.
 */
const Interval<Element extends Orderable>
// TODO this should be: Interval<Element extends Orderable+Stringable>
// and all casts to <Stringable> removed
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
        return lowerBound.as(Stringable).estimateStringLength() +
               upperBound.as(Stringable).estimateStringLength() + 2;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        lowerBound.as(Stringable).appendTo(appender);
        appender.add("..");
        upperBound.as(Stringable).appendTo(appender);
        }
    }