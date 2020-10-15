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
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct UIntNumber(bits);
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


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
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


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt128 add(UInt128 n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UInt128 sub(UInt128 n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UInt128 mul(UInt128 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UInt128 div(UInt128 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UInt128 mod(UInt128 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    UInt128 and(UInt128 n)
        {
        return new UInt128(this.bits & n.bits);
        }

    @Override
    @Op("|")
    UInt128 or(UInt128 n)
        {
        return new UInt128(this.bits | n.bits);
        }

    @Override
    @Op("^")
    UInt128 xor(UInt128 n)
        {
        return new UInt128(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    UInt128 not()
        {
        return new UInt128(~bits);
        }

    @Override
    @Op("<<")
    UInt128 shiftLeft(Int count)
        {
        return new UInt128(bits << count);
        }

    @Override
    @Op(">>")
    UInt128 shiftRight(Int count)
        {
        return new UInt128(bits >> count);
        }

    @Override
    @Op(">>>")
    UInt128 shiftAllRight(Int count)
        {
        return new UInt128(bits >>> count);
        }

    @Override
    UInt128 rotateLeft(Int count)
        {
        return new UInt128(bits.rotateLeft(count));
        }

    @Override
    UInt128 rotateRight(Int count)
        {
        return new UInt128(bits.rotateRight(count));
        }

    @Override
    UInt128 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt128(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    UInt128 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt128(bits.fill(0, [count..bitLength)));
        }

    @Override
    UInt128 reverseBits()
        {
        return new UInt128(bits.reversed());
        }

    @Override
    UInt128 reverseBytes()
        {
        return new UInt128(toByteArray().reversed());
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


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt128 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt128 prev()
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
    UInt128! toChecked()
        {
        return this.is(Unchecked) ? new UInt128(bits) : this;
        }

    @Override
    @Unchecked UInt128 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UInt128(bits);
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
        return new Int64(bits[bitLength-64..bitLength));
        }

    @Override
    @Auto Int128 toInt128()
        {
        assert:bounds this <= Int128.maxvalue;
        return new Int128(bits);
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
        assert:bounds this <= UInt64.maxvalue;
        return new UInt64(bits[bitLength-64..bitLength));
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        return this;
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
        if (this == 0)
            {
            buf.add('0');
            }
        else
            {
            (UInt128 left, UInt128 digit) = this /% 10;
            if (left != 0)
                {
                left.appendTo(buf);
                }
            buf.add(DIGITS[digit]);
            }
        return buf;
        }

    // maxvalue = 340_282_366_920_938_463_463_374_607_431_768_211_455 (39 digits)
    private static UInt128[] sizeArray =
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