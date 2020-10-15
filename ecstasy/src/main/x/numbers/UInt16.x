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
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 16;
        construct UIntNumber(bits);
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


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
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


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt16 add(UInt16 n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UInt16 sub(UInt16 n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UInt16 mul(UInt16 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UInt16 div(UInt16 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UInt16 mod(UInt16 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    UInt16 and(UInt16 n)
        {
        return new UInt16(this.bits & n.bits);
        }

    @Override
    @Op("|")
    UInt16 or(UInt16 n)
        {
        return new UInt16(this.bits | n.bits);
        }

    @Override
    @Op("^")
    UInt16 xor(UInt16 n)
        {
        return new UInt16(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    UInt16 not()
        {
        return new UInt16(~bits);
        }

    @Override
    @Op("<<")
    UInt16 shiftLeft(Int count)
        {
        return new UInt16(bits << count);
        }

    @Override
    @Op(">>")
    UInt16 shiftRight(Int count)
        {
        return new UInt16(bits >> count);
        }

    @Override
    @Op(">>>")
    UInt16 shiftAllRight(Int count)
        {
        return new UInt16(bits >>> count);
        }

    @Override
    UInt16 rotateLeft(Int count)
        {
        return new UInt16(bits.rotateLeft(count));
        }

    @Override
    UInt16 rotateRight(Int count)
        {
        return new UInt16(bits.rotateRight(count));
        }

    @Override
    UInt16 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt16(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    UInt16 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt16(bits.fill(0, [count..bitLength)));
        }

    @Override
    UInt16 reverseBits()
        {
        return new UInt16(bits.reversed());
        }

    @Override
    UInt16 reverseBytes()
        {
        return new UInt16(toByteArray().reversed());
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


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt16 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt16 prev()
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
    UInt16! toChecked()
        {
        return this.is(Unchecked) ? new UInt16(bits) : this;
        }

    @Override
    @Unchecked UInt16 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UInt16(bits);
        }

    @Override
    @Auto Int8 toInt8()
        {
        assert:bounds this <= Int8.maxvalue;
        return new Int8(bits[bitLength-8..bitLength));
        }

    @Override
    @Auto Int16 toInt16()
        {
        assert:bounds this <= Int16.maxvalue;
        return new Int16(bits);
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
        assert:bounds this <= UInt8.maxvalue;
        return new UInt8(bits[bitLength-8..bitLength));
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        return this;
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
        return bits[0] == 0 ? new IntN(bits) : toUIntN().toIntN();
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
            (UInt16 left, UInt16 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(buf);
                }
            buf.add(DIGITS[digit]);
            }
        return buf;
        }

    // maxvalue = 65_535 (5 digits)
    private static UInt16[] sizeArray =
         [
         9, 99, 999, 9_999, 65_535
         ];
    }
