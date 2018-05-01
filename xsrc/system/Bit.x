const Bit
    {
    construct(IntLiteral literal)
        {
        assert:always literal == 0 || literal == 1;
        this.literal = literal;
        }

    private IntLiteral literal;

    static Bit defaultValue = 0;        // TODO could this be an annotation on Bit?

    IntLiteral to<IntLiteral>()
        {
        return literal;
        }

    Boolean to<Boolean>()
        {
        return literal == 1;
        }

    @Auto Byte to<Byte>()
        {
        return literal.to<Byte>();
        }

    @Auto Int to<Int>()
        {
        return literal.to<Int>();
        }

    @Auto UInt to<UInt>()
        {
        return literal.to<UInt>();
        }

    @Op Bit and(Bit that)
        {
        return this.literal == 1 && that.literal == 1 ? 1 : 0;
        }

    @Op Bit or(Bit that)
        {
        return this.literal == 1 || that.literal == 1 ? 1 : 0;
        }

    @Op Bit xor(Bit that)
        {
        return this.literal == 1 ^ that.literal == 1 ? 1 : 0;
        }

    @Op Bit not()
        {
        return literal == 1 ? 0 : 1;
        }
    }
