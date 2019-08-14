const UInt128
        extends UIntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt128.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt128.
     */
    static IntLiteral maxvalue =  0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct IntNumber(bits);
        }

    /**
     * Construct a 128-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 16;
        construct UIntNumber(bytes);
        }


    @Override
    Signum sign.get()
        {
        if (this < 0)
            {
            return Negative;
            }

        if (this == 0)
            {
            return Zero;
            }

        return Positive;
        }

    @Override
    @RO UInt128 magnitude.get()
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
    @Op UInt128 shiftLeft(Int count)
        {
        return new UInt128(bitShiftLeft(bits, count));
        }

    @Override
    UInt128 rotateLeft(Int count)
        {
        return new UInt128(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt128 shiftRight(Int count)
        {
        return new UInt128(bitShiftRight(bits, count));
        }

    @Override
    @Op UInt128 rotateRight(Int count)
        {
        return new UInt128(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt128 shiftAllRight(Int count)
        {
        return new UInt128(bitShiftAllRight(bits, count));
        }

    @Override
    UInt128 truncate(Int count)
        {
        return new UInt128(bitTruncate(bits, count));
        }

    @Override
    UInt128 leftmostBit.get()
        {
        TODO
        }

    @Override
    UInt128 rightmostBit.get()
        {
        TODO
        }

    @Override
    UInt128 reverseBits()
        {
        return new UInt128(bitReverse(bits));
        }

    @Override
    UInt128 reverseBytes()
        {
        UInt128 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt128 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op UInt128 or(UInt128 n)
        {
        return new UInt128(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt128 and(UInt128 n)
        {
        return new UInt128(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt128 xor(UInt128 n)
        {
        return new UInt128(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt128 not()
        {
        return new UInt128(bitNot(bits));
        }

    @Override
    conditional UInt128 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt128 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt128 add(UInt128 n)
        {
        return new UInt128(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt128 sub(UInt128 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt128 mul(UInt128 n)
        {
        return this * n;
        }

    @Override
    @Op UInt128 div(UInt128 n)
        {
        return this / n;
        }

    @Override
    @Op UInt128 mod(UInt128 n)
        {
        return this % n;
        }

    @Override
    UInt128 abs()
        {
        return this;
        }

    @Override
    UInt128 pow(UInt128 n)
        {
        UInt128 result = 1;

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
        if (this == 0)
            {
            appender.add('0');
            }
        else
            {
            (UInt128 left, UInt128 digit) = this /% 10;
            if (left != 0)
                {
                left.appendTo(appender);
                }
            appender.add(DIGITS[digit]);
            }
        }

    // maxvalue = 340_282_366_920_938_463_463_374_607_431_768_211_455 (39 digits)
    static private UInt128[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999, 9_999_999_999, 99_999_999_999, 999_999_999_999,
         9_999_999_999_999, 99_999_999_999_999, 999_999_999_999_999,
         9_999_999_999_999_999, 99_999_999_999_999_999, 999_999_999_999_999_999,
         9_999_999_999_999_999_999, 99_999_999_999_999_999_999, 999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999_999_999,
         340_282_366_920_938_463_463_374_607_431_768_211_455
         ];
    }