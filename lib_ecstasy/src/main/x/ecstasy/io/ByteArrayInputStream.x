/**
 * The ByteArrayInputStream is an implementation of an InputStream on top of a byte array.
 */
class ByteArrayInputStream
        implements InputStream {
    // ----- constructors --------------------------------------------------------------------------

    construct(Byte[] bytes, Int offset = 0) {
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
    Int offset.set(Int offset) {
        assert:bounds offset >= 0 && offset <= bytes.size;
        super(offset);
    }

    @Override
    Int size.get() = bytes.size;

    // ----- BinaryInput interface -----------------------------------------------------------------

    @Override
    @RO Int available.get() = remaining;

    @Override
    Byte readByte() {
        if (offset >= size) {
            throw new EndOfFile();
        }

        return bytes[offset++];
    }

    @Override
    immutable Byte[] readBytes(Int count) {
        if (offset >= size) {
            return [];
        }

        Int first     = offset;
        Int afterLast = (first + count).notGreaterThan(size);
        offset        = afterLast;
        return bytes[first..<afterLast].freeze(inPlace=False);
    }

    @Override
    Int pipeTo(BinaryOutput out, Int count = MaxValue) {
        if (out.is(ByteArrayOutputStream)) {
            Int copy = Int.minOf(count, remaining);
            out.writeBytes(bytes, offset, copy);
            offset += copy;
            return copy;
        }

        return super(out, count);
    }
}