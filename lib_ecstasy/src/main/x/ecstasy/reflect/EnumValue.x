import ClassTemplate.Composition;

/**
 * An EnumValue is a class of singleton Enum value objects.
 *
 * @see Enumeration
 */
const EnumValue<Value extends Enum>
        extends Class<Value>
    {
    construct(Composition composition, Enumeration<Value> enumeration)
        {
        super(composition);

        this.enumeration = enumeration;
        }

    /**
     * The Enumeration that contains this Enum value.
     */
    Enumeration<Value> enumeration;

    /**
     * The singleton Enum value of this EnumValue class.
     */
    Value value.get()
        {
        assert Value value := enumeration.byName.get(name);
        return value;
        }
    }
