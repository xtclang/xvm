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
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 8;
        construct UIntNumber(bits);
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


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
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


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt8 add(UInt8 n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UInt8 sub(UInt8 n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UInt8 mul(UInt8 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UInt8 div(UInt8 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UInt8 mod(UInt8 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    UInt8 and(UInt8 n)
        {
        return new UInt8(this.bits & n.bits);
        }

    @Override
    @Op("|")
    UInt8 or(UInt8 n)
        {
        return new UInt8(this.bits | n.bits);
        }

    @Override
    @Op("^")
    UInt8 xor(UInt8 n)
        {
        return new UInt8(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    UInt8 not()
        {
        return new UInt8(~bits);
        }

    @Override
    @Op("<<")
    UInt8 shiftLeft(Int count)
        {
        return new UInt8(bits << count);
        }

    @Override
    @Op(">>")
    UInt8 shiftRight(Int count)
        {
        return new UInt8(bits >> count);
        }

    @Override
    @Op(">>>")
    UInt8 shiftAllRight(Int count)
        {
        return new UInt8(bits >>> count);
        }

    @Override
    UInt8 rotateLeft(Int count)
        {
        return new UInt8(bits.rotateLeft(count));
        }

    @Override
    UInt8 rotateRight(Int count)
        {
        return new UInt8(bits.rotateRight(count));
        }

    @Override
    UInt8 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt8(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    UInt8 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt8(bits.fill(0, [count..bitLength)));
        }

    @Override
    UInt8 reverseBits()
        {
        return new UInt8(bits.reversed());
        }

    @Override
    UInt8 reverseBytes()
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


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt8 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt8 prev()
        {
        if (this > minvalue)
            {
            return True, this - 1;
            }

        return False;
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    immutable Bit[] toBitArray()
        {
        return bits.as(immutable Bit[]);
        }

    @Override
    immutable Boolean[] toBooleanArray()
        {
        return new Array<Boolean>(bits.size, i -> bits[i].toBoolean()).freeze(True);
        }

    @Override
    UInt8! toChecked()
        {
        return this.is(Unchecked) ? new UInt8(bits) : this;
        }

    @Override
    @Unchecked UInt8 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UInt8(bits);
        }

    @Override
    @Auto Int8 toInt8()
        {
        assert:bounds this <= Int8.maxvalue;
        return new Int8(bits);
        }

    @Override
    @Auto Int16 toInt16()
        {
        return new Int16(new Array<Bit>(16, i -> (i < 16-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto Int32 toInt32()
        {
        return new Int32(new Array<Bit>(32, i -> (i < 32-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto Int64 toInt()
        {
        return new Int64(new Array<Bit>(64, i -> (i < 64-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto Int128 toInt128()
        {
        return new Int128(new Array<Bit>(128, i -> (i < 128-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto UInt8 toByte()
        {
        return this;
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        return new UInt16(new Array<Bit>(16, i -> (i < 16-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        return new UInt32(new Array<Bit>(32, i -> (i < 32-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto UInt64 toUInt()
        {
        return new UInt64(new Array<Bit>(64, i -> (i < 64-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        return new UInt128(new Array<Bit>(128, i -> (i < 128-bitLength ? 0 : bits[i])));
        }

    @Override
    @Auto IntN toIntN()
        {
        return bits[0] == 0 ? new IntN(bits) : toUInt16().toIntN();
        }

    @Override
    @Auto UIntN toUIntN()
        {
        return new UIntN(bits);
        }

    @Override
    @Auto FloatN toFloatN()
        {
        TODO
        }

    @Override
    @Auto DecN toDecN()
        {
        TODO
        }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return calculateStringSize(this, sizeArray);
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        if (sign == Zero)
            {
            buf.add('0');
            }
        else
            {
            (UInt8 left, UInt8 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(buf);
                }
            buf.add(DIGITS[digit]);
            }
        return buf;
        }

    private static UInt8[] sizeArray =
         [
         9, 99, 255
         ];
    }