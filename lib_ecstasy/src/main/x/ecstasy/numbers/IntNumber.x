/**
 * An IntNumber is a Number that represents an integer value.
 */
@Abstract const IntNumber
        extends Number
        implements Sequential
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        super(bits);
        }

    /**
     * Construct an integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        super(bytes);
        }


    // ----- IntNumber properties ------------------------------------------------------------------

    @Override
    @RO UIntNumber magnitude;

    /**
     * If any bits are set in this integer, then return an integer with only the most significant
     * (left-most) of those bits set, otherwise return zero.
     */
    @Abstract @RO IntNumber leftmostBit;

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @Abstract @RO IntNumber rightmostBit;

    /**
     * The number of bits that are zero preceding the most significant (left-most) `1` bit.
     * This scans from left-to-right (most significant to least significant).
     */
    Int leadingZeroCount.get()
        {
        for (Int count : [0..bitLength))
            {
            if (bits[count] == 1)
                {
                return count;
                }
            }
        return bitLength;
        }

    /**
     * The number of bits that are zero following the least significant (right-most) `1` bit.
     * This scans from right-to-left (least significant to most significant).
     *
     * For an integer with `bitCount==1`, this provides the log2 value of the integer.
     */
    Int trailingZeroCount.get()
        {
        for (Int count : [0..bitLength))
            {
            if (bits[bitLength - count - 1] == 1)
                {
                return count;
                }
            }
        return bitLength;
        }

    /**
     * The number of bits that are set (non-zero) in the integer. This is also referred to as a
     * _population count_, or `POPCNT`.
     */
    Int bitCount.get()
        {
        Int count = 0;
        for (Bit bit : bits)
            {
            if (bit == 1)
                {
                ++count;
                }
            }
        return count;
        }


    // ----- IntNumber operations ------------------------------------------------------------------

    /**
     * Bitwise AND.
     */
    @Op("&") IntNumber and(IntNumber that);

    /**
     * Bitwise OR.
     */
    @Op("|") IntNumber or(IntNumber that);

    /**
     * Bitwise XOR.
     */
    @Op("^") IntNumber xor(IntNumber that);

    /**
     * Bitwise NOT.
     */
    @Op("~") IntNumber not();

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     */
    @Op("<<") IntNumber shiftLeft(Int count);

    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     */
    @Op(">>") IntNumber shiftRight(Int count);

    /**
     * "Unsigned" shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     */
    @Op(">>>") IntNumber shiftAllRight(Int count);

    /**
     * Rotate bits left.
     */
    IntNumber rotateLeft(Int count);

    /**
     * Rotate bits right.
     */
    IntNumber rotateRight(Int count);

    /**
     * Keep the specified number of least-significant (right-most) bit values unchanged, zeroing any
     * remaining bits. Note that for negative values, if any bits are zeroed, this will change the
     * sign of the resulting value.
     */
    IntNumber retainLSBits(Int count);

    /**
     * Keep the specified number of most-significant (left-most) bit values unchanged, zeroing any
     * remaining bits.
     */
    IntNumber retainMSBits(Int count);

    /**
     * Swap the bit ordering of this integer's bits to produce a new integer with the
     * opposite bit order.
     */
    IntNumber reverseBits();

    /**
     * Swap the byte ordering of this integer's bytes to produce a new integer with the
     * opposite byte order. This can be used to convert a little endian integer to a big endian
     * integer, and vice versa.
     */
    IntNumber reverseBytes();


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    Int stepsTo(IntNumber that)
        {
        return (that - this).toInt64();
        }

    @Override
    IntNumber skip(Int steps)
        {
        return this + steps;
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the integer number to a character.
     */
    Char toChar()
        {
        return new Char(toUInt32());
        }

    /**
     * Convert the least significant 4 bits of the integer value to a hexadecimal digit (hexit).
     *
     * @return the hexit for the least significant 4 bits of the integer value
     */
    Char toHexit()
        {
        UInt32 nibble = toUnchecked().toUInt32() & 0xF;          // TODO CP sliceByte()
        return nibble <= 9 ? '0' + nibble : 'A' + nibble - 10;
        }

    /**
     * Convert the IntNumber to a Nibble.
     *
     * @return the hexit for the least significant 4 bits of the integer value
     */
    Nibble toNibble()
        {
        return new Nibble(toInt64());
        }

    /**
     * Obtain the number as an array of boolean values, each corresponding to one bit.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the number as an array of booleans.
     */
    Boolean[] toBooleanArray()
        {
        return toBitArray().toBooleanArray();
        }

    /**
     * Obtain the integer number as an integer that checks for overflow and underflow conditions.
     */
    (IntNumber - Unchecked) toChecked();

    /**
     * Obtain the integer number as an integer that does not check for overflow or underflow.
     */
    @Unchecked IntNumber toUnchecked();

    @Override
    IntLiteral toIntLiteral()
        {
        return new IntLiteral(toString());
        }

    @Override
    FPLiteral toFPLiteral()
        {
        return new FPLiteral(toString());
        }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return magnitude.estimateStringLength() + (sign == Negative ? 1 : 0);
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        if (sign == Negative)
            {
            buf.add('-');
            }
        return magnitude.appendTo(buf);
        }

    /**
     * Calculate the string size for the specified IntNumber and type specific size array.
     */
    protected static <IntType extends IntNumber> Int calculateStringSize(IntType n, IntType[] sizeArray)
        {
        for (Int index = 0; True; index++)
            {
            if (n <= sizeArray[index])
                {
                return index + 1;
                }
            }
        assert;
        }
    }
