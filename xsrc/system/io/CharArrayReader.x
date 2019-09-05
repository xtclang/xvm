import Reader.AbstractPos;

/**
 * A CharArrayReader provides a Reader interface on top of a raw `Char[]` or a `String`. Because its
 * unit of storage is already in the form of a `Char`, its navigation both forwards and backwards
 * can be more efficient than a format with an underlying variable-length binary encoding, such as
 * UTF-8.
 */
class CharArrayReader(immutable Char[] chars)
        implements Reader
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(String str)
        {
        construct CharArrayReader(str.toCharArray());
        this.str = str;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The character contents.
     */
    protected/private immutable Char[] chars;

    /**
     * A cached String representing the contents.
     */
    protected/private String? str;

    /**
     * @return the total number of characters represented by the reader
     */
    Int size.get()
        {
        return chars.size;
        }

    /**
     * @return the remaining (yet to be read) number of characters represented by the reader
     */
    Int remaining.get()
        {
        return size - offset;
        }

    /**
     * The offset of the first character (if any) in the current line.
     */
    protected/private Int lineStartOffset;


    // ----- Position implementation ---------------------------------------------------------------

    /**
     * Simple constant implementation of the Position interface.
     */
    private static const SimplePos(Int offset, Int lineNumber, Int lineOffset)
            extends AbstractPos;

    /**
     * A Position implementation that packs all the data into a single Int.
     */
    private static const TinyPos
            extends AbstractPos
        {
        construct(Int offset, Int lineNumber, Int lineOffset)
            {
            // up to 24 bits for offset, 20 bits for line and line offset
            assert:arg offset     >= 0 && offset     <= 0xFFFFFF;
            assert:arg lineNumber >= 0 && lineNumber <= 0xFFFFF;
            assert:arg lineOffset >= 0 && lineOffset <= 0xFFFFF;

            combo = offset << 20 | lineNumber << 20 | lineOffset;
            }

        private Int combo;

        @Override
        Int offset.get()
            {
            return combo >>> 40;
            }

        @Override
        Int lineNumber.get()
            {
            return combo >>> 20 & 0xFFFFF;
            }

        @Override
        Int lineOffset.get()
            {
            return combo & 0xFFFFF;
            }
        }


    // ----- Reader operations ---------------------------------------------------------------------

    @Override
    public/private Int offset;

    @Override
    public/private Int lineNumber;

    @Override
    Int lineOffset.get()
        {
        return offset - lineStartOffset;
        }

    @Override
    AbstractPos position
        {
        @Override
        AbstractPos get()
            {
            return offset <= 0xFFFFFF && lineNumber <= 0xFFFFF && lineOffset <= 0xFFFFF
                    ? new TinyPos(offset, lineNumber, lineOffset)
                    : new SimplePos(offset, lineNumber, lineOffset);
            }

        @Override
        void set(AbstractPos position)
            {
            assert:arg position.is(SimplePos) || position.is(TinyPos);

            offset          = position.offset;
            lineNumber      = position.lineNumber;
            lineStartOffset = position.lineStartOffset;
            }
        }

    @Override
    Boolean eof.get()
        {
        return offset >= size;
        }

    @Override
    Char nextChar()
        {
        if (eof)
            {
            throw new EndOfFile();
            }

        Char ch = chars[offset++];

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
                if (chars[offset] == '\n')
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
        assert:arg count >= 0;
        if (count > offset)
            {
            return reset();
            }

        if (count <= lineOffset)
            {
            // rewind within the current line
            offset -= count;
            return this;
            }

        // TODO this could be optimized further
        return super(count);
        }

    @Override
    Reader reset()
        {
        offset          = 0;
        lineNumber      = 0;
        lineStartOffset = 0;

        return this;
        }


    // ----- bulk read operations ------------------------------------------------------------------

    @Override
    Boolean hasAtLeast(Int count)
        {
        return count <= remaining;
        }


    // ----- line oriented operations --------------------------------------------------------------

    @Override
    CharArrayReader seekLine(Int line)
        {
        TODO
        }

    @Override
    String nextLine()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the contents of this entire Reader, as an immutable Array of Char
     */
    @Override
    immutable Char[] toCharArray()
        {
        return chars;
        }

    @Override
    String toString()
        {
// TODO GG
//Failed to generate code for Compiler (Module=Ecstasy.xtclang.org, Stage=Emitting)
//java.lang.NullPointerException
//	at org.xvm.compiler.ast.StatementBlock$RootContext.isReservedNameReadable(StatementBlock.java:716)
//	at org.xvm.compiler.ast.StatementBlock$RootContext.getVarAssignment(StatementBlock.java:662)
//	at org.xvm.compiler.ast.Context.getVarAssignment(Context.java:764)
//	at org.xvm.compiler.ast.Context.getVarAssignment(Context.java:764)
//	at org.xvm.compiler.ast.StatementBlock.validateImpl(StatementBlock.java:322)
//	at org.xvm.compiler.ast.Statement.validate(Statement.java:140)
//	at org.xvm.compiler.ast.StatementBlock.compileMethod(StatementBlock.java:267)
//        return str ?: () ->
//            {
//            String result = super();
//            str = result;
//            return result;
//            }();
        return str ?:
            {
            String result = super();
            str = result;
            return result;
            };
        }
    }
