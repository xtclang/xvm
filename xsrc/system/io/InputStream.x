/**
 * The InputStream interface represents an input stream of binary data.
 */
interface InputStream
    {
    /**
     * @return  a value of type Byte read from the stream
     */
    Byte readByte();

    /**
     * Read bytes into the provided array.
     *
     * @param  bytes  the byte array to read into
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
     * Skip forward in the stream over the specified number of bytes.
     *
     * @param count  the number of bytes to skip over
     */
    void skip(Int count)
        {
        while (count-- > 0)
            {
            readByte();
            }
        }

    /**
     * Obtain a token that can later be used to restore the stream to the current position.
     *
     * @return an object that can later be used to restore the current location within the stream
     *
     * @throws UnsupportedOperation  if the stream cannot later restore the current position
     */
    immutable Const mark()
        {
        TODO
        }

    /**
     * Restore the stream to the position represented by the provided `mark`.
     *
     * @param mark  a value previously returned by the `mark()` method on this stream
     */
    void restore(immutable Const mark)
        {
        TODO
        }
    }