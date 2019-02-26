const Dec64
        extends DecimalFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct DecimalFPNumber(bits);
        }
    }
