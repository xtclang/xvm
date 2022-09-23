/**
 * Encapsulates the process of converting from one type to another.
 */
@Abstract const Converter<FromType, ToType>
    {
    typedef Tuple<Type, Type> as Key;

    /**
     * Each Converter is identified by the combination of convert-from and convert-to type.
     */
    Tuple<Type<FromType>, Type<ToType>> key = Tuple:(FromType, ToType);

    /**
     * Convert the specified value from one type to another.
     *
     * @param input  the value to convert, of type `FromType`
     *
     * @return the converted value, of type `ToType`
     */
    ToType convert(FromType input);
    }
