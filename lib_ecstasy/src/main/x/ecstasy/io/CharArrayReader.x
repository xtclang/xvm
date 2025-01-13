import TextPosition.Snapshot;
import TextPosition.Stringer;

/**
 * A CharArrayReader provides a Reader interface on top of a raw `Char[]` or a `String`. Because its
 * unit of storage is already in the form of a `Char`, its navigation both forwards and backwards
 * can be more efficient than a format with an underlying variable-length binary encoding, such as
 * UTF-8.
 */
class CharArrayReader(immutable Char[] chars)
        implements Reader {
    // ----- constructors --------------------------------------------------------------------------

    construct(String str) {
        construct CharArrayReader(str.toCharArray());
        this.str = str;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The character contents.
     */
    protected/private immutable Char[] chars;

    /**
     * A cached String representing the contents.
     */
    protected/private String? str;

    /**
     * @return the total number of characters represented by the reader
     */
    @Override
    Int size.get() = chars.size;

    /**
     * @return the remaining (yet to be read) number of characters represented by the reader
     */
    conditional Int knownSize() {

    /**
     * @return the remaining (yet to be read) number of characters represented by the reader
     */
    Int remaining.get() = size - offset;

    // ----- Iterator operations -------------------------------------------------------------------

    @Override
    Boolean knownEmpty() = eof;

    @Override
    conditional Int knownSize() = (True, remaining);

    // ----- Reader operations ---------------------------------------------------------------------

    @Override
    Int offset; // TODO

    @Override
    Int lineNumber; // TODO

    @Override
    public/private Int lineStartOffset;

    @Override
    TextPosition position {
        @Override
        TextPosition get() {
            return offset <= 0xFFFFFF && lineNumber <= 0xFFFFF && lineOffset <= 0xFFFFF
                    ? new TinyPos(offset, lineNumber, lineOffset)
                    : new Snapshot(offset, lineNumber, lineOffset);
        }

        @Override
        void set(TextPosition position) {
            assert:arg position.is(Snapshot) || position.is(TinyPos);

            offset          = position.offset;
            lineNumber      = position.lineNumber;
            lineStartOffset = position.lineStartOffset;
        }
    }

    @Override
    Boolean eof.get() {
        return offset >= size;
    }

    @Override
    Char take() {
        if (eof) {
            throw new EndOfFile();
        }

        Char ch = chars[offset++];

        HandleTerminator: if (ch.isLineTerminator()) {
            if (ch == '\r' && !eof) {
                // there's a weird situation that hearkens back to the teletype (shortly after the
                // invention of the wheel), where a CR was required before an LF in order to achieve
                // the functionality of a "new line"; Microsoft retained this antiquated convention,
                // so we are forced to peek at the next char, see if it is an LF, and if it is, then
                // we have to ignore the preceding CR (as if it were a regular character), because
                // it's followed by something that will actually act as a for-real line terminator
                if (chars[offset] == '\n') {
                    break HandleTerminator;
                }
            }

            ++lineNumber;
            lineStartOffset = offset;
        }

        return ch;
    }

    @Override
    Reader rewind(Int count = 1) {
        assert:arg count >= 0;
        if (count > offset) {
            return reset();
        }

        if (count <= lineOffset) {
            // rewind within the current line
            offset -= count;
            return this;
        }

        // TODO this could be optimized further
        return super(count);
    }

    @Override
    Reader reset() {
        offset          = 0;
        lineNumber      = 0;
        lineStartOffset = 0;

        return this;
    }

    // ----- bulk read operations ------------------------------------------------------------------

    @Override
    Boolean hasAtLeast(Int count) = count <= remaining;

    // ----- line oriented operations --------------------------------------------------------------

    @Override
    CharArrayReader seekLine(Int line) {
        TODO
    }

    @Override
    String nextLine() {
        TODO
    }

    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the contents of this entire Reader, as an immutable Array of Char
     */
    immutable Char[] toCharArray() = chars;

    @Override
    String toString() = str ?: (str <- new String(chars));
}