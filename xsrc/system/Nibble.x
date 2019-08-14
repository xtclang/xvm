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

    Char toChar()
        {
        Byte b = toByte();
        if (b >= 0 && b <= 9)
            {
            b = '0'.toByte() + b;
            }
        else
            {
            b = 'A'.toByte() + b - 0xA;
            }
        return new Char(b);
        }

    @Auto Byte toByte()
        {
        return    (bits[0] == 0 ? 0 : 1)
                + (bits[1] == 0 ? 0 : 2)
                + (bits[2] == 0 ? 0 : 4)
                + (bits[3] == 0 ? 0 : 8);
        }

    @Auto Int toInt()
        {
        return toByte().toInt();
        }

    @Auto UInt toUInt()
        {
        return toByte().toUInt();
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
