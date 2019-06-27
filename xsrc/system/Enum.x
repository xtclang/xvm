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
    @RO Enumeration<Enum> enumeration;

    /**
     * The ordinal value for this Enum value.
     */
    @RO Int ordinal;

    /**
     * The unique name (within the Enumeration) of this Enum value.
     */
    @RO String name;

    /**
     * Obtain the Enum value that follows this Enum in the Enumeration.
     */
    @Override
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
    @Override
    conditional Enum prev()
        {
        if (ordinal > 0)
            {
            return true, enumeration.values[ordinal - 1];
            }

        return false;
        }

    /**
     * Calculate a hash code for the specified Enum value.
     */
    static <CompileType extends Enum> Int hashCode(CompileType value)
        {
        return value.ordinal;
        }

    /**
     * Compare two enumerated values that belong to the same enumeration purposes of ordering.
     */
    static <CompileType extends Enum> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1.ordinal <=> value2.ordinal;
        }

    /**
     * Compare two enumerated values that belong to the same enumeration for equality.
     */
    static <CompileType extends Enum> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.ordinal == value2.ordinal;
        }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return name.size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        return appender.add(name);
        }
    }
