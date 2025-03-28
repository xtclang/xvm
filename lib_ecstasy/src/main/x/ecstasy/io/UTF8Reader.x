import TextPosition.Stringer;

/**
 * A `UTF8Reader` represents a bounded source of character data from an underlying [InputStream].
 */
class UTF8Reader
        implements Reader {
    // ----- constructors --------------------------------------------------------------------------

    construct(InputStream in) {
        this.in      = in;
        this.rawBase = in.offset;
    }

    // ----- internal properties -------------------------------------------------------------------

    /**
     * The [InputStream] to read UTF8-encoded characters from.
     */
    protected/private InputStream in;

    /**
     * The initial offset in the [InputStream] when this `UTF8Reader` was constructed.
     */
    protected/private Int rawBase;

    /**
     * The adjusted offset in the [InputStream] vis-a-vis the initial offset.
     */
    protected/private Int rawOffset {
        @Override
        Int get() = in.offset - rawBase;

        @Override
        void set(Int newValue) {
            in.offset = rawBase + newValue;
        }
    }

    /**
     * True iff the `UTF8Reader` has encountered any non-ASCII characters (any characters using a
     * multi-byte UTF-8 encoding to the left of the current offset).
     */
    protected Boolean anyMultibyte.get() = offset != rawOffset;

    private Int size_ = -1;

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

    // ----- Reader interface ----------------------------------------------------------------------

    @Override
    Int offset {
        @Override
        Int get() = offset_;

        @Override
        void set(Int newValue) = skip(newValue - offset_);
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
                // is the new position to the left or the right of the old position
                if (newValue > oldValue) {
                    // scan forward, to verify no end-of-lines will be passed
                    // TODO
//                        assert:bounds !chars[i].isLineTerminator();
                } else {
                    rewind(oldValue - newValue);
                }
            }
        }
    }

    @Override
    TextPosition position {
        @Override
        TextPosition get() = snapshot(this);

        @Override
        void set(TextPosition newPos) {
            TODO
        }
    }
