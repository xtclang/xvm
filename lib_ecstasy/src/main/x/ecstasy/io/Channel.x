/**
 * A `Channel` provides the ability to read from a data source and/or write to a data destination.
 *
 * An important aspect of the `Channel`, which is a complicating factor in its design, is that it is
 * intended to provide support for non-blocking I/O operations. Specifically, the interface is
 * designed such that methods that might naturally block on I/O internally could instead return a
 * future, if the caller intends to perform the operation in a non-blocking manner.
 *
 * All information that is transmitted and received via a `Channel` is transferred through the API
 * in _buffers_. To write to the channel, a [ReadBuffer] is passed to the channel, with its contents
 * being the data to write. Similarly, to read from the channel, a [WriteBuffer] is passed to the
 * channel, and the channel will deposit contents into the buffer. While any `Channel` can work with
 * any `ReadBuffer` or `WriteBuffer` implementation from any [BufferProvider], each channel also
 * provides access to its own internal `BufferProvider`. It is _possible_ that using the channel's
 * own `BufferProvider` may eliminate an extra buffer copy; this approach is known as "zero copy
 * I/O", which is a misnomer -- zero-copy I/O is actually "one less copy I/O". Note that the
 * channel's own `BufferProvider` is likely limited in terms of its capacity; this is a natural
 * consequence of the possibility that the channel's own buffers represent a fixed resource, such
 * as the underlying process' or operating system's own buffers.
 */
