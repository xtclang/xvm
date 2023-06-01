import Number.Rounding;

/**
 * A Nibble is half of a byte (bite); basically, a nibble is the number of bits necessary to hold a
 * hexadecimal value (a _hexit_, akin to a digit).
 */
const Nibble(Bit[] bits)
        implements Sequential
        implements IntConvertible
        default(0) {
    assert() {
        assert bits.size == 4;
    }

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
        new Nibble([0, 0, 0, 0]),
        new Nibble([0, 0, 0, 1]),
        new Nibble([0, 0, 1, 0]),
        new Nibble([0, 0, 1, 1]),
        new Nibble([0, 1, 0, 0]),
        new Nibble([0, 1, 0, 1]),
        new Nibble([0, 1, 1, 0]),
        new Nibble([0, 1, 1, 1]),
        new Nibble([1, 0, 0, 0]),
        new Nibble([1, 0, 0, 1]),
        new Nibble([1, 0, 1, 0]),
        new Nibble([1, 0, 1, 1]),
        new Nibble([1, 1, 0, 0]),
        new Nibble([1, 1, 0, 1]),
        new Nibble([1, 1, 1, 0]),
        new Nibble([1, 1, 1, 1]),
        ];


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The actual array of bits representing this nibble, ordered from left-to-right, Most
     * Significant Bit (MSB) to Least Significant Bit (LSB).
     */
    private Bit[] bits;


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

    @Override
    Int stepsTo(Nibble that) {
        return that - this;
    }

    @Override
    Nibble skip(Int steps) {
        return values[toInt64() + steps];
    }


    // ----- math operations -----------------------------------------------------------------------

    /**
     * Addition: Add another Nibble to this Nibble, and return the result.
     *
     * @param n  the Nibble to add to this number (the addend)
     *
     * @return the resulting sum
     *
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("+")
    Nibble add(Nibble n) {
        return add(n.toInt64());
    }

    /**
     * Subtraction: Subtract another Nibble from this Nibble, and return the result.
     *
     * @param n  the Nibble to subtract from this Nibble (the subtrahend)
     *
     * @return the resulting difference
     *
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("-")
    Nibble sub(Nibble n) {
        return sub(n.toInt64());
    }

    /**
     * Addition: Add another number to this Nibble, and return the result.
     *
     * @param n  the number to add to this number (the addend)
     *
     * @return the resulting sum
     *
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("+")
    Nibble add(IntNumber n) {
        Int sum = this.toInt64() + n.toInt64();
        assert:bounds 0 <= sum < 16;
        return values[sum];
    }

    /**
     * Subtraction: Subtract another number from this Nibble, and return the result.
     *
     * @param n  the number to subtract from this number (the subtrahend)
     *
     * @return the resulting difference
     *
     * @throws OutOfBounds  if the resulting value is out of range for this type
     */
    @Op("-")
    Nibble sub(IntNumber n) {
        Int dif = this.toInt64() - n.toInt64();
        assert:bounds 0 <= dif < 16;
        return values[dif];
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain the nibble as an array of bits, in left-to-right order.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return the nibble as an array of bits
     */
    Bit[] toBitArray(Array.Mutability mutability = Constant) {
        return bits.toArray(mutability, True);
    }

    /**
     * @return the character representation of the nibble, which is the digit `0..9` or the alpha
     *         letter `A..F`
     */
    @Auto
    Char toChar() {
        UInt32 n = toUInt32();
        return n <= 9 ? '0' + n : 'A' + n - 0xA;
    }

    @Auto
    @Override
    IntLiteral toIntLiteral(Rounding direction = TowardZero) {
        return new IntLiteral(toChar().toString());
    }

    /**
     * @return the Int value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toInt();
    }

    /**
     * @return the Int8 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toInt8();
    }

    /**
     * @return the Int16 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toInt16();
    }

    /**
     * @return the Int32 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toInt32();
    }

    /**
     * @return the Int64 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toInt64();
    }

    /**
     * @return the Int128 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toInt128();
    }

    /**
     * @return the IntN value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        return toUInt8().toIntN();
    }

    /**
     * @return the Int8 (Byte) value corresponding to the magnitude of the nibble, in the range
     *         `[0..F]`
     */
    @Auto
    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        return bits.toUInt8();
    }

    /**
     * @return the UInt16 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toUInt16();
    }

    /**
     * @return the UInt32 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toUInt32();
    }

    /**
     * @return the UInt64 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toUInt64();
    }

    /**
     * @return the UInt128 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return toUInt8().toUInt128();
    }

    /**
     * @return the UIntN value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto
    @Override
    UIntN toUIntN(Rounding direction = TowardZero) {
        return toUInt8().toUIntN();
    }


    // ----- Orderable and Hashable ----------------------------------------------------------------

    /**
     * Calculate a hash code for the specified Enum value.
     */
    static <CompileType extends Nibble> Int64 hashCode(CompileType value) {
        return value.toInt64();
    }

    /**
     * Compare two enumerated values that belong to the same enumeration purposes of ordering.
     */
    static <CompileType extends Nibble> Ordered compare(CompileType value1, CompileType value2) {
        return value1.toUInt8() <=> value2.toUInt8();
    }

    /**
     * Compare two enumerated values that belong to the same enumeration for equality.
     */
    static <CompileType extends Nibble> Boolean equals(CompileType value1, CompileType value2) {
        return value1.toUInt8() == value2.toUInt8();
    }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return 1;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return buf.add(toChar());
    }
}