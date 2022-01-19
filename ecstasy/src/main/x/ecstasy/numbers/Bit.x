/**
 * A bit represents the smallest possible unit of storage. It has two states: `0` and `1`.
 */
const Bit(IntLiteral literal)
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

    @Auto Byte toUInt8()
        {
        return literal.toUInt8();
        }

    @Auto Int8    toInt8()    {return literal.toInt8();   }
    @Auto Int16   toInt16()   {return literal.toInt16();  }
    @Auto Int32   toInt32()   {return literal.toInt32();  }
    @Auto Int64   toInt64()   {return literal.toInt64();  }
    @Auto Int128  toInt128()  {return literal.toInt128(); }
    @Auto IntN    toIntN()    {return literal.toIntN();   }
    @Auto UInt8   toUInt8()   {return literal.toUInt8();  }
    @Auto UInt16  toUInt16()  {return literal.toUInt16(); }
    @Auto UInt32  toUInt32()  {return literal.toUInt32(); }
    @Auto UInt64  toUInt64()  {return literal.toUInt64(); }
    @Auto UInt128 toUInt128() {return literal.toUInt128();}
    @Auto UIntN   toUIntN()   {return literal.toUIntN();  }

    @Op("&")
    Bit and(Bit! that)
        {
        return this.literal == 1 && that.literal == 1 ? 1 : 0;
        }

    @Op("|")
    Bit or(Bit! that)
        {
        return this.literal == 1 || that.literal == 1 ? 1 : 0;
        }

    @Op("^")
    Bit xor(Bit! that)
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
            return True, 1;
            }

        return False;
        }

    @Override
    conditional Bit prev()
        {
        if (this == 1)
            {
            return True, 0;
            }

        return False;
        }

    @Override
    Int stepsTo(Bit! that)
        {
        return that - this;
        }

    @Override
    Bit skip(Int steps)
        {
        return switch (this, steps)
            {
            case (_,  0): this;
            case (0,  1): 1;
            case (1, -1): 0;
            default: throw new OutOfBounds($"Bit={this}, steps={steps}");
            };
        }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return buf.add(toBoolean() ? '1' : '0');
        }
    }
