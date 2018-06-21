enum Boolean
    {
    False
        {
        @Override
        Bit to<Bit>()
            {
            return 0;
            }
        @Override
        Byte to<Byte>()
            {
            return 0;
            }
        @Override
        Int to<Int>()
            {
            return 0;
            }
        @Override
        UInt to<UInt>()
            {
            return 0;
            }
        @Override
        @Op Boolean and(Boolean that)
            {
            return this;
            }
        @Override
        @Op Boolean or(Boolean that)
            {
            return that;
            }
        @Override
        @Op Boolean xor(Boolean that)
            {
            return that;
            }
        @Override
        @Op Boolean not()
            {
            return True;
            }
        },

    True
        {
        @Override
        Bit to<Bit>()
            {
            return 1;
            }
        @Override
        Byte to<Byte>()
            {
            return 1;
            }
        @Override
        Int to<Int>()
            {
            return 1;
            }
        @Override
        UInt to<UInt>()
            {
            return 1;
            }
        @Override
        @Op Boolean and(Boolean that)
            {
            return that;
            }
        @Override
        @Op Boolean or(Boolean that)
            {
            return this;
            }
        @Override
        @Op Boolean xor(Boolean that)
            {
            return !that;
            }
        @Override
        @Op Boolean not()
            {
            return False;
            }
        };

    Bit  to<Bit>();
    Byte to<Byte>();
    Int  to<Int>();
    UInt to<UInt>();

    @Op Boolean and(Boolean that);
    @Op Boolean or(Boolean that);
    @Op Boolean xor(Boolean that);
    @Op Boolean not();
    }
