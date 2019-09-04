/**
 * A Reader represents a bounded source of character data. It is, in essence, an InputStream of
 * character values, but one with awareness of basic line-oriented text formatting. While the Reader
 * is technically bounded, the end bound is only known when it is reached.
 */
interface Reader
        extends Iterator<Char>
        extends Position
        extends Closeable
    {
    // ----- Position interface --------------------------------------------------------------------

    /**
     * The position within the character stream. The implementation of the Position is opaque to the
     * caller, in order to hide the internal details that may be necessary for the implementation of
     * Reader to efficiently restore a previous position.
     */
    static interface Position
        {
        /**
         * The character offset within the reader, starting with zero.
         */
        @RO Int offset;

        /**
         * The line number, starting with zero.
         */
        @RO Int lineNumber;

        /**
         * The offset within the current line, starting with zero.
         */
        @RO Int lineOffset;
        }

    /**
     * Optional base class for Position implementations.
     */
    @Abstract
    protected static const AbstractPos
            implements Position
        {
        Int lineStartOffset.get()
            {
            return offset - lineOffset;
            }

        @Override
        String toString() // TODO GG - I want this stuff on Position or on AbstractPos (not the leaf)
            {
            return $"({lineNumber}:{lineOffset})";
            }

        @Override
        Int estimateStringLength() // TODO GG - I really did not want to have to write this method and the next one but const does not call toString()
            {
            return lineNumber.estimateStringLength() + lineOffset.estimateStringLength() + 3;
            }

        @Override
        void appendTo(Appender<Char> appender)
            {
            appender.add('(');
            lineNumber.appendTo(appender);
            appender.add(':');
            lineOffset.appendTo(appender);
            appender.add(')');
            }
        }


    // ----- general operations --------------------------------------------------------------------

    /**
     * The current position within the character stream. This property allows the caller to save off
     * the current position, and also allows a previously saved position to be restored later.
     */
    Position position;

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
    Reader skip(Int count = 1)
        {
        assert:arg count >= 0;

        while (count-- > 0 && next())
            {
            }

        return this;
        }

    /**
     * Rewind the Reader by the specified number of characters, but not past the beginning.
     *
     * @param count  the number of characters to rewind (optional)
     *
     * @return this Reader
     */
    Reader rewind(Int count = 1)
        {
        assert:arg count >= 0;

        Int target = (offset - count).maxOf(0);
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
    conditional Char next()
        {
        if (eof)
            {
            return False;
            }

        return True, nextChar();
        }


    // ----- bulk read operators -------------------------------------------------------------------

    /**
     * Returns a portion of this Reader, as a String.
     *
     * @param range  specifies a starting and stopping position for the slice
     *
     * @return a slice of this Reader as a String, corresponding to the specified range of positions
     *
     * @throws IOException  represents the general category of input/output exceptions
     */
    @Op("[..]")
    String slice(Range<Position> range)
        {
        String result;

        try (Position current = position)
            {
            position = range.lowerBound;
            result   = nextString(range.upperBound.offset - offset + 1);
            if (range.reversed)
                {
                result = result.reverse();
                }
            }
        finally
            {
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
    void nextChars(Char[] chars)
        {
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
    void nextChars(Char[] chars, Int offset, Int count)
        {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last)
            {
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
    immutable Char[] nextChars(Int count)
        {
        Char[] chars = new Char[count];
        nextChars(chars);
        return chars.ensureConst(True);
        }

    /**
     * Read the specified number of characters.
     *
     * @return a String of the specified size
     */
    String nextString(Int count)
        {
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
    Reader seekLine(Int line)
        {
        switch (line <=> lineNumber)
            {
            case Equal:
                rewind(lineOffset);
                return this;

            case Lesser:
                reset();
                continue;
            case Greater:
                // attempt to "fast forward" to the line
                while (!eof && lineNumber + 1 < line)
                    {
                    skip(line - lineNumber);
                    }
                // now that we're close, advance until we reach that line
                while (Char ch := next())
                    {
                    if (lineNumber >= line)
                        {
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
    String nextLine()
        {
        StringBuffer buf = new StringBuffer();
        while (Char ch := next())
            {
            if (ch.isLineTerminator())
                {
                if (ch == '\r' && !eof && nextChar() != '\n')
                    {
                    rewind(1);
                    }

                break;
                }
            else
                {
                buf.add(ch);
                }
            }

        return buf.toString();
        }


    // ----- redirection ---------------------------------------------------------------------------

    /**
     * Pipe the remainder of the contents of this reader to the specified appender, such as a
     * Writer.
     *
     * @param out  the Writer or other `Appender<Char>` to pipe to
     *
     * @throws IOException  represents the general category of input/output exceptions
     */
    void pipeTo(Appender<Char> out)
        {
        while (Char ch := next())
            {
            out.add(ch);
            }
        }

    /**
     * Pipe contents from this stream to the specified stream.
     *
     * @param out  the Writer or other `Appender<Char>` to pipe to
     * @param max  the number of characters to pipe
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void pipeTo(Appender<Char> out, Int count)
        {
        assert:arg count >= 0;

        out.ensureCapacity(count);

        while (count > 0)
            {
            out.add(nextChar());
            --count;
            }
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the contents of this entire Reader, as an immutable Array of Char
     */
    immutable Char[] toCharArray()
        {
        return toString().toCharArray();
        }

    /**
     * @return the contents of this entire Reader, as a String
     */
    @Override
    String toString()
        {
        // TODO GG - fix bug in try..finally (CP bug)
        //Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index 28 out of bounds for length 28
        //	at org.xvm.asm.MethodStructure$Code.follow(MethodStructure.java:2181)
        //	at org.xvm.asm.MethodStructure$Code.follow(MethodStructure.java:2171)
        //	at org.xvm.asm.MethodStructure$Code.eliminateDeadCode(MethodStructure.java:2124)
        //	at org.xvm.asm.MethodStructure$Code.ensureAssembled(MethodStructure.java:2292)
        //	at org.xvm.asm.MethodStructure.assemble(MethodStructure.java:1618)
        //
        // Position current = position;
        // try
        //     {
        //     reset();
        //     StringBuffer buf = new StringBuffer();
        //     while (Char ch := next())
        //         {
        //         buf.add(ch);
        //         }
        //     return buf.toString();
        //     }
        // finally
        //     {
        //     position = current;
        //     }

        StringBuffer buf = new StringBuffer();
        try (Position current = position)
            {
            reset();
            while (Char ch := next())
                {
                buf.add(ch);
                }
            }
        finally
            {
            position = current;
            }
        return buf.toString();
        }


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close()
        {
        }
    }