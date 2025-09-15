/**
 * An interface that allows a class to optimize its rendering into a [String], by allowing the full
 * length of the resulting `String` to be calculated first, and then using that length to
 * pre-allocate a buffer of that size into which the object can build its `String` representation.
 *
 * In general, it is ideal for the [String] value of a [Stringable] object to be built entirely from
 * `Stringable` properties and values, so that the implementation of the [estimateStringLength] can
 * correctly determine the resulting `String` size, which minimizes the number of object allocations
 * and memory copies necessary to render a `String` for the [Object].
 */
interface Stringable {
    /**
     * Estimate the number of characters that this [Stringable] object will use to render a [String]
     * representation of itself.
     *
     * This is intended primarily for debugging, log messages, and other diagnostic features. The
     * default implementation returns the estimate for the size of the String that would be returned
     * from the default implementation of [Object.toString()].
     *
     * @return the estimated [String] size
     */
    Int estimateStringLength();

    /**
     * Render the [String] form of this [Stringable] object into the provided
     * [Appender<Char>](Appender).
     *
     * @param buf  the [Appender] to append the [String] form of this object to
     *
     * @return the provided [Appender<Char>](Appender)
     */
    Appender<Char> appendTo(Appender<Char> buf);

    @Override
    String toString() = appendTo(new StringBuffer(estimateStringLength())).toString();
}
