/**
 * A StringWriter is a delegating text [Writer] that tracks its current position.
 */
class StringWriter(Writer writer)
        implements Writer
        implements TextPosition
    {
    // ----- TextPosition --------------------------------------------------------------------------

    @Override
    public/private Int offset;

    @Override
    public/private Int lineNumber;

    @Override
    public/private Int lineStartOffset;


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    StringWriter add(Char v)
        {
        writer.add(v);

        ++offset;
        if (v == '\n')
            {
            ++lineNumber;
            lineStartOffset = offset;
            }

        return this;
        }

    @Override
    StringWriter ensureCapacity(Int count)
        {
        writer.ensureCapacity(count);
        return this;
        }
    }
