/**
 * A ReadBuffer represents a _transferable_ holder of binary data. Its contents are read-only, but
 * its state includes a current offset, which is mutable.
 *
 * The purpose of the ReadBuffer is to represent a chunk of raw data inside of a service; as such,
 * this API is always assumed to be "service local", i.e. not requiring crossing a service boundary.
 * Basically, this is a wrapper around a `Byte[]`, but without exposing any references to that
 * `Byte[]` so that after a call to [close()], that `Byte[]` can be re-used by another [WriteBuffer].
 *
 * In addition to binary stream-based data access, the ReadBuffer also allows random access to data
 * within the buffer.
 */
interface ReadBuffer
        extends InputStream {
    /**
     * Read the byte at the specified position. This method allows random read access into the
     * buffer. This method does not alter the current buffer offset.
     *
     * @param index  the index (a 0-based offset) of the element
     *
     * @return the byte value at the specified index
     *
     * @throws OutOfBounds  if the `(index < 0 || index >= size)`
     */
    @Op("[]") Byte getByte(Int index);

    /**
     * Read up to the specified number of bytes into the provided array.
     *
     * @param  bytes   the byte array to read into
     * @param  offset  the offset into the array to store the first byte read
     * @param  count   the maximum number of bytes to read
     *
     * @return the actual number of bytes read; if this value is less than `count`, then the end of
     *         buffer has been reached
     *
     * @throws IOException  represents the general category of input/output exceptions
     */
    Int readBytes(Byte[] bytes, Int offset, Int count);

    /**
     * Skip over the specified number of bytes. This modifies the [offset] of the `ReadBuffer`.
     *
     * @param count  a value in the range `(0 <= count <= size - offset)` that indicates the number
     *               of elements to skip over
     *
     * @return this `ReadBuffer`
     */
    ReadBuffer skip(Int count) {
        assert:bounds 0 <= count <= size - offset;
        offset += count;
        return this;
    }

    /**
     * Rewind this `ReadBuffer`. This sets the buffer [offset] to zero.
     *
     * @return this `ReadBuffer`
     */
    ReadBuffer rewind() {
        offset = 0;
        return this;
    }

    /**
     * Set the current [offset] to the specified `newOffset`.
     *
     * @param newOffset  a value in the range `(0 <= newOffset <= size)` that specifies the new
     *                   [offset] for this `ReadBuffer`
     *
     * @return this `ReadBuffer`
     */
    ReadBuffer moveTo(Int newOffset) {
        assert:bounds 0 <= newOffset <= size;
        offset = newOffset;
        return this;
    }

    /**
     * Release the buffer. After this method is invoked, the `ReadBuffer` is no longer valid, and
     * must not be used; any attempt to subsequently use the buffer may result in an exception.
     */
    @Override
    void close(Exception? cause = Null);

    /**
     * An empty `ReadBuffer`.
     */
    static ReadBuffer Empty = new ReadBuffer() {
        @Override
        Byte getByte(Int index) = throw new EndOfFile();

        @Override
        Int readBytes(Byte[] bytes, Int offset, Int count) = 0;

        @Override
        @RO Int offset.get() = 0;

        @Override
        @RO Int size.get() = 0;

        @Override
        @RO Int available.get() = 0;

        @Override
        Byte readByte() = throw new EndOfFile();
    }.makeImmutable();
}
