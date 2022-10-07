/**
 * Encapsulates the process of converting from one type to another.
 */
@Abstract const Converter<FromType, ToType>
    {
    static const Key(Type FromType, Type ToType);

    /**
     * Each Converter is identified by the combination of convert-from and convert-to type.
     */
    Key key = new Key(FromType, ToType);

    /**
     * Convert the specified value from one type to another.
     *
     * @param input  the value to convert, of type `FromType`
     *
     * @return the converted value, of type `ToType`
     */
    ToType convert(FromType input);
    }
