const UInt32
        extends UIntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt32.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt32.
     */
    static IntLiteral maxvalue =  0xFFFFFFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct IntNumber(bits);
        }

    /**
     * Construct a 32-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
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
    @RO UInt32 magnitude.get()
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
    conditional UInt32 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt32 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt32 shiftLeft(Int count)
        {
        return new UInt32(bitShiftLeft(bits, count));
        }

    @Override
    UInt32 rotateLeft(Int count)
        {
        return new UInt32(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt32 shiftRight(Int count)
        {
        return new UInt32(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt32 rotateRight(Int count)
        {
        return new UInt32(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt32 shiftAllRight(Int count)
        {
        return new UInt32(bitShiftAllRight(bits, count));
        }

    @Override
    UInt32 truncate(Int count)
        {
        return new UInt32(bitTruncate(bits, count));
        }

    @Override
    UInt32 leftmostBit.get()
        {
        TODO
        }

    @Override
    UInt32 rightmostBit.get()
        {
        TODO
        }

    @Override
    UInt32 reverseBits()
        {
        return new UInt32(bitReverse(bits));
        }

    @Override
    UInt32 reverseBytes()
        {
        UInt32 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt32 neg()
        {
        return this;
        }

    @Override
    @Op UInt32 or(UInt32 n)
        {
        return new UInt32(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt32 and(UInt32 n)
        {
        return new UInt32(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt32 xor(UInt32 n)
        {
        return new UInt32(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt32 not()
        {
        return new UInt32(bitNot(bits));
        }

    @Override
    @Op UInt32 add(UInt32 n)
        {
        return new UInt32(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt32 sub(UInt32 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt32 mul(UInt32 n)
        {
        return this * n;
        }

    @Override
    @Op UInt32 div(UInt32 n)
        {
        return this / n;
        }

    @Override
    @Op UInt32 mod(UInt32 n)
        {
        return this % n;
        }

    @Override
    UInt32 abs()
        {
        return this;
        }

    @Override
    UInt32 pow(UInt32 n)
        {
        UInt32 result = 1;

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
            (UInt32 left, UInt32 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(appender);
                }
            appender.add(DIGITS[digit]);
            }
        }

    // maxvalue = 4_294_967_295 (10 digits)
    static private UInt32[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999,
         4_294_967_295
         ];
     }