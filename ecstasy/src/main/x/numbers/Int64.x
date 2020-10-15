const Int64
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int64.
     */
    static IntLiteral minvalue = -0x8000_0000_0000_0000;

    /**
     * The maximum value for an Int64.
     */
    static IntLiteral maxvalue =  0x7FFF_FFFF_FFFF_FFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct IntNumber(bits);
        }

    /**
     * Construct a 64-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 8;
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
    UInt64 magnitude.get()
        {
        return toInt128().abs().toUInt();
        }

    @Override
    Int64 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int64 rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int64 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    Int64 add(Int64 n)
        {
        TODO return new Int64(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    Int64 sub(Int64 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    Int64 mul(Int64 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    Int64 div(Int64 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    Int64 mod(Int64 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    Int64 and(Int64 n)
        {
        return new Int64(this.bits & n.bits);
        }

    @Override
    @Op("|")
    Int64 or(Int64 n)
        {
        return new Int64(this.bits | n.bits);
        }

    @Override
    @Op("^")
    Int64 xor(Int64 n)
        {
        return new Int64(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    Int64 not()
        {
        return new Int64(~bits);
        }

    @Override
    @Op("<<")
    Int64 shiftLeft(Int count)
        {
        return new Int64(bits << count);
        }

    @Override
    @Op(">>")
    Int64 shiftRight(Int count)
        {
        return new Int64(bits >> count);
        }

    @Override
    @Op(">>>")
    Int64 shiftAllRight(Int count)
        {
        return new Int64(bits >>> count);
        }

    @Override
    Int64 rotateLeft(Int count)
        {
        return new Int64(bits.rotateLeft(count));
        }

    @Override
    Int64 rotateRight(Int count)
        {
        return new Int64(bits.rotateRight(count));
        }

    @Override
    Int64 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int64(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    Int64 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int64(bits.fill(0, [count..bitLength)));
        }

    @Override
    Int64 reverseBits()
        {
        return new Int64(bits.reversed());
        }

    @Override
    Int64 reverseBytes()
        {
        return new Int64(toByteArray().reversed());
        }

    @Override
    Int64 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int64 pow(Int64 n)
        {
        Int64 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int64 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Int64 prev()
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
    Int64! toChecked()
        {
        return this.is(Unchecked) ? new Int64(bits) : this;
        }

    @Override
    @Unchecked Int64 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked Int64(bits);
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
        return this;
        }

    @Override
    @Auto Int128 toInt128()
        {
        return new Int128(new Array<Bit>(128, i -> bits[i < 128-bitLength ? 0 : i]));
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
        assert:bounds this >= 0;
        return new UInt64(bits);
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        assert:bounds this >= 0;
        return new UInt128(new Array<Bit>(128, i -> (i < 128-bitLength ? 0 : bits[i])));
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