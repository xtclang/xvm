/**
 * The position within a sequence of character data. The implementation of the Position is opaque to
 * the caller, in order to hide any internal details that may be necessary for an implementation
 * producing and consuming position data to efficiently do so.
 */
interface TextPosition
        extends immutable Orderable
        extends Hashable
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
    @RO Int lineOffset.get()
        {
        return offset - lineStartOffset;
        }

    /**
     * The starting offset of the current line.
     */
    @RO Int lineStartOffset;


    // ----- Orderable & Hashable funky interface implementations ----------------------------------

    static <CompileType extends TextPosition> Int64 hashCode(CompileType value)
        {
        return value.offset.toInt64() ^ value.lineNumber.toInt64();
        }

    static <CompileType extends TextPosition> Ordered compare(CompileType value1, CompileType value2)
        {
        Ordered result = value1.offset <=> value2.offset;
        if (result == Equal)
            {
            result = value1.lineNumber <=> value2.lineNumber;
            if (result == Equal)
                {
                result = value1.lineOffset <=> value2.lineOffset;
                }
            }
        return result;
        }

    static <CompileType extends TextPosition> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.offset     == value2.offset
            && value1.lineNumber == value2.lineNumber
            && value1.lineOffset == value2.lineOffset;
        }
    }