/**
 * The Comparable interface allows two values of the same type to be compared for purposes of
 * ordering.
 */
interface Comparable
    {
    Ordered compareTo(Comparable that);

    /**
     * Create a Range that represents the range of values from _this_ to _that_.
     */
    @op Range<Comparable> to(Comparable that)
        {
        return new Range<Comparable>(this, that);
        }

    /**
     * Return the minimum value of this and that.
     */
    Comparable minOf(Comparable that)
        {
        return (this.compareTo(that) == Lesser) ? this : that;
        }

    /**
     * Return the maximum value of this and that.
     */
    Comparable maxOf(Comparable that)
        {
        return (this.compareTo(that) == Greater) ? this : that;
        }
    }
