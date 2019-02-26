const Dec32
        extends DecimalFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct DecimalFPNumber(bits);
        }
    }
