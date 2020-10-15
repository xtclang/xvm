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
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct UIntNumber(bits);
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


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
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


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt64 add(UInt64 n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UInt64 sub(UInt64 n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UInt64 mul(UInt64 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UInt64 div(UInt64 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UInt64 mod(UInt64 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    UInt64 and(UInt64 n)
        {
        return new UInt64(this.bits & n.bits);
        }

    @Override
    @Op("|")
    UInt64 or(UInt64 n)
        {
        return new UInt64(this.bits | n.bits);
        }

    @Override
    @Op("^")
    UInt64 xor(UInt64 n)
        {
        return new UInt64(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    UInt64 not()
        {
        return new UInt64(~bits);
        }

    @Override
    @Op("<<")
    UInt64 shiftLeft(Int count)
        {
        return new UInt64(bits << count);
        }

    @Override
    @Op(">>")
    UInt64 shiftRight(Int count)
        {
        return new UInt64(bits >> count);
        }

    @Override
    @Op(">>>")
    UInt64 shiftAllRight(Int count)
        {
        return new UInt64(bits >>> count);
        }

    @Override
    UInt64 rotateLeft(Int count)
        {
        return new UInt64(bits.rotateLeft(count));
        }

    @Override
    UInt64 rotateRight(Int count)
        {
        return new UInt64(bits.rotateRight(count));
        }

    @Override
    UInt64 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt64(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    UInt64 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt64(bits.fill(0, [count..bitLength)));
        }

    @Override
    UInt64 reverseBits()
        {
        return new UInt64(bits.reversed());
        }

    @Override
    UInt64 reverseBytes()
        {
        return new UInt64(toByteArray().reversed());
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


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt64 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt64 prev()
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
    UInt64! toChecked()
        {
        return this.is(Unchecked) ? new UInt64(bits) : this;
        }

    @Override
    @Unchecked UInt64 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UInt64(bits);
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
        return new Int16(bits[bitLength-16..bitLength));
        }

    @Override
    @Auto Int32 toInt32()
        {
        assert:bounds this <= Int32.maxvalue;
        return new Int32(bits[bitLength-32..bitLength));
        }

    @Override
    @Auto Int64 toInt()
        {
        assert:bounds this <= Int64.maxvalue;
        return new Int64(bits);
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
        assert:bounds this <= UInt16.maxvalue;
        return new UInt16(bits[bitLength-16..bitLength));
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        assert:bounds this <= UInt32.maxvalue;
        return new UInt32(bits[bitLength-32..bitLength));
        }

    @Override
    @Auto UInt64 toUInt()
        {
        return this;
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
            (UInt64 left, UInt64 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(buf);
                }
            buf.add(DIGITS[digit]);
            }
        return buf;
        }

    // maxvalue = 18_446_744_073_709_551_615 (20 digits)
    private static UInt64[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999, 9_999_999_999, 99_999_999_999, 999_999_999_999,
         9_999_999_999_999, 99_999_999_999_999, 999_999_999_999_999,
         9_999_999_999_999_999, 99_999_999_999_999_999, 999_999_999_999_999_999,
         9_999_999_999_999_999_999, 18_446_744_073_709_551_615
         ];
    }