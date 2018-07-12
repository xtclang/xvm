/**
 * An IntNumber is a Number that represents an integer value.
 */
interface IntNumber
        extends Number
        extends Sequential
    {
    // ----- Sequential interface ------------------------------------------------------------------

    /**
     * Integer increment.
     *
     * @throws BoundsException if _this_ is the maximum value
     */
    @Override
    IntNumber nextValue()
        {
        return add(1);
        }

    /**
     * Integer decrement.
     *
     * @throws BoundsException if _this_ is the minimum value
     */
    @Override
    IntNumber prevValue()
        {
        return sub(1);
        }

    /**
     * Checked integer increment.
     */
    @Override
    conditional IntNumber next()
        {
        try
            {
            IntNumber n = add(1);
            if (n > this)
                {
                return true, n;
                }
            }
        catch (Exception e) {}

        return false;
        }

    /**
     * Checked integer decrement.
     */
    @Override
    conditional IntNumber prev()
        {
        try
            {
            IntNumber n = sub(1);
            if (n < this)
                {
                return true, n;
                }
            }
        catch (Exception e) {}

        return false;
        }

    // ----- additional IntNumber capabilities -----------------------------------------------------

    /**
     * Bitwise AND.
     */
    @Op IntNumber and(IntNumber that);

    /**
     * Bitwise OR.
     */
    @Op IntNumber or(IntNumber that);

    /**
     * Bitwise XOR.
     */
    @Op IntNumber xor(IntNumber that);

    /**
     * Bitwise NOT.
     */
    @Op IntNumber not();

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     */
    @Op IntNumber shiftLeft(Int count);

    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     */
    @Op IntNumber shiftRight(Int count);

    /**
     * "Unsigned" shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     */
    @Op IntNumber shiftAllRight(Int count);

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
    @RO Int leftmostBit;

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @RO Int rightmostBit;

    /**
     * Determine, from left-to-right (most significant to least) the number of bits that are zero
     * preceding the most significant (left-most) bit.
     */
    @RO Int leadingZeroCount;

    /**
     * Determine, from right-to-left (least significant to most) the number of bits that are zero
     * following the least significant (right-most) bit.
     */
    @RO Int trailingZeroCount;

    /**
     * Determine the number of bits that are set (non-zero) in the integer.
     */
    @RO Int bitCount;

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
     * Convert the number to a Char. Any additional magnitude is discarded.
     */
    Char to<Char>()
        {
        TODO
        }

    /**
     * Obtain the number as an array of boolean values.
     */
    @Override
    Boolean[] to<Boolean[]>()
        {
        class SequenceImpl
                implements Sequence<Boolean>
            {
            @Override @RO Int size.get()
                {
                return IntNumber.this.bitCount;
                }

            @Override Boolean get(Int index)
                {
                return IntNumber.this.to<Bit[]>()[index].to<Boolean>();
                }
            }

        return new SequenceImpl();
        }
    }
