/**
 * The InputStream interface represents a bounded input stream of binary data. Unlike Channel, an
 * InputStream is **not** open-ended; it has a finite size, although that size may be significantly
 * larger would fit into a program's memory, and thus the interface supports underlying
 * implementations that can represent the entirety of the binary stream, while only having an
 * arbitrarily small portion of it memory-resident.
 *
 * By its nature, an InputStream has a knowable length, a current position, and it is _seekable_.
 * That means that the position within the stream can be modified -- in either direction.
 */
interface InputStream
        extends BinaryInput
    {
    /**
     * The 0-based offset within the stream. Attempting to set the offset outside of the bounds of
     * the stream will result in an exception.
     */
    Int offset;

    /**
     * The total size of the stream, in bytes, including any portion that has already been read.
     */
    @RO Int size;

    /**
     * The number of bytes remaining in the stream. This will be equal to `size` at the start of
     * the stream, and equal to `0` at the end of the stream.
     */
    @RO Int remaining.get()
        {
        return (size - offset).notLessThan(0);
        }

    /**
     * True iff the end of the stream has been reached.
     */
    @RO Boolean eof.get()
        {
        return remaining == 0;
        }
    }