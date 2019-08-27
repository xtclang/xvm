/**
 * The BinaryInput interface represents an abstract input stream of binary data.
 *
 * Note that the inclusion of the Closeable interface is for the benefit of the stream
 * implementation, not the consumer of the stream. In other words, the stream is not required to
 * strictly reject calls after a call to `close()`; the purpose for the `close()` method is to allow
 * the stream to release any underlying resources, including those managed by an operating system,
 * and any subsequent calls may _or may not_ fail as a result.
 */
interface BinaryInput
        extends Closeable
    {
    /**
     * @return  a value of type Byte read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    Byte readByte();

    /**
     * Read bytes into the provided array.
     *
     * @param  bytes  the byte array to read into
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void readBytes(Byte[] bytes)
        {
        readBytes(bytes, 0, bytes.size);
        }

    /**
     * Read the specified number of bytes into the provided array.
     *
     * @param  bytes   the byte array to read into
     * @param  offset  the offset into the array to store the first byte read
     * @param  count   the number of bytes to read
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void readBytes(Byte[] bytes, Int offset, Int count)
        {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last)
            {
            bytes[offset++] = readByte();
            }
        }

    /**
     * Read the specified number of bytes, returning those bytes as an array.
     *
     * @param count  the number of bytes to read
     *
     * @return an array of the specified size, containing the bytes read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    Byte[] readBytes(Int count)
        {
        Byte[] bytes = new Byte[count];
        readBytes(bytes);
        return bytes;
        }

    /**
     * Pipe contents from this stream to the specified stream.
     *
     * @param out  the OutputStream to pipe to
     * @param max  the number of bytes to pipe
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void pipeTo(BinaryOutput out, Int count)
        {
        Int    bufSize  = count.minOf(8192);
        Byte[] buf      = new Byte[bufSize];
        while (count > 0)
            {
            Int copySize = bufSize.minOf(count);
            readBytes(buf, 0, copySize);
            out.writeBytes(buf, 0, copySize);
            count -= copySize;
            }
        }

    @Override
    void close()
        {
        }
    }