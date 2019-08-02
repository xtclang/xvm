const Nibble
    implements Sequential
    default(0)
    {
    /**
     * The minimum value for a Nibble.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for a Nibble.
     */
    static IntLiteral maxvalue = 0xF;

    construct(Bit[] bits)
        {
        assert bits.size == 4;
        this.bits = bits;
        }

    private Bit[] bits;

    Char to<Char>()
        {
        Byte b = to<Byte>();
        if (b >= 0 && b <= 9)
            {
            b = '0'.to<Byte>() + b;
            }
        else
            {
            b = 'A'.to<Byte>() + b - 0xA;
            }
        return new Char(b);
        }

    @Auto Byte to<Byte>()
        {
        return    (bits[0] == 0 ? 0 : 1)
                + (bits[1] == 0 ? 0 : 2)
                + (bits[2] == 0 ? 0 : 4)
                + (bits[3] == 0 ? 0 : 8);
        }

    @Auto Int to<Int>()
        {
        return to<Byte>().to<Int>();
        }

    @Auto UInt to<UInt>()
        {
        return to<Byte>().to<UInt>();
        }

    /**
     * Get a Nibble for a given Int value.
     */
    static Nibble of(Int n)
        {
        assert n >= 0 && n <= 0xF;
        return values[n];
        }

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
    }
