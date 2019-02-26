const Float32
        extends BinaryFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct BinaryFPNumber(bits);
        }
    }
