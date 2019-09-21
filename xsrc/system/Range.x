/**
 * A range specifies a lower bound and an upper bound.
 */
const Range<Element extends Orderable>
        incorporates conditional Interval<Element extends Sequential>
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
     * The starting bound of the range.
     */
    Element first.get()
        {
        return reversed ? upperBound : lowerBound;
        }

    /**
     * The ending bound of the range.
     */
    Element last.get()
        {
        return reversed ? lowerBound : upperBound;
        }

    /**
     * The lower bound of the range.
     */
    Element lowerBound;

    /**
     * The upper bound of the range.
     */
    Element upperBound;

    /**
     * Reversed is true if the range was specified from its upper bound to its lower bound.
     */
    Boolean reversed;

    /**
     * Create a new range in the reverse order of this range.
     */
    Range! reverse()
        {
        return reversed ? lowerBound..upperBound : upperBound..lowerBound;
        }

    /**
     * Determine if the specified value exists within this range.
     */
    Boolean contains(Element value)
        {
        return lowerBound <= value && upperBound >= value;
        }

    /**
     * This range contains that range iff every value within that range is also in this range.
     */
    Boolean contains(Range that)
        {
        return this.lowerBound <= that.lowerBound && this.upperBound >= that.upperBound;
        }

    /**
     * That range contains this range iff every value within this range is also in that range.
     */
    Boolean isContainedBy(Range that)
        {
        return that.contains(this);
        }

    /**
     * Two ranges overlap iff there exists at least one value that is within both ranges.
     */
    Boolean overlaps(Range that)
        {
        return this.upperBound >= that.lowerBound && this.lowerBound <= that.upperBound;
        }

    /**
     * The intersection of this range and that range is the range that contains all of the values
     * that exist within both this range and that range.
     */
    conditional Range intersection(Range that)
        {
        if (!this.overlaps(that))
            {
            return false;
            }

        return true, new Range(this.lowerBound.maxOf(that.lowerBound), this.upperBound.minOf(that.upperBound));
        }

    /**
     * Two ranges that are contiguous or overlap can be joined together to form a larger range.
     */
    conditional Range union(Range that)
        {
        if (!this.overlaps(that))
            {
            return false;
            }

        return true, new Range(this.lowerBound.minOf(that.lowerBound), this.upperBound.maxOf(that.upperBound));
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
// TODO GG : COMPILER-56: Could not find a matching method or function "estimateStringLength" for type "Ecstasy:Range.Element + Ecstasy:Stringable". ("x.estimateStringLength()")
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
