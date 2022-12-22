/**
 * An interface for random number generators.
 */
interface Random
    {
    /**
     * @return a random boolean value
     */
    Boolean boolean()
        {
        return bit().toBoolean();
        }

    /**
     * @return a random bit value
     */
    Bit bit();

    /**
     * Fill the passed array with random bit values.
     *
     * @param bits  an array of bits to fill; the array mutability must be either `Mutable` or
     *              `Fixed`
     *
     * @return the passed array
     */
    Bit[] fill(Bit[] bits)
        {
        for (Int i = 0, Int c = bits.size; i < c; ++i)
            {
            bits[i] = bit();
            }
        return bits;
        }

    /**
     * A second name for the [toUInt8] method, to assist with readability. By using a property
     * to alias the method, instead of creating a second delegating method, this prevents the
     * potential for accidentally overriding the wrong method.
     */
    static Method<Random, <>, <Byte>> byte = uint8;

    /**
     * Fill the passed array with random byte values.
     *
     * @param bytes  an array of bytes to fill; the array mutability must be either `Mutable` or
     *               `Fixed`
     *
     * @return the passed array
     */
    Byte[] fill(Byte[] bytes)
        {
        for (Int i = 0, Int c = bytes.size; i < c; ++i)
            {
            // TODO GG bytes[i] = byte();
            bytes[i] = uint8();
            }
        return bytes;
        }

    /**
     * Select a random number that is between `0` (inclusive) and the specified maximum value
     * (exclusive).
     *
     * @param max  the maximum value (exclusive) for the resulting random value
     *
     * @return a random signed integer value in the range `0..<max`
     */
    Xnt int(Xnt max)
        {
        assert max > 0;
        UInt128 umax     = max.toUInt128();
        Int     nonzeros = 128 - umax.leadingZeroCount;
        Bit[]   rndbits  = fill(new Bit[nonzeros]);
        if (umax & umax-1 == 0)
            {
            // max is a power of 2, so we're done
            return rndbits.toInt();
            }

        // depending on the maximum value's proximity to the next lower power of two, up to (almost)
        // half of the random values generated will be out of range; if we don't regenerate the
        // random bits from scratch, we'll skew the distribution of results from this method
        while (True)
            {
            Xnt n = rndbits.toInt();
            if (n < max)
                {
                return n;
                }
            fill(rndbits);
            }
        }

    /**
     * Select a random signed integer number that is in the specified range.
     *
     * @param range  the range of permitted values
     *
     * @return a random 64-bit signed integer value within in the specified range
     */
    Xnt int(Range<Xnt> range)
        {
        return range.effectiveLowerBound + int(range.size);
        }

// TODO temporary; remove
    Int64 int(Int64 max)
        {
        return int(max.toInt()).toInt64();
        }
    Int64 int(Range<Int64> range)
        {
        return int(new Range(range.first.toInt(), range.last.toInt(), range.firstExclusive, range.lastExclusive)).toInt64();
        }
// TODO end remove

    /**
     * Select a random number that is between `0` (inclusive) and the specified maximum value
     * (exclusive).
     *
     * @param max  the maximum value (exclusive) for the resulting random value
     *
     * @return a random signed integer value in the range `0..<max`
     */
    UInt uint(UInt max)
        {
        return int(max.toInt()).toUInt();
        }

    /**
     * Select a random unsigned integer number that is in the specified range.
     *
     * @param range  the range of permitted values
     *
     * @return a random 64-bit unsigned integer value within in the specified range
     */
    UInt uint(Range<UInt> range)
        {
        return range.effectiveLowerBound + uint(range.size.toUInt());
        }

    /**
     * @return a random 8-bit signed integer value
     */
    Int8 int8()
        {
        return fill(Int8:0.toByteArray(Fixed)).toInt8();
        }

    /**
     * @return a random 16-bit signed integer value
     */
    Int16 int16()
        {
        return fill(Int16:0.toByteArray(Fixed)).toInt16();
        }

    /**
     * @return a random 32-bit signed integer value
     */
    Int32 int32()
        {
        return fill(Int32:0.toByteArray(Fixed)).toInt32();
        }

    /**
     * @return a random 64-bit signed integer value
     */
    Int64 int64()
        {
        return fill(Int64:0.toByteArray(Fixed)).toInt64();
        }

    /**
     * @return a random 128-bit signed integer value
     */
    Int128 int128()
        {
        return fill(Int128:0.toByteArray(Fixed)).toInt128();
        }

    /**
     * @return a random 8-bit unsigned integer value
     */
    UInt8 uint8()
        {
        return fill(UInt8:0.toByteArray(Fixed)).toUInt8();
        }

    /**
     * @return a random 16-bit unsigned integer value
     */
    UInt16 uint16()
        {
        return fill(UInt16:0.toByteArray(Fixed)).toUInt16();
        }

    /**
     * @return a random 32-bit unsigned integer value
     */
    UInt32 uint32()
        {
        return fill(UInt32:0.toByteArray(Fixed)).toUInt32();
        }

    /**
     * @return a random 64-bit unsigned integer value
     */
    UInt64 uint64()
        {
        return fill(UInt64:0.toByteArray(Fixed)).toUInt64();
        }

    /**
     * @return a random 128-bit unsigned integer value
     */
    UInt128 uint128()
        {
        return fill(UInt128:0.toByteArray(Fixed)).toUInt128();
        }

    /**
     * @return a random decimal value in the range `[0..1)`.
     */
    Dec dec()
        {
        // 15 digits of precision
        static IntLiteral bound  = 1_000_000_000_000_000;
        static Dec        scalar = 1.0 / bound;
        return uint(bound).toDec() * scalar;    // TODO could we use scaleByPow(-15) instead?
        }

    /**
     * @return a random 32-bit decimal value in the range `[0..1)`.
     */
    Dec32 dec32()
        {
        TODO default implementation of dec32()
        }

    /**
     * @return a random 64-bit decimal value in the range `[0..1)`.
     */
    Dec64 dec64()
        {
        TODO default implementation of dec64()
        }

    /**
     * @return a random 128-bit decimal value in the range `[0..1)`.
     */
    Dec128 dec128()
        {
        TODO default implementation of dec128()
        }

    /**
     * @return a random 8-bit "FP8 E4M3" floating point value in the range `0..<1`.
     */
    Float8e4 float8e4()
        {
        TODO default Random.float8e4() implementation
        }

    /**
     * @return a random 8-bit "FP8 E5M2" floating point value in the range `0..<1`.
     */
    Float8e5 float8e5()
        {
        TODO default Random.float8e5() implementation
        }

    /**
     * @return a random 16-bit "brain" floating point value in the range `0..<1`.
     */
    BFloat16 bfloat16()
        {
        TODO default Random.bfloat16() implementation
        }

    /**
     * @return a random 16-bit binary floating point value in the range `0..<1`.
     */
    Float16 float16()
        {
        static Int     precision = 1 << 11;
        static Float16 scalar    = 1.0 / precision.toFloat16();

        // initialize only the least significant 53 bits of information; leave [0..10] blank
        Int n = int(precision);
        return n.toFloat16() * scalar;
        }

    /**
     * @return a random 32-bit binary floating point value in the range `0..<1`.
     */
    Float32 float32()
        {
        static Int     precision = 1 << 24;
        static Float32 scalar    = 1.0 / precision.toFloat32();

        // initialize only the least significant 53 bits of information; leave [0..10] blank
        Int n = int(precision);
        return n.toFloat32() * scalar;
        }

    /**
     * @return a random 64-bit binary floating point value in the range `0..<1`.
     */
    Float64 float64()
        {
        static Int     precision = 1 << 53;
        static Float64 scalar    = 1.0 / precision.toFloat64();

        // initialize only the least significant 53 bits of information; leave [0..10] blank
        Int n = int(precision);
        return n.toFloat64() * scalar; // REVIEW could we use scaleByPow(-53) instead?
        }

    /**
     * @return a random 128-bit binary floating point value in the range `0..<1`.
     */
// TODO
//    Float128 float128()
//        {
//        static Int      precision = 1 << 113;
//        static Float128 scalar    = 1.0 / precision.toFloat128();
//
//        // initialize only the least significant 53 bits of information; leave [0..10] blank
//        Int n = int(precision);
//        return n.toFloat128() * scalar;
//        }
    }