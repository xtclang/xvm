/**
 * BFloat16 is a 16-bit floating point number, commonly known as a "brain floating point", and
 * based directly on the "binary32" type in the IEEE 754 floating point standard -- but with the
 * trailing 16 bits truncated. It is composed of a sign bit, 8 exponent bits, and 7 mantissa bits.
 * With its mantissa chopped from 23 bits (in Float32) to 7 bits (in Bfloat16), the values are
 * fairly poor for general purpose math, but lose almost no precision when applied to tasks like
 * machine learning -- hence the name "brain float". In other words, with large machine learning
 * models, BFloat16 would achieve almost as good of results as Float32, but would use half the
 * memory and less CPU time to do so. However, the main reason for reducing the size of the floating
 * point values was to be able to double the number of values being used in a machine learning model
 * in the same amount of memory, which produces significantly better results despite the loss of
 * precision in each data point.
 */
const BFloat16
        extends BinaryFPNumber
        default(0.0) {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "brain float 16") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert:bounds bits.size == 16;
        super(bits);
    }

    /**
     * Construct a 16-bit "brain" floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert:bounds bytes.size == 2;
        super(bytes);
    }

    /**
     * Construct a 16-bit "brain" floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text) {
        construct BFloat16(new FPLiteral(text).toBFloat16().bits);
    }

    /**
     * Construct the floating point number from its constituent pieces: A sign bit, a significand,
     * and an exponent.
     *
     * @param negative     true if explicitly negative
     * @param significand  the significand value, in the range `0` to TODO
     * @param exponent     the exponent value, in the range [EMIN] to [EMAX]
     */
    construct(Boolean negative, Int significand, Int exponent) {
        if (significand == 0) {
            construct BFloat16(negative ? BFloat16:-0.bits : BFloat16:0.bits);
            return;
        }

        // note that this allows one more significant bit than can be stored, because IEEE754 uses
        // an implicit leading '1' bit (for non-zero, and non-sub-normal values) that is not encoded
        // into the resulting IEEE754 value
        // TODO CP: is "64" right?
        Int sigCount = 64 - significand.toInt16().leadingZeroCount;
        assert:bounds significand > 0 && sigCount - 1 <= SIG_BITS;
        assert:bounds EMIN <= exponent <= EMAX;

        Bit[] signBits = negative ? [1] : [0];
        Bit[] expBits  = (exponent + BIAS).toBitArray()[64-EXP_BITS ..< 64];
        Bit[] sigBits  = (significand.toInt16() << (SIG_BITS - (sigCount-1))).toBitArray()[64-SIG_BITS ..< 64];
        construct BFloat16(signBits + expBits + sigBits);
    }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 16;
    }

    @Override
    static BFloat16 zero() {
        return 0.0;
    }

    @Override
    static BFloat16 one() {
        return 1.0;
    }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get() {
        TODO need to think this through carefully because there is a sign bit and both +/-0
    }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op BFloat16 add(BFloat16 n) {
        TODO
    }

    @Override
    @Op BFloat16 sub(BFloat16 n) {
        TODO
    }

    @Override
    @Op BFloat16 mul(BFloat16 n) {
        TODO
    }

    @Override
    @Op BFloat16 div(BFloat16 n) {
        TODO
    }

    @Override
    @Op BFloat16 mod(BFloat16 n) {
        TODO
    }

    @Override
    BFloat16 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    @Op BFloat16 neg() {
        TODO
    }

    @Override
    BFloat16 pow(BFloat16 n) {
        TODO
    }


    // ----- FPNumber properties -------------------------------------------------------------------

    /**
     * Number of significand bits in a BFloat16 value (7).
     */
    static Int SIG_BITS = 7;

    /**
     * Number of exponent bits in a BFloat16 value (8).
     */
    static Int EXP_BITS = 8;

    /**
     * Maximum exponent value in a BFloat16 value (127).
     */
    static Int EMAX     = (1 << (EXP_BITS-1)) - 1;

    /**
     * Minimum exponent value in a BFloat16 value (-126).
     */
    static Int EMIN     = 1 - EMAX;

    /**
     * Exponent bias for a BFloat16 value (126).
     */
    static Int BIAS     = EMAX;

    @Override
    Int emax.get() {
        return EMAX;
    }

    @Override
    Int emin.get() {
        return EMIN;
    }

    @Override
    Int bias.get() {
        return BIAS;
    }

    @Override
    Int significandBitLength.get() {
        return SIG_BITS;
    }

    @Override
    Int exponentBitLength.get() {
        return EXP_BITS;
    }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int significand, Int exponent) split() {
        TODO
    }

    @Override
    BFloat16 round(Rounding direction = TiesToAway) {
        TODO
    }

    @Override
    BFloat16 floor() {
        TODO
    }

    @Override
    BFloat16 ceil() {
        TODO
    }

    @Override
    BFloat16 exp() {
        TODO
    }

    @Override
    BFloat16 scaleByPow(Int n) {
        TODO
    }

    @Override
    BFloat16 log() {
        TODO
    }

    @Override
    BFloat16 log2() {
        TODO
    }

    @Override
    BFloat16 log10() {
        TODO
    }

    @Override
    BFloat16 sqrt() {
        TODO
    }

    @Override
    BFloat16 cbrt() {
        TODO
    }

    @Override
    BFloat16 sin() {
        TODO
    }

    @Override
    BFloat16 cos() {
        TODO
    }

    @Override
    BFloat16 tan() {
        TODO
    }

    @Override
    BFloat16 asin() {
        TODO
    }

    @Override
    BFloat16 acos() {
        TODO
    }

    @Override
    BFloat16 atan() {
        TODO
    }

    @Override
    BFloat16 atan2(BFloat16 y) {
        TODO
    }

    @Override
    BFloat16 sinh() {
        TODO
    }

    @Override
    BFloat16 cosh() {
        TODO
    }

    @Override
    BFloat16 tanh() {
        TODO
    }

    @Override
    BFloat16 asinh() {
        TODO
    }

    @Override
    BFloat16 acosh() {
        TODO
    }

    @Override
    BFloat16 atanh() {
        TODO
    }

    @Override
    BFloat16 deg2rad() {
        TODO
    }

    @Override
    BFloat16 rad2deg() {
        TODO
    }

    @Override
    BFloat16 nextUp() {
        TODO
    }

    @Override
    BFloat16 nextDown() {
        TODO
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        return round(direction).toIntN();
    }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UIntN toUIntN(Rounding direction = TowardZero) {
        return round(direction).toUIntN();
    }

    @Override
    Float8e4 toFloat8e4();

    @Override
    Float8e5 toFloat8e5();

    @Override
    BFloat16 toBFloat16() {
        return this;
    }

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
    FloatN toFloatN() {
        return new FloatN(bits);
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
    DecN toDecN() {
        return toFPLiteral().toDecN();
    }
}