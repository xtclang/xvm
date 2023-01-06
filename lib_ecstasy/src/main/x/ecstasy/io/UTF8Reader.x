/**
 * A UTF8Reader represents a bounded source of character data from an underlying InputStream.
 */
class UTF8Reader
        implements Reader
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(InputStream in)
        {
        this.in         = in;
        this.initOffset = in.offset;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The InputStream to read UTF8-encoded characters from.
     */
    protected/private InputStream in;

    /**
     * The initial offset in the InputStream when this Reader was constructed.
     */
    protected/private Int initOffset;

    /**
     * The offset in the InputStream vis-a-vis the initial offset.
     */
    protected/private Int rawOffset
        {
        @Override
        Int get()
            {
            return in.offset - initOffset;
            }

        @Override
        void set(Int offset)
            {
            in.offset = offset + initOffset;
            }
        }

    /**
     * True iff the reader has encountered any non-ASCII characters (any characters using a
     * multi-byte UTF-8 encoding).
     */
    protected Boolean anyMultibyte.get()
        {
        return offset != rawOffset;
        }


    // ----- Position implementation ---------------------------------------------------------------

    /**
     * Abstract base class for the two Position implementations.
     */
    @Abstract
    protected static const AbstractPos
            extends Reader.AbstractPos
        {
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
            extends AbstractPos
        {
        construct(Int offset, Int lineNumber, Int lineOffset, Int rawOffset)
            {
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
        Int offset.get()
            {
            return combo >>> 44;
            }

        @Override
        Int lineNumber.get()
            {
            return combo >>> 28 & 0xFFFF;
            }

        @Override
        Int lineOffset.get()
            {
            return combo >>> 12 & 0xFFFF;
            }

        @Override
        Int rawOffset.get()
            {
            return offset + (combo & 0xFFF);
            }
        }


    // ----- Reader interface ----------------------------------------------------------------------

    @Override
    public/private Int offset;

    @Override
    public/private Int lineNumber;

    @Override
    public/private Int lineStartOffset;

    @Override
    TextPosition position
        {
        @Override
        TextPosition get()
            {
            return offset <= 0xFFFFF && lineNumber <= 0xFFFF && lineOffset <= 0xFFFF
                    && (rawOffset - offset) <= 0xFFF
                            ? new TinyPos(offset, lineNumber, lineOffset, rawOffset)
                            : new SimplePos(offset, lineNumber, lineOffset, rawOffset);
            }

        @Override
        void set(TextPosition position)
            {
            assert:arg position.is(SimplePos) || position.is(TinyPos);

            offset          = position.offset;
            lineNumber      = position.lineNumber;
            lineStartOffset = position.lineStartOffset;
            rawOffset       = position.rawOffset;
            }
        }

    @Override
    Boolean eof.get()
        {
        return in.eof;
        }

    @Override
    Char nextChar()
        {
        Char ch = DataInput.readUTF8Char(in);
        ++offset;

        HandleTerminator: if (ch.isLineTerminator())
            {
            if (ch == '\r' && !eof)
                {
                // there's a weird situation that hearkens back to the teletype (shortly after the
                // invention of the wheel), where a CR was required before an LF in order to achieve
                // the functionality of a "new line"; Microsoft retained this antiquated convention,
                // so we are forced to peek at the next char, see if it is an LF, and if it is, then
                // we have to ignore the preceding CR (as if it were a regular character), because
                // it's followed by something that will actually act as a for-real line terminator
                Int  ofNext = in.offset;
                Char chNext = DataInput.readUTF8Char(in);
                in.offset = ofNext;
                if (chNext == '\n')
                    {
                    break HandleTerminator;
                    }
                }

            ++lineNumber;
            lineStartOffset = offset;
            }

        return ch;
        }

    @Override
    Reader rewind(Int count = 1)
        {
        if (count == 0)
            {
            return this;
            }

        assert:arg count > 0;
        Int target = (offset - count).maxOf(0);
        if (target == 0)
            {
            return reset();
            }

        // TODO go backwards but ignore all UTF8 trailing bytes
        return reset().skip(target);
        }

    @Override
    Reader reset()
        {
        offset          = 0;
        lineNumber      = 0;
        lineStartOffset = 0;
        rawOffset       = 0;

        return this;
        }

    @Override
    Boolean hasAtLeast(Int count)
        {
        // while UTF-8 allows for up to 6 bytes per character, the legal Unicode codepoint range
        // never exceeds 3 bytes per character
        if (in.remaining / 3 > count)
            {
            return True;
            }

        // UTF-8 requires at least 1 byte per character
        if (in.remaining < count)
            {
            return False;
            }

        // brute force check: peek forward using the underlying stream (no need to track line number
        // and offset, since we're just peeking)
        InputStream in = this.in;
        try (Int of = in.offset)
            {
            while (count > 0)
                {
                if (in.eof)
                    {
                    return False;
                    }
                DataInput.readUTF8Char(in);
                --count;
                }
            }
        catch (EndOfFile e)
            {
            return False;
            }
        finally
            {
            in.offset = of;
            }

        return True;
        }

    @Override
    Reader seekLine(Int line)
        {
        // TODO use the underlying stream directly
        // for current line, use lineStartOffset
        // for line > current, seek forward
        // for line == 0 reset()
        // for line > 0 but line < current, could go forwards or backwards
        return super(line);
        }


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null)
        {
        in.close(cause);
        }
    }