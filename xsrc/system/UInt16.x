const UInt16
        extends UIntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt16.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt16.
     */
    static IntLiteral maxvalue = 0xFFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 16;
        construct IntNumber(bits);
        }

    /**
     * Construct a 16-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 2;
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
    @RO UInt16 magnitude.get()
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
    conditional UInt16 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt16 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt16 shiftLeft(Int count)
        {
        return new UInt16(bitShiftLeft(bits, count));
        }

    @Override
    UInt16 rotateLeft(Int count)
        {
        return new UInt16(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt16 shiftRight(Int count)
        {
        return new UInt16(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt16 rotateRight(Int count)
        {
        return new UInt16(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt16 shiftAllRight(Int count)
        {
        return new UInt16(bitShiftAllRight(bits, count));
        }

    @Override
    UInt16 truncate(Int count)
        {
        return new UInt16(bitTruncate(bits, count));
        }

    @Override
    UInt16 leftmostBit.get()
        {
        TODO
        }

    @Override
    UInt16 rightmostBit.get()
        {
        TODO
        }

    @Override
    UInt16 reverseBits()
        {
        return new UInt16(bitReverse(bits));
        }

    @Override
    UInt16 reverseBytes()
        {
        UInt16 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt16 neg()
        {
        return this;
        }

    @Override
    @Op UInt16 or(UInt16 n)
        {
        return new UInt16(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt16 and(UInt16 n)
        {
        return new UInt16(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt16 xor(UInt16 n)
        {
        return new UInt16(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt16 not()
        {
        return new UInt16(bitNot(bits));
        }

    @Override
    @Op UInt16 add(UInt16 n)
        {
        return new UInt16(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt16 sub(UInt16 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt16 mul(UInt16 n)
        {
        return this * n;
        }

    @Override
    @Op UInt16 div(UInt16 n)
        {
        return this / n;
        }

    @Override
    @Op UInt16 mod(UInt16 n)
        {
        return this % n;
        }

    @Override
    UInt16 abs()
        {
        return this;
        }

    @Override
    UInt16 pow(UInt16 n)
        {
        UInt16 result = 1;

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
            (UInt16 left, UInt16 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(appender);
                }
            appender.add(DIGITS[digit]);
            }
        }

    // maxvalue = 65_535 (5 digits)
    static private UInt16[] sizeArray =
         [
         9, 99, 999, 9_999, 65_535
         ];
    }
