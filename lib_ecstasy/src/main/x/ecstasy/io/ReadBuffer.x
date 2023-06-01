/**
 * A ReadBuffer represents a _transferable_ holder of binary data. Its contents are read-only, but
 * its state includes a current offset, which is mutable.
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
     * Skip over the specified number of bytes. This modifies the [offset] of the ReadBuffer.
     *
     * @param count  a value in the range `(0 <= count <= size - offset)` that indicates the number
     *               of elements to skip over
     *
     * @return this buffer
     */
    ReadBuffer skip(Int count) {
        assert:bounds 0 <= count <= size - offset;
        offset += count;
        return this;
    }

    /**
     * Rewind this buffer. This sets the buffer offset to zero.
     *
     * @return this buffer
     */
    ReadBuffer rewind() {
        offset = 0;
        return this;
    }

    /**
     * Move the current position to the specified offset.
     *
     * @param newOffset  a value in the range `(0 <= newOffset <= size)` that specifies the new
     *                   offset for this ReadBuffer
     *
     * @return this buffer
     */
    ReadBuffer moveTo(Int newOffset) {
        assert:bounds 0 <= newOffset <= size;
        offset = newOffset;
        return this;
    }

    /**
     * Release the buffer. After this method is invoked, the ReadBuffer is no longer valid, and must
     * not be used; any attempt to subsequently use the buffer may result in an exception.
     */
    @Override
    void close(Exception? cause = Null);
}
