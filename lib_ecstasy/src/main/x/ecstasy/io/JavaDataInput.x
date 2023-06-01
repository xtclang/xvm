import io.IllegalUTF;

/**
 * The JavaDataInput mixin uses the same wire formats as defined by the Java `DataInputStream` for
 * bytes, characters, strings, and for the subset of the number types supported by that language.
 */
mixin JavaDataInput
        into BinaryInput
        implements DataInput {

    @Override
    Char readChar() {
        // Java's DataInputStream uses the UTF-16 format for individual characters; note that this
        // implementation will consume 32 bits if the first 16 bits are a surrogate codepoint (as
        // per the Unicode standard, since a surrogate codepoint by itself is an illegal character)
        return readUTF16BEChar(this);
    }

    @Override
    String readString() {
        // read the number of bytes (not the number of chars)
        Int length = readUInt16();
        if (length == 0) {
            return "";
        }

        // because of the design of the data format, the reader must keep track of the number of
        // bytes read (because the number of chars is unknown)
        DataInputStream in = this.is(DataInputStream)
                ? this
                : new @JavaDataInput ByteArrayInputStream(readBytes(length));

        StringBuffer buf = new StringBuffer(length);
        Int next = in.offset + length;
        while (in.offset < next) {
            buf.append(readUTF8Char(in));
        }

        // if the stream isn't corrupted, then the offset will always end in the exact right spot
        if (in.offset != next) {
            throw new IllegalUTF();
        }

        return buf.toString();
    }
}