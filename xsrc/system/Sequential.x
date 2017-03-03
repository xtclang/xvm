/**
 * Sequential values are values that exist within a series of values. A sequential value is aware of
 * both its next and previous value in the series, if any.
 */
interface Sequential
        extends Orderable
    {
    /**
     * Obtain the value that precedes this value, if there is one.
     */
    conditional Sequential prev();

    /**
     * Obtain the value that follows this value, if there is one.
     */
    conditional Sequential next();

    /**
     * Obtain the value that precedes this value.
     *
     * @exception BoundsException  if there is no previous value
     */
    Sequential prevValue()
        {
        if (Sequential value : prev())
            {
            return value;
            }

        throw new BoundsException();
        }

    /**
     * Obtain the value that follows this value.
     *
     * @exception BoundsException  if there is no next value
     */
    Sequential nextValue();
        {
        if (Sequential value : next())
            {
            return value;
            }

        throw new BoundsException();
        }
    }
