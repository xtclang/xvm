/**
 * TransferableBuffer represents a transferable holder for an array of bytes. As such, it is
 * implemented as a service, so that it can be reached from different services. The objects that
 * are actually crossing the service boundaries are the const inner classes ReadBuffer and
 * WriteBuffer, which have no mutable state of their own, but which delegate their operations to a
 * hidden implementation class, [ActualBuffer], that is part of (hidden inside of) the service.
 *
 * There is an exclusive access contract for the buffer; it is enforced by strictly invalidating a
 * buffer when either its lifecycle reaches closure, or when a new ReadBuffer or WriteBuffer is
 * created (since the new instance would then have exclusive access). This prevents an old reference
 * (to either a ReadBuffer or a WriteBuffer) to be held and later used to access the data in the
 * underlying buffer, which would represent a security risk. Additionally, the exclusive access is
 * _from_ the context of a particular service; in other words, when a ReadBuffer or WriteBuffer is
 * used from a service, that will be the only service that the buffer will be permitted to be used
 * from. While this contract may seem unnecessarily rigid, it represents the purpose of the buffer,
 * which is to provide an abstraction that can be efficiently written-to within one service, and
 * then subsequently read-from in another service. Regarding efficiency, the implementation as
 * provided here is correct and reasonable, but it is necessarily inefficient, because the Ecstasy
 * language does not provide explicit language support for the concept of exclusive access when
 * references are passed across service boundaries. It is expected that an implementation of the
 * Ecstasy runtime would provide an intrinsic implementation of this service that adheres to the
 * specified contracts, while minimizing the implied costs of (i) the buffer's service boundary
 * and (ii) the creation of one-time-use proxy references.
 */
