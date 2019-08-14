/**
 * The Number interface represents the properties and operations available on every
 * numeric type included in Ecstasy.
 *
 * Numbers are constant values, represented internally as an array of bits, which are ordered from
 * Least Significant Bit (LSB) to Most Significant Bit (MSB) -- _in a right-to-left order_. Numbers
 * can be instantiated from an array of bits in this order, and those bits are available via the
 * `bits` property.
 *
 * Numbers can also be instantiated from an array of bytes, in a left-to-right order, as they would
 * appear when communicated over the network, or as they would be stored in a file. To obtain the
 * bytes from a number in the left-to-right order from a Number, use the `toByteArray()` method.
 */
const Number
        implements Orderable
    {
    /**
     * Construct a number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    protected construct(Bit[] bits)
        {
        // make sure the bit length is at least 8, and also a power-of-two
        assert bits.size == (bits.size & ~0x7).leftmostBit;

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
        construct Number(bytes.toBitArray().reverse());
        }


    // ----- related types -------------------------------------------------------------------------

    enum Signum(String prefix, IntLiteral factor, Ordered ordered)
        {
        Negative("-", -1, Lesser ),
        Zero    ("" ,  0, Equal  ),
        Positive("+", +1, Greater)
        }

    /**
     * An IllegalMath exception is raised when an assert fails.
     */
    const IllegalMath(String? text = null, Exception? cause = null)
            extends Exception(text, cause);

    /**
     * An Assertion exception is raised when an assert fails.
     */
    const DivisionByZero(String? text = null, Exception? cause = null)
            extends IllegalMath(text, cause);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The actual array of bits representing this number, ordered from Least Significant Bit (LSB)
     * in the `0` element, to Most Significant Bit (MSB) in the `bitLength-1` element. In other
     * words, the bit positions in the array correspond to a right-to-left ordering, where the '0'
     * element is the right-most bit; for example, in the case of a typical 2s-complement integer,
     * element '0' corresponds to `2^0=1`, element '1' corresponds to `2^1=2`,  element '2'
     * corresponds to `2^2=4`, and so on.
     */
    Bit[] bits;

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
        return bitLength / 8;
        }

    /**
     * True if the numeric type is signed (has the potential to hold positive or negative values);
     * false if unsigned (representing only a magnitude).
     */
    Boolean signed.get()
        {
        return true;
        }

    /**
     * The Sign of the number.
     */
    @Abstract @RO Signum sign;


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Addition: Add another number to this number, and return the result.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op Number add(Number n);

    /**
     * Subtraction: Subtract another number from this number, and return the result.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op Number sub(Number n);

    /**
     * Multiplication: Multiply this number by another number, and return the result.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op Number mul(Number n);

    /**
     * Division: Divide this number by another number, and return the result.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op Number div(Number n);

    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    @Op Number mod(Number n);

    /**
     * Division and Modulo: Divide this number by another number, and return both the
     * quotient and the modulo.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op (Number quotient, Number modulo) divmod(Number n)
        {
        return (this / n, this % n);
        }

    /**
     * Remainder: Return the remainder that would result from dividing this number by another
     * number. Note that the remainder is the same as the modulo for unsigned dividend values
     * and for signed dividend values that are zero or positive, but for signed dividend values
     * that are negative, the remainder will be zero or negative.
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
     * The magnitude of this number (its distance from zero), which may use a different Number type
     * if the magnitude cannot be represented by the type of this value.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     */
    @RO Number! magnitude.get()
        {
        return abs();
        }

    /**
     * Calculate the negative of this number.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if no corresponding negated value is possible to express with this type
     */
    @Op Number neg();

    /**
     * Calculate this number raised to the specified power.
     *
     * @throws IllegalMath  if the requested operation cannot be performed for any reason
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    Number pow(Number n);

    /**
     * Obtain an interval beginning with this number and proceeding to the specified number.
     */
    @Override
    @Op Interval<Number> through(Number n)
        {
        return new Interval<Number>(this, n);
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the contents of the number as an array of bits in **left-to-right** order.
     */
    immutable Bit[] toBitArray()
        {
        return bits.reverse.as(immutable Bit[]);
        }

    /**
     * Obtain the number as an array of nibbles, in left-to-right order.
     */
    immutable Nibble[] toNibbleArray()
        {
        return toBitArray().toNibbleArray();
        }

    /**
     * Obtain the number as an array of bytes, in left-to-right order.
     */
    immutable Byte[] toByteArray()
        {
        return toBitArray().toByteArray();
        }

    /**
     * Convert the number to a variable-length signed integer.
     */
    VarInt toVarInt();

    /**
     * Convert the number to a signed 8-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 8-bit integer range
     */
    Int8 toInt8()
        {
        return toVarInt().toInt8();
        }

    /**
     * Convert the number to a signed 16-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 16-bit integer range
     */
    Int16 toInt16()
        {
        return toVarInt().toInt16();
        }

    /**
     * Convert the number to a signed 32-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 32-bit integer range
     */
    Int32 toInt32()
        {
        return toVarInt().toInt32();
        }

    /**
     * Convert the number to a signed 64-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 64-bit integer range
     */
    Int64 toInt()
        {
        return toVarInt().toInt();
        }

    /**
     * Convert the number to a signed 128-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the signed 128-bit integer range
     */
    Int128 toInt128()
        {
        return toVarInt().toInt128();
        }

    /**
     * Convert the number to a variable-length unsigned integer.
     */
    VarUInt toVarUInt();

    /**
     * Convert the number to a unsigned 8-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 8-bit integer range
     */
    UInt8 toByte()
        {
        return toVarUInt().toByte();
        }

    /**
     * Convert the number to a unsigned 16-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 16-bit integer range
     */
    UInt16 toUInt16()
        {
        return toVarUInt().toUInt16();
        }

    /**
     * Convert the number to a unsigned 32-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 32-bit integer range
     */
    UInt32 toUInt32()
        {
        return toVarUInt().toUInt32();
        }

    /**
     * Convert the number to a unsigned 64-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 64-bit integer range
     */
    UInt64 toUInt()
        {
        return toVarUInt().toUInt();
        }

    /**
     * Convert the number to a unsigned 128-bit integer.
     *
     * @throws OutOfBounds  if the resulting value is out of the unsigned 128-bit integer range
     */
    UInt128 toUInt128()
        {
        return toVarInt().toUInt128();
        }

    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    VarFloat toVarFloat();

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    Float16 toFloat16()
        {
        return toVarFloat().toFloat16();
        }

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    BFloat16 toBFloat16()
        {
        return toVarFloat().toBFloat16();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    Float32 toFloat32()
        {
        return toVarFloat().toFloat32();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    Float64 toFloat64()
        {
        return toVarFloat().toFloat64();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    Float128 toFloat128()
        {
        return toVarFloat().toFloat128();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    VarDec toVarDec();

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    Dec32 toDec32()
        {
        return toVarDec().toDec32();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    Dec64 toDec64()
        {
        return toVarDec().toDec64();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    Dec128 toDec128()
        {
        return toVarDec().toDec128();
        }


    // ----- Stringable support --------------------------------------------------------------------

    static Char[] DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'];
    }
