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
        @op Boolean and(Boolean that)
            {
            return False;
            }
        @op Boolean or(Boolean that)
            {
            return that;
            }
        @op Boolean xor(Boolean that)
            {
            return that;
            }
        @op Boolean not()
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
        @op Boolean and(Boolean that)
            {
            return that;
            }
        @op Boolean or(Boolean that)
            {
            return True;
            }
        @op Boolean xor(Boolean that)
            {
            return that == False;
            }
        @op Boolean not()
            {
            return False;
            }
        };

    Bit  to<Bit>();
    Byte to<Byte>();
    Int  to<Int>();
    UInt to<UInt>();
    
    @op Boolean and(Boolean that);
    @op Boolean or(Boolean that);
    @op Boolean xor(Boolean that);
    @op Boolean not();
    };
