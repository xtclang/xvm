import ecstasy.io.EndOfFile;
import ecstasy.io.IOException;
import ecstasy.io.ReadBuffer;
import ecstasy.io.TransferableBuffer;
import ecstasy.io.WriteBuffer;

/**
 * A specialized implementation of a `WriteBuffer` and/or a `ReadBuffer` around a native buffer,
 * that is capable of being passed from one service to another without copying or freezing the data.
 */
class RTBuffer(RawChannel rawChannel, Byte[] rawBytes, Int rawSize, Boolean readOnly)
        implements ReadBuffer
        implements WriteBuffer
        implements Transferable
        implements Closeable
    {
    // none of the properties needs to be marked `private`, since the API will be masked at all
    // times, so this is as much documentation-of-intent as anything; these properties are never
    // exposed
    private RawChannel rawChannel;
    private Byte[]     rawBytes;
    private Int        rawSize;
    private Int        rawOffset;


    // ----- ReadBuffer methods --------------------------------------------------------------------

    @Override
    @Op("[]") Byte getByte(Int index)
        {
        assert:bounds 0 <= index < size;
        return rawBytes[index];
        }

    @Override
    Int offset
        {
        @Override
        Int get()
            {
            return rawOffset;
            }

        @Override
        void set(Int offset)
            {
            assert:bounds 0 <= offset <= size;
            rawOffset = offset;
            }

        }

    @Override
    @RO Int size.get()
        {
        return rawSize;
        }

    @Override
    Byte readByte()
        {
        Int offset = rawOffset;
        if (offset >= size)
            {
            throw new EndOfFile();
            }

        this.rawOffset = offset+1;
        return rawBytes[offset];
        }

    @Override
    void readBytes(Byte[] bytes, Int offset, Int count)
        {
        Int thisOffset = rawOffset;
        Int thisSize   = rawSize;
        Int thatSize   = bytes.size;

        assert:bounds 0 <= offset <= thatSize;
        assert:bounds 0 <= count;
        assert:bounds offset + count <= thatSize;

        Int copy = count.minOf(thisSize - thisOffset);
        for (Int i = 0; i < copy; ++i)
            {
            bytes[offset+i] = rawBytes[thisOffset+i];
            }

        rawOffset = thisOffset + copy;

        if (copy < count)
            {
            // pretend that we were reading byte by byte and then suddenly hit an unexpected EOF
            throw new EndOfFile();
            }
        }

    @Override
    void pipeTo(BinaryOutput out, Int count)
        {
        WriteBuffer? buf = Null;
        if (buf := &out.revealAs(RTBuffer))
            {
            // it's one of "me"
            }
        else if (buf := &out.revealAs(TransferableBuffer:private.WriteBuffer))
            {
            // it's not one of "me", but we're going to carefully cheat and allow the
            // TransferableBuffer direct access to my raw bytes
            }

        if (buf != Null)
            {
            Int space = buf.capacity - buf.offset;
            Int copy  = count.minOf(space).minOf(remaining);
            if (copy > 0)
                {
                buf.writeBytes(rawBytes, rawOffset, copy);
                rawOffset += copy;

                count -= copy;
                if (count == 0)
                    {
                    return;
                    }
                }
            }

        super(out, count);
        }


    // ----- WriteBuffer methods -------------------------------------------------------------------

    @Override
    @RO Int capacity.get()
        {
        return rawBytes.size;
        }

    @Override
    @Op("[]=") void setByte(Int index, Byte value)
        {
        Int oldSize = rawSize;
        assert:bounds 0 <= index <= oldSize;
        rawBytes[index] = value;
        if (index == oldSize)
            {
            rawSize = oldSize+1;
            }
        }

    @Override
    void writeByte(Byte value)
        {
        Int offset = rawOffset;
        if (offset >= capacity)
            {
            throw new IOException("Attempt to write beyond end of buffer");
            }

        rawBytes[offset++] = value;
        rawOffset = offset;
        if (offset > rawSize)
            {
            rawSize = offset;
            }
        }

    @Override
    void writeBytes(Byte[] bytes, Int offset, Int count)
        {
        Int thisOffset = this.rawOffset;
        Int thatSize   = bytes.size;

        assert:bounds 0 <= offset <= thatSize;
        assert:bounds 0 <= count;
        assert:bounds offset + count <= thatSize;

        Int copy = count.minOf(capacity - thisOffset);
        for (Int i = 0; i < copy; ++i)
            {
            rawBytes[thisOffset+i] = bytes[offset+i];
            }

        thisOffset += copy;
        if (thisOffset > rawSize)
            {
            rawSize = thisOffset;
            }
        rawOffset = thisOffset;

        if (copy < count)
            {
            // pretend that we were writing byte by byte and then suddenly ran out of buffer
            throw new IOException("Attempt to write beyond end of buffer");
            }
        }

    @Override
    ReadBuffer toReadBuffer()
        {
        assert capacity > 0 as "WriteBuffer is already closed";

        // this "transfers" the reference to the underlying native buffer from the WriteBuffer to
        // a new ReadBuffer
        RTBuffer buf = new RTBuffer(rawChannel, rawBytes, rawSize, readOnly=True);
        reset();
        return &buf.maskAs(ReadBuffer);
        }


    // ----- Transferable methods ------------------------------------------------------------------

    @Override
    RTBuffer transfer()
        {
        RTBuffer transferee = new RTBuffer(rawChannel, rawBytes, rawSize, readOnly);

        if (capacity > 0)
            {
            if (readOnly)
                {
                // we can continue to use the read buffer within this service, because it is a
                // read-only object, but we need to create a reference from the service that the
                // new buffer is being transferred to
                rawChannel.incRefCount(rawBytes);
                }
            else
                {
                // once the write buffer is transferred, it can no longer be used here, because it
                // is a mutable object; we are transferring our "reference count" to the other
                // service
                reset();
                }
            }

        return transferee;
        }


    // ----- Closeable methods ---------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null)
        {
        if (capacity > 0)
            {
            Byte[] bytes = rawBytes;
            reset();
            rawChannel.decRefCount(bytes);
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    String toString()
        {
        return readOnly ? "ReadBuffer" : "WriteBuffer";
        }

    @Override
    immutable RTBuffer makeImmutable()
        {
        // it is illegal to attempt to make a native buffers into an immutable object
        throw new UnsupportedOperation();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Internally reset the state of the RTBuffer so that it no longer refers to the underlying
     * native buffer, as if the RTBuffer has been closed or otherwise invalidated.
     */
    private void reset()
        {
        rawBytes  = [];
        rawOffset = 0;
        rawSize   = 0;
        }
    }