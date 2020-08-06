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

    construct(Bit[] bits)
        {
        assert bits.size == 4;
        this.bits = bits;
        }

    static Nibble of(Char ch)
        {
        return switch (ch)
            {
            case '0'..'9': Nibble.of(ch - '0' + 0x0);
            case 'A'..'F': Nibble.of(ch - 'A' + 0xA);
            case 'a'..'f': Nibble.of(ch - 'a' + 0xa);
            default: assert:arg;
            };
        }

    /**
     * Get a Nibble for a given Int value.
     */
    static Nibble of(Int n)
        {
        assert:arg n >= 0 && n <= 0xF;
        return values[n];
        }


    // ----- properties ----------------------------------------------------------------------------

    private Bit[] bits;

    private static Nibble[] values =
        [
        new Nibble([0, 0, 0, 0]),
        new Nibble([1, 0, 0, 0]),
        new Nibble([0, 1, 0, 0]),
        new Nibble([1, 1, 0, 0]),
        new Nibble([0, 0, 1, 0]),
        new Nibble([1, 0, 1, 0]),
        new Nibble([0, 1, 1, 0]),
        new Nibble([1, 1, 1, 0]),
        new Nibble([0, 0, 0, 1]),
        new Nibble([1, 0, 0, 1]),
        new Nibble([0, 1, 0, 1]),
        new Nibble([1, 1, 0, 1]),
        new Nibble([0, 0, 1, 1]),
        new Nibble([1, 0, 1, 1]),
        new Nibble([0, 1, 1, 1]),
        new Nibble([1, 1, 1, 1])
        ];


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Nibble next()
        {
        if (this < maxvalue)
            {
            return true, of(this + 1);
            }

        return false;
        }

    @Override
    conditional Nibble prev()
        {
        if (this > minvalue)
            {
            return true, of(this - 1);
            }

        return false;
        }

    @Override
    Int stepsTo(Nibble that)
        {
        return that - this;
        }

    @Override
    Nibble skip(Int steps)
        {
        return Nibble.of(toInt() + steps);
        }


    // ----- conversions ---------------------------------------------------------------------------

    immutable Bit[] toBitArray()
        {
        return bits.as(immutable Bit[]);
        }

    @Auto
    Byte toByte()
        {
        return bits.toByte();
        }

    UInt toUInt32()
        {
        return toByte().toUInt32();
        }

    Char toChar()
        {
        Int n = toInt();
        return n <= 9 ? '0' + n : 'A' + n - 0xA;
        }

    @Auto
    Int toInt()
        {
        return toByte().toInt();
        }

    @Auto
    UInt toUInt()
        {
        return toByte().toUInt();
        }
    }
