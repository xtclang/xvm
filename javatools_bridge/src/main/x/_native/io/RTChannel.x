import ecstasy.io.BufferProvider;
import ecstasy.io.Channel;
import ecstasy.io.EndOfFile;
import ecstasy.io.IOException;
import ecstasy.io.IOClosed;
import ecstasy.io.ReadBuffer;
import ecstasy.io.WriteBuffer;

/**
 * A native `Channel` implementation.
 */
@Concurrent
service RTChannel(RawChannel rawChannel)
        implements Channel
        implements BufferProvider {
    /**
     * The underlying native channel implementation.
     */
    protected RawChannel rawChannel;

    /**
     * A residual amount of data that was previously read from the underlying channel, but has not
     * yet been requested by a client of this channel.
     */
    protected ReadBuffer? leftover;

    /**
     * True iff a read operation is currently processing.
     */
    protected Boolean reading;

    /**
     * A queue of deferred (pending) read operations.
     */
    protected Pending? pendingRead;

    /**
     * True iff a write operation is currently processing.
     */
    protected Boolean writing;

    /**
     * A queue of deferred (pending) write operations.
     */
    protected Pending? pendingWrite;

    /**
     * Set to True when this channel is explicitly closed.
     */
    protected Boolean closed;


    // ----- Channel API ---------------------------------------------------------------------------

    @Override
    @RO BufferProvider buffers.get() {
        return &this.maskAs(BufferProvider);
    }

    @Override
    @RO Boolean readable.get() {
        return !closed && leftover == Null && rawChannel.readable;
    }

    @Override
    @RO Boolean eof.get() {
        return !closed && leftover == Null && rawChannel.eof;
    }

    @Override
    ReadBuffer? read(Origin origin=Incoming) {
        assert !closed as "The Channel was closed before the read operation began";

        // if another read is in progress, queue up this read to run later
        if (!startRead(origin)) {
            @Future ReadBuffer? result;
            addPendingRead(new PendingTakeRead(&result));
            return result;
        }

        try {
            ReadBuffer? previous = leftover;
            if (previous != Null) {
                leftover = Null;
                return previous;
            }

            Byte[]|Int result = rawChannel.take();
            if (result.is(Byte[])) {
                ReadBuffer buf = new RTBuffer(rawChannel, result, result.size, readOnly=True);
                return &buf.maskAs(ReadBuffer);
            }

            switch (result) {
            case -1:
                return Null;
            case -2:
                throw new IOClosed("Input has been shut down");
            case -3:
                throw new IOClosed("The Channel was closed during the read operation");
            default:
                throw new IOException($"Unknown failure ({result})");
            }
        } finally {
            finishRead(origin);
        }
    }

    @Override
    Int read(WriteBuffer buffer, Int minBytes=MaxValue, Origin origin=Incoming) {
        assert !closed as "The Channel was closed before the read operation began";

        // if another read is in progress, queue up this read to run later
        if (!startRead(origin)) {
            @Future Int result;
            addPendingRead(new PendingSingleRead(buffer, minBytes, &result));
            return result;
        }

        try {
            Int copied = 0;
            if (ReadBuffer in ?= read(Delegated)) {
                Int space = buffer.capacity - buffer.offset;
                do {
                    Int max  = in.remaining;
                    Int copy = space.notGreaterThan(max);
                    in.pipeTo(buffer, copy);                // REVIEW: this API doesn't "feel right"
                    copied += copy;
                    space  -= copy;

                    if (copy < max) {
                        // we have leftovers in the read buffer
                        leftover = in;
                        return copied;
                    } else {
                        in.close();
                    }
                } while (copied < minBytes, in ?= read());
            }

            return copied;
        } finally {
            finishRead(origin);
        }
    }

    @Override
    (Int, Int) read(WriteBuffer[] buffers, Int minBytes=MaxValue, Origin origin=Incoming) {
        assert !closed as "The Channel was closed before the read operation began";

        // if another read is in progress, queue up this read to run later
        if (!startRead(origin)) {
            @Future Tuple<Int, Int> result;
            addPendingRead(new PendingMultiRead(buffers, minBytes, &result));
            return result;
        }

        try {
            Int bytesRead  = 0;
            Int nextBuffer = 0;

            for (WriteBuffer buffer : buffers) {
                Int space = buffer.capacity - buffer.offset;
                if (space > 0) {
                    Int request = minBytes.notGreaterThan(space);
                    Int actual  = read(buffer, request, Delegated);
                    if (actual >= space) {
                        // filled this buffer completely
                        ++nextBuffer;
                    }

                    bytesRead += actual;
                    minBytes  -= actual;
                    if (actual < request || minBytes <= 0) {
                        // minBytes have been read, or we hit an EOF
                        break;
                    }
                }
            }

            return bytesRead, nextBuffer;
        } finally {
            finishRead(origin);
        }
    }

    @Override
    @RO Boolean writable.get() {
        return !closed && rawChannel.writable;
    }

    @Override
    Int write(ReadBuffer buffer, Origin origin=Incoming) {
        assert !closed as "The Channel was closed before the write operation began";

        // if another write is in progress, queue up this write to run later
        if (!startWrite(origin)) {
            @Future Int result;
            addPendingWrite(new PendingSingleWrite(buffer, &result));
            return result;
        }

        try {
            Int start = buffer.offset;
            Int end   = buffer.size;
            Int size  = end - start;

            // the most common case is that the passed in buffer belongs to this channel (or comes
            // from the same underlying native provider)
            if (Byte[] bytes := extractBytes(buffer)) {
                try {
                    // check if the buffer contains anything left to write
                    if (size > 0) {
                        Int result = rawChannel.submit(bytes, start, end);
                        switch (result) {
                        case 0:
                            return size;

                        case -1:
                        case -2:
                            throw new IllegalState("Channel closed");

                        default:
                            throw new IllegalState($"Unknown failure ({result})");
                        }
                    }
                } finally {
                    rawChannel.decRefCount(bytes);
                }
            }

            // the buffer is foreign; we'll have to copy any contents out that need to be written.
            // alloc a new buffer and copy the data out, but there is an added complexity because
            // the allocated buffer is likely sized to optimize for the channel implementation, and
            // may be smaller than the passed-in buffer (meaning it will take a number of bites
            // to chew up all of those bytes)
            if (size == 0) {
                // nothing to write
                return 0;
            }

            // let's try to get everything over to the native I/O handler as fast as we can get
            // buffers to carry the data-to-write, so that it can bulk up the writes as much as
            // it can
            @Future Int pendingResult;
            Int remain = size;
            Int total  = 0;
            do {
                Byte[]|Int result = rawChannel.allocate(internal=True);
                if (result.is(Int)) {
                    switch (result) {
                    case -1: throw new OutOfMemory();
                    case -2: throw new IllegalState("Channel closed");
                    default: throw new IllegalState($"Unknown failure ({result})");
                    }
                }

                // note: we are handing a native buffer into some ReadBuffer that we do not trust,
                // which is why the native BufferProvider must always create a new Byte[] reference
                // when it allocates a buffer (and must always invalidate an old Byte[] reference
                // when its reference count drops to zero), so that malicious code cannot hold on to
                // the Byte[] and peek at the data flowing through it over time
                Byte[]  bytes = result;
                Int     copy  = remain.notGreaterThan(bytes.size);
                Boolean last  = copy == remain;
                buffer.readBytes(bytes, 0, copy);

                rawChannel.submit^(bytes, 0, copy).whenComplete((n, e) -> {
                    // release the buffer
                    rawChannel.decRefCount(bytes);

                    if (e != Null) {
                        &pendingResult.completeExceptionally(e);
                    } else {
                        switch (n ?: assert) {
                        case 0:
                            total += copy;
                            if (last) {
                                assert total == size; // REVIEW completeExceptionally???
                                &pendingResult.complete(total);
                            }
                            break;

                        case -1:
                        case -2:
                            &pendingResult.completeExceptionally(new IllegalState("Channel closed"));
                            break;

                        default:
                            &pendingResult.completeExceptionally(new IllegalState($"Unknown failure ({n})"));
                            break;
                        }
                    }
                });

                remain -= copy;
            } while (remain > 0);
            return pendingResult;
        } finally {
            finishWrite(origin);
        }
    }

    @Override
    Int write(ReadBuffer[] buffers, function void(ReadBuffer)? written=Null, Origin origin=Incoming) {
        assert !closed as "The Channel was closed before the write operation began";

        // if another write is in progress, queue up this write to run later
        if (!startWrite(origin)) {
            @Future Int result;
            addPendingWrite(new PendingMultiWrite(buffers, written, &result));
            return result;
        }

        try {
            Int total = 0;
            for (ReadBuffer buffer : buffers) {
                total += write(buffer, origin=Delegated);
                written?(buffer);
            }
            return total;
        } finally {
            finishWrite(origin);
        }
    }


    // ----- BufferProvider API --------------------------------------------------------------------

    @Override
    conditional WriteBuffer next() {
        Byte[]|Int result = rawChannel.allocate(internal=False);
        if (result.is(Byte[])) {
            WriteBuffer buf = new RTBuffer(rawChannel, result, 0, readOnly=False);
            return True, &buf.maskAs(WriteBuffer);
        }

        switch (result) {
        case -1:
            return False;
        case -2:
            throw new IllegalState("BufferProvider closed");
        default:
            throw new IllegalState($"Unknown failure ({result})");
        }
    }

    @Override
    conditional Int capacityLimit() {
        Int n = rawChannel.capacityLimit;
        return n != 0, n;
    }

    @Override
    conditional Int fixedBufferSize() {
        Int n = rawChannel.fixedBufferSize;
        return n != 0, n;
    }

    @Override
    @RO Int totalBuffers.get() {
        return rawChannel.totalBuffers;
    }

    @Override
    @RO Int totalBytes.get() {
        return rawChannel.totalBytes;
    }

    @Override
    @RO Int consumedBuffers.get() {
        return rawChannel.consumedBuffers;
    }

    @Override
    @RO Int consumedBytes.get() {
        return rawChannel.consumedBytes;
    }


    // ----- Closeable API -------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null) {
        if (!closed) {
            rawChannel.close();
            closed = True;
        }
    }

    @Override
    String toString() {
        return "Channel";
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain the raw bytes of the buffer, if it happens to be an `RTBuffer`.
     *
     * @param buffer the `ReadBuffer` or `WriteBuffer` to examine
     *
     * @return True iff the buffer is an `RTBuffer`
     * @return (conditional) the raw bytes of the buffer
     */
    private conditional Byte[] extractBytes(ReadBuffer buffer) {
        if (val rtBuffer := &buffer.revealAs((private RTBuffer))) {
            assert rtBuffer.capacity > 0 as "Buffer closed";
            return True, rtBuffer.rawBytes;
        }

        return False;
    }

    /**
     * The origin of a read or a write call:
     *
     * * `Incoming` means that the call is coming from a client of (a caller to) the channel.
     * * `Delegated` means that the call is coming from another method in the channel, and it is
     *   delegating some work to the method being called (so the operation was already approved).
     * * `Deferred` means that the work was queued and is now approved to run.
     */
    private enum Origin {Incoming, Delegated, Deferred}

    /**
     * Check if a read operation can execute immediately.
     *
     * @param origin  the origin of the read call
     *
     * @return True iff the read operation should execute immediately; False indicates that the
     *         operation should be transformed into a pending operation and queued for later
     */
    private Boolean startRead(Origin origin) {
        if (origin == Incoming) {
            if (reading) {
                return False;
            }

            assert pendingRead == Null;
        }

        reading = True;
        return True;
    }

    /**
     * Indicate that a read operation is completing, and kick of the next pending read operation,
     * if any.
     *
     * @param origin  the origin of the read call that just finished
     */
    private void finishRead(Origin origin) {
        assert reading;
        if (origin != Delegated) {
            if (val alreadyPending ?= pendingRead) {
                callLater(alreadyPending.run);
            } else {
                reading = False;
            }
        }
    }

    /**
     * Add a pending read operation to the end of the queue of pending read operations.
     *
     * @param newPending  the pending read operation to add
     */
    private void addPendingRead(Pending newPending) {
        assert reading;
        if (val alreadyPending ?= pendingRead) {
            alreadyPending.add(newPending);
        } else {
            pendingRead = newPending;
        }
    }

    /**
     * Check if a write operation can execute immediately.
     *
     * @param origin  the origin of the write call
     *
     * @return True iff the write operation should execute immediately; False indicates that the
     *         operation should be transformed into a pending operation and queued for later
     */
    private Boolean startWrite(Origin origin) {
        if (origin == Incoming) {
            if (writing) {
                return False;
            }

            assert pendingWrite == Null;
        }

        writing = True;
        return True;
    }

    /**
     * Indicate that a write operation is completing, and kick of the next pending write operation,
     * if any.
     *
     * @param origin  the origin of the write call that just finished
     */
    private void finishWrite(Origin origin) {
        assert writing;
        if (origin != Delegated) {
            if (val alreadyPending ?= pendingWrite) {
                callLater(alreadyPending.run);
            } else {
                writing = False;
            }
        }
    }

    /**
     * Add a pending write operation to the end of the queue of pending write operations.
     *
     * @param newPending  the pending write operation to add
     */
    private void addPendingWrite(Pending newPending) {
        assert writing;
        if (val alreadyPending ?= pendingWrite) {
            alreadyPending.add(newPending);
        } else {
            pendingWrite = newPending;
        }
    }

    /**
     * Represents a queued up read or write operation.
     */
    private class Pending {
        /**
         * The next operation in the queue, if any.
         */
        private Pending? next;

        /**
         * Execute the deferred read or write operation.
         */
        void run() {
            try {
                process();
            } finally {
                // determine if this previously-pending operation was a read or a write
                Boolean readOp  = &pendingRead  == &this;
                Boolean writeOp = &pendingWrite == &this;
                assert readOp ^ writeOp;

                // advance the queue head past this previously-pending operation
                if (readOp) {
                    pendingRead = next;
                    finishRead(Deferred);
                } else {
                    pendingWrite = next;
                    finishWrite(Deferred);
                }
            }
        }

        /**
         * Each type of pending operation needs to implement this method to actually perform the
         * pending operation.
         */
        protected @Abstract void process();

        /**
         * Queue up another pending read or write operation.
         */
        void add(Pending next) {
            if (val alreadyPending ?= this.next) {
                alreadyPending.add(next);
            } else {
                this.next = next;
            }
        }
    }

    /**
     * A pending call to `ReadBuffer? read()`.
     */
    private class PendingTakeRead(Future<ReadBuffer?> pendingResult)
            extends Pending {
        @Override
        protected void process() {
            // REVIEW mf: completeLambda(() -> read(origin=Deferred));
            try {
                pendingResult.complete(read(origin=Deferred));
            } catch (Exception e) {
                pendingResult.completeExceptionally(e);
            }
        }
    }

    /**
     * A pending call to `Int read(WriteBuffer buffer, Int minBytes)`.
     */
    private class PendingSingleRead(WriteBuffer buffer, Int minBytes, Future<Int> pendingResult)
            extends Pending {
        @Override
        protected void process() {
            try {
                pendingResult.complete(read(buffer, minBytes, origin=Deferred));
            } catch (Exception e) {
                pendingResult.completeExceptionally(e);
            }
        }
    }

    /**
     * A pending call to `(Int, Int) read(WriteBuffer[] buffers, Int minBytes)`.
     */
    private class PendingMultiRead(WriteBuffer[] buffers, Int minBytes, Future<Tuple<Int,Int>> pendingResult)
            extends Pending {
        @Override
        protected void process() {
            try {
                pendingResult.complete(read(buffers, minBytes, origin=Deferred));
            } catch (Exception e) {
                pendingResult.completeExceptionally(e);
            }
        }
    }

    /**
     * A pending call to `Int write(ReadBuffer buffer)`.
     */
    private class PendingSingleWrite(ReadBuffer buffer, Future<Int> pendingResult)
            extends Pending {
        @Override
        protected void process() {
            try {
                pendingResult.complete(write(buffer, origin=Deferred));
            } catch (Exception e) {
                pendingResult.completeExceptionally(e);
            }
        }
    }

    /**
     * A pending call to `Int write(ReadBuffer[] buffers, function void(ReadBuffer)? written)`.
     */
    private class PendingMultiWrite(ReadBuffer[] buffers, function void(ReadBuffer)? written, Future<Int> pendingResult)
            extends Pending {
        @Override
        protected void process() {
            try {
                pendingResult.complete(write(buffers, written, origin=Deferred));
            } catch (Exception e) {
                pendingResult.completeExceptionally(e);
            }
        }
    }
}