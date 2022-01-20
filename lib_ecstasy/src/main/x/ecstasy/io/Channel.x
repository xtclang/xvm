/**
 * Channel provides the ability to read from a data source and/or write to a data destination.
 */
interface Channel
        extends Closeable
    {
    /**
     * True iff the channel can be used to read data.
     */
    @RO Boolean readable;

    /**
     * Read a sequence of bytes from this channel into the specified buffer.
     *
     * This operation will complete once any of the following occurs:
     *   - the buffer has been filled;
     *   - at least the `minBytes` number of bytes has been read;
     *   - the end-of-stream has been reached
     *
     * The buffer position is updated according to the number of bytes read.
     *
     * To make the operation asynchronous, and allow an explicit completion check, use the
     * @Future annotation:
     *
     *     @Future Int bytesRead = channel.read(buffer, headerSize);
     *     while (&bytesRead.completion == FutureVar.Pending)
     *         {
     *         // do something else
     *         ...
     *
     *         // or create an asynchronous continuation
     *         &bytesRead.whenComplete(onComplete);
     *         }
     *
     * @param minBytes  the minimum number of bytes the caller requires to be read before
     *                  the operation completes; passing zero when there are no incomplete
     *                  asynchronous read requests guarantees a non-blocking operation
     *                  (meaning that only already "available" bytes are going to be read)
     *
     * @return the number of bytes read or False if the end-of-stream has been reached
     *
     * @throw IOException if the operation fails to complete due to an unrecoverable IO error
     */
    conditional Int read(WriteBuffer buffer, Int minBytes = Int.maxvalue);

    /**
     * Read a sequence of bytes from this channel into the specified buffers.
     *
     * This operation will complete once any of the following occurs:
     *   - all the buffers have been filled;
     *   - at least the `minBytes` number of bytes has been read;
     *   - the end-of-stream has been reached
     *
     * @return the number of bytes read and the index of the buffer the next byte would be
     *         written into (e.g. if all the buffers are filled, the second return value would be
     *         equal to the buffer array length) or False if the end-of-stream has been reached
     */
    conditional (Int, Int) read(WriteBuffer[] buffers, Int minBytes = Int.maxvalue);

    /**
     * True iff the channel can be used to write data.
     */
    @RO Boolean writable;

    /**
     * Write a sequence of bytes from the specified buffer into this channel.
     *
     * To make the operation asynchronous, and allow an explicit completion check, use the
     * @Future annotation:
     *
     *     @Future Int bytesWritten = channel.write(buffer);
     *     while (&bytesWritten.completion == FutureVar.Pending)
     *         {
     *         // do something else
     *         ...
     *
     *         // or create an asynchronous continuation
     *         &bytesWritten.whenComplete(onComplete);
     *         }
     *
     * @return the number of bytes written
     *
     * @throws IOException if an I/O error occurs
     */
    Int write(ReadBuffer buffer);

    /**
     * Write a sequence of bytes from the specified buffers into this channel into starting at
     * the current channel's position.
     *
     * @return the number of bytes written and the index of the buffer the next byte would be read
     *         from (e.g. if all the buffers are exhausted, the second return value would be equal
     *         to the buffer array length)
     *
     * @throws IOException if an I/O error occurs
     */
    (Int, Int) write(ReadBuffer[] buffers);
    }