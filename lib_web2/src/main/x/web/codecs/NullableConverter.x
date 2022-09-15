/**
 * Adds `Null` as an optional input and output value to a converter.
 */
const NullableConverter<FromNonNullableType, ToNonNullableType>
        (Converter<FromNonNullableType, ToNonNullableType> nonNullableConverter)
        extends Converter<Nullable|FromNonNullableType, Nullable|ToNonNullableType>
    {
    @Override
    ToType convert(FromType input)
        {
        return input == Null
                ? Null
                : nonNullableConverter.convert(input);
        }
    }
