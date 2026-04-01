
const ByteString(Byte[] bytes)
        implements Iterable<Byte> {

    static ByteString Empty = new ByteString([]);

    @Override Int size.get() = bytes.size;

    @Override Boolean empty.get() = bytes.empty;

    @Override Iterator<Element> iterator() = bytes.iterator();

    /**
     * Write the contents of this ByteString to the given BinaryOutput.
     */
    void writeTo(BinaryOutput out) {
        out.writeBytes(bytes);
    }
}
