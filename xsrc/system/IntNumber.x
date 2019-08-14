/**
 * An IntNumber is a Number that represents an integer value.
 */
const IntNumber
        extends Number
        implements Sequential
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    protected construct(Bit[] bits)
        {
        construct Number(bits);
        }

    /**
     * Construct an integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    protected construct(Byte[] bytes)
        {
        construct Number(bytes);
        }


    // ----- Number --------------------------------------------------------------------------------

    @Override
    @RO IntNumber! magnitude;


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    Int stepsTo(IntNumber that)
        {
        return (that - this).toInt();
        }


    // ----- additional IntNumber capabilities -----------------------------------------------------

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
     * Keep the specified number of least-significant bit values unchanged, zeroing any remaining
     * bits. Note that for negative values, if any bits are zeroed, this will change the sign of the
     * resulting value.
     */
    IntNumber truncate(Int count);

    /**
     * If any bits are set in this integer, then return an integer with only the most significant
     * (left-most) of those bits set, otherwise return zero.
     */
    @Abstract IntNumber leftmostBit;

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @Abstract IntNumber rightmostBit;

    /**
     * Determine, from left-to-right (most significant to least) the number of bits that are zero
     * preceding the most significant (left-most) bit.
     */
    Int leadingZeroCount.get()
        {
        for (Int count : 0..bitLength-1)
            {
            if (bits[bitLength - count - 1] == 1)
                {
                return count;
                }
            }
        return bitLength;
        }

    /**
     * Determine, from right-to-left (least significant to most) the number of bits that are zero
     * following the least significant (right-most) bit.
     */
    Int trailingZeroCount.get()
        {
        loop: for (Bit bit : bits)
            {
            if (bit == 1)
                {
                return loop.count;
                }
            }
        return bitLength;
        }

    /**
     * The number of bits that are set (non-zero) in the integer.
     */
    Int bitCount.get()
        {
        Int count = 0;
        for (Bit bit : bits)
            {
            if (bit == 1)
                {
                return ++count;
                }
            }
        return count;
        }

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


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the number to a Nibble (4-bit) integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     * REVIEW maybe this should be on Byte or IntNumber instead?
     */
    Nibble toNibble()
        {
        return toVarInt().toNibble();
        }

    /**
     * Convert the integer number to a character.
     */
    Char toChar()
        {
        return new Char(toUInt32());
        }

    /**
     * Obtain the number as an array of boolean values.
     */
    immutable Boolean[] toBooleanArray();

    /**
     * Obtain the integer number as an integer that does not check for overflow or underflow.
     */
    @Unchecked IntNumber toUnchecked()
        {
        TODO
        }


    // ----- formatting ----------------------------------------------------------------------------

    /**
     * The number of digits necessary to represent the magnitude of this integer value.
     */
    Int digitCount.get()
        {
        if (sign == Negative)
            {
            return magnitude.digitCount;
            }

        TODO
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return a new bit array that is a "shiftLeft" of the specified bit array.
     */
    static Bit[] bitShiftLeft(Bit[] bits, Int count)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = i < count ? 0 : bits[i - count];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "rotateLeft" of the specified bit array.
     */
    static Bit[] bitRotateLeft(Bit[] bits, Int count)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = i < count ? bits[bitLength - i + count] : bits[i - count];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "shiftRight" of the specified bit array
     * (the sign bit is carried).
     */
    static Bit[] bitShiftRight(Bit[] bits, Int count)
        {
        Int bitLength = bits.size;
        Bit bitSign   = bits[bitLength - 1];

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = i < bitLength - count ? bits[i + count] : bitSign;
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "shiftAllRight" of the specified bit array
     * (the sign bit is not carried).
     */
    static Bit[] bitShiftAllRight(Bit[] bits, Int count)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = i < bitLength - count ? bits[i + count] : 0;
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "rotateRight" of the specified bit array.
     */
    static Bit[] bitRotateRight(Bit[] bits, Int count)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = i < bitLength - count ? bits[i + count] : bits[i + count - bitLength];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is an "and" of the specified bit arrays.
     */
    static Bit[] bitAnd(Bit[] bits1, Bit[] bits2)
        {
        Int bitLength = bits1.size;
        assert bits2.size == bitLength;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = bits1[i] & bits2[i];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is an "or" of the specified bit arrays.
     */
    static Bit[] bitOr(Bit[] bits1, Bit[] bits2)
        {
        Int bitLength = bits1.size;
        assert bits2.size == bitLength;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = bits1[i] | bits2[i];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "xor" of the specified bit arrays.
     */
    static Bit[] bitXor(Bit[] bits1, Bit[] bits2)
        {
        Int bitLength = bits1.size;
        assert bits2.size == bitLength;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = bits1[i] ^ bits2[i];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "not" of the specified bit array.
     */
    static Bit[] bitNot(Bit[] bits)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = ~bits[i];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is an "add" of the specified bit arrays.
     */
    static Bit[] bitAdd(Bit[] bits1, Bit[] bits2)
        {
        Int bitLength = bits1.size;
        assert bits2.size == bitLength;

        Bit[] bitsNew = new Bit[bitLength];
        Bit carry = 0;
        for (Int i = 0; i < bitLength; ++i)
            {
            Bit aXorB = bits1[i] ^ bits2[i];
            Bit aAndB = bits1[i] & bits2[i];

            bitsNew[i] = aXorB ^ carry;
            carry = (aXorB & carry) | aAndB;
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "truncate" of the specified bit array.
     */
    static Bit[] bitTruncate(Bit[] bits, Int count)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = i < count ? bits[i] : 0;
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that is a "reverse" of the specified bit array.
     */
    static Bit[] bitReverse(Bit[] bits)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; ++i)
            {
            bitsNew[i] = bits[bitLength - i];
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that contains only the leftmost bit.
     */
    static Bit[] bitLeftmost(Bit[] bits)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = bitLength - 1; i >= 0; i--)
            {
            if (bits[i] != 0)
                {
                bitsNew[i] = 1;
                break;
                }
            }
        return bitsNew;
        }

    /**
     * @return a new bit array that contains only the rightmost bit.
     */
    static Bit[] bitRightmost(Bit[] bits)
        {
        Int bitLength = bits.size;

        Bit[] bitsNew = new Bit[bitLength];
        for (Int i = 0; i < bitLength; i++)
            {
            if (bits[i] != 0)
                {
                bitsNew[i] = 1;
                break;
                }
            }
        return bitsNew;
        }

    /**
     * @return a new Boolean array for this bit array.
     */
    static immutable Boolean[] bitBooleans(Bit[] bits)
        {
        return new Array<Boolean>(bits.size, i -> bits[i] == 1).ensureConst();
        }

    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return magnitude.estimateStringLength() + (sign == Negative ? 1 : 0);
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        if (sign == Negative)
            {
            appender.add('-');
            }
        magnitude.appendTo(appender);
        }

    // ----- Stringable support --------------------------------------------------------------------

    /**
     * Calculate the string size for the specified IntNumber and type specific size array.
     */
    static <IntType extends IntNumber> Int calculateStringSize(IntType n, IntType[] sizeArray)
        {
        for (Int index = 0; true; index++)
            {
            if (n <= sizeArray[index])
                {
                return index + 1;
                }
            }
        assert;
        }
    }
