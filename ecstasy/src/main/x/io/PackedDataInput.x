/**
 * The PackedDataInput mixin uses the packed integer format for integers and the UTF-8 encoding for
 * characters.
 */
mixin PackedDataInput
        into BinaryInput
        implements DataInput
    {
    @Override
    Char readChar()
        {
        return readUTF8Char(this);
        }

    @Override
    Int16 readInt16()
        {
        return readInt64().toInt16();
        }

    @Override
    Int32 readInt32()
        {
        return readInt64().toInt32();
        }

    @Override
    Int64 readInt64()
        {
        return readPackedInt(this);
        }

    @Override
    Int128 readInt128()
        {
        return readIntN().toInt128();
        }

    @Override
    IntN readIntN()
        {
        return readPackedIntN(this);
        }

    @Override
    UInt16 readUInt16()
        {
        return readInt64().toUInt16();
        }

    @Override
    UInt32 readUInt32()
        {
        return readInt64().toUInt32();
        }

    @Override
    UInt64 readUInt64()
        {
        return readIntN().toUInt64();
        }

    @Override
    UInt128 readUInt128()
        {
        return readIntN().toUInt128();
        }

    @Override
    UIntN readUIntN()
        {
        return readIntN().toUIntN();
        }
    }