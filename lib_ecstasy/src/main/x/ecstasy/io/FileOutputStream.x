import ecstasy.fs.FileChannel;

/**
 * The FileOutputStream is an implementation of an [OutputStream] on top of a [File].
 */
 class FileOutputStream(File file)
        implements OutputStream {

    assert() {
        assert:arg !file.exists || file.writable;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying file.
     */
    private File file;

    /**
     * The underlying file channel.
     */
    private @Lazy FileChannel channel.calc() = file.open(NoRead, [Write]);

    // ----- OutputStream interface ----------------------------------------------------------------

    @Override
    Int offset {
        @Override
        Int get() = size;

        @Override
        void set(Int newOffset) {
            channel.position = newOffset;
        }
    }

    @Override
    Int size.get() = file.size;

    // ----- BinaryOutput interface ----------------------------------------------------------------

    @Override
    void writeByte(Byte value) = file.append([value]);

    @Override
    void writeBytes(Byte[] bytes) = file.append(bytes);

    @Override
    void writeBytes(Byte[] bytes, Int offset, Int count) = file.append(bytes[offset..<offset+count]);
}