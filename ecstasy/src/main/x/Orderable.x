/**
 * The Orderable interface represents the general capabilities of data types that can be compared
 * for purposes of ordering.
 */
interface Orderable
        extends Comparable
    {
    /**
     * Create a Range that represents the values from _this_ (inclusive) **to** _that_
     * (inclusive).
     */
    @Op("..") Range<Orderable> to(Orderable that)
        {
        assert this.is(immutable Object) && that.is(immutable Object);
        return new Range<immutable Orderable>(this, that);
        }

    /**
     * Create a Range that represents the values from _this_ (inclusive) **to** _that_
     * (**exclusive**).
     */
    @Op("..<") Range<Orderable> toExcluding(Orderable that)
        {
        assert this.is(immutable Object) && that.is(immutable Object);
        return new Range<immutable Orderable>(this, that, lastExclusive=True);
        }

    /**
     * Return the minimum value of the two.
     */
    static <CompileType extends Orderable> CompileType minOf(CompileType value1, CompileType value2)
        {
        return value1 <= value2 ? value1 : value2;
        }

    /**
     * Return the maximum value of the two.
     */
    static <CompileType extends Orderable> CompileType maxOf(CompileType value1, CompileType value2)
        {
        return value1 >= value2 ? value1 : value2;
        }

    /**
     * Compare two objects of the same type for purposes of ordering.
     *
     * Note: this function must yield "Equal" *if and only if* the result of `equals` function is
     *       "True".
     */
    static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);

    /**
     * Compare two objects of the same Orderable type for equality.
     *
     * Note: this function must yield "True" *if and only if* the result of `compare` function is
     *       "Equal".
     *
     * @return True iff the objects are equivalent
     */
    @Override
    static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
    }
