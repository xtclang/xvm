/**
 * A Writer represents an append-only stream of characters.
 */
interface Writer
        extends Reader.Position
        extends Appender<Char>
        extends Closeable
    {
    // ----- Appender interface --------------------------------------------------------------------

    @Override
    Writer add(Char ch);


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Advance to a new line.
     */
    Writer newline()
        {
        add('\n');
        return this;
        }

    /**
     * Append the specified number of characters to the stream from the provided array.
     *
     * @param  chars   the character array to read from
     * @param  offset  the offset into the array of the first character to write
     * @param  count   the number of characters to write
     */
    Writer add(Char[] chars, Int offset, Int count)
        {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last)
            {
            add(chars[offset++]);
            }

        return this;
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the contents of the Writer as an immutable Array of Char
     */
    immutable Char[] toCharArray();

    /**
     * @return the contents of the Writer as a String
     */
    @Override
    String toString();


    // ----- Closeable interface -------------------------------------------------------------------

    @Override
    void close()
        {
        }
    }