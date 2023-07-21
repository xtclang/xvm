/**
 * Number represents the properties and operations available on every numeric type included in
 * Ecstasy.
 *
 * Numbers are constant values, represented internally as an array of bits; for example, an 8-bit
 * integer value for `3` is represented by the bits `00000011', in which the left-most bit is the
 * _Most Significant Bit_ (MSB) and the rightmost bit is the _Least Significant Bit_ (LSB).
 *
 * Arrays are normally left-to-right (or top-to-bottom) in nature, but bits of a number are often
 * identified in the opposite order; the Least Significant Bit (LSB) is often referred to as bit
 * number 0, and each bit of greater significance has a one-higher bit number than the last, with
 * the Most Significant Bit (MSB) of an n-bit number being bit number `n-1`. Since this is the
 * opposite of the order that the bits appear in the array of bits used to create a number, the
 * `Array.reversed()` method can be used to obtain an array in LSB-to-MSB order.
 *
 * Numbers can also be instantiated from an array of bytes, in a left-to-right order, as they would
 * appear when communicated over the network, or as they would be stored in a file. To obtain the
 * bytes from a number in the left-to-right order from a Number, use the `toByteArray()` method.
 */
@Abstract const Number
        implements Numeric
        implements IntConvertible
        implements FPConvertible
        implements Orderable {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        this.bits = bits;
    }

    /**
     * Construct a number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        construct Number(bytes.toBitArray());
    }


    // ----- related types -------------------------------------------------------------------------

    /**
     * Signum represents the sign of the number, whether zero, negative or positive.
     */
    enum Signum(String prefix, IntLiteral factor, Ordered ordered) {
        Negative("-", -1, Lesser ),
        Zero    ("" ,  0, Equal  ),
        Positive("+", +1, Greater)
    }

    /**
     * An IllegalMath exception is raised to indicate any operation that violates mathematical
     * rules.
     */
    static const IllegalMath(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * A DivisionByZero exception is raised to indicate that an attempt to divide-by-zero has
     * occurred.
     */
    static const DivisionByZero(String? text = Null, Exception? cause = Null)
            extends IllegalMath(text, cause);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The actual array of bits representing this number, ordered from left-to-right, Most
     * Significant Bit (MSB) to Least Significant Bit (LSB).
     */
    Bit[] bits;

    /**
     * The number of bits that the number uses.
     */
    @RO Int bitLength.get() {
        return bits.size;
    }

    /**
     * The number of bytes that this number uses for its storage. In the case of a number that does
     * not use an exact number of bytes, the number is rounded up to the closest byte.
     */
    @RO Int byteLength.get() {
        return (bitLength + 7) / 8;
    }

    /**
     * True if the numeric type is signed (has the potential to hold positive or negative values);
     * False if unsigned (representing only a magnitude).
     */
    @RO Boolean signed.get() {
        return True;
    }

    /**
     * The Sign of the number. The case of [Zero] is special, in that some numeric formats have an
     * explicitly signed zero value; in that case, using the
     */
    @RO Signum sign;

    /**
     * The explicit negative sign of this number. This method only differs from the `sign==Negative`
     * when the value is a negative zero.
     */
    @RO Boolean negative.get() {
        return sign == Negative;
    }

    /**
     * True iff the floating point value is a finite value, indicating that it is neither infinity
     * nor Not-a-Number (`NaN`).
     */
    @RO Boolean finite.get() {
        return !infinity && !NaN;
    }

    /**
     * True iff the this Number is positive infinity or negative infinity. Some forms of Numbers
     * such as floating point values can be infinite as the result of math overflow, for example.
     */
    @RO Boolean infinity.get() {
        return False;
    }

    /**
     * True iff the this Number is a `NaN` (_Not-a-Number_). Some forms of Numbers such as floating
     * point values can be `NaN` as the result of math underflow, for example.
     */
    @RO Boolean NaN.get() {
        return False;
    }

    /**
     * The magnitude of this number (its distance from zero), which may use a different Number type
     * if the magnitude cannot be represented by the type of this value.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    @RO Number! magnitude.get() {
        return abs();
    }


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Calculate the negative of this number.
     *
     * @return the negative of this number, generally equal to `0-this`
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if no corresponding negated value is possible to express with this type
     */
    @Op("-#") Number neg();

    /**
     * Addition: Add another number to this number, and return the result.
     *
     * @param n  the number to add to this number (the addend)
     *
     * @return the resulting sum
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("+") Number add(Number n);

    /**
     * Subtraction: Subtract another number from this number, and return the result.
     *
     * @param n  the number to subtract from this number (the subtrahend)
     *
     * @return the resulting difference
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("-") Number sub(Number n);

    /**
     * Multiplication: Multiply this number by another number, and return the result.
     *
     * @param n  the number to multiply this number by
     *
     * @return the resulting product
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("*") Number mul(Number n);

    /**
     * Division: Divide this number by another number, and return the result.
     *
     * @param n  the divisor to divide this number by
     *
     * @return the resulting quotient
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("/") Number div(Number n);

    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     * Note that the result is the modulo, and not the remainder.
     *
     * @param n  the divisor to divide this number by
     *
     * @return the resulting modulo, in the range `0..<n` for a positive divisor, and in the range
     *         `n>..0` for a negative divisor
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    @Op("%") Number mod(Number n);

    /**
     * Division and Remainder: Divide this number by another number, and return both the
     * quotient and the remainder (not the modulo).
     *
     * @param n  the divisor to divide this number by
     *
     * @return quotient   the resulting quotient
     * @return remainder  the resulting remainder
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("/%") (Number quotient, Number remainder) divrem(Number n) {
        Number quotient  = this / n;
        Number remainder = this - (n * quotient);
        return quotient, remainder;
    }


    // ----- other operations ----------------------------------------------------------------------

    /**
     * Remainder: Return the remainder that would result from dividing this number by another
     * number. Note that the remainder is the same as the modulo for unsigned dividend values
     * and for signed dividend values that are zero or positive, but for signed dividend values
     * that are negative, the remainder will be zero or negative.
     *
     * @param n  the number to divide this number by
     *
     * @return the resulting remainder
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    Number remainder(Number n) {
        return this - (this / n * n);
    }

    /**
     * Calculate the absolute value of this number. If there is no absolute value representable
     * using this number's type, then an exception is thrown; this can happen for a signed integer
     * of the minimum value for that integer type, since the positive range for a 2's-complement
     * signed integer is always one element smaller than the negative range.
     *
     * @return the absolute value of this number
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    Number abs() {
        if (sign != Negative) {
            return this;
        }

        Number n = -this;
        assert:bounds n.sign != Negative;
        return n;
    }

    /**
     * Calculate this number raised to the specified power.
     *
     * @param n  the exponent value
     *
     * @return the result of raising this number to the power of the specified exponent
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  iff this type is bounded and the resulting value would be beyond the
     *                      bounds of this type
     */
    Number pow(Number n);


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the number as an array of bits, in left-to-right order.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the number as an array of bits
     */
    Bit[] toBitArray(Array.Mutability mutability = Constant) {
        return bits.toArray(mutability);
    }

    /**
     * Obtain the number as an array of nibbles, in left-to-right order.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the number as an array of nibbles
     */
    Nibble[] toNibbleArray(Array.Mutability mutability = Constant) {
        return bits.toNibbleArray(mutability);
    }

    /**
     * Obtain the number as an array of bytes, in left-to-right order.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the number as an array of bytes
     */
    Byte[] toByteArray(Array.Mutability mutability = Constant) {
        return bits.toByteArray(mutability);
    }

    /**
     * Obtain a function that converts from the first specified numeric type to the second
     * specified numeric type.
     *
     * @param from  the type to convert from
     * @param to    the type to convert to
     *
     * @return a function that converts from the `from` type and to the `to` type
     */
    static <From extends Number!, To extends Number!> function To(From) converterFor(Type<From> from, Type<To> to) {
        return From.converterTo(to);
    }


    // ----- Numeric interface ---------------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return False;
    }

    @Override
    static conditional Range<Number> range() {
        return False;
    }

    @Override
    static <To extends Number!> function To (Number) converterTo(Type<To> to) {
        return switch (to) {
            case Int8               : n -> n.toInt8()    .as(To);
            case Int16              : n -> n.toInt16()   .as(To);
            case Int32              : n -> n.toInt32()   .as(To);
            case Int64              : n -> n.toInt64()   .as(To);
            case Int128             : n -> n.toInt128()  .as(To);
            case IntN               : n -> n.toIntN()    .as(To);

            case UInt8              : n -> n.toUInt8()   .as(To);
            case UInt16             : n -> n.toUInt16()  .as(To);
            case UInt32             : n -> n.toUInt32()  .as(To);
            case UInt64             : n -> n.toUInt64()  .as(To);
            case UInt128            : n -> n.toUInt128() .as(To);
            case UIntN              : n -> n.toUIntN()   .as(To);

            case Dec32              : n -> n.toDec32()   .as(To);
            case Dec64              : n -> n.toDec64()   .as(To);
            case Dec128             : n -> n.toDec128()  .as(To);
            case DecN               : n -> n.toDecN()    .as(To);

            case Float8e4           : n -> n.toFloat8e4().as(To);
            case Float8e5           : n -> n.toFloat8e5().as(To);
            case BFloat16           : n -> n.toBFloat16().as(To);
            case Float16            : n -> n.toFloat16() .as(To);
            case Float32            : n -> n.toFloat32() .as(To);
            case Float64            : n -> n.toFloat64() .as(To);
            case Float128           : n -> n.toFloat128().as(To);
            case FloatN             : n -> n.toFloatN()  .as(To);

            default: assert as $"unsupported convert-to type: {to}";
        };
    }


    // ----- Stringable support --------------------------------------------------------------------

    /**
     * The representations for "digits" in any radix up to 16 (hexadecimal).
     */
    static Char[] Digits = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'];
}