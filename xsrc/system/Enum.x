/**
 * Each value in an {@link Enumeration} is a singleton constant that implements the Enum interface.
 *
 * @see Enumeration
 */
interface Enum
        extends Const
        extends Sequential
    {
    /**
     * The Enumeration that contains this Enum value.
     */
    @ro Enumeration<Enum> enumeration.get()
        {
        return meta.class_.parent.as(Enumeration<Enum>);
        }

    /**
     * The ordinal value for this Enum value.
     */
    @ro Int ordinal;

    /**
     * The unique name (within the Enumeration) of this Enum value.
     */
    @ro String name.get()
        {
        return meta.class_.name;
        }

    /**
     * Obtain the Enum value that follows this Enum in the Enumeration.
     */
    conditional Enum next()
        {
        if (ordinal + 1 < enumeration.count)
            {
            return true, enumeration.values[ordinal + 1];
            }

        return false;
        }

    /**
     * Obtain the Enum value that precedes this Enum in the Enumeration.
     */
    conditional Enum prev()
        {
        if (ordinal > 0)
            {
            return true, enumeration.values[ordinal - 1];
            }

        return false;
        }

    /**
     * Compare two enumerated values for order. If they are both members of the same enumeration,
     * then compare then using their ordinal values; otherwise, use the result of comparing their
     * Enumerations to each other.
     */
    static Ordered compare(Enum value1, Enum value2)
        {
        if (value1.enumeration == value2.enumeration)
            {
            return value1.ordinal <=> value2.ordinal;
            }
        else
            {
            return value1.enumeration <=> value2.enumeration;
            }
        }
    }
