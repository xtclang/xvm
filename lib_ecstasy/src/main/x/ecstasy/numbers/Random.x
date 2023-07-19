/**
 * An interface for random number generators.
 */
interface Random {
    /**
     * @return a random boolean value
     */
    Boolean boolean() {
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
    Bit[] bits(Int size) = new Bit[size](_ -> bit()).freeze(inPlace=True);

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
    immutable Byte[] bytes(Int size) = new Byte[size](_ -> this.byte()).freeze(inPlace=True); // TODO GG why is "this." required?

    /**
     * Select a random number that is between `0` (inclusive) and the specified maximum value
     * (exclusive).
     *
     * @param max  the maximum value (exclusive) for the resulting random value
     *
     * @return a random signed integer value in the range `0..<max`
     */
    Int int(Int max) {
        assert max > 0;
        if (max == 1) {
            return 0;
        }

        Int   bitSize = (max-1).leftmostBit.trailingZeroCount + 1;
        Bit[] rndbits = bits(bitSize);
        if (max & max-1 == 0) {
            // max is a power of 2, so we're done
            return rndbits.toUInt().toInt();
        }

        // depending on the maximum value's proximity to the next lower power of two, up to (almost)
        // half of the random values generated will be out of range; if we don't regenerate the
        // random bits from scratch, we'll skew the distribution of results from this method
        while (True) {
            Int n = rndbits.toUInt().toInt();
            if (n < max) {
                return n;
            }
            rndbits = bits(bitSize);
        }
    }

    /**
     * Select a random signed integer number that is in the specified range.
     *
     * @param range  the range of permitted values
     *
     * @return a random 64-bit signed integer value within in the specified range
     */
    Int int(Range<Int> range) = range.effectiveLowerBound + int(range.size);

    /**
     * Select a random number that is between `0` (inclusive) and the specified maximum value
     * (exclusive).
     *
     * @param max  the maximum value (exclusive) for the resulting random value
     *
     * @return a random signed integer value in the range `0..<max`
     */
    UInt uint(UInt max) = int(max.toInt()).toUInt();

    /**
     * Select a random unsigned integer number that is in the specified range.
     *
     * @param range  the range of permitted values
     *
     * @return a random 64-bit unsigned integer value within in the specified range
     */
    UInt uint(Range<UInt> range) = range.effectiveLowerBound + uint(range.size.toUInt());

    /**
     * @return a random 8-bit signed integer value
     */
    Int8 int8() = bits(8).toInt8();

    /**
     * @return a random 16-bit signed integer value
     */
    Int16 int16() = bytes(2).toInt16();

    /**
     * @return a random 32-bit signed integer value
     */
    Int32 int32() = bytes(4).toInt32();

    /**
     * @return a random 64-bit signed integer value
     */
    Int64 int64() = bytes(8).toInt64();

    /**
     * @return a random 128-bit signed integer value
     */
    Int128 int128() = bytes(16).toInt128();

    /**
     * @return a random 8-bit unsigned integer value
     */
    UInt8 uint8() = bits(8).toUInt8();

    /**
     * @return a random 16-bit unsigned integer value
     */
    UInt16 uint16() = bytes(2).toUInt16();

    /**
     * @return a random 32-bit unsigned integer value
     */
    UInt32 uint32() = bytes(4).toUInt32();

    /**
     * @return a random 64-bit unsigned integer value
     */
    UInt64 uint64() = bytes(8).toUInt64();

    /**
     * @return a random 128-bit unsigned integer value
     */
    UInt128 uint128() = bytes(16).toUInt128();

    /**
     * @return a random decimal value in the range `[0..1)`.
     */
    Dec dec() {
        // 15 digits of precision
        static IntLiteral bound  = 1_000_000_000_000_000;
        static Dec        scalar = 1.0 / bound;
        return uint(bound).toDec() * scalar;    // TODO could we use scaleByPow(-15) instead?
    }

    /**
     * @return a random 32-bit decimal value in the range `[0..1)`.
     */
    Dec32 dec32() = TODO default implementation of dec32()

    /**
     * @return a random 64-bit decimal value in the range `[0..1)`.
     */
    Dec64 dec64() = TODO default implementation of dec64()

    /**
     * @return a random 128-bit decimal value in the range `[0..1)`.
     */
    Dec128 dec128() = TODO default implementation of dec128()

    /**
     * @return a random 8-bit "FP8 E4M3" floating point value in the range `0..<1`.
     */
    Float8e4 float8e4() = TODO default Random.float8e4() implementation

    /**
     * @return a random 8-bit "FP8 E5M2" floating point value in the range `0..<1`.
     */
    Float8e5 float8e5() = TODO default Random.float8e5() implementation

    /**
     * @return a random 16-bit "brain" floating point value in the range `0..<1`.
     */
    BFloat16 bfloat16() = TODO default Random.bfloat16() implementation

    /**
     * @return a random 16-bit binary floating point value in the range `0..<1`.
     */
    Float16 float16() {
        static Int     precision = 1 << 11;
        static Float16 scalar    = 1.0 / precision.toFloat16();

        // initialize only the least significant 53 bits of information; leave [0..10] blank
        Int n = int(precision);
        return n.toFloat16() * scalar;
    }

    /**
     * @return a random 32-bit binary floating point value in the range `0..<1`.
     */
    Float32 float32() {
        static Int     precision = 1 << 24;
        static Float32 scalar    = 1.0 / precision.toFloat32();

        // initialize only the least significant 53 bits of information; leave [0..10] blank
        Int n = int(precision);
        return n.toFloat32() * scalar;
    }

    /**
     * @return a random 64-bit binary floating point value in the range `0..<1`.
     */
    Float64 float64() {
        static Int     precision = 1 << 53;
        static Float64 scalar    = 1.0 / precision.toFloat64();

        // initialize only the least significant 53 bits of information; leave [0..10] blank
        Int n = int(precision);
        return n.toFloat64() * scalar; // REVIEW could we use scaleByPow(-53) instead?
    }

    /**
     * @return a random 128-bit binary floating point value in the range `0..<1`.
     */
    Float128 float128() {
        TODO
        // static Int      precision = 1 << 113;
        // static Float128 scalar    = 1.0 / precision.toFloat128();
        //
        // // initialize only the least significant 53 bits of information; leave [0..10] blank
        // Int n = int(precision);
        // return n.toFloat128() * scalar;
    }
}