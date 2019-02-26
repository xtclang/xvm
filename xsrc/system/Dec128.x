const Dec128
        extends DecimalFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct DecimalFPNumber(bits);
        }
    }
