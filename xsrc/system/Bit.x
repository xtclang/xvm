const Bit(IntLiteral literal)
    {
    construct Bit(IntLiteral literal)
        {
        assert:always literal == 0 || literal == 1;
        this.literal = literal;
        }

    IntLiteral to<IntLiteral>()
        {
        return literal;
        }

    Boolean to<Boolean>()
        {
        return literal == 1;
        }

    @auto Byte to<Byte>()
        {
        return literal.to<Byte>();
        }

    @auto Int to<Int>()
        {
        return literal.to<Int>();
        }

    @auto UInt to<UInt>()
        {
        return literal.to<UInt>();
        }

    @op Bit and(Bit that)
        {
        return this.literal == 1 && that.literal == 1 ? 1 : 0;
        }

    @op Bit or(Bit that)
        {
        return this.literal == 1 || that.literal == 1 ? 1 : 0;
        }

    @op Bit xor(Bit that)
        {
        return this.literal == 1 ^ that.literal == 1 ? 1 : 0;
        }

    @op Bit not()
        {
        return literal == 1 ? 0 : 1;
        }
    };
