/**
 * The DataOutput interface represents a output stream of values of various fundamental Ecstasy
 * types. It provides default implementations for some methods, but does not prescribe an underlying
 * data format. For example, integers could be fixed length or compressed, and characters could be
 * encoded as UTF-8, UTF-16, UTF-32, or even ASCII.
 */
mixin PackedDataOutput
        into BinaryOutput
        implements DataOutput {
    @Override
    void writeChar(Char value) {
        writeUTF8Char(this, value);
    }

    @Override
    void writeInt16(Int16 value) {
        writeInt128(value);
    }

    @Override
    void writeInt32(Int32 value) {
        writeInt128(value);
    }

    @Override
    void writeInt64(Int64 value) {
        writeInt128(value);
    }

    @Override
    void writeInt128(Int128 value) {
        writePackedInt(this, value);
    }

    @Override
    void writeIntN(IntN value) {
        writePackedIntN(this, value);
    }

    @Override
    void writeUInt16(UInt16 value) {
        writeInt64(value);
    }

    @Override
    void writeUInt32(UInt32 value) {
        writeInt64(value);
    }

    @Override
    void writeUInt64(UInt64 value) {
        writeIntN(value);
    }

    @Override
    void writeUInt128(UInt128 value) {
        writeIntN(value);
    }

    @Override
    void writeUIntN(UIntN value) {
        writeIntN(value);
    }
}