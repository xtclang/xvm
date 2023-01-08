/**
 * The ByteArrayInputStream is an implementation of an InputStream on top of a byte array.
 */
class ByteArrayInputStream
        implements InputStream
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Byte[] bytes, Int offset = 0)
        {
        assert:bounds offset >= 0 && offset <= bytes.size;

        this.bytes  = bytes;
        this.offset = offset;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying array of bytes.
     */
    public/private Byte[] bytes;


    // ----- InputStream interface -----------------------------------------------------------------

    @Override
    Int offset.set(Int offset)
        {
        assert:bounds offset >= 0 && offset <= bytes.size;
        super(offset);
        }

    @Override
    Int size.get()
        {
        return bytes.size;
        }


    // ----- BinaryInput interface -----------------------------------------------------------------

    @Override
    Byte readByte()
        {
        if (offset >= size)
            {
            throw new EndOfFile();
            }

        return bytes[offset++];
        }


    @Override
    void readBytes(Byte[] bytes)
        {
        readBytes(bytes, 0, bytes.size);
        }

    @Override
    void readBytes(Byte[] bytes, Int offset, Int count)
        {
        if (bytes.size >= 0)
            {
            assert:arg bytes.inPlace;
            assert:arg offset >= 0 && offset <= bytes.size;
            assert:arg count >= 0 && offset + count <= bytes.size;

            if (this.offset + count >= size)
                {
                // not enough remaining bytes to satisfy the request
                if (this.offset < size)
                    {
                    // take whatever bytes remain (as if we had read each byte until encountering
                    // an EndOfFile)
                    bytes.replaceAll(offset, this.bytes[this.offset ..< size]);
                    this.offset = size;
                    }
                throw new EndOfFile();
                }

            Int first   = this.offset;
            this.offset = first + count;
            bytes.replaceAll(offset, this.bytes[first ..< this.offset]);
            }
        }

    @Override
    Byte[] readBytes(Int count)
        {
        if (offset + count > size)
            {
            // behave as if we had read each byte until encountering an EndOfFile
            offset = size;
            throw new EndOfFile();
            }

        Int first = offset;
        Int last  = first + count - 1;
        offset    = last + 1;
        return bytes[first..last];
        }

    @Override
    void pipeTo(BinaryOutput out, Int count)
        {
        if (out.is(ByteArrayOutputStream))
            {
            Int copy = Int.minOf(count, remaining);
            out.writeBytes(bytes, offset, copy);
            offset += copy;
            if (copy < count)
                {
                throw new EndOfFile();
                }
            return;
            }

        super(out, count);
        }
    }