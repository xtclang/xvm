/**
 * The position within a sequence of character data. The implementation of the Position is opaque to
 * the caller, in order to hide any internal details that may be necessary for an implementation
 * producing and consuming position data to efficiently do so.
 */
interface TextPosition
        extends Orderable
        extends Hashable {
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

    /**
     * The starting offset of the current line.
     */
    @RO Int lineStartOffset;


    // ----- Orderable & Hashable funky interface implementations ----------------------------------

    @Override
    static <CompileType extends TextPosition> Int64 hashCode(CompileType value) = value.offset;

    @Override
    static <CompileType extends TextPosition> Ordered compare(CompileType value1, CompileType value2) {
        Ordered result = value1.offset <=> value2.offset;
        if (result == Equal) {
            result = value1.lineNumber <=> value2.lineNumber;
            if (result == Equal) {
                result = value1.lineOffset <=> value2.lineOffset;
            }
        }
        return result;
    }

    @Override
    static <CompileType extends TextPosition> Boolean equals(CompileType value1, CompileType value2) {
        return value1.offset     == value2.offset
            && value1.lineNumber == value2.lineNumber
            && value1.lineOffset == value2.lineOffset;
    }

    // ----- Stringer mixin: supporting Stringable -------------------------------------------------

    /**
     * TextPosition string formatting implementation.
     */
    protected static mixin Stringer
            into TextPosition
            implements Stringable {
        @Override
        Int estimateStringLength() {
            return lineNumber.estimateStringLength() + 1 + lineOffset.estimateStringLength();
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            lineNumber.appendTo(buf);
            buf.add(':');
            return lineOffset.appendTo(buf);
        }
    }

    // ----- Snapshot implementations --------------------------------------------------------------

    /**
     * Produce an immutable snapshot of the provided [TextPosition].
     *
     * @param pos  a [TextPosition] to produce a snapshot from
     *
     * @return an immutable [TextPosition]
     */
    static immutable TextPosition snapshot(TextPosition pos) {
        // the TextPosition may already be immutable, in which case, use it as the snapshot
        return pos.is(immutable)?;

        Int offset     = pos.offset;
        Int lineNumber = pos.lineNumber;
        Int lineOffset = pos.lineOffset;
        return offset <= 0xFFFFFF && lineNumber <= 0xFFFFF && lineOffset <= 0xFFFFF
                ? new Compressed(offset, lineNumber, lineOffset)
                : new Snapshot(offset, lineNumber, lineOffset);
    }

    /**
     * Produce an immutable snapshot from the provided [TextPosition] information.
     *
     * @param offset           the character offset within the [TextPosition]
     * @param lineNumber       the line number within the [TextPosition]
     * @param lineOffset       the line offset within the line within the [TextPosition]
     * @param lineStartOffset  the starting offset of the line within the [TextPosition]
     *
     * @return an immutable [TextPosition]
     */
    static immutable TextPosition snapshot(Int offset,
                                           Int lineNumber,
                                           Int lineOffset,
                                           Int lineStartOffset,
                                          ) {
        assert:arg lineStartOffset == offset - lineOffset;
        return  offset <= 0xFFFFFF && lineNumber <= 0xFFFFF && lineOffset <= 0xFFFFF
                ? new Compressed(offset, lineNumber, lineOffset)
                : new Snapshot(offset, lineNumber, lineOffset);
    }

    /**
     * A simple implementation of the [TextPosition] interface, useful for creating a constant
     * snapshot of `TextPosition` information.
     */
    static const Snapshot(Int offset,
                          Int lineNumber,
                          Int lineOffset,
                         )
            implements TextPosition
            incorporates Stringer {

        assert() {
            assert:arg offset >= 0 && lineNumber >= 0 && lineOffset >= 0;
        }

        @Override
        Int lineStartOffset.get() = offset - lineOffset;
    }

    /**
     * An implementation of the [TextPosition] interface that packs all the data into a single
     * `Int`. This is basically a compressed form of the [Snapshot] implementation.
     */
    static const Compressed
            implements TextPosition
            incorporates Stringer {

        construct(Int offset, Int lineNumber, Int lineOffset) {
            // up to 24 bits for offset, 20 bits for line and line offset
            assert:arg offset     >= 0 && offset     <= 0xFFFFFF;
            assert:arg lineNumber >= 0 && lineNumber <= 0xFFFFF;
            assert:arg lineOffset >= 0 && lineOffset <= 0xFFFFF;

            combo = offset << 20 | lineNumber << 20 | lineOffset;
        }

        private Int combo;

        @Override
        Int offset.get() = combo >>> 40;

        @Override
        Int lineNumber.get() = combo >>> 20 & 0xFFFFF;

        @Override
        Int lineOffset.get() = combo & 0xFFFFF;

        @Override
        Int lineStartOffset.get() = offset - lineOffset;
    }
}