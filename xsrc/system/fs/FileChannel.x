import io.Buffer;

/**
 * FileChannel provides the ability to read, write, and manipulate a file.
 */
interface FileChannel
        extends Closeable
    {
    /**
     * The size of the channel's file. Reducing this value will truncate the file accordingly.
     */
    Int size;

    /**
     * The number of bytes from the beginning of the file to the current file position.
     */
    Int position;

    /**
     * The number of bytes available for reading or writing without growing the size.
     */
    @RO Int remaining.get()
        {
        return size - position;
        }

    /**
     * Read a sequence of bytes from this channel into the specified buffer starting at the
     * channel's current position.
     *
     * This operation will complete once the minimum of the buffer's `remaining` value and the
     * channel's `remaining` value has been read.
     *
     * The buffer and channel positions are updated according to the number of bytes read.
     *
     * To make the operation asynchronous, and allow an explicit completion check, use the
     * @Future annotation:
     *
     *     @Future void result = channel.read(buffer);
     *     while (&result.completion == FutureVar.Pending)
     *         {
     *         // do something else
     *         ...
     *
     *         // or create an asynchronous continuation
     *         &result.whenComplete(onComplete);
     *         }
     *
     * @throw IOException if the operation fails to complete due to an unrecoverable IO error
     */
    void read(Buffer<Byte> buffer);

    /**
     * Read a sequence of bytes from this channel into the specified buffers starting at the
     * channel's current position.
     *
     * This operation will complete once the minimum of the combined buffer's `remaining` values
     * and the channel's `remaining` value has been read.
     *
     * The buffers and channel positions are updated according to the number of bytes read.
     *
     * @return the index of the buffer the next byte would be written into (e.g. if all the buffers
     *         are filled, the return value would be equal to the buffer array length)
     *
     * @throw IOException if the operation fails to complete due to an unrecoverable IO error
     */
    Int read(Buffer<Byte>[] buffers);

    /**
     * Write a sequence of bytes from the specified buffer into this channel at the channel's
     * current position.
     *
     * The buffer and channel positions are updated according to the number of bytes written.
     *
     * To make the operation asynchronous, and allow an explicit completion check, use the
     * @Future annotation:
     *
     *     @Future void result = channel.write(buffer);
     *     while (&result.completion == FutureVar.Pending)
     *         {
     *         // do something else
     *         ...
     *
     *         // or create an asynchronous continuation
     *         &result.whenComplete(onComplete);
     *         }
     *
     * @throw IOException if the operation fails to complete due to an unrecoverable IO error
     */
    void write(Buffer<Byte> buffer);

    /**
     * Write a sequence of bytes from the specified buffers into this channel starting at the
     * channel's current position.
     *
     * The buffers and channel positions are updated according to the number of bytes written.
     *
     * @throw IOException if the operation fails to complete due to an unrecoverable IO error
     */
    void write(Buffer<Byte>[] buffers);

    /**
     * Ensure all the changes are written to the underlying storage medium.
     */
    void flush();

    // TODO: modes and attributes...
    }