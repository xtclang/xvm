package org.xvm.compiler2.parser;

import java.util.Objects;

/**
 * Immutable representation of source code text with position tracking.
 * <p>
 * This provides efficient line/column computation from offsets and
 * support for extracting source ranges for error messages.
 */
public final class SourceText {

    private final String text;
    private final String fileName;
    private final int[] lineStarts;

    /**
     * Create a SourceText from the given string.
     *
     * @param text     the source code text
     * @param fileName the file name (for error messages)
     */
    public SourceText(String text, String fileName) {
        this.text = Objects.requireNonNull(text, "text");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.lineStarts = computeLineStarts(text);
    }

    /**
     * Create a SourceText from a string with a default file name.
     *
     * @param text the source code text
     */
    public SourceText(String text) {
        this(text, "<unknown>");
    }

    private static int[] computeLineStarts(String text) {
        // Count lines first
        int lineCount = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineCount++;
            }
        }

        // Build line starts array
        int[] starts = new int[lineCount];
        starts[0] = 0;
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && line < lineCount) {
                starts[line++] = i + 1;
            }
        }

        return starts;
    }

    /**
     * @return the source text
     */
    public String getText() {
        return text;
    }

    /**
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @return the length of the source text
     */
    public int getLength() {
        return text.length();
    }

    /**
     * @return the number of lines in the source
     */
    public int getLineCount() {
        return lineStarts.length;
    }

    /**
     * Get the 0-based line number for a character offset.
     *
     * @param offset the character offset
     * @return the line number (0-based)
     */
    public int getLine(int offset) {
        if (offset < 0 || offset > text.length()) {
            throw new IndexOutOfBoundsException("offset: " + offset);
        }

        // Binary search for line
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (lineStarts[mid] <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Get the 0-based column number for a character offset.
     *
     * @param offset the character offset
     * @return the column number (0-based)
     */
    public int getColumn(int offset) {
        int line = getLine(offset);
        return offset - lineStarts[line];
    }

    /**
     * Get a TextLocation for the given offset.
     *
     * @param offset the character offset
     * @return the location
     */
    public TextLocation getLocation(int offset) {
        int line = getLine(offset);
        int column = offset - lineStarts[line];
        return new TextLocation(this, offset, line, column);
    }

    /**
     * Get a TextSpan for the given range.
     *
     * @param start  the start offset
     * @param length the length
     * @return the span
     */
    public TextSpan getSpan(int start, int length) {
        return new TextSpan(this, start, length);
    }

    /**
     * Get the text of a line.
     *
     * @param line the 0-based line number
     * @return the line text (without newline)
     */
    public String getLineText(int line) {
        if (line < 0 || line >= lineStarts.length) {
            throw new IndexOutOfBoundsException("line: " + line);
        }

        int start = lineStarts[line];
        int end;
        if (line + 1 < lineStarts.length) {
            end = lineStarts[line + 1] - 1; // exclude newline
        } else {
            end = text.length();
        }

        // Handle CR-LF
        if (end > start && end - 1 >= 0 && text.charAt(end - 1) == '\r') {
            end--;
        }

        return text.substring(start, end);
    }

    /**
     * Get a substring of the source.
     *
     * @param start the start offset
     * @param end   the end offset (exclusive)
     * @return the substring
     */
    public String substring(int start, int end) {
        return text.substring(start, end);
    }

    /**
     * Get a character at the given offset.
     *
     * @param offset the offset
     * @return the character
     */
    public char charAt(int offset) {
        return text.charAt(offset);
    }

    @Override
    public String toString() {
        return "SourceText[" + fileName + ", " + text.length() + " chars, " + lineStarts.length + " lines]";
    }
}
