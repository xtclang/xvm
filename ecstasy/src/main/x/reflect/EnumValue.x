import ClassTemplate.Composition;

/**
 * An EnumValue is a class of singleton Enum value objects.
 *
 * @see Enumeration
 */
const EnumValue<BaseType extends Enum>
        extends Class<BaseType>
    {
    construct(Composition composition, Enumeration<BaseType> enumeration)
        {
        construct Class(composition);

        this.enumeration = enumeration;
        }

    /**
     * The Enumeration that contains this Enum value.
     */
    Enumeration<BaseType> enumeration;

    /**
     * The singleton Enum value of this EnumValue class.
     */
    BaseType value.get()
        {
        assert BaseType value := enumeration.byName.get(name);
        return value;
        }
    }
