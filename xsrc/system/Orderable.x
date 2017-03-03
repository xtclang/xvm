/**
 * The Orderable interface allows two values of the same type to be compared for purposes of
 * ordering.
 */
interface Orderable
    {
    /**
     * Create a Range that represents the range of values from _this_ to _that_.
     */
    @op Range<Orderable> to(Orderable that)
        {
        return new Range<Comparable>(this, that);
        }

    /**
     * Return the minimum value of this and that.
     */
    Orderable minOf(Orderable that)
        {
        return this < that ? this : that;
        }

    /**
     * Return the maximum value of this and that.
     */
    Orderable maxOf(Orderable that)
        {
        return this > that ? this : that;
        }
    }
