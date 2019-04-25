const UInt128
        extends UIntNumber
        default(0)
    {
    /**
     * The minimum value for an UInt128.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt128.
     */
    static IntLiteral maxvalue =  0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;

    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct IntNumber(bits);
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
    @Auto Int8 to<Int8>()
        {
        return this;
        }

    @Override
    @Auto Int16 to<Int16>()
        {
        return this;
        }

    @Override
    @Auto Int32 to<Int32>()
        {
        return this;
        }

    @Override
    @Auto Int64 to<Int64>()
        {
        return this;
        }

    @Override
    @Auto Int128 to<Int128>()
        {
        return this;
        }

    @Override
    @Auto UInt8 to<UInt8>()
        {
        return this;
        }

    @Override
    @Auto UInt16 to<UInt16>()
        {
        return this;
        }

    @Override
    @Auto UInt32 to<UInt32>()
        {
        return this;
        }

    @Override
    @Auto UInt64 to<UInt64>()
        {
        return this;
        }

    @Auto UInt128 to<UInt128>()
        {
        return this;
        }

    @Override
    @Auto VarUInt to<VarUInt>()
        {
        return this;
        }

    @Override
    @Auto VarFloat to<VarFloat>()
        {
        return this;
        }

    @Override
    @Auto VarDec to<VarDec>()
        {
        return this;
        }

    @Override
    @Auto VarInt to<VarInt>()
        {
        return this;
        }

    @Override
    Boolean[] to<Boolean[]>()
        {
        return bitBooleans(bits);
        }

    @Override
    Bit[] to<Bit[]>()
        {
        return bits;
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