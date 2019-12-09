import numbers.Bit;

enum Boolean
        default(False)
    {
    False
        {
        @Override
        Bit toBit()
            {
            return 0;
            }
        @Override
        Byte toByte()
            {
            return 0;
            }
        @Override
        Int toInt()
            {
            return 0;
            }
        @Override
        UInt toUInt()
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
        Bit toBit()
            {
            return 1;
            }
        @Override
        Byte toByte()
            {
            return 1;
            }
        @Override
        Int toInt()
            {
            return 1;
            }
        @Override
        UInt toUInt()
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

    Bit  toBit();
    Byte toByte();
    Int  toInt();
    UInt toUInt();

    @Op Boolean and(Boolean that);
    @Op Boolean or(Boolean that);
    @Op Boolean xor(Boolean that);
    @Op Boolean not();
    }
