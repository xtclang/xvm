const VarDec
        extends DecimalFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct DecimalFPNumber(bits);
        }
    }
