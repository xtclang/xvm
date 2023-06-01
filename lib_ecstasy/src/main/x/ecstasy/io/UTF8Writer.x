/**
 * A UTF8Writer emits UTF8 data to an underlying OutputStream.
 */
class UTF8Writer(BinaryOutput out)
        implements Writer {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The OutputStream to write UTF8-encoded characters to.
     */
    protected/private BinaryOutput out;


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    UTF8Writer add(Char v) {
        DataOutput.writeUTF8Char(out, v);
        return this;
    }
}
