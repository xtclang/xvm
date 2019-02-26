const Float16
        extends BinaryFPNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size == 16;
        construct BinaryFPNumber(bits);
        }
    }
