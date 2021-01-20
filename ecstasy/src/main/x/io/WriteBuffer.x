/**
 * A WriteBuffer represents the formation of a _transferable_ holder of binary data. Its contents
 * are read/write, until the buffer is _flipped_ into a ReadBuffer, at which point the WriteBuffer
 * is no longer usable.
 *
 * In addition to binary stream-based data access, the WriteBuffer also allows random access to data
 * within the buffer.
 *
 * Conceptually, a buffer provides mutual exclusion, in terms of its ownership: At any point, at
 * most one service should be writing to and/or reading from a buffer. Indeed, that is the very
 * purpose for a buffer to exist: It holds a deposit from a writer until that deposit can be
 * extracted by a reader. With respect to efficiency, it is extremely important that a region of
 * memory not be modified by more than one actor (thread, CPU core, bus device) at a time (i.e.
 * within some period of time that any one actor is modifying the memory), because doing so will
 * cause CPU cache flushes, CPU cache invalidation, memory bus contention, and so on, and may even
 * allow for unpredictable outcomes due to concurrent updates. Furthermore, if any actor is
 * modifying a region of memory, no other actor should be reading from that region of memory, or the
 * read performance will drop dramatically. (Conceptually, multiple actors can read from a region of
 * memory concurrently without contention; it is the presence of any writer that mandates the mutual
 * exclusion principle.)
 *
 * In Ecstasy, a buffer is modeled as if it were a service, which means that it _conceptually_
 * behaves independently of (and asynchronously with respect to) any and all readers and writers.
 * However, it is expected that any intrinsic implementations of I/O buffers will be synchronous,
 * and that the buffer data will be treated as if it were located within the memory space of the
 * service writing to and/or reading from the buffer. In other words, while the buffer implies a
 * mutual exclusion contract, an intrinsic implementation may restructure the manner in which the
 * contract is enforced while a buffer is actually being used by a particular service. The primary
 * reason for this is to "lift" the validity and range checks from inside of hot I/O loops, while
 * simultaneously allowing buffers to be re-used repeatedly, while .
 */
interface WriteBuffer
        extends ReadBuffer
        extends OutputStream
    {
    /**
     * The buffer's capacity, in terms of the number of bytes that it can hold.
     */
    @RO Int capacity;

    /**
     * Write the specified element into this buffer at the specified position. This method allows
     * random write access into the buffer.
     *
     * @param index  the index of the element
     * @param value  the element value to store
     *
     * @throws BufferException if the specified position is beyond the buffer's limit or this buffer
     *                         is read-only
     */
    @Op("[]=") void setByte(Int index, Byte value);

    /**
     * Convert the contents of the buffer into a ReadBuffer. After this method is invoked, the
     * WriteBuffer is no longer valid, and must not be used; the only operation allowed after
     * `toReadBuffer()` is `close()`.
     *
     * The results of this method is a ReadBuffer with a size equal to this WriteBuffer's size, and
     * an offset of `0`.
     *
     * @return a ReadBuffer that contains the data that was written into this WriteBuffer
     */
    ReadBuffer toReadBuffer();

    /**
     * Seal the WriteBuffer. After this method is invoked, the buffer can not longer be modified
     * nor can be it be read from; the only operation allowed to follow `close()` is the
     * `toReadBuffer()` method, which is permitted at most once for the life of the WriteBuffer.
     * Any other attempt to subsequently use the buffer may result in an exception.
     */
    @Override
    void close(Exception? cause = Null);
    }