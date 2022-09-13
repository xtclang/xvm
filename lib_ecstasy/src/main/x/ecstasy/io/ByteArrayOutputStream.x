/**
 * The ByteArrayOutputStream is an implementation of an OutputStream on top of a byte array.
 */
 class ByteArrayOutputStream
        implements OutputStream
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ByteArrayOutputStream with an optional initial capacity. The stream will
     * construct its own mutable array internally.
     *
     * @param initialCapacity  the initial byte array capacity (optional)
     */
    construct(Int initialCapacity = 0)
        {
        this.bytes = new Array<Byte>(initialCapacity);
        }

    /**
     * Construct a ByteArrayOutputStream on top of an existing array.
     *
     * @param bytes  the mutable or fixed-size byte array to write to
     */
    construct(Array<Byte> bytes)
        {
        assert:arg bytes.inPlace;

        this.bytes = bytes;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying array of bytes.
     */
    public/private Array<Byte> bytes;

    /**
     * The allocated capacity of the output stream.
     *
     * REVIEW is there some way to "delegates capacity"?
     */
    Int capacity
        {
        @Override
        Int get()
            {
            return bytes.capacity;
            }

        @Override
        void set(Int newCapacity)
            {
            bytes.capacity = newCapacity;
            }
        }


    // ----- OutputStream interface ----------------------------------------------------------------

    @Override
    Int offset.set(Int offset)
        {
        assert:bounds offset >= 0 && offset <= size;
        super(offset);
        }

    @Override
    Int size.get()
        {
        return bytes.size;
        }


    // ----- BinaryInput interface -----------------------------------------------------------------

    @Override
    void writeByte(Byte value)
        {
        if (bytes.mutability == Fixed && offset >= size)
            {
            throw new EndOfFile();
            }

        bytes[offset] = value;
        ++offset;
        }

    @Override
    void writeBytes(Byte[] bytes)
        {
        writeBytes(bytes, 0, bytes.size);
        }

    @Override
    void writeBytes(Byte[] bytes, Int offset, Int count)
        {
        assert:bounds offset >= 0 && count >= 0 && offset + count <= bytes.size;

        // appending nothing does nothing (eliminate this case up front to simplify subsequent
        // processing)
        if (count == 0)
            {
            return;
            }

        // determine whether our current position into the underlying array is pointing to an
        // existing element (versus the normal case of pointing past the end of the array)
        Int remaining = this.size - this.offset;
        if (remaining > 0)
            {
            // determine how many bytes to copy into existing elements of the underlying array
            Int replace = count.minOf(remaining);

            // replace those bytes in the underlying array
            this.bytes.replaceAll(this.offset, bytes[offset ..< offset+replace]);
            this.offset += replace;

            // update the portion of the passed bytes that still need to be written (and if nothing
            // remains to be written, then we're done)
            count -= replace;
            if (count == 0)
                {
                return;
                }
            offset += replace;
            }

        // with a fixed size array, once the existing elements are filled up, it is not possible to
        // continue to write any further
        if (this.bytes.mutability == Fixed)
            {
            throw new EndOfFile();
            }

        // pre-size the underlying array if it isn't big enough to hold the data to write
        Int total = size + count;
        if (total > capacity)
            {
            capacity = total;
            }

        // append the remaining-data-to-write to the underlying array
        this.bytes.capacity = total;
        this.bytes.addAll(bytes[offset ..< offset+count]);
        this.offset += count;
        }
    }