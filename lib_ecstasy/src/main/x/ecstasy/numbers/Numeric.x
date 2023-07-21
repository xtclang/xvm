/**
 * Numeric is a funky interface used by Number classes to expose their metadata without needing an
 * instance.
 */
interface Numeric
        extends Destringable {
    /**
     * Construct a number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits);

    /**
     * Construct a number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes);

    @Override
    construct(String text);

    /**
     * Determine if the numeric type is a fixed length format.
     *
     * @return True iff the Numeric type is a fixed length format
     * @return (conditional) the number of bits in the format
     */
    static conditional Int fixedBitLength();

    /**
     * Determine the "zero" value for the numeric type.
     *
     * @return the zero value
     */
    static Numeric zero();

    /**
     * Determine the "one" value (the unit value) for the numeric type.
     *
     * @return the unit value
     */
    static Numeric one();

    /**
     * Determine the range of finite values.
     *
     * @return True iff the numeric type has a known range
     * @return (conditional) the range from the lowest value (likely to be either zero or a
     *         negative value) to the highest value
     */
    static conditional Range<Numeric> range();

    /**
     * Obtain a function that converts from this type to the specified numeric type.
     *
     * @param to  the type to convert to
     *
     * @return a function that converts from this type to the specified numeric type
     */
    static <To extends Number> function To(Numeric) converterTo(Type<To> to);

    /**
     * Determine if the numeric type is a fixed length floating point format.
     *
     * @return True iff the Numeric type is a fixed length floating point format
     * @return radix      the radix of the significand
     * @return precision  the precision, in "digits" (of the `radix`) of this floating point
     *                    format
     * @return emax       the maximum exponent value for this floating point format
     * @return emin       the minimum exponent value for the floating point format of this
     *                    format
     * @return bias       the exponent bias for the floating point format of this number
     * @return significandBitLength  the size, in bits, of the significand data in the floating
     *                    point format
     * @return exponentBitLength  the size, in bits, of the exponent data
     */
//        static conditional (Int radix, Int precision, Int emax, Int emin, Int bias,
//                            Int significandBitLength, Int exponentBitLength) fixedLengthFP();
}
