/**
 * An IntNumber is a Number that represents an integer value.
 */
@Abstract const IntNumber
        extends Number
        implements Sequential {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        super(bits);
    }

    /**
     * Construct an integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        super(bytes);
    }


    // ----- IntNumber properties ------------------------------------------------------------------

    @Override
    @RO UIntNumber magnitude;

    /**
     * If any bits are set in this integer, then return an integer with only the most significant
     * (left-most) of those bits set, otherwise return zero.
     */
    @RO IntNumber leftmostBit.get() {
        if (bitCount <= 0) {
            return this;
        }

        Bit[] bits = this.bits.reify(Mutable);
        Int index = 0;
        Int size  = bits.size;
        while (index < size) {
            if (bits[index++] != 0) {
                break;
            }
        }

        while (index < size) {
            bits[index++] = 0;
        }

        return this.new(bits);
    }

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @RO IntNumber rightmostBit.get() {
        if (bitCount <= 0) {
            return this;
        }

        Bit[] bits  = this.bits.reify(Mutable);
        Int   index = bits.size - 1;
        while (index >= 0) {
            if (bits[index--] != 0) {
                break;
            }
        }

        while (index >= 0) {
            bits[index--] = 0;
        }

        return this.new(bits);
    }

    /**
     * The number of bits that are zero preceding the most significant (left-most) `1` bit.
     * This scans from left-to-right (most significant to least significant).
     */
    Int leadingZeroCount.get() {
        for (Int count : 0 ..< bitLength) {
            if (bits[count] == 1) {
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
    Int trailingZeroCount.get() {
        for (Int count : 0 ..< bitLength) {
            if (bits[bitLength - count - 1] == 1) {
                return count;
            }
        }
        return bitLength;
    }

    /**
     * The number of bits that are set (non-zero) in the integer. This is also referred to as a
     * _population count_, or `POPCNT`.
     */
    Int bitCount.get() {
        Int count = 0;
        for (Bit bit : bits) {
            if (bit == 1) {
                ++count;
            }
        }
        return count;
    }


    // ----- Bitwise operations --------------------------------------------------------------------

    /**
     * Bitwise AND.
     */
    @Op("&") IntNumber and(IntNumber that) = this.new(this.bits & that.bits);

    /**
     * Bitwise OR.
     */
    @Op("|") IntNumber or(IntNumber that) = this.new(this.bits | that.bits);

    /**
     * Bitwise XOR.
     */
    @Op("^") IntNumber xor(IntNumber that) = this.new(this.bits ^ that.bits);

    /**
     * Bitwise NOT.
     */
    @Op("~") IntNumber not() = this.new(~bits);

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     */
    @Op("<<") IntNumber shiftLeft(Int count) = this.new(bits << count);

    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     */
    @Op(">>") IntNumber shiftRight(Int count) = this.new(bits >> count);

    /**
     * "Unsigned" shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     */
    @Op(">>>") IntNumber shiftAllRight(Int count) = this.new(bits >>> count);

    /**
     * Rotate bits left.
     */
    IntNumber rotateLeft(Int count) = this.new(bits.rotateLeft(count));

    /**
     * Rotate bits right.
     */
    IntNumber rotateRight(Int count) = this.new(bits.rotateRight(count));

    /**
     * Keep the specified number of least-significant (right-most) bit values unchanged, zeroing any
     * remaining bits. Note that for negative values, if any bits are zeroed, this will change the
     * sign of the resulting value.
     */
    IntNumber retainLSBits(Int count) = this & ~(~(zero().as(IntNumber)) << count);

    /**
     * Keep the specified number of most-significant (left-most) bit values unchanged, zeroing any
     * remaining bits.
     */
    IntNumber retainMSBits(Int count) = this & ~(~(zero().as(IntNumber)) >>> count);

    /**
     * Swap the bit ordering of this integer's bits to produce a new integer with the
     * opposite bit order.
     */
    IntNumber reverseBits() = this.new(bits.reversed());

    /**
     * Swap the byte ordering of this integer's bytes to produce a new integer with the
     * opposite byte order. This can be used to convert a little endian integer to a big endian
     * integer, and vice versa.
     */
    IntNumber reverseBytes() = this.new(toByteArray().reversed());


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    Int stepsTo(IntNumber that) = (that - this).toInt64();

    @Override
    IntNumber skip(Int steps) = this + steps;


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert the integer number to a character.
     *
     * This conversion method always checks for out-of-bounds.
     *
     * @return the corresponding Char value
     *
     * @throws OutOfBounds  iff the Integer value is not in the legal Unicode codepoint range
     */
    Char toChar() = new Char(toUInt32(checkBounds = True));

    /**
     * Convert the IntNumber to a Nibble.
     *
     * @param checkBounds  pass `True` to bounds-check this value before conversion, or `False` to
     *                     blindly retain only the necessary number of least significant bits, which
     *                     may lose magnitude or change the sign of the result
     *
     * @return the `Nibble` for the least significant 4 bits of the integer value
     *
     * @throws OutOfBounds  iff `checkBounds` is `True` and the resulting value is out of the
     *                      Nibble range
     */
    Nibble toNibble(Boolean checkBounds = False) {
        Byte byte = toByte(checkBounds);
        assert:bounds !checkBounds || byte <= 0xF;
        return Nibble.of(byte & 0xF);
    }

    /**
     * Convert the least significant 4 bits of the integer value to a hexadecimal digit (hexit).
     *
     * This method never checks for out-of-bounds.
     *
     * @return the hexit for the least significant 4 bits of the integer value
     */
    Char toHexit() = toNibble().toChar();

    /**
     * Obtain the number as an array of boolean values, each corresponding to one bit.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the number as an array of booleans.
     */
    Boolean[] toBooleanArray() = toBitArray().toBooleanArray();

    @Override
    IntLiteral toIntLiteral() = new IntLiteral(toString());

    @Override
    FPLiteral toFPLiteral() = new FPLiteral(toString());


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength() = magnitude.estimateStringLength() + (sign == Negative ? 1 : 0);

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        if (sign == Negative) {
            buf.add('-');
        }
        return magnitude.appendTo(buf);
    }

    /**
     * Calculate the string size for the specified IntNumber and type specific size array.
     */
    protected static <IntType extends IntNumber> Int calculateStringSize(IntType n, IntType[] sizeArray) {
        for (Int index = 0; True; index++) {
            if (n <= sizeArray[index]) {
                return index + 1;
            }
        }
    }
}