const VarFloat
        extends BinaryFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct BinaryFPNumber(bits);
        }
    }
