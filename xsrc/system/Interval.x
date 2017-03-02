/**
 * An interval specifies a lower bound and an upper bound.
 */
const Interval<ElementType extends Comparable>
    {
    construct(ElementType first, ElementType last)
        {
        if (first.compareTo(last) == Greater)
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
     * The lower bound of the interval.
     */
    ElementType lowerBound;

    /**
     * The upper bound of the interval.
     */
    ElementType upperBound;

    /**
     * Reversed is true if the interval was specified from its upper bound to its lower bound.
     */
    Boolean reversed;

    /**
     * Determine if the specified value exists within this interval.
     */
    Boolean contains(ElementType value)
        {
        return lowerBound.compareTo(value) != Greater &&
               upperBound.compareTo(value) != Lesser;
        }

    /**
     * This interval contains that interval iff every value within that interval is also in this interval.
     */
    Boolean contains(Interval<ElementType> that)
        {
        return this.lowerBound.compareTo(that.lowerBound) != Greater &&
               this.upperBound.compareTo(that.upperBound) != Lesser;
        }

    /**
     * That interval contains this interval iff every value within this interval is also in that interval.
     */
    Boolean isContainedBy(Interval<ElementType> that)
        {
        return that.contains(this);
        }

    /**
     * Two intervals overlap iff there exists at least one value that is within both intervals.
     */
    Boolean overlaps(Interval<ElementType> that)
        {
        return this.upperBound.compareTo(that.lowerBound) != Lesser &&
               this.lowerBound.compareTo(that.upperBound) != Greater)
        }

    /**
     * The intersection of this interval and that interval is the interval that contains all of the values
     * that exist within both this interval and that interval.
     */
    conditional Interval<ElementType> intersection(Interval<ElementType> that)
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
    conditional Interval<ElementType> union(Interval<ElementType> that)
        {
        if (!this.overlaps(that))
            {
            return false;
            }

        return true, new Interval(this.lowerBound.minOf(that.lowerBound), this.upperBound.maxOf(that.upperBound));
        }
    }