package org.xvm.compiler2.parser;

/**
 * An immutable span of text in a source file.
 */
public record TextSpan(
        SourceText source,
        int start,
        int length
) {
    /**
     * @return the end offset (exclusive)
     */
    public int end() {
        return start + length;
    }

    /**
     * @return the start location
     */
    public TextLocation getStartLocation() {
        return source.getLocation(start);
    }

    /**
     * @return the end location
     */
    public TextLocation getEndLocation() {
        return source.getLocation(end());
    }

    /**
     * @return the text covered by this span
     */
    public String getText() {
        return source.substring(start, end());
    }

    /**
     * @return true if this span is empty
     */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Check if this span contains the given offset.
     *
     * @param offset the offset to check
     * @return true if offset is within this span
     */
    public boolean contains(int offset) {
        return offset >= start && offset < end();
    }

    /**
     * Check if this span overlaps with another.
     *
     * @param other the other span
     * @return true if the spans overlap
     */
    public boolean overlaps(TextSpan other) {
        return start < other.end() && other.start < end();
    }

    @Override
    public String toString() {
        return "TextSpan[" + start + ".." + end() + "]";
    }
}
