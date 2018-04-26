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
    @RO Enumeration<Enum> enumeration.get()
        {
        return meta.class_.parent.as(Enumeration<Enum>);
        }

    /**
     * The ordinal value for this Enum value.
     */
    @RO Int ordinal;

    /**
     * The unique name (within the Enumeration) of this Enum value.
     */
    @RO String name.get()
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
     * Compare two enumerated values that belong to the same enumeration.
     */
    static <CompileType extends Enum> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1.ordinal <=> value2.ordinal;
        }
    }
