/**
 * A Nibble is half of a byte (bite); basically, a nibble is the number of bits necessary to hold a
 * hexadecimal value (a _hexit_, akin to a digit).
 */
const Nibble(Bit[] bits)
        extends UIntNumber
        default(0) {

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for a Nibble.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for a Nibble.
     */
    static IntLiteral MaxValue = 0xF;

    /**
     * The entire set of nibbles, in magnitude order.
     */
    private static Nibble[] values =
        [
        new Nibble(Array<Bit>:[0, 0, 0, 0]),
        new Nibble(Array<Bit>:[0, 0, 0, 1]),
        new Nibble(Array<Bit>:[0, 0, 1, 0]),
        new Nibble(Array<Bit>:[0, 0, 1, 1]),
        new Nibble(Array<Bit>:[0, 1, 0, 0]),
        new Nibble(Array<Bit>:[0, 1, 0, 1]),
        new Nibble(Array<Bit>:[0, 1, 1, 0]),
        new Nibble(Array<Bit>:[0, 1, 1, 1]),
        new Nibble(Array<Bit>:[1, 0, 0, 0]),
        new Nibble(Array<Bit>:[1, 0, 0, 1]),
        new Nibble(Array<Bit>:[1, 0, 1, 0]),
        new Nibble(Array<Bit>:[1, 0, 1, 1]),
        new Nibble(Array<Bit>:[1, 1, 0, 0]),
        new Nibble(Array<Bit>:[1, 1, 0, 1]),
        new Nibble(Array<Bit>:[1, 1, 1, 0]),
        new Nibble(Array<Bit>:[1, 1, 1, 1]),
        ];

    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 4;
    }

    @Override
    static Nibble zero() = 0;

    @Override
    static Nibble one() = 1;

    @Override
    static conditional Range<Nibble> range() {
        return True, MinValue..MaxValue;
    }

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 4-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 4;
        super(bits);
    }

    /**
     * Construct an 4-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 1;
        super(bytes);
    }

    /**
     * Construct an 4-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct Nibble(new IntLiteral(text).toNibble().bits);
    }

    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() = this == 0 ? Zero : Positive;

    // ----- obtaining a Nibble --------------------------------------------------------------------

    /**
     * Obtain the nibble corresponding to an integer value.
     *
     * @param n  an integer value in the range `[0..F]`
     *
     * @return the corresponding Nibble
     */
    static Nibble of(Int n) {
        assert:arg 0 <= n <= 0xF;
        return values[n];
    }

    /**
     * Obtain the nibble corresponding to a hex character.
     *
     * @param ch  the character value, one of `0..9`, `A..F`, or `a..f`
     *
     * @return the corresponding Nibble
     */
    static Nibble of(Char ch) {
        return values[switch (ch) {
            case '0'..'9': ch - '0' + 0x0;
            case 'A'..'F': ch - 'A' + 0xA;
            case 'a'..'f': ch - 'a' + 0xa;
            default: throw new IllegalArgument($|Illegal character {ch.quoted()};\
                                                | the character value must be in the range\
                                                | \"0..9\", \"A..F\", or \"a..f\"
                                              );
        }];
    }

    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Nibble next() {
        if (this < MaxValue) {
            return True, values[this.toInt64() + 1];
        }

        return False;
    }

    @Override
    conditional Nibble prev() {
        if (this > MinValue) {
            return True, values[this - 1];
        }

        return False;
    }

    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    Nibble add(Nibble! n) = this + n;

    @Override
    @Op("-")
    Nibble sub(Nibble! n)= this - n;

    @Override
    @Op("*")
    Nibble mul(Nibble! n) = this * n;

    @Override
    @Op("/")
    Nibble div(Nibble! n) = this / n;

    @Override
    @Op("%")
    Nibble mod(Nibble! n) =  this % n;

    @Override
    Nibble pow(Nibble! n) {
        Nibble result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }

    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the character representation of the nibble, which is the digit `0..9` or the alpha
     *         letter `A..F`
     */
    @Auto
    @Override
    Char toChar() {
        UInt32 n = toUInt32();
        return n <= 9 ? '0' + n : 'A' + n - 0xA;
    }

    @Override
    Nibble toNibble(Boolean checkBounds = False) = this;

    /**
     * @return the Int8 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int8 toInt8(Boolean checkBounds = False) = new Int8(new Bit[8](i -> i < 8-bitLength ? 0 : bits[i]));

    /**
     * @return the Int16 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int16 toInt16(Boolean checkBounds = False) = new Int16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));

    /**
     * @return the Int32 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int32 toInt32(Boolean checkBounds = False)  = new Int32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));

    /**
     * @return the Int64 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) = new Int64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));

    /**
     * @return the Int128 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));

    /**
     * @return the Int8 (Byte) value corresponding to the magnitude of the nibble, in the range
     *         `[0..F]`
     */
    @Auto
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) = new UInt8(new Bit[8](i -> i < 8-bitLength ? 0 : bits[i]));

    /**
     * @return the UInt16 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) = new UInt16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));

    /**
     * @return the UInt32 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) =  new UInt32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));

    /**
     * @return the UInt64 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt64 toUInt64(Boolean checkBounds = False)  = new UInt64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));

    /**
     * @return the UInt128 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) = new UInt128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = 1;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = buf.add(toChar());
}
