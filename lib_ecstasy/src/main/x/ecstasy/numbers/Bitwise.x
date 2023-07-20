/**
 * `Bitwise` is a mixin that adds bit-oriented operations to a fixed length [IntNumber].
 */
mixin Bitwise
        into IntNumber {
    /**
     * If any bits are set in this integer, then return an integer with only the most significant
     * (left-most) of those bits set, otherwise return zero.
     */
    @RO Bitwise leftmostBit.get() {
        if (bitCount <= 0) {
            return this;
        }

        Bit[] bits = this.bits.reify(Mutable);
        Int start = 0;
        Int stop  = bits.size - 1;
        while (start <= stop) {
            if (bits[start++] != 0) {
                break;
            }
        }

        while (start <= stop) {
            bits[start++] = 0;
        }

        return this.new(bits);
    }

    /**
     * If any bits are set in this integer, then return an integer with only the least significant
     * (right-most) of those bits set, otherwise return zero.
     */
    @RO Bitwise rightmostBit.get() {
        if (bitCount <= 0) {
            return this;
        }

        TODO use above algorithm in reverse
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
    @Op("&") Bitwise and(Bitwise that) {
        TODO similar to leftmostBit
    }

    /**
     * Bitwise OR.
     */
    @Op("|") Bitwise or(Bitwise that) {
        TODO similar to leftmostBit
    }

    /**
     * Bitwise XOR.
     */
    @Op("^") Bitwise xor(Bitwise that) {
        TODO similar to leftmostBit
    }

    /**
     * Bitwise NOT.
     */
    @Op("~") Bitwise not() {
        return this.new(~bits);
    }

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     */
    @Op("<<") Bitwise shiftLeft(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     */
    @Op(">>") Bitwise shiftRight(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * "Unsigned" shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     */
    @Op(">>>") Bitwise shiftAllRight(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * Rotate bits left.
     */
    Bitwise rotateLeft(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * Rotate bits right.
     */
    Bitwise rotateRight(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * Keep the specified number of least-significant (right-most) bit values unchanged, zeroing any
     * remaining bits. Note that for negative values, if any bits are zeroed, this will change the
     * sign of the resulting value.
     */
    Bitwise retainLSBits(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * Keep the specified number of most-significant (left-most) bit values unchanged, zeroing any
     * remaining bits.
     */
    Bitwise retainMSBits(Int count) {
        TODO similar to leftmostBit
    }

    /**
     * Swap the bit ordering of this integer's bits to produce a new integer with the
     * opposite bit order.
     */
    Bitwise reverseBits() {
        TODO
    }

    /**
     * Swap the byte ordering of this integer's bytes to produce a new integer with the
     * opposite byte order. This can be used to convert a little endian integer to a big endian
     * integer, and vice versa.
     */
    Bitwise reverseBytes() {
        TODO
    }
}