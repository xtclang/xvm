import ecstasy.fs.FileChannel;

/**
 * The FileInputStream is an implementation of an InputStream on top of a [File].
 */
 class FileInputStream(File file)
        implements InputStream {

    assert() {
        assert:arg file.readable;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying file.
     */
    private File file;

    /**
     * The underlying file channel.
     */
    private @Lazy FileChannel channel.calc() = file.open(Read, []);

    /**
     * The current ReadBuffer.
     */
    private ReadBuffer? buffer;

    // ----- OutputStream interface ----------------------------------------------------------------

    @Override
    Int offset {
        @Override
        Int get() = channel.position;

        @Override
        void set(Int newOffset) = throw new ReadOnly();
    }

    @Override
    Int size.get() = file.size;

    // ----- BinaryInput interface -----------------------------------------------------------------

    @Override
    Byte readByte() = ensureBuffer().readByte();

    // ----- internal ------------------------------------------------------------------------------

    ReadBuffer ensureBuffer() {
        if (!eof) {
            if (ReadBuffer buffer ?= this.buffer) {
                return buffer;
            }
            buffer = channel.read();
            return ensureBuffer();
        }
        throw new EndOfFile();
    }
}