const Bit
    implements Sequential
    default(0)
    {
    construct(IntLiteral literal)
        {
        assert literal == 0 || literal == 1;
        this.literal = literal;
        }

    private IntLiteral literal;

    IntLiteral toIntLiteral()
        {
        return literal;
        }

    Boolean toBoolean()
        {
        return literal == 1;
        }

    @Auto Byte toByte()
        {
        return literal.toByte();
        }

    @Auto Int toInt()
        {
        return literal.toInt();
        }

    @Auto UInt toUInt()
        {
        return literal.toUInt();
        }

    @Op("&")
    Bit and(Bit that)
        {
        return this.literal == 1 && that.literal == 1 ? 1 : 0;
        }

    @Op("|")
    Bit or(Bit that)
        {
        return this.literal == 1 || that.literal == 1 ? 1 : 0;
        }

    @Op("^")
    Bit xor(Bit that)
        {
        return this.literal == 1 ^^ that.literal == 1 ? 1 : 0;
        }

    @Op("~")
    @Op Bit not()
        {
        return literal == 1 ? 0 : 1;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Bit next()
        {
        if (this == 0)
            {
            return true, 1;
            }

        return false;
        }

    @Override
    conditional Bit prev()
        {
        if (this == 1)
            {
            return true, 0;
            }

        return false;
        }

    @Override
    Int stepsTo(Bit that)
        {
        return that - this;
        }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1;
        }

    @Override
    void appendTo(Appender<Char> buf)
        {
        buf.add(toBoolean() ? '1' : '0');
        }
    }
