/**
 * The Number interface represents the properties and operations available on every
 * numeric type included in Ecstasy.
 */
interface Number
    {
    enum Signum(String prefix)
        {
        Negative("-"),
        Zero(""),
        Positive("+")
        }

    // ----- properties

    /**
     * The number of bits that the number uses.
     */
    @ro Int bitLength;

    /**
     * The number of bytes that the number uses.
     */
    @ro Int byteLength.get()
        {
        // make sure the bit length is at least 8, and also a power-of-two
        assert:always bitLength == (bitLength & ~0x7).leftmostBit;

        return bitLength / 8;
        }

    /**
     * The Sign of the number.
     */
    @ro Signum sign;

    // ----- operations

    /**
     * Addition: Add another number to this number, and retu
     rn the result.
     */
    @op Number add(Number n);

    /**
     * Subtraction: Subtract another number from this number, and return the result.
     */
    @op Number sub(Number n);

    /**
     * Multiplication: Multiply this number by another number, and return the result.
     */
    @op Number mul(Number n);

    /**
     * Division: Divide this number by another number, and return the result.
     */
    @op Number div(Number n);

    /**
     * Modulo: Return the modulo that would result from dividing this number by another number.
     */
    @op Number mod(Number n);

    /**
     * Division and Modulo: Divide this number by another number, and return both the
     * quotient and the modulo.
     */
    @op (Number quotient, Number modulo) divmod(Number n)
        {
        return (this / n, this % n);
        }

    /**
     * Remainder: Return the remainder that would result from dividing this number by another
     * number. Note that the remainder is the same as the modulo for unsigned dividend values
     * and for signed dividend values that are zero or positive, but for signed dividend values
     * that are negative, the remainder will be zero or negative.
     */
    Number remainder(Number n)
        {
        return this - (this / n * n);
        }

    /**
     * Calculate the absolute value of this number. If there is no absolute value representable
     * using this number's type, then an exception is thrown; this can happen for a signed integer
     * of the minimum value for that integer type, since the positive range for a 2s-complement
     * signed integer is always one smaller than the negative range.
     */
    Number abs()
        {
        if (sign != Negative)
            {
            return this;
            }

        Number n = -this;
        assert:always n.sign != Negative;
        return n;
        };

    /**
     * Calculate the negative of this number.
     */
    @op Number neg();

    /**
     * Calculate this number raised to the specified power.
     */
    Number pow(Number n);

    /**
     * Determine the smaller of this number and the passed number.
     */
// TODO "at most"?
    Number min(Number n)
        {
        return n < this ? n : this;
        }

    /**
     * Determine the larger of this number and the passed number.
     */
// TODO "at least"?
    Number max(Number n)
        {
        return n > this ? n : this;
        }

    // ----- conversions

    /**
     * Obtain the number as an array of bits.
     */
    Bit[] to<Bit[]>();

    /**
     * Obtain the number as an array of nibbles.
     */
    Nibble[] to<Nibble[]>();
        {
        // make sure the bit length is at least 4, and also a power-of-two
        assert:always bitLength == (bitLength & ~0x3).leftmostBit;

        return new Sequence<Nibble>()
            {
            @override Int length.get()
                {
                return IntNumber.this.bitCount / 4;
                }

            @override Boolean get(Int index)
                {
                return new Nibble(IntNumber.this.to<Bit[]>().subSequence(index * 4, index * 4 + 4));
                }

            @override Void set(Int index, Nibble newValue)
                {
                Bit[] oldBits = Number.this.to<Bit[]>();
                Bit[] newBits = newValue.to<Bit[]>();
                for (Int ofOld = index * 4, ofNew = 0; ofNew < 4; )
                    {
                    oldBits[ofOld++] = newBits[ofNew++];
                    }
                }
            }
        }

    /**
     * Obtain the number as an array of bytes.
     */
    Byte[] to<Byte[]>();
        {
        // make sure the bit length is at least 8, and also a power-of-two
        assert:always bitLength == (bitLength & ~0x7).leftmostBit;

        return new Sequence<Byte>()
            {
            @override Int length.get()
                {
                return IntNumber.this.bitCount / 8;
                }

            @override Boolean get(Int index)
                {
                return new Byte(IntNumber.this.to<Bit[]>().subSequence(index * 8, index * 8 + 8));
                }

            @override Void set(Int index, Nibble newValue)
                {
                Bit[] oldBits = Number.this.to<Bit[]>();
                Bit[] newBits = newValue.to<Bit[]>();
                for (Int ofOld = index * 8, ofNew = 0; ofNew < 8; )
                    {
                    oldBits[ofOld++] = newBits[ofNew++];
                    }
                }
            }
        }

    /**
     * Convert the number to a variable-length signed integer.
     */
    VarInt to<VarInt>();

    /**
     * Convert the number to a signed 8-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int8 to<Int8>()
        {
        return to<VarInt>().to<Int8>();
        }

    /**
     * Convert the number to a signed 16-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int16 to<Int16>();
        {
        return to<VarInt>().to<Int16>();
        }

    /**
     * Convert the number to a signed 32-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int32 to<Int32>();
        {
        return to<VarInt>().to<Int32>();
        }

    /**
     * Convert the number to a signed 64-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int64 to<Int64>();
        {
        return to<VarInt>().to<Int64>();
        }

    /**
     * Convert the number to a signed 128-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    Int128 to<Int128>();
        {
        return to<VarInt>().to<Int128>();
        }

    /**
     * Convert the number to a variable-length unsigned integer.
     */
    VarUInt to<VarUInt>();

    /**
     * Convert the number to a unsigned 8-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt8 to<UInt8>()
        {
        return to<VarUInt>().to<UInt8>();
        }

    /**
     * Convert the number to a unsigned 16-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt16 to<UInt16>();
        {
        return to<VarUInt>().to<UInt16>();
        }

    /**
     * Convert the number to a unsigned 32-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt32 to<UInt32>();
        {
        return to<VarUInt>().to<UInt32>();
        }

    /**
     * Convert the number to a unsigned 64-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt64 to<UInt64>();
        {
        return to<VarUInt>().to<UInt64>();
        }

    /**
     * Convert the number to a unsigned 128-bit integer.
     * Any additional magnitude is discarded; any fractional value is discarded.
     */
    UInt128 to<UInt128>();
        {
        return to<VarInt>().to<UInt128>();
        }

    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    VarFloat to<VarFloat>();

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    Float16 to<Float16>()
        {
        return to<VarFloat>().to<Float16>();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    Float32 to<Float32>()
        {
        return to<VarFloat>().to<Float32>();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    Float64 to<Float64>()
        {
        return to<VarFloat>().to<Float64>();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    Float128 to<Float128>()
        {
        return to<VarFloat>().to<Float128>();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    VarDec to<VarDec>();

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    Dec32 to<Dec32>()
        {
        return to<VarDec>().to<Dec32>();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    Dec64 to<Dec64>()
        {
        return to<VarDec>().to<Dec64>();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    Dec128 to<Dec128>()
        {
        return to<VarDec>().to<Dec128>();
        }
    }
