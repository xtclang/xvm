/**
 * A Reader represents a bounded source of character data. It is, in essence, an InputStream of
 * character values, but one with awareness of basic line-oriented text formatting.
 */
interface Reader
        extends Iterator<Char>
        extends TextPosition
        extends Closeable {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * Setting the offset may be an expensive operation.
     */
    @Override
    Int offset;

    /**
     * When this property is set, the [lineOffset] will automatically be reset to `0`, and the
     * [offset] will be adjusted as necessary. An [OutOfBounds] exception will be thrown if the
     * specified value is not within the bounds of the `Reader`.
     *
     * Getting or setting the line number may be an expensive operation.
     */
    @Override
    Int lineNumber;

    @Override
    @RO Int lineStartOffset;

    /**
     * When this property is set, the [lineNumber] will remain unchanged but the [offset] may
     * change. An [OutOfBounds] exception will be thrown if the specified value is not within the
     * bounds of the current line.
     *
     * Getting or setting the line offset may be an expensive operation.
     */
    @Override
    Int lineOffset;

    /**
     * The current [TextPosition] within the character stream. It is possible to use this property
     * to save off and later (optionally) restore a `TextPosition` within the `Reader`, but the main
     * purpose of this property is to save off information about a location within the `Reader` that
     * can provide a user with information such as line number and offset.
     */
    TextPosition position;

    /**
     * The known bounds (the "total size") of the `Reader`, including characters that have already
     * been read.
     *
     * Calculating the total reader size may be a very expensive operation.
     *
     * Note: This method is often overridden on an implementation of `Reader`.
     */
    @RO Int size.get() = offset + remaining;

    /**
     * The number of characters remaining in the stream.
     *
     * Calculating the remaining amount may be a very expensive operation.
     *
     * Note: This property must generally be overridden on an implementation of `Reader`.
     */
    @RO Int remaining;

    /**
     * True iff the end of the character stream has been reached.
     *
     * Note: This property is often overridden on an implementation of `Reader`.
     */
    @RO Boolean eof.get() = remaining <= 0;

    // ----- Iterator operations -------------------------------------------------------------------

    /**
     * @return `True` iff no characters remain in the `Reader`; this is the same as eof
     */
    @Override
    Boolean knownEmpty() = eof;

    /**
     * Note: This method may be overridden on an implementation of `Reader` if the [remaining] value
     * is expensive to compute.
     */
    @Override
    conditional Int knownSize() = (True, remaining);

    @Override
    conditional Char next() {
        // the default implementation of the Reader relies on the combination of eof and take()
        if (eof) {
            return False;
        }

        return True, take();
    }

    /**
     * Note: Either this method or [next()] must be overridden by implementations of `Reader`.
     */
    @Override
    Char take();

    @Override
    Reader skip(Int count = 1) {
        advance(count);
        return this;
    }

    // ----- Reader operations ---------------------------------------------------------------------

    /**
     * Obtain the next character from the `Reader`, **without changing the current `Reader`
     * position**.
     *
     * Note: This method is often overridden on an implementation of `Reader`, because the default
     * implementation of `peek()` is fairly expensive.
     *
     * @return `True` iff the `Reader` has not reached the [eof]
     * @return (conditional) the next character
     */
    conditional Char peek() {
        if (eof) {
            return False;
        }

        // this is inefficient; rewind in particular may be very expensive
        Char ch = take();
        rewind();
        return True, ch;
    }

    /**
     * Advance the `Reader` one character and obtain that next character iff there exists a next
     * character in the `Reader` **and** that next character matches the specified character.
     *
     * @param ch  the character to match
     *
     * @return `True` iff the `Reader` had not already reached the [eof] **and** the next character
     *         matches the specified character `ch`
     * @return (conditional) the next character
     */
    conditional Char match(Char ch) {
        if (Char next := peek(), next == ch) {
            return True, take();
        }
        return False;
    }

    /**
     * Advance the `Reader` one character and obtain that next character iff there exists a next
     * character in the `Reader` **and** that next character matches using the specified function.
     *
     * @param matches  a character-matching function
     *
     * @return `True` iff the `Reader` had not already reached the [eof] **and** the next character
     *         matches using the specified character matching function
     * @return (conditional) the next character
     */
    conditional Char match(function Boolean(Char) matches) {
        if (Char next := peek(), matches(next)) {
            return True, take();
        }
        return False;
    }

    /**
     * Advance the Reader by the specified number of characters, but not past the end.
     *
     * Note: This method is often overridden on an implementation of `Reader`, because the default
     * implementation is unoptimized.
     *
     * @param count  the number of characters to skip over (optional)
     *
     * @return `True` if the specified number of characters were skipped
     * @return (conditional) this `Reader`
     */
    conditional Reader advance(Int count = 1) {
        if (count < 0) {
            return rewind(-count);
        }

        while (count-- > 0) {
            if (!next()) {
                return False;
            }
        }
        return True, this;
    }

    /**
     * Rewind the Reader by the specified number of characters, but not past the beginning.
     *
     * This may be an expensive operation.
     *
     * Note: This method is often overridden on an implementation of `Reader`, because the default
     * implementation is unoptimized.
     *
     * @param count  the number of characters to rewind (optional)
     *
     * @return `True` if the specified number of characters were rewound
     * @return (conditional) this `Reader`
     */
    conditional Reader rewind(Int count = 1) {
        if (count < 0) {
            return advance(-count);
        }

        Int newOffset = offset - count;
        if (newOffset <= 0) {
            reset();
            return newOffset == 0, this;
        }

        // warning: implementations must avoid the possibility of infinite recursion between this
        //          rewind() implementation and the implementation of offset.set()
        offset = newOffset;
        return True, this;
    }

    /**
     * Rewinds the Reader to the beginning.
     *
     * @return this Reader
     */
    Reader reset() {
        offset = 0;
        return this;
    }

    // ----- Sliceable operators -------------------------------------------------------------------

    /**
     * Returns a portion of this `Reader`, as a `String`. This operator leaves the position within
     * the `Reader` unchanged.
     *
     * @param indexes  specifies a starting and stopping [TextPosition] for the slice
     *
     * @return a slice of this `Reader` as a `String`, corresponding to the specified range
     *         [TextPosition]s
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

        try (Int previous = offset) {
            position = indexes.lowerBound;
            if (indexes.lowerExclusive) {
                take();
            }

            String result = nextString(count);
            return indexes.descending ? result.reversed() : result;
        } finally {
            offset = previous;
        }
    }

    /**
     * Returns a portion of this `Reader`, as a `String`. This operator leaves the position within
     * the `Reader` unchanged.
     *
     * @param indexes  specifies a starting and stopping character offset for the slice
     *
     * @return a slice of this `Reader` as a `String`, corresponding to the specified range offsets
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws OutOfBounds  if the range indicates a slice that would contains illegal indexes
     */
    @Op("[..]") String slice(Range<Int> indexes) {
        Int count = indexes.size;
        if (count <= 0) {
            return "";
        }

        try (Int previous = offset) {
            offset = indexes.effectiveLowerBound;
            String result = nextString(count);
            return indexes.descending ? result.reversed() : result;
        } finally {
            offset = previous;
        }
    }

    // ----- bulk read operations ------------------------------------------------------------------

    /**
     * Test to see if the `Reader` contains at least the specified number of remaining characters.
     *
     * Note: This property should be overridden on any implementation of `Reader` that does not have
     * fast access to both the [remaining] and [size] values.
     *
     * This may be an expensive operation.
     *
     * @param count  the number of remaining characters to test for
     *
     * @return `True` iff the `Reader` contains at least the specified number of remaining
     *         characters
     */
    Boolean hasAtLeast(Int count) = remaining >= count;

    /**
     * Read the specified number of characters into the provided array.
     *
     * @param  chars   the character array to read into
     * @param  offset  the offset into the array to store the first character read
     * @param  count   the maximum number of characters to read
     *
     * @return the number of characters read, which is always the same as `count` unless the [eof]
     *         is reached, in which case the number will be less than `count`
     */
    Int nextChars(Char[] chars, Int offset, Int count) {
        assert:arg offset >= 0 && count >= 0;
        Int last = offset + count;
        while (offset < last) {
            if (!(chars[offset++] := next())) {
                return last - offset;
            }
        }
        return count;
    }

    /**
     * Read the specified number of characters, returning those characters as an array.
     *
     * @param count  the number of characters to read
     *
     * @return an array of the specified count of characters (or fewer, if [eof] is encountered),
     *         containing the characters read from the `Reader`
     */
    immutable Char[] nextChars(Int count) {
        Char[] chars = new Char[](count);
        nextChars(chars, 0, count);
        return chars.freeze(inPlace=True);
    }

    /**
     * Read the specified number of characters, returning those characters as a `String`.
     *
     * @param count  the number of characters to take into the resulting `String`
     *
     * @return a `String` of the specified size (or fewer, if [eof] is encountered), containing the
     *         characters read from the `Reader`
     */
    String nextString(Int count) = new String(nextChars(count));

    /**
     * Read from the current position to the end of the line, returning the characters up to but
     * not including the end of the line, and leaving the current position on the beginning of (the
     * first character of) the next line.
     *
     * @return `True` iff the `Reader` had not already reached the end-of-file
     * @return (conditional) the String representing the contents of the current line, starting from
     *         the current position, up to but not including the line terminator
     */
    conditional String nextLine() {
        if (eof) {
            return False;
        }

        StringBuffer buf = new StringBuffer();
        while (Char ch := next()) {
            if (ch.isLineTerminator()) {
                if (ch == '\r') {
                    // '\r' (CR) is followed by an optional '\n' (LF)
                    match('\n');
                }
                break;
            } else {
                buf.add(ch);
            }
        }
        return True, buf.toString();
    }

    /**
     * @return the contents of this entire `Reader`, as an `immutable Char[]`
     */
    immutable Char[] toCharArray() {
        try (Int previous = offset) {
            return reset().nextChars(size);
        } finally {
            offset = previous;
        }
    }

    /**
     * @return the contents of this entire `Reader`, as a [String]
     */
    @Override
    String toString() {
        try (Int previous = offset) {
            return reset().nextString(size);
        } finally {
            offset = previous;
        }
    }

    // ----- line oriented operations --------------------------------------------------------------

    /**
     * Attempt to move the current position to the optionally specified offset within the specified
     * line. If no offset within the line is specified, then the start of the line is the target.
     *
     * If the `Reader` fails to find the desired line and/or offset, it will still move the current
     * `Reader` position as close as possible to the desired line and offset, without exceeding the
     * desired line and offset.
     *
     * @param seekLine    the **zero-based** line number to seek to the beginning of
     * @param seekOffset  (optional) the **zero-based** offset within the line to seek to
     *
     * @return `True` iff the `Reader` contains the specified line with the specified offset, and
     *         was able to successfully seek to that line and offset within the line (if specified)
     * @return (conditional) this `Reader`
     */
    conditional Reader seekLine(Int seekLine, Int seekOffset = 0) {
        assert:arg seekLine >= 0 && seekOffset >= 0;
        Int curLine = lineNumber;
        while (seekLine < curLine) {
            Int lineDiff = curLine - seekLine;
            if (lineDiff < curLine >> 2 && seekLine > 0) {
                rewind(lineDiff + lineOffset);
            } else {
                reset();
            }
            curLine = lineNumber;
        }

        while (seekLine > curLine) {
            Int lineDiff = seekLine - curLine;
            if (lineDiff > 1) {
                // if we need to jump forwards 10 lines, we know it's going to be at least 10 chars
                if (!advance(lineDiff)) {
                    return False;
                }
            } else if (!next()) {
                return False;
            }
            curLine = lineNumber;
        }

        Int advance = seekOffset - lineOffset;
        if (advance < 0) {
            rewind(-advance);
            return True, this;
        }

        // advance a character at a time, making sure we don't accidentally pass a new line
        while (advance-- > 0) {
            if (!next()) {
                return False;
            }
            if (lineNumber > seekLine) {
                rewind(1);
                return False, this;
            }
        }
        return True, this;
    }

    // ----- redirection ---------------------------------------------------------------------------

    /**
     * Pipe the remainder of the contents of this reader to the specified Appender, such as a
     * Writer.
     *
     * @param buf  the Writer or other `Appender<Char>` to pipe to
     *
     * @return the [Writer] that was passed in
     *
     * @throws IOException  represents the general category of input/output exceptions
     */
    Writer pipeTo(Writer buf) {
        while (Char ch := next()) {
            buf.add(ch);
        }
        return buf;
    }

    /**
     * Pipe contents from this stream to the specified stream.
     *
     * @param buf    the [Writer] (an `Appender<Char>`) to pipe to
     * @param count  the number of characters to pipe to the provided [Appender]
     *
     * @return the [Writer] that was passed in
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream is reached before `count` characters have been
     *                      successfully piped to the provided [Writer]
     */
    Writer pipeTo(Writer buf, Int count) {
        if (count > 0) {
            buf.ensureCapacity(count);
            do {
                buf.add(take());
            } while (--count > 0);
        } else {
            assert:arg count == 0;
        }
        return buf;
    }

    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null) {}
}