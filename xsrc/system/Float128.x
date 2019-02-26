const Float128
        extends BinaryFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct BinaryFPNumber(bits);
        }
    }
