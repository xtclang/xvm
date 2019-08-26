/**
 * A signed integer (using twos-complement) with a variable number of bytes.
 */
const VarInt
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
    VarUInt magnitude.get()
        {
        return abs().toVarUInt();
        }

    @Override
    VarInt leftmostBit.get()
        {
        TODO
        }

    @Override
    VarInt rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    VarInt neg()
        {
        return ~this + 1;
        }

    @Override
    @Op("+")
    VarInt add(VarInt n)
        {
        TODO return new VarInt(bitAdd(bits, n.bits));
        }

    @Override
    @Op("-")
    VarInt sub(VarInt n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op("*")
    VarInt mul(VarInt n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    VarInt div(VarInt n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    VarInt mod(VarInt n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    VarInt and(VarInt n)
        {
        return new VarInt(this.bits & n.bits);
        }

    @Override
    @Op("|")
    VarInt or(VarInt n)
        {
        return new VarInt(this.bits | n.bits);
        }

    @Override
    @Op("^")
    VarInt xor(VarInt n)
        {
        return new VarInt(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    VarInt not()
        {
        return new VarInt(~bits);
        }

    @Override
    @Op("<<")
    VarInt shiftLeft(Int count)
        {
        return new VarInt(bits << count);
        }

    @Override
    @Op(">>")
    VarInt shiftRight(Int count)
        {
        return new VarInt(bits >> count);
        }

    @Override
    @Op(">>>")
    VarInt shiftAllRight(Int count)
        {
        return new VarInt(bits >>> count);
        }

    @Override
    VarInt rotateLeft(Int count)
        {
        return new VarInt(bits.rotateLeft(count));
        }

    @Override
    VarInt rotateRight(Int count)
        {
        return new VarInt(bits.rotateRight(count));
        }

    @Override
    VarInt retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new VarInt(bits.fill(0, 0..bitLength-count-1));
        }

    @Override
    VarInt retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new VarInt(bits.fill(0, count..bitLength-1));
        }

    @Override
    VarInt reverseBits()
        {
        return new VarInt(bits.reverse());
        }

    @Override
    VarInt reverseBytes()
        {
        return new VarInt(toByteArray().reverse());
        }

    @Override
    VarInt abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    VarInt pow(VarInt n)
        {
        VarInt result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional VarInt next()
        {
        return true, this + 1;
        }

    @Override
    conditional VarInt prev()
        {
        return true, this - 1;
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
        return new Array<Boolean>(bits.size, i -> bits[i].toBoolean()).ensureConst(True);
        }

    @Override
    VarInt! toChecked()
        {
        return this.is(Unchecked) ? new VarInt!(bits) : this;
        }

    @Override
    @Unchecked VarInt toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked VarInt!(bits);
        }

    @Override
    @Auto Int8 toInt8()
        {
        assert:bounds this >= Int8.minvalue && this <= Int8.maxvalue;
        return new Int8(bits[bitLength-8..bitLength-1]);
        }

    @Override
    @Auto Int16 toInt16()
        {
        assert:bounds this >= Int16.minvalue && this <= Int16.maxvalue;
        return new Int16(bits[bitLength-16..bitLength-1]);
        }

    @Override
    @Auto Int32 toInt32()
        {
        assert:bounds this >= Int32.minvalue && this <= Int32.maxvalue;
        return new Int32(bits[bitLength-32..bitLength-1]);
        }

    @Override
    @Auto Int64 toInt()
        {
        assert:bounds this >= Int64.minvalue && this <= Int64.maxvalue;
        return new Int64(bits[bitLength-64..bitLength-1]);
        }

    @Override
    @Auto Int128 toInt128()
        {
        assert:bounds this >= Int128.minvalue && this <= Int128.maxvalue;
        return new Int128(bits[bitLength-128..bitLength-1]);
        }

    @Override
    @Auto UInt8 toByte()
        {
        assert:bounds this >= UInt8.minvalue && this <= UInt8.maxvalue;
        return new UInt8(bits[bitLength-8..bitLength-1]);
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        assert:bounds this >= UInt16.minvalue && this <= UInt16.maxvalue;
        return new UInt16(bits[bitLength-16..bitLength-1]);
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        assert:bounds this >= UInt32.minvalue && this <= UInt32.maxvalue;
        return new UInt32(bits[bitLength-32..bitLength-1]);
        }

    @Override
    @Auto UInt64 toUInt()
        {
        assert:bounds this >= UInt64.minvalue && this <= UInt64.maxvalue;
        return new UInt64(bits[bitLength-64..bitLength-1]);
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        assert:bounds this >= UInt128.minvalue && this <= UInt128.maxvalue;
        return new UInt128(bits[bitLength-128..bitLength-1]);
        }

    @Override
    @Auto VarInt toVarInt()
        {
        return this;
        }

    @Override
    @Auto VarUInt toVarUInt()
        {
        assert:bounds this >= 0;
        return new VarUInt(bits);
        }

    @Override
    @Auto VarFloat toVarFloat()
        {
        TODO
        }

    @Override
    @Auto VarDec toVarDec()
        {
        TODO
        }
    }