interface Channel
        extends Closeable
    {
    /**
     * The Channel's own [BufferProvider] that is intrinsic to the `Channel`. A caller can choose to
     * use this `BufferProvider`, and there may be efficiency benefits in doing so, but the caller
     * should assume that the Channel's `BufferProvider` is severely capacity-limited.
     */
    @RO BufferProvider buffers;

    /**
     * `True` iff the channel can be used to attempt to read data; `False` indicates that the
     * `Channel` has reached EOF, is writable-only, has encountered a fatal read error, or has been
     * closed.
     *
     * Note: A `True` value does **not** indicate that data is ready to be read, i.e. that a read
     * is guaranteed to complete without blocking.
     */
    @RO Boolean readable;

    /**
     * `True` iff the channel has reached its EOF condition ("end of file", or "end of stream").
     *
     * This property is provided as a convenience; the state of EOF can also be determined by
     * using any of the `read` methods.
     */
    @RO Boolean eof;

    /**
     * Read a chunk via the channel, blocking until data is available.
     *
     * To make the operation non-blocking, use the `@Future` annotation; for example:
     *
     *     @Future ReadBuffer? buf = channel.read();
     *     &buf.thenDo(() -> { ... });
     *
     * The resulting buffer is allocated from the channel's [BufferProvider], and that allocation
     * may block if a buffer is currently not available, or may throw an exception if this channel
     * currently has allocated too many buffers without releasing them.
     *
     * This method allows data from a channel to be consumed without an additional copy, but the
     * requirement for achieving this reduction in copying is that the consumer must use and then
     * `close()` the returned buffer, so that the channel can re-use it. Attempting to collect more
     * than two un-closed buffers from the channel's [BufferProvider] **may** result in an
     * exception. In other words, the channel's `BufferProvider` should be assumed to be a strictly
     * constrained resource, perhaps representing the underlying operating system's own fixed set of
     * buffers.
     *
     * @return the next [ReadBuffer] from the channel, or `Null` if EOF has been reached
     *
     * @throws IllegalState  if the channel has been closed, or is not readable
     * @throws IOException   if the operation fails to complete due to an unrecoverable IO error
     * @throws IOClosed      if the Channel is closed before the operation completes
     * @throws TimedOut      it is expected that a time-out could occur while a read is being
     *                       awaited, if a [Timeout] exists
     */
    ReadBuffer? read();

    /**
     * Read bytes from this channel into the specified buffer, blocking if necessary.
     *
     * This operation will complete once any of the following occurs:
     *   - the passed buffer has been filled;
     *   - at least the `minBytes` number of bytes has been read, although additional bytes may be
     *     read into the buffer if that can be done without additional I/O blocking;
     *   - the end-of-stream (aka "EOF") has been reached.
     *
     * The buffer position is updated according to the number of bytes read by this operation, and
     * that number of bytes read is also returned.
     *
     * To make the operation non-blocking, use the `@Future` annotation; for example:
     *
     *     @Future Int bytesRead = channel.read(buffer, headerSize);
     *     &bytesRead.thenDo(() -> { ... });
     *
     * @param minBytes  the minimum number of bytes the caller requires to be read before
     *                  the operation completes; passing zero when there are no incomplete
     *                  asynchronous read requests results in a non-blocking operation
     *                  (meaning that only already-"available" bytes are going to be read)
     *
     * @return the number of bytes read, or `0` if EOF has been reached; note that `0` can also be
     *         returned if `minBytes==0`, and in that case the `0` does not imply EOF
     *
     * @throws IllegalState  if the channel has been closed, or is not readable
     * @throws IOException   if the operation fails to complete due to an unrecoverable IO error
     * @throws IOClosed      if the Channel is closed before the operation completes
     * @throws TimedOut      it is expected that a time-out could occur while a read is being
     *                       awaited, if a [Timeout] exists
     */
    Int read(WriteBuffer buffer, Int minBytes = Int.maxvalue)
        {
        return read([buffer], minBytes);
        }

    /**
     * Read a sequence of bytes from this channel into the specified buffers.
     *
     * This operation will complete without exception once any of the following occurs:
     *   - all the buffers have been filled;
     *   - at least the `minBytes` number of bytes has been read;
     *   - the end-of-stream has been reached
     *
     * @param minBytes  the minimum number of bytes the caller requires to be read before
     *                  the operation completes; passing zero when there are no incomplete
     *                  asynchronous read requests results in a non-blocking operation
     *                  (meaning that only already-"available" bytes are going to be read)
     *
     * @return the number of bytes read, or `0` if EOF has been reached; note that `0` can also be
     *         returned if `minBytes==0`, and in that case the `0` does not imply EOF
     * @return the index of the buffer the next byte would be written into; if all the passed
     *         buffers have been filled, this will be equal to the buffer array length
     *
     * @throws IllegalState  if the channel has been closed, or is not readable
     * @throws IOException   if the operation fails to complete due to an unrecoverable IO error
     * @throws IOClosed      if the Channel is closed before the operation completes
     * @throws TimedOut      it is expected that a time-out could occur while a read is being
     *                       awaited, if a [Timeout] exists
     */
    (Int bytesRead, Int nextBuffer) read(WriteBuffer[] buffers, Int minBytes = Int.maxvalue);

    /**
     * `True` iff the channel can be used to attempt to write data; `False` indicates that the
     * `Channel` has reached its limit, is readable-only, has encountered a fatal write error, or
     * has been closed.
     *
     * Note: A `True` value does **not** indicate that a write can be performed without blocking.
     */
    @RO Boolean writable;

    /**
     * Write a sequence of bytes from the specified buffer into this channel.
     *
     * To make the operation non-blocking, use the `@Future` annotation; for example:
     *
     *     @Future Int bytesWritten = channel.write(buffer);
     *     &bytesWritten.thenDo(() -> { ... });
     *
     * @param buffer  a `ReadBuffer` containing data to write to the channel
     *
     * @return the number of bytes written
     *
     * @throws IllegalState  if the channel has been closed, or is not writable
     * @throws IOException   if the operation fails to complete due to an unrecoverable IO error
     * @throws IOClosed      if the Channel is closed before the operation completes
     * @throws TimedOut      it is expected that a time-out could occur while a write completion is
     *                       being awaited, if a [Timeout] exists
     */
    Int write(ReadBuffer buffer)
        {
        return write([buffer]);
        }

    /**
     * Write a sequence of bytes from the specified buffers into this channel into starting at
     * the current channel's position.
     *
     * To make the operation non-blocking, use the `@Future` annotation; for example:
     *
     *     @Future Tuple<Int, Int> result = channel.write(buffers);
     *     &result.thenDo(() -> { ... });
     *
     * @param buffers  zero or more `ReadBuffer` objects containing data to write to the channel
     * @param written  a function called with each buffer as that buffer has been successfully
     *                 written (and thus the buffer is no longer being used by this operation)
     *
     * @return the number of bytes written
     *
     * @throws IllegalState  if the channel has been closed, or is not writable
     * @throws IOException   if the operation fails to complete due to an unrecoverable IO error
     * @throws IOClosed      if the Channel is closed before the operation completes
     * @throws TimedOut      it is expected that a time-out could occur while a write completion is
     *                       being awaited, if a [Timeout] exists
     */
    Int write(ReadBuffer[] buffers, function void(ReadBuffer)? written=Null)
        {
        // either this implementation or the implementation of the write(ReadBuffer) method must be
        // overridden, or infinite recursion will result; this implementation is intended to convey
        // the expected behavior for this method, assuming that the other method is re-implemented
        switch (buffers.size)
            {
            case 0:
                return 0;

            case 1:
                ReadBuffer buffer = buffers[0];
                // TODO GG?
//                return written == Null
//                        ? write(buffer)
//                        : write^(buffer).thenDo(() -> {written(buffer);});
                if (written == Null)
                    {
                    return write(buffer);
                    }

                @Future Int result = write(buffer);
                &result.thenDo(() -> written(buffer));
                return result;

            default:
                private void accumulate(ReadBuffer[] buffers, Int index, Int part, Int sum, Future<Int> pending)
                    {
                    sum += part;
                    if (++index < buffers.size)
                        {
                        write^(buffers[index]).passTo(accumulate(buffers, index, _, sum, pending));
                        }
                    else
                        {
                        pending.complete(sum);
                        }
                    }

                @Future Int finalResult;
                write^(buffers[0]).passTo(accumulate(buffers, 0, _, 0, &finalResult));
                return finalResult;
            }
        }
    }