const Float128
        extends BinaryFPNumber {
        // TODO default(0.0)
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit binary floating point number (a "quad float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert:bounds bits.size == 128;
        super(bits);
    }

    /**
     * Construct a 128-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert:bounds bytes.size == 16;
        super(bytes);
    }

    /**
     * Construct a 128-bit binary floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text) {
        construct Float128(new FPLiteral(text).toFloat128().bits);
    }


    // ----- Numberic interface --------------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 128;
    }

    @Override
    static Float128 zero() {
        TODO return 0.0;
    }

    @Override
    static Float128 one() {
        TODO return 1.0;
    }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get() {
        TODO need to think this through carefully because there is a sign bit and both +/-0
    }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Float128 add(Float128 n) {
        TODO
    }

    @Override
    @Op Float128 sub(Float128 n) {
        TODO
    }

    @Override
    @Op Float128 mul(Float128 n) {
        TODO
    }

    @Override
    @Op Float128 div(Float128 n) {
        TODO
    }

    @Override
    @Op Float128 mod(Float128 n) {
        TODO
    }

    @Override
    Float128 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    @Op Float128 neg() {
        TODO
    }

    @Override
    Float128 pow(Float128 n) {
        TODO
    }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get() {
        return 16383;
    }

    @Override
    Int emin.get() {
        return 1 - emax;
    }

    @Override
    Int bias.get() {
        return emax;
    }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int128 significand, Int exponent) split() {
        TODO
    }

    @Override
    Float128 round(Rounding direction = TiesToAway) {
        TODO
    }

    @Override
    Float128 floor() {
        TODO
    }

    @Override
    Float128 ceil() {
        TODO
    }

    @Override
    Float128 exp() {
        TODO
    }

    @Override
    Float128 scaleByPow(Int n) {
        TODO
    }

    @Override
    Float128 log() {
        TODO
    }

    @Override
    Float128 log2() {
        TODO
    }

    @Override
    Float128 log10() {
        TODO
    }

    @Override
    Float128 sqrt() {
        TODO
    }

    @Override
    Float128 cbrt() {
        TODO
    }

    @Override
    Float128 sin() {
        TODO
    }

    @Override
    Float128 cos() {
        TODO
    }

    @Override
    Float128 tan() {
        TODO
    }

    @Override
    Float128 asin() {
        TODO
    }

    @Override
    Float128 acos() {
        TODO
    }

    @Override
    Float128 atan() {
        TODO
    }

    @Override
    Float128 atan2(Float128 y) {
        TODO
    }

    @Override
    Float128 sinh() {
        TODO
    }

    @Override
    Float128 cosh() {
        TODO
    }

    @Override
    Float128 tanh() {
        TODO
    }

    @Override
    Float128 asinh() {
        TODO
    }

    @Override
    Float128 acosh() {
        TODO
    }

    @Override
    Float128 atanh() {
        TODO
    }

    @Override
    Float128 deg2rad() {
        TODO
    }

    @Override
    Float128 rad2deg() {
        TODO
    }

    @Override
    Float128 nextUp() {
        TODO
    }

    @Override
    Float128 nextDown() {
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
    BFloat16 toBFloat16();

    @Override
    Float16 toFloat16();

    @Override
    Float32 toFloat32();

    @Override
    Float64 toFloat64();

    @Override
    Float128 toFloat128() {
        return this;
    }

    @Auto
    @Override
    FloatN toFloatN() {
        return new FloatN(bits);
    }

    @Override
    Dec32 toDec32();

    @Override
    Dec64 toDec64();

    @Override
    Dec128 toDec128();

    @Auto
    @Override
    DecN toDecN() {
        return toFPLiteral().toDecN();
    }
}