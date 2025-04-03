import TextPosition.Snapshot;
import TextPosition.Stringer;

/**
 * A `CharArrayReader` provides a [Reader] interface on top of a raw `Char[]` or a [String]. Because
 * its unit of storage is already in the form of a `Char`, its navigation -- both forwards and
 * backwards -- can be more efficient than a format with an underlying variable-length binary
 * encoding, such as UTF-8.
 */
class CharArrayReader(immutable Char[] chars)
        implements Reader {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `CharArrayReader` from a `String`.
     *
     * @param str  the `String` providing the contents of the `Reader`
     */
    construct(String str) {
        construct CharArrayReader(str.chars);
        this.str = str;
    }

    // ----- internal properties -------------------------------------------------------------------

    /**
     * The character contents.
     */
    protected/private immutable Char[] chars;

    /**
     * A cached String representing the contents.
     */
    protected/private String? str;

    /**
     * Internal offset.
     */
    private Int offset_;

    /**
     * Internal line number, or -1 to indicate that we're not currently tracking line info.
     */
    private Int lineNumber_;

    /**
     * Internal offset of start of line, or -1 to indicate that we're not currently tracking line
     * info.
     */
    private Int lineStartOffset_;

    private static @Inject Random rnd;

    /**
     * A random value used as a non-authoritative identity for this `Reader`.
     */
    private Int randomId = rnd.int64();

    // ----- Reader interface ----------------------------------------------------------------------

    @Override
    Int offset {
        @Override
        Int get() = offset_;

        @Override
        void set(Int newValue) {
            Int oldValue = offset_;
            if (newValue != oldValue) {
                assert:bounds 0 <= newValue <= size;
                // use advance() to adjust if we need to maintain line information; otherwise just
                // update the offset directly
                if (newValue == 0) {
                    reset();
                } else if (hasLineInfo) {
                    advance(newValue - oldValue);
                } else {
                    offset_ = newValue;
                }
            }
        }
    }

    @Override
    Int lineNumber {
        @Override
        Int get() {
            // line information isn't always maintained; once we lose it, it stays gone until we
            // need it, so we need to verify that the line info exists
            ensureLineInfo();
            return lineNumber_;
        }

        @Override
        void set(Int newValue) {
            assert:bounds newValue >= 0;
            assert:bounds seekLine(newValue);
        }
    }

    @Override
    Int lineStartOffset.get() {
        // line information isn't always maintained; once we lose it, it stays gone until we
        // need it, so we need to verify that the line info exists
        ensureLineInfo();
        return lineStartOffset_;
    }

    @Override
    Int lineOffset {
        @Override
        Int get() = offset_ - lineStartOffset;      // ensures that line info exists

        @Override
        void set(Int newValue) {
            Int oldValue = get();                   // ensures that line info exists
            if (newValue != oldValue) {
                assert:bounds newValue >= 0;
                Int rawOffset = offset_;
                Int delta     = newValue - oldValue;
                if (delta > 0) {
                    // scan forward, to verify no end-of-lines will be passed
                    assert:bounds delta <= remaining;
                    for (Int i : rawOffset ..< rawOffset+delta) {
                        assert:bounds !chars[i].isLineTerminator();
                    }
                }
                offset_ = rawOffset + delta;
            }
        }
    }

    @Override
    TextPosition position {
        @Override
        TextPosition get() {
            Int offset     = this.offset;
            Int lineNumber = this.lineNumber;
            Int lineOffset = this.lineOffset;
            return offset <= 0xFFFFF && lineNumber <= 0xFFFF && lineOffset <= 0xFFFF
                    ? new TaggedCompressed(offset, lineNumber, lineOffset, randomId)
                    : new TaggedSnapshot(offset, lineNumber, lineOffset, randomId);
        }

        @Override
        void set(TextPosition newPos) {
            if (newPos.is(Tagged) && newPos.tagMatches(randomId)) {
                offset_          = newPos.offset;
                lineNumber_      = newPos.lineNumber;
                lineStartOffset_ = newPos.lineStartOffset;
            } else {
                offset = newPos.offset;
                assert:arg lineNumber == newPos.lineNumber;
                assert:arg lineStartOffset == newPos.lineStartOffset;
            }
        }

        private static interface Tagged {
            Boolean tagMatches(Int tag);
        }

        private static const TaggedSnapshot(Int offset, Int lineNumber, Int lineOffset, Int tag)
                extends Snapshot(offset, lineNumber, lineOffset)
                implements Tagged {
            @Override
            Boolean tagMatches(Int tag) {
                return tag == this.tag;
            }
        }

        private static const TaggedCompressed
                implements TextPosition
                implements Tagged
                incorporates Stringer {

            construct(Int offset, Int lineNumber, Int lineOffset, Int tag) {
                // up to 20 bits for offset, 16 bits for line and line offset
                assert:test offset     >= 0 && offset     <= 0xFFFFF;
                assert:test lineNumber >= 0 && lineNumber <= 0xFFFF;
                assert:test lineOffset >= 0 && lineOffset <= 0xFFFF;

                combo = tag << 20 | offset << 16 | lineNumber << 16 | lineOffset;
            }

            private Int combo;

            @Override
            Int offset.get() = combo >>> 32 & 0xFFFFF;

            @Override
            Int lineNumber.get() = combo >>> 16 & 0xFFFF;

            @Override
            Int lineOffset.get() = combo & 0xFFFF;

            @Override
            Int lineStartOffset.get() = offset - lineOffset;

            @Override
            Boolean tagMatches(Int tag) = tag & 0xFFF == combo >>> 52;
        }
    }

    @Override
    Int size.get() = chars.size;

    @Override
    Int remaining.get() = size - offset_;

    @Override
    Boolean eof.get() = offset_ >= size;

    @Override
    conditional Char next() {
        Char[] chars = this.chars;
        Int    of    = offset_;
        if (of < chars.size) {
            Char ch = chars[of++];
            if (hasLineInfo && ch.isLineTerminator()) {
                // there's a weird situation that hearkens back to the teletype (shortly after the
                // invention of the wheel), where a CR was required before an LF in order to achieve
                // the functionality of a "new line"; Microsoft retained this antiquated convention,
                // so we are forced to peek at the next char, see if it is an LF, and if it is, then
                // we have to ignore the preceding CR (as if it were a regular character), because
                // it's followed by something that will actually act as a for-real line terminator
                if (ch != '\r' || of >= chars.size || chars[of] != '\n') {
                    ++lineNumber_;
                    lineStartOffset_ = of;
                }
            }
            offset_ = of;
            return True, ch;
        }
        return False;
    }

    @Override
    conditional Char peek() {
        Char[] chars = this.chars;
        Int    of    = offset_;
        if (of < chars.size) {
            return True, chars[of];
        }
        return False;
    }

    @Override
    conditional CharArrayReader advance(Int count = 1) {
        if (hasLineInfo) {
            return super(count);
        }

        if (count < 0) {
            return rewind(-count);
        }

        Int newOffset = offset_ + count;
        if (newOffset > size) {
            offset_ = size;
            return False;
        }
        offset_ = newOffset;
        return True, this;
    }

    @Override
    conditional CharArrayReader rewind(Int count = 1) {
        if (count < 0) {
            return advance(-count);
        }

        Int oldOffset = offset_;
        Int newOffset = oldOffset - count;
        if (newOffset <= 0) {
            reset();
            return newOffset == 0, this;
        }

        // this tests both for (i) if we're not tracking line info or (ii) we're not rewinding to a
        // different line; in both cases, we just need to set offset_ and we're done
        if (lineStartOffset_ <= newOffset) {
            offset_ = newOffset;
            return True, this;
        }

        if (count > (oldOffset >> 2).notLessThan(100)) {
            // this is rewinding more than a quarter of the way back through the reader, so just
            // jettison the line information
            clearLineInfo();
            offset_ = newOffset;
            return True, this;
        }

        // rewind character by character
        Char[] chars   = this.chars;
        Int    of      = lineStartOffset_ - 1;
        Int    curLine = lineNumber_;
        while (of >= 0) {
            Char ch = chars[of];
            if (ch.isLineTerminator() && !(ch == '\r' && of+1 < chars.size && chars[of+1] == '\n')) {
                if (of < newOffset) {
                    // we have reached the beginning of the line that contains newOffset; position
                    // `of` on the first character of the line
                    ++of;
                    break;
                }
                // otherwise, we are passing a line boundary (in reverse)
                --curLine;
            }
            --of;
        }
        offset_          = newOffset;
        lineStartOffset_ = of.notLessThan(0);
        lineNumber_      = curLine;
        return True, this;
    }

    @Override
    CharArrayReader reset() {
        offset_          = 0;
        lineNumber_      = 0;
        lineStartOffset_ = 0;
        return this;
    }

    @Override
    immutable Char[] toCharArray() = chars;

    @Override
    String toString() = str ?: (str <- new String(chars));

    // ----- internal ------------------------------------------------------------------------------

    /**
     * `True` iff the line number is currently being tracked.
     */
    protected Boolean hasLineInfo.get() = lineNumber_ >= 0;

    /**
     * Ensure the the [lineNumber_] and [lineStartOffset] properties are set.
     */
    protected void ensureLineInfo() {
        if (lineNumber_ < 0) {
            Char[] chars = this.chars;
            Int    of    = 0;
            Int    stop  = offset_;
            Int    lines = 0;
            Int    start = 0;
            while (of < stop) {
                Char ch = chars[of++];
                if (ch.isLineTerminator() && (ch != '\r' || of >= chars.size || chars[of] != '\n')) {
                    ++lines;
                    start = of;
                }
            }
            lineNumber_      = lines;
            lineStartOffset_ = start;
        }
    }

    /**
     * Ensure the the [lineNumber_] and [lineStartOffset] properties are obviously invalid.
     */
    protected void clearLineInfo() {
        // these values are illegal, and indicate that the line info is not being actively tracked
        lineNumber_      = -1;
        lineStartOffset_ = -1;
    }
}