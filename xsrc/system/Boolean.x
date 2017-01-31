enum Boolean
    {
    False
        {
        Bit to<Bit>()
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

    Bit to<Bit>();

    Byte to<Byte>()
        {
        return bit.to<Byte>();
        }

    Int to<Int>()
        {
        return bit.to<Int>();
        }

    UInt to<UInt>()
        {
        return bit.to<UInt>();
        }

    @op Boolean and(Boolean that);
    @op Boolean or(Boolean that);
    @op Boolean xor(Boolean that);
    @op Boolean not();
    };
