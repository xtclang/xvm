const Int16
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int16.
     */
    static IntLiteral minvalue = -0x8000;

    /**
     * The maximum value for an Int16.
     */
    static IntLiteral maxvalue = 0x7FFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 16;
        construct IntNumber(bits);
        }

    /**
     * Construct a 16-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 2;
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
    UInt16 magnitude.get()
        {
        return toInt32().abs().toUInt16();
        }

    @Override
    Int16 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int16 rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int16 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    Int16 add(Int16 n)
        {
        TODO return new Int16(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    Int16 sub(Int16 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    Int16 mul(Int16 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    Int16 div(Int16 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    Int16 mod(Int16 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    Int16 and(Int16 n)
        {
        return new Int16(this.bits & n.bits);
        }

    @Override
    @Op("|")
    Int16 or(Int16 n)
        {
        return new Int16(this.bits | n.bits);
        }

    @Override
    @Op("^")
    Int16 xor(Int16 n)
        {
        return new Int16(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    Int16 not()
        {
        return new Int16(~bits);
        }

    @Override
    @Op("<<")
    Int16 shiftLeft(Int count)
        {
        return new Int16(bits << count);
        }

    @Override
    @Op(">>")
    Int16 shiftRight(Int count)
        {
        return new Int16(bits >> count);
        }

    @Override
    @Op(">>>")
    Int16 shiftAllRight(Int count)
        {
        return new Int16(bits >>> count);
        }

    @Override
    Int16 rotateLeft(Int count)
        {
        return new Int16(bits.rotateLeft(count));
        }

    @Override
    Int16 rotateRight(Int count)
        {
        return new Int16(bits.rotateRight(count));
        }

    @Override
    Int16 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int16(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    Int16 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int16(bits.fill(0, [count..bitLength)));
        }

    @Override
    Int16 reverseBits()
        {
        return new Int16(bits.reversed());
        }

    @Override
    Int16 reverseBytes()
        {
        return new Int16(toByteArray().reversed());
        }

    @Override
    Int16 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int16 pow(Int16 n)
        {
        Int16 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int16 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Int16 prev()
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
    Int16! toChecked()
        {
        return this.is(Unchecked) ? new Int16(bits) : this;
        }

    @Override
    @Unchecked Int16 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked Int16(bits);
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
        return this;
        }

    @Override
    @Auto Int32 toInt32()
        {
        return new Int32(new Array<Bit>(32, i -> bits[i < 32-bitLength ? 0 : i]));
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
        assert:bounds this >= 0;
        return new UInt16(bits);
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        assert:bounds this >= 0;
        return new UInt32(new Array<Bit>(32, i -> (i < 32-bitLength ? 0 : bits[i])));
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