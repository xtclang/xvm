import ecstasy.fs.FileChannel;

/**
 * The FileInputStream is an implementation of an [InputStream] on top of a [File].
 */
class FileInputStream(File file)
        extends PrefetchBufferInput
        implements InputStream  {

    construct(File file) {
        assert:arg file.readable;

        FileChannel channel = file.open(Read, []);

        this.file    = file;
        this.channel = channel;

        construct PrefetchBufferInput(() -> channel.read() ?: Empty);
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying file.
     */
    protected File file;

    /**
     * The underlying file channel.
     */
    protected FileChannel channel;

    // ----- InputStream interface -----------------------------------------------------------------

    @Override
    Int offset {
        Int get() = channel.position;

        void set(Int newOffset) {
            assert:bounds newOffset < size;
            channel.position = newOffset;
            requestNextBuffer();
        }
    }

    @Override
    Int size.get() = file.size;
}