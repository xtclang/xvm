const Dec128
        extends DecimalFPNumber
        default(0.0) {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit decimal floating point number from its bitwise machine representation.
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
     * Construct a 128-bit decimal floating point number from its network-portable representation.
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
     * Construct a 128-bit decimal floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text) {
        construct Dec128(new FPLiteral(text).toDec128().bits);
    }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 128;
    }

    @Override
    static Dec128 zero() {
        return 0.0;
    }

    @Override
    static Dec128 one() {
        return 1.0;
    }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Dec128 add(Dec128 n) {
        TODO
    }

    @Override
    @Op Dec128 sub(Dec128 n) {
        TODO
    }

    @Override
    @Op Dec128 mul(Dec128 n) {
        TODO
    }

    @Override
    @Op Dec128 div(Dec128 n) {
        TODO
    }

    @Override
    @Op Dec128 mod(Dec128 n) {
        TODO
    }

    @Override
    Dec128 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    @Op Dec128 neg() {
        TODO
    }

    @Override
    Dec128 pow(Dec128 n) {
        TODO
    }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO Int emax.get() {
        return 6144;
    }

    @Override
    Int emin.get() {
        return 1 - emax;
    }

    @Override
    Int bias.get() {
        return 6176;
    }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int128 significand, Int exponent) split() {
        TODO
    }

    @Override
    Dec128 round(Rounding direction = TiesToAway) {
        TODO
    }

    @Override
    Dec128 floor() {
        TODO
    }

    @Override
    Dec128 ceil() {
        TODO
    }

    @Override
    Dec128 exp() {
        TODO
    }

    @Override
    Dec128 scaleByPow(Int n) {
        TODO
    }

    @Override
    Dec128 log() {
        TODO
    }

    @Override
    Dec128 log2() {
        TODO
    }

    @Override
    Dec128 log10() {
        TODO
    }

    @Override
    Dec128 sqrt() {
        TODO
    }

    @Override
    Dec128 cbrt() {
        TODO
    }

    @Override
    Dec128 sin() {
        TODO
    }

    @Override
    Dec128 cos() {
        TODO
    }

    @Override
    Dec128 tan() {
        TODO
    }

    @Override
    Dec128 asin() {
        TODO
    }

    @Override
    Dec128 acos() {
        TODO
    }

    @Override
    Dec128 atan() {
        TODO
    }

    @Override
    Dec128 atan2(Dec128 y) {
        TODO
    }

    @Override
    Dec128 sinh() {
        TODO
    }

    @Override
    Dec128 cosh() {
        TODO
    }

    @Override
    Dec128 tanh() {
        TODO
    }

    @Override
    Dec128 asinh() {
        TODO
    }

    @Override
    Dec128 acosh() {
        TODO
    }

    @Override
    Dec128 atanh() {
        TODO
    }

    @Override
    Dec128 deg2rad() {
        TODO
    }

    @Override
    Dec128 rad2deg() {
        TODO
    }

    @Override
    Dec128 nextUp() {
        TODO
    }

    @Override
    Dec128 nextDown() {
        TODO
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Dec128 toDec128() = this;
}