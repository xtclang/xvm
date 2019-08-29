/**
 * A Reader represents a bounded source of character data. It is, in essence, an InputStream of
 * character values, but one with awareness of basic line-oriented text formatting. While the Reader
 * is technically bounded, the end bound is only known when it is reached.
 */
interface Reader
        extends Iterator<Char>
        extends Closeable
    {
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
     * The current position within the character stream. This can be used to save off the current
     * position and later restore it.
     */
    Position position;

    /**
     * Move the current position to the beginning of the specified line.
     *
     * @param line  the zero-based line number to seek to the beginning of
     *
     * @throws EndOfFile  if an EOF condition is encountered while attempting to seek to the
     *                    specified line
     */
    void seekLine(Int line);

    /**
     * True iff the end of the stream has been reached.
     */
    @RO Boolean eof;

    /**
     * Test to see if the Reader contains at least the specified number of remaining characters.
     *
     * @param count  the number of remaining characters to test for
     *
     * @return True iff the Reader contains at least the specified number of remaining characters
     */
    Boolean peekAtLeast(Int count);

    /**
     * @return  a value of type Char read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    Char nextChar();


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


    // ----- bulk read operations ------------------------------------------------------------------

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
    Char[] nextChars(Int count)
        {
        Char[] chars = new Char[count];
        nextChars(chars);
        return chars;
        }

    /**
     * Read the specified number of characters.

     * @return a String of the specified size
     */
    String nextString(Int count)
        {
        return new String(nextChars(count));
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
        TODO
        }


    // ----- redirection ---------------------------------------------------------------------------

    /**
     * Pipe the remainder of the contents of this reader to the specified writer.
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
    immutable Char[] toCharArray();

    /**
     * @return the contents of this entire Reader, as a String
     */
    @Override
    String toString()
        {
        return new String(toCharArray());
        }


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close()
        {
        }
    }