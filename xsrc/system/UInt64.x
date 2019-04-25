const UInt64
        extends UIntNumber
        default(0)
    {
    /**
     * The minimum value for an UInt64.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt64.
     */
    static IntLiteral maxvalue =  0xFFFF_FFFF_FFFF_FFFF;

    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct IntNumber(bits);
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

    @Override
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
        if (this == 0)
            {
            appender.add('0');
            }
        else
            {
            (UInt64 left, UInt64 digit) = this /% 10;
            if (left != 0)
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