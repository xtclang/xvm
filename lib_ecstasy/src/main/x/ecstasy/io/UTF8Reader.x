import TextPosition.Stringer;

/**
 * A `UTF8Reader` represents a bounded source of character data encoded in UTF-8 format and read
 * from an underlying [InputStream].
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
     * The calculated size of the `Reader` in characters, or `-1` if it hasn't yet been computed.
     */
    private Int size_ = -1;

    /**
     * Internal offset.
     */
    private Int offset_;

    /**
     * Internal line number.
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
            Int oldValue = get();
            if (newValue != oldValue) {
                assert:bounds newValue >= 0;
                assert:bounds advance(newValue - offset_);
            }
        }
    }

    @Override
    Int lineNumber {
        @Override
        Int get() = lineNumber_;

        @Override
        void set(Int newValue) {
            assert:bounds newValue >= 0;
            assert:bounds seekLine(newValue);
        }
    }

    @Override
    Int lineStartOffset.get() {
        // line start offset isn't always maintained; once we lose it, it stays gone until we need
        // it, at which point we have to recalculate it
        if (lineStartOffset_ < 0) {
            Int oldLineNumber = lineNumber_;
            if (oldLineNumber == 0) {
                return lineStartOffset_ <- 0;
            }

            Int oldRawOffset = rawOffset;
            Int oldOffset    = offset_;
            Int count        = 0;
            assert Char ch := prev();
            if (ch == '\r') {
                // this sucks: we started going backwards, and we may have found the first char of
                // the "\r\n" sequence, which "\r" is NOT treated as a line change (the "\n" is)
                assert next();  // "\r"
                if (ch := next(), ch == '\n') {
                    assert prev();  // "\n"
                    assert prev();  // "\r"
                    ch = ' ';       // lie and pretend the char is any non-line-terminator
                }
            }
            while (!ch.isLineTerminator()) {
                ++count;
                assert ch := prev();
            }
            // restore previous position and include the line offset information
            rawOffset        = oldRawOffset;
            offset_          = oldOffset;
            lineNumber_      = oldLineNumber;
            lineStartOffset_ = oldOffset - count;
        }
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
                    InputStream in           = this.in;
                    Int         oldRawOffset = in.offset;
                    Int         advance      = newValue - oldValue;
                    for (Int i = 0; i < advance; ++i) {
                        if (in.eof) {
                            in.offset = oldRawOffset;   // make it look like we didn't advance
                            throw new OutOfBounds($"eof on line {lineNumber_} at offset {oldValue+i}");
                        }
                        Char ch = DataInput.readUTF8Char(in);
                        if (ch.isLineTerminator()) {
                            in.offset = oldRawOffset;   // make it look like we didn't advance
                            throw new OutOfBounds($"end of line {lineNumber_} at offset {oldValue+i}");
                        }
                    }
                    offset_ += advance;
                } else {
                    rewind(oldValue - newValue);
                }
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
            Int rawOffset  = this.rawOffset;
            Int rawAdjust  = rawOffset - offset;
            return offset <= 0x3FFFF && lineNumber <= 0x3FFF && lineOffset <= 0xFFF && rawAdjust <= 0xFFF
                    ? new TaggedCompressed(offset, lineNumber, lineOffset, rawAdjust, randomId)
                    : new TaggedSnapshot(offset, lineNumber, lineOffset, rawOffset, randomId);
        }

        @Override
        void set(TextPosition newPos) {
            if (newPos.is(Tagged) && newPos.tagMatches(randomId)) {
                offset_          = newPos.offset;
                lineNumber_      = newPos.lineNumber;
                lineStartOffset_ = newPos.lineStartOffset;
                rawOffset        = newPos.rawOffset;
            } else {
                offset = newPos.offset;
                assert:arg lineNumber == newPos.lineNumber;
                assert:arg lineStartOffset == newPos.lineStartOffset;
            }
        }

        private static interface Tagged {
            Boolean tagMatches(Int tag);
            @RO Int rawOffset;
        }

        private static const TaggedSnapshot(Int offset, Int lineNumber, Int lineOffset, Int rawOffset, Int tag)
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

            construct(Int offset, Int lineNumber, Int lineOffset, Int rawAdjust, Int tag) {
                // up to 18 bits for offset, 14 bits for line number, 12 bits for line offset, 12 bits
                // for raw offset adjust, and 8 bits for the tag
                assert:test offset     >= 0 && offset     <= 0x3FFFF;
                assert:test lineNumber >= 0 && lineNumber <= 0x3FFF;
                assert:test lineOffset >= 0 && lineOffset <= 0xFFF;
                assert:test rawAdjust  >= 0 && rawAdjust  <= 0xFFF;

                combo = tag << 18 | offset << 14 | lineNumber << 12 | lineOffset << 12 | rawAdjust;
            }

            private Int combo;

            @Override
            Int offset.get() = combo >>> 38 & 0x3FFFF;

            @Override
            Int lineNumber.get() = combo >>> 24 & 0x3FFF;

            @Override
            Int lineOffset.get() = combo >>> 12 & 0xFFF;

            @Override
            Int rawOffset.get() = offset + combo & 0xFFF;

            @Override
            Int lineStartOffset.get() = offset - lineOffset;

            @Override
            Boolean tagMatches(Int tag) = tag & 0xFF == combo >>> 56;
        }
    }

    @Override
    Int size.get() {
        Int value = size_;
        if (value < 0) {
            // lazily compute the size, starting from the current offset
            InputStream in = this.in;
            try (Int oldRawOffset = in.offset) {
                value = offset_;
                while (!in.eof) {
                    DataInput.readUTF8Char(in);
                    ++value;
                }
                size_ = value;
            } finally {
                in.offset = oldRawOffset;
            }
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
        return value >= 0, value - offset_;
    }

    @Override
    conditional Char next() {
        InputStream in = this.in;
        if (in.eof) {
            return False;
        }

        Char ch        = DataInput.readUTF8Char(in);
        Int  newOffset = offset_ + 1;
        CheckNewLine: if (ch.isLineTerminator()) {
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
                    break CheckNewLine;
                }
            }
            ++lineNumber_;
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
        if (b & 0x80 == 0) {
            // ascii char
            ch = b.toChar();
        } else {
            // multi-byte UTF8 char:  "trailing bytes" in the UTF-8 format have the format 10xxxxxx
            assert b & 0xC0 == 0x80;
            UInt32 codepoint = b & 0x3F;
            Int    trailing  = 1;
            while (True) {
                in.offset = --of;
                b = in.readByte();
                if (b & 0xC0 == 0x80) {
                    codepoint |= b & 0x3F << 6 * trailing++;
                    continue;
                }
                // we've reached the first byte of the UTF-8 byte sequence, which is NOT 10xxxxxx;
                // verify that the first byte has the correct number of leading 1 bits followed by
                // a zero bit
                assert trailing <= 5 && 0xFF << 6 - trailing & b == 0xFE << 6 - trailing;
                codepoint |= 0x3F >>> trailing & b << 6 * trailing;
                break;
            }
            ch = codepoint.toChar();
        }
        CheckNewLine: if (ch.isLineTerminator()) {
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
    conditional UTF8Reader rewind(Int count = 1) {
        if (count < 0) {
            return advance(-count);
        }

        if (count == 0) {
            return True, this;
        }

        Int offset = offset_;
        Int target = offset - count;
        if (target <= 0) {
            return target == 0, reset();
        }

        if (count >= offset >> 2 + offset >> 3 + offset >> 4) {
            // this is rewinding more than 40% of the way back through the reader, so start
            // at the beginning and go forwards instead (assumption: it's faster, and has line info)
            return reset().advance(target);
        }

        // walk backwards
        for (Int i = 0; i < count; ++i) {
            assert prev();
        }
        return True, this;
    }

    @Override
    UTF8Reader reset() {
        offset_          = 0;
        lineNumber_      = 0;
        lineStartOffset_ = 0;
        rawOffset        = 0;
        return this;
    }

    @Override
    Boolean hasAtLeast(Int count) {
        // if the reader size is known, then use it
        if (size_ >= 0) {
            return remaining >= count;
        }

        // while UTF-8 allows for up to 6 bytes per character, the legal Unicode codepoint range
        // never exceeds 3 bytes per character
        Int bytesRemaining = in.remaining;
        if (bytesRemaining >= count * 3) {
            return True;
        }

        // UTF-8 requires at least 1 byte per character
        if (bytesRemaining < count) {
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
        } finally {
            in.offset = of;
        }

        return True;
    }

    @Override
    void close(Exception? cause = Null) = in.close(cause);
}