const Float64
        extends BinaryFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct BinaryFPNumber(bits);
        }
    }