service TransferableBuffer(Int size)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new Buffer of the specified size.
     *
     * @param size        the size in bytes of the buffer
     * @param recycle     the function to call when the ReadBuffer is closed and the underlying
     *                    buffer is ready to be re-used
     * @param alwaysZero  pass True to always zero the buffer contents when invalidating a buffer
     */
    construct(Int size, function void(TransferableBuffer)? recycle = Null, Boolean alwaysZero = False)
        {
        }
    finally
        {
        this.actual = new ActualBuffer(size, recycle, alwaysZero);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The buffer implementation.
     */
    private ActualBuffer actual;

    /**
     * The configured buffer size.
     */
    Int capacity.get()
        {
        return actual.capacity;
        }


    // ----- public API ----------------------------------------------------------------------------

    /**
     * Create a new WriteBuffer that will use the buffer storage of this Buffer.
     *
     * @return a new WriteBuffer
     */
    io.WriteBuffer createWriteBuffer()
        {
        return actual.createWriteBuffer();
        }

    /**
     * Invalidate whatever WriteBuffer or ReadBuffer is current. Optionally erase all of the buffer
     * contents.
     *
     * @param zero  pass True to explicitly zero the contents of the buffer
     */
    void invalidateCurrent(Boolean zero = False)
        {
        actual.invalidateCurrent(zero);
        }


    // ----- ActualBuffer hidden implementation class ----------------------------------------------

    /**
     * An internal implementation that provides support for both the read- and the write-buffers.
     */
    private class ActualBuffer
        {
        /**
         * Construct an ActualBuffer, which provides a proxied API to both the WriteBuffer and the
         * ReadBuffer implementations.
         *
         * @param size        the size in bytes of the buffer
         * @param recycle     the function to call when the ReadBuffer is closed and the underlying
         *                    buffer is ready to be re-used
         * @param alwaysZero  pass True to always zero the buffer contents when invalidating
         */
        construct(Int size, function void(TransferableBuffer)? recycle = Null, Boolean alwaysZero = False)
            {
            this.bytes      = new Byte[size];
            this.recycle    = recycle;
            this.alwaysZero = alwaysZero;
            }


        // ----- properties --------------------------------------------------------------------

        /**
         * The configured buffer size.
         */
        Int capacity.get()
            {
            return bytes.size;
            }

        /**
         * The current WriteBuffer or ReadBuffer "ticket" value that is permitted to access and
         * possibly mutate the buffer state.
         */
        Int currentTicket = 0;

        /**
         * The underlying buffer memory.
         */
        Byte[] bytes;

        /**
         * The stream position in the byte array.
         */
        Int offset;

        /**
         * The size of the data in the byte array. Not the size of the byte array itself; that is
         * the [capacity].
         */
        Int size;

        /**
         * The optional notification function (probably from the buffer provider) that this buffer
         * calls when it is ready to be recycled.
         */
        function void(TransferableBuffer)? recycle;

        /**
         * If True, then always zero the buffer contents when invalidating.
         */
        Boolean alwaysZero;


        // ----- methods ---------------------------------------------------------------------------

        /**
         * Create a new WriteBuffer that will re-use the buffer storage managed by this
         * ActualBuffer.
         *
         * @return a new WriteBuffer
         */
        io.WriteBuffer createWriteBuffer()
            {
            invalidateCurrent();

            offset = 0;
            size   = 0;

            return new WriteBuffer(this, currentTicket);
            }

        /**
         * Clean up when a WriteBuffer converts to a ReadBuffer. Creates a new ReadBuffer that will
         * read from whatever data is currently in the buffer storage managed by this ActualBuffer.
         *
         * @param ticket  the WriteBuffer's ticket
         *
         * @return a new ReadBuffer
         */
        io.ReadBuffer convertWriteToReadBuffer(Int ticket)
            {
            checkValid(ticket);
            invalidateCurrent();

            offset = 0;

            return new ReadBuffer(this, currentTicket);
            }

        /**
         * Clean up when a ReadBuffer closes.
         *
         * @param ticket  the ReadBuffer's ticket
         */
        void destroyReadBuffer(Int ticket)
            {
            if (ticket == currentTicket)
                {
                invalidateCurrent();
                recycle?(this.TransferableBuffer);
                }
            }

        /**
         * Validate the provided buffer ticket. This method is used to prevent a previously allocated
         * ReadBuffer or WriteBuffer from accessing or mutating the buffer's data once the ReadBuffer
         * or WriteBuffer has been invalidated, or once another ReadBuffer or WriteBuffer has been
         * allocated.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         */
        void checkValid(Int ticket)
            {
            assert ticket == currentTicket;
            }

        /**
         * Invalidate whatever WriteBuffer or ReadBuffer is current. Optionally erase all of the
         * buffer contents.
         *
         * @param zero  pass True to explicitly zero the contents of the buffer
         */
        void invalidateCurrent(Boolean zero = False)
            {
            ++currentTicket;
            if (zero || alwaysZero)
                {
                bytes.fill(0);
                }
            }


        // ----- backing methods for ReadBuffer and WriteBuffer ------------------------------------

        /**
         * Obtain the buffer's capacity.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         *
         * @return the buffer's capacity (the number of bytes that it can hold when full)
         */
        Int getCapacity(Int ticket)
            {
            checkValid(ticket);
            return capacity;
            }

        /**
         * Obtain the current buffer offset.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         *
         * @return the current buffer offset
         */
        Int getOffset(Int ticket)
            {
            checkValid(ticket);
            return offset;
            }

        /**
         * Modify the current buffer offset.
         *
         * @param ticket     the ticket that was provided to the ReadBuffer or WriteBuffer
         * @param newOffset  the value to set the offset to
         *
         * @throws OutOfBounds  if the new offset is not in the range `(0 <= newOffset <= size)`
         */
        void setOffset(Int ticket, Int newOffset)
            {
            checkValid(ticket);
            assert:bounds newOffset >= 0 && newOffset <= size;
            offset = newOffset;
            }

        /**
         * Modify the current buffer offset by changing it relative to its current value.
         *
         * @param ticket      the ticket that was provided to the ReadBuffer or WriteBuffer
         * @param adjustment  the amount to change the offset by (negative or positive)
         *
         * @throws OutOfBounds  if the adjustment would cause the buffer offset to exceed the range
         *                      `(0 <= offset <= size)`
         */
        void adjustOffset(Int ticket, Int adjustment)
            {
            setOffset(ticket, offset + adjustment);
            }

        /**
         * Obtain the size of the data in the buffer.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         *
         * @return the current buffer size, which refers to the number of bytes in the buffer, and
         *         not to the capacity of the buffer
         */
        Int getSize(Int ticket)
            {
            checkValid(ticket);
            return size;
            }

        /**
         * Determine the number of bytes that can still be read from the buffer.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         *
         * @return the difference between the buffer's size and its current offset
         */
        Int getRemaining(Int ticket)
            {
            checkValid(ticket);
            return size - offset;
            }

        /**
         * Obtain the byte at the specified index.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         * @param index   the 0-based index into the buffer to obtain the byte from,
         *                `(0 <= index < size)`
         *
         * @return the byte value at the specified index in the buffer
         *
         * @throws OutOfBounds  if the index is outside of the range `(0 <= index < size)`
         */
        Byte getByte(Int ticket, Int index)
            {
            checkValid(ticket);
            assert:bounds index < size;
            return bytes[index];
            }

        /**
         * Store the specified byte value at the specified index.
         *
         * @param ticket  the ticket that was provided to the WriteBuffer
         * @param index   the 0-based index into the buffer to store the byte at,
         *                `(0 <= index <= size)`
         * @param value   the byte value to store
         *
         * @throws OutOfBounds  if the index is outside of the range `(0 <= index <= size)`
         */
        void setByte(Int ticket, Int index, Byte value)
            {
            checkValid(ticket);
            assert:bounds index <= size;
            bytes[index] = value;
            if (index == size)
                {
                size = index + 1;
                }
            }

        /**
         * Obtain the byte at the current offset within the stream.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         *
         * @return the byte at the current offset in the stream
         *
         * @throws OutOfBounds  if the buffer offset is equal to the buffer size (remaining == 0)
         */
        Byte readByte(Int ticket)
            {
            checkValid(ticket);
            assert:bounds offset < size;
            return bytes[offset++];
            }

        /**
         * Store the specified byte value at the current offset within the stream.
         *
         * @param ticket  the ticket that was provided to the ReadBuffer or WriteBuffer
         * @param value   the byte value to store
         *
         * @throws OutOfBounds  if the buffer offset is equal to the buffer capacity
         */
        void writeByte(Int ticket, Byte value)
            {
            checkValid(ticket);
            assert:bounds offset < capacity;
            bytes[offset++] = value;
            if (offset > size)
                {
                size = offset;
                }
            }
        }


    // ----- ReadBuffer proxy implementation -------------------------------------------------------

    /**
     * A ReadBuffer implementation that uses the reusable internal buffer to read from. This class,
     * as a `Const`, acts as a portable proxy to the reusable buffer; it proxies all buffer
     * operations to the reusable buffer, and once this buffer is invalidated or another buffer is
     * created by the reusable buffer, this ReadBuffer will cease to operate, having lost its
     * exclusive access to the reusable ActualBuffer.
     */
    private static const ReadBuffer(ActualBuffer actual, Int ticket)
            implements io.ReadBuffer
        {
        /**
         * The actual buffer storage.
         */
        protected ActualBuffer actual;

        /**
         * This proxy's ticket to use the buffer. As soon as another buffer is created to use the
         * same underlying buffer, this buffer's ticket is invalid.
         */
        protected Int ticket;

        /**
         * As part of the buffer's mutual exclusion guarantee, once a ReadBuffer is used within a
         * service, it can only be used within that specific service.
         */
        void checkService()
            {
            private @Lazy Service sticky.calc()
                {
                return this:service;
                }

            assert this:service == sticky;
            }

        @Override
        @RO Int size.get()
            {
            checkService();
            return actual.getSize(ticket);
            }

        @Override
        Int offset
            {
            @Override
            Int get()
                {
                checkService();
                return actual.getOffset(ticket);
                }

            @Override
            void set(Int newOffset)
                {
                checkService();
                actual.setOffset(ticket, newOffset);
                }
            }

        @Override
        ReadBuffer skip(Int count)
            {
            checkService();
            if (count > 0)
                {
                actual.adjustOffset(ticket, count);
                }
            else
                {
                assert:bounds count >= 0;
                }
            return this;
            }

        @Override
        ReadBuffer rewind()
            {
            checkService();
            actual.setOffset(ticket, 0);
            return this;
            }

        @Override
        ReadBuffer moveTo(Int newOffset)
            {
            checkService();
            actual.setOffset(ticket, newOffset);
            return this;
            }

        @Override
        @RO Int remaining.get()
            {
            checkService();
            return actual.getRemaining(ticket);
            }

        @Override
        @Op("[]") Byte getByte(Int index)
            {
            checkService();
            return actual.getByte(ticket, index);
            }

        @Override
        Byte readByte()
            {
            checkService();
            return actual.readByte(ticket);
            }

        @Override
        void readBytes(Byte[] bytes, Int offset, Int count)
            {
            checkService();
            assert offset >= 0 && count >= 0;

            Int last = offset + count;
            assert last <= bytes.size;
            while (offset < last)
                {
                bytes[offset++] = actual.readByte(ticket);
                }
            }

        @Override
        void pipeTo(BinaryOutput out, Int count)
            {
            checkService();
            assert:bounds count >= 0;
            while (count-- > 0)
                {
                out.writeByte(actual.readByte(ticket));
                }
            }

        @Override
        void close(Exception? cause = Null)
            {
            // note: this is not strictly required to be called on the same service
            actual.destroyReadBuffer(ticket);
            }
        }


    // ----- WriteBuffer proxy implementation ------------------------------------------------------

    /**
     * A WriteBuffer implementation that uses the reusable internal buffer to write to. This class,
     * as a `Const`, acts as a portable proxy to the reusable buffer; it proxies all buffer
     * operations to the reusable buffer, and once this buffer is invalidated or another buffer is
     * created by the reusable buffer, this WriteBuffer will cease to operate, having lost its
     * exclusive access to the reusable ActualBuffer.
     */
    private static const WriteBuffer(ActualBuffer actual, Int ticket)
            extends ReadBuffer(actual, ticket)
            implements io.WriteBuffer
        {
        /**
         * When the WriteBuffer is "flipped" to a ReadBuffer, this property is lazily calculated
         * with the result of that conversion; after this occurs, the WriteBuffer can no longer be
         * used, because it will have lost its exclusive access to the actual buffer.
         */
        private @Lazy io.ReadBuffer conversion.calc()
            {
            return actual.convertWriteToReadBuffer(ticket);
            }

        @Override
        @RO Int capacity.get()
            {
            checkService();
            return actual.getCapacity(ticket);
            }

        @Override
        @Op("[]=") void setByte(Int index, Byte value)
            {
            checkService();
            actual.setByte(ticket, index, value);
            }

        @Override
        void writeByte(Byte value)
            {
            checkService();
            actual.writeByte(ticket, value);
            }

        @Override
        void writeBytes(Byte[] bytes)
            {
            checkService();
            for (Byte byte : bytes)
                {
                actual.writeByte(ticket, byte);
                }
            }

        @Override
        void writeBytes(Byte[] bytes, Int offset, Int count)
            {
            checkService();
            assert offset >= 0 && count >= 0;

            Int last = offset + count;
            while (offset < last)
                {
                actual.writeByte(ticket, bytes[offset++]);
                }
            }

        @Override
        io.ReadBuffer toReadBuffer()
            {
            // note: this is not strictly required to be called on the same service
            return conversion;
            }

        @Override
        void close(Exception? cause = Null)
            {
            // note: this is not strictly required to be called on the same service
            val notUsedHere = conversion;
            }
        }
    }