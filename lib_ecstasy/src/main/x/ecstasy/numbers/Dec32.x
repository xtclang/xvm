const Dec32
        extends DecimalFPNumber
        default(0.0) {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert:bounds bits.size == 32;
        super(bits);
    }

    /**
     * Construct a 32-bit decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert:bounds bytes.size == 4;
        super(bytes);
    }

    /**
     * Construct a 32-bit decimal floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text) {
        construct Dec32(new FPLiteral(text).toDec32().bits);
    }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 32;
    }

    @Override
    static Dec32 zero() {
        return 0.0;
    }

    @Override
    static Dec32 one() {
        return 1.0;
    }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Dec32 add(Dec32 n) {
        TODO
    }

    @Override
    @Op Dec32 sub(Dec32 n) {
        TODO
    }

    @Override
    @Op Dec32 mul(Dec32 n) {
        TODO
    }

    @Override
    @Op Dec32 div(Dec32 n) {
        TODO
    }

    @Override
    @Op Dec32 mod(Dec32 n) {
        TODO
    }

    @Override
    Dec32 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    @Op Dec32 neg() {
        TODO
    }

    @Override
    Dec32 pow(Dec32 n) {
        TODO
    }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO Int emax.get() {
        return 96;
    }

    @Override
    Int emin.get() {
        return 1 - emax;
    }

    @Override
    Int bias.get() {
        return 101;
    }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int significand, Int exponent) split() {
        TODO
    }

    @Override
    Dec32 round(Rounding direction = TiesToAway) {
        TODO
    }

    @Override
    Dec32 floor() {
        TODO
    }

    @Override
    Dec32 ceil() {
        TODO
    }

    @Override
    Dec32 exp() {
        TODO
    }

    @Override
    Dec32 scaleByPow(Int n) {
        TODO
    }

    @Override
    Dec32 log() {
        TODO
    }

    @Override
    Dec32 log2() {
        TODO
    }

    @Override
    Dec32 log10() {
        TODO
    }

    @Override
    Dec32 sqrt() {
        TODO
    }

    @Override
    Dec32 cbrt() {
        TODO
    }

    @Override
    Dec32 sin() {
        TODO
    }

    @Override
    Dec32 cos() {
        TODO
    }

    @Override
    Dec32 tan() {
        TODO
    }

    @Override
    Dec32 asin() {
        TODO
    }

    @Override
    Dec32 acos() {
        TODO
    }

    @Override
    Dec32 atan() {
        TODO
    }

    @Override
    Dec32 atan2(Dec32 y) {
        TODO
    }

    @Override
    Dec32 sinh() {
        TODO
    }

    @Override
    Dec32 cosh() {
        TODO
    }

    @Override
    Dec32 tanh() {
        TODO
    }

    @Override
    Dec32 asinh() {
        TODO
    }

    @Override
    Dec32 acosh() {
        TODO
    }

    @Override
    Dec32 atanh() {
        TODO
    }

    @Override
    Dec32 deg2rad() {
        TODO
    }

    @Override
    Dec32 rad2deg() {
        TODO
    }

    @Override
    Dec32 nextUp() {
        TODO
    }

    @Override
    Dec32 nextDown() {
        TODO
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Auto
    @Override
    Float64 toFloat64();

    @Auto
    @Override
    Float128 toFloat128();

    @Override
    Dec32 toDec32() = this;

    @Auto
    @Override
    Dec64 toDec64();

    @Auto
    @Override
    Dec128 toDec128();
}