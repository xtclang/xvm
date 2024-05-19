/**
 * A bit represents the smallest possible unit of storage. It has two states: `0` and `1`.
 */
const Bit(IntLiteral literal)
        implements Sequential
        implements IntConvertible
        default(0) {
    assert() {
        assert literal == 0 || literal == 1;
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for a Bit.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for a Bit.
     */
    static IntLiteral MaxValue = 1;


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The entire state of a bit is an IntLiteral whose value is either 0 or 1.
     */
    private IntLiteral literal;


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the [IntLiteral] that corresponds to the bit value
     */
    @Override
    IntLiteral toIntLiteral() {
        return literal;
    }

    /**
     * @return the [Boolean] that corresponds to the bit value, in which `0` is `False`, and `1` is
     *         `True`
     */
    Boolean toBoolean() {
        return literal == 1;
    }

    /**
     * @return the [Int8] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    Int8 toInt8() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [Int16] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    Int16 toInt16() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [Int32] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    Int32 toInt32() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [Int64] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    Int64 toInt64() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [Int128] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    Int128 toInt128() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [IntN] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    IntN toIntN() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [UInt8] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    UInt8 toUInt8() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [UInt16] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    UInt16 toUInt16() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [UInt32] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    UInt32 toUInt32() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [UInt64] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    UInt64 toUInt64() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [UInt128] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    UInt128 toUInt128() {
        return toBoolean() ? 1 : 0;
    }

    /**
     * @return the [UIntN] value of either 0 or 1 that corresponds to this bit's value
     */
    @Auto
    @Override
    UIntN toUIntN() {
        return toBoolean() ? 1 : 0;
    }


    // ----- operators -----------------------------------------------------------------------------

    @Op("&")
    Bit and(Bit! that) {
        return this.literal == 1 && that.literal == 1 ? 1 : 0;
    }

    @Op("|")
    Bit or(Bit! that) {
        return this.literal == 1 || that.literal == 1 ? 1 : 0;
    }

    @Op("^")
    Bit xor(Bit! that) {
        return this.literal == 1 ^^ that.literal == 1 ? 1 : 0;
    }

    @Op("~")
    Bit not() {
        return literal == 1 ? 0 : 1;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Bit next() {
        if (this == 0) {
            return True, 1;
        }

        return False;
    }

    @Override
    conditional Bit prev() {
        if (this == 1) {
            return True, 0;
        }

        return False;
    }

    @Override
    Int stepsTo(Bit! that) {
        return that - this;
    }

    @Override
    Bit skip(Int steps) {
        return switch (this, steps) {
            case (_,  0): this;
            case (0,  1): 1;
            case (1, -1): 0;
            default: throw new OutOfBounds($"Bit={this}, steps={steps}");
        };
    }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return 1;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return buf.add(toBoolean() ? '1' : '0');
    }
}