/**
 * The OutputStream interface represents a output stream of binary data.
 */
interface OutputStream
    {
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
    void writeBytes(Byte[] bytes)
        {
        for (Byte byte : bytes)
            {
            writeByte(byte);
            }
        }

    /**
     * Write the specified number of bytes to the stream from the provided array.
     *
     * @param  bytes   the byte array to read from
     * @param  offset  the offset into the array of the first byte to write
     * @param  count   the number of bytes to write
     */
    void writeBytes(Byte[] bytes, Int offset, Int count)
        {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last)
            {
            writeByte(bytes[offset++]);
            }
        }
    }