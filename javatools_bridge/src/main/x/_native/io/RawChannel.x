/**
 * A representation of a native read/write channel in the runtime.
 *
 * For reads, the conceptual model is simple: There is a native queue of zero or more buffers, with
 * an I/O service thread being the sole appender to the queue, and this object being the sole taker
 * from the queue (via the `take()` method).
 *
 * For writes, the conceptual model is similarly simple: There is a native queue of zero or more
 * buffers pending being written out, with an I/O service thread being the sole taker from the
 * queue, and this object being the sole appender to the queue (via the `submit()` method).
 */
service RawChannel
    {
    // ----- generic config ------------------------------------------------------------------------

    /**
     * Generic configuration property support: Read a property value.
     *
     * This is not an externally-facing capability: The property names and the structure of the
     * corresponding bytes must be perfect, and are defined solely by the native implementation.
     *
     * @param property  the name of the property
     *
     * @return the bytes that represent the value of the property
     */
    Byte[] getConfig(String property)
        {TODO("Native");}

    /**
     * Generic configuration property support: Modify a property value.
     *
     * This is not an externally-facing capability: The property names and the structure of the
     * corresponding bytes must be perfect, and are defined solely by the native implementation.
     *
     * @param property  the name of the property
     * @param config    the bytes that represent the value of the property
     */
    void setConfig(String property, Byte[] config)
        {TODO("Native");}


    // ----- read operations -----------------------------------------------------------------------

    /**
     * `True` iff the channel can be used to attempt to read data; `False` indicates that the
     * `Channel` has reached EOF, is writable-only, or has been closed.
     *
     * Note: A `True` value does **not** indicate that data is ready to be read, i.e. that a read
     * is guaranteed to complete without blocking.
     */
    @RO Boolean readable.get()
        {TODO("Native");}

    /**
     * `True` iff the channel has reached its end-of-file / end-of-stream condition.
     */
    @RO Boolean eof.get()
        {TODO("Native");}

    /**
     * Wait for the next buffer of data to read to become available. The returned buffer, if any,
     * must be passed to the [release] method when it is no longer being used.
     *
     * @return  a buffer containing bytes to read; otherwise an integer value indicating a status
     *          code, `-1` for EOF, `-2` for `inputShutdown`, `-3` for `closed`, and positive values
     *          indicating error codes defined by the specific channel type
     */
    Byte[]|Int take()
        {TODO("Native");}


    // ----- write operations ----------------------------------------------------------------------

    /**
     * Indicates that the [shutdownWrite] method has **not** been called and that the channel is
     * **not** known to have refused write operations. It is _possible_ that this evaluates to
     * `True`, yet an attempt to write would still return a status error.
     */
    @RO Boolean writable.get()
        {TODO("Native");}

    /**
     * Submit a buffer to write that was previously obtained from the [allocate] method. This method
     * does not wait for the write to occur, or for any other reason.
     *
     * @param buffer  a buffer containing bytes to write
     * @param start   the index into the buffer of the first byte (inclusive) to write
     * @param end     the index into the buffer of the last byte (exclusive) to write
     *
     * @return a status of `0` for ok, `-1` indicating the channel is closed, `-2` indicating that
     *         the channel parent (e.g. file or socket) is closed, and positive values indicating
     *         error codes defined by the specific native implementation
     */
    Int submit(Byte[] buffer, Int start, Int end)
        {TODO("Native");}


    // ----- buffer operations ---------------------------------------------------------------------

    /**
     * Allocate a buffer to support write operations. This method can block.
     *
     * The returned buffer, if any, must be passed to the [release] method if it is abandoned
     * without being submitted to the write queue via [submit].
     *
     * @param internal  pass `True` to indicate that the buffer is not being exposed to user code
     *
     * @return  a buffer that the caller can write to; otherwise an integer value indicating a
     *          status code, `-1` for capacity limit, `-2` for `closed`
     */
    Byte[]|Int allocate(Boolean internal)
        {TODO("Native");}

    /**
     * When a native buffer gets referenced from more than one place, its reference count is
     * increased such that an additional `release()` call is required.
     *
     * @param buffer  the native buffer that is now being referenced from another object
     */
    void incRefCount(Byte[] buffer)
        {TODO("Native");}

    /**
     * Release a buffer previously obtained from the [take] or [allocate] methods, or from the
     * [RTBuffer.toReadBuffer] or [RTBuffer.transfer] methods. When the reference count drop to
     * zero, it is reclaimed.
     *
     * @param buffer  the native buffer that is no longer being used
     */
    void decRefCount(Byte[] buffer)
        {TODO("Native");}

    @RO Int capacityLimit.get()
        {TODO("Native");}

    @RO Int fixedBufferSize.get()
        {TODO("Native");}

    @RO Int totalBuffers.get()
        {TODO("Native");}

    @RO Int totalBytes.get()
        {TODO("Native");}

    @RO Int consumedBuffers.get()
        {TODO("Native");}

    @RO Int consumedBytes.get()
        {TODO("Native");}


    // ----- lifecycle operations ------------------------------------------------------------------

    /**
     * Close the RawChannel, which prevents further operations over the channel.
     */
    void close()
        {TODO("Native");}

    /**
     * True iff the `RawChannel` is closed.
     * REVIEW is this necessary?
     */
    @RO Boolean closed.get()
        {TODO("Native");}
    }