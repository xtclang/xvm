/**
 * An unsigned integer with a power-of-2 number of bits (at least 8).
 */
const VarUInt
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
    VarUInt leftmostBit.get()
        {
        TODO
        }

    @Override
    VarUInt rightmostBit.get()
        {
        TODO
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    VarUInt add(VarUInt n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    VarUInt sub(VarUInt n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    VarUInt mul(VarUInt n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    VarUInt div(VarUInt n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    VarUInt mod(VarUInt n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    VarUInt and(VarUInt n)
        {
        return new VarUInt(this.bits & n.bits);
        }

    @Override
    @Op("|")
    VarUInt or(VarUInt n)
        {
        return new VarUInt(this.bits | n.bits);
        }

    @Override
    @Op("^")
    VarUInt xor(VarUInt n)
        {
        return new VarUInt(this.bits ^ n.bits);
        }

    @Override
    @Op("~")
    VarUInt not()
        {
        return new VarUInt(~bits);
        }

    @Override
    @Op("<<")
    VarUInt shiftLeft(Int count)
        {
        return new VarUInt(bits << count);
        }

    @Override
    @Op(">>")
    VarUInt shiftRight(Int count)
        {
        return new VarUInt(bits >> count);
        }

    @Override
    @Op(">>>")
    VarUInt shiftAllRight(Int count)
        {
        return new VarUInt(bits >>> count);
        }

    @Override
    VarUInt rotateLeft(Int count)
        {
        return new VarUInt(bits.rotateLeft(count));
        }

    @Override
    VarUInt rotateRight(Int count)
        {
        return new VarUInt(bits.rotateRight(count));
        }

    @Override
    VarUInt retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new VarUInt(bits.fill(0, [0..bitLength-count)));
        }

    @Override
    VarUInt retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new VarUInt(bits.fill(0, [count..bitLength)));
        }

    @Override
    VarUInt reverseBits()
        {
        return new VarUInt(bits.reverse());
        }

    @Override
    VarUInt reverseBytes()
        {
        return new VarUInt(toByteArray().reverse());
        }

    @Override
    VarUInt pow(VarUInt n)
        {
        VarUInt result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional VarUInt next()
        {
        return true, this + 1;
        }

    @Override
    conditional VarUInt prev()
        {
        if (this > 0)
            {
            return true, this - 1;
            }

        return false;
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
    VarUInt! toChecked()
        {
        return this.is(Unchecked) ? new VarUInt(bits) : this;
        }

    @Override
    @Unchecked VarUInt toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked VarUInt(bits);
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
    @Auto VarInt toVarInt()
        {
        Bit[] bits = this.bits;
        if (bits[0] == 1)
            {
            bits = new Array<Bit>(bits.size + 8, i -> (i < 8 ? 0 : bits[i-8]));
            }
        return new VarInt(bits);
        }

    @Override
    @Auto VarUInt toVarUInt()
        {
        return this;
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
