import numbers.Int16;
import numbers.Int32;
import numbers.Int128;
import numbers.UInt16;
import numbers.UInt32;
import numbers.UInt128;
import numbers.VarInt;
import numbers.VarUInt;

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
        return readInt().toInt16();
        }

    @Override
    UInt16 readUInt16()
        {
        return readInt().toUInt16();
        }

    @Override
    Int32 readInt32()
        {
        return readInt().toInt32();
        }

    @Override
    UInt32 readUInt32()
        {
        return readInt().toUInt32();
        }

    @Override
    Int readInt()
        {
        return readPackedInt(this);
        }

    @Override
    UInt readUInt()
        {
        return readVarInt().toUInt();
        }

    @Override
    Int128 readInt128()
        {
        return readVarInt().toInt128();
        }

    @Override
    UInt128 readUInt128()
        {
        return readVarInt().toUInt128();
        }

    @Override
    VarInt readVarInt()
        {
        return readPackedVarInt(this);
        }

    @Override
    VarUInt readVarUInt()
        {
        return readVarInt().toVarUInt();
        }
    }