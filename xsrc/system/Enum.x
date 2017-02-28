/**
 * Each value in an {@link Enumeration} is a singleton constant that implements the Enum interface.
 *
 * @see Enumeration
 */
interface Enum
        extends Const
    {
    /**
     * The Enumeration that contains this Enum value.
     */
    @ro Enumeration enumeration
        {
        Class+Enumeration clz = (Class+Enumeration) meta.class;
        if (clz.parent instanceof Class+Enumeration && clz.extends(clz.parent))
            {
            clz = clz.parent;
            }
        return &clz.narrowTo(Enumeration);
        }

    /**
     * The ordinal value for this Enum value.
     */
    @ro Int ordinal;

    /**
     * The unique name (within the Enumeration) of this Enum value.
     */
    @ro String name
        {
        return meta.class.name;
        }

    /**
     * Obtain the Enum value that follows this Enum in the Enumeration.
     */
    conditional Enum next()
        {
        if (ordinal + 1 < enumeration.count)
            {
            return enumeration.values[ordinal + 1];
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
            return enumeration.values[ordinal - 1];
            }

        return false;
        }

    /**
     * Determine the enum with the smaller ordinal of this enum and the passed enum.
     */
    Enum atMost(Enum that)
        {
        return this < this ? that : this;
        }

    /**
     * Determine the larger of this number and the passed number.
     */
    Enum atLeast(Enum that)
        {
        return that > this ? that : this;
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
