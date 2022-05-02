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
service RTChannel(RawChannel rawChannel)
        implements Channel
        implements BufferProvider
    {
    construct(RawChannel rawChannel)
        {
        this.rawChannel = rawChannel;
        }

    protected RawChannel rawChannel;

    protected ReadBuffer? leftover;

    protected Boolean closed;


    // ----- Channel API ---------------------------------------------------------------------------

    @Override
    @RO BufferProvider buffers.get()
        {
        return &this.maskAs(BufferProvider);
        }

    @Override
    @RO Boolean readable.get()
        {
        return leftover == Null && rawChannel.eof;
        }

    @Override
    @RO Boolean eof.get()
        {
        return leftover == Null && rawChannel.eof;
        }

    @Override
    ReadBuffer? read()
        {
        assert !closed as "The Channel was closed before the read operation began";

        ReadBuffer? previous = leftover;
        if (previous != Null)
            {
            leftover = Null;
            return previous;
            }

        Byte[]|Int result = rawChannel.take();
        if (result.is(Byte[]))
            {
            ReadBuffer buf = new RTBuffer(rawChannel, result, result.size, readOnly=True);
            return &buf.maskAs(ReadBuffer);
            }

        switch (result)
            {
            case -1:
                return Null;
            case -2:
                throw new IOClosed("Input has been shut down");
            case -3:
                throw new IOClosed("The Channel was closed during the read operation");
            default:
                throw new IOException($"Unknown failure ({result})");
            }
        }

    @Override
    Int read(WriteBuffer buffer, Int minBytes = Int.maxvalue)
        {
        ReadBuffer in;
        if (!(in ?= read()))
            {
            return 0;
            }

        Int space  = buffer.capacity - buffer.offset;
        Int copied = 0;
        do
            {
            Int max  = in.remaining;
            Int copy = space.minOf(max);
            in.pipeTo(buffer, copy);                // REVIEW: this API doesn't "feel right"
            copied += copy;
            space  -= copy;

            if (copy < max)
                {
                // we have leftovers in the read buffer
                leftover = in;
                return copied;
                }
            else
                {
                in.close();
                }
            }
        while (copied < minBytes, in ?= read());

        return copied;
        }

    @Override
    (Int, Int) read(WriteBuffer[] buffers, Int minBytes = Int.maxvalue)
        {
        // TODO
        TODO
        }

    @Override
    @RO Boolean writable.get()
        {
        // TODO
        TODO
        }

    @Override
    Int write(ReadBuffer buffer)
        {
        // TODO
        TODO
        }

    @Override
    Int write(ReadBuffer[] buffers, function void(ReadBuffer)? written=Null)
        {
        // TODO
        TODO
        }


    // ----- BufferProvider API --------------------------------------------------------------------

    @Override
    conditional WriteBuffer next()
        {
        Byte[]|Int result = rawChannel.allocate();
        if (result.is(Byte[]))
            {
            WriteBuffer buf = new RTBuffer(rawChannel, result, 0, readOnly=False);
            return True, &buf.maskAs(WriteBuffer);
            }

        switch (result)
            {
            case -1:
                return False;
            case -2:
                throw new IllegalState("The Channel corresponding to the BufferProvider is closed");
            default:
                throw new IllegalState($"Unknown failure ({result})");
            }
        }

    @Override
    conditional Int capacityLimit()
        {
        Int n = rawChannel.capacityLimit;
        return n != 0, n;
        }

    @Override
    conditional Int fixedBufferSize()
        {
        Int n = rawChannel.fixedBufferSize;
        return n != 0, n;
        }

    @Override
    @RO Int totalBuffers.get()
        {
        return rawChannel.totalBuffers;
        }

    @Override
    @RO Int totalBytes.get()
        {
        return rawChannel.totalBytes;
        }

    @Override
    @RO Int consumedBuffers.get()
        {
        return rawChannel.consumedBuffers;
        }

    @Override
    @RO Int consumedBytes.get()
        {
        return rawChannel.consumedBytes;
        }


    // ----- Closeable API -------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null)
        {
        if (!closed)
            {
            rawChannel.close();
            closed = True;
            }
        }
    }