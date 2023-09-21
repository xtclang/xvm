/**
 * A UIntNumber is a Number that represents an unsigned integer value.
 */
@Abstract const UIntNumber
        extends IntNumber {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        super(bits);
    }

    /**
     * Construct an unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        super(bytes);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Boolean signed.get() {
        return False;
    }

    @Override
    UIntNumber magnitude.get() {
        return this;
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    UIntNumber abs() {
        return this;
    }

    @Override
    @Op("-#")
    UIntNumber neg() {
        throw new UnsupportedOperation();
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Auto
    @Override
    IntN toIntN() {
        // going from unsigned to signed, we need to make sure that the first bit is not `1`,
        // because that will be treated as a sign bit, and cause the result to be negative
        Bit[] bits = this.bits;
        return new IntN(bits[0] == 0 ? bits : Byte:0.bits + bits);
    }

    @Auto
    @Override
    UIntN toUIntN() = new UIntN(bits);


    // ----- Orderable implementation --------------------------------------------------------------

    @Override
    static <CompileType extends UIntNumber> Ordered compare(CompileType value1, CompileType value2) {
        return value1.bits <=> value1.bits;
    }

    @Override
    static <CompileType extends Number> Boolean equals(CompileType value1, CompileType value2) {
        return (value1 <=> value2) == Equal;
    }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        TODO($"Implementations of UIntNumber must override appendTo(); class={this:class}");
    }
}