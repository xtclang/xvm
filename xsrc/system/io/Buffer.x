/**
 * Buffer represents a transferable holder for a uniform array of immutable data elements.
 */
interface Buffer<DataType extends Const>
        extends collections.UniformIndexed<Int, DataType>
    {
    /**
     * Specifies if the buffer is writable.
     */
    @RO Boolean readOnly;

    /**
     * The buffer's maximum capacity.
     */
    @RO Int capacity;

    /**
     * The number of readable or writable elements in the buffer. It cannot be set to a value
     * greater than the capacity.
     */
    Int limit;

    /**
     * The index of the first element to be read or written. This value can never go beyond the
     * limit.
     */
    Int position;

    /**
     * The buffer's mark.
     */
    Int mark;

    /**
     * The number of elements between the limit and the position.
     */
    @RO Int remaining.get()
        {
        return limit - position;
        }

    /**
     * Read the element at this buffer's position, and then increments the position.
     *
     * @return the element at the buffer's position
     *
     * @throws BufferException if the buffer's position at its limit
     */
    DataType get();

    /**
     * Read the element at the specified position.
     *
     * @return the element at the specified position
     *
     * @throws BufferException if the specified position is beyond the buffer's limit
     */
    @Override @Op("getElement")
    DataType getElement(Int ix);

    /**
     * Write the specified element into this buffer at the position, and then increments the
     * position.
     *
     * @throws BufferException if the buffer's position is at its limit or this buffer is read-only
     */
    Void put(DataType el);

    /**
     * Write the specified element into this buffer at the specified position.
     *
     * @throws BufferException if the specified position is beyond the buffer's limit or this buffer
     *                         is read-only
     */
    @Override @Op("setElement")
    Void put(Int ix, DataType el);

    /**
     * Clear this buffer. This sets the position to zero, the limit to the capacity,
     * and discards the mark.
     */
    Buffer clear();

    /**
     * Rewind this buffer. This sets the position to zero and discards the mark.
     */
    Buffer rewind();

    /**
     * Flip this buffer. This sets the limit to the position, position to zero and discards the mark.
     */
    Buffer flip();
    }