/**
 * The OutputStream interface represents a output stream of binary data.
 */
interface OutputStream
        extends BinaryOutput
    {
    /**
     * The 0-based offset within the stream. Attempting to set the offset outside of the bounds of
     * the stream will result in an exception. When the offset is equal to the size, then the next
     * write to the stream will append to the end of the stream.
     */
    Int offset;

    /**
     * The total size of the stream, in bytes. This value will grow as more binary data is written.
     */
    @RO Int size;
    }