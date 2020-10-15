/**
 * A signed integer (using twos-complement) with a variable number of bytes.
 */
const IntN
        extends IntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct IntNumber(bits);
        }

    /**
     * Construct a variable-length signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 1;
        construct IntNumber(bytes);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        // twos-complement number will have the MSB set if negative
        if (bits[bits.size-1] == 1)
            {
            return Negative;
            }

        // any other bits set to 1 means positive
        return this == 0 ? Zero : Positive;
        }

    @Override
    UIntN magnitude.get()
        {
        return abs().toUIntN();
        }

    @Override
    IntN leftmostBit.get()
        {
        TODO
        }

    @Override
    IntN rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    IntN neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    IntN add(IntN n)
        {
        TODO return new IntN(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    IntN sub(IntN n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    IntN mul(IntN n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    IntN div(IntN n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    IntN mod(IntN n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    IntN and(IntN n)
        {
        return new IntN(this.bits & n.bits);
        }

    @Override
    @Op("|")
    IntN or(IntN n)
        {
        return new IntN(this.bits | n.bits);
        }

    @Override
    @Op("^")
    IntN xor(IntN n)
        {
        return new IntN(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    IntN not()
        {
        return new IntN(~bits);
        }

    @Override
    @Op("<<")
    IntN shiftLeft(Int count)
        {
        return new IntN(bits << count);
        }

    @Override
    @Op(">>")
    IntN shiftRight(Int count)
        {
        return new IntN(bits >> count);
        }

    @Override
    @Op(">>>")
    IntN shiftAllRight(Int count)
        {
        return new IntN(bits >>> count);
        }

    @Override
    IntN rotateLeft(Int count)
        {
        return new IntN(bits.rotateLeft(count));
        }

    @Override
    IntN rotateRight(Int count)
        {
        return new IntN(bits.rotateRight(count));
        }

    @Override
    IntN retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new IntN(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    IntN retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new IntN(bits.fill(0, [count..bitLength)));
        }

    @Override
    IntN reverseBits()
        {
        return new IntN(bits.reversed());
        }

    @Override
    IntN reverseBytes()
        {
        return new IntN(toByteArray().reversed());
        }

    @Override
    IntN abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    IntN pow(IntN n)
        {
        IntN result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional IntN next()
        {
        return True, this + 1;
        }

    @Override
    conditional IntN prev()
        {
        return True, this - 1;
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
    IntN! toChecked()
        {
        return this.is(Unchecked) ? new IntN(bits) : this;
        }

    @Override
    @Unchecked IntN toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked IntN(bits);
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
        assert:bounds this >= Int128.minvalue && this <= Int128.maxvalue;
        return new Int128(bits[bitLength-128..bitLength));
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
        assert:bounds this >= UInt128.minvalue && this <= UInt128.maxvalue;
        return new UInt128(bits[bitLength-128..bitLength));
        }

    @Override
    @Auto IntN toIntN()
        {
        return this;
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