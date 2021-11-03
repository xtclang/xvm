/**
 * Sequential values are values that exist within a series of values. A sequential value is aware of
 * both its next and previous value in the series, if any.
 */
interface Sequential
        extends Orderable
    {
    /**
     * @return the value that precedes this value, if there is one.
     */
    conditional Sequential prev();

    /**
     * @return the value that follows this value, if there is one.
     */
    conditional Sequential next();

    /**
     * @return the value that precedes this value.
     *
     * @throws OutOfBounds  if there is no previous value
     */
    @Op
    Sequential prevValue()
        {
        if (Sequential value := prev())
            {
            return value;
            }

        throw new OutOfBounds();
        }

    /**
     * @return the value that follows this value.
     *
     * @throws OutOfBounds  if there is no next value
     */
    @Op
    Sequential nextValue()
        {
        if (Sequential value := next())
            {
            return value;
            }

        throw new OutOfBounds();
        }

    /**
     * Determine the distance between this value and that value.
     *
     * Consider these examples:
     * * The span from 'a' to 'a' is 0
     * * The span from 'a' to 'z' is 25
     * * The span from 'z' to 'a' is -25
     *
     * This method is conceptually a "sub" method, like a "-" operation (but with the operands
     * reversed). The choice of method name, and the lack of an operator are purposeful, in order
     * to avoid potential conflicts with implementations of the Sequential interface.
     *
     * @param that  another value of this type
     *
     * @return the number of times that next() (or prev(), if negative) would need to be called to
     *         sequentially transition from _this_ to _that_
     *
     * @throws OutOfBounds  if the span cannot be represented in a 64-bit integer value
     */
    Int stepsTo(Sequential that);

    /**
     * Apply a number of steps to this value to find a new value. This is the inverse of the
     * [stepsTo] method.
     *
     * Consider these examples:
     * * The result of adding 0 steps to 'a' is 'a'
     * * The result of adding 25 steps to 'a' is 'z'
     * * The result of adding -25 steps to 'z' is 'a'
     *
     * This method is conceptually an "add" method, or a "+" operation. The choice of method name,
     * and the lack of an operator are purposeful, in order to avoid potential conflicts with
     * implementations of the Sequential interface.
     *
     * @param steps  the number of steps (positive or negative) to apply from this value to create
     *               the new value
     *
     * @return the new value
     *
     * @throws OutOfBounds  if adding the number of specified steps exceeds the range of this
     *                      Sequential type
     */
    Sequential skip(Int steps);
    }
