/**
 * An unsigned integer with a power-of-2 number of bits (at least 8).
 */
const UIntN
        extends UIntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct UIntNumber(bits);
        }

    /**
     * Construct a variable-length unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 1;
        construct UIntNumber(bytes);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
        }

    @Override
    UIntN leftmostBit.get()
        {
        TODO
        }

    @Override
    UIntN rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UIntN add(UIntN n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UIntN sub(UIntN n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UIntN mul(UIntN n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UIntN div(UIntN n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UIntN mod(UIntN n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    UIntN and(UIntN n)
        {
        return new UIntN(this.bits & n.bits);
        }

    @Override
    @Op("|")
    UIntN or(UIntN n)
        {
        return new UIntN(this.bits | n.bits);
        }

    @Override
    @Op("^")
    UIntN xor(UIntN n)
        {
        return new UIntN(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    UIntN not()
        {
        return new UIntN(~bits);
        }

    @Override
    @Op("<<")
    UIntN shiftLeft(Int count)
        {
        return new UIntN(bits << count);
        }

    @Override
    @Op(">>")
    UIntN shiftRight(Int count)
        {
        return new UIntN(bits >> count);
        }

    @Override
    @Op(">>>")
    UIntN shiftAllRight(Int count)
        {
        return new UIntN(bits >>> count);
        }

    @Override
    UIntN rotateLeft(Int count)
        {
        return new UIntN(bits.rotateLeft(count));
        }

    @Override
    UIntN rotateRight(Int count)
        {
        return new UIntN(bits.rotateRight(count));
        }

    @Override
    UIntN retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UIntN(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    UIntN retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UIntN(bits.fill(0, [count..bitLength)));
        }

    @Override
    UIntN reverseBits()
        {
        return new UIntN(bits.reversed());
        }

    @Override
    UIntN reverseBytes()
        {
        return new UIntN(toByteArray().reversed());
        }

    @Override
    UIntN pow(UIntN n)
        {
        UIntN result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UIntN next()
        {
        return True, this + 1;
        }

    @Override
    conditional UIntN prev()
        {
        if (this > 0)
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
    UIntN! toChecked()
        {
        return this.is(Unchecked) ? new UIntN(bits) : this;
        }

    @Override
    @Unchecked UIntN toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UIntN(bits);
        }

    @Override
    @Auto Int8 toInt8()
        {
        assert:bounds this <= Int8.maxvalue;
        return bitLength < 8
                ? new Int8(new Array<Bit>(8, i -> (i < 8-bitLength ? 0 : bits[i])))
                : new Int8(bits[bitLength-8..bitLength));
        }

    @Override
    @Auto Int16 toInt16()
        {
        assert:bounds this <= Int16.maxvalue;
        return bitLength < 16
                ? new Int16(new Array<Bit>(16, i -> (i < 16-bitLength ? 0 : bits[i])))
                : new Int16(bits[bitLength-16..bitLength));
        }

    @Override
    @Auto Int32 toInt32()
        {
        assert:bounds this <= Int32.maxvalue;
        return bitLength < 32
                ? new Int32(new Array<Bit>(32, i -> (i < 32-bitLength ? 0 : bits[i])))
                : new Int32(bits[bitLength-32..bitLength));
        }

    @Override
    @Auto Int64 toInt()
        {
        assert:bounds this <= Int64.maxvalue;
        return bitLength < 64
                ? new Int64(new Array<Bit>(64, i -> (i < 64-bitLength ? 0 : bits[i])))
                : new Int64(bits[bitLength-64..bitLength));
        }

    @Override
    @Auto Int128 toInt128()
        {
        assert:bounds this <= Int128.maxvalue;
        return bitLength < 128
                ? new Int128(new Array<Bit>(128, i -> (i < 128-bitLength ? 0 : bits[i])))
                : new Int128(bits[bitLength-128..bitLength));
        }

    @Override
    @Auto UInt8 toByte()
        {
        assert:bounds this <= UInt8.maxvalue;
        return bitLength < 8
                ? new UInt8(new Array<Bit>(8, i -> (i < 8-bitLength ? 0 : bits[i])))
                : new UInt8(bits[bitLength-8..bitLength));
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        assert:bounds this <= UInt16.maxvalue;
        return bitLength < 16
                ? new UInt16(new Array<Bit>(16, i -> (i < 16-bitLength ? 0 : bits[i])))
                : new UInt16(bits[bitLength-16..bitLength));
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        assert:bounds this <= UInt32.maxvalue;
        return bitLength < 32
                ? new UInt32(new Array<Bit>(32, i -> (i < 32-bitLength ? 0 : bits[i])))
                : new UInt32(bits[bitLength-32..bitLength));
        }

    @Override
    @Auto UInt64 toUInt()
        {
        assert:bounds this <= UInt64.maxvalue;
        return bitLength < 64
                ? new UInt64(new Array<Bit>(64, i -> (i < 64-bitLength ? 0 : bits[i])))
                : new UInt64(bits[bitLength-64..bitLength));
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        assert:bounds this <= UInt64.maxvalue;
        return bitLength < 128
                ? new UInt128(new Array<Bit>(128, i -> (i < 128-bitLength ? 0 : bits[i])))
                : new UInt128(bits[bitLength-128..bitLength));
        }

    @Override
    @Auto IntN toIntN()
        {
        Bit[] bits = this.bits;
        if (bits[0] == 1)
            {
            bits = new Array<Bit>(bits.size + 8, i -> (i < 8 ? 0 : bits[i-8]));
            }
        return new IntN(bits);
        }

    @Override
    @Auto UIntN toUIntN()
        {
        return this;
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
