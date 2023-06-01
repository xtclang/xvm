import io.Channel;

/**
 * FileChannel provides the ability to read, write, and manipulate a file.
 */
interface FileChannel
        extends Channel {
    /**
     * The size of the channel's file. Reducing this value will truncate the file accordingly.
     */
    Int size;

    /**
     * The number of bytes from the beginning of the file to the current file position.
     */
    Int position;

    /**
     * The number of bytes available for reading or writing without growing the size.
     */
    @RO Int remaining.get() {
        return size - position;
    }

    /**
     * Ensure all the changes are written to the underlying storage medium.
     */
    void flush();

    // TODO: modes and attributes...
}