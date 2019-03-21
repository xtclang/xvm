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
     * Determine the distance between this value and that value.
     *
     * Consider these examples:
     * * The span from 'a' to 'a' is 0
     * * The span from 'a' to 'z' is 25
     * * The span from 'z' to 'a' is -25
     *
     * @return the number of times that next() (or prev(), if negative) would need to be called to
     *         sequentially transition from _this_ to _that_
     *
     * @throws OutOfBounds  if the span cannot be represented in a 64-bit integer value
     */
    Int stepsTo(Sequential that);

    /**
     * @return the value that precedes this value.
     *
     * @throws OutOfBounds  if there is no previous value
     */
    Sequential prevValue()
        {
        if (Sequential value : prev())
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
    Sequential nextValue()
        {
        if (Sequential value : next())
            {
            return value;
            }

        throw new OutOfBounds();
        }
    }
