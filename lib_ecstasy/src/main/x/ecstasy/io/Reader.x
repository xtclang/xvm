/**
 * A Reader represents a bounded source of character data. It is, in essence, an InputStream of
 * character values, but one with awareness of basic line-oriented text formatting. While the Reader
 * is technically bounded, the end bound is only known when it is reached.
 */
interface Reader
        extends Iterator<Char>
        extends TextPosition
        extends Closeable {
    // ----- TextPosition support ------------------------------------------------------------------

    /**
     * Optional base class for TextPosition implementations.
     */
    @Abstract
    protected static const AbstractPos
            implements TextPosition {

        @Override
        Int estimateStringLength() {
            return 3 + lineNumber.estimateStringLength() + lineOffset.estimateStringLength();
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            buf.add('(');
            lineNumber.appendTo(buf);
            buf.add(':');
            lineOffset.appendTo(buf);
            return buf.add(')');
        }
    }


    // ----- general operations --------------------------------------------------------------------

    /**
     * The current position within the character stream. This property allows the caller to save off
     * the current position, and also allows a previously saved position to be restored later.
     */
    TextPosition position;

    /**
     * True iff the end of the stream has been reached.
     */
    @RO Boolean eof;

    /**
     * @return  a value of type Char read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    Char nextChar();

    /**
     * Advance the Reader by the specified number of characters, but not past the end.
     *
     * @param count  the number of characters to skip over (optional)
     *
     * @return this Reader
     */
    @Override
    Reader skip(Int count = 1) {
        assert:arg count >= 0;

        while (count-- > 0 && next()) {}

        return this;
    }

    /**
     * Rewind the Reader by the specified number of characters, but not past the beginning.
     *
     * @param count  the number of characters to rewind (optional)
     *
     * @return this Reader
     */
    Reader rewind(Int count = 1) {
        assert:arg count >= 0;

        Int target = (offset - count).notLessThan(0);
        return reset().skip(target);
    }

    /**
     * Rewinds the Reader to the beginning.
     *
     * @return this Reader
     */
    Reader reset();


    // ----- Iterator ------------------------------------------------------------------------------

    @Override
    conditional Char next() {
        if (eof) {
            return False;
        }

        return True, nextChar();
    }


    // ----- bulk read operators -------------------------------------------------------------------

    /**
     * Returns a portion of this Reader, as a String.
     *
     * @param indexes  specifies a starting and stopping position for the slice
     *
     * @return a slice of this Reader as a String, corresponding to the specified range of
     *         positions
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws OutOfBounds  if the range indicates a slice that would contains illegal indexes
     */
    @Op("[..]") String slice(Range<TextPosition> indexes) {
        Int count = indexes.upperBound.offset
                  - indexes.lowerBound.offset
                  + (indexes.upperExclusive ? 0 : 1)
                  - (indexes.lowerExclusive ? 1 : 0);
        if (count <= 0) {
            return "";
        }

        String result;
        try (TextPosition current = position) {
            position = indexes.lowerBound;
            if (indexes.lowerExclusive) {
                nextChar();
            }

            result = nextString(count);
            if (indexes.descending) {
                result = result.reversed();
            }
        } finally {
            position = current;
        }

        return result;
    }


    // ----- bulk read operations ------------------------------------------------------------------

    /**
     * Test to see if the Reader contains at least the specified number of remaining characters.
     *
     * @param count  the number of remaining characters to test for
     *
     * @return True iff the Reader contains at least the specified number of remaining characters
     */
    Boolean hasAtLeast(Int count);

    /**
     * Read characters into the provided array.
     *
     * @param  chars  the character array to read into
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void nextChars(Char[] chars) {
        nextChars(chars, 0, chars.size);
    }

    /**
     * Read the specified number of characters into the provided array.
     *
     * @param  chars   the character array to read into
     * @param  offset  the offset into the array to store the first character read
     * @param  count   the number of characters to read
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void nextChars(Char[] chars, Int offset, Int count) {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last) {
            chars[offset++] = nextChar();
        }
    }

    /**
     * Read the specified number of characters, returning those characters as an array.
     *
     * @param count  the number of characters to read
     *
     * @return an array of the specified size, containing the characters read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    immutable Char[] nextChars(Int count) {
        Array<Char> chars = new Char[count];
        nextChars(chars);
        return chars.freeze(True);
    }

    /**
     * Read the specified number of characters.
     *
     * @return a String of the specified size
     */
    String nextString(Int count) {
        return new String(nextChars(count));
    }


    // ----- line oriented operations --------------------------------------------------------------

    /**
     * Move the current position to the beginning of the specified line.
     *
     * @param line  the zero-based line number to seek to the beginning of
     *
     * @return this Reader
     *
     * @throws EndOfFile  if an EOF condition is encountered while attempting to seek to the
     *                    specified line
     */
    Reader seekLine(Int line) {
        switch (line <=> lineNumber) {
        case Equal:
            rewind(lineOffset);
            return this;

        case Lesser:
            reset();
            continue;
        case Greater:
            // attempt to "fast forward" to the line
            while (!eof && lineNumber + 1 < line) {
                skip(line - lineNumber);
            }
            // now that we're close, advance until we reach that line
            while (Char ch := next()) {
                if (lineNumber >= line) {
                    assert lineNumber == line;
                    return this;
                }
            }
            throw new EndOfFile();
        }
    }

    /**
     * Read from the current position to the end of the line, returning the characters up to but
     * not including the end of the line, and leaving the current position on the beginning of (the
     * first character of) the next line.
     *
     * @return the String representing the contents of the current line, starting from the current
     *         position, up to but not including the line terminator
     */
    String nextLine() {
        StringBuffer buf = new StringBuffer();
        while (Char ch := next()) {
            if (ch.isLineTerminator()) {
                if (ch == '\r' && !eof && nextChar() != '\n') {
                    rewind(1);
                }

                break;
            } else {
                buf.add(ch);
            }
        }

        return buf.toString();
    }


    // ----- redirection ---------------------------------------------------------------------------

    /**
     * Pipe the remainder of the contents of this reader to the specified Appender, such as a
     * Writer.
     *
     * @param buf  the Writer or other `Appender<Char>` to pipe to
     *
     * @throws IOException  represents the general category of input/output exceptions
     */
    void pipeTo(Appender<Char> buf) {
        while (Char ch := next()) {
            buf.add(ch);
        }
    }

    /**
     * Pipe contents from this stream to the specified stream.
     *
     * @param buf  the Writer or other `Appender<Char>` to pipe to
     * @param max  the number of characters to pipe
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void pipeTo(Appender<Char> buf, Int count) {
        assert:arg count >= 0;

        buf.ensureCapacity(count);

        while (count > 0) {
            buf.add(nextChar());
            --count;
        }
    }


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null) {
    }
}