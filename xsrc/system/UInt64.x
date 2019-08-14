const UInt64
        extends UIntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt64.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt64.
     */
    static IntLiteral maxvalue =  0xFFFF_FFFF_FFFF_FFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct IntNumber(bits);
        }

    /**
     * Construct a 64-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 8;
        construct UIntNumber(bytes);
        }


    @Override
    Signum sign.get()
        {
        if (this == 0)
            {
            return Zero;
            }

        return Positive;
        }

    @Override
    @RO UInt64 magnitude.get()
        {
        return this;
        }

    @Override
    @Auto Int8 toInt8()
        {
        return this;
        }

    @Override
    @Auto Int16 toInt16()
        {
        return this;
        }

    @Override
    @Auto Int32 toInt32()
        {
        return this;
        }

    @Override
    @Auto Int64 toInt()
        {
        return this;
        }

    @Override
    @Auto Int128 toInt128()
        {
        return this;
        }

    @Override
    @Auto UInt8 toByte()
        {
        return this;
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        return this;
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        return this;
        }

    @Override
    @Auto UInt64 toUInt()
        {
        return this;
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        return this;
        }

    @Override
    @Auto VarUInt toVarUInt()
        {
        return this;
        }

    @Override
    @Auto VarFloat toVarFloat()
        {
        return this;
        }

    @Override
    @Auto VarDec toVarDec()
        {
        return this;
        }

    @Override
    @Auto VarInt toVarInt()
        {
        return this;
        }

    @Override
    immutable Boolean[] toBooleanArray()
        {
        return bitBooleans(bits);
        }

    @Override
    immutable Bit[] toBitArray()
        {
        return bits.reverse.as(immutable Bit[]);
        }

    @Override
    conditional UInt64 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt64 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt64 shiftLeft(Int count)
        {
        return new UInt64(bitShiftLeft(bits, count));
        }

    @Override
    UInt64 rotateLeft(Int count)
        {
        return new UInt64(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt64 shiftRight(Int count)
        {
        return new UInt64(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt64 rotateRight(Int count)
        {
        return new UInt64(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt64 shiftAllRight(Int count)
        {
        return new UInt64(bitShiftAllRight(bits, count));
        }

    @Override
    UInt64 truncate(Int count)
        {
        return new UInt64(bitTruncate(bits, count));
        }

    @Override
    UInt64 leftmostBit.get()
        {
        TODO
        }

    @Override
    UInt64 rightmostBit.get()
        {
        TODO
        }

    @Override
    UInt64 reverseBits()
        {
        return new UInt64(bitReverse(bits));
        }

    @Override
    UInt64 reverseBytes()
        {
        UInt64 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt64 neg()
        {
        return this;
        }

    @Override
    @Op UInt64 or(UInt64 n)
        {
        return new UInt64(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt64 and(UInt64 n)
        {
        return new UInt64(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt64 xor(UInt64 n)
        {
        return new UInt64(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt64 not()
        {
        return new UInt64(bitNot(bits));
        }

    @Override
    @Op UInt64 add(UInt64 n)
        {
        return new UInt64(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt64 sub(UInt64 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt64 mul(UInt64 n)
        {
        return this * n;
        }

    @Override
    @Op UInt64 div(UInt64 n)
        {
        return this / n;
        }

    @Override
    @Op UInt64 mod(UInt64 n)
        {
        return this % n;
        }

    @Override
    UInt64 abs()
        {
        return this;
        }

    @Override
    UInt64 pow(UInt64 n)
        {
        UInt64 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }

    @Override
    Int estimateStringLength()
        {
        return calculateStringSize(this, sizeArray);
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        if (sign == Zero)
            {
            appender.add('0');
            }
        else
            {
            (UInt64 left, UInt64 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(appender);
                }
            appender.add(DIGITS[digit]);
            }
        }

    // maxvalue = 18_446_744_073_709_551_615 (20 digits)
    static private UInt64[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999, 9_999_999_999, 99_999_999_999, 999_999_999_999,
         9_999_999_999_999, 99_999_999_999_999, 999_999_999_999_999,
         9_999_999_999_999_999, 99_999_999_999_999_999, 999_999_999_999_999_999,
         9_999_999_999_999_999_999, 18_446_744_073_709_551_615
         ];
    }