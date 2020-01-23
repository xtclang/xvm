import io.Writer;

/**
 * A StringWriter is an `Appender<Char>` that accumulates the characters to an internal buffer, and
 * tracks its position.
 */
class StringWriter
        implements Writer
        implements Stringable
    {
    /**
     * Construct a StringWriter.
     *
     * @param capacity  an optional value indicating the expected size of the resulting String
     */
    construct(Int capacity = 0)
        {
        chars = new Array<Char>(capacity);
        }

    /**
     * The underlying representation of a StringWriter is a mutable array of characters.
     */
    private Char[] chars;


    // ----- Writer methods ------------------------------------------------------------------------

    @Override
    immutable Char[] toCharArray()
        {
        return chars.ensureImmutable(True);
        }

    @Override
    String toString()
        {
        return new String(chars);
        }


    // ----- Reader.Position methods ---------------------------------------------------------------

    @Override
    Int offset.get()
        {
        return chars.size;
        }

    @Override
    public/private Int lineNumber;

    @Override
    public/private Int lineOffset;


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return chars.size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add(chars);
        }


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    StringWriter add(Char v)
        {
        chars[chars.size] = v;
        if (v == '\n')
            {
            ++lineNumber;
            lineOffset = 0;
            }
        else
            {
            ++lineOffset;
            }
        return this;
        }

    @Override
    StringWriter ensureCapacity(Int count)
        {
        if (chars.capacity - chars.size < count)
            {
            chars.capacity = chars.size + count;
            }
        return this;
        }
    }
