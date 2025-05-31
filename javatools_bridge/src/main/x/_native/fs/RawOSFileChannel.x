import io.RawChannel;

/**
 * Native OS implementation of RawChannel for OSFileChannel.
 */
service RawOSFileChannel
        extends RawChannel {

    /**
     * The size of the channel's file. Reducing this value will truncate the file accordingly.
     */
    Int size;

    /**
     * The number of bytes from the beginning of the file to the current file position.
     */
    Int position;

    @Override
    @RO Boolean eof.get() = size - position <= 0;

    /**
     * Ensure all the changes are written to the underlying storage medium.
     */
    void flush() = TODO("native");
}