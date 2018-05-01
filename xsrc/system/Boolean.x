enum Boolean
    {
    False
        {
        Bit to<Bit>()
            {
            return 0;
            }
        Byte to<Byte>()
            {
            return 0;
            }
        Int to<Int>()
            {
            return 0;
            }
        UInt to<UInt>()
            {
            return 0;
            }
        @Op Boolean and(Boolean that)
            {
            return this;
            }
        @Op Boolean or(Boolean that)
            {
            return that;
            }
        @Op Boolean xor(Boolean that)
            {
            return that;
            }
        @Op Boolean not()
            {
            return True;
            }
        },

    True
        {
        Bit to<Bit>()
            {
            return 1;
            }
        Byte to<Byte>()
            {
            return 1;
            }
        Int to<Int>()
            {
            return 1;
            }
        UInt to<UInt>()
            {
            return 1;
            }
        @Op Boolean and(Boolean that)
            {
            return that;
            }
        @Op Boolean or(Boolean that)
            {
            return this;
            }
        @Op Boolean xor(Boolean that)
            {
            return !that;
            }
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
