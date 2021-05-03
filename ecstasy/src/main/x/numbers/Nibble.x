const Nibble
    implements Sequential
    default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for a Nibble.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for a Nibble.
     */
    static IntLiteral maxvalue = 0xF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Nibble from a bit array.
     *
     * @param bits  an array of 4 bits
     */
    construct(Bit[] bits)
        {
        assert bits.size == 4;
        this.bits = bits;
        }

    /**
     * Construct a Nibble from an integer value.
     *
     * @param n  an integer value in the range `[0..F]`
     */
    construct(Int n)
        {
        assert:arg 0 <= n <= 0xF;
        bits = n.toBitArray()[60..64);
        // TODO GG this should compile (but I don't want it here): assert (n == 0) == (bits == [0,0,0,0]);
        }

    /**
     * Obtain the nibble corresponding to a hex character.

     * @param ch  the character value, one of `0..9`, `A..F`, or `a..f`
     *
     * @return the corresponding Nibble
     */
    static Nibble of(Char ch)
        {
        return values[switch (ch)
            {
            case '0'..'9': ch - '0' + 0x0;
            case 'A'..'F': ch - 'A' + 0xA;
            case 'a'..'f': ch - 'a' + 0xa;
            default: assert:arg;
            }];
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The actual array of bits representing this nibble, ordered from left-to-right, Most
     * Significant Bit (MSB) to Least Significant Bit (LSB).
     */
    private Bit[] bits;

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


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Nibble next()
        {
        if (this < maxvalue)
            {
            // TODO GG does not compile: console.println($"(this+1)={this+1}");
            return True, values[this.toInt64() + 1];
            }

        return False;
        }

    @Override
    conditional Nibble prev()
        {
        if (this > minvalue)
            {
            return True, values[this - 1];
            }

        return False;
        }

    @Override
    Int stepsTo(Nibble that)
        {
        return that - this;
        }

    @Override
    Nibble skip(Int steps)
        {
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
    @Op("+") Nibble add(Nibble n)
        {
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
    @Op("-") Nibble sub(Nibble n)
        {
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
    @Op("+") Nibble add(IntNumber n)
        {
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
    @Op("-") Nibble sub(IntNumber n)
        {
        Int dif = this.toInt64() - n.toInt64();
        assert:bounds 0 <= dif < 16;
        return values[dif];
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return an immutable array of four bits
     */
    immutable Bit[] toBitArray()
        {
        return bits.as(immutable Bit[]);
        }

    /**
     * @return the character representation of the nibble, which is the digit `0..9` or the alpha
     *         letter `A..F`
     */
    @Auto Char toChar()
        {
        UInt32 n = toUInt32();
        return n <= 9 ? '0' + n : 'A' + n - 0xA;
        }

    /**
     * @return the Int8 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto Int8 toInt8()
        {
        return toUInt8().toInt8();
        }

    /**
     * @return the Int16 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto Int16 toInt16()
        {
        return toUInt8().toInt16();
        }

    /**
     * @return the Int32 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto Int32 toInt32()
        {
        return toUInt8().toInt32();
        }

    /**
     * @return the Int64 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto Int64 toInt64()
        {
        return toUInt8().toInt64();
        }

    /**
     * @return the Int128 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto Int128 toInt128()
        {
        return toUInt8().toInt128();
        }

    /**
     * @return the Int8 (Byte) value corresponding to the magnitude of the nibble, in the range
     *         `[0..F]`
     */
    @Auto UInt8 toUInt8()
        {
        return bits.toUInt8();
        }

    /**
     * @return the UInt16 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto UInt16 toUInt16()
        {
        return toUInt8().toUInt16();
        }

    /**
     * @return the UInt32 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto UInt32 toUInt32()
        {
        return toUInt8().toUInt32();
        }

    /**
     * @return the UInt64 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto UInt64 toUInt64()
        {
        return toUInt8().toUInt64();
        }

    /**
     * @return the UInt128 value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto UInt128 toUInt128()
        {
        return toUInt8().toUInt128();
        }

    /**
     * @return the UIntN value corresponding to the magnitude of the nibble, in the range `[0..F]`
     */
    @Auto UIntN toUIntN()
        {
        return toUInt8().toUIntN();
        }


    // ----- Orderable and Hashable ----------------------------------------------------------------

    /**
     * Calculate a hash code for the specified Enum value.
     */
    static <CompileType extends Nibble> Int hashCode(CompileType value)
        {
        return value.toInt64();
        }

    /**
     * Compare two enumerated values that belong to the same enumeration purposes of ordering.
     */
    static <CompileType extends Nibble> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1.toUInt8() <=> value2.toUInt8();
        }

    /**
     * Compare two enumerated values that belong to the same enumeration for equality.
     */
    static <CompileType extends Nibble> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.toUInt8() == value2.toUInt8();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return buf.add(toChar());
        }
    }
