const Int128
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int128.
     */
    static IntLiteral minvalue = -0x8000_0000_0000_0000_0000_0000_0000_0000;

    /**
     * The maximum value for an Int128.
     */
    static IntLiteral maxvalue =  0x7FFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct IntNumber(bits);
        }

    /**
     * Construct a 128-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 16;
        construct IntNumber(bytes);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return switch (this <=> 0)
            {
            case Lesser : Negative;
            case Equal  : Zero;
            case Greater: Positive;
            };
        }

    @Override
    UInt128 magnitude.get()
        {
        return toIntN().abs().toUInt128();
        }

    @Override
    Int128 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int128 rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int128 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    Int128 add(Int128 n)
        {
        TODO return new Int128(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    Int128 sub(Int128 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    Int128 mul(Int128 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    Int128 div(Int128 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    Int128 mod(Int128 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    Int128 and(Int128 n)
        {
        return new Int128(this.bits & n.bits);
        }

    @Override
    @Op("|")
    Int128 or(Int128 n)
        {
        return new Int128(this.bits | n.bits);
        }

    @Override
    @Op("^")
    Int128 xor(Int128 n)
        {
        return new Int128(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    Int128 not()
        {
        return new Int128(~bits);
        }

    @Override
    @Op("<<")
    Int128 shiftLeft(Int count)
        {
        return new Int128(bits << count);
        }

    @Override
    @Op(">>")
    Int128 shiftRight(Int count)
        {
        return new Int128(bits >> count);
        }

    @Override
    @Op(">>>")
    Int128 shiftAllRight(Int count)
        {
        return new Int128(bits >>> count);
        }

    @Override
    Int128 rotateLeft(Int count)
        {
        return new Int128(bits.rotateLeft(count));
        }

    @Override
    Int128 rotateRight(Int count)
        {
        return new Int128(bits.rotateRight(count));
        }

    @Override
    Int128 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int128(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    Int128 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int128(bits.fill(0, [count..bitLength)));
        }

    @Override
    Int128 reverseBits()
        {
        return new Int128(bits.reversed());
        }

    @Override
    Int128 reverseBytes()
        {
        return new Int128(toByteArray().reversed());
        }

    @Override
    Int128 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int128 pow(Int128 n)
        {
        Int128 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int128 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Int128 prev()
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
    Int128! toChecked()
        {
        return this.is(Unchecked) ? new Int128(bits) : this;
        }

    @Override
    @Unchecked Int128 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked Int128(bits);
        }

    @Override
    @Auto Int8 toInt8()
        {
        assert:bounds this >= Int8.minvalue && this <= Int8.maxvalue;
        return new Int8(bits[bitLength-8..bitLength));
        }

    @Override
    @Auto Int16 toInt16()
        {
        assert:bounds this >= Int16.minvalue && this <= Int16.maxvalue;
        return new Int16(bits[bitLength-16..bitLength));
        }

    @Override
    @Auto Int32 toInt32()
        {
        assert:bounds this >= Int32.minvalue && this <= Int32.maxvalue;
        return new Int32(bits[bitLength-32..bitLength));
        }

    @Override
    @Auto Int64 toInt()
        {
        assert:bounds this >= Int64.minvalue && this <= Int64.maxvalue;
        return new Int64(bits[bitLength-64..bitLength));
        }

    @Override
    @Auto Int128 toInt128()
        {
        return this;
        }

    @Override
    @Auto UInt8 toByte()
        {
        assert:bounds this >= UInt8.minvalue && this <= UInt8.maxvalue;
        return new UInt8(bits[bitLength-8..bitLength));
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        assert:bounds this >= UInt16.minvalue && this <= UInt16.maxvalue;
        return new UInt16(bits[bitLength-16..bitLength));
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        assert:bounds this >= UInt32.minvalue && this <= UInt32.maxvalue;
        return new UInt32(bits[bitLength-32..bitLength));
        }

    @Override
    @Auto UInt64 toUInt()
        {
        assert:bounds this >= UInt64.minvalue && this <= UInt64.maxvalue;
        return new UInt64(bits[bitLength-64..bitLength));
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        assert:bounds this >= 0;
        return new UInt128(bits);
        }

    @Override
    @Auto IntN toIntN()
        {
        return new IntN(bits);
        }

    @Override
    @Auto UIntN toUIntN()
        {
        assert:bounds this >= 0;
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
    }