/**
 * The PackedDataInput annotation uses the packed integer format for integers and the UTF-8 encoding
 * for characters.
 */
annotation PackedDataInput
        into BinaryInput
        implements DataInput {
    @Override
    Char readChar() = readUTF8Char(this);

    @Override
    Int16 readInt16() = readInt64().toInt16();

    @Override
    Int32 readInt32() = readInt64().toInt32();

    @Override
    Int64 readInt64() = readPackedInt(this).toInt64();

    @Override
    Int128 readInt128() = readIntN().toInt128();

    @Override
    IntN readIntN() = readPackedIntN(this);

    @Override
    UInt16 readUInt16() = readInt64().toUInt16();

    @Override
    UInt32 readUInt32() =  readInt64().toUInt32();

    @Override
    UInt64 readUInt64() = readIntN().toUInt64();

    @Override
    UInt128 readUInt128() = readIntN().toUInt128();

    @Override
    UIntN readUIntN() = readIntN().toUIntN();
}