const Int8
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int8.
     */
    static IntLiteral minvalue = -128;

    /**
     * The maximum value for an Int8.
     */
    static IntLiteral maxvalue = 127;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an 8-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 8;
        construct IntNumber(bits);
        }

    /**
     * Construct an 8-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 1;
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
    UInt8 magnitude.get()
        {
        return toInt16().abs().toByte();
        }

    @Override
    Int8 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int8 rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int8 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    Int8 add(Int8 n)
        {
        TODO return new Int8(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    Int8 sub(Int8 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    Int8 mul(Int8 n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    Int8 div(Int8 n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    Int8 mod(Int8 n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    Int8 and(Int8 n)
        {
        return new Int8(this.bits & n.bits);
        }

    @Override
    @Op("|")
    Int8 or(Int8 n)
        {
        return new Int8(this.bits | n.bits);
        }

    @Override
    @Op("^")
    Int8 xor(Int8 n)
        {
        return new Int8(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    Int8 not()
        {
        return new Int8(~bits);
        }

    @Override
    @Op("<<")
    Int8 shiftLeft(Int count)
        {
        return new Int8(bits << count);
        }

    @Override
    @Op(">>")
    Int8 shiftRight(Int count)
        {
        return new Int8(bits >> count);
        }

    @Override
    @Op(">>>")
    Int8 shiftAllRight(Int count)
        {
        return new Int8(bits >>> count);
        }

    @Override
    Int8 rotateLeft(Int count)
        {
        return new Int8(bits.rotateLeft(count));
        }

    @Override
    Int8 rotateRight(Int count)
        {
        return new Int8(bits.rotateRight(count));
        }

    @Override
    Int8 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int8(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    Int8 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new Int8(bits.fill(0, [count..bitLength)));
        }

    @Override
    Int8 reverseBits()
        {
        return new Int8(bits.reversed());
        }

    @Override
    Int8 reverseBytes()
        {
        return this;
        }

    @Override
    Int8 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int8 pow(Int8 n)
        {
        Int8 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int8 next()
        {
        if (this < maxvalue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional Int8 prev()
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
    Int8! toChecked()
        {
        return this.is(Unchecked) ? new Int8(bits) : this;
        }

    @Override
    @Unchecked Int8 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked Int8(bits);
        }

    @Override
    @Auto Int8 toInt8()
        {
        return this;
        }

    @Override
    @Auto Int16 toInt16()
        {
        return new Int16(new Array<Bit>(16, i -> bits[i < 16-bitLength ? 0 : i]));
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
        assert:bounds this >= 0;
        return new UInt8(bits);
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        assert:bounds this >= 0;
        return new UInt16(new Array<Bit>(16, i -> (i < 16-bitLength ? 0 : bits[i]))); // TODO CP+GG these extra () suck because no ? : in lambda
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