/**
 * The BinaryOutput interface represents an abstract output stream of binary data.
 *
 * Note that the inclusion of the Closeable interface is for the benefit of the stream
 * implementation, not the consumer of the stream. In other words, the stream is not required to
 * strictly reject calls after a call to `close()`; the purpose for the `close()` method is to allow
 * the stream to release any underlying resources, including those managed by an operating system,
 * and any subsequent calls may _or may not_ fail as a result.
 */
interface BinaryOutput
        extends Closeable {
    /**
     * Write a Byte value to the stream.
     *
     * @param value  a value of type Byte to write to the stream
     */
    void writeByte(Byte value);

    /**
     * Write the bytes from the provided array to the stream.
     *
     * @param  bytes   the byte array containing the bytes to write out
     */
    void writeBytes(Byte[] bytes) {
        writeBytes(bytes, 0, bytes.size);
    }

    /**
     * Write the specified number of bytes to the stream from the provided array.
     *
     * @param  bytes   the byte array to read from
     * @param  offset  the offset into the array of the first byte to write
     * @param  count   the number of bytes to write
     */
    void writeBytes(Byte[] bytes, Int offset, Int count) {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last) {
            writeByte(bytes[offset++]);
        }
    }

    @Override
    void close(Exception? cause = Null) {
    }
}