/**
 * The Number interface represents the properties and operations available on every
 * numeric type included in Ecstasy.
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
const Number
        implements Orderable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    protected construct(Bit[] bits)
        {
        this.bits = bits;
        }

    /**
     * Construct a number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    protected construct(Byte[] bytes)
        {
        construct Number(bytes.toBitArray());
        }


    // ----- related types -------------------------------------------------------------------------

    /**
     * Signum represents the sign of the number, whether zero, negative or positive.
     */
    enum Signum(String prefix, IntLiteral factor, Ordered ordered)
        {
        Negative("-", -1, Lesser ),
        Zero    ("" ,  0, Equal  ),
        Positive("+", +1, Greater)
        }

    /**
     * An IllegalMath exception is raised when an assert fails.
     */
    static const IllegalMath(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * An Assertion exception is raised when an assert fails.
     */
    static const DivisionByZero(String? text = Null, Exception? cause = Null)
            extends IllegalMath(text, cause);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The actual array of bits representing this number, ordered from left-to-right, Most
     * Significant Bit (MSB) to Least Significant Bit (LSB).
     */
    protected Bit[] bits;

    /**
     * The number of bits that the number uses.
     */
    Int bitLength.get()
        {
        return bits.size;
        }

    /**
     * The number of bytes that the number uses.
     */
    Int byteLength.get()
        {
        return (bitLength + 7) / 8;
        }

    /**
     * True if the numeric type is signed (has the potential to hold positive or negative values);
     * False if unsigned (representing only a magnitude).
     */
    Boolean signed.get()
        {
        return True;
        }

    /**
     * The Sign of the number.
     */
    @Abstract @RO Signum sign;

    /**
     * The magnitude of this number (its distance from zero), which may use a different Number type
     * if the magnitude cannot be represented by the type of this value.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    Number! magnitude.get()
        {
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
    @Op("-#")
    Number neg();

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
    @Op("+")
    Number add(Number n);

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
    @Op("-")
    Number sub(Number n);

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
    @Op("*")
    Number mul(Number n);

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
    @Op("/")
    Number div(Number n);

    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     * Note that the result is the modulo, and not the remainder.
     *
     * @param n  the divisor to divide this number by
     *
     * @return the resulting modulo, in the range `[0..n)` for a positive divisor, and in the range
     *         `(n..0]` for a negative divisor
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    @Op("%")
    Number mod(Number n);

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
    @Op("/%")
    (Number quotient, Number remainder) divrem(Number n)
        {
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
    Number remainder(Number n)
        {
        return this - (this / n * n);
        }

    /**
     * Calculate the absolute value of this number. If there is no absolute value representable
     * using this number's type, then an exception is thrown; this can happen for a signed integer
     * of the minimum value for that integer type, since the positive range for a 2s-complement
     * signed integer is always one smaller than the negative range.
     *
     * @return the absolute value of this number
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    Number abs()
        {
        if (sign != Negative)
            {
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
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    Number pow(Number n);


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the number as an array of bits, in left-to-right order.
     *
     * @return the number as an array of bits
     */
    immutable Bit[] toBitArray()
        {
        return bits.as(immutable Bit[]);
        }

    /**
     * Obtain the number as an array of bytes, in left-to-right order.
     *
     * @return the number as an array of bytes
     */
    immutable Byte[] toByteArray()
        {
        return toBitArray().toByteArray();
        }

    /**
     * Convert the number to a variable-length signed integer.
     *
     * @return the number as a signed integer of variable length
     */
    IntN toIntN();

    /**
     * Convert the number to a signed 8-bit integer.
     *
     * @return the number as a signed 8-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
     */
    Int8 toInt8()
        {
        return toIntN().toInt8();
        }

    /**
     * Convert the number to a signed 16-bit integer.
     *
     * @return the number as a signed 16-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
     */
    Int16 toInt16()
        {
        return toIntN().toInt16();
        }

    /**
     * Convert the number to a signed 32-bit integer.
     *
     * @return the number as a signed 32-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
     */
    Int32 toInt32()
        {
        return toIntN().toInt32();
        }

    /**
     * Convert the number to a signed 64-bit integer.
     *
     * @return the number as a signed 64-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
     */
    Int64 toInt()
        {
        return toIntN().toInt();
        }

    /**
     * Convert the number to a signed 128-bit integer.
     *
     * @return the number as a signed 128-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
     */
    Int128 toInt128()
        {
        return toIntN().toInt128();
        }

    /**
     * Convert the number to a variable-length unsigned integer.
     *
     * @return the number as an unsigned integer of variable length
     */
    UIntN toUIntN();

    /**
     * Convert the number to an unsigned 8-bit integer.
     *
     * @return the number as an unsigned 8-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
     */
    UInt8 toByte()
        {
        return toUIntN().toByte();
        }

    /**
     * Convert the number to an unsigned 16-bit integer.
     *
     * @return the number as an unsigned 16-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
     */
    UInt16 toUInt16()
        {
        return toUIntN().toUInt16();
        }

    /**
     * Convert the number to an unsigned 32-bit integer.
     *
     * @return the number as an unsigned 32-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     */
    UInt32 toUInt32()
        {
        return toUIntN().toUInt32();
        }

    /**
     * Convert the number to an unsigned 64-bit integer.
     *
     * @return the number as an unsigned 64-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
     */
    UInt64 toUInt()
        {
        return toUIntN().toUInt();
        }

    /**
     * Convert the number to an unsigned 128-bit integer.
     *
     * @return the number as an unsigned 128-bit integer
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
     */
    UInt128 toUInt128()
        {
        return toIntN().toUInt128();
        }

    /**
     * Convert the number to a variable-length binary radix floating point number.
     *
     * @return the number as a binary floating point of variable length
     */
    FloatN toFloatN();

    /**
     * Convert the number to a 16-bit radix-2 (binary) "brain" floating point number.
     *
     * @return the number as a 16-bit "brain" floating point number
     */
    BFloat16 toBFloat16()
        {
        return toFloatN().toBFloat16();
        }

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     *
     * @return the number as a 16-bit binary floating point number
     */
    Float16 toFloat16()
        {
        return toFloatN().toFloat16();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     *
     * @return the number as a 32-bit binary floating point number
     */
    Float32 toFloat32()
        {
        return toFloatN().toFloat32();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     *
     * @return the number as a 64-bit binary floating point number
     */
    Float64 toFloat64()
        {
        return toFloatN().toFloat64();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     *
     * @return the number as a 128-bit binary floating point number
     */
    Float128 toFloat128()
        {
        return toFloatN().toFloat128();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     *
     * @return the number as a decimal of variable length
     */
    DecN toDecN();

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     *
     * @return the number as a 32-bit decimal
     *
     * @throws OutOfBounds  if the resulting value is out of the 32-bit decimal range
     */
    Dec32 toDec32()
        {
        return toDecN().toDec32();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     *
     * @return the number as a 64-bit decimal
     *
     * @throws OutOfBounds  if the resulting value is out of the 64-bit decimal range
     */
    Dec64 toDec64()
        {
        return toDecN().toDec64();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     *
     * @return the number as a 128-bit decimal
     *
     * @throws OutOfBounds  if the resulting value is out of the 128-bit decimal range
     */
    Dec128 toDec128()
        {
        return toDecN().toDec128();
        }

    /**
     * @return the integer literal for this number
     */
    IntLiteral toIntLiteral();

    /**
     * @return the floating point literal for this number
     */
    FPLiteral toFPLiteral();


    // ----- Stringable support --------------------------------------------------------------------

    static Char[] DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'];
    }
