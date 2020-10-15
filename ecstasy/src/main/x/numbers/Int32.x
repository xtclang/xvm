const Int32
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int32.
     */
    static IntLiteral minvalue = -0x80000000;

    /**
     * The maximum value for an Int32.
     */
    static IntLiteral maxvalue =  0x7FFFFFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct IntNumber(bits);
        }

    /**
     * Construct a 32-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
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
    UInt32 magnitude.get()
        {
        return toInt().abs().toUInt32();
        }

    @Override
    Int32 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int32 rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int32 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    Int32 add(Int32 n)
        {
        TODO return new Int32(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    Int32 sub(Int32 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    Int32 mul(Int32 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    Int32 div(Int32 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    Int32 mod(Int32 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    Int32 and(Int32 n)
        {
        return new Int32(this.bits & n.bits);
        }

    @Override
    @Op("|")
    Int32 or(Int32 n)
        {
        return new Int32(this.bits | n.bits);
        }

    @Override
    @Op("^")
    Int32 xor(Int32 n)
        {
        return new Int32(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    Int32 not()
        {
        return new Int32(~bits);
        }

    @Override
    @Op("<<")
    Int32 shiftLeft(Int count)
        {
        return new Int32(bits << count);
        }

    @Override
    @Op(">>")
    Int32 shiftRight(Int count)
        {
        return new Int32(bits >> count);
        }

    @Override
    @Op(">>>")
    Int32 shiftAllRight(Int count)
        {
        return new Int32(bits >>> count);
        }

    @Override
    Int32 rotateLeft(Int count)
        {
        return new Int32(bits.rotateLeft(count));
        }

    @Override
    Int32 rotateRight(Int count)
        {
        return new Int32(bits.rotateRight(count));
        }

    @Override
    Int32 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int32(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    Int32 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int32(bits.fill(0, [count..bitLength)));
        }

    @Override
    Int32 reverseBits()
        {
        return new Int32(bits.reversed());
        }

    @Override
    Int32 reverseBytes()
        {
        return new Int32(toByteArray().reversed());
        }

    @Override
    Int32 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int32 pow(Int32 n)
        {
        Int32 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int32 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Int32 prev()
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
    Int32! toChecked()
        {
        return this.is(Unchecked) ? new Int32(bits) : this;
        }

    @Override
    @Unchecked Int32 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked Int32(bits);
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
        return this;
        }

    @Override
    @Auto Int64 toInt()
        {
        return new Int64(new Array<Bit>(64, i -> bits[i < 64-bitLength ? 0 : i]));
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
        assert:bounds this >= 0;
        return new UInt32(bits);
        }

    @Override
    @Auto UInt64 toUInt()
        {
        assert:bounds this >= 0;
        return new UInt64(new Array<Bit>(64, i -> (i < 64-bitLength ? 0 : bits[i])));
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