/**
 * An IntNumber is a Number that represents an integer value.
 */
interface IntNumber
        extends Number
    {
    /**
     * Integer increment.
     */
    @op IntNumber increment()
        {
        return this + 1;
        }

    /**
     * Integer decrement.
     */
    @op IntNumber decrement()
        {
        return this - 1;
        }

    /**
     * Bitwise AND.
     */
    @op IntNumber and(IntNumber that);

    /**
     * Bitwise OR.
     */
    @op IntNumber or(IntNumber that);

    /**
     * Bitwise XOR.
     */
    @op IntNumber xor(IntNumber that);

    /**
     * Bitwise NOT.
     */
    @op IntNumber not();

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     */
    @op IntNumber shiftLeft(Int count);

    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     */
    @op IntNumber shiftRight(Int count);

    /**
     * "Unsigned" shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     */
    @op IntNumber shiftAllRight(Int count);

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
    @ro IntNumber leftmostBit;

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @ro IntNumber rightmostBit;

    /**
     * Determine, from left-to-right (most significant to least) the number of bits that are zero
     * preceding the most significant (left-most) bit.
     */
    @ro IntNumber leadingZeroCount;

    /**
     * Determine, from right-to-left (least significant to most) the number of bits that are zero
     * following the least significant (right-most) bit.
     */
    @ro IntNumber trailingZeroCount;

    /**
     * Determine the number of bits that are set (non-zero) in the integer.
     */
    @ro IntNumber bitCount;

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

    /**
     * Obtain a range of integers from this number to that number.
     */
    Range<IntNumber> to(IntNumber that);

    /**
     * Obtain the number as an array of boolean values.
     */
    Boolean[] to<Boolean[]>()
        {
        return new Sequence<Boolean>()
            {
            @override Int length.get()
                {
                return IntNumber.this.bitCount;
                }

            @override Boolean get(Int index)
                {
                return IntNumber.this.to<Bit[]>()[index].to<Boolean>();
                }
            }
        }
    }