// TODO REVIEW? do we need a special carrier that copies raw offset out and back in?
//    @Override
//    TextPosition position {
//        @Override
//        TextPosition get() {
//            return offset <= 0xFFFFF && lineNumber <= 0xFFFF && lineOffset <= 0xFFFF
//                    && (rawOffset - offset) <= 0xFFF
//                            ? new TinyPos(offset, lineNumber, lineOffset, rawOffset)
//                            : new SimplePos(offset, lineNumber, lineOffset, rawOffset);
//        }
//
//        @Override
//        void set(TextPosition position) {
//            assert:arg position.is(SimplePos) || position.is(TinyPos);
//
//            offset          = position.offset;
//            lineNumber      = position.lineNumber;
//            lineStartOffset = position.lineStartOffset;
//            rawOffset       = position.rawOffset;
//        }
//    }

    @Override
    Int size.get() {
        Int value = size_;
        if (value < 0) {
            // lazily compute the size, starting from the current offset_
            // TODO
            size_ = value;
        }
        return value;
    }

    @Override
    Int remaining.get() = size - offset_;

    @Override
    Boolean eof.get() = in.eof;

    @Override
    conditional Int knownSize() {
        Int value = size_;
        return value >= 0, value;
    }

    @Override
    conditional Char next() {
        InputStream in = this.in;
        if (in.eof) {
            return False;
        }

        Char ch        = DataInput.readUTF8Char(in);
        Int  newOffset = offset_ + 1;
        CheckNewLine: if (lineNumber_ >= 0 && ch.isLineTerminator()) {
            if (ch == '\r' && !in.eof) {
                // there's a weird situation that hearkens back to the teletype (shortly after the
                // invention of the wheel), where a CR was required before an LF in order to achieve
                // the functionality of a "new line"; Microsoft retained this antiquated convention,
                // so we are forced to peek at the next char, see if it is an LF, and if it is, then
                // we have to ignore the preceding CR (as if it were a regular character), because
                // it's followed by something that will actually act as a for-real line terminator
                Int  ofNext = in.offset;
                Char chNext = DataInput.readUTF8Char(in);
                in.offset = ofNext;
                if (chNext == '\n') {
                    break HandleTerminator;
                }
            }
            ++lineNumber;
            lineStartOffset_ = newOffset;
        }
        offset_ = newOffset;
        return True, ch;
    }

    @Override
    conditional Char peek() {
        InputStream in = this.in;
        if (in.eof) {
            return False;
        }

        Int  of = in.offset;
        Char ch = DataInput.readUTF8Char(in);
        in.offset = of;
        return True, ch;
    }

    /**
     * Like [next], but in reverse.
     */
    protected conditional Char prev() {
        Int oldOffset = offset_;
        if (oldOffset <= 0) {
            return False;
        }

        InputStream in           = this.in;
        Int         oldRawOffset = in.offset;
        Int         of           = oldRawOffset - 1;
        in.offset = of;
        Byte b = in.readByte();
        Char ch;
        if (b & 0xC0 == 0x80) {
            // multi-byte UTF8 char
            UInt32 codepoint = b & 0x3F;
            Int    trailing  = 1;
            while (True) {
                in.offset = --of;
                b = in.readByte();
                if (b & 0xC0 == 0x80) {
                    codepoint |= b & 0x3F << 6 * trailing++;
                    continue;
                }
                assert trailing <= 5 && b & 0xFE << 6 - trailing == 0xFE << 6 - trailing;
                codepoint |= 0x3F >>> trailing & b << 6 * trailing;
                break;
            }
            ch = codepoint.toChar();
        } else {
            // ascii char
            assert b & 80 == 0;
            ch = b.toChar();
        }
        CheckNewLine: if (lineNumber_ >= 0 && ch.isLineTerminator()) {
            if (ch == '\r') {
                // if it's \r\n, we only count the \n, not both of them
                in.offset = oldRawOffset;
                if (!in.eof) {
                    if (DataInput.readUTF8Char(in) == '\n') {
                        break CheckNewLine;
                    }
                }
            }
            --lineNumber_;
            lineStartOffset_ = -1;
        }
        offset_   = oldOffset - 1;
        in.offset = of;
        return True, ch;
    }

    @Override
    Reader rewind(Int count = 1) {
        if (count == 0) {
            return this;
        }

        assert:arg count > 0;
        Int target = (offset - count).notLessThan(0);
        if (target == 0) {
            return reset();
        }

        // TODO go backwards but ignore all UTF8 trailing bytes
        return reset().skip(target);
    }

    @Override
    Reader reset() {
        offset_          = 0;
        lineNumber_      = 0;
        lineStartOffset_ = 0;
        rawOffset        = 0;

        return this;
    }

    @Override
    Boolean hasAtLeast(Int count) {
        // while UTF-8 allows for up to 6 bytes per character, the legal Unicode codepoint range
        // never exceeds 3 bytes per character
        if (in.remaining / 3 > count) {
            return True;
        }

        // UTF-8 requires at least 1 byte per character
        if (in.remaining < count) {
            return False;
        }

        // brute force check: peek forward using the underlying stream (no need to track line number
        // and offset, since we're just peeking)
        InputStream in = this.in;
        try (Int of = in.offset) {
            while (count > 0) {
                if (in.eof) {
                    return False;
                }
                DataInput.readUTF8Char(in);
                --count;
            }
        } catch (EndOfFile e) {
            return False;
        } finally {
            in.offset = of;
        }

        return True;
    }

    @Override
    conditional UTF8Reader seekLine(Int line) {
        // TODO use the underlying stream directly
        // for current line, use lineStartOffset
        // for line > current, seek forward
        // for line == 0 reset()
        // for line > 0 but line < current, could go forwards or backwards
        return super(line);
    }

    @Override
    void close(Exception? cause = Null) {
        in.close(cause);
    }

    // ----- Position implementation ---------------------------------------------------------------

    /**
     * Abstract base class for the two Position implementations.
     */
    @Abstract
    protected static const AbstractPos
            implements Reader
            incorporates Stringer {
        @Abstract
        @RO Int rawOffset;
    }

    /**
     * Simple constant implementation of the Position interface.
     */
    private static const SimplePos(Int offset, Int lineNumber, Int lineOffset, Int rawOffset)
            extends AbstractPos;

    /**
     * A Position implementation that packs all the data into a single Int64.
     */
    private static const TinyPos
            extends AbstractPos {
        construct(Int offset, Int lineNumber, Int lineOffset, Int rawOffset) {
            // instead of storing the entire raw offset, just store the amount that it exceeds the
            rawOffset -= offset;

            // up to 20 bits for raw (byte) offset, 16 bits for line and line offset, and 12 bits
            // for the character offset vis-a-vis the byte offset
            assert:arg offset     >= 0 && offset     <= 0xFFFFF;
            assert:arg lineNumber >= 0 && lineNumber <= 0xFFFF;
            assert:arg lineOffset >= 0 && lineOffset <= 0xFFFF;
            assert:arg rawOffset  >= 0 && rawOffset  <= 0xFFF;

            combo = (offset << 16 | lineNumber << 16 | lineOffset << 12 | rawOffset).toInt64();
        }

        private Int64 combo;

        @Override
        Int offset.get() {
            return combo >>> 44;
        }

        @Override
        Int lineNumber.get() {
            return combo >>> 28 & 0xFFFF;
        }

        @Override
        Int lineOffset.get() {
            return combo >>> 12 & 0xFFFF;
        }

        @Override
        Int rawOffset.get() {
            return offset + (combo & 0xFFF);
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Ensure the the [lineNumber_] and [lineStartOffset] properties are set.
     */
    protected void ensureLineInfo() {
        if (lineNumber_ < 0) {
// TODO
//            Char chars = this.chars;
//            Int  of    = 0;
//            Int  stop  = offset_;
            Int  lines = 0;
            Int  start = 0;
//            while (of < stop) {
//                Char ch = chars[of++];
//                if (ch.isLineTerminator() && (ch != '\r' || of >= chars.size || chars[of] != '\n')) {
//                    ++lines;
//                    start = of;
//                }
//            }
            lineNumber_      = lines;
            lineStartOffset_ = start;
        } else if (lineStartOffset_ < 0) {
            // TODO
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