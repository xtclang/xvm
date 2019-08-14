const UInt8
        extends UIntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt8.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt8.
     */
    static IntLiteral maxvalue = 0xFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an 8-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 8;
        construct IntNumber(bits);
        }

    /**
     * Construct an 8-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 1;
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
    @RO UInt8 magnitude.get()
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
    conditional UInt8 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt8 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt8 shiftLeft(Int count)
        {
        return new UInt8(bitShiftLeft(bits, count));
        }

    @Override
    UInt8 rotateLeft(Int count)
        {
        return new UInt8(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt8 shiftRight(Int count)
        {
        return new UInt8(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt8 rotateRight(Int count)
        {
        return new UInt8(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt8 shiftAllRight(Int count)
        {
        return new UInt8(bitShiftAllRight(bits, count));
        }

    @Override
    UInt8 truncate(Int count)
        {
        return new UInt8(bitTruncate(bits, count));
        }

    @Override
    UInt8 leftmostBit.get()
        {
        TODO
        }

    @Override
    UInt8 rightmostBit.get()
        {
        TODO
        }

    @Override
    UInt8 reverseBits()
        {
        return new UInt8(bitReverse(bits));
        }

    @Override
    UInt8 reverseBytes()
        {
        UInt8 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt8 neg()
        {
        return this;
        }

    @Override
    @Op UInt8 or(UInt8 n)
        {
        return new UInt8(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt8 and(UInt8 n)
        {
        return new UInt8(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt8 xor(UInt8 n)
        {
        return new UInt8(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt8 not()
        {
        return new UInt8(bitNot(bits));
        }

    @Override
    @Op UInt8 add(UInt8 n)
        {
        return new UInt8(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt8 sub(UInt8 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt8 mul(UInt8 n)
        {
        return this * n;
        }

    @Override
    @Op UInt8 div(UInt8 n)
        {
        return this / n;
        }

    @Override
    @Op UInt8 mod(UInt8 n)
        {
        return this % n;
        }

    @Override
    UInt8 abs()
        {
        return this;
        }

    @Override
    UInt8 pow(UInt8 n)
        {
        UInt8 result = 1;

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
            (UInt8 left, UInt8 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(appender);
                }
            appender.add(DIGITS[digit]);
            }
        }

    static private UInt8[] sizeArray =
         [
         9, 99, 255
         ];
    }