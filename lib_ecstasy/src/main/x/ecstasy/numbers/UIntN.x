/**
 * An unsigned integer with a power-of-2 number of bits (at least 8).
 */
const UIntN
        extends UIntNumber
        default(0)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        super(bits);
        }

    /**
     * Construct a variable-length unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert bytes.size >= 1;
        super(bytes);
        }

    /**
     * Construct a variable-length unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text)
        {
        construct UIntN(new IntLiteral(text).toUIntN().bits);
        }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static UIntN zero()
        {
        return 0;
        }

    @Override
    static UIntN one()
        {
        return 1;
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
    UIntN add(UIntN! n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UIntN sub(UIntN! n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UIntN mul(UIntN! n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UIntN div(UIntN! n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UIntN mod(UIntN! n)
        {
        return this % n;
        }

    @Override
    @Op("&")
    UIntN and(UIntN! n)
        {
        return new UIntN(this.bits & n.bits);
        }

    @Override
    @Op("|")
    UIntN or(UIntN! n)
        {
        return new UIntN(this.bits | n.bits);
        }

    @Override
    @Op("^")
    UIntN xor(UIntN! n)
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

        return new UIntN(bits.fill(0, 0 ..< bitLength-count));
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

        return new UIntN(bits.fill(0, count ..< bitLength));
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
    UIntN pow(UIntN! n)
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
    (UIntN - Unchecked) toChecked()
        {
        return this.is(Unchecked) ? new UIntN(bits) : this;
        }

    @Override
    @Unchecked UIntN toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UIntN(bits);
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int8.MaxValue;
        return bitLength < 8
                ? new Int8(new Array<Bit>(8, i -> i < 8-bitLength ? 0 : bits[i]))
                : new Int8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int16.MaxValue;
        return bitLength < 16
                ? new Int16(new Array<Bit>(16, i -> i < 16-bitLength ? 0 : bits[i]))
                : new Int16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int32.MaxValue;
        return bitLength < 32
                ? new Int32(new Array<Bit>(32, i -> i < 32-bitLength ? 0 : bits[i]))
                : new Int32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int64.MaxValue;
        return bitLength < 64
                ? new Int64(new Array<Bit>(64, i -> i < 64-bitLength ? 0 : bits[i]))
                : new Int64(bits[bitLength-64 ..< bitLength]);
        }

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int128.MaxValue;
        return bitLength < 128
                ? new Int128(new Array<Bit>(128, i -> i < 128-bitLength ? 0 : bits[i]))
                : new Int128(bits[bitLength-128 ..< bitLength]);
        }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        Bit[] bits = this.bits;
        if (bits[0] == 1)
            {
            bits = new Array<Bit>(bits.size + 8, i -> i < 8 ? 0 : bits[i-8]);
            }
        return new IntN(bits);
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt8.MaxValue;
        return bitLength < 8
                ? new UInt8(new Array<Bit>(8, i -> i < 8-bitLength ? 0 : bits[i]))
                : new UInt8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt16.MaxValue;
        return bitLength < 16
                ? new UInt16(new Array<Bit>(16, i -> i < 16-bitLength ? 0 : bits[i]))
                : new UInt16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt32.MaxValue;
        return bitLength < 32
                ? new UInt32(new Array<Bit>(32, i -> i < 32-bitLength ? 0 : bits[i]))
                : new UInt32(bits[bitLength-32 ..< bitLength]);
        }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt64.MaxValue;
        return bitLength < 64
                ? new UInt64(new Array<Bit>(64, i -> i < 64-bitLength ? 0 : bits[i]))
                : new UInt64(bits[bitLength-64 ..< bitLength]);
        }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt64.MaxValue;
        return bitLength < 128
                ? new UInt128(new Array<Bit>(128, i -> i < 128-bitLength ? 0 : bits[i]))
                : new UInt128(bits[bitLength-128 ..< bitLength]);
        }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        return this;
        }

    @Auto
    @Override
    BFloat16 toBFloat16();

    @Auto
    @Override
    Float16 toFloat16();

    @Auto
    @Override
    Float32 toFloat32();

    @Auto
    @Override
    Float64 toFloat64();

    @Auto
    @Override
    Float128 toFloat128();

    @Auto
    @Override
    FloatN toFloatN()
        {
        return toIntLiteral().toFloatN();
        }

    @Auto
    @Override
    Dec32 toDec32();

    @Auto
    @Override
    Dec64 toDec64();

    @Auto
    @Override
    Dec128 toDec128();

    @Auto
    @Override
    DecN toDecN()
        {
        return toIntLiteral().toDecN();
        }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        if (this <= UInt64.MaxValue)
            {
            return toUInt64().estimateStringLength();
            }

        static UIntN chunkVal = 10_000_000_000_000_000_000;
        static Int   chunkLen = 19;

        UIntN left     = this;
        Int   rightLen = 0;
        do
            {
            (left, _) = left /% chunkVal;
            rightLen += chunkLen;
            }
        while (left > UInt64.MaxValue);
        return left.toUInt64().estimateStringLength() + rightLen;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        if (this <= UInt64.MaxValue)
            {
            return toUInt64().appendTo(buf);
            }

        static UIntN chunkVal = 10_000_000_000_000_000_000;
        (UIntN dividend, UIntN remainder) = this /% chunkVal;
        dividend.appendTo(buf);
        return remainder.toUInt64().appendTo(buf);
        }
    }