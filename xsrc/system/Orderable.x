/**
 * The Orderable interface represents the general capabilities of data types that can be compared
 * for purposes of ordering.
 */
interface Orderable
    {
    /**
     * Create an Interval that represents the values between _this_ (inclusive) to _that_
     * (inclusive).
     */
    @Op Interval<Orderable> through(Orderable that)
        {
        assert this.is(immutable Object) && that.is(immutable Object);
        return new Interval<immutable Orderable>(this, that);
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
        return this >= that ? this : that;
        }

    /**
     * The existence of a real implementation of comparison for Orderable instances will be checked
     * by the run-time.
     */
    static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1 <=> value2;
        }
    }
