/**
 * The Orderable interface represents the general capabilities of data types that can be compared
 * for purposes of ordering.
 */
interface Orderable
        extends Comparable {
    /**
     * Create a Range that represents the values from _this_ (inclusive) **to** _that_
     * (inclusive).
     */
    @Op("..") Range<Orderable> to(Orderable that) {
        assert this.is(immutable) && that.is(immutable);
        return new Range<immutable Orderable>(this, that);
    }

    /**
     * Create a Range that represents the values from _this_ (**exclusive**) **to** _that_
     * (inclusive).
     */
    @Op(">..") Range<Orderable> exTo(Orderable that) {
        assert this.is(immutable) && that.is(immutable);
        return new Range<immutable Orderable>(this, that, firstExclusive=True);
    }

    /**
     * Create a Range that represents the values from _this_ (inclusive) **to** _that_
     * (**exclusive**).
     */
    @Op("..<") Range<Orderable> toEx(Orderable that) {
        assert this.is(immutable) && that.is(immutable);
        return new Range<immutable Orderable>(this, that, lastExclusive=True);
    }

    /**
     * Create a Range that represents the values from _this_ (**exclusive**) **to** _that_
     * (**exclusive**).
     */
    @Op(">..<") Range<Orderable> exToEx(Orderable that) {
        assert this.is(immutable) && that.is(immutable);
        return new Range<immutable Orderable>(this, that, firstExclusive=True, lastExclusive=True);
    }

    /**
     * Return the minimum value of the two specified values. This is the traditional `min` function
     * found in many standard libraries; for example, if `n1` and `n2` are both of type `Int`:
     *
     *     Int minVal = Int.minOf(n1, n2);
     *
     * Alternatively, the function can be invoked on either of the two values, passing the other:
     *
     *     Int minVal = n1.minOf(n2);
     */
    static <CompileType extends Orderable> CompileType minOf(CompileType value1, CompileType value2) {
        return value1 <= value2 ? value1 : value2;
    }

    /**
     * This function is identical to [minOf], but is useful for improving the readability of code.
     * For example, instead of writing:
     *
     *     Int width = size.maxOf(4).minOf(32);
     *
     * Use the following instead:
     *
     *     Int width = size.notLessThan(4).notGreaterThan(32);
     */
    static <CompileType extends Orderable> CompileType notGreaterThan(CompileType value1, CompileType value2) {
        return value1 <= value2 ? value1 : value2;
    }

    /**
     * Return the maximum value of the two specified values. This is the traditional `max` function
     * found in many standard libraries; for example, if `n1` and `n2` are both of type `Int`:
     *
     *     Int maxVal = Int.maxOf(n1, n2);
     *
     * Alternatively, the function can be invoked on either of the two values, passing the other:
     *
     *     Int maxVal = n1.maxOf(n2);
     */
    static <CompileType extends Orderable> CompileType maxOf(CompileType value1, CompileType value2) {
        return value1 >= value2 ? value1 : value2;
    }

    /**
     * This function is identical to [maxOf], but is useful for improving the readability of code.
     * For example, instead of writing:
     *
     *     Int width = size.maxOf(4).minOf(32);
     *
     * Use the following instead:
     *
     *     Int width = size.notLessThan(4).notGreaterThan(32);
     */
    static <CompileType extends Orderable> CompileType notLessThan(CompileType value1, CompileType value2) {
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
