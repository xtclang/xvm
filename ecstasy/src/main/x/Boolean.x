enum Boolean
        default(False)
    {
    False
        {
        @Override
        @Op("&") Boolean and(Boolean that)
            {
            return this;
            }

        @Override
        @Op("|") Boolean or(Boolean that)
            {
            return that;
            }

        @Override
        @Op("^") Boolean xor(Boolean that)
            {
            return that;
            }

        @Override
        @Op("~") Boolean not()
            {
            return True;
            }

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
        },

    True
        {
        @Override
        @Op("&") Boolean and(Boolean that)
            {
            return that;
            }

        @Override
        @Op("|") Boolean or(Boolean that)
            {
            return this;
            }

        @Override
        @Op("^") Boolean xor(Boolean that)
            {
            return !that;
            }

        @Override
        @Op("~") Boolean not()
            {
            return False;
            }

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
        };


    // ----- logical operations --------------------------------------------------------------------

    @Op("&") Boolean and(Boolean that);
    @Op("|") Boolean or(Boolean that);
    @Op("^") Boolean xor(Boolean that);
    @Op("~") Boolean not();


    // ----- conversion methods --------------------------------------------------------------------

    Bit  toBit();
    Byte toByte();

    Int8    toInt8()    {return toBit(); }
    Int16   toInt16()   {return toByte();}
    Int32   toInt32()   {return toByte();}
    Int64   toInt64()   {return toByte();}
    Int128  toInt128()  {return toByte();}
    IntN    toIntN()    {return toByte();}
    UInt8   toUInt8()   {return toByte();}
    UInt16  toUInt16()  {return toByte();}
    UInt32  toUInt32()  {return toByte();}
    UInt64  toUInt64()  {return toByte();}
    UInt128 toUInt128() {return toByte();}
    UIntN   toUIntN()   {return toByte();}
    }
